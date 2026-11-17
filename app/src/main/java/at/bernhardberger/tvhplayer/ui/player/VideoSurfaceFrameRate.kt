package at.bernhardberger.tvhplayer.ui.player

import android.app.Activity
import android.content.ContextWrapper
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
 * callbacks from the view. A recreated surface gets the request again; a destroyed one clears
 * its surface vote. Display-mode requests go to the window's [AppDisplayMode] instead and stay
 * through surface recreation during a mode switch; with an app refresh rate set, so do Always
 * ones. Browse retains non-seamless requests, comparing
 * against the app's baseline when supported, otherwise a latched clean observation, not a previously switched channel.
 * [release] clears both kinds of request, which returns the window to the app refresh rate, and
 * forgets that base mode.
 */
internal class VideoSurfaceFrameRate : SurfaceHolder.Callback {
    private val vote = SurfaceFrameRateVote()
    private var holder: SurfaceHolder? = null
    private var view: SurfaceView? = null
    private var displayMode: AppDisplayMode? = null
    private var target = SurfaceFrameRateRequest()
    private var baseMode: DisplayModeSpec? = null
    private var lastRequestedRate: Float? = null

    fun attach(view: SurfaceView) {
        this.view = view
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        displayMode = (context as? Activity)?.window?.let(AppDisplayMode::of)
        val surfaceHolder = view.holder
        holder = surfaceHolder
        surfaceHolder.addCallback(this)
        if (surfaceHolder.surface?.isValid == true) apply(surfaceHolder, vote.surfaceCreated())
    }

    fun request(request: SurfaceFrameRateRequest, playerShown: Boolean) {
        baseMode = nextBaseMode(
            baseMode,
            (view?.display ?: displayMode?.display)?.mode?.toSpec(),
            unrequested = target.rate == null,
            lastRequestedRate = lastRequestedRate,
        )
        val display = view?.display ?: displayMode?.display
        val browseBase = displayMode?.appRate?.let { rate ->
            display?.let { videoDisplayMode(rate, it.mode.toSpec(), it.supportedModes.map { mode -> mode.toSpec() }) }
        } ?: baseMode
        target = if (playerShown || keepVideoFrameRateInBrowse(request.change, request.rate, browseBase)) {
            request
        } else {
            SurfaceFrameRateRequest()
        }
        applyDisplayMode()
        val surfaceRequest = if (target.change == SurfaceFrameRateChange.DISPLAY_MODE) SurfaceFrameRateRequest() else target
        holder?.let { apply(it, vote.request(surfaceRequest)) }
        target.rate?.let { lastRequestedRate = it }
    }

    fun release() {
        displayMode?.setVideoRequest(null, null)
        displayMode = null
        view = null
        target = SurfaceFrameRateRequest()
        baseMode = null
        lastRequestedRate = null
        val surfaceHolder = holder ?: return
        apply(surfaceHolder, vote.release())
        surfaceHolder.removeCallback(this)
        holder = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        applyDisplayMode()
        apply(holder, vote.surfaceCreated())
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) = apply(holder, vote.surfaceDestroyed())

    private fun applyDisplayMode() {
        val displayMode = displayMode ?: return
        displayMode.setVideoRequest(target.change, target.rate)
    }

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
