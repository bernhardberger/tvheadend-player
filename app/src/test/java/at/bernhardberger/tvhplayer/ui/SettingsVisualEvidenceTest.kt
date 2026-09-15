package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.depth.*
import at.bernhardberger.tvhplayer.ui.player.DebugVideoBackdrop
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticePresentation
import at.bernhardberger.tvhplayer.ui.screens.*
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.CacheClearState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Matched production-composable evidence. Optional approved still stays outside application resources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsVisualEvidenceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi") fun englishNormal() = captureStates("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi") fun germanNormal() = captureStates("de", 1f)
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi") fun englishEnlarged() = captureStates("en", 1.3f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi") fun germanEnlarged() = captureStates("de", 1.3f)

    private fun captureStates(locale: String, fontScale: Float) {
        var cacheState by mutableStateOf(CacheClearState.IDLE)
        var cacheCalls = 0
        var accessibilityCalls = 0
        lateinit var navigation: DepthNavigationState
        val resources = RuntimeEnvironment.getApplication().resources
        val cacheLabel = resources.getString(at.bernhardberger.tvhplayer.R.string.clear_cache)
        val accessLabel = resources.getString(at.bernhardberger.tvhplayer.R.string.open_accessibility_settings)
        val disclosure = resources.getString(at.bernhardberger.tvhplayer.R.string.appliance_accessibility_disclosure)
        val still = File("../artifacts/settings-c-review/approved-comparison-still.png")
            .takeIf { it.isFile }?.let { requireNotNull(BitmapFactory.decodeFile(it.path)).asImageBitmap() }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        if (still == null) DebugVideoBackdrop(true, Modifier.fillMaxSize())
                        else Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        WarmPlaybackScrim() // This evidence set explicitly models warm playback.
                        SideRail(currentRoute = AppDestination.SETTINGS, showEpgMenu = true,
                            onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                            SettingsScreenNavigation(navigation,
                                listOf(settingsRootLevel(), settingsApplianceLevel(false, false, {}, { accessibilityCalls++ }),
                                    settingsConnectionOverview(resources.getString(at.bernhardberger.tvhplayer.R.string.connection_overview_connected),
                                        "tvheadend.local", 9982)) +
                                    settingsGeneralLevels(UiSettings(), AppLanguage.SYSTEM, CacheStatistics.EMPTY, cacheState,
                                        {}, {}, { cacheCalls++; cacheState = CacheClearState.CLEARING }),
                                initialFocusEnabled = !drawerActive, contentPadding = padding)
                        }
                        if (cacheState == CacheClearState.CLEARED || cacheState == CacheClearState.FAILED) {
                            AppNoticePresentation(androidx.compose.ui.res.stringResource(
                                if (cacheState == CacheClearState.CLEARED) at.bernhardberger.tvhplayer.R.string.cache_notice_cleared
                                else at.bernhardberger.tvhplayer.R.string.cache_notice_failed))
                        }
                    }
                }
            }
        }
        val prefix = "$locale-font$fontScale"
        compose.runOnIdle {
            navigation.update(DepthStack(listOf(DepthFrame(SETTINGS_ROOT, SettingsSection.CONNECTION.name))))
        }
        capture("$prefix-connection-preview")
        press(Key.DirectionCenter)
        compose.onNodeWithText(resources.getString(at.bernhardberger.tvhplayer.R.string.edit_connection)).assertIsFocused()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        capture("$prefix-connection-overview")
        press(Key.Back)
        compose.runOnIdle { navigation.update(DepthStack(listOf(DepthFrame(SETTINGS_ROOT, SettingsSection.GENERAL.name)), visit = navigation.stack.visit + 1)) }
        // Accepted Settings C geometry on the 80dp Material for TV drawer shell.
        val activeColumn = compose.onNodeWithTag("depth-active").fetchSemanticsNode().boundsInRoot
        assertEquals(128f, activeColumn.left, .5f)
        assertEquals(352f, activeColumn.width, .5f)
        capture("$prefix-root")
        press(Key.DirectionCenter)
        capture("$prefix-general")
        press(Key.DirectionRight)
        // Match the accepted board: Follow system is both focused and selected.
        capture("$prefix-language")
        press(Key.Back)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        val row = compose.onNodeWithText(cacheLabel)
        row.assertIsFocused()
        val bounds = row.fetchSemanticsNode().boundsInRoot
        capture("$prefix-cache-idle")
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(1, cacheCalls)
        for (state in listOf(CacheClearState.CLEARING, CacheClearState.FAILED, CacheClearState.CLEARED)) {
            compose.runOnIdle { cacheState = state }
            row.assertIsFocused()
            assertEquals("Cache state must not grow the row", bounds, row.fetchSemanticsNode().boundsInRoot)
            if (state == CacheClearState.CLEARING) {
                row.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            } else {
                val notice = compose.onNodeWithTag("app-notice")
                notice.assertIsDisplayed()
                val noticeBounds = notice.fetchSemanticsNode().boundsInRoot
                assertEquals(480f, noticeBounds.center.x, .5f) // Odd pixel widths round at placement.
                assertEquals(508f, noticeBounds.bottom)
                assertTrue(noticeBounds.width <= 324f)
            }
            capture("$prefix-cache-${state.name.lowercase()}")
            if (state == CacheClearState.FAILED) {
                press(Key.DirectionCenter)
                assertEquals(2, cacheCalls)
            }
        }
        compose.runOnIdle { cacheState = CacheClearState.IDLE }
        press(Key.Back)
        repeat(4) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNodeWithText(accessLabel).assertIsFocused()
        val accessBounds = compose.onNodeWithText(accessLabel).fetchSemanticsNode().boundsInRoot
        val textBounds = compose.onNodeWithTag("appliance-disclosure").fetchSemanticsNode().boundsInRoot
        assertTrue("Disclosure must be outside the focused action", textBounds.top >= accessBounds.bottom)
        compose.onNodeWithText(disclosure).assertExists()
        capture("$prefix-appliance-action")
        assertEquals(0, accessibilityCalls)
        press(Key.DirectionCenter)
        assertEquals(1, accessibilityCalls) // Direct system-settings action, no extra disclosure step.
        press(Key.DirectionDown)
        val reading = compose.onNodeWithTag("appliance-disclosure")
        reading.assertIsFocused()
        assertTrue(reading.fetchSemanticsNode().boundsInRoot.bottom <= view.height - 32)
        capture("$prefix-appliance-reading")
        repeat(8) { press(Key.DirectionDown) }
        reading.assertIsFocused()
        val range = reading.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(range.maxValue(), range.value())
        capture("$prefix-appliance-reading-end")
        repeat(8) {
            if (reading.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f) press(Key.DirectionUp)
        }
        press(Key.DirectionUp)
        compose.onNodeWithText(accessLabel).assertIsFocused()
        // Scrolling the disclosure never invokes its neighboring action.
        assertEquals(1, accessibilityCalls)
        press(Key.Back)
        assertEquals(SETTINGS_ROOT, navigation.stack.active.levelId)
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val directory = File("build/outputs/settings-c-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
