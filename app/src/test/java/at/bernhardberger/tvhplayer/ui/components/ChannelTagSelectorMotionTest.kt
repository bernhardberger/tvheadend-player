package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Hold the parent's selection echo explicitly; exercise the real selector and native pill. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalFoundationApi::class)
class ChannelTagSelectorMotionTest {
    @get:Rule val compose = createComposeRule()
    private val active = mutableStateOf<ChannelTagId?>(ChannelTagId(5))
    private val tags = mutableStateOf((1..9).map {
        ChannelTag.create(ChannelTagId(it.toLong()), name = "Long channel category $it")
    })
    private val allVisible = mutableStateOf(true)
    private val selections = mutableListOf<ChannelTagId?>()
    private val scopeFocus = FocusRequester()
    private val bodyFocus = FocusRequester()
    private lateinit var view: View
    private var pillColor = 0
    private var pivotDistance = 0f
    private var bodyClicks = 0
    private var bodyMoves = 0

    @Test fun overflowingLeftwardPillRetargetsBeforeParentEcho() = pendingEcho(Key.DirectionLeft, 4)

    @Test fun overflowingRightwardPillRetargetsBeforeParentEcho() = pendingEcho(Key.DirectionRight, 6)

    @Test fun interruptedReversalIgnoresAnOlderEchoWhileFocused() {
        show()
        val inset = pillLeft() - tabLeft(5)
        key(Key.DirectionRight)
        advance(96)
        key(Key.DirectionLeft)
        advance(32)
        key(Key.DirectionLeft)
        compose.runOnIdle { active.value = ChannelTagId(6) }
        advance(1000)
        tab(4).assertIsFocused().assertIsSelected()
        assertEquals(inset, pillLeft() - tabLeft(4), 1f)
        assertEquals(listOf(5L, 6L, 5L, 4L), selections.map { it?.value })
        key(Key.DirectionDown)
        advance(1000)
        tab(6).assertIsSelected()
    }

    private fun pendingEcho(direction: Key, target: Int) {
        show()
        val start = pillLeft() - tabLeft(5)
        key(direction)
        assertEquals(ChannelTagId(target.toLong()), selections.last())
        assertEquals("parent echo is deliberately still pending", ChannelTagId(5), active.value)
        advance(96)
        val relative = pillLeft() - tabLeft(5)
        assertTrue("native pill must retarget independently of strip scroll: start=$start now=$relative",
            if (target < 5) relative < start - 20f else relative > start + 20f)
        advance(1000)
        assertEquals("native pill must settle on focused scope before echo", start, pillLeft() - tabLeft(target), 1f)
        tab(target).assertIsFocused().assertIsSelected()
        tab(5).assertIsNotSelected()
        compose.runOnIdle { active.value = ChannelTagId(target.toLong()) }
        advance(64)
        assertEquals("echo must not restart/reposition the native pill", start, pillLeft() - tabLeft(target), 1f)
    }

    @Test fun focusExitUsesAuthoritativeSelectionAndNativeOkDoesNotActivateBody() {
        show()
        key(Key.DirectionLeft)
        advance(1000)
        tab(4).assertIsSelected()
        phase(Key.DirectionCenter, KeyEvent.ACTION_DOWN)
        tab(4).assertIsFocused()
        assertEquals(0, bodyMoves)
        phase(Key.DirectionCenter, KeyEvent.ACTION_UP)
        advance(1000)
        compose.onNodeWithText("Body").assertIsFocused()
        assertEquals(1, bodyMoves)
        assertEquals(0, bodyClicks)
        tab(5).assertIsSelected()
        tab(4).assertIsNotSelected()
        compose.runOnIdle { active.value = ChannelTagId(3) }
        advance(1000)
        tab(3).assertIsSelected()
        compose.runOnIdle { scopeFocus.requestFocus() }
        advance(1000)
        // The existing focusRestorer keeps the last focused scope on re-entry.
        tab(4).assertIsFocused().assertIsSelected()
        key(Key.DirectionDown)
        advance(1000)
        compose.onNodeWithText("Body").assertIsFocused()
        assertEquals(2, bodyMoves)
        assertEquals(0, bodyClicks)
    }

    @Test fun focusedIdentitySurvivesReorderAndAllVisibilityChanges() {
        show()
        key(Key.DirectionLeft)
        advance(1000)
        compose.runOnIdle {
            tags.value = tags.value.reversed()
            allVisible.value = false
        }
        advance(1000)
        tab(4).assertIsFocused().assertIsSelected()
        assertEquals("settings recomposition must not commit another scope",
            listOf(ChannelTagId(5), ChannelTagId(4)), selections)
    }

    @Test fun removingFocusedScopeDropsItsLocalIndication() {
        show()
        key(Key.DirectionLeft)
        advance(1000)
        compose.runOnIdle { tags.value = tags.value.filterNot { it.id == ChannelTagId(4) } }
        advance(1000)
        tab(5).assertIsSelected()
        compose.runOnIdle { scopeFocus.requestFocus() }
        advance(1000)
        tab(5).assertIsFocused().assertIsSelected()
    }

    @Test fun removedRestorationTargetFallsBackToCurrentSelection() {
        show()
        key(Key.DirectionLeft)
        advance(1000)
        key(Key.DirectionDown)
        compose.runOnIdle {
            tags.value = tags.value.filterNot { it.id == ChannelTagId(4) }
            active.value = ChannelTagId(3)
            allVisible.value = false
        }
        advance(1000)
        tab(3).assertIsSelected()
        compose.runOnIdle { scopeFocus.requestFocus() }
        advance(1000)
        tab(3).assertIsFocused().assertIsSelected()
    }

    @Test fun allChannelsIsAFocusedScopeNotTheNoFocusSentinel() {
        active.value = ChannelTagId(1)
        show()
        key(Key.DirectionLeft)
        advance(1000)
        compose.onNodeWithText("All channels").assertIsFocused().assertIsSelected()
        assertEquals(null, selections.last())
        key(Key.DirectionDown)
        advance(1000)
        tab(1).assertIsSelected()
        compose.onNodeWithText("All channels").assertIsNotSelected()
    }

    private fun show() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    pillColor = MaterialTheme.colorScheme.onSurface.toArgb()
                    pivotDistance = LocalBringIntoViewSpec.current.calculateScrollDistance(0f, 100f, 600f)
                    Column(Modifier.size(600.dp, 300.dp).background(Color.Black)) {
                        ChannelTagSelector(
                            tags = tags.value,
                            activeTagId = active.value,
                            onSelectTag = { selections += it },
                            allChannelsVisible = allVisible.value,
                            activeFocusRequester = scopeFocus,
                            onMoveToContent = {
                                bodyMoves++
                                bodyFocus.requestFocus()
                            },
                        )
                        Button(onClick = { bodyClicks++ }, modifier = Modifier.focusRequester(bodyFocus)) {
                            Text("Body")
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { scopeFocus.requestFocus() }
        compose.waitForIdle()
        assertEquals("fixture uses the actual TV pivot", -180f, pivotDistance, .01f)
        tab(requireNotNull(active.value).value.toInt()).assertIsFocused()
        compose.mainClock.autoAdvance = false
    }

    private fun tab(id: Int) = compose.onNodeWithText("Long channel category $id")
    private fun tabLeft(id: Int) = tab(id).fetchSemanticsNode().boundsInRoot.left

    private fun key(key: Key) {
        phase(key, KeyEvent.ACTION_DOWN)
        phase(key, KeyEvent.ACTION_UP)
    }

    private fun phase(key: Key, action: Int) = compose.runOnIdle {
        view.dispatchKeyEvent(KeyEvent(action, key.nativeKeyCode))
    }

    private fun advance(millis: Long) {
        repeat((millis / 16).toInt()) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.waitForIdle()
    }

    // The native pill's top padding contains no glyphs. Relative to a tab this excludes scrolling.
    private fun pillLeft(): Float {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val y = tab(5).fetchSemanticsNode().boundsInRoot.top.toInt() + 4
        val pixels = (0 until 600).filter { bitmap.getPixel(it, y) == pillColor }
        bitmap.recycle()
        assertTrue("native focused pill must be visible", pixels.isNotEmpty())
        return pixels.first().toFloat()
    }
}
