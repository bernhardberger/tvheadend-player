package at.bernhardberger.tvhplayer.touch.recordings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import at.bernhardberger.tvhplayer.touch.R
import java.time.Duration
import java.time.Instant

/**
 * Recordings > Browse on supplied state. The producer arranges [root] for [view] (the shared
 * `buildDvrArchive` and `dvrDateSectionStarts`); the screen walks the tree one level at a time,
 * keeps the picked recording and writes the rows' lines against [clock].
 */
internal data class RecordingsState(
    val view: RecordingsView,
    val root: RecordingFolder,
    val clock: RecordingClock,
    val space: RecordingSpace? = null,
)

/** The viewer's choices, one to one with the shared `DvrArchiveView`. */
internal data class RecordingsView(
    val grouping: RecordingsGrouping = RecordingsGrouping.Flat,
    val sort: RecordingsSort = RecordingsSort.Newest,
    val show: RecordingsShow = RecordingsShow.All,
)

/** The shared `DvrArchiveGrouping`, with the TV app's wording. */
internal enum class RecordingsGrouping(@param:StringRes val label: Int, @param:StringRes val detail: Int) {
    Flat(R.string.recordings_grouping_flat, R.string.recordings_grouping_flat_detail),
    Folders(R.string.recordings_grouping_folders, R.string.recordings_grouping_folders_detail),
    Channels(R.string.recordings_grouping_channels, R.string.recordings_grouping_channels_detail),
    Genres(R.string.recordings_grouping_genres, R.string.recordings_grouping_genres_detail),
}

/** The shared `DvrArchiveSort`. */
internal enum class RecordingsSort(@param:StringRes val label: Int) {
    Newest(R.string.recordings_sort_newest),
    Oldest(R.string.recordings_sort_oldest),
    Name(R.string.recordings_sort_name),
    NameDescending(R.string.recordings_sort_name_descending),
    Largest(R.string.recordings_sort_largest),
    Smallest(R.string.recordings_sort_smallest),
}

/** The shared `DvrArchiveShow`; All needs no line under it. */
internal enum class RecordingsShow(@param:StringRes val label: Int, @param:StringRes val detail: Int?) {
    All(R.string.recordings_show_all, null),
    Unwatched(R.string.recordings_show_unwatched, R.string.recordings_show_unwatched_detail),
    PartiallyWatched(R.string.recordings_show_partially_watched, R.string.recordings_show_partially_watched_detail),
    Watched(R.string.recordings_show_watched, R.string.recordings_show_watched_detail),
}

/** Free space on the server's recording storage, formatted by the producer. */
internal data class RecordingSpace(val free: String, val total: String, val usedFraction: Float)

/**
 * One level of the shared `DvrArchiveFolder`. [recordingCount] counts every recording at any
 * depth below; [size] is their total, formatted.
 */
internal data class RecordingFolder(
    val id: String,
    val name: String,
    val folders: List<RecordingFolder> = emptyList(),
    val recordings: List<RecordingItem> = emptyList(),
    val recordingCount: Int = recordings.size,
    val size: String? = null,
    val recordingNow: Boolean = false,
)

internal data class RecordingItem(
    val id: String,
    val title: String,
    val episode: String? = null,
    val channel: String? = null,
    val channelLogo: Painter? = null,
    val logoTint: Color? = null,
    val picture: Painter? = null,
    /** The date label a list starts here (`dvrDateSectionStarts`), or null when it continues. */
    val section: RecordingSection? = null,
    val start: Instant,
    val length: Duration,
    val state: RecordingProgress = RecordingProgress.Unwatched,
    val details: RecordingDetails = RecordingDetails(),
)

/** A date label: a day's ("Today", "Wed, Nov 11") or a month's ("October"). */
internal data class RecordingSection(val label: String, val dates: RowDates)

internal sealed interface RecordingProgress {
    /** Still recording: [progress] is how much of the planned time has been recorded. */
    data class Recording(val until: Instant, val progress: Float) : RecordingProgress
    data object Unwatched : RecordingProgress
    /** The viewer's resume point. */
    data class PartlyWatched(val left: Duration, val progress: Float) : RecordingProgress
    data object Watched : RecordingProgress
}

/** The facts of section 9's details view; a missing one is left out. */
internal data class RecordingDetails(
    val synopsis: String? = null,
    val genre: String? = null,
    val recorded: String? = null,
    val size: String? = null,
    val picture: String? = null,
    val audio: String? = null,
    val subtitles: String? = null,
    val errors: String? = null,
    val retention: String? = null,
)

internal class RecordingsActions(
    val onView: (RecordingsView) -> Unit,
    val onPlay: (id: String, fromBeginning: Boolean) -> Unit,
    val onDelete: (String) -> Unit,
    val onMore: (String) -> Unit,
)
