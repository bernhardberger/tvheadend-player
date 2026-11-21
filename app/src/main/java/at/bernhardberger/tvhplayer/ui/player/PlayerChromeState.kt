package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val PLAYER_BANNER_MS = 5_000L

/**
 * The player chrome: the passive Banner and the full controls, their timeouts and how
 * the controls enter. The screen tells it through [isCovered] when one of its own
 * layers (a drawer, panel or menu) covers the chrome.
 */
@Stable
internal class PlayerChromeState(
    private val scope: CoroutineScope,
    private val autoHideTimeoutMillis: Long,
    private val bannerTimeoutMillis: Long = PLAYER_BANNER_MS,
    private val isCovered: () -> Boolean,
) {
    /** The player enters with the Banner from the first frame, never with the controls. */
    var controlsVisible by mutableStateOf(false)
        private set

    /** How the most recent request revealed the controls: a zap fades them in place. */
    var controlsEntry by mutableStateOf(PlayerControlsEntry.TRAVEL)
        private set

    /**
     * The passive Banner (info bar and top status cluster, no actions, nothing focusable)
     * shown on player enter and on a zap while the controls are hidden. It hides
     * [bannerTimeoutMillis] after the first frame is presented.
     */
    var bannerVisible by mutableStateOf(true)
        private set
    private var bannerFramePresented = false
    private var bannerHideJob: Job? = null
    /** Paused playback keeps the Banner up until Back or another layer replaces it. */
    private var bannerHeld = false

    private var autoHideEligible = false
    private var autoHideJob: Job? = null
    private var disposed = false

    /** Neither the controls nor a covering layer show: only the video, perhaps with the Banner. */
    val hidden: Boolean
        get() = !controlsVisible && !isCovered()

    /** The first frame of the current tune is presented (true) or a new tune is pending (false). */
    fun onBannerFramePresented(presented: Boolean) {
        if (bannerFramePresented == presented) return
        bannerFramePresented = presented
        if (!bannerVisible) return
        if (presented && !bannerHeld) restartBannerHide() else cancelBannerHide()
    }

    fun hideBanner() {
        cancelBannerHide()
        bannerVisible = false
    }

    private fun showBanner() {
        bannerVisible = true
        // A tune keeps the Banner until its first frame; a programme change starts at once.
        if (bannerFramePresented && !bannerHeld) restartBannerHide() else cancelBannerHide()
    }

    /**
     * A key on the hidden player that acts without the controls (pause, a quick step) shows the
     * Banner, or keeps it up for another full timeout.
     */
    fun peekBanner() {
        if (hidden) showBanner()
    }

    /** While paused the Banner stays up; resuming lets it hide after a full timeout. */
    fun holdBanner(held: Boolean) {
        if (bannerHeld == held) return
        bannerHeld = held
        if (!bannerVisible) return
        if (held) cancelBannerHide() else if (bannerFramePresented) restartBannerHide()
    }

    private fun restartBannerHide() {
        cancelBannerHide()
        if (disposed) return
        bannerHideJob = scope.launch {
            delay(bannerTimeoutMillis)
            bannerHideJob = null
            bannerVisible = false
        }
    }

    private fun cancelBannerHide() {
        bannerHideJob?.cancel()
        bannerHideJob = null
    }

    /**
     * Reveals the controls. Without an [entry] a reveal while the Banner shows takes its
     * content over instead of fading in a second copy.
     */
    fun showControls(entry: PlayerControlsEntry? = null) {
        controlsEntry = entry ?: if (bannerVisible) PlayerControlsEntry.FROM_BANNER else PlayerControlsEntry.TRAVEL
        hideBanner()
        controlsVisible = true
        restartAutoHideIfEligible()
    }

    fun hideControls() {
        controlsVisible = false
        suspendAutoHide()
    }

    /**
     * A covering layer takes over: the Banner hides, the controls stop auto-hiding and a
     * later reveal travels in. [controls] shows or hides the controls with it; null keeps them.
     */
    fun yieldToLayer(controls: Boolean? = null) {
        hideBanner()
        suspendAutoHide()
        controlsEntry = PlayerControlsEntry.TRAVEL
        if (controls != null) controlsVisible = controls
    }

    fun updateAutoHideEligibility(eligible: Boolean) {
        if (disposed || autoHideEligible == eligible) return
        autoHideEligible = eligible
        if (eligible) restartAutoHide() else cancelAutoHide()
    }

    fun onUserInteraction() {
        restartAutoHideIfEligible()
    }

    fun dispose() {
        disposed = true
        autoHideEligible = false
        cancelAutoHide()
        cancelBannerHide()
    }

    private fun suspendAutoHide() {
        autoHideEligible = false
        cancelAutoHide()
    }

    private fun restartAutoHideIfEligible() {
        if (autoHideEligible) restartAutoHide()
    }

    private fun restartAutoHide() {
        cancelAutoHide()
        autoHideJob = scope.launch {
            delay(autoHideTimeoutMillis)
            autoHideJob = null
            autoHideEligible = false
            controlsVisible = false
        }
    }

    private fun cancelAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = null
    }
}
