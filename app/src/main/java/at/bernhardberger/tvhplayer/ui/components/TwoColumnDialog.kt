package at.bernhardberger.tvhplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import at.bernhardberger.tvhplayer.ui.player.*

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
    @DrawableRes icon: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
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
        title = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = icon?.let { resource -> { Icon(painterResource(resource), null, Modifier.size(24.dp)) } })
}
