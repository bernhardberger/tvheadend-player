package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.core.ProgrammeCategory

internal fun programmeCategoryRouteValue(category: ProgrammeCategory): String = when (category) {
    ProgrammeCategory.ALL -> "all"
    ProgrammeCategory.FILM_DRAMA -> "film-drama"
    ProgrammeCategory.NEWS -> "news"
    ProgrammeCategory.ENTERTAINMENT -> "entertainment"
    ProgrammeCategory.SPORT -> "sport"
    ProgrammeCategory.CHILDREN -> "children"
    ProgrammeCategory.MUSIC -> "music"
    ProgrammeCategory.ARTS_CULTURE -> "arts-culture"
    ProgrammeCategory.SOCIETY_POLITICS -> "society-politics"
    ProgrammeCategory.EDUCATION_FACTUAL -> "education-factual"
    ProgrammeCategory.LIFESTYLE_LEISURE -> "lifestyle-leisure"
}

internal fun programmeCategoryFromRoute(value: String?): ProgrammeCategory =
    ProgrammeCategory.entries.firstOrNull {
        programmeCategoryRouteValue(it) == value
    } ?: ProgrammeCategory.ALL
