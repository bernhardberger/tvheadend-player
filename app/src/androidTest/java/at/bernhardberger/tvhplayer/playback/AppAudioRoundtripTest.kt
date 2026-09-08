@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.playback

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.LegacyCredentialSource
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.testing.testSessionObservation
import at.bernhardberger.tvhplayer.ui.player.collectTracks
import at.bernhardberger.tvhplayer.ui.player.selectAudioTrack
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real app/SDK target installation, with controlled Player tracks; no subscription or decoder proof. */
@RunWith(AndroidJUnit4::class)
class AppAudioRoundtripTest {
    @Test
    fun explicitChoiceSurvivesAppOwnedChannelRoundtripWithFreshTracks() = runBlocking {
        withTimeout(20.seconds) {
            withContext(Dispatchers.Main) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val settings = PlayerSettingsStore(context)
                val profileStore = TvheadendServerProfileStore(context)
                check(profileStore.storeAnonymous("offline.invalid", 9982) is at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult.Available)
                val session = FakeTvheadendSession(testSessionObservation(channels = (1L..2L).map { id ->
                    Channel.create(
                        id = ChannelId(id), name = "Channel $id", uuid = null, number = id,
                        numberMinor = 0, icon = null, currentEventId = null, nextEventId = null,
                        services = emptyList(), tagIds = emptyList(),
                    )
                }))
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                var profiles = AppProfileOwner(context, session, profileStore, LegacyCredentialSource(context), settings, Dispatchers.IO)
                var profileJob = scope.launch { profiles.run() }
                var controlled = ControlledAudioPlayer()
                var coordinator = createTvheadendPlaybackCoordinator(controlled.player)
                var lifetime = coordinator.launchIn(scope)
                var runtime = AppPlaybackRuntime(controlled.player, session, coordinator, settings, profiles, scope)
                try {
                    profiles.serverProfile.filterNotNull().first()
                    suspend fun tune(id: Long, tracks: Tracks = audioTracks()) {
                        session.scriptLivePlaybackSuccess()
                        val selection = checkNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(id)))
                        assertTrue(runtime.playLive(selection)?.isStarted == true)
                        assertEquals(AppPlaybackTarget.Live(ChannelId(id)), runtime.activeTarget.value)
                        controlled.publishTracks(tracks)
                    }
                    tune(1)
                    val explicit = collectTracks(controlled.tracks, C.TRACK_TYPE_AUDIO).last()
                    selectAudioTrack(controlled.player, explicit)
                    tune(2)
                    assertTrue(controlled.parameters.overrides.isEmpty())
                    val profileIdentity = profiles.audioProfileId
                    lifetime.shutdown(2.seconds)
                    lifetime.join()
                    profileJob.cancelAndJoin()
                    runtime.detach()
                    controlled.player.release()
                    val freshSettings = PlayerSettingsStore(context)
                    profiles = AppProfileOwner(context, session, TvheadendServerProfileStore(context), LegacyCredentialSource(context), freshSettings, Dispatchers.IO)
                    profileJob = scope.launch { profiles.run() }
                    profiles.serverProfile.filterNotNull().first()
                    assertEquals(profileIdentity, profiles.audioProfileId)
                    controlled = ControlledAudioPlayer()
                    coordinator = createTvheadendPlaybackCoordinator(controlled.player)
                    lifetime = coordinator.launchIn(scope)
                    runtime = AppPlaybackRuntime(controlled.player, session, coordinator, freshSettings, profiles, scope)
                    tune(1, audioTracks(reverse = true))
                    val restored = controlled.parameters.overrides.values.single()
                    assertNotSame(explicit.group.mediaTrackGroup, restored.mediaTrackGroup)
                    assertSame(controlled.tracks.groups.first().mediaTrackGroup, restored.mediaTrackGroup)
                    assertEquals("alternate", restored.mediaTrackGroup.getFormat(restored.trackIndices.single()).id)
                    tune(2)
                    tune(1, audioTracks(missing = true))
                    assertTrue(controlled.parameters.overrides.isEmpty())
                    // Profile edits cannot transfer an old channel ID's explicit choice.
                    profiles.saveServer("replacement.invalid", 9982)
                    tune(1)
                    assertTrue(controlled.parameters.overrides.isEmpty())
                } finally {
                    lifetime.shutdown(2.seconds)
                    lifetime.join()
                    profileJob.cancelAndJoin()
                    session.shutdown()
                    runtime.detach()
                    controlled.player.release()
                    scope.cancel()
                    profileStore.clearProfile()
                }
            }
        }
    }
}

private class ControlledAudioPlayer {
    private val listeners = mutableSetOf<Player.Listener>()
    var parameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        private set
    var tracks = Tracks.EMPTY
        private set
    private var item: MediaItem? = null
    private var ready = false
    private var playWhenReady = false
    val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "ControlledAudioPlayer"
            "getApplicationLooper" -> Looper.getMainLooper()
            "addListener" -> { listeners += args!![0] as Player.Listener; null }
            "removeListener" -> { listeners -= args!![0] as Player.Listener; null }
            "getTrackSelectionParameters" -> parameters
            "setTrackSelectionParameters" -> {
                val next = args!![0] as TrackSelectionParameters
                if (parameters != next) {
                    parameters = next
                    listeners.toList().forEach { it.onTrackSelectionParametersChanged(next) }
                }
                null
            }
            "getCurrentTracks" -> tracks
            "getCurrentMediaItem" -> item
            "getCurrentTimeline" -> Timeline.EMPTY
            "getVideoSize" -> VideoSize.UNKNOWN
            "getPlayerError" -> null
            "getVideoFormat", "getAudioFormat" -> null
            "getPlaybackState" -> if (ready) Player.STATE_READY else Player.STATE_IDLE
            "getPlayWhenReady" -> playWhenReady
            "isPlaying" -> ready && playWhenReady
            "getDuration" -> C.TIME_UNSET
            "play" -> { playWhenReady = true; null }
            "setPlayWhenReady" -> { playWhenReady = args!![0] as Boolean; null }
            "setMediaSource" -> {
                item = (args!![0] as MediaSource).mediaItem
                tracks = Tracks.EMPTY
                listeners.toList().forEach { it.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
                listeners.toList().forEach { it.onTracksChanged(tracks) }
                null
            }
            "prepare" -> { ready = true; null }
            "stop" -> { ready = false; null }
            "clearMediaItems" -> {
                item = null
                tracks = Tracks.EMPTY
                listeners.toList().forEach { it.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
                null
            }
            "release" -> { listeners.clear(); null }
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 1f
                java.lang.Void.TYPE -> null
                else -> error("Unexpected ExoPlayer call: ${method.name}")
            }
        }
    } as ExoPlayer

    fun publishTracks(current: Tracks) {
        tracks = current
        listeners.toList().forEach { it.onTracksChanged(current) }
    }
}
