package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import at.bernhardberger.tvheadend.sdk.core.ChannelId

/** Presentation only; neither focus nor tab selection is watch history. */
class PlayerChannelCollectionState {
    var focusedId by mutableStateOf<ChannelId?>(null)
    val listState = LazyListState()
}

internal enum class PlayerChannelRow { ON_NOW, RECENT }

/** Survives the rail closing: it reopens on the last row and that row's card. */
internal class PlayerChannelBrowserState {
    var row by mutableStateOf(PlayerChannelRow.ON_NOW)
    val onNow = PlayerChannelCollectionState()
    val recent = PlayerChannelCollectionState()
    fun collection(row: PlayerChannelRow) = if (row == PlayerChannelRow.ON_NOW) onNow else recent

    /** The key whose remaining events belong to the move that took focus to another row. */
    internal var relocatingKey by mutableStateOf<Key?>(null)
    /** Left or Right is held: the system repeats the key and the cards scroll until it is released. */
    internal var scrolling by mutableStateOf(false)
}
