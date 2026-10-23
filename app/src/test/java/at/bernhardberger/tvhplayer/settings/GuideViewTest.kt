package at.bernhardberger.tvhplayer.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideViewTest {
    @Test
    fun missingOrUnknownStoredValuesFallBackToDenseRows() {
        assertEquals(GuideView(GuideRows.DENSE), GuideView())
        assertEquals(GuideView(), resolveGuideView(null))
        assertEquals(GuideView(), resolveGuideView("COMPACT"))
        assertEquals(GuideView(GuideRows.REGULAR), resolveGuideView("REGULAR"))
    }

    @Test
    fun theStoreKeepsTheRows() = runTest {
        val store = UiSettingsStore(InMemoryPreferencesDataStore())
        assertEquals(GuideView(), store.settings.first().guideView)
        store.setGuideView(GuideView(GuideRows.REGULAR))
        assertEquals(GuideView(GuideRows.REGULAR), store.settings.first().guideView)
        store.setGuideView(GuideView(GuideRows.DENSE))
        assertEquals(GuideView(GuideRows.DENSE), store.settings.first().guideView)
    }
}
