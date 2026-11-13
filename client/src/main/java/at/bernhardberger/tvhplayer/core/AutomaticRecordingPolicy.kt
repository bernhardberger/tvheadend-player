package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.AutorecRule
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleCreate
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleUpdate
import at.bernhardberger.tvheadend.sdk.core.AutorecTime
import at.bernhardberger.tvheadend.sdk.core.BroadcastType
import at.bernhardberger.tvheadend.sdk.core.DuplicateDetection
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrPriority
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import kotlin.time.Duration

enum class AutomaticRecordingKind { SERIES, TITLE, KEYWORD, TIMER }

/** Raw expressions keep their mode even after editing; never silently escape a web-UI expression. */
data class RecordingPattern(val words: String, val rawExpression: Boolean = false)

private const val REGEX_META = "\\^$.|?*+()[]{}"
private const val START_WINDOW_STEP_MINUTES = 5
private val lastMinuteOfDay = LocalTime.MAX.withSecond(0).withNano(0)

fun readRecordingPattern(stored: String): RecordingPattern {
    val body = if (stored.startsWith("^") && stored.endsWith("$") && stored.length >= 2)
        stored.substring(1, stored.length - 1) else stored
    val plain = StringBuilder()
    var index = 0
    var raw = false
    while (index < body.length && !raw) {
        val char = body[index++]
        if (char == '\\') {
            raw = index == body.length || body[index] !in REGEX_META
            if (!raw) plain.append(body[index++])
        } else {
            raw = char in REGEX_META
            if (!raw) plain.append(char)
        }
    }
    return if (raw) RecordingPattern(stored, true) else RecordingPattern(plain.toString())
}

internal fun RecordingPattern.stored(kind: AutomaticRecordingKind): String {
    if (rawExpression) return words
    val literal = buildString {
        for (char in words) {
            if (char in REGEX_META) append('\\')
            append(char)
        }
    }
    return if (kind == AutomaticRecordingKind.TITLE) "^$literal$" else literal
}

fun autorecKind(rule: AutorecRule): AutomaticRecordingKind = when {
    !rule.seriesLinkUri.isNullOrBlank() -> AutomaticRecordingKind.SERIES
    rule.title?.let { it.startsWith("^") && it.endsWith("$") && !readRecordingPattern(it).rawExpression } == true ->
        AutomaticRecordingKind.TITLE
    else -> AutomaticRecordingKind.KEYWORD
}

/**
 * Wall-clock times on the server, not the Android device. Matching is inclusive
 * (dvr_autorec.c:283-305): After = [time, 23:59], Before = [00:00, time].
 * Both AnyTime fields disable the window. Preserve wraparound/non-editor shapes verbatim.
 */
sealed interface RecordingStartWindow {
    data object AnyTime : RecordingStartWindow
    data class After(val time: LocalTime) : RecordingStartWindow {
        init { require(time.second == 0 && time.nano == 0 && time.minute % START_WINDOW_STEP_MINUTES == 0) }
    }
    data class Before(val time: LocalTime) : RecordingStartWindow {
        init { require(time.second == 0 && time.nano == 0 && time.minute % START_WINDOW_STEP_MINUTES == 0) }
    }
    data class Stored(val start: AutorecTime.At, val end: AutorecTime.At) : RecordingStartWindow
}

internal fun RecordingStartWindow.bounds(): Pair<AutorecTime?, AutorecTime?> = when (this) {
    RecordingStartWindow.AnyTime -> AutorecTime.AnyTime to AutorecTime.AnyTime
    is RecordingStartWindow.After -> AutorecTime.At(time) to AutorecTime.At(lastMinuteOfDay)
    is RecordingStartWindow.Before -> AutorecTime.At(LocalTime.MIDNIGHT) to AutorecTime.At(time)
    is RecordingStartWindow.Stored -> start to end
}

private fun readStartWindow(start: AutorecTime?, end: AutorecTime?): RecordingStartWindow {
    val from = start as? AutorecTime.At
    val until = end as? AutorecTime.At
    if (from == null || until == null) return RecordingStartWindow.AnyTime
    return when {
        from.time.minute % START_WINDOW_STEP_MINUTES == 0 && until.time == lastMinuteOfDay ->
            RecordingStartWindow.After(from.time)
        from.time == LocalTime.MIDNIGHT && until.time.minute % START_WINDOW_STEP_MINUTES == 0 ->
            RecordingStartWindow.Before(until.time)
        else -> RecordingStartWindow.Stored(from, until)
    }
}

enum class RecordingDraftField {
    WORDS, CHANNEL, DAYS, START_WINDOW, SEARCH_IN, MERGE_TEXT, ENABLED, NAME, KEEP_UNTIL,
    DUPLICATE_DETECTION, PRIORITY, PADDING_BEFORE, PADDING_AFTER, BROADCAST_TYPE,
    MAXIMUM_RECORDINGS, PROFILE, DATE, START, END,
}

/** Duplicate detection and maximum recordings remain server-owned, not app match estimators. */
val autorecMatchAffectingFields: Set<RecordingDraftField> = setOf(
    RecordingDraftField.WORDS, RecordingDraftField.SEARCH_IN, RecordingDraftField.MERGE_TEXT,
    RecordingDraftField.CHANNEL, RecordingDraftField.DAYS, RecordingDraftField.START_WINDOW,
    RecordingDraftField.BROADCAST_TYPE, RecordingDraftField.ENABLED,
)

enum class RecordingSubmitUnavailable { WORDS_REQUIRED, CHANNEL_REQUIRED, UNCHANGED, REPEAT_REQUIRED, EMPTY_SPAN }

val offeredDuplicateDetection: List<DuplicateDetection> = listOf(
    DuplicateDetection.ALL, DuplicateDetection.DIFFERENT_EPISODE_NUMBER,
    DuplicateDetection.DIFFERENT_SUBTITLE, DuplicateDetection.DIFFERENT_DESCRIPTION,
    DuplicateDetection.ONCE_PER_DAY, DuplicateDetection.ONCE_PER_WEEK,
)

/**
 * New nullable duplicate detection follows the recording profile (DVR_AUTOREC_RECORD_DVR_PROFILE,
 * dvr_autorec.c:466); an edit retains the server's stored value, including non-offered policies.
 * Null removal/priority/padding on a new draft omit the field, leaving the profile in charge.
 */
data class AutorecDraft(
    val kind: AutomaticRecordingKind,
    val pattern: RecordingPattern = RecordingPattern(""),
    val seriesLinkUri: String? = null,
    val channel: RecordingRuleChannel = RecordingRuleChannel.AllChannels,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val startWindow: RecordingStartWindow = RecordingStartWindow.AnyTime,
    val fullText: Boolean = false,
    val mergeText: Boolean = false,
    val enabled: Boolean = true,
    val name: String = "",
    val removal: DvrRemoval? = null,
    val duplicateDetection: DuplicateDetection? = null,
    val priority: DvrPriority? = null,
    val paddingBefore: Duration? = null,
    val paddingAfter: Duration? = null,
    val broadcastType: BroadcastType = BroadcastType.ALL,
    val maximumRecordings: Long = 0,
    val configId: DvrConfigId? = null,
    val excludedEventIds: Set<EventId> = emptySet(),
) {
    init { require(kind != AutomaticRecordingKind.TIMER) }
}

/** Timer times are server wall-clock times. Repeating timers never send padding or a date. */
data class TimerDraft(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val channelId: ChannelId? = null,
    val days: Set<DayOfWeek> = emptySet(),
    val name: String = "",
    val removal: DvrRemoval? = null,
    val priority: DvrPriority? = null,
    val configId: DvrConfigId? = null,
    val enabled: Boolean = true,
    val paddingBefore: Duration? = null,
    val paddingAfter: Duration? = null,
    val existingTimer: Boolean = false,
    /** Only app-owned literal titles follow a renamed timer; preserve web-UI strftime formats. */
    val titleFollowsName: Boolean = true,
) {
    init {
        require(start.second == 0 && start.nano == 0 && end.second == 0 && end.nano == 0)
    }
    val kind: AutomaticRecordingKind get() = AutomaticRecordingKind.TIMER
    val repeating: Boolean get() = existingTimer || days.isNotEmpty()
}

fun newAutorecDraft(
    kind: AutomaticRecordingKind,
    configurations: List<DvrConfiguration> = emptyList(),
    preferred: DvrConfigId? = null,
    programme: EpgEvent? = null,
): AutorecDraft {
    require(kind != AutomaticRecordingKind.SERIES || !programme?.seriesLinkUri.isNullOrBlank())
    return AutorecDraft(
        kind,
        pattern = RecordingPattern(programme?.title.orEmpty()),
        seriesLinkUri = programme?.seriesLinkUri?.takeIf { kind == AutomaticRecordingKind.SERIES },
        channel = programme?.channelId?.let(RecordingRuleChannel::SpecificChannel) ?: RecordingRuleChannel.AllChannels,
        name = programme?.title.orEmpty(),
        configId = chooseDvrConfig(configurations, preferred),
    )
}

/** A missing weekday set means the server default, every day; an explicit empty set means never. */
fun AutorecRule.toDraft(): AutorecDraft = AutorecDraft(
    kind = autorecKind(this),
    pattern = readRecordingPattern(title.orEmpty()),
    seriesLinkUri = seriesLinkUri,
    channel = channelId?.let(RecordingRuleChannel::SpecificChannel) ?: RecordingRuleChannel.AllChannels,
    days = daysOfWeekMask ?: DayOfWeek.entries.toSet(),
    startWindow = readStartWindow(startMinutesSinceMidnight, startWindowEndMinutesSinceMidnight),
    fullText = fullText ?: false,
    mergeText = mergeText ?: false,
    enabled = enabled ?: true,
    name = name.orEmpty(),
    removal = removal,
    duplicateDetection = duplicateDetection,
    priority = priority,
    paddingBefore = startExtraMinutes,
    paddingAfter = stopExtraMinutes,
    broadcastType = broadcastType ?: BroadcastType.ALL,
    maximumRecordings = maximumRecordingCount ?: 0,
    configId = configId,
)

/** Excluded episodes are draft-only, not a saved rule field or an update. */
fun AutorecDraft.changedFields(baseline: AutorecDraft): Set<RecordingDraftField> = buildSet {
    fun changed(field: RecordingDraftField, value: Any?, original: Any?) { if (value != original) add(field) }
    changed(RecordingDraftField.WORDS, pattern, baseline.pattern)
    changed(RecordingDraftField.CHANNEL, channel, baseline.channel)
    changed(RecordingDraftField.DAYS, days, baseline.days)
    changed(RecordingDraftField.START_WINDOW, startWindow, baseline.startWindow)
    changed(RecordingDraftField.SEARCH_IN, fullText, baseline.fullText)
    changed(RecordingDraftField.MERGE_TEXT, mergeText, baseline.mergeText)
    changed(RecordingDraftField.ENABLED, enabled, baseline.enabled)
    changed(RecordingDraftField.NAME, name, baseline.name)
    changed(RecordingDraftField.KEEP_UNTIL, removal, baseline.removal)
    changed(RecordingDraftField.DUPLICATE_DETECTION, duplicateDetection, baseline.duplicateDetection)
    changed(RecordingDraftField.PRIORITY, priority, baseline.priority)
    changed(RecordingDraftField.PADDING_BEFORE, paddingBefore, baseline.paddingBefore)
    changed(RecordingDraftField.PADDING_AFTER, paddingAfter, baseline.paddingAfter)
    changed(RecordingDraftField.BROADCAST_TYPE, broadcastType, baseline.broadcastType)
    changed(RecordingDraftField.MAXIMUM_RECORDINGS, maximumRecordings, baseline.maximumRecordings)
    changed(RecordingDraftField.PROFILE, configId, baseline.configId)
}

fun AutorecDraft.cannotSubmit(baseline: AutorecDraft? = null): RecordingSubmitUnavailable? = when {
    kind != AutomaticRecordingKind.SERIES && pattern.words.isBlank() -> RecordingSubmitUnavailable.WORDS_REQUIRED
    baseline != null && changedFields(baseline).isEmpty() -> RecordingSubmitUnavailable.UNCHANGED
    else -> null
}

fun AutorecDraft.toCreateRequest(): AutorecRuleCreate {
    val (start, end) = startWindow.bounds()
    return AutorecRuleCreate(
        title = pattern.stored(kind),
        channel = channel,
        fullText = fullText,
        mergeText = mergeText,
        duplicateDetection = duplicateDetection,
        maximumRecordingCount = maximumRecordings,
        broadcastType = broadcastType,
        startExtraMinutes = paddingBefore,
        stopExtraMinutes = paddingAfter,
        seriesLinkUri = seriesLinkUri,
        startMinutesSinceMidnight = start,
        startWindowEndMinutesSinceMidnight = end,
        enabled = enabled,
        removal = removal,
        priority = priority,
        name = name.ifBlank { pattern.words },
        configId = configId,
        daysOfWeekMask = days,
    )
}

fun AutorecDraft.toUpdateRequest(baseline: AutorecDraft): AutorecRuleUpdate? {
    require(kind == baseline.kind && seriesLinkUri == baseline.seriesLinkUri) {
        "Create a new rule to change its kind or series"
    }
    val changed = changedFields(baseline)
    if (changed.isEmpty()) return null
    fun has(field: RecordingDraftField) = field in changed
    require(!has(RecordingDraftField.DUPLICATE_DETECTION) || duplicateDetection != null) {
        "Unset duplicate detection is new-rule only"
    }
    require(!has(RecordingDraftField.PROFILE) || configId != null) { "Choose a recording profile" }
    val (start, end) = startWindow.bounds()
    return AutorecRuleUpdate(
        title = pattern.stored(kind).takeIf { has(RecordingDraftField.WORDS) },
        channel = channel.takeIf { has(RecordingDraftField.CHANNEL) },
        daysOfWeekMask = days.takeIf { has(RecordingDraftField.DAYS) },
        startMinutesSinceMidnight = start.takeIf { has(RecordingDraftField.START_WINDOW) },
        startWindowEndMinutesSinceMidnight = end.takeIf { has(RecordingDraftField.START_WINDOW) },
        fullText = fullText.takeIf { has(RecordingDraftField.SEARCH_IN) },
        mergeText = mergeText.takeIf { has(RecordingDraftField.MERGE_TEXT) },
        enabled = enabled.takeIf { has(RecordingDraftField.ENABLED) },
        name = name.takeIf { has(RecordingDraftField.NAME) },
        removal = (removal ?: DvrRemoval.ServerDefault).takeIf { has(RecordingDraftField.KEEP_UNTIL) },
        duplicateDetection = duplicateDetection.takeIf { has(RecordingDraftField.DUPLICATE_DETECTION) },
        priority = (priority ?: DvrPriority.DEFAULT).takeIf { has(RecordingDraftField.PRIORITY) },
        startExtraMinutes = (paddingBefore ?: Duration.ZERO).takeIf { has(RecordingDraftField.PADDING_BEFORE) },
        stopExtraMinutes = (paddingAfter ?: Duration.ZERO).takeIf { has(RecordingDraftField.PADDING_AFTER) },
        broadcastType = broadcastType.takeIf { has(RecordingDraftField.BROADCAST_TYPE) },
        maximumRecordingCount = maximumRecordings.takeIf { has(RecordingDraftField.MAXIMUM_RECORDINGS) },
        configId = configId.takeIf { has(RecordingDraftField.PROFILE) },
    )
}
