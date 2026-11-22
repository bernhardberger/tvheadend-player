@file:androidx.media3.common.util.UnstableApi
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.settings.SubtitleChoice
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
internal class SubtitleSelectionRuntimeTest : AudioInterruptionFixture() {
    @Test fun explicitTrackReturnsOnlyOnItsChannelWithNewGroupIdentity() = exercise {
        useChoiceProfile()
        live(timeshift = false)
        select(installSubtitleTracks("de", "en")[1])
        settle()
        assertEquals(english, settings.subtitleChoices.read("test-profile", 1))
        player.trackSelectionParameters = trackPreferences(player.trackSelectionParameters, PlayerSettings(subtitleLanguage = "de"))
        settle()
        assertNotNull(textOverride())
        assertEquals(english, settings.subtitleChoices.read("test-profile", 1))
        live(timeshift = false, channel = 2)
        installSubtitleTracks("de", "en")
        assertTrue(runtime.subtitlesDefault.value)
        live(timeshift = false)
        val returned = installSubtitleTracks("de", "en")[1]
        assertSame(returned, textOverride()?.mediaTrackGroup)
        assertFalse(runtime.subtitlesDefault.value)
    }

    @Test fun storedOffReturnsAndNeverLeaksIntoAnotherChannelOrRecording() = exercise {
        useChoiceProfile()
        live(timeshift = false)
        installSubtitleTracks("en")
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        settle()
        assertEquals(SubtitleChoice.Off, settings.subtitleChoices.read("test-profile", 1))
        player.trackSelectionParameters = trackPreferences(player.trackSelectionParameters, PlayerSettings(subtitleLanguage = "de"))
        settle()
        assertTrue(textDisabled())
        assertEquals(SubtitleChoice.Off, settings.subtitleChoices.read("test-profile", 1))
        live(timeshift = false, channel = 2)
        assertFalse(textDisabled())
        recording()
        assertFalse(textDisabled())
        live(timeshift = false)
        installSubtitleTracks("en")
        assertTrue("the channel's Off choice returns", textDisabled())
    }

    @Test fun freshRuntimeReadsStoredChoiceAndUnmatchedOrAmbiguousTracksKeepTheDefaultAndTheChoice() = exercise {
        useChoiceProfile()
        settings.subtitleChoices.set("test-profile", 1, english)
        live(timeshift = false)
        installSubtitleTracks("de")
        assertTrue(runtime.subtitlesDefault.value)
        assertEquals(english, settings.subtitleChoices.read("test-profile", 1))
        val identical = installSubtitleTracks("en", "en")
        assertTrue(runtime.subtitlesDefault.value)
        select(identical[1])
        settle()
        signalTracksChanged()
        assertSame("a live pick among identically keyed subtitles survives a tracks change",
            identical[1], textOverride()?.mediaTrackGroup)
        assertEquals(english, settings.subtitleChoices.read("test-profile", 1))
        installSubtitleTracks("en", supported = false)
        assertTrue(runtime.subtitlesDefault.value)
        installSubtitleTracks("en", mimeType = MimeTypes.TEXT_VTT)
        assertTrue(runtime.subtitlesDefault.value)
        installSubtitleTracks("en", roleFlags = C.ROLE_FLAG_CAPTION)
        assertTrue(runtime.subtitlesDefault.value)
        val unique = installSubtitleTracks("en").single()
        assertSame("a uniquely matching supported track restores the saved choice", unique, textOverride()?.mediaTrackGroup)
        assertEquals(english, settings.subtitleChoices.read("test-profile", 1))
    }

    @Test fun defaultOutlivesItsCallerAndRemovesTheChoiceAfterThePendingWrite() = exercise {
        useChoiceProfile()
        live(timeshift = false)
        val gate = CompletableDeferred<Unit>()
        val writing = CompletableDeferred<Unit>()
        beforeSettingsWrite = { writing.complete(Unit); gate.await() }
        select(installSubtitleTracks("en").single())
        scheduler.runCurrent()
        assertTrue(writing.isCompleted)
        val releaseCommand = CompletableDeferred<Unit>()
        val blocker = scope.launch { commands.serialize(onClosed = {}) { releaseCommand.await() } }
        scheduler.runCurrent()
        lateinit var default: Job
        val caller = scope.launch {
            default = runtime.useDefaultSubtitles()
            awaitCancellation()
        }
        scheduler.runCurrent()
        assertFalse(default.isCompleted)
        caller.cancelAndJoin()
        assertFalse(runtime.subtitlesDefault.value)
        releaseCommand.complete(Unit)
        blocker.join()
        settle()
        assertFalse(default.isCancelled)
        assertTrue(runtime.subtitlesDefault.value)
        gate.complete(Unit)
        settle()
        assertNull(settings.subtitleChoices.read("test-profile", 1))
        live(timeshift = false, channel = 2)
        live(timeshift = false)
        installSubtitleTracks("en")
        assertTrue(runtime.subtitlesDefault.value)
    }

    @Test fun recordingManualChoiceSurvivesStopAndDoesNotChangeLiveOrAnotherRecording() = exercise {
        useChoiceProfile()
        recording()
        select(installSubtitleTracks("en").single())
        settle()
        assertFalse(runtime.subtitlesDefault.value)
        assertNull(settings.subtitleChoices.read("test-profile", 1))
        stopPlayback()
        recording(start = RecordingPlaybackStart.RESUME)
        val resumed = installSubtitleTracks("en").single()
        assertSame(resumed, textOverride()?.mediaTrackGroup)
        recording(id = 2)
        installSubtitleTracks("en")
        assertTrue(runtime.subtitlesDefault.value)
        live(timeshift = false)
        assertNull(textOverride())
        assertFalse(textDisabled())
        restartPlaybackRuntime()
        recording()
        val restarted = installSubtitleTracks("en").single()
        assertSame(restarted, textOverride()?.mediaTrackGroup)
        stopPlayback()
        useChoiceProfile("other-profile")
        recording()
        installSubtitleTracks("en")
        assertTrue(runtime.subtitlesDefault.value)
    }

    @Test fun recordingInheritsLiveSubtitlesUntilItChoosesOffOrDefault() = exercise {
        useChoiceProfile()
        settings.subtitleChoices.set("test-profile", 1, english)
        recording(id = 2)
        val inherited = installSubtitleTracks("de", "en")[1]
        assertSame(inherited, textOverride()?.mediaTrackGroup)
        settle()
        assertNull(settings.recordingSubtitleChoices.read("test-profile", 2))
        settings.subtitleChoices.set("test-profile", 1, english.copy(language = "de"))
        recording(id = 2)
        val updated = installSubtitleTracks("de", "en")[0]
        assertSame(updated, textOverride()?.mediaTrackGroup)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
        runtime.selectSubtitles(TrackSelectionOverride(updated, listOf(0)), off = false).join()
        settle()
        assertEquals(english.copy(language = "de"), settings.recordingSubtitleChoices.read("test-profile", 2))

        runtime.selectSubtitles(null, off = true)
        settle()
        recording(id = 2)
        assertTrue(textDisabled())
        runtime.useDefaultSubtitles()
        settle()
        restartPlaybackRuntime()
        recording(id = 2)
        installSubtitleTracks("de", "en")
        assertTrue(runtime.subtitlesDefault.value)
        assertEquals(english.copy(language = "de"), settings.subtitleChoices.read("test-profile", 1))
    }

    @Test fun queuedDefaultAndBackRestoreInOrderAndRetiredChoicesCannotApplyToTheNextChannel() = exercise {
        useChoiceProfile()
        live(timeshift = false)
        val group = installSubtitleTracks("en").single()
        select(group)
        settle()
        val release = CompletableDeferred<Unit>()
        val blocker = scope.launch { commands.serialize(onClosed = {}) { release.await() } }
        scheduler.runCurrent()
        runtime.useDefaultSubtitles()
        runtime.selectSubtitles(TrackSelectionOverride(group, listOf(0)), off = false)
        scheduler.runCurrent()
        release.complete(Unit)
        blocker.join()
        settle()
        assertFalse("Back after a queued Default restores the opening explicit choice", runtime.subtitlesDefault.value)
        assertEquals(english, settings.subtitleChoices.read("test-profile", 1))
        runtime.selectSubtitles(null, off = true)
        runtime.selectSubtitles(null, off = false)
        settle()
        assertTrue(runtime.subtitlesDefault.value)
        assertNull(settings.subtitleChoices.read("test-profile", 1))
        runtime.selectSubtitles(null, off = true, quickListEpoch = -1L)
        settle()
        assertTrue("a retired quick list cannot change the current playback", runtime.subtitlesDefault.value)

        val releaseSwitch = CompletableDeferred<Unit>()
        val switchBlocker = scope.launch { commands.serialize(onClosed = {}) { releaseSwitch.await() } }
        scheduler.runCurrent()
        val switch = scope.launch {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        scheduler.runCurrent()
        // A full-panel command has no quick-list epoch. It must capture its current target
        // before waiting behind the already queued switch, not disable the successor.
        runtime.selectSubtitles(null, off = true)
        scheduler.runCurrent()
        releaseSwitch.complete(Unit)
        switchBlocker.join()
        await { switch.isCompleted }
        switch.join()
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse("a queued full-panel Off must not disable the next channel", textDisabled())
        assertNull(settings.subtitleChoices.read("test-profile", 2))
    }

    @Test fun storageWriteFailureDoesNotInterruptTheActivePlaybackOrDefault() = exercise {
        useChoiceProfile()
        live(timeshift = false)
        val target = runtime.activeTarget.value
        beforeSettingsWrite = { throw IOException("storage unavailable") }
        select(installSubtitleTracks("en").single())
        settle()
        assertNotNull(textOverride())
        assertEquals(target, runtime.activeTarget.value)
        runtime.useDefaultSubtitles()
        settle()
        assertTrue(runtime.subtitlesDefault.value)
        assertEquals(target, runtime.activeTarget.value)
    }

    private fun Fixture.select(group: TrackGroup) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .addOverride(TrackSelectionOverride(group, listOf(0))).build()
    }

    private fun Fixture.textOverride() =
        player.trackSelectionParameters.overrides.values.singleOrNull { it.type == C.TRACK_TYPE_TEXT }
    private fun Fixture.textDisabled() = C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes

    private val english = SubtitleChoice.Track("en", MimeTypes.APPLICATION_DVBSUBS, 0)
}
