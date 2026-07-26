package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import at.bernhardberger.tvhplayer.core.programmeDetailsBody
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.ui.common.programmeMetadata
import kotlinx.coroutines.launch

/**
 * Shared Content Details cluster: title, metadata, full scrollable synopsis, and
 * optional actions. Used by Guide details, Channels detail pane, and player Info.
 */
@Composable
fun ProgrammeContentDetails(
    event: EpgEventEntry,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val body = programmeDetailsBody(event)
    val metadata = programmeMetadata(event)

    ContentDetails(
        title = event.title,
        subtitle = subtitle,
        metadata = metadata,
        body = body,
        modifier = modifier,
        actions = actions,
        footer = footer,
    )
}

@Composable
fun RecordingContentDetails(
    entry: DvrEntry,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    ContentDetails(
        title = entry.title,
        subtitle = entry.subtitle,
        metadata = entry.channelName,
        body = entry.summary?.takeIf(String::isNotBlank) ?: entry.description,
        modifier = modifier,
        actions = actions,
    )
}

@Composable
private fun ContentDetails(
    title: String,
    subtitle: String?,
    metadata: String?,
    body: String?,
    modifier: Modifier,
    actions: (@Composable RowScope.() -> Unit)?,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var bodyFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!metadata.isNullOrBlank()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 280.dp)
                        .verticalScroll(scroll)
                        .onFocusChanged { bodyFocused = it.isFocused }
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val delta = when (event.key) {
                                Key.DirectionUp -> -120f
                                Key.DirectionDown -> 120f
                                else -> return@onPreviewKeyEvent false
                            }
                            if (
                                (delta < 0 && scroll.value == 0) ||
                                (delta > 0 && scroll.value == scroll.maxValue)
                            ) {
                                return@onPreviewKeyEvent false
                            }
                            scope.launch { scroll.animateScrollTo((scroll.value + delta).toInt()) }
                            true
                        }
                        .background(
                            if (bodyFocused) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .testTag("programme-details-body")
                        .padding(end = 8.dp),
                )
            }
            footer?.invoke(this)
        }
        if (actions != null) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = actions,
                )
            }
        }
    }
}
