package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.focusable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.core.programmeDetailsBody
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.ui.common.programmeMetadata

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
    val scroll = rememberScrollState()

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
                text = event.title,
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
                        .focusable()
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
