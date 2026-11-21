package at.bernhardberger.tvhplayer.touch.recordings

import at.bernhardberger.tvhplayer.touch.ui.LocalArtworkModels
import android.content.res.Resources
import android.icu.text.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvhplayer.core.DvrDateSection
import at.bernhardberger.tvhplayer.core.programmeCategory
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.ui.common.formatFileSize
import at.bernhardberger.tvhplayer.ui.common.formatRecordingKeepUntil
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabelRes
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

/**
 * The Recordings destination of the app. Play, Resume, Delete and More are left out until the
 * recording player and the server's changes are wired.
 */
@Composable
internal fun RecordingsDestination(
    recordings: RecordingsViewModel,
    connection: AppConnectionViewModel,
    destinations: List<Destination>,
    onOpenConnections: () -> Unit,
    onSignIn: () -> Unit,
) {
    val presentation by connection.uiState.collectAsStateWithLifecycle()
    val observation by recordings.observation.collectAsStateWithLifecycle()
    val view by recordings.view.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val nowSec = rememberCurrentEpochSeconds()()
    val texts = remember(context, locale) { recordingsTexts(context, locale) }
    val artwork = LocalArtworkModels.current
    val state = remember(observation, view, presentation, nowSec, texts, artwork) {
        val clock = RecordingClock(Instant.ofEpochSecond(nowSec), ZoneId.systemDefault(), locale)
        recordingsState(observation, view, presentation.connection, presentation.degraded, clock, texts, artwork)
    }
    RecordingsScreen(
        state = state,
        actions = RecordingsActions(
            onView = recordings::setView,
            onPlay = null,
            onDelete = null,
            onMore = null,
            onRetry = { connection.reconnectNow() },
            onOpenConnectionSettings = onOpenConnections,
            onSignIn = onSignIn,
        ),
        destinations = destinations,
    )
}

private fun recordingsTexts(context: android.content.Context, locale: Locale): RecordingsTexts {
    val resources: Resources = context.resources
    return RecordingsTexts(
        noChannel = resources.getString(SharedR.string.recordings_group_no_channel),
        noGenre = resources.getString(SharedR.string.recordings_group_no_genre),
        recordingNow = resources.getString(SharedR.string.recordings_recording_now),
        genre = { entry ->
            programmeCategory(entry.contentGenre)?.let { resources.getString(programmeCategoryLabelRes(it)) }
        },
        episode = { entry ->
            programmeEpisodeLine(entry.episode, entry.subtitle, resources).takeIf { it.isNotBlank() }
        },
        section = { section -> sectionLabel(section, resources, locale) },
        size = { bytes -> formatFileSize(bytes, context) },
        keepUntil = { entry, nowSec -> formatRecordingKeepUntil(entry, nowSec, resources, locale) },
    )
}

/** The date labels of the flat list, in the rows' own day style. */
private fun sectionLabel(section: DvrDateSection, resources: Resources, locale: Locale): String {
    val zone = ZoneId.systemDefault()
    val thisYear = ZonedDateTime.now(zone).year
    fun format(skeleton: String, date: ZonedDateTime) =
        DateFormat.getInstanceForSkeleton(skeleton, locale).format(Date.from(date.toInstant()))
    return when (section) {
        DvrDateSection.Today -> resources.getString(SharedR.string.recordings_date_today)
        DvrDateSection.Yesterday -> resources.getString(SharedR.string.recordings_date_yesterday)
        is DvrDateSection.Day -> section.date.atStartOfDay(zone)
            .let { format(if (it.year == thisYear) "MMMEd" else "yMMMEd", it) }
        is DvrDateSection.Month -> section.month.atDay(1).atStartOfDay(zone)
            .let { format(if (it.year == thisYear) "MMMM" else "yMMMM", it) }
        DvrDateSection.Undated -> resources.getString(SharedR.string.recordings_date_unknown)
    }
}
