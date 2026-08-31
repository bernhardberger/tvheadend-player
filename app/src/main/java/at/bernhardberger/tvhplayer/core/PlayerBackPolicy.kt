package at.bernhardberger.tvhplayer.core

enum class PlayerSeekPreviewPhase {
    NONE,
    PENDING,
    DISPATCHED,
}

enum class PlayerForegroundLayer {
    CONFIRMATION,
    INFO,
    OPTIONS_DETAIL,
    OPTIONS_ROOT,
    NUMBER_ENTRY,
    CHANNEL_DRAWER,
    RECOVERY,
    TERMINAL_ERROR,
    PENDING_SEEK_PREVIEW,
    DISPATCHED_SEEK_PREVIEW,
    CONTROLS,
    STATS,
    NONE,
}

enum class PlayerBackAction {
    DISMISS_CONFIRMATION,
    CLOSE_INFO,
    RETURN_TO_OPTIONS_ROOT,
    CLOSE_OPTIONS,
    CLEAR_NUMBER_ENTRY,
    CLOSE_CHANNEL_DRAWER,
    CLOSE_PLAYER,
    CANCEL_PENDING_SEEK,
    DISMISS_SEEK_FEEDBACK,
    HIDE_CONTROLS,
    HIDE_STATS,
    CONSUME_WITHOUT_CHANGE,
}

data class PlayerForegroundContext(
    val confirmationVisible: Boolean,
    val infoVisible: Boolean,
    val optionsPage: PlaybackOptionsPage?,
    val numberEntryVisible: Boolean,
    val channelDrawerVisible: Boolean,
    val recoveryVisible: Boolean,
    val terminalErrorVisible: Boolean,
    val seekPreviewPhase: PlayerSeekPreviewPhase,
    val controlsVisible: Boolean,
    val statsEnabled: Boolean,
)

fun playerForegroundLayer(context: PlayerForegroundContext): PlayerForegroundLayer = when {
    context.confirmationVisible -> PlayerForegroundLayer.CONFIRMATION
    context.infoVisible -> PlayerForegroundLayer.INFO
    context.optionsPage != null && context.optionsPage != PlaybackOptionsPage.ROOT ->
        PlayerForegroundLayer.OPTIONS_DETAIL
    context.optionsPage == PlaybackOptionsPage.ROOT -> PlayerForegroundLayer.OPTIONS_ROOT
    context.numberEntryVisible -> PlayerForegroundLayer.NUMBER_ENTRY
    context.channelDrawerVisible -> PlayerForegroundLayer.CHANNEL_DRAWER
    context.recoveryVisible -> PlayerForegroundLayer.RECOVERY
    context.terminalErrorVisible -> PlayerForegroundLayer.TERMINAL_ERROR
    context.seekPreviewPhase == PlayerSeekPreviewPhase.PENDING ->
        PlayerForegroundLayer.PENDING_SEEK_PREVIEW
    context.seekPreviewPhase == PlayerSeekPreviewPhase.DISPATCHED ->
        PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW
    context.controlsVisible -> PlayerForegroundLayer.CONTROLS
    context.statsEnabled -> PlayerForegroundLayer.STATS
    else -> PlayerForegroundLayer.NONE
}

fun playerRootFocusRequired(foregroundLayer: PlayerForegroundLayer): Boolean =
    when (foregroundLayer) {
        PlayerForegroundLayer.NUMBER_ENTRY,
        PlayerForegroundLayer.PENDING_SEEK_PREVIEW,
        PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW,
        PlayerForegroundLayer.STATS,
        PlayerForegroundLayer.NONE -> true
        PlayerForegroundLayer.CONFIRMATION,
        PlayerForegroundLayer.INFO,
        PlayerForegroundLayer.OPTIONS_DETAIL,
        PlayerForegroundLayer.OPTIONS_ROOT,
        PlayerForegroundLayer.CHANNEL_DRAWER,
        PlayerForegroundLayer.RECOVERY,
        PlayerForegroundLayer.TERMINAL_ERROR,
        PlayerForegroundLayer.CONTROLS -> false
    }

fun playerBackAction(
    surface: PlayerSurface,
    playerCloseAllowed: Boolean,
    foregroundLayer: PlayerForegroundLayer,
): PlayerBackAction = when (foregroundLayer) {
    PlayerForegroundLayer.CONFIRMATION -> PlayerBackAction.DISMISS_CONFIRMATION
    PlayerForegroundLayer.INFO -> PlayerBackAction.CLOSE_INFO
    PlayerForegroundLayer.OPTIONS_DETAIL -> PlayerBackAction.RETURN_TO_OPTIONS_ROOT
    PlayerForegroundLayer.OPTIONS_ROOT -> PlayerBackAction.CLOSE_OPTIONS
    PlayerForegroundLayer.NUMBER_ENTRY -> PlayerBackAction.CLEAR_NUMBER_ENTRY
    PlayerForegroundLayer.CHANNEL_DRAWER -> PlayerBackAction.CLOSE_CHANNEL_DRAWER
    PlayerForegroundLayer.RECOVERY,
    PlayerForegroundLayer.TERMINAL_ERROR,
    PlayerForegroundLayer.NONE -> if (playerCloseAllowed) {
        PlayerBackAction.CLOSE_PLAYER
    } else {
        PlayerBackAction.CONSUME_WITHOUT_CHANGE
    }
    PlayerForegroundLayer.PENDING_SEEK_PREVIEW -> PlayerBackAction.CANCEL_PENDING_SEEK
    PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW -> PlayerBackAction.DISMISS_SEEK_FEEDBACK
    PlayerForegroundLayer.CONTROLS -> PlayerBackAction.HIDE_CONTROLS
    PlayerForegroundLayer.STATS -> PlayerBackAction.HIDE_STATS
}
