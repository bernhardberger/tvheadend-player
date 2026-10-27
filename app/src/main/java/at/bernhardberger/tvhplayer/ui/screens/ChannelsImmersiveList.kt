package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.tv.material3.CardContainerDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.programmeAgeRating
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import at.bernhardberger.tvhplayer.ui.LocalWarmPlayback
import at.bernhardberger.tvhplayer.ui.TvGridColumnWidth
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.CardGlowReach
import at.bernhardberger.tvhplayer.ui.components.cardGlowRoom
import at.bernhardberger.tvhplayer.ui.components.ChannelLogoGlow
import at.bernhardberger.tvhplayer.ui.components.ChannelNowIndicators
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.CompactZapCardGap
import at.bernhardberger.tvhplayer.ui.components.CompactZapRowOffscreen
import at.bernhardberger.tvhplayer.ui.components.DetailsHero
import at.bernhardberger.tvhplayer.ui.components.DetailsHeroLogo
import at.bernhardberger.tvhplayer.ui.components.FocusBackdrop
import at.bernhardberger.tvhplayer.ui.components.LoadDetailsHeroLogo
import at.bernhardberger.tvhplayer.ui.components.LocalArtworkContext
import at.bernhardberger.tvhplayer.ui.components.progressColor
import at.bernhardberger.tvhplayer.ui.components.programmeProgress
import at.bernhardberger.tvhplayer.ui.components.watchingIndicator
import at.bernhardberger.tvhplayer.ui.components.isPlayingItem
import at.bernhardberger.tvhplayer.ui.components.ProgrammeEyebrow
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadline
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadlineFactsLineHeight
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadlineGroupGap
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadlineLineGap
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadlineSynopsisGap
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadlineTitleGap
import at.bernhardberger.tvhplayer.ui.components.ProgrammePanePicture
import at.bernhardberger.tvhplayer.ui.components.ProgrammePaneWidth
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.RecordingBadge
import at.bernhardberger.tvhplayer.ui.components.ScheduledIndicator
import at.bernhardberger.tvhplayer.ui.components.SkeletonLine
import at.bernhardberger.tvhplayer.ui.components.StandardProgrammeCard
import at.bernhardberger.tvhplayer.ui.components.StartKeylineBringIntoViewSpec
import at.bernhardberger.tvhplayer.ui.components.browseDrawerUnderlap
import at.bernhardberger.tvhplayer.ui.components.browseScreenBackdrop
import at.bernhardberger.tvhplayer.ui.components.browseUnderlap
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.navigationEdgeFadeMask
import at.bernhardberger.tvhplayer.ui.components.overflowingRowViewport
import at.bernhardberger.tvhplayer.ui.components.programmeAboutFacts
import at.bernhardberger.tvhplayer.ui.components.programmeHeaderMetadata
import at.bernhardberger.tvhplayer.ui.components.programmeSynopsis
import at.bernhardberger.tvhplayer.ui.components.rememberBrowseContextMenuTrigger
import at.bernhardberger.tvhplayer.ui.components.rememberDetailsTint
import at.bernhardberger.tvhplayer.ui.components.rememberTabReader
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.components.underBrowseShellScrim
import at.bernhardberger.tvhplayer.ui.player.programmeFactsSegments
import at.bernhardberger.tvhplayer.ui.screens.guide.guideHeadlineHeight
import kotlin.time.Instant
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.components.ChannelAccentFill
import at.bernhardberger.tvhplayer.ui.components.channelAccentGround
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ArtworkLogoPlate
import at.bernhardberger.tvhplayer.ui.components.AiringCardWidth

/**
 * The row's cards: four and their gaps span the content from the keyline to the header's end
 * (x130 to x902), so the fifth peeks in at the screen edge.
 */
private val ImmersiveCardWidth = 178.dp

/** The kit's 40sp leading spreads a two-line 32sp title apart; display type sits tighter. */
private val ImmersiveTitleLineHeight = 36.sp

/** The channel line's gap under the card: just what a 178dp card's focus scale and outline need. */
private val ImmersiveCardLabelGap = 8.dp

/** The recording badge's inset in the card's corner, the kit's embed inset. */
private val ImmersiveCardEmbedInset = 8.dp

/** The programme line's clearance above the picture's bottom and its progress strip. */
private val ImmersiveCardTextBottom = 8.dp

/** A logo's clearance in the picture above the programme line and below the card's top. */
private val ImmersiveCardLogoInset = 8.dp

private const val ImmersiveCardLogoWidthFraction = 0.6f

/** Room above and below the cards for their native focus scale and outline. */
private val ImmersiveFocusRoom = 12.dp

/** Between the hero's last line and the row's focus room: the kit's and JetStream's 40dp to the cards. */
private val ImmersiveHeroGap = 40.dp - ImmersiveFocusRoom

private const val ImmersiveSynopsisLines = 2
private const val ImmersiveListSynopsisLines = 3

/** The channel logo standing in for a missing programme picture, this far below the header. */
private val ImmersiveFallbackLogoTop = 24.dp

internal val ImmersiveHeroWidth = 480.dp

/**
 * Background of the immersive Channels list for the focused channel, as the kit's immersive list
 * has it: the programme picture at the screen's top end under the kit's cinematic scrim, on the
 * picture's colour, or else the logo below the header ([logoTop]) on the glow of its colour.
 * Without [programArt] the logo stands in, matching cards that show logos. The picture is the
 * channel's on-now programme's unless [picture] picks another, as the Guide's focused programme.
 * The picture is as wide as the kit's unless [pictureHeight] scales it down to a height.
 * It reaches under the closed rail, beneath the shell's gradient. Warm playback keeps the video as
 * the background, so nothing is drawn.
 */
@Composable
internal fun ChannelsImmersiveBackground(
    channels: List<Channel>,
    channelId: () -> ChannelId?,
    observation: SessionObservation,
    nowSec: () -> Long,
    logoTop: Dp,
    endInset: Dp,
    modifier: Modifier = Modifier,
    programArt: Boolean = true,
    picture: (ChannelId) -> ArtworkId? = { observation.eventAt(it, Instant.fromEpochSeconds(nowSec()))?.image },
    pictureHeight: Dp? = null,
) {
    if (LocalWarmPlayback.current) return
    val session = observation.currentSession != null
    val artwork = LocalArtworkContext.current
    var revealed by remember { mutableStateOf<ChannelBackdrop?>(null) }
    FocusBackdrop(
        // A new backdrop for whatever changes what it shows, so the session's and the guide's
        // arrival fade in like a focus step rather than popping in piece by piece.
        {
            val id = channelId()
            val art = id?.takeIf { programArt && session }?.let(picture)
            val channel = channels.firstOrNull { it.id == id }
            val icon = channel?.icon
            ChannelBackdrop(id, art, icon, logo = channel != null && (session || icon?.let { artwork.logo(it).saved } != null))
        },
        // The art in the corner leaves the rest clear, so the gradient is kept off its holes.
        modifier.browseScreenBackdrop().underBrowseShellScrim().testTag("channels-immersive-background"),
        onReveal = { revealed = it },
    ) { shown, onReady ->
        val channel = shown.channel
        // Without a session or a saved logo neither colour nor picture can load; the plain background stays.
        if (channel == null || !shown.logo) {
            LaunchedEffect(Unit) { onReady() }
            return@FocusBackdrop
        }
        val art = shown.art
        if (art == null) {
            // The logo's colour glows from the top right, as on the details page, under the sharp
            // logo. Shown once the glow is baked and the logo loaded, so the logo comes with it.
            var glowSettled by remember { mutableStateOf(false) }
            var logoSettled by remember { mutableStateOf(false) }
            if (glowSettled && logoSettled) LaunchedEffect(Unit) { onReady() }
            ChannelLogoGlow(shown.icon, channel, Modifier.testTag("channels-logo-glow"), onSettled = { glowSettled = true })
            LoadDetailsHeroLogo(shown.icon, onSettled = { logoSettled = true })
            return@FocusBackdrop
        }
        // Shown once both its colour and its picture are known, so no step of either is seen.
        var tintSettled by remember { mutableStateOf(false) }
        var pictureSettled by remember { mutableStateOf(false) }
        if (tintSettled && pictureSettled) LaunchedEffect(Unit) { onReady() }
        Box(Modifier.fillMaxSize()) {
            // The picture's colour fills the screen, as on the details page.
            val tint = rememberDetailsTint(art, shown.icon, onSettled = { tintSettled = true })
            Box(Modifier.fillMaxSize().drawBehind { drawRect(tint.value) }.testTag("channels-backdrop"))
            DetailsHero(art, Modifier.align(Alignment.TopEnd).testTag("channels-immersive-art"),
                onSettled = { pictureSettled = true }, height = pictureHeight)
        }
    }
    // The sharp logo belongs to the content: it moves with it as the drawer opens, while the
    // backdrop beneath stays put. It comes and changes as the backdrop fades in, which also
    // waits for the logo to be there.
    val logo = revealed?.takeIf { it.art == null && it.logo }
    Crossfade(logo, modifier, BrowseMotionPolicy.backdropFade(), label = "immersiveLogo") { shown ->
        if (shown == null) return@Crossfade
        Box(Modifier.fillMaxSize()) {
            DetailsHeroLogo(shown.icon,
                Modifier.align(Alignment.TopEnd).padding(top = logoTop + ImmersiveFallbackLogoTop, end = endInset)
                    .testTag("channels-immersive-picon"),
            )
        }
    }
}

/**
 * What a backdrop shows: the channel's programme picture, which needs the session, or else its
 * [logo], there with the session or before it as a saved copy. It carries the channel's [icon], so
 * a backdrop fading out still draws after its channel has left the list.
 */
private data class ChannelBackdrop(val channel: ChannelId?, val art: ArtworkId?, val icon: ArtworkId?, val logo: Boolean)

/** What the immersive list's cards need from the screen. */
internal class ChannelsImmersiveCards(
    val frame: ChannelsTabBody,
    val nowSec: () -> Long,
    val keyline: Dp,
    val programmePicture: Boolean,
    /** Whether each card's channel is named below it. */
    val channelNames: Boolean,
    val focusRequesters: Map<ChannelId, FocusRequester>,
    val navigationFocus: FocusRequester?,
    val headerFocus: FocusRequester?,
    val onFocusChannel: (ChannelId) -> Unit,
    val onPlay: (Channel) -> Unit,
    val onOpenContextMenu: (ChannelId) -> Unit,
    val onExitTowardNavigation: () -> Unit,
    val onPage: (Int) -> Boolean,
)

/**
 * The immersive list: the focused channel's programme above one row of compact zap cards along
 * the bottom, over [ChannelsImmersiveBackground]. Up from the row reaches the header, Left at its
 * start the navigation rail; Down stays.
 */
@Composable
internal fun ChannelsImmersiveBody(
    channels: List<Channel>,
    cards: ChannelsImmersiveCards,
    state: LazyListState,
    current: Boolean,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
) {
    Column(modifier.padding(bottom = (bottomInset - ImmersiveFocusRoom).coerceAtLeast(0.dp))) {
        Spacer(Modifier.weight(1f))
        ChannelsImmersiveHero(channels, cards.frame, cards.nowSec, Modifier.padding(start = cards.keyline))
        Spacer(Modifier.height(ImmersiveHeroGap))
        ChannelsCardRow(
            channels = channels,
            cards = cards,
            state = state,
            current = current,
            modifier = rowModifier,
        )
    }
}

/**
 * One overflowing card row; the focused card rests on the start keyline like the player rail.
 * A leaving group's row ([current] false) takes no focus or keys.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelsCardRow(
    channels: List<Channel>,
    cards: ChannelsImmersiveCards,
    state: LazyListState,
    current: Boolean,
    modifier: Modifier,
) {
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    val offscreenPx = with(density) { CompactZapRowOffscreen.roundToPx() }
    // The row reaches under the drawer to the screen edge, open or closed, so its keyline lies that much further in.
    val underlap = browseDrawerUnderlap()
    val keyline = cards.keyline + underlap
    // Where the screen's edge lies in the row, as the drawer moves it, for the fade.
    var screenEdge by remember { mutableFloatStateOf(0f) }
    val bringIntoView = remember(density, keyline) {
        StartKeylineBringIntoViewSpec(with(density) { keyline.toPx() } + offscreenPx)
    }
    val frame = cards.frame
    var focusedIndex by remember { mutableIntStateOf(-1) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
        LazyRow(
            state = state,
            userScrollEnabled = current,
            horizontalArrangement = Arrangement.spacedBy(CompactZapCardGap),
            contentPadding = PaddingValues(
                start = keyline + CompactZapRowOffscreen,
                end = cards.keyline + CompactZapRowOffscreen,
                top = CardGlowReach,
                bottom = CardGlowReach,
            ),
            modifier = modifier
                .browseUnderlap(underlap)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val left = coordinates.positionInRoot().x
                    val rootWidth = coordinates.findRootCoordinates().size.width
                    screenEdge = (if (ltr) -left else left + coordinates.size.width - rootWidth).coerceAtLeast(0f)
                }
                // The focused card's glow reaches past the focus room, outside the fade's layer.
                .cardGlowRoom(top = CardGlowReach - ImmersiveFocusRoom)
                // The card before the keyline runs on under the navigation drawer and fades out at the screen edge.
                .navigationEdgeFadeMask(keyline - CompactZapCardGap, leadingInsetPx = { screenEdge })
                // Under the opening drawer the cards darken like the backdrop, inside the fade's layer.
                .underBrowseShellScrim(inOffscreenLayer = true)
                .overflowingRowViewport(offscreenPx)
                .testTag("channels-immersive-cards")
                .focusGroup()
                .focusRestorer()
                .onPreviewKeyEvent { event ->
                    if (!current || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val index = focusedIndex.takeIf { it in channels.indices }
                    val towardNavigation = if (ltr) Key.DirectionLeft else Key.DirectionRight
                    val awayFromNavigation = if (ltr) Key.DirectionRight else Key.DirectionLeft
                    when {
                        index == null -> false
                        event.key == towardNavigation -> {
                            if (index == 0) cards.onExitTowardNavigation()
                            false
                        }
                        event.key == awayFromNavigation -> index == channels.lastIndex
                        event.key == Key.DirectionDown -> true
                        else -> ChannelNavigation.pageDirectionForKeyCode(event.nativeKeyEvent.keyCode)?.let(cards.onPage) ?: false
                    }
                },
        ) {
            itemsIndexed(channels, key = { _, channel -> channelLazyItemKey(channel.id) }) { index, channel ->
                val channelId = channel.id
                val requester = cards.focusRequesters[channelId]?.takeIf { current }
                // A focused lazy item can stay pinned for the publication that removes it.
                if (current && requester == null) return@itemsIndexed
                val now = cards.nowSec()
                val event = remember(channelId, frame.observation, now) {
                    frame.observation.eventAt(channelId, Instant.fromEpochSeconds(now))
                }
                ChannelsRowCard(
                    channel = channel,
                    event = event,
                    nowSec = now,
                    playbackIndicator = frame.playbackIndicator.takeIf { channelId == frame.playbackChannelId }
                        ?: ChannelPlaybackIndicator.NONE,
                    recording = channelId in frame.recordingIds,
                    onClick = { if (current) cards.onPlay(channel) },
                    onLongClick = { if (current) cards.onOpenContextMenu(channelId) },
                    programmeLoading = frame.guideLoading,
                    programmePicture = cards.programmePicture,
                    channelName = cards.channelNames,
                    modifier = Modifier
                        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                        .tabFocus()
                        .focusProperties {
                            canFocus = current
                            if (index == 0) {
                                if (ltr) left = cards.navigationFocus ?: FocusRequester.Default
                                else right = cards.navigationFocus ?: FocusRequester.Default
                            }
                            cards.headerFocus?.let { up = it }
                        }
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusedIndex = index
                                cards.onFocusChannel(channelId)
                            }
                        }
                        .then(if (current) Modifier.testTag("channel-card-${channelId.value}") else Modifier),
                )
            }
        }
    }
}

/**
 * A card of the immersive row and the grid, the details page's standard card: the current programme inside
 * its picture, always, as a browse card names what it shows. With [programmePicture] the channel's
 * logo sits in the picture's corner; without a picture the logo stands on its ambient colour above
 * the programme. With [channelName] the channel's number and name go below, and the playing mark
 * with them; without, the mark ends the programme line or sits in the picture's other bottom corner.
 * While the guide is [programmeLoading], a missing
 * programme line is a [SkeletonLine] rather than "No program information".
 */
@Composable
internal fun ChannelsRowCard(
    channel: Channel,
    event: EpgEvent?,
    nowSec: Long,
    playbackIndicator: ChannelPlaybackIndicator,
    recording: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    programmeLoading: Boolean,
    programmePicture: Boolean,
    modifier: Modifier,
    channelName: Boolean,
    width: Dp = ImmersiveCardWidth,
    tagPrefix: String = "channels-immersive",
) {
    val contextMenu = rememberBrowseContextMenuTrigger(onLongClick, menuKey = true)
    val running = event?.takeIf { it.isRunning(nowSec) }
    val programme = running?.title?.takeIf { it.isNotBlank() }
    val tag = "$tagPrefix-${channel.id.value}"
    val session = LocalArtworkContext.current.currentSession
    val art = running?.image?.takeIf { programmePicture && session != null }
    var artFailed by remember(art, session) { mutableStateOf(false) }
    val context = LocalPlatformContext.current
    val textHeight = with(LocalDensity.current) { MaterialTheme.typography.titleSmall.lineHeight.toDp() } +
        ImmersiveCardTextBottom
    // A picture card has no text on it, so no scrim.
    val artModel = remember(art, session) {
        if (art == null || session == null) null else AppArtworkSource(session, art)
    }
    val showsArt = artModel != null && !artFailed
    StandardProgrammeCard(
        width = width,
        onClick = onClick,
        onLongClick = contextMenu.onLongClick,
        modifier = modifier.then(contextMenu.modifier),
        art = art.takeIf { showsArt },
        picon = channel.icon,
        picture = {
            if (artModel != null && !artFailed) AsyncImage(artModel, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().testTag("$tag-art"),
                onError = { artFailed = true })
            else {
                ChannelAccentFill(channel.icon, channel.id, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().padding(top = ImmersiveCardLogoInset, bottom = textHeight + ImmersiveCardLogoInset),
                    contentAlignment = Alignment.Center) {
                    val pictureHeight = width * 9f / 16f
                    PiconBox(channel.icon, Modifier.fillMaxWidth(ImmersiveCardLogoWidthFraction).fillMaxHeight()
                        .testTag("$tag-picon"),
                        ground = channelAccentGround(channel.icon, channel.id, depth = (pictureHeight - textHeight) / 2 / pictureHeight))
                }
            }
            val lineModifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = ImmersiveCardTextBottom)
            // Without a picture the logo leads; beside picture cards its programme stays quiet.
            val lineColor = if (programmePicture) TvTextColors.secondary else TvTextColors.primary
            if (!showsArt) CompositionLocalProvider(LocalContentColor provides lineColor) {
                ProvideTextStyle(MaterialTheme.typography.titleSmall.copy(color = lineColor)) {
                    Row(lineModifier, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val textModifier = Modifier.weight(1f)
                        when {
                            // The strip shows how far along it is; the headline gives the focused card's times.
                            programme != null -> Text(programme, textModifier.testTag("$tag-now"), maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            programmeLoading -> SkeletonLine(textModifier.testTag("$tag-loading"))
                            else -> Text(stringResource(R.string.no_epg), textModifier.testTag("$tag-now"),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        // Without the line below, the playing mark ends the programme line.
                        if (!channelName) ChannelNowIndicators(
                            playingNow = playbackIndicator == ChannelPlaybackIndicator.PLAYING,
                            recordingNow = false, playbackIndicator = playbackIndicator)
                    }
                }
            }
            // The picture carries the channel's logo in its embed corner, like the airing cards.
            if (showsArt && channel.icon != null) ArtworkLogoPlate(channel.icon,
                Modifier.align(Alignment.BottomStart).padding(ImmersiveCardEmbedInset).testTag("$tag-art-picon"),
                scale = width / AiringCardWidth)
            // Without the line below, a picture card's playing mark takes the picture's other bottom corner.
            if (showsArt && !channelName && playbackIndicator != ChannelPlaybackIndicator.NONE) Box(
                Modifier.align(Alignment.BottomEnd).padding(ImmersiveCardEmbedInset)
                    .height(32.dp * (width / AiringCardWidth))
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 6.dp).testTag("$tag-art-playing"),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides TvTextColors.primary) {
                    ChannelNowIndicators(playingNow = playbackIndicator == ChannelPlaybackIndicator.PLAYING,
                        recordingNow = false, playbackIndicator = playbackIndicator)
                }
            }
            // The kit's embed corner holds the recording badge, as on the player's schedule tiles.
            if (recording) RecordingBadge(DvrEntryState.RECORDING, Modifier.align(Alignment.TopEnd).padding(ImmersiveCardEmbedInset),
                compact = true)
            // Same embedded strip as the airing cards; the card's border keeps clear of it.
            // The playing channel's strip follows the viewer, like the other channel cards.
            val progress = programmeProgress(running, nowSec)
            if (progress != null) ProgressStrip(progress,
                Modifier.align(Alignment.BottomStart).fillMaxWidth().testTag("$tag-progress"),
                color = progressColor(watchingIndicator(running, nowSec).isPlayingItem),
                height = 3.dp)
        },
        title = {
            if (!channelName) return@StandardProgrammeCard
            // Start-aligned like the kit's content block.
            Row(Modifier.fillMaxWidth().padding(top = ImmersiveCardLabelGap), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The card's content colour: muted, and white on the focused card, so the eye goes there.
                Text(channelTitleText(channel.visibleChannelNumber, channel.name.orEmpty()),
                    Modifier.weight(1f).testTag("$tag-identity"),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                ChannelNowIndicators(playingNow = playbackIndicator == ChannelPlaybackIndicator.PLAYING,
                    recordingNow = false, playbackIndicator = playbackIndicator)
            }
        },
        subtitle = {},
        contentColor = CardContainerDefaults.contentColor(contentColor = TvTextColors.secondary,
            focusedContentColor = TvTextColors.primary),
    )
}

/** The focused channel's identity and programme, passive; it dissolves like the list preview. */
@Composable
internal fun ChannelsImmersiveHero(
    channels: List<Channel>,
    frame: ChannelsTabBody,
    nowSec: () -> Long,
    modifier: Modifier,
    besideList: Boolean = false,
    sparse: Boolean = false,
) {
    // A leaving group keeps showing its own channel while it fades.
    val focusedId = rememberTabReader(frame.focusedId)
    val entryId = rememberTabReader(frame.entryId)
    val channelId = focusedId()?.takeIf { id -> channels.any { it.id == id } }
        ?: browsingFocusChannelId(channels, entryId())
    val typography = MaterialTheme.typography
    // A fixed height keeps the row in place while the hero's text changes length; shorter text
    // sits on the row, so a two-line title grows upward. Beside the list nothing moves with it.
    val height = if (sparse) guideHeadlineHeight() else with(LocalDensity.current) {
        typography.labelMedium.lineHeight.toDp() + ProgrammeHeadlineTitleGap + typography.headlineLarge.lineHeight.toDp() * 2 +
            4.dp + typography.bodyLarge.lineHeight.toDp() +
            ProgrammeHeadlineGroupGap + ProgrammeHeadlineFactsLineHeight + ProgrammeHeadlineLineGap + typography.labelMedium.lineHeight.toDp() +
            ProgrammeHeadlineSynopsisGap + typography.bodyLarge.lineHeight.toDp() * ImmersiveSynopsisLines +
            ProgrammeHeadlineSynopsisGap + typography.labelMedium.lineHeight.toDp()
    }
    AnimatedContent(
        targetState = channelId,
        transitionSpec = {
            fadeIn(tween(ChannelsDetailCrossfadeMillis)) togetherWith
                fadeOut(tween(ChannelsDetailCrossfadeMillis)) using
                SizeTransform(clip = false) { _, _ -> snap() }
        },
        contentAlignment = if (besideList) Alignment.TopStart else Alignment.BottomStart,
        modifier = modifier.then(if (besideList) Modifier else Modifier.height(height))
            .widthIn(max = ImmersiveHeroWidth).testTag("channels-immersive-hero"),
        label = "immersiveHero",
    ) { id ->
        val channel = channels.firstOrNull { it.id == id } ?: return@AnimatedContent
        val now = nowSec()
        val observation = frame.observation
        val current = remember(observation, id, now) { observation.eventAt(channel.id, Instant.fromEpochSeconds(now)) }
        val next = remember(observation, id, now) { observation.nextEvent(channel.id, Instant.fromEpochSeconds(now)) }
        val nextScheduled = remember(observation, next) {
            next?.let { observation.dvrEntryForEvent(it.id) }?.state == DvrEntryState.SCHEDULED
        }
        // AnimatedContent hands its fixed height to the content; wrapping keeps short text on the row.
        // Beside the list the headline hangs from the picture, and the synopsis takes what is left.
        ChannelsHeadline(channel, current, next, nextScheduled, channel.id in frame.recordingIds, now,
            synopsisLines = when {
                sparse -> 0
                besideList -> ImmersiveListSynopsisLines
                else -> ImmersiveSynopsisLines
            },
            loading = frame.guideLoading, fitSynopsis = besideList, showNext = !sparse,
            // Beside the list the title steps down one size, so it doesn't compete with the rows;
            // the sparse headline over the grid is the Guide's.
            titleStyle = when {
                sparse -> MaterialTheme.typography.headlineSmall
                besideList -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.headlineLarge.copy(lineHeight = ImmersiveTitleLineHeight)
            },
            // The kit's 16sp description; the narrow column beside the list keeps 14sp for its line length.
            synopsisStyle = if (besideList) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.wrapContentHeight(if (besideList) Alignment.Top else Alignment.Bottom))
    }
}

/** Avoid repeating a supplied subtitle as the synopsis when a fuller description exists. */
internal fun channelsProgrammeSynopsis(event: EpgEvent): String? =
    programmeSynopsis(event.title, event.subtitle, event.summary, event.description)

/**
 * The headline of the immersive row and beside the list: who and what is on now, then what
 * follows in the quietest tone.
 */
@Composable
internal fun ChannelsHeadline(
    channel: Channel, current: EpgEvent?, next: EpgEvent?, nextScheduled: Boolean, recording: Boolean, now: Long,
    synopsisLines: Int, loading: Boolean, modifier: Modifier, fitSynopsis: Boolean = false, showNext: Boolean = true,
    titleStyle: TextStyle = MaterialTheme.typography.headlineLarge,
    synopsisStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val metadata = remember(current, resources, locale) {
        current?.let {
            programmeHeaderMetadata(programmeAboutFacts(it, null, resources, locale), resources, it.episode, it.firstAired, locale, it.contentGenre)
        }.orEmpty()
    }
    ProgrammeHeadline(
        eyebrow = {
            // Everything here is on now, so only a recording earns a mark: the player's REC badge.
            ProgrammeEyebrow(channel.icon, channelTitleText(channel.visibleChannelNumber, channel.name.orEmpty()),
                Modifier.semantics(mergeDescendants = true) {}.testTag("channels-headline-channel")) {
                if (recording) RecordingBadge(DvrEntryState.RECORDING)
            }
        },
        title = current?.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_epg),
        tagPrefix = "channels-immersive",
        synopsisLines = synopsisLines,
        modifier = modifier,
        // With EIT it is often the only text besides the title.
        subtitle = current?.subtitle,
        facts = metadata,
        rating = current?.rating?.let { programmeAgeRating(it.label, it.age) },
        timing = current?.let { formatTimeInterval(it.start.epochSeconds, it.stop.epochSeconds) },
        progress = programmeProgress(current, now),
        progressColor = progressColor(watchingIndicator(current, now).isPlayingItem),
        synopsis = current?.takeIf { synopsisLines > 0 }?.let(::channelsProgrammeSynopsis),
        fitSynopsis = fitSynopsis,
        loading = current == null && loading,
        titleStyle = titleStyle,
        synopsisStyle = synopsisStyle,
        // As the player's info bar has it: now above, what follows on the bottom edge, quieter.
        footer = next?.takeIf { showNext }?.let { { footerModifier -> NextProgramme(it, nextScheduled, footerModifier) } },
    )
}

/** What follows, with a scheduled recording marked on the programme as the rows mark theirs. */
@Composable
private fun NextProgramme(next: EpgEvent, scheduled: Boolean, modifier: Modifier) {
    val color = TvTextColors.tertiary
    Row(modifier.testTag("channels-immersive-next"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val title = next.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_epg)
        Text(stringResource(R.string.player_next, "${formatHm(next.start.epochSeconds)} $title"),
            Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelMedium, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (scheduled) ScheduledIndicator(Modifier.testTag("channels-next-scheduled"), tint = color)
    }
}

/**
 * The focused channel's picture at the top of the pane beside the list, one size and place
 * whatever the headline below holds and whether playback runs behind: with [programArt] the
 * programme's artwork, else, and for a programme without artwork, the logo on the logo's ambient
 * backdrop.
 */
@Composable
internal fun ChannelsListArt(
    channels: List<Channel>,
    frame: ChannelsTabBody,
    nowSec: () -> Long,
    programArt: Boolean,
    modifier: Modifier,
) {
    val focusedId = rememberTabReader(frame.focusedId)
    val entryId = rememberTabReader(frame.entryId)
    val channelId = focusedId()?.takeIf { id -> channels.any { it.id == id } }
        ?: browsingFocusChannelId(channels, entryId())
    AnimatedContent(
        targetState = channelId,
        transitionSpec = { fadeIn(tween(ChannelsDetailCrossfadeMillis)) togetherWith fadeOut(tween(ChannelsDetailCrossfadeMillis)) },
        modifier = modifier.width(ProgrammePaneWidth).aspectRatio(16f / 9f).testTag("channels-list-art"),
        label = "listArt",
    ) { id ->
        val channel = channels.firstOrNull { it.id == id } ?: return@AnimatedContent
        val now = nowSec()
        val art = remember(frame.observation, id, now, programArt) {
            if (programArt) frame.observation.eventAt(channel.id, Instant.fromEpochSeconds(now))?.image else null
        }
        ProgrammePanePicture(art, channel.icon, channel.id, "channels-list-art-logo", Modifier.fillMaxSize())
    }
}
