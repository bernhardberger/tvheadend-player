package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionFailureKind
import at.bernhardberger.tvhplayer.core.ConnectionProbeResult

sealed interface ConnectionProbeUiState {
    data object Idle : ConnectionProbeUiState
    data object Testing : ConnectionProbeUiState
    data class Complete(val result: ConnectionProbeResult) : ConnectionProbeUiState
}

@Composable
fun connectionProbeMessage(state: ConnectionProbeUiState): String? = when (state) {
    ConnectionProbeUiState.Idle -> null
    ConnectionProbeUiState.Testing -> stringResource(R.string.connection_test_running)
    is ConnectionProbeUiState.Complete -> when (val result = state.result) {
        is ConnectionProbeResult.Success -> pluralStringResource(
            R.plurals.connection_test_success,
            result.channelCount,
            result.channelCount,
        )
        is ConnectionProbeResult.Failure -> stringResource(
            when (result.kind) {
                ConnectionFailureKind.AUTHENTICATION ->
                    R.string.connection_test_authentication
                ConnectionFailureKind.DNS -> R.string.connection_test_dns
                ConnectionFailureKind.UNREACHABLE -> R.string.connection_test_unreachable
                ConnectionFailureKind.TIMEOUT -> R.string.connection_test_timeout
                ConnectionFailureKind.INCOMPATIBLE_SERVER ->
                    R.string.connection_test_incompatible
                ConnectionFailureKind.PERMISSION_DENIED ->
                    R.string.connection_test_permission
                ConnectionFailureKind.ZERO_CHANNELS -> R.string.connection_test_zero_channels
                ConnectionFailureKind.OTHER -> R.string.connection_test_other
            }
        )
    }
}
