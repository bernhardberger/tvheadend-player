package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
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
): DvrArchiveFolder = when (grouping) {
    DvrArchiveGrouping.FOLDERS -> buildDvrArchive(entries)
    // Channel number order, as the channel list reads; unnumbered channels after, by name.
    DvrArchiveGrouping.CHANNELS -> virtualGroups(
        entries, naming.channelName, naming.noChannel,
        compareBy<DvrArchiveFolder> { naming.channelNumber(it.recordings.first()) ?: Int.MAX_VALUE }
            .thenBy { it.name.lowercase() },
    )
    DvrArchiveGrouping.GENRES -> virtualGroups(
        entries, naming.genreName, naming.noGenre,
        compareByDescending<DvrArchiveFolder> { it.newestRecordingStart ?: Long.MIN_VALUE }
            .thenBy { it.name.lowercase() },
    )
    DvrArchiveGrouping.NONE -> DvrArchiveFolder("", emptyList(), emptyList(), entries.newestFirst(),
        offersRecordingNow = false)
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
