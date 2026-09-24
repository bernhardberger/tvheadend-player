package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ContentCategory
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

fun programmeCategory(event: EpgEventEntry): ProgrammeCategory? =
    when (event.contentGenre?.category) {
        ContentCategory.MOVIE_DRAMA -> ProgrammeCategory.FILM_DRAMA
        ContentCategory.NEWS_CURRENT_AFFAIRS -> ProgrammeCategory.NEWS
        ContentCategory.SHOW_GAME_SHOW -> ProgrammeCategory.ENTERTAINMENT
        ContentCategory.SPORTS -> ProgrammeCategory.SPORT
        ContentCategory.CHILDREN_YOUTH -> ProgrammeCategory.CHILDREN
        ContentCategory.MUSIC_BALLET_DANCE -> ProgrammeCategory.MUSIC
        ContentCategory.ARTS_CULTURE -> ProgrammeCategory.ARTS_CULTURE
        ContentCategory.SOCIAL_POLITICAL_ECONOMICS -> ProgrammeCategory.SOCIETY_POLITICS
        ContentCategory.EDUCATION_SCIENCE_FACTUAL -> ProgrammeCategory.EDUCATION_FACTUAL
        ContentCategory.LEISURE_HOBBIES -> ProgrammeCategory.LIFESTYLE_LEISURE
        ContentCategory.SPECIAL_CHARACTERISTICS,
        ContentCategory.USER_DEFINED,
        null -> null
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
