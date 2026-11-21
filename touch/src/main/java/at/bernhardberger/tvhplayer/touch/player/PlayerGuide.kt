package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import at.bernhardberger.tvhplayer.touch.guide.GuideSelection
import at.bernhardberger.tvhplayer.touch.guide.GuideTexts
import at.bernhardberger.tvhplayer.touch.guide.GuideViewModel
import at.bernhardberger.tvhplayer.touch.guide.guideCoverage
import at.bernhardberger.tvhplayer.touch.guide.guideDays
import at.bernhardberger.tvhplayer.touch.guide.guideTexts
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionPresentation
import java.time.LocalDate

@Suppress("LongParameterList")
internal fun playerSchedule(
    shown: ChannelId,
    channels: List<Channel>,
    observation: SessionObservation,
    programs: ShownPrograms,
    clock: GuideClock,
    texts: GuideTexts,
    selection: GuideSelection = GuideSelection(),
    artwork: ArtworkModels? = null,
    presentation: AppConnectionPresentation = AppConnectionPresentation(ConnectionUiState.Ready),
    dayLoading: Boolean = false,
): ChannelSchedule {
    val snapshot = observation.epgSnapshotForDisplay
    val events = snapshot?.events.orEmpty()
    val days = guideDays(events.filter { it.channelId == shown }, clock)
    val today = days.first()
    val date = selection.dateIn(days)
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
        programs = mapper.channelDay(shown, date, watched),
        days = days.map { texts.day(it, today) },
        selectedDay = days.indexOf(date),
        date = texts.date(date),
        details = { mapper.details(it, watched) }, availability = availability,
        dayLoading = dayLoading && availability == GuideAvailability.Current,
        dayDates = days,
    )
}

internal data class PlayerInfo(
    val schedule: ChannelSchedule?,
    val onShown: (Boolean) -> Unit,
    val onDay: (Int) -> Unit,
)

/** Coverage belongs to this player activity, independent of the browse Guide's selected day. */
@Composable
@Suppress("LongParameterList")
internal fun rememberPlayerInfo(
    available: Boolean,
    guide: GuideViewModel,
    shown: ChannelId,
    channels: List<Channel>,
    observation: SessionObservation,
    programs: ShownPrograms,
    clock: GuideClock,
    artwork: ArtworkModels,
    presentation: AppConnectionPresentation,
): PlayerInfo {
    var open by remember { mutableStateOf(false) }
    var pickedDay by rememberSaveable(shown.value) { mutableStateOf<String?>(null) }
    val selection = GuideSelection(day = pickedDay?.let(LocalDate::parse))
    val schedule = if (open && available) {
        playerInfoSchedule(guide, shown, channels, observation, programs, clock, selection, artwork, presentation)
    } else {
        null
    }
    return PlayerInfo(
        schedule,
        onShown = { shown ->
            if (!shown) pickedDay = null
            open = shown
        },
        onDay = { index ->
            schedule?.dayDates?.getOrNull(index)?.let { pickedDay = it.takeUnless { it == clock.today }?.toString() }
        },
    )
}

@Composable
@Suppress("LongParameterList")
private fun playerInfoSchedule(
    guide: GuideViewModel,
    shown: ChannelId,
    channels: List<Channel>,
    observation: SessionObservation,
    programs: ShownPrograms,
    clock: GuideClock,
    selection: GuideSelection,
    artwork: ArtworkModels,
    presentation: AppConnectionPresentation,
): ChannelSchedule {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val texts = remember(context, locale) { guideTexts(context, locale) }
    val pendingVersion by guide.pendingVersion.collectAsStateWithLifecycle()
    val coverage = remember(shown, observation, selection.day, clock.zone, clock.today) {
        guideCoverage(listOf(shown), observation, selection, clock)
    }
    LaunchedEffect(coverage, observation.currentSession?.generationIdentity) { coverage?.let(guide::request) }
    return remember(
        shown, channels, observation, programs, clock, texts, selection, artwork, presentation, pendingVersion,
    ) {
        playerSchedule(
            shown, channels, observation, programs, clock, texts, selection, artwork, presentation,
            dayLoading = coverage?.let(guide::isPending) == true,
        )
    }
}
