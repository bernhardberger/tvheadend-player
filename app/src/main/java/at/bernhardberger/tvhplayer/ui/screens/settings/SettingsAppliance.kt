package at.bernhardberger.tvhplayer.ui.screens.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.accessibility.ApplianceEntryAccessibilityService
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow
import at.bernhardberger.tvhplayer.ui.components.depth.DepthItem
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun settingsApplianceLevel(
    settingsStore: UiSettingsStore = koinInject(),
): DepthLevel {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(false) }
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(context) {
        serviceEnabled = isApplianceEntryServiceEnabled(context)
        onPauseOrDispose { }
    }

    return settingsApplianceLevel(settings.autoStartPlayback, serviceEnabled,
        onAutoplayChanged = { scope.launch { settingsStore.setAutoStartPlayback(it) } },
        onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
}

@Composable
internal fun settingsApplianceLevel(
    autoplay: Boolean,
    serviceEnabled: Boolean,
    onAutoplayChanged: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
): DepthLevel = settingsLevel(
    SettingsSection.APPLIANCE.name,
    stringResource(R.string.settings_appliance),
    listOf(
        settingsRow("autoplay", stringResource(R.string.auto_start_playback),
            section = stringResource(R.string.appliance_section_app_open),
            checked = autoplay,
            onClick = { onAutoplayChanged(!autoplay) },
        ),
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
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var scrollKey by remember { mutableStateOf<Key?>(null) }
    val step = with(LocalDensity.current) { 120.dp.roundToPx() }
    Text(
        stringResource(R.string.appliance_accessibility_disclosure),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().heightIn(max = 168.dp)
            .border(1.dp, if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == scrollKey) {
                    scrollKey = null
                    true
                } else if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
                    val delta = if (event.key == Key.DirectionDown) step else -step
                    if ((delta < 0 && !scroll.canScrollBackward) || (delta > 0 && !scroll.canScrollForward)) {
                        event.key == scrollKey
                    } else {
                        scrollKey = event.key
                        scope.launch { scroll.scrollTo((scroll.value + delta).coerceIn(0, scroll.maxValue)) }
                        true
                    }
                } else false
            }
            .focusable()
            .padding(horizontal = TvSpacing16, vertical = TvSpacing8)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clipToBounds()
            .drawWithContent {
                drawContent()
                val fade = 24.dp.toPx().coerceAtMost(size.height)
                if (scroll.canScrollForward) drawRect(
                    Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                    blendMode = BlendMode.DstIn,
                )
                if (scroll.canScrollBackward) drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = fade),
                    blendMode = BlendMode.DstIn,
                )
            }
            .verticalScroll(scroll)
            .testTag("appliance-disclosure"),
    )
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
