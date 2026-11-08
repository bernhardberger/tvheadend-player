package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** How Recordings > Archive arranges the same recordings. */
enum class DvrArchiveGrouping {
    /** The server's folder tree. */
    FOLDERS,
    /** One folder per channel at the root. */
    CHANNELS,
    /** One folder per genre at the root. */
    GENRES,
    /** One flat list, with date sections. */
    NONE,
}

/**
 * Names the app supplies for the virtual groupings. A null name means the entry has no channel
 * (or no genre) and goes to the one group labeled [noChannel] (or [noGenre]), listed last.
 */
class DvrArchiveNaming(
    val channelName: (DvrEntry) -> String?,
    val channelNumber: (DvrEntry) -> Int?,
    val genreName: (DvrEntry) -> String?,
    val noChannel: String,
    val noGenre: String,
)

/**
 * Folders keeps the server's tree. Channels and Genres give a root with one virtual folder per
 * group (recordings newest first inside, group path equal to its name so [DvrArchiveFolder.folderAt]
 * resolves it); None gives a root holding every recording newest first.
 */
fun buildDvrArchive(
    entries: List<DvrEntry>,
    grouping: DvrArchiveGrouping,
    naming: DvrArchiveNaming,
    sort: DvrArchiveSort = DvrArchiveSort.NEWEST,
    isWatched: ((DvrEntry) -> Boolean)? = null,
): DvrArchiveFolder {
    // Hide watched: a running recording is not finished, so it is never hidden; a group left with
    // nothing is never built, and the counts and sizes of what remains follow from what is shown.
    val shown = if (isWatched == null) entries else entries.filterNot { !recordingIsRunning(it) && isWatched(it) }
    val tree = when (grouping) {
        DvrArchiveGrouping.FOLDERS -> buildDvrArchive(shown)
        // Channel number order, as the channel list reads; unnumbered channels after, by name.
        DvrArchiveGrouping.CHANNELS -> virtualGroups(
            shown, naming.channelName, naming.noChannel,
            compareBy<DvrArchiveFolder> { naming.channelNumber(it.recordings.first()) ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() },
        )
        DvrArchiveGrouping.GENRES -> virtualGroups(
            shown, naming.genreName, naming.noGenre,
            compareByDescending<DvrArchiveFolder> { it.newestRecordingStart ?: Long.MIN_VALUE }
                .thenBy { it.name.lowercase() },
        )
        DvrArchiveGrouping.NONE -> DvrArchiveFolder("", emptyList(), emptyList(), shown.newestFirst(),
            offersRecordingNow = false)
    }
    val lastGroup = when (grouping) {
        DvrArchiveGrouping.CHANNELS -> naming.noChannel
        DvrArchiveGrouping.GENRES -> naming.noGenre
        else -> null
    }
    return if (sort == DvrArchiveSort.NEWEST) tree else tree.arranged(sort, lastGroup)
}

/** The viewer's choices for Recordings > Archive: how it is grouped, ordered and filtered. */
data class DvrArchiveView(
    val grouping: DvrArchiveGrouping = DvrArchiveGrouping.FOLDERS,
    val sort: DvrArchiveSort = DvrArchiveSort.NEWEST,
    val hideWatched: Boolean = false,
)

/** How the recordings, and the group rows, of every level are ordered. */
enum class DvrArchiveSort {
    /** Recordings by start; groups as the grouping orders them (folders and genres by newest, channels by number). */
    NEWEST,
    /** By title, or by group name; locale-aware and case-insensitive. */
    NAME,
    /** By file size, or by the total size of a group. */
    LARGEST,
}

/**
 * Re-sorts every level of the tree. Ties are broken by the newest start and recordings without a
 * known title or size come last; the group named [lastGroup] (the catch-all) stays after the rest.
 */
private fun DvrArchiveFolder.arranged(sort: DvrArchiveSort, lastGroup: String?): DvrArchiveFolder {
    val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
    val newest = compareByDescending<DvrEntry> { it.start?.epochSeconds ?: Long.MIN_VALUE }
    val recordingOrder: Comparator<DvrEntry> = when (sort) {
        DvrArchiveSort.NEWEST -> newest
        DvrArchiveSort.NAME -> Comparator<DvrEntry> { a, b ->
            val left = a.title?.takeIf(String::isNotBlank)
            val right = b.title?.takeIf(String::isNotBlank)
            when {
                left == null && right == null -> 0
                left == null -> 1
                right == null -> -1
                else -> collator.compare(left, right)
            }
        }.then(newest)
        DvrArchiveSort.LARGEST -> compareByDescending<DvrEntry> { recordingSizeBytes(it) ?: -1L }.then(newest)
    }
    val groupOrder: Comparator<DvrArchiveFolder> = when (sort) {
        DvrArchiveSort.NEWEST -> compareByDescending { it.newestRecordingStart ?: Long.MIN_VALUE }
        DvrArchiveSort.NAME -> Comparator<DvrArchiveFolder> { a, b -> collator.compare(a.name, b.name) }
            .thenByDescending { it.newestRecordingStart ?: Long.MIN_VALUE }
        DvrArchiveSort.LARGEST -> compareByDescending<DvrArchiveFolder> { summarizeDvrFolder(it).totalSizeBytes }
            .thenByDescending { it.newestRecordingStart ?: Long.MIN_VALUE }
    }
    fun DvrArchiveFolder.sorted(): DvrArchiveFolder = copy(
        folders = folders.map { it.sorted() }
            .sortedWith(compareBy<DvrArchiveFolder> { lastGroup != null && it.name == lastGroup && path.isEmpty() }
                .then(groupOrder)),
        recordings = recordings.sortedWith(recordingOrder),
    )
    return sorted()
}

private fun List<DvrEntry>.newestFirst() = sortedByDescending { it.start?.epochSeconds ?: Long.MIN_VALUE }

private fun virtualGroups(
    entries: List<DvrEntry>,
    nameOf: (DvrEntry) -> String?,
    unnamed: String,
    order: Comparator<DvrArchiveFolder>,
): DvrArchiveFolder {
    val named = entries.groupBy { nameOf(it)?.takeIf(String::isNotBlank) }
    fun folder(name: String, members: List<DvrEntry>) = DvrArchiveFolder(
        name = name,
        path = listOf(name),
        folders = emptyList(),
        recordings = members.newestFirst(),
        channelId = members.firstNotNullOfOrNull { it.channelId },
    )
    val groups = named.filterKeys { it != null }.map { (name, members) -> folder(checkNotNull(name), members) }
        .sortedWith(order)
    val rest = named[null]?.let { folder(unnamed, it) }
    return DvrArchiveFolder("", emptyList(), groups + listOfNotNull(rest), emptyList())
}

/** The date label a flat list shows above a run of recordings. */
sealed interface DvrDateSection {
    data object Today : DvrDateSection
    data object Yesterday : DvrDateSection
    /** One of the last days before yesterday. */
    data class Day(val date: LocalDate) : DvrDateSection
    /** Older than that: the whole month. */
    data class Month(val month: YearMonth) : DvrDateSection
    data object Undated : DvrDateSection
}

private const val DaySectionDays = 6L

fun dvrDateSection(entry: DvrEntry, now: Instant, zone: ZoneId): DvrDateSection {
    val start = entry.start ?: return DvrDateSection.Undated
    val date = Instant.ofEpochSecond(start.epochSeconds).atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when {
        date >= today -> DvrDateSection.Today
        date == today.minusDays(1) -> DvrDateSection.Yesterday
        date >= today.minusDays(DaySectionDays) -> DvrDateSection.Day(date)
        else -> DvrDateSection.Month(YearMonth.from(date))
    }
}

/**
 * Per recording of a newest-first list: the section it opens, or null when it continues the
 * previous recording's section. The labels are display-only; they are never list rows.
 */
fun dvrDateSectionStarts(entries: List<DvrEntry>, now: Instant, zone: ZoneId): List<DvrDateSection?> {
    var previous: DvrDateSection? = null
    return entries.map { entry ->
        val section = dvrDateSection(entry, now, zone)
        section.takeIf { it != previous }.also { previous = section }
    }
}
