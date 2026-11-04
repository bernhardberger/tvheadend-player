package at.bernhardberger.tvhplayer.ui.screens.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel

@Composable
internal fun settingsApplianceLevel(): DepthLevel {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(false) }

    LifecycleResumeEffect(context) {
        serviceEnabled = isApplianceEntryServiceEnabled(context)
        onPauseOrDispose { }
    }

    return settingsApplianceLevel(serviceEnabled, onOpenSetup = { openApplianceSetup(context) })
}

@Composable
internal fun settingsApplianceLevel(
    serviceEnabled: Boolean,
    onOpenSetup: () -> Unit,
): DepthLevel = settingsLevel(
    SettingsSection.APPLIANCE.name,
    stringResource(R.string.settings_appliance),
    listOf(
        settingsRow("setup", stringResource(R.string.appliance_setup),
            trailingIcon = R.drawable.ic_arrow_forward,
            supporting = stringResource(
                if (serviceEnabled) R.string.appliance_service_enabled
                else R.string.appliance_service_disabled
            ),
            onClick = onOpenSetup,
        ),
    ),
)

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
