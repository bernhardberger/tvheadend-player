package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.TimeZone
import kotlin.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickZapPresentationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var layers: LivePlayerLayerState
    private lateinit var view: View
    private var playing by mutableStateOf<ChannelId?>(ChannelId(2))
    private var indicator by mutableStateOf(ChannelPlaybackIndicator.PLAYING)
    private var picks = 0
    private var confirmPicks = true
    private var focusColor = 0
    private var defaultZone: TimeZone? = null

    @Before fun pinClock() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreClock() { defaultZone?.let(TimeZone::setDefault) }

    @Test fun playbackMarkersShareNeutralSlotThroughPauseAndTune() {
        content()
        key(Key.DirectionDown)
        card(2).assertIsFocused()
        val playingBounds = compose.onNodeWithTag("channel-playing-indicator", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        capture("en-font1-marker-playing", 2)
        compose.runOnIdle { indicator = ChannelPlaybackIndicator.PAUSED }
        assertEquals(playingBounds, compose.onNodeWithTag("channel-paused-indicator", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val brightPixels = buildList {
            for (y in playingBounds.top.toInt() until playingBounds.bottom.toInt()) {
                for (x in playingBounds.left.toInt() until playingBounds.right.toInt()) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) > 140) add(pixel)
                }
            }
        }
        assertTrue(brightPixels.isNotEmpty())
        assertTrue(brightPixels.all { kotlin.math.abs(android.graphics.Color.blue(it) - android.graphics.Color.red(it)) < 15 })
        bitmap.recycle()
        capture("en-font1-marker-paused", 2)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { indicator = ChannelPlaybackIndicator.TUNING }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        assertEquals(playingBounds, compose.onNodeWithTag("channel-tuning-indicator", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot)
        capture("en-font1-marker-tuning-96ms", 2)
        compose.runOnIdle { indicator = ChannelPlaybackIndicator.NONE }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("channel-tuning-indicator", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun expandedRailIgnoresStaleSeekEmphasis() {
        var expanded by mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.White)) {
                QuickZapPresentation(preview = {}, expanded = expanded, channelsAvailable = true,
                    peekAlpha = { 0f }, channelContent = {
                        Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Red))
                    }, controls = {})
            }
        }
        fun pixel(y: Int): Int {
            compose.waitForIdle()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            return bitmap.getPixel(480, y).also { bitmap.recycle() }
        }
        assertEquals(android.graphics.Color.WHITE, pixel(530))
        compose.runOnIdle { expanded = true }
        assertEquals(android.graphics.Color.RED, pixel(470))
    }

    @Test fun inPlaceRailStartsAtEntryWithoutComposingTheStartOfTheCatalog() {
        val catalog = (1L..100L).map { Channel.create(id = ChannelId(it), number = it, name = "Channel $it") }
        val composedChannels = mutableSetOf<ChannelId>()
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            TVHeadendPlayerTheme {
                ChannelDrawer(catalog, ChannelId(50), ChannelId(50), recordingChannelIds = emptySet(),
                    nowEvent = { composedChannels += it; null }, imageLoader = loader,
                    onFocusChannel = {}, onPickChannel = {}, onCloseDrawer = {},
                    inPlace = true, entryFocusId = ChannelId(50))
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("player-channel-card-50").assertIsFocused()
        assertFalse("do not measure the start then jump to the entry card", ChannelId(1) in composedChannels)
        assertTrue("only the viewport and one reserve card per side: $composedChannels", composedChannels.size <= 8)
        println("RAIL_INITIAL_COMPOSITION count=${composedChannels.size}")
    }

    @Test fun zeroAlphaPageContentDoesNotDraw() {
        var progress by mutableStateOf(0f)
        var draws = 0
        compose.setContent {
            view = LocalView.current
            androidx.compose.runtime.CompositionLocalProvider(LocalPlayerPageProgress provides { progress }) {
                Box(Modifier.fillMaxSize().pageMotion(150..480)
                    .then(Modifier.drawBehind { draws++; drawRect(Color.Red) }))
            }
        }
        fun draw() {
            compose.waitForIdle()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.recycle()
        }
        draw()
        assertEquals(0, draws)
        compose.runOnIdle { progress = 1f }
        draw()
        assertTrue(draws > 0)
    }

    @Test fun identityAndFocusedRailCardHaveTheSameGeometry() {
        val inspectorEnabled = androidx.compose.ui.platform.isDebugInspectorInfoEnabled
        androidx.compose.ui.platform.isDebugInspectorInfoEnabled = true
        try {
            val channel = channels.first()
            compose.setContent {
                view = LocalView.current
                val loader = remember { ImageLoader(view.context) }
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(start = 58.dp, top = 30.dp)) {
                            PlayerIdentityCard(PlayerChromeContent("", PlayerInfoBarData("1", "Channel 1", "", ""),
                                picon = channel.icon), loader, null, onClick = {}, channelCard = true, held = true)
                        }
                        Box(Modifier.padding(top = 230.dp)) {
                            ChannelDrawer(listOf(channel), channel.id, channel.id, recordingChannelIds = emptySet(),
                                nowEvent = { null }, imageLoader = loader, onFocusChannel = {}, onPickChannel = {},
                                onCloseDrawer = {}, inPlace = true, entryFocusId = channel.id)
                        }
                    }
                }
            }
            compose.waitForIdle()
            fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            fun rect(tag: String, card: String): androidx.compose.ui.geometry.Rect {
                val parent = node(card).layoutInfo.coordinates
                return parent.localBoundingBoxOf(node(tag).layoutInfo.coordinates, clipBounds = false)
            }
            fun label(tag: String, card: String): List<Float> {
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node(tag).config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
                val text = results.single()
                val bounds = rect(tag, card)
                return listOf(bounds.left, bounds.top, text.firstBaseline + bounds.top,
                    text.getBoundingBox(text.layoutInput.text.text.indexOf("Channel")).left + bounds.left, bounds.width)
            }
            fun corner(card: String): Float = node(card).layoutInfo.getModifierInfo()
                .mapNotNull { it.modifier as? androidx.compose.ui.platform.InspectableValue }
                .filter { it.nameFallback == "graphicsLayer" }.mapNotNull { layer ->
                    @Suppress("UNCHECKED_CAST")
                    val block = layer.inspectableElements.firstOrNull { it.name == "block" }?.value as? (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)
                    val shape = block?.let { androidx.compose.ui.graphics.GraphicsLayerScope().apply(it).shape }
                        ?: layer.inspectableElements.firstOrNull { it.name == "shape" }?.value as? androidx.compose.ui.graphics.Shape
                    (shape?.createOutline(androidx.compose.ui.geometry.Size(196f, 110f),
                        androidx.compose.ui.unit.LayoutDirection.Ltr, Density(1f)) as? androidx.compose.ui.graphics.Outline.Rounded)
                        ?.roundRect?.topLeftCornerRadius?.x
                }.first()
            val identity = "player-identity-card"
            val rail = "player-channel-card-1"
            val identityLabel = label("player-identity-label", identity)
            val railLabel = label("player-channel-1-identity", rail)
            println("CARD_GEOMETRY identitySize=${node(identity).layoutInfo.coordinates.size} railSize=${node(rail).layoutInfo.coordinates.size} " +
                "identityPicon=${rect("player-identity-logo", identity)} railPicon=${rect("player-channel-1-picon", rail)} " +
                "identityLabel=$identityLabel railLabel=$railLabel identityCorner=${corner(identity)} railCorner=${corner(rail)}")
            assertEquals(node(identity).layoutInfo.coordinates.size, node(rail).layoutInfo.coordinates.size)
            assertEquals(rect("player-identity-logo", identity), rect("player-channel-1-picon", rail))
            identityLabel.zip(railLabel).forEach { (a, b) -> assertEquals(a, b, 0.01f) }
            assertEquals(corner(identity), corner(rail), 0.01f)
        } finally {
            androidx.compose.ui.platform.isDebugInspectorInfoEnabled = inspectorEnabled
        }
    }

    @Test fun railPeekWaitsForRevealAndCanReverseDuringItsFade() {
        var expanded by mutableStateOf(false)
        var expansion: () -> Float = { 0f }
        lateinit var peek: androidx.compose.animation.core.MutableTransitionState<Boolean>
        var headerCompositions = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerPage<Unit>(details = null, railHeader = if (expanded) ({
                    androidx.compose.runtime.SideEffect { headerCompositions++ }
                    ProgrammeScheduleHeader("1 · Channel 1")
                }) else null, player = {
                    peek = LocalPlayerRailPeek.current!!
                    QuickZapPresentation(expanded, channelsAvailable = false,
                        inPlaceAnchor = { androidx.compose.ui.geometry.Rect(58f, 300f, 254f, 410f) },
                        channelContent = { Box(Modifier.fillMaxWidth().height(100.dp)) }, preview = {},
                        controls = { expansion = LocalInPlaceRailExpansion.current })
                }, programme = {})
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { expanded = true; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(192)
        compose.waitForIdle()
        assertTrue(expansion() in 0f..0.999f)
        assertEquals("no header work competes with the reveal", 0, headerCompositions)
        compose.mainClock.advanceTimeBy(112)
        compose.waitForIdle()
        assertEquals(1f, expansion(), 0f)
        assertTrue("peek is fading in", peek.targetState && !peek.currentState && !peek.isIdle)
        compose.runOnIdle { expanded = false; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        assertEquals("even an interrupted peek leaves before collapse", 1f, expansion(), 0f)
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertTrue(peek.isIdle && !peek.currentState)
        assertEquals(0f, expansion(), 0f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun inPlaceRailRevealsFromCardBoundsWithinPlayerPage() {
        var expanded by mutableStateOf(false)
        var expansion: () -> Float = { 0f }
        val anchor = androidx.compose.ui.geometry.Rect(58f, 300f, 254f, 410f)
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                PlayerPage<Unit>(details = null, player = {
                    QuickZapPresentation(expanded = expanded, channelsAvailable = false,
                        inPlaceAnchor = { anchor }, preview = {}, controls = {
                            expansion = LocalInPlaceRailExpansion.current
                        }, channelContent = {
                            Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Red))
                        })
                }, programme = {})
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { expanded = true; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(16)
        compose.waitForIdle()
        val e = expansion()
        assertTrue("the reveal is in flight: $e", e > 0f && e < 1f)
        val right = anchor.right + (960f - anchor.right) * e
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        assertTrue("the card's original area is revealed", android.graphics.Color.red(bitmap.getPixel(150, 320)) > 100)
        val outside = (right + 100).toInt()
        assertTrue("the reveal has not yet reached the viewport edge: $right", outside < bitmap.width)
        assertEquals("pixels beyond the expanding edge remain hidden", 0, android.graphics.Color.red(bitmap.getPixel(outside, 320)))
        val feather = android.graphics.Color.red(bitmap.getPixel((right + 48).toInt(), 320))
        assertTrue("the expanding edge stays feathered, not hard-clipped: $feather", feather in 1..200)
        bitmap.recycle()
    }

    @Test fun peekingNewTrayDoesNotDimControlsAboveTheRow() {
        var expanded by mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.White)) {
                QuickZapPresentation(expanded = expanded, channelsAvailable = true,
                    peekAlpha = { 1f }, preview = {}, channelContent = {
                        Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Red))
                    }, controls = { Box(Modifier.fillMaxSize().background(Color.White)) })
            }
        }
        fun column(): IntArray {
            compose.waitForIdle()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            return IntArray(view.height) { bitmap.getPixel(480, it) }.also { bitmap.recycle() }
        }
        val peek = column()
        val cardTop = peek.indexOfFirst { it == android.graphics.Color.RED }
        assertTrue("the channel row peeks at the bottom edge: $cardTop", cardTop in 480 until 540)
        assertTrue("controls above the peeking row keep full brightness",
            (0 until cardTop).all { peek[it] == android.graphics.Color.WHITE })
        compose.runOnIdle { expanded = true }
        val open = column()
        val openCardTop = open.indexOfFirst { it == android.graphics.Color.RED }
        assertTrue("the open tray keeps its scrim behind the preview",
            android.graphics.Color.red(open[openCardTop - 8]) < 80)
    }
    private val channels = (1L..12L).map {
        Channel.create(id = ChannelId(it), number = it, name = if (it == 4L) "Dokumentation und Zeitgeschichte HD" else "Channel $it",
            icon = if (it == 5L) null else ArtworkId(it.toInt()))
    }
    private var catalog by mutableStateOf(channels)

    @Test fun nativeCardCornerGapIsUniformFocusedAndPressed() = cornerGeometry(1f, "en-font1")

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanLargeNativeCardCornerGapIsUniformFocusedAndPressed() = cornerGeometry(1.3f, "de-font1.3")

    private fun cornerGeometry(fontScale: Float, prefix: String) {
        content(fontScale = fontScale)
        key(Key.DirectionDown)
        card(2).assertIsFocused()
        fun verify(state: String, expectedScale: Float) {
            // The pinned rail keeps card 1 off-screen, left of the keyline; card 3 rests unfocused.
            val native = card(3).fetchSemanticsNode().boundsInRoot
            val focused = card(2).fetchSemanticsNode().boundsInRoot
            val picon = compose.onNodeWithTag("player-channel-2-picon", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val scale = picon.width / 100f
            assertEquals(expectedScale, scale, .01f)
            // Supersample the real production composition, not a copied RoundedCornerShape.
            val sampleScale = 4
            val bitmap = Bitmap.createBitmap(view.width * sampleScale, view.height * sampleScale, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap).apply { scale(sampleScale.toFloat(), sampleScale.toFloat()) })
            fun pixel(x: Float, y: Float) = bitmap.getPixel((x * sampleScale).toInt(), (y * sampleScale).toInt())
            fun colorDistance(a: Int, b: Int): Int =
                abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                    abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                    abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
            val background = pixel(native.right + 3, native.top - 3)
            val fill = pixel(native.right - 20, native.top + 3)
            // Recover the native corner from its painted silhouette. Fit many boundary
            // samples so a single antialiased pixel cannot determine the radius.
            val edge = (1..47).mapNotNull { row ->
                val y = row / 4f + .125f
                val x = (0..64).firstOrNull { col ->
                    val sample = pixel(native.right - col / 4f - .125f, native.top + y)
                    colorDistance(sample, fill) < colorDistance(sample, background)
                }?.let { it / 4f }
                x?.takeIf { it > 0f }?.let { it to y }
            }
            assertTrue("native curved silhouette must be sampled", edge.size >= 12)
            val radius = (100..1600).map { it / 100f }.minBy { r ->
                edge.sumOf { (x, y) ->
                    val error = hypot(x - r, y - r) - r
                    (error * error).toDouble()
                }
            }
            val left = focused.center.x - focused.width * scale / 2
            val top = focused.center.y - focused.height * scale / 2
            val gaps = listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1).flatMap { (dx, dy) ->
                val centerX = if (dx < 0) left + radius * scale else left + (focused.width - radius) * scale
                val centerY = if (dy < 0) top + radius * scale else top + (focused.height - radius) * scale
                listOf(0, 22, 45, 68, 90).map { degrees ->
                    val angle = Math.toRadians(degrees.toDouble())
                    val ring = (0..160).map { radius - 1 + it / 20f }.filter { r ->
                        val sample = pixel(centerX + dx * cos(angle).toFloat() * r * scale,
                            centerY + dy * sin(angle).toFloat() * r * scale)
                        android.graphics.Color.red(sample) > 110 &&
                            abs(android.graphics.Color.red(sample) - android.graphics.Color.blue(sample)) < 20
                    }
                    assertTrue("ring missing at $degrees degrees", ring.isNotEmpty())
                    val gap = ring.first() - radius
                    assertEquals("$state native radius=$radius corner=$dx,$dy angle=$degrees inner gap", .5f, gap, .4f)
                    assertEquals("$state centered 3dp stroke at $degrees degrees", 3f, ring.last() - ring.first(), .5f)
                    gap
                }
            }
            assertTrue("$state corner gap varies: $gaps", gaps.max() - gaps.min() < .5f)
            val progress = compose.onNodeWithTag("player-channel-2-progress", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val strip = pixel(progress.left + progress.width * .1f, progress.bottom - .75f * scale)
            assertTrue("lower progress strip remains visible", android.graphics.Color.blue(strip) > android.graphics.Color.red(strip) + 30)
            bitmap.recycle()
            capture("$prefix-native-corner-$state", 2, fontScale)
            File("../artifacts/quick-zap/$prefix-native-corner-$state-geometry.txt").writeText(
                "nativeRadiusMeasuredDp=$radius\nscale=$scale\ninnerGapsDp=$gaps\n" +
                    "supersampling=4x production view draw; native shape and scale unmodified\n",
            )
        }
        verify("focused", 1.1f)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.waitForIdle()
        verify("pressed", 1f)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
    }

    @Test fun tunedCardsKeepBoundsAcrossUpAndBack() = stableReentry(1f, "en-font1")

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanLargeTextTunedCardsKeepBoundsAcrossUpAndBack() = stableReentry(1.3f, "de-font1.3")

    private fun stableReentry(fontScale: Float, prefix: String) {
        content(fontScale = fontScale)
        key(Key.DirectionDown)
        for ((id, close) in listOf(3 to Key.DirectionUp, 4 to Key.Back)) {
            key(Key.DirectionRight)
            card(id).assertIsFocused()
            key(Key.DirectionCenter)
            val before = card(id).fetchSemanticsNode().boundsInRoot
            capture("$prefix-tuned-$id-before", id, fontScale)
            key(close)
            compose.onNodeWithTag("player-pause").assertIsFocused()
            key(Key.DirectionDown)
            card(id).assertIsFocused()
            assertEquals(before, card(id).fetchSemanticsNode().boundsInRoot)
            capture("$prefix-tuned-$id-reopened", id, fontScale)
        }
        assertEquals(2, picks)
    }

    @Test fun browsedNonplayingCardAndViewportSurviveReentry() {
        content()
        key(Key.DirectionDown)
        repeat(7) { key(Key.DirectionRight) }
        val before = card(9).assertIsFocused().fetchSemanticsNode().boundsInRoot
        for (close in listOf(Key.DirectionUp, Key.Back)) {
            key(close)
            compose.onNodeWithTag("player-pause").assertIsFocused()
            key(Key.DirectionDown)
            card(9).assertIsFocused()
            assertEquals(before, card(9).fetchSemanticsNode().boundsInRoot)
        }
        assertEquals(ChannelId(2), playing)
        assertEquals(0, picks)
    }

    @Test fun delayedOwnConfirmationDoesNotReplaceLaterBrowseFocus() {
        confirmPicks = false
        content()
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        compose.runOnIdle { playing = null }
        key(Key.DirectionRight)
        val before = card(4).assertIsFocused().fetchSemanticsNode().boundsInRoot
        key(Key.Back)
        compose.runOnIdle { playing = ChannelId(3) }
        key(Key.DirectionDown)
        card(4).assertIsFocused()
        assertEquals(before, card(4).fetchSemanticsNode().boundsInRoot)
        assertEquals(1, picks)
    }

    @Test fun lossOfConfirmationWhileClosedPreservesVisibleBrowseUntilConfirmation() {
        confirmPicks = false
        content()
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        key(Key.DirectionRight)
        val before = card(4).assertIsFocused().fetchSemanticsNode().boundsInRoot
        key(Key.DirectionUp)
        compose.runOnIdle { playing = null }
        compose.onNodeWithTag("player-pause").assertIsFocused()
        key(Key.DirectionDown)
        card(4).assertIsFocused()
        assertEquals(before, card(4).fetchSemanticsNode().boundsInRoot)
        key(Key.Back)
        compose.runOnIdle { playing = ChannelId(3) }
        key(Key.DirectionDown)
        card(4).assertIsFocused()
        assertEquals(before, card(4).fetchSemanticsNode().boundsInRoot)
        assertEquals(1, picks)
    }

    @Test fun externalConfirmationAfterNullSupersedesPendingLocalPick() {
        confirmPicks = false
        content()
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        compose.runOnIdle { playing = null }
        key(Key.DirectionRight)
        key(Key.Back)
        compose.runOnIdle { playing = ChannelId(10) }
        compose.onNodeWithTag("player-pause").assertIsFocused()
        key(Key.DirectionDown)
        val distant = card(10).assertIsFocused().fetchSemanticsNode().boundsInRoot
        assertTrue("distant card is brought on screen safely: $distant", distant.left >= 48f && distant.right <= 912f)
        // The different confirmation consumed the old intent: a later channel 3
        // confirmation is external now, not the still-pending local pick.
        key(Key.Back)
        compose.runOnIdle { playing = null }
        compose.runOnIdle { playing = ChannelId(3) }
        key(Key.DirectionDown)
        card(3).assertIsFocused()
        assertEquals(1, picks)
    }

    @Test fun activePlaybackConfirmationAndMetadataRefreshDoNotStealBrowseFocus() {
        content()
        key(Key.DirectionDown)
        repeat(2) { key(Key.DirectionRight) }
        val before = card(4).assertIsFocused().fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            playing = ChannelId(8)
            catalog = channels.map { Channel.create(id = it.id, number = it.number, name = "Updated ${it.id.value}") }
        }
        card(4).assertIsFocused()
        assertEquals(before, card(4).fetchSemanticsNode().boundsInRoot)
        key(Key.Back)
        key(Key.DirectionDown)
        // The confirmed external change re-anchors the rail once it has closed.
        card(8).assertIsFocused()
        assertEquals(0, picks)
    }

    @Test fun confirmedTuneWhileOpenReanchorsOnlyAfterClose() {
        content()
        key(Key.DirectionDown)
        repeat(2) { key(Key.DirectionRight) }
        val before = card(4).assertIsFocused().fetchSemanticsNode().boundsInRoot
        // A completed numeric tune: awaiting presentation, then confirmed.
        compose.runOnIdle { playing = null }
        compose.runOnIdle { playing = ChannelId(8) }
        card(4).assertIsFocused()
        assertEquals(before, card(4).fetchSemanticsNode().boundsInRoot)
        key(Key.DirectionRight)
        card(5).assertIsFocused()
        key(Key.Back)
        compose.onNodeWithTag("player-pause").assertIsFocused()
        key(Key.DirectionDown)
        val anchored = card(8).assertIsFocused().fetchSemanticsNode().boundsInRoot
        // Without a further change, reopening keeps the re-anchored card and viewport.
        key(Key.Back)
        key(Key.DirectionDown)
        card(8).assertIsFocused()
        assertEquals(anchored, card(8).fetchSemanticsNode().boundsInRoot)
        assertEquals(0, picks)
    }

    @Test fun unconfirmedOrFailedTuneKeepsBrowseAndConfirmedChannelUpReanchors() {
        content()
        key(Key.DirectionDown)
        repeat(2) { key(Key.DirectionRight) }
        val before = card(4).assertIsFocused().fetchSemanticsNode().boundsInRoot
        key(Key.Back)
        // A tune that never confirms, then the previous channel re-confirmed.
        compose.runOnIdle { playing = null }
        key(Key.DirectionDown)
        card(4).assertIsFocused()
        key(Key.Back)
        compose.runOnIdle { playing = ChannelId(2) }
        key(Key.DirectionDown)
        card(4).assertIsFocused()
        assertEquals(before, card(4).fetchSemanticsNode().boundsInRoot)
        // The same failure while the rail is open does not re-anchor on close either.
        compose.runOnIdle { playing = null }
        compose.runOnIdle { playing = ChannelId(2) }
        key(Key.Back)
        key(Key.DirectionDown)
        card(4).assertIsFocused()
        // CH+ while closed: awaiting presentation, then the next channel confirmed.
        key(Key.Back)
        compose.runOnIdle { playing = null }
        compose.runOnIdle { playing = ChannelId(3) }
        key(Key.DirectionDown)
        card(3).assertIsFocused()
        assertEquals(0, picks)
    }

    @Test fun reorderedAndRemovedBrowseAnchorRecoverWithoutOffscreenFocusWait() {
        content()
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        key(Key.Back)
        compose.runOnIdle { catalog = channels.filterNot { it.id == ChannelId(3) } + channels[2] }
        key(Key.DirectionDown)
        card(3).assertIsFocused()
        compose.runOnIdle { catalog = channels.filterNot { it.id == ChannelId(3) } }
        card(2).assertIsFocused()
        compose.runOnIdle { catalog = emptyList() }
        compose.onNodeWithTag("player-shelf-close").assertIsFocused()
        compose.runOnIdle { catalog = channels.takeLast(2) }
        card(11).assertIsFocused()
        assertEquals(0, picks)
    }

    @Test fun activeReorderKeepsIdentityAndInitialMissingPlayingUsesFirstCard() {
        playing = ChannelId(99)
        content()
        key(Key.DirectionDown)
        card(1).assertIsFocused()
        key(Key.DirectionRight)
        compose.runOnIdle { catalog = channels.filterNot { it.id == ChannelId(2) } + channels[1] }
        card(2).assertIsFocused()
        val bounds = card(2).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= 48f && bounds.right <= 912f)
        assertEquals(0, picks)
    }

    @Test fun focusedOutlineMatchesNativeButtonColorAndKeepsProgressVisible() {
        content()
        val button = compose.onNodeWithTag("player-pause").fetchSemanticsNode().boundsInRoot
        fun pixel(x: Float, y: Float): Int {
            compose.waitForIdle()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            return bitmap.getPixel(x.toInt(), y.toInt()).also { bitmap.recycle() }
        }
        assertEquals(focusColor, pixel(button.center.x, button.top + 8f))
        key(Key.DirectionDown)
        val bounds = card(2).fetchSemanticsNode().boundsInRoot
        val picon = compose.onNodeWithTag("player-channel-2-picon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val scale = picon.width / 100f
        val outlineTop = bounds.center.y - (bounds.height / 2f + 2f) * scale
        assertEquals(focusColor, pixel(bounds.center.x, outlineTop))
        assertEquals(0xFFE1E2E5.toInt(), focusColor)
    }

    @Test fun peekOpensWithoutTuningAndDifferentPickKeepsFocusUntilPlayingPick() {
        content()
        compose.onNodeWithTag("player-pause").assertIsFocused()
        compose.onNodeWithTag("player-channel-card-2").assertDoesNotExist()
        capture("en-font1-peek")
        key(Key.DirectionDown)
        card(2).assertIsFocused()
        assertEquals(0, picks)
        compose.onNodeWithTag("player-pause").assertDoesNotExist()
        val focusedBounds = card(2).fetchSemanticsNode().boundsInRoot
        val progressBounds = compose.onNodeWithTag("player-channel-2-progress", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // Card semantics describe its unscaled layout; descendants include the native focus transform.
        val piconBounds = compose.onNodeWithTag("player-channel-2-picon", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val scale = piconBounds.width / 100f
        val paintedBottom = focusedBounds.center.y + focusedBounds.height * scale / 2f
        val paintedLeft = focusedBounds.center.x - focusedBounds.width * scale / 2f
        assertEquals("progress stays embedded at the bottom", paintedBottom, progressBounds.bottom, .5f)
        assertEquals("progress stays full width", paintedLeft, progressBounds.left, .5f)
        assertTrue("progress remains a visible strip", progressBounds.height >= 2f)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixel = bitmap.getPixel(
                (progressBounds.left + progressBounds.width * .1f).toInt(),
                (progressBounds.bottom - 1f).toInt(),
            )
            assertTrue("cyan progress is painted above the bottom edge, not covered by the gray ring",
                android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel) + 30)
            bitmap.recycle()
        }
        capture("en-font1-expanded")
        key(Key.DirectionRight)
        card(3).assertIsFocused()
        val before = card(3).fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        card(3).assertIsFocused()
        assertEquals(1, picks)
        assertEquals(before, card(3).fetchSemanticsNode().boundsInRoot)
        assertTrue(layers.channelDrawerOpen)
        key(Key.DirectionCenter)
        assertFalse(layers.channelDrawerOpen)
        compose.onNodeWithTag("player-pause").assertIsFocused()
        assertEquals(1, picks)
    }

    @Test fun upAndBackRestoreControlsAndNextDownStillOpens() {
        content()
        for (close in listOf(Key.DirectionUp, Key.Back)) {
            key(Key.DirectionDown)
            card(2).assertIsFocused()
            key(close)
            compose.onNodeWithTag("player-pause").assertIsFocused()
            assertFalse(layers.channelDrawerOpen)
        }
        assertEquals(0, picks)
    }

    @Test fun horizontalViewportReachesScreenEdgesButFocusedCardsStaySafe() {
        content()
        key(Key.DirectionDown)
        repeat(7) { key(Key.DirectionRight) }
        val focused = card(9).assertIsFocused().fetchSemanticsNode().boundsInRoot
        assertTrue("focused left safe: $focused", focused.left >= 48f)
        assertTrue("focused right safe: $focused", focused.right <= 912f)
        val shelf = compose.onNodeWithTag("player-channel-shelf").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, shelf.left, .5f)
        assertEquals(960f, shelf.right, .5f)
        assertTrue("bottom third: $focused", focused.top >= 360f)
        assertTrue("bottom focus overflow inside screen: $focused", focused.bottom <= 520f)
        capture("en-font1-scrolled")
    }

    @Test fun pinnedRailKeepsTheFocusedCardOnTheStartKeylineUntilTheListEnds() {
        content()
        key(Key.DirectionDown)
        val keyline = card(2).assertIsFocused().fetchSemanticsNode().boundsInRoot.left
        assertTrue("keyline is screen-safe: $keyline", keyline >= 48f)
        key(Key.DirectionLeft)
        assertEquals(keyline, card(1).assertIsFocused().fetchSemanticsNode().boundsInRoot.left, .5f)
        repeat(4) { key(Key.DirectionRight) }
        assertEquals(keyline, card(5).assertIsFocused().fetchSemanticsNode().boundsInRoot.left, .5f)
        repeat(7) { key(Key.DirectionRight) }
        val last = card(12).assertIsFocused().fetchSemanticsNode().boundsInRoot
        assertTrue("the row stops at its end, so focus leaves the keyline: $last", last.left > keyline + 1f)
        assertTrue("last card stays safe: $last", last.right <= 912f)
    }

    // A target that is not composed yet costs a synchronous beyond-bounds search per repeat.
    @Test fun heldLeftFindsEachPinnedTargetAlreadyComposed() {
        content()
        key(Key.DirectionDown)
        repeat(9) { key(Key.DirectionRight) }
        card(11).assertIsFocused()
        compose.mainClock.autoAdvance = false
        for (target in 10 downTo 3) {
            assertTrue("card $target is composed before Left", compose.onAllNodes(hasTestTag("player-channel-card-$target")).fetchSemanticsNodes().isNotEmpty())
            compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.mainClock.advanceTimeBy(16)
            card(target).assertIsFocused()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        card(3).assertIsFocused()
    }

    @Test fun browsingRailStepsFocusWithChannelKeysInsteadOfTuning() {
        content()
        key(Key.DirectionDown)
        val keyline = card(2).assertIsFocused().fetchSemanticsNode().boundsInRoot.left
        key(Key.ChannelUp)
        assertEquals(keyline, card(3).assertIsFocused().fetchSemanticsNode().boundsInRoot.left, .5f)
        key(Key.ChannelDown)
        key(Key.ChannelDown)
        card(1).assertIsFocused()
        assertEquals(0, picks)
        assertTrue(layers.channelDrawerOpen)
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun largeLocalizedCardsStayCompactAndMissingEpgHasNoInventedProgress() {
        content(fontScale = 1.3f, bright = true)
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        card(4).assertIsFocused()
        val bounds = card(4).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= 360f)
        assertTrue(bounds.bottom <= 520f)
        compose.onAllNodes(
            hasAnyAncestor(hasTestTag("player-channel-card-5")) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        capture("de-font1.3-expanded-bright")
    }

    @Test fun transitionKeepsTheOpeningPressFromActivatingCards() {
        content()
        compose.mainClock.autoAdvance = false
        key(Key.DirectionDown)
        compose.mainClock.advanceTimeBy(96)
        capture("en-font1-opening")
        assertEquals(0, picks)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        card(2).assertIsFocused()
    }

    private fun content(
        fontScale: Float = 1f,
        bright: Boolean = false,
    ) {
        compose.setContent {
            view = LocalView.current
            layers = rememberLivePlayerLayerState()
            val context = LocalContext.current
            val session = remember {
                FakeSessionObservation(SessionObservation.create(
                    sessionState = SessionState.Ready(ServerCapabilities.create(
                        streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
                    )),
                    channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
                    epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
                    dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
                )).captureCurrentSession()
            }
            val loader = remember {
                ImageLoader.Builder(context)
                    .components { add(object : Mapper<AppArtworkSource, ByteArray> {
                        override fun map(data: AppArtworkSource, options: Options): ByteArray {
                            val bitmap = Bitmap.createBitmap(200, 90, Bitmap.Config.ARGB_8888)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = 0xFF79D1FF.toInt(); textSize = 64f; isFakeBoldText = true
                            }
                            Canvas(bitmap).drawText("TV ${data.id.value}", 12f, 68f, paint)
                            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                        }
                    }) }
                    .coroutineContext(Dispatchers.Main.immediate)
                    .fetcherCoroutineContext(Dispatchers.Main.immediate)
                    .decoderCoroutineContext(Dispatchers.Main.immediate)
                    .diskCache(null).build()
            }
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TVHeadendPlayerTheme {
                    focusColor = androidx.tv.material3.MaterialTheme.colorScheme.onSurface.toArgb()
                    Box(Modifier.fillMaxSize().background(if (bright) Color(0xFFDDEAF0) else Color(0xFF24384A))
                        .onPreviewKeyEvent { event ->
                            val code = event.nativeKeyEvent.keyCode
                            when {
                                layers.revealingKeyCode == code -> {
                                    if (event.type == KeyEventType.KeyUp) layers.endOpeningKeyCycle(code)
                                    true
                                }
                                event.key == Key.Back && layers.channelDrawerOpen -> {
                                    if (event.type == KeyEventType.KeyDown) {
                                        layers.beginOpeningKeyCycle(code)
                                        layers.dismissChannelDrawer()
                                    }
                                    true
                                }
                                else -> false
                            }
                        }) {
                        PlayerChrome(
                            mode = PlayerChromeMode.CONTROLS,
                            content = PlayerChromeContent("", liveInfoBarData(playing?.value,
                                playing?.let { "Channel ${it.value}" }.orEmpty(), null, null, false, 900, "")),
                            timeline = PlayerChromeTimeline.Live(AppTimeshiftState(), nowSec = 900, programme = playing?.let(::event)),
                            actions = PlayerChromeActions(active = layers.chrome.controlsVisible, restoreFocus = layers.restoreChannelAction),
                            imageLoader = loader, currentSession = session,
                            onDownFromActions = {
                                layers.beginOpeningKeyCycle(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                                layers.openChannelDrawer()
                            },
                            onStop = {}, onInteraction = {}, onOptions = {}, onInfo = {},
                            onTogglePause = {}, onSeek = {},
                            onFocusRestored = layers::onChannelActionRestored,
                            onActionFocused = layers::onActionFocused,
                            decorationCoversControls = layers.channelDrawerOpen,
                            controlsDecoration = { emphasisAlpha, controls -> QuickZapPresentation(
                              expanded = layers.channelDrawerOpen,
                              channelsAvailable = true,
                              peekAlpha = emphasisAlpha,
                              preview = {},
                              controls = controls,
                              channelContent = {
                                ChannelDrawer(
                                    channels = catalog, selectedId = playing, playingChannelId = playing,
                                    playbackIndicator = indicator,
                                    recordingChannelIds = setOf(ChannelId(4)), nowEvent = { if (it == ChannelId(5)) null else event(it) },
                                    imageLoader = loader, currentSession = session, active = layers.channelDrawerOpen, nowSec = 900,
                                    onFocusChannel = {}, onPickChannel = {
                                        if (it.id == playing) layers.dismissChannelDrawer() else {
                                            if (confirmPicks) playing = it.id
                                            picks++
                                            layers.onChannelTuneRequested()
                                        }
                                    },
                                    onCloseDrawer = {
                                        if (it != null) layers.beginOpeningKeyCycle(it)
                                        layers.dismissChannelDrawer()
                                    },
                                )
                              },
                            ) },
                        )
                    }
                }
            }
        }
        // The player enters with the Banner; these cases start from the revealed controls.
        compose.runOnIdle { layers.showControls() }
        compose.waitForIdle()
    }

    private fun event(id: ChannelId) = EpgEvent.create(
        id = EventId(id.value), channelId = id, title = "A journey through the mountains",
        start = Instant.fromEpochSeconds(0), stop = Instant.fromEpochSeconds(3600),
    )
    private fun card(id: Int) = compose.onNodeWithTag("player-channel-card-$id")
    private fun key(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }
    private fun capture(name: String, focusedCard: Int? = null, fontScale: Float = 1f) {
        compose.waitForIdle()
        val focusedBounds = focusedCard?.let { card(it).assertIsFocused().fetchSemanticsNode().boundsInRoot }
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("../artifacts/quick-zap").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (focusedCard != null) File(directory, "$name.txt").writeText(
                "canvas=960x540\ndensity=1.0\nlocale=${if (name.startsWith("de")) "de" else "en"}\n" +
                    "fontScale=$fontScale\nfocus=player-channel-card-$focusedCard\nbounds=$focusedBounds\n" +
                    "focusOutlineArgb=${Integer.toHexString(focusColor)}\n",
            )
            bitmap.recycle()
        }
    }
}
