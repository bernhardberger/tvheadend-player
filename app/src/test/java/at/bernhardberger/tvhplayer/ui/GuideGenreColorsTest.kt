package at.bernhardberger.tvhplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideGenreColorsTest {
    @Test fun everyFamilyHasItsOwnTintAndProgrammesWithoutACodeStayPlain() {
        val families = ProgrammeCategory.entries - ProgrammeCategory.ALL
        val colors = families.map { assertNotNull("$it has colors", tvGenreColors(it)); tvGenreColors(it)!! }
        assertEquals("Each family tints differently", families.size, colors.map { it.tint }.distinct().size)
        colors.forEach { assertNotEquals(TvSurfaceColors.containerHigh, it.tint) }
        // FolderListRow keeps TV Material ListItem's default focused container: inverseSurface.
        val containerLuminance = TvDarkColors.inverseSurface.luminance()
        val contrasts = families.zip(colors).associate { (family, palette) ->
            val iconLuminance = palette.focusedBase.luminance()
            assertTrue("$family keeps a darker genre color", iconLuminance < palette.base.luminance())
            family to (maxOf(containerLuminance, iconLuminance) + 0.05f) /
                (minOf(containerLuminance, iconLuminance) + 0.05f)
        }
        assertTrue("Focused genre graphics need 3:1 on the native light card: $contrasts",
            contrasts.values.all { it >= 3f })
        assertNull(tvGenreColors(null))
        assertNull(tvGenreColors(ProgrammeCategory.ALL))
        val sport = tvGenreColors(ProgrammeCategory.SPORT)!!
        assertEquals(Color(0xFF73F094), sport.base)
        assertEquals(Color(0xFF314238), sport.tint)
    }
}
