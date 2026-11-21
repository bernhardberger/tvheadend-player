package at.bernhardberger.tvhplayer.touch.player

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.view.View
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The player's picture-in-picture window, by the X1 trial's mechanics: the parameters follow the
 * picture's display aspect and on-screen bounds (the source rect hint), Android 12 and later enter
 * the window by themselves on leaving the app, and the per-app switch is watched so the button and
 * Back follow it. The activity owns when to enter; this owns how.
 */
@OptIn(UnstableApi::class)
internal class PlayerWindow(private val activity: ComponentActivity, private val player: ExoPlayer) {
    /** The device has the window and the app may use it. */
    val available = MutableStateFlow(activity.canUseWindow())

    /** The player shows in the window, or is on its way there. */
    val inWindow = MutableStateFlow(false)

    private var view: PlayerView? = null
    private val relayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
    private val videoSize = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) = update()
    }
    private val appSwitch = AppOpsManager.OnOpChangedListener { _, _ ->
        activity.runOnUiThread { if (!activity.isDestroyed) update() }
    }

    fun open() {
        activity.getSystemService(AppOpsManager::class.java)
            ?.startWatchingMode(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, activity.packageName, appSwitch)
        player.addListener(videoSize)
        update()
    }

    fun close() {
        activity.getSystemService(AppOpsManager::class.java)?.stopWatchingMode(appSwitch)
        player.removeListener(videoSize)
        attach(null)
    }

    /** The picture's view; its layout changes move the source rect hint. */
    fun attach(playerView: PlayerView?) {
        view?.let {
            it.removeOnLayoutChangeListener(relayout)
            it.videoSurfaceView?.removeOnLayoutChangeListener(relayout)
        }
        view = playerView
        playerView?.let {
            it.addOnLayoutChangeListener(relayout)
            it.videoSurfaceView?.addOnLayoutChangeListener(relayout)
        }
        update()
    }

    /** Back from the window: the parameters again once the full-screen layout has settled. */
    fun left() {
        inWindow.value = false
        view?.post(::update) ?: update()
    }

    fun update() {
        available.value = activity.canUseWindow()
        if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            activity.setPictureInPictureParams(params())
        }
    }

    /** False when the window can't be used, or the activity is finishing or already in it. */
    fun enter(): Boolean {
        available.value = activity.canUseWindow()
        if (!available.value || activity.isFinishing || activity.isInPictureInPictureMode) return false
        val entered = activity.enterPictureInPictureMode(params())
        if (entered) inWindow.value = true
        return entered
    }

    private fun params(): PictureInPictureParams = PictureInPictureParams.Builder().apply {
        windowAspect(pictureAspect(player))?.let { (width, height) -> setAspectRatio(Rational(width, height)) }
        // The picture's bounds also publish where the exit animation returns to.
        val rect = Rect()
        if (view?.videoSurfaceView?.getGlobalVisibleRect(rect) == true && !rect.isEmpty) setSourceRectHint(rect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setAutoEnterEnabled(available.value)
            setSeamlessResizeEnabled(true)
        }
    }.build()
}
