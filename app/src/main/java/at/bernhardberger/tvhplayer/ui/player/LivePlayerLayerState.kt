package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerForegroundContext
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LIVE_PLAYER_AUTO_HIDE_MS = 5_000L
internal const val LIVE_PLAYER_LAYER_TRANSITION_MS = 180

@Stable
internal class LivePlayerLayerState(
    private val scope: CoroutineScope,
    private val autoHideTimeoutMillis: Long,
) {
    var controlsVisible by mutableStateOf(true)
        private set

    var channelDrawerOpen by mutableStateOf(false)
        private set

    var optionsPage by mutableStateOf<PlaybackOptionsPage?>(null)
        private set

    var infoOpen by mutableStateOf(false)
        private set

    var recordingConfirmationVisible by mutableStateOf(false)
        private set

    var statsVisible by mutableStateOf(false)
        private set

    var revealingKeyCode by mutableStateOf<Int?>(null)
        private set

    private var autoHideEligible = false
    private var autoHideJob: Job? = null
    private var disposed = false

    fun showControls() {
        controlsVisible = true
        channelDrawerOpen = false
        restartAutoHideIfEligible()
    }

    fun hideControls() {
        controlsVisible = false
        suspendAutoHide()
    }

    fun openInfo() {
        suspendAutoHide()
        controlsVisible = false
        channelDrawerOpen = false
        optionsPage = null
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
        suspendAutoHide()
        controlsVisible = true
        channelDrawerOpen = false
        infoOpen = false
        recordingConfirmationVisible = false
        optionsPage = page
    }

    fun closeOptions() {
        optionsPage = null
    }

    fun openChannelDrawer() {
        suspendAutoHide()
        controlsVisible = false
        infoOpen = false
        recordingConfirmationVisible = false
        optionsPage = null
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
