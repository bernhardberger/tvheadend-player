package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.depth.DepthItem
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthAlphaMillis
import at.bernhardberger.tvhplayer.ui.components.depth.DepthSlideMillis
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SETTINGS_ROOT
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settingsRootLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsGeneralLevels
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsRow
import at.bernhardberger.tvhplayer.viewmodels.CacheClearState
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

/**
 * In-flight motion evidence: what the shell actually draws between the settled
 * screens. Settled captures cannot show a column that is a frame behind, a column
 * drawn over the drawer, or the same level drawn twice while it moves, so every
 * assertion here samples the intermediate frames of a real transition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DepthMotionEvidenceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private lateinit var navigation: DepthNavigationState

    /**
     * The library centres its leading box in a slot that steps from 24dp to 32dp the
     * frame the item grows past 64dp. The icon must cancel that step in the same
     * frame: a correction that arrives from a measured value one frame later shows
     * the icon jumping off its axis and back.
     */
    @Test
    fun railIconsKeepTheirAxisInEveryDrawerFrame() {
        shell()
        val axis = iconInk("closed")
        assertEquals("kit icon column", 40f, (axis.first + axis.last) / 2f, 1f)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-channels")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val opening = drawerFrames("drawer-open")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-settings")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        val closing = drawerFrames("drawer-close")

        // The drawer is a live width animation in both directions, not a still.
        assertTrue("opening never animated", opening.any { it.second.second != opening.first().second.second })
        assertTrue("closing never animated", closing.any { it.second.second != closing.first().second.second })
        for ((label, data) in opening + closing) { val ink = data.first
            assertEquals("icon left the kit axis at $label (item width ${data.second})",
                axis, ink)
        }
    }

    @Test
    @Config(qualifiers = "en-ldrtl-w960dp-h540dp-land-mdpi")
    fun railIconsKeepTheirAxisInEveryRtlDrawerFrame() {
        shell(rtl = true)
        val axis = iconInk("closed-rtl", rtl = true)
        val canvas = draw().width
        assertEquals("kit icon column rtl", canvas - 40f, (axis.first + axis.last) / 2f, 1f)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-channels")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val opening = drawerFrames("drawer-open-rtl", rtl = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("General")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val closing = drawerFrames("drawer-close-rtl", rtl = true)

        assertTrue("rtl opening never animated", opening.any { it.second.second != opening.first().second.second })
        assertTrue("rtl closing never animated", closing.any { it.second.second != closing.first().second.second })
        val axisCenter = (axis.first + axis.last) / 2f
        for ((label, data) in opening + closing) { val ink = data.first
            assertEquals("rtl icon left the kit axis at $label (item width ${data.second})",
                axisCenter, (ink.first + ink.last) / 2f, 1f)
        }
    }

    /**
     * The overlap trial lets departing columns remain visible beneath the rail.
     * The protective gradient must dim them, and navigation remains on top.
     */
    @Test
    fun depthMotionPassesBeneathProtectedDrawer() {
        shell()
        assertEquals(128f, bounds("depth-active").left, .5f)
        val reference = draw()
        var sawOverflow = false
        fun checkOverlap(frame: Bitmap, label: String) {
            // Below the header and above the first nav icon: only departing
            // Settings rows can contribute ink here, never a drawer item.
            for (x in 16..70) for (y in 120..170) {
                val pixel = Color(frame.getPixel(x, y))
                if (frame.getPixel(x, y) != reference.getPixel(x, y)) sawOverflow = true
                assertTrue("overflow must be subdued at $label", pixel.red < 0.7f)
            }
            write("drawer-overlap-$label", frame)
        }

        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        depthFrames("push") { frame, label -> checkOverlap(frame, label) }
        settle()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        depthFrames("pop") { frame, label -> checkOverlap(frame, label) }
        settle()

        assertEquals(128f, bounds("depth-active").left, .5f)
        assertTrue("departing content must remain visible beneath the rail", sawOverflow)
    }

    /**
     * Back must move one column, not draw two. The child level is on screen once at
     * every instant: the column leaving the active slot is the one that arrives in
     * the preview slot, at the preview's own dim, so the handoff at the end of the
     * transition changes nothing.
     */
    @Test
    fun backCarriesOneChildColumnIntoThePreviewSlot() {
        shell()
        // Sampling band: the level below the root draws exactly one control here in
        // both settled states, so a second run in flight is a second representation.
        assertEquals("root fixture band", 1, runs(draw(), BAND).size)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        val active = runs(draw(), BAND)
        assertEquals("child fixture band", 1, active.size)

        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        var previous = active.single()
        val travelled = mutableListOf<IntRange>()
        depthFrames("back") { frame, label ->
            val drawn = runs(frame, BAND)
            assertEquals("child level drawn ${drawn.size} times at $label: $drawn", 1, drawn.size)
            val run = drawn.single()
            assertTrue("column moved back to the active slot at $label", run.first >= previous.first)
            previous = run
            travelled += run
        }
        val handoff = draw()
        settle()
        val settled = runs(draw(), BAND).single()

        // The column that carried the level is exactly where the preview now is, and
        // no brighter: two stacked 0.6 layers would not survive this comparison.
        assertEquals("landing position", settled.first.toFloat(), previous.first.toFloat(), 2f)
        assertEquals("landing dim", peak(draw(), settled), peak(handoff, previous), .04f)
        assertTrue("the column crossed the gap", travelled.first().first < settled.first - 300)
    }

    /**
     * First Back after entering the second Settings level: the preview slot must
     * keep exactly one production column for the whole slide and the 100ms after
     * handoff. Semantics on the preview are cleared, so this samples pixels of
     * real General / App language rows, not a synthetic band.
     */
    @Test
    fun firstBackAfterSecondLevelKeepsOneProductionPreview() {
        shell()
        assertTrue("settled root has a preview", slotHasInk(draw(), PREVIEW_LEFT, PREVIEW_RIGHT))
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        val child = draw()
        assertTrue("settled child has a preview", slotHasInk(child, PREVIEW_LEFT, PREVIEW_RIGHT))
        write("first-back-settled-child", child)

        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        samplePreviewHandoff("first-back", requireOccupied = true)
        settle()
        assertTrue("landed root has a preview", slotHasInk(draw(), PREVIEW_LEFT, PREVIEW_RIGHT))
        compose.onNodeWithText("General").assertIsFocused()
    }

    /**
     * Deeper first-Back (General → language, then Back) and a second root→General
     * Back must not empty or double the preview either.
     */
    @Test
    fun firstBackFromDeeperSettingsKeepsOneProductionPreview() {
        shell()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithText("Follow system").assertIsFocused()

        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        samplePreviewHandoff("deeper-back", requireOccupied = false)
        settle()
        compose.onNodeWithText("App language").assertIsFocused()
        assertTrue("general preview after leaving language", slotHasInk(draw(), PREVIEW_LEFT, PREVIEW_RIGHT))

        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        samplePreviewHandoff("deeper-back-to-root", requireOccupied = true)
        settle()
        compose.onNodeWithText("General").assertIsFocused()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        samplePreviewHandoff("second-root-back", requireOccupied = true)
        settle()
        compose.onNodeWithText("General").assertIsFocused()
    }

    /**
     * Changing sibling during push/pop must replace the obsolete same-slot column.
     * Two 0.6 layers peak around 0.84, so occupancy of unique row ink is the check.
     */
    @Test
    fun siblingSwitchDuringPushAndPopKeepsOneColumnPerSlot() {
        twoSiblings()
        compose.onNodeWithText("Sibling One").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.waitForIdle()
        focusRow("Sibling Two")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.waitForIdle()
        val rootItems = listOf(DepthItem("one", "one"), DepthItem("two", "two"))

        fun enterSibling(id: String) {
            compose.runOnIdle {
                val focused = navigation.stack.focus(id, rootItems)
                navigation.update(focused.enter(rootItems.first { it.id == id }, rootItems))
            }
        }
        compose.mainClock.autoAdvance = false
        enterSibling("one")
        compose.mainClock.advanceTimeBy(100)
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.mainClock.advanceTimeBy(50)
        enterSibling("two")
        sampleSiblingSlot("sibling-push-pop")

        settle()
        assertEquals("two", navigation.stack.active.levelId)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { navigation.update(navigation.stack.focus("one", rootItems)) }
        sampleSiblingSlot("sibling-pop")
        enterSibling("one")
        sampleSiblingSlot("sibling-reverse")
        settle()
        assertEquals("one", navigation.stack.active.levelId)
    }

    /**
     * Settled Up/Down sibling preview must crossfade, not hard-cut. The two previews use
     * distinct vertical bands (red top for One, blue bottom for Two) so overlap is visible
     * as ink in both bands: preview semantics are cleared, so pixel occupancy is the oracle.
     * Old hard-cut code shows only the incoming band immediately and fails the mid-fade check.
     * Duration is framework config_longAnimTime (AOSP preview replace), separate from the
     * 1000ms depth slide and 200ms emphasis.
     */
    @Test
    fun siblingPreviewCrossfadesInsteadOfCutting() {
        twoBandedSiblings()
        compose.onNodeWithText("Sibling One").assertIsFocused()
        var left = previewLeft()
        var settledOne = draw()
        write("sibling-fade-settled-one", settledOne)
        assertTrue("settled One preview top band must have ink", previewTopHasInk(settledOne, left))
        assertFalse("settled One must not ghost Two bottom", previewBottomHasInk(settledOne, left))

        compose.mainClock.autoAdvance = false
        focusRow("Sibling Two")
        val timeline = StringBuilder()
        timeline.append("focus=${navigation.stack.active.focusedItemId} ")
        for (t in listOf(0L, 50L, 100L, 250L, 400L)) {
            if (t > 0) compose.mainClock.advanceTimeBy(if (t == 50L) 50L else if (t == 100L) 50L else if (t == 250L) 150L else 150L)
            val frame = draw()
            val l = previewLeft()
            val top = previewTopHasInk(frame, l)
            val bottom = previewBottomHasInk(frame, l)
            timeline.append("t$t:top=$top,bottom=$bottom,focus=${navigation.stack.active.focusedItemId} ")
            write("sibling-fade-timeline-t$t", frame)
        }
        // Intermediate overlap (both bands ink) is the fade evidence: hard-cut old code shows only
        // the incoming band immediately (top=false at t50+), failing here. Duration is framework
        // config_longAnimTime (500ms), separate from depth slide/emphasis.
        val timelineString = timeline.toString()
        assertTrue("sibling preview must overlap top+bottom at t50 (fade, not cut): $timelineString",
            timelineString.contains("t50:top=true,bottom=true"))
        assertTrue("sibling preview must overlap top+bottom at t100 (fade, not cut): $timelineString",
            timelineString.contains("t100:top=true,bottom=true"))
        assertTrue("sibling preview must overlap top+bottom at t250 (fade, not cut): $timelineString",
            timelineString.contains("t250:top=true,bottom=true"))

        compose.mainClock.advanceTimeBy(150)
        var mid = draw()
        left = previewLeft()
        write("sibling-fade-mid-550", mid)
        assertFalse("fade complete 550ms must not ghost outgoing top", previewTopHasInk(mid, left))
        assertTrue("fade complete 550ms must show incoming bottom", previewBottomHasInk(mid, left))

        settle()
        compose.onNodeWithText("Sibling Two").assertIsFocused()
        left = previewLeft()
        val settledTwo = draw()
        write("sibling-fade-settled-two", settledTwo)
        assertFalse("settled Two must not ghost One top", previewTopHasInk(settledTwo, left))
        assertTrue("settled Two preview bottom band must have ink", previewBottomHasInk(settledTwo, left))
    }

    /**
     * Rapid Up/Down sibling changes must converge to the latest preview without ghosts.
     * AnimatedContent drops intermediates: after settling only the latest band has ink.
     */
    @Test
    fun rapidSiblingPreviewChangesConvergeWithoutGhosts() {
        twoBandedSiblings()
        compose.onNodeWithText("Sibling One").assertIsFocused()

        compose.mainClock.autoAdvance = false
        focusRow("Sibling Two")
        compose.mainClock.advanceTimeBy(80)
        focusRow("Sibling One")
        compose.mainClock.advanceTimeBy(80)
        focusRow("Sibling Two")
        compose.mainClock.advanceTimeBy(80)
        val mid = draw()
        write("sibling-rapid-mid", mid)
        // Mid-rapid may overlap (fade), but must never show the same level twice in one slot:
        // top and bottom are different siblings at the same preview slot, which is the only
        // intentional overlap. No assertion on single here; convergence is the oracle below.

        settle()
        compose.onNodeWithText("Sibling Two").assertIsFocused()
        val left = previewLeft()
        val landed = draw()
        write("sibling-rapid-settled-two", landed)
        assertFalse("rapid must not ghost One top after settling to Two", previewTopHasInk(landed, left))
        assertTrue("rapid must converge to Two bottom", previewBottomHasInk(landed, left))

        compose.mainClock.autoAdvance = false
        focusRow("Sibling One")
        compose.mainClock.advanceTimeBy(50)
        focusRow("Sibling Two")
        compose.mainClock.advanceTimeBy(50)
        focusRow("Sibling One")
        settle()
        compose.onNodeWithText("Sibling One").assertIsFocused()
        val backLeft = previewLeft()
        val back = draw()
        write("sibling-rapid-settled-one", back)
        assertTrue("rapid must converge back to One top", previewTopHasInk(back, backLeft))
        assertFalse("rapid must not ghost Two bottom after settling to One", previewBottomHasInk(back, backLeft))
    }

    /**
     * Enter mid-fade must capture the latest focus item (Two) even though the fade has not
     * completed, and Back must restore a single preview column with no ghost of One.
     * Focus stays left (preview read-only, no editor), stale outgoing cannot act.
     */
    @Test
    fun enterMidPreviewFadeThenBackKeepsSingleColumn() {
        twoBandedSiblings()
        compose.onNodeWithText("Sibling One").assertIsFocused()

        compose.mainClock.autoAdvance = false
        focusRow("Sibling Two")
        compose.mainClock.advanceTimeBy(100)
        var mid = draw()
        var left = previewLeft()
        write("enter-midfade-preview-mid", mid)
        assertTrue("mid-fade must overlap top before Enter", previewTopHasInk(mid, left))
        assertTrue("mid-fade must overlap bottom before Enter", previewBottomHasInk(mid, left))

        // Enter mid-fade: latest focus is Two, so the push must target Two, not the fading One.
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        // Sample the 1000ms push slide: the same level must never appear twice (no duplicate
        // blue run at the bottom band), and the obsolete sibling (red top) must stay gone.
        var pushElapsed = 0L
        while (pushElapsed <= DepthSlideMillis) {
            val frame = draw()
            write("enter-midfade-push-t$pushElapsed", frame)
            val bottomRuns = runs(frame, PREVIEW_BOTTOM_Y)
            assertTrue("duplicate preview/active blue at push t=$pushElapsed: $bottomRuns", bottomRuns.size <= 1)
            // Red top ghost would appear as ink in the top band at the active columns area;
            // the push target is Two (spacer top, no ink), so top ink at the preview slot area
            // must stay absent. Use the settled preview slot for the ghost check after settle below;
            // in flight only assert no duplicate bottom run (same level twice).
            compose.mainClock.advanceTimeBy(100)
            pushElapsed += 100
        }
        settle()
        assertEquals("enter mid-fade must capture latest Two", "two", navigation.stack.active.levelId)
        // Active Two shows spacer top (no ink) + blue bottom at the active slot; preview is empty
        // (Two has no child), so the preview slot must have no ghost top ink.
        left = previewLeft()
        val pushed = draw()
        write("enter-midfade-pushed", pushed)
        assertFalse("pushed must not ghost One top at preview slot", previewTopHasInk(pushed, left))

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.pop() }
        val diagAfterBack = navigation.stack.frames.map { it.levelId to it.focusedItemId }
        assertEquals("Back must pop immediately, got frames=$diagAfterBack", 1, navigation.stack.frames.size)
        var popElapsed = 0L
        while (popElapsed <= DepthSlideMillis) {
            val frame = draw()
            write("enter-midfade-pop-t$popElapsed", frame)
            val bottomRuns = runs(frame, PREVIEW_BOTTOM_Y)
            assertTrue("duplicate blue at pop t=$popElapsed: $bottomRuns", bottomRuns.size <= 1)
            compose.mainClock.advanceTimeBy(100)
            popElapsed += 100
        }
        settle()
        // Focus target (not focus feel: production firstBack tests prove feel) must restore to Two,
        // with a single preview column and no ghost of One.
        val diagFrames = navigation.stack.frames.map { it.levelId to it.focusedItemId }
        assertEquals("after Back must pop to root, got frames=$diagFrames active=${navigation.stack.active.levelId}", 1, navigation.stack.frames.size)
        assertEquals("two", navigation.stack.active.focusedItemId)
        left = previewLeft()
        val landed = draw()
        write("enter-midfade-landed", landed)
        assertFalse("landed must not ghost One top", previewTopHasInk(landed, left))
        assertTrue("landed preview Two bottom must be single", previewBottomHasInk(landed, left))
    }

    /**
     * Visit is not visual identity. Enter then Back before the fade (200ms) or
     * before the slide settles (1000ms) must resume the still-running root, not
     * start a second copy from the preview slot.
     */
    @Test
    fun interruptingPushWithPopReusesTheReturningRoot() {
        shell()
        for (delay in listOf(DepthAlphaMillis / 2L, DepthAlphaMillis + 200L)) {
            compose.mainClock.autoAdvance = false
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.mainClock.advanceTimeBy(delay)
            compose.onRoot().performKeyInput { pressKey(Key.Back) }
            var elapsed = delay
            while (elapsed <= DepthSlideMillis) {
                val frame = draw()
                write("push-pop-${delay}-t$elapsed", frame)
                val childInk = runs(frame, BAND)
                assertTrue("duplicate child pixels at t=$elapsed after push-pop $delay: $childInk", childInk.size <= 1)
                assertEquals("duplicate root at t=$elapsed after push-pop $delay", 1, copies("Channel groups"))
                assertTrue("child duplicated at t=$elapsed after push-pop $delay", copies("App language") <= 1)
                val left = textLeft("Channel groups")
                assertTrue("root restarted from full offset ($left) at t=$elapsed after push-pop $delay", left < 250f)
                compose.mainClock.advanceTimeBy(50)
                elapsed += 50
            }
            settle()
            assertEquals(1, navigation.stack.frames.size)
            compose.onNodeWithText("General").assertIsFocused()
        }
    }

    /**
     * The matching reverse: Back then Enter must resume the still-running child
     * instead of drawing a second copy entering from full offset.
     */
    @Test
    fun interruptingPopWithPushReusesTheReturningChild() {
        shell()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(1, copies("App language"))
        for (delay in listOf(DepthAlphaMillis / 2L, DepthAlphaMillis + 200L)) {
            compose.mainClock.autoAdvance = false
            compose.onRoot().performKeyInput { pressKey(Key.Back) }
            compose.mainClock.advanceTimeBy(delay)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            var elapsed = delay
            while (elapsed <= DepthSlideMillis) {
                val frame = draw()
                write("pop-push-${delay}-t$elapsed", frame)
                val childInk = runs(frame, BAND)
                assertEquals("duplicate child pixels at t=$elapsed after pop-push $delay: $childInk", 1, childInk.size)
                assertTrue("duplicate child at t=$elapsed after pop-push $delay", copies("App language") <= 1)
                assertTrue("root duplicated at t=$elapsed after pop-push $delay", copies("Channel groups") <= 1)
                val left = childInk.first().first.toFloat()
                assertTrue(
                    "child split or entered from a second offset ($left) at t=$elapsed after pop-push $delay",
                    left < PREVIEW_RIGHT,
                )
                compose.mainClock.advanceTimeBy(50)
                elapsed += 50
            }
            settle()
            assertEquals(2, navigation.stack.frames.size)
            compose.onNodeWithText("App language").assertIsFocused()
        }
    }

    private fun dummyWidthAt(frames: List<Pair<String, Pair<IntRange, Float>>>, label: String) =
        frames.first { it.first == label }.second

    /** One entry per animation frame: label and the icon's drawn ink span. */
    private fun drawerFrames(name: String, rtl: Boolean = false): List<Pair<String, Pair<IntRange, Float>>> =
        (0 until DRAWER_FRAMES).map { index ->
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            capture("$name-f$index")
            val ink = iconInk("$name-f$index", rtl)
            val w = bounds("nav-channels").width
            "$name-f$index" to (ink to w)
        }.also {
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }

    private fun copies(text: String) =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun focusRow(label: String) {
        compose.onNode(
            hasClickAction() and hasAnyDescendant(hasText(label)),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    }

    private fun sampleSiblingSlot(name: String) {
        var elapsed = 0L
        while (elapsed <= DepthSlideMillis) {
            val frame = draw()
            write("$name-t$elapsed", frame)
            val red = colorXs(frame, isRed = true)
            val blue = colorXs(frame, isRed = false)
            val overlap = if (red.isEmpty() || blue.isEmpty()) false else {
                val a = red.min()..red.max()
                val b = blue.min()..blue.max()
                minOf(a.last, b.last) - maxOf(a.first, b.first) > 80
            }
            assertFalse(
                "$name t=$elapsed both siblings in one slot red=${red.minOrNull()}..${red.maxOrNull()} blue=${blue.minOrNull()}..${blue.maxOrNull()}",
                overlap,
            )
            compose.mainClock.advanceTimeBy(50)
            elapsed += 50
        }
    }

    private fun colorXs(bitmap: Bitmap, isRed: Boolean): List<Int> {
        val xs = mutableListOf<Int>()
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = Color(bitmap.getPixel(x, y))
                val match = if (isRed) pixel.red > 0.8f && pixel.green < 0.25f && pixel.blue < 0.25f
                else pixel.blue > 0.8f && pixel.red < 0.25f && pixel.green < 0.25f
                if (match) xs += x
            }
        }
        return xs.distinct()
    }

    private fun twoSiblings() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    navigation = rememberDepthNavigationState("root")
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        SettingsScreenNavigation(
                            navigation,
                            listOf(
                                settingsLevel("root", "Root", listOf(
                                    settingsRow("one", "Sibling One", child = "one"),
                                    settingsRow("two", "Sibling Two", child = "two"),
                                )),
                                settingsLevel("one", "One", listOf(
                                    DepthRow(DepthItem("oa")) { modifier, _ ->
                                        Box(modifier.fillMaxWidth().height(48.dp).background(Color.Red))
                                    },
                                )),
                                settingsLevel("two", "Two", listOf(
                                    DepthRow(DepthItem("ob")) { modifier, _ ->
                                        Box(modifier.fillMaxWidth().height(48.dp).background(Color.Blue))
                                    },
                                )),
                            ),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * Sibling previews on distinct vertical bands: One shows red at the top band only,
     * Two shows blue at the bottom band only (transparent spacers elsewhere). During a
     * crossfade both bands have ink at the same preview x-slot; a hard cut shows only the
     * incoming band. Preview semantics are cleared, so ink presence per band is the oracle.
     */
    private fun twoBandedSiblings() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    navigation = rememberDepthNavigationState("root")
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        SettingsScreenNavigation(
                            navigation,
                            listOf(
                                settingsLevel("root", "Root", listOf(
                                    settingsRow("one", "Sibling One", child = "one"),
                                    settingsRow("two", "Sibling Two", child = "two"),
                                )),
                                settingsLevel("one", "One", listOf(
                                    DepthRow(DepthItem("oa-red")) { modifier, _ ->
                                        Box(modifier.fillMaxWidth().height(48.dp).background(Color.Red))
                                    },
                                    DepthRow(DepthItem("oa-spacer")) { modifier, _ ->
                                        Box(modifier.fillMaxWidth().height(200.dp))
                                    },
                                )),
                                settingsLevel("two", "Two", listOf(
                                    DepthRow(DepthItem("ob-spacer")) { modifier, _ ->
                                        Box(modifier.fillMaxWidth().height(200.dp))
                                    },
                                    DepthRow(DepthItem("ob-blue")) { modifier, _ ->
                                        Box(modifier.fillMaxWidth().height(48.dp).background(Color.Blue))
                                    },
                                )),
                            ),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun previewLeft(): Int =
        (compose.onNodeWithTag("depth-active").fetchSemanticsNode().boundsInRoot.left + 460f).toInt()

    private fun bandHasInk(bitmap: Bitmap, left: Int, top: Int, bottom: Int): Boolean {
        val right = minOf(left + 352, bitmap.width)
        val start = maxOf(left, 0)
        for (y in top until bottom step 3) {
            if (y < 0 || y >= bitmap.height) continue
            val bg = bitmap.getPixel(bitmap.width - 4, y)
            for (x in start until right step 3) {
                if (bitmap.getPixel(x, y) != bg) return true
            }
        }
        return false
    }

    private fun previewTopHasInk(bitmap: Bitmap, left: Int) =
        bandHasInk(bitmap, left, PREVIEW_TOP, PREVIEW_TOP_END)

    private fun previewBottomHasInk(bitmap: Bitmap, left: Int) =
        bandHasInk(bitmap, left, PREVIEW_BOTTOM, PREVIEW_BOTTOM_END)

    private fun textLeft(text: String): Float =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().minOf { it.boundsInRoot.left }

    /**
     * Every 16ms through the 1000ms slide and 100ms past handoff. Preview
     * occupancy uses pixels (preview semantics are cleared). A gap in occupancy
     * is the blink-out; a late reappearance after a gap is the blink-in.
     */
    private fun samplePreviewHandoff(name: String, requireOccupied: Boolean) {
        val occupancy = mutableListOf<Pair<Long, Boolean>>()
        var elapsed = 0L
        var peak = 0f
        while (elapsed <= DepthSlideMillis + 100L) {
            val frame = draw()
            write("$name-t$elapsed", frame)
            val occupied = slotHasInk(frame, PREVIEW_LEFT, PREVIEW_RIGHT)
            occupancy += elapsed to occupied
            peak = maxOf(peak, slotPeak(frame, PREVIEW_LEFT, PREVIEW_RIGHT))
            if (requireOccupied) {
                assertTrue("$name t=$elapsed preview slot empty (blink-out)", occupied)
            }
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            elapsed += FRAME_MILLIS
        }
        val timeline = occupancy.joinToString { (t, on) -> if (on) "${t}y" else "${t}n" }
        val blinked = occupancy.zipWithNext().any { (a, b) -> a.second && !b.second } &&
            occupancy.zipWithNext().any { (a, b) -> !a.second && b.second }
        assertTrue("$name preview blinked out then in: $timeline", !blinked)
        assertTrue("$name preview peak $peak looks like stacked copies", peak < 0.95f)
    }

    private fun slotHasInk(bitmap: Bitmap, left: Int, right: Int): Boolean {
        val bg = bitmap.getPixel(bitmap.width - 4, 300)
        val last = minOf(right, bitmap.width)
        for (y in SLOT_TOP until SLOT_BOTTOM step 4) {
            for (x in left until last step 3) {
                if (bitmap.getPixel(x, y) != bg) return true
            }
        }
        return false
    }

    private fun slotPeak(bitmap: Bitmap, left: Int, right: Int): Float {
        var peak = 0f
        val last = minOf(right, bitmap.width)
        for (y in SLOT_TOP until SLOT_BOTTOM step 4) {
            for (x in left until last step 3) {
                val pixel = Color(bitmap.getPixel(x, y))
                peak = maxOf(peak, pixel.red, pixel.green, pixel.blue)
            }
        }
        return peak
    }

    private fun depthFrames(name: String, check: (Bitmap, String) -> Unit) {
        var elapsed = 0L
        while (elapsed <= DepthSlideMillis) {
            val frame = draw()
            write("$name-t$elapsed", frame)
            check(frame, "$name t=$elapsed")
            val step = if (elapsed < 100L) FRAME_MILLIS else 50L
            compose.mainClock.advanceTimeBy(step)
            elapsed += step
        }
    }

    private fun settle() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /** Horizontal ink spans on one row of the canvas, merged across letter gaps. */
    private fun runs(bitmap: Bitmap, y: Int): List<IntRange> {
        val background = bitmap.getPixel(bitmap.width - 4, y)
        val ink = (RAIL_WIDTH until bitmap.width).filter { bitmap.getPixel(it, y) != background }
        val out = mutableListOf<IntRange>()
        var start = ink.firstOrNull() ?: return out
        var previous = start
        for (x in ink.drop(1)) {
            if (x > previous + 8) {
                out.add(start..previous)
                start = x
            }
            previous = x
        }
        out.add(start..previous)
        return out
    }

    /** Strongest drawn value in a span; the dim of a column shows up here. */
    private fun peak(bitmap: Bitmap, span: IntRange): Float =
        span.maxOf { x ->
            val pixel = Color(bitmap.getPixel(x, BAND))
            maxOf(pixel.red, pixel.green, pixel.blue)
        }

    private fun iconInk(label: String, rtl: Boolean = false): IntRange {
        val bitmap = draw()
        val row = bounds("nav-channels")
        val rows = row.top.toInt()..row.bottom.toInt()
        val background = bitmap.getPixel(if (rtl) bitmap.width - 3 else 2, 300)
        val xs = if (rtl) (bitmap.width - 65) until (bitmap.width - 12) else 12..64
        val columns = xs.filter { x ->
            rows.any { y -> bitmap.getPixel(x, y) != background }
        }
        check(columns.isNotEmpty()) { "no rail icon ink at $label" }
        return columns.first()..columns.last()
    }

    private fun bounds(tag: String) =
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun draw(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun capture(name: String) = write(name, draw())

    private fun write(name: String, bitmap: Bitmap) {
        val directory = File("build/outputs/motion-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    /** The production shell over a flat field, so ink is only what the shell drew. */
    private fun shell(rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.GENERAL.name)
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        SideRail(
                            currentRoute = AppDestination.SETTINGS,
                            showEpgMenu = true,
                            onRootBack = {},
                            onNavigate = {},
                         ) { padding, drawerActive ->
                            NavDisplay(
                                backStack = listOf<AppNavKey>(SettingsKey(SettingsSection.GENERAL)),
                                onBack = {},
                                sceneStrategies = listOf(rememberSidebarGuideSceneStrategy(
                                    drawerActive, SettingsKey(SettingsSection.GENERAL),
                                )),
                                transitionSpec = { appDestinationContentTransform() },
                                entryProvider = entryProvider {
                                    entry<SettingsKey>(metadata = mapOf(
                                        SIDEBAR_SCENE_DESTINATION to AppDestination.SETTINGS,
                                    )) {
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
                                },
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

    private companion object {
        const val RAIL_WIDTH = 80
        const val FRAME_MILLIS = 16L
        const val DRAWER_FRAMES = 34
        /**
     * Sampling scanline. It must cross a control that *translates* between the two
     * settled states — a static element would make the assertion vacuous — and it
     * must never cross text: glyphs break into several pixel runs, which reads as a
     * duplicate representation and says nothing about the transition. 240 used to
     * qualify, then landed on the General group subheader once that label took its
     * 12sp kit styling and split into four runs. Re-derive with a scan of every
     * scanline holding exactly one run in both settled states if layout shifts again.
     */
    const val BAND = 300
        const val ACTIVE_LEFT = 128
        const val ACTIVE_RIGHT = 128 + 352
        const val PREVIEW_LEFT = 588
        const val PREVIEW_RIGHT = 588 + 352
        const val SLOT_TOP = 70
        const val SLOT_BOTTOM = 500
        // Distinct vertical bands for the banded sibling previews: One reds the top band,
        // Two blues the bottom band. Bottom Y is also the single-run oracle for push/pop.
        const val PREVIEW_TOP = 120
        const val PREVIEW_TOP_END = 220
        const val PREVIEW_BOTTOM = 320
        const val PREVIEW_BOTTOM_END = 420
        const val PREVIEW_BOTTOM_Y = 360
    }
}
