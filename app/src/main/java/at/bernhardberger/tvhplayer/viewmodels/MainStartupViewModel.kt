package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.core.StartupBootstrapCoordinator
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import at.bernhardberger.tvhplayer.settings.SimpleTvSettingsStore
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.SimpleTvSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainStartupViewModel(
    serverSettingsStore: ServerSettingsStore,
    uiSettingsStore: UiSettingsStore,
    simpleTvSettingsStore: SimpleTvSettingsStore,
    simpleTvSession: SimpleTvSession,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val createStartupRequest = shouldCreateStartupRequest(savedStateHandle)
    private val initialActivityIntentPolicy = InitialActivityIntentPolicy(
        allowRestoredIntent = createStartupRequest,
    )
    val applianceLaunchRequests = createRetainedApplianceLaunchRequests(savedStateHandle)
    private val _state = MutableStateFlow<MainStartupState>(MainStartupState.ResolvingLocal)
    val state = _state.asStateFlow()
    private val _runtimeServerSettings = MutableStateFlow<ServerSettings?>(null)
    val runtimeServerSettings = _runtimeServerSettings.asStateFlow()
    private val bootstrapCoordinator = StartupBootstrapCoordinator(
        applianceLaunchRequests = applianceLaunchRequests,
        loadServerSettings = { serverSettingsStore.serverSettings.first() },
        loadUiSettings = { uiSettingsStore.settings.first() },
        loadSimpleTvSettings = { simpleTvSettingsStore.settings.first() },
        startSimpleTvSession = simpleTvSession::start,
        createStartupRequest = createStartupRequest,
        onStartupRequestCreationHandled = {
            markStartupRequestCreationHandled(savedStateHandle)
        },
        startupState = _state,
    )

    init {
        viewModelScope.launch {
            bootstrapCoordinator.bootstrap()
            // Ready keeps the immutable startup decision; this observation only
            // refreshes the configured/onboarding branch after bootstrap.
            serverSettingsStore.serverSettings.collect { server ->
                _runtimeServerSettings.value = server
            }
        }
    }

    internal fun shouldHandleInitialActivityIntent(activityWasRestored: Boolean): Boolean =
        initialActivityIntentPolicy.shouldHandle(activityWasRestored)

    internal class InitialActivityIntentPolicy(
        private val allowRestoredIntent: Boolean,
    ) {
        private var initialIntentHandled = false

        fun shouldHandle(activityWasRestored: Boolean): Boolean {
            if (initialIntentHandled) return false
            initialIntentHandled = true
            return !activityWasRestored || allowRestoredIntent
        }
    }

    companion object {
        private const val RETAINED_REQUEST_ID_KEY = "startup.retainedRequestId"
        private const val STARTUP_REQUEST_CREATION_HANDLED_KEY =
            "startup.requestCreationHandled"

        internal fun createRetainedApplianceLaunchRequests(
            savedStateHandle: SavedStateHandle,
        ): ApplianceLaunchRequests = ApplianceLaunchRequests(
            restoredRequestId = savedStateHandle[RETAINED_REQUEST_ID_KEY],
            onRetainedRequestIdChanged = { requestId ->
                if (requestId == null) {
                    savedStateHandle.remove<Long>(RETAINED_REQUEST_ID_KEY)
                } else {
                    savedStateHandle[RETAINED_REQUEST_ID_KEY] = requestId
                }
            },
        )

        internal fun shouldCreateStartupRequest(savedStateHandle: SavedStateHandle): Boolean =
            savedStateHandle.get<Boolean>(STARTUP_REQUEST_CREATION_HANDLED_KEY) != true

        internal fun markStartupRequestCreationHandled(savedStateHandle: SavedStateHandle) {
            savedStateHandle[STARTUP_REQUEST_CREATION_HANDLED_KEY] = true
        }
    }
}
