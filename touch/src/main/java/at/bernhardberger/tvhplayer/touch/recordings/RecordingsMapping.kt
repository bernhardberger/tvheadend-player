package at.bernhardberger.tvhplayer.touch.recordings

import at.bernhardberger.tvhplayer.touch.ui.recordingRemoveControl
import at.bernhardberger.tvhplayer.core.recordingArtwork
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveNaming
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.core.DvrDateSection
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.RecordingWatchState
import at.bernhardberger.tvhplayer.core.allRecordings
import at.bernhardberger.tvhplayer.core.browseAppError
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.core.deleteRemovesEntryOnly
import at.bernhardberger.tvhplayer.core.dvrDateSectionStarts
import at.bernhardberger.tvhplayer.core.offeredResumePosition
import at.bernhardberger.tvhplayer.core.partitionDvrLibrary
import at.bernhardberger.tvhplayer.core.recordingContextActions
import at.bernhardberger.tvhplayer.core.recordingElapsedProgress
import at.bernhardberger.tvhplayer.core.recordingIsRunning
import at.bernhardberger.tvhplayer.core.recordingLengthSeconds
import at.bernhardberger.tvhplayer.core.recordingNowEntries
import at.bernhardberger.tvhplayer.core.recordingPaddedStop
import at.bernhardberger.tvhplayer.core.recordingSizeBytes
import at.bernhardberger.tvhplayer.core.recordingSpaceIndicator
import at.bernhardberger.tvhplayer.core.recordingTimeLeft
import at.bernhardberger.tvhplayer.core.recordingWatchState
import at.bernhardberger.tvhplayer.core.summarizeDvrFolder
import java.time.Duration
import java.time.Instant
import kotlin.time.toJavaDuration

internal fun recordingPlayActions(
    entry: DvrEntry,
    admission: RecordingPlaybackAdmission?,
): List<RecordingPlayAction> {
    // A Remove entry action stands alone; all other play actions follow shared admission.
    if (deleteRemovesEntryOnly(entry)) return emptyList()
    return recordingContextActions(entry, admission, canModifyRecordings = false).mapNotNull { action ->
        when (action) {
            BrowseContextAction.PLAY, BrowseContextAction.WATCH -> RecordingPlayAction.Watch
            BrowseContextAction.RESUME -> offeredResumePosition(admission)?.let { RecordingPlayAction.Resume(it) }
            BrowseContextAction.START_OVER -> RecordingPlayAction.StartOver
            else -> null
        }
    }
}

/**
 * Recordings > Browse from the session, as the TV's archive builds it: the finished recordings and
 * the running ones, grouped, sorted and filtered by the shared `buildDvrArchive` for [view].
 *
 * In this order: an unreadable password asks for sign-in; with no list from the server yet a
 * failure says so in the catalog's words and anything else waits; a list while the connection is
 * degraded is the saved one; otherwise the current one. The list and its Show filter use the
 * same admitted resume position.
 */
@OptIn(ExperimentalTvheadendApi::class)
@Suppress("LongParameterList")
internal fun recordingsState(
    observation: SessionObservation,
    view: DvrArchiveView,
    connection: ConnectionUiState,
    degraded: Boolean,
    clock: RecordingClock,
    texts: RecordingsTexts,
    artwork: ArtworkModels? = null,
    playingRecordingId: DvrEntryId? = null,
): RecordingsState {
    val empty = RecordingFolder(ROOT_ID, "")
    val space = observation.dvrDiskSpaceState.recordingSpaceIndicator()?.let {
        RecordingSpace(texts.size(it.freeBytes), texts.size(it.totalBytes), it.usedFraction)
    }
    val entries = observation.dvrSnapshotForDisplay?.entries
    val availability = when {
        connection == ConnectionUiState.CredentialUnavailable -> RecordingsAvailability.SignInNeeded
        entries == null -> connection.browseAppError()
            ?.let(RecordingsAvailability::Failed) ?: RecordingsAvailability.Loading
        degraded -> RecordingsAvailability.Offline
        else -> RecordingsAvailability.Current
    }
    val shown = availability == RecordingsAvailability.Current || availability == RecordingsAvailability.Offline
    if (entries == null || !shown) {
        return RecordingsState(view, empty, clock, space, availability)
    }
    val nowSec = clock.now.epochSecond
    val catalog = observation.channelCatalogForDisplay?.channels.orEmpty()
    val channelNames = catalog.associate { it.id to it.name }
    val icons = catalog.associate { it.id to it.icon }
    val naming = DvrArchiveNaming(
        channelName = { entry -> entry.channelId?.let(channelNames::get) ?: entry.channelName },
        genreName = texts.genre,
        noChannel = texts.noChannel,
        noGenre = texts.noGenre,
    )
    val archive = partitionDvrLibrary(entries).archive + entries.filter(::recordingIsRunning)
    val folder = buildDvrArchive(archive.distinctBy { it.id }, view.grouping, naming, view.sort, view.show) {
        recordingWatchState(it, offeredResumePosition(observation.recordingPlaybackAdmission(it)), nowSec)
    }
    val rows = RowMapper(naming, texts, clock, observation, availability, playingRecordingId) { entry ->
        val logo = entry.channelId?.let(icons::get)
        RowArtwork(artwork?.logo(logo, observation), artwork?.picture(recordingArtwork(entry), observation))
    }
    val recordingNow = folder.recordingNowEntries(view).takeIf { it.isNotEmpty() }?.let { running ->
        RecordingFolder(
            RECORDING_NOW_ID, texts.recordingNow, recordings = running.map { rows.item(it, null) },
            recordingNow = true,
        )
    }
    val root = rows.folder(folder, sections = datesLabelled(view)).let {
        if (recordingNow == null) it else it.copy(folders = listOf(recordingNow) + it.folders)
    }
    return RecordingsState(view, root, clock, space, availability)
}

/** As on the TV: date labels only on the flat list in date order. */
private fun datesLabelled(view: DvrArchiveView): Boolean =
    view.grouping == DvrArchiveGrouping.FLAT &&
        (view.sort == DvrArchiveSort.NEWEST || view.sort == DvrArchiveSort.OLDEST)

/** A recording row's image models: its channel's logo and its own picture. */
private class RowArtwork(val logo: Any?, val picture: Any?)

private class RowMapper(
    val naming: DvrArchiveNaming,
    val texts: RecordingsTexts,
    val clock: RecordingClock,
    val observation: SessionObservation,
    val availability: RecordingsAvailability,
    val playingRecordingId: DvrEntryId?,
    val artwork: (DvrEntry) -> RowArtwork,
) {
    private val canOfferPlayback = availability == RecordingsAvailability.Current && observation.currentSession != null

    fun folder(folder: DvrArchiveFolder, sections: Boolean): RecordingFolder {
        val starts = if (sections) {
            dvrDateSectionStarts(folder.recordings, clock.now, clock.zone)
        } else {
            List(folder.recordings.size) { null }
        }
        return RecordingFolder(
            id = folder.path.lastOrNull() ?: ROOT_ID,
            name = folder.name,
            folders = folder.folders.map { folder(it, sections = false) },
            recordings = folder.recordings.zip(starts) { entry, start -> item(entry, start) },
            recordingCount = folder.allRecordings().size,
            size = summarizeDvrFolder(folder).totalSizeBytes.takeIf { it > 0 }?.let(texts.size),
        )
    }

    @OptIn(ExperimentalTvheadendApi::class)
    fun item(entry: DvrEntry, section: DvrDateSection?): RecordingItem {
        val art = artwork(entry)
        val admission = observation.recordingPlaybackAdmission(entry)
        return recordingItem(entry, admission, naming.channelName(entry), texts, clock.now.epochSecond).copy(
            channelLogoModel = art.logo,
            pictureModel = art.picture,
            section = section?.let { RecordingSection(texts.section(it), it.rowDates) },
            remove = observation.recordingRemoveControl(entry),
            play = if (canOfferPlayback) recordingPlayActions(entry, admission) else emptyList(),
            playing = entry.id == playingRecordingId,
        )
    }
}

/** One entry's content, without archive-only section, artwork, or actions. */
internal fun recordingItem(
    entry: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    channel: String?,
    texts: RecordingsTexts,
    nowSec: Long,
): RecordingItem = RecordingItem(
    id = entry.id.value.toString(),
    title = entry.title.orEmpty(),
    episode = texts.episode(entry),
    channel = channel?.takeIf { it.isNotBlank() },
    start = entry.start?.let { Instant.ofEpochSecond(it.epochSeconds) },
    length = recordingLengthSeconds(entry, nowSec)?.takeIf { it > 0 }?.let(Duration::ofSeconds),
    state = recordingProgress(entry, admission, nowSec),
    details = RecordingDetails(
        synopsis = entry.description?.takeIf { it.isNotBlank() } ?: entry.summary?.takeIf { it.isNotBlank() },
        genre = texts.genre(entry),
        size = recordingSizeBytes(entry)?.let(texts.size),
        facts = texts.facts(entry, nowSec),
    ),
    remove = null,
)

/**
 * Saved watch state and time left follow
 * `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/recordings/RecordingsContent.kt:489-505`.
 * Touch has no active viewer position to overlay.
 */
private fun recordingProgress(
    entry: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    nowSec: Long,
): RecordingProgress {
    val stop = recordingPaddedStop(entry)
    if (recordingIsRunning(entry) && stop != null) {
        return RecordingProgress.Recording(
            until = Instant.ofEpochSecond(stop.epochSeconds),
            progress = recordingElapsedProgress(entry, nowSec) ?: 0f,
        )
    }
    return when (val watched = recordingWatchState(entry, offeredResumePosition(admission), nowSec)) {
        RecordingWatchState.Watched -> RecordingProgress.Watched
        RecordingWatchState.Unwatched -> RecordingProgress.Unwatched
        is RecordingWatchState.PartlyWatched -> {
            val left = recordingTimeLeft(entry, admission)
            val fraction = watched.fraction
            if (left != null && fraction != null) {
                RecordingProgress.PartlyWatched(left.toJavaDuration(), fraction.toFloat())
            } else RecordingProgress.Unwatched
        }
    }
}

private val DvrDateSection.rowDates: RowDates
    get() = when (this) {
        DvrDateSection.Today, DvrDateSection.Yesterday, is DvrDateSection.Day -> RowDates.DayLabel
        is DvrDateSection.Month -> RowDates.MonthLabel
        DvrDateSection.Undated -> RowDates.None
    }

private const val ROOT_ID = "root"
private const val RECORDING_NOW_ID = "recording-now"
