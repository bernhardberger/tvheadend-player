package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import at.bernhardberger.tvhplayer.core.PlaybackOptionsKeyOutcome
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.playbackOptionsKeyOutcome
import at.bernhardberger.tvhplayer.core.playbackOptionsKeyRequest
import at.bernhardberger.tvhplayer.core.playbackSuppressesRevealingKey
import at.bernhardberger.tvhplayer.core.playerKeyAction
import at.bernhardberger.tvhplayer.core.playerParentConsumesRecoveryKey
import kotlinx.coroutines.CoroutineScope

/**
 * The layers both players share: the options menu and its quick lists, Info, the stats,
 * the chrome they cover, the opening key's cycle and the control focus returns to.
 *
 * A screen with layers of its own (the live player's channel drawer and schedule) reports
 * them through [screenLayerOpen], so they cover the chrome too, and closes them in
 * [closeScreenLayers] whenever a shared layer takes their place.
 */
@Stable
internal class PlayerLayerState(
    scope: CoroutineScope,
    autoHideTimeoutMillis: Long,
    bannerTimeoutMillis: Long = PLAYER_BANNER_MS,
    initialFocusedControl: String? = null,
    /** Where a quick list opened over the full menu returns focus; null returns to the last control. */
    private val quickListOverMenuReturn: String? = null,
    private val screenLayerOpen: () -> Boolean = { false },
    private val closeScreenLayers: () -> Unit = {},
) {
    var optionsPage by mutableStateOf<PlaybackOptionsPage?>(null)
        private set

    /** [optionsPage] is the Audio or Subtitles short list opened by its remote key. */
    var optionsQuickList by mutableStateOf(false)
        private set

    val quickList = PlaybackQuickListSignals()

    var infoOpen by mutableStateOf(false)
        private set

    var statsVisible by mutableStateOf(false)
        private set

    var revealingKeyCode by mutableStateOf<Int?>(null)
        private set

    /** The Banner and the controls; Info, the options and the screen's own layers cover them. */
    val chrome = PlayerChromeState(
        scope = scope,
        autoHideTimeoutMillis = autoHideTimeoutMillis,
        bannerTimeoutMillis = bannerTimeoutMillis,
        isCovered = { infoOpen || optionsPage != null || screenLayerOpen() },
    )

    /** The focused control a quick list returns to. */
    private var lastFocusedControl: String? = initialFocusedControl
    private var quickListReturnControl: String? = null

    /** The control the chrome focuses when it comes back; the screen's own layers set it too. */
    var restoreControl by mutableStateOf<String?>(null)
    /** Closing Info into the controls returns focus to its button. */
    var restoreInfoFocus by mutableStateOf(false)
    /** Closing the options menu returns focus to the gear. */
    var restoreOptionsFocus by mutableStateOf(false)

    fun onActionFocused(action: String) {
        lastFocusedControl = action
    }

    /** The chrome focused its restore target: nothing is left to restore. */
    fun onFocusRestored() {
        restoreControl = null
        restoreInfoFocus = false
        restoreOptionsFocus = false
    }

    fun openInfo() {
        chrome.yieldToLayer(controls = false)
        closeScreenLayers()
        optionsPage = null
        optionsQuickList = false
        infoOpen = true
    }

    /** Info comes back as it was, under a screen layer that covered it. */
    fun reopenInfo() {
        infoOpen = true
    }

    /**
     * Closes Info: [controls] true reveals the controls with focus on the Info button,
     * false hides them, null leaves the chrome to the screen layer Info returns to.
     */
    fun closeInfo(controls: Boolean?) {
        infoOpen = false
        restoreInfoFocus = controls == true
        when (controls) {
            true -> chrome.showControls()
            false -> chrome.hideControls()
            null -> Unit
        }
    }

    /** A screen layer opens over the chrome in place of Info and the options. */
    fun yieldToScreenLayer() {
        chrome.yieldToLayer(controls = false)
        infoOpen = false
        optionsPage = null
        optionsQuickList = false
    }

    /** The options menu from its gear. */
    fun openOptions() {
        restoreOptionsFocus = false
        showOptionsPage(PlaybackOptionsPage.ROOT)
    }

    fun showOptionsPage(page: PlaybackOptionsPage) {
        chrome.yieldToLayer(controls = true)
        closeScreenLayers()
        infoOpen = false
        optionsQuickList = false
        optionsPage = page
    }

    /**
     * The Audio or Subtitles short list over whatever the controls show now: the
     * controls are neither revealed nor hidden, and closing the list returns focus to
     * the control that had it.
     */
    fun showQuickList(page: PlaybackOptionsPage) {
        if (!optionsQuickList) {
            quickListReturnControl = (optionsPage?.let { quickListOverMenuReturn } ?: lastFocusedControl)
                .takeIf { chrome.controlsVisible }
        }
        chrome.yieldToLayer()
        closeScreenLayers()
        infoOpen = false
        optionsQuickList = true
        optionsPage = page
    }

    /** Closes the quick list, keeping what is playing. */
    fun closeQuickList() {
        optionsPage = null
        optionsQuickList = false
        restoreControl = quickListReturnControl.takeIf { chrome.controlsVisible }
        quickListReturnControl = null
    }

    fun closeOptions() {
        optionsPage = null
        optionsQuickList = false
    }

    fun updateStatsVisibility(visible: Boolean) {
        statsVisible = visible
    }

    /** Every key down while a quick list is open restarts its auto-close. */
    fun onKeyDown() {
        if (optionsQuickList && optionsPage != null) quickList.onKeyDown()
    }

    fun beginOpeningKeyCycle(keyCode: Int) {
        revealingKeyCode = keyCode
    }

    fun endOpeningKeyCycle(keyCode: Int) {
        if (revealingKeyCode == keyCode) revealingKeyCode = null
    }

    /** The rest of the key that opened a layer: consumed, and its key-up ends the cycle. */
    fun consumesRevealingKey(event: KeyEvent): Boolean {
        val keyCode = event.nativeKeyEvent.keyCode
        if (!playbackSuppressesRevealingKey(revealingKeyCode, keyCode)) return false
        if (event.type == KeyEventType.KeyUp) endOpeningKeyCycle(keyCode)
        return true
    }

    /**
     * A Menu, audio-track or captions key: Menu opens the full options root, the
     * audio-track and captions keys their short list (the key of the open list moves
     * its focus down) over any other layer. The key cycle is held so the key-up cannot
     * activate the newly focused row. Returns false, leaving every layer untouched,
     * for any other key or while a modal confirmation owns the keys.
     */
    fun openOptionsForKey(
        keyContext: PlayerKeyContext,
        keyCode: Int,
    ): Boolean {
        if (playerKeyAction(keyContext, keyCode) != PlayerKeyAction.OPEN_OPTIONS) return false
        val outcome = playbackOptionsKeyOutcome(
            keyCode = keyCode,
            quickListPage = optionsPage.takeIf { optionsQuickList },
        ) ?: return false
        beginOpeningKeyCycle(keyCode)
        restoreOptionsFocus = false
        when (outcome) {
            PlaybackOptionsKeyOutcome.OpenMenu -> showOptionsPage(PlaybackOptionsPage.ROOT)
            PlaybackOptionsKeyOutcome.MoveDown -> quickList.moveDown()
            is PlaybackOptionsKeyOutcome.OpenQuickList -> showQuickList(outcome.page)
        }
        return true
    }

    /**
     * The overlay keys both players route alike: the opening key's cycle, Back, the
     * recovery and error layers, the options keys, and Left closing Info or the options.
     * Null lets the remaining playback keys proceed. The caller reports key downs to
     * [onKeyDown] first.
     */
    fun handleOverlayKey(
        event: KeyEvent,
        keyContext: PlayerKeyContext,
        foregroundLayer: PlayerForegroundLayer,
        quickListAvailable: Boolean,
        onBack: () -> Unit,
        onOptionsOpened: () -> Unit,
    ): Boolean? {
        val keyCode = event.nativeKeyEvent.keyCode
        if (consumesRevealingKey(event)) return true
        if (event.type != KeyEventType.KeyDown) return false
        if (keyCode == AndroidKeyEvent.KEYCODE_BACK) {
            if (event.nativeKeyEvent.repeatCount == 0) {
                beginOpeningKeyCycle(keyCode)
                onBack()
            }
            return true
        }
        if (foregroundLayer == PlayerForegroundLayer.RECOVERY || foregroundLayer == PlayerForegroundLayer.TERMINAL_ERROR) {
            return playerParentConsumesRecoveryKey(keyCode)
        }
        val requestedPage = playbackOptionsKeyRequest(keyCode)
        if (requestedPage != null) {
            // A quick list for a target that is gone swallows its key rather than open empty.
            if (requestedPage != PlaybackOptionsPage.ROOT && !quickListAvailable && !keyContext.confirmationOpen) {
                beginOpeningKeyCycle(keyCode)
                return true
            }
            val opened = openOptionsForKey(keyContext, keyCode)
            if (opened) onOptionsOpened()
            return opened
        }
        if (infoOpen || optionsPage != null) {
            if (keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT && foregroundLayer != PlayerForegroundLayer.CONFIRMATION) {
                beginOpeningKeyCycle(keyCode)
                onBack()
                return true
            }
            return false
        }
        return null
    }

    /** Applies a Back step on a shared layer; false leaves [action] to the screen. */
    fun applyBackAction(action: PlayerBackAction): Boolean {
        when (action) {
            PlayerBackAction.RESTORE_AND_CLOSE_QUICK_LIST -> {
                quickList.restoreStart()
                closeQuickList()
            }
            PlayerBackAction.RETURN_TO_OPTIONS_ROOT -> showOptionsPage(PlaybackOptionsPage.ROOT)
            PlayerBackAction.CLOSE_OPTIONS -> {
                closeOptions()
                restoreOptionsFocus = true
            }
            PlayerBackAction.HIDE_BANNER -> chrome.hideBanner()
            PlayerBackAction.HIDE_CONTROLS -> chrome.hideControls()
            PlayerBackAction.HIDE_STATS -> statsVisible = false
            else -> return false
        }
        return true
    }

    fun dispose() {
        chrome.dispose()
        revealingKeyCode = null
    }
}

@Composable
internal fun rememberPlayerLayerState(
    autoHideTimeoutMillis: Long,
    initialFocusedControl: String? = null,
): PlayerLayerState {
    val scope = rememberCoroutineScope()
    val state = remember(scope, autoHideTimeoutMillis) {
        PlayerLayerState(
            scope = scope,
            autoHideTimeoutMillis = autoHideTimeoutMillis,
            initialFocusedControl = initialFocusedControl,
        )
    }
    DisposableEffect(state) {
        onDispose(state::dispose)
    }
    return state
}
