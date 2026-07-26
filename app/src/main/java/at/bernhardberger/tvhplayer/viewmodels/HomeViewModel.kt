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
import kotlinx.coroutines.flow.MutableStateFlow
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
    /**
     * Capability gate written by [at.bernhardberger.tvhplayer.ui.screens.HomeScreen] from
     * the session [at.bernhardberger.tvhplayer.core.SimpleTvProfile] — not from settings alone.
     */
    private val allowRecordings = MutableStateFlow(true)

    fun setAllowRecordings(value: Boolean) {
        allowRecordings.value = value
    }

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

    private val recordingsAndCapability = combine(
        dvrRepository.entries,
        allowRecordings,
    ) { recordings, allow ->
        RecordingsAndCapability(recordings = recordings, allowRecordings = allow)
    }

    val dashboard: StateFlow<HomeDashboardModel> = combine(
        browsingScope,
        playbackAndHistory,
        recordingsAndCapability,
        ticker,
    ) { browsing, playback, dvr, nowSec ->
        buildDashboard(
            visibleChannels = browsing.visibleChannels,
            activeServiceId = playback.activeServiceId,
            activeRecordingId = playback.activeRecordingId,
            lastWatchedChannelId = playback.lastWatchedChannelId,
            recentChannelIds = playback.recentChannelIds,
            recordings = dvr.recordings,
            nowSec = nowSec,
            allowRecordings = dvr.allowRecordings,
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
        allowRecordings: Boolean,
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

        // Point-read only channels the hero/rows actually need — not the full visible list.
        val candidateIds = buildSet {
            activeServiceId?.let(::add)
            lastWatchedChannelId?.let(::add)
            addAll(recentChannelIds)
            visibleChannels.asSequence().take(HOME_ON_NOW_CANDIDATE_LIMIT).forEach { add(it.id) }
        }

        val nextEvents = HashMap<Int, EpgEventEntry>(candidateIds.size)
        val scopedOnNow = ArrayList<Pair<ChannelUi, EpgEventEntry>>(candidateIds.size)
        val seenOnNow = HashSet<Int>(candidateIds.size)
        for (channelId in candidateIds) {
            val channel = channelsById[channelId] ?: continue
            val event = repo.nowEvent(channelId, nowSec)
            if (event != null && event.start <= nowSec && nowSec < event.stop && channelId !in seenOnNow) {
                scopedOnNow += channel to event
                seenOnNow += channelId
            }
            repo.nextEvent(channelId, nowSec)?.let { nextEvents[channelId] = it }
        }
        // Preserve visible-channel order for the on-now row among the candidates we read.
        val orderedOnNow = ArrayList<Pair<ChannelUi, EpgEventEntry>>(scopedOnNow.size)
        val byId = scopedOnNow.associateBy { it.first.id }
        for (channel in visibleChannels) {
            byId[channel.id]?.let(orderedOnNow::add)
        }
        for (pair in scopedOnNow) {
            if (orderedOnNow.none { it.first.id == pair.first.id }) {
                orderedOnNow += pair
            }
        }

        val activeProgrammeTitle = activeServiceId?.let { id ->
            scopedOnNow.firstOrNull { it.first.id == id }?.second?.title
                ?: repo.nowEvent(id, nowSec)?.title
        }

        return buildHomeDashboard(
            channelsById = channelsById,
            activeServiceId = activeServiceId,
            activeRecordingId = activeRecordingId,
            activeProgrammeTitle = activeProgrammeTitle,
            lastWatchedChannelId = lastWatchedChannelId,
            recentChannelIds = recentChannelIds,
            onNowEvents = orderedOnNow,
            nextEvents = nextEvents,
            recordings = recordings,
            nowSec = nowSec,
            allowRecordings = allowRecordings,
        )
    }

    private data class PlaybackAndHistory(
        val activeServiceId: Int?,
        val activeRecordingId: Int?,
        val lastWatchedChannelId: Int?,
        val recentChannelIds: List<Int>,
    )

    private data class RecordingsAndCapability(
        val recordings: List<DvrEntry>,
        val allowRecordings: Boolean,
    )

    companion object {
        private const val HOME_ON_NOW_CANDIDATE_LIMIT = 24
    }
}
