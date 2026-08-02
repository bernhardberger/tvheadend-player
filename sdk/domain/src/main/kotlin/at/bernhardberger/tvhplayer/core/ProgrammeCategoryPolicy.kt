package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry

enum class ProgrammeCategory {
    ALL,
    FILM_DRAMA,
    NEWS,
    ENTERTAINMENT,
    SPORT,
    CHILDREN,
    MUSIC,
    ARTS_CULTURE,
    SOCIETY_POLITICS,
    EDUCATION_FACTUAL,
    LIFESTYLE_LEISURE,
}

fun programmeCategory(event: EpgEventEntry): ProgrammeCategory? {
    val contentType = event.contentType ?: return null
    if (contentType !in 0..0xff) return null
    val major = contentType ushr 4
    return when (major) {
        0x1 -> ProgrammeCategory.FILM_DRAMA
        0x2 -> ProgrammeCategory.NEWS
        0x3 -> ProgrammeCategory.ENTERTAINMENT
        0x4 -> ProgrammeCategory.SPORT
        0x5 -> ProgrammeCategory.CHILDREN
        0x6 -> ProgrammeCategory.MUSIC
        0x7 -> ProgrammeCategory.ARTS_CULTURE
        0x8 -> ProgrammeCategory.SOCIETY_POLITICS
        0x9 -> ProgrammeCategory.EDUCATION_FACTUAL
        0xa -> ProgrammeCategory.LIFESTYLE_LEISURE
        else -> null
    }
}

fun EpgEventEntry.matchesProgrammeCategory(category: ProgrammeCategory): Boolean =
    category == ProgrammeCategory.ALL || programmeCategory(this) == category

fun availableProgrammeCategories(events: Iterable<EpgEventEntry>): List<ProgrammeCategory> {
    val available = events.mapNotNull(::programmeCategory).toSet()
    return buildList {
        add(ProgrammeCategory.ALL)
        ProgrammeCategory.entries.drop(1).filterTo(this) { it in available }
    }
}
