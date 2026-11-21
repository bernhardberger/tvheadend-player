package at.bernhardberger.tvhplayer.touch.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelsActions
import at.bernhardberger.tvhplayer.touch.channels.ChannelsFailure
import at.bernhardberger.tvhplayer.touch.channels.ChannelsMessage
import at.bernhardberger.tvhplayer.touch.channels.MessageKind

/**
 * What stands in for the list while there is none: Material's loading indicator for the first
 * load (section 12's short wait; no recordings skeleton is drawn), and Channels' message form for
 * sign-in needed and for failures, in the catalog's words.
 */
@Composable
internal fun RecordingsStandIn(availability: RecordingsAvailability, actions: RecordingsActions) {
    val messageActions = ChannelsActions(
        onPlay = null,
        onTag = {},
        onRetry = actions.onRetry,
        onOpenConnectionSettings = actions.onOpenConnectionSettings,
        onSignIn = actions.onSignIn,
    )
    when (availability) {
        RecordingsAvailability.Loading -> RecordingsLoading()
        RecordingsAvailability.SignInNeeded -> ChannelsMessage(MessageKind.SignInNeeded, messageActions)
        is RecordingsAvailability.Failed -> ChannelsFailure(availability.error, messageActions)
        RecordingsAvailability.Current, RecordingsAvailability.Offline -> Unit
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordingsLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val loading = stringResource(SharedR.string.recordings_loading)
        LoadingIndicator(Modifier.semantics { contentDescription = loading })
    }
}

/** Channels' offline line above the saved list, in Recordings' words. */
@Composable
internal fun RecordingsOfflineBanner(layout: RecordingsLayout, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.channels_cloud_off), null)
        if (layout.compact) {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(stringResource(R.string.channels_unreachable), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.recordings_saved), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onSettings) {
                Icon(
                    painterResource(R.drawable.channels_settings),
                    stringResource(R.string.channels_connection_settings),
                )
            }
        } else {
            FlowRow(
                Modifier.weight(1f).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.recordings_offline), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.channels_reconnecting), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(onClick = onSettings) { Text(stringResource(R.string.channels_connection_settings)) }
        }
    }
}
