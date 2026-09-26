package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.StreamSignalRow
import at.bernhardberger.tvhplayer.core.StreamSignalSection
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

/** Reading-only content. Back is deliberately left to the enclosing options page. */
@Composable
fun StreamSignalDetailsPage(sections: Map<StreamSignalSection, List<StreamSignalRow>>, modifier: Modifier = Modifier) {
    val visible = sections.filterValues { it.isNotEmpty() }
    val emptyKey = StreamSignalSection.STREAM to ""
    val keys = visible.flatMap { (section, rows) -> rows.map { section to it.label } }.ifEmpty { listOf(emptyKey) }
    val requesters = remember { mutableMapOf<Pair<StreamSignalSection, String>, FocusRequester>() }
    var focusedKey by remember { mutableStateOf<Pair<StreamSignalSection, String>?>(null) }
    var previousKeys by remember { mutableStateOf(keys) }
    var entered by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState())) {
        if (visible.isEmpty()) {
            key(emptyKey) {
                val requester = remember { requesters.getOrPut(emptyKey) { FocusRequester() } }
                ReadingRow(StreamSignalRow("", stringResource(R.string.trial_no_details)),
                    Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) focusedKey = emptyKey })
            }
        }
        visible.forEach { (section, rows) ->
            Text(stringResource(when (section) {
                StreamSignalSection.STREAM -> R.string.trial_stream
                StreamSignalSection.SOURCE -> R.string.trial_source
                StreamSignalSection.RECEPTION -> R.string.trial_reception
                StreamSignalSection.HEALTH -> R.string.trial_health
            }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() })
            rows.forEach { row ->
                key(section, row.label) {
                    val rowKey = section to row.label
                    val requester = remember { requesters.getOrPut(rowKey) { FocusRequester() } }
                    ReadingRow(row, Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) focusedKey = rowKey })
                }
            }
        }
    }
    LaunchedEffect(keys) {
        val target = when {
            !entered -> keys.first()
            focusedKey != null && focusedKey !in keys -> keys[previousKeys.indexOf(focusedKey).coerceIn(0, keys.lastIndex)]
            else -> null
        }
        target?.let { requesters.getValue(it).requestFocus() }
        entered = true
        previousKeys = keys
        requesters.keys.retainAll(keys.toSet())
    }
}

@Composable
private fun ReadingRow(row: StreamSignalRow, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val description = if (row.label.isBlank()) row.value.orEmpty() else stringResource(R.string.trial_label_value, row.label, row.value.orEmpty())
    Surface(
        modifier = modifier.fillMaxWidth()
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small) else Modifier)
            .semantics(mergeDescendants = true) {}
            .focusable(interactionSource = interactions),
        shape = MaterialTheme.shapes.small,
        colors = SurfaceDefaults.colors(containerColor = if (focused) TvSurfaceColors.containerHigh else Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Row(Modifier.heightIn(min = 36.dp).padding(horizontal = 8.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
            if (row.label.isNotBlank()) Text(row.label, Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            Text(row.value.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
