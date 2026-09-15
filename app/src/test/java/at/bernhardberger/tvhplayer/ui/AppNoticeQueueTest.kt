package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.ui.notifications.*
import org.junit.Assert.*
import org.junit.Test

class AppNoticeQueueTest {
    private var time = 0L
    private var context: Any = AppNoticeContext(0, null)
    private val queue = AppNoticeQueue({ time }, { context })
    private fun post(key: String = "cache") = queue.post(key, 1, AppNoticeKind.SUCCESS, context)

    @Test fun oneVisibleAndTwoPendingWithLatestPendingKeyWinning() {
        post()
        val first = queue.state.value.pending.single()
        assertTrue(queue.show(first.id, 4_000))
        post("a"); post("b"); post("a")
        assertEquals(listOf("b", "a"), queue.state.value.pending.map { it.key })
        post("c")
        assertEquals(listOf("a", "c"), queue.state.value.pending.map { it.key })
        assertEquals(first.id, queue.state.value.active!!.notice.id)
        assertFalse(queue.show(first.id, 4_000))
    }

    @Test fun pendingExpiryDoesNotCapAccessibilityExtendedDisplayOrRestartIt() {
        post()
        val id = queue.state.value.pending.single().id
        time = 29_000
        assertTrue(queue.show(id, 60_000))
        time = 31_000
        queue.prune()
        assertEquals(89_000L, queue.state.value.active!!.expiresAt)
        assertFalse(queue.show(id, 60_000)) // Recomposition/recreation cannot restart it.
        time = 89_000
        queue.prune()
        assertNull(queue.state.value.candidate)
    }

    @Test fun contextReplacementInvalidatesPendingVisibleAndLateOldResults() {
        val old = context
        post(); queue.show(queue.state.value.pending.single().id, 4_000); post("pending")
        context = AppNoticeContext(1, null)
        queue.prune()
        queue.post("late", 1, AppNoticeKind.FAILURE, old)
        assertNull(queue.state.value.candidate)
        val session = Any()
        context = AppNoticeContext(1, session)
        post()
        context = AppNoticeContext(1, Any())
        queue.prune()
        assertNull(queue.state.value.candidate)
    }

    @Test fun blockedUnshownNoticesKeepOriginalExpiryAndStaleCandidatesCannotShow() {
        post()
        val stale = queue.state.value.pending.single().id
        time = 1_000; post()
        assertFalse(queue.show(stale, 4_000))
        time = 31_000
        queue.prune()
        assertNull(queue.state.value.candidate)
    }
}
