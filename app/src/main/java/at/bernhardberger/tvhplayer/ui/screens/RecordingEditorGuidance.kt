package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.RecordingDraftField

@Composable
internal fun RecordingEditorHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun RecordingEditorSecondary(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha))
}

@Composable
internal fun recordingEditorMoreSummary(changed: Set<RecordingDraftField>, editing: Boolean): String =
    if (changed.isEmpty()) stringResource(if (editing)
        R.string.autorec_editor_unchanged_options else R.string.autorec_editor_defaults)
    else changed.map { automaticRecordingFieldLabel(it) }.joinToString(", ")

@Composable
internal fun RecordingEditorPreviewPair(title: String, meta: String) {
    Column {
        if (title.isNotBlank()) Text(
            title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
        )
        RecordingEditorSecondary(meta)
    }
}
