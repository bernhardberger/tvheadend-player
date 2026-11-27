@file:OptIn(at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi::class,
    at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)

package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingEndPolicyTest {
    private val observation = SessionObservation.create(recordingProgressCapability = RecordingProgressCapability.SUPPORTED)
    private val ended = recording(1, 1_000, 2_000)

    @Test
    fun overlappingPartsContinueFromTheSameBroadcastTimeAndEarlierPartsComeFirst() {
        val later = recording(2, 1_960, 2_500, files = listOf(
            DvrRecordingFile.create(start = at(1_850), stop = at(1_919)),
            DvrRecordingFile.create(start = at(1_920), stop = at(2_500)),
        ))
        val earlier = recording(3, 1_900, 2_500, fileStart = 1_880, resume = 15.seconds)
        val subsequent = recording(4, 2_120, 3_000, files = listOf(
            DvrRecordingFile.create(start = at(1_990), stop = at(2_110)),
            DvrRecordingFile.create(start = at(2_120), stop = at(3_000)),
        ))
        val result = offers(later, subsequent, earlier)
        assertEquals(
            listOf(
                chain(earlier, RecordingEndStart.AtTime(at(2_000))),
                chain(later, RecordingEndStart.AtTime(at(2_000))),
                chain(subsequent, RecordingEndStart.AtTime(at(2_000))),
            ),
            result,
        )
        assertEquals(listOf(at(2_000), at(2_000), null),
            result.filterIsInstance<RecordingEndOffer.Recording>().map { it.displayContinuationTime })
    }

    @Test
    fun chainGapUsesTheLastFileEndEvenWhenTheViewerIsStillWatching() {
        val finished = recording(1, 1_000, 2_000, stopExtra = 1.minutes, files = listOf(
            DvrRecordingFile.create(start = at(1_000), stop = at(1_500)),
            DvrRecordingFile.create(start = at(1_600), stop = at(2_300)),
        ))
        val overlapping = recording(4, 1_800, 2_500)
        val next = recording(2, 2_420, 3_000)
        val tooLate = recording(3, 2_421, 3_000)
        val result = offers(tooLate, next, overlapping, finished = finished, reached = at(1_850))
        assertEquals(listOf(at(1_850), null),
            result.filterIsInstance<RecordingEndOffer.Recording>().map { it.displayContinuationTime })
        assertEquals(listOf(
            chain(overlapping, RecordingEndStart.AtTime(at(1_850))),
            chain(next, RecordingEndStart.AtTime(at(1_850))),
        ), result)
    }

    @Test
    fun runningEndedPartUsesTheReachedTimeWhenTheLastFileStopIsMissingOrStale() {
        val next = recording(2, 2_120, 3_000)
        val tooLate = recording(3, 2_121, 3_000)
        for (stop in listOf(null, 1_900L, 2_010L)) {
            val running = recording(1, 1_000, 2_600, state = DvrEntryState.RECORDING, fileStop = stop)
            val expected = if (stop == 2_010L) listOf(next, tooLate) else listOf(next)
            assertEquals(expected.map { chain(it, RecordingEndStart.AtTime(at(2_000))) },
                offers(tooLate, next, finished = running, now = at(2_050)))
        }
    }

    @Test
    fun missingEndedStopAllowsProvenOverlapButNotAGapOrWatchLive() {
        val finished = recording(1, 1_000, null)
        val overlap = recording(2, 1_900, 2_800)
        val later = recording(3, 2_010, 2_800)
        val exhausted = recording(4, 1_950, 2_000)
        val startsAtReachedTime = recording(5, 2_000, 2_800)
        assertEquals(listOf(chain(overlap, RecordingEndStart.AtTime(at(2_000)))),
            offers(later, exhausted, overlap, startsAtReachedTime, finished = finished, now = at(2_050)))
        assertEquals(emptyList<RecordingEndOffer>(), offers(later, finished = finished, now = at(2_050)))
    }

    @Test
    fun chainExcludesDuplicateBroadcastsAndTargetsWithoutPlayableContinuation() {
        val valid = recording(10, 2_100, 3_000)
        val event = EpgEvent.create(requireNotNull(ended.eventId), ended.channelId, at(1_000), at(2_000))
        val sameEvent = recording(2, 1_900, 3_000, event = ended.eventId)
        assertEquals(listOf(chain(valid, RecordingEndStart.AtTime(at(2_000)))), offers(sameEvent, valid))
        assertEquals(listOf(chain(valid, RecordingEndStart.AtTime(at(2_000)))), offers(
            sameEvent,
            recording(4, 2_000, 3_000, state = DvrEntryState.SCHEDULED),
            recording(6, 2_121, 3_000),
            recording(7, 1_900, 1_980),
            recording(8, 2_000, 3_000, channel = ChannelId(2)),
            recording(9, 1_000, 2_000, event = null, fileStart = 1_005, fileStop = 2_100),
            valid, lookup = mapOf(event.id to event)::get,
        ))
    }

    @Test
    fun onlyTheEarliestChainCandidateForABroadcastIsOffered() {
        val rule = AutorecRuleId("rule")
        val finished = recording(1, 1_000, 2_000, rule = rule)
        val first = recording(3, 1_900, 2_800)
        val duplicate = recording(2, 1_950, 2_850, event = first.eventId, rule = rule)
        val event = EpgEvent.create(requireNotNull(first.eventId), first.channelId, at(1_900), at(2_800))
        assertEquals(listOf(chain(first, RecordingEndStart.AtTime(at(2_000)))),
            offers(duplicate, first, finished = finished, lookup = mapOf(event.id to event)::get))
    }

    @Test
    fun resolvedDistinctAiringsAreNotCollapsedByIdenticalDvrSlots() {
        val rule = AutorecRuleId("rule")
        val finished = recording(1, 1_000, 2_000, rule = rule)
        val first = recording(2, 1_000, 2_000, fileStart = 1_900, fileStop = 2_800)
        val second = recording(3, 1_000, 2_000, fileStart = 1_950, fileStop = 2_900)
        val next = recording(4, 1_000, 2_000, rule = rule, fileStart = 3_000, fileStop = 3_800)
        val airings = listOf(finished, first, second, next).mapIndexed { index, entry ->
            val start = 1_000L + index * 1_000
            EpgEvent.create(requireNotNull(entry.eventId), entry.channelId, at(start), at(start + 1_000))
        }.associateBy { it.id }
        assertEquals(listOf(
            chain(first, RecordingEndStart.AtTime(at(2_000))),
            chain(second, RecordingEndStart.AtTime(at(2_000))),
            RecordingEndOffer.Recording(next, RecordingEndKind.NEXT_EPISODE, RecordingEndStart.FromBeginning),
        ), offers(next, second, first, finished = finished, lookup = airings::get))
    }

    @Test
    fun unknownLastFileStartCanUseThePaddedStartOnlyForASingleFile() {
        val unknownStart = DvrRecordingFile.create(stop = at(2_800))
        val single = recording(2, 2_001, 2_800, startExtra = 1.minutes, files = listOf(unknownStart))
        val multi = recording(3, 1_960, 2_800, files = listOf(
            DvrRecordingFile.create(start = at(1_960), stop = at(1_990)),
            unknownStart,
        ))
        assertEquals(listOf(chain(single, RecordingEndStart.AtTime(at(2_000)))), offers(multi, single))
    }

    @Test
    fun admittedRunningPartWithAFileIsOffered() {
        val growing = recording(2, 1_960, 3_000, state = DvrEntryState.RECORDING, files = listOf(
            TvheadendTestResultFactory.recordingFile(fileId = 1, serverPath = "/rec/part.ts", start = at(1_950)),
        ))
        assertEquals(
            listOf(chain(growing, RecordingEndStart.AtTime(at(2_000)))),
            offers(growing, now = at(2_060)),
        )
    }

    @Test
    fun seriesUsesAiringOrderSkipsWatchedAndKeepsPartlyWatched() {
        val watched = recording(2, 2_500, 3_000, playCount = 1)
        val partlyWatched = recording(3, 5_500, 6_500, playCount = 1, resume = 12.minutes)
        val next = recording(4, 5_000, 6_000)
        val airings = listOf(ended, watched, partlyWatched, next).associate { entry ->
            val start = if (entry == partlyWatched) 3_000L else requireNotNull(entry.start).epochSeconds
            val event = EpgEvent.create(requireNotNull(entry.eventId), entry.channelId, at(start), at(start + 500),
                seriesLinkUri = "series-id")
            event.id to event
        }
        assertEquals(listOf(
            RecordingEndOffer.Recording(partlyWatched, RecordingEndKind.NEXT_EPISODE, RecordingEndStart.Resume(12.minutes)),
            RecordingEndOffer.Recording(next, RecordingEndKind.OTHER_EPISODE, RecordingEndStart.FromBeginning),
        ), offers(next, watched, partlyWatched, lookup = airings::get))
        assertEquals(listOf(
            RecordingEndOffer.Recording(next, RecordingEndKind.NEXT_EPISODE, RecordingEndStart.FromBeginning),
        ), offers(next, watched, lookup = airings::get))
    }

    @Test
    fun ruleFindsNextAndAtMostTwoOthersNewestFirst() {
        val rule = AutorecRuleId("rule")
        val earlier = recording(2, 400, 900, rule = rule)
        val next = recording(3, 3_000, 4_000, rule = rule)
        val middle = recording(4, 4_000, 5_000, rule = rule)
        val newest = recording(5, 5_000, 6_000, rule = rule)
        val unrelated = recording(6, 6_000, 7_000)
        val finished = recording(1, 1_000, 2_000, rule = rule)
        assertEquals(listOf(
            RecordingEndOffer.Recording(next, RecordingEndKind.NEXT_EPISODE, RecordingEndStart.FromBeginning),
            RecordingEndOffer.Recording(newest, RecordingEndKind.OTHER_EPISODE, RecordingEndStart.FromBeginning),
            RecordingEndOffer.Recording(middle, RecordingEndKind.OTHER_EPISODE, RecordingEndStart.FromBeginning),
        ), offers(earlier, newest, next, unrelated, middle, finished = finished))
    }

    @Test
    fun withoutANextEpisodeOnlyTheNewestTwoOtherEpisodesAreOffered() {
        val rule = AutorecRuleId("rule")
        val finished = recording(1, 1_000, 2_000, rule = rule)
        val oldest = recording(2, 200, 400, rule = rule)
        val older = recording(3, 400, 600, rule = rule)
        val recent = recording(4, 600, 800, rule = rule)
        assertEquals(listOf(
            RecordingEndOffer.Recording(recent, RecordingEndKind.OTHER_EPISODE, RecordingEndStart.FromBeginning),
            RecordingEndOffer.Recording(older, RecordingEndKind.OTHER_EPISODE, RecordingEndStart.FromBeginning),
        ), offers(older, oldest, recent, finished = finished))
    }

    @Test
    fun withoutAnEndedChannelOnlyRelatedEpisodesAreOffered() {
        val finished = recording(1, 1_000, 2_000, channel = null)
        val next = recording(2, 2_010, 2_800, channel = null)
        val airings = listOf(finished, next).associate { entry ->
            val event = EpgEvent.create(requireNotNull(entry.eventId), start = requireNotNull(entry.start),
                stop = requireNotNull(entry.stop), seriesLinkUri = "series-id")
            event.id to event
        }
        assertEquals(listOf(
            RecordingEndOffer.Recording(next, RecordingEndKind.NEXT_EPISODE, RecordingEndStart.FromBeginning),
        ), offers(next, finished = finished, lookup = airings::get, now = at(2_050)))
    }

    @Test
    fun liveUsesTheObservedFileEndOrFallsBackToThePaddedEnd() {
        val finished = recording(1, 1_000, 2_000, fileStop = null, stopExtra = 5.minutes)
        val live = RecordingEndOffer.WatchLive(requireNotNull(finished.channelId), null)
        assertEquals(emptyList<RecordingEndOffer>(), offers(finished = finished, now = at(2_299)))
        assertEquals(listOf(live), offers(finished = finished, now = at(2_300)))
        assertEquals(listOf(live), offers(finished = finished, now = at(2_480)))
        assertEquals(emptyList<RecordingEndOffer>(), offers(finished = finished, now = at(2_481)))
        val observed = recording(1, 1_000, 2_000, fileStop = 2_250, stopExtra = 5.minutes)
        assertEquals(listOf(live), offers(finished = observed, now = at(2_250)))
    }

    @Test
    fun stillRecordingDoesNotOfferWatchLiveEvenWithAClosedFile() {
        val running = recording(1, 1_000, 2_000, state = DvrEntryState.RECORDING)
        assertEquals(emptyList<RecordingEndOffer>(), offers(finished = running, now = at(2_050)))
    }

    @Test
    fun liveDisappearsWhenTheLiveEventMatchesAnOfferedRecording() {
        val part = recording(2, 2_000, 2_800)
        val event = EpgEvent.create(requireNotNull(part.eventId), part.channelId, at(1_990), at(2_810))
        val linked = EpgEvent.create(EventId(3), part.channelId, at(1_990), at(2_810), dvrEntryId = part.id)
        val sameSlot = EpgEvent.create(EventId(4), part.channelId, at(2_000), at(2_800))
        val different = EpgEvent.create(EventId(5), part.channelId, at(1_990), at(2_810))
        val now = at(2_050)
        val expected = listOf(chain(part, RecordingEndStart.AtTime(at(2_000))))
        assertEquals(expected, offers(part, now = now, live = event))
        assertEquals(expected, offers(part, now = now, live = linked))
        assertEquals(expected, offers(part, now = now, live = sameSlot))
        assertEquals(expected + RecordingEndOffer.WatchLive(requireNotNull(part.channelId), different),
            offers(part, now = now, live = different))
    }

    @Test
    fun missingLiveEventUsesOfferedRecordingsEpgAiringBeforeDvrTimes() {
        val part = recording(2, 2_100, 2_800)
        val event = EpgEvent.create(requireNotNull(part.eventId), part.channelId, at(2_000), at(2_800))
        val expected = listOf(chain(part, RecordingEndStart.AtTime(at(2_000))))
        val live = RecordingEndOffer.WatchLive(requireNotNull(part.channelId), null)
        assertEquals(expected, offers(part, now = at(2_050), lookup = mapOf(event.id to event)::get))
        assertEquals(expected + live, offers(part, now = at(2_050)))
        assertEquals(expected, offers(part, now = at(2_100)))

        val laterAiring = EpgEvent.create(event.id, event.channelId, at(2_150), at(2_800))
        assertEquals(expected + live, offers(part, now = at(2_100), lookup = mapOf(event.id to laterAiring)::get))
    }

    @Test
    fun fourCardLimitKeepsPriorityAcrossGroups() {
        val rule = AutorecRuleId("rule")
        val finished = recording(1, 1_000, 2_000, rule = rule)
        val part = recording(2, 2_000, 2_800, rule = rule)
        val second = recording(6, 2_050, 2_800)
        val next = recording(3, 3_000, 4_000, rule = rule)
        val older = recording(4, 500, 900, rule = rule)
        val oldest = recording(5, 200, 400, rule = rule)
        assertEquals(listOf(
            chain(part, RecordingEndStart.AtTime(at(2_000))),
            chain(second, RecordingEndStart.AtTime(at(2_000))),
            RecordingEndOffer.Recording(next, RecordingEndKind.NEXT_EPISODE, RecordingEndStart.FromBeginning),
            RecordingEndOffer.Recording(older, RecordingEndKind.OTHER_EPISODE, RecordingEndStart.FromBeginning),
        ), offers(oldest, older, next, second, part, finished = finished, now = at(2_001)))
    }

    @Test
    fun fourEarliestChainPartsFillTheOfferSet() {
        val parts = (2L..6L).map { recording(it, 2_000 + it * 10, 2_800) }
        assertEquals(parts.take(4).map { chain(it, RecordingEndStart.AtTime(at(2_000))) },
            offers(*parts.asReversed().toTypedArray()))
    }

    private fun chain(entry: DvrEntry, start: RecordingEndStart) =
        RecordingEndOffer.Recording(entry, RecordingEndKind.CHAIN_PART, start)

    private fun offers(
        vararg entries: DvrEntry,
        finished: DvrEntry = ended,
        lookup: (EventId) -> EpgEvent? = { null },
        reached: Instant = at(2_000),
        now: Instant = at(10_000),
        live: EpgEvent? = null,
    ) = recordingEndOffers(finished, reached, entries.toList(), lookup, observation::recordingPlaybackAdmission, live, now)

    private fun recording(
        id: Long,
        start: Long,
        stop: Long?,
        channel: ChannelId? = ChannelId(1),
        event: EventId? = EventId(id),
        rule: AutorecRuleId? = null,
        fileStart: Long = start,
        state: DvrEntryState = DvrEntryState.COMPLETED,
        fileStop: Long? = stop,
        startExtra: Duration? = null,
        stopExtra: Duration? = null,
        files: List<DvrRecordingFile> = listOf(DvrRecordingFile.create(start = at(fileStart), stop = fileStop?.let(::at))),
        playCount: Long? = null,
        resume: Duration? = null,
    ) = DvrEntry.create(DvrEntryId(id), channelId = channel, eventId = event, autorecRuleId = rule,
        start = at(start), stop = stop?.let(::at), startExtraMinutes = startExtra, stopExtraMinutes = stopExtra,
        files = files, state = state,
        playCount = playCount, playPosition = resume)

    private fun at(seconds: Long) = Instant.fromEpochSeconds(seconds)
}
