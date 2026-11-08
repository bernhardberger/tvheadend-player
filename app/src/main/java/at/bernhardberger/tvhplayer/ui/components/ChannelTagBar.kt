package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

@Composable
fun UnavailableTagNotice(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        colors = SurfaceDefaults.colors(
            containerColor = TvSurfaceColors.containerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.active_tag_unavailable),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onDismiss, modifier = Modifier.tabFocus()) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}
