package at.bernhardberger.tvhplayer.ui.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvhplayer.core.shouldMountPersistentPlayerSurface
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerVideoSurfaceLifecycleTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var player: ExoPlayer
    private var playerReleased = false

    @Before
    fun createPlayer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(instrumentation.targetContext).build().also { exoPlayer ->
                exoPlayer.addAnalyticsListener(
                    object : AnalyticsListener {
                        override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) {
                            playerReleased = true
                        }
                    }
                )
            }
        }
    }

    @After
    fun releasePlayer() {
        if (::player.isInitialized) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                player.release()
            }
        }
    }

    @Test
    fun removingSurfaceDetachesPassiveViewWithoutReleasingPlayer() {
        val mounted = mutableStateOf(true)
        lateinit var rootView: View

        composeRule.setContent {
            rootView = LocalView.current.rootView
            if (mounted.value) {
                PlayerVideoSurface(
                    player = player,
                    aspectRatio = AspectRatioMode.FIT,
                    videoVisible = true,
                )
            }
        }

        val mountedView = composeRule.runOnIdle {
            rootView.playerViews().single()
        }
        assertSame(player, mountedView.player)
        assertFalse(mountedView.useController)
        assertFalse(mountedView.isFocusable)
        assertFalse(mountedView.isFocusableInTouchMode)
        assertFalse(mountedView.isClickable)
        assertFalse(mountedView.hasFocusable())
        assertFalse(mountedView.keepScreenOn)
        assertTrue(
            mountedView.importantForAccessibility ==
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        )

        composeRule.runOnIdle { mounted.value = false }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertFalse(mountedView.isAttachedToWindow)
            assertNull(mountedView.player)
            assertFalse(mountedView.keepScreenOn)
            assertFalse(playerReleased)
        }
    }

    @Test
    fun warmPlayerShellCycleRetainsOneNativeViewUntilPlaybackBecomesIdleInShell() {
        val hasActivePlayback = mutableStateOf(false)
        val isPlayerRoute = mutableStateOf(true)
        lateinit var rootView: View

        composeRule.setContent {
            rootView = LocalView.current.rootView
            if (
                shouldMountPersistentPlayerSurface(
                    hasActivePlayback = hasActivePlayback.value,
                    isPlayerRoute = isPlayerRoute.value,
                )
            ) {
                PlayerVideoSurface(
                    player = player,
                    aspectRatio = AspectRatioMode.FIT,
                    videoVisible = true,
                )
            }
        }

        val mountedView = composeRule.runOnIdle {
            rootView.playerViews().single()
        }

        composeRule.runOnIdle {
            hasActivePlayback.value = true
            isPlayerRoute.value = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(mountedView, rootView.playerViews().single())
        }

        composeRule.runOnIdle { isPlayerRoute.value = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(mountedView, rootView.playerViews().single())
        }

        composeRule.runOnIdle {
            hasActivePlayback.value = false
            isPlayerRoute.value = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(rootView.playerViews().isEmpty())
            assertFalse(mountedView.isAttachedToWindow)
            assertNull(mountedView.player)
            assertFalse(mountedView.keepScreenOn)
            assertFalse(playerReleased)
        }
    }

    @Test
    fun targetReplacementConcealsOneNativeViewUntilItsFirstFrame() {
        val videoVisible = mutableStateOf(true)
        lateinit var rootView: View

        composeRule.setContent {
            rootView = LocalView.current.rootView
            PlayerVideoSurface(
                player = player,
                aspectRatio = AspectRatioMode.FIT,
                videoVisible = videoVisible.value,
            )
        }

        val mountedView = composeRule.runOnIdle {
            rootView.playerViews().single()
        }
        assertEquals(1f, mountedView.alpha)

        composeRule.runOnIdle { videoVisible.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(mountedView, rootView.playerViews().single())
            assertSame(player, mountedView.player)
            assertEquals(0f, mountedView.alpha)
            assertEquals(View.VISIBLE, mountedView.visibility)
            assertFalse(mountedView.isFocusable)
        }

        composeRule.runOnIdle { videoVisible.value = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(mountedView, rootView.playerViews().single())
            assertSame(player, mountedView.player)
            assertEquals(1f, mountedView.alpha)
        }
    }

    @Test
    fun playbackAndTrackEventsUpdateScreenAwakeWithoutReplacingSurface() {
        val observedPlayer = ObservableSurfacePlayer(player)
        lateinit var rootView: View
        composeRule.setContent {
            rootView = LocalView.current.rootView
            PlayerVideoSurface(observedPlayer, AspectRatioMode.FIT, videoVisible = true)
        }
        val mountedView = composeRule.runOnIdle { rootView.playerViews().single() }

        fun assertScreenOnAfter(expected: Boolean, change: () -> Unit) {
            composeRule.runOnIdle {
                change()
                observedPlayer.publish()
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertSame(mountedView, rootView.playerViews().single())
                assertEquals(expected, mountedView.keepScreenOn)
            }
        }

        assertScreenOnAfter(true) { observedPlayer.state = Player.STATE_READY }
        assertScreenOnAfter(false) { observedPlayer.readyToPlay = false }
        assertScreenOnAfter(true) { observedPlayer.readyToPlay = true }
        assertScreenOnAfter(false) {
            observedPlayer.suppression = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
        }
        assertScreenOnAfter(true) { observedPlayer.suppression = Player.PLAYBACK_SUPPRESSION_REASON_NONE }
        assertScreenOnAfter(false) { observedPlayer.state = Player.STATE_BUFFERING }
        assertScreenOnAfter(false) { /* Buffering events must not renew a wake request. */ }
        assertScreenOnAfter(true) { observedPlayer.state = Player.STATE_READY }
        assertScreenOnAfter(false) { observedPlayer.tracks = Tracks.EMPTY }
        assertScreenOnAfter(false) { observedPlayer.tracks = selectedVideoTracks(selected = false) }
        assertScreenOnAfter(true) { observedPlayer.tracks = selectedVideoTracks() }
        assertScreenOnAfter(false) {
            observedPlayer.error = PlaybackException("fixture failure", null, PlaybackException.ERROR_CODE_UNSPECIFIED)
        }
        assertScreenOnAfter(true) { observedPlayer.error = null }
        assertScreenOnAfter(false) { observedPlayer.state = Player.STATE_ENDED }
        assertScreenOnAfter(false) { observedPlayer.state = Player.STATE_IDLE }
    }

    @Test
    fun visibilityAndHostLifecycleReleaseScreenAwakeWhileSurfaceIsRetained() {
        val observedPlayer = ObservableSurfacePlayer(player).apply { state = Player.STATE_READY }
        val host = composeRule.runOnIdle { SurfaceLifecycleOwner() }
        val visible = mutableStateOf(true)
        val mounted = mutableStateOf(true)
        lateinit var rootView: View
        composeRule.setContent {
            rootView = LocalView.current.rootView
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                if (mounted.value) {
                    PlayerVideoSurface(observedPlayer, AspectRatioMode.FIT, visible.value)
                }
            }
        }
        val mountedView = composeRule.runOnIdle { rootView.playerViews().single() }

        fun assertScreenOnAfter(expected: Boolean, change: () -> Unit) {
            composeRule.runOnIdle(change)
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertSame(mountedView, rootView.playerViews().single())
                assertEquals(expected, mountedView.keepScreenOn)
            }
        }

        assertScreenOnAfter(true) { }
        val listenerCount = composeRule.runOnIdle { observedPlayer.listenerCount }
        assertScreenOnAfter(false) { visible.value = false }
        assertScreenOnAfter(true) { visible.value = true }
        assertScreenOnAfter(false) { host.registry.currentState = Lifecycle.State.CREATED }
        assertScreenOnAfter(false) { observedPlayer.publish() }
        assertScreenOnAfter(true) { host.registry.currentState = Lifecycle.State.STARTED }
        composeRule.runOnIdle { assertEquals(listenerCount, observedPlayer.listenerCount) }

        composeRule.runOnIdle { mounted.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0, observedPlayer.listenerCount)
            assertEquals(0, host.registry.observerCount)
            observedPlayer.publish()
            assertFalse(mountedView.keepScreenOn)
            assertNull(mountedView.player)
            assertFalse(playerReleased)
        }
    }

    @Test
    fun destroyedHostReleasesScreenAwakeWithoutDependingOnCompositionDisposal() {
        val observedPlayer = ObservableSurfacePlayer(player).apply { state = Player.STATE_READY }
        val host = composeRule.runOnIdle { SurfaceLifecycleOwner() }
        lateinit var rootView: View
        composeRule.setContent {
            rootView = LocalView.current.rootView
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                PlayerVideoSurface(observedPlayer, AspectRatioMode.FIT, videoVisible = true)
            }
        }
        val mountedView = composeRule.runOnIdle { rootView.playerViews().single() }
        composeRule.runOnIdle {
            assertTrue(mountedView.keepScreenOn)
            host.registry.currentState = Lifecycle.State.DESTROYED
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(mountedView, rootView.playerViews().single())
            assertFalse(mountedView.keepScreenOn)
        }
    }

    @Test
    fun playerReplacementRebindsRetainedViewAndRemovesOldWakeListener() {
        val previous = ObservableSurfacePlayer(player).apply { state = Player.STATE_READY }
        val replacement = ObservableSurfacePlayer(player)
        val current = mutableStateOf<Player>(previous)
        lateinit var rootView: View
        composeRule.setContent {
            rootView = LocalView.current.rootView
            PlayerVideoSurface(current.value, AspectRatioMode.FIT, videoVisible = true)
        }
        val mountedView = composeRule.runOnIdle { rootView.playerViews().single() }
        composeRule.runOnIdle { assertTrue(mountedView.keepScreenOn) }
        composeRule.runOnIdle { current.value = replacement }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(mountedView, rootView.playerViews().single())
            assertSame(replacement, mountedView.player)
            assertEquals(0, previous.listenerCount)
            previous.publish()
            assertFalse(mountedView.keepScreenOn)
            replacement.state = Player.STATE_READY
            replacement.publish()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(mountedView.keepScreenOn) }
    }
}

private class SurfaceLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

private class ObservableSurfacePlayer(delegate: Player) : ForwardingPlayer(delegate) {
    private val listeners = mutableSetOf<Player.Listener>()
    val listenerCount: Int get() = listeners.size
    var state = Player.STATE_IDLE
    var readyToPlay = true
    var suppression = Player.PLAYBACK_SUPPRESSION_REASON_NONE
    var tracks = selectedVideoTracks()
    var error: PlaybackException? = null

    override fun getPlaybackState(): Int = state
    override fun getPlayWhenReady(): Boolean = readyToPlay
    override fun getPlaybackSuppressionReason(): Int = suppression
    override fun isPlaying(): Boolean =
        state == Player.STATE_READY && readyToPlay && suppression == Player.PLAYBACK_SUPPRESSION_REASON_NONE
    override fun getCurrentTracks(): Tracks = tracks
    override fun getPlayerError(): PlaybackException? = error
    override fun addListener(listener: Player.Listener) { listeners += listener }
    override fun removeListener(listener: Player.Listener) { listeners -= listener }

    fun publish() {
        val events = Player.Events(
            FlagSet.Builder().addAll(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_TRACKS_CHANGED,
                Player.EVENT_PLAYER_ERROR,
            ).build(),
        )
        listeners.toList().forEach { it.onEvents(this, events) }
    }
}

private fun selectedVideoTracks(selected: Boolean = true): Tracks = Tracks(
    listOf(
        Tracks.Group(
            TrackGroup("video", Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
            false,
            intArrayOf(C.FORMAT_HANDLED),
            booleanArrayOf(selected),
        ),
    ),
)

private fun View.playerViews(): List<PlayerView> = buildList {
    fun collect(view: View) {
        if (view is PlayerView) add(view)
        if (view is ViewGroup) {
            repeat(view.childCount) { index -> collect(view.getChildAt(index)) }
        }
    }
    collect(this@playerViews)
}
