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

/** How many left levels keep their focused row and viewport; the oldest left is forgotten first. */
internal const val DepthRememberedLevels = 64

/**
 * An arbitrary-depth local stack; no destination registry or product commands. A level left
 * by [pop] stays in [remembered], so entering it again lands on the row it was left on, as
 * Kodi returns to the item last selected in a folder.
 */
@Serializable
data class DepthStack(
    val frames: List<DepthFrame>,
    val visit: Long = 0,
    val previewRevision: Long = 0,
    val remembered: Map<String, DepthFrame> = emptyMap(),
) {
    init { require(frames.isNotEmpty()) }
    val active: DepthFrame get() = frames.last()
    val canPop: Boolean get() = frames.size > 1
    /** Root-to-active level ids. Visit is not part of this visual identity. */
    val path: List<String> get() = frames.map { it.levelId }

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

    /** [restore] is false for a choice level, which always opens on its current choice. */
    fun enter(item: DepthItem, items: List<DepthItem>, restore: Boolean = true): DepthStack {
        // A stale rendered row cannot enter a replacement branch. Leaves never push.
        if (items.none { it == item } || item.id != active.focusedItemId) return this
        val child = item.childLevelId ?: return this
        return copy(frames = frames + if (restore) frameFor(child) else DepthFrame(child), visit = visit + 1)
    }

    fun pop(): DepthStack = if (canPop) {
        val kept = (remembered - active.levelId).entries.toList().takeLast(DepthRememberedLevels - 1)
        copy(
            frames = frames.dropLast(1),
            visit = visit + 1,
            remembered = kept.associate { it.toPair() } + (active.levelId to active),
        )
    } else this

    /** The frame a level opens with: where it was left, or its first row. */
    fun frameFor(levelId: String): DepthFrame = remembered[levelId] ?: DepthFrame(levelId)

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

/**
 * Keep relocation presses consumed even after focus moves to the new level. A cycle started
 * with [repeats] relocates again on each key repeat, as AOSP TvSettings walks levels while
 * Left or Right is held; its repeats stay consumed either way.
 */
class DepthKeyCycle {
    private val held = mutableSetOf<Int>()
    private val repeating = mutableSetOf<Int>()
    fun handle(keyCode: Int, down: Boolean, repeat: Int, repeats: Boolean = false, relocate: () -> Boolean): Boolean {
        if (!down) {
            repeating.remove(keyCode)
            return held.remove(keyCode)
        }
        if (keyCode in held && repeat > 0) {
            if (keyCode in repeating) relocate()
            return true
        }
        // A fresh Down after an interrupted/lost release starts a new physical cycle.
        held.remove(keyCode)
        repeating.remove(keyCode)
        if (repeat > 0) return false
        if (!relocate()) return false
        held += keyCode
        if (repeats) repeating += keyCode
        return true
    }
}
