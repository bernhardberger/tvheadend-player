package at.bernhardberger.tvhplayer.touch.player

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.View
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
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

    val isInOrEnteringWindow: Boolean
        get() = inWindow.value || activity.isInPictureInPictureMode

    private var view: PlayerView? = null

    /** The display mode the picture is drawn in, so the window takes the same shape. */
    var mode: AspectRatioMode = AspectRatioMode.FIT
        set(value) {
            if (field == value) return
            field = value
            update()
        }
    private val relayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
    private val videoSize = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) = update()
    }
    private val appSwitch = AppOpsManager.OnOpChangedListener { _, _ -> activity.runOnUiThread(::update) }

    // Each resume sets the parameters: the first one after creation, and the return from the window
    // to full screen. A window closed with its X never resumes the activity again.
    private val resumed = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            inWindow.value = activity.isInPictureInPictureMode
            update()
        }
    }

    fun open() {
        activity.getSystemService(AppOpsManager::class.java)
            ?.startWatchingMode(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, activity.packageName, appSwitch)
        player.addListener(videoSize)
        activity.lifecycle.addObserver(resumed)
    }

    fun close() {
        activity.lifecycle.removeObserver(resumed)
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
            update()
        }
    }

    /** Out of the window: back to full screen (the next resume and layout set the parameters) or closed. */
    fun left() {
        inWindow.value = false
    }

    /**
     * Only while the activity is started and not finishing: a closed window stops and finishes it,
     * and the system has forgotten it by then.
     */
    fun update() {
        available.value = activity.canUseWindow()
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || activity.isFinishing) return
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        try {
            activity.setPictureInPictureParams(params())
        } catch (gone: IllegalStateException) {
            // Closing the window with its X removes the activity's token in the system before the
            // client hears of it ("setPictureInPictureParams: Can't find activity for token"); a
            // layout or video size change in that gap has nowhere to go, and nothing to update.
            Log.d(TAG, "Window closed before its parameters arrived", gone)
        }
    }

    /** False when the window can't be used, or the activity is finishing or already on its way in. */
    fun enter(): Boolean {
        available.value = activity.canUseWindow()
        if (!available.value || activity.isFinishing || isInOrEnteringWindow) return false
        val entered = try {
            activity.enterPictureInPictureMode(params())
        } catch (gone: IllegalStateException) {
            Log.d(TAG, "Window entry refused before the activity could enter", gone)
            false
        }
        if (entered) inWindow.value = true
        return entered
    }

    private companion object {
        const val TAG = "PlayerWindow"
    }

    private fun params(): PictureInPictureParams = PictureInPictureParams.Builder().apply {
        windowAspect(pictureAspect(player, mode))?.let { (width, height) -> setAspectRatio(Rational(width, height)) }
        // The picture's bounds also publish where the exit animation returns to.
        val rect = Rect()
        if (view?.videoSurfaceView?.getGlobalVisibleRect(rect) == true && !rect.isEmpty) setSourceRectHint(rect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setAutoEnterEnabled(available.value)
            setSeamlessResizeEnabled(true)
        }
    }.build()
}
