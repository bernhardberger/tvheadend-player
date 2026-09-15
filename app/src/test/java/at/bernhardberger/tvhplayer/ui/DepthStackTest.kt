package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.ui.components.depth.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DepthStackTest {
    @Test fun fourLevelsRestoreExactParentsAndViewportsAfterSerialization() {
        var stack = DepthStack(listOf(DepthFrame("root")))
        val saved = mutableListOf<DepthFrame>()
        repeat(3) { depth ->
            val items = List(20) { DepthItem("$depth-item-$it", "level-${depth + 1}") }
            stack = stack.reconcile(items).focus(items[12].id, items).viewport(items[9].id, 9, 27 + depth)
            saved += stack.active
            stack = stack.enter(items[12], items)
        }
        assertEquals(4, stack.frames.size)
        stack = Json.decodeFromString<DepthStack>(Json.encodeToString(stack))
        saved.asReversed().forEach { parent -> stack = stack.pop(); assertEquals(parent, stack.active) }
        assertEquals(stack, stack.pop())
    }

    @Test fun missingItemsUseNearestRemainingIndexAndEmptyRecoveryIsDeterministic() {
        val items = (0..4).map { DepthItem("item-$it") }
        val stack = DepthStack(listOf(DepthFrame("root"))).reconcile(items)
            .focus("item-3", items).viewport("item-2", 2, 19)
        val remaining = listOf(items[0], items[1], items[4])
        val restored = stack.reconcile(remaining)
        assertEquals("item-4", restored.active.focusedItemId)
        assertEquals("item-4", restored.active.firstVisibleItemId)
        assertEquals(19, restored.active.scrollOffset)
        assertNull(restored.reconcile(emptyList()).active.focusedItemId)
        assertEquals(0, restored.reconcile(emptyList()).active.scrollOffset)
    }

    @Test fun stalePreviewsAndOldVisitsNeverWinAfterFocusChangesOrReentry() {
        val items = listOf(DepthItem("a", "child-a"), DepthItem("b", "child-b"))
        var stack = DepthStack(listOf(DepthFrame("root"))).reconcile(items)
        val preview = requireNotNull(stack.previewKey(items))
        stack = stack.focus("b", items)
        assertFalse(stack.acceptsPreview(preview, items))
        stack = stack.focus("a", items)
        assertFalse(stack.acceptsPreview(preview, items))
        stack = stack.focus("a", items).enter(items[0], items).pop()
        assertFalse(stack.acceptsPreview(preview, items))
        assertTrue(stack.acceptsPreview(requireNotNull(stack.previewKey(items)), items))
    }

    @Test fun leafAndSubmenuSemanticsRejectStaleOrUnfocusedRows() {
        val items = listOf(DepthItem("leaf"), DepthItem("child", "level"))
        val stack = DepthStack(listOf(DepthFrame("root"))).reconcile(items)
        assertEquals(stack, stack.enter(items[0], items))
        assertEquals(stack, stack.enter(items[1], items))
        assertNull(stack.previewKey(items))
        val focused = stack.focus("child", items)
        assertEquals(focused, focused.enter(DepthItem("child", "stale"), items))
        assertEquals("level", focused.enter(items[1], items).active.levelId)
    }

    @Test fun relocationConsumesRepeatAndReleaseWithoutActivatingNewTarget() {
        val cycle = DepthKeyCycle()
        var calls = 0
        assertTrue(cycle.handle(23, true, 0) { calls++; true })
        repeat(4) { assertTrue(cycle.handle(23, true, it + 1) { calls++; true }) }
        assertTrue(cycle.handle(23, false, 0) { calls++; true })
        assertEquals(1, calls)
        assertFalse(cycle.handle(4, true, 0) { false })
        assertFalse(cycle.handle(4, false, 0) { error("release must not act") })
        assertTrue(cycle.handle(23, true, 0) { calls++; true })
        assertTrue(cycle.handle(23, true, 0) { calls++; true }) // previous release was lost
        assertTrue(cycle.handle(23, false, 0) { error("release must not act") })
        assertEquals(3, calls)
    }
}
