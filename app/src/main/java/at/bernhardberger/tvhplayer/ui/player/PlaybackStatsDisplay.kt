package at.bernhardberger.tvhplayer.ui.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import at.bernhardberger.tvhplayer.playback.PlaybackFrameRate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal enum class MatchFrameRatePreference { NEVER, SEAMLESS_ONLY, ALWAYS }

internal data class DisplayModeSpec(val width: Int, val height: Int, val refreshRate: Float)

/** The active display mode and what the panel offers at its resolution, already formatted. */
internal data class DisplayOutput(
    val width: Int,
    val height: Int,
    val refreshRate: String,
    /** Every display mode's rates, grouped by resolution (largest first). */
    val refreshRatesByResolution: String,
    val matchFrameRate: MatchFrameRatePreference?,
)

/** Whole rates print bare; NTSC-style rates keep two decimals (23.98, 29.97, 59.94). */
internal fun formatRefreshRate(hz: Float): String {
    val whole = hz.roundToInt()
    if (abs(hz - whole) < 0.02f) return whole.toString()
    return String.format(Locale.ROOT, "%.2f", hz).trimEnd('0')
}

/**
 * Every rate the display offers, grouped by resolution, largest first: "2160p: 30 · 60 / 1080p:
 * 24 · 50 · 60". A height shared by two widths keeps the full size so the groups stay apart.
 */
internal fun refreshRatesByResolution(modes: List<DisplayModeSpec>): String {
    val groups = modes.groupBy { it.width to it.height }
        .toSortedMap(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
    return groups.entries.joinToString(" / ") { (size, group) ->
        val (width, height) = size
        val label = if (groups.keys.count { it.second == height } > 1) "$width×$height" else "${height}p"
        val rates = group.map { it.refreshRate }.sorted().map(::formatRefreshRate).distinct()
        "$label: ${rates.joinToString(" · ")}"
    }
}

/** Percent of same-time frames for the stats, or null when there are none. */
internal fun sameTimeFramePercent(share: Float): Int? =
    if (share <= 0f) null else (share * 100f).roundToInt().coerceIn(1, 100)

internal fun matchFrameRatePreference(value: Int): MatchFrameRatePreference? = when (value) {
    DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER -> MatchFrameRatePreference.NEVER
    DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY -> MatchFrameRatePreference.SEAMLESS_ONLY
    DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS -> MatchFrameRatePreference.ALWAYS
    else -> null
}

private fun readDisplayOutput(view: View, context: Context): DisplayOutput? {
    val manager = context.getSystemService(DisplayManager::class.java) ?: return null
    val display = view.display ?: manager.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
    val mode = display.mode
    val width = mode.physicalWidth
    val height = mode.physicalHeight
    return DisplayOutput(
        width = width,
        height = height,
        refreshRate = formatRefreshRate(mode.refreshRate),
        refreshRatesByResolution = refreshRatesByResolution(
            display.supportedModes.map { DisplayModeSpec(it.physicalWidth, it.physicalHeight, it.refreshRate) },
        ),
        matchFrameRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            matchFrameRatePreference(manager.matchContentFrameRateUserPreference)
        } else {
            null
        },
    )
}

/** Follows display mode switches while the stats are composed. */
@Composable
internal fun rememberDisplayOutput(): DisplayOutput? {
    val context = LocalContext.current
    val view = LocalView.current
    var output by remember(view, context) { mutableStateOf(readDisplayOutput(view, context)) }
    DisposableEffect(view, context) {
        val manager = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == (view.display?.displayId ?: Display.DEFAULT_DISPLAY)) {
                    output = readDisplayOutput(view, context)
                }
            }
        }
        manager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        output = readDisplayOutput(view, context)
        onDispose { manager?.unregisterDisplayListener(listener) }
    }
    return output
}

internal data class MeasuredFrameRate(val framesPerSecond: Float?, val sameTimePercent: Int?)

/** The runtime's measured video frame rate, sampled once a second while the stats are composed. */
@Composable
internal fun rememberMeasuredFrameRate(frameRate: PlaybackFrameRate?): MeasuredFrameRate? {
    var measured by remember(frameRate) { mutableStateOf<MeasuredFrameRate?>(null) }
    LaunchedEffect(frameRate) {
        while (frameRate != null) {
            measured = MeasuredFrameRate(frameRate.measured(), sameTimeFramePercent(frameRate.sameTimeShare()))
            delay(1_000L)
        }
    }
    return measured
}
