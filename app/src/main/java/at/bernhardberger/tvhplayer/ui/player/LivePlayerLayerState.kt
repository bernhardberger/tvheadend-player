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
import at.bernhardberger.tvhplayer.core.PlayerForegroundContext
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import kotlinx.coroutines.CoroutineScope

private const val LIVE_PLAYER_AUTO_HIDE_MS = 5_000L

/** Actions Back from the rail returns to: every action-row button the rail can be entered from. */
private val RailReturnActions = setOf(
    "player-pause", "player-stop", "player-audio", "player-subtitles", "player-info", "player-schedule", "player-settings",
)

/**
 * The live player's layers: the shared [core] plus the channel drawer, the schedule and
 * the recording confirmation only live TV has.
 */
@Stable
internal class LivePlayerLayerState(
    scope: CoroutineScope,
    autoHideTimeoutMillis: Long,
    bannerTimeoutMillis: Long = PLAYER_BANNER_MS,
) {
    var channelDrawerOpen by mutableStateOf(false)
        private set

    var scheduleOpen by mutableStateOf(false)
        private set
    var scheduleDetails by mutableStateOf<at.bernhardberger.tvheadend.sdk.core.EpgEvent?>(null)
        private set

    var recordingConfirmationVisible by mutableStateOf(false)
        private set

    /** Info, the options and the chrome; the drawer and the schedule cover the chrome too. */
    val core = PlayerLayerState(
        scope = scope,
        autoHideTimeoutMillis = autoHideTimeoutMillis,
        bannerTimeoutMillis = bannerTimeoutMillis,
        // Over the full menu, focus returns to the gear the menu belongs to.
        quickListOverMenuReturn = "player-settings",
        screenLayerOpen = { channelDrawerOpen || scheduleOpen },
        closeScreenLayers = {
            channelDrawerOpen = false
            recordingConfirmationVisible = false
        },
    )

    fun showScheduleDetails(event: at.bernhardberger.tvheadend.sdk.core.EpgEvent?) {
        scheduleDetails = event
    }

    private var scheduleReturnControls = true
    private var scheduleReturnLayer = PlayerForegroundLayer.CONTROLS
    private var scheduleReturnAction = "player-schedule"
    private var infoReturnControls = true
    private var railReturnControls = true

    fun openSchedule(fromKey: Boolean = false) {
        scheduleReturnControls = !fromKey || core.chrome.controlsVisible
        scheduleReturnLayer = if (fromKey) at.bernhardberger.tvhplayer.core.playerForegroundLayer(foregroundContext())
            else PlayerForegroundLayer.CONTROLS
        scheduleReturnAction = if (fromKey && core.chrome.controlsVisible) lastFocusedAction ?: "player-pause"
            else "player-schedule"
        recordingConfirmationVisible = false
        scheduleDetails = null
        channelDrawerOpen = false
        core.yieldToScreenLayer()
        scheduleOpen = true
    }

    fun closeSchedule(showControls: Boolean? = null) {
        scheduleOpen = false
        scheduleDetails = null
        if (showControls == null) when (scheduleReturnLayer) {
            PlayerForegroundLayer.INFO -> { core.reopenInfo(); return }
            PlayerForegroundLayer.CHANNEL_DRAWER -> { channelDrawerOpen = true; return }
            else -> Unit
        }
        if (showControls ?: scheduleReturnControls) {
            core.restoreControl = scheduleReturnAction
            this.showControls()
        } else { core.restoreControl = null; core.chrome.hideControls() }
    }

    /** Own GUIDE's whole cycle; navigation in the sheet must not become a player seek/reveal. */
    fun handleScheduleKey(
        event: KeyEvent,
        foregroundLayer: PlayerForegroundLayer = at.bernhardberger.tvhplayer.core.playerForegroundLayer(foregroundContext()),
        prepare: () -> Unit = {},
    ): Boolean? {
        val code = event.nativeKeyEvent.keyCode
        if (scheduleOpen && core.consumesRevealingKey(event)) return true
        if (code == AndroidKeyEvent.KEYCODE_GUIDE) {
            // Options own a preview/undo session; do not dispose it to cover it with Schedule.
            if (core.optionsPage != null) return true
            if (foregroundLayer in setOf(PlayerForegroundLayer.RECOVERY, PlayerForegroundLayer.TERMINAL_ERROR,
                    PlayerForegroundLayer.CONFIRMATION)) return true
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                core.beginOpeningKeyCycle(code)
                if (scheduleOpen) closeSchedule() else {
                    prepare()
                    openSchedule(fromKey = true)
                }
            }
            if (event.type == KeyEventType.KeyUp) core.endOpeningKeyCycle(code)
            return true
        }
        if (!scheduleOpen) return null
        if (at.bernhardberger.tvhplayer.core.ChannelNavigation.digitForKeyCode(code) != null ||
            at.bernhardberger.tvhplayer.core.ChannelNavigation.directionForKeyCode(code) != null) {
            if (event.type == KeyEventType.KeyDown) closeSchedule(showControls = false)
            return null
        }
        // The sheet owns Back through its rows, tabs and details, plus ordinary navigation.
        // Like Info, it suppresses playback/options shortcuts.
        return when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
            AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, AndroidKeyEvent.KEYCODE_BACK,
            AndroidKeyEvent.KEYCODE_MENU -> false
            else -> true
        }
    }

    var infoFromRail by mutableStateOf(false)
        private set
    /** The rail stays expanded while its page steps down to the schedule and back. */
    val channelRailPresented: Boolean get() = channelDrawerOpen || (core.infoOpen && infoFromRail)

    private var lastFocusedAction: String? = null
    private var channelDrawerReturnAction: String? = null

    fun onActionFocused(action: String) = profileTrace("P44:focus:control") {
        lastFocusedAction = action.takeIf { it in RailReturnActions } ?: lastFocusedAction
        core.onActionFocused(action)
    }

    fun dismissChannelDrawer() {
        channelDrawerOpen = false
        if (railReturnControls) {
            core.restoreControl = channelDrawerReturnAction ?: "player-pause"
            showControls()
        } else core.chrome.hideControls()
    }

    fun onChannelTuneRequested() {
        if (scheduleOpen) closeSchedule(showControls = false)
        if (core.chrome.hidden) {
            core.chrome.peekBanner()
            return
        }
        // Quick-zap owns focus until explicitly dismissed, including a failed tune.
        if (!channelDrawerOpen) core.chrome.showControls(PlayerControlsEntry.FADE)
    }

    /** A failed tune: with hidden chrome the centre message replaces the Banner. */
    fun onChannelUnavailable() {
        if (core.chrome.hidden) core.chrome.hideBanner() else onChannelTuneRequested()
    }

    /** A programme boundary on the watched channel at the live edge; the caller checks the policy. */
    fun onProgrammeChanged() {
        core.chrome.peekBanner()
    }

    /** Reveals the controls in place of the channel drawer. */
    fun showControls() {
        channelDrawerOpen = false
        core.chrome.showControls()
    }

    fun openInfo(fromKey: Boolean = false) {
        infoReturnControls = !fromKey || core.chrome.controlsVisible
        infoFromRail = channelDrawerOpen
        core.openInfo()
    }

    fun closeInfo(returnToRail: Boolean = false) {
        recordingConfirmationVisible = false
        if (returnToRail) {
            core.closeInfo(controls = null)
            // Resume the same rail visit, including its original controls focus target.
            channelDrawerOpen = true
            core.chrome.yieldToLayer(controls = false)
        } else core.closeInfo(controls = infoReturnControls)
    }

    fun showRecordingConfirmation() {
        if (core.infoOpen) recordingConfirmationVisible = true
    }

    fun dismissRecordingConfirmation() {
        recordingConfirmationVisible = false
    }

    fun openChannelDrawer(fromKey: Boolean = false) {
        railReturnControls = !fromKey || core.chrome.controlsVisible
        channelDrawerReturnAction = lastFocusedAction.takeIf { core.chrome.controlsVisible }
        core.restoreControl = null
        recordingConfirmationVisible = false
        core.yieldToScreenLayer()
        channelDrawerOpen = true
    }

    fun closeChannelDrawer() {
        channelDrawerOpen = false
    }

    fun foregroundContext(
        numberEntryVisible: Boolean = false,
        recoveryVisible: Boolean = false,
        terminalErrorVisible: Boolean = false,
        seekPreviewPhase: PlayerSeekPreviewPhase = PlayerSeekPreviewPhase.NONE,
    ) = PlayerForegroundContext(
        confirmationVisible = core.infoOpen && recordingConfirmationVisible,
        infoVisible = core.infoOpen && !recordingConfirmationVisible,
        optionsPage = core.optionsPage,
        optionsQuickList = core.optionsQuickList && core.optionsPage != null,
        numberEntryVisible = numberEntryVisible,
        channelDrawerVisible = channelDrawerOpen && !core.chrome.controlsVisible && !core.infoOpen,
        recoveryVisible = recoveryVisible,
        terminalErrorVisible = terminalErrorVisible,
        seekPreviewPhase = seekPreviewPhase,
        controlsVisible = core.chrome.controlsVisible,
        statsEnabled = core.statsVisible,
        scheduleVisible = scheduleOpen,
    )

    fun dispose() {
        core.dispose()
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
