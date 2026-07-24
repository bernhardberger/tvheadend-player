package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.TagScopeFallback
import at.bernhardberger.tvhplayer.core.resolveChannelScope
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.repositories.TvhRepository
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelsViewModel(
    private val repo: TvhRepository,
    private val tagSettings: ChannelTagSettingsStore,
) : ViewModel() {
    val scope: StateFlow<ChannelBrowsingScope> = combine(
        repo.channelsUi,
        repo.tagsUi,
        tagSettings.activeTagId,
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
    val allChannels = repo.channelsUi
    val unavailableTagNotice = tagSettings.unavailableTagNotice

    init {
        viewModelScope.launch {
            combine(scope, repo.metadataReady, tagSettings.activeTagId) {
                    currentScope, metadataReady, requestedTagId ->
                metadataReady &&
                    requestedTagId != null &&
                    currentScope.fallback == TagScopeFallback.TAG_UNAVAILABLE
            }.collect { shouldFallback ->
                if (shouldFallback) tagSettings.fallbackToAllChannels()
            }
        }
    }

    fun selectTag(tagId: Int?) {
        viewModelScope.launch { tagSettings.selectTag(tagId) }
    }

    fun dismissUnavailableTagNotice() {
        tagSettings.dismissUnavailableTagNotice()
    }

    fun nowEvent(channelId: Int, nowSec: Long) = repo.nowEvent(channelId, nowSec)

    fun nextEvent(channelId: Int, nowSec: Long): EpgEventEntry? {
        return repo.nextEvent(channelId, nowSec)
    }

    fun epgForChannel(channelId: Int): StateFlow<List<EpgEventEntry>> =
        repo.epgForChannel(channelId)
}
