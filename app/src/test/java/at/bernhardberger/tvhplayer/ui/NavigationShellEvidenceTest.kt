package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isNotFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.player.DebugVideoBackdrop
import at.bernhardberger.tvhplayer.ui.screens.SETTINGS_ROOT
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settingsRootLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsGeneralLevels
import at.bernhardberger.tvhplayer.viewmodels.CacheClearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Global navigation shell evidence: Material for TV drawer geometry, the
 * non-focusable brand top section, and the absence of any drawer-owned scrim.
 * Captures render the production composables on the 960x540 logical canvas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationShellEvidenceTest {
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun scopeTabContentDoesNotReclipLeadingOverflow() {
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.Black).padding(start = 80.dp)) {
                at.bernhardberger.tvhplayer.ui.components.BrowseTabContent(
                    motion = at.bernhardberger.tvhplayer.ui.components.rememberBrowseContentMotion("scope"),
                    selectedKey = "scope",
                    state = { Unit },
                    modifier = Modifier.fillMaxSize(),
                ) { _, _ ->
                    Box(Modifier.fillMaxSize().drawBehind {
                        drawRect(Color.Red, topLeft = androidx.compose.ui.geometry.Offset(-80.dp.toPx(), 0f))
                    })
                }
            }
        }
        compose.waitForIdle()
        assertEquals("scope wrapper must allow content beneath the rail", Color.Red.toArgb(), drawShell().getPixel(40, 400))
    }
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi") fun englishNormal() = captureShell("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi") fun germanNormal() = captureShell("de", 1f)
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi") fun englishEnlarged() = captureShell("en", 1.3f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi") fun germanEnlarged() = captureShell("de", 1.3f)

    /**
     * 12 + 56 + 12 closed and 12 + 256 + 12 expanded, with Settings C keeping its
     * accepted absolute columns on the re-aligned shell.
     */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun drawerWidthsStayEightyAndTwoEightyAroundSettingsColumns() {
        settingsShell(1f)

        assertEquals(80f, drawerWidth(), .5f)
        assertEquals(128f, bounds("depth-active").left, .5f)
        assertEquals(352f, bounds("depth-active").width, .5f)
        assertEquals(588f, derivedPreviewLeft(bounds("depth-active")), .5f)

        openDrawer()

        assertEquals(280f, drawerWidth(), .5f)
        // The expanded drawer pushes the unchanged viewport: same width, 200dp over.
        assertEquals(328f, bounds("depth-active").left, .5f)
        assertEquals(352f, bounds("depth-active").width, .5f)
        assertEquals(788f, derivedPreviewLeft(bounds("depth-active")), .5f)
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun brandHeaderIsNotAFocusTargetAndEntryFocusesTheCurrentDestination() {
        settingsShell(1f)
        val appName = RuntimeEnvironment.getApplication().resources
            .getString(at.bernhardberger.tvhplayer.R.string.app_name)

        compose.onNodeWithTag("global-drawer-brand").assertExists()
        compose.onNodeWithTag("global-drawer-brand").assert(isNotFocusable())
        compose.onNodeWithTag("global-drawer-brand")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        // Like the item labels, the wordmark is only composed while expanded.
        compose.onNodeWithTag("global-drawer-wordmark").assertDoesNotExist()
        compose.onAllNodesWithContentDescription(appName).assertCountEquals(0)

        openDrawer()
        // Drawer entry still lands on the current destination, not on the mark.
        compose.onNodeWithTag("nav-settings").assertIsFocused()
        compose.onNodeWithTag("global-drawer-wordmark")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onAllNodesWithContentDescription(appName).assertCountEquals(1)

        compose.onNodeWithTag("nav-channels")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        // Up from the first destination cannot reach the brand section.
        compose.onNodeWithTag("nav-channels").assertIsFocused()
        compose.onNodeWithTag("global-drawer-brand").assert(isNotFocusable())
    }

    /**
     * The shared overlap backing falls off gradually in either drawer state,
     * and leaves the rest of the content untouched.
     */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun sharedDrawerBackingHasSoftFalloffInBothStates() {
        val contentFocus = FocusRequester()
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                Box(Modifier.fillMaxSize().background(Color.Red)) {
                    SideRail(
                        currentRoute = AppDestination.CHANNELS,
                        showEpgMenu = true,
                        onRootBack = {},
                        onNavigate = {},
                    ) { _, drawerActive ->
                        // Focusable browse content, well clear of the sampled band.
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .padding(start = 600.dp, top = 40.dp)
                                .focusRequester(contentFocus)
                                .testTag("browse-field"),
                        ) { Text("Browse") }
                        LaunchedEffect(drawerActive) {
                            if (!drawerActive) contentFocus.requestFocus()
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        assertEquals(80f, drawerWidth(), .5f)
        assertUntouchedField("closed")

        compose.onNodeWithTag("nav-channels")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()

        assertEquals(280f, drawerWidth(), .5f)
        assertUntouchedField("open")
    }

    /**
     * Kit anatomy: the icon column stays at 12 + 16 + 12 = 40dp in both states and
     * the mark shares it, so nothing in the rail moves horizontally when it
     * expands (the library alone would nudge its icon 4dp right). The wordmark
     * shares the item label's origin, and the mark is dimmed like the inactive
     * items while the drawer is closed.
     */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun nothingInTheRailMovesHorizontallyWhenTheDrawerExpands() {
        settingsShell(1f)

        val iconAxis = 12f + 16f + 12f
        assertEquals("collapsed icon axis", iconAxis, itemIconCenter(), 1f)
        assertEquals("collapsed mark axis", iconAxis, symbolCenter(), 1f)
        val dimmed = symbolPeakBlue()

        openDrawer()

        assertEquals("expanded icon axis", iconAxis, itemIconCenter(), 1f)
        assertEquals("expanded mark axis", iconAxis, symbolCenter(), 1f)
        assertEquals("wordmark on the label origin", labelInkLeft(), bounds("global-drawer-wordmark").left, 1f)
        assertTrue("mark brightens when the drawer is active ($dimmed -> ${symbolPeakBlue()})", symbolPeakBlue() > dimmed + 0.3f)
    }

    /**
     * The collapsed-rail dim is the library's own: `NavigationDrawerScope.hasFocus`
     * is `drawerState.currentValue == DrawerValue.Open`, so a closed drawer renders
     * unselected items with `inactiveContentColor` while the selected destination
     * keeps its full colour and container — the rail still reports where you are.
     * The brand mark is not an item, so it follows the same ratio explicitly.
     *
     * The selected container is asserted against the library value on the closed
     * capture only: opening the drawer also focuses that destination, so the open
     * capture samples the focus container and cannot witness a selected-state dim.
     */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun collapsedDrawerDimsUnselectedContentAndKeepsTheSelectedDestinationFull() {
        settingsShell(1f)
        val closed = drawShell()
        val iconClosed = destinationIconPeak(closed)
        val brandClosed = brandPeakBlue(closed)
        val indicatorClosed = selectedIndicator(closed)

        openDrawer()
        val open = drawShell()
        val iconOpen = destinationIconPeak(open)
        val brandOpen = brandPeakBlue(open)

        assertInactiveAlpha("unselected destination icon", iconClosed, iconOpen)
        assertInactiveAlpha("brand mark", brandClosed, brandOpen)

        val expectedIndicator = overlay(
            TvDarkColors.secondaryContainer,
            0.4f,
            TvDarkColors.background,
        )
        assertTrue(
            "closed selected indicator $indicatorClosed should be the library selected " +
                "container (expected $expectedIndicator), undimmed by any app-owned rail alpha",
            channelDistance(indicatorClosed, expectedIndicator) < 0.04f,
        )
    }

    /** Strongest blue channel inside the brand box, as drawn. */
    private fun symbolPeakBlue(): Float = brandPeakBlue(drawShell())

    private fun brandPeakBlue(bitmap: Bitmap): Float {
        val brand = bounds("global-drawer-brand")
        return (0..70).maxOf { x ->
            (brand.top.toInt()..brand.bottom.toInt()).maxOf { y -> Color(bitmap.getPixel(x, y)).blue }
        }
    }

    /**
     * Strongest channel of the unselected Channels icon. Settings is the selected
     * destination in [settingsShell], so this ink is ordinary item content.
     */
    private fun destinationIconPeak(bitmap: Bitmap): Float {
        val row = bounds("nav-channels")
        val background = bitmap.getPixel(2, 300)
        var peak = 0f
        for (x in 12..64) {
            for (y in row.top.toInt()..row.bottom.toInt()) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == background) continue
                val color = Color(pixel)
                peak = maxOf(peak, color.red, color.green, color.blue)
            }
        }
        check(peak > 0f) { "no destination icon ink in ${row}" }
        return peak
    }

    /** Trailing interior of the selected Settings item — the pill, not the icon. */
    private fun selectedIndicator(bitmap: Bitmap): Color {
        val row = bounds("nav-settings")
        val x = (row.right - 8f).toInt()
        val y = ((row.top + row.bottom) / 2f).toInt()
        return Color(bitmap.getPixel(x, y))
    }

    /** graphicsLayer alpha over an opaque colour on [TvDarkColors.background]. */
    private fun overlay(source: Color, alpha: Float, background: Color) = Color(
        red = source.red * alpha + background.red * (1f - alpha),
        green = source.green * alpha + background.green * (1f - alpha),
        blue = source.blue * alpha + background.blue * (1f - alpha),
    )

    private fun channelDistance(a: Color, b: Color) = maxOf(
        kotlin.math.abs(a.red - b.red),
        kotlin.math.abs(a.green - b.green),
        kotlin.math.abs(a.blue - b.blue),
    )

    /**
     * Content drawn at the library's `inactiveContentColor` over the dark
     * background lands near 0.4 of the active peak, not full opacity.
     */
    private fun assertInactiveAlpha(label: String, inactive: Float, active: Float) {
        val ratio = inactive / active
        assertTrue("$label must brighten when the drawer is active ($inactive -> $active)", active > inactive + 0.08f)
        assertTrue(
            "$label inactive/active peak $ratio ($inactive / $active) should be the library " +
                "inactive content colour, not full opacity or an app-owned rail alpha",
            ratio in 0.33f..0.50f,
        )
    }

    /** Horizontal centre of the first destination's icon ink, as drawn. */
    private fun itemIconCenter(): Float {
        val bitmap = drawShell()
        val row = bounds("nav-channels")
        val rows = row.top.toInt()..row.bottom.toInt()
        val columns = (12..64).filter { x ->
            rows.any { y -> channelDistance(Color(bitmap.getPixel(x, y)), Color(bitmap.getPixel(x, 400))) > 0.03f }
        }
        check(columns.isNotEmpty()) { "no icon ink in rows $rows of ${bitmap.width}x${bitmap.height}; brand=${bounds("global-drawer-brand")}" }
        return (columns.first() + columns.last()) / 2f
    }

    /**
     * Kit vertical anatomy: 12dp edge, 56dp top section, destinations centred
     * between the top and bottom sections, 12dp item gap. Rows must not move
     * when the drawer expands.
     */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun destinationsAreCentredBetweenTopAndBottomSectionsInBothStates() {
        settingsShell(1f)

        fun rows() = listOf("nav-channels", "nav-epg", "nav-recordings", "nav-settings").map { bounds(it).top }

        val brand = bounds("global-drawer-brand")
        assertEquals("top section under the 12dp edge", 12f, brand.top, 1f)
        assertEquals("top section height", 56f, brand.height, 1f)
        val collapsed = rows()
        assertEquals("settings above the 12dp edge", 540f - 12f - 48f, collapsed[3], 1f)
        assertEquals("first item row", 190f, collapsed[0], 1f)
        assertEquals("item gap", 60f, collapsed[1] - collapsed[0], 1f)
        val above = collapsed[0] - brand.bottom
        val below = collapsed[3] - (collapsed[2] + 48f)
        assertEquals("block centred between sections", above, below, 1f)

        openDrawer()

        assertEquals("rows stable across states", collapsed, rows())
    }

    /** Horizontal centre of the brand symbol's cyan ink, as drawn. */
    private fun symbolCenter(): Float {
        val bitmap = drawShell()
        val brand = bounds("global-drawer-brand")
        val columns = (0..70).filter { x ->
            (brand.top.toInt()..brand.bottom.toInt()).any { y ->
                val pixel = bitmap.getPixel(x, y)
                Color(pixel).blue > 0.35f && Color(pixel).blue > Color(pixel).red + 0.12f
            }
        }
        check(columns.isNotEmpty()) { "no symbol ink in brand $brand of ${bitmap.width}x${bitmap.height}" }
        return (columns.first() + columns.last()) / 2f
    }

    /** Leading ink column of the first destination's label in the expanded drawer. */
    private fun labelInkLeft(): Float {
        val bitmap = drawShell()
        val row = bounds("nav-channels")
        val rows = row.top.toInt()..row.bottom.toInt()
        return (60..260).first { x ->
            rows.any { y -> channelDistance(Color(bitmap.getPixel(x, y)), Color(bitmap.getPixel(x, 400))) > 0.03f }
        }.toFloat()
    }

    private fun assertUntouchedField(state: String) {
        val bitmap = drawShell()
        val end = drawerWidth().toInt() + 128
        val red = (0..end).map { Color(bitmap.getPixel(it, 400)).red }
        assertTrue("$state backing should be translucent", red.first() in 0.03f..0.07f)
        assertTrue("$state backing must have no hard edge", red.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) < 0.02f })
        assertEquals("$state content beyond the fade", Color.Red.toArgb(), bitmap.getPixel(end + 8, 400))
    }

    /** EN/DE at 1.0 and 1.3, drawer closed and open, over no playback and warm playback. */
    private fun captureShell(locale: String, fontScale: Float) {
        val warmLabel = if (File(APPROVED_STILL).isFile) {
            "warm-approved-still"
        } else {
            "warm-synthetic-backdrop"
        }
        settingsShell(fontScale)
        val prefix = "$locale-font$fontScale"

        capture("$prefix-drawer-closed-no-playback", drawerOpen = false)
        compose.runOnIdle { warmPlayback = true }
        compose.waitForIdle()
        capture("$prefix-drawer-closed-$warmLabel", drawerOpen = false)
        openDrawer()
        capture("$prefix-drawer-open-$warmLabel", drawerOpen = true)
        compose.runOnIdle { warmPlayback = false }
        compose.waitForIdle()
        capture("$prefix-drawer-open-no-playback", drawerOpen = true)

        assertEquals(128f + 200f, bounds("depth-active").left, .5f)
    }

    /**
     * Every capture must show the same real state at every font scale: Settings is
     * the selected destination, it owns drawer focus only while the drawer is open,
     * and the shell is at its settled 80/280dp width.
     */
    private fun assertCaptureState(name: String, drawerOpen: Boolean) {
        val item = compose.onNodeWithTag("nav-settings").fetchSemanticsNode().config
        assertEquals("$name selection", true, item.getOrNull(SemanticsProperties.Selected))
        assertEquals("$name drawer focus", drawerOpen, item.getOrNull(SemanticsProperties.Focused))
        assertEquals("$name width", if (drawerOpen) 280f else 80f, drawerWidth(), .5f)
    }

    private var warmPlayback by mutableStateOf(false)

    /** Settings root on the global shell, over the approved warm still when present. */
    private fun settingsShell(fontScale: Float) {
        val still = File(APPROVED_STILL).takeIf { it.isFile }
            ?.let { requireNotNull(BitmapFactory.decodeFile(it.path)).asImageBitmap() }
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.GENERAL.name)
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (warmPlayback) {
                            if (still == null) DebugVideoBackdrop(true, Modifier.fillMaxSize())
                            else Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            WarmPlaybackScrim()
                        }
                        SideRail(
                            currentRoute = AppDestination.SETTINGS,
                            showEpgMenu = true,
                            onRootBack = {},
                            onNavigate = {},
                        ) { padding, drawerActive ->
                            SettingsScreenNavigation(
                                navigation,
                                listOf(settingsRootLevel()) + settingsGeneralLevels(
                                    UiSettings(), AppLanguage.SYSTEM, CacheStatistics.EMPTY,
                                    CacheClearState.IDLE, {}, {}, {},
                                ),
                                initialFocusEnabled = !drawerActive,
                                contentPadding = padding,
                            )
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            navigation.update(DepthStack(listOf(DepthFrame(SETTINGS_ROOT, SettingsSection.GENERAL.name))))
        }
        compose.waitForIdle()
    }

    private fun openDrawer() {
        compose.onNodeWithTag("nav-settings")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
    }

    private fun drawerWidth() = bounds("global-drawer-surface").width

    private fun bounds(tag: String) =
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /**
     * Derived constraint, not a measurement: the preview column clears its own
     * semantics, so its origin is only checked as the measured active column plus
     * the pinned depth tokens. It pins those tokens against the measured column;
     * it cannot observe where the preview is actually placed.
     */
    private fun derivedPreviewLeft(activeColumn: Rect) =
        activeColumn.left + SettingsDepthColumnWidth.value + SettingsDepthColumnGap.value

    private fun drawShell(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun capture(name: String, drawerOpen: Boolean) {
        assertCaptureState(name, drawerOpen)
        val bitmap = drawShell()
        // A second frame of the same state proves no animation was still running.
        compose.waitForIdle()
        assertTrue("$name was captured mid-animation", bitmap.sameAs(drawShell()))
        val directory = File("build/outputs/navigation-shell-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private companion object {
        const val APPROVED_STILL = "../artifacts/settings-c-review/approved-comparison-still.png"
    }
}
