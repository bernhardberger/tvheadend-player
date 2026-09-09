package at.bernhardberger.tvhplayer.profiling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail

/** Offline rail-only fixture. No SDK, persisted profiles, images or live destinations. */
class RailProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var route by remember { mutableStateOf(AppDestination.CHANNELS) }
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = route,
                    showEpgMenu = true,
                    onRootBack = { finish() },
                    onNavigate = { route = it },
                ) { padding, _ ->
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(padding),
                    ) {
                        Text(
                            "Offline rail fixture: ${route.name}",
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}
