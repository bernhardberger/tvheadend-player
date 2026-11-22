package at.bernhardberger.tvhplayer.touch.recordings

import at.bernhardberger.tvhplayer.touch.ui.ArtworkScreenShown
import at.bernhardberger.tvhplayer.touch.ui.DvrControls
import at.bernhardberger.tvhplayer.touch.ui.LocalArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.traceArtworkMapping
import android.content.res.Resources
import android.icu.text.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.DvrConfigurationsState
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.core.DvrDateSection
import at.bernhardberger.tvhplayer.core.programmeCategory
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatFileSize
import at.bernhardberger.tvhplayer.ui.common.programmeAboutFacts
import at.bernhardberger.tvhplayer.ui.common.recordingMetadataFacts
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
 * The Recordings destination of the app. Delete goes through [dvr]; viewing opens the shared player.
 */
@Composable
internal fun RecordingsDestination(
    recordings: RecordingsViewModel,
    connection: AppConnectionViewModel,
    destinations: List<Destination>,
    dvr: DvrControls,
    onOpenConnections: () -> Unit,
    onSignIn: () -> Unit,
    onPlay: ((String, Boolean) -> Unit)?,
    playingRecordingId: DvrEntryId?,
) {
    ArtworkScreenShown("Recordings")
    val presentation by connection.uiState.collectAsStateWithLifecycle()
    val observation by recordings.observation.collectAsStateWithLifecycle()
    val view = recordings.view.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val nowSec = rememberCurrentEpochSeconds()()
    val texts = remember(context, locale, observation.dvrConfigurationsState) {
        recordingsTexts(context, locale, observation)
    }
    val artwork = LocalArtworkModels.current
    val current = observation.currentSession
    // Keep the whole offline observation for profile-bound saved artwork.
    // The DVR entries carry saved positions; playback admission also uses the progress capability.
    val state = remember(
        observation.takeIf { current == null }, current, observation.dvrState, observation.dvrDiskSpaceState,
        observation.channelCatalogForDisplay, observation.recordingProgressCapability,
        (observation.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite,
        view, presentation.connection, presentation.degraded, nowSec, texts, artwork, playingRecordingId,
    ) {
        val clock = RecordingClock(Instant.ofEpochSecond(nowSec), ZoneId.systemDefault(), locale) {
            formatClock(it, locale)
        }
        if (view == null) {
            return@remember RecordingsState(
                DvrArchiveView(), RecordingFolder(ROOT_ID, ""), clock, availability = RecordingsAvailability.Loading,
            )
        }
        traceArtworkMapping(context, "Recordings") {
            recordingsState(
                observation, view, presentation.connection, presentation.degraded, clock, texts, artwork,
                playingRecordingId,
            )
        }
    }
    RecordingsScreen(
        state = state,
        actions = RecordingsActions(
            onView = recordings::setView,
            onPlay = onPlay,
            onDelete = dvr::delete,
            onStop = dvr::stopRecording,
            onMore = null,
            onRetry = { connection.reconnectNow() },
            onOpenConnectionSettings = onOpenConnections,
            onSignIn = onSignIn,
        ),
        destinations = destinations,
    )
}

internal fun recordingsTexts(
    context: android.content.Context,
    locale: Locale,
    observation: SessionObservation,
): RecordingsTexts {
    val resources: Resources = context.resources
    val configs = (observation.dvrConfigurationsState as? DvrConfigurationsState.Current)?.configurations.orEmpty()
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
        facts = { entry, nowSec ->
            programmeAboutFacts(null, entry, resources, locale) +
                recordingMetadataFacts(entry, configs, context, locale, nowSec)
        },
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
