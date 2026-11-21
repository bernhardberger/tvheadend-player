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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R

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
) {
    var visible by rememberSaveable(state.channel.id) { mutableStateOf(true) }
    var panel by rememberSaveable { mutableStateOf<PlayerPanel?>(null) }
    // Info opens on the program being watched each time; inside the sheet the viewer opens others.
    var infoStack by rememberSaveable(stateSaver = InfoStackSaver) { mutableStateOf(InfoStack()) }
    val showPanel: (PlayerPanel?) -> Unit = {
        if (it == PlayerPanel.Info && panel?.button != it) infoStack = InfoStack(state.schedule?.watchingId)
        panel = it
    }
    val panels = PanelHost(panel, showPanel, onChoice)
    val openOptions = { panel = PlayerPanel.Options }
    var preview by remember(state.channel.id, state.status) { mutableStateOf<Int?>(null) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val layout = playerLayout(maxWidth, maxHeight)
        val playing = state.status == LivePlayerStatus.Playing
        // A sheet takes the controls off the picture; a menu keeps them, it belongs to a button.
        val sheet = panel?.let { !it.atButton(layout) } == true
        val controls = !sheet && (visible || !playing)
        val toggle = if (playing) ({ visible = !visible }) else null
        if (layout.upright) {
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).background(Color.Black)) {
                    Picture(state, layout, controls, toggle, video)
                    PictureControls(state, layout, controls, preview, onAction, onBrightness, openOptions)
                    val timeline = state.timeline
                    if (state.status.hasTimeline && timeline != null) {
                        PlayerTimeline(
                            timeline, state.status, layout, controls, preview, { preview = it }, onSeek,
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp),
                        )
                    }
                }
                UprightPlayerContent(state, layout, preview != null, onAction, panels, onChannel)
            }
        } else {
            Picture(state, layout, controls, toggle, video)
            PictureControls(state, layout, controls, preview, onAction, onBrightness, openOptions)
            LandscapeFooter(state, layout, controls, sheet, preview, { preview = it }, onAction, panels, onSeek)
        }
        // On the upright phone a sheet rises only to the picture's foot, so the picture stays in view.
        val belowPicture = maxHeight - WindowInsets.statusBars.asPaddingValues().calculateTopPadding() -
            maxWidth * (9f / 16)
        PlayerSheets(
            panel, state, layout, showPanel, onAction, onChoice, onChannel,
            InfoPage(infoStack) { infoStack = it }, belowPicture,
        )
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
) {
    val timeline = state.timeline
    val showFooter = controls && state.status !is LivePlayerStatus.Reconnecting &&
        state.status != LivePlayerStatus.PauseEnded
    if (showFooter) {
        LandscapePlayerContent(
            state, layout, preview, onAction, panels, onPreview, onSeek, Modifier.align(Alignment.BottomCenter),
        )
    } else if (!controls && !sheet && timeline != null) {
        // Controls hidden: the thin line stays on the edge.
        PlayerTimeline(
            timeline, state.status, layout, false, null, {}, onSeek,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp).offset(y = 20.dp),
        )
    }
}

@Composable
private fun BoxScope.Picture(
    state: LivePlayerState,
    layout: PlayerLayout,
    controls: Boolean,
    toggle: (() -> Unit)?,
    video: (@Composable (Modifier) -> Unit)?,
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
                Frame(state, layout, controls, fitted, video)
            }
        } else {
            Frame(state, layout, controls, frameModifier.align(Alignment.Center), video)
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
) {
    val shades = when {
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

private fun playerLayout(width: Dp, height: Dp) = when {
    width >= height && height >= 600.dp -> PlayerLayout.Tablet
    width >= height -> PlayerLayout.PhoneLandscape
    width >= 600.dp -> PlayerLayout.Upright
    else -> PlayerLayout.Phone
}

private val LivePlayerStatus.hasTimeline: Boolean
    get() = this == LivePlayerStatus.Playing || this == LivePlayerStatus.Tuning

private val InfoStackSaver = listSaver<InfoStack, String?>(
    save = { listOf(it.open) + it.details },
    restore = { InfoStack(it.first(), it.drop(1).filterNotNull()) },
)
