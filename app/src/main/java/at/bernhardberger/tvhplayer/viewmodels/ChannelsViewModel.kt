package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.repositories.TvhRepository
import kotlinx.coroutines.flow.StateFlow

class ChannelsViewModel(
    private val repo: TvhRepository
) : ViewModel() {
    val channels = repo.channelsUi

    fun nowEvent(channelId: Int, nowSec: Long) = repo.nowEvent(channelId, nowSec)

    fun nextEvent(channelId: Int, nowSec: Long): EpgEventEntry? {
        return repo.nextEvent(channelId, nowSec)
    }

    fun epgForChannel(channelId: Int): StateFlow<List<EpgEventEntry>> =
        repo.epgForChannel(channelId)
}