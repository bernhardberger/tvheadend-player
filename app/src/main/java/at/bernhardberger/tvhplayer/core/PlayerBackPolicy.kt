package at.bernhardberger.tvhplayer.core

enum class PlayerSeekPreviewPhase {
    NONE,
    PENDING,
    DISPATCHED,
}

enum class PlayerForegroundLayer {
    CONFIRMATION,
    INFO,
    /** The Audio or Subtitles short list opened by its remote key. */
    OPTIONS_QUICK_LIST,
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
    /** Put back what was selected when the quick list opened, then close it. */
    RESTORE_AND_CLOSE_QUICK_LIST,
    CLEAR_NUMBER_ENTRY,
    CLOSE_CHANNEL_DRAWER,
    CLOSE_PLAYER,
    CANCEL_PENDING_SEEK,
    DISMISS_SEEK_FEEDBACK,
    HIDE_CONTROLS,
    HIDE_STATS,
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
    /** [optionsPage] is shown as the short list of its remote key. */
    val optionsQuickList: Boolean = false,
)

fun playerForegroundLayer(context: PlayerForegroundContext): PlayerForegroundLayer = when {
    context.confirmationVisible -> PlayerForegroundLayer.CONFIRMATION
    context.infoVisible -> PlayerForegroundLayer.INFO
    context.optionsPage != null && context.optionsQuickList ->
        PlayerForegroundLayer.OPTIONS_QUICK_LIST
    context.optionsPage != null && context.optionsPage != PlaybackOptionsPage.ROOT ->
        PlayerForegroundLayer.OPTIONS_DETAIL
    context.optionsPage == PlaybackOptionsPage.ROOT -> PlayerForegroundLayer.OPTIONS_ROOT
    context.numberEntryVisible -> PlayerForegroundLayer.NUMBER_ENTRY
    context.channelDrawerVisible -> PlayerForegroundLayer.CHANNEL_DRAWER
    context.recoveryVisible -> PlayerForegroundLayer.RECOVERY
    context.terminalErrorVisible -> PlayerForegroundLayer.TERMINAL_ERROR
    context.controlsVisible -> PlayerForegroundLayer.CONTROLS
    context.seekPreviewPhase == PlayerSeekPreviewPhase.PENDING ->
        PlayerForegroundLayer.PENDING_SEEK_PREVIEW
    context.seekPreviewPhase == PlayerSeekPreviewPhase.DISPATCHED ->
        PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW
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
        PlayerForegroundLayer.OPTIONS_QUICK_LIST,
        PlayerForegroundLayer.OPTIONS_DETAIL,
        PlayerForegroundLayer.OPTIONS_ROOT,
        PlayerForegroundLayer.CHANNEL_DRAWER,
        PlayerForegroundLayer.RECOVERY,
        PlayerForegroundLayer.TERMINAL_ERROR,
        PlayerForegroundLayer.CONTROLS -> false
    }

fun playerBackAction(
    surface: PlayerSurface,
    foregroundLayer: PlayerForegroundLayer,
    seekPreviewPhase: PlayerSeekPreviewPhase = PlayerSeekPreviewPhase.NONE,
): PlayerBackAction = when (foregroundLayer) {
    PlayerForegroundLayer.CONFIRMATION -> PlayerBackAction.DISMISS_CONFIRMATION
    PlayerForegroundLayer.INFO -> PlayerBackAction.CLOSE_INFO
    PlayerForegroundLayer.OPTIONS_QUICK_LIST -> PlayerBackAction.RESTORE_AND_CLOSE_QUICK_LIST
    PlayerForegroundLayer.OPTIONS_DETAIL -> PlayerBackAction.RETURN_TO_OPTIONS_ROOT
    PlayerForegroundLayer.OPTIONS_ROOT -> PlayerBackAction.CLOSE_OPTIONS
    PlayerForegroundLayer.NUMBER_ENTRY -> PlayerBackAction.CLEAR_NUMBER_ENTRY
    PlayerForegroundLayer.CHANNEL_DRAWER -> PlayerBackAction.CLOSE_CHANNEL_DRAWER
    PlayerForegroundLayer.RECOVERY,
    PlayerForegroundLayer.TERMINAL_ERROR,
    PlayerForegroundLayer.NONE -> PlayerBackAction.CLOSE_PLAYER
    PlayerForegroundLayer.PENDING_SEEK_PREVIEW -> PlayerBackAction.CANCEL_PENDING_SEEK
    PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW -> PlayerBackAction.DISMISS_SEEK_FEEDBACK
    PlayerForegroundLayer.CONTROLS -> when (seekPreviewPhase) {
        PlayerSeekPreviewPhase.PENDING -> PlayerBackAction.CANCEL_PENDING_SEEK
        PlayerSeekPreviewPhase.DISPATCHED -> PlayerBackAction.DISMISS_SEEK_FEEDBACK
        PlayerSeekPreviewPhase.NONE -> PlayerBackAction.HIDE_CONTROLS
    }
    PlayerForegroundLayer.STATS -> PlayerBackAction.HIDE_STATS
}
