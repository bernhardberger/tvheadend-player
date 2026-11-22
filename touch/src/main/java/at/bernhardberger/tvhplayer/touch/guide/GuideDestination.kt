package at.bernhardberger.tvhplayer.touch.guide

import at.bernhardberger.tvhplayer.touch.ui.DvrControls
import at.bernhardberger.tvhplayer.touch.ui.ArtworkFirstLoad
import at.bernhardberger.tvhplayer.touch.ui.LocalArtworkModels
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.core.programmeCategory
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.ui.common.episodeLabel
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatDayHeader
import at.bernhardberger.tvhplayer.ui.common.formatDayMonth
import at.bernhardberger.tvhplayer.ui.common.formatFirstAired
import at.bernhardberger.tvhplayer.ui.common.formatMetadataNumber
import at.bernhardberger.tvhplayer.ui.common.formatNarrowDuration
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabelRes
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.common.rememberDetailsEpochSeconds
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The Guide destination of the app over the shared channel scope and the session's guide. Watch
 * plays a program on now through [onWatch], and the one on now on the [playingId] channel shows as
 * watched. Record and Cancel go through [dvr]; Record's options, Show in Guide and the other airings'
 * links are left out until they are wired.
 */
@Suppress("LongParameterList") // The app's connection, its player and its pages.
@Composable
internal fun GuideDestination(
    guide: GuideViewModel,
    channels: ChannelsViewModel,
    connection: AppConnectionViewModel,
    destinations: List<Destination>,
    playingId: String?,
    onWatch: ((String) -> Unit)?,
    dvr: DvrControls,
    onOpenConnections: () -> Unit,
    onSignIn: () -> Unit,
) {
    val presentation by connection.uiState.collectAsStateWithLifecycle()
    val scope by channels.scope.collectAsStateWithLifecycle()
    val observation by guide.observation.collectAsStateWithLifecycle()
    val selection by guide.selection.collectAsStateWithLifecycle()
    val pendingVersion by guide.pendingVersion.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val result by guide.mapped.collectAsStateWithLifecycle()
    val activeClock = activeGuideClock()
    val clock = activeClock ?: result?.input?.clock
        ?: remember { GuideClock(Instant.now().epochSecond, ZoneId.systemDefault()) }
    val coverage = remember(
        scope, observation, selection.view, selection.channelId, selection.day, clock.zone, clock.today,
    ) {
        guideCoverage(scope, observation, selection, clock)
    }
    LaunchedEffect(coverage, observation.currentSession?.generationIdentity) { coverage?.let(guide::request) }
    val input = GuideMappingInput(
        scope, presentation.connection, presentation.degraded, observation, selection, clock,
        Configuration(configuration), AppClock.uses24Hour, LocalArtworkModels.current,
        playingId, guide.overviewViewport.collectAsStateWithLifecycle().value,
    )
    SubmitGuideMapping(guide, channels, connection, input, activeClock)
    val retained = result?.takeIf { it.input.scope.profileId == scope.profileId }
    val mapped = retained?.state ?: EmptyGuide
    val dayLoading = remember(mapped.listed, coverage, pendingVersion, observation.currentSession?.generationIdentity) {
        mapped.listed && coverage?.let(guide::isPending) == true
    }
    val state = mapped.withCurrentDetails(selection.detailsId, dayLoading).withSelectedDay(input, retained)
    ArtworkFirstLoad("Guide", firstLoadWait(state, input))
    // Forget invalid choices, so they cannot silently return when a tag or the schedule changes again.
    val available = selection.availableIn(state)
    if (state.listed && selection != available && input.changesFrom(retained?.input).isEmpty()) {
        LaunchedEffect(selection, available) { guide.reconcileSelection(selection, available) }
    }
    GuideScreen(
        state = state,
        view = selection.view,
        channelId = selection.channelId,
        dayIndex = state.dayDates.indexOf(selection.day).coerceAtLeast(0),
        actions = GuideActions(
            onWatch = onWatch, onRecord = dvr::record,
            onRecordOptions = null,
            onDetails = guide::openDetails,
            onDay = { state.dayDates.getOrNull(it)?.let(guide::selectDay) },
            onJump = { guide.selectJump(state.jumpTimes.getOrNull(it - 1)) },
            onTag = { id -> channels.selectTag(id?.let { ChannelTagId(it.toLong()) }) },
            onShowInGuide = null,
            onAiring = null,
            onRetry = { connection.reconnectNow() },
            onOpenConnectionSettings = onOpenConnections,
            onSignIn = onSignIn,
            onCancel = dvr::cancel,
            onStop = dvr::stopProgram,
            onChannel = { guide.selectPage(GuideView.ByChannel, it) },
            onView = { guide.selectPage(it) },
            onOverviewViewport = { guide.overviewViewport.value = it },
        ),
        destinations = destinations,
    )
}

@Composable
private fun activeGuideClock(): GuideClock? {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (!lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.STARTED)) return null
    val nowSec = rememberDetailsEpochSeconds()
    return GuideClock(nowSec - Math.floorMod(nowSec, SECONDS_PER_MINUTE), ZoneId.systemDefault())
}

private const val SECONDS_PER_MINUTE = 60L

@Composable
private fun SubmitGuideMapping(
    guide: GuideViewModel, channels: ChannelsViewModel, connection: AppConnectionViewModel,
    input: GuideMappingInput, activeClock: GuideClock?,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LifecycleStartEffect(guide) { onStopOrDispose { guide.map(context, null) } }
    SideEffect {
        if (activeClock != null && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // On restart, the UI's lifecycle-aware collectors may still be catching up independently.
            val presentation = connection.uiState.value
            guide.map(
                context,
                GuideMappingInput(
                    channels.scope.value, presentation.connection, presentation.degraded,
                    guide.observation.value, guide.selection.value, activeClock, input.configuration,
                    input.uses24Hour, input.artwork, input.playingId, guide.overviewViewport.value,
                ),
            )
        }
    }
}

private fun GuideState.withSelectedDay(input: GuideMappingInput, result: GuideMappingResult?): GuideState {
    if (input.selection.view != GuideView.ByChannel) return this
    val day = input.selection.day?.takeIf { it in dayDates } ?: input.clock.today
    val days = result?.takeIf { it.input.sameDayOwner(input) }?.days.orEmpty()
    return copy(
        date = formatDayHeader(day, input.configuration.locales[0]),
        retainedDays = days.mapValues { it.value.programs },
    )
}

private fun GuideState.withCurrentDetails(detailsId: String?, dayLoading: Boolean): GuideState {
    val current = details?.takeIf { it.id == detailsId }
    return if (dayLoading || current !== details) copy(dayLoading = dayLoading, details = current) else this
}

private fun GuideSelection.availableIn(state: GuideState): GuideSelection = copy(
    day = day?.takeIf { it in state.dayDates },
    jump = state.jumpTimes.getOrNull(state.selectedJump - 1),
    channelId = channelId?.takeIf { id -> state.channels.any { it.id == id } },
)

private fun firstLoadWait(state: GuideState, input: GuideMappingInput): String? = when {
    state.availability != GuideAvailability.Loading -> null
    !input.scope.settingsLoaded -> "settings"
    input.scope.scope.visibleChannels.isEmpty() -> "channels"
    input.observation.epgSnapshotForDisplay == null -> "guideSnapshot"
    else -> "mapping"
}

private val EmptyGuide =
    GuideState(emptyList(), days = emptyList(), date = "", availability = GuideAvailability.Loading)

internal fun guideTexts(
    context: Context,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
    uses24Hour: Boolean? = null,
): GuideTexts {
    val resources = context.resources
    // The Player's synchronous caller keeps reading the live preference; the worker gets a captured value.
    fun use24Hour() = uses24Hour ?: AppClock.uses24Hour
    fun at(epochSec: Long) = Instant.ofEpochSecond(epochSec).atZone(zone)
    fun day(date: LocalDate, today: LocalDate) = when (date) {
        today -> resources.getString(SharedR.string.channel_schedule_today)
        today.plusDays(1) -> resources.getString(SharedR.string.channel_schedule_tomorrow)
        else -> formatDayMonth(date, locale)
    }
    return GuideTexts(
        clock = { formatClock(at(it), locale, use24Hour()) },
        timeRange = { start, stop -> formatTimeInterval(start, stop, locale = locale, uses24Hour = use24Hour()) },
        duration = { formatNarrowDuration(it, resources) },
        day = ::day,
        date = { formatDayHeader(it, locale) },
        airing = { start, today ->
            "${day(at(start).toLocalDate(), today)} ${formatClock(at(start), locale, use24Hour())}"
        },
        number = { episodeLabel(it.episode, resources) },
        episode = { programmeEpisodeLine(it.episode, it.subtitle, resources).takeIf(String::isNotBlank) },
        genre = { event ->
            programmeCategory(event)?.let { resources.getString(programmeCategoryLabelRes(it)) }
                ?: event.genre?.takeIf(String::isNotBlank)
        },
        firstAired = { formatFirstAired(it, locale) },
        count = { number, of ->
            if (of != null) {
                resources.getString(SharedR.string.details_count_of, number.toInt(), of.toInt())
            } else {
                formatMetadataNumber(number, locale)
            }
        },
        broadcast = {
            resources.getString(
                if (it) SharedR.string.details_broadcast_new else SharedR.string.details_broadcast_repeat,
            )
        },
    )
}
