package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.ui.components.SettingsPane

@Composable
fun SettingsLanguage(initialFocusRequester: FocusRequester) {
    val selected = AppLanguage.fromLanguageTags(
        AppCompatDelegate.getApplicationLocales().toLanguageTags()
    )
    val options = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.language_follow_system),
        AppLanguage.GERMAN to stringResource(R.string.language_german),
        AppLanguage.ENGLISH to stringResource(R.string.language_english),
    )

    SettingsPane(title = stringResource(R.string.settings_language)) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, (language, label) ->
                val onClick = {
                    if (language != selected) {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(language.languageTag)
                        )
                    }
                }
                ListItem(
                    selected = language == selected,
                    onClick = onClick,
                    headlineContent = { Text(label) },
                    trailingContent = {
                        RadioButton(
                            selected = language == selected,
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
        }
    }
}
