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
import at.bernhardberger.tvhplayer.ui.components.depth.DepthKeyCycle
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
        fun send(key: KeyEvent) = cycle.intercept(key, false, false, { backs++ }, { stops++ })
        assertTrue(send(event(true)))
        assertTrue(send(event(true, 1)))
        send(event(true, 2))
        send(event(false))
        assertEquals(1, stops)
        assertEquals(0, backs)
    }

    @Test fun confirmationsAndNestedDialogsKeepTheirOwnBackKeys() {
        for ((confirmation, nested) in listOf(true to false, false to true)) {
            val cycle = PlayerBackKeyCycle()
            val fail = { fail("dialog Back must pass through"); Unit }
            for (key in listOf(event(true), event(true, 1), event(false))) {
                assertFalse(cycle.intercept(key, confirmation, nested, fail, fail))
            }
        }
    }

    @Test fun canceledHardwareReleaseDoesNotUnwindPlayer() {
        val cycle = PlayerBackKeyCycle()
        val fail = { fail("canceled Back must do nothing"); Unit }
        assertTrue(cycle.intercept(event(true), false, false, fail, fail))
        assertTrue(cycle.intercept(event(false, flags = NativeKey.FLAG_CANCELED), false, false, fail, fail))
    }

    @Test fun modalPassThroughResetsTrackingAndCanceledReleaseIsConsumed() {
        val cycle = PlayerBackKeyCycle()
        val fail = { fail("stale or canceled Back must do nothing"); Unit }
        cycle.intercept(event(true), false, false, fail, fail)
        assertFalse(cycle.intercept(event(true, 1), false, true, fail, fail))
        assertTrue(cycle.intercept(event(false, flags = NativeKey.FLAG_CANCELED), false, true, fail, fail))
        assertTrue(cycle.intercept(event(true, 2), false, false, fail, fail))
        assertTrue(cycle.intercept(event(false), false, false, fail, fail))
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
        cycle.intercept(event(true), false, false, {}, stop, owner)
        cycle.intercept(event(true, 1), false, false, {}, stop, owner)
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
            val browseCycle = remember { DepthKeyCycle() }
            val focus = remember { FocusRequester() }
            BackHandler { systemBacks++ }
            Box(Modifier.fillMaxSize().focusRequester(focus).onPreviewKeyEvent { key ->
                if (playing) cycle.intercept(key, false, false, { fail("short Back after hold") }, {
                    stops++
                    playing = false
                }) else {
                    val native = key.nativeKeyEvent
                    browseCycle.handle(native.keyCode, native.action == NativeKey.ACTION_DOWN, native.repeatCount) {
                        browseBacks++
                        true
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
        // A new browse press still acts on its first down and ignores its hold/release.
        send(event(true))
        assertEquals(1, browseBacks)
        send(event(true, 1))
        send(event(false))
        assertEquals(1, browseBacks)
        assertEquals(0, systemBacks)
    }
}
