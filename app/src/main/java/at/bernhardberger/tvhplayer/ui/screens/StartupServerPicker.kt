package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsSetupFrame
import at.bernhardberger.tvhplayer.ui.notifications.AppShellNoticeHost

/** Cold startup has no drawer; Settings' key-cycle frame owns Back releases for this page. */
@Composable
internal fun StartupServerPicker(onFinished: () -> Unit, onExisting: (String) -> Unit = { onFinished() },
    selectionPending: Boolean = false,
    onSelect: (at.bernhardberger.tvheadend.sdk.core.ServerProfileId) -> Unit,
) {
    SettingsSetupFrame {
        BackHandler(onBack = onFinished)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ServerSwitcherScreen(true, true, onFinished, onExisting = { onExisting(it.value) },
                contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp),
                selectionPending = selectionPending, onSelect = onSelect)
            AppShellNoticeHost()
        }
    }
}
