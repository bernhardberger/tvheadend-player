package at.bernhardberger.tvhplayer.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

const val TvNavigationScrimAlpha = 0.50f
const val TvBrowsePanelAlpha = 0.84f
const val TvEpgPanelAlpha = 0.88f
const val TvSettingsPanelAlpha = 0.90f

/** Asymmetric padding for content that sits beside the collapsed global rail. */
val TvScreenPadding = PaddingValues(
    start = 24.dp,
    top = 32.dp,
    end = 48.dp,
    bottom = 32.dp,
)

/**
 * Symmetric TV-safe padding for rail-less full-screen destinations such as
 * onboarding, unlock, and Settings (which keeps only its category rail).
 */
val TvFullScreenPadding = PaddingValues(
    start = 48.dp,
    top = 32.dp,
    end = 48.dp,
    bottom = 32.dp,
)

val TvPlaybackPadding = PaddingValues(
    start = 48.dp,
    top = 32.dp,
    end = 24.dp,
    bottom = 32.dp,
)

/** Featured Home hero carousel height (canvas 960×540 dp). */
val HomeHeroHeight = 288.dp

/** 16:9 programme card width for Home content rows. */
val HomeCardWidth = 176.dp

/** 16:9 media area height for [HomeCardWidth] cards. */
val HomeCardMediaHeight = 99.dp
