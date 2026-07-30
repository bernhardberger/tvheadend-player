package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTopPadding
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerIdentityHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun titleIsAnAccessibilityHeading() {
        val content = mutableStateOf(
            HeaderContent(
                eyebrow = "104  Documentary",
                title = "Programme title",
                support = "Up next: News",
                clockSupport = "Ends at 00:05",
            ),
        )
        setHeader(content)

        composeRule.onNodeWithTag("player-header-title").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
    }

    @Test
    fun longEnglishHeaderKeepsTwoLineTitleAndStableSafeAnchorsAt960x540() {
        val content = mutableStateOf(
            HeaderContent(
                eyebrow = "104  Documentary",
                title = "Short title",
                support = "Up next at 23:55: News",
                clockSupport = "Ends at 00:05",
            ),
        )
        setHeader(
            content = content,
            fontScale = 1.3f,
        )
        val shortAnchors = anchors()
        composeRule.runOnIdle {
            content.value = HeaderContent(
                eyebrow = "104  International Documentary Channel Europe HD",
                title = "A comprehensive documentary title whose complete meaning requires " +
                    "two lines before it is safely ellipsized",
                support = "Up next at 23:55: A very long follow-up programme title for the evening",
                clockSupport = "Ends at 00:05",
            )
        }
        composeRule.waitForIdle()

        assertHeaderGeometry(shortAnchors)
    }

    @Test
    fun longGermanHeaderKeepsTwoLineTitleAndStableSafeAnchorsAtLargeText() {
        val content = mutableStateOf(
            HeaderContent(
                eyebrow = "104  Dokumentation",
                title = "Kurzer Titel",
                support = "Als Nächstes um 23:55: Nachrichten",
                clockSupport = "Endet um 00:05",
            ),
        )
        setHeader(
            content = content,
            fontScale = 1.3f,
        )
        val shortAnchors = anchors()
        composeRule.runOnIdle {
            content.value = HeaderContent(
                eyebrow = "104  Internationaler Dokumentationskanal Europa HD",
                title = "Eine ausführliche Dokumentationssendung mit einem besonders langen " +
                    "Titel für die begrenzte Fernsehoberfläche",
                support = "Als Nächstes um 23:55: Eine weitere außergewöhnlich lange " +
                    "Sendungsbezeichnung",
                clockSupport = "Endet um 00:05",
            )
        }
        composeRule.waitForIdle()

        assertHeaderGeometry(shortAnchors)
    }

    private fun setHeader(
        content: State<HeaderContent>,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
            ) {
                TVHeadendPlayerTheme {
                    Box(
                        modifier = Modifier
                            .requiredSize(width = 960.dp, height = 540.dp)
                            .testTag("player-header-surface"),
                    ) {
                        PlayerIdentityHeader(
                            imageLoader = imageLoader,
                            piconPath = null,
                            eyebrow = content.value.eyebrow,
                            title = content.value.title,
                            support = content.value.support,
                            clock = "23:59",
                            clockSupport = content.value.clockSupport,
                            modifier = Modifier.padding(
                                start = TvOverlaySidePadding,
                                end = TvOverlaySidePadding,
                                top = TvOverlayTopPadding,
                            ),
                            tags = PlayerHeaderTags(
                                picon = "player-header-picon",
                                eyebrow = "player-header-eyebrow",
                                title = "player-header-title",
                                support = "player-header-support",
                                clock = "player-header-clock",
                                clockSupport = "player-header-clock-support",
                            ),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertHeaderGeometry(shortAnchors: HeaderAnchors) {
        val surface = bounds("player-header-surface")
        val picon = bounds("player-header-picon")
        val eyebrow = bounds("player-header-eyebrow")
        val title = bounds("player-header-title")
        val support = bounds("player-header-support")
        val clock = bounds("player-header-clock")
        val clockSupport = bounds("player-header-clock-support")

        val sidePaddingPx = with(composeRule.density) { TvOverlaySidePadding.toPx() }
        val trailingColumnStart = minOf(clock.left, clockSupport.left)

        assertEquals(surface.left + sidePaddingPx, picon.left, 1f)
        assertEquals(surface.right - sidePaddingPx, clock.right, 1f)
        assertEquals(surface.right - sidePaddingPx, clockSupport.right, 1f)
        assertTrue(picon.right < eyebrow.left)
        assertTrue(eyebrow.bottom <= title.top)
        assertTrue(title.bottom <= support.top)
        assertTrue(clock.bottom <= clockSupport.top)
        assertEquals(eyebrow.top, clock.top, 1f)
        assertTrue(title.right < trailingColumnStart)
        assertTrue(support.right < trailingColumnStart)
        assertTrue(support.bottom < surface.bottom / 2f)
        assertEquals(shortAnchors.picon.left, picon.left, 1f)
        assertEquals(shortAnchors.picon.top, picon.top, 1f)
        assertEquals(shortAnchors.eyebrow.left, eyebrow.left, 1f)
        assertEquals(shortAnchors.eyebrow.top, eyebrow.top, 1f)
        assertEquals(shortAnchors.title.top, title.top, 1f)
        assertEquals(shortAnchors.clock.top, clock.top, 1f)
        assertEquals(shortAnchors.clock.right, clock.right, 1f)
        assertEquals(shortAnchors.clockSupport.top, clockSupport.top, 1f)
        assertEquals(shortAnchors.clockSupport.right, clockSupport.right, 1f)

        val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        composeRule.onNodeWithTag("player-header-title").performSemanticsAction(
            SemanticsActions.GetTextLayoutResult,
        ) { action ->
            action(textLayouts)
        }
        assertEquals(2, textLayouts.single().lineCount)
    }

    private fun bounds(tag: String) =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun anchors() = HeaderAnchors(
        picon = bounds("player-header-picon"),
        eyebrow = bounds("player-header-eyebrow"),
        title = bounds("player-header-title"),
        clock = bounds("player-header-clock"),
        clockSupport = bounds("player-header-clock-support"),
    )

    private data class HeaderContent(
        val eyebrow: String,
        val title: String,
        val support: String,
        val clockSupport: String,
    )

    private data class HeaderAnchors(
        val picon: Rect,
        val eyebrow: Rect,
        val title: Rect,
        val clock: Rect,
        val clockSupport: Rect,
    )
}
