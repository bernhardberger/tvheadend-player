package at.bernhardberger.tvhplayer.ui.notifications

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppNoticeKind(val displayMillis: Long) { SUCCESS(4_000), FAILURE(6_000) }

data class AppNotice(
    val id: Long,
    val key: String,
    @param:StringRes val message: Int,
    val kind: AppNoticeKind,
    internal val context: Any,
    internal val pendingUntil: Long,
)

/** Single shell consumer. Pending and presented lifetimes are separate, with no persisted inbox. */
class AppNoticeQueue(
    private val now: () -> Long,
    private val currentContext: () -> Any,
) {
    private val mutableState = MutableStateFlow(AppNoticeState())
    val state = mutableState.asStateFlow()
    private var nextId = 0L

    fun context(): Any = currentContext()

    @Synchronized
    fun post(key: String, @StringRes message: Int, kind: AppNoticeKind, context: Any) {
        prune()
        if (context != currentContext()) return
        val notice = AppNotice(++nextId, key, message, kind, context, now() + 30_000)
        mutableState.value = mutableState.value.copy(
            pending = (mutableState.value.pending.filterNot { it.key == key } + notice).takeLast(2),
        )
    }

    @Synchronized
    fun show(expectedId: Long, displayMillis: Long): Boolean {
        prune()
        val state = mutableState.value
        val next = state.pending.firstOrNull() ?: return false
        if (state.active != null || next.id != expectedId) return false
        mutableState.value = AppNoticeState(
            active = PresentedAppNotice(next, now() + displayMillis), pending = state.pending.drop(1),
        )
        return true
    }

    fun remaining(deadline: Long): Long = (deadline - now()).coerceAtLeast(0)

    @Synchronized
    fun prune() {
        val context = currentContext()
        val time = now()
        val state = mutableState.value
        mutableState.value = AppNoticeState(
            active = state.active?.takeIf { it.notice.context == context && time < it.expiresAt },
            pending = state.pending.filter { it.context == context && time < it.pendingUntil },
        )
    }
}

data class PresentedAppNotice(val notice: AppNotice, val expiresAt: Long)
data class AppNoticeState(
    val active: PresentedAppNotice? = null,
    val pending: List<AppNotice> = emptyList(),
) {
    val candidate: AppNotice? get() = active?.notice ?: pending.firstOrNull()
    val nextExpiry: Long? get() = (pending.map { it.pendingUntil } + listOfNotNull(active?.expiresAt)).minOrNull()
}

/** Opaque identity only: never profile values, credentials, or presentation strings. */
class AppNoticeContext(private val generation: Long, private val session: Any?) {
    override fun equals(other: Any?): Boolean = other is AppNoticeContext &&
        generation == other.generation && session === other.session
    override fun hashCode(): Int = 31 * generation.hashCode() + System.identityHashCode(session)
}
