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
    onWhatsNewOpened: () -> Unit = {},
): List<DepthLevel> {
    val credits = stringArrayResource(R.array.about_translator_credits).filter { it.isNotBlank() }
    // The reader is a dialog over Settings; reader rows have no trailing navigation icon.
    var reading by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    reading?.let { (title, assets) ->
        AboutReadingDialog(title, assets.first(), assets.drop(1)) { reading = null }
    }
    val licenses = stringResource(R.string.about_licenses)
    val privacy = stringResource(R.string.about_privacy)
    val whatsNew = stringResource(R.string.whats_new)
    val rows = listOf(
        settingsRow("version", stringResource(R.string.about_version), stringResource(
            R.string.about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            stringResource(if (companionInstalled) R.string.about_build_appliance else R.string.about_build_play))),
        settingsRow("whats-new", whatsNew, titleMaxLines = 3,
            onClick = { reading = whatsNew to listOf("CHANGELOG.md"); onWhatsNewOpened() }),
        settingsRow("server", stringResource(R.string.about_server), aboutServerText(sessionState)),
        settingsRow("licenses", licenses, supporting = stringResource(R.string.about_licenses_summary), titleMaxLines = 3,
            onClick = { reading = licenses to listOf("NOTICE.md", "LICENSE") }),
        settingsRow("privacy", privacy, titleMaxLines = 3,
            onClick = { reading = privacy to listOf("privacy-policy.md") }),
    )
    val translation = stringResource(R.string.about_translation)
    val translationRows = credits.mapIndexed { index, credit ->
        settingsRow("translator-$index", credit, titleMaxLines = Int.MAX_VALUE,
            section = if (index == 0) translation else null)
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
internal fun AboutReadingDialog(title: String, asset: String, followingAssets: List<String> = emptyList(), onDismiss: () -> Unit) {
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
        followingAssets.forEach { followingAsset ->
            val followingSource = remember(followingAsset) {
                context.assets.open("licenses/$followingAsset").bufferedReader().use { it.readText() }
            }
            Text(followingSource, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 32.dp))
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
