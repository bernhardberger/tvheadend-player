package at.bernhardberger.tvhplayer.ui

import android.view.KeyEvent
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyCycleOwner
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainStartupActivityKeyDispatcherTest {
    @Test
    fun settingsActivationCycleDispatchesOnlyOneStartupAction() {
        val dispatcher = MainStartupActivityKeyDispatcher(MainStartupKeyCycleOwner())
        var startupActions = 0
        var contract = MainStartupActivityKeyContract(
            mode = MainStartupKeyMode.Actionable,
        )

        assertFalse(
            dispatcher.dispatch(
                contract = contract,
                keyCode = KeyEvent.KEYCODE_ENTER,
                action = KeyEvent.ACTION_DOWN,
            ),
        )
        assertTrue(dispatcher.dispatch(
                contract = contract,
                keyCode = KeyEvent.KEYCODE_ENTER,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
            )
        )
        if (!dispatcher.dispatch(
                contract = contract,
                keyCode = KeyEvent.KEYCODE_ENTER,
                action = KeyEvent.ACTION_UP,
            )
        ) {
            startupActions++
            contract = MainStartupActivityKeyContract(mode = MainStartupKeyMode.Inactive)
        }

        assertEquals(1, startupActions)
    }

    @Test
    fun preUpTransitionConsumesForwardedActivationUp() {
        val dispatcher = MainStartupActivityKeyDispatcher(MainStartupKeyCycleOwner())
        var startupActions = 0
        var settingsActions = 0
        var contract = MainStartupActivityKeyContract(
            mode = MainStartupKeyMode.Actionable,
        )

        if (!dispatcher.dispatch(contract, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN)) {
            startupActions++
            contract = MainStartupActivityKeyContract(mode = MainStartupKeyMode.Inactive)
        }
        if (!dispatcher.dispatch(contract, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_UP)) {
            settingsActions++
        }

        assertEquals(1, startupActions)
        assertEquals(0, settingsActions)
    }

    @Test
    fun rawBackAlwaysPassesThroughToTheAndroidXDispatcher() {
        val dispatcher = MainStartupActivityKeyDispatcher(MainStartupKeyCycleOwner())
        val contract = MainStartupActivityKeyContract(mode = MainStartupKeyMode.Passive)

        assertFalse(dispatcher.dispatch(contract, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN))
        assertFalse(
            dispatcher.dispatch(
                contract = contract,
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
            ),
        )
        assertFalse(dispatcher.dispatch(contract, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP))
    }
}
