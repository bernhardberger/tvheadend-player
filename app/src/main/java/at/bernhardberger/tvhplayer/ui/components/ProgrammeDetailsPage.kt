package at.bernhardberger.tvhplayer.ui.components

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.LocalWarmPlayback
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.common.*
import at.bernhardberger.tvhplayer.ui.player.*
import at.bernhardberger.tvhplayer.ui.notifications.label
import at.bernhardberger.tvhplayer.images.BackdropTransformation
import at.bernhardberger.tvhplayer.images.CinematicScrimTransformation
import at.bernhardberger.tvhplayer.images.GuideScrimTransformation
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import at.bernhardberger.tvhplayer.ui.common.formatFirstAired
import at.bernhardberger.tvhplayer.ui.common.formatFileSize
import at.bernhardberger.tvhplayer.ui.common.formatMetadataNumber
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class DetailsSection { ACTIONS, ABOUT, AIRINGS, FACTS }

/** Browse details only; the shell remains the sole owner of the video scrim. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProgrammeDetailsPage(
    event: EpgEvent?,
    recording: DvrEntry?,
    channel: Channel?,
    nowSec: Long,
    actions: List<BrowseContextAction>,
    onAction: (BrowseContextAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    related: List<EpgEvent> = emptyList(),
    onRelated: (EpgEvent) -> Unit = {},
    onRelatedContextMenu: ((EpgEvent) -> Unit)? = null,
    channelOf: (ChannelId) -> Channel? = { null },
    returnToCard: EventId? = null,
    onReturnToCardConsumed: () -> Unit = {},
    initialAction: BrowseContextAction? = null,
    backEnabled: Boolean = true,
    onActionFocused: (BrowseContextAction) -> Unit = {},
    visibilityAlpha: State<Float>? = null,
    recordingEvent: EpgEvent? = null,
    recordingConfigurations: List<DvrConfiguration> = emptyList(),
    /** The SDK's admission of a [recording] shown without [event]; its time left follows the Resume it offers. */
    recordingAdmission: RecordingPlaybackAdmission? = null,
) {
    val layering = LocalBrowseContentLayering.current
    val pageKey = remember { Any() }
    val alpha = visibilityAlpha ?: remember { mutableFloatStateOf(1f) }
    DisposableEffect(layering, alpha) {
        layering?.details?.set(pageKey, alpha)
        onDispose { layering?.details?.remove(pageKey) }
    }
    val title = event?.title ?: recording?.title.orEmpty()
    val summary = event?.summary ?: recording?.summary
    val description = event?.description ?: recording?.description
    val synopsis = summary?.takeIf(String::isNotBlank) ?: description?.takeIf(String::isNotBlank)
    val recordingFailure = recording?.subscriptionError?.label()?.takeIf(String::isNotBlank)
    val context = LocalContext.current
    val resources = context.resources
    val locale = LocalConfiguration.current.locales[0]
    val aboutText = remember(summary, description) { programmeAboutText(summary, description, false) }
    val aboutFacts = remember(event, recording, locale) { programmeAboutFacts(event, recording, resources, locale) }
    val credits = programmeCreditFacts(event ?: recordingEvent, resources)
    val importantFacts = aboutFacts + (if (event == null && recording != null)
        recordingMetadataFacts(recording, recordingConfigurations, context, locale, nowSec) else emptyList()) +
        credits
    val hasAbout = aboutText.isNotEmpty()
    val requesters = remember { BrowseContextAction.entries.associateWith { FocusRequester() } }
    val panelFocus = remember { FocusRequester() }
    val aboutFocus = remember { FocusRequester() }
    val factsFocus = remember { FocusRequester() }
    val ids = remember(related) { related.map { it.id } }
    val cardFocus = remember(ids) { ids.associateWith { FocusRequester() } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ids.indexOf(returnToCard).coerceAtLeast(0))
    val pageScroll = rememberScrollState()
    val factsScroll = rememberScrollState()
    var section by remember { mutableStateOf(if (returnToCard in ids) DetailsSection.AIRINGS else DetailsSection.ACTIONS) }
    var lastAction by remember { mutableStateOf(initialAction ?: actions.firstOrNull()) }
    var lastCard by remember { mutableStateOf(returnToCard ?: ids.firstOrNull()) }
    var openingRelease by remember { mutableStateOf(true) }
    var reading by remember { mutableStateOf(false) }
    var viewport by remember { mutableIntStateOf(0) }
    var heroHeight by remember { mutableIntStateOf(0) }
    var factsHeadingHeight by remember { mutableIntStateOf(0) }
    var factsTop by remember { mutableIntStateOf(Int.MAX_VALUE) }
    // The height of the text under the airing cards as last laid out; only the scroll reads it.
    val airingsText = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val headingKeyline = with(density) { DetailsSectionKeyline.roundToPx() }
    val scope = rememberCoroutineScope()
    // The action last focused, or its replacement after an action changed the row.
    fun actionFocus() = (lastAction?.takeIf { it in actions }?.let(requesters::get)
            ?: actions.firstOrNull { lastAction in listOf(BrowseContextAction.MARK_WATCHED, BrowseContextAction.MARK_UNWATCHED) &&
                it in listOf(BrowseContextAction.MARK_WATCHED, BrowseContextAction.MARK_UNWATCHED) }?.let(requesters::get)
            ?: actions.firstOrNull { lastAction in listOf(BrowseContextAction.RECORD, BrowseContextAction.CANCEL, BrowseContextAction.STOP) &&
                it in listOf(BrowseContextAction.RECORD, BrowseContextAction.CANCEL, BrowseContextAction.STOP) }?.let(requesters::get)
            ?: initialAction?.takeIf { it in actions }?.let(requesters::get)
            ?: actions.firstOrNull()?.let(requesters::get) ?: panelFocus)
    fun restoreAction() {
        section = DetailsSection.ACTIONS
        actionFocus().requestFocus()
    }
    suspend fun showCard(target: EventId) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.key == target.value }) listState.scrollToItem(ids.indexOf(target))
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == target.value } }.first { it }
        withFrameNanos { }
        cardFocus.getValue(target).requestFocus()
    }
    // Back first returns to the actions, with the page at its top; then it closes.
    fun close() {
        if (reading) reading = false
        else if (section == DetailsSection.ACTIONS) onClose() else restoreAction()
    }
    BackHandler(enabled = backEnabled, onBack = ::close)
    LaunchedEffect(reading) {
        if (!reading && section in listOf(DetailsSection.ABOUT, DetailsSection.FACTS)) {
            withFrameNanos { }
            (if (section == DetailsSection.FACTS) factsFocus else aboutFocus).requestFocus()
        }
    }
    LaunchedEffect(actions, backEnabled) {
        if (!backEnabled) return@LaunchedEffect
        withFrameNanos { }
        val card = returnToCard?.takeIf { it in ids }
        if (card != null) {
            lastCard = card
            // A page returned to at its airings row opens there instead of scrolling to it.
            pageScroll.scrollTo(heroHeight - headingKeyline)
            showCard(card)
            onReturnToCardConsumed()
        } else if (section == DetailsSection.ACTIONS) restoreAction()
    }
    // The section holding focus is at full emphasis, the others dimmed; the hero holds the About
    // panel and the actions.
    val focusedSection = if (section == DetailsSection.ABOUT) DetailsSection.ACTIONS else section
    val heroEmphasis = animateFloatAsState(if (focusedSection == DetailsSection.ACTIONS) 1f else 0f, RowChangeMotion, label = "details-hero")
    val airingsEmphasis = animateFloatAsState(if (focusedSection == DetailsSection.AIRINGS) 1f else 0f, RowChangeMotion, label = "details-airings")
    val factsEmphasis = animateFloatAsState(if (focusedSection == DetailsSection.FACTS) 1f else 0f, RowChangeMotion, label = "details-facts")
    // Focus search moves between the sections. The focused item's section comes to the heading
    // keyline, the hero to the page's top, in one calm motion as the airings row's text shrinks or
    // grows (the hero stands for its About panel too). Once input focus has left the airings row, the
    // facts aim past the text the row is still going to lose, so their heading reaches the keyline
    // without passing it; while the row keeps focus, its text stays.
    val pageSpec = rememberSectionScrollSpec(pageScroll,
        if (returnToCard in ids) DetailsSection.AIRINGS else DetailsSection.ACTIONS, RowChangeMotion,
        sectionAt = { top ->
            when {
                top < heroHeight -> DetailsSection.ACTIONS
                top < factsTop -> DetailsSection.AIRINGS
                else -> DetailsSection.FACTS
            }
        }) { target, focused ->
        when (target) {
            DetailsSection.ACTIONS, DetailsSection.ABOUT -> 0
            DetailsSection.AIRINGS -> heroHeight - headingKeyline
            DetailsSection.FACTS -> factsTop - (if (focused == DetailsSection.AIRINGS) 0 else airingsText.intValue) - headingKeyline
        }
    }
    val readingTransition = updateTransition(reading, label = "details-reading")
    val readingProgress = readingTransition.animateFloat(
        transitionSpec = { tween(PlayerMotion.MediumMs * 2, easing = PlayerMotion.Standard) }, label = "reading-backdrop") {
        if (it) 1f else 0f
    }
    val warmPlayback = LocalWarmPlayback.current
    val art = (event?.image ?: recording?.image).takeUnless { warmPlayback }
    val picon = channel?.icon.takeUnless { warmPlayback }
    // The picture's colour, or the logo's should the picture fail. Without a picture the logo's
    // colour glows over the page instead, opaque in itself.
    val background = rememberDetailsTint(art, picon.takeIf { art != null })
    val glowChannel = channel?.id?.takeIf { art == null && picon != null }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(Modifier.browseDetailsViewport().then(modifier).fillMaxSize()
             .graphicsLayer { this.alpha = alpha.value }
             // Warm playback remains transparent to the shell's video and scrim.
             .drawBehind { if (!warmPlayback && glowChannel == null) drawRect(background.value) }
            .semantics { paneTitle = title }.testTag("programme-details-page")
            .focusProperties { onExit = { if (backEnabled) cancelFocusChange() } }
            .onSizeChanged { viewport = it.height }
            .onPreviewKeyEvent { key ->
                val activation = key.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
                when {
                    !backEnabled -> true
                    activation && openingRelease -> {
                        if (key.type == KeyEventType.KeyDown && key.nativeKeyEvent.repeatCount == 0) {
                            openingRelease = false; false
                        } else {
                            if (key.type == KeyEventType.KeyUp) openingRelease = false
                            true
                        }
                    }
                     reading -> false
                     section == DetailsSection.FACTS && activation -> {
                         if (key.type == KeyEventType.KeyUp) reading = true
                         true
                     }
                     // The panel has nothing to focus inside: Down reads on through its facts.
                     section == DetailsSection.FACTS && key.key == Key.DirectionDown -> {
                         if (key.type == KeyEventType.KeyDown) scope.launch {
                             factsScroll.animateScrollTo(factsScroll.value + with(density) { 60.dp.roundToPx() }, RowChangeMotion)
                         }
                         true
                     }
                     section == DetailsSection.FACTS && (key.key == Key.DirectionLeft || key.key == Key.DirectionRight) -> true
                    key.key == Key.DirectionLeft && (section == DetailsSection.ABOUT ||
                        section == DetailsSection.ACTIONS && lastAction == actions.firstOrNull() ||
                        section == DetailsSection.AIRINGS && lastCard == ids.firstOrNull()) -> true
                    else -> false
                }
            }.then(if (actions.isEmpty()) Modifier.focusRequester(panelFocus).onFocusChanged {
                if (it.isFocused) section = DetailsSection.ACTIONS
            }.focusProperties {
                canFocus = backEnabled
                up = FocusRequester.Cancel; down = FocusRequester.Cancel
                left = FocusRequester.Cancel; right = FocusRequester.Cancel
            }.focusable() else Modifier.focusGroup())) {
            // Blurred and dimmed in full once the page has scrolled as far as the airings' heading.
            val backdrop: () -> Float = {
                val full = minOf(heroHeight - headingKeyline, pageScroll.maxValue).coerceAtLeast(1)
                maxOf((pageScroll.value.toFloat() / full).coerceIn(0f, 1f), readingProgress.value)
            }
            // With warm playback the live video is the background, so the page draws no art.
            // Scrolled away (or reading), the picture blurs in place, like Google TV; no zoom.
            // Without one the logo scrolls away with the page, like the photo on Google TV's actor
            // page, and the glow gives way to an even tint of its colour; the reader replaces the
            // page, so the logo fades with it.
            if (art != null) DetailsBackdrop(art, background, backdrop, reading = { readingProgress.value })
            else if (glowChannel != null) {
                ChannelLogoGlow(picon, glowChannel, Modifier.testTag("details-logo-glow"), scrolled = backdrop)
                DetailsHeroLogo(picon,
                    Modifier.align(Alignment.TopEnd).padding(top = DetailsSafeEdge, end = DetailsKeyline)
                        .graphicsLayer {
                            translationY = -pageScroll.value.toFloat()
                            this.alpha = 1f - readingProgress.value
                        }.testTag("details-hero-logo"))
            }
            readingTransition.AnimatedContent(transitionSpec = {
                (fadeIn(tween(PlayerMotion.MediumMs * 2, easing = PlayerMotion.Standard)) togetherWith
                    fadeOut(tween(PlayerMotion.MediumMs * 2, easing = PlayerMotion.Standard))).using(null)
            }) { showingReader ->
            PlayerMotionFrame(leaving = showingReader != reading) {
            if (showingReader) ProgrammeDetailsFullDescription(
                modifier = Modifier.padding(horizontal = DetailsKeyline, vertical = DetailsSafeEdge),
                // The programme names the reader, for screen readers too.
                title = title,
                heading = {
                    Column(Modifier.testTag("details-reading-heading"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                        (event?.subtitle ?: recording?.subtitle)?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.titleLarge, color = TvTextColors.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                reading = {},
            ) {
                 if (section == DetailsSection.FACTS) DetailsAboutFacts(importantFacts)
                 else if (aboutText.isNotEmpty()) Text(aboutText.joinToString("\n\n"),
                    Modifier.testTag("details-reading-text"), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 30.sp))
            }
            else {
            val rowSpec = LocalBringIntoViewSpec.current
            CompositionLocalProvider(LocalBringIntoViewSpec provides pageSpec) {
              BoxWithConstraints {
                val screen = constraints.maxHeight
                Column(Modifier.fillMaxSize().testTag("details-page-scroll").verticalScroll(pageScroll)
                    .then(if (!backEnabled) Modifier.clearAndSetSemantics { } else Modifier)) {
                    DetailsFirstScreen(screen, ids.isNotEmpty() || importantFacts.isNotEmpty(),
                        if (ids.isNotEmpty()) stringResource(R.string.details_other_airings, title)
                        else stringResource(R.string.details_important_information),
                        Modifier.onSizeChanged { heroHeight = it.height }.rowContentEmphasis { heroEmphasis.value }
                            .scrollSection(pageSpec, DetailsSection.ACTIONS)) {
                        DetailsInformation(event, recording, recordingAdmission, channel, nowSec, besideLogo = glowChannel != null) {
                            recordingFailure?.let { text ->
                                Spacer(Modifier.height(20.dp))
                                Text(text, Modifier.testTag("details-synopsis"), style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                         if (hasAbout) {
                             // Group gap; also clears the 1.05 focus growth (~3dp) and its 2dp outline.
                             Spacer(Modifier.height(24.dp))
                            DetailsAbout(synopsis, { reading = true },
                                Modifier.focusRequester(aboutFocus).onFocusChanged {
                                    if (it.isFocused) section = DetailsSection.ABOUT
                                }.focusProperties { canFocus = backEnabled })
                        }
                         // With the action row's 8dp inset, 28dp before the buttons.
                         Spacer(Modifier.height(20.dp))
                        // Entering the row returns to the action last focused there.
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                            Box(Modifier.focusProperties { onEnter = { actionFocus().requestFocus() } }.focusGroup()) {
                                DetailsActions(actions, event, recording, nowSec, requesters, { if (backEnabled) onAction(it) }) {
                                    lastAction = it; section = DetailsSection.ACTIONS; onActionFocused(it)
                                }
                            }
                        }
                    }
                    if (ids.isNotEmpty()) DetailsAirings(related, title, event?.let { detailsSubtitle(it, resources) } ?: recording?.subtitle,
                        nowSec, channelOf, listState, cardFocus, lastCard, section == DetailsSection.AIRINGS, { airingsEmphasis.value },
                         Modifier.onFocusChanged { if (it.hasFocus) section = DetailsSection.AIRINGS }
                             .scrollSection(pageSpec, DetailsSection.AIRINGS),
                        onFocused = { lastCard = it }, onTextHeight = { airingsText.intValue = it },
                        onClick = { if (backEnabled) onRelated(it) },
                        onOpenContextMenu = onRelatedContextMenu?.let { open -> { if (backEnabled) open(it) } },
                        bottomSpace = if (importantFacts.isNotEmpty()) DetailsSectionGap else DetailsSafeEdge)
                     if (importantFacts.isNotEmpty()) Column(Modifier.fillMaxWidth()
                         .onPlaced { factsTop = it.positionInParent().y.roundToInt() }
                         .padding(start = DetailsKeyline, end = DetailsKeyline, bottom = DetailsSafeEdge)
                         .scrollSection(pageSpec, DetailsSection.FACTS)
                         .focusRequester(factsFocus).onFocusChanged {
                             if (it.isFocused && section != DetailsSection.FACTS) {
                                 // Entered again, the panel reads from its first facts.
                                 if (!reading) scope.launch { factsScroll.scrollTo(0) }
                                 section = DetailsSection.FACTS
                             }
                         }
                         .focusProperties {
                             canFocus = backEnabled
                             down = FocusRequester.Cancel
                             left = FocusRequester.Cancel; right = FocusRequester.Cancel
                          }.semantics(mergeDescendants = true) {
                              onClick { if (backEnabled) reading = true; true }
                          }.focusable().testTag("details-facts-panel")) {
                          DetailsSectionHeading(stringResource(R.string.details_important_information),
                              // Plain labels have their own ink inset, unlike image cards.
                              { factsEmphasis.value }, focusOverflow = (-2).dp,
                              bottomSpace = DetailsHeadingContentGap - 9.dp,
                              modifier = Modifier.onSizeChanged { factsHeadingHeight = it.height })
                           // The facts scroll by their own spec, not the page's.
                           CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                           Box(Modifier.heightIn(max = with(density) {
                               (viewport - headingKeyline - factsHeadingHeight).coerceAtLeast(1).toDp() - DetailsSafeEdge
                           }.coerceAtLeast(1.dp)).verticalScroll(factsScroll)
                               .rowContentEmphasis { factsEmphasis.value }) {
                              DetailsAboutFacts(importantFacts, credits.map { it.first }.toSet())
                          }
                           }
                     } else if (ids.isEmpty()) Spacer(Modifier.height(DetailsSafeEdge))
                     // Room after the last section, so that it reaches the same keyline as the others.
                     if (ids.isNotEmpty() || importantFacts.isNotEmpty())
                         Spacer(Modifier.height(with(density) { (viewport - headingKeyline).coerceAtLeast(0).toDp() }))
                }
              }
            }
            }
            }
            }
        }
    }
}

private val DetailsKeyline = 58.dp
private val DetailsSafeEdge = 48.dp
// One gap before every section heading: buttons to the airings, airings to the details.
private val DetailsSectionGap = 32.dp
// A fixed lower-section heading keyline leaves the preceding action row fully visible.
private val DetailsSectionKeyline = 160.dp
// The next section starts this far above the screen bottom: its heading and the top of its cards
// peek below the first screen's block (JetStream details, Figma 3:432).
private val DetailsPeek = 96.dp
// A block up to this much taller still leaves the cards peeking; beyond it, only a hint names them.
private val DetailsPeekSlack = 16.dp
// The hint's distance from the screen bottom (JetFit "Navigation" hint, Figma 1155:37100).
private val DetailsHintEdge = 24.dp
internal val AiringCardWidth = 220.dp
// Room around the cards for the native focus scale and outline.
private val AiringCardRoom = 12.dp

@Composable
private fun DetailsAbout(synopsis: String?, onRead: () -> Unit, modifier: Modifier) {
    Box(Modifier.widthIn(max = 584.dp).testTag("details-about")) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        DetailsReadingPanel(onRead, modifier.fillMaxWidth().testTag("details-about-description"), interaction) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f)) { DetailsSynopsis(synopsis.orEmpty()) }
                // Like a focused button, the arrow's circle turns white and the arrow dark.
                Icon(painterResource(R.drawable.ic_arrow_forward), null,
                    Modifier.size(32.dp).background(
                        if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                        CircleShape).padding(6.dp).testTag("details-about-arrow"),
                    tint = if (focused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun DetailsSynopsis(text: String) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    val more = stringResource(R.string.details_more)
    BoxWithConstraints {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }
        val displayed = remember(text, more, width, style, measurer) {
            val constraints = Constraints(maxWidth = width)
            fun measure(value: AnnotatedString) = measurer.measure(value, style,
                maxLines = 2, overflow = TextOverflow.Clip, constraints = constraints)
            val full = AnnotatedString(text)
            if (!measure(full).hasVisualOverflow) full else {
                fun candidate(end: Int) = buildAnnotatedString {
                    append(text.take(end).trimEnd())
                    append("… ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(more) }
                }
                var low = 0
                var high = text.length
                while (low < high) {
                    val middle = (low + high + 1) / 2
                    if (measure(candidate(middle)).hasVisualOverflow) high = middle - 1 else low = middle
                }
                candidate(low)
            }
        }
        Text(displayed, Modifier.testTag("details-synopsis"), style = style, maxLines = 2)
    }
}

@Composable
private fun DetailsReadingPanel(onRead: () -> Unit, modifier: Modifier, interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit) {
            val onSurface = MaterialTheme.colorScheme.onSurface
            Surface(onClick = onRead, modifier = modifier, interactionSource = interactionSource,
                shape = ClickableSurfaceDefaults.shape(DetailsReadingShape),
                 scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                // The description in its quieter grey until focus makes it the thing to read.
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = detailsReadingFill(false), contentColor = TvTextColors.secondary,
                    focusedContainerColor = detailsReadingFill(true), focusedContentColor = onSurface),
                border = ClickableSurfaceDefaults.border(
                    border = detailsReadingBorder(false), focusedBorder = detailsReadingBorder(true))) {
                Column(Modifier.padding(DetailsReadingInset), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
}

/** One visible heading/content gap for both lower sections. */
private val DetailsHeadingContentGap = 16.dp

@Composable
private fun DetailsSectionHeading(title: String, emphasis: () -> Float, modifier: Modifier = Modifier,
    focusOverflow: androidx.compose.ui.unit.Dp = AiringCardWidth / (16f / 9f) * .05f + 3.dp,
    bottomSpace: androidx.compose.ui.unit.Dp = DetailsHeadingContentGap - 4.dp) {
    Text(title, modifier.layout { measurable, constraints ->
        val heading = measurable.measure(constraints)
        layout(heading.width, heading.height + bottomSpace.roundToPx()) { heading.place(0, 0) }
    }.rowHeaderEmphasis(quietScale = 18f / 28f,
        focusOverflow = focusOverflow, emphasis = emphasis).semantics { heading() },
        style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * The first screen's block sits at one bottom anchor and grows upward, so the next section's heading
 * and the top of its cards peek below it ([lower]). A block too tall for that peek pushes the section
 * off the first screen and names it in a hint ([next]) instead. One layout pass, so the section's
 * height (and the page's scroll anchors) never change after the first frame.
 */
@Composable
private fun DetailsFirstScreen(screen: Int, lower: Boolean, next: String, modifier: Modifier, block: @Composable ColumnScope.() -> Unit) {
    // The hint is composed only when it is shown, in the same pass that measures the block.
    SubcomposeLayout(modifier.fillMaxWidth().padding(horizontal = DetailsKeyline)) { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val content = subcompose(DetailsFirstScreenPart.BLOCK) { Column(content = block) }.single().measure(loose)
        val top = DetailsSafeEdge.roundToPx()
        val gap = DetailsSectionGap.roundToPx()
        val bounded = screen != Constraints.Infinity
        // Where the block ends on the first screen: the next section's gap and peek below it.
        val anchor = if (bounded) screen - DetailsPeek.roundToPx() - gap else 0
        val hint = if (lower && bounded && top + content.height > anchor + DetailsPeekSlack.roundToPx())
            subcompose(DetailsFirstScreenPart.HINT) { DetailsNextHint(next) }.single().measure(loose) else null
        val edge = DetailsHintEdge.roundToPx()
        val bottom = maxOf(top + content.height, if (hint == null) anchor else screen - edge - hint.height)
        val height = bottom + (hint?.height?.plus(edge) ?: if (lower) gap else 0)
        layout(constraints.maxWidth, height) {
            content.place(0, bottom - content.height)
            hint?.place(0, bottom)
        }
    }
}

private enum class DetailsFirstScreenPart { BLOCK, HINT }

/** Names the section below a first screen that has no room for its peek; the heading itself is spoken. */
@Composable
private fun DetailsNextHint(section: String) {
    Row(Modifier.padding(top = 20.dp).alpha(.6f).clearAndSetSemantics { testTag = "details-next-hint" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(section, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailsAboutFacts(facts: List<Pair<String, String>>, abbreviatedLabels: Set<String> = emptySet()) {
    if (facts.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth().testTag("details-facts"),
        horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        facts.forEach { (label, value) ->
            Column(Modifier.width(192.dp)) {
                Text(label, Modifier.alpha(0.75f),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal))
                  Text(value, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                      maxLines = if (label in abbreviatedLabels) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsAirings(
    airings: List<EpgEvent>, pageTitle: String, pageEpisode: String?, nowSec: Long,
    channelOf: (ChannelId) -> Channel?, listState: LazyListState, requesters: Map<EventId, FocusRequester>,
    lastCard: EventId?, rowFocused: Boolean, emphasis: () -> Float, modifier: Modifier, onFocused: (EventId) -> Unit,
    onTextHeight: (Int) -> Unit, onClick: (EpgEvent) -> Unit,
    bottomSpace: androidx.compose.ui.unit.Dp,
    onOpenContextMenu: ((EpgEvent) -> Unit)?,
) {
    val keyline = with(LocalDensity.current) { DetailsKeyline.toPx() }
    // The row's height beyond its cards' pictures is the text it shows, measured as laid out, so
    // the page knows what the row still loses while the text shrinks, also when it opened focused.
    val cardRoom = with(LocalDensity.current) { AiringCardRoom.roundToPx() } * 2
    val rowHeight = remember { mutableIntStateOf(0) }
    val pictureHeight = remember { mutableIntStateOf(0) }
    val textHeight = { (rowHeight.intValue - cardRoom - pictureHeight.intValue).coerceAtLeast(0) }
    DisposableEffect(Unit) { onDispose { onTextHeight(0) } }
    // The focused card moves to the text keyline; other cards scroll off the screen normally.
    val bringIntoView = remember(keyline) { object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = offset - keyline
    } }
    // Like Google TV, only the focused row shows the text under its cards, and grows by it.
    val textShown = updateTransition(rowFocused, label = "details-airings-text")
    Column(modifier.fillMaxWidth().padding(bottom = bottomSpace - AiringCardRoom)) {
        DetailsSectionHeading(stringResource(R.string.details_other_airings, pageTitle), emphasis, Modifier.padding(start = DetailsKeyline))
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
            // Entering the row returns to the card last focused there, at first its first card.
            LazyRow(Modifier.fillMaxWidth().focusProperties {
                onEnter = {
                    if (listState.layoutInfo.visibleItemsInfo.any { it.key == lastCard?.value })
                        lastCard?.let(requesters::get)?.requestFocus()
                }
            }.focusGroup().rowContentEmphasis(emphasis).testTag("details-airings")
                .onSizeChanged { rowHeight.intValue = it.height; onTextHeight(textHeight()) },
                state = listState,
                contentPadding = PaddingValues(horizontal = DetailsKeyline, vertical = AiringCardRoom),
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                itemsIndexed(airings, key = { _, it -> it.id.value }) { index, item ->
                    AiringCard(item, airingEpisode(item, pageTitle, pageEpisode, LocalContext.current.resources), item.channelId?.let(channelOf), nowSec,
                        textShown,
                        Modifier.focusRequester(requesters.getValue(item.id))
                            .onSizeChanged { pictureHeight.intValue = it.height; onTextHeight(textHeight()) }
                            .onFocusChanged { if (it.isFocused) onFocused(item.id) }
                            .focusProperties {
                                if (index == 0) left = FocusRequester.Cancel
                                if (index == airings.lastIndex) right = FocusRequester.Cancel
                            }.testTag("details-card-${item.id.value}"),
                        onOpenContextMenu = onOpenContextMenu?.let { open -> { open(item) } }) { onClick(item) }
                }
            }
        }
    }
}

/** What tells this airing apart from the page: a different title, else a different episode line. */
internal fun airingEpisode(item: EpgEvent, pageTitle: String, pageEpisode: String?, resources: Resources): String? =
    item.title?.trim()?.takeIf { it.isNotEmpty() && !it.equals(pageTitle.trim(), ignoreCase = true) }
        ?: detailsSubtitle(item, resources)?.takeIf { it != pageEpisode }

@Composable
private fun AiringCard(
    item: EpgEvent, episode: String?, channel: Channel?, nowSec: Long, textShown: Transition<Boolean>,
    modifier: Modifier, onOpenContextMenu: (() -> Unit)?, onClick: () -> Unit,
) {
    val contextMenu = rememberBrowseContextMenuTrigger(onOpenContextMenu)
    // The page already names the programme: the card leads with when, then what differs, then where.
    // A running airing says when it ends; its card shows how far along it is.
    val running = item.isRunning(nowSec)
    val time = programmeAiringTime(item, nowSec)
    val channelName = channel?.let { channelTitleText(it.number, it.name.orEmpty()) }?.takeIf(String::isNotBlank)
    val resources = LocalContext.current.resources
    val spokenEpisode = if (episode == episodeLabel(item.episode, resources))
        episodeLabel(item.episode, resources, spoken = true) else episode
    StandardProgrammeCard(
        width = AiringCardWidth,
        onClick = onClick,
        onLongClick = contextMenu.onLongClick,
        modifier = modifier.then(contextMenu.modifier)
            .semantics { contentDescription = listOfNotNull(time, spokenEpisode, channelName).joinToString(", ") },
        picture = {
            ProgrammeArtwork(item.image, channel?.icon, item.progress(nowSec).takeIf { running },
                Modifier.fillMaxSize(),
                plateModifier = Modifier.testTag("details-card-plate-${item.id.value}"),
                progressModifier = Modifier.testTag("details-card-progress-${item.id.value}"))
        },
        title = {
            textShown.AnimatedVisibility({ it }, enter = AiringTextEnter, exit = AiringTextExit) {
                Text(time, Modifier.fillMaxWidth().padding(top = StandardCardTextGap).clearAndSetSemantics { testTag = "details-card-time" },
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        subtitle = {
            textShown.AnimatedVisibility({ it }, enter = AiringTextEnter, exit = AiringTextExit) {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { testTag = "details-card-subtitle-${item.id.value}" },
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    episode?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    channelName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
    )
}

private val AiringTextEnter = expandVertically(tween(RowChangeMs, easing = PlayerMotion.Standard), Alignment.Top) +
    fadeIn(RowChangeMotion)
private val AiringTextExit = shrinkVertically(tween(RowChangeMs, easing = PlayerMotion.Standard), Alignment.Top) +
    fadeOut(RowChangeMotion)

// The blurred backdrop's decode: 16:9 at a sixth of the screen, blurred at 160px wide. Three
// passes of radius 3 there match the former 32dp live blur on a 1080p screen.
private val DetailsBackdropDecodeSize = 320 to 180
private const val DetailsBackdropWidth = 160
private const val DetailsBackdropBlurRadius = 3
private val DetailsHeroLogoSize = 240.dp to 135.dp
private val DetailsTitleLogoGap = 32.dp
// Material for TV kit "Immersive row" background image: 758 of 960dp, top-right (Figma 8689:24742).
private const val DetailsHeroWidthFraction = 758f / 960f

/**
 * The programme picture over the whole page, as Google TV's details page has it: sharp at the
 * top under [detailsScrim] in the page's [background] colour, and as the page scrolls or reads
 * ([blur]) the same picture blurred under an even dim of that colour instead. The blur
 * is baked into a small decode, which stretches smoothly, instead of blurred by the GPU on every
 * frame. Until the picture loads the page's colour beneath shows through. The reader ([reading]),
 * all text, dims it further, so bright art stays behind its lines.
 */
@Composable
private fun DetailsBackdrop(art: ArtworkId, background: State<Color>, blur: () -> Float, reading: () -> Float) {
    val session = LocalArtworkContext.current.currentSession ?: return
    val context = LocalPlatformContext.current
    val source = remember(session, art) { AppArtworkSource(session, art) }
    val blurred = remember(source, context) {
        ImageRequest.Builder(context).data(source)
            .size(DetailsBackdropDecodeSize.first, DetailsBackdropDecodeSize.second)
            .transformations(BackdropTransformation(DetailsBackdropWidth, DetailsBackdropBlurRadius))
            .build()
    }
    var blurredLoaded by remember(blurred) { mutableStateOf(false) }
    // Scrolled, Google TV trades the scrim for an even dim: the rows then run across the whole width.
    Box(Modifier.fillMaxSize()
        .drawWithContent {
            drawContent()
            drawRect(background.value, alpha = blur() * DetailsScrolledDimAlpha +
                reading() * (DetailsReadingDimAlpha - DetailsScrolledDimAlpha))
        }
        .unditheredGradient(over = true, alpha = { 1f - blur() }) { size -> detailsScrim(background.value, size) }) {
        // The blur fades in over the sharp picture, which goes once the loaded blur covers it.
        AsyncImage(source, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { this.alpha = if (blur() >= 1f && blurredLoaded) 0f else 1f }
                .testTag("details-hero"))
        AsyncImage(blurred, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = blur() }.testTag("details-hero-blurred"),
            onSuccess = { blurredLoaded = true })
    }
}

/**
 * The details backdrop's scrim, after Google TV's details page: the page's [color] reaching out
 * from the top-right corner, nearly clear there and dark across the text on the left and the
 * rows at the bottom.
 */
private fun detailsScrim(color: Color, size: androidx.compose.ui.geometry.Size): Shader {
    val colors = IntArray(DetailsScrimAlphas.size) { color.copy(alpha = DetailsScrimAlphas[it]).toArgb() }
    return android.graphics.RadialGradient(0f, 0f, 1f, colors, DetailsScrimStops,
        android.graphics.Shader.TileMode.CLAMP).apply {
        setLocalMatrix(android.graphics.Matrix().apply {
            setScale(size.width * DetailsScrimReachX, size.height * DetailsScrimReachY)
            postTranslate(size.width, 0f)
        })
    }
}

/** The scrim's reach from the top-right corner, as shares of the width and the height. */
private const val DetailsScrimReachX = 1f
private const val DetailsScrimReachY = 1.2f
private val DetailsScrimStops = floatArrayOf(0f, .3f, .55f, .8f, 1f)
private val DetailsScrimAlphas = floatArrayOf(.05f, .2f, .6f, .85f, .92f)
/** Google TV's scrolled details page measured about 0.6 of the sharp art's brightness. */
private const val DetailsScrolledDimAlpha = .4f
/** The reader's dim, at which the brightest blurred art still keeps its text at about 4.5:1. */
private const val DetailsReadingDimAlpha = .6f

/**
 * The kit's immersive background: top-right 16:9 art with the kit's cinematic scrim baked in, to
 * draw over the page's colour, as wide as the kit's or as tall as [height]. The Guide's picture,
 * the one given a [height], takes the Guide's softer scrim instead. [onSettled] reports once the
 * picture has loaded or failed, or at once when there is none.
 */
@Composable
internal fun DetailsHero(
    image: ArtworkId,
    modifier: Modifier,
    onSettled: (() -> Unit)? = null,
    height: Dp? = null,
) {
    val session = LocalArtworkContext.current.currentSession
    if (session == null) {
        if (onSettled != null) LaunchedEffect(Unit) { onSettled() }
        return
    }
    val context = LocalPlatformContext.current
    val request = remember(session, image, context, height != null) {
        ImageRequest.Builder(context).data(AppArtworkSource(session, image))
            .transformations(if (height != null) GuideScrimTransformation else CinematicScrimTransformation).build()
    }
    AsyncImage(request, null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .then(
                if (height == null) Modifier.fillMaxWidth(DetailsHeroWidthFraction).aspectRatio(16f / 9f)
                else Modifier.height(height).aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
            )
            .testTag("details-hero"),
        onSuccess = { onSettled?.invoke() },
        onError = { onSettled?.invoke() })
}

/**
 * The channel's logo for a programme without art: in the immersive Channels list in the corner
 * below the header, and on the details page in the corner over the glow, scrolling away with the
 * page. At this size a placeholder would be the loudest thing on the page, so only the logo itself
 * shows. It sits on its own colour's glow rather than the dark screen, so it is drawn as it is, the
 * same request [LoadDetailsHeroLogo] made ahead.
 */
@Composable
internal fun DetailsHeroLogo(picon: ArtworkId?, modifier: Modifier) {
    PiconBox(picon, modifier.size(DetailsHeroLogoSize.first, DetailsHeroLogoSize.second), placeholders = false,
        ground = null)
}

/**
 * Loads [DetailsHeroLogo]'s logo ahead, as its [PiconBox] asks for it and at its size, so that it
 * is in memory and shows at once when it comes. [onSettled] reports once it has loaded or failed,
 * or at once when there is none.
 */
@Composable
internal fun LoadDetailsHeroLogo(picon: ArtworkId?, onSettled: () -> Unit) {
    val artwork = LocalArtworkContext.current
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val currentOnSettled by rememberUpdatedState(onSettled)
    val model by remember(artwork, picon) { derivedStateOf { picon?.let(artwork::logo) } }
    LaunchedEffect(model, density) {
        model?.let {
            val (width, height) = with(density) { DetailsHeroLogoSize.first.roundToPx() to DetailsHeroLogoSize.second.roundToPx() }
            SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(it).size(width, height).build())
        }
        currentOnSettled()
    }
}

/**
 * Text for the About section: summary, then a different description. The first of them is the top
 * block's synopsis; when [synopsisShownInFull], About leaves it out rather than repeat it.
 */
internal fun programmeAboutText(summary: String?, description: String?, synopsisShownInFull: Boolean): List<String> =
    listOfNotNull(summary, description).map { it.replace("\\r\\n", "\n").replace("\\n", "\n").trim() }
        .filter(String::isNotEmpty).distinct().let { if (synopsisShownInFull) it.drop(1) else it }

/** Recording-only facts; the header keeps programme timing rather than padded file timing. */
@OptIn(ExperimentalTvheadendApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun recordingMetadataFacts(recording: DvrEntry, configurations: List<DvrConfiguration>,
    context: android.content.Context, locale: Locale, nowSec: Long): List<Pair<String, String>> = buildList {
    val resources = context.resources
    fun fact(label: Int, value: String?) {
        value?.takeIf(String::isNotBlank)?.let { add(resources.getString(label) to it) }
    }
    recordingSizeBytes(recording)?.let {
        fact(R.string.details_size, formatFileSize(it, context))
    }
    if (configurations.size > 1) fact(R.string.details_recording_profile,
        configurations.firstOrNull { it.id == recording.configId }?.name?.trim())
    fact(R.string.details_picture, recordingPicture(recording))
    val streams = recording.files.orEmpty().flatMap { it.streams.orEmpty() }
    fun languages(types: List<SubscriptionStreamType>) = streams.filter { it.type in types }.mapNotNull {
        humanLanguageName(it.language?.let(androidx.media3.common.util.Util::normalizeLanguageCode), resources.getString(R.string.track_unknown_language),
            resources.getString(R.string.track_multiple_languages), locale)
    }.distinct().joinToString(", ")
    fact(R.string.details_audio, languages(listOf(SubscriptionStreamType.AAC, SubscriptionStreamType.AC3,
        SubscriptionStreamType.EAC3, SubscriptionStreamType.MPEG2_AUDIO)))
    fact(R.string.subtitles, languages(listOf(SubscriptionStreamType.DVB_SUBTITLE,
        SubscriptionStreamType.TEXT_SUBTITLE, SubscriptionStreamType.TELETEXT)))
    recordingErrorCount(recording)?.let { errors ->
        fact(R.string.details_errors, if (errors == 0L) resources.getString(R.string.details_no_errors)
            else resources.getQuantityString(R.plurals.details_error_count, errors.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), errors))
    }
    // A missed or invalid entry recorded nothing; its slot, padding included, is what explains why.
    val neverRecorded = recording.state == DvrEntryState.MISSED || recording.state == DvrEntryState.INVALID
    (recordingRecordedSpan(recording, nowSec)?.takeUnless { neverRecorded } ?: recordingPaddedStart(recording)?.let { paddedStart ->
        recordingPaddedStop(recording)?.let { paddedStart to it }
    })?.let { (start, stop) ->
        fact(if (neverRecorded) R.string.recording_state_scheduled else R.string.details_recorded,
            formatTimeInterval(start.epochSeconds, stop.epochSeconds, withDate = true, locale = locale, nowSec = nowSec))
    }
    if (recording.removalDays == RECORDING_REMOVAL_WHEN_SPACE_NEEDED)
        fact(R.string.details_retention, resources.getString(R.string.details_kept_until_space_needed))
}

@OptIn(ExperimentalTvheadendApi::class)
internal fun programmeCreditFacts(event: EpgEvent?, resources: Resources): List<Pair<String, String>> = buildList {
    val credits = event?.credits.orEmpty()
    listOf(EpgCreditRole.ACTOR to R.plurals.details_cast, EpgCreditRole.GUEST to R.plurals.details_guests,
        EpgCreditRole.PRESENTER to R.plurals.details_presenters, EpgCreditRole.DIRECTOR to R.plurals.details_directors,
        EpgCreditRole.WRITER to R.plurals.details_writers).forEach { (role, label) ->
        // One entry per name and role; a person can hold several roles, and the wire may repeat a pair.
        val names = credits.filter { it.role == role }.map { it.name.trim() }.filter(String::isNotEmpty).distinct()
        if (names.isNotEmpty()) add(resources.getQuantityString(label, names.size) to names.joinToString(", "))
    }
}

/** Only the EPG facts present, as label to value, in reading order. */
internal fun programmeAboutFacts(event: EpgEvent?, recording: DvrEntry?, resources: Resources, locale: Locale): List<Pair<String, String>> =
    buildList {
        fun fact(label: Int, value: String?) {
            value?.trim()?.takeIf(String::isNotEmpty)?.let { add(resources.getString(label) to it) }
        }
        fun count(label: Int, number: Long?, total: Long?) = fact(label, number?.let { n ->
            total?.takeIf { it >= n }?.let { resources.getString(R.string.details_count_of, n.toInt(), it.toInt()) } ?: formatMetadataNumber(n, locale)
        })
        fact(R.string.details_genre, event?.genre?.takeIf(String::isNotBlank)
            ?: programmeCategory(event?.contentGenre ?: recording?.contentGenre)?.let { resources.getString(programmeCategoryLabelRes(it)) })
        (event?.episode ?: recording?.episode)?.let {
            count(R.string.details_season, it.seasonNumber, it.seasonCount)
            if (it.episodeNumber != null) count(R.string.details_episode, it.episodeNumber, it.episodeCount)
            else fact(R.string.details_episode, it.onscreen)
            count(R.string.details_part, it.partNumber, it.partCount)
        }
        fact(R.string.details_year, (event?.copyrightYear ?: recording?.copyrightYear)?.let { formatMetadataNumber(it, locale) })
        (event?.rating ?: recording?.rating)?.let { rating ->
            fact(R.string.details_age_rating, programmeAgeRating(rating.label, rating.age))
            fact(R.string.details_stars, rating.stars?.let { formatMetadataNumber(it, locale) })
        }
        event?.firstAired?.let {
            fact(R.string.details_first_aired, formatFirstAired(it.toEpochMilliseconds(), locale))
        }
        event?.isNew?.let {
            fact(R.string.details_broadcast, resources.getString(if (it) R.string.details_broadcast_new else R.string.details_broadcast_repeat))
        }
        fact(R.string.details_categories, event?.categories?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()?.joinToString(" • "))
        fact(R.string.details_keywords, event?.keywords?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()?.joinToString(" • "))
    }

/**
 * Metadata order is shared by live and recorded details; absent fields reserve no separator.
 * XMLTV guides mostly send category text without a genre code, so the first two categories
 * stand in for a missing genre. A genre code's family name leads, so the headline names the
 * same genre the Guide's colors and filter use; the server's own genre text follows when it differs.
 */
internal fun programmeHeaderMetadata(facts: List<Pair<String, String>>, resources: Resources,
    episode: at.bernhardberger.tvheadend.sdk.core.EpgEpisode?, firstAired: kotlin.time.Instant? = null,
    locale: Locale = resources.configuration.locales[0], contentGenre: ContentGenre? = null): List<String> {
    val genre = facts.firstOrNull { it.first == resources.getString(R.string.details_genre) }?.second
    val categories = facts.firstOrNull { it.first == resources.getString(R.string.details_categories) }
        ?.second?.split(" • ")?.take(2).orEmpty()
    val year = facts.firstOrNull { it.first == resources.getString(R.string.details_year) }?.second
    val date = programmeDateLabel(episode, firstAired, year, locale)
    val label = episodeLabel(episode, resources)
    val family = programmeCategory(contentGenre)?.let { resources.getString(programmeCategoryLabelRes(it)) }
    val lead = when {
        family != null -> listOfNotNull(family, genre?.takeUnless { it.equals(family, ignoreCase = true) })
        genre != null -> listOf(genre)
        else -> categories
    }
    return lead +
        if (date == year) listOfNotNull(year, label) else listOfNotNull(label, date)
}

@Composable
private fun DetailsInformation(
    event: EpgEvent?, recording: DvrEntry?, recordingAdmission: RecordingPlaybackAdmission?,
    channel: Channel?, nowSec: Long,
    besideLogo: Boolean,
    synopsis: @Composable () -> Unit,
) {
    // Only the title spans the page; the remaining information keeps its narrow text column.
    // Beside the corner logo the title stops short of it and takes a second line instead.
    Column(Modifier.fillMaxWidth().testTag("details-information")) {
        ProgrammeEyebrow(channel?.icon, channelTitleText(channel?.number, channel?.name.orEmpty()),
            Modifier.widthIn(max = 470.dp)) {
            val removal = recording?.takeIf { event == null }?.let { recordingRemovalTime(it, nowSec) }
            if (removal != null) {
                DetailsStatusBadge(null, stringResource(R.string.details_available_until,
                    formatRecordingDate(removal, dateOnly = true, locale = LocalConfiguration.current.locales[0], nowSec = nowSec)))
            } else detailsStatus(event, recording, nowSec)?.let { DetailsStatusBadge(it) }
        }
        Spacer(Modifier.height(8.dp))
        Text(event?.title ?: recording?.title.orEmpty(),
            Modifier.fillMaxWidth()
                .then(if (besideLogo) Modifier.padding(end = DetailsHeroLogoSize.first + DetailsTitleLogoGap) else Modifier)
                .testTag("details-title").semantics { heading() },
            style = MaterialTheme.typography.displaySmall, maxLines = if (besideLogo) 2 else 1,
            overflow = TextOverflow.Ellipsis)
        val subtitle = (event?.subtitle ?: recording?.subtitle)?.takeIf(String::isNotBlank)
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, Modifier.widthIn(max = 470.dp).testTag("details-subtitle"), style = MaterialTheme.typography.titleLarge, color = TvTextColors.primary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.widthIn(max = 470.dp)) { synopsis() }
        val facts = event?.let { programmeDetailsFacts(it, nowSec) } ?: recording?.let { recordingDetailsFacts(it, recordingAdmission, nowSec) }.orEmpty()
        val resources = LocalContext.current.resources
        val metadata = programmeHeaderMetadata(programmeAboutFacts(event, recording, resources,
            LocalConfiguration.current.locales[0]), resources, event?.episode ?: recording?.episode,
            event?.firstAired, LocalConfiguration.current.locales[0], event?.contentGenre ?: recording?.contentGenre)
        val timelineVisible = detailsFactsUseTimeline(event, nowSec)
        // The timeline already labels start/end; its remaining time is not the total runtime.
        val segments = facts.filter { it.kind != DetailsFactKind.LEFT &&
            (!timelineVisible || it.kind == DetailsFactKind.LENGTH) }.map { detailsFactValue(it, nowSec) } + metadata
        val rating = event?.rating ?: recording?.rating
        val ratingText = programmeAgeRating(rating?.label, rating?.age)
        if (segments.isNotEmpty() || ratingText != null) {
            // Groups: identity, then facts with timeline, then the description panel, then the actions.
            // The facts stay with the title whether or not a subtitle sits between them; a wider
            // gap under the title alone reads as a missing line.
            Spacer(Modifier.height(8.dp))
            Row(Modifier.widthIn(max = 470.dp).testTag("details-metadata-row"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Screen readers hear the episode as "Season 5, Episode 10", as on the airing cards.
            val episode = event?.episode ?: recording?.episode
            val compactEpisode = episodeLabel(episode, resources)
            val spokenMetadata = segments.map { if (it == compactEpisode) episodeLabel(episode, resources, spoken = true)!! else it }
                .joinToString(", ")
            if (segments.isNotEmpty()) Text(programmeFactsSegments(segments), Modifier.weight(1f, fill = false)
                .semantics { contentDescription = spokenMetadata }.testTag("details-metadata"),
                style = MaterialTheme.typography.labelMedium, color = TvTextColors.tertiary,
                 maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ratingText != null) ProgrammeAgeRatingBox(ratingText, "details-rating")
            }
        }
        DetailsProgress(event, recording, recordingAdmission, nowSec)
        // The same warning as the recording's row and pane.
        if (recording != null && remember(recording) { recordingStoppedEarly(recording) != null }) {
            Spacer(Modifier.height(8.dp))
            ProgrammeStatusLine(stringResource(R.string.recordings_stopped_early), R.drawable.ic_warning_outlined,
                "details-stopped-early", modifier = Modifier.widthIn(max = 470.dp))
        }
    }
}

internal fun detailsFactsUseTimeline(event: EpgEvent?, nowSec: Long): Boolean = event?.isRunning(nowSec) == true

@Composable
private fun DetailsProgress(event: EpgEvent?, recording: DvrEntry?, recordingAdmission: RecordingPlaybackAdmission?, nowSec: Long) {
    val resources = LocalContext.current.resources
    val indicator = watchingIndicator(event, nowSec)
    val progress = programmeProgress(event, nowSec)
    if (event != null && (event.isRunning(nowSec) || progress != null)) {
        Spacer(Modifier.height(8.dp))
        val at = if (indicator.isPlayingItem) liveViewerEpochSec(event.channelId, nowSec) else nowSec
        val start = formatClock(event.start.epochSeconds)
        val end = formatClock(event.stop.epochSeconds)
        val spokenRange = formatTimeIntervalSpoken(event.start.epochSeconds, event.stop.epochSeconds, resources)
        val minutesLeft = at?.takeIf { progress != null }?.let { ((event.stop.epochSeconds - it + 59) / 60).toInt() }
        DetailsTimeline(start, progress, if (minutesLeft != null) "$end • ${playerMinutesLeft(minutesLeft)}" else end,
            if (minutesLeft != null) "$spokenRange, ${playerMinutesLeft(minutesLeft, spoken = true)}" else spokenRange,
            Modifier.widthIn(max = 470.dp).fillMaxWidth(), color = progressColor(indicator.isPlayingItem))
    } else if (recording != null && (event == null || !event.isRunning(nowSec))) {
        val facts = recordingDetailsFacts(recording, recordingAdmission, nowSec)
        val viewer = recordingViewerPosition(recording.id)?.takeIf { it.fraction != null }
        if (viewer == null && facts.none { it.kind == DetailsFactKind.LEFT }) return
        Spacer(Modifier.height(8.dp))
        val length = viewer?.durationMs?.div(1000L) ?: facts.first { it.kind == DetailsFactKind.LENGTH }.seconds
        val position = viewer?.positionMs?.div(1000L)?.coerceIn(0, length)
            ?: (length - facts.first { it.kind == DetailsFactKind.LEFT }.seconds)
        val minutesLeft = ((length - position + 59) / 60).toInt()
        DetailsTimeline(formatPlaybackDuration(position * 1000L), viewer?.fraction ?: (position.toFloat() / length),
            "${formatPlaybackDuration(length * 1000L)} • ${playerMinutesLeft(minutesLeft)}",
            stringResource(R.string.details_recording_progress_spoken,
                formatDurationMinutes(position / 60, resources, spoken = true),
                formatDurationMinutes(length / 60, resources, spoken = true),
                playerMinutesLeft(minutesLeft, spoken = true)), Modifier.widthIn(max = 470.dp).fillMaxWidth(),
            color = progressColor(true))
    }
}

@Composable
internal fun ProgrammeEyebrow(
    picon: ArtworkId?, text: String,
    modifier: Modifier = Modifier, piconModifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        picon?.let { InlineLogo(it, piconModifier) }
        Text(text, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelMedium,
            color = TvTextColors.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/**
 * A trimmed logo at the start of a line. Once it has loaded the line keeps only the width it is
 * drawn in, so the text follows a square logo as closely as a wide one; until then, and for the
 * placeholder, the whole box stands.
 */
@Composable
private fun InlineLogo(picon: ArtworkId, modifier: Modifier) {
    val density = LocalDensity.current
    var drawnWidth by remember(picon) { mutableStateOf<Dp?>(null) }
    Box(Modifier.size(drawnWidth ?: InlineLogoWidth, InlineLogoHeight)) {
        PiconBox(picon, modifier.wrapContentWidth(Alignment.Start, unbounded = true).size(InlineLogoWidth, InlineLogoHeight),
            contentScale = InlineLogoScale, alignment = Alignment.CenterStart, trim = true,
            onLogoSize = { logo ->
                drawnWidth = with(density) { inlineLogoDrawnWidth(logo, Size(InlineLogoWidth.toPx(), InlineLogoHeight.toPx())).toDp() }
            })
    }
}

private val InlineLogoWidth = 36.dp
private val InlineLogoHeight = 20.dp

/** The outlined age rating that ends a programme's metadata line. */
@Composable
internal fun ProgrammeAgeRatingBox(rating: String, tag: String) {
    val description = stringResource(R.string.details_age_rating_description, rating)
    Text(rating, Modifier.border(1.dp, TvTextColors.tertiary, MaterialTheme.shapes.extraSmall)
        .padding(horizontal = 6.dp, vertical = 2.dp).clearAndSetSemantics {
            contentDescription = description; testTag = tag
        }, style = MaterialTheme.typography.labelMedium, color = TvTextColors.tertiary)
}

@Composable
private fun DetailsTimeline(
    startLabel: String, progress: Float?, endLabel: String, spokenDescription: String, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(modifier.testTag("details-timeline").semantics(mergeDescendants = true) {
        contentDescription = spokenDescription
    },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(startLabel, style = MaterialTheme.typography.labelMedium, color = TvTextColors.tertiary)
        if (progress != null) ProgressStrip(progress, Modifier.weight(1f).testTag("details-progress"), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant, height = 4.dp)
        else Spacer(Modifier.weight(1f))
        Text(endLabel, style = MaterialTheme.typography.labelMedium, color = TvTextColors.tertiary)
    }
}

@Composable
private fun DetailsActions(
    actions: List<BrowseContextAction>, event: EpgEvent?, recording: DvrEntry?, nowSec: Long,
    requesters: Map<BrowseContextAction, FocusRequester>, onAction: (BrowseContextAction) -> Unit,
    onFocused: (BrowseContextAction) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp).testTag("details-actions"),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.forEachIndexed { index, action ->
            key(if (action == BrowseContextAction.MARK_UNWATCHED) BrowseContextAction.MARK_WATCHED else action) {
                val modifier = Modifier.height(40.dp).focusRequester(requesters.getValue(action))
                    .onFocusChanged { if (it.isFocused) onFocused(action) }
                    .focusProperties {
                        left = actions.getOrNull(index - 1)?.let(requesters::get) ?: FocusRequester.Cancel
                        right = actions.getOrNull(index + 1)?.let(requesters::get) ?: FocusRequester.Cancel
                    }.testTag("details-${action.name.lowercase()}")
                val content: @Composable RowScope.() -> Unit = {
                    val indicator = when (action) {
                        BrowseContextAction.WATCH -> watchingIndicator(event, nowSec)
                        BrowseContextAction.PLAY, BrowseContextAction.RESUME -> watchingIndicator(recording?.id)
                        // Behind live this can be the programme on air, not the one at the viewer's position.
                        BrowseContextAction.CONTINUE_WATCHING -> if (event == null) watchingIndicator(recording?.id)
                            else channelWatchingIndicator(event.channelId)
                        else -> ChannelPlaybackIndicator.NONE
                    }
                    BrowseContextActionIcon(action, indicator, ButtonDefaults.IconSize)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(action.label(action == BrowseContextAction.WATCH &&
                        (event == null && recording?.state == DvrEntryState.SCHEDULED ||
                            event != null && !event.isRunning(nowSec)))),
                        maxLines = 1, softWrap = false)
                }
                 if (index == 0 && action != BrowseContextAction.DELETE) Button(onClick = { onAction(action) }, modifier = modifier,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding, content = content)
                else OutlinedButton(onClick = { onAction(action) }, modifier = modifier,
                    contentPadding = OutlinedButtonDefaults.ButtonWithIconContentPadding, content = content)
            }
        }
    }
}

/** The page's status in the eyebrow: the recording badges, or a neutral fact badge. */
@Composable
private fun DetailsStatusBadge(status: DetailsStatus?, label: String? = null) {
    when (status) {
        DetailsStatus.RECORDING -> RecordingBadge(DvrEntryState.RECORDING)
        DetailsStatus.RECORDING_ERRORS -> RecordingBadge(DvrEntryState.RECORDING_ERROR)
        DetailsStatus.SCHEDULED -> RecordingBadge(DvrEntryState.SCHEDULED)
        DetailsStatus.MISSED -> RecordingBadge(DvrEntryState.MISSED)
        DetailsStatus.CANCELLED -> RecordingBadge(DvrEntryState.INVALID)
        DetailsStatus.FAILED -> RecordingBadge(DvrEntryState.COMPLETED_ERROR)
        DetailsStatus.ON_NOW -> StatusBadge(stringResource(R.string.details_status_on_now), StatusBadgeTone.NEUTRAL)
        null -> StatusBadge(checkNotNull(label) { "A status-less badge requires a label" }, StatusBadgeTone.NEUTRAL)
    }
}

@Composable
internal fun detailsFactValue(fact: DetailsFact, nowSec: Long): String {
    val resources = LocalContext.current.resources
    return when (fact.kind) {
        DetailsFactKind.LENGTH -> formatRecordedDuration(fact.seconds, resources)
        DetailsFactKind.LEFT -> formatDurationMinutes(fact.seconds / 60, resources)
        DetailsFactKind.RECORDED -> formatRecordingSchedule(fact.seconds, dateOnly = true,
            locale = LocalConfiguration.current.locales[0], nowSec = nowSec)
        DetailsFactKind.STARTS, DetailsFactKind.STARTED -> {
            val zone = java.time.ZoneId.systemDefault()
            val day = java.time.Instant.ofEpochSecond(fact.seconds).atZone(zone).toLocalDate()
            val today = java.time.Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
            when (day) {
                today -> stringResource(R.string.details_today, formatHm(fact.seconds))
                today.plusDays(1) -> stringResource(R.string.details_tomorrow, formatHm(fact.seconds))
                else -> formatRecordingSchedule(fact.seconds, locale = LocalConfiguration.current.locales[0], nowSec = nowSec)
            }
        }
    }
}
