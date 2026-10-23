package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.components.ChannelRowEdgeInset
import at.bernhardberger.tvhplayer.ui.components.ChannelRowWidth
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap

// Channels geometry, expressed relative to the shell's safe-area inset.
// The shell passes 24dp leading and 32dp top; nominal rows sit 26dp further in
// (x130 on the 960x540 canvas), including the list's 12dp native focus reserve.

/** Top content reserve inside the list so the first focused row's scale is not clipped. */
internal val ChannelsListTopReserve = 4.dp

/** List column including the horizontal focus-scale reserve on both sides of the rows. */
internal val ChannelsListColumnWidth = ChannelRowWidth + ChannelRowEdgeInset * 2

/** The trailing focus reserve is part of the nominal column separation, not extra. */
internal val ChannelsListToDetailsGap = TvBrowseColumnGap - ChannelRowEdgeInset
