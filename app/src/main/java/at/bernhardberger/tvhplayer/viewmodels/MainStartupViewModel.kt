package at.bernhardberger.tvhplayer.viewmodels

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
) : ViewModel() {
    val applianceLaunchRequests = ApplianceLaunchRequests()
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
}
