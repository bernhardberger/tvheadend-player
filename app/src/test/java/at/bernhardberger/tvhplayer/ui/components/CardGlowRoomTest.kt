package at.bernhardberger.tvhplayer.ui.components

import android.animation.ValueAnimator
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.images.ChannelAccents
import at.bernhardberger.tvhplayer.images.DetailsArtColors
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvCardFocusedScale
import at.bernhardberger.tvhplayer.ui.player.PlayerChannelBrowser
import at.bernhardberger.tvhplayer.ui.player.PlayerChannelBrowserState
import at.bernhardberger.tvhplayer.ui.player.QuickZapPresentation
import at.bernhardberger.tvhplayer.ui.screens.ChannelsGridColumns
import at.bernhardberger.tvhplayer.ui.screens.ChannelsGridEdgeInset
import at.bernhardberger.tvhplayer.ui.screens.ChannelsLayoutFixture
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.experimental.categories.Category
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.time.Instant

/**
 * A focused card's glow fades out on every side instead of ending in a straight cut where its row
 * or grid stops drawing. Reads the drawn pixels on lines leaving the card; the review lane also
 * writes the frames to `build/review/glow-room`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardGlowRoomTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val accentStore = ChannelAccentStore()
    private val accents = ChannelAccents(accentStore) { _, id -> "channels-${id.value}" }
    private val fixture = ChannelsLayoutFixture(compose, images, accents)

    private fun card(id: Int) = hasTestTag("channel-card-$id")

    @Test fun gridFirstCardKeepsItsGlowAboveAndBeforeIt() {
        fixture.show(ChannelsView(ChannelsLayout.GRID, setOf(ChannelsLayout.GRID)), fixture.fixtureArt())
        channels("grid-1-first-card", 1, Side.TOP, Side.START)
        repeat(ChannelsGridColumns - 1) { fixture.key(Key.DirectionRight) }
        channels("grid-2-last-column", ChannelsGridColumns, Side.TOP, Side.END)
        repeat(ChannelsGridColumns - 1) { fixture.key(Key.DirectionLeft) }
        scrolledGrid()
    }

    private fun scrolledGrid() {
        fixture.awaitFocus(card(1))
        repeat(2) { fixture.key(Key.DirectionDown) }
        // Past the inset line the row above fades out, so only the gap before it shows the glow alone.
        channels("grid-3-scrolled", 1 + 2 * ChannelsGridColumns, Side.TOP, rowAbove = ChannelsGridEdgeInset.value)
    }

    @Test fun theRowAboveShowsOnlyWhileTheGridCanScrollBack() {
        fixture.show(ChannelsView(ChannelsLayout.GRID, setOf(ChannelsLayout.GRID)), fixture.fixtureArt())
        fixture.awaitFocus(card(1))
        val line = bounds(card(1)).top.toInt()
        val column = bounds(card(3))
        val xRange = (column.left + column.width / 4).toInt() until (column.right - column.width / 4).toInt()
        fun rowAboveEdge(): Float {
            compose.mainClock.advanceTimeBy(1_500)
            compose.waitForIdle()
            val frame = draw()
            val bottomEdge = line - ChannelsGridEdgeInset.value.toInt()
            val step = xRange.map { abs(luminance(frame.getPixel(it, bottomEdge - 2)) - luminance(frame.getPixel(it, bottomEdge + 1))) }.average()
            frame.recycle()
            return step.toFloat()
        }
        assertTrue("nothing stands above the first row", rowAboveEdge() < 1f)
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(1 + ChannelsGridColumns))
        assertTrue("the row above ends visibly above the focused row", rowAboveEdge() > 1f)
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(card(1))
        assertTrue("back at the top nothing stands above it again", rowAboveEdge() < 1f)
    }

    @Test @Category(VisualCapture::class)
    fun captureScrolledGridAtRest() {
        fixture.show(ChannelsView(ChannelsLayout.GRID, setOf(ChannelsLayout.GRID)), fixture.fixtureArt())
        scrolledGrid()
    }

    @Test @Category(VisualCapture::class)
    fun captureRingPulseFrames() = ringPulsesBetweenSolidAndSeeThrough()

    @Test fun immersiveRowKeepsItsGlowAbove() {
        fixture.show(ChannelsView(ChannelsLayout.IMMERSIVE, setOf(ChannelsLayout.IMMERSIVE)), fixture.fixtureArt())
        channels("immersive-1-first-card", 1, Side.TOP)
        repeat(5) { fixture.key(Key.DirectionRight) }
        channels("immersive-2-scrolled", 6, Side.TOP)
    }

    @Test fun immersiveLogoRowKeepsItsGlowAbove() {
        fixture.show(ChannelsView(ChannelsLayout.IMMERSIVE), fixture.fixtureArt())
        channels("immersive-3-logo", 1, Side.TOP)
    }

    /** The second card: the row's title stands above the first. */
    @Test fun railKeepsItsGlowAboveAndBelow() = rail("rail-1-one-row", recent = false, Key.DirectionRight, 2, Side.TOP, Side.BOTTOM)

    /** Recent, the lower row, focused: below it the rail runs off the screen's edge. */
    @Test fun railRecentRowKeepsItsGlowBelow() = rail("rail-2-recent-row", recent = true, Key.DirectionDown, 2, Side.BOTTOM)

    /** The ring breathes between solid and see-through white; captured at both ends of one breath. */
    @Test fun ringPulsesBetweenSolidAndSeeThrough() {
        compose.mainClock.autoAdvance = false
        fixture.show(ChannelsView(ChannelsLayout.GRID, setOf(ChannelsLayout.GRID)), fixture.fixtureArt())
        repeat(40) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
        fixture.awaitFocus(card(1))
        val bounds = bounds(card(1))
        // The focus scale and glow colour settle; the pulse alone runs on.
        repeat(5) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
        val changes = recomposerChanges()
        assertTrue("the recomposer counts the screen's own changes", changes > 0)
        var solid = -1f
        var faint = 2f
        var solidFrame: Bitmap? = null
        var faintFrame: Bitmap? = null
        repeat(2 * CardFocusRingPulseMs / 100) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            val frame = draw()
            val alpha = ringAlpha(frame, bounds)
            if (alpha > solid) {
                solid = alpha
                solidFrame?.recycle()
                solidFrame = frame.copy(Bitmap.Config.ARGB_8888, false)
            }
            if (alpha < faint) {
                faint = alpha
                faintFrame?.recycle()
                faintFrame = frame.copy(Bitmap.Config.ARGB_8888, false)
            }
            frame.recycle()
        }
        assertEquals("the pulse redraws the ring without recomposing", changes, recomposerChanges())
        save("ring-1-solid", solidFrame!!)
        save("ring-2-faint", faintFrame!!)
        // Antialiasing on the scaled stroke keeps even the solid ring a little below full white.
        assertTrue("one breath reaches a solid ring: $solid", solid > 0.8f)
        assertTrue("and a see-through one: $faint of $solid",
            faint / solid in CardFocusRingPulseLow - 0.1f..CardFocusRingPulseLow + 0.1f)

        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(2))
        repeat(40) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
        val oldBounds = bounds(card(1))
        val newBounds = bounds(card(2))
        val oldFrame = draw()
        assertTrue("the old card has no ring", ringAlpha(oldFrame, oldBounds) < 0.05f)
        val newAlphas = (1..2 * CardFocusRingPulseMs / 100).map {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            val frame = draw()
            // The neighbour's real glow overlaps the right edge; sample this card's top ring and interior.
            for (y in oldBounds.top.toInt() - 12 until oldBounds.bottom.toInt()) {
                for (x in (oldBounds.left + oldBounds.width / 4).toInt() until (oldBounds.right - oldBounds.width / 4).toInt()) {
                    assertEquals("the old card and ring pixels stay still at $x,$y", oldFrame.getPixel(x, y), frame.getPixel(x, y))
                }
            }
            ringAlpha(frame, newBounds).also { frame.recycle() }
        }
        oldFrame.recycle()
        assertTrue("only the new card's ring breathes: $newAlphas", newAlphas.max() - newAlphas.min() > 0.3f)
    }

    @Test fun ringRestoresCurrentInteractionsWhenMotionChanges() {
        compose.mainClock.autoAdvance = false
        val motion = mutableStateOf(true)
        val source = mutableStateOf(MutableInteractionSource())
        lateinit var ring: CardFocusRing
        compose.setContent { ring = rememberCardFocusRing(source.value, motion.value) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(source.value.tryEmit(FocusInteraction.Focus())) }
        compose.mainClock.advanceTimeBy(CardFocusRingPulseMs.toLong())
        compose.runOnIdle {
            assertTrue("focused mid-breath", ring.alpha.value < 0.6f)
            motion.value = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(1f, ring.alpha.value, 0f)
            assertTrue(ring.shown)
            assertEquals(TvCardFocusedScale, ring.scale.value, 0f)
        }
        compose.mainClock.advanceTimeBy(2 * CardFocusRingPulseMs.toLong())
        compose.runOnIdle {
            assertEquals(1f, ring.alpha.value, 0f)
            motion.value = true
        }
        repeat(CardFocusRingPulseMs / 100) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
        compose.runOnIdle {
            assertTrue("the held focus resumes breathing", ring.alpha.value < 0.6f)
            source.value = MutableInteractionSource()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(1f, ring.alpha.value, 0f)
            assertEquals(1f, ring.scale.value, 0f)
            assertTrue("a new source inherits neither focus nor ring", !ring.shown)
        }
    }

    @Test fun ringStaysSolidWhenAnimationsAreOff() {
        animatorDurationScale(0f)
        try {
            compose.mainClock.autoAdvance = false
            fixture.show(ChannelsView(ChannelsLayout.GRID, setOf(ChannelsLayout.GRID)), fixture.fixtureArt())
            repeat(40) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
            fixture.awaitFocus(card(1))
            val bounds = bounds(card(1))
            val alphas = (1..2 * CardFocusRingPulseMs / 100).map {
                compose.mainClock.advanceTimeBy(100)
                compose.waitForIdle()
                draw().let { frame -> ringAlpha(frame, bounds).also { frame.recycle() } }
            }
            assertTrue("the ring stays solid: $alphas", alphas.min() > 0.8f && alphas.max() - alphas.min() < 0.02f)
        } finally {
            animatorDurationScale(1f)
        }
    }

    /** The system's animator duration scale, which 0 turns off as the Remove animations setting does. */
    private fun animatorDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    private fun recomposerChanges(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

    /** How opaque the ring's top stroke reads over what lies just beyond it, as a fraction of solid white. */
    private fun ringAlpha(bitmap: Bitmap, card: Rect): Float {
        val x = card.center.x.toInt()
        // The ring is lifted with the card: its brightest row lies within 12px above the card.
        val rows = (card.top.toInt() - 12 until card.top.toInt())
        val ring = rows.maxBy { luminance(bitmap.getPixel(x, it)) }
        val ringLuminance = luminance(bitmap.getPixel(x, ring))
        val beyond = luminance(bitmap.getPixel(x, ring - 4))
        return (ringLuminance - beyond) / (255f - beyond)
    }

    private fun channels(name: String, focused: Int, vararg sides: Side, rowAbove: Float? = null) {
        val focus = card(focused)
        fixture.awaitFocus(focus)
        // Glow colour, background and scroll settle; the logo glow is baked off the main thread.
        compose.mainClock.advanceTimeBy(1_500)
        awaitChannelColors(focused)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("channels-logo-glow").fetchSemanticsNodes().isEmpty() ||
                compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        check(name, bounds(focus), sides, rowAbove)
    }

    private fun awaitChannelColors(focused: Int) {
        val prefix = if (fixture.channelsView.layout == ChannelsLayout.GRID) "channels-grid" else "channels-immersive"
        val observation = fixture.shownObservation
        val art = observation.eventAt(ChannelId(focused.toLong()), Instant.fromEpochSeconds(System.currentTimeMillis() / 1000))
            ?.image?.takeIf { fixture.channelsView.showsProgramArt }
        val picture = art?.let { AppArtworkSource(requireNotNull(observation.currentSession), it) }
        // Neither the focused picture's glow nor any visible logo card's fill is tracked by Compose idle.
        // Wait for their palette results before advancing the colour animations, not just for the backdrop.
        compose.waitUntil(10_000) {
            (picture == null || DetailsArtColors.stored(picture) != null) && ChannelsLayoutFixture.PICONS.indices.all { index ->
                val id = index + 1
                compose.onAllNodesWithTag("$prefix-$id-picon").fetchSemanticsNodes().isEmpty() ||
                    accentStore["channels-$id"] != null
            }
        }
    }

    private lateinit var view: View

    private fun rail(name: String, recent: Boolean, key: Key, focused: Int, vararg sides: Side) {
        val channels = listOf(
            Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = ChannelIcon.ImageCache(ArtworkId(1))),
            Channel.create(ChannelId(2), name = "Harbor Sport HD", number = 102, icon = ChannelIcon.ImageCache(ArtworkId(2))),
            Channel.create(ChannelId(3), name = "Northline News", number = 103),
        )
        val session = FakeSessionObservation(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).captureCurrentSession()
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext<Application>(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(2) to FixtureArt.picon("harbor-sport"),
        )))
        // The cards sample their colours off the main thread, where waiting for idle does not see them.
        val accentStore = ChannelAccentStore()
        val accents = ChannelAccents(accentStore) { _, id -> "rail-${id.value}" }
        val state = PlayerChannelBrowserState().apply { onNow.focusedId = channels.first().id }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalChannelAccents provides accents) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ProvideArtworkSession(session) {
                            QuickZapPresentation(
                                expanded = true,
                                channelContent = {
                                    PlayerChannelBrowser(state, channels, if (recent) channels.drop(1) else emptyList(),
                                        channels[0].id, emptySet(), { null }, 0L, true, {}, {}, { _, _, _ -> }, {})
                                },
                                preview = {},
                                controls = {},
                                trayBleeds = recent,
                            )
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("player-channel-card-1") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().performKeyInput { pressKey(key) }
        val focus = hasTestTag("player-channel-card-$focused")
        compose.waitUntil(10_000) { compose.onAllNodes(focus and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { accentStore["rail-1"] != null && accentStore["rail-2"] != null }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        check(name, bounds(focus), sides)
    }

    private fun bounds(target: SemanticsMatcher): Rect = compose.onNode(target).fetchSemanticsNode().boundsInRoot

    private enum class Side { TOP, BOTTOM, START, END }

    /**
     * On every [sides] the glow's brightness falls without a step from just past the ring to
     * [Reach]. A clip inside that reach shows as one jump from glow to background. Content of a
     * row ending [rowAbove] pixels above the card is left out of the profile.
     */
    private fun check(name: String, card: Rect, sides: Array<out Side>, rowAbove: Float? = null) {
        val bitmap = draw()
        val bitmap2 = draw()
        assertTrue("$name captured mid-animation", bitmap.sameAs(bitmap2))
        bitmap2.recycle()
        save(name, bitmap)
        val report = StringBuilder()
        var failure: String? = null
        for (side in sides) {
            val profile = profile(bitmap, card, side, rowAbove.takeIf { side == Side.TOP })
            val step = profile.zipWithNext { a, b -> abs(a - b) }.max()
            val glow = profile.first() - profile.last()
            report.append("$side: step=${"%.1f".format(step)} glow=${"%.1f".format(glow)} profile=${profile.joinToString(",") { "%.0f".format(it) }}\n")
            if (failure == null && step > MaxStep) failure = "$name: the glow is cut on its $side side (a step of $step)"
            // Above a scrolled grid only the glow's first pixels stand clear of the row above.
            val minGlow = if (side == Side.TOP && rowAbove != null) MinGlow / 2 else MinGlow
            if (failure == null && glow < minGlow) failure = "$name: no glow reaches past the $side side ($glow)"
        }
        CaptureOutput.writeText(File(DIRECTORY, "$name.txt")) {
            "canvas=960x540 dp; density=1.0 (mdpi); locale=en; fontScale=1.0\n" +
                "card=$card\n$report" +
                "evidenceLimit=static composition only; not physical-TV glow, motion or overscan\n"
        }
        bitmap.recycle()
        assertTrue(failure ?: "", failure == null)
    }

    /**
     * Mean brightness at each pixel from just past the lifted ring out to [Reach] beyond [side],
     * over the middle half of that edge, stopping short of the [rowAbove]'s bottom edge.
     */
    private fun profile(bitmap: Bitmap, card: Rect, side: Side, rowAbove: Float? = null): List<Float> {
        val vertical = side == Side.TOP || side == Side.BOTTOM
        val lines = if (vertical) (card.left + card.width / 4).toInt() until (card.right - card.width / 4).toInt() step 4
            else (card.top + card.height / 4).toInt() until (card.bottom - card.height / 4).toInt() step 4
        // The ring's outer edge, lifted 5% with the card about its centre.
        val ring = ((4.25f + 1.25f) * 1.05f + 0.025f * (if (vertical) card.height else card.width)).toInt() + 1
        val last = rowAbove?.let { it.toInt() - 2 } ?: Reach
        return (ring..last).map { distance ->
            lines.map { along ->
                val (x, y) = when (side) {
                    Side.TOP -> along to card.top.toInt() - 1 - distance
                    Side.BOTTOM -> along to card.bottom.toInt() + distance
                    Side.START -> card.left.toInt() - 1 - distance to along
                    Side.END -> card.right.toInt() + distance to along
                }
                luminance(bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1)))
            }.average().toFloat()
        }
    }

    private fun luminance(pixel: Int): Float =
        0.2126f * android.graphics.Color.red(pixel) + 0.7152f * android.graphics.Color.green(pixel) +
            0.0722f * android.graphics.Color.blue(pixel)

    private fun draw(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val root = if (::view.isInitialized) view.rootView else fixture.view.rootView
            bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun save(name: String, bitmap: Bitmap) = CaptureOutput.writePng(File(DIRECTORY, "$name.png"), bitmap)

    private companion object {
        val DIRECTORY = File("../build/review/glow-room")

        /** Pixels past the card edge where the 24dp glow, lifted with the card, has faded below one level. */
        const val Reach = 38
        /** The largest brightness change between neighbouring pixels a fading glow makes. */
        const val MaxStep = 3.5f
        const val MinGlow = 4f
    }
}
