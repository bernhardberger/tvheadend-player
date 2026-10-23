package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.EpgFocusColumn
import at.bernhardberger.tvhplayer.core.EpgFocusTarget
import at.bernhardberger.tvhplayer.core.programmeAgeRating
import at.bernhardberger.tvhplayer.core.recordingIsRunning
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.ProgrammeEyebrow
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadline
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadlineFactsLineHeight
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadlineLineGap
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadlineSynopsisGap
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.RecordingBadge
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.programmeAboutFacts
import at.bernhardberger.tvhplayer.ui.components.programmeHeaderMetadata
import at.bernhardberger.tvhplayer.ui.screens.ChannelsDetailCrossfadeMillis
import at.bernhardberger.tvhplayer.ui.screens.ChannelsImmersiveBackground
import at.bernhardberger.tvhplayer.ui.screens.ImmersiveHeroWidth
import at.bernhardberger.tvhplayer.ui.screens.channelsProgrammeSynopsis
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** How long focus rests on a programme before the headline and its picture follow it. */
internal const val GuideHeadlineSettleMs = 200L

/** The eyebrow's picon height, which a small font's line does not reach. */
private val GuideHeadlinePiconHeight = 20.dp

/** The programme the Guide's headline shows, with the channel it is on. */
internal data class GuideHeadlineProgramme(val channel: Channel, val event: EpgEvent)

/**
 * The programme the grid focuses, or focuses next on entry: [target]'s event in [rows], on the
 * channel at the same index of [channels].
 */
internal fun guideHeadlineProgramme(
    target: EpgFocusTarget?,
    channels: List<Channel>,
    rows: List<EpgFocusColumn>,
): GuideHeadlineProgramme? {
    target ?: return null
    val channel = channels.getOrNull(target.channelIndex) ?: return null
    val event = rows.getOrNull(target.channelIndex)?.events?.firstOrNull { it.id == target.eventId } ?: return null
    return GuideHeadlineProgramme(channel, event)
}

/**
 * The programme the headline and its picture show: [programme] once focus has rested on it
 * ([GuideHeadlineSettleMs]); the first one shows at once. While the grid focuses nothing but is
 * still finding its programme ([repositioning]: rows loading or a focus pending), the last one
 * stays, so a slow group switch neither claims there is no guide data nor drops the picture. Read it
 * only inside the headline's lambdas: focus steps and settles then recompose the headline, not the
 * screen.
 */
@Composable
internal fun rememberSettledGuideHeadline(
    programme: () -> GuideHeadlineProgramme?,
    repositioning: () -> Boolean,
): State<GuideHeadlineProgramme?> {
    val currentProgramme by rememberUpdatedState(programme)
    val currentRepositioning by rememberUpdatedState(repositioning)
    val shown = remember { mutableStateOf(Snapshot.withoutReadObservation { programme() }) }
    LaunchedEffect(Unit) {
        // Asks whether the grid is repositioning only when nothing is focused, so focus steps stay cheap.
        snapshotFlow { currentProgramme().let { it to (it == null && currentRepositioning()) } }
            .collectLatest { (next, hold) ->
                if (shown.value == null) {
                    shown.value = next
                    return@collectLatest
                }
                if (hold) return@collectLatest
                delay(GuideHeadlineSettleMs)
                shown.value = next
            }
    }
    return shown
}

/**
 * The focused programme's picture at the screen's top end behind the headline, or else its
 * channel's logo: the Channels screen's corner background, which waits for focus to rest itself.
 */
@Composable
internal fun GuideHeadlineBackground(
    programme: () -> GuideHeadlineProgramme?,
    channels: List<Channel>,
    observation: SessionObservation,
    scopeObservation: SessionObservation?,
    nowSec: () -> Long,
    logoTop: Dp,
    endInset: Dp,
    modifier: Modifier = Modifier,
) {
    // A slow group switch holds the last programme, whose channel the new group may not have.
    val held = programme()?.channel
    val lookup = remember(channels, held) {
        if (held == null || channels.any { it.id == held.id }) channels else channels + held
    }
    ProvideArtworkSession(observation.currentSession, scopeObservation) {
        ChannelsImmersiveBackground(
            channels = lookup,
            channelId = { programme()?.channel?.id },
            observation = observation,
            nowSec = nowSec,
            logoTop = logoTop,
            endInset = endInset,
            modifier = modifier,
            picture = { id -> programme()?.event?.takeIf { it.channelId == id }?.image },
        )
    }
}

/**
 * The Guide's headline: who and what the settled [programme] is ([rememberSettledGuideHeadline]),
 * passive, at a fixed height so the grid below stays put; shorter text sits on its bottom edge.
 * Before anything is focused it shows the programme the grid enters.
 */
@Composable
internal fun GuideHeadline(
    programme: () -> GuideHeadlineProgramme?,
    observation: SessionObservation,
    scopeObservation: SessionObservation?,
    loading: Boolean,
    nowSec: () -> Long,
    modifier: Modifier = Modifier,
) {
    val typography = MaterialTheme.typography
    // A step below the Channels hero's title, so the grid keeps its rows.
    val titleStyle = typography.headlineSmall
    // Room for the eyebrow, a one-line title, the facts, the time and one synopsis line, as the
    // Channels hero computes it, so about four and a half rows stay below. A second title line or
    // a subtitle takes the synopsis's place: HeadlineColumn gives way inside it, the grid stays.
    val height = with(LocalDensity.current) {
        maxOf(GuideHeadlinePiconHeight, typography.labelMedium.lineHeight.toDp()) + 8.dp + titleStyle.lineHeight.toDp() +
            ProgrammeHeadlineLineGap + ProgrammeHeadlineFactsLineHeight + ProgrammeHeadlineLineGap + typography.labelMedium.lineHeight.toDp() +
            ProgrammeHeadlineSynopsisGap + typography.bodyLarge.lineHeight.toDp()
    }
    ProvideArtworkSession(observation.currentSession, scopeObservation) {
        AnimatedContent(
            targetState = programme(),
            transitionSpec = {
                // The entry's programme, resolved a frame after the screen, replaces the
                // placeholder at once rather than fading in over "no programme information".
                val change = if (initialState == null) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(ChannelsDetailCrossfadeMillis)) togetherWith
                        fadeOut(tween(ChannelsDetailCrossfadeMillis))
                }
                change using SizeTransform(clip = false) { _, _ -> snap() }
            },
            contentAlignment = Alignment.BottomStart,
            contentKey = { it?.event?.id },
            modifier = modifier.height(height).widthIn(max = ImmersiveHeroWidth)
                .semantics(mergeDescendants = true) {}.testTag("guide-headline"),
            label = "guideHeadline",
        ) { shownProgramme ->
            GuideHeadlineContent(shownProgramme, observation, loading, nowSec, titleStyle,
                Modifier.wrapContentHeight(Alignment.Bottom))
        }
    }
}

/**
 * One programme of [GuideHeadline], as `ChannelsHeadline` fills it but for any time: a programme
 * on another day names its day, and only one on air has progress. Without a programme the
 * channel's missing guide data takes the title.
 */
@Composable
private fun GuideHeadlineContent(
    programme: GuideHeadlineProgramme?,
    observation: SessionObservation,
    loading: Boolean,
    nowSec: () -> Long,
    titleStyle: TextStyle,
    modifier: Modifier,
) {
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val event = programme?.event
    val metadata = remember(event, resources, locale) {
        event?.let {
            programmeHeaderMetadata(programmeAboutFacts(it, null, resources, locale), resources, it.episode, it.firstAired, locale)
        }.orEmpty()
    }
    val now = nowSec()
    // The rows' marks, a running recording or a scheduled one, and a failed one as details names it.
    val recording = event?.let { observation.dvrEntryForEvent(it.id) }
    val mark = recording?.state?.takeIf {
        recordingIsRunning(recording) || it == DvrEntryState.SCHEDULED ||
            it == DvrEntryState.COMPLETED_ERROR || it == DvrEntryState.FILE_MISSING
    }
    ProgrammeHeadline(
        eyebrow = {
            if (programme != null) {
                val channel = programme.channel
                ProgrammeEyebrow(channel.icon, channelTitleText(channel.visibleChannelNumber, channel.name.orEmpty()),
                    Modifier.semantics(mergeDescendants = true) {}.testTag("guide-headline-channel")) {
                    RecordingBadge(mark)
                }
            }
        },
        title = event?.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_epg),
        tagPrefix = "guide-headline",
        synopsisLines = 1,
        modifier = modifier,
        subtitle = event?.subtitle,
        facts = metadata,
        rating = event?.rating?.let { programmeAgeRating(it.label, it.age) },
        timing = event?.let { guideHeadlineTiming(it, now) },
        progress = event?.takeIf { it.isRunning(now) }?.progress(now),
        synopsis = event?.let(::channelsProgrammeSynopsis),
        fitSynopsis = true,
        loading = event == null && loading,
        titleStyle = titleStyle,
    )
}

/** The programme's times, after its day as the details page names it when that is not today. */
@Composable
private fun guideHeadlineTiming(event: EpgEvent, nowSec: Long): String {
    val start = event.start.epochSeconds
    val stop = event.stop.epochSeconds
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
    return when (ChronoUnit.DAYS.between(today, Instant.ofEpochSecond(start).atZone(zone).toLocalDate())) {
        0L -> formatTimeInterval(start, stop, locale = locale)
        1L -> stringResource(R.string.details_tomorrow, formatTimeInterval(start, stop, locale = locale))
        else -> formatTimeInterval(start, stop, withDate = true, locale = locale, nowSec = nowSec)
    }
}
