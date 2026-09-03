@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoActivityResumedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.ExternalTargetAcceptanceRule
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.MainActivity
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class ForegroundPlaybackLifecycleDeviceAcceptanceTest {
    @get:Rule
    val externalTargetAcceptance = ExternalTargetAcceptanceRule()

    @Test
    fun liveHomeReleasesAndReturnRetunesExactlyOnce() = runAcceptance {
        val fixture = fixture()
        withActivity { runtime ->
            startLive(runtime, fixture.observation, fixture.channels.first())
            awaitPlayerProgress(runtime)

            moveTaskToBackground()
            awaitLiveReleased(runtime)
            reportEvidence("live", "home", runtime)
            delay(BACKGROUND_OBSERVATION)

            val targetWatch = TargetChangeWatch(this, runtime)
            returnToApp()
            awaitTarget(runtime, AppPlaybackTarget.Live(fixture.channels.first().id))
            awaitPlayerProgress(runtime)
            delay(FOREGROUND_SETTLE)
            assertEquals("live return retune count", 1, targetWatch.close())
            reportEvidence("live", "return", runtime)
        }
    }

    @Test
    fun recordingHomePausesAndResumesSameTargetAtExactPosition() = runAcceptance {
        val fixture = fixture()
        val expectedTarget = AppPlaybackTarget.Recording(fixture.recording.id)
        withActivity { runtime ->
            startRecording(runtime, fixture.observation, fixture.recording)
            awaitPlayerProgress(runtime)
            val targetWatch = TargetChangeWatch(this, runtime)

            moveTaskToBackground()
            awaitPaused(runtime)
            assertEquals(expectedTarget, runtime.activeTarget.value)
            val pausedPosition = playerPosition(runtime)
            delay(BACKGROUND_OBSERVATION)
            val stablePosition = playerPosition(runtime)
            assertTrue(
                "recording position moved while backgrounded: $pausedPosition -> $stablePosition",
                abs(stablePosition - pausedPosition) <= POSITION_STABILITY_TOLERANCE_MILLIS,
            )
            reportEvidence("recording", "home", runtime, positionMillis = stablePosition)

            returnToApp()
            awaitTarget(runtime, expectedTarget)
            val resumedPosition = awaitResumedPosition(runtime)
            assertTrue(
                "recording resumed at a different position: $stablePosition -> $resumedPosition",
                abs(resumedPosition - stablePosition) <= RESUME_POSITION_TOLERANCE_MILLIS,
            )
            awaitProgressFrom(runtime, resumedPosition)
            assertEquals("recording target transitions", 0, targetWatch.close())
            reportEvidence("recording", "return", runtime, positionMillis = resumedPosition)
        }
    }

    @Test
    fun foregroundRetunesTheReplacementChannelNotTheStaleChannel() = runAcceptance {
        val fixture = fixture(channelCount = 2)
        val initial = fixture.channels[0]
        val replacement = fixture.channels[1]
        withActivity { runtime ->
            startLive(runtime, fixture.observation, initial)
            awaitPlayerProgress(runtime)

            moveTaskToBackground()
            awaitLiveReleased(runtime)
            startLive(runtime, fixture.observation, replacement, awaitActive = false)
            awaitLiveReleased(runtime)
            val targetWatch = TargetChangeWatch(this, runtime)
            reportEvidence("replacement", "home", runtime)

            returnToApp()
            awaitTarget(runtime, AppPlaybackTarget.Live(replacement.id))
            awaitPlayerProgress(runtime)
            delay(FOREGROUND_SETTLE)
            assertEquals("replacement return retune count", 1, targetWatch.close())
            assertFalse(
                "stale channel was restored",
                runtime.activeTarget.value == AppPlaybackTarget.Live(initial.id),
            )
            reportEvidence("replacement", "return", runtime)
        }
    }

    @Test
    fun rootBackStopsLiveTarget() = runAcceptance {
        val fixture = fixture()
        withActivity { runtime ->
            reportEvidence("live", "root-ready", runtime)
            startLive(runtime, fixture.observation, fixture.channels.first())
            reportEvidence("live", "root-started", runtime)
            awaitPlayerProgress(runtime)
            reportEvidence("live", "root-playing", runtime)
            delay(FOREGROUND_SETTLE)
            pressBackUntilStopped(runtime)
            reportEvidence("live", "root-back", runtime)
        }
    }

    @Test
    fun rootBackStopsRecordingTarget() = runAcceptance {
        val fixture = fixture()
        withActivity { runtime ->
            reportEvidence("recording", "root-ready", runtime)
            requireStage("recording root start") {
                startRecording(runtime, fixture.observation, fixture.recording)
            }
            reportEvidence("recording", "root-started", runtime)
            requireStage("recording root progress") { awaitPlayerProgress(runtime) }
            reportEvidence("recording", "root-playing", runtime)
            delay(FOREGROUND_SETTLE)
            requireStage("recording root stop") { pressBackUntilStopped(runtime) }
            reportEvidence("recording", "root-back", runtime)
        }
    }

    private suspend fun <T> requireStage(stage: String, block: suspend () -> T): T = try {
        block()
    } catch (error: TimeoutCancellationException) {
        throw AssertionError("$stage timed out", error)
    }

    private fun runAcceptance(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        runTest(timeout = TEST_TIMEOUT) {
            withContext(Dispatchers.Default) {
                block()
            }
        }

    private suspend fun kotlinx.coroutines.CoroutineScope.withActivity(
        block: suspend kotlinx.coroutines.CoroutineScope.(AppPlaybackRuntime) -> Unit,
    ) {
        val runtime = GlobalContext.get().get<AppPlaybackRuntime>()
        val settings = GlobalContext.get().get<UiSettingsStore>()
        val autoStartPlayback = settings.settings.first().autoStartPlayback
        settings.setAutoStartPlayback(false)
        try {
            withContext(Dispatchers.Main) { runtime.stop() }
            returnToApp(resetTask = true)
            awaitMainActivity(resumed = true)
            block(runtime)
        } finally {
            try {
                withContext(Dispatchers.Main) { runtime.stop() }
                moveTaskToBackgroundIfResumed()
                awaitMainActivity(resumed = false)
            } finally {
                settings.setAutoStartPlayback(autoStartPlayback)
            }
        }
    }

    private suspend fun awaitMainActivity(resumed: Boolean) {
        withTimeout(STATE_TIMEOUT) {
            while (true) {
                val isResumed = withContext(Dispatchers.Main) {
                    ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)
                        .any { it is MainActivity }
                }
                if (isResumed == resumed) return@withTimeout
                delay(PLAYER_POLL_INTERVAL)
            }
        }
    }

    private suspend fun fixture(channelCount: Int = 1): AcceptanceFixture {
        val session = GlobalContext.get().get<TvheadendSession>()
        val observation = withTimeout(CONNECTION_TIMEOUT) {
            session.observation.first { value ->
                value.currentSession != null &&
                    value.channelState is ChannelRepositoryState.Current &&
                    value.dvrState is DvrRepositoryState.Current
            }
        }
        val channels = (observation.channelState as ChannelRepositoryState.Current)
            .catalog.channels
            .filterNot { it.name.containsSensitivePlaybackContent() }
            .distinctBy { it.id }
            .take(channelCount)
        assertEquals("live channel fixture count", channelCount, channels.size)
        val recording = (observation.dvrState as DvrRepositoryState.Current)
            .snapshot.entries
            .filter { entry ->
                entry.state == DvrEntryState.COMPLETED &&
                    !entry.containsSensitivePlaybackContent() &&
                    entry.files.orEmpty().any { (it.sizeBytes ?: 0L) > 0L }
            }
            .maxWithOrNull(
                compareBy<DvrEntry> { it.playPosition?.inWholeMilliseconds ?: 0L }
                    .thenBy { entry -> entry.files.orEmpty().sumOf { it.sizeBytes ?: 0L } },
            )
        return AcceptanceFixture(
            observation = observation,
            channels = channels,
            recording = requireNotNull(recording) { "no completed recording fixture with a non-empty file" },
        )
    }

    private suspend fun startLive(
        runtime: AppPlaybackRuntime,
        observation: SessionObservation,
        channel: Channel,
        awaitActive: Boolean = true,
    ) {
        val result = withContext(Dispatchers.Main) {
            runtime.playLive(
                LivePlaybackSelection(
                    currentSession = checkNotNull(observation.currentSession),
                    channelId = channel.id,
                ),
            )
        }
        assertEquals("live target start", PlaybackTargetResult.STARTED, result)
        if (awaitActive) awaitTarget(runtime, AppPlaybackTarget.Live(channel.id))
    }

    private fun DvrEntry.containsSensitivePlaybackContent(): Boolean = listOf(
        channelName,
        title,
        subtitle,
        summary,
        description,
    ).any { it.containsSensitivePlaybackContent() }

    private fun String?.containsSensitivePlaybackContent(): Boolean {
        val normalized = orEmpty().lowercase().filter(Char::isLetterOrDigit)
        return "servustv" in normalized || "motogp" in normalized
    }

    private suspend fun startRecording(
        runtime: AppPlaybackRuntime,
        observation: SessionObservation,
        recording: DvrEntry,
    ) {
        val result = withContext(Dispatchers.Main) {
            runtime.playRecording(
                RecordingPlaybackSelection(
                    currentSession = checkNotNull(observation.currentSession),
                    recordingId = recording.id,
                ),
                RecordingPlaybackStart.START_OVER,
            )
        }
        assertEquals("recording target start", PlaybackTargetResult.STARTED, result)
        awaitTarget(runtime, AppPlaybackTarget.Recording(recording.id))
    }

    private suspend fun awaitTarget(runtime: AppPlaybackRuntime, target: AppPlaybackTarget) {
        withTimeout(STATE_TIMEOUT) {
            runtime.activeTarget.first { it == target }
        }
    }

    private suspend fun awaitLiveReleased(runtime: AppPlaybackRuntime) {
        withTimeout(STATE_TIMEOUT) {
            runtime.activeTarget.first { it == null }
        }
        withTimeout(STATE_TIMEOUT) {
            runtime.timeshiftState.first { it == LiveTimeshiftState.Unavailable }
        }
        val sample = playerSample(runtime)
        assertFalse("live player remained audible after HOME", sample.isPlaying)
        assertEquals("live player did not stop", Player.STATE_IDLE, sample.playbackState)
    }

    private suspend fun awaitStopped(runtime: AppPlaybackRuntime) {
        withTimeout(STATE_TIMEOUT) {
            runtime.activeTarget.first { it == null }
        }
        val sample = playerSample(runtime)
        assertFalse("player remained active after root Back", sample.isPlaying)
        assertEquals("player did not stop after root Back", Player.STATE_IDLE, sample.playbackState)
    }

    private suspend fun pressBackUntilStopped(runtime: AppPlaybackRuntime) {
        repeat(MAX_ROOT_BACK_ACTIONS) {
            try {
                pressBack()
            } catch (_: NoActivityResumedException) {
                awaitStopped(runtime)
                return
            }
            val stopped = withTimeoutOrNull(ROOT_BACK_SETTLE) {
                runtime.activeTarget.first { it == null }
            } != null
            if (stopped) {
                awaitStopped(runtime)
                return
            }
        }
        awaitStopped(runtime)
    }

    private suspend fun awaitPaused(runtime: AppPlaybackRuntime) {
        withTimeout(STATE_TIMEOUT) {
            while (playerSample(runtime).playWhenReady) delay(PLAYER_POLL_INTERVAL)
        }
        assertFalse("recording remained audible after HOME", playerSample(runtime).isPlaying)
    }

    private suspend fun awaitPlayerProgress(runtime: AppPlaybackRuntime): Long =
        withTimeout(STATE_TIMEOUT) {
            var baseline: Long? = null
            while (true) {
                val sample = playerSample(runtime)
                if (
                    sample.playbackState == Player.STATE_READY &&
                    sample.playWhenReady &&
                    sample.isPlaying
                ) {
                    val start = baseline
                    if (start == null) {
                        baseline = sample.positionMillis
                    } else if (sample.positionMillis - start >= MINIMUM_PROGRESS_MILLIS) {
                        return@withTimeout sample.positionMillis - start
                    }
                } else {
                    baseline = null
                }
                delay(PLAYER_POLL_INTERVAL)
            }
            error("unreachable")
        }

    private suspend fun awaitProgressFrom(runtime: AppPlaybackRuntime, positionMillis: Long) {
        withTimeout(STATE_TIMEOUT) {
            while (true) {
                val sample = playerSample(runtime)
                if (
                    sample.playbackState == Player.STATE_READY &&
                    sample.playWhenReady &&
                    sample.isPlaying &&
                    sample.positionMillis - positionMillis >= MINIMUM_PROGRESS_MILLIS
                ) {
                    return@withTimeout
                }
                delay(PLAYER_POLL_INTERVAL)
            }
        }
    }

    private suspend fun awaitResumedPosition(runtime: AppPlaybackRuntime): Long =
        withTimeout(STATE_TIMEOUT) {
            while (true) {
                val sample = playerSample(runtime)
                if (
                    sample.playbackState == Player.STATE_READY &&
                    sample.playWhenReady &&
                    sample.isPlaying
                ) {
                    return@withTimeout sample.positionMillis
                }
                delay(PLAYER_POLL_INTERVAL)
            }
            error("unreachable")
        }

    private suspend fun playerPosition(runtime: AppPlaybackRuntime): Long =
        withContext(Dispatchers.Main) { runtime.player.currentPosition }

    private suspend fun playerSample(runtime: AppPlaybackRuntime): PlayerSample =
        withContext(Dispatchers.Main) {
            PlayerSample(
                playbackState = runtime.player.playbackState,
                playWhenReady = runtime.player.playWhenReady,
                isPlaying = runtime.player.isPlaying,
                positionMillis = runtime.player.currentPosition,
            )
        }

    private fun moveTaskToBackground() {
        var moved = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = requireNotNull(resumedMainActivity())
            // The app can own HOME, so move its task back to exercise HOME's lifecycle transition.
            moved = activity.moveTaskToBack(true)
        }
        assertTrue("MainActivity task did not move to the background", moved)
    }

    private fun moveTaskToBackgroundIfResumed() {
        var moved: Boolean? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            moved = resumedMainActivity()?.moveTaskToBack(true)
        }
        assertTrue("Resumed MainActivity task did not move to the background", moved != false)
    }

    private fun resumedMainActivity(): MainActivity? =
        ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>()
            .singleOrNull()

    private fun returnToApp(resetTask: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = requireNotNull(
            context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName),
        ).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or if (resetTask) {
                Intent.FLAG_ACTIVITY_CLEAR_TASK
            } else {
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
        )
        context.startActivity(intent)
    }

    private suspend fun reportEvidence(
        fixture: String,
        stage: String,
        runtime: AppPlaybackRuntime,
        positionMillis: Long? = null,
    ) {
        val sample = playerSample(runtime)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            EVIDENCE_STATUS_CODE,
            Bundle().apply {
                putString("fixture", fixture)
                putString("stage", stage)
                putString("target", runtime.activeTarget.value?.javaClass?.simpleName ?: "none")
                putString("playerState", sample.playbackState.toString())
                putString("playIntent", sample.playWhenReady.toString())
                putString("isPlaying", sample.isPlaying.toString())
                putString("positionMs", (positionMillis ?: sample.positionMillis).toString())
            },
        )
    }

    private class TargetChangeWatch(
        scope: kotlinx.coroutines.CoroutineScope,
        runtime: AppPlaybackRuntime,
    ) {
        private val changes = AtomicInteger()
        private val job: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runtime.activeTarget.drop(1).collect { target ->
                if (target != null) changes.incrementAndGet()
            }
        }

        suspend fun close(): Int {
            job.cancelAndJoin()
            return changes.get()
        }
    }

    private data class AcceptanceFixture(
        val observation: SessionObservation,
        val channels: List<Channel>,
        val recording: DvrEntry,
    )

    private data class PlayerSample(
        val playbackState: Int,
        val playWhenReady: Boolean,
        val isPlaying: Boolean,
        val positionMillis: Long,
    )

    private companion object {
        const val EVIDENCE_STATUS_CODE = 2
        const val PLAYER_POLL_INTERVAL = 100L
        const val MINIMUM_PROGRESS_MILLIS = 1_000L
        const val POSITION_STABILITY_TOLERANCE_MILLIS = 250L
        const val RESUME_POSITION_TOLERANCE_MILLIS = 1_000L
        const val MAX_ROOT_BACK_ACTIONS = 6
        val TEST_TIMEOUT = 8.minutes
        val CONNECTION_TIMEOUT = 1.minutes
        val STATE_TIMEOUT = 1.minutes
        val BACKGROUND_OBSERVATION = 5.seconds
        val FOREGROUND_SETTLE = 2.seconds
        val ROOT_BACK_SETTLE = 2.seconds
    }
}
