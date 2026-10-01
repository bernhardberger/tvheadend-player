package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.profiling.profileTrace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.playbackOptionsKeyRequest
import at.bernhardberger.tvhplayer.core.playbackSuppressesRevealingKey
import at.bernhardberger.tvhplayer.core.playerParentConsumesRecoveryKey
import at.bernhardberger.tvhplayer.core.PlaybackOptionsKeyOutcome
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerForegroundContext
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import at.bernhardberger.tvhplayer.core.playbackOptionsKeyOutcome
import at.bernhardberger.tvhplayer.core.playerKeyAction
import kotlinx.coroutines.CoroutineScope

private const val LIVE_PLAYER_AUTO_HIDE_MS = 5_000L

@Stable
internal class LivePlayerLayerState(
    scope: CoroutineScope,
    autoHideTimeoutMillis: Long,
    bannerTimeoutMillis: Long = PLAYER_BANNER_MS,
) {
    var channelDrawerOpen by mutableStateOf(false)
        private set

    var optionsPage by mutableStateOf<PlaybackOptionsPage?>(null)
        private set

    /** [optionsPage] is the Audio or Subtitles short list opened by its remote key. */
    var optionsQuickList by mutableStateOf(false)
        private set

    val quickList = PlaybackQuickListSignals()

    var infoOpen by mutableStateOf(false)
        private set

    var recordingConfirmationVisible by mutableStateOf(false)
        private set

    var statsVisible by mutableStateOf(false)
        private set

    var revealingKeyCode by mutableStateOf<Int?>(null)
        private set

    /** The Banner and the controls; the drawer, info panel and options menu cover them. */
    val chrome = PlayerChromeState(
        scope = scope,
        autoHideTimeoutMillis = autoHideTimeoutMillis,
        bannerTimeoutMillis = bannerTimeoutMillis,
        isCovered = { channelDrawerOpen || infoOpen || optionsPage != null },
    )

    private var lastFocusedAction: String? = null
    /** The focused control a quick list returns to; unlike the drawer's, it may be Stop. */
    private var lastFocusedControl: String? = null
    private var channelDrawerReturnAction: String? = null
    private var quickListReturnAction: String? = null
    var restoreChannelAction by mutableStateOf<String?>(null)
        private set

    fun onActionFocused(action: String) = profileTrace("P44:focus:control") {
        lastFocusedAction = action.takeIf { it in setOf("player-pause", PlayerIdentityCardTag, "player-record", "player-settings") }
        lastFocusedControl = lastFocusedAction ?: action.takeIf { it == "player-stop" }
    }

    fun onChannelActionRestored() {
        restoreChannelAction = null
    }

    fun dismissChannelDrawer() {
        restoreChannelAction = channelDrawerReturnAction ?: "player-pause"
        showControls()
    }

    fun onChannelTuneRequested() {
        if (chrome.hidden) {
            chrome.peekBanner()
            return
        }
        // Quick-zap owns focus until explicitly dismissed, including a failed tune.
        if (!channelDrawerOpen) chrome.showControls(PlayerControlsEntry.FADE)
    }

    /** A failed tune: with hidden chrome the centre message replaces the Banner. */
    fun onChannelUnavailable() {
        if (chrome.hidden) chrome.hideBanner() else onChannelTuneRequested()
    }

    /** A programme boundary on the watched channel at the live edge; the caller checks the policy. */
    fun onProgrammeChanged() {
        chrome.peekBanner()
    }

    /** Reveals the controls in place of the channel drawer. */
    fun showControls() {
        channelDrawerOpen = false
        chrome.showControls()
    }

    fun openInfo() {
        chrome.yieldToLayer(controls = false)
        channelDrawerOpen = false
        optionsPage = null
        optionsQuickList = false
        recordingConfirmationVisible = false
        infoOpen = true
    }

    fun closeInfo() {
        recordingConfirmationVisible = false
        infoOpen = false
        showControls()
    }

    fun showRecordingConfirmation() {
        if (infoOpen) recordingConfirmationVisible = true
    }

    fun dismissRecordingConfirmation() {
        recordingConfirmationVisible = false
    }

    fun openOptions() {
        showOptionsPage(PlaybackOptionsPage.ROOT)
    }

    fun showOptionsPage(page: PlaybackOptionsPage) {
        chrome.yieldToLayer(controls = true)
        channelDrawerOpen = false
        infoOpen = false
        recordingConfirmationVisible = false
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
            // Over the full menu, focus returns to the gear the menu belongs to.
            quickListReturnAction = (if (optionsPage != null) "player-settings" else lastFocusedControl)
                .takeIf { chrome.controlsVisible }
        }
        chrome.yieldToLayer()
        channelDrawerOpen = false
        infoOpen = false
        recordingConfirmationVisible = false
        optionsQuickList = true
        optionsPage = page
    }

    /** Closes the quick list, keeping what is playing. */
    fun closeQuickList() {
        optionsPage = null
        optionsQuickList = false
        restoreChannelAction = quickListReturnAction.takeIf { chrome.controlsVisible }
        quickListReturnAction = null
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
        when (outcome) {
            PlaybackOptionsKeyOutcome.OpenMenu -> showOptionsPage(PlaybackOptionsPage.ROOT)
            PlaybackOptionsKeyOutcome.MoveDown -> quickList.moveDown()
            is PlaybackOptionsKeyOutcome.OpenQuickList -> showQuickList(outcome.page)
        }
        return true
    }

    /** Shared live-screen overlay routing; null lets the remaining playback keys proceed. */
    fun handleOverlayKey(
        event: KeyEvent,
        keyContext: PlayerKeyContext,
        foregroundLayer: PlayerForegroundLayer,
        quickListAvailable: Boolean,
        onBack: () -> Unit,
        onOptionsOpened: (Boolean) -> Unit,
    ): Boolean? {
        val keyCode = event.nativeKeyEvent.keyCode
        if (event.type == KeyEventType.KeyDown) onKeyDown()
        if (playbackSuppressesRevealingKey(revealingKeyCode, keyCode)) {
            if (event.type == KeyEventType.KeyUp) endOpeningKeyCycle(keyCode)
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        if (keyCode == AndroidKeyEvent.KEYCODE_BACK) {
            if (event.nativeKeyEvent.repeatCount == 0) {
                beginOpeningKeyCycle(keyCode)
                onBack()
            }
            return true
        }
        if (foregroundLayer == PlayerForegroundLayer.RECOVERY) {
            return playerParentConsumesRecoveryKey(keyCode)
        }
        val requestedPage = playbackOptionsKeyRequest(keyCode)
        if (requestedPage != null) {
            if (requestedPage != PlaybackOptionsPage.ROOT && !quickListAvailable && !keyContext.confirmationOpen) {
                beginOpeningKeyCycle(keyCode)
                return true
            }
            val infoWasOpen = infoOpen
            val opened = openOptionsForKey(keyContext, keyCode)
            if (opened) onOptionsOpened(infoWasOpen)
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

    /** Every key down while a quick list is open restarts its auto-close. */
    fun onKeyDown() {
        if (optionsQuickList && optionsPage != null) quickList.onKeyDown()
    }

    fun closeOptions() {
        optionsPage = null
        optionsQuickList = false
    }

    fun openChannelDrawer() {
        channelDrawerReturnAction = lastFocusedAction.takeIf { chrome.controlsVisible }
        restoreChannelAction = null
        chrome.yieldToLayer(controls = false)
        infoOpen = false
        recordingConfirmationVisible = false
        optionsPage = null
        optionsQuickList = false
        channelDrawerOpen = true
    }

    fun closeChannelDrawer() {
        channelDrawerOpen = false
    }

    fun updateStatsVisibility(visible: Boolean) {
        statsVisible = visible
    }

    fun beginOpeningKeyCycle(keyCode: Int) {
        revealingKeyCode = keyCode
    }

    fun endOpeningKeyCycle(keyCode: Int) {
        if (revealingKeyCode == keyCode) revealingKeyCode = null
    }

    fun foregroundContext(
        numberEntryVisible: Boolean = false,
        recoveryVisible: Boolean = false,
        terminalErrorVisible: Boolean = false,
        seekPreviewPhase: PlayerSeekPreviewPhase = PlayerSeekPreviewPhase.NONE,
    ) = PlayerForegroundContext(
        confirmationVisible = infoOpen && recordingConfirmationVisible,
        infoVisible = infoOpen && !recordingConfirmationVisible,
        optionsPage = optionsPage,
        optionsQuickList = optionsQuickList && optionsPage != null,
        numberEntryVisible = numberEntryVisible,
        channelDrawerVisible = channelDrawerOpen && !chrome.controlsVisible && !infoOpen,
        recoveryVisible = recoveryVisible,
        terminalErrorVisible = terminalErrorVisible,
        seekPreviewPhase = seekPreviewPhase,
        controlsVisible = chrome.controlsVisible,
        statsEnabled = statsVisible,
    )

    fun dispose() {
        chrome.dispose()
        revealingKeyCode = null
    }
}

@Composable
internal fun rememberLivePlayerLayerState(
    autoHideTimeoutMillis: Long = LIVE_PLAYER_AUTO_HIDE_MS,
): LivePlayerLayerState {
    val scope = rememberCoroutineScope()
    val state = remember(scope, autoHideTimeoutMillis) {
        LivePlayerLayerState(
            scope = scope,
            autoHideTimeoutMillis = autoHideTimeoutMillis,
        )
    }
    DisposableEffect(state) {
        onDispose(state::dispose)
    }
    return state
}
