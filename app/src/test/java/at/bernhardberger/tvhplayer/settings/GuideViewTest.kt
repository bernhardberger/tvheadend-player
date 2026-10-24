package at.bernhardberger.tvhplayer.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideViewTest {
    @Test
    fun missingOrUnknownStoredValuesFallBackToDenseRowsWithTheHeadlineAndGenreColors() {
        assertEquals(GuideView(GuideRows.DENSE, headline = true, genreColors = true), GuideView())
        assertEquals(GuideView(), resolveGuideView(null, null, null))
        assertEquals(GuideView(), resolveGuideView("COMPACT", null, null))
        assertEquals(GuideView(GuideRows.REGULAR), resolveGuideView("REGULAR", null, null))
        assertEquals(GuideView(headline = false), resolveGuideView(null, false, null))
        assertEquals(GuideView(genreColors = false), resolveGuideView(null, null, false))
    }

    @Test
    fun theStoreKeepsTheRowsTheHeadlineAndGenreColors() = runTest {
        val store = UiSettingsStore(InMemoryPreferencesDataStore())
        assertEquals(GuideView(), store.settings.first().guideView)
        store.setGuideView(GuideView(GuideRows.REGULAR))
        assertEquals(GuideView(GuideRows.REGULAR), store.settings.first().guideView)
        store.setGuideView(GuideView(GuideRows.DENSE, headline = false))
        assertEquals(GuideView(GuideRows.DENSE, headline = false), store.settings.first().guideView)
        store.setGuideView(GuideView(GuideRows.DENSE, genreColors = false))
        assertEquals(GuideView(GuideRows.DENSE, genreColors = false), store.settings.first().guideView)
        store.setGuideView(GuideView(GuideRows.DENSE))
        assertEquals(GuideView(GuideRows.DENSE), store.settings.first().guideView)
    }
}
