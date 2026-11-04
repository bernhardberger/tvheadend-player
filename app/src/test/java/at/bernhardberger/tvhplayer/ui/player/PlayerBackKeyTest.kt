package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.view.KeyEvent as NativeKey
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import at.bernhardberger.tvhplayer.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayerBackKeyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun event(down: Boolean, repeat: Int = 0, flags: Int = 0) = KeyEvent(
        NativeKey(0, 0, if (down) NativeKey.ACTION_DOWN else NativeKey.ACTION_UP,
            NativeKey.KEYCODE_BACK, repeat, 0, 0, 0, flags))

    @Test fun holdActsOnceWithoutTakingAShortBackStep() {
        val cycle = PlayerBackKeyCycle()
        var stops = 0
        var backs = 0
        fun send(key: KeyEvent) = cycle.intercept(key, true, { backs++ }, { stops++ })
        assertTrue(send(event(true)))
        assertTrue(send(event(true, 1)))
        send(event(true, 2))
        send(event(false))
        assertEquals(1, stops)
        assertEquals(0, backs)
    }

    @Test fun panelHoldActsOnceOnReleaseAndDoesNotBackTheExposedWatchingLayer() {
        val cycle = PlayerBackKeyCycle()
        var panelOpen = true
        var backs = 0
        fun send(key: KeyEvent) = cycle.intercept(key, !panelOpen, {
            backs++
            panelOpen = false
        }, { fail("panel hold must not stop playback") })
        send(event(true))
        send(event(true, 1, NativeKey.FLAG_LONG_PRESS))
        send(event(true, 2))
        assertTrue(panelOpen)
        assertEquals(0, backs)
        send(event(false))
        assertFalse(panelOpen)
        send(event(true, 3))
        send(event(false))
        assertEquals(1, backs)
    }

    @Test fun canceledHardwareReleaseDoesNotUnwindPlayer() {
        val cycle = PlayerBackKeyCycle()
        val fail = { fail("canceled Back must do nothing"); Unit }
        assertTrue(cycle.intercept(event(true), true, fail, fail))
        assertTrue(cycle.intercept(event(false, flags = NativeKey.FLAG_CANCELED), true, fail, fail))
    }

    @Test fun canceledPanelReleaseDoesNotDispatchBackAndResetsTracking() {
        val cycle = PlayerBackKeyCycle()
        val fail = { fail("stale or canceled Back must do nothing"); Unit }
        cycle.intercept(event(true), false, fail, fail)
        assertTrue(cycle.intercept(event(true, 1), false, fail, fail))
        assertTrue(cycle.intercept(event(false, flags = NativeKey.FLAG_CANCELED), false, fail, fail))
        assertTrue(cycle.intercept(event(true, 2), true, fail, fail))
        assertTrue(cycle.intercept(event(false), true, fail, fail))
    }

    @Test fun holdClaimsActivityRemainderBeforeClosingAndCanceledUpClearsIt() {
        val owner = at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyCycleOwner()
        val dispatcher = at.bernhardberger.tvhplayer.ui.MainStartupActivityKeyDispatcher(owner)
        val contract = at.bernhardberger.tvhplayer.ui.MainStartupActivityKeyContract(
            at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode.Inactive)
        val cycle = PlayerBackKeyCycle()
        val stop = {
            assertTrue(dispatcher.dispatch(contract, event(true, 2).nativeKeyEvent))
        }
        cycle.intercept(event(true), true, {}, stop, owner)
        cycle.intercept(event(true, 1), true, {}, stop, owner)
        assertTrue(dispatcher.dispatch(contract, event(false, flags = NativeKey.FLAG_CANCELED).nativeKeyEvent))
        assertFalse(dispatcher.dispatch(contract, event(true).nativeKeyEvent))
    }

    @Test fun holdExitLeavesBrowseAndActivityWithNoTrackedBack() {
        var playing by mutableStateOf(true)
        var browseBacks = 0
        var systemBacks = 0
        var stops = 0
        lateinit var root: android.view.View
        compose.setContent {
            root = androidx.compose.ui.platform.LocalView.current
            val cycle = remember { PlayerBackKeyCycle() }
            val browseCycle = remember { BackKeyCycle() }
            val focus = remember { FocusRequester() }
            BackHandler { systemBacks++ }
            Box(Modifier.fillMaxSize().focusRequester(focus).onPreviewKeyEvent { key ->
                if (playing) cycle.intercept(key, true, { fail("short Back after hold") }, {
                    stops++
                    playing = false
                }) else {
                    val native = key.nativeKeyEvent
                    browseCycle.handle(native.action == NativeKey.ACTION_DOWN, native.repeatCount, native.isCanceled) {
                        browseBacks++
                    }
                }
            }.focusable())
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        compose.waitForIdle()
        fun send(key: KeyEvent) {
            compose.runOnIdle {
                // Robolectric Activity dispatch does not traverse AndroidComposeView. Deliver
                // to the focused Compose root first, then the real Activity fallback if unhandled.
                if (!root.dispatchKeyEvent(key.nativeKeyEvent)) {
                    compose.activity.dispatchKeyEvent(key.nativeKeyEvent)
                }
            }
            compose.waitForIdle()
        }
        send(event(true))
        send(event(true, 1, NativeKey.FLAG_LONG_PRESS))
        assertFalse(playing)
        send(event(true, 2))
        send(event(false))
        assertEquals(1, stops)
        assertEquals(0, browseBacks)
        assertEquals(0, systemBacks)
        assertFalse(compose.activity.isFinishing)
        // A new browse press acts only on its own release, not on the player's held press.
        send(event(true))
        assertEquals(0, browseBacks)
        send(event(true, 1))
        send(event(false))
        assertEquals(1, browseBacks)
        assertEquals(0, systemBacks)
    }

}
