package at.bernhardberger.tvhplayer.touch.player

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import kotlin.math.roundToInt

/** What makes the player go: Back (system or the top bar's), the activity hidden, or the user leaving the app. */
internal enum class LeaveTrigger { Back, Hidden, UserLeave }

internal enum class Leave { EnterWindow, Stop, Stay }

/**
 * Back and leaving the app put the picture into the picture-in-picture window when the window is
 * available, and end playback when it is not (touch design section 5). The activity hidden outside
 * a rotation ends playback: in the window that is the window being closed, as in the X1 trial.
 */
internal fun leavePlayer(
    trigger: LeaveTrigger,
    windowAvailable: Boolean,
    inWindow: Boolean,
    changingConfigurations: Boolean,
): Leave = when (trigger) {
    LeaveTrigger.Back -> when {
        inWindow -> Leave.Stay
        windowAvailable -> Leave.EnterWindow
        else -> Leave.Stop
    }
    LeaveTrigger.Hidden -> if (changingConfigurations) Leave.Stay else Leave.Stop
    // Without the window the activity is hidden next, and that ends playback.
    LeaveTrigger.UserLeave -> if (windowAvailable && !inWindow) Leave.EnterWindow else Leave.Stay
}

/** The live channel the runtime plays or is starting; a failed or finished target plays nothing. */
internal fun playingChannelId(target: AppPlaybackTarget?, state: AppPlaybackState): ChannelId? =
    (target as? AppPlaybackTarget.Live)?.channelId
        ?.takeUnless { state is AppPlaybackState.Failed || state is AppPlaybackState.Finished }

/** Watch on the channel that plays brings its player back without tuning again; any other channel tunes. */
internal fun watchTunes(channelId: ChannelId, playingId: ChannelId?): Boolean = channelId != playingId

/**
 * The window's aspect as numerator and denominator, inside the 100:239…239:100 Android allows
 * (the X1 trial's clamp); null leaves it to the system.
 */
internal fun windowAspect(aspect: Float?): Pair<Int, Int>? = when {
    aspect == null || aspect.isNaN() || aspect <= 0f -> null
    aspect <= MIN_ASPECT -> ASPECT_SHORT to ASPECT_LONG
    aspect >= MAX_ASPECT -> ASPECT_LONG to ASPECT_SHORT
    else -> (aspect * ASPECT_SCALE).roundToInt() to ASPECT_SCALE
}

/**
 * The channel [direction] steps from [current] in [ordered], wrapping round, as the TV player steps
 * (app core/ChannelNavigation.kt `adjacentId`): a channel not in the list steps to the first.
 */
internal fun adjacentChannel(ordered: List<ChannelId>, current: ChannelId, direction: Int): ChannelId? {
    val index = ordered.indexOf(current)
    return when {
        ordered.isEmpty() -> null
        index < 0 -> ordered.first()
        else -> ordered[Math.floorMod(index + if (direction < 0) -1 else 1, ordered.size)]
    }
}

/** The device has the window and the per-app switch allows it (the X1 trial's check). */
internal fun Context.canUseWindow(): Boolean {
    val ops = getSystemService(AppOpsManager::class.java)
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) || ops == null) return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), packageName)
    } else {
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private const val ASPECT_SHORT = 100
private const val ASPECT_LONG = 239
private const val MIN_ASPECT = ASPECT_SHORT.toFloat() / ASPECT_LONG
private const val MAX_ASPECT = ASPECT_LONG.toFloat() / ASPECT_SHORT
private const val ASPECT_SCALE = 10_000
