package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.TagScopeFallback
import at.bernhardberger.tvhplayer.core.resolveChannelScope
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.htsp.ChannelEpgRuntime
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelsViewModel(
    private val runtime: ChannelEpgRuntime,
    private val tagSettings: ChannelTagSettingsStore,
) : ViewModel() {
    val scope: StateFlow<ChannelBrowsingScope> = combine(
        runtime.channelsUi,
        runtime.tagsUi,
        tagSettings.activeTagId,
        tagSettings.scopeVisibility,
        ::resolveChannelScope,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = resolveChannelScope(emptyList(), emptyList(), null),
    )

    val channels = scope.map { it.visibleChannels }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )
    val allChannels = runtime.channelsUi
    val unavailableTagNotice = tagSettings.unavailableTagNotice

    init {
        viewModelScope.launch {
            combine(scope, runtime.metadataReady, tagSettings.activeTagId) {
                    currentScope, metadataReady, requestedTagId ->
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
            combine(runtime.tagsUi, runtime.metadataReady, tagSettings.scopeVisibility) {
                    tags, metadataReady, visibility ->
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

    fun selectTag(tagId: Int?) {
        viewModelScope.launch { tagSettings.selectTag(tagId) }
    }

    fun dismissUnavailableTagNotice() {
        tagSettings.dismissUnavailableTagNotice()
    }

    fun nowEvent(channelId: Int, nowSec: Long) = runtime.nowEvent(channelId, nowSec)

    fun nextEvent(channelId: Int, nowSec: Long): EpgEventEntry? {
        return runtime.nextEvent(channelId, nowSec)
    }

    fun epgForChannel(channelId: Int): StateFlow<List<EpgEventEntry>> =
        runtime.epgForChannel(channelId)
}
