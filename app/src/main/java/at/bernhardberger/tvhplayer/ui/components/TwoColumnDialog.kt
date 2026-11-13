package at.bernhardberger.tvhplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import androidx.tv.material3.WideButtonDefaults
import at.bernhardberger.tvhplayer.ui.player.LocalPlayerPanelMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame
import at.bernhardberger.tvhplayer.ui.player.RegisterPlayerModal
import at.bernhardberger.tvhplayer.ui.player.animateShown

/** Kit Two Column action column and LongButton width. */
private val TwoColumnActionWidth = 268.dp

/** Kit Two Column; item menus use the top-aligned 268/268dp More Options layout. */
@Composable
internal fun TwoColumnDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    leading: (@Composable ColumnScope.() -> Unit)? = null,
    itemCardLayout: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit,
) {
    var openingReleasePending by remember { mutableStateOf(true) }
    val parentMotion = LocalPlayerPanelMotion.current
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    val closing = parentMotion == null && !visibility.targetState
    RegisterPlayerModal(backEnabled)
    BackHandler(enabled = backEnabled) {
        if (!closing) {
            if (parentMotion != null) onDismiss() else visibility.targetState = false
        }
    }
    LaunchedEffect(closing, visibility.isIdle, visibility.currentState) {
        if (closing && visibility.isIdle && !visibility.currentState) onDismiss()
    }
    val content: @Composable () -> Unit = {
        val motion = LocalPlayerPanelMotion.current
        val alpha = motion?.animateShown(
            tween(PlayerMotion.PanelMs, easing = PlayerMotion.Standard),
            tween(PlayerMotion.PanelMs, easing = PlayerMotion.Standard), "two-column-fade")
        val layering = LocalBrowseContentLayering.current
        val pageKey = remember { Any() }
        val opacity = alpha ?: remember { mutableFloatStateOf(1f) }
        DisposableEffect(layering, opacity) {
            layering?.details?.set(pageKey, opacity)
            onDispose { layering?.details?.remove(pageKey) }
        }
        Box(modifier.browseDetailsViewport().fillMaxSize().graphicsLayer { this.alpha = opacity.value }
            .background(MaterialTheme.colorScheme.background)
            .semantics { dialog(); paneTitle = title }
            .onPreviewKeyEvent { event ->
                if (closing || onPreviewKeyEvent(event)) return@onPreviewKeyEvent true
                val activation = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (activation && openingReleasePending) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        openingReleasePending = false
                    } else {
                        if (event.type == KeyEventType.KeyUp) openingReleasePending = false
                        return@onPreviewKeyEvent true
                    }
                }
                event.key == Key.DirectionLeft || event.key == Key.DirectionRight
            }.focusGroup().padding(horizontal = 58.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center) {
            // Kit Two Column: the container is centred on screen, its columns are top-aligned inside it.
            Row(Modifier.width(700.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.width(if (itemCardLayout) 268.dp else 340.dp).testTag("two-column-content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (leading != null) leading() else {
                            Text(title, style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 3,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                            Text(message, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(Modifier.width(TwoColumnActionWidth).testTag("two-column-actions"),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = actions)
            }
        }
    }
    if (parentMotion != null) content() else {
        AnimatedVisibility(visibleState = visibility, enter = EnterTransition.None, exit = ExitTransition.None) {
            CompositionLocalProvider(LocalPlayerPanelMotion provides this) {
                PlayerMotionFrame(leaving = closing) { content() }
            }
        }
    }
}

/** The kit LongButton, with native TV focus and press treatment. */
@Composable
internal fun TwoColumnDialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val horizontalPadding = 16.dp
    WideButton(onClick = onClick, modifier = modifier.width(TwoColumnActionWidth),
        interactionSource = interaction,
        background = {
            // TV Material 1.1 measures its inner background at the 240dp minimum, not the
            // 268dp Surface: SurfaceImpl's inner Box drops minimum constraints. Fill the
            // actual Surface so its native clip reaches both right corners as well.
            Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).requiredWidth(TwoColumnActionWidth)) {
                WideButtonDefaults.Background(enabled = true, interactionSource = interaction)
            }
        },
        contentPadding = PaddingValues(horizontal = horizontalPadding,
            vertical = if (subtitle == null) 10.dp else 4.dp),
        // Keep the compact stack in the title slot: native subtitle mode top-aligns
        // the icon and reserves a taller row. Single-line actions retain native padding.
        title = {
            // Fill the Surface's padded width, not the native title's 240dp minimum.
            val rowModifier = if (trailingContent == null) Modifier else Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .requiredWidth(TwoColumnActionWidth - horizontalPadding * 2)
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (trailingContent != null) Box(Modifier.size(ButtonDefaults.IconSize),
                    contentAlignment = Alignment.Center) { trailingContent() }
            }
        },
        icon = icon)
}
