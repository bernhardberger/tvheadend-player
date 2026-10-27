package at.bernhardberger.tvhplayer.ui.components

import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame

/** Safe choice first, in the same horizontal pill row as the resume prompt. */
@Composable
internal fun CentredDialog(
    title: String,
    message: String,
    firstLabel: String,
    secondLabel: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    onDismiss: () -> Unit,
    backEnabled: Boolean = true,
    firstTag: String = "two-column-first",
    secondTag: String = "two-column-second",
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    thirdLabel: String? = null,
    thirdTag: String = "two-column-third",
    onThird: () -> Unit = {},
    secondaryText: String? = null,
    alternativeContent: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    visibility: MutableTransitionState<Boolean> = remember { MutableTransitionState(false).apply { targetState = true } },
) {
    val firstFocus = remember { FocusRequester() }
    val secondFocus = remember { FocusRequester() }
    val thirdFocus = remember { FocusRequester() }
    var chosen by remember { mutableStateOf<Int?>(null) }
    CentredDialog(title, message, onDismiss = {
        when (chosen) { 0 -> onFirst(); 1 -> onSecond(); else -> onDismiss() }
    }, backEnabled = backEnabled, visibility = visibility, secondaryText = secondaryText,
        alternativeContent = alternativeContent, onBack = onBack,
        modifier = Modifier.onPreviewKeyEvent(onPreviewKeyEvent)) {
        LaunchedEffect(alternativeContent == null) {
            if (alternativeContent == null) { withFrameNanos { }; firstFocus.requestFocus() }
        }
        Button(onClick = { chosen = 0; visibility.targetState = false },
            modifier = Modifier.height(40.dp).focusRequester(firstFocus).focusProperties {
                left = FocusRequester.Cancel; right = secondFocus
            }.testTag(firstTag)) { Text(firstLabel) }
        OutlinedButton(onClick = { chosen = 1; visibility.targetState = false },
            modifier = Modifier.height(40.dp).focusRequester(secondFocus).focusProperties {
                left = firstFocus; right = if (thirdLabel != null) thirdFocus else FocusRequester.Cancel
            }.testTag(secondTag)) { Text(secondLabel) }
        if (thirdLabel != null) OutlinedButton(onClick = onThird,
            modifier = Modifier.height(40.dp).focusRequester(thirdFocus).focusProperties {
                left = secondFocus; right = FocusRequester.Cancel
            }.testTag(thirdTag)) { Text(thirdLabel) }
    }
}

/** A modal decision within the current flow, above the entire browse shell. */
@Composable
internal fun CentredDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    secondaryText: String? = null,
    eyebrow: (@Composable () -> Unit)? = null,
    alternativeContent: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    visibility: MutableTransitionState<Boolean> = remember { MutableTransitionState(false).apply { targetState = true } },
    actions: @Composable RowScope.() -> Unit,
) {
    var openingReleasePending by remember { mutableStateOf(true) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visibility.isIdle, visibility.currentState, visibility.targetState) {
        if (visibility.isIdle && !visibility.currentState && !visibility.targetState) latestDismiss()
    }
    val back = onBack ?: { visibility.targetState = false }
    BackHandler(enabled = backEnabled) { back() }
    // Expand over the rail without registering opaque details: the shell stays visible below
    // this single scrim, and focus never crosses an Android window boundary on dismissal.
    AnimatedVisibility(visibleState = visibility, modifier = modifier.browseDetailsViewport(),
        enter = fadeIn(tween(PlayerMotion.PanelMs, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(PlayerMotion.PanelMs, easing = PlayerMotion.Standard))) {
        val layering = LocalBrowseContentLayering.current
        val key = remember { Any() }
        val scrim = transition.animateFloat(
            transitionSpec = { tween(PlayerMotion.PanelMs, easing = PlayerMotion.Standard) },
            label = "modal-rail-scrim",
        ) { if (it == EnterExitState.Visible) TvScrimModalAlpha else 0f }
        DisposableEffect(layering, scrim) {
            layering?.modalScrims?.set(key, scrim)
            onDispose { layering?.modalScrims?.remove(key) }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = TvScrimModalAlpha))
            .testTag("centred-dialog-scrim")
            .semantics { dialog(); paneTitle = title }
            .onPreviewKeyEvent { event ->
                if (!visibility.targetState) return@onPreviewKeyEvent true
                if (backEnabled && event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) back()
                    return@onPreviewKeyEvent true
                }
                val activation = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (activation && openingReleasePending) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        openingReleasePending = false
                    } else {
                        if (event.type == KeyEventType.KeyUp) openingReleasePending = false
                        return@onPreviewKeyEvent true
                    }
                }
                alternativeContent == null && (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
            }.focusGroup(), contentAlignment = Alignment.Center) {
            // Keep one dimmed backdrop while a decision drills into its options. Fade through
            // rather than drawing the card and side panel visibly on top of each other.
            AnimatedContent(targetState = alternativeContent, contentKey = { it != null },
                transitionSpec = {
                    (fadeIn(tween(PlayerMotion.MediumMs, delayMillis = PlayerMotion.FastMs)) togetherWith
                        fadeOut(tween(PlayerMotion.FastMs))).using(null)
                }, label = "dialog-options") { alternative ->
            PlayerMotionFrame(leaving = (alternative != null) != (alternativeContent != null)) {
            if (alternative != null) alternative() else {
            Column(Modifier.width(528.dp).clip(RoundedCornerShape(16.dp)).background(TvSurfaceColors.container)
                .testTag("centred-dialog-card").padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.fillMaxWidth().testTag("centred-dialog-content")) {
                    Column(Modifier.fillMaxWidth().testTag("centred-dialog-text"),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            eyebrow?.let {
                                it()
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!secondaryText.isNullOrBlank()) Text(secondaryText, style = MaterialTheme.typography.titleMedium,
                                color = TvTextColors.primary,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyLarge,
                            color = TvTextColors.secondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
            }
            }
            }
            }
        }
    }
}
