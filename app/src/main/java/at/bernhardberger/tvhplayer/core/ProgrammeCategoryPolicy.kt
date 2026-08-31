package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import kotlinx.serialization.Serializable

@Serializable
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
    if (contentType !in 0L..0xffL) return null
    val major = contentType ushr 4
    return when (major) {
        0x1L -> ProgrammeCategory.FILM_DRAMA
        0x2L -> ProgrammeCategory.NEWS
        0x3L -> ProgrammeCategory.ENTERTAINMENT
        0x4L -> ProgrammeCategory.SPORT
        0x5L -> ProgrammeCategory.CHILDREN
        0x6L -> ProgrammeCategory.MUSIC
        0x7L -> ProgrammeCategory.ARTS_CULTURE
        0x8L -> ProgrammeCategory.SOCIETY_POLITICS
        0x9L -> ProgrammeCategory.EDUCATION_FACTUAL
        0xaL -> ProgrammeCategory.LIFESTYLE_LEISURE
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
