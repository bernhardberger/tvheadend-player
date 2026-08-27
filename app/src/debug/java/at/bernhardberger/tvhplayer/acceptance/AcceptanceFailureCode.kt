package at.bernhardberger.tvhplayer.acceptance

import at.bernhardberger.tvhplayer.playback.AppLivePlaybackIssue
import at.bernhardberger.tvhplayer.playback.AppPlaybackCommandResult
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException

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

internal inline fun <T> withAcceptanceStage(code: String, block: () -> T): T = try {
    block()
} catch (failure: CancellationException) {
    throw failure
} catch (failure: AssertionError) {
    throw failure
} catch (_: Exception) {
    throw AssertionError(code)
}

internal fun acceptanceTargetFailureCode(result: AppPlaybackCommandResult): String = when (result) {
    AppPlaybackCommandResult.NOT_RUNNING -> "ACCEPTANCE_TARGET_NOT_RUNNING"
    AppPlaybackCommandResult.SHUT_DOWN -> "ACCEPTANCE_TARGET_SHUT_DOWN"
    AppPlaybackCommandResult.NOT_READY -> "ACCEPTANCE_TARGET_NOT_READY"
    AppPlaybackCommandResult.TARGET_UNAVAILABLE -> "ACCEPTANCE_TARGET_UNAVAILABLE"
    AppPlaybackCommandResult.PLAYER_UNAVAILABLE -> "ACCEPTANCE_TARGET_PLAYER_UNAVAILABLE"
    else -> "ACCEPTANCE_TARGET_ADMISSION_REJECTED"
}

internal fun acceptanceSubscriptionFailureCode(issue: AppLivePlaybackIssue): String = when (issue) {
    AppLivePlaybackIssue.INVALID_TARGET -> "ACCEPTANCE_SUBSCRIPTION_INVALID_TARGET"
    AppLivePlaybackIssue.NO_FREE_ADAPTER -> "ACCEPTANCE_SUBSCRIPTION_NO_FREE_ADAPTER"
    AppLivePlaybackIssue.MUX_NOT_ENABLED -> "ACCEPTANCE_SUBSCRIPTION_MUX_NOT_ENABLED"
    AppLivePlaybackIssue.TUNING_FAILED -> "ACCEPTANCE_SUBSCRIPTION_TUNING_FAILED"
    AppLivePlaybackIssue.BAD_SIGNAL -> "ACCEPTANCE_SUBSCRIPTION_BAD_SIGNAL"
    AppLivePlaybackIssue.SCRAMBLED -> "ACCEPTANCE_SUBSCRIPTION_SCRAMBLED"
    AppLivePlaybackIssue.OVERRIDDEN -> "ACCEPTANCE_SUBSCRIPTION_OVERRIDDEN"
    AppLivePlaybackIssue.ACCESS_DENIED -> "ACCEPTANCE_SUBSCRIPTION_ACCESS_DENIED"
    AppLivePlaybackIssue.CONNECTION_LIMIT -> "ACCEPTANCE_SUBSCRIPTION_CONNECTION_LIMIT"
    AppLivePlaybackIssue.WEAK_STREAM -> "ACCEPTANCE_SUBSCRIPTION_WEAK_STREAM"
    AppLivePlaybackIssue.NO_DISK_SPACE -> "ACCEPTANCE_SUBSCRIPTION_NO_DISK_SPACE"
    AppLivePlaybackIssue.UNKNOWN -> "ACCEPTANCE_SUBSCRIPTION_UNKNOWN"
    AppLivePlaybackIssue.NO_INPUT -> "ACCEPTANCE_SUBSCRIPTION_NO_INPUT"
}

private val FAILURE_CODE = Regex("""(?<![A-Z0-9_])ACCEPTANCE_[A-Z0-9_]{1,96}(?![A-Z0-9_])""")
