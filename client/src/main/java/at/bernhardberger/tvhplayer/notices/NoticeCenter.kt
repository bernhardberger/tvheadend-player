package at.bernhardberger.tvhplayer.notices

import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Opaque identity only, never profile values or credentials. */
class NoticeContext(val profileGeneration: Long, val session: SessionGenerationIdentity?) {
    override fun equals(other: Any?): Boolean = other is NoticeContext &&
        profileGeneration == other.profileGeneration && session === other.session
    override fun hashCode(): Int = 31 * profileGeneration.hashCode() + System.identityHashCode(session)
}

data class QueuedNotice(val id: Long, val notice: Notice, val context: NoticeContext, val pendingUntil: Long)
data class PresentedNotice(val notice: QueuedNotice, val expiresAt: Long)
data class NoticeState(val active: PresentedNotice? = null, val pending: List<QueuedNotice> = emptyList()) {
    val candidate: QueuedNotice? get() = active?.notice ?: pending.firstOrNull()
    val nextExpiry: Long? get() = (pending.map { it.pendingUntil } + listOfNotNull(active?.expiresAt)).minOrNull()
}

/** Single consumer, FIFO with in-place pending replacement. No persisted inbox or replay. */
class NoticeCenter(private val now: () -> Long, private val currentContext: () -> NoticeContext) {
    private val mutableState = MutableStateFlow(NoticeState())
    val state = mutableState.asStateFlow()
    private var nextId = 0L

    fun context(): NoticeContext = currentContext()

    @Synchronized
    fun post(notice: Notice, context: NoticeContext): Boolean {
        prune()
        if (context != currentContext()) return false
        val queued = QueuedNotice(++nextId, notice, context, now() + 30_000)
        val pending = mutableState.value.pending.toMutableList()
        val index = pending.indexOfFirst { it.notice.key == notice.key }
        if (index >= 0) pending[index] = queued else pending.add(queued)
        mutableState.value = mutableState.value.copy(pending = pending.takeLast(8))
        return true
    }

    @Synchronized
    fun show(expectedId: Long, displayMillis: Long): Boolean {
        prune()
        val state = mutableState.value
        val next = state.pending.firstOrNull() ?: return false
        if (state.active != null || next.id != expectedId) return false
        mutableState.value = NoticeState(PresentedNotice(next, now() + displayMillis), state.pending.drop(1))
        return true
    }

    fun remaining(deadline: Long): Long = (deadline - now()).coerceAtLeast(0)

    @Synchronized
    fun prune() {
        val context = currentContext()
        val time = now()
        val state = mutableState.value
        mutableState.value = NoticeState(
            active = state.active?.takeIf { it.notice.context == context && time < it.expiresAt },
            pending = state.pending.filter { it.context == context && time < it.pendingUntil },
        )
    }
}
