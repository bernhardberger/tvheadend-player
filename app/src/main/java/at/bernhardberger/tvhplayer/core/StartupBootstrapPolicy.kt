package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface MainStartupState {
    data object ResolvingLocal : MainStartupState

    data class Ready(
        val server: ServerSettings,
        val autoStartPlayback: Boolean,
    ) : MainStartupState
}

internal fun MainStartupState.Ready.serverSettingsForRuntime(
    runtimeServer: ServerSettings?,
): ServerSettings = runtimeServer ?: server

internal class StartupBootstrapCoordinator(
    val applianceLaunchRequests: ApplianceLaunchRequests,
    private val loadServerSettings: suspend () -> ServerSettings,
    private val loadUiSettings: suspend () -> UiSettings,
    private val createStartupRequest: Boolean = true,
    private val onStartupRequestCreationHandled: () -> Unit = {},
    private val startupState: MutableStateFlow<MainStartupState> =
        MutableStateFlow(MainStartupState.ResolvingLocal),
) {
    private val bootstrapMutex = Mutex()
    val state = startupState.asStateFlow()

    suspend fun bootstrap() {
        bootstrapMutex.withLock {
            if (startupState.value is MainStartupState.Ready) return@withLock

            val server = loadServerSettings()
            val uiSettings = loadUiSettings()
            currentCoroutineContext().ensureActive()
            val configured = server.host.isNotBlank()
            val autoStartPlayback = configured && uiSettings.autoStartPlayback
            if (createStartupRequest) {
                applianceLaunchRequests.requestStartup(
                    autoStartPlayback,
                )
            }
            onStartupRequestCreationHandled()
            startupState.value = MainStartupState.Ready(
                server = server,
                autoStartPlayback = autoStartPlayback,
            )
        }
    }
}
