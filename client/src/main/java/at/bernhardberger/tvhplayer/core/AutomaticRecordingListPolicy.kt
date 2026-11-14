package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.AutorecRule
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TimerecRule

/** List-owned data, retaining the SDK rule as the editor/mutation target. */
sealed interface AutomaticRecordingListItem {
    val kind: AutomaticRecordingKind
    val id: String
    val headline: String
    val channelId: ChannelId?
    val enabled: Boolean
    val upcoming: Int
    val next: DvrEntry?

    data class Automatic(
        val rule: AutorecRule,
        override val upcoming: Int,
        override val next: DvrEntry?,
    ) : AutomaticRecordingListItem {
        override val kind get() = autorecKind(rule)
        override val id get() = rule.id.value
        override val headline get() = readRecordingPattern(rule.title.orEmpty()).words
            .ifEmpty { if (kind == AutomaticRecordingKind.SERIES) rule.name.orEmpty() else "" }
        override val channelId get() = rule.channelId
        override val enabled get() = rule.enabled ?: true
    }

    data class Timer(
        val rule: TimerecRule,
        override val upcoming: Int,
        override val next: DvrEntry?,
    ) : AutomaticRecordingListItem {
        override val kind get() = AutomaticRecordingKind.TIMER
        override val id get() = rule.id.value
        override val headline get() = rule.recordingName()
        override val channelId get() = rule.channelId
        override val enabled get() = rule.enabled ?: true
    }
}

data class AutomaticRecordingList(val items: List<AutomaticRecordingListItem>, val available: Boolean) {
    val onCount get() = items.count { it.enabled }
    val offCount get() = items.size - onCount
}

/** The same stored name shown by the timer editor and the combined list. */
fun TimerecRule.recordingName(): String = name?.takeUnless(String::isBlank) ?: title.orEmpty()

fun automaticRecordingList(
    observation: SessionObservation,
    headlineComparator: Comparator<String> = String.CASE_INSENSITIVE_ORDER,
): AutomaticRecordingList {
    val snapshot = observation.dvrSnapshotForDisplay
    val upcoming = snapshot?.entries.orEmpty().filter {
        it.state == DvrEntryState.SCHEDULED && it.enabled != false && it.duplicate != true
    }
    val automatic = upcoming.groupBy { it.autorecRuleId }
    val timed = upcoming.groupBy { it.timerecRuleId }
    val items = snapshot?.autorecRules.orEmpty().map {
        val own = automatic[it.id].orEmpty()
        AutomaticRecordingListItem.Automatic(it, own.size,
            own.minWithOrNull(compareBy(nullsLast()) { entry: DvrEntry -> entry.start }))
    } + snapshot?.timerecRules.orEmpty().map {
        val own = timed[it.id].orEmpty()
        AutomaticRecordingListItem.Timer(it, own.size,
            own.minWithOrNull(compareBy(nullsLast()) { entry: DvrEntry -> entry.start }))
    }
    return AutomaticRecordingList(items.sortedWith(
        compareBy<AutomaticRecordingListItem> { it.headline.isBlank() }
            .thenBy(headlineComparator) { it.headline }
            .thenBy { it.kind }.thenBy { it.id },
    ), available = snapshot != null)
}
