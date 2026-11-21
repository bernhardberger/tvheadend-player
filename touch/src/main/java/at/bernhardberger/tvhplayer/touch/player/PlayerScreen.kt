@file:Suppress("MagicNumber", "LongParameterList")

package at.bernhardberger.tvhplayer.touch.player

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
) {
    var visible by rememberSaveable(state.channel.id) { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var touching by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<PlayerPanel?>(null) }
    // Info opens on the program being watched each time; inside the sheet the viewer opens others.
    var infoStack by rememberSaveable(state.channel.id, stateSaver = InfoStackSaver) {
        mutableStateOf(InfoStack(followWatching = true))
    }
    val showPanel: (PlayerPanel?) -> Unit = {
        if (it == PlayerPanel.Info && panel?.button != it) infoStack = InfoStack(followWatching = true)
        panel = it
    }
    val openOptions = { panel = PlayerPanel.Options }
    var preview by remember(state.channel.id, state.status) { mutableStateOf<Int?>(null) }
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
        val panelGone = panelGone(panel, state.offered, inWindow, layout)
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
        val toggle = if (playing && !inWindow) ({ visible = !visible }) else null
        val latestShown by rememberUpdatedState(onControlsShown)
        LaunchedEffect(controls) { latestShown(controls) }
        if (layout.upright) {
            UprightPlayer(
                state, layout, controls, toggle, video, inWindow, preview, { preview = it }, onAction,
                onBrightness, openOptions, onSeek, panels, onChannel, notices,
            )
        } else {
            Picture(state, layout, controls, toggle, video, inWindow)
            if (!inWindow) {
                PictureControls(state, layout, controls, preview, onAction, onBrightness, openOptions)
                LandscapeFooter(
                    state, layout, controls, sheet, preview, { preview = it }, onAction, panels, onSeek, notices,
                )
            }
        }
        // On the upright phone a sheet rises only to the picture's foot, so the picture stays in view.
        val belowPicture = maxHeight - WindowInsets.statusBars.asPaddingValues().calculateTopPadding() -
            maxWidth * (9f / 16)
        PlayerSheets(
            activePanel, state, layout, showPanel, onAction, onChoice, onChannel,
            InfoPage(infoStack) { infoStack = it }, belowPicture,
        )
    }
}

private fun PlayerPanel?.infoPanel(stack: InfoStack): PlayerPanel? =
    if (this == PlayerPanel.InfoDetails && stack.shown == null) PlayerPanel.Info else this

private fun panelGone(panel: PlayerPanel?, offered: Set<PlayerControl>, inWindow: Boolean, layout: PlayerLayout) =
    inWindow || (layout.upright && panel == PlayerPanel.Channels) ||
        panel?.button?.control?.let { it !in offered } == true

/** The upright layout: the picture on top with its controls, the program and the channel list below. */
@Composable
private fun UprightPlayer(
    state: LivePlayerState,
    layout: PlayerLayout,
    controls: Boolean,
    toggle: (() -> Unit)?,
    video: (@Composable (Modifier) -> Unit)?,
    inWindow: Boolean,
    preview: Int?,
    onPreview: (Int?) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onBrightness: (Float) -> Unit,
    openOptions: () -> Unit,
    onSeek: (Int) -> Unit,
    panels: PanelHost,
    onChannel: (String) -> Unit,
    notices: @Composable (Modifier) -> Unit,
) = Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).background(Color.Black)) {
            Picture(state, layout, controls, toggle, video, inWindow)
            if (!inWindow) {
                PictureControls(state, layout, controls, preview, onAction, onBrightness, openOptions)
                if (state.status.hasTimeline) {
                    PlayerTimeline(
                        state.timeline, state.status, layout, controls, preview, onPreview, onSeek,
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp),
                    )
                }
                // The upright tablet's notice stands over the picture, above its timeline.
                if (layout == PlayerLayout.Upright) {
                    notices(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 12.dp))
                }
            }
        }
        if (!inWindow) UprightPlayerContent(state, layout, preview != null, onAction, panels, onChannel)
    }
    // The upright phone's notice stands at the window's foot, as in browsing, not over the picture.
    if (!inWindow && layout == PlayerLayout.Phone) {
        notices(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun BoxScope.LandscapeFooter(
    state: LivePlayerState,
    layout: PlayerLayout,
    controls: Boolean,
    sheet: Boolean,
    preview: Int?,
    onPreview: (Int?) -> Unit,
    onAction: (PlayerAction) -> Unit,
    panels: PanelHost,
    onSeek: (Int) -> Unit,
    notices: @Composable (Modifier) -> Unit,
) {
    val showFooter = controls && state.status !is LivePlayerStatus.Reconnecting &&
        state.status != LivePlayerStatus.PauseEnded
    if (showFooter) {
        LandscapePlayerContent(
            state, layout, preview, onAction, panels, onPreview, onSeek, Modifier.align(Alignment.BottomCenter),
            notice = notices,
        )
    } else if (!controls && !sheet) {
        // Controls hidden: the thin line stays on the edge.
        PlayerTimeline(
            state.timeline, state.status, layout, false, null, {}, onSeek,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp).offset(y = 20.dp),
        )
    }
    if (!showFooter) {
        val margin = if (layout == PlayerLayout.Tablet) 40.dp else 48.dp
        // TouchNotice adds 16dp at the sides and 12dp below the snackbar.
        notices(Modifier.align(Alignment.BottomStart).padding(start = margin - 16.dp, bottom = margin - 12.dp))
    }
}

@Composable
private fun BoxScope.Picture(
    state: LivePlayerState,
    layout: PlayerLayout,
    controls: Boolean,
    toggle: (() -> Unit)?,
    video: (@Composable (Modifier) -> Unit)?,
    inWindow: Boolean,
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
                Frame(state, layout, controls, fitted, video, inWindow)
            }
        } else {
            Frame(state, layout, controls, frameModifier.align(Alignment.Center), video, inWindow)
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
    state: LivePlayerState,
    layout: PlayerLayout,
    controls: Boolean,
    modifier: Modifier,
    video: (@Composable (Modifier) -> Unit)?,
    inWindow: Boolean,
) {
    val shades = when {
        // The window shows the picture alone, without a scrim.
        inWindow -> listOf(0f, 0f)
        !controls -> listOf(0f, 0f, 0.05f)
        layout == PlayerLayout.Tablet -> listOf(.66f, .45f, .25f, .20f, .22f, .39f, .54f, .70f, .77f, .84f, .90f)
        layout == PlayerLayout.PhoneLandscape ->
            listOf(.77f, .58f, .38f, .28f, .42f, .57f, .70f, .76f, .82f, .88f, .94f)
        else -> listOf(.58f, .49f, .39f, .32f, .32f, .41f, .54f, .68f, .74f, .79f, .85f)
    }
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
        } else {
            Box(
                Modifier.matchParentSize()
                    .background(Brush.verticalGradient(shades.map { Color.Black.copy(alpha = it) })),
            )
            if (state.status is LivePlayerStatus.Reconnecting) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .5f)))
            }
        }
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

private val LivePlayerStatus.hasTimeline: Boolean
    get() = this == LivePlayerStatus.Playing || this == LivePlayerStatus.Tuning

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
