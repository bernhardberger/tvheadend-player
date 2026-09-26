package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.core.PlayerStatus

/** The host owns safe-area positioning and places PlayerTopEndScrim behind this cluster. */
@Composable
fun PlayerTopStatusCluster(clock: String, status: PlayerStatus?, modifier: Modifier = Modifier, recording: PlayerStatus? = null) {
    Column(modifier.padding(8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(clock, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f))
        status?.let { PlayerStatusChip(it) }
        recording?.let { PlayerStatusChip(it) }
    }
}
