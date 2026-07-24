package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrActionFailure
import at.bernhardberger.tvhplayer.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.DvrSection
import at.bernhardberger.tvhplayer.core.groupRecordings
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.ui.TvScreenPadding
import at.bernhardberger.tvhplayer.ui.common.formatHm
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class PendingRecordingAction {
    CANCEL,
    DELETE,
}

@Composable
fun RecordingsScreen(
    repository: DvrRepository = koinInject(),
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    onRetry: () -> Unit = {},
) {
    val entries by repository.entries.collectAsStateWithLifecycle()
    val groups = remember(entries) { groupRecordings(entries) }
    val scope = rememberCoroutineScope()
    val selectedFocus = remember { FocusRequester() }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var detailsEntry by remember { mutableStateOf<DvrEntry?>(null) }
    var pendingAction by remember { mutableStateOf<PendingRecordingAction?>(null) }
    var actionResult by remember { mutableStateOf<DvrActionResult?>(null) }

    val closeDetails: () -> Unit = {
        detailsEntry = null
        actionResult = null
        scope.launch {
            delay(80)
            runCatching { selectedFocus.requestFocus() }
        }
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(TvScreenPadding),
    ) {
        Text(
            text = stringResource(R.string.recordings_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(14.dp))
        if (entries.isEmpty()) {
            RecordingsEmptyState(connectionUiState, onRetry)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup(),
            ) {
                DvrSection.entries.forEach { section ->
                    val sectionEntries = groups[section].orEmpty()
                    if (sectionEntries.isNotEmpty()) {
                        item(key = "header-$section") {
                            Text(
                                text = sectionLabel(section),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                            )
                        }
                        items(sectionEntries, key = { it.id }) { entry ->
                            val selected = selectedId == entry.id
                            RecordingRow(
                                entry = entry,
                                selected = selected,
                                modifier = if (selected) {
                                    Modifier.focusRequester(selectedFocus)
                                } else {
                                    Modifier
                                },
                                onFocused = { selectedId = entry.id },
                                onClick = {
                                    selectedId = entry.id
                                    detailsEntry = entry
                                    actionResult = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    detailsEntry?.let { opened ->
        val authoritative = entries.firstOrNull { it.id == opened.id } ?: opened
        RecordingDetailsPanel(
            entry = authoritative,
            actionResult = actionResult,
            onCancel = { pendingAction = PendingRecordingAction.CANCEL },
            onDelete = { pendingAction = PendingRecordingAction.DELETE },
            onClose = closeDetails,
        )
    }

    val action = pendingAction
    val target = detailsEntry
    if (action != null && target != null) {
        RecordingConfirmationDialog(
            action = action,
            onDismiss = { pendingAction = null },
            onConfirm = {
                pendingAction = null
                scope.launch {
                    actionResult = when (action) {
                        PendingRecordingAction.CANCEL -> repository.cancelEntry(target.id)
                        PendingRecordingAction.DELETE -> repository.deleteEntry(target.id)
                    }
                }
            },
        )
    }
}

@Composable
private fun RecordingRow(
    entry: DvrEntry,
    selected: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = {
            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    "${entry.start.recordingDateTime()}–${formatHm(entry.stop)} • ${
                        dvrStateLabel(entry.state)
                    }",
                    maxLines = 1,
                )
                entry.failureReason?.takeIf(String::isNotBlank)?.let {
                    Text(
                        stringResource(R.string.recording_failure_reason, it),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() },
    )
}

@Composable
private fun RecordingDetailsPanel(
    entry: DvrEntry,
    actionResult: DvrActionResult?,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    val canCancel = entry.state == DvrState.SCHEDULED || entry.state == DvrState.RECORDING
    val canDelete = entry.state == DvrState.COMPLETED
    LaunchedEffect(entry.id, entry.state) { initialFocus.requestFocus() }
    BackHandler(onBack = onClose)
    RecordingDialogSurface {
        Text(
            entry.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        entry.subtitle?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "${entry.start.recordingDateTime()}–${formatHm(entry.stop)} • ${
                dvrStateLabel(entry.state)
            }",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entry.summary?.takeIf(String::isNotBlank)?.let { Text(it, maxLines = 3) }
        entry.description
            ?.takeIf { it.isNotBlank() && it != entry.summary }
            ?.let { Text(it, maxLines = 5, overflow = TextOverflow.Ellipsis) }
        entry.failureReason?.takeIf(String::isNotBlank)?.let {
            Text(
                stringResource(R.string.recording_failure_reason, it),
                color = MaterialTheme.colorScheme.error,
            )
        }
        actionResult?.let {
            Text(
                dvrActionResultLabel(it),
                color = if (it is DvrActionResult.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canCancel) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.focusRequester(initialFocus),
                ) {
                    Text(stringResource(R.string.cancel_recording))
                }
            }
            if (canDelete) {
                Button(
                    onClick = onDelete,
                    modifier = Modifier.focusRequester(initialFocus),
                ) {
                    Text(stringResource(R.string.delete_recording))
                }
            }
            OutlinedButton(
                onClick = onClose,
                modifier = if (!canCancel && !canDelete) {
                    Modifier.focusRequester(initialFocus)
                } else {
                    Modifier
                },
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun RecordingConfirmationDialog(
    action: PendingRecordingAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(action) { initialFocus.requestFocus() }
    BackHandler(onBack = onDismiss)
    RecordingDialogSurface {
        Text(
            stringResource(
                if (action == PendingRecordingAction.CANCEL) {
                    R.string.cancel_recording_confirm_title
                } else {
                    R.string.delete_recording_confirm_title
                }
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(
                if (action == PendingRecordingAction.CANCEL) {
                    R.string.cancel_recording_confirm_message
                } else {
                    R.string.delete_recording_confirm_message
                }
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onConfirm, modifier = Modifier.focusRequester(initialFocus)) {
                Text(
                    stringResource(
                        if (action == PendingRecordingAction.CANCEL) {
                            R.string.cancel_recording
                        } else {
                            R.string.delete_recording
                        }
                    )
                )
            }
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

@Composable
private fun RecordingDialogSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(720.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun RecordingsEmptyState(
    connectionUiState: ConnectionUiState,
    onRetry: () -> Unit,
) {
    val error = connectionUiState is ConnectionUiState.Error
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(
                when (connectionUiState) {
                    ConnectionUiState.Connecting,
                    ConnectionUiState.SyncingChannels -> R.string.recordings_loading
                    ConnectionUiState.Reconnecting -> R.string.recordings_reconnecting
                    is ConnectionUiState.Error -> R.string.recordings_server_failure
                    else -> R.string.recordings_empty
                }
            ),
            style = MaterialTheme.typography.titleLarge,
        )
        if (error) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun sectionLabel(section: DvrSection): String = stringResource(
    when (section) {
        DvrSection.UPCOMING_ACTIVE -> R.string.recordings_upcoming_active
        DvrSection.COMPLETED -> R.string.recordings_completed
        DvrSection.FAILED_CANCELLED -> R.string.recordings_failed_cancelled
    }
)

@Composable
private fun dvrStateLabel(state: DvrState): String = stringResource(
    when (state) {
        DvrState.SCHEDULED -> R.string.recording_state_scheduled
        DvrState.RECORDING -> R.string.recording_state_recording
        DvrState.COMPLETED -> R.string.recording_state_completed
        DvrState.FAILED -> R.string.recording_state_failed
        DvrState.CANCELLED -> R.string.recording_state_cancelled
        DvrState.UNKNOWN -> R.string.recording_state_unknown
    }
)

@Composable
private fun dvrActionResultLabel(result: DvrActionResult): String = stringResource(
    when (result) {
        is DvrActionResult.Accepted -> R.string.recording_action_accepted
        is DvrActionResult.Failed -> when (result.reason) {
            DvrActionFailure.PERMISSION_DENIED -> R.string.recording_action_permission
            DvrActionFailure.CONFLICT -> R.string.recording_action_conflict
            DvrActionFailure.REJECTED -> R.string.recording_action_rejected
            DvrActionFailure.CONNECTION -> R.string.recording_action_connection
        }
    }
)

private fun Long.recordingDateTime(): String = Instant.ofEpochSecond(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm"))
