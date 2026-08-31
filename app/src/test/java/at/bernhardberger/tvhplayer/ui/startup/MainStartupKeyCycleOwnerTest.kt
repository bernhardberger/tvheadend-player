package at.bernhardberger.tvhplayer.ui.startup

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class MainStartupKeyCycleOwnerTest {
    @Test
    fun inactive_passesFreshKeysThrough() {
        val owner = MainStartupKeyCycleOwner()

        assertDecision(
            MainStartupKeyDecision.PASS_THROUGH,
            owner.keyEvent(MainStartupKeyMode.Inactive, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN),
        )
        assertDecision(
            MainStartupKeyDecision.PASS_THROUGH,
            owner.keyEvent(MainStartupKeyMode.Inactive, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_UP),
        )
    }

    @Test
    fun passive_consumesEveryStartupOwnedCategoryForItsCompleteCycle() {
        listOf(MainStartupKeyMode.Passive).forEach { mode ->
            startupOwnedAppKeys.forEach { keyCode ->
                val owner = MainStartupKeyCycleOwner()

                assertDecision(
                    MainStartupKeyDecision.CONSUME,
                    owner.keyEvent(mode, keyCode, KeyEvent.ACTION_DOWN),
                    keyCode,
                )
                assertDecision(
                    MainStartupKeyDecision.CONSUME,
                    owner.keyEvent(mode, keyCode, KeyEvent.ACTION_DOWN, repeatCount = 1),
                    keyCode,
                )
                assertDecision(
                    MainStartupKeyDecision.CONSUME,
                    owner.keyEvent(mode, keyCode, KeyEvent.ACTION_UP),
                    keyCode,
                )
            }
        }
    }

    @Test
    fun systemOwnedAndUnknownKeys_alwaysPassThrough() {
        (systemOwnedKeys + listOf(KeyEvent.KEYCODE_A)).forEach { keyCode ->
            val owner = MainStartupKeyCycleOwner()

            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(MainStartupKeyMode.Passive, keyCode, KeyEvent.ACTION_DOWN),
                keyCode,
            )
            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(MainStartupKeyMode.Actionable, keyCode, KeyEvent.ACTION_UP),
                keyCode,
            )
        }
    }

    @Test
    fun backAlwaysPassesThroughToTheAndroidXDispatcher() {
        listOf(MainStartupKeyMode.Passive, MainStartupKeyMode.Actionable).forEach { mode ->
            val owner = MainStartupKeyCycleOwner()

            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(mode, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN),
            )
            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(mode, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, repeatCount = 1),
            )
            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(mode, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP),
            )
        }
    }

    @Test
    fun passiveClaimsSurviveModeChangesUntilTheirMatchingUp() {
        listOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_INFO,
        ).forEach { keyCode ->
            val owner = MainStartupKeyCycleOwner()

            assertDecision(
                MainStartupKeyDecision.CONSUME,
                owner.keyEvent(MainStartupKeyMode.Passive, keyCode, KeyEvent.ACTION_DOWN),
                keyCode,
            )
            assertDecision(
                MainStartupKeyDecision.CONSUME,
                owner.keyEvent(MainStartupKeyMode.Actionable, keyCode, KeyEvent.ACTION_DOWN, repeatCount = 1),
                keyCode,
            )
            assertDecision(
                MainStartupKeyDecision.CONSUME,
                owner.keyEvent(MainStartupKeyMode.Inactive, keyCode, KeyEvent.ACTION_UP),
                keyCode,
            )
            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(MainStartupKeyMode.Inactive, keyCode, KeyEvent.ACTION_DOWN),
                keyCode,
            )
        }
    }

    @Test
    fun claimedCycles_areIndependentWhenInterleaved() {
        val owner = MainStartupKeyCycleOwner()

        assertDecision(
            MainStartupKeyDecision.CONSUME,
            owner.keyEvent(MainStartupKeyMode.Passive, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN),
        )
        assertDecision(
            MainStartupKeyDecision.CONSUME,
            owner.keyEvent(MainStartupKeyMode.Passive, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.ACTION_DOWN),
        )
        assertDecision(
            MainStartupKeyDecision.CONSUME,
            owner.keyEvent(MainStartupKeyMode.Inactive, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_UP),
        )
        assertDecision(
            MainStartupKeyDecision.CONSUME,
            owner.keyEvent(MainStartupKeyMode.Inactive, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.ACTION_UP),
        )
    }

    @Test
    fun actionableDirectionalEvents_passThroughIncludingRepeats() {
        val owner = MainStartupKeyCycleOwner()

        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        ).forEach { keyCode ->
            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(MainStartupKeyMode.Actionable, keyCode, KeyEvent.ACTION_DOWN),
                keyCode,
            )
            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(MainStartupKeyMode.Actionable, keyCode, KeyEvent.ACTION_DOWN, repeatCount = 1),
                keyCode,
            )
            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(MainStartupKeyMode.Actionable, keyCode, KeyEvent.ACTION_UP),
                keyCode,
            )
        }
    }

    @Test
    fun actionableActivation_allowsOneFocusedActionPerCycle() {
        val owner = MainStartupKeyCycleOwner()
        val focusedControl = FakeFocusedControl()

        focusedControl.receive(
            owner.keyEvent(MainStartupKeyMode.Actionable, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN),
        )
        focusedControl.receive(
            owner.keyEvent(MainStartupKeyMode.Actionable, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, repeatCount = 1),
        )
        focusedControl.receive(
            owner.keyEvent(MainStartupKeyMode.Inactive, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN),
        )
        focusedControl.receive(
            owner.keyEvent(MainStartupKeyMode.Inactive, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP),
        )
        focusedControl.receive(
            owner.keyEvent(MainStartupKeyMode.Actionable, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN),
        )
        focusedControl.receive(
            owner.keyEvent(MainStartupKeyMode.Actionable, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP),
        )

        assertEquals(2, focusedControl.activationDownCount)
        assertEquals(2, focusedControl.activationUpCount)
    }

    @Test
    fun actionableActivation_consumesDuplicateZeroRepeatDownBeforeAllowingNextCycle() {
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        ).forEach { keyCode ->
            val owner = MainStartupKeyCycleOwner()

            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(MainStartupKeyMode.Actionable, keyCode, KeyEvent.ACTION_DOWN),
                keyCode,
            )
            assertDecision(
                MainStartupKeyDecision.CONSUME,
                owner.keyEvent(MainStartupKeyMode.Actionable, keyCode, KeyEvent.ACTION_DOWN),
                keyCode,
            )
            assertDecision(
                MainStartupKeyDecision.CONSUME,
                owner.keyEvent(MainStartupKeyMode.Actionable, keyCode, KeyEvent.ACTION_DOWN, repeatCount = 2),
                keyCode,
            )
            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(MainStartupKeyMode.Actionable, keyCode, KeyEvent.ACTION_UP),
                keyCode,
            )
            assertDecision(
                MainStartupKeyDecision.PASS_THROUGH,
                owner.keyEvent(MainStartupKeyMode.Actionable, keyCode, KeyEvent.ACTION_DOWN),
                keyCode,
            )
        }
    }

    @Test
    fun actionableModes_consumeNonNavigationAppKeys() {
        listOf(MainStartupKeyMode.Actionable).forEach { mode ->
            actionableNonNavigationKeys.forEach { keyCode ->
                val owner = MainStartupKeyCycleOwner()

                assertDecision(
                    MainStartupKeyDecision.CONSUME,
                    owner.keyEvent(mode, keyCode, KeyEvent.ACTION_DOWN),
                    keyCode,
                )
                assertDecision(
                    MainStartupKeyDecision.CONSUME,
                    owner.keyEvent(mode, keyCode, KeyEvent.ACTION_UP),
                    keyCode,
                )
            }
        }
    }

    private fun assertDecision(
        expected: MainStartupKeyDecision,
        actual: MainStartupKeyDecision,
        keyCode: Int? = null,
    ) {
        assertEquals(keyCode?.toString().orEmpty(), expected, actual)
    }

    private class FakeFocusedControl {
        var activationDownCount = 0
        var activationUpCount = 0

        fun receive(decision: MainStartupKeyDecision) {
            if (decision == MainStartupKeyDecision.PASS_THROUGH) {
                if (activationUpCount == activationDownCount) {
                    activationDownCount += 1
                } else {
                    activationUpCount += 1
                }
            }
        }
    }

    private companion object {
        val systemOwnedKeys = listOf(
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_TV_POWER,
            KeyEvent.KEYCODE_STB_POWER,
            KeyEvent.KEYCODE_AVR_POWER,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MUTE,
        )

        val digits = (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9).toList().toIntArray()
        val numpadDigits = (KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9).toList().toIntArray()

        val actionableNonNavigationKeys = listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_RECORD,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
        ) + digits.toList() + numpadDigits.toList() + listOf(
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_TV_CONTENTS_MENU,
            KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_TV_NUMBER_ENTRY,
            KeyEvent.KEYCODE_BOOKMARK,
        )

        val startupOwnedAppKeys = listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        ) + actionableNonNavigationKeys
    }
}
