package at.bernhardberger.tvhplayer.touch.guide

import at.bernhardberger.tvhplayer.touch.ui.DvrControls
import at.bernhardberger.tvhplayer.touch.ui.LocalArtworkModels
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
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
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val nowSec = rememberCurrentEpochSeconds()()
    val clock = GuideClock(nowSec, ZoneId.systemDefault())
    val coverage = remember(scope, observation, selection.day, clock.zone, nowSec / DAY_SEC) {
        guideCoverage(scope, observation, selection, clock)
    }
    LaunchedEffect(coverage, observation.currentSession) { coverage?.let(guide::request) }
    val texts = remember(context, locale) { guideTexts(context, locale) }
    val artwork = LocalArtworkModels.current
    val state = remember(
        presentation, scope, observation, selection, pendingVersion, nowSec, texts, artwork, playingId,
    ) {
        val dayLoading = coverage?.let(guide::isPending) == true
        guideState(
            scope, presentation.connection, presentation.degraded, observation, selection, dayLoading, clock, texts,
            artwork, playingId,
        )
    }
    GuideScreen(
        state = state,
        actions = GuideActions(
            onWatch = onWatch,
            onRecord = dvr::record,
            onRecordOptions = null,
            onDetails = guide::openDetails,
            onDay = guide::selectDay,
            onJump = guide::selectJump,
            onTag = { id -> channels.selectTag(id?.let { ChannelTagId(it.toLong()) }) },
            onShowInGuide = null,
            onAiring = null,
            onRetry = { connection.reconnectNow() },
            onOpenConnectionSettings = onOpenConnections,
            onSignIn = onSignIn,
            onCancel = dvr::cancel,
            onStop = dvr::stopProgram,
        ),
        destinations = destinations,
    )
}

internal fun guideTexts(context: Context, locale: Locale): GuideTexts {
    val resources = context.resources
    val zone = ZoneId.systemDefault()
    fun at(epochSec: Long) = Instant.ofEpochSecond(epochSec).atZone(zone)
    fun day(date: LocalDate, today: LocalDate) = when (date) {
        today -> resources.getString(SharedR.string.channel_schedule_today)
        today.plusDays(1) -> resources.getString(SharedR.string.channel_schedule_tomorrow)
        else -> formatDayMonth(date, locale)
    }
    return GuideTexts(
        clock = { formatClock(at(it), locale) },
        timeRange = { start, stop -> formatTimeInterval(start, stop, locale = locale) },
        duration = { formatNarrowDuration(it, resources) },
        day = ::day,
        date = { formatDayHeader(it, locale) },
        airing = { start, today -> "${day(at(start).toLocalDate(), today)} ${formatClock(at(start), locale)}" },
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

private const val DAY_SEC = 86_400L
