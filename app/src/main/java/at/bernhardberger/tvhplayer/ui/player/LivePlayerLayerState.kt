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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LIVE_PLAYER_AUTO_HIDE_MS = 5_000L
private const val LIVE_PLAYER_BANNER_MS = 5_000L

@Stable
internal class LivePlayerLayerState(
    private val scope: CoroutineScope,
    private val autoHideTimeoutMillis: Long,
    private val bannerTimeoutMillis: Long = LIVE_PLAYER_BANNER_MS,
) {
    var controlsVisible by mutableStateOf(true)
        private set

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

    /** New design: the info panel shows its "Stream & signal" details page. */
    var infoDetailsOpen by mutableStateOf(false)
        private set

    var recordingConfirmationVisible by mutableStateOf(false)
        private set

    var statsVisible by mutableStateOf(false)
        private set

    var revealingKeyCode by mutableStateOf<Int?>(null)
        private set

    /** How the most recent request revealed the controls: a zap fades them in place. */
    var controlsEntry by mutableStateOf(PlayerControlsEntry.TRAVEL)
        private set

    /**
     * New player design only: the passive Banner (info bar and top status cluster, no
     * actions, nothing focusable) shown on player enter and on a zap while the controls
     * are hidden. It hides [bannerTimeoutMillis] after the first frame is presented.
     */
    var bannerVisible by mutableStateOf(false)
        private set
    private var bannerEnabled = false
    private var entryUntouched = true
    private var bannerFramePresented = false
    private var bannerHideJob: Job? = null

    private var autoHideEligible = false
    private var autoHideJob: Job? = null
    private var disposed = false
    private var lastFocusedAction: String? = null
    private var channelDrawerReturnAction: String? = null
    private var quickListReturnAction: String? = null
    var restoreChannelAction by mutableStateOf<String?>(null)
        private set

    fun onActionFocused(action: String) = profileTrace("P44:focus:control") {
        lastFocusedAction = action.takeIf { it in setOf("player-pause", "player-info", "player-record", "player-settings") }
    }

    fun onChannelActionRestored() {
        restoreChannelAction = null
    }

    fun dismissChannelDrawer() {
        restoreChannelAction = channelDrawerReturnAction ?: "player-pause"
        showControls()
    }

    fun onChannelTuneRequested() {
        if (bannerEnabled && chromeHidden()) {
            showBanner()
            return
        }
        // Quick-zap owns focus until explicitly dismissed, including a failed tune.
        if (!channelDrawerOpen) revealControls(PlayerControlsEntry.FADE)
    }

    /** A failed tune: with the New design and hidden chrome the centre message replaces the Banner. */
    fun onChannelUnavailable() {
        if (bannerEnabled && chromeHidden()) hideBanner() else onChannelTuneRequested()
    }

    /**
     * Enables the Banner for the New design. On player enter, before any key, the
     * initial controls become the Banner.
     */
    fun enableBanner(enabled: Boolean) {
        if (bannerEnabled == enabled) return
        bannerEnabled = enabled
        if (!enabled) {
            hideBanner()
            return
        }
        if (entryUntouched && controlsVisible && !channelDrawerOpen && !infoOpen && optionsPage == null) {
            controlsVisible = false
            cancelAutoHide()
            showBanner()
        }
    }

    /** A programme boundary on the watched channel at the live edge; the caller checks the policy. */
    fun onProgrammeChanged() {
        if (bannerEnabled && chromeHidden()) showBanner()
    }

    /** The first frame of the current tune is presented (true) or a new tune is pending (false). */
    fun onBannerFramePresented(presented: Boolean) {
        if (bannerFramePresented == presented) return
        bannerFramePresented = presented
        if (!bannerVisible) return
        if (presented) restartBannerHide() else cancelBannerHide()
    }

    fun hideBanner() {
        cancelBannerHide()
        bannerVisible = false
    }

    private fun chromeHidden() = !controlsVisible && !channelDrawerOpen && !infoOpen && optionsPage == null

    private fun showBanner() {
        bannerVisible = true
        // A tune keeps the Banner until its first frame; a programme change starts at once.
        if (bannerFramePresented) restartBannerHide() else cancelBannerHide()
    }

    private fun restartBannerHide() {
        cancelBannerHide()
        bannerHideJob = scope.launch {
            delay(bannerTimeoutMillis)
            bannerHideJob = null
            bannerVisible = false
        }
    }

    private fun cancelBannerHide() {
        bannerHideJob?.cancel()
        bannerHideJob = null
    }

    fun showControls() = revealControls(PlayerControlsEntry.TRAVEL)

    private fun revealControls(entry: PlayerControlsEntry) {
        hideBanner()
        controlsEntry = entry
        controlsVisible = true
        channelDrawerOpen = false
        restartAutoHideIfEligible()
    }

    fun hideControls() {
        controlsVisible = false
        suspendAutoHide()
    }

    fun openInfo() {
        hideBanner()
        suspendAutoHide()
        controlsEntry = PlayerControlsEntry.TRAVEL
        controlsVisible = false
        channelDrawerOpen = false
        optionsPage = null
        optionsQuickList = false
        recordingConfirmationVisible = false
        infoOpen = true
        infoDetailsOpen = false
    }

    fun closeInfo() {
        recordingConfirmationVisible = false
        infoOpen = false
        infoDetailsOpen = false
        showControls()
    }

    fun openInfoDetails() {
        if (infoOpen && !recordingConfirmationVisible) infoDetailsOpen = true
    }

    /** Back from the details page returns to the info page. */
    fun closeInfoDetails() {
        infoDetailsOpen = false
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
        hideBanner()
        suspendAutoHide()
        controlsEntry = PlayerControlsEntry.TRAVEL
        controlsVisible = true
        channelDrawerOpen = false
        infoOpen = false
        infoDetailsOpen = false
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
            quickListReturnAction = (if (optionsPage != null) "player-settings" else lastFocusedAction)
                .takeIf { controlsVisible }
        }
        hideBanner()
        suspendAutoHide()
        controlsEntry = PlayerControlsEntry.TRAVEL
        channelDrawerOpen = false
        infoOpen = false
        infoDetailsOpen = false
        recordingConfirmationVisible = false
        optionsQuickList = true
        optionsPage = page
    }

    /** Closes the quick list, keeping what is playing. */
    fun closeQuickList() {
        optionsPage = null
        optionsQuickList = false
        restoreChannelAction = quickListReturnAction.takeIf { controlsVisible }
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
        entryUntouched = false
        if (optionsQuickList && optionsPage != null) quickList.onKeyDown()
    }

    fun closeOptions() {
        optionsPage = null
        optionsQuickList = false
    }

    fun openChannelDrawer() {
        channelDrawerReturnAction = lastFocusedAction.takeIf { controlsVisible }
        restoreChannelAction = null
        hideBanner()
        suspendAutoHide()
        controlsEntry = PlayerControlsEntry.TRAVEL
        controlsVisible = false
        infoOpen = false
        infoDetailsOpen = false
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

    fun updateAutoHideEligibility(eligible: Boolean) {
        if (disposed || autoHideEligible == eligible) return
        autoHideEligible = eligible
        if (eligible) restartAutoHide() else cancelAutoHide()
    }

    fun onUserInteraction() {
        restartAutoHideIfEligible()
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
        channelDrawerVisible = channelDrawerOpen && !controlsVisible && !infoOpen,
        recoveryVisible = recoveryVisible,
        terminalErrorVisible = terminalErrorVisible,
        seekPreviewPhase = seekPreviewPhase,
        controlsVisible = controlsVisible,
        statsEnabled = statsVisible,
    )

    fun dispose() {
        disposed = true
        autoHideEligible = false
        cancelAutoHide()
        cancelBannerHide()
        revealingKeyCode = null
    }

    private fun suspendAutoHide() {
        autoHideEligible = false
        cancelAutoHide()
    }

    private fun restartAutoHideIfEligible() {
        if (autoHideEligible) restartAutoHide()
    }

    private fun restartAutoHide() {
        cancelAutoHide()
        autoHideJob = scope.launch {
            delay(autoHideTimeoutMillis)
            autoHideJob = null
            autoHideEligible = false
            controlsVisible = false
        }
    }

    private fun cancelAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = null
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
