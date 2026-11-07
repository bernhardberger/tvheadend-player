package at.bernhardberger.tvhplayer.ui.player

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Window
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateChange
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateRequest
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateVote

/**
 * Asks the display for the video's frame rate on the player's SurfaceView, the only vote on it
 * (Media3's change strategy is Off). Main thread only: [request] from composition, the holder
 * callbacks from the view. A recreated surface gets the request again; a destroyed one clears
 * its surface vote. Display-mode requests use the hosting window instead and stay through
 * surface recreation during a mode switch. Browse retains non-seamless requests, comparing
 * against a latched clean observation without an app request, not a previously switched channel.
 * [release] clears both kinds of request and forgets that base mode.
 */
internal class VideoSurfaceFrameRate : SurfaceHolder.Callback {
    private val vote = SurfaceFrameRateVote()
    private var holder: SurfaceHolder? = null
    private var view: SurfaceView? = null
    private var window: Window? = null
    private var target = SurfaceFrameRateRequest()
    private var baseMode: DisplayModeSpec? = null
    private var lastRequestedRate: Float? = null

    fun attach(view: SurfaceView) {
        this.view = view
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        window = (context as? Activity)?.window
        val surfaceHolder = view.holder
        holder = surfaceHolder
        surfaceHolder.addCallback(this)
        if (surfaceHolder.surface?.isValid == true) apply(surfaceHolder, vote.surfaceCreated())
    }

    fun request(request: SurfaceFrameRateRequest, playerShown: Boolean) {
        baseMode = nextBaseMode(
            baseMode,
            (view?.display ?: window?.decorView?.display)?.mode?.toSpec(),
            unrequested = target.rate == null,
            lastRequestedRate = lastRequestedRate,
        )
        target = if (playerShown || keepVideoFrameRateInBrowse(request.change, request.rate, baseMode)) {
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
        setDisplayModeId(0)
        window = null
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
        val rate = target.rate
        if (target.change != SurfaceFrameRateChange.DISPLAY_MODE || rate == null) {
            setDisplayModeId(0)
            return
        }
        val display = view?.display ?: return
        val current = display.mode.toSpec()
        val selected = videoDisplayMode(rate, current, display.supportedModes.map { it.toSpec() })
        val preferred = window?.attributes?.preferredDisplayModeId ?: 0
        displayModeIdToRequest(selected, current, preferred)?.let(::setDisplayModeId)
    }

    private fun setDisplayModeId(modeId: Int) {
        val window = window ?: return
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId == modeId) return
        attributes.preferredDisplayModeId = modeId
        window.attributes = attributes
    }

    private fun Display.Mode.toSpec() = DisplayModeSpec(
        physicalWidth, physicalHeight, refreshRate, modeId,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alternativeRefreshRates.toList() else emptyList(),
    )

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
