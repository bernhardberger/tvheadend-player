package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.SemanticsProperties
import at.bernhardberger.tvhplayer.ui.components.RowChangeMs
import at.bernhardberger.tvhplayer.ui.components.RowHeaderQuietScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsPage
import at.bernhardberger.tvhplayer.ui.components.programmeAboutFacts
import at.bernhardberger.tvhplayer.ui.components.programmeAboutText
import coil3.ImageLoader
import coil3.compose.AsyncImage
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgrammeDetailsPageTest {
    @get:Rule val compose = createComposeRule()
    private val oldLocale = Locale.getDefault()
    private val oldZone = TimeZone.getDefault()
    private val oldClock = AppClock.systemUses24Hour
    private val oldFormat = AppClock.format
    private lateinit var loader: ImageLoader
    private lateinit var view: View
    private val start = Instant.parse("2026-09-15T19:00:00Z")
    private val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = ArtworkId(1))
    private val otherChannel = Channel.create(ChannelId(2), name = "Ridge Earth Plus", number = 102, icon = ArtworkId(1))
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()
    private val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds,
        title = "Ridge Light", subtitle = "Beyond the Arctic Circle", image = ArtworkId(2),
        genre = "Documentary", episode = EpgEpisode(null, null, 2, null, 3, 8, null, null, "S2 E3"),
        summary = "A winter expedition beyond the Arctic Circle follows the changing light across the ridge.",
        description = "The crew follows the changing light across the ridge, discovering the wildlife and people of the far north. ".repeat(2))
    /** Later airings of the same programme: one tomorrow on the same channel, others later on two channels. */
    private val airings = (2L..9L).map { id ->
        EpgEvent.create(EventId(id), if (id % 2 == 0L) channel.id else otherChannel.id,
            start + (id * 86400 - 86400 + 3600).seconds, start + (id * 86400 - 86400 + 7200).seconds,
            title = if (id == 5L) "Ridge Light: Northern Journey Across the Frozen Ridge" else "Ridge Light",
            subtitle = if (id == 4L) "Into the Polar Night" else null,
            image = if (id == 3L) null else ArtworkId(if (id % 3 == 0L) 3 else 2))
    }

    @Before fun prepare() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        AppClock.format = at.bernhardberger.tvhplayer.settings.ClockFormat.SYSTEM
        AppClock.systemUses24Hour = true
        loader = FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(2) to FixtureArt.art("ridge-light"),
            ArtworkId(3) to FixtureArt.art("lantern-road"), ArtworkId(4) to FixtureArt.art("still-ridge-light")))
    }
    @After fun release() {
        Locale.setDefault(oldLocale)
        TimeZone.setDefault(oldZone)
        AppClock.systemUses24Hour = oldClock
        AppClock.format = oldFormat
        loader.shutdown()
    }

    @Test fun airingTopBlockShowsOwnProgressAndAiringsFollowActions() {
        show(); compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onNodeWithText("21:00 · 112m left", substring = true).assertExists()
        compose.onNodeWithText("101 Ridge Earth HD").assertExists()
        compose.onNodeWithTag("details-metadata").assertTextEquals("120m · Documentary · S2\u00a0E3")
        compose.onNodeWithTag("details-timeline").assertExists()
        compose.onNodeWithTag("details-progress", useUnmergedTree = true).assertExists()
        // The text column keeps to the left ~55% of the screen, clear of the art's focal area.
        Assert.assertTrue(compose.onNodeWithTag("details-timeline").getUnclippedBoundsInRoot().right.value <= 960f * .55f)
        // The lower section follows the natural header instead of reserving an empty first screen.
        val heading = compose.onNodeWithText("Other airings of Ridge Light").getBoundsInRoot()
        capture("details-top")
        Assert.assertTrue("$heading", heading.top >= compose.onNodeWithTag("details-actions").getUnclippedBoundsInRoot().bottom + 8.dp)
        Assert.assertTrue("$heading", heading.bottom.value <= 540f - 48f)
        compose.onNodeWithText("About Ridge Light").assertDoesNotExist()
        capture("details-top")
        capture("live-art", "u15-details-layout")
    }
    @Test fun brightArtKeepsTheTopBlockReadable() { show(bright = true); capture("details-top-bright") }

    @Test fun plainDetailsOpensReaderAndRestoresTheAiring() {
        show(richFacts = true)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertIsFocused()
        compose.onNode(hasText("Genre") and hasAnyAncestor(hasTestTag("details-facts")), useUnmergedTree = true).assertExists()
        capture("airings-and-details")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
        compose.onNodeWithTag("details-card-2").assertIsNotFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
        compose.onNodeWithText("Mountain · Winter", useUnmergedTree = true).assertIsDisplayed()
        capture("details-panel-focused-dimmed-airings")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-facts-panel").assertHasClickAction()
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-card-2").assertIsFocused()
    }

    @Test fun overflowingDetailsMoveFocusAndUpReturns() {
        show(richFacts = true, fontScale = 1.8f)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertIsFocused()
        val before = compose.onNodeWithTag("details-card-2").getUnclippedBoundsInRoot().top
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
        compose.onNodeWithText("Mountain · Winter", useUnmergedTree = true).assertIsDisplayed()
        Assert.assertTrue(compose.onNodeWithTag("details-card-2").getUnclippedBoundsInRoot().top < before)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        Assert.assertEquals(before, compose.onNodeWithTag("details-card-2").getUnclippedBoundsInRoot().top)
    }

    @Test fun headerBlockDimsWithAiringsUsingTheSharedClock() {
        show(heroVariant = 1)
        fun brightestAboutText(): Int {
            val bounds = compose.onNodeWithText("Record", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val bitmap = snapshot()
            val brightest = (bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(1080)).maxOf { y ->
                (bounds.left.toInt() until bounds.right.toInt()).maxOf { x -> android.graphics.Color.red(bitmap.getPixel(x, y)) }
            }
            bitmap.recycle()
            return brightest
        }
        val active = brightestAboutText()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        val quiet = brightestAboutText()
        Assert.assertTrue("header glyph brightness $active -> $quiet", quiet < active * .55f)
        capture("header-dimmed-airings-active")
    }

    @Test fun headingVisibleGapIsSixteenDpInQuietAndActiveStates() {
        show(future = true)
        fun gap(): Float {
            val heading = compose.onNodeWithText("Other airings of Ridge Light").fetchSemanticsNode().boundsInRoot
            val card = compose.onNodeWithTag("details-card-2").fetchSemanticsNode().boundsInRoot
            val bitmap = snapshot()
            val glyphBottom = (heading.top.toInt() until heading.bottom.toInt()).last { y ->
                (heading.left.toInt() until heading.right.toInt()).any { x -> android.graphics.Color.red(bitmap.getPixel(x, y)) > 50 }
            }
            val cardTop = (glyphBottom + 1..card.top.toInt() + 20).first { y ->
                android.graphics.Color.red(bitmap.getPixel(card.center.x.toInt(), y)) > 50
            }
            bitmap.recycle()
            return (cardTop - glyphBottom - 1) / 2f
        }
        val quiet = gap()
        capture("heading-gap-quiet")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        val active = gap()
        capture("heading-gap-active")
        Assert.assertEquals("quiet gap", 16f, quiet, 1f)
        Assert.assertEquals("active gap", 16f, active, 1f)
        Assert.assertEquals("same visible gap", quiet, active, 1f)
    }

    @Test fun detailsAndAiringsHeadingsUseTheSameQuietAndActiveSizes() {
        show(richFacts = true)
        fun height(text: String): Float {
            val coordinates = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
            return coordinates.localToRoot(androidx.compose.ui.geometry.Offset(0f, coordinates.size.height.toFloat())).y -
                coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y
        }
        val quietAirings = height("Other airings of Ridge Light")
        val quietDetails = height("Details")
        Assert.assertEquals(quietAirings, quietDetails, .5f)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        val activeAirings = height("Other airings of Ridge Light")
        fun factsGap(): Float {
            val heading = compose.onNodeWithText("Details", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val label = compose.onNode(hasText("Genre") and hasAnyAncestor(hasTestTag("details-facts")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val bitmap = snapshot()
            val glyphBottom = (heading.top.toInt() until heading.bottom.toInt()).last { y ->
                (heading.left.toInt() until heading.right.toInt()).any { x -> android.graphics.Color.red(bitmap.getPixel(x, y)) > 50 }
            }
            val labelTop = (label.top.toInt() until label.bottom.toInt()).first { y ->
                (label.left.toInt() until label.right.toInt()).any { x -> android.graphics.Color.red(bitmap.getPixel(x, y)) > 45 }
            }
            bitmap.recycle()
            return (labelTop - glyphBottom - 1) / 2f
        }
        val quietGap = factsGap()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        Assert.assertEquals(activeAirings, height("Details"), .5f)
        Assert.assertEquals(18f / 28f, quietDetails / height("Details"), .02f)
        Assert.assertEquals("quiet facts gap", 16f, quietGap, 1f)
        Assert.assertEquals("active facts gap", 16f, factsGap(), 1f)
    }

    @Test fun focusedAiringScaleIsNotClippedMidSectionTransition() {
        show(future = true)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithTag("details-card-2").assertIsFocused()
        val card = compose.onNodeWithTag("details-card-2").getUnclippedBoundsInRoot()
        val bitmap = snapshot(settle = false)
        // Native 1.1 scale extends beyond the unscaled 58dp keyline, inside the row's reserved room.
        val x = ((card.left.value - 8f) * 2).toInt()
        val y = ((card.top.value + (card.bottom - card.top).value / 2) * 2).toInt()
        Assert.assertTrue("focused overflow at $x,$y", android.graphics.Color.red(bitmap.getPixel(x, y)) > 35)
        val directory = File("build/outputs/u15-about").apply { mkdirs() }
        File(directory, "airings-focused-mid-transition.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.mainClock.autoAdvance = true
    }

    @Test fun dimmedAiringPiconPlateRetainsItsOpacityRelativeToArt() {
        show(richFacts = true)
        fun platePixels(): Pair<Int, Int> {
            // An unfocused artwork card with a picon plate, clear of its corner and logo glyphs,
            // and the page just left of that card.
            val bounds = compose.onNodeWithTag("details-card-4").getUnclippedBoundsInRoot()
            val bitmap = snapshot()
            val y = ((bounds.bottom.value - 20f) * 2).toInt()
            val pixels = bitmap.getPixel(((bounds.left.value + 10f) * 2).toInt(), y) to
                bitmap.getPixel(((bounds.left.value - 4f) * 2).toInt(), y)
            bitmap.recycle()
            return pixels
        }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        val active = platePixels().first
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
        val (quiet, page) = platePixels()
        for (channel in listOf(android.graphics.Color::red, android.graphics.Color::green, android.graphics.Color::blue)) {
            Assert.assertEquals("plate must be faded once with its art", channel(active) * .4f + channel(page) * .6f,
                channel(quiet).toFloat(), 2f)
        }
        capture("dimmed-airings-picon-plates")
    }
    @Test fun longTitleKeepsPanelAndActionsAboveBottomSafeEdge() {
        show(longTitle = true)
        compose.onNodeWithTag("details-watch").assertIsFocused().assertIsDisplayed()
        val actions = compose.onNodeWithTag("details-actions").getUnclippedBoundsInRoot()
        Assert.assertTrue("$actions", actions.bottom.value <= 540f - 48f)
        compose.onNodeWithTag("details-about-description").assertIsDisplayed()
        capture("details-long-title")
    }
    @Test fun shortAboutAlwaysFocusesAndOpensSharedReader() {
        show()
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-about-description").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        compose.onNodeWithTag("details-hero-blurred").assertExists()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-about-description").assertIsFocused()
    }
    @Test fun factsWithoutDescriptionAppearInHeaderWithoutAnEmptyReader() {
        show(factsOnly = true)
        compose.onNodeWithTag("details-metadata").assertTextEquals("120m · Documentary")
        compose.onNodeWithTag("details-about-description").assertDoesNotExist()
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionUp) }.assertIsFocused()
    }
    @Test fun detailsWithoutDescriptionRemainPlainFacts() {
        show(richFacts = true, noDescription = true, noAirings = true)
        compose.onNodeWithTag("details-about-description").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-facts-panel").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-reading-text", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("details-facts", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
    }
    @Test fun detailsHidesRealRailAndUsesFullScreenWidth() {
        show(realRail = true)
        compose.onNodeWithTag("nav-channels").assertDoesNotExist()
        val page = compose.onNodeWithTag("programme-details-page").getUnclippedBoundsInRoot()
        Assert.assertEquals(0f, page.left.value, 1f)
        Assert.assertEquals(960f, page.right.value, 1f)
        capture("details-fullscreen-no-rail")
    }
    @Test fun leftAtEverySectionEdgeStaysInDetailsAndBackStillCloses() {
        var closed = 0
        show(onClose = { closed++ }, rail = true, longText = true)
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionLeft) }.assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-about-description").performKeyInput { pressKey(Key.DirectionLeft) }.assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").performKeyInput { pressKey(Key.DirectionLeft) }.assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        Assert.assertEquals(1, closed)
    }
    @Test fun futureWithoutArtReservesNoHero() {
        show(future = true); compose.onNodeWithTag("details-hero").assertDoesNotExist()
        compose.onNodeWithTag("details-progress").assertDoesNotExist()
        capture("details-top-noart")
        capture("future-no-art", "u15-details-layout")
    }
    @Test fun recordingPartlyWatched() {
        show(recording = true); compose.onNodeWithTag("details-resume").assertIsFocused()
        val progress = compose.onNodeWithTag("details-progress", useUnmergedTree = true)
        Assert.assertEquals(3723f / 7200f, progress.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current, .0001f)
        val left = compose.onNodeWithText("1:02:03", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val right = compose.onNodeWithText("2:00:00 · 58m left", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val strip = progress.getUnclippedBoundsInRoot()
        Assert.assertEquals(8f, (strip.left - left.right).value, .5f)
        Assert.assertEquals(8f, (right.left - strip.right).value, .5f)
        Assert.assertEquals(4f, (strip.bottom - strip.top).value, .5f)
        compose.onNodeWithText("Left", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("details-timeline").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.ContentDescription,
            listOf("62 minutes of 120 minutes, 58 minutes left")))
        compose.onNodeWithText("Other airings of Ridge Light").assertDoesNotExist()
        capture("recording-partly-watched")
        capture("recording-partly-watched", "u15-details-layout")
    }

    @Test fun upFocusesPanelAboveActionsAndBackReturnsToTheActions() {
        var closed = 0
        show(onClose = { closed++ }, noAirings = true)
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-about-description").assertIsFocused()
        val about = compose.onNodeWithTag("details-about")
        val bounds = about.getUnclippedBoundsInRoot()
        Assert.assertTrue("$bounds", bounds.top.value >= 48f && bounds.bottom.value <= 540f - 48f)
        val text = compose.onNode(hasText(event.summary!!) and hasAnyAncestor(hasTestTag("details-about")), useUnmergedTree = true)
        Assert.assertEquals(64.4f, text.getUnclippedBoundsInRoot().left.value, 1f)
        text.assertIsDisplayed()
        compose.onNode(hasText("More info") and hasAnyAncestor(hasTestTag("details-about")), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("details-about-arrow", useUnmergedTree = true).assertIsDisplayed()
        Assert.assertEquals(584f, (bounds.right - bounds.left).value, 1f)
        Assert.assertTrue(bounds.bottom < compose.onNodeWithTag("details-actions").getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("details-facts", useUnmergedTree = true).assertExists()
        capture("about-focused")
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-watch").assertIsFocused().assertIsDisplayed()
        Assert.assertEquals(0, closed)
        compose.runOnIdle { back.onBackPressed() }
        Assert.assertEquals(1, closed)
    }

    @Test fun longAboutOpensSharedReaderScrollsToEndAndRestoresAbout() {
        show(longText = true)
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-about-description").assertIsFocused()
        capture("about-long-focused")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        compose.onNode(hasText(event.title!!) and hasAnyAncestor(hasTestTag("details-reading-heading")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("More info") and hasAnyAncestor(hasTestTag("details-reading-heading")), useUnmergedTree = true).assertDoesNotExist()
        capture("full-description-open")
        val text = compose.onNodeWithTag("details-reading-text", useUnmergedTree = true)
        val firstTop = text.getUnclippedBoundsInRoot().top.value
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        Assert.assertEquals(60f, firstTop - text.getUnclippedBoundsInRoot().top.value, 2f)
        capture("reading-long-scrolled")
        repeat(100) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        Assert.assertTrue(text.getUnclippedBoundsInRoot().bottom.value <= 540f - 48f + 1f)
        val endTop = text.getUnclippedBoundsInRoot().top.value
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        Assert.assertTrue(text.getUnclippedBoundsInRoot().top.value > endTop)
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-about-description").assertIsFocused().assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
    }

    @Test fun sectionScrollFinishesInOneMotionWithoutPausing() {
        show(longText = true)
        fun scrollValue() = compose.onNodeWithTag("details-page-scroll", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.mainClock.autoAdvance = false
        for (key in listOf(Key.DirectionDown, Key.DirectionUp)) {
            val values = mutableListOf(scrollValue())
            compose.onRoot().performKeyInput { pressKey(key) }
            repeat((RowChangeMs + 64) / 16) {
                compose.mainClock.advanceTimeByFrame()
                values += scrollValue()
            }
            val finish = values.last()
            compose.mainClock.advanceTimeBy(1000)
            Assert.assertEquals("$key must finish in one motion: $values", scrollValue(), finish, 1f)
            val moving = values.dropWhile { it == values.first() }.dropLastWhile { it == values.last() }
            Assert.assertTrue("$key must move: $values", moving.isNotEmpty())
            // Sub-pixel easing steps may repeat a pixel within the last two before the finish.
            Assert.assertTrue("$key must not pause/reverse: $values", moving.zipWithNext().all { (a, b) ->
                if (finish > values.first()) b > a || b == a && finish - a <= 2f else b < a || b == a && a - finish <= 2f
            })
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun airingsHeadingEmphasisFollowsThePageScrollMidAnimation() {
        show(longText = true)
        fun scrollValue() = compose.onNodeWithTag("details-page-scroll", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun headingWidth() = compose.onNodeWithText("Other airings of Ridge Light").fetchSemanticsNode().boundsInRoot.width
        val startScroll = scrollValue()
        val quietWidth = headingWidth()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(150)
        val midScroll = scrollValue()
        val midWidth = headingWidth()
        compose.mainClock.advanceTimeBy(RowChangeMs.toLong())
        val endScroll = scrollValue()
        val fullWidth = headingWidth()
        Assert.assertTrue("scroll $startScroll < $midScroll < $endScroll", midScroll > startScroll && midScroll < endScroll)
        Assert.assertTrue("heading width $quietWidth < $midWidth < $fullWidth", midWidth > quietWidth && midWidth < fullWidth)
        val scale = midWidth / fullWidth
        val quietScale = 18f / 28f
        Assert.assertTrue("heading scale $scale", scale > quietScale && scale < 1f)
        val progress = (midScroll - startScroll) / (endScroll - startScroll)
        Assert.assertEquals("heading reads scroll, not a separate focus animation",
            quietScale + (1f - quietScale) * progress, scale, .01f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun panelAndActionsShareTheTopWithoutScrolling() {
        show(summaryOnly = true)
        fun scrollValue() = compose.onNodeWithTag("details-page-scroll", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val top = scrollValue()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-about-description").assertIsFocused()
        Assert.assertEquals(top, scrollValue(), 0f)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        Assert.assertEquals(top, scrollValue(), 0f)
    }

    @Test fun pageReturnedToFromAnAiringStartsAtTheAiringsWithoutScrolling() {
        compose.mainClock.autoAdvance = false
        show(longText = true, returnToCard = airings[2].id)
        fun scrollValue() = compose.onNodeWithTag("details-page-scroll", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val values = mutableListOf(scrollValue())
        repeat((RowChangeMs + 64) / 16) {
            compose.mainClock.advanceTimeByFrame()
            values += scrollValue()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settled = scrollValue()
        Assert.assertTrue("page must start below the top: $values", settled > 0f)
        Assert.assertTrue("page must jump straight to the airings: $values",
            values.dropWhile { it == 0f }.all { it == settled })
        compose.onNodeWithTag("details-card-${airings[2].id.value}").assertIsFocused()
    }

    @Test fun aboutPrecedesAiringsAndRowScrollsToTheScreenEdge() {
        var opened: EpgEvent? = null
        show(onRelated = { opened = it }, realRail = true)
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-read-more").assertDoesNotExist()
        val first = compose.onNodeWithTag("details-card-2").assertIsFocused().assertIsDisplayed()
        Assert.assertEquals(58f, first.getUnclippedBoundsInRoot().left.value, 1f)
        compose.onNodeWithText("Other airings of Ridge Light").assertIsDisplayed()
        compose.onNodeWithTag("details-about-description").assertIsNotFocused()
        // Cards lead with when; the page's own title is never repeated, a different one is the episode line.
        val time = compose.onAllNodesWithTag("details-card-time", useUnmergedTree = true)[0]
        first.assertContentDescriptionEquals("Tomorrow 20:00, 101 Ridge Earth HD")
        compose.onNodeWithTag("details-card-4").assertContentDescriptionEquals("Fri, Sep 18 20:00, Into the Polar Night, 101 Ridge Earth HD")
        compose.onNodeWithTag("details-card-5").assertContentDescriptionEquals(
            "Sat, Sep 19 20:00, Ridge Light: Northern Journey Across the Frozen Ridge, 102 Ridge Earth Plus")
        compose.onNode(hasText("Ridge Light") and hasAnyAncestor(hasTestTag("details-airings")), useUnmergedTree = true).assertDoesNotExist()
        // The text clears the focused card's 1.1 scale (~6dp) and outline.
        val card = first.getUnclippedBoundsInRoot()
        Assert.assertTrue(time.getUnclippedBoundsInRoot().top.value - card.bottom.value >= 6f + 4f + 4f)
        // The row runs past the right screen edge.
        Assert.assertTrue(compose.onNodeWithTag("details-card-5").getUnclippedBoundsInRoot().right.value > 960f)
        capture("airings-row-focused-first")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-card-3").assertIsFocused()
        capture("airings-card-without-art")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-card-4").assertIsFocused()
        Assert.assertEquals(58f, compose.onNodeWithTag("details-card-4").getUnclippedBoundsInRoot().left.value, 1f)
        Assert.assertTrue(compose.onNodeWithTag("details-card-3").getUnclippedBoundsInRoot().left.value < 58f)
        capture("airings-row-scrolled")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { Assert.assertEquals(EventId(4), opened?.id) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-4").assertIsFocused()
        // Back from the row returns to the actions with the page at its top.
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-watch").assertIsFocused().assertIsDisplayed()
    }

    @Test fun missingContentReservesNoSynopsisHeroOrAbout() {
        show(future = true)
        compose.onNodeWithTag("details-synopsis").assertDoesNotExist()
        compose.onNodeWithTag("details-hero").assertDoesNotExist()
        compose.onNodeWithTag("details-about").assertDoesNotExist()
        val title = compose.onNodeWithText("Ridge Light").getUnclippedBoundsInRoot()
        val facts = compose.onNodeWithTag("details-metadata").assertTextEquals("Today\u00a019:00 · 120m").getUnclippedBoundsInRoot()
        Assert.assertEquals(20f, facts.top.value - title.bottom.value, 1f)
        // Without an About section, Down goes straight to the row.
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanPlainDetailsAndDescriptionOnlyReader() {
        show(noAirings = true, richFacts = true)
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
        compose.onNodeWithText("Details").assertIsDisplayed()
        val facts = hasAnyAncestor(hasTestTag("details-facts"))
        val labels = listOf("Genre", "Staffel", "Folge").map {
            compose.onNode(hasText(it) and facts, useUnmergedTree = true).getUnclippedBoundsInRoot()
        }
        Assert.assertTrue(labels.all { it.top == labels.first().top })
        Assert.assertTrue(labels.zipWithNext().all { (a, b) -> b.left - a.left == 224.dp })
        val values = listOf("Documentary", "2", "3 von 8").map {
            compose.onNode(hasText(it) and facts, useUnmergedTree = true).getUnclippedBoundsInRoot()
        }
        Assert.assertTrue(values.zip(labels).all { (value, label) -> value.left == label.left && value.top >= label.bottom })
        compose.onNode(hasText("Genre") and facts, useUnmergedTree = true).assertExists()
        capture("facts-flow-de")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-about-description").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        compose.onNodeWithTag("details-facts", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("details-reading-text", useUnmergedTree = true).assertExists()
    }

    @Test fun largeTextKeepsActionsAboutAndAiringsReachable() {
        show(fontScale = 1.3f)
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-record").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-show_guide").assertIsFocused().assertIsDisplayed()
            .performKeyInput { pressKey(Key.DirectionDown) }
        var steps = 0
        while (compose.onAllNodes(hasTestTag("details-card-2") and isFocused()).fetchSemanticsNodes().isEmpty()) {
            Assert.assertTrue(++steps < 20)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithTag("details-card-2").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-show_guide").assertIsFocused().assertIsDisplayed()
    }

    @Test fun aboutFactsListOnlyFieldsThatArePresent() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        val rich = EpgEvent.create(event.id, event.channelId, event.start, event.stop, genre = "Documentary",
            episode = EpgEpisode(null, null, 2, 5, 3, 10, 1, 2, "S2 E3"),
            rating = EpgRating(12, "XMLTV:FSK:12", null, null, null, 4), copyrightYear = 2024,
            firstAired = Instant.parse("2024-01-01T12:00:00Z"), isNew = false,
            categories = listOf("Nature", " ", "Nature"), keywords = listOf("Mountain", "Winter"))
        Assert.assertEquals(listOf("Genre" to "Documentary", "Season" to "2 of 5", "Episode" to "3 of 10", "Part" to "1 of 2",
            "Year" to "2024", "Age rating" to "FSK 12", "Star rating" to "4", "First aired" to "Jan 1, 2024",
            "Broadcast" to "Repeat", "Categories" to "Nature", "Keywords" to "Mountain · Winter"),
             programmeAboutFacts(rich, null, resources, Locale.US))
        Assert.assertEquals(listOf("Documentary", "2024", "S2 E3 · Part 1"),
            at.bernhardberger.tvhplayer.ui.components.programmeHeaderMetadata(programmeAboutFacts(rich, null, resources, Locale.US), resources, rich.episode))
        val sparse = EpgEvent.create(event.id, event.channelId, event.start, event.stop,
            episode = EpgEpisode(null, null, null, null, null, null, null, null, "Pilot"), isNew = true,
            contentGenre = ContentGenre.fromContentType(0x90))
        Assert.assertEquals(listOf("Genre" to "Education & Factual", "Episode" to "Pilot", "Broadcast" to "New"),
            programmeAboutFacts(sparse, null, resources, Locale.US))
        Assert.assertEquals(listOf("Education & Factual", "Pilot"),
            at.bernhardberger.tvhplayer.ui.components.programmeHeaderMetadata(programmeAboutFacts(sparse, null, resources, Locale.US), resources, sparse.episode))
        // XMLTV guides mostly send categories without a genre: the first two stand in for it.
        val categoriesOnly = EpgEvent.create(event.id, event.channelId, event.start, event.stop, copyrightYear = 2021,
            categories = listOf("Sitcom", "Comedy", "Series"))
        Assert.assertEquals(listOf("Sitcom", "Comedy", "2021"),
            at.bernhardberger.tvhplayer.ui.components.programmeHeaderMetadata(programmeAboutFacts(categoriesOnly, null, resources, Locale.US), resources, categoriesOnly.episode))
        val bare = EpgEvent.create(event.id, event.channelId, event.start, event.stop, title = "Ridge Light")
        Assert.assertEquals(emptyList<Pair<String, String>>(), programmeAboutFacts(bare, null, resources, Locale.US))
        Assert.assertEquals(listOf("Short", "Long\ntext"), programmeAboutText("Short", "Long\\ntext", synopsisShownInFull = false))
        Assert.assertEquals(listOf("Long\ntext"), programmeAboutText("Short", "Long\\ntext", synopsisShownInFull = true))
        Assert.assertEquals(emptyList<String>(), programmeAboutText(" Same", "Same ", synopsisShownInFull = true))
        Assert.assertEquals(listOf("Same"), programmeAboutText(" Same", "Same ", synopsisShownInFull = false))
        Assert.assertEquals(emptyList<String>(), programmeAboutText(" ", null, synopsisShownInFull = false))
    }

    @Test fun episodeHeaderUsesLocaleNumericPremiereInsteadOfCopyrightYear() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        val episode = EpgEpisode(null, null, 5, null, 10, null, null, null, null)
        val facts = listOf(resources.getString(at.bernhardberger.tvhplayer.R.string.details_year) to "2005")
        val premiere = Instant.parse("2004-03-12T12:00:00Z")
        fun header(ep: EpgEpisode?, date: Instant?, values: List<Pair<String, String>> = facts,
            locale: Locale = Locale.US) =
            at.bernhardberger.tvhplayer.ui.components.programmeHeaderMetadata(values, resources, ep, date, locale)
        Assert.assertEquals(listOf("S5 E10", "3/12/2004"), header(episode, premiere))
        Assert.assertEquals(listOf("S5 E10", "12.3.2004"), header(episode, premiere, locale = Locale.GERMAN))
        Assert.assertEquals(listOf("2005"), header(null, premiere))
        Assert.assertEquals(listOf("2005", "S5 E10"), header(episode, null))
        Assert.assertEquals(listOf("S5 E10"), header(episode, null, emptyList()))
        Assert.assertEquals(emptyList<String>(), header(null, null, emptyList()))
    }

    @Test fun airingEpisodeShowsOnlyWhatDiffersFromThePage() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        fun airing(title: String?, subtitle: String? = null) = EpgEvent.create(EventId(9), channel.id, start, event.stop,
            title = title, subtitle = subtitle)
        Assert.assertNull(at.bernhardberger.tvhplayer.ui.components.airingEpisode(airing(" ridge light "), "Ridge Light", "Part 1", resources))
        Assert.assertNull(at.bernhardberger.tvhplayer.ui.components.airingEpisode(airing("Ridge Light", "Part 1"), "Ridge Light", "Part 1", resources))
        Assert.assertEquals("Part 2", at.bernhardberger.tvhplayer.ui.components.airingEpisode(airing("Ridge Light", "Part 2"), "Ridge Light", "Part 1", resources))
        Assert.assertEquals("Ridge Light: Winter", at.bernhardberger.tvhplayer.ui.components.airingEpisode(
            airing("Ridge Light: Winter", "Part 1"), "Ridge Light", "Part 1", resources))
    }

    @Test fun durationFormatterKeepsMinutesForDetailsAndDialogs() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        Assert.assertEquals("120m", at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes(120, resources))
        Assert.assertEquals("45m", at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes(45, resources))
        Assert.assertEquals("112m", at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes(112, resources))
    }

    private lateinit var back: androidx.activity.OnBackPressedDispatcher

    @Test fun todayAiringCardNamesTheDay() {
        show(todayCard = true)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertContentDescriptionEquals("Today 20:00, 101 Ridge Earth HD")
        capture("blurred-backdrop")
    }

    @Test fun runningAiringCardSaysWhenItEndsAndShowsItsProgress() {
        show(runningCard = true)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        val stop = at.bernhardberger.tvhplayer.ui.common.formatHm(event.stop.epochSeconds)
        compose.onNodeWithTag("details-card-2").assertContentDescriptionEquals("On now · until $stop, 102 Ridge Earth Plus")
        compose.onNodeWithTag("details-card-progress-2", useUnmergedTree = true).assertExists()
    }

    @Test fun longPlainFactsAreUncappedAndReadableWithoutMovingTheHeading() {
        show(longFacts = true)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown) }
        val value = compose.onNode(hasText("Mountain ".repeat(100).trim()) and
            hasAnyAncestor(hasTestTag("details-facts")), useUnmergedTree = true)
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        value.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        Assert.assertTrue(results.single().lineCount > 3)
        Assert.assertFalse(results.single().hasVisualOverflow)
        repeat(30) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        Assert.assertTrue(value.getUnclippedBoundsInRoot().bottom.value <= 492.5f)
        Assert.assertEquals(160f, compose.onNodeWithText("Details", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value, .5f)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-card-2").assertIsFocused()
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanRatingMoreAndToday() {
        show(longSynopsis = true, todayCard = true)
        compose.onNodeWithTag("details-rating").assertContentDescriptionEquals("Altersfreigabe 12+")
        val text = compose.onNodeWithTag("details-synopsis", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        Assert.assertTrue(text.text.endsWith("… Mehr"))
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertContentDescriptionEquals("Heute 20:00, 101 Ridge Earth HD")
    }

    @Test fun cornerArtRevealsTheBackgroundThenBlursFullScreen() = uniformScrim()
    @Test @Config(sdk = [30]) fun pre31CornerArtRevealsTheBackgroundThenDarkensFullScreen() = uniformScrim()

    private fun uniformScrim() {
        loader.shutdown()
        loader = FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"),
            ArtworkId(2) to Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }))
        show()
        compose.waitUntil(10_000) {
            at.bernhardberger.tvhplayer.images.DetailsArtColors.stored(AppArtworkSource(requireNotNull(session), ArtworkId(2))) != null
        }
        val sharp = snapshot()
        // Kit cinematic scrim: ~10% at the top-right centre, opaque page background towards the bottom left.
        Assert.assertEquals("top-right art stays bright", 205f, android.graphics.Color.red(sharp.getPixel(1800, 100)).toFloat(), 15f)
        // Top-left is outside the corner art (the pulled-up airings may occupy the bottom).
        Assert.assertEquals("outside the corner art the page background shows",
            android.graphics.Color.rgb(19, 19, 19), sharp.getPixel(20, 20))
        Assert.assertTrue("left edge fades to background", android.graphics.Color.red(sharp.getPixel(300, 20)) < 40)
        // Behind the text column (450dp) the art dissolves into the background.
        Assert.assertEquals("art behind the text is mostly scrimmed", 91f, android.graphics.Color.red(sharp.getPixel(900, 20)).toFloat(), 15f)
        Assert.assertTrue("bottom of the frame is background", android.graphics.Color.red(sharp.getPixel(1800, 850)) < 40)
        sharp.recycle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(1000)
        val bitmap = snapshot()
        // Scrolled away, the darkened blurred art covers the whole screen.
        for ((x, y) in listOf(1000 to 60, 1800 to 100)) {
            Assert.assertEquals("blurred art is darkened at $x,$y", 102f, android.graphics.Color.red(bitmap.getPixel(x, y)).toFloat(), 1f)
        }
        for ((x, y) in listOf(20 to 20, 1800 to 1070)) {
            Assert.assertTrue("blurred art covers the corner $x,$y", android.graphics.Color.red(bitmap.getPixel(x, y)) > 40)
        }
        bitmap.recycle()
    }

    @Test fun heroFrameIsTopEndAndStaysStillWhileTheBlurFadesIn() {
        show()
        fun frame(tag: String): androidx.compose.ui.geometry.Rect {
            val coordinates = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
            val topLeft = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
            val bottomRight = coordinates.localToRoot(androidx.compose.ui.geometry.Offset(
                coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
            return androidx.compose.ui.geometry.Rect(topLeft, bottomRight)
        }
        val initial = frame("details-hero")
        Assert.assertEquals(202f, initial.left / 2f, .5f)
        Assert.assertEquals(0f, initial.top, .5f)
        Assert.assertEquals(758f, initial.width / 2f, .5f)
        Assert.assertEquals(426.375f, initial.height / 2f, .5f)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(1000)
        // The corner frame stays still (no zoom) and the blurred backdrop is full-screen.
        Assert.assertEquals(initial, frame("details-hero"))
        val blurred = frame("details-hero-blurred")
        Assert.assertEquals(androidx.compose.ui.geometry.Rect(0f, 0f, 1920f, 1080f), blurred)
    }

    @Test fun warmPlaybackShowsNoArtSoTheVideoStaysTheBackground() {
        show(warm = true)
        capture("warm-playback", "u15-details-layout")
        compose.onNodeWithTag("details-hero").assertDoesNotExist()
        compose.onNodeWithTag("details-hero-blurred").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("details-hero-blurred").assertDoesNotExist()
    }

    @Test fun longTitleUsesOneLineAcrossThePageWithinTheKeylines() {
        show(longTitle = true)
        val title = compose.onNodeWithTag("details-title")
        val bounds = title.getUnclippedBoundsInRoot()
        Assert.assertTrue((bounds.right - bounds.left).value > 470f)
        Assert.assertEquals(58f, bounds.left.value, .5f)
        Assert.assertTrue(bounds.right.value <= 902f)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        title.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        Assert.assertEquals(1, layouts.single().lineCount)
        capture("details-wide-one-line-title")
    }

    @Test fun shortHeaderHasOnlyTheNormalGapBeforeAirings() {
        show(heroVariant = 0)
        val actions = compose.onNodeWithTag("details-actions").getUnclippedBoundsInRoot()
        // Measure the section container, before the quiet heading's existing baseline translation.
        val section = compose.onNodeWithText("Other airings of Ridge Light").fetchSemanticsNode()
            .layoutInfo.coordinates.parentLayoutCoordinates!!
        val sectionTop = section.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y / 2f
        Assert.assertEquals(32f, sectionTop - actions.bottom.value, .5f)
        compose.onNodeWithTag("details-card-2").assertIsDisplayed()
        capture("short-header-natural-airings")
    }

    @Test fun airingTextGapsAndPiconPlateMatchTheKit() {
        show()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        val simple = compose.onNodeWithTag("details-card-subtitle-2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val episode = compose.onNodeWithTag("details-card-subtitle-4", useUnmergedTree = true).getUnclippedBoundsInRoot()
        Assert.assertEquals((simple.bottom - simple.top).value * 2 + 4f, (episode.bottom - episode.top).value, .5f)
        val time = compose.onAllNodesWithTag("details-card-time", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        Assert.assertEquals(4f, (simple.top - time.bottom).value, .5f)
        val plate = compose.onNodeWithTag("details-card-plate-4", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // The plate keeps its size; the picon gets the padding inside it.
        Assert.assertEquals(56f, (plate.right - plate.left).value, .5f)
        Assert.assertEquals(32f, (plate.bottom - plate.top).value, .5f)
        val card = compose.onNodeWithTag("details-card-4").getUnclippedBoundsInRoot()
        Assert.assertEquals(8f, (plate.left - card.left).value, .5f)
        Assert.assertEquals(8f, (card.bottom - plate.bottom).value, .5f)
    }

    @Test fun subtitleHierarchyAndAccessibleRatingBadge() {
        show(withRating = true)
        val title = compose.onNodeWithText("Ridge Light", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val subtitle = compose.onNodeWithTag("details-subtitle").getUnclippedBoundsInRoot()
        val metadata = compose.onNodeWithTag("details-metadata-row").getUnclippedBoundsInRoot()
        Assert.assertEquals(4f, (subtitle.top - title.bottom).value, .5f)
        Assert.assertEquals(8f, (metadata.top - subtitle.bottom).value, .5f)
        compose.onNodeWithTag("details-rating").assertContentDescriptionEquals("Age rating 12+")
        compose.onNodeWithTag("details-metadata").assertTextEquals("120m · Documentary · S2\u00a0E3")
        capture("top-subtitle-rating")
    }

    @Test fun cutSynopsisHasBoldMoreAndGrowsWithoutTouchingButtons() {
        show(longSynopsis = true)
        val panel = compose.onNodeWithTag("details-about-description")
        val before = compose.onNodeWithTag("details-synopsis", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        panel.assertIsFocused()
        compose.mainClock.advanceTimeBy(1000)
        val after = compose.onNodeWithTag("details-synopsis", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        Assert.assertEquals(1.05f, after.width / before.width, .005f)
        Assert.assertTrue(after.bottom < compose.onNodeWithTag("details-actions").fetchSemanticsNode().boundsInRoot.top)
        val texts = compose.onNodeWithTag("details-synopsis", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text]
        val text = texts.single()
        Assert.assertTrue(text.text.endsWith("… More"))
        Assert.assertTrue(text.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold &&
            text.text.substring(it.start, it.end) == "More" })
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("details-synopsis", useUnmergedTree = true).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        Assert.assertEquals(2, layouts.single().lineCount)
        Assert.assertFalse(layouts.single().hasVisualOverflow)
        capture("about-focused-cut-more")
    }

    @Test fun fittingSynopsisHasNoMoreHint() {
        show(summaryOnly = true)
        val text = compose.onNodeWithTag("details-synopsis", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        Assert.assertEquals(event.summary, text.text)
        Assert.assertTrue(text.spanStyles.isEmpty())
    }

    @Test fun shortHeroUsesFixedSectionKeylines() = fixedSectionKeylines(0)
    @Test fun mediumHeroUsesFixedSectionKeylines() = fixedSectionKeylines(1)
    @Test fun longHeroUsesFixedSectionKeylines() = fixedSectionKeylines(2)

    private fun fixedSectionKeylines(variant: Int) {
        show(heroVariant = variant)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        Assert.assertEquals(160f, compose.onNodeWithText("Other airings of Ridge Light").getUnclippedBoundsInRoot().top.value, .5f)
        Assert.assertEquals(72f, compose.onNodeWithTag("details-actions").getUnclippedBoundsInRoot().top.value, .5f)
        capture("airings-active-${listOf("short", "medium", "long")[variant]}")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        Assert.assertEquals(160f, compose.onNodeWithText("Details").getUnclippedBoundsInRoot().top.value, .5f)
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
        capture("details-active-${listOf("short", "medium", "long")[variant]}")
    }


    // The first screen's block keeps one bottom anchor (540 - 96 peek - 32 gap) and grows upward; the
    // next section's heading and the top of its cards peek below it.
    private fun assertBottomAnchored(peek: Boolean = true) {
        Assert.assertEquals(412f, compose.onNodeWithTag("details-actions").getUnclippedBoundsInRoot().bottom.value, 1f)
        Assert.assertTrue(compose.onNodeWithTag("details-information").getUnclippedBoundsInRoot().top.value >= 48f - .5f)
        compose.onNodeWithTag("details-next-hint", useUnmergedTree = true).assertDoesNotExist()
        if (!peek) return
        Assert.assertTrue(compose.onNodeWithText("Other airings of", substring = true).getBoundsInRoot().bottom.value <= 540f - 48f)
        val card = compose.onNodeWithTag("details-card-2").getUnclippedBoundsInRoot()
        Assert.assertTrue("$card", card.top.value <= 540f - 32f)
    }
    @Test fun shortProgrammeBlockSitsAtTheBottomAnchorWithTheAiringsPeeking() {
        show(heroVariant = 0); assertBottomAnchored(); capture("short-programme", "u15-details-layout")
    }
    @Test fun longProgrammeBlockGrowsUpwardFromTheSameAnchor() {
        show(longMeta = true)
        val metadata = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("details-metadata").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(metadata) }
        Assert.assertEquals(2, metadata.single().lineCount)
        assertBottomAnchored(); capture("long-title-two-line-metadata", "u15-details-layout")
    }
    @Test fun recordingWithoutLowerSectionsKeepsTheSameAnchor() { show(recording = true); assertBottomAnchored(peek = false) }

    @Test fun oldRecordingHasTwoLineEllipsizedSubtitleAndDatedProgress() {
        show(recording = true, longSubtitle = true, pastYear = true)
        assertTwoLineSubtitle()
        compose.onNodeWithTag("details-metadata").assertTextContains("2025", substring = true)
        assertBottomAnchored(peek = false)
        capture("old-recording-long-subtitle", "u15-details-layout")
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanTwoLineSubtitleKeepsTheAiringsPeek() {
        show(longSubtitle = true)
        assertTwoLineSubtitle()
        assertBottomAnchored(peek = false)
        Assert.assertTrue(compose.onNodeWithText("Weitere Ausstrahlungen von", substring = true)
            .getBoundsInRoot().bottom.value <= 492f)
        Assert.assertTrue(compose.onNodeWithTag("details-card-2").getUnclippedBoundsInRoot().top.value <= 508f)
        capture("two-line-subtitle-de", "u15-details-layout")
    }

    @Test fun recordingDeleteChipAndFactsKeepTheAnchorWithoutAirings() {
        show(recording = true, recordingFacts = true, longSubtitle = true)
        compose.onNodeWithTag("details-resume").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-delete").assertIsFocused()
        compose.onNodeWithText("Available until Tue, Sep 22").assertExists()
        assertBottomAnchored(peek = false)
        assertTwoLineSubtitle()
        compose.onNodeWithTag("details-airings").assertDoesNotExist()
        capture("recording-delete-chip", "u15-recording-round2")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
        compose.onNodeWithText("HD 1080", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("No errors", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("English", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("German", useUnmergedTree = true).assertExists()
        capture("recording-facts-cast", "u15-recording-round2")
    }

    @OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun recordingFactsReuseTrackLanguagesAndHideUnknownAndDefaultProfile() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val profile = DvrConfiguration(DvrConfigId("default"), "", "")
        val entry = DvrEntry.create(DvrEntryId(1), configId = profile.id,
            removalDays = RECORDING_REMOVAL_WHEN_SPACE_NEEDED,
            files = listOf(DvrRecordingFile.create(streams = listOf("deu", "ger", "eng").map {
                DvrRecordingStream(at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType.AAC, language = it)
            })))
        fun facts(locale: Locale) = at.bernhardberger.tvhplayer.ui.components.recordingMetadataFacts(
            entry, listOf(profile), context, locale, start.epochSeconds).toMap()
        Assert.assertEquals("German, English", facts(Locale.US)["Audio"])
        Assert.assertEquals("Deutsch, Englisch", facts(Locale.GERMAN)["Audio"])
        Assert.assertEquals("Kept until space is needed", facts(Locale.US)["Retention"])
        Assert.assertFalse(facts(Locale.US).containsKey("Errors"))
        Assert.assertFalse(facts(Locale.US).containsKey("Recording profile"))
        Assert.assertTrue(at.bernhardberger.tvhplayer.ui.components.programmeCreditFacts(null, context.resources).isEmpty())
        val credited = EpgEvent.create(event.id, channel.id, start, event.stop, credits = listOf(
            EpgCredit("Director One", EpgCreditRole.DIRECTOR), EpgCredit("Actor One", EpgCreditRole.ACTOR),
            EpgCredit("Actor Two", EpgCreditRole.ACTOR), EpgCredit("Actor Two", EpgCreditRole.ACTOR),
            EpgCredit("Actor One", EpgCreditRole.DIRECTOR), EpgCredit("Director Two", EpgCreditRole.DIRECTOR)))
        Assert.assertEquals(listOf("Cast" to "Actor One, Actor Two", "Directors" to "Director One, Actor One, Director Two"),
            at.bernhardberger.tvhplayer.ui.components.programmeCreditFacts(credited, context.resources))
    }

    @Test fun castIsAbbreviatedInFactsAndFullInTheSharedReader() {
        show(cast = true, noAirings = true)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
        val names = "Mara Vale, Ellis North, Robin Stone, Jules Brook, Taylor Ridge"
        fun layouts(): androidx.compose.ui.text.TextLayoutResult {
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(names, useUnmergedTree = true).performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }
        Assert.assertEquals(2, layouts().lineCount)
        Assert.assertTrue(layouts().isLineEllipsized(1))
        capture("programme-cast", "u15-recording-round2")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        Assert.assertTrue(layouts().lineCount > 2)
        Assert.assertFalse(layouts().hasVisualOverflow)
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-facts-panel").assertIsFocused()
    }

    private fun assertTwoLineSubtitle() {
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("details-subtitle").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        Assert.assertEquals(2, layouts.single().lineCount)
        Assert.assertTrue(layouts.single().isLineEllipsized(1))
        val bounds = compose.onNodeWithTag("details-subtitle").getUnclippedBoundsInRoot()
        Assert.assertTrue((bounds.right - bounds.left).value <= 470f)
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeTextLongProgrammeNamesTheAiringsInAHintInsteadOfAPeek() {
        show(longMeta = true, fontScale = 1.3f)
        val information = compose.onNodeWithTag("details-information").getUnclippedBoundsInRoot()
        val actions = compose.onNodeWithTag("details-actions").getUnclippedBoundsInRoot()
        val hint = compose.onNodeWithTag("details-next-hint", useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot()
        Assert.assertTrue("$information", information.top.value >= 48f - .5f)
        Assert.assertEquals("hint 24dp above the screen bottom", 516f, hint.bottom.value, 1f)
        Assert.assertTrue("$hint below $actions", hint.top.value >= actions.bottom.value + 12f)
        // The section itself starts below the first screen; the hint names it.
        Assert.assertTrue(compose.onNodeWithTag("details-card-2").getUnclippedBoundsInRoot().top.value >= 540f)
        // Long German text at 1.3 stays inside the block: the cut About text keeps its "Mehr", the rest stays on screen.
        val synopsis = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("details-synopsis", useUnmergedTree = true).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(synopsis) }
        Assert.assertFalse(synopsis.single().hasVisualOverflow)
        listOf("details-watch", "details-record", "details-show_guide").forEach {
            Assert.assertTrue(it, compose.onNodeWithTag(it).getUnclippedBoundsInRoot().right.value <= 960f - 48f)
        }
        capture("long-title-two-line-metadata-de-1.3", "u15-details-layout")
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertIsFocused().assertIsDisplayed()
        Assert.assertEquals(160f, compose.onNodeWithText("Weitere Ausstrahlungen von", substring = true)
            .getUnclippedBoundsInRoot().top.value, .5f)
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onNodeWithTag("details-next-hint", useUnmergedTree = true).assertIsDisplayed()
    }

    @OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    private fun show(recording: Boolean = false, future: Boolean = false, bright: Boolean = false, longText: Boolean = false,
        fontScale: Float = 1f, onClose: () -> Unit = {}, onRelated: (EpgEvent) -> Unit = {}, rail: Boolean = false,
        noAirings: Boolean = false, realRail: Boolean = false, richFacts: Boolean = false, returnToCard: EventId? = null, summaryOnly: Boolean = false,
        factsOnly: Boolean = false, longTitle: Boolean = false, noDescription: Boolean = false, heroVariant: Int? = null,
        withRating: Boolean = false, longSynopsis: Boolean = false, todayCard: Boolean = false, longFacts: Boolean = false, runningCard: Boolean = false,
        warm: Boolean = false, longMeta: Boolean = false, longSubtitle: Boolean = false, pastYear: Boolean = false,
        recordingFacts: Boolean = false, cast: Boolean = false) {
        val subtitle = if (longSubtitle && recordingFacts) "An extraordinary journey through the snowy mountains of the north, along the coast and across the wide valleys in the changing light of a long winter."
            else if (longSubtitle) "Eine außergewöhnliche Reise durch die verschneiten Berge des Nordens, entlang der Küste und über die weiten Täler im wechselnden Licht des langen Winters." else event.subtitle
        val shown = when {
            recordingFacts || cast -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title,
                subtitle = subtitle, image = event.image, summary = event.summary, description = event.description,
                credits = listOf(EpgCredit("Mara Vale, Ellis North, Robin Stone, Jules Brook, Taylor Ridge", EpgCreditRole.ACTOR),
                    EpgCredit("Rowan Frost", EpgCreditRole.GUEST), EpgCredit("Avery Lake", EpgCreditRole.PRESENTER),
                    EpgCredit("Morgan Pine", EpgCreditRole.DIRECTOR), EpgCredit("Alex Winter", EpgCreditRole.WRITER)))
            longSubtitle -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title,
                subtitle = subtitle, genre = event.genre, image = event.image, summary = event.summary, description = event.description)
            longMeta -> EpgEvent.create(event.id, channel.id, start, event.stop,
                title = "Ridge Light: Across the Frozen North and the Polar Sea", subtitle = event.subtitle,
                genre = "Documentary and natural history about northern landscapes", episode = event.episode,
                copyrightYear = 2024, rating = EpgRating(12, null, null, null, null, null), image = event.image,
                summary = event.summary!!.repeat(3), description = event.description)
            longFacts -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title,
                keywords = listOf("Mountain ".repeat(100).trim()))
            withRating || longSynopsis -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title,
                subtitle = event.subtitle, genre = event.genre, episode = event.episode, image = event.image,
                rating = EpgRating(12, null, null, null, null, null),
                summary = if (longSynopsis) event.summary!!.repeat(6) else event.summary, description = event.description)
            heroVariant != null -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title,
                subtitle = event.subtitle.takeIf { heroVariant == 2 }, genre = event.genre,
                summary = event.summary.takeIf { heroVariant > 0 }, description = event.description.takeIf { heroVariant > 0 })
            longTitle -> EpgEvent.create(event.id, channel.id, start, event.stop,
                title = "Ridge Light: Across the Frozen North", subtitle = event.subtitle,
                summary = event.summary, description = event.description, genre = event.genre, image = event.image)
            factsOnly -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title, genre = event.genre)
             richFacts -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title, genre = event.genre,
                 summary = event.summary.takeUnless { noDescription }, description = event.description.takeUnless { noDescription },
                 episode = event.episode, copyrightYear = 2024,
                firstAired = Instant.parse("2024-01-01T12:00:00Z"), isNew = false,
                categories = listOf("Nature"), keywords = listOf("Mountain", "Winter"))
            future -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title)
            summaryOnly -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title,
                genre = event.genre, summary = event.summary)
            bright -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title, subtitle = event.subtitle,
                summary = event.summary, genre = event.genre, image = ArtworkId(4))
            longText -> EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title, genre = event.genre,
                image = event.image, summary = event.summary, description = event.description!!.repeat(12))
            else -> event
        }
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = event.title,
            removalDays = 7.takeIf { recordingFacts }?.toLong(),
            dataSizeBytes = 2_500_000_000.takeIf { recordingFacts }, configId = DvrConfigId("archive").takeIf { recordingFacts },
            streamErrors = 0.takeIf { recordingFacts }?.toLong(), dataErrors = 0.takeIf { recordingFacts }?.toLong(),
            startExtraMinutes = 120.seconds.takeIf { recordingFacts }, stopExtraMinutes = 300.seconds.takeIf { recordingFacts },
            files = if (recordingFacts) listOf(DvrRecordingFile.create(streams = listOf(
                DvrRecordingStream(at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType.H264, height = 1080),
                DvrRecordingStream(at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType.AAC, language = "eng"),
                DvrRecordingStream(at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType.DVB_SUBTITLE, language = "deu")))) else null,
            start = if (pastYear) Instant.parse("2025-09-15T19:00:00Z") else start,
            stop = if (pastYear) Instant.parse("2025-09-15T21:00:00Z") else event.stop,
            subtitle = subtitle.takeIf { longSubtitle }, image = event.image.takeUnless { future }, summary = event.summary.takeUnless { future },
            playPosition = 3723.seconds, state = if (future) DvrEntryState.SCHEDULED else DvrEntryState.COMPLETED)
        val now = start.epochSeconds + if (future) -60 else 480
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale),
                at.bernhardberger.tvhplayer.ui.LocalWarmPlayback provides warm) {
                TVHeadendPlayerTheme {
                    back = requireNotNull(androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    view = LocalView.current
                    val railFocus = androidx.compose.runtime.remember { FocusRequester() }
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onKeyEvent {
                        if (rail && it.key == Key.DirectionLeft) {
                            if (it.type == KeyEventType.KeyDown) railFocus.requestFocus()
                            true
                        } else false
                    }) {
                        if (rail) androidx.tv.material3.Button({}, Modifier.focusRequester(railFocus).testTag("rail")) {
                            androidx.tv.material3.Text("Rail")
                        }
                        val details: @androidx.compose.runtime.Composable () -> Unit = {
                        ProgrammeDetailsPage(shown.takeUnless { recording }, entry.takeIf { recording || future }, channel, now,
                            if (recording) recordingDetailsActions(entry, true, true, true)
                            else programmeDetailsActions(shown, now, entry.takeIf { future }, true, true, false),
                              loader, session, {}, onClose, related = if (recording || noAirings) emptyList() else if (runningCard)
                                  listOf(EpgEvent.create(EventId(2), otherChannel.id, start, event.stop, title = event.title)) else if (todayCard)
                                  listOf(EpgEvent.create(EventId(2), channel.id, start + 3600.seconds, event.stop, title = event.title)) else airings,
                            onRelated = onRelated, channelOf = { listOf(channel, otherChannel).firstOrNull { c -> c.id == it } },
                            returnToCard = returnToCard, recordingEvent = shown.takeIf { recordingFacts },
                            recordingConfigurations = listOf(DvrConfiguration(DvrConfigId("default"), "", ""),
                                DvrConfiguration(DvrConfigId("archive"), "Archive", "")))
                        }
                        if (realRail) at.bernhardberger.tvhplayer.ui.components.SideRail(
                            currentRoute = AppDestination.CHANNELS, showEpgMenu = true, onRootBack = {}, onNavigate = {},
                        ) { _, _ -> details() } else details()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String, folder: String = "u15-about") {
        val bitmap = snapshot()
        val directory = File("build/outputs/$folder").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun snapshot(settle: Boolean = true): Bitmap {
        if (settle) compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        return bitmap
    }
}
