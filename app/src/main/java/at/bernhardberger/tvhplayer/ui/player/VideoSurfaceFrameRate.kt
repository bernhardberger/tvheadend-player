package at.bernhardberger.tvhplayer.ui.player

import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateChange
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateRequest
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateVote

/**
 * Asks the display for the video's frame rate on the player's SurfaceView, the only vote on it
 * (Media3's change strategy is Off). Main thread only: [request] from composition, the holder
 * callbacks from the view. A recreated surface gets the request again; a destroyed one and
 * [release] clear it.
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

    fun request(request: SurfaceFrameRateRequest) {
        holder?.let { apply(it, vote.request(request)) }
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

    /** Sets [request] on the surface; one without a rate clears the vote. */
    private fun apply(holder: SurfaceHolder, request: SurfaceFrameRateRequest?) {
        if (request == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val surface = holder.surface?.takeIf { it.isValid } ?: return
        val rate = request.rate
        try {
            when {
                rate == null -> surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && request.change != SurfaceFrameRateChange.SEAMLESS_DEFAULT ->
                    surface.setFrameRate(
                        rate,
                        Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        if (request.change == SurfaceFrameRateChange.ALWAYS) {
                            Surface.CHANGE_FRAME_RATE_ALWAYS
                        } else {
                            Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                        },
                    )
                else -> surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
            }
        } catch (_: IllegalStateException) {
            // The surface went away meanwhile; its vote went with it.
        } catch (_: IllegalArgumentException) {
            // A rate the platform rejects: keep the display as it is.
        }
    }
}
