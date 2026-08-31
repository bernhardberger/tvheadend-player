package at.bernhardberger.tvhplayer.ui.startup

import android.view.KeyEvent

enum class MainStartupKeyDecision {
    PASS_THROUGH,
    CONSUME,
}

sealed interface MainStartupKeyMode {
    data object Inactive : MainStartupKeyMode
    data object Passive : MainStartupKeyMode
    data object Actionable : MainStartupKeyMode
}

/**
 * Owns startup key cycles while startup state can change between their down and up events.
 */
class MainStartupKeyCycleOwner {
    private val consumedKeyCodes = mutableSetOf<Int>()
    private val forwardedActivationKeyCodes = mutableSetOf<Int>()

    fun keyEvent(
        mode: MainStartupKeyMode,
        keyCode: Int,
        action: Int,
        repeatCount: Int = 0,
    ): MainStartupKeyDecision {
        if (isAlwaysSystemKey(keyCode)) return MainStartupKeyDecision.PASS_THROUGH

        if (keyCode in consumedKeyCodes) {
            if (action == KeyEvent.ACTION_UP) consumedKeyCodes.remove(keyCode)
            return MainStartupKeyDecision.CONSUME
        }

        if (keyCode in forwardedActivationKeyCodes) {
            return when (action) {
                KeyEvent.ACTION_DOWN -> MainStartupKeyDecision.CONSUME
                KeyEvent.ACTION_UP -> {
                    forwardedActivationKeyCodes.remove(keyCode)
                    MainStartupKeyDecision.PASS_THROUGH
                }
                else -> MainStartupKeyDecision.PASS_THROUGH
            }
        }

        if (action != KeyEvent.ACTION_DOWN) return MainStartupKeyDecision.PASS_THROUGH

        return when (mode) {
            MainStartupKeyMode.Inactive -> MainStartupKeyDecision.PASS_THROUGH
            MainStartupKeyMode.Passive -> passiveDecision(keyCode)
            MainStartupKeyMode.Actionable -> actionableDecision(
                keyCode = keyCode,
                repeatCount = repeatCount,
            )
        }
    }

    private fun passiveDecision(keyCode: Int): MainStartupKeyDecision = when {
        isPassiveOwnedKey(keyCode) -> claimAndConsume(keyCode)
        else -> MainStartupKeyDecision.PASS_THROUGH
    }

    private fun actionableDecision(
        keyCode: Int,
        repeatCount: Int,
    ): MainStartupKeyDecision = when {
        isActionableNonNavigationKey(keyCode) -> claimAndConsume(keyCode)
        isActivationKey(keyCode) && repeatCount == 0 -> {
            forwardedActivationKeyCodes.add(keyCode)
            MainStartupKeyDecision.PASS_THROUGH
        }
        isActivationKey(keyCode) -> claimAndConsume(keyCode)
        else -> MainStartupKeyDecision.PASS_THROUGH
    }

    private fun claimAndConsume(keyCode: Int): MainStartupKeyDecision {
        consumedKeyCodes.add(keyCode)
        return MainStartupKeyDecision.CONSUME
    }

    private fun isAlwaysSystemKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_TV_POWER,
        KeyEvent.KEYCODE_STB_POWER,
        KeyEvent.KEYCODE_AVR_POWER,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MUTE -> true
        else -> false
    }

    private fun isPassiveOwnedKey(keyCode: Int): Boolean =
        isDirectionalKey(keyCode) || isActivationKey(keyCode) || isActionableNonNavigationKey(keyCode)

    private fun isDirectionalKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT -> true
        else -> false
    }

    private fun isActivationKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> true
        else -> false
    }

    private fun isActionableNonNavigationKey(keyCode: Int): Boolean = when (keyCode) {
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
        KeyEvent.KEYCODE_INFO,
        KeyEvent.KEYCODE_TV_CONTENTS_MENU,
        KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU,
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_GUIDE,
        KeyEvent.KEYCODE_TV_NUMBER_ENTRY,
        KeyEvent.KEYCODE_BOOKMARK -> true
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> true
        else -> false
    }
}
