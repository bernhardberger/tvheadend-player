package at.bernhardberger.tvhplayer.ui.notifications

import android.content.Context
import at.bernhardberger.tvheadend.sdk.core.DvrSubscriptionError
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrEvent
import at.bernhardberger.tvhplayer.core.DvrEventBaseline
import at.bernhardberger.tvhplayer.core.DvrEventKind
import at.bernhardberger.tvhplayer.core.RecentDvrIntents
import at.bernhardberger.tvhplayer.core.dvrEvents
import at.bernhardberger.tvhplayer.ui.player.programmeFactsSegments
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class DvrEventNotices(
    private val context: Context,
    private val intents: RecentDvrIntents,
    private val now: () -> Instant = Clock.System::now,
) {
    private data class Batch(
        val resolve: suspend () -> List<DvrEvent>,
        val time: Instant,
        val generation: SessionGenerationIdentity,
        val context: Any,
    )

    suspend fun collect(observation: StateFlow<SessionObservation>, queue: AppNoticeQueue) {
        val baseline = DvrEventBaseline()
        observation.mapNotNull { current ->
            val (old, new) = baseline.advance(current) ?: return@mapNotNull null
            val generation = current.currentSession?.generationIdentity ?: return@mapNotNull null
            val time = now()
            val tickets = intents.capture(generation)
            val provisional = dvrEvents(old, new, time, tickets.map { it.intent })
            if (provisional.isEmpty()) return@mapNotNull null
            val relevantTickets = tickets.filter { ticket -> provisional.any {
                it.entry.id == ticket.intent.id && it.kind in setOf(DvrEventKind.STOPPED, DvrEventKind.DELETED)
            } }
            Batch(resolve = {
                val accepted = relevantTickets.filter {
                    withTimeoutOrNull(30.seconds) { it.accepted.await() } == true
                }.map { it.intent }
                dvrEvents(old, new, time, accepted)
            }, time, generation, queue.context())
        // Advance the baseline even while an SDK command is awaiting server confirmation.
        }.buffer(8, BufferOverflow.DROP_OLDEST).collect { batch ->
            val events = batch.resolve()
            if (observation.value.currentSession?.generationIdentity !== batch.generation ||
                now() - batch.time >= 30.seconds) return@collect
            events.forEach { event ->
                queue.post(event.key, event.headline(), event.noticeKind(), batch.context,
                    icon = event.icon(), detail = event.detail(context, batch.time))
            }
        }
    }
}

internal fun DvrEvent.headline(): Int = when (kind) {
    DvrEventKind.SCHEDULED -> R.string.recording_notice_scheduled
    DvrEventKind.SERIES_SCHEDULED -> R.string.recording_notice_series_scheduled
    DvrEventKind.STARTED -> R.string.recording_notice_started
    DvrEventKind.FINISHED -> R.string.recording_notice_finished
    DvrEventKind.STOPPED -> R.string.recording_notice_stopped
    DvrEventKind.FAILED -> R.string.recording_notice_failed
    DvrEventKind.MISSED -> R.string.recording_notice_missed
    DvrEventKind.CANCELED -> R.string.recording_notice_canceled
    DvrEventKind.DELETED -> R.string.recording_notice_deleted
}

internal fun DvrEvent.icon(): AppNoticeIcon = when (kind) {
    DvrEventKind.SCHEDULED, DvrEventKind.SERIES_SCHEDULED -> AppNoticeIcon.SCHEDULE
    DvrEventKind.STARTED -> AppNoticeIcon.RECORDING
    DvrEventKind.FINISHED -> AppNoticeIcon.CHECK
    DvrEventKind.STOPPED -> AppNoticeIcon.STOP
    DvrEventKind.FAILED, DvrEventKind.MISSED -> AppNoticeIcon.WARNING
    DvrEventKind.CANCELED -> AppNoticeIcon.CANCEL
    DvrEventKind.DELETED -> AppNoticeIcon.DELETE
}

internal fun DvrEvent.noticeKind(): AppNoticeKind = when (kind) {
    DvrEventKind.FAILED, DvrEventKind.MISSED -> AppNoticeKind.FAILURE
    else -> AppNoticeKind.SUCCESS
}

internal fun DvrEvent.detail(context: Context, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val title = entry.title?.trim()?.takeIf(String::isNotEmpty)
    val channel = entry.channelName?.trim()?.takeIf(String::isNotEmpty)
    val name = title ?: channel ?: context.getString(R.string.recording_notice_fallback)
    val segments = buildList {
        add(name)
        when (kind) {
            DvrEventKind.SCHEDULED -> {
                if (channel != null && channel != name) add(channel)
                entry.start?.let { start ->
                    val locale = context.resources.configuration.locales[0]
                    val date = java.time.Instant.ofEpochMilli(start.toEpochMilliseconds()).atZone(zone)
                    val today = java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atZone(zone).toLocalDate()
                    val day = when (date.toLocalDate()) {
                        today -> context.getString(R.string.today)
                        today.plusDays(1) -> context.getString(R.string.tomorrow)
                        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                    }
                    val time = date.format(DateTimeFormatter.ofPattern("HH:mm", locale))
                    val whenText = "$day $time"
                    if (size > 1) this[lastIndex] = "${last()}, $whenText" else add(whenText)
                }
            }
            DvrEventKind.SERIES_SCHEDULED -> add(context.resources.getQuantityString(
                R.plurals.recording_notice_count, count, count))
            DvrEventKind.STARTED -> if (channel != null && channel != name) add(channel)
            DvrEventKind.FAILED -> entry.subscriptionError?.noticeReason()?.let { add(context.getString(it)) }
            else -> Unit
        }
    }
    // Keep facts together without making a long programme title an unbreakable word.
    return segments.first() + segments.drop(1).takeIf { it.isNotEmpty() }
        ?.let { " · " + programmeFactsSegments(it) }.orEmpty()
}

private fun DvrSubscriptionError.noticeReason(): Int? = when (this) {
    DvrSubscriptionError.NO_FREE_ADAPTER -> R.string.tvh_no_free_adapter
    DvrSubscriptionError.SCRAMBLED -> R.string.tvh_scrambled
    DvrSubscriptionError.BAD_SIGNAL, DvrSubscriptionError.TUNING_FAILED,
    DvrSubscriptionError.MUX_NOT_ENABLED, DvrSubscriptionError.NO_SERVICE,
    DvrSubscriptionError.INVALID_SERVICE -> R.string.recording_notice_no_signal
    DvrSubscriptionError.NO_DISK_SPACE -> R.string.recording_notice_disk_full
    DvrSubscriptionError.USER_ACCESS -> R.string.recording_notice_access
    DvrSubscriptionError.USER_LIMIT -> R.string.recording_notice_limit
    DvrSubscriptionError.WEAK_STREAM -> R.string.recording_notice_weak_stream
    DvrSubscriptionError.UNKNOWN, DvrSubscriptionError.INVALID_TARGET,
    DvrSubscriptionError.SUBSCRIPTION_OVERRIDDEN -> null
}
