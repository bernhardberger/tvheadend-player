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

// ---- Player overlay geometry ----
val TvOverlaySidePadding = 56.dp
val TvOverlayTopPadding = 32.dp
val TvOverlayBottomPadding = 32.dp

/** Gradient run-out, not content spacing. Do not unify with the paddings. */
val TvOverlayHeaderGradientRunout = 72.dp
val TvOverlayFooterGradientRunout = 80.dp

val TvOverlayHeaderMinHeight = 96.dp
/** First-baseline anchor for both header columns. Must exceed the clock's ascent. */
val TvOverlayHeaderFirstBaseline = 44.dp
val TvOverlayHeaderPiconWidth = 160.dp
val TvOverlayHeaderPiconHeight = 90.dp
val TvOverlayHeaderPiconGap = 24.dp
val TvOverlayHeaderColumnGap = 48.dp

val TvOverlayTimelineBarHeight = 6.dp
val TvOverlayTimelineBarFocusedHeight = 10.dp
val TvOverlayTimelineThumbSize = 20.dp
/** Fixed band the bar is centred in; must be at least the thumb size. */
val TvOverlayTimelineRowHeight = 24.dp
val TvOverlayTimelineLabelGap = 12.dp
val TvOverlayTimelineBlockGap = 24.dp

val TvOverlayActionButtonSize = 48.dp
val TvOverlayActionGap = 8.dp
val TvOverlayActionGroupGap = 24.dp
/** Separation before the terminal Stop action. */
val TvOverlayTerminalGap = 40.dp
/** Keeps a focus-scaled 48dp control inside the safe margin. */
val TvOverlayFocusInset = 4.dp

// ---- Player overlay tone ----
const val TvOverlayTextPrimaryAlpha = 1.00f
const val TvOverlayTextSecondaryAlpha = 0.88f
const val TvOverlayTextTertiaryAlpha = 0.72f
const val TvOverlayTrackAlpha = 0.24f
const val TvOverlayGhostFillAlpha = 0.40f
const val TvOverlayTimelineTickAlpha = 0.70f
