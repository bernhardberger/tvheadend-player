package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.SettingsSectionTitle
import at.bernhardberger.tvhplayer.ui.components.SettingsSwitchRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsGeneral(
    initialFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    settingsStore: UiSettingsStore = koinInject(),
) {
    val selectedLanguage = AppLanguage.fromLanguageTags(
        AppCompatDelegate.getApplicationLocales().toLanguageTags()
    )
    val languageOptions = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.language_follow_system),
        AppLanguage.GERMAN to stringResource(R.string.language_german),
        AppLanguage.ENGLISH to stringResource(R.string.language_english),
    )
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val scope = rememberCoroutineScope()

    SettingsPane(
        title = stringResource(R.string.settings_general),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .verticalScroll(rememberScrollState())
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsSectionTitle(stringResource(R.string.settings_language))
            languageOptions.forEachIndexed { index, (language, label) ->
                ListItem(
                    selected = language == selectedLanguage,
                    onClick = {
                        if (language != selectedLanguage) {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(language.languageTag)
                            )
                        }
                    },
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
                onClick = {
                    scope.launch { settingsStore.setShowEpgMenu(!settings.showEpgMenu) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
