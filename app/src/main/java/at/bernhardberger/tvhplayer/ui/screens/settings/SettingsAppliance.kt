package at.bernhardberger.tvhplayer.ui.screens.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.lifecycle.compose.LifecycleResumeEffect
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel

@Composable
internal fun settingsApplianceLevel(companionInstalled: Boolean): DepthLevel {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(false) }

    LifecycleResumeEffect(context) {
        serviceEnabled = isApplianceEntryServiceEnabled(context)
        onPauseOrDispose { }
    }

    return settingsApplianceLevel(serviceEnabled, onOpenSetup = { openApplianceSetup(context) },
        companionInstalled = companionInstalled)
}

@Composable
internal fun settingsApplianceLevel(
    serviceEnabled: Boolean,
    onOpenSetup: () -> Unit,
    companionInstalled: Boolean,
): DepthLevel {
    val title = stringResource(R.string.appliance_companion)
    val setup = stringResource(R.string.appliance_setup)
    // The reader is a dialog over Settings, like About's; installing the companion makes it moot.
    var reading by remember { mutableStateOf(false) }
    LaunchedEffect(companionInstalled) { if (companionInstalled) reading = false }
    if (reading && !companionInstalled) ApplianceSetupReader(title) { reading = false }
    return settingsLevel(
        SettingsSection.APPLIANCE.name,
        stringResource(R.string.settings_appliance),
        if (companionInstalled) listOf(
            settingsRow("setup", setup,
                trailingIcon = R.drawable.ic_arrow_forward,
                supporting = stringResource(
                    if (serviceEnabled) R.string.appliance_service_enabled
                    else R.string.appliance_service_disabled
                ),
                onClick = onOpenSetup,
            ),
        ) else listOf(
            settingsRow("companion", title, stringResource(R.string.appliance_companion_not_installed)),
            settingsRow("setup", setup, trailingIcon = R.drawable.ic_arrow_forward, onClick = { reading = true }),
        ),
    )
}

@Composable
private fun ApplianceSetupReader(title: String, onDismiss: () -> Unit) {
    SettingsReadingDialog(title, onDismiss, trailing = {
        Image(painterResource(R.drawable.qr_appliance_companion),
            contentDescription = stringResource(R.string.appliance_companion_qr_description),
            modifier = Modifier.size(270.dp))
    }) {
        Text(stringResource(R.string.appliance_companion_description), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.appliance_companion_install_info), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.appliance_companion_address), style = MaterialTheme.typography.bodyLarge)
    }
}

/** The companion owns the setup, status and the accessibility disclosure; the player only opens it. */
private fun openApplianceSetup(context: Context) {
    val launch = context.packageManager.getLeanbackLaunchIntentForPackage(BuildConfig.APPLIANCE_COMPANION_PACKAGE)
        ?: return
    try {
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
}

private fun isApplianceEntryServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java)
    val serviceClassName = "at.bernhardberger.tvhplayer.accessibility.ApplianceEntryAccessibilityService"
    return manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            info.resolveInfo.serviceInfo.packageName == BuildConfig.APPLIANCE_COMPANION_PACKAGE &&
                info.resolveInfo.serviceInfo.name == serviceClassName
        }
}
