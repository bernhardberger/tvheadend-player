package at.bernhardberger.tvhplayer.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

// ---- Product spacing ----
val TvSpacing4 = 4.dp
val TvSpacing8 = 8.dp
val TvSpacing12 = 12.dp
val TvSpacing16 = 16.dp
val TvSpacing24 = 24.dp
val TvSpacing32 = 32.dp
val TvSpacing48 = 48.dp
val TvSpacing56 = 56.dp
val TvSpacing80 = 80.dp

// ---- Product opacity ----
const val TvTextPrimaryAlpha = 1.00f
const val TvTextSecondaryAlpha = 0.88f
const val TvTextTertiaryAlpha = 0.72f
const val TvTextDisabledAlpha = 0.38f
const val TvPanelBrowseAlpha = 0.84f
const val TvPanelDenseAlpha = 0.92f
const val TvScrimNavigationAlpha = 0.50f
const val TvScrimModalAlpha = 0.76f
const val TvTrackAlpha = 0.20f
const val TvGhostFillAlpha = 0.40f

const val TvNavigationScrimAlpha = 0.50f
const val TvBrowsePanelAlpha = 0.84f
const val TvEpgPanelAlpha = 0.88f
const val TvSettingsPanelAlpha = 0.90f

/** Browse safe-area input owned and passed down by the global navigation shell. */
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

/** Four-across programme card width on the 960dp guidance canvas. */
val HomeCardWidth = 196.dp

/** 16:9 media area height for [HomeCardWidth] cards. */
val HomeCardMediaHeight = 110.25.dp

/** Three-across channel card width on the 960dp guidance canvas. */
val ChannelCardWidth = 268.dp

/** Four-across/fallback card width used by narrower playback surfaces. */
val CompactChannelCardWidth = 196.dp

/** Material for TV card-lane peek spacing. */
val TvCardSpacing = 20.dp

/** Stable master-detail width for the local Settings category pane. */
val SettingsCategoryPaneWidth = 268.dp

/** Non-interactive programme progress on browse cards, rows, and detail panes. */
val TvProgressStripHeight = 4.dp

// ---- Player overlay geometry ----
val TvOverlaySidePadding = TvSpacing56
val TvOverlayTopPadding = TvSpacing32
val TvOverlayBottomPadding = TvSpacing32

/** Gradient run-out, not content spacing. Do not unify with the paddings. */
val TvOverlayHeaderGradientRunout = 72.dp
val TvOverlayFooterGradientRunout = 80.dp

val TvOverlayHeaderMinHeight = 96.dp
/** First-baseline anchor for both header columns. Must exceed the clock's ascent. */
val TvOverlayHeaderFirstBaseline = 44.dp
val TvOverlayHeaderPiconWidth = 160.dp
val TvOverlayHeaderPiconHeight = 90.dp
val TvOverlayHeaderPiconGap = TvSpacing24
val TvOverlayHeaderColumnGap = TvSpacing48

val TvOverlayTimelineBarHeight = 6.dp
val TvOverlayTimelineBarFocusedHeight = 10.dp
val TvOverlayTimelineThumbSize = 20.dp
/** Fixed band the bar is centred in; must be at least the thumb size. */
val TvOverlayTimelineRowHeight = 24.dp
val TvOverlayTimelineLabelGap = TvSpacing12
val TvOverlayTimelineBlockGap = TvSpacing24

val TvOverlayActionButtonSize = 48.dp
val TvOverlayActionGap = TvSpacing8
val TvOverlayActionGroupGap = TvSpacing24
/** Separation before the terminal Stop action. */
val TvOverlayTerminalGap = 40.dp
/** Keeps a focus-scaled 48dp control inside the safe margin. */
val TvOverlayFocusInset = TvSpacing4

// ---- Player overlay tone ----
const val TvOverlayTextPrimaryAlpha = TvTextPrimaryAlpha
const val TvOverlayTextSecondaryAlpha = TvTextSecondaryAlpha
const val TvOverlayTextTertiaryAlpha = TvTextTertiaryAlpha
const val TvOverlayTrackAlpha = TvTrackAlpha
const val TvOverlayGhostFillAlpha = TvGhostFillAlpha
const val TvOverlayTimelineTickAlpha = TvTextTertiaryAlpha
