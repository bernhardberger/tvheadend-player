package at.bernhardberger.tvhplayer.ui.screens.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.accessibility.ApplianceEntryAccessibilityService
import at.bernhardberger.tvhplayer.ui.components.SettingsPane

@Composable
fun SettingsAppliance() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }

    LifecycleResumeEffect(context) {
        enabled = isApplianceEntryServiceEnabled(context)
        onPauseOrDispose { }
    }

    SettingsPane(title = stringResource(R.string.settings_appliance)) {
        Text(
            text = stringResource(R.string.appliance_accessibility_disclosure),
        )
        Text(
            text = stringResource(
                if (enabled) R.string.appliance_service_enabled
                else R.string.appliance_service_disabled
            ),
        )
        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        ) {
            Text(stringResource(R.string.open_accessibility_settings))
        }
    }
}

private fun isApplianceEntryServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java)
    val serviceClassName = ApplianceEntryAccessibilityService::class.java.name
    return manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            info.resolveInfo.serviceInfo.packageName == context.packageName &&
                info.resolveInfo.serviceInfo.name == serviceClassName
        }
}
