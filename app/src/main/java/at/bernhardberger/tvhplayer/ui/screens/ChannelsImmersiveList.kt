package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.programmeAgeRating
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.ui.LocalWarmPlayback
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.CompactZapCard
import at.bernhardberger.tvhplayer.ui.components.CompactZapCardGap
import at.bernhardberger.tvhplayer.ui.components.CompactZapCardWidth
import at.bernhardberger.tvhplayer.ui.components.CompactZapRowOffscreen
import at.bernhardberger.tvhplayer.ui.components.DetailsHero
import at.bernhardberger.tvhplayer.ui.components.DetailsHeroLogo
import at.bernhardberger.tvhplayer.ui.components.FocusBackdrop
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ProgrammeAgeRatingBox
import at.bernhardberger.tvhplayer.ui.components.ProgrammeEyebrow
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.StartKeylineBringIntoViewSpec
import at.bernhardberger.tvhplayer.ui.components.browseDetailsViewport
import at.bernhardberger.tvhplayer.ui.components.browseRailUnderlap
import at.bernhardberger.tvhplayer.ui.components.browseScreenBackdrop
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.navigationEdgeFadeMask
import at.bernhardberger.tvhplayer.ui.components.overflowingRowViewport
import at.bernhardberger.tvhplayer.ui.components.programmeAboutFacts
import at.bernhardberger.tvhplayer.ui.components.programmeHeaderMetadata
import at.bernhardberger.tvhplayer.ui.components.rememberDetailsTint
import at.bernhardberger.tvhplayer.ui.components.rememberTabReader
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.components.underBrowseShellScrim
import at.bernhardberger.tvhplayer.ui.player.PlayerRecBadge
import at.bernhardberger.tvhplayer.ui.player.programmeFactsSegments
import kotlin.time.Instant

/** Room above and below the cards for their native focus scale and outline. */
private val ImmersiveFocusRoom = 12.dp

/** Between the hero's last line and the row's focus room: the kit's and JetStream's 40dp to the cards. */
private val ImmersiveHeroGap = 40.dp - ImmersiveFocusRoom

/** The kit's gap between the title group and the description. */
private val ImmersiveSynopsisGap = 20.dp

/** The kit's content gap between the hero's text and its closing metadata line. */
private val ImmersiveMetadataGap = 20.dp

/** The channel logo standing in for a missing programme picture, this far below the header. */
private val ImmersiveFallbackLogoTop = 24.dp

private val ImmersiveHeroWidth = 480.dp


/** What the Channels screen draws behind its channels while no playback is warm. */
internal enum class ChannelsBackdrop {
    /** The immersive list's: the programme picture in the top end corner, or the logo. */
    CORNER,
    /** The details page's: the picture, or else the logo, blurred over the whole screen on its colour. */
    BLURRED,
    /** With backdrop art off: the logo blurred on its colour, whatever the programme's picture. */
    LOGO_COLOUR,
}

/**
 * Background of the Channels screen for the focused channel, per [backdrop]: for the immersive list
 * its programme picture in the screen's top end corner under the kit's cinematic scrim, as the kit's
 * immersive list has it, on the picture's colour, or its logo below the header ([logoTop]) on the
 * theme background. It
 * reaches under the closed rail, beneath the shell's gradient. Warm playback keeps the video as
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
    backdrop: ChannelsBackdrop = ChannelsBackdrop.CORNER,
) {
    if (LocalWarmPlayback.current) return
    FocusBackdrop(
        channelId,
        // The art in the corner leaves the rest clear, so the gradient is kept off its holes;
        // full-screen backdrops are opaque and take it directly.
        modifier.browseScreenBackdrop()
            .then(if (backdrop == ChannelsBackdrop.CORNER) Modifier.underBrowseShellScrim() else Modifier)
            .testTag("channels-immersive-background"),
    ) { id ->
        val channel = channels.firstOrNull { it.id == id } ?: return@FocusBackdrop
        val now = nowSec()
        val art = remember(observation, id, now) {
            id?.let { observation.eventAt(it, Instant.fromEpochSeconds(now)) }?.image
        }
        val session = observation.currentSession
        if (backdrop != ChannelsBackdrop.CORNER) {
            val logoColour = backdrop == ChannelsBackdrop.LOGO_COLOUR
            val tint = rememberDetailsTint(art.takeUnless { logoColour }, channel.icon)
            // Once the opaque art shows, the tint beneath it would only cost another full-screen fill.
            var artShown by remember(art) { mutableStateOf(false) }
            Box(
                Modifier.fillMaxSize().underBrowseShellScrim(opaque = true)
                    .drawBehind { if (!artShown) drawRect(tint.value) }.testTag("channels-backdrop"),
            ) {
                DetailsHero(art.takeUnless { logoColour }, Modifier, blurred = true, picon = channel.icon, onLoaded = { artShown = true })
            }
            return@FocusBackdrop
        }
        Box(Modifier.fillMaxSize()) {
            // The picture's colour, or else the logo's, fills the screen, as on the details page.
            val shownArt = art.takeIf { session != null }
            val tint = rememberDetailsTint(shownArt, channel.icon)
            Box(Modifier.fillMaxSize().drawBehind { drawRect(tint.value) }.testTag("channels-backdrop"))
            if (shownArt != null) {
                DetailsHero(shownArt, Modifier.align(Alignment.TopEnd).testTag("channels-immersive-art"), background = tint)
            } else {
                DetailsHeroLogo(channel.icon,
                    Modifier.align(Alignment.TopEnd).padding(top = logoTop + ImmersiveFallbackLogoTop, end = endInset)
                        .testTag("channels-immersive-picon"),
                )
            }
        }
    }
}

/** What the immersive list's cards need from the screen. */
internal class ChannelsImmersiveCards(
    val frame: ChannelsTabBody,
    val nowSec: () -> Long,
    val keyline: Dp,
    val programmePicture: Boolean,
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
        ChannelsImmersiveHero(channels, cards, Modifier.padding(start = cards.keyline))
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
    // The row reaches under the closed rail to the screen edge, so its keyline lies that much further in.
    val keyline = cards.keyline + browseRailUnderlap()
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
                top = ImmersiveFocusRoom,
                bottom = ImmersiveFocusRoom,
            ),
            modifier = modifier
                .browseDetailsViewport()
                .fillMaxWidth()
                // The card before the keyline runs on under the navigation rail and fades out at the screen edge.
                .navigationEdgeFadeMask(keyline - CompactZapCardGap)
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
                CompactZapCard(
                    channel = channel,
                    number = channel.visibleChannelNumber,
                    event = event,
                    nowSec = now,
                    playbackIndicator = frame.playbackIndicator.takeIf { channelId == frame.playbackChannelId }
                        ?: ChannelPlaybackIndicator.NONE,
                    recording = channelId in frame.recordingIds,
                    onClick = { if (current) cards.onPlay(channel) },
                    onLongClick = { if (current) cards.onOpenContextMenu(channelId) },
                    tagPrefix = "channels-immersive",
                    channelLed = true,
                    menuKey = true,
                    programmePicture = cards.programmePicture,
                    modifier = Modifier
                        .width(CompactZapCardWidth)
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

/** The focused channel's identity and programme, passive; it dissolves like the list preview. */
@Composable
private fun ChannelsImmersiveHero(
    channels: List<Channel>,
    cards: ChannelsImmersiveCards,
    modifier: Modifier,
) {
    val frame = cards.frame
    // A leaving group keeps showing its own channel while it fades.
    val focusedId = rememberTabReader(frame.focusedId)
    val entryId = rememberTabReader(frame.entryId)
    val channelId = focusedId()?.takeIf { id -> channels.any { it.id == id } }
        ?: browsingFocusChannelId(channels, entryId())
    val typography = MaterialTheme.typography
    // A fixed height keeps the row in place while the hero's text changes length; shorter text
    // sits on the row, so a two-line title grows upward.
    val height = with(LocalDensity.current) {
        typography.bodyMedium.lineHeight.toDp() + 8.dp + typography.headlineSmall.lineHeight.toDp() * 2 +
            4.dp + typography.titleMedium.lineHeight.toDp() + ImmersiveSynopsisGap +
            typography.bodyMedium.lineHeight.toDp() * 2 + ImmersiveMetadataGap + typography.bodyMedium.lineHeight.toDp()
    }
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    AnimatedContent(
        targetState = channelId,
        transitionSpec = {
            fadeIn(tween(ChannelsDetailCrossfadeMillis)) togetherWith
                fadeOut(tween(ChannelsDetailCrossfadeMillis)) using
                SizeTransform(clip = false) { _, _ -> snap() }
        },
        contentAlignment = Alignment.BottomStart,
        modifier = modifier.height(height).widthIn(max = ImmersiveHeroWidth).testTag("channels-immersive-hero"),
        label = "immersiveHero",
    ) { id ->
        val channel = channels.firstOrNull { it.id == id } ?: return@AnimatedContent
        val now = cards.nowSec()
        val observation = frame.observation
        val current = remember(observation, id, now) { observation.eventAt(channel.id, Instant.fromEpochSeconds(now)) }
        // AnimatedContent hands its fixed height to the content; wrapping keeps short text on the row.
        Column(Modifier.wrapContentHeight(Alignment.Bottom)) {
            // Everything here is on now, so only a recording earns a mark: the player's REC badge.
            ProgrammeEyebrow(channel, channelTitleText(channel.visibleChannelNumber, channel.name.orEmpty())) {
                if (channel.id in frame.recordingIds) PlayerRecBadge()
            }
            Text(
                current?.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_epg),
                style = typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).testTag("channels-immersive-title"),
            )
            if (current != null) {
                // As in the list preview; with EIT it is often the only text besides the title.
                current.subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp).testTag("channels-immersive-subtitle"))
                }
                channelsProgrammeSynopsis(current)?.let {
                    Text(it, style = typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = ImmersiveSynopsisGap))
                }
                // As in Google TV's immersive row, the metadata closes the block next to the cards.
                val metadata = remember(current, resources, locale) {
                    programmeHeaderMetadata(programmeAboutFacts(current, null, resources, locale), resources,
                        current.episode, current.firstAired, locale)
                }
                val rating = current.rating?.let { programmeAgeRating(it.label, it.age) }
                Row(Modifier.padding(top = ImmersiveMetadataGap).testTag("channels-immersive-metadata"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        formatTimeInterval(current.start.epochSeconds, current.stop.epochSeconds),
                        style = typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    ProgressStrip(current.progress(now), Modifier.width(160.dp))
                    if (metadata.isNotEmpty()) {
                        Text(programmeFactsSegments(metadata), Modifier.weight(1f, fill = false),
                            style = typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (rating != null) ProgrammeAgeRatingBox(rating, "channels-immersive-rating")
                }
            }
        }
    }
}
