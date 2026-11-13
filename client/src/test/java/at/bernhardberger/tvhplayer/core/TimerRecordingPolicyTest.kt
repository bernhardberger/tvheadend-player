package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.DvrRetention
import at.bernhardberger.tvheadend.sdk.core.TimerecRule
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleId
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleUpdate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerRecordingPolicyTest {
    @Test fun `timer occurrences use weekdays next day end and DST calendar arithmetic`() {
        val zone = ZoneId.of("Europe/Berlin")
        val draft = TimerDraft(LocalDate.of(2026, 3, 29), LocalTime.of(1, 30), LocalTime.of(3, 30))
        val now = Instant.parse("2026-03-28T00:00:00Z")
        val oneOff = draft.nextOccurrences(now, zone, 3)
        assertEquals(1, oneOff.size)
        assertEquals(60.minutes, oneOff.single().stop - oneOff.single().start)
        val repeated = draft.copy(days = setOf(DayOfWeek.SUNDAY)).nextOccurrences(now, zone, 2)
        assertEquals(2, repeated.size)
        assertEquals(120.minutes, repeated.last().stop - repeated.last().start)
        val midnightDraft = draft.copy(start = LocalTime.of(23, 0), end = LocalTime.of(1, 0))
        val midnight = midnightDraft.nextOccurrences(now, zone, 1).single()
        assertEquals(120.minutes, midnight.stop - midnight.start)
        assertTrue(draft.nextOccurrences(Instant.parse("2026-03-30T00:00:00Z"), zone, 1).isEmpty())
        val equal = draft.copy(existingTimer = true, days = setOf(DayOfWeek.SUNDAY), end = draft.start)
        assertTrue(equal.nextOccurrences(now, zone, 2).isEmpty())
        val hours = midnightDraft.timerHourChoices(ending = true)
        assertEquals(24, hours.distinct().size)
        assertEquals(listOf(23, 0, 1), hours.take(3))
        assertTrue(midnightDraft.timerMinuteChoices(23, ending = true).all { it > midnightDraft.start })
        assertTrue(midnightDraft.copy(start = LocalTime.of(23, 55)).timerMinuteChoices(23, ending = true).isEmpty())
    }

    @Test fun `one off crosses midnight and inherits profile defaults`() {
        val draft = TimerDraft(
            LocalDate.of(2026, 11, 12),
            LocalTime.of(23, 0),
            LocalTime.of(1, 0),
        ).copy(channelId = ChannelId(1), paddingBefore = 5.minutes)
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-11-12T23:30:00Z")
        val request = draft.toScheduleRequest("Channel", zone, now)
        val schedule = request.schedule as DvrSchedule.ExplicitTime
        assertEquals(120.minutes, schedule.stop - schedule.start)
        assertEquals("Channel", request.title)
        assertEquals("", draft.name)
        assertEquals(5.minutes, request.startExtraMinutes)
        assertNull(request.removal)
        assertNull(request.retention)
        for (removal in listOf(DvrRemoval.Forever, DvrRemoval.Days(30), DvrRemoval.UntilSpaceNeeded)) {
            val keep = draft.copy(removal = removal).toScheduleRequest("Channel", zone, now)
            assertEquals(removal, keep.removal)
            assertEquals(if (removal == DvrRemoval.Forever) DvrRetention.UntilFilesRemoved else null, keep.retention)
        }
        assertNull(request.priority)
        assertNull(draft.cannotSubmit(zone, now))
        assertEquals(RecordingSubmitUnavailable.TIME_PASSED, draft.cannotSubmit(zone, schedule.stop))
        assertEquals(RecordingSubmitUnavailable.TIME_PASSED, draft.cannotSubmit(zone, schedule.stop + 1.minutes))
        val rounded = newTimerDraft(Instant.parse("2026-11-12T23:58:00Z"), zone)
        assertEquals(LocalDate.of(2026, 11, 13), rounded.date)
        assertEquals(LocalTime.MIDNIGHT, rounded.start)
        assertEquals(LocalTime.of(1, 0), rounded.end)
        assertNull(rounded.channelId)
        val repeating = newTimerDraft(Instant.parse("2026-11-12T12:00:00.001Z"), zone, startingRepeating = true)
        assertEquals(LocalTime.of(12, 5), repeating.start)
        assertEquals(DayOfWeek.entries.toSet(), repeating.days)
    }

    @Test fun `repeating ignores date and padding`() {
        val draft = TimerDraft(
            LocalDate.of(2026, 11, 12),
            LocalTime.of(23, 0),
            LocalTime.of(1, 0),
        ).copy(channelId = ChannelId(1), paddingBefore = 5.minutes)
        val repeat = draft.copy(days = setOf(DayOfWeek.MONDAY))
        val rule = repeat.toCreateRequest("Channel")
        assertEquals(LocalTime.of(23, 0), rule.startMinutesSinceMidnight)
        assertEquals(LocalTime.of(1, 0), rule.stopMinutesSinceMidnight)
        assertEquals(setOf(DayOfWeek.MONDAY), rule.daysOfWeekMask)
        assertNull(rule.retention)
        for (removal in listOf(DvrRemoval.Forever, DvrRemoval.Days(30), DvrRemoval.UntilSpaceNeeded)) {
            val keep = repeat.copy(removal = removal).toCreateRequest("Channel")
            assertEquals(removal, keep.removal)
            assertEquals(if (removal == DvrRemoval.Forever) DvrRetention.UntilFilesRemoved else null, keep.retention)
        }
        assertEquals(rule, repeat.copy(date = draft.date.plusDays(1), paddingBefore = null).toCreateRequest("Channel"))
        assertTrue(repeat.copy(date = draft.date.plusDays(1), paddingBefore = null).changedFields(repeat).isEmpty())
    }

    @Test fun `stored missing days and enabled mean every day and on`() {
        val draft = TimerecRule.create(TimerecRuleId("default")).toDraft(LocalDate.of(2026, 11, 12))
        assertEquals(DayOfWeek.entries.toSet(), draft.days)
        assertEquals(true, draft.enabled)
    }

    @Test fun `timer names escape strftime only for repeating titles`() {
        val draft = TimerDraft(
            LocalDate.of(2026, 11, 12),
            LocalTime.NOON,
            LocalTime.of(13, 0),
        ).copy(channelId = ChannelId(1), name = "100% sport")
        assertEquals("100% sport",
            draft.toScheduleRequest("Channel", ZoneId.of("UTC"), Instant.parse("2026-11-12T00:00:00Z")).title)
        val request = draft.copy(days = setOf(DayOfWeek.MONDAY)).toCreateRequest("Channel")
        assertEquals("100% sport", request.name)
        assertEquals("100%% sport", request.title)
        val stored = TimerecRule.create(
            TimerecRuleId("literal"),
            channelId = draft.channelId,
            daysOfWeekMask = draft.days + DayOfWeek.MONDAY,
            name = request.name,
            title = request.title,
        ).toDraft(draft.date)
        val update = stored.copy(name = "20% sport").toUpdateRequest(stored, "Channel")
        assertEquals("20% sport", update?.name)
        assertEquals("20%% sport", update?.title)
    }

    @Test fun `renaming a web UI timer preserves its strftime format`() {
        val stored = TimerecRule.create(
            TimerecRuleId("format"),
            channelId = ChannelId(1),
            daysOfWeekMask = setOf(DayOfWeek.MONDAY),
            name = "Evening",
            title = "Time-%F_%R",
        ).toDraft(LocalDate.of(2026, 11, 12))
        val update = stored.copy(name = "Late evening").toUpdateRequest(stored, "Channel")
        assertEquals("Late evening", update?.name)
        assertNull(update?.title)
        val blankName = TimerecRule.create(TimerecRuleId("blank"), name = " ", title = "Time-%F_%R")
        assertEquals("Time-%F_%R", blankName.toDraft(stored.date).name)
    }

    @Test fun `existing timer edits are sparse and cannot become one off`() {
        val rule = TimerecRule.create(
            TimerecRuleId("timer"),
            channelId = ChannelId(1),
            title = "title",
            name = "name",
            daysOfWeekMask = setOf(DayOfWeek.TUESDAY),
            startMinutesSinceMidnight = LocalTime.NOON,
            stopMinutesSinceMidnight = LocalTime.of(13, 0),
            removal = DvrRemoval.Forever,
            retention = DvrRetention.Days(30),
        )
        val baseline = rule.toDraft(LocalDate.of(2026, 11, 12))
        assertEquals(DvrRemoval.Days(30), baseline.removal)
        assertNull(baseline.toUpdateRequest(baseline, "Channel"))
        val forever = baseline.copy(removal = DvrRemoval.Forever)
        assertEquals(setOf(RecordingDraftField.KEEP_UNTIL), forever.changedFields(baseline))
        assertEquals(TimerecRuleUpdate(removal = DvrRemoval.Forever, retention = DvrRetention.UntilFilesRemoved),
            forever.toUpdateRequest(baseline, "Channel"))
        for (removal in listOf(DvrRemoval.Days(7), DvrRemoval.UntilSpaceNeeded)) {
            assertEquals(TimerecRuleUpdate(removal = removal),
                baseline.copy(removal = removal).toUpdateRequest(baseline, "Channel"))
        }
        val changed = baseline.copy(enabled = false)
        assertEquals(setOf(RecordingDraftField.ENABLED), changed.changedFields(baseline))
        assertEquals(TimerecRuleUpdate(enabled = false), changed.toUpdateRequest(baseline, "Channel"))
        assertEquals(RecordingSubmitUnavailable.REPEAT_REQUIRED,
            baseline.copy(days = emptySet()).cannotSubmit(
                ZoneId.of("UTC"), Instant.parse("2026-11-12T00:00:00Z"), baseline))
        assertEquals(RecordingSubmitUnavailable.CHANNEL_REQUIRED,
            TimerDraft(baseline.date, LocalTime.NOON, LocalTime.of(13, 0))
                .cannotSubmit(ZoneId.of("UTC"), Instant.parse("2026-11-12T00:00:00Z")))
        for (days in listOf(emptySet(), setOf(DayOfWeek.MONDAY))) {
            val incomplete = TimerecRule.create(TimerecRuleId("incomplete"), daysOfWeekMask = days)
                .toDraft(baseline.date)
            assertEquals(TimerecRuleUpdate(enabled = false),
                incomplete.copy(enabled = false).toUpdateRequest(incomplete, ""))
            assertEquals(RecordingSubmitUnavailable.CHANNEL_REQUIRED,
                incomplete.cannotSubmit(ZoneId.of("UTC"), Instant.parse("2026-11-12T00:00:00Z")))
        }
    }

    @Test fun `empty spans block new times but preserve equal stored times and DST gap shifts forward`() {
        val zone = ZoneId.of("Europe/Berlin")
        val beforeGap = Instant.parse("2026-03-28T00:00:00Z")
        val equal = TimerDraft(LocalDate.of(2026, 3, 29), LocalTime.NOON, LocalTime.NOON)
            .copy(channelId = ChannelId(1))
        assertEquals(RecordingSubmitUnavailable.EMPTY_SPAN, equal.cannotSubmit(zone, beforeGap))
        val stored = equal.copy(existingTimer = true, days = setOf(DayOfWeek.MONDAY))
        assertEquals(RecordingSubmitUnavailable.UNCHANGED, stored.cannotSubmit(zone, beforeGap, stored))
        assertNull(stored.copy(name = "Changed").cannotSubmit(zone, beforeGap, stored))
        val gap = equal.copy(start = LocalTime.of(2, 30), end = LocalTime.of(4, 0))
            .toScheduleRequest("Channel", zone, beforeGap).schedule as DvrSchedule.ExplicitTime
        assertEquals(30.minutes, gap.stop - gap.start)
        val reversed = equal.copy(start = LocalTime.of(2, 30), end = LocalTime.of(3, 0))
        assertEquals(RecordingSubmitUnavailable.EMPTY_SPAN, reversed.cannotSubmit(zone, beforeGap))
        assertTrue(reversed.nextOccurrences(beforeGap, zone, 1).isEmpty())
        val repeating = reversed.copy(days = setOf(DayOfWeek.SUNDAY))
            .nextOccurrences(beforeGap, zone, 1).single()
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), repeating.start)
        assertEquals(30.minutes, repeating.stop - repeating.start)
        val overlap = reversed.copy(date = LocalDate.of(2026, 10, 25))
        assertNull(overlap.cannotSubmit(zone, beforeGap))
        val occurrence = overlap.nextOccurrences(Instant.parse("2026-10-24T00:00:00Z"),
            zone, 1).single()
        assertEquals(90.minutes, occurrence.stop - occurrence.start)
        assertEquals(occurrence.start,
            (overlap.toScheduleRequest("Channel", zone, beforeGap).schedule as DvrSchedule.ExplicitTime).start)
    }
}
