package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.CardContainerDefaults
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.HomeFocusKey
import at.bernhardberger.tvhplayer.core.HomeInput
import at.bernhardberger.tvhplayer.core.HomePlaying
import at.bernhardberger.tvhplayer.core.HomeSection
import at.bernhardberger.tvhplayer.core.HomeSectionKind
import at.bernhardberger.tvhplayer.core.OnNowItem
import at.bernhardberger.tvhplayer.core.RecordingWatchState
import at.bernhardberger.tvhplayer.core.homeInitialFocus
import at.bernhardberger.tvhplayer.core.homeRestoreFocus
import at.bernhardberger.tvhplayer.core.offeredResumePosition
import at.bernhardberger.tvhplayer.core.recordingIsRunning
import at.bernhardberger.tvhplayer.core.recordingLengthSeconds
import at.bernhardberger.tvhplayer.core.recordingTimeLeft
import at.bernhardberger.tvhplayer.core.recordingWatchState
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvBrowseTrailingInset
import at.bernhardberger.tvhplayer.ui.TvCardFocusedScale
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordedDuration
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import at.bernhardberger.tvhplayer.ui.components.CardGlowReach
import at.bernhardberger.tvhplayer.ui.components.CompactZapRowOffscreen
import at.bernhardberger.tvhplayer.ui.components.EmbeddedProgressCardBorderOutset
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.rememberBrowseContextMenuTrigger
import at.bernhardberger.tvhplayer.ui.components.HomeActivityCard
import at.bernhardberger.tvhplayer.ui.components.HomeAllRecordingsCard
import at.bernhardberger.tvhplayer.ui.components.HomeCardWidth
import at.bernhardberger.tvhplayer.ui.components.HomeGenreCard
import at.bernhardberger.tvhplayer.ui.components.HomeRecentChannelTile
import at.bernhardberger.tvhplayer.ui.components.HomeTextCard
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseDrawerState
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.ui.components.ProgrammeArtwork
import at.bernhardberger.tvhplayer.ui.components.RoundIconButton
import at.bernhardberger.tvhplayer.ui.components.RowHeading
import at.bernhardberger.tvhplayer.ui.components.RowChangeMotion
import at.bernhardberger.tvhplayer.ui.components.StandardCardTextGap
import at.bernhardberger.tvhplayer.ui.components.StandardProgrammeCard
import at.bernhardberger.tvhplayer.ui.components.StartKeylineBringIntoViewSpec
import at.bernhardberger.tvhplayer.ui.screens.ChannelsHeadline
import at.bernhardberger.tvhplayer.ui.screens.ChannelsImmersiveBackground
import at.bernhardberger.tvhplayer.ui.screens.ChannelsRowCard
import at.bernhardberger.tvhplayer.ui.screens.ImmersiveHeroWidth
import at.bernhardberger.tvhplayer.ui.components.browseDrawerUnderlap
import at.bernhardberger.tvhplayer.ui.components.browseUnderlap
import at.bernhardberger.tvhplayer.ui.components.cardGlowRoom
import at.bernhardberger.tvhplayer.ui.components.navigationEdgeFadeMask
import at.bernhardberger.tvhplayer.ui.components.overflowingRowViewport
import at.bernhardberger.tvhplayer.ui.components.progressColor
import at.bernhardberger.tvhplayer.ui.components.rowContentEmphasis
import at.bernhardberger.tvhplayer.ui.components.watchingIndicator
import java.time.Instant
import kotlin.time.Instant as KotlinInstant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.text.rememberTextMeasurer

/** An ordinary focused row's heading sits at this offset from the page's top edge. */
private val HomeTopSlot = 76.dp

/** The expanded On now row holds its cards at this offset from the page's top edge. */
internal val HomeImmersiveSlot = 344.dp

/** Room the focus ring and card scale need around a card. */
private val HomeFocusRoom = 12.dp

internal val HomeSectionGap = 24.dp
private val HomeRecentSectionGap = 8.dp
internal val HomeHeadingContentGap = 8.dp
private val HomeOnNowHeadingContentGap = 16.dp
private val HomeHeroGap = 16.dp
private val HomeOnNowSectionGap = 16.dp
internal val HomeRecentGap = 16.dp
private val HomeGenreGap = 16.dp

private val HomeEmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
// G10 frames 81–103, timestamp-fitted row travel (240x135 source, <1px RMS).
internal val HomeDownInEasing = CubicBezierEasing(0.46f, 0f, 0f, 1f)
internal const val HomeDownInMs = 410

/** Interrupted moves start at the currently drawn page position, following live row measurements. */
internal class HomePageMotion {
    val progress = Animatable(1f)
    var from by mutableFloatStateOf(0f)
    var destination by mutableFloatStateOf(0f)
    var to by mutableStateOf<HomeSectionKind?>(null)
    val above = Animatable(1f)
    val aboveShift = Animatable(0f)
    val block = Animatable(0f)
    val blockShift = Animatable(0f)
    val background = Animatable(0f)
    val heading = Animatable(1f)
    var revealFromBottom by mutableStateOf(true)
}

/** The four measured Home transitions, independent of card actions and Channels' own motion. */
@Composable
internal fun rememberHomePageMotion(
    focused: HomeSectionKind?,
    target: (HomeSectionKind?) -> Float,
): HomePageMotion {
    val motion = remember { HomePageMotion() }
    val density = LocalDensity.current
    var previous by remember { mutableStateOf<HomeSectionKind?>(null) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        val to = focused
        val from = previous
        previous = to
        val shift = with(density) { HomeImmersiveSlot.toPx() }
        val blockShift = with(density) { 16.dp.toPx() }
        val expanded = to == HomeSectionKind.ON_NOW
        if (!initialized && to == null) return@LaunchedEffect
        if (!initialized) {
            initialized = true
            motion.to = to
            motion.progress.snapTo(1f)
            motion.background.snapTo(if (expanded) 1f else 0f)
            motion.block.snapTo(if (expanded) 1f else 0f)
            motion.heading.snapTo(if (expanded) 0f else 1f)
            motion.above.snapTo(if (expanded) 0f else 1f)
            motion.aboveShift.snapTo(if (expanded) -shift else 0f)
            motion.blockShift.snapTo(0f)
            return@LaunchedEffect
        }
        motion.from = target(motion.to) + (motion.from - motion.destination) * (1f - motion.progress.value)
        motion.to = to
        motion.destination = target(to)
        motion.progress.snapTo(0f)
        coroutineScope {
            when {
                expanded && (from == null || from.ordinal < HomeSectionKind.ON_NOW.ordinal) -> {
                    motion.revealFromBottom = true
                    motion.blockShift.snapTo(blockShift)
                    launch { motion.progress.animateTo(1f, tween(HomeDownInMs, easing = HomeDownInEasing)) }
                    launch { motion.above.animateTo(0f, tween(90, easing = LinearEasing)) }
                    launch { motion.aboveShift.animateTo(-shift, tween(90, easing = LinearOutSlowInEasing)) }
                    launch { motion.heading.animateTo(0f, tween(130, easing = LinearEasing)) }
                    launch { motion.background.animateTo(1f, tween(370, 100, LinearEasing)) }
                    launch { motion.block.animateTo(1f, tween(270, 100, LinearEasing)) }
                    launch { motion.blockShift.animateTo(0f, tween(270, 100, HomeEmphasizedEasing)) }
                }
                expanded -> {
                    motion.revealFromBottom = false
                    motion.blockShift.snapTo(-blockShift)
                    motion.above.snapTo(0f)
                    motion.aboveShift.snapTo(-shift)
                    launch { motion.progress.animateTo(1f, tween(370, easing = HomeEmphasizedEasing)) }
                    launch { motion.heading.animateTo(0f, tween(150, 50, LinearEasing)) }
                    launch { motion.background.animateTo(1f, tween(320, 50, LinearEasing)) }
                    launch { motion.block.animateTo(1f, tween(300, 150, LinearEasing)) }
                    launch { motion.blockShift.animateTo(0f, tween(300, 150, HomeEmphasizedEasing)) }
                }
                from == HomeSectionKind.ON_NOW && to != null && to.ordinal > from.ordinal -> {
                    motion.block.snapTo(0f)
                    launch { motion.progress.animateTo(1f, tween(370, easing = LinearOutSlowInEasing)) }
                    launch { motion.background.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) }
                    launch { motion.heading.animateTo(1f, tween(200, 150, LinearEasing)) }
                }
                from == HomeSectionKind.ON_NOW -> {
                    // The compact recent row's caption expands on RowChangeMotion.
                    // Share its clock: otherwise a short page return outlives that
                    // expansion and makes On now reverse in the last frames.
                    launch { motion.progress.animateTo(1f, RowChangeMotion) }
                    launch { motion.block.animateTo(0f, tween(170, easing = LinearEasing)) }
                    launch { motion.blockShift.animateTo(blockShift, tween(170, easing = LinearEasing)) }
                    launch { motion.background.animateTo(0f, tween(270, easing = LinearEasing)) }
                    launch { motion.heading.animateTo(1f, tween(200, 100, LinearEasing)) }
                    launch { motion.above.animateTo(1f, tween(90, 100, LinearEasing)) }
                    launch { motion.aboveShift.animateTo(0f, RowChangeMotion) }
                }
                else -> {
                    launch { motion.progress.animateTo(1f, tween(370, easing = LinearOutSlowInEasing)) }
                    launch { motion.above.animateTo(1f, tween(200, easing = LinearEasing)) }
                    launch { motion.aboveShift.animateTo(0f, tween(200, easing = LinearOutSlowInEasing)) }
                    launch { motion.heading.animateTo(1f, tween(200, easing = LinearEasing)) }
                    launch { motion.background.animateTo(0f, tween(200, easing = LinearEasing)) }
                    launch { motion.block.animateTo(0f, tween(200, easing = LinearEasing)) }
                    launch { motion.blockShift.animateTo(0f, tween(200, easing = LinearOutSlowInEasing)) }
                }
            }
        }
        if (!expanded) {
            motion.above.snapTo(1f)
            motion.aboveShift.snapTo(0f)
            motion.heading.snapTo(1f)
            motion.block.snapTo(0f)
            motion.background.snapTo(0f)
            motion.blockShift.snapTo(0f)
        }
    }
    return motion
}

/**
 * Home's approved page: the scroll-away header first, then the spec-order sections drawn from the
 * policy's [sections]. Opening cards and loading data remain the caller's responsibility, so card
 * actions are plain callbacks.
 *
 * The header is the page's first item, not a fixed bar. Section headings stay quiet until their row
 * takes focus and only that row shows text below its cards. The page scrolls so the focused row's
 * heading lands in the top slot, or the expanded On now row's cards land in the immersive slot.
 * [initialFocus] decides the deterministic first stop; with no sections the View options button is
 * the page's only stop, which keeps a still-empty Home usable.
 *
 * [observation] and [input] are the caller's current snapshot. Without them the page still renders
 * [sections], but the On now backdrop/headline and the activity/shelf details that need the
 * snapshot are omitted rather than guessed.
 */
@Composable
internal fun HomeContent(
    sections: List<HomeSection>,
    contentPadding: PaddingValues,
    initialFocus: HomeFocusKey?,
    onFocused: (HomeFocusKey) -> Unit,
    modifier: Modifier = Modifier,
    focusEnabled: Boolean = true,
    observation: SessionObservation? = null,
    input: HomeInput? = null,
    onActivate: (HomeFocusKey) -> Unit = {},
    onContextMenu: (HomeFocusKey) -> Unit = {},
    viewRequester: FocusRequester = remember { FocusRequester() },
    onViewOptions: () -> Unit = {},
    tagsRequester: FocusRequester = remember { FocusRequester() },
    tag: String? = null,
    onTags: () -> Unit = {},
    restoreTags: Boolean = false,
    onHeaderFocused: () -> Unit = {},
    restoreHeader: Boolean = false,
    emptyFocus: FocusRequester? = null,
    emptyContent: @Composable () -> Unit = {},
) {
    val direction = LocalLayoutDirection.current
    val ltr = direction == LayoutDirection.Ltr
    val startPadding = contentPadding.calculateStartPadding(direction)
    val endPadding = contentPadding.calculateEndPadding(direction)
    val keyline = startPadding + TvBrowseLeadingInset
    val endMargin = maxOf(endPadding, TvBrowseTrailingInset)
    val locale = LocalConfiguration.current.locales[0]
    val density = LocalDensity.current
    // Quiet headings reserve less height; their focused drawing overflows upward.
    // Keep that full-size drawing at the ordinary 76dp slot, including at large fonts.
    val headingHeight = rememberTextMeasurer().measure(
        "Ag", style = MaterialTheme.typography.headlineMedium, maxLines = 1).size.height
    val headingGrowth = with(density) { (headingHeight - (headingHeight * (18f / 28f)).roundToInt()).toDp() }
    val ordinarySlot = HomeTopSlot + headingGrowth
    val zone = remember { ZoneId.systemDefault() }
    val clock = rememberCurrentEpochSeconds()
    val nowSec = input?.nowSec ?: clock()
    val rail = LocalBrowseNavigationFocus.current ?: FocusRequester.Default
    val drawerState = LocalBrowseDrawerState.current
    val firstSection = sections.firstOrNull()?.kind
    val initialKey = remember(sections, initialFocus) { initialFocus ?: homeInitialFocus(sections) }
    val requesters = remember {
        HomeSectionKind.entries.associateWith { FocusRequester() }
    }
    var focusedSection by remember { mutableStateOf<HomeSectionKind?>(null) }
    var hasHomeFocus by remember { mutableStateOf(false) }
    var entryFocusApplied by remember { mutableStateOf(false) }
    var focusedCard by remember { mutableStateOf<HomeFocusKey?>(null) }
    var focusedOnNow by remember { mutableStateOf<ChannelId?>(null) }
    val tops = remember { mutableStateMapOf<HomeSectionKind, Float>() }
    var onNowRowTop by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var contentEnd by remember { mutableFloatStateOf(0f) }

    val scrollTarget: (HomeSectionKind?) -> Float = { kind ->
        val viewport = viewportHeight.toFloat()
        val topSlot = with(density) { ordinarySlot.toPx() }
        val desired = when (kind) {
            null -> 0f
            HomeSectionKind.ON_NOW ->
                (tops[kind] ?: 0f) + onNowRowTop - with(density) { HomeImmersiveSlot.toPx() }
            firstSection -> 0f
            else -> ((tops[kind] ?: 0f) - topSlot).coerceAtLeast(0f)
        }
        minOf(desired, (contentEnd - viewport).coerceAtLeast(0f))
    }
    val motion = rememberHomePageMotion(focusedSection, scrollTarget)
    val scrollPx by remember(motion, scrollTarget) {
        derivedStateOf {
            // Caption collapse changes both row tops and the content-end clamp. Anchor
            // remaining travel to that live target rather than easing its layout drift
            // a second time (which drove rows past their slot and back).
            scrollTarget(motion.to) +
                (motion.from - motion.destination) * (1f - motion.progress.value)
        }
    }

    // Metadata ticks and ordinary D-pad moves must not replay entry focus. Request it on entry,
    // after the first rows arrive, or when the destination regains ownership after a dialog.
    LaunchedEffect(focusEnabled, sections.isEmpty(), emptyFocus) {
        if (!focusEnabled) return@LaunchedEffect
        // Widget focus changes precede composition's drawerActive feedback: never take focus back
        // from an opening drawer.
        withFrameNanos { }
        if (drawerState?.currentValue == DrawerValue.Open) return@LaunchedEffect
        if (entryFocusApplied && hasHomeFocus && !restoreHeader) return@LaunchedEffect
        val target = if (restoreHeader) (if (restoreTags) tagsRequester else viewRequester) else initialKey?.let { requesters[it.section] } ?: emptyFocus ?: viewRequester
        repeat(60) {
            if (drawerState?.currentValue == DrawerValue.Open) return@LaunchedEffect
            if (entryFocusApplied && hasHomeFocus && !restoreHeader) return@LaunchedEffect
            if (target.requestFocus()) { entryFocusApplied = true; return@LaunchedEffect }
            withFrameNanos { }
        }
    }

    LaunchedEffect(sections, focusEnabled) {
        if (!focusEnabled) return@LaunchedEffect
        val previous = focusedCard ?: return@LaunchedEffect
        if (!focusEnabled || drawerState?.currentValue == DrawerValue.Open) return@LaunchedEffect
        if (previous.item in setOf("all-recordings", "all-channels", "all-scheduled") && sections.any { it.kind == previous.section }) return@LaunchedEffect
        val restored = homeRestoreFocus(sections, previous)
        if (restored?.section == previous.section && restored.item == previous.item) return@LaunchedEffect
        withFrameNanos { }
        if (!focusEnabled || drawerState?.currentValue == DrawerValue.Open) return@LaunchedEffect
        (restored?.let { requesters[it.section] } ?: emptyFocus ?: viewRequester).requestFocus()
    }

    BoxWithConstraints(
        modifier.fillMaxSize().onGloballyPositioned { viewportHeight = it.size.height }
            .onFocusChanged { hasHomeFocus = it.hasFocus }
            .focusProperties {
                onEnter = {
                    // The rail's Right entry must not spatially select the bottom summary card.
                    val awayFromNavigation = if (direction == LayoutDirection.Ltr) {
                        FocusDirection.Right
                    } else {
                        FocusDirection.Left
                    }
                    if (requestedFocusDirection == awayFromNavigation) {
                        val key = focusedCard ?: initialKey
                        (key?.let { requesters[it.section] } ?: emptyFocus ?: viewRequester).requestFocus()
                    }
                }
            }.focusGroup(),
    ) {
        val halfWidth = ((maxWidth - keyline - endMargin - TvGridGutter) / 2).coerceAtLeast(0.dp)
        val cardWidth = homeRowCardWidth(maxWidth - keyline - endMargin, 4, TvGridGutter)
        val recentWidth = homeRowCardWidth(maxWidth - keyline - endMargin, 6, HomeRecentGap)
        val emptyHeight = (maxHeight - HomeTopSlot).coerceAtLeast(0.dp)
        val recordingChannelIds = remember(input?.dvrEntries) {
            input?.dvrEntries.orEmpty()
                .filter(::recordingIsRunning)
                .mapNotNull { it.channelId }
                .toSet()
        }
        val onNow = sections.filterIsInstance<HomeSection.OnNow>().firstOrNull()
        if (observation != null && onNow != null) {
            Box(
                Modifier.fillMaxSize().testTag("home-immersive-background")
                    .graphicsLayer { alpha = motion.background.value },
            ) {
                ChannelsImmersiveBackground(
                    channels = onNow.items.map { it.channel },
                    channelId = { focusedOnNow },
                    observation = observation,
                    nowSec = { nowSec },
                    logoTop = contentPadding.calculateTopPadding() + TvBrowseHeaderHeight,
                    endInset = 48.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        CompositionLocalProvider(LocalContentColor provides TvTextColors.primary) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer { translationY = -scrollPx }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val index = sections.indexOfFirst { it.kind == focusedSection }
                        when (event.key) {
                            Key.DirectionDown -> {
                                val next = if (focusedSection == null) firstSection else sections.getOrNull(index + 1)?.kind
                                next?.let { requesters[it]?.requestFocus() } ?: false
                            }
                            Key.DirectionUp -> if (index == 0) tagsRequester.requestFocus()
                                else sections.getOrNull(index - 1)?.kind?.let { requesters[it]?.requestFocus() } ?: false
                            else -> false
                        }
                    },
            ) {
                HomeHeader(
                    nowSec = nowSec,
                    locale = locale,
                    zone = zone,
                    endMargin = endMargin,
                    viewRequester = viewRequester,
                    firstSectionRequester = firstSection?.let(requesters::get) ?: emptyFocus,
                    rail = rail,
                    ltr = ltr,
                    onFocused = { focusedSection = null; focusedCard = null; onHeaderFocused() },
                    onViewOptions = onViewOptions,
                    tagsRequester = tagsRequester,
                    tag = tag,
                    onTags = onTags,
                    // A negative page offset makes room for a first/early immersive row; it must
                    // not drag the header down into that row's content block.
                    modifier = Modifier.graphicsLayer {
                        translationY = minOf(scrollPx, 0f) + motion.aboveShift.value
                        alpha = motion.above.value
                    },
                )
                Spacer(Modifier.height(((if (sections.firstOrNull()?.headingTitle() != null) ordinarySlot else HomeTopSlot) -
                    24.dp - TvBrowseHeaderHeight).coerceAtLeast(0.dp)))
                if (sections.isEmpty()) {
                    Box(Modifier.height(emptyHeight)) { emptyContent() }
                }
                sections.forEach { section ->
                    val kind = section.kind
                    val focused = focusedSection == kind
                    val emphasisState = animateFloatAsState(if (focused) 1f else 0f, RowChangeMotion, label = "home-row-emphasis")
                    val emphasis = { emphasisState.value }
                    val entryIndex = initialKey
                        ?.takeIf { it.section == kind }
                        ?.position
                        ?.coerceIn(0, (section.itemCount() - 1 +
                            if (section is HomeSection.ContinueWatching || section is HomeSection.RecentlyRecorded || section is HomeSection.RecentChannels) 1 else 0)
                            .coerceAtLeast(0))
                        ?: 0
                    val entry = requireNotNull(requesters[kind])
                    val onNowSection = section as? HomeSection.OnNow
                    val onSectionFocus = {
                        if (focusedSection != kind) {
                            focusedSection = kind
                            if (kind == HomeSectionKind.ON_NOW) {
                                focusedOnNow = onNowSection?.items?.firstOrNull()?.channel?.id
                            }
                        }
                    }
                    val onCardFocus: (Int, String) -> Unit = { index, key ->
                        onSectionFocus()
                        focusedCard = HomeFocusKey(kind, key, index)
                        onFocused(HomeFocusKey(kind, key, index))
                    }
                    val activate: (Int, String) -> Unit = { index, key ->
                        onActivate(HomeFocusKey(kind, key, index))
                    }
                    val contextMenu: (Int, String) -> Unit = { index, key ->
                        onContextMenu(HomeFocusKey(kind, key, index))
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (kind.ordinal < HomeSectionKind.ON_NOW.ordinal) {
                                    alpha = motion.above.value
                                    translationY = motion.aboveShift.value
                                }
                            }
                            .testTag("home-section-${kind.name}")
                            .onGloballyPositioned {
                                // Scroll destinations are layout coordinates, not the departing
                                // row's animated parallax. Feeding that shift back into the page
                                // target made warm Home's Up-out overshoot and reverse.
                                tops[kind] = it.positionInParent().y -
                                    if (kind.ordinal < HomeSectionKind.ON_NOW.ordinal) motion.aboveShift.value else 0f
                            }
                            .onFocusChanged { if (it.hasFocus) onSectionFocus() },
                    ) {
                        val headingTitle = section.headingTitle()
                        Box(Modifier.fillMaxWidth()) {
                            if (headingTitle != null) {
                                RowHeading(
                                    title = headingTitle,
                                    emphasis = if (kind == HomeSectionKind.ON_NOW) ({ 0f }) else emphasis,
                                    modifier = Modifier.padding(start = keyline)
                                        .testTag("home-heading-${kind.name}"),
                                    quietScale = 18f / 28f,
                                    focusOverflow = section.focusOverflow(halfWidth),
                                    bottomSpace = (if (onNowSection != null) HomeOnNowHeadingContentGap else HomeHeadingContentGap) - HomeFocusRoom +
                                        section.focusOverflow(halfWidth) - 1.dp,
                                )
                            }
                            if (onNowSection != null && observation != null) {
                                val item = onNowSection.items.firstOrNull { it.channel.id == focusedOnNow }
                                    ?: onNowSection.items.firstOrNull()
                                if (item != null) {
                                    HomeOnNowDetails(
                                        item = item,
                                        observation = observation,
                                        recordingChannelIds = recordingChannelIds,
                                        nowSec = nowSec,
                                        keyline = keyline,
                                        motion = motion,
                                        modifier = Modifier.align(Alignment.BottomStart),
                                    )
                                }
                            }
                        }
                        when (section) {
                            is HomeSection.CurrentActivity -> HomeActivityRow(
                                items = section.items,
                                width = halfWidth,
                                keyline = keyline,
                                emphasis = emphasis,
                                entry = entry,
                                entryIndex = entryIndex,
                                rail = rail,
                                ltr = ltr,
                                nowSec = nowSec,
                                input = input,
                                onCardFocus = onCardFocus,
                                onActivate = activate,
                                onContextMenu = contextMenu,
                            )

                            is HomeSection.RecentChannels -> HomeRecentRow(
                                items = section.items,
                                width = recentWidth,
                                keyline = keyline,
                                emphasis = emphasis,
                                textShown = focused,
                                entry = entry,
                                entryIndex = entryIndex,
                                rail = rail,
                                ltr = ltr,
                                onCardFocus = onCardFocus,
                                onActivate = activate,
                                onContextMenu = contextMenu,
                            )

                            is HomeSection.OnNow -> HomeOnNowRow(
                                items = section.items,
                                width = cardWidth,
                                keyline = keyline,
                                emphasis = emphasis,
                                entry = entry,
                                entryIndex = entryIndex,
                                rail = rail,
                                ltr = ltr,
                                nowSec = nowSec,
                                recordingChannelIds = recordingChannelIds,
                                onActivate = activate,
                                onContextMenu = contextMenu,
                                onCardFocus = { index, channelId ->
                                    onCardFocus(index, "onnow:${channelId.value}")
                                    focusedOnNow = channelId
                                },
                                onRowTop = { onNowRowTop = it },
                            )

                            is HomeSection.Genres -> HomeGenreRow(
                                items = section.items,
                                keyline = keyline,
                                emphasis = emphasis,
                                entry = entry,
                                entryIndex = entryIndex,
                                rail = rail,
                                ltr = ltr,
                                onCardFocus = onCardFocus,
                                onActivate = activate,
                            )

                            is HomeSection.ContinueWatching -> HomeShelfRow(
                                items = section.items,
                                width = cardWidth,
                                continueWatching = true,
                                keyline = keyline,
                                emphasis = emphasis,
                                textShown = focused,
                                entry = entry,
                                entryIndex = entryIndex,
                                rail = rail,
                                ltr = ltr,
                                nowSec = nowSec,
                                locale = locale,
                                input = input,
                                onCardFocus = onCardFocus,
                                onActivate = activate,
                                onContextMenu = contextMenu,
                            )

                            is HomeSection.RecentlyRecorded -> HomeShelfRow(
                                items = section.items,
                                width = cardWidth,
                                continueWatching = false,
                                keyline = keyline,
                                emphasis = emphasis,
                                textShown = focused,
                                entry = entry,
                                entryIndex = entryIndex,
                                rail = rail,
                                ltr = ltr,
                                nowSec = nowSec,
                                locale = locale,
                                input = input,
                                onCardFocus = onCardFocus,
                                onActivate = activate,
                                onContextMenu = contextMenu,
                            )

                            is HomeSection.Upcoming -> HomeUpcomingRow(
                                items = section.items,
                                keyline = keyline,
                                emphasis = emphasis,
                                entry = entry,
                                entryIndex = entryIndex,
                                nowSec = nowSec,
                                zone = zone,
                                locale = locale,
                                rail = rail,
                                ltr = ltr,
                                onCardFocus = onCardFocus,
                                onActivate = activate,
                            )
                        }
                    }
                    Spacer(Modifier.height(when {
                        onNowSection != null -> HomeOnNowSectionGap
                        kind == HomeSectionKind.RECENT_CHANNELS -> HomeRecentSectionGap
                        else -> HomeSectionGap
                    }))
                }
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(0.dp)
                        .onGloballyPositioned { contentEnd = it.positionInParent().y },
                )
            }
        }
        if (focusedSection == HomeSectionKind.ON_NOW) {
            androidx.tv.material3.Icon(painterResource(R.drawable.ic_expand_less), null,
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(30.dp)
                    .testTag("home-up-hint").semantics { hideFromAccessibility() },
                tint = TvTextColors.secondary.copy(alpha = 0.5f))
        }
    }
}

internal fun homeRowCardWidth(contentWidth: Dp, columns: Int, gap: Dp): Dp =
    ((contentWidth - gap * (columns - 1)) / columns).coerceAtLeast(0.dp)

/** The existing date, clock and View options header, now the page's first scrolling item. */
@Composable
private fun HomeHeader(
    nowSec: Long,
    locale: Locale,
    zone: ZoneId,
    endMargin: Dp,
    viewRequester: FocusRequester,
    firstSectionRequester: FocusRequester?,
    rail: FocusRequester,
    ltr: Boolean,
    onFocused: () -> Unit,
    onViewOptions: () -> Unit,
    tagsRequester: FocusRequester,
    tag: String?,
    onTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 24.dp, end = endMargin)
            .height(TvBrowseHeaderHeight)
            .testTag("home-header"),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.End) {
        Text(
            text = formatClock(Instant.ofEpochSecond(nowSec).atZone(zone), locale),
            style = MaterialTheme.typography.titleMedium,
            color = TvTextColors.tertiary,
            maxLines = 1,
        )
        Text(formatRecordingDate(nowSec, dateOnly = true, locale = locale),
            style = MaterialTheme.typography.bodySmall, color = TvTextColors.secondary, maxLines = 1)
        }
        RoundIconButton(icon = painterResource(R.drawable.ic_sell),
            label = stringResource(R.string.browse_tags), value = tag ?: stringResource(R.string.all_channels),
            active = tag != null, onClick = onTags, focusRequester = tagsRequester, onFocused = onFocused,
            modifier = Modifier.focusProperties {
                up = FocusRequester.Cancel
                down = firstSectionRequester ?: FocusRequester.Cancel
                if (ltr) { left = rail; right = viewRequester } else { right = rail; left = viewRequester }
            }.testTag("home-tags-button"))
        RoundIconButton(
            icon = painterResource(R.drawable.ic_tune),
            label = stringResource(R.string.channels_view_options),
            onClick = onViewOptions,
            focusRequester = viewRequester,
            onFocused = onFocused,
            modifier = Modifier
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = firstSectionRequester ?: FocusRequester.Cancel
                    if (ltr) {
                        left = tagsRequester
                        right = FocusRequester.Cancel
                    } else {
                        right = tagsRequester
                        left = FocusRequester.Cancel
                    }
                }
                .testTag("home-view-button"),
        )
    }
}

/**
 * A Home card row: the same under-the-rail overhang, edge fade, glow room and keyline scroll
 * target the Channels rows use. The row is the section's stable [entry] focus destination; it
 * restores its last focused card, falling back to [entryIndex] on initial entry.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> HomeCardRow(
    items: List<T>,
    itemKey: (T) -> Any,
    keyline: Dp,
    gap: Dp,
    emphasis: () -> Float,
    entry: FocusRequester,
    entryIndex: Int,
    rail: FocusRequester,
    ltr: Boolean,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int, item: T, modifier: Modifier) -> Unit,
) {
    val underlap = browseDrawerUnderlap()
    val start = keyline + underlap
    val density = LocalDensity.current
    val offscreenPx = with(density) { CompactZapRowOffscreen.roundToPx() }
    var screenEdge by remember { mutableFloatStateOf(0f) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = entryIndex.coerceAtLeast(0))
    var rememberedIndex by remember { mutableIntStateOf(entryIndex) }
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides StartKeylineBringIntoViewSpec(
            with(density) { start.toPx() } + offscreenPx.toFloat(),
        ),
    ) {
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(
                start = start + CompactZapRowOffscreen,
                end = keyline + CompactZapRowOffscreen,
                top = CardGlowReach,
                bottom = CardGlowReach,
            ),
            modifier = modifier
                .browseUnderlap(underlap)
                .fillMaxWidth()
                .onGloballyPositioned { screenEdge = -it.positionInRoot().x.coerceAtLeast(0f) }
                .cardGlowRoom(top = CardGlowReach - HomeFocusRoom)
                .rowContentEmphasis(emphasis)
                .navigationEdgeFadeMask(start - gap, leadingInsetPx = { screenEdge })
                // LazyRow supplies the focus group. Entry targets its remembered card directly;
                // requesting the group permits geometric search to choose a different child.
                .overflowingRowViewport(offscreenPx),
        ) {
            itemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item ->
                val itemModifier = Modifier
                    .then(if (index == rememberedIndex.coerceIn(items.indices)) Modifier.focusRequester(entry) else Modifier)
                    .onFocusChanged { if (it.isFocused) rememberedIndex = index }
                    .then(
                        if (index == 0) {
                            Modifier.focusProperties {
                                if (ltr) left = rail else right = rail
                            }
                        } else {
                            Modifier
                        },
                    )
                itemContent(index, item, itemModifier)
            }
        }
    }
}

@Composable
private fun HomeActivityRow(
    items: List<at.bernhardberger.tvhplayer.core.CurrentActivityItem>,
    width: Dp,
    keyline: Dp,
    emphasis: () -> Float,
    entry: FocusRequester,
    entryIndex: Int,
    rail: FocusRequester,
    ltr: Boolean,
    nowSec: Long,
    input: HomeInput?,
    onCardFocus: (Int, String) -> Unit,
    onActivate: (Int, String) -> Unit,
    onContextMenu: (Int, String) -> Unit,
) {
    HomeCardRow(
        items = items,
        itemKey = ::activityKey,
        keyline = keyline,
        gap = TvGridGutter,
        emphasis = emphasis,
        entry = entry,
        entryIndex = entryIndex,
        rail = rail,
        ltr = ltr,
    ) { index, item, itemModifier ->
        val playing = item.playing
        val runningRecording = item.recording ?: (playing as? HomePlaying.Recording)?.entryId
            ?.let { id -> input?.dvrEntries?.firstOrNull { it.id == id } }
        val channel = when (playing) {
            is HomePlaying.LiveChannel -> input?.channels?.firstOrNull { it.id == playing.channelId }
            else -> runningRecording?.channelId?.let { id ->
                input?.channels?.firstOrNull { it.id == id }
            }
        }
        val event = ((playing as? HomePlaying.LiveChannel)?.channelId
            ?: runningRecording?.channelId?.takeIf { playing !is HomePlaying.Recording })
            ?.let { input?.currentEvents?.get(it) }
        val label = stringResource(
            when {
                playing != null && item.recording != null -> R.string.home_activity_watching_and_recording
                playing is HomePlaying.Recording -> R.string.home_back_to_player
                playing != null -> R.string.home_activity_watching_live
                else -> R.string.home_activity_recording
            },
        )
        val title = event?.title?.takeIf(String::isNotBlank)
            ?: runningRecording?.title?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.no_epg)
        val channelLabel = channel?.let {
            channelTitleText(
                it.visibleChannelNumber,
                it.name.orEmpty(),
            )
        } ?: runningRecording?.channelName.orEmpty()
        val description = listOf(label, title, channelLabel)
            .filter(String::isNotBlank)
            .joinToString(", ")
        HomeActivityCard(
            channel = channel,
            event = event,
            entry = runningRecording,
            playing = playing != null,
            playingRecording = playing is HomePlaying.Recording,
            nowSec = nowSec,
            width = width,
            description = description,
            onClick = { onActivate(index, activityKey(item)) },
            onLongClick = if (playing !is HomePlaying.LiveChannel) ({ onContextMenu(index, activityKey(item)) }) else null,
            modifier = itemModifier
                .testTag("home-activity-$index")
                .onFocusChanged { if (it.isFocused) onCardFocus(index, activityKey(item)) },
        )
    }
}

@Composable
private fun HomeRecentRow(
    items: List<at.bernhardberger.tvhplayer.core.RecentChannelItem>,
    width: Dp,
    keyline: Dp,
    emphasis: () -> Float,
    textShown: Boolean,
    entry: FocusRequester,
    entryIndex: Int,
    rail: FocusRequester,
    ltr: Boolean,
    onCardFocus: (Int, String) -> Unit,
    onActivate: (Int, String) -> Unit,
    onContextMenu: (Int, String) -> Unit,
) {
    HomeCardRow(
        items = items + listOf(null),
        itemKey = { it?.channel?.id?.value ?: "all-channels" },
        keyline = keyline,
        gap = HomeRecentGap,
        emphasis = emphasis,
        entry = entry,
        entryIndex = entryIndex,
        rail = rail,
        ltr = ltr,
    ) { index, item, itemModifier ->
        if (item == null) {
            HomeAllRecordingsCard(width = width, textShown = textShown, allChannels = true,
                onClick = { onActivate(index, "all-channels") },
                modifier = itemModifier.testTag("home-all-channels").onFocusChanged {
                    if (it.isFocused) onCardFocus(index, "all-channels")
                })
        } else {
            HomeRecentChannelTile(
                channel = item.channel,
                width = width,
                nameShown = textShown,
                onClick = { onActivate(index, "channel:${item.channel.id.value}") },
                onLongClick = { onContextMenu(index, "channel:${item.channel.id.value}") },
                modifier = itemModifier
                    .testTag("home-recent-$index")
                    .onFocusChanged {
                        if (it.isFocused) onCardFocus(index, "channel:${item.channel.id.value}")
                    },
            )
        }
    }
}

@Composable
private fun HomeOnNowRow(
    items: List<at.bernhardberger.tvhplayer.core.OnNowItem>,
    width: Dp,
    keyline: Dp,
    emphasis: () -> Float,
    entry: FocusRequester,
    entryIndex: Int,
    rail: FocusRequester,
    ltr: Boolean,
    nowSec: Long,
    recordingChannelIds: Set<ChannelId>,
    onCardFocus: (Int, ChannelId) -> Unit,
    onRowTop: (Float) -> Unit,
    onActivate: (Int, String) -> Unit,
    onContextMenu: (Int, String) -> Unit,
) {
    val density = LocalDensity.current
    HomeCardRow(
        items = items,
        itemKey = { it.channel.id.value },
        keyline = keyline,
        gap = TvGridGutter,
        emphasis = emphasis,
        entry = entry,
        entryIndex = entryIndex,
        rail = rail,
        ltr = ltr,
        modifier = Modifier.onGloballyPositioned {
            // cardGlowRoom draws the row this far above its own layout edge; the page's slot
            // positions the pictures.
            onRowTop(it.positionInParent().y + with(density) { HomeFocusRoom.toPx() })
        },
    ) { index, item, itemModifier ->
        ChannelsRowCard(
            channel = item.channel,
            event = item.event,
            nowSec = nowSec,
            playbackIndicator = watchingIndicator(item.event, nowSec),
            recording = item.channel.id in recordingChannelIds,
            onClick = { onActivate(index, "onnow:${item.channel.id.value}") },
            onLongClick = { onContextMenu(index, "onnow:${item.channel.id.value}") },
            programmeLoading = false,
            programmePicture = true,
            modifier = itemModifier
                .semantics(mergeDescendants = true) {
                    contentDescription = listOf(
                        channelTitleText(item.channel.visibleChannelNumber, item.channel.name.orEmpty()),
                        item.event.title.orEmpty(),
                    ).filter { it.isNotBlank() }.joinToString(", ")
                }
                .testTag("home-onnow-$index")
                .onFocusChanged { if (it.isFocused) onCardFocus(index, item.channel.id) },
            channelName = false,
            width = width,
            tagPrefix = "home-onnow",
        )
    }
}

/** Down-in reveals upward; Up-in enters from above. Neither expands the row's footprint. */
@Composable
private fun HomeOnNowDetails(
    item: OnNowItem,
    observation: SessionObservation,
    recordingChannelIds: Set<ChannelId>,
    nowSec: Long,
    keyline: Dp,
    motion: HomePageMotion,
    modifier: Modifier = Modifier,
) {
    Box(modifier.layout { measurable, constraints ->
        // Reserve the safe top edge and the heading/row gap; the shared headline gives
        // surplus synopsis lines up rather than displacing its eyebrow or Next line.
        val block = measurable.measure(constraints.copy(minHeight = 0,
            maxHeight = (HomeImmersiveSlot - HomeHeroGap - 20.dp - HomeOnNowHeadingContentGap - 28.dp - 24.dp).roundToPx()))
        layout(block.width, 0) {
            block.place(0, -block.height - (HomeHeroGap + 20.dp).roundToPx())
        }
    }) {
        Crossfade(
            targetState = item,
            animationSpec = tween(200, easing = LinearEasing),
            label = "home-on-now-details",
            modifier = Modifier.testTag("home-on-now-block")
                .layout { measurable, constraints ->
                    val block = measurable.measure(
                        constraints.copy(minHeight = 0),
                    )
                    val revealed = if (motion.revealFromBottom) (block.height * motion.block.value).toInt() else block.height
                    layout(block.width, revealed) {
                        block.place(0, revealed - block.height + motion.blockShift.value.toInt())
                    }
                }
                .graphicsLayer {
                    alpha = minOf(motion.block.value, motion.background.value)
                    clip = true
                }
                .semantics {
                    if (minOf(motion.block.value, motion.background.value) <= 0.05f) hideFromAccessibility()
                },
        ) { shown ->
            HomeOnNowBlock(
                channel = shown.channel,
                current = shown.event,
                next = observation.nextEvent(shown.channel.id, KotlinInstant.fromEpochSeconds(nowSec)),
                recording = shown.channel.id in recordingChannelIds,
                nowSec = nowSec,
                keyline = keyline,
            )
        }
    }
}

/**
 * The expanded On now row's content block: the Channels page's own headline for the focused
 * channel, so Home and Channels cannot drift apart.
 */
@Composable
private fun HomeOnNowBlock(
    channel: Channel,
    current: EpgEvent?,
    next: EpgEvent?,
    recording: Boolean,
    nowSec: Long,
    keyline: Dp,
) {
    ChannelsHeadline(
        channel = channel,
        current = current,
        next = next,
        nextScheduled = false,
        recording = recording,
        now = nowSec,
        synopsisLines = 2,
        fitSynopsis = true,
        loading = false,
        modifier = Modifier
            .padding(start = keyline)
            .widthIn(max = ImmersiveHeroWidth),
        showNext = true,
        titleStyle = MaterialTheme.typography.headlineLarge.copy(lineHeight = 36.sp),
    )
}

@Composable
private fun HomeGenreRow(
    items: List<at.bernhardberger.tvhplayer.core.GenreItem>,
    keyline: Dp,
    emphasis: () -> Float,
    entry: FocusRequester,
    entryIndex: Int,
    rail: FocusRequester,
    ltr: Boolean,
    onCardFocus: (Int, String) -> Unit,
    onActivate: (Int, String) -> Unit,
) {
    HomeCardRow(
        items = items,
        itemKey = { it.category.name },
        keyline = keyline,
        gap = HomeGenreGap,
        emphasis = emphasis,
        entry = entry,
        entryIndex = entryIndex,
        rail = rail,
        ltr = ltr,
    ) { index, item, itemModifier ->
        HomeGenreCard(
            category = item.category,
            onClick = { onActivate(index, "genre:${item.category.name}") },
            modifier = itemModifier
                .testTag("home-genre-$index")
                .onFocusChanged {
                    if (it.isFocused) onCardFocus(index, "genre:${item.category.name}")
                },
        )
    }
}

@Composable
private fun HomeShelfRow(
    items: List<at.bernhardberger.tvhplayer.core.RecordingItem>,
    width: Dp,
    continueWatching: Boolean,
    keyline: Dp,
    emphasis: () -> Float,
    textShown: Boolean,
    entry: FocusRequester,
    entryIndex: Int,
    rail: FocusRequester,
    ltr: Boolean,
    nowSec: Long,
    locale: Locale,
    input: HomeInput?,
    onCardFocus: (Int, String) -> Unit,
    onActivate: (Int, String) -> Unit,
    onContextMenu: (Int, String) -> Unit,
) {
    // The All recordings end card is part of the row: a null item marks it.
    val rowItems: List<at.bernhardberger.tvhplayer.core.RecordingItem?> = items + listOf(null)
    HomeCardRow(
        items = rowItems,
        itemKey = { it?.entry?.id?.value ?: "all-recordings" },
        keyline = keyline,
        gap = TvGridGutter,
        emphasis = emphasis,
        entry = entry,
        entryIndex = entryIndex,
        rail = rail,
        ltr = ltr,
    ) { index, item, itemModifier ->
        if (item == null) {
            HomeAllRecordingsCard(
                width = width,
                onClick = { onActivate(index, "all-recordings") },
                textShown = textShown,
                modifier = itemModifier
                    .testTag("home-all-recordings")
                    .onFocusChanged {
                        if (it.isFocused) onCardFocus(index, "all-recordings")
                    },
            )
        } else {
            val recording = item.entry
            val channel = recording.channelId?.let { id ->
                input?.channels?.firstOrNull { it.id == id }
            }
            val admission = input?.admission?.invoke(recording)
            val progress = (recordingWatchState(
                recording,
                offeredResumePosition(admission),
                nowSec,
            ) as? RecordingWatchState.PartlyWatched)?.fraction?.toFloat()
            val title = recording.title?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.no_epg)
            val contextMenu = rememberBrowseContextMenuTrigger(
                onOpen = { onContextMenu(index, "recording:${recording.id.value}") },
            )
            val subtitle = if (continueWatching) {
                continueSubtitle(recording, admission, nowSec, locale)
            } else {
                recordedSubtitle(recording, nowSec, locale)
            }
            StandardProgrammeCard(
                width = width,
                onClick = { onActivate(index, "recording:${recording.id.value}") },
                onLongClick = contextMenu.onLongClick,
                modifier = itemModifier.then(contextMenu.modifier)
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOf(title, subtitle)
                            .filter(String::isNotBlank)
                            .joinToString(", ")
                    }
                    .testTag("home-shelf-$index")
                    .onFocusChanged {
                        if (it.isFocused) {
                            onCardFocus(index, "recording:${recording.id.value}")
                        }
                    },
                art = recording.image ?: recording.fanartImage,
                picon = channel?.icon,
                title = {
                    AnimatedVisibility(
                        visible = textShown,
                        enter = at.bernhardberger.tvhplayer.ui.components.RowTextEnter,
                        exit = at.bernhardberger.tvhplayer.ui.components.RowTextExit,
                    ) {
                        Text(
                            title,
                            Modifier.fillMaxWidth().padding(top = StandardCardTextGap),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                subtitle = {
                    AnimatedVisibility(
                        visible = textShown,
                        enter = at.bernhardberger.tvhplayer.ui.components.RowTextEnter,
                        exit = at.bernhardberger.tvhplayer.ui.components.RowTextExit,
                    ) {
                        Text(
                            subtitle,
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = TvTextColors.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                contentColor = CardContainerDefaults.contentColor(
                    contentColor = TvTextColors.secondary,
                    focusedContentColor = TvTextColors.primary,
                ),
                picture = {
                    ProgrammeArtwork(
                        image = recording.image ?: recording.fanartImage,
                        channelIcon = channel?.icon,
                        progress = progress,
                        modifier = Modifier.fillMaxSize(),
                        progressColor = progressColor(watching = true),
                    )
                },
            )
        }
    }
}

@Composable
private fun HomeUpcomingRow(
    items: List<at.bernhardberger.tvhplayer.core.UpcomingItem>,
    keyline: Dp,
    emphasis: () -> Float,
    entry: FocusRequester,
    entryIndex: Int,
    nowSec: Long,
    zone: ZoneId,
    locale: Locale,
    rail: FocusRequester,
    ltr: Boolean,
    onCardFocus: (Int, String) -> Unit,
    onActivate: (Int, String) -> Unit,
) {
    HomeCardRow(items = items + listOf(null), itemKey = { it?.entry?.id?.value ?: "all-scheduled" }, keyline = keyline,
        gap = HomeGenreGap, emphasis = emphasis, entry = entry, entryIndex = entryIndex,
        rail = rail, ltr = ltr,
    ) { index, item, modifier ->
        if (item == null) HomeTextCard(stringResource(R.string.home_all_scheduled),
            containerColor = TvSurfaceColors.containerHigh,
            leading = { androidx.tv.material3.Icon(painterResource(R.drawable.ic_event), null, Modifier.size(36.dp), tint = TvTextColors.secondary) },
            onClick = { onActivate(items.size, "all-scheduled") },
            modifier = modifier.testTag("home-all-scheduled").onFocusChanged {
                if (it.isFocused) onCardFocus(items.size, "all-scheduled")
            }) else {
            val next = item.entry
            val title = next.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_epg)
            val whenText = next.start?.let { relativeDayTime(it.epochSeconds, nowSec, zone, locale) }.orEmpty()
            HomeTextCard(title, onClick = { onActivate(index, "recording:${next.id.value}") },
                containerColor = TvSurfaceColors.containerHigh,
                subtitle = listOf(whenText, next.channelName.orEmpty()).filter(String::isNotBlank).joinToString(" • "),
                modifier = modifier.width(220.dp).testTag("home-upcoming-$index").onFocusChanged {
                    if (it.isFocused) onCardFocus(index, "recording:${next.id.value}")
                })
        }
    }
}

/** The activity card's key in the same form the policy saves focus with. */
private fun activityKey(item: at.bernhardberger.tvhplayer.core.CurrentActivityItem): String =
    when (val playing = item.playing) {
        is HomePlaying.LiveChannel -> "live:${playing.channelId.value}"
        is HomePlaying.Recording -> "recording:${playing.entryId.value}"
        null -> "recording:${item.recording?.id?.value}"
    }

private fun HomeSection.itemCount(): Int = when (this) {
    is HomeSection.CurrentActivity -> items.size
    is HomeSection.RecentChannels -> items.size
    is HomeSection.OnNow -> items.size
    is HomeSection.Genres -> items.size
    is HomeSection.ContinueWatching -> items.size
    is HomeSection.RecentlyRecorded -> items.size
    is HomeSection.Upcoming -> items.size + 1
}

@Composable
private fun HomeSection.headingTitle(): String? = when (this) {
    is HomeSection.CurrentActivity -> stringResource(R.string.home_now_playing)
    is HomeSection.RecentChannels -> stringResource(R.string.home_recent_channels)
    is HomeSection.OnNow -> stringResource(R.string.home_on_now)
    is HomeSection.Genres -> stringResource(R.string.home_browse_by_genre)
    is HomeSection.ContinueWatching -> stringResource(R.string.home_continue_watching)
    is HomeSection.RecentlyRecorded -> stringResource(R.string.home_recently_recorded)
    is HomeSection.Upcoming -> stringResource(R.string.home_upcoming_recordings)
}

/** The picture height the heading's focus overflow has to cover for this section's row. */
private fun HomeSection.focusOverflow(halfActivityWidth: Dp): Dp {
    val pictureHeight = when (this) {
        is HomeSection.CurrentActivity -> halfActivityWidth * 9f / 16f
        is HomeSection.RecentChannels -> 104.dp
        is HomeSection.Genres, is HomeSection.Upcoming -> 56.dp
        else -> HomeCardWidth * 9f / 16f
    }
    return pictureHeight * ((TvCardFocusedScale - 1f) / 2f) +
        EmbeddedProgressCardBorderOutset * TvCardFocusedScale
}

@Composable
private fun continueSubtitle(
    entry: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    nowSec: Long,
    locale: Locale,
): String {
    val resources = LocalContext.current.resources
    val date = entry.start?.let {
        formatRecordingDate(it.epochSeconds, dateOnly = true, locale = locale, nowSec = nowSec)
    }
    val left = recordingTimeLeft(entry, admission)?.inWholeSeconds?.let {
        stringResource(R.string.player_minutes_left, formatRecordedDuration(it, resources))
    }
    return listOfNotNull(date, left).joinToString(" • ")
}

@Composable
private fun recordedSubtitle(entry: DvrEntry, nowSec: Long, locale: Locale): String {
    val resources = LocalContext.current.resources
    val whenText = entry.start?.let {
        relativeDayTime(it.epochSeconds, nowSec, ZoneId.systemDefault(), locale)
    }
    val length = recordingLengthSeconds(entry, nowSec)?.let {
        formatRecordedDuration(it, resources)
    }
    return listOfNotNull(whenText, length).joinToString(" • ")
}

/** "Today 18:00" / "Tomorrow 09:30", otherwise the calendar date and the clock. */
@Composable
private fun relativeDayTime(startSec: Long, nowSec: Long, zone: ZoneId, locale: Locale): String {
    val start = Instant.ofEpochSecond(startSec).atZone(zone)
    val today = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
    val prefix = when (start.toLocalDate()) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> null
    }
    val clockText = formatClock(start, locale)
    return if (prefix != null) {
        "$prefix $clockText"
    } else {
        formatRecordingDate(startSec, dateOnly = true, locale = locale, nowSec = nowSec) +
            " " + clockText
    }
}
