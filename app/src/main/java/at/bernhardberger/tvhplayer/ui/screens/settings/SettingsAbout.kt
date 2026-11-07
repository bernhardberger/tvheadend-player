package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.booleanResource
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
import at.bernhardberger.tvhplayer.ui.components.backKeyCycleFocus
import at.bernhardberger.tvhplayer.ui.components.rememberBackKeyCycle
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsFullDescription
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding

internal const val ABOUT_LEVEL = "settings-about"

@Composable
internal fun settingsAboutLevels(
    sessionState: SessionState,
    companionInstalled: Boolean = false,
    machineTranslated: Boolean = booleanResource(R.bool.language_machine_translated),
    whatsNewUnseen: Boolean = false,
    onWhatsNewOpened: () -> Unit = {},
): List<DepthLevel> {
    val credits = stringArrayResource(R.array.about_translator_credits).filter { it.isNotBlank() }
    // The reader is a dialog over Settings; the rows stay list items with the details page's read-more arrow.
    var reading by remember { mutableStateOf<Pair<String, String>?>(null) }
    reading?.let { (title, asset) -> AboutReadingDialog(title, asset) { reading = null } }
    val license = stringResource(R.string.about_license)
    val notices = stringResource(R.string.about_notices)
    val privacy = stringResource(R.string.about_privacy)
    val whatsNew = stringResource(R.string.whats_new)
    val rows = listOf(
        settingsRow("version", stringResource(R.string.about_version), stringResource(
            R.string.about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            stringResource(if (companionInstalled) R.string.about_build_appliance else R.string.about_build_play))),
        settingsRow("whats-new", whatsNew, titleMaxLines = 3, trailingIcon = R.drawable.ic_arrow_forward,
            newMarker = whatsNewUnseen,
            onClick = { reading = whatsNew to "CHANGELOG.md"; onWhatsNewOpened() }),
        settingsRow("server", stringResource(R.string.about_server), aboutServerText(sessionState)),
        settingsRow("license", license, titleMaxLines = 3, trailingIcon = R.drawable.ic_arrow_forward,
            onClick = { reading = license to "LICENSE" }),
        settingsRow("notices", notices, supporting = stringResource(R.string.about_attribution), titleMaxLines = 3,
            trailingIcon = R.drawable.ic_arrow_forward, onClick = { reading = notices to "NOTICE.md" }),
        settingsRow("privacy", privacy, titleMaxLines = 3, trailingIcon = R.drawable.ic_arrow_forward,
            onClick = { reading = privacy to "privacy-policy.md" }),
    )
    // One Translation group: the machine-translated notice first when this language has one, then the credits.
    val translation = stringResource(R.string.about_translation)
    val translationRows = listOfNotNull(if (machineTranslated) {
        settingsRow("machine-translated", stringResource(R.string.about_machine_translated),
            supporting = stringResource(R.string.about_machine_translated_details), section = translation)
    } else null) + credits.mapIndexed { index, credit ->
        settingsRow("translator-$index", credit, titleMaxLines = Int.MAX_VALUE,
            section = if (index == 0 && !machineTranslated) translation else null)
    }
    return listOf(
        settingsLevel(ABOUT_LEVEL, stringResource(R.string.settings_about), rows + translationRows),
    )
}

@Composable
private fun aboutServerText(state: SessionState): String {
    val ready = state as? SessionState.Ready ?: return stringResource(R.string.about_not_connected)
    // Explicit allowlist: never render a profile, endpoint, web root or diagnostic object.
    return listOfNotNull(ready.capabilities.serverName, ready.capabilities.serverVersion)
        .filter { it.isNotBlank() }.joinToString(" • ")
        .ifEmpty { stringResource(R.string.about_connected) }
}

@Composable
private fun AboutReadingDialog(title: String, asset: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val source = remember(asset) {
        context.assets.open("licenses/$asset").bufferedReader().use { it.readText() }
    }
    val blocks = remember(asset, source) { if (asset == "CHANGELOG.md") changelogBlocks(source) else null }
    SettingsReadingDialog(title, onDismiss) {
        if (blocks == null) {
            val text = remember(asset, source) { if (asset.endsWith(".md")) aboutNoticeText(source) else source }
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
        else Column {
            blocks.forEachIndexed { index, block ->
                val isVersion = block.kind == ChangelogBlockKind.VERSION_HEADING
                val isHeading = isVersion || block.kind == ChangelogBlockKind.GROUP_HEADING
                Text(block.text,
                    style = when (block.kind) {
                        ChangelogBlockKind.VERSION_HEADING -> MaterialTheme.typography.titleLarge
                        ChangelogBlockKind.GROUP_HEADING -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.bodyLarge
                    },
                    color = if (block.kind == ChangelogBlockKind.GROUP_HEADING) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = when {
                        index == 0 -> 0.dp
                        isVersion -> 32.dp
                        blocks[index - 1].kind == ChangelogBlockKind.VERSION_HEADING -> 12.dp
                        blocks[index - 1].kind == ChangelogBlockKind.GROUP_HEADING -> 8.dp
                        isHeading -> 20.dp
                        else -> 8.dp
                    }).semantics { if (isHeading) heading() },
                )
            }
        }
    }
}

/** Full-screen Settings reader: the text scrolls, [trailing] stays in view, Back closes only the reader. */
@Composable
internal fun SettingsReadingDialog(
    title: String,
    onDismiss: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backCycle = rememberBackKeyCycle()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = false,
    )) {
        ProgrammeDetailsFullDescription(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .backKeyCycleFocus(backCycle)
                .padding(TvFullScreenPadding).onPreviewKeyEvent {
                    if (it.key == Key.Back) {
                        backCycle.handle(it.type == KeyEventType.KeyDown, it.nativeKeyEvent.repeatCount,
                            it.nativeKeyEvent.isCanceled, onBack = onDismiss)
                    } else false
                },
            title = title,
            heading = { Text(title, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }) },
            reading = {},
            trailing = trailing,
            content = content,
        )
    }
}
