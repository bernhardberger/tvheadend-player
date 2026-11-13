package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingStream
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.DvrRetention
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMetadataPolicyTest {
    @Test fun keepUntilChoicesPreserveCurrentAndSelectManagedPoliciesWithoutADuplicateCurrentRow() {
        val now = Instant.parse("2026-11-13T12:00:00Z").epochSeconds
        fun options(removal: DvrRemoval?) = recordingKeepUntilOptions(
            DvrEntry.create(DvrEntryId(1), stop = Instant.fromEpochSeconds(now), removal = removal),
            now, ZoneId.of("UTC"),
        )
        val finite = options(DvrRemoval.Days(3))
        assertEquals(RecordingKeepUntilChoice.entries, finite.map { it.choice })
        assertEquals(RecordingKeepUntilChoice.CURRENT, finite.single { it.selected }.choice)
        assertEquals(DvrRemoval.Days(3), finite.first().removal)
        assertEquals(now + 3 * 86400L, finite.first().expirySec)
        for (offered in finite.drop(1)) {
            val matching = options(offered.removal)
            assertFalse("No duplicate Current row for ${offered.choice}",
                matching.any { it.choice == RecordingKeepUntilChoice.CURRENT })
            assertEquals(offered.choice, matching.single { it.selected }.choice)
        }
        for (removal in listOf(DvrRemoval.UntilSpaceNeeded, DvrRemoval.Forever)) {
            val managed = options(removal)
            assertFalse(managed.any { it.choice == RecordingKeepUntilChoice.CURRENT })
            assertEquals(removal, managed.single { it.selected }.removal)
            assertNull(managed.single { it.selected }.expirySec)
        }
        for (removal in listOf(null, DvrRemoval.ServerDefault)) {
            assertNull(options(removal).first().removal)
            assertNull(options(removal).first().expirySec)
        }
        val retained = recordingKeepUntilOptions(DvrEntry.create(DvrEntryId(1),
            stop = Instant.fromEpochSeconds(now), removal = DvrRemoval.Forever, retention = DvrRetention.Days(3)),
            now, ZoneId.of("UTC"))
        assertEquals(RecordingKeepUntilChoice.CURRENT, retained.single { it.selected }.choice)
        assertEquals(now + 3 * 86400L, retained.single { it.selected }.expirySec)
        assertFalse(retained.single { it.choice == RecordingKeepUntilChoice.FOREVER }.selected)
        assertEquals(DvrRetention.UntilFilesRemoved,
            retained.single { it.choice == RecordingKeepUntilChoice.FOREVER }.retention)
        assertTrue(retained.filter { it.choice != RecordingKeepUntilChoice.FOREVER }.all { it.retention == null })
        assertTrue(options(DvrRemoval.Forever).all { it.retention == null })
    }

    @Test fun keepUntilPeriodsCountFromNowAndRoundUpTheServerDayCount() {
        val now = Instant.parse("2026-11-13T12:00:00Z").epochSeconds
        for ((stop, days) in listOf(
            now - 10 * 86400L to 17, now to 7, now + 3600L to 7,
            now - 10 * 86400L - 1 to 18, now + 30 * 86400L to 1,
        )) {
            val option = recordingKeepUntilOptions(
                DvrEntry.create(DvrEntryId(1), stop = Instant.fromEpochSeconds(stop)),
                now, ZoneId.of("UTC"),
            ).single { it.choice == RecordingKeepUntilChoice.ONE_WEEK }
            assertEquals("stop=$stop", DvrRemoval.Days(days), option.removal)
            assertEquals(stop + days * 86400L, option.expirySec)
            assertTrue(option.expirySec!! >= now + 7 * 86400L)
        }
        val missingStop = recordingKeepUntilOptions(DvrEntry.create(DvrEntryId(1)), now, ZoneId.of("UTC"))
        assertNull(missingStop.single { it.choice == RecordingKeepUntilChoice.ONE_WEEK }.removal)
        for (stop in listOf(now - 86400L, now - 86401L)) {
            val expired = recordingKeepUntilOptions(DvrEntry.create(DvrEntryId(1),
                stop = Instant.fromEpochSeconds(stop), removal = DvrRemoval.Days(1)), now, ZoneId.of("UTC"))
            assertNull("A choice never displays a date at or before now", expired.first().expirySec)
        }
    }

    @Test fun keepUntilCalendarTargetsHandleMonthEndsLeapYearsAndThePassedZone() {
        val cases = listOf(
            Triple(RecordingKeepUntilChoice.ONE_MONTH, "2026-01-31T12:00:00Z", "2026-02-28T12:00:00Z"),
            Triple(RecordingKeepUntilChoice.THREE_MONTHS, "2026-01-31T12:00:00Z", "2026-04-30T11:00:00Z"),
            Triple(RecordingKeepUntilChoice.SIX_MONTHS, "2026-08-31T12:00:00Z", "2027-02-28T13:00:00Z"),
            Triple(RecordingKeepUntilChoice.ONE_YEAR, "2024-02-29T12:00:00Z", "2025-02-28T12:00:00Z"),
            Triple(RecordingKeepUntilChoice.ONE_WEEK, "2026-10-20T10:00:00Z", "2026-10-27T11:00:00Z"),
        )
        for ((choice, start, target) in cases) {
            val now = Instant.parse(start)
            val targetSec = Instant.parse(target).epochSeconds
            val option = recordingKeepUntilOptions(
                DvrEntry.create(DvrEntryId(1), stop = now), now.epochSeconds, ZoneId.of("Europe/Berlin"),
            ).single { it.choice == choice }
            val expiry = option.expirySec!!
            assertTrue("$choice does not expire before its calendar target", expiry >= targetSec)
            assertTrue("$choice uses the smallest whole day", expiry - 86400L < targetSec)
        }
    }

    @Test fun expiryRoundsDownAtMinuteHourAndDayBoundariesWithinSevenDays() {
        val now = 1_000L
        val cases = listOf(
            1L to RecordingExpiryTimeLeft(1, RecordingExpiryUnit.MINUTES),
            59L to RecordingExpiryTimeLeft(1, RecordingExpiryUnit.MINUTES),
            60L to RecordingExpiryTimeLeft(1, RecordingExpiryUnit.MINUTES),
            3599L to RecordingExpiryTimeLeft(59, RecordingExpiryUnit.MINUTES),
            3600L to RecordingExpiryTimeLeft(1, RecordingExpiryUnit.HOURS),
            86399L to RecordingExpiryTimeLeft(23, RecordingExpiryUnit.HOURS),
            86400L to RecordingExpiryTimeLeft(1, RecordingExpiryUnit.DAYS),
            7L * 86400 - 1 to RecordingExpiryTimeLeft(6, RecordingExpiryUnit.DAYS),
            7L * 86400 to RecordingExpiryTimeLeft(7, RecordingExpiryUnit.DAYS),
        )
        cases.forEach { (seconds, expected) ->
            assertEquals("$seconds seconds left", expected, recordingExpiryTimeLeft(now + seconds, now))
        }
        for (removal in listOf(null, now - 1, now, now + 7L * 86400 + 1)) {
            assertNull(recordingExpiryTimeLeft(removal, now))
        }
    }

    @Test fun removalUsesStopAndOnlyFutureFiniteDays() {
        fun entry(removal: DvrRemoval?, stop: Instant? = Instant.fromEpochSeconds(100)) =
            DvrEntry.create(DvrEntryId(1), stop = stop, removal = removal)
        assertEquals(86500L, recordingRemovalTime(entry(DvrRemoval.Days(1)), 100))
        for (retention in listOf(DvrRetention.Days(1), DvrRetention.UntilFilesRemoved, DvrRetention.Forever, null)) {
            val forever = DvrEntry.create(DvrEntryId(1), stop = Instant.fromEpochSeconds(100),
                removal = DvrRemoval.Forever, retention = retention)
            assertEquals("Forever with $retention", if (retention is DvrRetention.Days) 86500L else null,
                recordingRemovalTime(forever, 100))
            assertNull(recordingRemovalTime(forever, 86500))
        }
        for (removal in listOf(null, DvrRemoval.ServerDefault, DvrRemoval.UntilSpaceNeeded, DvrRemoval.Forever))
            assertNull(recordingRemovalTime(entry(removal), 100))
        assertNull(recordingRemovalTime(entry(DvrRemoval.Days(1)), 86500))
        assertNull(recordingRemovalTime(entry(DvrRemoval.Days(1)), 86501))
        assertNull(recordingRemovalTime(entry(DvrRemoval.Days(1), null), 100))
        assertEquals(100L + 2147483645L * 86400L, recordingRemovalTime(entry(DvrRemoval.Days(2147483645)), 100))
        for (state in listOf(DvrEntryState.COMPLETED, DvrEntryState.COMPLETED_ERROR, DvrEntryState.MISSED)) {
            for (files in listOf(null, emptyList<DvrRecordingFile>())) {
                val withoutFile = DvrEntry.create(DvrEntryId(1), state = state, stop = Instant.fromEpochSeconds(100),
                    removal = DvrRemoval.Days(1), files = files)
                assertNull("$state without files has no expiry", recordingExpiryTime(withoutFile, 100))
            }
        }
        val file = listOf(DvrRecordingFile.create())
        val finished = DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED, stop = Instant.fromEpochSeconds(100),
            removal = DvrRemoval.Days(1), files = file)
        assertEquals(86500L, recordingExpiryTime(finished, 100))
        val scheduled = DvrEntry.create(DvrEntryId(2), state = DvrEntryState.SCHEDULED, stop = finished.stop,
            removal = finished.removal, files = file)
        assertNull(recordingExpiryTime(scheduled, 100))
    }

    @Test fun sizePrefersTotalAndFallsBackToKnownFileSizes() {
        val files = listOf(DvrRecordingFile.create(sizeBytes = 100), DvrRecordingFile.create(sizeBytes = 200),
            DvrRecordingFile.create())
        assertEquals(500L, recordingSizeBytes(DvrEntry.create(DvrEntryId(1), dataSizeBytes = 500, files = files)))
        assertEquals(300L, recordingSizeBytes(DvrEntry.create(DvrEntryId(1), files = files)))
        assertEquals(0L, recordingSizeBytes(DvrEntry.create(DvrEntryId(1), dataSizeBytes = 0, files = files)))
        assertNull(recordingSizeBytes(DvrEntry.create(DvrEntryId(1), files = listOf(DvrRecordingFile.create()))))
    }

    private fun at(seconds: Long) = Instant.fromEpochSeconds(seconds)

    private fun timed(state: DvrEntryState, vararg files: DvrRecordingFile) = DvrEntry.create(DvrEntryId(1), state = state,
        start = at(1_000), stop = at(1_000 + 61 * 60), files = files.toList().takeIf { it.isNotEmpty() })

    @Test fun elapsedProgressRunsAcrossTheScheduleAndNeedsOne() {
        val entry = timed(DvrEntryState.RECORDING)
        assertEquals(0.5f, recordingElapsedProgress(entry, nowSec = 1_000 + 61 * 30)!!, 0.001f)
        assertEquals(1f, recordingElapsedProgress(entry, nowSec = 99_999)!!, 0f)
        assertNull(recordingElapsedProgress(entry, nowSec = 999))
        assertNull(recordingElapsedProgress(DvrEntry.create(DvrEntryId(1), start = at(1_000)), nowSec = 1_100))
    }

    @Test fun lengthSumsTheFilesTimesRatherThanTheSchedule() {
        assertEquals(5L, recordingLengthSeconds(timed(DvrEntryState.COMPLETED,
            DvrRecordingFile.create(start = at(1_010), stop = at(1_015))), nowSec = 99_999))
        assertEquals(46L * 60, recordingLengthSeconds(timed(DvrEntryState.COMPLETED,
            DvrRecordingFile.create(start = at(1_000), stop = at(1_000 + 30 * 60)),
            DvrRecordingFile.create(start = at(5_000), stop = at(5_000 + 16 * 60))), nowSec = 99_999))
    }

    @Test fun runningLengthMergesClosedAndOpenFilesWithoutCountingGaps() {
        for (state in listOf(DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) {
            assertEquals(600L, recordingLengthSeconds(timed(state,
                DvrRecordingFile.create(start = at(1_300), stop = at(1_400)),
                DvrRecordingFile.create(start = at(1_200))), nowSec = 1_800))
            val closed = DvrRecordingFile.create(start = at(1_000), stop = at(1_100))
            assertEquals(400L, recordingLengthSeconds(timed(state, closed,
                DvrRecordingFile.create(start = at(1_500))), nowSec = 1_800))
            assertEquals(400L, recordingLengthSeconds(timed(DvrEntryState.COMPLETED, closed,
                DvrRecordingFile.create(start = at(1_500), stop = at(1_800))), nowSec = 1_800))
            assertEquals(100L, recordingLengthSeconds(timed(state, closed), nowSec = 1_800))
        }
        assertTrue(recordingIsRunning(timed(DvrEntryState.RECORDING_ERROR)))
        assertFalse(recordingIsRunning(timed(DvrEntryState.COMPLETED_ERROR)))
    }

    @Test fun recordedSpanRunsFromTheFirstFileToTheLastStopOrNowWhileRecording() {
        assertEquals(at(1_000) to at(5_960), recordingRecordedSpan(timed(DvrEntryState.COMPLETED,
            DvrRecordingFile.create(start = at(5_000), stop = at(5_960)),
            DvrRecordingFile.create(start = at(1_000), stop = at(2_800))), nowSec = 99_999))
        assertEquals(at(1_200) to at(1_800), recordingRecordedSpan(timed(DvrEntryState.RECORDING,
            DvrRecordingFile.create(start = at(1_200))), nowSec = 1_800))
        assertNull(recordingRecordedSpan(timed(DvrEntryState.COMPLETED), nowSec = 99_999))
        assertNull(recordingRecordedSpan(timed(DvrEntryState.COMPLETED, DvrRecordingFile.create()), nowSec = 99_999))
    }

    @Test fun onlyMissingFileTimesFallBackToThePlannedLength() {
        assertEquals(61L * 60, recordingLengthSeconds(timed(DvrEntryState.COMPLETED), nowSec = 99_999))
        assertEquals(61L * 60, recordingLengthSeconds(timed(DvrEntryState.COMPLETED,
            DvrRecordingFile.create(sizeBytes = 7), DvrRecordingFile.create()), nowSec = 99_999))
        assertNull(recordingLengthSeconds(timed(DvrEntryState.RECORDING,
            DvrRecordingFile.create(sizeBytes = 7)), nowSec = 1_800))
        assertNull(recordingLengthSeconds(timed(DvrEntryState.RECORDING_ERROR), nowSec = 1_800))
        assertNull(recordingLengthSeconds(DvrEntry.create(DvrEntryId(1), start = at(1_000)), nowSec = 1_800))
    }

    @Test fun failedRecordingNeedsRealFileTimesForItsRecordedLength() {
        val failed = timed(DvrEntryState.COMPLETED_ERROR)
        assertNull(recordingLengthSeconds(failed, nowSec = 99_999))
        assertEquals(61L * 60, recordingPlannedSeconds(failed))
        assertNull(recordingLengthSeconds(timed(DvrEntryState.COMPLETED_ERROR,
            DvrRecordingFile.create(sizeBytes = 7)), nowSec = 99_999))
        assertEquals(30L, recordingLengthSeconds(timed(DvrEntryState.COMPLETED_ERROR,
            DvrRecordingFile.create(start = at(1_000), stop = at(1_030))), nowSec = 99_999))
    }

    @Test fun anEntryThatNeverRecordedHasNoLength() {
        for (state in listOf(DvrEntryState.MISSED, DvrEntryState.INVALID)) {
            assertNull(recordingLengthSeconds(timed(state), nowSec = 99_999))
            val leftBehind = timed(state, DvrRecordingFile.create(start = at(1_000), stop = at(1_030)))
            assertNull(recordingLengthSeconds(leftBehind, nowSec = 99_999))
        }
    }

    @Test fun incompleteOrMixedTimelessFilesMakeTheTotalUnknown() {
        val valid = DvrRecordingFile.create(start = at(1_000), stop = at(1_300))
        for (state in listOf(DvrEntryState.COMPLETED, DvrEntryState.COMPLETED_ERROR, DvrEntryState.REMOVED)) {
            for (invalid in listOf(
                DvrRecordingFile.create(start = at(1_400)),
                DvrRecordingFile.create(stop = at(1_500)),
            )) {
                for (files in listOf(listOf(invalid), listOf(valid, invalid))) {
                    val entry = timed(state, *files.toTypedArray())
                    assertNull(recordingLengthSeconds(entry, nowSec = 1_800))
                    assertNull(recordingStoppedEarly(entry))
                }
            }
            val mixed = timed(state, valid, DvrRecordingFile.create(sizeBytes = 1))
            assertNull(recordingLengthSeconds(mixed, nowSec = 1_800))
            assertNull(recordingStoppedEarly(mixed))
        }
        for (state in listOf(DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) {
            assertNull(recordingLengthSeconds(timed(state, valid,
                DvrRecordingFile.create(stop = at(1_500))), nowSec = 1_800))
            assertNull(recordingLengthSeconds(timed(state, valid,
                DvrRecordingFile.create(sizeBytes = 1)), nowSec = 1_800))
        }
    }

    @Test fun zeroRecordedLengthDoesNotProduceAStoppedEarlyHint() {
        assertNull(recordingStoppedEarly(timed(DvrEntryState.COMPLETED,
            DvrRecordingFile.create(start = at(1_000), stop = at(1_000)))))
    }

    @Test fun overlappingFilesCountEachRecordedSecondOnlyOnce() {
        val files = arrayOf(
            DvrRecordingFile.create(start = at(1_200), stop = at(1_400)),
            DvrRecordingFile.create(start = at(1_000), stop = at(1_300)),
            DvrRecordingFile.create(start = at(1_100), stop = at(1_200)),
            DvrRecordingFile.create(start = at(1_400), stop = at(1_500)),
        )
        for (state in listOf(DvrEntryState.COMPLETED, DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) {
            assertEquals(500L, recordingLengthSeconds(timed(state, *files), nowSec = 1_800))
        }
        assertEquals(RecordingShortfall(500, 61 * 60), recordingStoppedEarly(timed(DvrEntryState.COMPLETED, *files)))
    }

    @Test fun paddingComesFromTheEntrysEffectiveExtraMinutes() {
        val padded = DvrEntry.create(DvrEntryId(1), start = at(10_000), stop = at(12_000),
            startExtraMinutes = 2.minutes, stopExtraMinutes = 120.minutes)
        assertEquals(at(10_000 - 120), recordingPaddedStart(padded))
        assertEquals(at(12_000 + 7_200), recordingPaddedStop(padded))
        val unpadded = DvrEntry.create(DvrEntryId(1), start = at(10_000), stop = at(12_000))
        assertEquals(at(10_000), recordingPaddedStart(unpadded))
        assertEquals(at(12_000), recordingPaddedStop(unpadded))
        assertNull(recordingPaddedStop(DvrEntry.create(DvrEntryId(1), start = at(10_000))))
    }

    @Test fun finalLengthOfARunningRecordingRunsItsFilesOnToThePaddedStop() {
        fun running(fileStart: Long, stopExtra: Long = 0, state: DvrEntryState = DvrEntryState.RECORDING) =
            DvrEntry.create(DvrEntryId(1), state = state, start = at(10_000), stop = at(13_600),
                startExtraMinutes = 2.minutes, stopExtraMinutes = stopExtra.minutes,
                files = listOf(DvrRecordingFile.create(start = at(fileStart))))
        // Pre-padding is on disk and post-padding is still to come; the schedule alone counts neither.
        assertEquals(120L + 3_600 + 300, recordingFinalLengthSeconds(running(9_880, stopExtra = 5), nowSec = 12_000))
        // A late start never claims the time it missed.
        assertEquals(3_000L, recordingFinalLengthSeconds(running(10_600), nowSec = 12_000))
        // Past the stop it is what is on disk.
        assertEquals(4_000L, recordingFinalLengthSeconds(running(10_000), nowSec = 14_000))
        assertNull(recordingFinalLengthSeconds(running(9_880, state = DvrEntryState.COMPLETED), nowSec = 12_000))
        assertNull(recordingFinalLengthSeconds(timed(DvrEntryState.RECORDING), nowSec = 1_800))
    }

    @Test fun stoppedEarlyNeedsAFinishedFileUnderTheThreshold() {
        val planned = 61L * 60
        val threshold = (planned * RECORDING_STOPPED_EARLY_FRACTION).toLong()
        fun file(seconds: Long) = DvrRecordingFile.create(start = at(1_000), stop = at(1_000 + seconds))
        assertEquals(RecordingShortfall(5, planned), recordingStoppedEarly(timed(DvrEntryState.COMPLETED, file(5))))
        assertEquals(RecordingShortfall(5, planned), recordingStoppedEarly(timed(DvrEntryState.COMPLETED_ERROR, file(5))))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.REMOVED, file(threshold - 1))))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.COMPLETED, file(threshold))))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.COMPLETED, file(planned + 300))))
        // A short programme under the share but missing less than two minutes is not flagged.
        fun tenMinutes(recorded: Long) = DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED,
            start = at(1_000), stop = at(1_000 + 600), files = listOf(file(recorded)))
        assertNull(recordingStoppedEarly(tenMinutes(600 - 119)))
        assertNotNull(recordingStoppedEarly(tenMinutes(600 - 120)))
        // Running, scheduled and fallback lengths are not evidence of an early stop.
        assertNull(recordingStoppedEarly(timed(DvrEntryState.RECORDING, file(5))))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.SCHEDULED)))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.COMPLETED)))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.COMPLETED, DvrRecordingFile.create(sizeBytes = 7))))
    }

    @Test fun stoppedEarlyRequiresAnEarlyFinalStopNotALateStartOrGap() {
        val stop = 1_000 + 61L * 60
        fun file(start: Long, end: Long) = DvrRecordingFile.create(start = at(start), stop = at(end))
        for (state in listOf(DvrEntryState.COMPLETED, DvrEntryState.COMPLETED_ERROR)) {
            assertNull(recordingStoppedEarly(timed(state, file(1_600, stop))))
            assertNull(recordingStoppedEarly(timed(state, file(2_000, stop), file(1_000, 1_300))))
            assertNull(recordingStoppedEarly(timed(state, file(1_600, stop - 119))))
            assertEquals(RecordingShortfall(stop - 120 - 1_600, 61 * 60),
                recordingStoppedEarly(timed(state, file(1_600, stop - 120))))
        }
        // Post-padding must not turn a normal programme end into an early stop.
        assertNull(recordingStoppedEarly(DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED,
            start = at(1_000), stop = at(stop), stopExtraMinutes = 5.minutes,
            files = listOf(file(1_600, stop)))))
    }

    @Test fun errorsKeepUnknownDistinctFromZero() {
        fun errors(stream: Long?, data: Long?) = recordingErrorCount(
            DvrEntry.create(DvrEntryId(1), streamErrors = stream, dataErrors = data))
        assertNull(errors(null, null))
        assertEquals(0L, errors(0, null))
        assertEquals(3L, errors(1, 2))
        assertEquals(2L, errors(null, 2))
    }

    @OptIn(ExperimentalTvheadendApi::class)
    @Test fun pictureUsesHighestKnownVideoHeight() {
        fun picture(vararg heights: Int?) = recordingPicture(DvrEntry.create(DvrEntryId(1), files = listOf(
            DvrRecordingFile.create(streams = heights.map { DvrRecordingStream(SubscriptionStreamType.H264, height = it) }))))
        assertNull(picture(null, 0))
        assertEquals("SD 576", picture(576))
        assertEquals("HD 720", picture(720))
        assertEquals("HD 1080", picture(720, 1080))
        assertEquals("UHD 2160", picture(1080, 2160))
    }

    @Test fun watchStateIsUnwatchedWatchedOrPartlyWatchedButNeverACount() {
        fun entry(playCount: Long?, state: DvrEntryState = DvrEntryState.COMPLETED) = DvrEntry.create(DvrEntryId(1),
            state = state, start = at(0), stop = at(100 * 60), playCount = playCount)
        assertEquals(RecordingWatchState.Unwatched, recordingWatchState(entry(null), null, 0))
        assertEquals(RecordingWatchState.Unwatched, recordingWatchState(entry(0), null, 0))
        assertEquals(RecordingWatchState.Watched, recordingWatchState(entry(147), null, 0))
        assertEquals(RecordingWatchState.Unwatched, recordingWatchState(entry(3, DvrEntryState.MISSED), null, 0))
        // A saved position wins over the open count, and its share uses the recorded length.
        assertEquals(RecordingWatchState.PartlyWatched(0.25), recordingWatchState(entry(2), 25.minutes, 0))
        // The SDK clears the position when it marks a recording watched, so a late one is still partly watched.
        assertEquals(RecordingWatchState.PartlyWatched(0.99), recordingWatchState(entry(0), 99.minutes, 0))
        assertEquals(RecordingWatchState.Unwatched, recordingWatchState(entry(0), 0.minutes, 0))
        assertEquals(RecordingWatchState.PartlyWatched(null), recordingWatchState(
            DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED), 5.minutes, 0))
    }
}
