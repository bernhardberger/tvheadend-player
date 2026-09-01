@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.os.Bundle
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvhplayer.ExternalTargetAcceptanceRule
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class TimeshiftCommandDeviceAcceptanceTest {
    @get:Rule
    val externalTargetAcceptance = ExternalTargetAcceptanceRule()

    @Test
    fun terrestrialTimeshiftCommandsPreserveTargetContinuity() = runAcceptance(TERRESTRIAL)

    @Test
    fun motorvisionTimeshiftCommandsPreserveTargetContinuity() = runAcceptance(MOTORVISION)

    private fun runAcceptance(fixture: ChannelFixture) = runTest(timeout = TEST_TIMEOUT) {
        withContext(Dispatchers.Default) {
            val runtime = withContext(Dispatchers.Main) {
                GlobalContext.get().get<AppPlaybackRuntime>()
            }
            val session = GlobalContext.get().get<TvheadendSession>()
            val observation = awaitCurrentObservation(session)
            val channelState = observation.channelState as ChannelRepositoryState.Current
            val channel = channelState.catalog.channels.single(fixture.matches)

            runFixture(runtime, observation, channel, fixture.label)
        }
    }

    private suspend fun runFixture(
        runtime: AppPlaybackRuntime,
        observation: SessionObservation,
        channel: Channel,
        fixture: String,
    ) = coroutineScope {
        val selection = LivePlaybackSelection(
            currentSession = checkNotNull(observation.currentSession),
            channelId = channel.id,
        )
        val continuityWatch = ContinuityWatch(this, runtime, channel)
        try {
            startTarget(runtime, selection, fixture)
            awaitCurrentTarget(runtime, channel)
            val startupProgress = awaitPlayerProgress(runtime)
            delay(CONTINUITY_SETTLE)
            assertContinuous(fixture, "startup", runtime, channel, continuityWatch.snapshot())
            reportEvidence(
                fixture,
                "startup",
                "READY/+$startupProgress ms",
                runtime,
                runtime.timeshiftState.value,
                continuityWatch.snapshot(),
            )

            withContext(Dispatchers.Main) { runtime.pause() }
            val pauseResult = withContext(Dispatchers.Main) { runtime.pauseTimeshift() }
            assertTrue("$fixture pause=$pauseResult", pauseResult == TimeshiftCommandResult.ACCEPTED)
            awaitServerPaused(runtime, paused = true)
            delay(COMMAND_CONTINUITY_SETTLE)
            assertContinuous(fixture, "pause", runtime, channel, continuityWatch.snapshot())
            reportEvidence(
                fixture,
                "pause",
                pauseResult.name,
                runtime,
                runtime.timeshiftState.value,
                continuityWatch.snapshot(),
            )

            withContext(Dispatchers.Main) { runtime.play() }
            val resumeResult = withContext(Dispatchers.Main) { runtime.resumeTimeshift() }
            assertTrue("$fixture resume=$resumeResult", resumeResult == TimeshiftCommandResult.ACCEPTED)
            awaitServerPaused(runtime, paused = false)
            val resumeProgress = awaitPlayerProgress(runtime)
            delay(COMMAND_CONTINUITY_SETTLE)
            assertContinuous(fixture, "resume", runtime, channel, continuityWatch.snapshot())
            reportEvidence(
                fixture,
                "resume",
                "$resumeResult/+$resumeProgress ms",
                runtime,
                runtime.timeshiftState.value,
                continuityWatch.snapshot(),
            )

            val measuredSeek = awaitMeasuredSeek(runtime)
            assertContinuous(fixture, "seek-ready", runtime, channel, continuityWatch.snapshot())
            reportEvidence(
                fixture,
                "seek-ready",
                "-${measuredSeek.magnitudeMillis} ms",
                runtime,
                runtime.timeshiftState.value,
                continuityWatch.snapshot(),
            )
            val seekResult = withContext(Dispatchers.Main) {
                runtime.seekTimeshift(-measuredSeek.magnitudeMillis)
            }
            assertTrue("$fixture signed-seek=$seekResult", seekResult == TimeshiftCommandResult.ACCEPTED)
            awaitPositionBehindLive(
                runtime,
                measuredSeek.beforeBehindLiveMillis + MINIMUM_SEEK_MOVEMENT_MILLIS,
            )
            val seekProgress = awaitPlayerProgress(runtime)
            delay(COMMAND_CONTINUITY_SETTLE)
            assertContinuous(fixture, "signed seek", runtime, channel, continuityWatch.snapshot())
            reportEvidence(
                fixture,
                "signed-seek",
                "$seekResult/+$seekProgress ms",
                runtime,
                runtime.timeshiftState.value,
                continuityWatch.snapshot(),
            )

            val goLiveResult = withContext(Dispatchers.Main) { runtime.goLive() }
            assertTrue("$fixture go-live=$goLiveResult", goLiveResult == TimeshiftCommandResult.ACCEPTED)
            awaitNearLive(runtime)
            val goLiveProgress = awaitPlayerProgress(runtime)
            delay(COMMAND_CONTINUITY_SETTLE)
            val finalContinuity = continuityWatch.close()
            assertContinuous(fixture, "go live", runtime, channel, finalContinuity)
            reportEvidence(
                fixture,
                "go-live",
                "$goLiveResult/+$goLiveProgress ms",
                runtime,
                runtime.timeshiftState.value,
                finalContinuity,
            )
        } finally {
            continuityWatch.close()
            withContext(Dispatchers.Main) { runtime.stop() }
        }
    }

    private suspend fun startTarget(
        runtime: AppPlaybackRuntime,
        selection: LivePlaybackSelection,
        fixture: String,
    ) {
        val startResult = withContext(Dispatchers.Main) { runtime.playLive(selection) }
        reportEvidence(
            fixture,
            "start",
            startResult?.name ?: "null",
            runtime,
            null,
            ContinuityCounts(),
        )
        assertTrue("$fixture live target did not start: $startResult", startResult == PlaybackTargetResult.STARTED)
        val timeshift = awaitTimeshift(runtime)
        reportEvidence(
            fixture,
            "available",
            "AVAILABLE",
            runtime,
            timeshift,
            ContinuityCounts(),
        )
    }

    private suspend fun awaitCurrentObservation(session: TvheadendSession): SessionObservation =
        withTimeout(CONNECTION_TIMEOUT) {
            session.observation.first { observation ->
                observation.currentSession != null &&
                    observation.channelState is ChannelRepositoryState.Current
            }
        }

    private suspend fun awaitTimeshift(runtime: AppPlaybackRuntime): LiveTimeshiftState.Available =
        withTimeout(STATE_TIMEOUT) {
            runtime.timeshiftState.first { it is LiveTimeshiftState.Available }
                as LiveTimeshiftState.Available
        }

    private suspend fun awaitCurrentTarget(runtime: AppPlaybackRuntime, channel: Channel) {
        withTimeout(STATE_TIMEOUT) {
            runtime.activeTarget.first { target -> target == AppPlaybackTarget.Live(channel.id) }
        }
    }

    private suspend fun awaitServerPaused(runtime: AppPlaybackRuntime, paused: Boolean) {
        withTimeout(STATE_TIMEOUT) {
            runtime.timeshiftState.first { state ->
                state is LiveTimeshiftState.Available && state.serverPaused == paused
            }
        }
    }

    private suspend fun awaitPositionBehindLive(runtime: AppPlaybackRuntime, minimumMillis: Long) {
        withTimeout(STATE_TIMEOUT) {
            runtime.timeshiftState.first { state ->
                state.positionBehindLiveMillisOrNull()?.let { it >= minimumMillis } == true
            }
        }
    }

    private suspend fun awaitNearLive(runtime: AppPlaybackRuntime) {
        withTimeout(STATE_TIMEOUT) {
            runtime.timeshiftState.first { state ->
                state.positionBehindLiveMillisOrNull()?.let { it <= NEAR_LIVE_MILLIS } == true
            }
        }
    }

    private suspend fun awaitPlayerProgress(runtime: AppPlaybackRuntime): Long =
        withTimeout(STATE_TIMEOUT) {
            var readyPosition: Long? = null
            var progressMillis: Long? = null
            while (progressMillis == null) {
                val sample = withContext(Dispatchers.Main) {
                    PlayerSample(
                        playbackState = runtime.player.playbackState,
                        playWhenReady = runtime.player.playWhenReady,
                        isPlaying = runtime.player.isPlaying,
                        positionMillis = runtime.player.currentPosition,
                    )
                }
                if (
                    sample.playbackState == Player.STATE_READY &&
                    sample.playWhenReady &&
                    sample.isPlaying
                ) {
                    val baseline = readyPosition
                    if (baseline == null) {
                        readyPosition = sample.positionMillis
                    } else if (sample.positionMillis - baseline >= MINIMUM_PLAYER_PROGRESS_MILLIS) {
                        progressMillis = sample.positionMillis - baseline
                    }
                } else {
                    readyPosition = null
                }
                delay(PLAYER_POLL_INTERVAL)
            }
            progressMillis
        }

    private suspend fun awaitMeasuredSeek(runtime: AppPlaybackRuntime): MeasuredSeek =
        withTimeout(BUFFER_HISTORY_TIMEOUT) {
            var measuredSeek: MeasuredSeek? = null
            runtime.timeshiftState.first { state ->
                val available = state as? LiveTimeshiftState.Available ?: return@first false
                val bufferedMillis = available.bufferedDuration
                    ?.inWholeMilliseconds
                    ?: return@first false
                val behindLiveMillis = available.positionBehindLive
                    ?.inWholeMilliseconds
                    ?: return@first false
                if (bufferedMillis - behindLiveMillis < SEEK_BACK_MILLIS + SEEK_BUFFER_MARGIN_MILLIS) {
                    return@first false
                }
                measuredSeek = MeasuredSeek(
                    magnitudeMillis = SEEK_BACK_MILLIS,
                    beforeBehindLiveMillis = behindLiveMillis,
                )
                true
            }
            checkNotNull(measuredSeek)
        }

    private fun assertContinuous(
        fixture: String,
        stage: String,
        runtime: AppPlaybackRuntime,
        channel: Channel,
        continuity: ContinuityCounts,
    ) {
        val failures = mutableListOf<String>()
        continuity.requireContinuous(stage, failures)
        if (runtime.activeTarget.value != AppPlaybackTarget.Live(channel.id)) {
            failures += "$stage current target changed"
        }
        assertTrue("$fixture continuity failures: ${failures.joinToString()}", failures.isEmpty())
    }

    private suspend fun reportEvidence(
        fixture: String,
        stage: String,
        result: String,
        runtime: AppPlaybackRuntime,
        state: LiveTimeshiftState?,
        continuity: ContinuityCounts,
    ) {
        val playerState = withContext(Dispatchers.Main) { runtime.player.playbackState }
        val playerPlayIntent = withContext(Dispatchers.Main) { runtime.player.playWhenReady }
        val available = state as? LiveTimeshiftState.Available
        val evidence = Bundle().apply {
            putString("fixture", fixture)
            putString("stage", stage)
            putString("result", result)
            putString("timeshift", state?.javaClass?.simpleName ?: "not-observed")
            putString("grantMs", available?.grantedPeriod?.inWholeMilliseconds?.toString() ?: "unknown")
            putString("bufferMs", available?.bufferedDuration?.inWholeMilliseconds?.toString() ?: "unknown")
            putString(
                "behindLiveMs",
                available?.positionBehindLive?.inWholeMilliseconds?.toString() ?: "unknown",
            )
            putString("serverPaused", available?.serverPaused?.toString() ?: "unknown")
            putString("playerState", playerState.playerStateName())
            putString("playIntent", playerPlayIntent.toString())
            putString("recoveries", continuity.recoveries.toString())
            putString("targetChanges", continuity.targetChanges.toString())
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(EVIDENCE_STATUS_CODE, evidence)
    }

    private class ContinuityWatch(
        scope: CoroutineScope,
        runtime: AppPlaybackRuntime,
        expectedChannel: Channel,
    ) {
        private val recoveries = AtomicInteger()
        private val targetChanges = AtomicInteger()
        private val expectedTargetObserved = AtomicBoolean()
        private val stateJob: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runtime.state.collect { state ->
                if (state is AppPlaybackState.Recovering) recoveries.incrementAndGet()
            }
        }
        private val targetJob: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runtime.activeTarget.collect { target ->
                val expectedTarget = AppPlaybackTarget.Live(expectedChannel.id)
                if (target == expectedTarget) {
                    expectedTargetObserved.set(true)
                } else if (target != null || expectedTargetObserved.get()) {
                    targetChanges.incrementAndGet()
                }
            }
        }

        fun snapshot(): ContinuityCounts = ContinuityCounts(
            recoveries = recoveries.get(),
            targetChanges = targetChanges.get(),
        )

        suspend fun close(): ContinuityCounts {
            stateJob.cancelAndJoin()
            targetJob.cancelAndJoin()
            return snapshot()
        }
    }

    private data class ContinuityCounts(
        val recoveries: Int = 0,
        val targetChanges: Int = 0,
    ) {
        fun requireContinuous(stage: String, failures: MutableList<String>) {
            if (recoveries != 0) failures += "$stage recoveries=$recoveries"
            if (targetChanges != 0) failures += "$stage targetChanges=$targetChanges"
        }
    }

    private data class PlayerSample(
        val playbackState: Int,
        val playWhenReady: Boolean,
        val isPlaying: Boolean,
        val positionMillis: Long,
    )

    private data class MeasuredSeek(
        val magnitudeMillis: Long,
        val beforeBehindLiveMillis: Long,
    )

    private data class ChannelFixture(
        val label: String,
        val matches: (Channel) -> Boolean,
    )

    private companion object {
        const val SEEK_BACK_MILLIS = 30_000L
        const val SEEK_BUFFER_MARGIN_MILLIS = 5_000L
        const val MINIMUM_SEEK_MOVEMENT_MILLIS = 20_000L
        const val MINIMUM_PLAYER_PROGRESS_MILLIS = 1_000L
        const val NEAR_LIVE_MILLIS = 5_000L
        const val EVIDENCE_STATUS_CODE = 2
        val TEST_TIMEOUT = 5.minutes
        val CONNECTION_TIMEOUT = 1.minutes
        val STATE_TIMEOUT = 30.seconds
        val BUFFER_HISTORY_TIMEOUT = 1.minutes
        val CONTINUITY_SETTLE = 2.seconds
        val COMMAND_CONTINUITY_SETTLE = 8.seconds
        const val PLAYER_POLL_INTERVAL = 100L

        val TERRESTRIAL = ChannelFixture("terrestrial") { channel -> channel.number == 1L }
        val MOTORVISION = ChannelFixture("motorvision") { channel ->
            channel.name?.contains("motorvision", ignoreCase = true) == true
        }
    }
}

private fun LiveTimeshiftState?.positionBehindLiveMillisOrNull(): Long? =
    (this as? LiveTimeshiftState.Available)?.positionBehindLive?.inWholeMilliseconds

private fun Int.playerStateName(): String = when (this) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "UNKNOWN"
}
