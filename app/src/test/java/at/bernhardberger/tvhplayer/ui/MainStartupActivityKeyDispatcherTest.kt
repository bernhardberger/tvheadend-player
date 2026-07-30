package at.bernhardberger.tvhplayer.ui

import android.view.KeyEvent
import at.bernhardberger.tvhplayer.ui.startup.MainStartupBackProfile
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyCycleOwner
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainStartupActivityKeyDispatcherTest {
    @Test
    fun settingsActivationCycleCannotReachTheFirstSettingsControlAfterStartupDisappears() {
        val dispatcher = MainStartupActivityKeyDispatcher(MainStartupKeyCycleOwner())
        var startupActions = 0
        var firstSettingsControlInvocations = 0
        var contract = MainStartupActivityKeyContract(
            mode = MainStartupKeyMode.Actionable(MainStartupBackProfile.NORMAL),
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
        assertEquals(0, firstSettingsControlInvocations)
    }

    @Test
    fun preUpTransitionConsumesForwardedActivationUp() {
        val dispatcher = MainStartupActivityKeyDispatcher(MainStartupKeyCycleOwner())
        var startupActions = 0
        var settingsActions = 0
        var contract = MainStartupActivityKeyContract(
            mode = MainStartupKeyMode.Actionable(MainStartupBackProfile.NORMAL),
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
    fun normalBackCycleCancelsOnceAndRemainsConsumedAfterStartupDisappears() {
        val dispatcher = MainStartupActivityKeyDispatcher(MainStartupKeyCycleOwner())
        var cancels = 0
        var contract = MainStartupActivityKeyContract(
            mode = MainStartupKeyMode.Passive(MainStartupBackProfile.NORMAL),
            cancelNormalStartup = { cancels++ },
        )

        assertTrue(dispatcher.dispatch(contract, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN))
        contract = MainStartupActivityKeyContract(mode = MainStartupKeyMode.Inactive)
        assertTrue(
            dispatcher.dispatch(
                contract = contract,
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
            ),
        )
        assertTrue(dispatcher.dispatch(contract, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP))

        assertEquals(1, cancels)
    }

    @Test
    fun simpleTvBackRemainsContained() {
        val dispatcher = MainStartupActivityKeyDispatcher(MainStartupKeyCycleOwner())
        var cancels = 0
        val contract = MainStartupActivityKeyContract(
            mode = MainStartupKeyMode.Passive(MainStartupBackProfile.SIMPLE_TV),
            cancelNormalStartup = { cancels++ },
        )

        assertTrue(dispatcher.dispatch(contract, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN))
        assertTrue(dispatcher.dispatch(contract, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP))

        assertEquals(0, cancels)
    }
}
