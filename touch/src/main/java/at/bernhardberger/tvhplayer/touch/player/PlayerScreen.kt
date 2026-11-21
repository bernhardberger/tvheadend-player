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
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R

@Composable
internal fun PlayerScreen(
    state: LivePlayerState,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Int) -> Unit,
    onBrightness: (Float) -> Unit,
    onChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable(state.channel.id) { mutableStateOf(true) }
    var preview by remember(state.channel.id, state.status) { mutableStateOf<Int?>(null) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val layout = when {
            maxWidth >= maxHeight && maxHeight >= 600.dp -> PlayerLayout.Tablet
            maxWidth >= maxHeight -> PlayerLayout.PhoneLandscape
            maxWidth >= 600.dp -> PlayerLayout.Upright
            else -> PlayerLayout.Phone
        }
        val playing = state.status == LivePlayerStatus.Playing
        val controls = visible || !playing
        val toggle = if (playing) ({ visible = !visible }) else null
        if (layout.upright) {
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).background(Color.Black)) {
                    Picture(state, layout, controls, toggle)
                    PictureControls(state, layout, controls, preview, onAction, onBrightness)
                    if (state.status.hasTimeline) {
                        PlayerTimeline(
                            state.timeline, state.status, layout, controls, preview, { preview = it }, onSeek,
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp),
                        )
                    }
                }
                UprightPlayerContent(state, layout, preview != null, onAction, onChannel)
            }
        } else {
            Picture(state, layout, controls, toggle)
            PictureControls(state, layout, controls, preview, onAction, onBrightness)
            val showFooter = controls && state.status !is LivePlayerStatus.Reconnecting &&
                state.status != LivePlayerStatus.PauseEnded
            if (showFooter) {
                LandscapePlayerContent(
                    state, layout, preview, onAction, { preview = it }, onSeek,
                    Modifier.align(Alignment.BottomCenter),
                )
            } else if (!controls) {
                PlayerTimeline(
                    state.timeline, state.status, layout, false, null, {}, onSeek,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp).offset(y = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.Picture(
    state: LivePlayerState,
    layout: PlayerLayout,
    controls: Boolean,
    toggle: (() -> Unit)?,
) {
    val playing = state.status == LivePlayerStatus.Playing
    val reconnecting = state.status is LivePlayerStatus.Reconnecting
    val tall = layout == PlayerLayout.Tablet
    val frameModifier = when {
        layout.upright -> Modifier.fillMaxSize()
        tall -> Modifier.fillMaxWidth().aspectRatio(16f / 9)
        else -> Modifier.matchParentSize()
    }
    if (state.frame != null && (playing || reconnecting)) {
        if (layout == PlayerLayout.PhoneLandscape) {
            BoxWithConstraints(frameModifier) {
                val fitted = Modifier.align(Alignment.Center).width(maxHeight * (16f / 9)).height(maxHeight)
                Frame(state, layout, controls, fitted)
            }
        } else {
            Frame(state, layout, controls, frameModifier.align(Alignment.Center))
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
private fun Frame(state: LivePlayerState, layout: PlayerLayout, controls: Boolean, modifier: Modifier) {
    val shades = when {
        !controls -> listOf(0f, 0f, 0.05f)
        layout == PlayerLayout.Tablet -> listOf(.66f, .45f, .25f, .20f, .22f, .39f, .54f, .70f, .77f, .84f, .90f)
        layout == PlayerLayout.PhoneLandscape ->
            listOf(.77f, .58f, .38f, .28f, .42f, .57f, .70f, .76f, .82f, .88f, .94f)
        else -> listOf(.58f, .49f, .39f, .32f, .32f, .41f, .54f, .68f, .74f, .79f, .85f)
    }
    Box(modifier) {
        Image(checkNotNull(state.frame), null, Modifier.matchParentSize(), contentScale = ContentScale.Fit)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(shades.map { Color.Black.copy(alpha = it) })))
        if (state.status is LivePlayerStatus.Reconnecting) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .5f)))
        }
    }
}

private val LivePlayerStatus.hasTimeline: Boolean
    get() = this == LivePlayerStatus.Playing || this == LivePlayerStatus.Tuning
