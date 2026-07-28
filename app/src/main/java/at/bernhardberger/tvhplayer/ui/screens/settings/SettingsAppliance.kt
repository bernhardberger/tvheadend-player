package at.bernhardberger.tvhplayer.ui.screens.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.accessibility.ApplianceEntryAccessibilityService
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.SettingsSectionTitle
import at.bernhardberger.tvhplayer.ui.components.SettingsSwitchRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsAppliance(
    initialFocusRequester: FocusRequester,
    settingsStore: UiSettingsStore = koinInject(),
) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(false) }
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(context) {
        serviceEnabled = isApplianceEntryServiceEnabled(context)
        onPauseOrDispose { }
    }

    SettingsPane(title = stringResource(R.string.settings_appliance)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionTitle(stringResource(R.string.appliance_section_app_open))
            SettingsSwitchRow(
                label = stringResource(R.string.auto_start_playback),
                checked = settings.autoStartPlayback,
                supportingText = stringResource(R.string.auto_start_playback_description),
                onClick = {
                    scope.launch {
                        settingsStore.setAutoStartPlayback(!settings.autoStartPlayback)
                    }
                },
                modifier = Modifier.focusRequester(initialFocusRequester),
            )

            SettingsSectionTitle(stringResource(R.string.appliance_section_accessibility))
            Text(
                text = stringResource(
                    if (serviceEnabled) R.string.appliance_service_enabled
                    else R.string.appliance_service_disabled
                ),
            )
            Text(text = stringResource(R.string.appliance_accessibility_disclosure))
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            ) {
                Text(stringResource(R.string.open_accessibility_settings))
            }
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
