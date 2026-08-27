package at.bernhardberger.tvhplayer.acceptance

import java.util.Collections
import java.util.IdentityHashMap

internal fun acceptanceFailureCode(failures: Iterable<Throwable>): String {
    val pending = ArrayDeque<Throwable>()
    failures.forEach(pending::addLast)
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())

    while (pending.isNotEmpty()) {
        val failure = pending.removeFirst()
        if (!visited.add(failure)) continue

        FAILURE_CODE.find(failure.message.orEmpty())?.value?.let { return it }
        failure.cause?.let(pending::addLast)
        failure.suppressed.forEach(pending::addLast)
    }

    return "ACCEPTANCE_UNCLASSIFIED_FAILURE"
}

private val FAILURE_CODE = Regex("""(?<![A-Z0-9_])ACCEPTANCE_[A-Z0-9_]{1,96}(?![A-Z0-9_])""")
