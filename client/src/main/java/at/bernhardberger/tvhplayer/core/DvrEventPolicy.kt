package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

enum class DvrEventKind { SCHEDULED, SERIES_SCHEDULED, STARTED, FINISHED, STOPPED, FAILED, MISSED, CANCELED, DELETED }

data class DvrEvent(val kind: DvrEventKind, val entry: DvrEntry, val count: Int = 1) {
    val key: String get() = if (kind == DvrEventKind.SERIES_SCHEDULED) {
        "dvr-series:${entry.autorecRuleId?.value}"
    } else "dvr:${entry.id.value}"
}

enum class DvrLocalIntentKind { STOP, DELETE }
data class DvrLocalIntent(val id: DvrEntryId, val kind: DvrLocalIntentKind, val issuedAt: Instant) {
    fun isRecent(now: Instant): Boolean = now - issuedAt in 0.seconds..60.seconds
}

fun dvrEvents(
    oldEntries: List<DvrEntry>,
    newEntries: List<DvrEntry>,
    now: Instant,
    recentLocalIntents: List<DvrLocalIntent>,
): List<DvrEvent> {
    val old = oldEntries.associateBy { it.id }
    val new = newEntries.associateBy { it.id }
    fun intended(id: DvrEntryId, kind: DvrLocalIntentKind) = recentLocalIntents.any {
        it.id == id && it.kind == kind && it.isRecent(now)
    }
    val scheduled = newEntries.filter {
        it.id !in old && it.state == DvrEntryState.SCHEDULED && it.start?.let { start -> start > now } == true
    }
    val series = scheduled.filter { it.autorecRuleId != null }.groupBy { it.autorecRuleId }
        .filterValues { it.size >= 2 }
    val emittedSeries = mutableSetOf<at.bernhardberger.tvheadend.sdk.core.AutorecRuleId>()
    return buildList {
        for (entry in newEntries) {
            val previous = old[entry.id]
            val kind = when {
                previous == null && entry in scheduled -> {
                    val rule = entry.autorecRuleId
                    val group = series[rule]
                    if (rule != null && group != null) {
                        if (emittedSeries.add(rule)) add(DvrEvent(DvrEventKind.SERIES_SCHEDULED, entry, group.size))
                        continue
                    }
                    DvrEventKind.SCHEDULED
                }
                entry.state == DvrEntryState.RECORDING &&
                    (previous == null || previous.state == DvrEntryState.SCHEDULED) -> DvrEventKind.STARTED
                previous?.state == DvrEntryState.RECORDING -> when (entry.state) {
                    DvrEntryState.COMPLETED -> if (intended(entry.id, DvrLocalIntentKind.STOP)) {
                        DvrEventKind.STOPPED
                    } else DvrEventKind.FINISHED
                    DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED_ERROR -> DvrEventKind.FAILED
                    else -> null
                }
                previous?.state == DvrEntryState.SCHEDULED && entry.state == DvrEntryState.MISSED -> DvrEventKind.MISSED
                else -> null
            }
            if (kind != null) add(DvrEvent(kind, entry))
        }
        for (entry in oldEntries) {
            if (entry.id in new) continue
            val kind = when (entry.state) {
                DvrEntryState.SCHEDULED -> DvrEventKind.CANCELED
                DvrEntryState.RECORDING -> DvrEventKind.STOPPED.takeIf { intended(entry.id, DvrLocalIntentKind.STOP) }
                DvrEntryState.COMPLETED, DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED_ERROR,
                DvrEntryState.MISSED -> DvrEventKind.DELETED.takeIf { intended(entry.id, DvrLocalIntentKind.DELETE) }
                else -> null
            }
            if (kind != null) add(DvrEvent(kind, entry))
        }
    }
}

/** Non-current observations never advance the baseline or bridge an interruption. */
class DvrEventBaseline {
    private var previous: SessionObservation? = null
    private var uninterrupted = false

    fun advance(observation: SessionObservation): Pair<List<DvrEntry>, List<DvrEntry>>? {
        val current = observation.dvrState as? DvrRepositoryState.Current
        val generation = observation.currentSession?.generationIdentity
        if (current == null || generation == null) {
            uninterrupted = false
            return null
        }
        val old = previous
        val comparable = uninterrupted && old?.currentSession?.generationIdentity === generation
        previous = observation
        uninterrupted = true
        if (!comparable) return null
        val entries = (old?.dvrState as? DvrRepositoryState.Current)?.snapshot?.entries ?: return null
        return (entries to current.snapshot.entries).takeUnless { entries == current.snapshot.entries }
    }
}

/** A server event can precede the command result. Tickets become intents only on acceptance. */
class RecentDvrIntents(private val now: () -> Instant = Clock.System::now) {
    class Ticket internal constructor(
        val generation: SessionGenerationIdentity,
        val intent: DvrLocalIntent,
        val accepted: CompletableDeferred<Boolean> = CompletableDeferred(),
    )

    private val tickets = mutableListOf<Ticket>()

    @Synchronized
    fun begin(generation: SessionGenerationIdentity, id: DvrEntryId, kind: DvrLocalIntentKind): Ticket {
        prune()
        return Ticket(generation, DvrLocalIntent(id, kind, now())).also { tickets += it }
    }

    @Synchronized
    fun finish(ticket: Ticket, accepted: Boolean) {
        ticket.accepted.complete(accepted)
        if (!accepted) tickets.remove(ticket)
        prune()
    }

    @Synchronized
    fun capture(generation: SessionGenerationIdentity): List<Ticket> {
        prune()
        return tickets.filter { it.generation === generation }
    }

    private fun prune() {
        val time = now()
        tickets.removeAll { !it.intent.isRecent(time) }
    }
}
