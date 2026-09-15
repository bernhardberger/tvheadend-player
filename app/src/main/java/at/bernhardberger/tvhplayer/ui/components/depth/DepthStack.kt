package at.bernhardberger.tvhplayer.ui.components.depth

import kotlinx.serialization.Serializable

/** Navigation identity only. Content, loading and actions belong to the consumer. */
data class DepthItem(val id: String, val childLevelId: String? = null)

@Serializable
data class DepthFrame(
    val levelId: String,
    val focusedItemId: String? = null,
    val focusedIndex: Int = 0,
    val firstVisibleItemId: String? = null,
    val firstVisibleIndex: Int = 0,
    val scrollOffset: Int = 0,
)

data class DepthPreviewKey(val visit: Long, val revision: Long, val levelId: String, val itemId: String, val childLevelId: String)

/** An arbitrary-depth local stack; no destination registry or product commands. */
@Serializable
data class DepthStack(val frames: List<DepthFrame>, val visit: Long = 0, val previewRevision: Long = 0) {
    init { require(frames.isNotEmpty()) }
    val active: DepthFrame get() = frames.last()
    val canPop: Boolean get() = frames.size > 1

    fun focus(itemId: String, items: List<DepthItem>): DepthStack {
        val index = items.indexOfFirst { it.id == itemId }
        return if (index < 0) this else replace(active.copy(focusedItemId = itemId, focusedIndex = index))
    }

    fun viewport(itemId: String?, index: Int, offset: Int): DepthStack = replace(
        active.copy(firstVisibleItemId = itemId, firstVisibleIndex = index.coerceAtLeast(0), scrollOffset = offset.coerceAtLeast(0)),
    )

    fun reconcile(items: List<DepthItem>): DepthStack {
        val focusIndex = items.indexOfFirst { it.id == active.focusedItemId }
            .takeIf { it >= 0 } ?: active.focusedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val viewportIndex = items.indexOfFirst { it.id == active.firstVisibleItemId }
            .takeIf { it >= 0 } ?: active.firstVisibleIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        return replace(active.copy(
            focusedItemId = items.getOrNull(focusIndex)?.id,
            focusedIndex = focusIndex,
            firstVisibleItemId = items.getOrNull(viewportIndex)?.id,
            firstVisibleIndex = viewportIndex,
            scrollOffset = if (items.isEmpty()) 0 else active.scrollOffset,
        ))
    }

    fun enter(item: DepthItem, items: List<DepthItem>): DepthStack {
        // A stale rendered row cannot enter a replacement branch. Leaves never push.
        if (items.none { it == item } || item.id != active.focusedItemId) return this
        val child = item.childLevelId ?: return this
        return copy(frames = frames + DepthFrame(child), visit = visit + 1)
    }

    fun pop(): DepthStack = if (canPop) copy(frames = frames.dropLast(1), visit = visit + 1) else this

    fun previewKey(items: List<DepthItem>): DepthPreviewKey? = items
        .firstOrNull { it.id == active.focusedItemId }?.let { item ->
            item.childLevelId?.let { DepthPreviewKey(visit, previewRevision, active.levelId, item.id, it) }
        }

    /** Check after any suspended preview work, including leave/re-enter of the same path. */
    fun acceptsPreview(key: DepthPreviewKey, items: List<DepthItem>): Boolean = previewKey(items) == key

    private fun replace(frame: DepthFrame): DepthStack = copy(
        frames = frames.dropLast(1) + frame,
        previewRevision = previewRevision + if (frame.focusedItemId != active.focusedItemId) 1 else 0,
    )
}

/** Keep relocation presses consumed even after focus moves to the new level. */
class DepthKeyCycle {
    private val held = mutableSetOf<Int>()
    fun handle(keyCode: Int, down: Boolean, repeat: Int, relocate: () -> Boolean): Boolean {
        if (!down) return held.remove(keyCode)
        if (keyCode in held && repeat > 0) return true
        // A fresh Down after an interrupted/lost release starts a new physical cycle.
        held.remove(keyCode)
        if (repeat > 0) return false
        if (!relocate()) return false
        held += keyCode
        return true
    }
}
