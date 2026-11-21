package at.bernhardberger.tvhplayer.ui.player

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateChange
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateRequest
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateVote

// Give a mode switch time to recreate its surface, but don't leave a removed surface unpinned.
private const val SURFACE_RECREATION_GRACE_MILLIS = 2_000L

/**
 * Asks the display for the video's frame rate on the player's SurfaceView, the only vote on it
 * (Media3's change strategy is Off). Main thread only: [request] from composition, the holder
 * callbacks from the view. A recreated surface gets the request again; a destroyed one clears
 * its surface vote. Display-mode requests go to the window's [AppDisplayMode] instead and stay
 * through surface recreation during a mode switch. An installed Always surface vote can let
 * [AppDisplayMode] unpin the window. Browse retains non-seamless requests, comparing
 * against the app's baseline when supported, otherwise a latched clean observation, not a previously switched channel.
 * [release] clears both kinds of request, which returns the window to the app refresh rate, and
 * forgets that base mode.
 */
internal class VideoSurfaceFrameRate : SurfaceHolder.Callback {
    private val modernVotes = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    private val vote = SurfaceFrameRateVote(requiresAcknowledgement = modernVotes)
    private var holder: SurfaceHolder? = null
    private var view: SurfaceView? = null
    private var displayMode: AppDisplayMode? = null
    private var target = SurfaceFrameRateRequest()
    private var installedAlwaysRate: Float? = null
    private var restoreHandler: Handler? = null
    private val restoreAppRate = Runnable {
        restoreHandler = null
        displayMode?.setVideoRequest(target.change, null)
    }
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
        if (surfaceHolder.surface?.isValid == true) {
            cancelRestore()
            installVote(surfaceHolder, vote.surfaceCreated())
            if (target.change == SurfaceFrameRateChange.ALWAYS) applyDisplayMode()
        }
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
        val previous = target
        target = if (playerShown || keepVideoFrameRateInBrowse(request.change, request.rate, browseBase)) {
            request
        } else {
            SurfaceFrameRateRequest()
        }
        if (previous != target) cancelRestore()
        applyTarget()
        target.rate?.let { lastRequestedRate = it }
    }

    private fun applyTarget() {
        val modern = target.change == SurfaceFrameRateChange.ALWAYS
        if (!modern) applyDisplayMode()
        val surfaceRequest = if (target.change == SurfaceFrameRateChange.DISPLAY_MODE) SurfaceFrameRateRequest() else target
        val pending = vote.request(surfaceRequest)
        holder?.let { installVote(it, pending) }
        if (modern && restoreHandler == null) applyDisplayMode()
    }

    fun release() {
        cancelRestore()
        displayMode?.setVideoRequest(null, null)
        displayMode = null
        view = null
        target = SurfaceFrameRateRequest()
        installedAlwaysRate = null
        baseMode = null
        lastRequestedRate = null
        val pending = vote.release()
        val surfaceHolder = holder ?: return
        installVote(surfaceHolder, pending)
        surfaceHolder.removeCallback(this)
        holder = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        cancelRestore()
        val modern = target.change == SurfaceFrameRateChange.ALWAYS
        if (!modern) applyDisplayMode()
        installVote(holder, vote.surfaceCreated())
        if (modern) applyDisplayMode()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val hadInstalledVote = installedAlwaysRate != null
        installedAlwaysRate = null
        if (hadInstalledVote) {
            val handler = view?.handler
            if (handler != null && handler.postDelayed(restoreAppRate, SURFACE_RECREATION_GRACE_MILLIS)) {
                restoreHandler = handler
            } else {
                restoreAppRate.run()
            }
        }
        apply(holder, vote.surfaceDestroyed())
    }

    private fun cancelRestore() {
        restoreHandler?.removeCallbacks(restoreAppRate)
        restoreHandler = null
    }

    private fun applyDisplayMode() {
        val rate = if (target.change == SurfaceFrameRateChange.ALWAYS) installedAlwaysRate else target.rate
        displayMode?.setVideoRequest(target.change, rate)
    }

    private fun installVote(holder: SurfaceHolder, request: SurfaceFrameRateRequest?) {
        if (request == null) {
            if (holder.surface?.isValid != true) installedAlwaysRate = null
            return
        }
        val installed = apply(holder, request)
        installedAlwaysRate = request.rate.takeIf { installed && request.change == SurfaceFrameRateChange.ALWAYS }
        if (!modernVotes) return
        val clear = SurfaceFrameRateRequest()
        val applied = when {
            installed -> request
            request.rate != null && apply(holder, clear) -> clear
            else -> null
        }
        vote.acknowledge(applied)
    }

    /** Sets [request] on the surface; one without a rate clears the vote. */
    private fun apply(holder: SurfaceHolder, request: SurfaceFrameRateRequest?): Boolean {
        if (request == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val surface = holder.surface?.takeIf { it.isValid }
        val rate = request.rate
        return surface != null && try {
            when {
                rate == null -> surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && request.change == SurfaceFrameRateChange.ALWAYS ->
                    surface.setFrameRate(
                        rate,
                        Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ALWAYS,
                    )
                else -> surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
            }
            true
        } catch (_: IllegalStateException) {
            // The surface went away meanwhile; its vote went with it.
            false
        } catch (_: IllegalArgumentException) {
            // A rate the platform rejects: keep the display as it is.
            false
        }
    }
}
