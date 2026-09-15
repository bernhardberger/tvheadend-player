package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.depth.*
import org.koin.compose.koinInject

internal const val CONNECTION_EDITOR = "connection-editor"
internal const val EDIT_CONNECTION = "edit-connection"

@Composable
internal fun settingsConnectionLevels(
    navigation: DepthNavigationState,
    owner: AppProfileOwner = koinInject(),
    session: TvheadendSession = koinInject(),
): List<DepthLevel> {
    val profile by owner.serverProfile.collectAsStateWithLifecycle()
    val observation by session.observation.collectAsStateWithLifecycle()
    val available = profile as? ServerProfileReadResult.Available
    val status = stringResource(when {
        profile == null -> R.string.connection_overview_loading
        profile == ServerProfileReadResult.Missing -> R.string.connection_overview_not_configured
        profile == ServerProfileReadResult.Unavailable -> R.string.connection_overview_unavailable
        else -> when (observation.sessionState) {
            SessionState.Disconnected -> R.string.not_connected
            SessionState.Connecting -> R.string.connection_overview_connecting
            SessionState.Synchronizing -> R.string.connection_overview_synchronizing
            is SessionState.Ready -> R.string.connection_overview_connected
            is SessionState.Unavailable -> R.string.status_connection_failed_other
        }
    })
    return listOf(
        settingsConnectionOverview(status, available?.host, available?.port),
        settingsLevel(CONNECTION_EDITOR, stringResource(R.string.edit_connection),
            listOf(settingsRow("editor-placeholder", stringResource(R.string.edit_connection))),
            activeContent = { requester, left ->
                val visit = navigation.stack.visit
                SettingsConnection(requester, onNavigateLeft = left, onSaved = {
                    if (navigation.stack.visit == visit && navigation.stack.active.levelId == CONNECTION_EDITOR) navigation.pop()
                })
            }),
    )
}

/** Safe endpoint fields only. No editor state, credential read, username or password reaches this API. */
@Composable
internal fun settingsConnectionOverview(status: String, host: String?, port: Int?): DepthLevel {
    val missing = stringResource(R.string.connection_overview_unavailable)
    val edit = settingsRow(EDIT_CONNECTION, stringResource(R.string.edit_connection),
        supporting = stringResource(R.string.edit_connection_description), trailingIcon = R.drawable.ic_edit)
    // The information and its sole action scroll together. Only Edit is a navigation identity.
    val row = DepthRow(DepthItem(EDIT_CONNECTION), leafLevelId = CONNECTION_EDITOR) { modifier, activate ->
        Column {
            ConnectionValueRow(stringResource(R.string.connection_overview_status), status)
            ConnectionValueRow(stringResource(R.string.connection_overview_server), host ?: missing)
            ConnectionValueRow(stringResource(R.string.port_htsp), port?.toString() ?: missing)
            edit.content(modifier, activate)
        }
    }
    return settingsLevel(SettingsSection.CONNECTION.name, stringResource(R.string.settings_connection_nav),
        listOf(row), initialItemId = EDIT_CONNECTION)
}

@Composable
private fun ConnectionValueRow(title: String, value: String) {
    ListItem(selected = false, onClick = {},
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(bottom = TvSpacing8)
            .focusProperties { canFocus = false }
            .clearAndSetSemantics { text = AnnotatedString("$title\n$value") },
    )
}
