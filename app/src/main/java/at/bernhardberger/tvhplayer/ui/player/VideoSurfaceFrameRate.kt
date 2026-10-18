package at.bernhardberger.tvhplayer.ui.player

import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateVote

/**
 * Asks the display for the video's frame rate on the player's SurfaceView, switching modes even
 * when that is not seamless. Main thread only: [request] from composition, the holder callbacks
 * from the view. A recreated surface gets the request again; a destroyed one and [release] clear it.
 */
internal class VideoSurfaceFrameRate : SurfaceHolder.Callback {
    private val vote = SurfaceFrameRateVote()
    private var holder: SurfaceHolder? = null

    fun attach(view: SurfaceView) {
        val surfaceHolder = view.holder
        holder = surfaceHolder
        surfaceHolder.addCallback(this)
        if (surfaceHolder.surface?.isValid == true) apply(surfaceHolder, vote.surfaceCreated())
    }

    /** [rate] in Hz, or null for no request; [media3Votes]: see [SurfaceFrameRateVote.request]. */
    fun request(rate: Float?, media3Votes: Boolean) {
        holder?.let { apply(it, vote.request(rate, media3Votes)) }
    }

    fun release() {
        val surfaceHolder = holder ?: return
        apply(surfaceHolder, vote.release())
        surfaceHolder.removeCallback(this)
        holder = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) = apply(holder, vote.surfaceCreated())

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) = apply(holder, vote.surfaceDestroyed())

    private fun apply(holder: SurfaceHolder, rate: Float?) {
        if (rate == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val surface = holder.surface?.takeIf { it.isValid } ?: return
        try {
            surface.setFrameRate(
                rate,
                if (rate == 0f) Surface.FRAME_RATE_COMPATIBILITY_DEFAULT else Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS,
            )
        } catch (_: IllegalStateException) {
            // The surface went away meanwhile; its vote went with it.
        } catch (_: IllegalArgumentException) {
            // A rate the platform rejects: keep the display as it is.
        }
    }
}
