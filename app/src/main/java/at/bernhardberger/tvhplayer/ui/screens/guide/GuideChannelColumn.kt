package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvhplayer.ui.components.AppChannelNumbers

/**
 * The channel column, the same in both row densities. With the numbers hidden and a logo for every
 * channel it is the logo and its padding, and the timeline takes the rest. A channel shown by name
 * keeps the full width: the narrow column would cut the name to a few letters.
 */
internal fun guideChannelWidth(channels: List<Channel>, numbersShown: Boolean = AppChannelNumbers.shown): Dp =
    if (numbersShown || channels.any { it.icon == null || it.name.isNullOrBlank() }) 104.dp else 72.dp

/** [guideChannelWidth] for the channels on screen, set once by the Guide screen. */
internal val LocalGuideChannelWidth = compositionLocalOf { 104.dp }
internal val GuideChannelGap = 8.dp
