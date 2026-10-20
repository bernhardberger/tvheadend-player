package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
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
@Category(VisualCapture::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelsLayoutCaptureTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val fixture = ChannelsLayoutFixture(compose, images)

    private fun card(id: Int) = hasTestTag("channel-card-$id")
    private fun row(id: Int) = hasTestTag("channel-row-$id")
    private val groupButton = hasTestTag("channels-group-button")
    private val viewButton = hasTestTag("channels-view-button")

    private fun Boolean.slug() = if (this) "programme" else "logo"

    @Test fun list() {
        fixture.show(ChannelsView(ChannelsLayout.LIST), fixture.fixtureArt())
        capture("list-1-first-row", row(1))
        fixture.key(Key.DirectionDown)
        capture("list-2-long-names", row(2))
        repeat(6) { fixture.key(Key.DirectionDown) }
        capture("list-3-no-epg", row(8))
        repeat(7) { fixture.key(Key.DirectionUp) }
        fixture.key(Key.DirectionUp)
        capture("list-4-header-group-button", groupButton)
    }

    @Test fun listSingleGroup() {
        fixture.show(ChannelsView(ChannelsLayout.LIST), fixture.fixtureArt(), tags = false)
        capture("list-5-single-group", row(1))
    }

    // One composition per test: every layout x option combination is its own test.
    @Test fun gridProgramme() = grid(cardArt = true)
    @Test fun gridLogo() = grid(cardArt = false)
    @Test fun immersiveProgramme() = immersive(cardArt = true)
    @Test fun immersiveLogo() = immersive(cardArt = false)
    @Test fun viewOptionsList() = viewOptions(ChannelsLayout.LIST)
    @Test fun viewOptionsGrid() = viewOptions(ChannelsLayout.GRID)
    @Test fun viewOptionsImmersive() = viewOptions(ChannelsLayout.IMMERSIVE)

    private fun grid(cardArt: Boolean) {
        val columns = ChannelsGridColumns
        val picture = cardArt
        fixture.show(ChannelsView(ChannelsLayout.GRID, cardArt), fixture.fixtureArt())
        capture("grid-$columns-${picture.slug()}-1-first-card", card(1))
        fixture.key(Key.DirectionRight)
        capture("grid-$columns-${picture.slug()}-2-long-names", card(2))
        repeat(2) { fixture.key(Key.DirectionDown) }
        capture("grid-$columns-${picture.slug()}-3-no-epg", card(2 + 2 * columns))
    }

    private fun immersive(cardArt: Boolean) {
        val picture = cardArt
        fixture.show(ChannelsView(ChannelsLayout.IMMERSIVE, cardArt), fixture.fixtureArt())
        capture("immersive-${picture.slug()}-1-first-card", card(1))
        fixture.key(Key.DirectionRight)
        capture("immersive-${picture.slug()}-2-long-names", card(2))
        repeat(4) { fixture.key(Key.DirectionRight) }
        capture("immersive-${picture.slug()}-3-no-art", card(6))
        repeat(2) { fixture.key(Key.DirectionRight) }
        capture("immersive-${picture.slug()}-4-no-epg-logo-fallback", card(8))
        fixture.key(Key.DirectionUp)
        fixture.key(Key.DirectionCenter)
        repeat(2) { fixture.key(Key.DirectionDown) }
        fixture.awaitFocus(hasTestTag("channels-group-2"))
        fixture.key(Key.DirectionCenter)
        capture("immersive-${picture.slug()}-5-other-group", card(7))
    }

    @Test fun groupPickerAndEmptyGroup() {
        fixture.show(ChannelsView(ChannelsLayout.GRID), fixture.fixtureArt(), emptyGroup = true)
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionUp)
        fixture.key(Key.DirectionCenter)
        capture("panel-1-group-picker", hasTestTag("channels-group-all"))
        repeat(3) { fixture.key(Key.DirectionDown) }
        fixture.key(Key.DirectionCenter)
        capture("panel-2-empty-group", hasTestTag("channels-show-all"))
    }

    private fun viewOptions(layout: ChannelsLayout) {
        fixture.show(ChannelsView(layout), fixture.fixtureArt())
        fixture.awaitFocus(if (layout == ChannelsLayout.LIST) row(1) else card(1))
        fixture.key(Key.DirectionUp)
        fixture.key(Key.DirectionRight)
        fixture.key(Key.DirectionCenter)
        capture("panel-3-view-options-${layout.name.lowercase()}", hasTestTag("channels-view-layout-${layout.name.lowercase()}"))
    }

    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    @Test fun germanLargeTextHeaderAndPanels() {
        fixture.show(ChannelsView(ChannelsLayout.GRID), fixture.fixtureArt(), manyTags = true, scale = 1.3f)
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionUp)
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(hasTestTag("channels-group-all"))
        fixture.key(Key.DirectionDown)
        fixture.key(Key.DirectionDown)
        fixture.key(Key.DirectionCenter)
        // Nachrichten und Wetter: channels 1, 8 and 15.
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionUp)
        capture("de-1.3-1-header", groupButton, locale = "de", fontScale = 1.3f)
        fixture.key(Key.DirectionCenter)
        capture("de-1.3-2-group-picker", hasTestTag("channels-group-2"), locale = "de", fontScale = 1.3f)
        fixture.key(Key.Back)
        fixture.awaitFocus(groupButton)
        fixture.key(Key.DirectionRight)
        fixture.key(Key.DirectionCenter)
        capture("de-1.3-3-view-options", hasTestTag("channels-view-layout-grid"), locale = "de", fontScale = 1.3f)
    }

    private fun capture(name: String, focus: SemanticsMatcher, locale: String = "en", fontScale: Float = 1f) {
        fixture.awaitFocus(focus)
        // The background follows focus after a pause and a fade.
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val bitmap = draw()
        compose.waitForIdle()
        val settled = draw()
        assertTrue("$name captured mid-animation", bitmap.sameAs(settled))
        settled.recycle()
        val directory = File("build/outputs/u4-channel-views").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        File(directory, "$name.txt").writeText(
            """
            canvas=960x540 dp; density=1.0 (mdpi); locale=$locale; fontScale=$fontScale
            shell=TVHeadendPlayerTheme + SideRail; background=theme background, no video
            view=${fixture.channelsView}
            focus=${focus.description} reached by D-pad
            art=fictional fixture pack; channels 8-18 picon-only without EPG; channel 6 EPG without art
            evidenceLimit=static composition only; not physical-TV focus feel, motion or overscan
            """.trimIndent() + "\n",
        )
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
