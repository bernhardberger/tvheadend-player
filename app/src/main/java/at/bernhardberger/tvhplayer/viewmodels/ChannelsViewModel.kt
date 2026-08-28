package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.TagScopeFallback
import at.bernhardberger.tvhplayer.core.resolveChannelScope
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelsViewModel(
    private val session: TvheadendSession,
    private val tagSettings: ChannelTagSettingsStore,
) : ViewModel() {
    val observation: StateFlow<SessionObservation> = session.observation

    val scope: StateFlow<ChannelBrowsingScope> = combine(
        session.observation,
        tagSettings.activeTagId,
        tagSettings.scopeVisibility,
    ) { observation, activeTagId, visibility ->
        val catalog = observation.channelCatalog()
        resolveChannelScope(catalog.channels, catalog.tags, activeTagId, visibility)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = resolveChannelScope(emptyList(), emptyList(), null),
    )

    val channels = scope.map { it.visibleChannels }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )
    val allChannels = session.observation.map { it.channelCatalog().channels }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val unavailableTagNotice = tagSettings.unavailableTagNotice

    init {
        viewModelScope.launch {
            combine(scope, session.observation, tagSettings.activeTagId) {
                    currentScope, observation, requestedTagId ->
                val metadataReady = observation.channelState is ChannelRepositoryState.Current
                if (metadataReady && currentScope.activeTagId != requestedTagId) {
                    currentScope
                } else {
                    null
                }
            }.collect { fallbackScope ->
                fallbackScope ?: return@collect
                if (fallbackScope.fallback == TagScopeFallback.TAG_UNAVAILABLE) {
                    tagSettings.fallbackToScope(fallbackScope.activeTagId)
                } else {
                    tagSettings.selectTag(fallbackScope.activeTagId)
                }
            }
        }
        viewModelScope.launch {
            combine(session.observation, tagSettings.scopeVisibility) { observation, visibility ->
                val tags = observation.channelCatalog().tags
                val metadataReady = observation.channelState is ChannelRepositoryState.Current
                if (
                    metadataReady &&
                    visibility.configured &&
                    !visibility.allChannelsVisible &&
                    tags.none { visibility.isTagVisible(it.id) }
                ) {
                    tags.mapTo(mutableSetOf()) { it.id }
                } else {
                    null
                }
            }.collect { availableTagIds ->
                availableTagIds ?: return@collect
                tagSettings.setScopeVisible(
                    tagId = null,
                    visible = true,
                    availableTagIds = availableTagIds,
                )
            }
        }
    }

    fun selectTag(tagId: ChannelTagId?) {
        viewModelScope.launch { tagSettings.selectTag(tagId) }
    }

    fun dismissUnavailableTagNotice() {
        tagSettings.dismissUnavailableTagNotice()
    }

    fun nowEvent(channelId: ChannelId, nowSec: Long): EpgEvent? =
        session.observation.value.eventAt(channelId, kotlin.time.Instant.fromEpochSeconds(nowSec))

    fun nextEvent(channelId: ChannelId, nowSec: Long): EpgEvent? =
        session.observation.value.nextEvent(channelId, kotlin.time.Instant.fromEpochSeconds(nowSec))

    fun epgForChannel(channelId: ChannelId) = session.observation.map { observation ->
        observation.epgEvents().filter { it.channelId == channelId }
    }
}

private fun SessionObservation.channelCatalog(): ChannelCatalog = when (val state = channelState) {
    is ChannelRepositoryState.Current -> state.catalog
    is ChannelRepositoryState.Stale -> state.catalog
    is ChannelRepositoryState.Synchronizing -> state.staleCatalog ?: ChannelCatalog.create()
    ChannelRepositoryState.Empty -> ChannelCatalog.create()
}

private fun SessionObservation.epgEvents(): List<EpgEvent> = when (val state = epgState) {
    is EpgRepositoryState.Current -> state.snapshot.events
    is EpgRepositoryState.Stale -> state.snapshot.events
    is EpgRepositoryState.Synchronizing -> state.staleSnapshot?.events.orEmpty()
    EpgRepositoryState.Empty -> emptyList()
}
