package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.components.TabContentMotion
import at.bernhardberger.tvhplayer.ui.components.TabContent
import at.bernhardberger.tvhplayer.ui.components.TabOwner
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.depth.DepthItem
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigation
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import at.bernhardberger.tvhplayer.ui.components.rememberTabContentMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import kotlin.math.roundToInt

/** Pixel oracles for the production shell/scene/tab hosts, not a reconstruction of their layers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseLayeringTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private lateinit var motion: TabContentMotion
    private var selected by mutableStateOf("A")
    private var destination by mutableStateOf<AppNavKey>(ChannelsKey)
    private lateinit var backStack: MutableList<AppNavKey>
    private val visits = mutableListOf<Visit>()
    private val mounted = mutableSetOf<Visit>()
    private val header = Ink(88f, Color.Green)
    private val tabs = Ink(144f, Color.Magenta)
    private var rtl = false
    private var focusTag = "fixture-focus"

    @Test fun activeHeaderTabsAndRowsKeepTheirColourInsideTheClosedGradient() = settled("closed")

    @Test fun activeHeaderTabsAndRowsKeepTheirColourInsideTheExpandedGradient() = settled("expanded", expanded = true)

    @Test
    @Config(qualifiers = "en-ldrtl-w960dp-h540dp-land-mdpi")
    fun activeInkKeepsItsColourInsideTheRtlGradient() = settled("rtl", rightToLeft = true)

    @Test
    @Config(qualifiers = "en-ldrtl-w960dp-h540dp-land-mdpi")
    fun activeInkKeepsItsColourInsideTheExpandedRtlGradient() =
        settled("rtl-expanded", expanded = true, rightToLeft = true)

    @Test fun aPendingRequestDoesNotReclassifyTheStillRenderedVisit() {
        shell()
        compose.mainClock.autoAdvance = false
        val before = capture("pending-before")
        val original = visits.single()
        compose.runOnIdle { motion.select("B", listOf("A", "B", "C")) }
        advance(32)
        val waiting = capture("pending-waiting")

        assertFalse("action ownership is already withdrawn", original.owner.isCurrent)
        assertEquals("waiting must not compose a second visit", 1, visits.size)
        assertTrue("visual ownership must follow the rendered visit, not action ownership", before.sameAs(waiting))
    }

    @Test fun rapidReturnAndExternalReplacementDoNotDuplicateVisitsOrDimTheNewTarget() {
        shell()
        compose.mainClock.autoAdvance = false
        val original = visits.single()
        select("B")
        advance(64)
        capture("scope-b-before-return")
        select("A")
        advance(64)
        capture("scope-a-b-a-interruption")
        assertFalse("old A cannot become current again", original.owner.isCurrent)
        assertEquals("one composition per rendered visit", 3, visits.size)
        assertEquals("only the newest visit may act", 1, visits.count { it.owner.isCurrent })

        val replaced = visits.toList()
        compose.runOnIdle { selected = "C" }
        advance(64)
        val replacement = capture("scope-external-replacement")
        assertEquals("external replacement disposes the obsolete transition", 1, mounted.size)
        assertTrue("external replacement cannot resurrect an old visit", replaced.none { it.owner.isCurrent })
        assertEquals("external replacement leaves one actionable visit", 1, visits.count { it.owner.isCurrent })
        assertActiveInk(replacement, mounted.single().ink, "external replacement")
    }

    @Test fun departingDestinationDominatesItsLocallyCurrentInterruptedScopeExactlyOnce() {
        shell()
        compose.mainClock.autoAdvance = false
        select("B")
        advance(128)
        compose.runOnIdle {
            motion.select("A", listOf("A", "B", "C"))
            selected = "A"
            navigate(GuideKey)
        }
        advance(96)
        val bitmap = capture("destination-and-scope-interruption")
        val localCurrent = visits.single { it.owner.isCurrent }
        assertEquals("interrupted return is a new A", 3, visits.size)
        val (near, far) = sample(bitmap, localCurrent.ink)
        assertTrue("fixture must expose the moving/fading child outside the gradient", far in .03f..0.95f)
        val factor = backgroundFactor(bitmap)
        assertEquals("parent departure protects even a locally current child, once", far * factor, near, .015f)
        assertTrue("oracle distinguishes no protection", far - near > .025f)
        assertTrue("oracle distinguishes double protection", near - far * factor * factor > .015f)
    }

    @Test fun cachedDepthInkTracksAncestorMotionAndOpeningDrawer() = movingDepthInk(false)

    @Test
    @Config(qualifiers = "en-ldrtl-w960dp-h540dp-land-mdpi")
    fun cachedDepthInkTracksAncestorMotionAndOpeningRtlDrawer() = movingDepthInk(true)

    @Test fun returningCategoryKeepsTranslucentInkHolesAndActiveOrdering() {
        destination = SettingsKey(SettingsSection.GENERAL)
        val created = mutableMapOf<SettingsSection, Int>()
        val disposed = mutableMapOf<SettingsSection, Int>()
        shell(settingsContent = { key ->
            DisposableEffect(key.section) {
                created[key.section] = (created[key.section] ?: 0) + 1
                onDispose { disposed[key.section] = (disposed[key.section] ?: 0) + 1 }
            }
            val first = key.section == SettingsSection.GENERAL
            Box(Modifier.fillMaxSize().graphicsLayer().drawBehind {
                val color = if (first) Color.Green.copy(alpha = .5f) else Color.Blue.copy(alpha = .4f)
                drawRect(color, Offset(0f, if (first) 200f else 260f), Size(size.width, 24f))
                drawRect(color, Offset(0f, 320f), Size(size.width, 24f))
            })
        })
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigate(SettingsKey(SettingsSection.PLAYER)) }
        advance(48)
        compose.runOnIdle { navigate(SettingsKey(SettingsSection.GENERAL)) }
        advance(32)
        val bitmap = capture("category-a-b-a-translucent")
        val near = nearX(bitmap)
        val far = 600
        val factor = backgroundFactor(bitmap)
        val a = Color(bitmap.getPixel(far, 212)).green
        val b = Color(bitmap.getPixel(far, 272)).blue
        assertTrue("both entries must still be fading", a in .1f..0.49f && b in .03f..0.39f)
        fun pixel(y: Int) = Color(bitmap.getPixel(near, y))
        assertEquals("active translucent ink stays above the gradient", a, pixel(212).green, .015f)
        assertEquals("active alpha is preserved", factor * (1f - a), pixel(212).red, .015f)
        assertEquals("outgoing colour is attenuated once", b * factor, pixel(272).blue, .015f)
        assertEquals("outgoing alpha is preserved", factor * (1f - b), pixel(272).red, .015f)
        assertEquals("returning A's real wrapper must be on top of B", a, pixel(332).green, .015f)
        assertEquals("active ink occludes outgoing ink, not the reverse", b * factor * (1f - a), pixel(332).blue, .015f)
        assertEquals("overlap preserves background coverage", factor * (1f - a) * (1f - b), pixel(332).red, .015f)
        assertEquals("holes cannot acquire a per-sheet rectangle", factor, pixel(392).red, .01f)
        assertEquals(mapOf(SettingsSection.GENERAL to 1, SettingsSection.PLAYER to 1), created)
        assertTrue("no interrupted entry may be disposed early", disposed.isEmpty())
        advance(1000)
        assertEquals("only the obsolete entry is released after the shared fade", mapOf(SettingsSection.PLAYER to 1), disposed)
        capture("category-a-b-a-settled")
    }

    @Test fun categoryReturnThenDestinationDepartureFiltersMovingChildOnceWithoutRemounting() {
        destination = SettingsKey(SettingsSection.GENERAL)
        selected = "B"
        val created = mutableMapOf<SettingsSection, Int>()
        val disposed = mutableMapOf<SettingsSection, Int>()
        shell(settingsContent = { key ->
            DisposableEffect(key.section) {
                created[key.section] = (created[key.section] ?: 0) + 1
                onDispose { disposed[key.section] = (disposed[key.section] ?: 0) + 1 }
            }
            if (key.section == SettingsSection.GENERAL) Page() else Box(Modifier.fillMaxSize())
        })
        compose.mainClock.autoAdvance = false
        select("A")
        advance(64)
        compose.runOnIdle { navigate(SettingsKey(SettingsSection.PLAYER)) }
        advance(32)
        compose.runOnIdle { navigate(SettingsKey(SettingsSection.GENERAL)) }
        advance(32)
        compose.runOnIdle { navigate(GuideKey) }
        advance(64)
        val bitmap = capture("category-return-destination-departure")
        val current = visits.single { it.owner.isCurrent }
        val (near, far) = sample(bitmap, current.ink)
        val factor = backgroundFactor(bitmap)
        assertTrue("child must still be visible during the destination exit", far in .03f..0.95f)
        assertEquals("destination departure dominates both nested boundaries, once", far * factor, near, .015f)
        assertTrue("the nested-double-filter oracle must discriminate", near - far * factor * factor > .02f)
        assertEquals("the category return must reuse its content", mapOf(SettingsSection.GENERAL to 1, SettingsSection.PLAYER to 1), created)
        assertEquals("one composition per scope visit", 2, visits.size)
        assertEquals("the exiting scope is still retained", 2, mounted.size)
        assertTrue("entries stay mounted until their shared transition settles", disposed.isEmpty())
        advance(1200)
        assertEquals(created, disposed)
        assertTrue("Settings and both scope owners leave together", mounted.isEmpty())
        assertTrue(visits.none { it.owner.isCurrent })
    }

    @Test fun earlierDestinationDrawsAboveLaterDepartingDestination() {
        destination = SettingsKey(SettingsSection.GENERAL)
        val outgoing = Ink(256f, Color.Blue.copy(alpha = .4f))
        shell(
            pageContent = {
                Box(Modifier.fillMaxSize().drawBehind {
                    drawRect(Color.Green.copy(alpha = .6f), size = Size(440f, size.height))
                })
            },
            settingsContent = { PaintInk(outgoing) },
        )
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigate(ChannelsKey) }
        advance(160)
        val bitmap = capture("destination-active-before-outgoing-slot")
        val x = nearX(bitmap)
        val y = requireNotNull(outgoing.coordinates).localToRoot(Offset(0f, outgoing.y + 16f)).y.roundToInt()
        val activeAlpha = Color(bitmap.getPixel(x, 120)).green
        val outgoingAlpha = Color(bitmap.getPixel(700, y)).blue
        val overlap = Color(bitmap.getPixel(x, y))
        assertTrue("both destinations must still be fading", activeAlpha in .03f..0.57f && outgoingAlpha in .03f..0.39f)
        assertTrue("the overlap must discriminate reversed sibling order", activeAlpha * outgoingAlpha > .025f)
        assertEquals("the active earlier slot is above the outgoing later slot", activeAlpha, overlap.green, .015f)
        assertEquals("only outgoing ink is attenuated beneath active translucent ink",
            outgoingAlpha * backgroundFactor(bitmap) * (1f - activeAlpha), overlap.blue, .015f)
    }

    @Test fun outgoingPaneDoesNotCropOverflowInsideAnExistingViewportAlphaLayer() {
        val ink = Ink(32f, Color.Cyan)
        val navigation = DepthNavigationState(DepthStack(listOf(DepthFrame("root", "child"))))
        shell(pageContent = {
            // An existing full-viewport alpha may clip at its own bounds. The new
            // outgoing boundary must not add a smaller pane-sized crop inside it.
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = .75f }) {
                DepthInk(navigation, ink, width = 200, overflow = 32f, previewAlpha = 1f)
            }
        })
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            navigation.update(DepthStack(listOf(DepthFrame("root", "child"), DepthFrame("child")), visit = 1))
        }
        repeat(3) { frame ->
            advance(16)
            val bitmap = capture("pane-overflow-alpha-$frame")
            val coordinates = requireNotNull(ink.coordinates)
            val edge = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), ink.y + 16f))
            val x = (edge.x + 16).roundToInt()
            val y = edge.y.roundToInt()
            assertTrue("sample must be outside the pane but inside the ancestor viewport", x > edge.x && x in 81..900)
            val factor = Color(bitmap.getPixel(x, 520)).red
            assertEquals("overflow ink survives the outgoing pane boundary under alpha", .75f * factor,
                Color(bitmap.getPixel(x, y)).green, .015f)
        }
    }

    private fun movingDepthInk(rightToLeft: Boolean) {
        val ink = Ink(32f, Color.Cyan.copy(alpha = .6f))
        val navigation = DepthNavigationState(DepthStack(listOf(DepthFrame("root", "child"))))
        // Wide cached ink lets the same physical samples remain covered while the
        // production strip translates in either layout direction. Pane alpha stays
        // one; translucency belongs to the ink, not a node-bounds offscreen layer.
        shell(rightToLeft, pageContent = { DepthInk(navigation, ink, width = 600, overflow = 800f, previewAlpha = 1f) })
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            navigation.update(DepthStack(listOf(DepthFrame("root", "child"), DepthFrame("child")), visit = 1))
        }
        val frames = mutableListOf<Triple<Float, Int, Float>>()
        var previousWidth = drawerWidth()
        var expandingFrames = 0
        repeat(10) { frame ->
            if (frame == 3) {
                compose.onNodeWithTag("nav-channels").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                focusTag = "nav-channels"
            }
            advance(16)
            val bitmap = capture("depth-alignment-${if (rtl) "rtl" else "ltr"}-$frame")
            val coordinates = requireNotNull(ink.coordinates)
            val origin = coordinates.localToRoot(Offset(0f, ink.y + 16f))
            val right = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), ink.y + 16f)).x
            val x = nearX(bitmap)
            val farX = bitmap.width / 2
            val y = origin.y.roundToInt()
            assertTrue("moving ink must cover the physical gradient sample at frame $frame", x > origin.x - 800 && x < right + 800)
            val factor = backgroundFactor(bitmap)
            val near = Color(bitmap.getPixel(x, y))
            val far = Color(bitmap.getPixel(farX, y))
            assertTrue("far sample must remain beyond the runout", far.red < .5f && far.green > .55f)
            assertEquals("moving cached ink is aligned with physical x at frame $frame", far.green * factor, near.green, .015f)
            assertEquals("moving ink preserves alpha at frame $frame", (1f - far.green) * factor, near.red, .015f)
            frames += Triple(origin.x, ink.recordings, near.green)
            if (drawerWidth() > previousWidth + .5f) expandingFrames++
            previousWidth = drawerWidth()
        }
        assertTrue("drawer must expand during the still-moving strip", expandingFrames >= 3)
        assertTrue("the oracle must exercise moving ancestors with cached leaf drawing", frames.zipWithNext().any { (a, b) ->
            kotlin.math.abs(a.first - b.first) > 2f && a.second == b.second && kotlin.math.abs(a.third - b.third) > .005f
        })
    }

    @Composable private fun DepthInk(
        navigation: DepthNavigationState,
        ink: Ink,
        width: Int,
        overflow: Float = 0f,
        previewAlpha: Float = .6f,
    ) {
        val levels = remember {
            mapOf(
                "root" to DepthLevel("root", listOf(DepthRow(DepthItem("child", "child")) { _, _ -> }), heading = {
                    Box(Modifier.fillMaxWidth().height(96.dp).onGloballyPositioned { ink.coordinates = it }
                        .drawWithCache {
                            val layer = obtainGraphicsLayer()
                            ink.recordings++
                            layer.record {
                                drawRect(ink.color, Offset(-overflow, ink.y), Size(size.width + 2 * overflow, 32f))
                            }
                            onDrawBehind { drawLayer(layer) }
                        })
                }),
                "child" to DepthLevel("child", listOf(DepthRow(DepthItem("leaf")) { _, _ -> }), heading = {}),
            )
        }
        DepthNavigation(
            state = navigation,
            levels = levels,
            fallbackLevel = { levels.getValue("child") },
            columnWidth = width.dp,
            columnGap = 0.dp,
            contentPadding = PaddingValues(start = 48.dp, top = 240.dp),
            modifier = Modifier.fillMaxSize(),
            initialFocusEnabled = false,
            previewAlpha = previewAlpha,
        )
    }

    private fun settled(name: String, expanded: Boolean = false, rightToLeft: Boolean = false) {
        shell(rightToLeft)
        if (expanded) {
            compose.onNodeWithTag("nav-channels")
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.waitForIdle()
            focusTag = "nav-channels"
        }
        compose.onNodeWithTag(focusTag).assertIsFocused()
        assertEquals("native drawer width", if (expanded) 280f else 80f, drawerWidth(), .5f)
        val bitmap = capture(name)
        val factor = backgroundFactor(bitmap)
        val expectedFactor = 1f - .95f * (1f - (drawerWidth() + 48f) / (drawerWidth() + 128f))
        assertEquals("the original background gradient remains", expectedFactor, factor, .01f)
        assertActiveInk(bitmap, header, "stationary header")
        assertActiveInk(bitmap, tabs, "stationary tabs")
        assertActiveInk(bitmap, visits.single().ink, "rendered rows")
    }

    private fun select(key: String) {
        compose.runOnIdle {
            motion.select(key, listOf("A", "B", "C"))
            selected = key
        }
    }

    private fun advance(millis: Long) {
        // Robolectric's Android layout/snapshot notifications are not driven by MainTestClock.
        // Flush between frames so a rapid second request cannot overtake the first composition.
        repeat((millis / 16).toInt()) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.waitForIdle()
    }

    private fun navigate(key: AppNavKey) {
        destination = key
        backStack.navigateTopLevel(key)
    }

    private fun shell(
        rightToLeft: Boolean = false,
        pageContent: @Composable () -> Unit = { Page() },
        settingsContent: @Composable (SettingsKey) -> Unit = {},
    ) {
        rtl = rightToLeft
        val contentFocus = FocusRequester()
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    backStack = rememberAppNavBackStack(destination)
                    // Red has no green/blue: it separates background attenuation from ink alpha.
                    Box(Modifier.fillMaxSize().background(Color.Red)) {
                        SideRail(
                            currentRoute = when (destination) {
                                ChannelsKey -> AppDestination.CHANNELS
                                is SettingsKey -> AppDestination.SETTINGS
                                else -> AppDestination.GUIDE
                            },
                            showEpgMenu = true,
                            onRootBack = {},
                            onNavigate = {},
                        ) { _, drawerActive ->
                            NavDisplay(
                                backStack = backStack,
                                onBack = {},
                                sceneStrategies = listOf(rememberSidebarGuideSceneStrategy(drawerActive, backStack.last())),
                                transitionSpec = { appDestinationContentTransform() },
                                entryProvider = entryProvider {
                                    entry<ChannelsKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.CHANNELS)) {
                                        pageContent()
                                    }
                                    entry<GuideKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.GUIDE)) {
                                        // Transparent target isolates the outgoing scene's alpha/translation.
                                        Box(Modifier.fillMaxSize())
                                    }
                                    entry<SettingsKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.SETTINGS)) {
                                        settingsContent(it)
                                    }
                                },
                            )
                            Button(
                                onClick = {},
                                modifier = Modifier.padding(start = 600.dp, top = 24.dp)
                                    .focusRequester(contentFocus).testTag("fixture-focus"),
                            ) { Text("Fixture focus") }
                            LaunchedEffect(drawerActive) {
                                if (!drawerActive) contentFocus.requestFocus()
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("fixture-focus").assertIsFocused()
    }

    @Composable private fun Page() {
        motion = rememberTabContentMotion(selected)
        Box(Modifier.fillMaxSize()) {
            PaintInk(header)
            PaintInk(tabs)
            TabContent(motion, selectedKey = selected, state = { selected }, modifier = Modifier.fillMaxSize()) { _, owner ->
                val visit = remember(owner) {
                    Visit(owner, Ink(208f + 56f * visits.size, Color.Cyan)).also { visits += it }
                }
                DisposableEffect(visit) {
                    mounted += visit
                    onDispose { mounted -= visit }
                }
                PaintInk(visit.ink)
            }
        }
    }

    @Composable private fun PaintInk(ink: Ink) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { ink.coordinates = it }.drawBehind {
            drawRect(ink.color, Offset(0f, ink.y), Size(size.width, 32f))
        })
    }

    private fun drawerWidth() = compose.onNodeWithTag("global-drawer-surface").fetchSemanticsNode().boundsInRoot.width

    private fun nearX(bitmap: Bitmap): Int =
        (if (rtl) bitmap.width - drawerWidth() - 48f else drawerWidth() + 48f).roundToInt()

    private fun backgroundFactor(bitmap: Bitmap) = Color(bitmap.getPixel(nearX(bitmap), 520)).red

    private fun sample(bitmap: Bitmap, ink: Ink): Pair<Float, Float> {
        val coordinates = requireNotNull(ink.coordinates)
        val topLeft = coordinates.localToRoot(Offset(0f, ink.y + 16f))
        val right = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), ink.y + 16f)).x
        val x = nearX(bitmap)
        assertTrue("ink must actually cross the sampled gradient pixel", x > topLeft.x + 2 && x < right - 2)
        val farX = if (rtl) maxOf(16f, topLeft.x + 32f) else minOf(bitmap.width - 16f, right - 32f)
        val y = topLeft.y.roundToInt()
        fun peak(at: Int): Float = Color(bitmap.getPixel(at, y)).let { maxOf(it.green, it.blue) }
        return peak(x) to peak(farX.roundToInt())
    }

    private fun assertActiveInk(bitmap: Bitmap, ink: Ink, label: String) {
        val (near, far) = sample(bitmap, ink)
        assertEquals("$label reference must contain bright ink", 1f, far, .01f)
        assertEquals("$label inside the navbar gradient must retain its colour", far, near, .01f)
    }

    private fun capture(name: String): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val directory = File("build/outputs/browse-cohesion/shell").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        File(directory, "$name.txt").writeText(
            "captureUtc=${Instant.now()}\ncanvas=${bitmap.width}x${bitmap.height}; density=1; fontScale=1; locale=en\n" +
                "direction=${if (rtl) "RTL" else "LTR"}; drawerWidth=${drawerWidth()}; destination=$destination; scope=$selected\n" +
                "focus=$focusTag; clockMillis=${compose.mainClock.currentTime}; autoAdvance=${compose.mainClock.autoAdvance}\n" +
                "fixture=synthetic red background and coloured ink; production SideRail/sidebar scene with scope, category or depth content\n" +
                "createdVisits=${visits.size}; mountedVisits=${mounted.size}; actionableVisits=${visits.count { it.owner.isCurrent }}\n",
        )
        return bitmap
    }

    private class Ink(val y: Float, val color: Color) {
        var coordinates: LayoutCoordinates? = null
        var recordings = 0
    }
    private class Visit(val owner: TabOwner, val ink: Ink)
}
