@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionPlaybackPlayerTest {
    @Test fun commandsAreAnExactAllowlistForEachTarget() {
        val read = setOf(Player.COMMAND_GET_METADATA, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
        fun commands(target: AppPlaybackTarget?, timeshift: Boolean = false, seekable: Boolean = true): Set<Int> =
            sessionPlaybackCommands(target, timeshift, seekable).let { value ->
                (0 until value.size()).map { value[it] }.toSet()
            }
        val live = AppPlaybackTarget.Live(ChannelId(1))
        val recording = AppPlaybackTarget.Recording(DvrEntryId(1))
        assertEquals(emptySet<Int>(), commands(null))
        assertEquals(read, commands(live))
        assertEquals(read + Player.COMMAND_PLAY_PAUSE, commands(live, timeshift = true))
        assertEquals(read + Player.COMMAND_PLAY_PAUSE, commands(recording, seekable = false))
        assertEquals(read + setOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM), commands(recording))
    }

    @Test fun liveWithoutTimeshiftDoesNotPauseOrSendServerCommands() = exercise {
        live(false)
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        wrapper.pause()
        wrapper.play()
        wrapper.seekTo(10_000)
        settle()
        assertTrue(player.playWhenReady)
        assertTrue(connection.speeds.isEmpty())
        assertEquals(1, focusRequests)
    }

    @Test fun timeshiftPauseAndPlayReachServerExactlyOnceAndSeekIsUnavailable() = exercise {
        live(true)
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        wrapper.pause()
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        wrapper.play()
        await { connection.speeds == listOf(0, 100) && player.playWhenReady }
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertEquals(2, focusRequests)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun rejectedTimeshiftPauseRollsBackLocalIntent() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        var localPauseBeforeServerCommand = false
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) localPauseBeforeServerCommand = connection.speeds.isEmpty()
            }
        })
        wrapper.pause()
        await { connection.speeds == listOf(0) }
        settle()
        assertTrue(player.playWhenReady)
        assertTrue(wrapper.playWhenReady)
        assertTrue(localPauseBeforeServerCommand)
        assertEquals(1, focusRequests)
    }

    @Test fun deniedFocusDoesNotResumeServerWhenRejectedHoldKeepsMutedVideoRunning() = exercise {
        live(true)
        focusGranted = false
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        wrapper.play()
        await { connection.speeds == listOf(0) }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
    }

    @Test fun resumingFocusInterruptionSendsOnlyOneServerResume() = exercise {
        live(true)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        wrapper.play()
        await { connection.speeds == listOf(0, 100) && player.playWhenReady }
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertEquals(2, focusRequests)
    }

    @Test fun rejectedResumeAfterFocusInterruptionPausesLocallyAndSendsOneResume() = exercise {
        live(true)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        wrapper.play()
        await { connection.speeds == listOf(0, 100) && focusRequests == 2 }
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
    }

    @Test fun pauseDuringInterruptionDoesNotSendASecondServerHoldOrAutoResume() = exercise {
        live(true)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        wrapper.pause()
        settle()
        focusListener?.invoke(AudioInterruption.GAIN)
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertEquals(1, focusRequests)
        assertFalse(player.playWhenReady)
    }

    @Test fun pauseWhileInterruptionMutedRestoresSoundWithoutServerCommands() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes }
        connection.scriptSpeed(SubscriptionOperationResult.Ok(Unit))
        wrapper.pause()
        await { focusRequests == 2 }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
    }

    @Test fun pauseWhileInterruptionMutedAndFocusDeniedPreservesMuteWithoutServerPause() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes }
        focusGranted = false
        wrapper.pause()
        await { focusRequests == 2 }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(player.playWhenReady)
        assertTrue(androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
    }

    @Test fun closeDuringLocalPauseLetsServerRejectionRollbackComplete() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        val closed = CompletableDeferred<Unit>()
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && !closed.isCompleted) {
                    observation.cancel()
                    wrapper.close()
                    closed.complete(Unit)
                }
            }
        })
        wrapper.pause()
        await { closed.isCompleted && connection.speeds == listOf(0) && player.playWhenReady }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(player.playWhenReady)
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
    }

    @Test fun recordingPauseAndPlayUseRuntimeFocusPath() = exercise {
        recording()
        wrapper.pause()
        await { !player.playWhenReady }
        wrapper.play()
        await { player.playWhenReady && focusRequests == 2 }
        settle()
        assertEquals(2, focusRequests)
        assertTrue(connection.speeds.isEmpty())
    }

    @Test fun queuedPlayAfterCloseDoesNotRequestFocusOrResumeWhileRuntimeIsForeground() = exercise {
        live(true)
        wrapper.pause()
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        whileCommandsBlocked {
            wrapper.play()
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertEquals(1, focusRequests)
        assertFalse(player.playWhenReady)
        assertEquals(listOf(0), connection.speeds)
    }

    @Test fun queuedPauseAfterCloseDoesNotPauseWhileRuntimeIsForeground() = exercise {
        live(true)
        whileCommandsBlocked {
            wrapper.pause()
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertTrue(player.playWhenReady)
        assertTrue(connection.speeds.isEmpty())
        assertEquals(1, focusRequests)
    }

    @Test fun queuedSeekAfterCloseDoesNotMoveRecordingWhileRuntimeIsForeground() = exercise {
        recording()
        player.setMediaSource(SilenceMediaSource.Factory().setDurationUs(60_000_000).createMediaSource())
        player.prepare()
        player.pause()
        await { wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }
        val position = player.currentPosition
        whileCommandsBlocked {
            wrapper.seekTo(12_000)
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertEquals(position, player.currentPosition)
        assertEquals(1, focusRequests)
    }

    @Test fun recordingSeekUsesSerializedRuntimeCommandAndRequiresSeekableTimeline() = exercise {
        recording()
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        // Replace only the fixture's transport with a deterministic, seekable offline source.
        player.setMediaSource(SilenceMediaSource.Factory().setDurationUs(60_000_000).createMediaSource())
        player.prepare()
        await { wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }
        wrapper.seekTo(12_000)
        await { player.currentPosition >= 12_000 }
        assertTrue(player.currentPosition < 13_000)
        assertTrue(connection.speeds.isEmpty())
    }

    @Test fun metadataUsesNamesAndPublishesProgrammeAndTargetChanges() = exercise {
        val titles = mutableListOf<String?>()
        wrapper.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                titles += mediaMetadata.title?.toString()
            }
        })
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
        live(true)
        await { wrapper.mediaMetadata.title == "Channel One" }
        assertEquals("Channel One", wrapper.mediaMetadata.artist)
        val now = Clock.System.now()
        val previous = session.observation.value
        session.publish(SessionObservation.create(
            sessionState = previous.sessionState, channelState = previous.channelState, dvrState = previous.dvrState,
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(
            events = listOf(EpgEvent.create(EventId(1), ChannelId(1), start = now - 1.hours,
                stop = now + 1.hours, title = "Programme")),
        ))))
        await { wrapper.mediaMetadata.title == "Programme" }
        recording()
        await { wrapper.mediaMetadata.title == "Recording" }
        assertEquals("Recorded Channel", wrapper.mediaMetadata.subtitle)
        assertTrue(titles.containsAll(listOf("Channel One", "Programme", "Recording")))
        assertNull(wrapper.currentMediaItem?.localConfiguration)
        assertNull(wrapper.mediaMetadata.artworkUri)
        assertNull(wrapper.mediaMetadata.extras)
        assertEquals("", wrapper.currentMediaItem?.mediaId)
        val stop = scope.async { runtime.stop() }
        await { stop.isCompleted && wrapper.availableCommands == Player.Commands.EMPTY }
        assertNull(wrapper.currentMediaItem)
    }

    @Test fun closeLeavesRawPlayerAliveAndRejectsFurtherCommands() = exercise {
        recording()
        val requests = focusRequests
        observation.cancel()
        wrapper.close()
        wrapper.close()
        wrapper.pause()
        settle()
        assertTrue(player.playWhenReady)
        assertEquals(requests, focusRequests)
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
        player.pause()
        assertFalse(player.playWhenReady)
    }

    // Real Media3 and the SDK's asynchronous subscription machinery require looper pumping.
    private fun exercise(block: suspend Fixture.() -> Unit) = runBlocking {
        val fixture = Fixture(CoroutineScope(coroutineContext + SupervisorJob()))
        try { withTimeout(15_000) { fixture.block() } }
        finally {
            fixture.observation.cancel()
            fixture.wrapper.close()
            fixture.scope.cancel()
            fixture.runtime.detach()
            fixture.player.release()
            fixture.session.shutdown()
        }
    }

    private class Fixture(val scope: CoroutineScope) {
        private val context = ApplicationProvider.getApplicationContext<Application>()
        var focusRequests = 0
        var focusGranted = true
        var focusListener: ((AudioInterruption) -> Unit)? = null
        private val focus = object : PlaybackAudioFocus {
            override fun request(onInterruption: (AudioInterruption) -> Unit): Boolean {
                focusRequests++
                focusListener = onInterruption
                return focusGranted
            }
            override fun abandon() = Unit
        }
        val connection = ScriptedSubscriptionConnection()
        private val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Channel One")))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(DvrEntry.create(
                id = DvrEntryId(1), state = DvrEntryState.COMPLETED, title = "Recording", channelName = "Recorded Channel",
            )))),
        )).apply { scriptLivePlaybackSuccess(manager); scriptRecordingPlaybackSuccess() }
        private val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        private val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing }).also { owner -> scope.launch { owner.run() } }
        val player = ExoPlayer.Builder(context).build()
        private val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        val runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope, TvheadendAudioOutputProvider(context), focus,
            PlaybackRuntimePolicy.fromPlayerSettings())
        val wrapper = SessionPlaybackPlayer(runtime)
        val observation = scope.launch { wrapper.observe(session.observation) }

        suspend fun live(timeshift: Boolean) {
            settings.setTimeshiftEnabled(timeshift)
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, if (timeshift) 120 else 0)))
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1))))
            }
            await { connection.subscribeCount == 1 }
            connection.awaitCollectionRegistered()
            connection.emit(SubscriptionEvent.Started(listOf(
                SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                    language = null, compositionId = null, ancillaryId = null, width = 320, height = 240,
                    frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                    audioVersion = null, channelCount = null, rate = null, rdsUecp = null, codecMetadata = null),
            ), null, SubscriptionCondition.NO_DETAIL))
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            if (timeshift) {
                connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
                await { wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            }
            settle()
        }

        suspend fun recording() {
            val install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                    RecordingPlaybackStart.START_OVER)
            }
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            settle()
        }

        suspend fun whileCommandsBlocked(block: suspend () -> Unit) {
            // Hold the existing serializer, as in AutomaticAudioRuntimeTest; no runtime test seam.
            val commands = AppPlaybackRuntime::class.java.getDeclaredField("targetCommands").let {
                it.isAccessible = true
                it.get(runtime) as PlaybackTargetCommandSerialization
            }
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val blocker = scope.launch {
                commands.serialize(onClosed = {}) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            try { block() } finally {
                release.complete(Unit)
                blocker.join()
            }
            // Drain commands queued by the wrapper before checking their effects.
            commands.serialize(onClosed = {}) {}
        }

        suspend fun await(predicate: () -> Boolean) {
            withTimeout(5_000) { while (!predicate()) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
        }
        suspend fun settle() { repeat(5) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
    }
}
