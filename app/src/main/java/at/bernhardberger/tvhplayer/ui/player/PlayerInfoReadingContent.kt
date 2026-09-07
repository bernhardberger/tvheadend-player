package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

/** Player-only reading pane; actions stay reachable even with a very long synopsis. */
@Composable
internal fun PlayerInfoReadingContent(
    title: String,
    subtitle: String?,
    body: String?,
    readingFocus: FocusRequester,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var scrollKey by remember { mutableStateOf<Key?>(null) }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .background(if (focused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else Color.Transparent)
                .border(1.dp, if (focused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else Color.Transparent)
                .focusRequester(readingFocus)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == scrollKey) {
                        scrollKey = null
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val delta = when (event.key) {
                        Key.DirectionDown -> 120
                        Key.DirectionUp -> -120
                        else -> return@onPreviewKeyEvent false
                    }
                    if ((delta < 0 && scroll.value == 0) || (delta > 0 && scroll.value == scroll.maxValue)) {
                        return@onPreviewKeyEvent event.key == scrollKey
                    }
                    scrollKey = event.key
                    scope.launch { scroll.scrollTo((scroll.value + delta).coerceIn(0, scroll.maxValue)) }
                    true
                }
                .focusable()
                .padding(8.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    val fade = 32.dp.toPx().coerceAtMost(size.height)
                    if (scroll.canScrollForward) drawRect(
                        Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                        blendMode = BlendMode.DstIn,
                    )
                    if (scroll.canScrollBackward) drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = fade),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .verticalScroll(scroll)
                .testTag("player-info-reading"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!body.isNullOrBlank()) Text(body, style = MaterialTheme.typography.bodyLarge)
        }
        footer()
    }
}
