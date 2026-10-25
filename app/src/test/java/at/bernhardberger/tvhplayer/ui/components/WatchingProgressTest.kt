package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchingProgressTest {
    @Test fun watchingUsesViewerColorAndOtherItemsUseAmbientColor() {
        assertEquals(Color.Yellow, progressColor(true, Color.Cyan, Color.Yellow))
        assertEquals(Color.Cyan, progressColor(false, Color.Cyan, Color.Yellow))
    }
}
