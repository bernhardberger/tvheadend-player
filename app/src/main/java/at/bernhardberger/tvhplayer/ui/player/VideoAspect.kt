package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.settings.ChannelAspects
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val WIDE = 16f / 9f
private const val STANDARD = 4f / 3f
private const val SD_MAX_HEIGHT = 576
private const val NOMINAL_TOLERANCE = 0.03f

/**
 * The shape SD broadcast pictures are meant to be shown in. ITU-R BT.601 codes 720 samples across
 * a 702/704-sample active picture, so a 576-line 16:11 channel measures about 1.82:1 yet is a 16:9
 * programme; set-top boxes and TVs show it as exactly 16:9 (12:11 as 4:3). Returns that ratio for
 * an SD picture whose display aspect is within 3 % of 16:9 or 4:3, else null (HD, cinema shapes,
 * unknown size), which keeps the picture's own shape.
 */
internal fun broadcastNominalAspect(width: Int, height: Int, pixelWidthHeightRatio: Float): Float? {
    if (width <= 0 || height <= 0 || height > SD_MAX_HEIGHT) return null
    val display = width * (if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f) / height
    return listOf(WIDE, STANDARD).firstOrNull { abs(display / it - 1f) <= NOMINAL_TOLERANCE }
}

/**
 * The aspect the video surface is laid out at, or null to fill the screen and let the picture fit
 * itself. Auto snaps SD broadcast pictures to their nominal shape; the forced modes always win.
 */
internal fun surfaceFrameAspect(
    mode: AspectRatioMode,
    width: Int,
    height: Int,
    pixelWidthHeightRatio: Float,
): Float? = when (mode) {
    AspectRatioMode.FIT -> broadcastNominalAspect(width, height, pixelWidthHeightRatio)
    AspectRatioMode.FORCE_16_9 -> WIDE
    AspectRatioMode.FORCE_4_3 -> STANDARD
}

/**
 * The channel whose remembered display mode the picture of [target] uses: the live channel, or the
 * channel of the recording ([recordingChannel], null when the recording does not know it).
 */
internal fun aspectChannel(target: AppPlaybackTarget?, recordingChannel: (DvrEntryId) -> ChannelId?): ChannelId? =
    when (target) {
        is AppPlaybackTarget.Live -> target.channelId
        is AppPlaybackTarget.Recording -> recordingChannel(target.recordingId)
        null -> null
    }

/** The display mode in effect for the playing target and the way to change it for its channel. */
class TargetAspect(val mode: AspectRatioMode, val select: (AspectRatioMode) -> Unit)

/**
 * The single source of the display mode, created once in AppRoot and handed to the video surface
 * and to both player screens (options, stats), so they always read the same value. It is the
 * stored mode of the playing target's channel, so a zap brings the new channel's mode with its
 * picture. A selection with nothing to store it under (a recording without a channel, or no
 * server identity yet) is held in memory for the current target only, and is dropped when the
 * target changes.
 */
@Composable
internal fun rememberTargetAspect(
    target: AppPlaybackTarget?,
    store: PlayerSettingsStore,
    recordingChannel: (DvrEntryId) -> ChannelId?,
): TargetAspect {
    val scope = rememberCoroutineScope()
    val aspects by store.channelAspects.choices.collectAsStateWithLifecycle(ChannelAspects.None)
    val channel = aspectChannel(target, recordingChannel)
    var unsaved by remember(target) { mutableStateOf<AspectRatioMode?>(null) }
    val select = remember(store, scope, target, channel) {
        { mode: AspectRatioMode ->
            if (target != null) {
                scope.launch {
                    val stored = channel != null && store.channelAspects.set(channel, mode)
                    unsaved = if (stored) null else mode
                }
            }
            Unit
        }
    }
    return TargetAspect(unsaved ?: aspects.modeFor(channel), select)
}
