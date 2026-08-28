package at.bernhardberger.tvhplayer.stores

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChannelSelectionStore {
    private val _selectedId = MutableStateFlow<ChannelId?>(null)
    val selectedId: StateFlow<ChannelId?> = _selectedId.asStateFlow()

    fun setSelected(id: ChannelId) {
        if (_selectedId.value == id) return
        _selectedId.value = id
    }
}
