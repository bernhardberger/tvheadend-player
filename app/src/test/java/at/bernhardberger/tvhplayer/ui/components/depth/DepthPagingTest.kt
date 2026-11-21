package at.bernhardberger.tvhplayer.ui.components.depth

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.core.AutomaticRecordingList
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.DvrScheduleSection
import at.bernhardberger.tvhplayer.core.DvrScheduleSectionKind
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.ui.screens.automaticRecordingSavedEntries
import at.bernhardberger.tvhplayer.ui.screens.recordings.SCHEDULE_ROOT_LEVEL
import at.bernhardberger.tvhplayer.ui.screens.recordings.ScheduleDepthContent
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A column given a page-key mapping pages its rows with CH+/CH-, clamped at both ends, as Settings does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class DepthPagingTest {
    @get:Rule val compose = createComposeRule()

    private val rowIds = (1..40).map { "row-$it" }
    private val navigation = DepthNavigationState(DepthStack(listOf(DepthFrame("root", "row-1"))))

    private fun show(paging: Boolean) {
        val level = DepthLevel("root", rowIds.map { id ->
            DepthRow(DepthItem(id)) { modifier, _ -> Box(modifier.width(300.dp).height(48.dp).focusable()) }
        }, heading = {}, initialItemId = "row-1")
        val levels = mapOf("root" to level)
        compose.setContent {
            DepthNavigation(navigation, levels, { level }, columnWidth = 340.dp, columnGap = 92.dp,
                contentPadding = PaddingValues(), modifier = Modifier.fillMaxSize(),
                pageKeyDirection = if (paging) ChannelNavigation::pageDirectionForKeyCode else null)
        }
        compose.waitForIdle()
    }

    private fun focusedIndex() = compose.runOnIdle { rowIds.indexOf(navigation.stack.active.focusedItemId) }

    private fun press(code: Int) {
        compose.onRoot().performKeyInput { pressKey(Key(code)) }
        compose.waitForIdle()
    }

    @Test fun channelKeysPageTheRowsAndClamp() {
        show(paging = true)
        assertEquals(0, focusedIndex())
        press(android.view.KeyEvent.KEYCODE_CHANNEL_UP)
        assertEquals("clamped at the top", 0, focusedIndex())
        press(android.view.KeyEvent.KEYCODE_CHANNEL_DOWN)
        val page = focusedIndex()
        assertTrue("CH- moved a page ($page)", page >= 2)
        press(android.view.KeyEvent.KEYCODE_CHANNEL_UP)
        assertEquals(0, focusedIndex())
        repeat(30) { press(android.view.KeyEvent.KEYCODE_CHANNEL_DOWN) }
        assertEquals("clamped at the end", rowIds.lastIndex, focusedIndex())
    }

    @Test fun withoutAMappingChannelKeysDoNothing() {
        show(paging = false)
        press(android.view.KeyEvent.KEYCODE_CHANNEL_DOWN)
        assertEquals(0, focusedIndex())
    }

    private val pageDown = Key(android.view.KeyEvent.KEYCODE_CHANNEL_DOWN)
    private val scheduleEntries = (1L..30).map { id ->
        val fixture = automaticRecordingSavedEntries.first()
        DvrEntry.create(
            DvrEntryId(id), title = "Recording $id", channelId = fixture.channelId,
            start = fixture.start, stop = fixture.stop,
            state = if (id == 1L) DvrEntryState.RECORDING else DvrEntryState.SCHEDULED,
        )
    }
    private val scheduleRows = mutableStateOf(scheduleEntries)
    private val scheduleVisit = mutableIntStateOf(0)
    private val scheduleActive = mutableStateOf(true)

    private fun showSchedule(height: Dp = 600.dp) {
        compose.setContent {
            TVHeadendPlayerTheme {
                key(scheduleVisit.intValue) {
                    val navigation = remember { DepthNavigationState(DepthStack(listOf(DepthFrame(SCHEDULE_ROOT_LEVEL)))) }
                    val groups = listOf(
                        DvrScheduleSection(DvrScheduleSectionKind.RECORDING_NOW, null, scheduleRows.value.take(1)),
                        DvrScheduleSection(DvrScheduleSectionKind.TODAY, null, scheduleRows.value.drop(1)),
                    )
                    ScheduleDepthContent(
                        groups = groups, automatic = AutomaticRecordingList(emptyList(), available = true),
                        canModify = true, canAdd = true, onNewTimer = {}, channels = emptyMap(),
                        onNewAutomatic = {}, onEditRule = {}, onRuleMenu = {}, navigation = navigation,
                        contentPadding = PaddingValues(), isCurrent = scheduleActive.value, initialFocusEnabled = true,
                        backEnabled = true, contentFocus = remember { FocusRequester() }, onDrawerFocus = null,
                        onFirstRowUp = {}, onOpenRecording = {}, onOpenContextMenu = {}, piconForEntry = { null },
                        admissionForEntry = { null }, ruleForEntry = { null },
                        modifier = Modifier.height(height),
                    )
                }
            }
        }
        assertEquals(1L, focusedRecording())
    }

    private fun focusedRecording() = compose.onNode(isFocused()).fetchSemanticsNode()
        .config[SemanticsProperties.TestTag].removePrefix("recording-list-entry-").toLong()

    private fun wholeRecordingIds(expectClippedRow: Boolean = false): List<Long> {
        val list = compose.onNodeWithTag("recordings-schedule-list").fetchSemanticsNode().boundsInRoot
        val rows = compose.onAllNodes(SemanticsMatcher("recording row") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("recording-list-entry-")
        } and hasAnyAncestor(hasTestTag("depth-active"))).fetchSemanticsNodes()
        val whole = rows.filter { it.boundsInRoot.height == it.size.height.toFloat() &&
            it.boundsInRoot.top >= list.top && it.boundsInRoot.bottom <= list.bottom }
        if (expectClippedRow) assertTrue("fixture needs a clipped trailing recording", whole.last() != rows.last())
        return whole.map { it.config[SemanticsProperties.TestTag].removePrefix("recording-list-entry-").toLong() }
    }

    @Test
    @Config(qualifiers = "en-w960dp-h720dp-land-mdpi")
    fun schedulePageOverlapsTheLastWholeRecordingInsteadOfCountingClippedRows() {
        showSchedule()
        val expected = wholeRecordingIds(expectClippedRow = true).last()
        assertTrue("fixture needs multiple whole recordings", expected > 1)
        compose.pressKey(pageDown)
        assertEquals("PageDown should overlap the last whole recording", expected, focusedRecording())
    }

    @Test
    @Config(qualifiers = "en-w960dp-h800dp-land-mdpi")
    fun schedulePageKeepsMeasuredRowsAfterReactivation() {
        showSchedule(height = 760.dp)
        val expected = wholeRecordingIds().last()
        assertTrue("fixture needs more than a single-row step", expected > 2)
        compose.runOnIdle { scheduleActive.value = false }
        compose.waitForIdle()
        compose.runOnIdle { scheduleActive.value = true }
        assertEquals(1L, focusedRecording())
        compose.pressKey(pageDown)
        assertEquals("Reactivation should preserve the measured page size", expected, focusedRecording())
    }

    @Test
    @Config(qualifiers = "en-w960dp-h720dp-land-mdpi")
    fun schedulePageKeepsTheDestinationWhenTheFocusedRecordingDisappears() =
        schedulePagesAfterRemoval(pendingSurvives = true)

    @Test
    fun leavingForTheHeadingAfterDeletingTheFocusedRowCancelsPaging() {
        val rows = mutableStateOf(rowIds)
        val heading = FocusRequester()
        compose.setContent {
            val level = DepthLevel("root", rows.value.map { id ->
                DepthRow(DepthItem(id)) { modifier, _ ->
                    Box(modifier.width(300.dp).height(48.dp).testTag(id).focusable())
                }
            }, heading = {
                Box(Modifier.width(300.dp).height(48.dp).focusRequester(heading).testTag("heading").focusable())
            }, keepPageOnRowChanges = true)
            DepthNavigation(navigation, mapOf("root" to level), { level }, 340.dp, 92.dp, PaddingValues(),
                pageKeyDirection = ChannelNavigation::pageDirectionForKeyCode)
        }
        compose.onNodeWithTag("row-1").assertIsFocused()
        compose.mainClock.autoAdvance = false
        try {
            compose.pressKey(pageDown)
            compose.runOnIdle { rows.value = rowIds.drop(1) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("row-1").assertDoesNotExist()
            compose.runOnIdle { heading.requestFocus() }
            compose.onNodeWithTag("heading").assertIsFocused()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithTag("heading").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "en-w960dp-h720dp-land-mdpi")
    fun schedulePagesAgainWhenThePendingRecordingDisappears() =
        schedulePagesAfterRemoval(pendingSurvives = false)

    private fun schedulePagesAfterRemoval(pendingSurvives: Boolean) {
        showSchedule()
        val whole = wholeRecordingIds()
        val step = whole.size - 1
        assertTrue(step >= 1)
        val pending = whole.last()
        val kept = scheduleEntries.filterNot { it.id.value == 1L || !pendingSurvives && it.id.value == pending }
        // The second press uses the viewport at that press, not twice the first page size.
        val uninterrupted = if (pendingSurvives) {
            compose.mainClock.autoAdvance = false
            try {
                compose.pressKey(pageDown)
                compose.pressKey(pageDown)
            } finally {
                compose.mainClock.autoAdvance = true
            }
            focusedRecording().also {
                assertTrue("fixture needs a second page target past the first", it > pending)
                compose.runOnIdle { scheduleVisit.intValue++ }
                assertEquals(1L, focusedRecording())
            }
        } else null
        compose.mainClock.autoAdvance = false
        val expected = try {
            compose.pressKey(pageDown)
            if (pendingSurvives) compose.pressKey(pageDown)
            compose.runOnIdle { scheduleRows.value = kept }
            compose.mainClock.advanceTimeByFrame()
            uninterrupted ?: run {
                val restored = kept.first().id.value
                assertEquals(restored, focusedRecording())
                val newStep = (wholeRecordingIds().size - 1).coerceAtLeast(1)
                compose.pressKey(pageDown)
                kept[newStep].id.value
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertEquals("PageDown after catalogue change (destination survives=$pendingSurvives)", expected, focusedRecording())
    }

    @Test
    @Config(qualifiers = "en-w960dp-h800dp-land-mdpi")
    fun schedulePageContinuesWhenTheDestinationIndexDoesNotChange() {
        showSchedule(height = 760.dp)
        val expected = wholeRecordingIds().last()
        assertTrue("fixture needs more than a single-row step", expected > 2)
        val destination = compose.onAllNodes(hasTestTag("recording-list-entry-$expected") and isFocused())
        fun pageDuration(removeTail: Boolean): Long {
            compose.mainClock.autoAdvance = false
            return try {
                val start = compose.mainClock.currentTime
                compose.pressKey(pageDown)
                compose.mainClock.advanceTimeBy(128)
                if (removeTail) compose.runOnIdle { scheduleRows.value = scheduleEntries.dropLast(1) }
                while (destination.fetchSemanticsNodes().isEmpty() && compose.mainClock.currentTime - start < 650) {
                    compose.mainClock.advanceTimeByFrame()
                }
                assertEquals(expected, focusedRecording())
                compose.mainClock.currentTime - start
            } finally {
                compose.mainClock.autoAdvance = true
            }
        }
        val uninterrupted = pageDuration(removeTail = false)
        compose.runOnIdle { scheduleVisit.intValue++ }
        assertEquals(1L, focusedRecording())
        val beforeFrame = compose.mainClock.currentTime
        compose.mainClock.advanceTimeByFrame()
        val twoFrames = 2 * (compose.mainClock.currentTime - beforeFrame)
        val afterRemoval = pageDuration(removeTail = true)
        assertTrue("Removing an unrelated tail row must not restart paging ($uninterrupted vs $afterRemoval)",
            afterRemoval <= uninterrupted + twoFrames)
    }
}
