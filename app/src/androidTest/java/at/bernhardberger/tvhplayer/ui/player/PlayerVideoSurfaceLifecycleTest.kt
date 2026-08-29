package at.bernhardberger.tvhplayer.ui.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
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
        assertTrue(mountedView.keepScreenOn)
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
}

private fun View.playerViews(): List<PlayerView> = buildList {
    fun collect(view: View) {
        if (view is PlayerView) add(view)
        if (view is ViewGroup) {
            repeat(view.childCount) { index -> collect(view.getChildAt(index)) }
        }
    }
    collect(this@playerViews)
}
