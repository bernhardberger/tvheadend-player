package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** How Recordings > Archive arranges the same recordings. */
enum class DvrArchiveGrouping {
    /** One flat list, with date sections under the newest-first order. */
    FLAT,
    /** The server's folder tree. */
    FOLDERS,
    /** One folder per channel at the root. */
    CHANNELS,
    /** One folder per genre at the root. */
    GENRES,
}

/**
 * Channels group by the trimmed displayed name; genres group by category. Missing names/categories
 * go to the group labeled [noChannel]/[noGenre], listed last.
 */
class DvrArchiveNaming(
    val channelName: (DvrEntry) -> String?,
    val genreName: (DvrEntry) -> String?,
    val noChannel: String,
    val noGenre: String,
)

/**
 * Folders keeps the server's tree. Channels and Genres give a root with one virtual folder per
 * group (recordings newest first inside, safe identity components in paths); Flat gives a
 * root holding every recording newest first.
 */
fun buildDvrArchive(
    entries: List<DvrEntry>,
    grouping: DvrArchiveGrouping,
    naming: DvrArchiveNaming,
    sort: DvrArchiveSort = DvrArchiveSort.NEWEST,
    show: DvrArchiveShow = DvrArchiveShow.ALL,
    watchState: (DvrEntry) -> RecordingWatchState = { recordingWatchState(it, null, 0L) },
): DvrArchiveFolder {
    // The Show filter: a running recording is not finished, so it is never hidden; a group left with
    // nothing is never built, and the counts and sizes of what remains follow from what is shown.
    val shown = if (show == DvrArchiveShow.ALL) entries else
        entries.filter { recordingIsRunning(it) || show.shows(watchState(it)) }
    val tree = when (grouping) {
        DvrArchiveGrouping.FOLDERS -> buildDvrArchive(shown)
        // Channel folders follow their newest recording, like folders and genres; the catch-all stays last.
        DvrArchiveGrouping.CHANNELS -> virtualGroups(
            shown, naming.channelName, naming.noChannel,
            compareByDescending<DvrArchiveFolder> { it.newestRecordingStart ?: Long.MIN_VALUE }
                .thenBy { it.name.lowercase() },
            channelLogo = true,
        )
        DvrArchiveGrouping.GENRES -> virtualGroups(
            shown, naming.genreName, naming.noGenre,
            compareByDescending<DvrArchiveFolder> { it.newestRecordingStart ?: Long.MIN_VALUE }
                .thenBy { it.name.lowercase() },
            genreCategory = true,
        )
        DvrArchiveGrouping.FLAT -> DvrArchiveFolder("", emptyList(), emptyList(), shown.newestFirst())
    }
    val lastGroup = when (grouping) {
        DvrArchiveGrouping.CHANNELS -> "channel:unassigned"
        DvrArchiveGrouping.GENRES -> "genre:unassigned"
        else -> null
    }
    return if (sort == DvrArchiveSort.NEWEST) tree else tree.arranged(sort, lastGroup)
}

/**
 * The viewer's choices for Recordings > Browse: how it is grouped, ordered and filtered, and whether
 * the Folders root opens with the Recording now folder.
 */
data class DvrArchiveView(
    val grouping: DvrArchiveGrouping = DvrArchiveGrouping.FLAT,
    val sort: DvrArchiveSort = DvrArchiveSort.NEWEST,
    val show: DvrArchiveShow = DvrArchiveShow.ALL,
    val recordingNowFolder: Boolean = true,
)

/** Which recordings the archive lists, by how much of each has been watched. */
enum class DvrArchiveShow {
    ALL,
    /** Not played to the end: never played, or played partway. */
    UNWATCHED,
    /** Played partway, so there is a place to continue from. */
    PARTIALLY_WATCHED,
    /** Played to the end. */
    WATCHED,
}

fun DvrArchiveShow.shows(state: RecordingWatchState): Boolean = when (this) {
    DvrArchiveShow.ALL -> true
    DvrArchiveShow.UNWATCHED -> state !is RecordingWatchState.Watched
    DvrArchiveShow.PARTIALLY_WATCHED -> state is RecordingWatchState.PartlyWatched
    DvrArchiveShow.WATCHED -> state is RecordingWatchState.Watched
}

/** How the recordings, and the group rows, of every level are ordered. */
enum class DvrArchiveSort {
    /** Recordings by start and groups by their newest recording; catch-all groups stay last. */
    NEWEST,
    /** Recordings by start and groups by their oldest recording, oldest first. */
    OLDEST,
    /** By title, or by group name, A to Z; locale-aware and case-insensitive. */
    NAME,
    /** By title, or by group name, Z to A. */
    NAME_DESCENDING,
    /** By file size, or by the total size of a group, largest first. */
    LARGEST,
    /** By file size, or by the total size of a group, smallest first. */
    SMALLEST,
}

/**
 * Re-sorts every level of the tree. Ties are broken by the newest start and recordings without a
 * known title or size come last; the group identified by [lastGroup] (the catch-all) stays last.
 */
private fun DvrArchiveFolder.arranged(sort: DvrArchiveSort, lastGroup: String?): DvrArchiveFolder {
    val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
    val newest = compareByDescending<DvrEntry> { it.start?.epochSeconds ?: Long.MIN_VALUE }
    val recordingOrder = archiveRecordingOrder(sort, collator, newest)
    val groupOrder = archiveGroupOrder(sort, collator)
    fun DvrArchiveFolder.sorted(): DvrArchiveFolder = copy(
        folders = folders.map { it.sorted() }
            .sortedWith(compareBy<DvrArchiveFolder> { lastGroup != null && it.path == listOf(lastGroup) }
                .then(groupOrder)),
        recordings = recordings.sortedWith(recordingOrder),
    )
    return sorted()
}

/** Titles in collator order, a missing or blank title after every named one in both directions. */
private class TitleOrder(private val collator: Collator, private val descending: Boolean) : Comparator<DvrEntry> {
    override fun compare(a: DvrEntry, b: DvrEntry): Int {
        val left = a.title?.takeIf(String::isNotBlank)
        val right = b.title?.takeIf(String::isNotBlank)
        return when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            descending -> collator.compare(right, left)
            else -> collator.compare(left, right)
        }
    }
}

private fun archiveRecordingOrder(
    sort: DvrArchiveSort,
    collator: Collator,
    newest: Comparator<DvrEntry>,
): Comparator<DvrEntry> {
    return when (sort) {
        DvrArchiveSort.NEWEST -> newest
        // Without a known start, title or size a recording comes last in both directions.
        DvrArchiveSort.OLDEST -> compareBy<DvrEntry> { it.start?.epochSeconds ?: Long.MAX_VALUE }
        DvrArchiveSort.NAME -> TitleOrder(collator, descending = false).then(newest)
        DvrArchiveSort.NAME_DESCENDING -> TitleOrder(collator, descending = true).then(newest)
        DvrArchiveSort.LARGEST -> compareByDescending<DvrEntry> { recordingSizeBytes(it) ?: -1L }.then(newest)
        DvrArchiveSort.SMALLEST -> compareBy<DvrEntry> { recordingSizeBytes(it) ?: Long.MAX_VALUE }.then(newest)
    }
}

private fun archiveGroupOrder(sort: DvrArchiveSort, collator: Collator): Comparator<DvrArchiveFolder> {
    val newestFirst = compareByDescending<DvrArchiveFolder> { it.newestRecordingStart ?: Long.MIN_VALUE }
    return when (sort) {
        DvrArchiveSort.NEWEST -> newestFirst
        DvrArchiveSort.OLDEST ->
            compareBy<DvrArchiveFolder> { summarizeDvrFolder(it).oldestStart ?: Long.MAX_VALUE }.then(newestFirst)
        DvrArchiveSort.NAME ->
            Comparator<DvrArchiveFolder> { a, b -> collator.compare(a.name, b.name) }.then(newestFirst)
        DvrArchiveSort.NAME_DESCENDING ->
            Comparator<DvrArchiveFolder> { a, b -> collator.compare(b.name, a.name) }.then(newestFirst)
        DvrArchiveSort.LARGEST -> compareByDescending<DvrArchiveFolder> { summarizeDvrFolder(it).totalSizeBytes }
            .then(newestFirst)
        DvrArchiveSort.SMALLEST -> compareBy<DvrArchiveFolder> { summarizeDvrFolder(it).totalSizeBytes }
            .then(newestFirst)
    }
}

private fun List<DvrEntry>.newestFirst() = sortedByDescending { it.start?.epochSeconds ?: Long.MIN_VALUE }

private fun virtualGroups(
    entries: List<DvrEntry>,
    nameOf: (DvrEntry) -> String?,
    unnamed: String,
    order: Comparator<DvrArchiveFolder>,
    channelLogo: Boolean = false,
    genreCategory: Boolean = false,
): DvrArchiveFolder {
    val prefix = if (channelLogo) "channel" else "genre"
    val unassigned = "$prefix:unassigned"
    val named = entries.groupBy { entry ->
        virtualGroupIdentity(entry, nameOf, channelLogo) ?: unassigned
    }
    // Only a named channel group carries its channel (the row draws its logo); every other group keeps the folder icon.
    fun folder(identity: String, members: List<DvrEntry>): DvrArchiveFolder {
        val recordings = members.newestFirst()
        return DvrArchiveFolder(
            name = if (identity == unassigned) unnamed
                else members.firstNotNullOfOrNull { nameOf(it)?.takeIf(String::isNotBlank) }
                    ?.let { if (channelLogo) it.trim() else it } ?: unnamed,
            path = listOf(identity),
            folders = emptyList(),
            recordings = recordings,
            channelId = if (identity != unassigned && channelLogo)
                recordings.firstNotNullOfOrNull { it.channelId } else null,
            genreCategory = if (identity != unassigned && genreCategory)
                programmeCategory(members.first().contentGenre) else null,
        )
    }
    val groups = named.filterKeys { it != unassigned }.map { (identity, members) ->
        folder(identity, members)
    }
        .sortedWith(order)
    val rest = named[unassigned]?.let { folder(unassigned, it) }
    return DvrArchiveFolder("", emptyList(), groups + listOfNotNull(rest), emptyList())
}

private fun virtualGroupIdentity(entry: DvrEntry, nameOf: (DvrEntry) -> String?, channelLogo: Boolean): String? =
    if (channelLogo) channelGroupIdentity(nameOf(entry))
    else programmeCategory(entry.contentGenre)?.name?.let { "genre:$it" }

/** Escape percent first so a literal escape sequence cannot collide with a slash-containing name. */
private fun channelGroupIdentity(name: String?): String? = name?.trim()?.takeIf(String::isNotEmpty)?.let {
    "channel:name:${it.replace("%", "%25").replace("/", "%2F")}"
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

private const val DAY_SECTION_DAYS = 6L

fun dvrDateSection(entry: DvrEntry, now: Instant, zone: ZoneId): DvrDateSection {
    val start = entry.start ?: return DvrDateSection.Undated
    val date = Instant.ofEpochSecond(start.epochSeconds).atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when {
        date >= today -> DvrDateSection.Today
        date == today.minusDays(1) -> DvrDateSection.Yesterday
        date >= today.minusDays(DAY_SECTION_DAYS) -> DvrDateSection.Day(date)
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
