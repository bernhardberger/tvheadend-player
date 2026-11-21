package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.awaitsGuide
import at.bernhardberger.tvhplayer.core.browseAppError
import at.bernhardberger.tvhplayer.touch.guide.GuideAvailability
import at.bernhardberger.tvhplayer.touch.guide.GuideClock
import at.bernhardberger.tvhplayer.touch.guide.GuideMapper
import at.bernhardberger.tvhplayer.touch.guide.GuideTexts
import at.bernhardberger.tvhplayer.touch.guide.GuideViewModel
import at.bernhardberger.tvhplayer.touch.guide.guideCoverage
import at.bernhardberger.tvhplayer.touch.guide.guideDays
import at.bernhardberger.tvhplayer.touch.guide.guideTexts
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionPresentation

@Suppress("LongParameterList")
internal fun playerSchedule(
    shown: ChannelId,
    channels: List<Channel>,
    observation: SessionObservation,
    programs: ShownPrograms,
    clock: GuideClock,
    texts: GuideTexts,
    day: Int = 0,
    artwork: ArtworkModels? = null,
    presentation: AppConnectionPresentation = AppConnectionPresentation(ConnectionUiState.Ready),
    dayLoading: Boolean = false,
): ChannelSchedule {
    val snapshot = observation.epgSnapshotForDisplay
    val events = snapshot?.events.orEmpty()
    val days = guideDays(events.filter { it.channelId == shown }, clock)
    val selected = day.coerceIn(days.indices)
    val today = days.first()
    val date = days[selected]
    val availability = when {
        presentation.connection == ConnectionUiState.CredentialUnavailable -> GuideAvailability.SignInNeeded
        snapshot == null -> presentation.connection.browseAppError()?.let(GuideAvailability::Failed)
            ?: if (presentation.connection.awaitsGuide(observation.epgState)) GuideAvailability.Loading
            else GuideAvailability.Current
        presentation.degraded -> GuideAvailability.Offline
        else -> GuideAvailability.Current
    }
    val watched = programs.now?.takeIf { it.channelId == shown }
    val mapper = GuideMapper(channels, observation, events, clock, today, texts, artwork, playing = null)
    return ChannelSchedule(
        mapper.channelDay(shown, date, watched), days.map { texts.day(it, today) }, selected, texts.date(date),
        details = { mapper.details(it, watched) }, availability = availability,
        dayLoading = dayLoading && availability == GuideAvailability.Current,
    )
}

/** Coverage belongs to this player activity, independent of the browse Guide's selected day. */
@Composable
@Suppress("LongParameterList")
internal fun playerInfoSchedule(
    guide: GuideViewModel,
    shown: ChannelId,
    channels: List<Channel>,
    observation: SessionObservation,
    programs: ShownPrograms,
    clock: GuideClock,
    day: Int,
    artwork: ArtworkModels,
    presentation: AppConnectionPresentation,
): ChannelSchedule {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val texts = remember(context, locale) { guideTexts(context, locale) }
    val pendingVersion by guide.pendingVersion.collectAsStateWithLifecycle()
    val coverage = remember(shown, observation, day, clock) { guideCoverage(listOf(shown), observation, day, clock) }
    LaunchedEffect(coverage, observation.currentSession) { coverage?.let(guide::request) }
    return remember(shown, channels, observation, programs, clock, texts, day, artwork, presentation, pendingVersion) {
        playerSchedule(
            shown, channels, observation, programs, clock, texts, day, artwork, presentation,
            dayLoading = coverage?.let(guide::isPending) == true,
        )
    }
}
