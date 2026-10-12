package at.bernhardberger.tvhplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.graphics.Color
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
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha

/** A modal choice with a bounded text column and two vertically arranged TV actions. */
@Composable
internal fun TwoColumnDialog(
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
    @DrawableRes firstIcon: Int? = null,
    @DrawableRes secondIcon: Int? = null,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
) {
    val firstFocus = remember { FocusRequester() }
    val secondFocus = remember { FocusRequester() }
    var openingReleasePending by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        firstFocus.requestFocus()
    }
    BackHandler(enabled = backEnabled, onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = TvScrimModalAlpha))
            .semantics { dialog(); paneTitle = title }
            .onPreviewKeyEvent { event ->
                if (onPreviewKeyEvent(event)) return@onPreviewKeyEvent true
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
            }.focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.width(700.dp).heightIn(min = 196.dp)
                .background(at.bernhardberger.tvhplayer.ui.TvSurfaceColors.containerHigh, RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Text(message, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.width(268.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple(firstLabel, firstIcon, onFirst), Triple(secondLabel, secondIcon, onSecond))
                    .forEachIndexed { index, (label, icon, action) ->
                        Button(onClick = action,
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                .focusRequester(if (index == 0) firstFocus else secondFocus)
                                .focusProperties {
                                    up = if (index == 0) FocusRequester.Cancel else firstFocus
                                    down = if (index == 0) secondFocus else FocusRequester.Cancel
                                    left = FocusRequester.Cancel; right = FocusRequester.Cancel
                                }.testTag(if (index == 0) firstTag else secondTag),
                        ) {
                            if (icon != null) {
                                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                        }
                    }
            }
        }
    }
}
