package at.bernhardberger.tvhplayer.ui.screens.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.lifecycle.compose.LifecycleResumeEffect
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.accessibility.ApplianceEntryAccessibilityService
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.ScrollReadingBox
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow
import at.bernhardberger.tvhplayer.ui.components.depth.DepthItem

@Composable
internal fun settingsApplianceLevel(): DepthLevel {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(false) }

    LifecycleResumeEffect(context) {
        serviceEnabled = isApplianceEntryServiceEnabled(context)
        onPauseOrDispose { }
    }

    return settingsApplianceLevel(serviceEnabled,
        onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
}

@Composable
internal fun settingsApplianceLevel(
    serviceEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
): DepthLevel = settingsLevel(
    SettingsSection.APPLIANCE.name,
    stringResource(R.string.settings_appliance),
    listOf(
        settingsRow("accessibility", stringResource(R.string.open_accessibility_settings),
            trailingIcon = R.drawable.ic_settings_accessibility,
            section = stringResource(R.string.appliance_section_accessibility),
            supporting = stringResource(
                if (serviceEnabled) R.string.appliance_service_enabled
                else R.string.appliance_service_disabled
            ),
            onClick = onOpenAccessibility,
        ),
        DepthRow(DepthItem("accessibility-disclosure"), {}) { modifier, _ ->
            ApplianceDisclosure(modifier)
        },
    ),
)

/** Always on the Appliance page, outside its action row. Uses the existing Info reading pattern. */
@Composable
private fun ApplianceDisclosure(modifier: Modifier) {
    ScrollReadingBox(
        title = stringResource(R.string.appliance_section_accessibility),
        modifier = modifier.fillMaxWidth().heightIn(max = 168.dp),
        readingModifier = Modifier.testTag("appliance-disclosure"),
    ) {
        Text(stringResource(R.string.appliance_accessibility_disclosure),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
