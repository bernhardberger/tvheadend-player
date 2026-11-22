package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.PlaybackBindingResult
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.AudioTrackChoice
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class AutomaticAudioRuntimeTest : AudioInterruptionFixture() {
    @Test fun recordingAudioSurvivesStopAndContinueWithoutChangingLive() = exercise {
        useChoiceProfile()
        recording()
        val chosen = installAudio("en")
        runtime.selectAudio(TrackSelectionOverride(chosen, listOf(0)))
        settle()
        stopPlayback()
        recording(start = RecordingPlaybackStart.RESUME)
        val resumed = installAudio("en")
        assertSame(resumed, audioOverride()?.mediaTrackGroup)
        recording(id = 2)
        installAudio("en")
        assertNull(audioOverride())
        live(timeshift = false)
        installAudio("en")
        assertNull(audioOverride())
        restartPlaybackRuntime()
        recording()
        val restarted = installAudio("en")
        assertSame(restarted, audioOverride()?.mediaTrackGroup)
        stopPlayback()
        useChoiceProfile("other-profile")
        recording()
        installAudio("en")
        assertNull(audioOverride())
    }

    @Test fun recordingInheritsLiveAudioUntilItChoosesAutomatic() = exercise {
        useChoiceProfile()
        settings.audioChoices.set("test-profile", 1, english)
        recording(id = 2)
        val inherited = installAudio("en", channelCount = 6)
        assertSame(inherited, audioOverride()?.mediaTrackGroup)
        // A later parameters notification must not save the looser match as a manual choice.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        settle()
        assertNull(settings.recordingAudioChoices.read("test-profile", 2))
        val german = english.copy(language = "de")
        settings.audioChoices.set("test-profile", 1, german)
        recording(id = 2)
        val updated = installAudio("de")
        assertSame(updated, audioOverride()?.mediaTrackGroup)
        runtime.useAutomaticAudio()
        settle()
        restartPlaybackRuntime()
        recording(id = 2)
        installAudio("de")
        assertTrue(runtime.audioAutomatic.value)
        assertEquals(german, settings.audioChoices.read("test-profile", 1))
    }

    @Test fun recordingStartSettlesWhenTheObservationExpiresDuringChoiceLoad() = exercise {
        assertChoiceLoadExpiry { replaceSession() }
        assertChoiceLoadExpiry { useChoiceProfile("other-profile") }
    }

    private suspend fun Fixture.assertChoiceLoadExpiry(expire: suspend Fixture.() -> Unit) {
        useChoiceProfile()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        beforeSettingsRead = {
            if (runtime.state.value == AppPlaybackState.Starting && entered.complete(Unit)) {
                release.await()
            }
        }
        val selection = requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(2)))
        val install = scope.async { runtime.playRecording(selection, RecordingPlaybackStart.START_OVER) }
        await { entered.isCompleted }
        expire()
        release.complete(Unit)
        await { install.isCompleted }
        assertEquals(PlaybackTargetResult.NOT_READY, install.await())
        val failure = runtime.state.value as? AppPlaybackState.Failed
        assertEquals(AppPlaybackFailureReason.RECORDING_READ_FAILED, failure?.reason)
        assertEquals(RecordingPlaybackAdmission.ObservationExpired, runtime.recordingAdmission.value)
        assertNull(runtime.activeTarget.value)
    }

    @OptIn(SubscriptionInfrastructureApi::class)
    @Test fun queuedAutomaticChoiceAppliesAfterAContinuingRetune() = exercise {
        live(timeshift = false)
        installManualAudio()
        settle()
        assertNotNull(settings.audioChoices.read("test-profile", 1))
        replaceSession()
        var choice: Job? = null
        beforeLiveBinding = { choice = runtime.useAutomaticAudio() }
        val retune = scope.async { runtime.retuneAfterReconnect() }
        await { connection.subscribeCount == 2 }
        startSubscription(connection.awaitCollectionRegistered())
        await { retune.isCompleted && choice?.isCompleted == true }
        assertEquals(PlaybackTargetResult.STARTED, retune.await())
        assertTrue(runtime.audioAutomatic.value)
        assertNull(settings.audioChoices.read("test-profile", 1))
    }

    @Test fun queuedAutomaticChoiceDoesNotFollowASameIdRecordingOnAnotherProfile() = exercise {
        useChoiceProfile()
        recording()
        val group = installAudio("en")
        runtime.selectAudio(TrackSelectionOverride(group, listOf(0))).join()
        val release = CompletableDeferred<Unit>()
        val blocker = scope.launch { commands.serialize(onClosed = {}) { release.await() } }
        scheduler.runCurrent()
        val selection = requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1)))
        val replacement = scope.async { runtime.playRecording(selection, RecordingPlaybackStart.START_OVER) }
        scheduler.runCurrent()
        val stale = runtime.useAutomaticAudio()
        assertFalse(stale.isCompleted)
        useChoiceProfile("other-profile")
        settings.recordingAudioChoices.set("other-profile", 1, english)
        release.complete(Unit)
        await { blocker.isCompleted && replacement.isCompleted && stale.isCompleted }
        assertEquals(PlaybackTargetResult.STARTED, replacement.await())
        val successor = installAudio("en")
        assertSame(successor, audioOverride()?.mediaTrackGroup)
        assertEquals(english, settings.recordingAudioChoices.read("other-profile", 1))
    }

    @Test fun explicitRecordingAudioPickIsSavedWhenItChangesParameters() = exercise {
        useChoiceProfile()
        settings.audioChoices.set("test-profile", 1, english)
        recording(id = 2)
        val group = installAudio("en", channelCount = 6)
        assertSame(group, audioOverride()?.mediaTrackGroup)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        assertNull(audioOverride())
        runtime.selectAudio(TrackSelectionOverride(group, listOf(0)))
        settle()
        assertEquals(english.copy(channelCount = 6), settings.recordingAudioChoices.read("test-profile", 2))
    }

    @OptIn(FakePlaybackApi::class)
    @Test fun failedRestartOfTheSameRecordingKeepsItsCurrentChoice() = exercise {
        useChoiceProfile()
        settings.audioChoices.set("test-profile", 1, english)
        recording(id = 2)
        val current = installAudio("en")
        assertSame(current, audioOverride()?.mediaTrackGroup)
        reportedPlaybackState = Player.STATE_READY
        settings.audioChoices.set("test-profile", 1, english.copy(language = "de"))
        session.scriptRecordingPlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val selection = requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(2)))
        val install = scope.async { runtime.playRecording(selection, RecordingPlaybackStart.START_OVER) }
        await { install.isCompleted }
        assertEquals(PlaybackTargetResult.TARGET_UNAVAILABLE, install.await())
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(2)), runtime.activeTarget.value)
        assertSame(current, audioOverride()?.mediaTrackGroup)
    }

    private val english = AudioTrackChoice("en", MimeTypes.AUDIO_AAC, 0, 2, 48000)

    private fun Fixture.installAudio(language: String, channelCount: Int = 2): TrackGroup {
        val group = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setLanguage(language).setChannelCount(channelCount).setSampleRate(48000).build())
        audioTracks = Tracks(listOf(Tracks.Group(group, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))))
        signalTracksChanged()
        return group
    }

    private fun Fixture.audioOverride() =
        player.trackSelectionParameters.overrides.values.singleOrNull { it.type == C.TRACK_TYPE_AUDIO }

    @Test fun queuedAutomaticChoiceSurvivesSheetCancellationAndPublishesOverrideState() = runTest {
        val work = StandardTestDispatcher(testScheduler)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        val session = FakeTvheadendSession()
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings,
            work,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        val player = ExoPlayer.Builder(context).setClock(FakeClock(false)).build()
        val runtime = AppPlaybackRuntime(player, session, createTvheadendPlaybackCoordinator(player, workDispatcher = work),
            settings, profiles, backgroundScope, TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None,
            PlaybackRuntimePolicy.fromPlayerSettings(),
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
        val sheetScope = CoroutineScope(coroutineContext + Job())
        // Hold the existing command serializer without adding a production test seam.
        val commands = AppPlaybackRuntime::class.java.getDeclaredField("targetCommands").let {
            it.isAccessible = true
            it.get(runtime) as PlaybackTargetCommandSerialization
        }
        val releaseCommand = CompletableDeferred<Unit>()
        try {
            runCurrent()
            assertTrue(runtime.audioAutomatic.value)
            val group = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build())
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .addOverride(TrackSelectionOverride(group, listOf(0)))
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            assertFalse(runtime.audioAutomatic.value)
            val blocker = launch { commands.serialize(onClosed = {}) { releaseCommand.await() } }
            runCurrent()
            lateinit var choice: Job
            sheetScope.launch { choice = runtime.useAutomaticAudio() }
            runCurrent()
            assertFalse(choice.isCompleted)
            sheetScope.cancel()
            releaseCommand.complete(Unit)
            blocker.join()
            PlaybackTestDriver(testScheduler).awaitCurrent("queued automatic choice completed") { choice.isCompleted }
            choice.join()
            assertFalse(choice.isCancelled)
            assertTrue(runtime.audioAutomatic.value)
            assertTrue(player.trackSelectionParameters.overrides.isEmpty())
            assertTrue(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))
        } finally {
            releaseCommand.complete(Unit)
            sheetScope.cancel()
            runtime.detach()
            player.release()
            session.shutdown()
        }
    }
}
