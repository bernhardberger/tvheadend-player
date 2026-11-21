package at.bernhardberger.tvhplayer.touch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.screens.onboarding.discoverServers
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val session: TvheadendSession by inject()
    private val owner: AppProfileOwner by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                TouchApp(session, owner, discovery = { discoverServers(applicationContext) })
            }
        }
    }
}
