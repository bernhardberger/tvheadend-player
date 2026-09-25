package at.bernhardberger.tvhplayer.core

import android.view.KeyEvent
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability

enum class MediaPlaybackAction {
    NONE,
    PLAY,
    PAUSE,
    TOGGLE,
}

enum class ChannelPickAction {
    CLOSE_DRAWER,
    TUNE,
}

enum class ChannelKeyAction {
    PAGE_LIST,
    TUNE,
}

enum class PlaybackOverlayFocusTarget {
    TIMESHIFT_TOGGLE,
    CHANNELS,
    CONTROLS_CLUSTER,
}

enum class PlayerSurface {
    LIVE,
    RECORDING,
}

enum class PlayerKeyAction {
    PASS_THROUGH,
    REVEAL_CONTROLS,
    REVEAL_AND_TOGGLE_PAUSE,
    HIDE_CONTROLS,
    OPEN_CHANNELS,
    OPEN_INFO,
    /** Opens the playback options; [playbackOptionsKeyOutcome] names the list. */
    OPEN_OPTIONS,
    SEEK_BACK,
    SEEK_FORWARD,
    CLOSE_PLAYER,
    DISMISS_OVERLAY_ONLY,
}

data class PlayerKeyContext(
    val surface: PlayerSurface,
    val controlsVisible: Boolean,
    val seekbarFocused: Boolean,
    val timeshiftAvailable: Boolean,
    val optionsOpen: Boolean = false,
    val infoOpen: Boolean = false,
    val statsOpen: Boolean = false,
    val drawerOpen: Boolean = false,
    /** A modal confirmation (e.g. the live recording dialog) owns the keys. */
    val confirmationOpen: Boolean = false,
    /** Live Pause state; a still-starting timeshift accepts a pause like an available one. */
    val livePause: LivePauseAvailability = LivePauseAvailability.NONE,
)

/** What a media play/pause key does on the live surface. */
enum class LiveMediaKeyAction {
    DISPATCH,
    REVEAL_WITH_REASON,
    PASS_THROUGH,
}

/** True while a live Pause press is accepted: timeshift available or still starting. */
fun livePauseAccepted(timeshiftAvailable: Boolean, livePause: LivePauseAvailability): Boolean =
    timeshiftAvailable || livePause == LivePauseAvailability.READY || livePause == LivePauseAvailability.STARTING

/** Dimmed live Pause (no grant, or timeshift off in Settings) explains itself instead of acting. */
fun livePauseUnavailable(livePause: LivePauseAvailability): Boolean =
    livePause == LivePauseAvailability.UNAVAILABLE || livePause == LivePauseAvailability.OFF

fun liveMediaKeyAction(timeshiftAvailable: Boolean, livePause: LivePauseAvailability): LiveMediaKeyAction = when {
    livePauseAccepted(timeshiftAvailable, livePause) -> LiveMediaKeyAction.DISPATCH
    livePauseUnavailable(livePause) -> LiveMediaKeyAction.REVEAL_WITH_REASON
    else -> LiveMediaKeyAction.PASS_THROUGH
}

fun initialPlaybackOverlayFocus(timeshiftAvailable: Boolean): PlaybackOverlayFocusTarget =
    PlaybackOverlayFocusTarget.CONTROLS_CLUSTER

fun shouldRevealPlaybackControls(controlsVisible: Boolean, keyCode: Int): Boolean {
    if (controlsVisible) return false
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN -> true
        else -> false
    }
}

fun playbackSuppressesRevealingKey(
    revealingKeyCode: Int?,
    keyCode: Int,
): Boolean = revealingKeyCode == keyCode

fun playerKeyActionStartsOpeningCycle(action: PlayerKeyAction): Boolean = when (action) {
    PlayerKeyAction.REVEAL_CONTROLS,
    PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE,
    PlayerKeyAction.OPEN_CHANNELS,
    PlayerKeyAction.OPEN_INFO,
    PlayerKeyAction.OPEN_OPTIONS -> true
    PlayerKeyAction.PASS_THROUGH,
    PlayerKeyAction.HIDE_CONTROLS,
    PlayerKeyAction.SEEK_BACK,
    PlayerKeyAction.SEEK_FORWARD,
    PlayerKeyAction.CLOSE_PLAYER,
    PlayerKeyAction.DISMISS_OVERLAY_ONLY -> false
}

/**
 * The options page a dedicated remote key asks for: Menu opens the root, the
 * audio-track key the Audio page and the captions key the Subtitles page.
 */
fun playbackOptionsKeyRequest(keyCode: Int): PlaybackOptionsPage? = when (keyCode) {
    KeyEvent.KEYCODE_MENU -> PlaybackOptionsPage.ROOT
    KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> PlaybackOptionsPage.AUDIO
    KeyEvent.KEYCODE_CAPTIONS -> PlaybackOptionsPage.SUBTITLES
    else -> null
}

/** What an options key does given the Audio or Subtitles quick list open right now. */
sealed interface PlaybackOptionsKeyOutcome {
    /** Menu: the full options menu at its root, keeping the current selections. */
    data object OpenMenu : PlaybackOptionsKeyOutcome

    /** Audio or captions key: the short list of that page only. */
    data class OpenQuickList(val page: PlaybackOptionsPage) : PlaybackOptionsKeyOutcome

    /** The key of the quick list already open: focus moves down one row and wraps. */
    data object MoveDown : PlaybackOptionsKeyOutcome
}

/**
 * The outcome of an options key. [quickListPage] is the page of the open quick
 * list, or null when none is open (the full menu does not count). The other list
 * key switches lists without undoing what is playing.
 */
fun playbackOptionsKeyOutcome(
    keyCode: Int,
    quickListPage: PlaybackOptionsPage?,
): PlaybackOptionsKeyOutcome? = when (val page = playbackOptionsKeyRequest(keyCode)) {
    null -> null
    PlaybackOptionsPage.ROOT -> PlaybackOptionsKeyOutcome.OpenMenu
    quickListPage -> PlaybackOptionsKeyOutcome.MoveDown
    else -> PlaybackOptionsKeyOutcome.OpenQuickList(page)
}

fun playerParentConsumesRecoveryKey(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_BACK -> false
    else -> true
}

/**
 * Hidden-control player key contract.
 *
 * Center on seekable media both toggles pause/play and reveals controls; the
 * revealing key cycle must be suppressed so the same press cannot activate a
 * newly focused control.
 */
fun playerKeyAction(
    context: PlayerKeyContext,
    keyCode: Int,
): PlayerKeyAction {
    if (playbackOptionsKeyRequest(keyCode) != null) {
        // Options keys replace Info, the drawer or another options page; a modal
        // confirmation keeps them inert.
        return if (context.confirmationOpen) {
            PlayerKeyAction.PASS_THROUGH
        } else {
            PlayerKeyAction.OPEN_OPTIONS
        }
    }
    if (context.infoOpen && keyCode != KeyEvent.KEYCODE_BACK) {
        return PlayerKeyAction.PASS_THROUGH
    }
    if (keyCode == KeyEvent.KEYCODE_INFO) return PlayerKeyAction.OPEN_INFO
    if (
        keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU ||
        keyCode == KeyEvent.KEYCODE_TV_NUMBER_ENTRY ||
        keyCode == KeyEvent.KEYCODE_BOOKMARK
    ) {
        // Dedicated list keys open the live channel shelf.
        return if (context.surface == PlayerSurface.LIVE) {
            PlayerKeyAction.OPEN_CHANNELS
        } else {
            PlayerKeyAction.PASS_THROUGH
        }
    }
    if (context.seekbarFocused) {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> PlayerKeyAction.SEEK_BACK
            KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerKeyAction.SEEK_FORWARD
            KeyEvent.KEYCODE_BACK -> when {
                context.infoOpen || context.optionsOpen || context.statsOpen ||
                    context.drawerOpen -> PlayerKeyAction.DISMISS_OVERLAY_ONLY
                context.controlsVisible -> PlayerKeyAction.HIDE_CONTROLS
                else -> PlayerKeyAction.CLOSE_PLAYER
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> PlayerKeyAction.PASS_THROUGH
            else -> PlayerKeyAction.PASS_THROUGH
        }
    }

    if (context.controlsVisible) {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> when {
                context.infoOpen || context.optionsOpen || context.statsOpen ||
                    context.drawerOpen -> PlayerKeyAction.DISMISS_OVERLAY_ONLY
                else -> PlayerKeyAction.HIDE_CONTROLS
            }
            else -> PlayerKeyAction.PASS_THROUGH
        }
    }

    // Controls hidden.
    return when (keyCode) {
        KeyEvent.KEYCODE_BACK -> when {
            context.infoOpen || context.optionsOpen || context.statsOpen ||
                context.drawerOpen -> PlayerKeyAction.DISMISS_OVERLAY_ONLY
            else -> PlayerKeyAction.CLOSE_PLAYER
        }
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> when {
            context.surface == PlayerSurface.RECORDING ||
                livePauseAccepted(context.timeshiftAvailable, context.livePause) ->
                PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE
            else -> PlayerKeyAction.REVEAL_CONTROLS
        }
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN -> PlayerKeyAction.REVEAL_CONTROLS
        KeyEvent.KEYCODE_DPAD_LEFT -> when {
            context.surface == PlayerSurface.RECORDING || context.timeshiftAvailable ->
                PlayerKeyAction.SEEK_BACK
            context.surface == PlayerSurface.LIVE ->
                PlayerKeyAction.OPEN_CHANNELS
            else -> PlayerKeyAction.PASS_THROUGH
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> when {
            context.surface == PlayerSurface.RECORDING || context.timeshiftAvailable ->
                PlayerKeyAction.SEEK_FORWARD
            else -> PlayerKeyAction.PASS_THROUGH
        }
        else -> PlayerKeyAction.PASS_THROUGH
    }
}

fun channelPickAction(currentChannelId: ChannelId?, pickedChannelId: ChannelId): ChannelPickAction =
    if (currentChannelId == pickedChannelId) {
        ChannelPickAction.CLOSE_DRAWER
    } else {
        ChannelPickAction.TUNE
    }

fun playbackChannelKeyAction(browserVisible: Boolean): ChannelKeyAction =
    ChannelKeyAction.TUNE

fun mediaPlaybackAction(
    keyCode: Int,
    playKeyCode: Int,
    pauseKeyCode: Int,
    toggleKeyCode: Int,
    repeatCount: Int = 0,
): MediaPlaybackAction = if (repeatCount != 0) MediaPlaybackAction.NONE else when (keyCode) {
    playKeyCode -> MediaPlaybackAction.PLAY
    pauseKeyCode -> MediaPlaybackAction.PAUSE
    toggleKeyCode -> MediaPlaybackAction.TOGGLE
    else -> MediaPlaybackAction.NONE
}
