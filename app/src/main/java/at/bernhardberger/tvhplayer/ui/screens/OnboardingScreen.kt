package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.screens.onboarding.ConnectionSetupFlow
import at.bernhardberger.tvhplayer.ui.screens.onboarding.discoverServers
import at.bernhardberger.tvhplayer.ui.screens.onboarding.rememberConnectionSetupState
import org.koin.compose.koinInject

/** First run: the guided connection setup. Saving its profile hands the app over to the browse screens. */
@Composable
fun OnboardingScreen(
    session: TvheadendSession = koinInject(),
    settingsStore: AppProfileOwner = koinInject(),
) {
    val context = LocalContext.current.applicationContext
    val state = rememberConnectionSetupState(session, settingsStore, discovery = { discoverServers(context) })
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        ConnectionSetupFlow(state)
    }
}
