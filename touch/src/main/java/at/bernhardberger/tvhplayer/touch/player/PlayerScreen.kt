@file:Suppress("MagicNumber", "LongParameterList", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.MultiWindowModeChangedInfo
import androidx.core.util.Consumer
import at.bernhardberger.tvhplayer.touch.R
import kotlinx.coroutines.delay

@Composable
internal fun PlayerScreen(
    state: LivePlayerState,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Int) -> Unit,
    onBrightness: (Float) -> Unit,
    onChannel: (String) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
    modifier: Modifier = Modifier,
    video: (@Composable (Modifier) -> Unit)? = null,
    onControlsShown: (Boolean) -> Unit = {},
    onInfoShown: (Boolean) -> Unit = {},
    inWindow: Boolean = false,
    hideAfterMs: Long? = null,
    progressing: Boolean = true,
    confirmationOpen: Boolean = false,
    notices: @Composable (Modifier) -> Unit = {},
) = PlayerFace(
    state, onAction, { onSeek((it / 1_000).toInt()) }, onBrightness, onChannel, onChoice, modifier, video,
    onControlsShown, onInfoShown, inWindow, hideAfterMs, progressing, confirmationOpen, notices,
)

@Composable
internal fun PlayerScreen(
    state: RecordingPlayerState,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Long) -> Unit,
    onBrightness: (Float) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
    modifier: Modifier = Modifier,
    video: (@Composable (Modifier) -> Unit)? = null,
    onControlsShown: (Boolean) -> Unit = {},
    inWindow: Boolean = false,
    hideAfterMs: Long? = null,
    progressing: Boolean = true,
    notices: @Composable (Modifier) -> Unit = {},
) = PlayerFace(
    state, onAction, onSeek, onBrightness, {}, onChoice, modifier, video,
    onControlsShown, {}, inWindow, hideAfterMs, progressing, false, notices,
)

@Composable
private fun PlayerFace(
    state: PlayerFaceState,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Long) -> Unit,
    onBrightness: (Float) -> Unit,
    onChannel: (String) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
    modifier: Modifier,
    video: (@Composable (Modifier) -> Unit)?,
    onControlsShown: (Boolean) -> Unit,
    onInfoShown: (Boolean) -> Unit,
    inWindow: Boolean,
    hideAfterMs: Long?,
    progressing: Boolean,
    confirmationOpen: Boolean,
    notices: @Composable (Modifier) -> Unit,
) {
    var visible by rememberSaveable(state.key) { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var touching by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<PlayerPanel?>(null) }
    // Info opens on the program being watched each time; inside the sheet the viewer opens others.
    var infoStack by rememberSaveable(state.key, stateSaver = InfoStackSaver) {
        mutableStateOf(InfoStack(followWatching = true))
    }
    val showPanel: (PlayerPanel?) -> Unit = {
        if (it == PlayerPanel.Info && panel?.button != it) infoStack = InfoStack(followWatching = true)
        panel = it
    }
    val openOptions = { panel = PlayerPanel.Options }
    var preview by remember(state.key, state.status) { mutableStateOf<Long?>(null) }
    val hideAfter = controlsHideAfter(
        visible, state.status, state.paused, progressing, dragging = preview != null, fingerDown = touching,
        panelOpen = panel != null || confirmationOpen, inWindow = inWindow, timeoutMs = hideAfterMs,
    )
    HideControlsAfter(hideAfter, touches) { visible = false }
    val face = modifier.fillMaxSize().background(Color.Black)
    BoxWithConstraints(face.followTouches { down -> touching = down; touches++ }) {
        // In the window the layout stays the one the player had, so the picture keeps its place in
        // the composition and its surface is never rebuilt across the transition.
        val held = remember { HeldLayout(playerLayout(maxWidth, maxHeight)) }
        if (!inWindow) held.layout = playerLayout(maxWidth, maxHeight)
        val layout = held.layout
        val panelGone = panelGone(panel, state, inWindow, layout)
        LaunchedEffect(panelGone) { if (panelGone) panel = null }
        val activePanel = panel.takeUnless { panelGone }.infoPanel(infoStack)
        val latestInfoShown by rememberUpdatedState(onInfoShown)
        val infoShown = activePanel?.button == PlayerPanel.Info
        LaunchedEffect(infoShown) { latestInfoShown(infoShown) }
        val panels = PanelHost(activePanel, showPanel, onChoice)
        val playing = state.status == LivePlayerStatus.Playing
        // A sheet takes the controls off the picture; a menu keeps them, it belongs to a button.
        val sheet = activePanel?.let { !it.atButton(layout) } == true
        val controls = !inWindow && !sheet && (visible || !playing)
        val toggle = ({ visible = !visible }).takeIf { playing && !inWindow }
        val reveal = ({ visible = true }).takeIf { toggle != null && !sheet }
        val opacity = playerMotion(controls, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "Player controls")
        val latestShown by rememberUpdatedState(onControlsShown)
        LaunchedEffect(controls) { latestShown(controls) }
        if (layout.upright) {
            UprightPlayer(
                state, layout, controls, toggle, video, inWindow, preview, { preview = it }, onAction,
                onBrightness, openOptions, onSeek, panels, onChannel, notices, opacity, reveal,
            )
        } else {
            LandscapePlayer(
                state, layout, controls, toggle, video, inWindow, preview, { preview = it }, onAction,
                onBrightness, openOptions, onSeek, panels, notices, opacity, reveal, sheet,
            )
        }
        // On the upright phone a sheet rises only to the picture's foot, so the picture stays in view.
        val belowPicture = maxHeight - WindowInsets.statusBars.asPaddingValues().calculateTopPadding() -
            maxWidth * (9f / 16)
        PlayerSheets(
            activePanel, state, layout, showPanel, onAction, onChoice, onChannel,
            InfoPage(infoStack) { infoStack = it }, belowPicture, onSeek,
        )
    }
}

@Composable
private fun BoxScope.LandscapePlayer(
    state: PlayerFaceState,
    layout: PlayerLayout,
    controls: Boolean,
    toggle: (() -> Unit)?,
    video: (@Composable (Modifier) -> Unit)?,
    inWindow: Boolean,
    preview: Long?,
    onPreview: (Long?) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onBrightness: (Float) -> Unit,
    openOptions: () -> Unit,
    onSeek: (Long) -> Unit,
    panels: PanelHost,
    notices: @Composable (Modifier) -> Unit,
    opacity: State<Float>,
    reveal: (() -> Unit)?,
    sheet: Boolean,
) {
    Picture(state, layout, controls, toggle, video, inWindow, opacity)
    if (inWindow) return
    val notice = remember(layout) { LandscapeNoticeAnchor() }
    val footer = state.status !is LivePlayerStatus.Reconnecting && state.status != LivePlayerStatus.PauseEnded
    PlayerChrome(controls, opacity, reveal) {
        if (state.shouldShade(video != null)) PictureShade(layout)
        PictureControls(state, layout, preview, onAction, onBrightness, openOptions, panels.onChoice)
        if (footer) LandscapePlayerContent(
            state, layout, preview, onAction, panels, onPreview, onSeek,
            Modifier.align(Alignment.BottomCenter), notice,
        )
    }
    if (controls && preview == null) PictureStatus(state, layout, onAction)
    LandscapeNotice(notice, layout, controls && footer, notices)
    if (state is RecordingPlayerState) LandscapeBareAdOffer(state, layout, !controls && !sheet, panels)
}

private fun PlayerFaceState.shouldShade(hasVideo: Boolean): Boolean =
    status == LivePlayerStatus.Playing && (hasVideo || frame != null)

private fun PlayerPanel?.infoPanel(stack: InfoStack): PlayerPanel? =
    if (this == PlayerPanel.InfoDetails && stack.shown == null) PlayerPanel.Info else this

private fun panelGone(panel: PlayerPanel?, state: PlayerFaceState, inWindow: Boolean, layout: PlayerLayout) = when {
    inWindow -> true
    panel == PlayerPanel.Channels && layout.upright -> true
    panel == PlayerPanel.Markers -> layout.upright || (state as? RecordingPlayerState)?.hasMarkers != true
    panel == PlayerPanel.AdBreaks && state is RecordingPlayerState -> !state.hasAdBreakOptions
    else -> panel?.button?.control?.let { it !in state.offered } == true
}

/** The upright layout: the picture on top with its controls, the program and the channel list below. */
@Composable
private fun UprightPlayer(
    state: PlayerFaceState,
    layout: PlayerLayout,
    controls: Boolean,
    toggle: (() -> Unit)?,
    video: (@Composable (Modifier) -> Unit)?,
    inWindow: Boolean,
    preview: Long?,
    onPreview: (Long?) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onBrightness: (Float) -> Unit,
    openOptions: () -> Unit,
    onSeek: (Long) -> Unit,
    panels: PanelHost,
    onChannel: (String) -> Unit,
    notices: @Composable (Modifier) -> Unit,
    opacity: State<Float>,
    reveal: (() -> Unit)?,
) = Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).background(Color.Black)) {
            Picture(state, layout, controls, toggle, video, inWindow, opacity)
            if (!inWindow) {
                val activity = LocalActivity.current as? ComponentActivity
                var inMultiWindow by remember(activity) { mutableStateOf(activity?.isInMultiWindowMode == true) }
                DisposableEffect(activity) {
                    val listener = Consumer<MultiWindowModeChangedInfo> { inMultiWindow = it.isInMultiWindowMode }
                    activity?.addOnMultiWindowModeChangedListener(listener)
                    onDispose { activity?.removeOnMultiWindowModeChangedListener(listener) }
                }
                UprightPictureChrome(
                    state.withFullScreenOffer(layout, inWindow = inMultiWindow), layout, controls, video != null,
                    preview, onPreview,
                    onAction, onBrightness,
                    openOptions, onSeek, panels.onChoice, opacity, reveal,
                )
                if (state is RecordingPlayerState) {
                    BareAdOffer(
                        state, !controls && panels.panel == null, panels.onChoice,
                        Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 22.dp),
                    )
                }
                // The upright tablet's notice stands over the picture, above its timeline.
                if (layout == PlayerLayout.Upright) {
                    notices(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 12.dp))
                }
            }
        }
        if (!inWindow) UprightPlayerContent(state, layout, preview != null, onAction, panels, onChannel, onSeek)
    }
    // The upright phone's notice stands at the window's foot, as in browsing, not over the picture.
    if (!inWindow && layout == PlayerLayout.Phone) {
        notices(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun BoxScope.UprightPictureChrome(
    state: PlayerFaceState,
    layout: PlayerLayout,
    controls: Boolean,
    hasVideo: Boolean,
    preview: Long?,
    onPreview: (Long?) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onBrightness: (Float) -> Unit,
    openOptions: () -> Unit,
    onSeek: (Long) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
    opacity: State<Float>,
    reveal: (() -> Unit)?,
) {
    val timeline = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
    if (!controls && state.status.hasTimeline) {
        FaceTimeline(state, layout, false, null, onPreview, onSeek, timeline)
    }
    PlayerChrome(controls, opacity, reveal) {
        if (state.shouldShade(hasVideo)) PictureShade(layout)
        PictureControls(state, layout, preview, onAction, onBrightness, openOptions, onChoice)
        if (state.status.hasTimeline) FaceTimeline(state, layout, true, preview, onPreview, onSeek, timeline)
    }
    if (controls && preview == null) PictureStatus(state, layout, onAction)
}

@Composable
private fun BoxScope.LandscapeBareAdOffer(
    state: RecordingPlayerState,
    layout: PlayerLayout,
    visible: Boolean,
    panels: PanelHost,
) {
    // On a tablet align the offer to the fitted picture, not the black band below it.
    val picture = if (layout == PlayerLayout.Tablet) {
        Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9)
    } else Modifier.matchParentSize()
    Box(picture) {
        BareAdOffer(
            state, visible, panels.onChoice,
            Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun BoxScope.Picture(
    state: PlayerFaceState,
    layout: PlayerLayout,
    controls: Boolean,
    toggle: (() -> Unit)?,
    video: (@Composable (Modifier) -> Unit)?,
    inWindow: Boolean,
    opacity: State<Float>,
) {
    val playing = state.status == LivePlayerStatus.Playing
    val reconnecting = state.status is LivePlayerStatus.Reconnecting
    val tall = layout == PlayerLayout.Tablet
    val frameModifier = when {
        layout.upright -> Modifier.fillMaxSize()
        tall -> Modifier.fillMaxWidth().aspectRatio(16f / 9)
        else -> Modifier.matchParentSize()
    }
    // The video stays composed through every status, so its surface lives as long as the screen.
    val still = state.frame != null && (playing || reconnecting)
    if (video != null || still) {
        if (layout == PlayerLayout.PhoneLandscape) {
            BoxWithConstraints(frameModifier) {
                val fitted = Modifier.align(Alignment.Center).width(maxHeight * (16f / 9)).height(maxHeight)
                Frame(state, layout, controls, fitted, video, inWindow, opacity)
            }
        } else {
            Frame(state, layout, controls, frameModifier.align(Alignment.Center), video, inWindow, opacity)
        }
    }
    if (toggle != null) {
        val label = stringResource(if (controls) R.string.player_hide_controls else R.string.player_show_controls)
        Box(
            Modifier.matchParentSize().testTag("player-picture").semantics { contentDescription = label }
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = toggle),
        )
    }
}

@Composable
private fun Frame(
    state: PlayerFaceState,
    layout: PlayerLayout,
    controls: Boolean,
    modifier: Modifier,
    video: (@Composable (Modifier) -> Unit)?,
    inWindow: Boolean,
    opacity: State<Float>,
) {
    val shown = state.status == LivePlayerStatus.Playing || state.status is LivePlayerStatus.Reconnecting
    Box(modifier) {
        if (video != null) {
            video(Modifier.matchParentSize())
        } else {
            Image(checkNotNull(state.frame), null, Modifier.matchParentSize(), contentScale = ContentScale.Fit)
        }
        if (!shown) {
            // Tuning, unavailable and pause ended show no picture, as drawn; a kept frame is not the channel's.
            Box(Modifier.matchParentSize().background(Color.Black))
        } else if (!inWindow) {
            val reconnecting = state.status is LivePlayerStatus.Reconnecting
            val shade = if (reconnecting && controls) chromeShade(layout) else bareShade
            // The bare picture's light gradient gives way to the shade in the controls layer.
            val blend = if (reconnecting) Modifier else Modifier.graphicsLayer {
                alpha = 1f - opacity.value.coerceIn(0f, 1f)
            }
            Box(
                Modifier.matchParentSize().then(blend).background(shade),
            )
            if (reconnecting) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .5f)))
            }
        }
    }
}

private val bareShade = pictureGradient(listOf(0f, 0f, .05f))

private fun pictureGradient(stops: List<Float>) = Brush.verticalGradient(stops.map { Color.Black.copy(alpha = it) })

private fun chromeShade(layout: PlayerLayout) = pictureGradient(when (layout) {
    PlayerLayout.Tablet -> listOf(.66f, .45f, .25f, .20f, .22f, .39f, .54f, .70f, .77f, .84f, .90f)
    PlayerLayout.PhoneLandscape -> listOf(.77f, .58f, .38f, .28f, .42f, .57f, .70f, .76f, .82f, .88f, .94f)
    else -> listOf(.58f, .49f, .39f, .32f, .32f, .41f, .54f, .68f, .74f, .79f, .85f)
})

@Composable
private fun BoxScope.PictureShade(layout: PlayerLayout) {
    val shade = remember(layout) { chromeShade(layout) }
    when (layout) {
        PlayerLayout.Tablet ->
            Box(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9).background(shade))
        PlayerLayout.PhoneLandscape -> BoxWithConstraints(Modifier.matchParentSize()) {
            Box(Modifier.align(Alignment.Center).width(maxHeight * (16f / 9)).height(maxHeight).background(shade))
        }
        else -> Box(Modifier.matchParentSize().background(shade))
    }
}

/** The layout last measured outside the window; not state, it only carries the value across compositions. */
private class HeldLayout(var layout: PlayerLayout)

private fun playerLayout(width: Dp, height: Dp) = when {
    width >= height && height >= 600.dp -> PlayerLayout.Tablet
    width >= height -> PlayerLayout.PhoneLandscape
    width >= 600.dp -> PlayerLayout.Upright
    else -> PlayerLayout.Phone
}

private val InfoStackSaver = listSaver<InfoStack, String?>(
    save = { listOf(it.followWatching.toString(), it.open) + it.details },
    restore = { InfoStack(it[1], it.drop(2).filterNotNull(), it.first().toBoolean()) },
)

/** Every touch, and the end of whatever held the controls, starts the count anew. */
@Composable
private fun HideControlsAfter(hideAfter: Long?, touches: Int, onHide: () -> Unit) {
    LaunchedEffect(hideAfter, touches) {
        if (hideAfter != null) {
            delay(hideAfter)
            onHide()
        }
    }
}

/**
 * Reports a finger going down and the last one lifting on the face, without taking the touch from
 * the controls below: a finger held on the bar or the brightness slider holds the controls.
 */
private fun Modifier.followTouches(onTouching: (Boolean) -> Unit) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            when (event.type) {
                PointerEventType.Press -> onTouching(true)
                PointerEventType.Release -> if (event.changes.none { it.pressed }) onTouching(false)
                else -> Unit
            }
        }
    }
}
