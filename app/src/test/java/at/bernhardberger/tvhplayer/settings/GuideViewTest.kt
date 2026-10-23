package at.bernhardberger.tvhplayer.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideViewTest {
    @Test
    fun missingOrUnknownStoredValuesFallBackToDenseRowsWithTheHeadline() {
        assertEquals(GuideView(GuideRows.DENSE, headline = true), GuideView())
        assertEquals(GuideView(), resolveGuideView(null, null))
        assertEquals(GuideView(), resolveGuideView("COMPACT", null))
        assertEquals(GuideView(GuideRows.REGULAR), resolveGuideView("REGULAR", null))
        assertEquals(GuideView(headline = false), resolveGuideView(null, false))
    }

    @Test
    fun theStoreKeepsTheRowsAndTheHeadline() = runTest {
        val store = UiSettingsStore(InMemoryPreferencesDataStore())
        assertEquals(GuideView(), store.settings.first().guideView)
        store.setGuideView(GuideView(GuideRows.REGULAR))
        assertEquals(GuideView(GuideRows.REGULAR), store.settings.first().guideView)
        store.setGuideView(GuideView(GuideRows.DENSE, headline = false))
        assertEquals(GuideView(GuideRows.DENSE, headline = false), store.settings.first().guideView)
        store.setGuideView(GuideView(GuideRows.DENSE))
        assertEquals(GuideView(GuideRows.DENSE), store.settings.first().guideView)
    }
}
