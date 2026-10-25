package at.bernhardberger.tvhplayer.ui.screens.settings

import android.view.KeyEvent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsFullDescription
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel

internal const val ABOUT_LEVEL = "settings-about"
internal const val ABOUT_LICENSE_LEVEL = "settings-about-license"
internal const val ABOUT_NOTICES_LEVEL = "settings-about-notices"

@Composable
internal fun settingsAboutLevels(
    sessionState: SessionState,
    applianceBuild: Boolean = BuildConfig.APPLIANCE_BUILD,
): List<DepthLevel> {
    val credits = stringArrayResource(R.array.about_translator_credits).filter { it.isNotBlank() }
    val rows = listOf(
        settingsRow("version", stringResource(R.string.about_version), stringResource(
            R.string.about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            stringResource(if (applianceBuild) R.string.about_build_appliance else R.string.about_build_play))),
        settingsRow("server", stringResource(R.string.about_server), aboutServerText(sessionState)),
        settingsRow("license", stringResource(R.string.about_license), child = ABOUT_LICENSE_LEVEL, titleMaxLines = 3),
        settingsRow("notices", stringResource(R.string.about_notices),
            supporting = stringResource(R.string.about_attribution), child = ABOUT_NOTICES_LEVEL, titleMaxLines = 3),
    ) + credits.mapIndexed { index, credit ->
        settingsRow("translator-$index", credit, titleMaxLines = Int.MAX_VALUE,
            section = if (index == 0) stringResource(R.string.about_translators) else null)
    }
    return listOf(
        settingsLevel(ABOUT_LEVEL, stringResource(R.string.settings_about), rows),
        aboutReadingLevel(ABOUT_LICENSE_LEVEL, stringResource(R.string.about_license), "LICENSE"),
        aboutReadingLevel(ABOUT_NOTICES_LEVEL, stringResource(R.string.about_notices), "NOTICE.md"),
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
private fun aboutReadingLevel(id: String, title: String, asset: String): DepthLevel {
    val context = LocalContext.current
    return settingsLevel(id, title, emptyList(), activeContent = { requester, left ->
        val text = remember(asset) { context.assets.open("licenses/$asset").bufferedReader().use { it.readText() } }
        ProgrammeDetailsFullDescription(
            modifier = Modifier.padding(end = 48.dp).focusRequester(requester).onPreviewKeyEvent {
                it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && left(it.nativeKeyEvent)
            },
            title = title,
            heading = { Text(title, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }) },
            reading = {},
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    })
}
