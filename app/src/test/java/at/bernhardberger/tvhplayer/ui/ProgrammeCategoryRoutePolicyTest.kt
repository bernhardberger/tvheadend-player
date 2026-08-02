package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammeCategoryRoutePolicyTest {
    @Test
    fun categoryRouteValuesRemainStableAndRoundTrip() {
        val expectedRouteValues = mapOf(
            ProgrammeCategory.ALL to "all",
            ProgrammeCategory.FILM_DRAMA to "film-drama",
            ProgrammeCategory.NEWS to "news",
            ProgrammeCategory.ENTERTAINMENT to "entertainment",
            ProgrammeCategory.SPORT to "sport",
            ProgrammeCategory.CHILDREN to "children",
            ProgrammeCategory.MUSIC to "music",
            ProgrammeCategory.ARTS_CULTURE to "arts-culture",
            ProgrammeCategory.SOCIETY_POLITICS to "society-politics",
            ProgrammeCategory.EDUCATION_FACTUAL to "education-factual",
            ProgrammeCategory.LIFESTYLE_LEISURE to "lifestyle-leisure",
        )

        assertEquals(
            expectedRouteValues,
            ProgrammeCategory.entries.associateWith(::programmeCategoryRouteValue),
        )
        expectedRouteValues.forEach { (category, routeValue) ->
            assertEquals(category, programmeCategoryFromRoute(routeValue))
        }
    }

    @Test
    fun unknownAndMissingRouteValuesFallBackToAll() {
        assertEquals(ProgrammeCategory.ALL, programmeCategoryFromRoute("unknown"))
        assertEquals(ProgrammeCategory.ALL, programmeCategoryFromRoute(null))
    }
}
