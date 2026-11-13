package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrPriority
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import at.bernhardberger.tvheadend.sdk.core.TimerecRule
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleCreate
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleUpdate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Instant

fun newTimerDraft(
    date: LocalDate,
    start: LocalTime,
    end: LocalTime,
    configurations: List<DvrConfiguration> = emptyList(),
    preferred: DvrConfigId? = null,
): TimerDraft = TimerDraft(date, start, end, configId = chooseDvrConfig(configurations, preferred))

/**
 * Date is only editor context; existing timerecs cannot become one-off recordings.
 * A missing weekday set means every day, while an explicit empty set remains empty.
 */
fun TimerecRule.toDraft(date: LocalDate): TimerDraft = TimerDraft(
    date = date,
    start = startMinutesSinceMidnight ?: LocalTime.MIDNIGHT,
    end = stopMinutesSinceMidnight ?: LocalTime.MIDNIGHT,
    channelId = channelId,
    days = daysOfWeekMask ?: DayOfWeek.entries.toSet(),
    name = name?.takeUnless(String::isBlank) ?: title.orEmpty(),
    removal = removal,
    priority = priority,
    configId = configId,
    enabled = enabled ?: true,
    existingTimer = true,
    titleFollowsName = title == name.orEmpty().replace("%", "%%"),
)

fun TimerDraft.changedFields(baseline: TimerDraft): Set<RecordingDraftField> = buildSet {
    fun changed(field: RecordingDraftField, value: Any?, original: Any?) { if (value != original) add(field) }
    changed(RecordingDraftField.CHANNEL, channelId, baseline.channelId)
    changed(RecordingDraftField.DAYS, days, baseline.days)
    changed(RecordingDraftField.START, start, baseline.start)
    changed(RecordingDraftField.END, end, baseline.end)
    changed(RecordingDraftField.NAME, name, baseline.name)
    changed(RecordingDraftField.KEEP_UNTIL, removal, baseline.removal)
    changed(RecordingDraftField.PRIORITY, priority, baseline.priority)
    changed(RecordingDraftField.PROFILE, configId, baseline.configId)
    changed(RecordingDraftField.ENABLED, enabled, baseline.enabled)
    if (!repeating) {
        changed(RecordingDraftField.DATE, date, baseline.date)
        changed(RecordingDraftField.PADDING_BEFORE, paddingBefore, baseline.paddingBefore)
        changed(RecordingDraftField.PADDING_AFTER, paddingAfter, baseline.paddingAfter)
    }
}

fun TimerDraft.cannotSubmit(baseline: TimerDraft? = null): RecordingSubmitUnavailable? = when {
    channelId == null -> RecordingSubmitUnavailable.CHANNEL_REQUIRED
    existingTimer && days.isEmpty() -> RecordingSubmitUnavailable.REPEAT_REQUIRED
    baseline != null && changedFields(baseline).isEmpty() -> RecordingSubmitUnavailable.UNCHANGED
    start == end && (baseline == null || start != baseline.start || end != baseline.end) ->
        RecordingSubmitUnavailable.EMPTY_SPAN
    else -> null
}

/**
 * The caller passes the zone the viewer's clock times are meant in. Screens pass the
 * device zone and assume it equals the server's; this policy never reads the device zone.
 * An earlier end wraps to the following date. ZonedDateTime.of semantics shift a gap
 * forward and take the earlier offset in an overlap.
 */
fun TimerDraft.toScheduleRequest(channelName: String, serverZone: ZoneId): DvrScheduleRequest {
    require(!repeating && cannotSubmit() == null)
    val endDate = if (end <= start) date.plusDays(1) else date
    val from = date.atTime(start).atZone(serverZone).toInstant()
    val until = endDate.atTime(end).atZone(serverZone).toInstant()
    return DvrScheduleRequest(
        schedule = DvrSchedule.ExplicitTime(
            requireNotNull(channelId),
            Instant.fromEpochSeconds(from.epochSecond),
            Instant.fromEpochSeconds(until.epochSecond),
        ),
        title = name.ifBlank { channelName },
        configId = configId,
        enabled = enabled,
        removal = removal,
        priority = priority,
        startExtraMinutes = paddingBefore,
        stopExtraMinutes = paddingAfter,
    )
}

fun TimerDraft.toCreateRequest(channelName: String): TimerecRuleCreate {
    require(repeating && !existingTimer && cannotSubmit() == null)
    val title = name.ifBlank { channelName }
    return TimerecRuleCreate(
        title = title.replace("%", "%%"),
        name = title,
        channel = RecordingRuleChannel.SpecificChannel(requireNotNull(channelId)),
        startMinutesSinceMidnight = start,
        stopMinutesSinceMidnight = end,
        daysOfWeekMask = days,
        enabled = enabled,
        removal = removal,
        priority = priority,
        configId = configId,
    )
}

fun TimerDraft.toUpdateRequest(baseline: TimerDraft, channelName: String): TimerecRuleUpdate? {
    require(existingTimer && baseline.existingTimer && days.isNotEmpty())
    val changed = changedFields(baseline)
    if (changed.isEmpty()) return null
    fun has(field: RecordingDraftField) = field in changed
    require(channelId != null)
    require(!has(RecordingDraftField.PROFILE) || configId != null) { "Choose a recording profile" }
    val title = name.ifBlank { channelName }
    return TimerecRuleUpdate(
        channel = channelId?.let(RecordingRuleChannel::SpecificChannel).takeIf { has(RecordingDraftField.CHANNEL) },
        startMinutesSinceMidnight = start.takeIf { has(RecordingDraftField.START) },
        stopMinutesSinceMidnight = end.takeIf { has(RecordingDraftField.END) },
        daysOfWeekMask = days.takeIf { has(RecordingDraftField.DAYS) },
        title = title.replace("%", "%%").takeIf { has(RecordingDraftField.NAME) && baseline.titleFollowsName },
        name = title.takeIf { has(RecordingDraftField.NAME) },
        removal = (removal ?: DvrRemoval.ServerDefault).takeIf { has(RecordingDraftField.KEEP_UNTIL) },
        priority = (priority ?: DvrPriority.DEFAULT).takeIf { has(RecordingDraftField.PRIORITY) },
        configId = configId.takeIf { has(RecordingDraftField.PROFILE) },
        enabled = enabled.takeIf { has(RecordingDraftField.ENABLED) },
    )
}
