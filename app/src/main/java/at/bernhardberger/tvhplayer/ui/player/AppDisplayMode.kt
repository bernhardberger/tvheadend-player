package at.bernhardberger.tvhplayer.ui.player

import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Window
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateChange

/**
 * The one writer of the window's preferred display mode. The app refresh rate from Settings is
 * the mode browse screens and stopped playback ask for. A legacy video mode replaces it; a
 * modern surface vote clears the window preference only if Android allows all switches. Main thread only.
 */
internal class AppDisplayMode private constructor(private val window: Window) {
    var appRate: Float? = null
        private set
    private var videoChange: SurfaceFrameRateChange? = null
    private var videoRate: Float? = null
    val display: Display? get() = window.decorView.display

    fun useAppRate(rate: Float?) {
        appRate = rate
        apply()
    }

    /** Also re-evaluates an unchanged rate: a recreated surface may follow a finished mode switch. */
    fun setVideoRequest(change: SurfaceFrameRateChange?, rate: Float?) {
        videoChange = change
        videoRate = rate
        apply()
    }

    private fun apply() {
        if (appRate == null && (videoChange != SurfaceFrameRateChange.DISPLAY_MODE || videoRate == null)) {
            setDisplayModeId(0)
            return
        }
        val display = display ?: return
        val current = display.mode.toSpec()
        val systemAllowsAnySwitch = videoChange == SurfaceFrameRateChange.ALWAYS && videoRate != null &&
            readMatchFrameRatePreference(window.context.getSystemService(DisplayManager::class.java)) ==
            MatchFrameRatePreference.ALWAYS
        val selected = windowDisplayMode(
            videoChange, videoRate, appRate, current, display.supportedModes.map { it.toSpec() },
            systemAllowsAnySwitch,
        )
        displayModeIdToRequest(selected, current, window.attributes.preferredDisplayModeId, pin = appRate != null)
            ?.let(::setDisplayModeId)
    }

    private fun setDisplayModeId(modeId: Int) {
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId == modeId) return
        attributes.preferredDisplayModeId = modeId
        window.attributes = attributes
    }

    companion object {
        fun of(window: Window): AppDisplayMode {
            val decor = window.decorView
            return decor.getTag(R.id.app_display_mode) as? AppDisplayMode
                ?: AppDisplayMode(window).also { decor.setTag(R.id.app_display_mode, it) }
        }
    }
}

internal fun Display.Mode.toSpec() = DisplayModeSpec(
    physicalWidth, physicalHeight, refreshRate, modeId,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alternativeRefreshRates.toList() else emptyList(),
)
