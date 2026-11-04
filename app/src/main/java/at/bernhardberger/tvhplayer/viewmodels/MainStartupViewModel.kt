package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.core.StartupBootstrapCoordinator
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.settings.toServerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.settings.shouldShowStartupPicker
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import kotlinx.coroutines.CancellationException

class MainStartupViewModel(
    private val profileOwner: AppProfileOwner,
    uiSettingsStore: UiSettingsStore,
    savedStateHandle: SavedStateHandle,
    noteViewingIntent: () -> Unit = {},
    applianceBuild: Boolean = BuildConfig.APPLIANCE_BUILD,
) : ViewModel() {
    private val choice = CompletableDeferred<Unit>()
    private val _startupPickerVisible = MutableStateFlow(false)
    val startupPickerVisible = _startupPickerVisible.asStateFlow()
    private val _startupSelectionPending = MutableStateFlow(false)
    val startupSelectionPending = _startupSelectionPending.asStateFlow()
    private var completionRequested = false
    var startupSettingsProfileId: String? = null
        private set
    fun openStartupProfile(id: String) { startupSettingsProfileId = id; finishStartupPicker() }
    fun finishStartupPicker() {
        completionRequested = true
        if (!_startupSelectionPending.value) {
            _startupPickerVisible.value = false
            choice.complete(Unit)
        }
    }

    /** The retained cold-launch decision owns an admitted selection, not the Activity's page. */
    fun selectStartupServer(id: ServerProfileId, notices: NoticeCenter) {
        if (_startupSelectionPending.value) return
        _startupSelectionPending.value = true
        viewModelScope.launch {
            try {
                if (profileOwner.setActive(id) is ServerProfileWriteResult.Applied)
                    completionRequested = true
                else notices.post(Notice.ServerProfileWriteFailed, notices.context())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notices.post(Notice.ServerProfileWriteFailed, notices.context())
            } finally {
                _startupSelectionPending.value = false
                if (completionRequested) finishStartupPicker()
            }
        }
    }
    private val createStartupRequest = shouldCreateStartupRequest(savedStateHandle)
    private val initialActivityIntentPolicy = InitialActivityIntentPolicy(
        allowRestoredIntent = createStartupRequest,
    )
    val applianceLaunchRequests = createRetainedApplianceLaunchRequests(savedStateHandle, noteViewingIntent)
    private val _state = MutableStateFlow<MainStartupState>(MainStartupState.ResolvingLocal)
    val state = _state.asStateFlow()
    private val _runtimeServerSettings = MutableStateFlow<ServerSettings?>(null)
    val runtimeServerSettings = _runtimeServerSettings.asStateFlow()
    private val bootstrapCoordinator = StartupBootstrapCoordinator(
        applianceLaunchRequests = applianceLaunchRequests,
        loadServerSettings = {
            profileOwner.serverProfile.filterNotNull().first().also {
                applianceLaunchRequests.observeProfile(it)
            }.toServerSettings()
        },
        loadUiSettings = {
            uiSettingsStore.settings.first().let { settings ->
                if (startupSettingsProfileId == null) settings else settings.copy(autoStartPlayback = false)
            }
        },
        createStartupRequest = createStartupRequest,
        onStartupRequestCreationHandled = {
            markStartupRequestCreationHandled(savedStateHandle)
        },
        startupState = _state,
    )

    init {
        viewModelScope.launch {
            // Admission to autoplay is held here, before bootstrap can publish Ready or request
            // playback. The retained ViewModel owns this cold-launch decision, never onResume.
            profileOwner.profiles.filterNotNull().first()
            if (createStartupRequest && !applianceBuild && shouldShowStartupPicker(
                    profileOwner.askAtStartup.first(), profileOwner.switcherProfiles.first())) {
                _startupPickerVisible.value = true
                choice.await()
            }
            bootstrapCoordinator.bootstrap()
            // Ready keeps the immutable startup decision; this observation only
            // refreshes the configured/onboarding branch after bootstrap.
            profileOwner.serverProfile.filterNotNull().collect { profile ->
                applianceLaunchRequests.observeProfile(profile)
                _runtimeServerSettings.value = profile.toServerSettings()
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
            noteViewingIntent: () -> Unit = {},
        ): ApplianceLaunchRequests = ApplianceLaunchRequests(
            restoredRequestId = savedStateHandle[RETAINED_REQUEST_ID_KEY],
            onRetainedRequestIdChanged = { requestId ->
                if (requestId == null) {
                    savedStateHandle.remove<Long>(RETAINED_REQUEST_ID_KEY)
                } else {
                    savedStateHandle[RETAINED_REQUEST_ID_KEY] = requestId
                }
            },
            onLaunchRequested = noteViewingIntent,
        )

        internal fun shouldCreateStartupRequest(savedStateHandle: SavedStateHandle): Boolean =
            savedStateHandle.get<Boolean>(STARTUP_REQUEST_CREATION_HANDLED_KEY) != true

        internal fun markStartupRequestCreationHandled(savedStateHandle: SavedStateHandle) {
            savedStateHandle[STARTUP_REQUEST_CREATION_HANDLED_KEY] = true
        }
    }
}
