package at.bernhardberger.tvhplayer.appliance.update

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.appliance.BuildConfig
import at.bernhardberger.tvhplayer.appliance.R
import java.text.DateFormat
import java.util.Date

data class UpdateScreenState(
    val automatic: Boolean = true,
    val latestVersion: String = "",
    val lastCheck: Long = 0,
    val result: UpdateResult = UpdateResult.NEVER,
    val installerMessage: String = "",
)

@Composable
fun CompanionUpdateRows(state: UpdateScreenState, onAutomaticUpdates: () -> Unit, onUpdateNow: () -> Unit) {
    Text(stringResource(R.string.companion_updates), style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.companion_installed_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
    val latest = when {
        state.result == UpdateResult.UP_TO_DATE -> stringResource(R.string.update_up_to_date)
        state.latestVersion.isNotEmpty() -> state.latestVersion
        else -> stringResource(R.string.update_unknown)
    }
    Text(stringResource(R.string.companion_latest_version, latest))
    val lastCheck = if (state.lastCheck == 0L) stringResource(R.string.update_never)
        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.lastCheck))
    Text(stringResource(R.string.companion_last_check, lastCheck))
    val result = stringResource(state.result.messageResource())
    Text(stringResource(R.string.companion_update_result,
        if (state.installerMessage.isBlank()) result else "$result: ${state.installerMessage}"),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    // Keep focusable controls below the status rows so D-pad scrolling has no unreachable tail.
    ListItem(
        selected = state.automatic,
        onClick = onAutomaticUpdates,
        headlineContent = { Text(stringResource(R.string.automatic_updates)) },
        supportingContent = { Text(stringResource(R.string.automatic_updates_description)) },
        trailingContent = {
            Switch(checked = state.automatic, onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { })
        },
        colors = ListItemDefaults.colors(selectedContainerColor = Color.Transparent,
            selectedContentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth().semantics {
            role = Role.Switch
            toggleableState = ToggleableState(state.automatic)
        },
    )
    Button(onClick = onUpdateNow) { Text(stringResource(R.string.update_now)) }
}

internal fun UpdateResult.messageResource(): Int = when (this) {
    UpdateResult.NEVER -> R.string.update_never
    UpdateResult.CHECKING -> R.string.update_checking
    UpdateResult.UP_TO_DATE -> R.string.update_up_to_date
    UpdateResult.NO_NETWORK -> R.string.update_no_network
    UpdateResult.RATE_LIMITED -> R.string.update_rate_limited
    UpdateResult.RELEASE_PROBLEM -> R.string.update_release_problem
    UpdateResult.HASH_MISMATCH -> R.string.update_hash_mismatch
    UpdateResult.WRONG_PACKAGE -> R.string.update_wrong_package
    UpdateResult.NOT_NEWER -> R.string.update_not_newer
    UpdateResult.SIGNATURE_MISMATCH -> R.string.update_signature_mismatch
    UpdateResult.INSTALL_PERMISSION -> R.string.update_install_permission
    UpdateResult.WAITING_STANDBY -> R.string.update_waiting_standby
    UpdateResult.INSTALLING -> R.string.update_installing
    UpdateResult.PENDING_CONFIRMATION -> R.string.update_pending_confirmation
    UpdateResult.INSTALLER_FAILURE -> R.string.update_installer_failure
    UpdateResult.STORAGE_FAILURE -> R.string.update_storage_failure
    UpdateResult.SUCCESS -> R.string.update_success
}
