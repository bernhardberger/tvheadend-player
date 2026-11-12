package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import org.junit.Assert.assertTrue
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Prototype review captures of each Channels layout, option and panel; static composition only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi",
    instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelsLayoutCaptureTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val fixture = ChannelsLayoutFixture(compose, images)
    private var backdropChannel = ChannelId(1)

    @org.junit.Before fun freezeSceneClock() {
        // Match the presentation scene so captures compare channel titles, not wall-clock drift.
        assertTrue(android.os.SystemClock.setCurrentTimeMillis(1_791_144_900_000L))
    }

    /** The README scene: Immersive with the app's default view, Ridge Light on air under a 20:15 clock, xhdpi. */
    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun presentation() = presentation("channels", ChannelsView(), card(1))

    /** Store lead: a live match, recording badge and the surrounding evening lineup. */
    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-rUS-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun storeChannels() = atEvening {
        fixture.show(ChannelsView(), fixture.fixtureArt(), presentation = true, recordingNow = 2, scheduledNext = 1)
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionRight)
        capture("store-channels", card(2), locale = "en-US", outputs = "build/outputs/presentation")
    }

    /** The same evening as a grid with View options → Program art on, so the cards carry the program pictures. */
    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun presentationGrid() = presentation("channels-grid",
        ChannelsView(ChannelsLayout.GRID, programArt = setOf(ChannelsLayout.IMMERSIVE, ChannelsLayout.GRID)), card(1))

    /** The same evening as a list. */
    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun presentationList() = presentation("channels-list", ChannelsView(ChannelsLayout.LIST), row(1))

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun portraitArtwork() = atEvening {
        fixture.show(ChannelsView(ChannelsLayout.LIST), fixture.fixtureArt(portrait = true), presentation = true,
            portrait = true, playerReturn = PlayerReturnFocus(ChannelId(6), ChannelId(6)))
        capture("channels-list-portrait-artwork", row(6), outputs = "build/outputs/presentation")
    }

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun portraitArtworkImmersive() = atEvening {
        fixture.show(ChannelsView(), fixture.fixtureArt(portrait = true), presentation = true,
            portrait = true, playerReturn = PlayerReturnFocus(ChannelId(6), ChannelId(6)))
        capture("channels-immersive-portrait-artwork", card(6), outputs = "build/outputs/presentation")
    }

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun presentationListWithoutChannelNumbers() = atEvening {
        try {
            at.bernhardberger.tvhplayer.ui.components.AppChannelNumbers.shown = false
            fixture.show(ChannelsView(ChannelsLayout.LIST), fixture.fixtureArt(), presentation = true,
                recordingNow = 2, scheduledNext = 1)
            capture("channels-list-no-numbers", row(1), outputs = "build/outputs/presentation")
        } finally {
            at.bernhardberger.tvhplayer.ui.components.AppChannelNumbers.shown = true
        }
    }

    /** Immersive with program pictures switched off: how the screen looks when the guide has no art. */
    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun presentationLogos() = presentation("channels-logos", ChannelsView(programArt = emptySet()), card(1))

    /**
     * Back from the player, Northline News keeps playing behind Channels. Left past Ridge Earth's
     * card opens the side menu on Channels, its now-playing header above.
     */
    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun presentationDrawer() = atEvening {
        fixture.show(ChannelsView(), fixture.fixtureArt(), presentation = true, recordingNow = 2, scheduledNext = 1,
            warmPlayback = true, playerReturn = PlayerReturnFocus(ChannelId(3), ChannelId(3)),
            video = FixtureArt.art("still-northline-studio"), playingLive = ChannelId(3))
        fixture.awaitFocus(card(3))
        repeat(2) { fixture.key(Key.DirectionLeft) }
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionLeft)
        capture("drawer", hasTestTag("nav-channels"), outputs = "build/outputs/presentation", background = false)
    }

    private fun presentation(name: String, view: ChannelsView, focus: SemanticsMatcher) = atEvening {
        // Harbor Kickoff is recording and Tide Watch, next on Ridge Earth, is scheduled, as in the Guide.
        fixture.show(view, fixture.fixtureArt(), presentation = true, recordingNow = 2, scheduledNext = 1)
        capture(name, focus, outputs = "build/outputs/presentation")
    }

    /** Ridge Light on air under a 20:15 clock. */
    private fun atEvening(block: () -> Unit) {
        val zone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            assertTrue(android.os.SystemClock.setCurrentTimeMillis(1_791_144_900_000L))
            block()
        } finally {
            java.util.TimeZone.setDefault(zone)
        }
    }

    private fun card(id: Int) = hasTestTag("channel-card-$id")
    private fun row(id: Int) = hasTestTag("channel-row-$id")
    private val tagsButton = hasTestTag("channels-tags-button")

    private fun Boolean.slug() = if (this) "programme" else "logo"

    @Test @Category(VisualCapture::class) fun list() {
        fixture.show(ChannelsView(ChannelsLayout.LIST), fixture.fixtureArt())
        capture("list-1-first-row", row(1))
        fixture.key(Key.DirectionDown)
        capture("list-2-long-names", row(2))
        repeat(4) { fixture.key(Key.DirectionDown) }
        capture("list-6-no-art", row(6))
        repeat(2) { fixture.key(Key.DirectionDown) }
        capture("list-3-no-epg", row(8))
        repeat(7) { fixture.key(Key.DirectionUp) }
        fixture.key(Key.DirectionUp)
        capture("list-4-header-group-button", tagsButton)
    }

    // One composition per test: every layout x option combination is its own test.
    @Test @Category(VisualCapture::class) fun gridProgramme() = grid(cardArt = true)
    @Test @Category(VisualCapture::class) fun immersiveProgramme() = immersive(cardArt = true)
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun rowRailOverlap() {
        fixture.show(ChannelsView(ChannelsLayout.IMMERSIVE, setOf(ChannelsLayout.IMMERSIVE)), fixture.fixtureArt(), presentation = true)
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(2))
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithTag("channels-immersive-background").assertExists()
        compose.onNodeWithTag("channels-immersive-art").assertExists()
        val bitmap = draw()
        compose.waitForIdle()
        val settled = draw()
        assertTrue("row-rail-overlap captured mid-animation", bitmap.sameAs(settled))
        bitmap.recycle()
        settled.recycle()
        val bounds = compose.onNode(card(2)).fetchSemanticsNode().boundsInRoot
        assertTrue("focused card clears mask", bounds.left >= 260f)
    }

    private fun grid(cardArt: Boolean, slug: String = "grid") {
        val columns = ChannelsGridColumns
        val picture = cardArt
        fixture.show(ChannelsView(ChannelsLayout.GRID, setOfNotNull(ChannelsLayout.GRID.takeIf { cardArt })), fixture.fixtureArt())
        capture("$slug-$columns-${picture.slug()}-1-first-card", card(1))
        fixture.key(Key.DirectionRight)
        capture("$slug-$columns-${picture.slug()}-2-long-names", card(2))
        repeat(2) { fixture.key(Key.DirectionDown) }
        capture("$slug-$columns-${picture.slug()}-3-no-epg", card(2 + 2 * columns))
    }

    private fun immersive(cardArt: Boolean, slug: String = "immersive") {
        val picture = cardArt
        fixture.show(ChannelsView(ChannelsLayout.IMMERSIVE, setOfNotNull(ChannelsLayout.IMMERSIVE.takeIf { cardArt })), fixture.fixtureArt())
        capture("$slug-${picture.slug()}-1-first-card", card(1))
        fixture.key(Key.DirectionRight)
        capture("$slug-${picture.slug()}-2-long-names", card(2))
        repeat(4) { fixture.key(Key.DirectionRight) }
        capture("$slug-${picture.slug()}-3-no-art", card(6))
        repeat(2) { fixture.key(Key.DirectionRight) }
        capture("$slug-${picture.slug()}-4-no-epg-logo-fallback", card(8))
        fixture.key(Key.DirectionUp)
        fixture.key(Key.DirectionCenter)
        repeat(2) { fixture.key(Key.DirectionDown) }
        fixture.awaitFocus(hasTestTag("channels-tags-2"))
        fixture.key(Key.DirectionCenter)
        capture("$slug-${picture.slug()}-5-other-group", card(7))
    }

    private fun capture(name: String, focus: SemanticsMatcher, locale: String = "en", fontScale: Float = 1f,
        outputs: String = "build/outputs/u4-channel-views", background: Boolean = true) {
        fixture.awaitFocus(focus)
        compose.onNode(focus).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TestTag)
            ?.removePrefix("channel-card-")?.toLongOrNull()?.let { backdropChannel = ChannelId(it) }
        // Browse, image loading and glow baking run on the fixture's controlled Main context.
        // Finish the incoming headline before checking its expected artwork branch.
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        if (fixture.channelsView.layout == ChannelsLayout.IMMERSIVE && background) {
            compose.onNodeWithTag("channels-immersive-background").assertExists()
            val art = fixture.programmeArt(backdropChannel)
                .takeIf { ChannelsLayout.IMMERSIVE in fixture.channelsView.programArt }
            if (art != null) {
                compose.onNodeWithTag("channels-immersive-art").assertExists()
            } else {
                compose.onNodeWithTag("channels-logo-glow").assertExists()
                compose.onNodeWithTag("channels-immersive-picon").assertExists()
            }
        }
        val bitmap = draw()
        compose.waitForIdle()
        val settled = draw()
        assertTrue("$name captured mid-animation", bitmap.sameAs(settled))
        settled.recycle()
        val directory = File(outputs)
        CaptureOutput.writePng(File(directory, "$name.png"), bitmap)
        CaptureOutput.writeText(File(directory, "$name.txt")) {
            """
            canvas=960x540 dp; density=${fixture.context.resources.displayMetrics.density}; locale=$locale; fontScale=$fontScale
            shell=TVHeadendPlayerTheme + SideRail; background=theme background, no video
            view=${fixture.channelsView}
            focus=${focus.description} reached by D-pad
            art=fictional fixture pack; channels 8-18 picon-only without EPG; channel 6 EPG without art
            evidenceLimit=static composition only; not physical-TV focus feel, motion or overscan
            """.trimIndent() + "\n"
        }
        bitmap.recycle()
    }

    private fun draw(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = fixture.view.rootView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }
}
