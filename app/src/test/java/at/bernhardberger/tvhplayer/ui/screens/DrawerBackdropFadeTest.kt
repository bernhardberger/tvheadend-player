package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * With the drawer open, the Channels backdrop behind the drawer fades with the page as the drawer
 * moves between Channels and the Guide, rather than staying cut off until the fade ends. Pixels,
 * frame by frame, of the production Channels and drawer in the app's browse scene beside a bare Guide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerBackdropFadeTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val fixture = ChannelsLayoutFixture(compose, images)

    @Test fun behindTheOpenDrawerAProgrammePictureFadesWithItsPage() {
        val (channels, departure, guide, arrival) = moveBetweenChannelsAndGuide(ChannelId(1))
        val shown = distance(channels, guide, BehindDrawer)
        assertTrue("Channels shows its picture behind the drawer: $shown", shown > .03f)
        assertFades("leaving Channels", departure, channels, guide)
        assertFades("returning to Channels", arrival, guide, capture())
    }

    /**
     * The Kettle Hour has no programme art, so Channels shows its logo on a glow from the top right,
     * which leaves the page plain behind the drawer: no frame shows anything else there. The cards
     * before it run on under the drawer and come and go with the page's cards.
     */
    @Test fun behindTheOpenDrawerTheLogoFallbackLeavesThePagePlain() {
        val (channels, departure, guide, arrival) = moveBetweenChannelsAndGuide(ChannelId(6))
        (listOf(channels) + departure + arrival).forEachIndexed { frame, it ->
            val off = distance(it, guide, AboveCards)
            assertTrue("frame $frame shows a picture behind the drawer: $off", off < .01f)
            val page = distance(it, guide, PageCards)
            val behind = distance(it, guide, CardsBehindDrawer)
            if (page > .05f) assertTrue("frame $frame: cards %.3f behind the drawer, %.3f on the page".format(behind, page),
                behind > page / 4)
        }
    }

    /** Channels, the frames to the Guide, the Guide, and the frames back, with the drawer open. */
    private fun moveBetweenChannelsAndGuide(channel: ChannelId): Journey {
        fixture.show(ChannelsView(ChannelsLayout.IMMERSIVE, setOf(ChannelsLayout.IMMERSIVE)), fixture.fixtureArt(),
            playerReturn = PlayerReturnFocus(channel, channel), guide = true)
        fixture.awaitFocus(hasTestTag("channel-card-${channel.value}"))
        settle()
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(hasTestTag("channels-tags-button"))
        fixture.key(Key.DirectionLeft)
        fixture.awaitFocus(hasTestTag("nav-channels"))
        settle()
        val channels = capture()
        val departure = frames(Key.DirectionDown)
        fixture.awaitFocus(hasTestTag("nav-epg"))
        settle()
        val guide = capture()
        val arrival = frames(Key.DirectionUp)
        fixture.awaitFocus(hasTestTag("nav-channels"))
        settle()
        return Journey(channels, departure, guide, arrival)
    }

    private data class Journey(val channels: Bitmap, val departure: List<Bitmap>, val guide: Bitmap, val arrival: List<Bitmap>)

    /** Behind the drawer, the picture goes from [from] to [to] a little each frame, never in one step. */
    private fun assertFades(name: String, frames: List<Bitmap>, from: Bitmap, to: Bitmap) {
        val progress = listOf(0f) + frames.map { distance(it, from, BehindDrawer) / distance(to, from, BehindDrawer) }
        val log = progress.joinToString { "%.2f".format(it) }
        progress.zipWithNext().forEachIndexed { frame, (before, after) ->
            assertTrue("$name, frame ${frame + 1} jumps behind the drawer: $log", abs(after - before) < .35f)
        }
        assertTrue("$name never arrives behind the drawer: $log", abs(progress.last() - 1f) < .1f)
    }

    private fun frames(key: Key): List<Bitmap> {
        compose.mainClock.autoAdvance = false
        fixture.key(key)
        val frames = (1..30).map { advanceFrame(); capture() }
        compose.mainClock.autoAdvance = true
        return frames
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
    }

    private fun advanceFrame() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun capture(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(760, 410, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(-200f, -60f)
            // Preserve window coordinates while drawing only the bounding region of all probes.
            fixture.view.draw(canvas)
        }
        return bitmap
    }

    /** Mean colour distance over [region], given as x range and y ranges. */
    private fun distance(a: Bitmap, b: Bitmap, region: Region): Float {
        var sum = 0f
        var n = 0
        for (ys in region.ys) for (y in ys step 4) for (x in region.xs step 4) {
            val p = Color(a.getPixel(x - 200, y - 60))
            val q = Color(b.getPixel(x - 200, y - 60))
            sum += abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue)
            n++
        }
        return sum / n
    }

    private class Region(val xs: IntRange, val ys: List<IntRange>)

    private companion object {
        // The open drawer is 280 wide; this strip is clear of its items.
        val BehindDrawer = Region(200 until 276, listOf(60 until 180, 380 until 470))
        // Clear of the card row too, whose earlier cards run on under the drawer.
        val AboveCards = Region(200 until 276, listOf(60 until 180))
        val CardsBehindDrawer = Region(200 until 276, listOf(380 until 470))
        val PageCards = Region(290 until 960, listOf(380 until 470))
    }
}
