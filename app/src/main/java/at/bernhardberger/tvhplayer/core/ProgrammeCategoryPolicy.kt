package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry

enum class ProgrammeCategory(val routeValue: String) {
    ALL("all"),
    FILM_DRAMA("film-drama"),
    NEWS("news"),
    ENTERTAINMENT("entertainment"),
    SPORT("sport"),
    CHILDREN("children"),
    MUSIC("music"),
    ARTS_CULTURE("arts-culture"),
    SOCIETY_POLITICS("society-politics"),
    EDUCATION_FACTUAL("education-factual"),
    LIFESTYLE_LEISURE("lifestyle-leisure"),
    ;

    companion object {
        fun fromRoute(value: String?): ProgrammeCategory = entries.firstOrNull {
            it.routeValue == value
        } ?: ALL
    }
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
