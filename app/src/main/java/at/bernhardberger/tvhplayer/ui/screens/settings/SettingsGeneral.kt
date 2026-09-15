package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.cacheSizeMegabytes
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.viewmodels.CacheClearState
import at.bernhardberger.tvhplayer.viewmodels.SettingsStorageViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

internal const val LANGUAGE_LEVEL = "app-language"

@Composable
internal fun settingsGeneralLevels(
    navigation: DepthNavigationState,
    settingsStore: UiSettingsStore = koinInject(),
    storageViewModel: SettingsStorageViewModel = koinViewModel(),
): List<DepthLevel> {
    val selectedLanguage = AppLanguage.fromLanguageTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val statistics by storageViewModel.statistics.collectAsStateWithLifecycle()
    val clearState by storageViewModel.clearState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    return settingsGeneralLevels(
        settings, selectedLanguage, statistics, clearState,
        onSelectLanguage = { language ->
            // Save the parent identity/viewport BEFORE AppCompat recreates the activity.
            navigation.pop()
            if (language != selectedLanguage) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag))
            }
        },
        onToggleEpg = { scope.launch { settingsStore.setShowEpgMenu(!settings.showEpgMenu) } },
        onClearCache = storageViewModel::clearCache,
    )
}

/** Plain production presentation shared by runtime and deterministic captures. */
@Composable
internal fun settingsGeneralLevels(
    settings: UiSettings,
    selectedLanguage: AppLanguage,
    statistics: CacheStatistics,
    clearState: CacheClearState,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleEpg: () -> Unit,
    onClearCache: () -> Unit,
): List<DepthLevel> {
    val languages = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.language_follow_system),
        AppLanguage.GERMAN to stringResource(R.string.language_german),
        AppLanguage.ENGLISH to stringResource(R.string.language_english),
    )
    val languageTitle = stringResource(R.string.settings_app_language)
    val announcement = when (clearState) {
        CacheClearState.IDLE -> null
        CacheClearState.CLEARING -> stringResource(R.string.cache_clearing)
        CacheClearState.CLEARED, CacheClearState.FAILED -> null
    }
    val usage = stringResource(R.string.cache_usage, cacheSizeMegabytes(statistics, LocalConfiguration.current.locales[0]))
    return listOf(
        settingsLevel(SettingsSection.GENERAL.name, stringResource(R.string.settings_general), listOf(
            settingsRow(LANGUAGE_LEVEL, languageTitle, languages.first { it.first == selectedLanguage }.second, child = LANGUAGE_LEVEL),
            settingsRow("guide-menu", stringResource(R.string.show_epg_menu),
                section = stringResource(R.string.settings_navigation), checked = settings.showEpgMenu, onClick = onToggleEpg),
            settingsRow("clear-cache", stringResource(R.string.clear_cache),
                supporting = usage,
                section = stringResource(R.string.settings_storage), announcement = announcement,
                busy = clearState == CacheClearState.CLEARING,
                trailingIcon = R.drawable.ic_delete_outlined,
                onClick = { if (clearState != CacheClearState.CLEARING) onClearCache() }),
        )),
        settingsLevel(LANGUAGE_LEVEL, languageTitle, languages.map { (language, label) ->
            settingsRow(language.name, label, selected = language == selectedLanguage, onClick = { onSelectLanguage(language) })
        }, initialItemId = selectedLanguage.name),
    )
}
