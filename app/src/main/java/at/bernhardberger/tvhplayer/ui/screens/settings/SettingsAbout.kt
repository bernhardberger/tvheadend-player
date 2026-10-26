package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsFullDescription
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding

internal const val ABOUT_LEVEL = "settings-about"

@Composable
internal fun settingsAboutLevels(
    sessionState: SessionState,
    applianceBuild: Boolean = BuildConfig.APPLIANCE_BUILD,
): List<DepthLevel> {
    val credits = stringArrayResource(R.array.about_translator_credits).filter { it.isNotBlank() }
    // The reader is a dialog over Settings; the rows stay list items with the details page's read-more arrow.
    var reading by remember { mutableStateOf<Pair<String, String>?>(null) }
    reading?.let { (title, asset) -> AboutReadingDialog(title, asset) { reading = null } }
    val license = stringResource(R.string.about_license)
    val notices = stringResource(R.string.about_notices)
    val rows = listOf(
        settingsRow("version", stringResource(R.string.about_version), stringResource(
            R.string.about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            stringResource(if (applianceBuild) R.string.about_build_appliance else R.string.about_build_play))),
        settingsRow("server", stringResource(R.string.about_server), aboutServerText(sessionState)),
        settingsRow("license", license, titleMaxLines = 3, trailingIcon = R.drawable.ic_arrow_forward,
            onClick = { reading = license to "LICENSE" }),
        settingsRow("notices", notices, supporting = stringResource(R.string.about_attribution), titleMaxLines = 3,
            trailingIcon = R.drawable.ic_arrow_forward, onClick = { reading = notices to "NOTICE.md" }),
    ) + credits.mapIndexed { index, credit ->
        settingsRow("translator-$index", credit, titleMaxLines = Int.MAX_VALUE,
            section = if (index == 0) stringResource(R.string.about_translators) else null)
    }
    return listOf(
        settingsLevel(ABOUT_LEVEL, stringResource(R.string.settings_about), rows),
    )
}

@Composable
private fun aboutServerText(state: SessionState): String {
    val ready = state as? SessionState.Ready ?: return stringResource(R.string.about_not_connected)
    // Explicit allowlist: never render a profile, endpoint, web root or diagnostic object.
    return listOfNotNull(ready.capabilities.serverName, ready.capabilities.serverVersion)
        .filter { it.isNotBlank() }.joinToString(" · ")
        .ifEmpty { stringResource(R.string.about_connected) }
}

@Composable
private fun AboutReadingDialog(title: String, asset: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = false,
    )) {
        val text = remember(asset) {
            val source = context.assets.open("licenses/$asset").bufferedReader().use { it.readText() }
            if (asset == "NOTICE.md") aboutNoticeText(source) else source
        }
        ProgrammeDetailsFullDescription(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .padding(TvFullScreenPadding).onPreviewKeyEvent {
                    if (it.key == Key.Back) {
                        if (it.type == KeyEventType.KeyUp) onDismiss()
                        true
                    } else false
                },
            title = title,
            heading = { Text(title, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }) },
            reading = {},
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
