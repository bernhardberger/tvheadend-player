package at.bernhardberger.tvhplayer.ui

import androidx.compose.ui.graphics.Color
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GuideGenreColorsTest {
    @Test fun everyFamilyHasItsOwnTintAndWashAndProgrammesWithoutACodeStayPlain() {
        val families = ProgrammeCategory.entries - ProgrammeCategory.ALL
        val colors = families.map { assertNotNull("$it has colors", tvGenreColors(it)); tvGenreColors(it)!! }
        assertEquals("Each family tints differently", families.size, colors.map { it.tint }.distinct().size)
        assertEquals("Each family washes differently", families.size, colors.map { it.wash }.distinct().size)
        colors.forEach {
            assertNotEquals(TvSurfaceColors.containerHigh, it.tint)
            assertNotEquals(it.tint, it.wash)
        }
        assertNull(tvGenreColors(null))
        assertNull(tvGenreColors(ProgrammeCategory.ALL))
        val sport = tvGenreColors(ProgrammeCategory.SPORT)!!
        assertEquals(Color(0xFF73F094), sport.base)
        assertEquals(Color(0xFF314238), sport.tint)
        assertEquals(Color(0xFFBFE6CC), sport.wash)
    }
}
