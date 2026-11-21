package at.bernhardberger.tvhplayer.touch.recordings

import at.bernhardberger.tvhplayer.touch.ui.RecordingRemoveControl
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.core.DvrDateSection
import at.bernhardberger.tvhplayer.touch.R
import java.time.Duration
import java.time.Instant

/**
 * Recordings > Browse on supplied state. The producer arranges [root] for [view] (the shared
 * `buildDvrArchive` and `dvrDateSectionStarts`); the screen walks the tree one level at a time,
 * keeps the picked recording and writes the rows' lines against [clock].
 */
internal data class RecordingsState(
    val view: DvrArchiveView,
    val root: RecordingFolder,
    val clock: RecordingClock,
    val space: RecordingSpace? = null,
    val availability: RecordingsAvailability = RecordingsAvailability.Current,
)

/** Whether [RecordingsState.root] is the server's current list, a saved one, or stands in for nothing. */
internal sealed interface RecordingsAvailability {
    data object Current : RecordingsAvailability
    /** The first list is on its way; nothing to show or filter yet. */
    data object Loading : RecordingsAvailability
    /** The server can't be reached; [RecordingsState.root] is the last list it sent. */
    data object Offline : RecordingsAvailability
    /** The saved password can't be read. */
    data object SignInNeeded : RecordingsAvailability
    /** A failure with nothing saved to show, in the catalog's words. */
    data class Failed(val error: AppError) : RecordingsAvailability
}

/** The TV app's wording for the shared `DvrArchiveGrouping`. */
internal val DvrArchiveGrouping.label: Int
    @StringRes get() = when (this) {
        DvrArchiveGrouping.FLAT -> R.string.recordings_grouping_flat
        DvrArchiveGrouping.FOLDERS -> R.string.recordings_grouping_folders
        DvrArchiveGrouping.CHANNELS -> R.string.recordings_grouping_channels
        DvrArchiveGrouping.GENRES -> R.string.recordings_grouping_genres
    }

internal val DvrArchiveGrouping.detail: Int
    @StringRes get() = when (this) {
        DvrArchiveGrouping.FLAT -> R.string.recordings_grouping_flat_detail
        DvrArchiveGrouping.FOLDERS -> R.string.recordings_grouping_folders_detail
        DvrArchiveGrouping.CHANNELS -> R.string.recordings_grouping_channels_detail
        DvrArchiveGrouping.GENRES -> R.string.recordings_grouping_genres_detail
    }

internal val DvrArchiveSort.label: Int
    @StringRes get() = when (this) {
        DvrArchiveSort.NEWEST -> R.string.recordings_sort_newest
        DvrArchiveSort.OLDEST -> R.string.recordings_sort_oldest
        DvrArchiveSort.NAME -> R.string.recordings_sort_name
        DvrArchiveSort.NAME_DESCENDING -> R.string.recordings_sort_name_descending
        DvrArchiveSort.LARGEST -> R.string.recordings_sort_largest
        DvrArchiveSort.SMALLEST -> R.string.recordings_sort_smallest
    }

internal val DvrArchiveShow.label: Int
    @StringRes get() = when (this) {
        DvrArchiveShow.ALL -> R.string.recordings_show_all
        DvrArchiveShow.UNWATCHED -> R.string.recordings_show_unwatched
        DvrArchiveShow.PARTIALLY_WATCHED -> R.string.recordings_show_partially_watched
        DvrArchiveShow.WATCHED -> R.string.recordings_show_watched
    }

/** All needs no line under it. */
internal val DvrArchiveShow.detail: Int?
    @StringRes get() = when (this) {
        DvrArchiveShow.ALL -> null
        DvrArchiveShow.UNWATCHED -> R.string.recordings_show_unwatched_detail
        DvrArchiveShow.PARTIALLY_WATCHED -> R.string.recordings_show_partially_watched_detail
        DvrArchiveShow.WATCHED -> R.string.recordings_show_watched_detail
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
) {
    /**
     * Whether the rows keep their leading picture slot (section 13): a folder's tile, or one recording with a
     * picture or a channel logo, keeps it for every row so the titles stay on one line; else rows start with
     * the title.
     */
    val showsThumbnails: Boolean
        get() = folders.isNotEmpty() || recordings.any { it.hasPicture || it.hasLogo }
}

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
    /** Null for an entry the server sent without a start; its line and page leave the time out. */
    val start: Instant?,
    val length: Duration?,
    val state: RecordingProgress = RecordingProgress.Unwatched,
    val details: RecordingDetails = RecordingDetails(),
    /** The server image's model for the touch-level image composable; the fixture painter wins. */
    val channelLogoModel: Any? = null,
    /** The server image's model for the touch-level image composable; the fixture painter wins. */
    val pictureModel: Any? = null,
    /** How it is removed; null offers no remove control. */
    val remove: RecordingRemoveControl? = RecordingRemoveControl.Delete,
) {
    val hasPicture: Boolean get() = picture != null || pictureModel != null
    val hasLogo: Boolean get() = channelLogo != null || channelLogoModel != null
}

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

/** A null action has nothing behind it yet: its control is left out. */
internal class RecordingsActions(
    val onView: (DvrArchiveView) -> Unit,
    val onPlay: ((id: String, fromBeginning: Boolean) -> Unit)?,
    val onDelete: ((String) -> Unit)?,
    val onMore: ((String) -> Unit)?,
    val onRetry: () -> Unit = {},
    val onOpenConnectionSettings: () -> Unit = {},
    val onSignIn: (() -> Unit)? = null,
)

/**
 * The words the mapping needs from resources, so the mapping itself stays plain Kotlin: the
 * shared catalog's names for missing channels and genres, the genre, episode, size and
 * keep-until texts of the shared formatters, and the date labels.
 */
internal class RecordingsTexts(
    val noChannel: String,
    val noGenre: String,
    val recordingNow: String,
    val genre: (DvrEntry) -> String?,
    val episode: (DvrEntry) -> String?,
    val section: (DvrDateSection) -> String,
    val size: (Long) -> String,
    val keepUntil: (DvrEntry, nowSec: Long) -> String?,
)
