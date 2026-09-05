package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChannelScopeState(
    val scope: ChannelBrowsingScope,
    val channelCatalogCurrent: Boolean,
)

class ChannelsViewModel(
    private val session: TvheadendSession,
    private val tagSettings: ChannelTagSettingsStore,
) : ViewModel() {
    val observation: StateFlow<SessionObservation> = session.observation

    val scope: StateFlow<ChannelScopeState> = combine(
        session.observation,
        tagSettings.activeTagId,
        tagSettings.scopeVisibility,
    ) { observation, activeTagId, visibility ->
        resolveChannelScopeState(observation.channelState, activeTagId, visibility)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = resolveChannelScopeState(ChannelRepositoryState.Empty, null),
    )

    val channels = scope.map { it.scope.visibleChannels }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )
    val unavailableTagNotice = tagSettings.unavailableTagNotice

    init {
        viewModelScope.launch {
            combine(scope, tagSettings.activeTagId) { currentState, requestedTagId ->
                if (
                    currentState.channelCatalogCurrent &&
                    currentState.scope.activeTagId != requestedTagId
                ) {
                    currentState.scope
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
                val tags = observation.channelCatalogForDisplay?.tags.orEmpty()
                val metadataReady = observation.channelCatalogAuthority ==
                    RetainedMetadataAuthority.CURRENT
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
    )
}
