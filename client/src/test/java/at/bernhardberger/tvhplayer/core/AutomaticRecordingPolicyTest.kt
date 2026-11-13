package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.AutorecRule
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleUpdate
import at.bernhardberger.tvheadend.sdk.core.AutorecTime
import at.bernhardberger.tvheadend.sdk.core.BroadcastType
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DuplicateDetection
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.DvrRetention
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticRecordingPolicyTest {
    @Test fun `preview requests respect kind and bounded series search`() {
        val keyword = newAutorecDraft(AutomaticRecordingKind.KEYWORD)
            .copy(pattern = RecordingPattern("A|B"), fullText = true, mergeText = true)
        assertEquals("""A\|B""", keyword.previewSearchRequest()?.query)
        assertEquals(true, keyword.previewSearchRequest()?.fullText)
        assertEquals(true, keyword.previewSearchRequest()?.mergeText)
        assertEquals("^A\\|B$", keyword.copy(kind = AutomaticRecordingKind.TITLE).previewSearchRequest()?.query)
        val series = keyword.copy(kind = AutomaticRecordingKind.SERIES, seriesLinkUri = "series")
        assertEquals("""A\|B""", series.previewSearchRequest()?.query)
        assertEquals(false, series.previewSearchRequest()?.fullText)
        assertEquals(false, series.previewSearchRequest()?.mergeText)
        val channelSeries = series.copy(channel = RecordingRuleChannel.SpecificChannel(ChannelId(3)))
        assertEquals("", channelSeries.previewSearchRequest()?.query)
        assertEquals(ChannelId(3), channelSeries.previewSearchRequest()?.channelId)
        assertEquals(true, channelSeries.previewSearchRequest()?.mergeText)
        assertNull(series.copy(pattern = RecordingPattern("")).previewSearchRequest())
        assertNull(keyword.copy(pattern = RecordingPattern(" ")).previewSearchRequest())
        assertNull(keyword.copy(enabled = false).previewSearchRequest())
    }

    @Test fun `preview filters local weekday inclusive minute windows series and supported broadcast type`() {
        val now = Instant.parse("2026-11-12T00:00:00Z")
        fun event(start: Instant = Instant.parse("2026-11-12T23:15:30Z"), isNew: Boolean? = true) =
            EpgEvent.create(EventId(1), ChannelId(3), start, Instant.parse("2026-11-13T00:15:30Z"),
                title = "Episode", seriesLinkUri = "series", isNew = isNew)
        val event = event()
        val draft = newAutorecDraft(AutomaticRecordingKind.KEYWORD).copy(
            pattern = RecordingPattern("Episode"), days = setOf(DayOfWeek.FRIDAY),
            startWindow = RecordingStartWindow.Stored(
                AutorecTime.At(LocalTime.of(23, 0)), AutorecTime.At(LocalTime.of(0, 15))),
        )
        val zone = ZoneId.of("Europe/Berlin")
        fun matches(value: AutorecDraft, candidate: EpgEvent = event) =
            value.previewMatches(listOf(candidate), emptyList(), null, now, zone).rows
        assertEquals(listOf(event.id), matches(draft).map { it.eventId })
        assertTrue(matches(draft.copy(days = setOf(DayOfWeek.THURSDAY))).isEmpty())
        assertTrue(matches(draft.copy(days = emptySet())).isEmpty())
        assertTrue(matches(draft.copy(startWindow = RecordingStartWindow.Before(LocalTime.MIDNIGHT))).isEmpty())
        assertTrue(matches(draft.copy(channel = RecordingRuleChannel.SpecificChannel(ChannelId(4)))).isEmpty())
        assertTrue(matches(draft.copy(kind = AutomaticRecordingKind.SERIES, seriesLinkUri = "other")).isEmpty())
        assertTrue(matches(draft.copy(enabled = false)).isEmpty())
        assertTrue(matches(draft, event(start = now)).isEmpty())
        assertEquals(1, matches(draft.copy(broadcastType = BroadcastType.NEW)).size)
        assertTrue(matches(draft.copy(broadcastType = BroadcastType.NEW), event(isNew = null)).isEmpty())
        for (type in listOf(BroadcastType.ALL, BroadcastType.NEW, BroadcastType.NEW_OR_UNKNOWN,
            BroadcastType.REPEAT, BroadcastType.UNKNOWN)) {
            val result = draft.copy(broadcastType = type).previewMatches(
                listOf(event), emptyList(), null, now, zone)
            assertEquals(type !in setOf(BroadcastType.ALL, BroadcastType.NEW), result.broadcastFilterUnavailable)
        }
    }

    @Test fun `preview merges every own upcoming entry and never predicts duplicates`() {
        val now = Instant.fromEpochSeconds(100)
        val ruleId = AutorecRuleId("own")
        val events = (1..5).map { id ->
            EpgEvent.create(EventId(id.toLong()), ChannelId(1), Instant.fromEpochSeconds(200L + id),
                Instant.fromEpochSeconds(300L + id), title = "Episode $id")
        }
        fun entry(id: Int, duplicate: Boolean = false, enabled: Boolean = true, rule: AutorecRuleId = ruleId) =
            DvrEntry.create(DvrEntryId(id.toLong()), eventId = EventId(id.toLong()), autorecRuleId = rule,
                start = Instant.fromEpochSeconds(200L + id), stop = Instant.fromEpochSeconds(300L + id),
                title = "Entry $id", duplicate = duplicate, enabled = enabled)
        val entries = listOf(entry(1), entry(2, duplicate = true), entry(3, enabled = false),
            entry(4, rule = AutorecRuleId("foreign")), entry(6))
        val draft = newAutorecDraft(AutomaticRecordingKind.KEYWORD)
            .copy(pattern = RecordingPattern("Episode"), excludedEventIds = setOf(EventId(5)))
        val result = draft.previewMatches(events, entries, ruleId, now, ZoneId.of("UTC"), baseline = draft)
        assertEquals(listOf(AutorecEpisodeState.SCHEDULED, AutorecEpisodeState.DUPLICATE,
            AutorecEpisodeState.SKIPPED, AutorecEpisodeState.SCHEDULED), result.rows.map { it.state })
        assertEquals("Entry 1", result.rows.first().title)
        assertEquals(4, result.matchCount)
        assertEquals(2, result.willRecordCount)
        val keepOnly = draft.copy(removal = DvrRemoval.Forever)
        assertNull(keepOnly.previewSearchRequest(draft))
        assertEquals(result, keepOnly.previewMatches(events, entries, ruleId, now, ZoneId.of("UTC"), draft))
        assertNull(draft.previewSearchRequest(draft))
        val eventless = DvrEntry.create(DvrEntryId(7), autorecRuleId = ruleId,
            state = DvrEntryState.SCHEDULED, start = Instant.fromEpochSeconds(207))
        assertEquals(AutorecEpisodeState.REMOVED, keepOnly.previewMatches(emptyList(), listOf(eventless),
            ruleId, now, ZoneId.of("UTC"), draft).rows.single().state)
        val fresh = draft.previewMatches(events, entries, null, now, ZoneId.of("UTC"))
        assertEquals(4, fresh.willRecordCount)
        assertTrue(fresh.rows.none { it.state == AutorecEpisodeState.DUPLICATE })
        val noWords = draft.copy(kind = AutomaticRecordingKind.SERIES,
            pattern = RecordingPattern(""), seriesLinkUri = "series")
        assertEquals(4, noWords.previewMatches(emptyList(), entries, ruleId, now, ZoneId.of("UTC"), noWords).matchCount)
    }

    @Test fun `changed existing match criteria preview removed entries and newly scheduled matches`() {
        val now = Instant.parse("2026-11-12T23:00:00Z")
        val start = Instant.parse("2026-11-12T23:15:30Z")
        val stop = Instant.parse("2026-11-13T00:15:30Z")
        val ruleId = AutorecRuleId("own")
        val zone = ZoneId.of("Europe/Berlin")
        val draft = newAutorecDraft(AutomaticRecordingKind.KEYWORD).copy(
            pattern = RecordingPattern("Episode"), channel = RecordingRuleChannel.SpecificChannel(ChannelId(1)),
        )
        val entry = DvrEntry.create(DvrEntryId(1), eventId = EventId(1), autorecRuleId = ruleId,
            start = start, stop = stop, channelId = ChannelId(1))
        val event = EpgEvent.create(EventId(2), ChannelId(2), start, stop, title = "Episode")
        val newChannel = draft.copy(channel = RecordingRuleChannel.SpecificChannel(ChannelId(2)))
        val result = newChannel.previewMatches(listOf(event), listOf(entry), ruleId, now, zone, draft)
        assertEquals(mapOf(EventId(2) to AutorecEpisodeState.MATCH, EventId(1) to AutorecEpisodeState.REMOVED),
            result.rows.associate { it.eventId to it.state })
        assertEquals(1, result.willRecordCount)
        assertEquals(ChannelId(2), newChannel.previewSearchRequest(draft)?.channelId)
        val off = draft.copy(enabled = false)
        assertNull(off.previewSearchRequest(draft))
        val disabled = off.previewMatches(listOf(event), listOf(entry), ruleId, now, zone, draft)
        assertEquals(listOf(AutorecEpisodeState.REMOVED), disabled.rows.map { it.state })
        assertEquals(0, disabled.willRecordCount)
        for (changed in listOf(draft.copy(days = setOf(DayOfWeek.THURSDAY)),
            draft.copy(startWindow = RecordingStartWindow.Before(LocalTime.MIDNIGHT)))) {
            assertEquals(AutorecEpisodeState.REMOVED,
                changed.previewMatches(emptyList(), listOf(entry), ruleId, now, zone, draft).rows.single().state)
        }
        for (isNew in listOf(false, null)) {
            val known = EpgEvent.create(EventId(1), ChannelId(1), start, stop, isNew = isNew)
            val unknown = DvrEntry.create(DvrEntryId(4), eventId = EventId(4), autorecRuleId = ruleId,
                state = DvrEntryState.SCHEDULED, start = start, stop = stop, channelId = ChannelId(1))
            val newOnly = draft.copy(broadcastType = BroadcastType.NEW).previewMatches(
                listOf(known), listOf(entry, unknown), ruleId, now, zone, draft)
            assertEquals(mapOf(entry.id to AutorecEpisodeState.REMOVED,
                unknown.id to AutorecEpisodeState.SCHEDULED), newOnly.rows.associate { it.recordingId to it.state })
            assertEquals(1, newOnly.willRecordCount)
        }
        val renamed = draft.copy(pattern = RecordingPattern("Other"))
        val noEventId = DvrEntry.create(DvrEntryId(3), autorecRuleId = ruleId, state = DvrEntryState.SCHEDULED,
            start = start, stop = stop, channelId = ChannelId(1))
        val textResult = renamed.previewMatches(emptyList(), listOf(entry, noEventId), ruleId, now, zone, draft)
        assertEquals(mapOf(DvrEntryId(1) to AutorecEpisodeState.REMOVED,
            DvrEntryId(3) to AutorecEpisodeState.REMOVED), textResult.rows.associate { it.recordingId to it.state })
        assertEquals(0, textResult.willRecordCount)
    }

    @Test fun `literal metacharacters round trip for title and keyword`() {
        val words = """A\^$.|?*+()[]{}"""
        for (kind in listOf(AutomaticRecordingKind.TITLE, AutomaticRecordingKind.KEYWORD)) {
            val draft = newAutorecDraft(kind).copy(pattern = RecordingPattern(words))
            val stored = draft.toCreateRequest().title
            assertEquals(RecordingPattern(words), readRecordingPattern(stored))
            assertEquals(kind, autorecKind(AutorecRule.create(AutorecRuleId("rule"), title = stored)))
        }
        assertEquals(RecordingPattern("C.S.I."), readRecordingPattern("""C\.S\.I\."""))
    }

    @Test fun `ambiguous web expressions stay raw after editing`() {
        for (raw in listOf("A|B", "^A.*B$", "price$", """A\q""", "a{2}")) {
            val pattern = readRecordingPattern(raw)
            assertTrue(pattern.rawExpression)
            val draft = newAutorecDraft(AutomaticRecordingKind.KEYWORD).copy(pattern = pattern)
            assertEquals(raw, draft.toCreateRequest().title)
            assertEquals("C|D", draft.copy(pattern = pattern.copy(words = "C|D")).toCreateRequest().title)
        }
    }

    @Test fun `new defaults omit profile owned values and stored values are only sent when changed`() {
        val draft = newAutorecDraft(AutomaticRecordingKind.KEYWORD).copy(pattern = RecordingPattern("news"))
        val request = draft.toCreateRequest()
        assertEquals(DuplicateDetection.DVR_PROFILE, request.duplicateDetection)
        assertNull(request.removal)
        assertNull(request.retention)
        for (removal in listOf(DvrRemoval.Forever, DvrRemoval.Days(30), DvrRemoval.UntilSpaceNeeded)) {
            val keep = draft.copy(removal = removal).toCreateRequest()
            assertEquals(removal, keep.removal)
            assertEquals(if (removal == DvrRemoval.Forever) DvrRetention.UntilFilesRemoved else null, keep.retention)
        }
        assertNull(request.priority)
        assertNull(request.startExtraMinutes)
        assertNull(request.stopExtraMinutes)
        assertEquals("news", request.name)
        assertEquals("", draft.name)
        assertEquals(RecordingRuleChannel.AllChannels, request.channel)
        assertEquals(DayOfWeek.entries.toSet(), request.daysOfWeekMask)
        val rule = AutorecRule.create(
            AutorecRuleId("rule"),
            title = "news",
            duplicateDetection = DuplicateDetection.DVR_PROFILE,
            mergeText = true,
            startMinutesSinceMidnight = AutorecTime.At(LocalTime.of(23, 0)),
            startWindowEndMinutesSinceMidnight = AutorecTime.At(LocalTime.of(2, 0)),
        )
        val baseline = rule.toDraft()
        assertNull(baseline.toUpdateRequest(baseline))
        val changed = baseline.copy(fullText = true)
        assertEquals(setOf(RecordingDraftField.SEARCH_IN), changed.changedFields(baseline))
        assertEquals(AutorecRuleUpdate(fullText = true), changed.toUpdateRequest(baseline))
        assertEquals(RecordingSubmitUnavailable.UNCHANGED, baseline.cannotSubmit(baseline))
        assertEquals(RecordingSubmitUnavailable.WORDS_REQUIRED,
            newAutorecDraft(AutomaticRecordingKind.TITLE).cannotSubmit())
        val recordAll = baseline.copy(duplicateDetection = DuplicateDetection.ALL)
        assertEquals(AutorecRuleUpdate(duplicateDetection = DuplicateDetection.DVR_PROFILE),
            baseline.toUpdateRequest(recordAll))
    }

    @Test fun `missing stored fields use server defaults without changing the edit`() {
        val baseline = AutorecRule.create(AutorecRuleId("default"), title = "news").toDraft()
        assertEquals(true, baseline.enabled)
        assertEquals(false, baseline.fullText)
        assertEquals(false, baseline.mergeText)
        assertEquals(BroadcastType.ALL, baseline.broadcastType)
        assertEquals(0L, baseline.maximumRecordings)
        assertEquals(DayOfWeek.entries.toSet(), baseline.days)
        assertNull(baseline.toUpdateRequest(baseline))
        assertEquals(
            AutorecRuleUpdate(enabled = false),
            baseline.copy(enabled = false).toUpdateRequest(baseline),
        )
        val noDay = AutorecRule.create(AutorecRuleId("no-day"), title = "news", daysOfWeekMask = emptySet()).toDraft()
        assertTrue(noDay.days.isEmpty())
        assertNull(noDay.toUpdateRequest(noDay))
        val finiteLog = AutorecRule.create(AutorecRuleId("finite-log"), title = "news",
            removal = DvrRemoval.Forever, retention = DvrRetention.Days(30)).toDraft()
        assertEquals(DvrRemoval.Days(30), finiteLog.removal)
        assertNull(finiteLog.toUpdateRequest(finiteLog))
        assertEquals(AutorecRuleUpdate(enabled = false), finiteLog.copy(enabled = false).toUpdateRequest(finiteLog))
        val forever = finiteLog.copy(removal = DvrRemoval.Forever)
        assertEquals(setOf(RecordingDraftField.KEEP_UNTIL), forever.changedFields(finiteLog))
        assertEquals(AutorecRuleUpdate(removal = DvrRemoval.Forever, retention = DvrRetention.UntilFilesRemoved),
            forever.toUpdateRequest(finiteLog))
        for (removal in listOf(DvrRemoval.Days(7), DvrRemoval.UntilSpaceNeeded)) {
            assertEquals(AutorecRuleUpdate(removal = removal),
                finiteLog.copy(removal = removal).toUpdateRequest(finiteLog))
        }
    }

    @Test fun `start windows use inclusive server day edges and preserve other shapes`() {
        for ((window, start, end) in listOf(
            Triple(RecordingStartWindow.AnyTime, AutorecTime.AnyTime, AutorecTime.AnyTime),
            Triple(
                RecordingStartWindow.After(LocalTime.of(9, 5)),
                AutorecTime.At(LocalTime.of(9, 5)),
                AutorecTime.At(LocalTime.of(23, 59)),
            ),
            Triple(
                RecordingStartWindow.Before(LocalTime.of(18, 0)),
                AutorecTime.At(LocalTime.MIDNIGHT),
                AutorecTime.At(LocalTime.of(18, 0)),
            ),
        )) {
            val request = newAutorecDraft(AutomaticRecordingKind.KEYWORD).copy(startWindow = window).toCreateRequest()
            assertEquals(start, request.startMinutesSinceMidnight)
            assertEquals(end, request.startWindowEndMinutesSinceMidnight)
        }
        val series = AutorecRule.create(AutorecRuleId("series"), title = "^A.*$", seriesLinkUri = "series-id")
        assertEquals(AutomaticRecordingKind.SERIES, autorecKind(series))
        assertTrue(series.toDraft().pattern.rawExpression)
        for ((start, end) in listOf(
            AutorecTime.AnyTime to AutorecTime.At(LocalTime.NOON),
            AutorecTime.At(LocalTime.NOON) to AutorecTime.AnyTime,
            null to AutorecTime.At(LocalTime.NOON),
            AutorecTime.At(LocalTime.NOON) to null,
        )) {
            val stored = AutorecRule.create(
                AutorecRuleId("any"),
                startMinutesSinceMidnight = start,
                startWindowEndMinutesSinceMidnight = end,
            )
            assertEquals(RecordingStartWindow.AnyTime, stored.toDraft().startWindow)
        }
    }

    @Test fun `programme series keeps its title channel and URI but excluded episodes stay draft only`() {
        val event = EpgEvent.create(
            EventId(1),
            channelId = ChannelId(2),
            start = kotlin.time.Instant.fromEpochSeconds(100),
            stop = kotlin.time.Instant.fromEpochSeconds(200),
            title = "A|B",
            seriesLinkUri = "series-id",
        )
        val draft = newAutorecDraft(AutomaticRecordingKind.SERIES, programme = event)
        val excluded = draft.copy(excludedEventIds = setOf(event.id))
        assertEquals(draft.toCreateRequest(), excluded.toCreateRequest())
        assertTrue(excluded.changedFields(draft).isEmpty())
        assertEquals("series-id", draft.toCreateRequest().seriesLinkUri)
        assertEquals("""A\|B""", draft.toCreateRequest().title)
        assertEquals("A|B", draft.toCreateRequest().name)
        assertEquals(RecordingRuleChannel.SpecificChannel(ChannelId(2)), draft.toCreateRequest().channel)
        val entry = DvrEntry.create(DvrEntryId(1), eventId = event.id, autorecRuleId = AutorecRuleId("own"),
            channelId = event.channelId, start = event.start, stop = event.stop)
        for (edited in listOf(draft.copy(pattern = RecordingPattern("Other")),
            draft.copy(fullText = true), draft.copy(mergeText = true))) {
            assertNull(edited.previewSearchRequest(draft))
            assertEquals(AutorecEpisodeState.SCHEDULED, edited.previewMatches(emptyList(), listOf(entry),
                AutorecRuleId("own"), Instant.fromEpochSeconds(0), ZoneId.of("UTC"), draft).rows.single().state)
        }
    }

    @Test fun `raw expression supplies a blank rule name only at creation`() {
        val draft = newAutorecDraft(AutomaticRecordingKind.KEYWORD).copy(pattern = readRecordingPattern("A|B"))
        assertEquals("A|B", draft.toCreateRequest().name)
        assertEquals("", draft.name)
    }
}
