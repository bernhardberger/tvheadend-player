package at.bernhardberger.tvhplayer.touch.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R

@Composable
internal fun ConnectionsScreen(
    active: ActiveConnectionUiState?,
    onBack: () -> Unit,
    onEdit: (ActiveConnectionUiState) -> Unit,
    onRemove: (ActiveConnectionUiState) -> Unit,
) {
    // A changing state line keeps the question open; another connection closes it.
    var confirmRemove by rememberSaveable(active?.copy(status = ConnectionStatus.Connected)) { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopAppBar(
                title = { Text(stringResource(SharedR.string.server_list_title), Modifier.semantics { heading() }) },
                navigationIcon = {
                    ConnectionIconButton(R.drawable.connect_back_outline, SharedR.string.back, onClick = onBack)
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
            if (active != null) {
                Column(
                    Modifier.align(Alignment.CenterHorizontally).widthIn(max = 840.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(SharedR.string.server_in_use),
                        Modifier.padding(start = 4.dp).semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    ActiveConnectionCard(active, onEdit = { onEdit(active) }, onRemove = { confirmRemove = true })
                }
            }
        }
    }
    if (active != null && confirmRemove) {
        RemoveConnectionDialog(
            name = active.name,
            onCancel = { confirmRemove = false },
            onRemove = {
                confirmRemove = false
                onRemove(active)
            },
        )
    }
}

@Composable
private fun ActiveConnectionCard(active: ActiveConnectionUiState, onEdit: () -> Unit, onRemove: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painterResource(R.drawable.connect_server_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(active.name, style = MaterialTheme.typography.titleMedium)
                ConnectionDetails(
                    active.address,
                    stringResource(R.string.connect_port_detail, active.port),
                    active.username.ifBlank { stringResource(SharedR.string.server_no_sign_in) },
                )
                StatusLine(active.status)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 56.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f, fill = false)) {
                Icon(painterResource(R.drawable.connect_edit_outline), null, Modifier.size(18.dp))
                Text(stringResource(SharedR.string.setup_connection), Modifier.padding(start = 8.dp))
            }
            ConnectionIconButton(
                R.drawable.connect_delete_outline, SharedR.string.server_remove, onClick = onRemove,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusLine(status: ConnectionStatus) {
    val color = when (status.kind) {
        ConnectionStatus.Kind.Connected -> MaterialTheme.colorScheme.primary
        ConnectionStatus.Kind.Working -> MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionStatus.Kind.Failed -> MaterialTheme.colorScheme.error
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val mark = Modifier.size(18.dp)
        when (status.kind) {
            ConnectionStatus.Kind.Connected -> Icon(
                painterResource(R.drawable.connect_check_outline), contentDescription = null, mark, tint = color,
            )
            ConnectionStatus.Kind.Working -> LoadingIndicator(mark, color = color)
            ConnectionStatus.Kind.Failed -> Icon(
                painterResource(R.drawable.connect_error_outline), contentDescription = null, mark, tint = color,
            )
        }
        Text(stringResource(status.text), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * A bullet separates parts in the same line, but never begins or ends a wrapped line. FlowRow cannot do this: it
 * places every child it is given, so a separator kept with either neighbour shows at a line's start or end.
 */
@Composable
private fun ConnectionDetails(address: String, port: String, username: String) {
    Layout(content = {
        DetailPart(address)
        DetailPart(" • ", Modifier.semantics { hideFromAccessibility() })
        DetailPart(port)
        DetailPart(" • ", Modifier.semantics { hideFromAccessibility() })
        DetailPart(username)
    }) { measurables, constraints ->
        val parts = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rowHeight = parts.maxOf { it.height }
        val positions = arrayOfNulls<IntOffset>(parts.size)
        positions[0] = IntOffset.Zero
        var y = 0
        var x = parts.first().width
        for (index in 2 until parts.size step 2) {
            val separator = parts[index - 1]
            val part = parts[index]
            if (x + separator.width + part.width > constraints.maxWidth) {
                x = 0
                y += rowHeight
            } else {
                positions[index - 1] = IntOffset(x, y)
                x += separator.width
            }
            positions[index] = IntOffset(x, y)
            x += part.width
        }
        layout(constraints.maxWidth, (y + rowHeight).coerceIn(constraints.minHeight, constraints.maxHeight)) {
            parts.forEachIndexed { index, part -> positions[index]?.let { part.placeRelative(it.x, it.y) } }
        }
    }
}

@Composable
private fun DetailPart(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        softWrap = false,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RemoveConnectionDialog(name: String, onCancel: () -> Unit, onRemove: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.widthIn(max = 492.dp).padding(horizontal = 26.dp),
        title = { Text(stringResource(R.string.connect_remove_title, name)) },
        text = { Text(stringResource(R.string.connect_remove_body_active)) },
        confirmButton = { TextButton(onClick = onRemove) { Text(stringResource(R.string.connect_remove)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(SharedR.string.cancel)) } },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}
