package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvhplayer.core.cacheSizeMegabytes
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.SettingsSectionTitle
import at.bernhardberger.tvhplayer.ui.components.SettingsSwitchRow
import at.bernhardberger.tvhplayer.viewmodels.CacheClearState
import at.bernhardberger.tvhplayer.viewmodels.SettingsStorageViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsGeneral(
    initialFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    settingsStore: UiSettingsStore = koinInject(),
    storageViewModel: SettingsStorageViewModel = koinViewModel(),
) {
    val selectedLanguage = AppLanguage.fromLanguageTags(
        AppCompatDelegate.getApplicationLocales().toLanguageTags()
    )
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val statistics by storageViewModel.statistics.collectAsStateWithLifecycle()
    val clearState by storageViewModel.clearState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SettingsGeneralContent(
        initialFocusRequester = initialFocusRequester,
        settings = settings,
        selectedLanguage = selectedLanguage,
        onSelectLanguage = { language ->
            if (language != selectedLanguage) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag))
            }
        },
        onToggleEpg = { scope.launch { settingsStore.setShowEpgMenu(!settings.showEpgMenu) } },
        statistics = statistics,
        clearState = clearState,
        onClearCache = storageViewModel::clearCache,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsGeneralContent(
    initialFocusRequester: FocusRequester,
    settings: UiSettings,
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleEpg: () -> Unit,
    statistics: CacheStatistics,
    clearState: CacheClearState,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageOptions = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.language_follow_system),
        AppLanguage.GERMAN to stringResource(R.string.language_german),
        AppLanguage.ENGLISH to stringResource(R.string.language_english),
    )
    val scroll = rememberScrollState()
    val cacheInteraction = remember { MutableInteractionSource() }
    val cacheFocused by cacheInteraction.collectIsFocusedAsState()

    SettingsPane(
        title = stringResource(R.string.settings_general),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Do not mask a focused control when navigating back up the pane.
                    if (scroll.canScrollBackward && cacheFocused) {
                        val fadeHeight = 56.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color.Black,
                                endY = fadeHeight,
                            ),
                            size = Size(size.width, fadeHeight),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                .verticalScroll(scroll)
                .padding(vertical = 16.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsSectionTitle(stringResource(R.string.settings_language))
            languageOptions.forEachIndexed { index, (language, label) ->
                ListItem(
                    selected = language == selectedLanguage,
                    onClick = { onSelectLanguage(language) },
                    headlineContent = { Text(label) },
                    trailingContent = {
                        RadioButton(
                            selected = language == selectedLanguage,
                            onClick = null,
                        )
                    },
                    scale = ListItemDefaults.scale(
                        focusedScale = 1f,
                        focusedSelectedScale = 1f,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (index == 0) {
                                Modifier.focusRequester(initialFocusRequester)
                            } else {
                                Modifier
                            }
                        ),
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_navigation))
            SettingsSwitchRow(
                label = stringResource(R.string.show_epg_menu),
                checked = settings.showEpgMenu,
                supportingText = stringResource(R.string.show_epg_menu_description),
                onClick = onToggleEpg,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            SettingsSectionTitle(stringResource(R.string.settings_storage))
            val usage = stringResource(
                R.string.cache_statistics,
                cacheSizeMegabytes(statistics, LocalConfiguration.current.locales[0]),
                statistics.artworkEntryCount,
            )
            val status = when (clearState) {
                CacheClearState.IDLE -> stringResource(R.string.cache_clear_description)
                CacheClearState.CLEARING -> stringResource(R.string.cache_clearing)
                CacheClearState.CLEARED -> stringResource(R.string.cache_cleared)
                CacheClearState.FAILED -> stringResource(R.string.cache_clear_failed)
            }
            val statusColor = if (clearState == CacheClearState.FAILED) {
                if (cacheFocused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.error
            } else {
                Color.Unspecified
            }
            ListItem(
                selected = false,
                onClick = { if (clearState != CacheClearState.CLEARING) onClearCache() },
                headlineContent = { Text(stringResource(R.string.clear_cache)) },
                supportingContent = {
                    Column {
                        Text(usage)
                        Text(
                            status,
                            color = statusColor,
                            fontWeight = if (clearState == CacheClearState.CLEARING) FontWeight.SemiBold else null,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                },
                trailingContent = {
                    if (clearState == CacheClearState.CLEARING) {
                        CircularProgressIndicator(
                            color = LocalContentColor.current,
                            trackColor = LocalContentColor.current.copy(alpha = 0.25f),
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(
                            imageVector = when (clearState) {
                                CacheClearState.CLEARED -> Icons.Outlined.CheckCircle
                                CacheClearState.FAILED -> Icons.Outlined.ErrorOutline
                                else -> Icons.Outlined.DeleteOutline
                            },
                            contentDescription = null,
                            tint = if (clearState == CacheClearState.FAILED) statusColor else LocalContentColor.current,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
                interactionSource = cacheInteraction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
