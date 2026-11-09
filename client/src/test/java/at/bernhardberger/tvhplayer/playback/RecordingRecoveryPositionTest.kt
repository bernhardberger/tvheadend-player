@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.wav.WavExtractor
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.core.recordingErrorCondition
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import java.io.FileNotFoundException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The viewer's start choice applies to the first open; recovery continues where playback stood.
 * A real player plays a 30-minute recording whose reads can fail and whose seek map can arrive
 * late, as a progressive file's does. Growing-source cases instead use real synthetic TS bytes
 * through the SDK's growing recording source and Media3 extractor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecordingRecoveryPositionTest {
    @Test fun aGrowingRecordingContinuesPastTheOldEndWhenMoreBytesArrive() = exerciseGrowing {
        playGrowing()
        val oldEnd = player.duration
        assertTrue("the TS extent must be known", oldEnd > 0)
        appendGrowing()

        driver.advanceLive(player, clock, oldEnd + 1_000)

        assertTrue("position=${player.currentPosition}, old end=$oldEnd", player.currentPosition > oldEnd)
        assertTrue(player.isPlaying)
        assertNoRecordingFailure()
    }

    @Test fun aGrowingRecordingRecoversFromAnInterruptedReaderWhileTheFileKeepsGrowing() = exerciseGrowing {
        playGrowing()
        val oldEnd = player.duration
        requireNotNull(realRecording).interrupt()
        appendGrowing()

        // Media3 may retry a failed load. Let its retry/backoff run on the controlled clock,
        // then require playback beyond the bytes available before the interruption.
        driver.advanceLive(player, clock, oldEnd + 5_000)

        assertNoRecordingFailure()
        assertTrue("position=${player.currentPosition}, old end=$oldEnd", player.currentPosition > oldEnd)
        assertTrue(player.isPlaying)
    }

    @Test fun aGrowingRecordingEndsNormallyAfterTheWriterFinishes() = exerciseGrowing {
        playGrowing()
        appendGrowing()
        requireNotNull(realRecording).finish()
        session.publish(growingObservation(completed = true))

        driver.advanceLive(player, clock, 15_000)
        TestPlayerRunHelper.advance(player).untilState(Player.STATE_ENDED)
        driver.settleLive(player)

        assertNoRecordingFailure()
        assertEquals(Player.STATE_ENDED, player.playbackState)
    }

    private fun exerciseGrowing(block: suspend Fixture.() -> Unit) = runTest(timeout = 120.seconds) {
        val recording = FakeGrowingRecording(TS.copyOfRange(0, TS_SPLIT))
        val fixture = Fixture(CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
            testScheduler, null, true, null, recording)
        try {
            fixture.block()
        } finally {
            recording.finish()
            fixture.scope.cancel()
            testScheduler.runCurrent()
            fixture.runtime.detach()
            fixture.player.release()
            fixture.session.shutdown()
        }
    }

    @Test fun runtimeAndSessionSeeksRemainDeliberateWhenCutpointsArriveLater() {
        for (sessionSeek in listOf(false, true)) {
            for ((action, mode) in listOf(
                DvrCutpointAction.CUT to at.bernhardberger.tvhplayer.settings.AdBreakMode.ASK,
                DvrCutpointAction.COMMERCIAL_BREAK to at.bernhardberger.tvhplayer.settings.AdBreakMode.ASK,
                DvrCutpointAction.COMMERCIAL_BREAK to at.bernhardberger.tvhplayer.settings.AdBreakMode.AUTOMATIC,
            )) {
                val response = CompletableDeferred<Unit>()
                val points = listOf(DvrCutpoint(20.minutes, 25.minutes, action))
                exercise(cutpoints = { response.await(); DvrCutpointsResult.Available.create(points) }) {
                    settings.setAdBreakMode(mode)
                    play(FIRST)
                    if (sessionSeek) {
                        val seek = runtime.seekRecordingFromSession(TWENTY_MINUTES) { true }
                        awaitMain { seek.isCompleted }
                    } else runtime.seekTo(TWENTY_MINUTES)
                    awaitMain { player.currentPosition >= TWENTY_MINUTES }
                    response.complete(Unit)
                    awaitMain { runtime.recordingCutpoints.value == points }
                    settleMain()
                    assertTrue("deliberate destination: ${player.currentPosition}", player.currentPosition < 25 * ONE_MINUTE)
                    assertNull(runtime.recordingSkipPrompt.value)
                }
            }
        }
    }

    @Test fun sameRecordingRecoveryDoesNotForgetADeliberatelyEnteredCut() {
        for ((action, mode) in listOf(
            DvrCutpointAction.CUT to at.bernhardberger.tvhplayer.settings.AdBreakMode.ASK,
            DvrCutpointAction.COMMERCIAL_BREAK to at.bernhardberger.tvhplayer.settings.AdBreakMode.ASK,
            DvrCutpointAction.COMMERCIAL_BREAK to at.bernhardberger.tvhplayer.settings.AdBreakMode.AUTOMATIC,
        )) {
            val points = listOf(DvrCutpoint(20.minutes, 25.minutes, action))
            exercise(cutpoints = { DvrCutpointsResult.Available.create(points) }) {
                settings.setAdBreakMode(mode)
                play(FIRST)
                awaitMain { runtime.recordingCutpoints.value == points }
                failAtTwentyMinutes()
                media.failFromByte = Long.MAX_VALUE
                media.seekable = false
                assertTrue(runtime.retryRecording()?.isStarted == true)
                awaitUnseekablePlayback()
                awaitMain { runtime.recordingCutpoints.value == points }
                settleMain()
                assertTrue(player.currentPosition < ONE_MINUTE)
                media.openSeeking()
                awaitMain { player.isCurrentMediaItemSeekable && player.currentPosition >= TWENTY_MINUTES }
                settleMain()
                assertTrue("recovered deliberate destination: ${player.currentPosition}", player.currentPosition < 25 * ONE_MINUTE)
                assertNull(runtime.recordingSkipPrompt.value)
            }
        }
    }

    @Test fun savedPositionResumeInsideACutStillSkips() {
        val points = listOf(DvrCutpoint(20.minutes, 25.minutes, DvrCutpointAction.CUT))
        exercise(bookmark = 20.minutes, cutpoints = { DvrCutpointsResult.Available.create(points) }) {
            play(FIRST, RecordingPlaybackStart.RESUME)
            awaitMain { runtime.recordingCutpoints.value == points && player.currentPosition >= 25 * ONE_MINUTE }
        }
    }

    @Test fun retryAfterAReadErrorContinuesWhereTheRecordingStood() = exercise {
        play(FIRST)
        failAtTwentyMinutes()
        media.seekable = false

        assertTrue(runtime.retryRecording()?.isStarted == true)

        // Until the recording can seek, the recovery seek waits instead of being lost.
        awaitUnseekablePlayback()
        media.openSeeking()
        assertEventuallyNear(TWENTY_MINUTES)
        assertEquals(AppPlaybackTarget.Recording(FIRST), runtime.activeTarget.value)
    }

    @Test fun retryOfAResumedRecordingContinuesWhereItStoodNotAtTheBookmark() = exercise(bookmark = 5.minutes) {
        play(FIRST, RecordingPlaybackStart.RESUME)
        assertEventuallyNear(FIVE_MINUTES) // the viewer's choice: the server bookmark
        failAtTwentyMinutes()

        assertTrue(runtime.retryRecording()?.isStarted == true)

        assertEventuallyNear(TWENTY_MINUTES)
        settleMain()
        assertNear(TWENTY_MINUTES, player.currentPosition) // no late bookmark seek follows
    }

    @Test fun routeRestorationAfterANewServerSessionContinuesWhereTheRecordingStood() = exercise {
        play(FIRST)
        failAtTwentyMinutes()
        session.replaceGeneration(observation())
        settleMain()
        // The server session's loss leaves the failed target's position in the player.
        assertNear(TWENTY_MINUTES, player.currentPosition)
        media.seekable = false

        assertTrue(restore(FIRST)?.isStarted == true)

        awaitUnseekablePlayback()
        media.openSeeking()
        assertEventuallyNear(TWENTY_MINUTES)
        assertEquals(AppPlaybackTarget.Recording(FIRST), runtime.activeTarget.value)
    }

    @Test fun routeRestorationAfterAnAutomaticStopContinuesWhereTheRecordingStood() = exercise {
        play(FIRST)
        seekToTwentyMinutes()
        runtime.stopAfterLoss()
        assertNull(runtime.activeTarget.value)
        media.seekable = false

        assertTrue(restore(FIRST)?.isStarted == true)

        awaitUnseekablePlayback()
        media.openSeeking()
        assertEventuallyNear(TWENTY_MINUTES)
    }

    @Test fun aFailureBeforeTheRecoverySeekKeepsThePosition() = exercise {
        play(FIRST)
        failAtTwentyMinutes()
        media.seekable = false
        media.failFromByte = byteAt(2_000) // the recovered play fails again 2 s in, still unseekable

        assertTrue(runtime.retryRecording()?.isStarted == true)
        awaitMain { player.playerError != null }
        assertTrue(player.currentPosition < ONE_MINUTE)
        media.failFromByte = Long.MAX_VALUE
        assertTrue(runtime.retryRecording()?.isStarted == true)

        awaitUnseekablePlayback()
        media.openSeeking()
        assertEventuallyNear(TWENTY_MINUTES)
    }

    @Test fun aRetryThatCannotInstallKeepsThePosition() = exercise {
        play(FIRST)
        failAtTwentyMinutes()
        session.replaceGeneration(observation(entries = listOf(SECOND)))

        assertEquals(PlaybackTargetResult.TARGET_UNAVAILABLE, runtime.retryRecording())

        session.replaceGeneration(observation())
        media.seekable = false
        assertTrue(runtime.retryRecording()?.isStarted == true)
        awaitUnseekablePlayback()
        media.openSeeking()
        assertEventuallyNear(TWENTY_MINUTES)
    }

    @Test fun repeatedRetryKeepsTheOriginalDurationWhenTheReplacementIsShorter() = exercise {
        retryWithChangedPendingDuration(unknown = false)
    }

    @Test fun repeatedRetryKeepsTheOriginalDurationWhenTheReplacementDurationIsUnknown() = exercise {
        retryWithChangedPendingDuration(unknown = true)
    }

    @Test fun aNewPlayWhileTheRecoverySeekWaitsStartsPerTheViewersChoice() = exercise {
        play(FIRST)
        failAtTwentyMinutes()
        media.seekable = false
        assertTrue(runtime.retryRecording()?.isStarted == true)

        // The viewer opens the same recording from the start before recovery could seek.
        play(FIRST, awaitSeekable = false)
        awaitUnseekablePlayback()
        media.openSeeking()

        awaitMain { player.isCurrentMediaItemSeekable }
        settleMain()
        assertTrue("after a new play: ${player.currentPosition}", player.currentPosition < ONE_MINUTE)
    }

    @Test fun aNewPlayAfterStopStartsPerTheViewersChoice() = exercise {
        play(FIRST)
        failAtTwentyMinutes()
        runtime.stop()
        media.failFromByte = byteAt(2_000) // the new play fails 2 s in

        play(FIRST)
        // The new play's own failure does not resurface the stopped play's position.
        awaitMain { player.playerError != null }
        assertTrue(player.currentPosition < ONE_MINUTE)
        media.failFromByte = Long.MAX_VALUE
        assertTrue(runtime.retryRecording()?.isStarted == true)
        awaitMain { player.isPlaying && coordinator.pendingRecordingRecoveryPosition.value == null }
        assertTrue("after retry: ${player.currentPosition}", player.currentPosition < ONE_MINUTE)
    }

    @Test fun anotherRecordingAfterAFailureDoesNotInheritItsPosition() = exercise {
        play(FIRST)
        failAtTwentyMinutes()

        media.failFromByte = byteAt(2_000) // the second recording fails 2 s in
        play(SECOND)
        awaitMain { player.playerError != null }
        media.failFromByte = Long.MAX_VALUE
        assertTrue(runtime.retryRecording()?.isStarted == true)
        awaitMain { player.isPlaying && coordinator.pendingRecordingRecoveryPosition.value == null }
        assertTrue("second after retry: ${player.currentPosition}", player.currentPosition < ONE_MINUTE)

        assertTrue(restore(FIRST)?.isStarted == true)
        awaitMain { player.isPlaying && coordinator.pendingRecordingRecoveryPosition.value == null }
        assertEquals(AppPlaybackTarget.Recording(FIRST), runtime.activeTarget.value)
        assertTrue("first restored: ${player.currentPosition}", player.currentPosition < ONE_MINUTE)
    }

    @Test fun recoveryNearTheEndStartsPerTheViewersChoice() = exercise {
        play(FIRST)
        failAt(TWENTY_NINE_MINUTES) // past the orderly-completion fraction of 30 minutes

        assertTrue(runtime.retryRecording()?.isStarted == true)

        awaitMain { player.isPlaying && coordinator.pendingRecordingRecoveryPosition.value == null }
        assertTrue("near the end: ${player.currentPosition}", player.currentPosition < ONE_MINUTE)
    }

    @Test fun retryOfAGrowingRecordingWaitsForSeekabilityThenContinuesWhereItStood() = exercise(growing = true) {
        play(FIRST)
        assertGrowingTarget()
        failAtTwentyMinutes()
        media.seekable = false

        assertTrue(runtime.retryRecording()?.isStarted == true)
        assertGrowingTarget()

        awaitUnseekablePlayback()
        media.openSeeking()
        assertEventuallyNear(TWENTY_MINUTES)
        assertEquals(AppPlaybackTarget.Recording(FIRST), runtime.activeTarget.value)
    }

    @Test fun retryOfAGrowingRecordingClampsToThePlayableEndOfItsExtent() = exercise(growing = true) {
        play(FIRST)
        assertGrowingTarget()
        failAtTwentyMinutes()
        media.lengthMs = TEN_MINUTES // the retried recording's probe reports less than was played

        assertTrue(runtime.retryRecording()?.isStarted == true)
        assertGrowingTarget()

        // The SDK's edge margin: three seconds before the extent's end, not at the end itself.
        awaitMain { player.currentPosition >= TEN_MINUTES - 3_000 }
        assertTrue("arrived at ${player.currentPosition}", player.currentPosition < TEN_MINUTES - 2_000)
        assertEventuallyNear(TEN_MINUTES - 3_000)
    }

    @Test fun aGrowingRecordingSeekableOnlyAfterTheSettleBoundKeepsPlayingFromTheStart() = exercise(growing = true) {
        play(FIRST)
        assertGrowingTarget()
        failAtTwentyMinutes()
        media.seekable = false
        assertTrue(runtime.retryRecording()?.isStarted == true)
        assertGrowingTarget()
        awaitUnseekablePlayback()

        awaitRecoveryTimeout()
        media.openSeeking()

        awaitMain { player.isCurrentMediaItemSeekable }
        settleMain()
        assertTrue("after the settle bound: ${player.currentPosition}", player.currentPosition < ONE_MINUTE)
    }

    @Test fun aGrowingRecordingThatCompletedBeforeTheRetryIsJudgedByItsMediaNotItsFileTimes() =
        exercise(growing = true) {
            media.lengthMs = SIXTY_MINUTES
            play(FIRST)
            assertGrowingTarget()
            failAt(FORTY_NINE_MINUTES)
            // The files' wall-clock span (50 minutes) says less than the 60-minute media.
            session.publish(completedObservation(spanMinutes = 50))

            assertTrue(runtime.retryRecording()?.isStarted == true)
            assertTrue(runtime.recordingAdmission.value is RecordingPlaybackAdmission.Completed)

            assertEventuallyNear(FORTY_NINE_MINUTES)
        }

    @Test fun aGrowingRecordingThatCompletedBeforeTheRetryStartsPerTheViewersChoiceNearItsMediaEnd() =
        exercise(growing = true) {
            media.lengthMs = SIXTY_MINUTES
            play(FIRST)
            assertGrowingTarget()
            failAt(FIFTY_EIGHT_MINUTES) // past the orderly-completion fraction of the 60-minute media
            // The files' wall-clock span (70 minutes) says more than the media.
            session.publish(completedObservation(spanMinutes = 70))

            assertTrue(runtime.retryRecording()?.isStarted == true)
            assertTrue(runtime.recordingAdmission.value is RecordingPlaybackAdmission.Completed)

            awaitMain { player.isCurrentMediaItemSeekable && player.isPlaying }
            settleMain()
            assertTrue("near the media end: ${player.currentPosition}", player.currentPosition < ONE_MINUTE)
        }

    @Test fun aGrowingRecordingThatCompletesWhileItsRecoveryWaitsKeepsTheGrowingClamp() =
        exercise(growing = true) {
            play(FIRST)
            assertGrowingTarget()
            failAt(TWENTY_NINE_MINUTES) // past the orderly-completion fraction of the 30-minute files
            media.seekable = false
            media.lengthMs = TEN_MINUTES // the retried recording's probe reports less than was played
            assertTrue(runtime.retryRecording()?.isStarted == true)
            assertGrowingTarget()
            awaitUnseekablePlayback()

            session.publish(completedObservation(spanMinutes = 30)) // the recording ends while recovery waits
            media.openSeeking()

            awaitMain { player.currentPosition >= TEN_MINUTES - 3_000 }
            assertTrue("arrived at ${player.currentPosition}", player.currentPosition < TEN_MINUTES - 2_000)
            assertEventuallyNear(TEN_MINUTES - 3_000)
        }

    @Test fun aGrowingRecordingThatCompletesWhileItsRecoveryWaitsKeepsTheSettleBound() =
        exercise(growing = true) {
            play(FIRST)
            assertGrowingTarget()
            failAtTwentyMinutes()
            media.seekable = false
            assertTrue(runtime.retryRecording()?.isStarted == true)
            assertGrowingTarget()
            awaitUnseekablePlayback()

            session.publish(completedObservation(spanMinutes = 30)) // the recording ends while recovery waits
            awaitRecoveryTimeout()
            media.openSeeking()

            awaitMain { player.isCurrentMediaItemSeekable }
            settleMain()
            assertTrue("after the settle bound: ${player.currentPosition}", player.currentPosition < ONE_MINUTE)
        }

    // Real Media3 and the SDK's asynchronous machinery require looper pumping.
    private fun exercise(
        bookmark: kotlin.time.Duration? = null,
        growing: Boolean = false,
        cutpoints: (suspend () -> DvrCutpointsResult)? = null,
        block: suspend Fixture.() -> Unit,
    ) = runTest(timeout = 120.seconds) {
        val fixture = Fixture(CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
            testScheduler, bookmark, growing, cutpoints)
        try {
            fixture.driver.exercise(this, 120_000) { fixture.block() }
        }
        finally {
            fixture.scope.cancel()
            testScheduler.runCurrent()
            fixture.runtime.detach()
            fixture.player.release()
            fixture.session.shutdown()
        }
    }

    private class Fixture(
        val scope: CoroutineScope,
        val scheduler: TestCoroutineScheduler,
        private val bookmark: kotlin.time.Duration?,
        private val growing: Boolean,
        cutpoints: (suspend () -> DvrCutpointsResult)?,
        val realRecording: FakeGrowingRecording? = null,
    ) {
        private val context = ApplicationProvider.getApplicationContext<Application>()
        private val focus = object : PlaybackAudioFocus {
            override fun request(onInterruption: (AudioInterruption) -> Unit): Boolean = true
            override fun abandon() = Unit
        }
        private val connection = ScriptedSubscriptionConnection()
        private val work = StandardTestDispatcher(scheduler)
        private val manager = createSubscriptionManager(connection,
            if (realRecording != null) work else Dispatchers.Default).apply { startAdmission() }
        val session = FakeTvheadendSession(if (realRecording != null) growingObservation() else observation()).apply {
            scriptLivePlaybackSuccess(manager)
            if (realRecording != null) scriptGrowingRecordingPlayback(realRecording) else scriptRecordingPlaybackSuccess()
        }
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        private val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings,
            if (realRecording != null) work else Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing }).also { owner -> scope.launch { owner.run() } }
        val media = ControlledRecording()
        val clock = FakeClock(realRecording == null)
        val player: ExoPlayer = ExoPlayer.Builder(context)
            .setClock(clock)
            .setRenderersFactory { _, _, _, _, _ -> arrayOf<Renderer>(ConsumingAudioSink()) }
            .build()

        // The SDK installs its recording item as usual; the fake server has no bytes, so the
        // same item plays from the controlled recording instead.
        val coordinator = createTvheadendPlaybackCoordinator(if (realRecording != null) player else object : ExoPlayer by player {
            override fun setMediaSource(mediaSource: MediaSource) {
                player.setMediaSource(media.source(mediaSource.mediaItem))
            }
            override fun setMediaSource(mediaSource: MediaSource, startPositionMs: Long) {
                player.setMediaSource(media.source(mediaSource.mediaItem), startPositionMs)
            }
        }, workDispatcher = if (realRecording != null) work else Dispatchers.IO).also { it.launchIn(scope) }

        private val playbackSession = when {
            realRecording != null -> session
            growing -> GrowingRecordingSession(session)
            cutpoints == null -> session
            else -> object : TvheadendSession by session {
                @OptIn(TvheadendTestingApi::class)
                override fun bindRecordingPlayback(currentSession: CurrentSessionObservation, recordingId: DvrEntryId) =
                    TvheadendTestResultFactory.boundCompletedRecordingPlayback(session, currentSession, recordingId, cutpoints = cutpoints)
            }
        }
        val runtime = AppPlaybackRuntime(player, playbackSession,
            coordinator, settings, profiles, scope, TvheadendAudioOutputProvider(context), focus,
            PlaybackRuntimePolicy.fromPlayerSettings(),
            elapsedRealtime = { scheduler.currentTime },
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })

        private val growingStates = mutableListOf<AppPlaybackState>()

        @OptIn(TvheadendTestingApi::class)
        fun growingObservation(completed: Boolean = false) = recordings(
            listOf(FIRST), null, if (completed) DvrEntryState.COMPLETED else DvrEntryState.RECORDING,
            progressSupported = true,
            files = listOf(TvheadendTestResultFactory.recordingFile(fileId = 1, serverPath = "synthetic.ts",
                start = RECORDING_START, stop = if (completed) RECORDING_START + 12.seconds else null,
                sizeBytes = requireNotNull(realRecording).sizeBytes)),
        )

        suspend fun playGrowing() {
            scope.launch(UnconfinedTestDispatcher(scheduler)) { runtime.state.collect { growingStates += it } }
            val install = scope.async { runtime.playRecording(selection(FIRST), RecordingPlaybackStart.START_OVER) }
            driver.settleLive(player)
            assertTrue("recording installation must complete", install.isCompleted)
            val installed = install.await()
            assertTrue("installation: $installed; state: ${runtime.state.value}", installed?.isStarted == true)
            // untilBackgroundThreadCondition rechecks through the player's clock after 1ms,
            // which cannot fire on FakeClock(false). Use the driver's looper signal wait;
            // it also drains due SDK work without advancing recurring probe/watchdog timers.
            driver.await("growing recording loader caught up") {
                requireNotNull(realRecording).waitingReads.value > 0
            }
            driver.advanceLive(player, clock, 100)
            TestPlayerRunHelper.advance(player).untilState(Player.STATE_READY)
            assertGrowingTarget()
            assertTrue(player.isPlaying)
            assertNoRecordingFailure()
        }

        fun appendGrowing() {
            val recording = requireNotNull(realRecording)
            recording.append(TS.copyOfRange(TS_SPLIT, TS.size))
            session.publish(growingObservation())
            driver.settleLive(player)
        }

        fun assertNoRecordingFailure() {
            val failure = growingStates.filterIsInstance<AppPlaybackState.Failed>().firstOrNull()
            val condition = recordingErrorCondition(session.observation.value.dvrEntry(FIRST),
                runtime.recordingAdmission.value, failure)
            assertNull("growing recording must recover, not fail: $condition; $failure", failure)
            assertNull(player.playerError)
            assertNull(condition)
        }

        fun observation(entries: List<DvrEntryId> = listOf(FIRST, SECOND)) = recordings(
            entries, bookmark, if (growing) DvrEntryState.RECORDING else DvrEntryState.COMPLETED, growing,
        )

        /** The growing recordings as TVHeadend reports them once ended, their files spanning [spanMinutes]. */
        fun completedObservation(spanMinutes: Int) = recordings(
            listOf(FIRST, SECOND), bookmark, DvrEntryState.COMPLETED, growing, files = listOf(
                DvrRecordingFile.create(fileId = 1, start = RECORDING_START,
                    stop = RECORDING_START + spanMinutes.minutes, sizeBytes = null),
            ),
        )

        fun selection(recordingId: DvrEntryId) =
            requireNotNull(currentRecordingPlaybackSelection(session.observation.value, recordingId))

        /** The viewer opens [recordingId]. */
        suspend fun play(
            recordingId: DvrEntryId,
            start: RecordingPlaybackStart = RecordingPlaybackStart.START_OVER,
            awaitSeekable: Boolean = true,
        ) {
            val install = scope.async { runtime.playRecording(selection(recordingId), start) }
            awaitMain { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            if (awaitSeekable) awaitMain { player.isCurrentMediaItemSeekable && player.isPlaying }
        }

        suspend fun restore(recordingId: DvrEntryId): PlaybackTargetResult? {
            val restore = scope.async {
                runtime.restoreRecordingRoute(selection(recordingId), RecordingPlaybackStart.START_OVER)
            }
            awaitMain { restore.isCompleted }
            return restore.await()
        }

        /** The installed target plays the recording as the growing file TVHeadend still writes. */
        fun assertGrowingTarget() {
            assertTrue("admission: ${runtime.recordingAdmission.value}",
                runtime.recordingAdmission.value is RecordingPlaybackAdmission.Growing)
        }

        /** The recovered play runs from the start while its recording cannot seek yet. */
        suspend fun awaitUnseekablePlayback() {
            awaitMain { player.isPlaying }
            assertFalse(player.isCurrentMediaItemSeekable)
            assertTrue("before seekability: ${player.currentPosition}", player.currentPosition < ONE_MINUTE)
        }

        suspend fun seekToTwentyMinutes() {
            runtime.seekTo(TWENTY_MINUTES)
            awaitMain { player.isPlaying && player.currentPosition >= TWENTY_MINUTES }
        }

        suspend fun retryWithChangedPendingDuration(unknown: Boolean) {
            play(FIRST)
            failAtTwentyMinutes()
            media.seekable = false
            media.lengthMs = 21 * ONE_MINUTE
            media.unknownDuration = unknown
            assertTrue(runtime.retryRecording()?.isStarted == true)
            awaitUnseekablePlayback()
            assertEquals(if (unknown) C.TIME_UNSET else 21 * ONE_MINUTE, player.duration)
            assertNotNull(coordinator.pendingRecordingRecoveryPosition.value)
            media.unknownDuration = false
            assertTrue(runtime.retryRecording()?.isStarted == true)
            awaitUnseekablePlayback()
            // 20/21 exceeds the completion cutoff; only the original 30-minute duration
            // keeps this owed position eligible, even when the new timeline becomes seekable.
            assertNotNull(coordinator.pendingRecordingRecoveryPosition.value)
            media.openSeeking()
            assertEventuallyNear(TWENTY_MINUTES)
        }

        /** The viewer skips to 20 minutes and the recording's read fails 2 s later. */
        suspend fun failAtTwentyMinutes() = failAt(TWENTY_MINUTES)

        suspend fun failAt(positionMs: Long) {
            media.failFromByte = byteAt(positionMs + 2_000)
            // Nothing at or past the seek target is loaded yet, so the seek restarts the load there
            // and that load must reach the armed byte.
            assertTrue("seek target already loaded: ${failurePlayback()}", media.readPosition < byteAt(positionMs))
            runtime.seekTo(positionMs)
            try {
                TestPlayerRunHelper.advance(player).withTimeoutMs(10_000).untilPlayerError()
            } catch (timeout: java.util.concurrent.TimeoutException) {
                throw AssertionError("Timed out waiting for armed read failure: ${failurePlayback()}", timeout)
            }
            awaitMain { runtime.state.value is AppPlaybackState.Failed }
            // A real read error leaves the failed target where it stood.
            assertNear(positionMs, player.currentPosition)
            media.failFromByte = Long.MAX_VALUE
        }

        /** Playback reaches [positionMs], keeps playing from there, and has not failed. */
        suspend fun assertEventuallyNear(positionMs: Long) {
            driver.await("playing near $positionMs", diagnostics = { playback() }) {
                near(positionMs, player.currentPosition) && player.isPlaying
            }
            val arrivedMs = player.currentPosition
            try {
                TestPlayerRunHelper.advance(player).withTimeoutMs(30_000).untilPositionAtLeast(arrivedMs + 1_000)
            } catch (timeout: java.util.concurrent.TimeoutException) {
                throw AssertionError("Timed out waiting for resumed playback progress: ${playback()}", timeout)
            }
            assertNull("player error: ${playback()}", player.playerError)
            assertTrue("not playing: ${playback()}", player.isPlaying)
            assertNear(positionMs, player.currentPosition)
        }

        private fun playback() = "position=${player.currentPosition} playing=${player.isPlaying} " +
            "state=${player.playbackState} error=${player.playerError?.errorCodeName}"

        private fun failurePlayback() = "position=${player.currentPosition} " +
            "buffered=${player.bufferedPosition} state=${player.playbackState} loading=${player.isLoading} " +
            "playWhenReady=${player.playWhenReady} readByte=${media.readPosition} armedByte=${media.failFromByte}"

        val driver = PlaybackTestDriver(scheduler, waitTimeoutMillis = 10_000)

        fun awaitMain(condition: () -> Boolean) = driver.await("recording playback condition", predicate = condition)

        fun settleMain() {
            scheduler.advanceTimeBy(1_000)
            scheduler.runCurrent()
            TestPlayerRunHelper.advance(player).untilPendingCommandsAreFullyHandled()
            scheduler.runCurrent()
        }

        suspend fun awaitRecoveryTimeout() {
            assertNotNull(coordinator.pendingRecordingRecoveryPosition.value)
            RobolectricUtil.runMainLooperUntil({
                scheduler.runCurrent()
                coordinator.pendingRecordingRecoveryPosition.value == null
            }, 10_000, androidx.media3.common.util.Clock.DEFAULT)
        }
    }

    /**
     * Binds the recordings TVHeadend still writes as the SDK does: growing, with an admission
     * that follows the recording's state in the observation the binding was made from.
     */
    private class GrowingRecordingSession(private val session: FakeTvheadendSession) : TvheadendSession by session {
        @OptIn(at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi::class)
        override fun bindRecordingPlayback(
            currentSession: CurrentSessionObservation,
            recordingId: DvrEntryId,
        ): PlaybackBindingResult<PlaybackBinding.Recording> {
            if (!session.isCurrent(currentSession)) return PlaybackBindingResult.ObservationExpired
            if (session.observation.value.dvrEntry(recordingId)?.state != DvrEntryState.RECORDING) {
                return session.bindRecordingPlayback(currentSession, recordingId)
            }
            val lease = object : GrowingRecordingFileLease {
                override val isCurrent: Boolean get() = session.isCurrent(currentSession)
                override suspend fun open(position: Long): RecordingFileResult<GrowingRecordingFileReader> =
                    RecordingFileResult.Failed(at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure.NOT_SUPPORTED)
            }
            return GrowingRecordingBindings.bound({
                val observation = session.observation.value
                if (observation.currentSession !== currentSession) {
                    RecordingPlaybackAdmission.ObservationExpired
                } else when (observation.dvrEntry(recordingId)?.state) {
                    DvrEntryState.RECORDING -> at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory.growingRecordingAdmission(null, RecordingProgressCapability.SUPPORTED)
                    DvrEntryState.COMPLETED -> at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory.completedRecordingAdmission(null, RecordingProgressCapability.SUPPORTED)
                    else -> RecordingPlaybackAdmission.TargetUnavailable
                }
            }, lease)
        }
    }

    /**
     * A 30-minute (or [lengthMs]) mono 8-bit 1 kHz WAV recording: one byte per millisecond. Reads from
     * [failFromByte] on fail as a lost server read does, and no byte past it is served; while
     * [seekable] is false a new extraction announces an unseekable map until [openSeeking], as a
     * progressive file can. The loader asks the player's load control every [LOAD_CHECK_BYTES], so
     * what is loaded stays within the player's buffer ahead of playback instead of Media3's default
     * 1 MiB (17 minutes here) chunk, and a seek minutes ahead always restarts the load.
     */
    private class ControlledRecording {
        @Volatile var failFromByte = Long.MAX_VALUE
        /** The byte offset the most recent read reached, for diagnostics. */
        @Volatile var readPosition = 0L
        @Volatile var seekable = true
        @Volatile var unknownDuration = false
        /** The length a new open of the recording serves, as a growing file's probe reports it. */
        @Volatile var lengthMs = RECORDING_MS.toLong()
        private val held = mutableListOf<Pair<ExtractorOutput, SeekMap>>()

        fun source(item: MediaItem): MediaSource =
            ProgressiveMediaSource.Factory(DataSource.Factory { ControlledDataSource() }, ExtractorsFactory {
                arrayOf<Extractor>(GatedExtractor())
            }).setContinueLoadingCheckIntervalBytes(LOAD_CHECK_BYTES).createMediaSource(item)

        fun openSeeking() {
            val pending = synchronized(held) {
                seekable = true
                held.toList().also { held.clear() }
            }
            pending.forEach { (output, seekMap) -> output.seekMap(seekMap) }
        }

        private inner class ControlledDataSource : BaseDataSource(false) {
            private val bytes = ByteArrayDataSource(if (lengthMs == RECORDING_MS.toLong()) WAV else wav(lengthMs.toInt()))
            private var position = 0L
            override fun open(dataSpec: DataSpec): Long {
                position = dataSpec.position
                return bytes.open(dataSpec)
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val failFrom = failFromByte
                if (position >= failFrom) throw FileNotFoundException("recording read failed")
                val allowed = minOf(length.toLong(), 1_000L, failFrom - position).toInt()
                return bytes.read(buffer, offset, allowed).also { if (it > 0) position += it; readPosition = position }
            }
            override fun getUri(): Uri? = bytes.uri
            override fun close() = bytes.close()
        }

        private inner class GatedExtractor : Extractor {
            private val wav = WavExtractor()
            override fun sniff(input: ExtractorInput): Boolean = wav.sniff(input)
            override fun init(output: ExtractorOutput) = wav.init(object : ExtractorOutput by output {
                override fun seekMap(seekMap: SeekMap) {
                    val gated = synchronized(held) {
                        (!seekable).also { if (it) held += output to seekMap }
                    }
                    output.seekMap(if (gated) SeekMap.Unseekable(if (unknownDuration) C.TIME_UNSET else seekMap.durationUs) else seekMap)
                }
            })
            override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = wav.read(input, seekPosition)
            override fun seek(position: Long, timeUs: Long) = wav.seek(position, timeUs)
            override fun release() = wav.release()
        }
    }

    /** Takes each audio sample once playback reaches it, so a failed read surfaces as it would. */
    private class ConsumingAudioSink : BaseRenderer(C.TRACK_TYPE_AUDIO) {
        private val formatHolder = FormatHolder()
        private val buffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED)
        private var heldTimeUs: Long? = null
        private var ended = false

        override fun getName() = "ConsumingAudioSink"
        override fun supportsFormat(format: Format): Int = RendererCapabilities.create(
            if (MimeTypes.getTrackType(format.sampleMimeType) == C.TRACK_TYPE_AUDIO) {
                C.FORMAT_HANDLED
            } else {
                C.FORMAT_UNSUPPORTED_TYPE
            },
        )
        override fun isReady() = heldTimeUs != null || isSourceReady
        override fun isEnded() = ended
        override fun onPositionReset(positionUs: Long, joining: Boolean, sampleStreamIsResetToKeyFrame: Boolean) {
            heldTimeUs = null
            ended = false
        }
        override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
            while (true) {
                val timeUs = heldTimeUs ?: run {
                    buffer.clear()
                    when (readSource(formatHolder, buffer, SampleStream.FLAG_OMIT_SAMPLE_DATA)) {
                        C.RESULT_FORMAT_READ -> return@run null
                        C.RESULT_BUFFER_READ -> if (buffer.isEndOfStream) { ended = true; return } else buffer.timeUs
                        else -> return
                    }
                } ?: continue
                if (timeUs > positionUs) {
                    heldTimeUs = timeUs
                    return
                }
                heldTimeUs = null
            }
        }
    }

    private companion object {
        val FIRST = DvrEntryId(1)
        val SECOND = DvrEntryId(2)
        const val ONE_MINUTE = 60_000L
        const val FIVE_MINUTES = 5 * 60_000L
        const val TEN_MINUTES = 10 * 60_000L
        const val TWENTY_MINUTES = 20 * 60_000L
        const val TWENTY_NINE_MINUTES = 29 * 60_000L
        const val FORTY_NINE_MINUTES = 49 * 60_000L
        const val FIFTY_EIGHT_MINUTES = 58 * 60_000L
        const val SIXTY_MINUTES = 60 * 60_000L
        const val RECORDING_MS = 30 * 60_000
        const val WAV_HEADER = 44
        const val LOAD_CHECK_BYTES = 10_000 // 10 s of the recording
        val RECORDING_START: Instant = Instant.fromEpochSeconds(1_700_000_000)
        val TS: ByteArray = requireNotNull(RecordingRecoveryPositionTest::class.java
            .getResourceAsStream("/playback/growing-recording.ts")).use { it.readBytes() }
        val TS_SPLIT: Int = (TS.size / 2 / 188) * 188

        fun wav(lengthMs: Int): ByteArray = java.nio.ByteBuffer.allocate(WAV_HEADER + lengthMs)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + lengthMs); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(1_000); putInt(1_000)
                putShort(1); putShort(8)
                put("data".toByteArray()); putInt(lengthMs)
            }.array()

        val WAV: ByteArray = wav(RECORDING_MS)

        fun byteAt(positionMs: Long) = WAV_HEADER + positionMs

        fun near(expectedMs: Long, actualMs: Long) = actualMs in expectedMs..expectedMs + 10_000

        fun assertNear(expectedMs: Long, actualMs: Long) {
            assertTrue("expected about $expectedMs ms but was $actualMs ms", near(expectedMs, actualMs))
        }

        fun recordings(
            entries: List<DvrEntryId>,
            bookmark: kotlin.time.Duration?,
            state: DvrEntryState = DvrEntryState.COMPLETED,
            progressSupported: Boolean = false,
            files: List<DvrRecordingFile>? = null,
        ) = SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Channel One")))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries.map { id ->
                DvrEntry.create(id = id, state = state, title = "Recording $id",
                    channelName = "Recorded Channel", playPosition = bookmark, files = files,
                    folder = if (files != null) emptyList() else null)
            })),
            recordingProgressCapability = if (bookmark != null || progressSupported) {
                RecordingProgressCapability.SUPPORTED
            } else {
                RecordingProgressCapability.UNKNOWN
            },
        )
    }
}
