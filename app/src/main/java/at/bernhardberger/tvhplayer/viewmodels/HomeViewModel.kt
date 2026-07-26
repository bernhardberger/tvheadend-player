package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.core.HomeDashboardModel
import at.bernhardberger.tvhplayer.core.buildHomeDashboard
import at.bernhardberger.tvhplayer.core.resolveChannelScope
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.player.PlayerSession
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.repositories.TvhRepository
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    private val repo: TvhRepository,
    private val tagSettings: ChannelTagSettingsStore,
    private val dvrRepository: DvrRepository,
    private val playerSession: PlayerSession,
    private val lastPlayedStore: LastPlayedChannelStore,
) : ViewModel() {
    private val browsingScope = combine(
        repo.channelsUi,
        repo.tagsUi,
        tagSettings.activeTagId,
        tagSettings.scopeVisibility,
        ::resolveChannelScope,
    )

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis() / 1000L)
            delay(10_000)
        }
    }

    private val playbackAndHistory = combine(
        playerSession.activeServiceId,
        playerSession.activeRecordingId,
        lastPlayedStore.channelId,
        lastPlayedStore.recentChannelIds,
    ) { activeServiceId, activeRecordingId, lastWatchedChannelId, recentChannelIds ->
        PlaybackAndHistory(
            activeServiceId = activeServiceId,
            activeRecordingId = activeRecordingId,
            lastWatchedChannelId = lastWatchedChannelId,
            recentChannelIds = recentChannelIds,
        )
    }

    val dashboard: StateFlow<HomeDashboardModel> = combine(
        browsingScope,
        playbackAndHistory,
        dvrRepository.entries,
        ticker,
    ) { browsing, playback, recordings, nowSec ->
        buildDashboard(
            visibleChannels = browsing.visibleChannels,
            activeServiceId = playback.activeServiceId,
            activeRecordingId = playback.activeRecordingId,
            lastWatchedChannelId = playback.lastWatchedChannelId,
            recentChannelIds = playback.recentChannelIds,
            recordings = recordings,
            nowSec = nowSec,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeDashboardModel(hero = emptyList(), rows = emptyList()),
    )

    private fun buildDashboard(
        visibleChannels: List<ChannelUi>,
        activeServiceId: Int?,
        activeRecordingId: Int?,
        lastWatchedChannelId: Int?,
        recentChannelIds: List<Int>,
        recordings: List<DvrEntry>,
        nowSec: Long,
    ): HomeDashboardModel {
        val channelsById = visibleChannels.associateBy { it.id }.toMutableMap()
        // Keep resume targets even if they sit outside the active tag scope.
        listOfNotNull(activeServiceId, lastWatchedChannelId).forEach { id ->
            if (id !in channelsById) {
                repo.channelsUi.value.firstOrNull { it.id == id }?.let { channelsById[id] = it }
            }
        }
        recentChannelIds.forEach { id ->
            if (id !in channelsById) {
                repo.channelsUi.value.firstOrNull { it.id == id }?.let { channelsById[id] = it }
            }
        }

        val candidateIds = buildSet {
            activeServiceId?.let(::add)
            lastWatchedChannelId?.let(::add)
            addAll(recentChannelIds)
            visibleChannels.asSequence().take(HOME_ON_NOW_CANDIDATE_LIMIT).forEach { add(it.id) }
        }

        val nextEvents = HashMap<Int, EpgEventEntry>(candidateIds.size)
        for (channelId in candidateIds) {
            repo.nextEvent(channelId, nowSec)?.let { nextEvents[channelId] = it }
        }

        val scopedOnNow = ArrayList<Pair<ChannelUi, EpgEventEntry>>(visibleChannels.size)
        for (channel in visibleChannels) {
            val event = repo.nowEvent(channel.id, nowSec) ?: continue
            if (event.start <= nowSec && nowSec < event.stop) {
                scopedOnNow += channel to event
            }
        }
        // Ensure active / last-watched / recent also contribute on-now pairs when in scope map.
        for (channelId in candidateIds) {
            if (scopedOnNow.any { it.first.id == channelId }) continue
            val channel = channelsById[channelId] ?: continue
            val event = repo.nowEvent(channelId, nowSec) ?: continue
            if (event.start <= nowSec && nowSec < event.stop) {
                scopedOnNow += channel to event
            }
        }

        val activeProgrammeTitle = activeServiceId?.let { id ->
            repo.nowEvent(id, nowSec)?.title
        }

        return buildHomeDashboard(
            channelsById = channelsById,
            activeServiceId = activeServiceId,
            activeRecordingId = activeRecordingId,
            activeProgrammeTitle = activeProgrammeTitle,
            lastWatchedChannelId = lastWatchedChannelId,
            recentChannelIds = recentChannelIds,
            onNowEvents = scopedOnNow,
            nextEvents = nextEvents,
            recordings = recordings,
            nowSec = nowSec,
            // Screen applies Simple TV capability via allowRecordings on the model.
            allowRecordings = true,
        )
    }

    private data class PlaybackAndHistory(
        val activeServiceId: Int?,
        val activeRecordingId: Int?,
        val lastWatchedChannelId: Int?,
        val recentChannelIds: List<Int>,
    )

    companion object {
        private const val HOME_ON_NOW_CANDIDATE_LIMIT = 24
    }
}
