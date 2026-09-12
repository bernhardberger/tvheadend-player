package at.bernhardberger.tvhplayer.viewmodels

import at.bernhardberger.tvhplayer.profiling.profileTrace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.MainThread
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.RetainedMetadataAuthority
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.core.channelCatalogAuthority
import at.bernhardberger.tvheadend.sdk.core.channelCatalogForDisplay
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import at.bernhardberger.tvhplayer.core.TagScopeFallback
import at.bernhardberger.tvhplayer.core.resolveChannelScope
import at.bernhardberger.tvhplayer.core.resolveOrderedChannelScope
import at.bernhardberger.tvhplayer.core.orderBrowseChannels
import at.bernhardberger.tvhplayer.core.updateChannelScopeVisibility
import at.bernhardberger.tvhplayer.settings.ChannelTagPreferences
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChannelScopeState(
    val scope: ChannelBrowsingScope,
    val channelCatalogCurrent: Boolean,
    val settingsLoaded: Boolean = true,
    val visibility: ChannelScopeVisibility = ChannelScopeVisibility(),
)

class ChannelsViewModel(
    private val session: TvheadendSession,
    private val tagSettings: ChannelTagSettingsStore,
) : ViewModel() {
    val observation: StateFlow<SessionObservation> = session.observation

    private val mutableScope = MutableStateFlow(
        resolveChannelScopeState(ChannelRepositoryState.Empty, null).copy(settingsLoaded = false),
    )
    val scope = mutableScope.asStateFlow()
    private var preferences: ChannelTagPreferences? = null
    private var tagSelectedBeforeLoad = false
    private var initialTagId: ChannelTagId? = null
    private var catalogChannels: List<Channel>? = null
    private var orderedChannels: List<Channel> = emptyList()
    private var unsavedPreferences: ChannelTagPreferences? = null
    private var saveJob: Job? = null
    private var loadJob: Job? = null
    private val mutableSettingsFailure = MutableStateFlow(false)
    val settingsFailure = mutableSettingsFailure.asStateFlow()
    private val mutableUnavailableTagNotice = MutableStateFlow(false)
    val unavailableTagNotice = mutableUnavailableTagNotice.asStateFlow()

    val channels = scope.map { it.scope.visibleChannels }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )
    init {
        loadSettings()
        viewModelScope.launch {
            observation.map { it.channelState }.distinctUntilChanged().collect { publishScope() }
        }
    }

    private fun loadSettings() {
        if (loadJob?.isActive == true || preferences != null) return
        loadJob = viewModelScope.launch {
            try {
                val saved = tagSettings.settings.first()
                preferences = if (tagSelectedBeforeLoad) saved.copy(activeTagId = initialTagId) else saved
                mutableSettingsFailure.value = false
                publishScope()
                if (preferences != saved) enqueueSave()
            } catch (_: IOException) {
                mutableSettingsFailure.value = true
            }
        }
    }

    /** UI intent and its resolved scope are published together, before any storage suspension. */
    @MainThread
    fun selectTag(tagId: ChannelTagId?) {
        mutableUnavailableTagNotice.value = false
        val current = preferences
        if (current == null) {
            tagSelectedBeforeLoad = true
            initialTagId = tagId
            return
        }
        preferences = current.copy(activeTagId = tagId)
        publishScope()
        if (preferences != current) enqueueSave()
    }

    @MainThread
    fun toggleScopeVisibility(tagId: ChannelTagId?) {
        val visibility = preferences?.visibility ?: return
        setScopeVisible(tagId, if (tagId == null) !visibility.isAllChannelsVisible() else !visibility.isTagVisible(tagId))
    }

    @MainThread
    fun setScopeVisible(tagId: ChannelTagId?, visible: Boolean) {
        val current = preferences ?: return
        val channelState = observation.value.channelState
        val tags = channelState.channelCatalogForDisplay?.tags.orEmpty()
        val availableTagIds = tags.mapTo(mutableSetOf()) { it.id }
        if (channelState.channelCatalogAuthority != RetainedMetadataAuthority.CURRENT) {
            availableTagIds += current.visibility.visibleTagIds
        }
        preferences = current.copy(visibility = updateChannelScopeVisibility(
            current.visibility, availableTagIds, tagId, visible,
        ))
        publishScope()
        if (preferences != current) enqueueSave()
    }

    private fun publishScope() {
        val requested = preferences ?: return
        val channelState = observation.value.channelState
        val catalog = channelState.channelCatalogForDisplay ?: ChannelCatalog.create()
        if (catalog.channels != catalogChannels) {
            catalogChannels = catalog.channels
            orderedChannels = orderBrowseChannels(catalog.channels)
        }
        val current = channelState.channelCatalogAuthority == RetainedMetadataAuthority.CURRENT
        var effective = requested
        if (current && !requested.visibility.isAllChannelsVisible() &&
            catalog.tags.none { requested.visibility.isTagVisible(it.id) }
        ) {
            effective = requested.copy(visibility = requested.visibility.copy(allChannelsVisible = true))
        }
        val resolved = profileTrace("P44:channelScope") {
            resolveOrderedChannelScope(orderedChannels, catalog.tags, effective.activeTagId, effective.visibility)
        }
        if (current && resolved.fallback != null) {
            effective = effective.copy(activeTagId = resolved.activeTagId)
            mutableUnavailableTagNotice.value = resolved.fallback == TagScopeFallback.TAG_UNAVAILABLE
        }
        preferences = effective
        mutableScope.value = ChannelScopeState(resolved, current, visibility = effective.visibility)
        if (effective != requested) enqueueSave()
    }

    private fun enqueueSave() {
        unsavedPreferences = preferences
        if (saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            while (true) {
                val saving = unsavedPreferences ?: break
                try {
                    tagSettings.save(saving)
                } catch (_: IOException) {
                    mutableSettingsFailure.value = true
                    break
                }
                // A completed old write acknowledges only itself, never a newer UI choice.
                if (unsavedPreferences == saving) unsavedPreferences = null
                mutableSettingsFailure.value = false
            }
        }
    }

    @MainThread
    fun retrySettings() {
        if (preferences == null) loadSettings() else enqueueSave()
    }

    fun dismissUnavailableTagNotice() {
        mutableUnavailableTagNotice.value = false
    }

    fun nowEvent(channelId: ChannelId, nowSec: Long): EpgEvent? =
        session.observation.value.eventAt(channelId, kotlin.time.Instant.fromEpochSeconds(nowSec))

    fun nextEvent(channelId: ChannelId, nowSec: Long): EpgEvent? =
        session.observation.value.nextEvent(channelId, kotlin.time.Instant.fromEpochSeconds(nowSec))
}

internal fun resolveChannelScopeState(
    channelState: ChannelRepositoryState,
    activeTagId: ChannelTagId?,
    visibility: ChannelScopeVisibility = ChannelScopeVisibility(),
): ChannelScopeState {
    val catalog = channelState.channelCatalogForDisplay ?: ChannelCatalog.create()
    return ChannelScopeState(
        scope = resolveChannelScope(catalog.channels, catalog.tags, activeTagId, visibility),
        channelCatalogCurrent = channelState.channelCatalogAuthority ==
            RetainedMetadataAuthority.CURRENT,
        visibility = visibility,
    )
}
