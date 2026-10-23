package at.bernhardberger.tvhplayer.notices

import at.bernhardberger.tvheadend.sdk.core.DvrChangeKind
import at.bernhardberger.tvheadend.sdk.core.DvrChangeOrigin
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrProgressResult
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackNotice

enum class NoticeSeverity { INFO, FAILURE }
enum class ConnectionNoticeKind { LOST, RESTORED, LOGIN_REJECTED }

/** User feedback policy, with no resources or presentation dependencies. */
sealed interface Notice {
    val key: String
    val severity: NoticeSeverity

    data class Dvr(
        val kind: DvrChangeKind,
        val entry: DvrEntry,
        val origin: DvrChangeOrigin,
        val count: Int = 1,
    ) : Notice {
        init { require(count > 0) }
        override val key: String = if (kind == DvrChangeKind.SCHEDULED && entry.autorecRuleId != null) {
            "dvr-series:${entry.autorecRuleId?.value}"
        } else if (kind == DvrChangeKind.REMOVED && count > 1 && entry.autorecRuleId != null) {
            "dvr-series-canceled:${entry.autorecRuleId?.value}"
        } else "dvr:${entry.id.value}"
        override val severity = when (kind) {
            DvrChangeKind.RECORDING_FAILED, DvrChangeKind.MISSED -> NoticeSeverity.FAILURE
            else -> NoticeSeverity.INFO
        }
    }

    data class DvrActionFailed(
        val action: DvrMutationKind,
        val failure: DvrMutationFeedback,
        /** SDK-sanitized, display-only text; never include in diagnostics. */
        val serverMessage: String? = null,
    ) : Notice {
        init {
            require(failure.isFailure)
            require(serverMessage == null || failure == DvrMutationFeedback.REJECTED)
        }
        override val key = "dvr-action"
        override val severity = NoticeSeverity.FAILURE
        override fun toString(): String = "DvrActionFailed(action=$action, failure=$failure)"
    }

    data class Connection(val kind: ConnectionNoticeKind, val showingSavedChannels: Boolean = false) : Notice {
        override val key = "connection"
        override val severity = if (kind == ConnectionNoticeKind.RESTORED) NoticeSeverity.INFO else NoticeSeverity.FAILURE
    }

    data class CacheClear(val success: Boolean) : Notice {
        override val key = "cache-clear"
        override val severity = if (success) NoticeSeverity.INFO else NoticeSeverity.FAILURE
    }

    data class BackgroundPlayback(val reason: BackgroundPlaybackNotice) : Notice {
        override val key = "background-playback"
        override val severity = if (reason == BackgroundPlaybackNotice.LIMIT_EXPIRED) NoticeSeverity.INFO else NoticeSeverity.FAILURE
    }
}

/** Coarse user-facing action outcome; success is never posted as an action notice. */
enum class DvrMutationFeedback(val isFailure: Boolean) {
    CONFIRMED(false), ACCEPTED_UNCONFIRMED(false), PERMISSION_DENIED(true), CONNECTION_LIMIT(true),
    REJECTED(true), NOT_SUPPORTED(true), TIMEOUT(true), UNKNOWN_OUTCOME(true), CONNECTION_UNAVAILABLE(true),
}

fun DvrMutationResult<*>.toDvrMutationFeedback(): DvrMutationFeedback = when (this) {
    is DvrMutationResult.Confirmed -> DvrMutationFeedback.CONFIRMED
    is DvrMutationResult.AcceptedButUnconfirmed -> DvrMutationFeedback.ACCEPTED_UNCONFIRMED
    is DvrMutationResult.Failed -> dvrFailureFeedback(failure, mayHaveApplied)
}

fun DvrProgressResult.Failed.toDvrMutationFeedback(): DvrMutationFeedback =
    dvrFailureFeedback(failure, mayHaveApplied)

private fun dvrFailureFeedback(failure: TvheadendFailure, mayHaveApplied: Boolean): DvrMutationFeedback = when (failure) {
    TvheadendFailure.ACCESS_DENIED -> DvrMutationFeedback.PERMISSION_DENIED
    TvheadendFailure.CONNECTION_LIMIT -> DvrMutationFeedback.CONNECTION_LIMIT
    TvheadendFailure.SERVER_REJECTED -> DvrMutationFeedback.REJECTED
    TvheadendFailure.NOT_SUPPORTED -> DvrMutationFeedback.NOT_SUPPORTED
    TvheadendFailure.TIMEOUT -> DvrMutationFeedback.TIMEOUT
    TvheadendFailure.NOT_READY, TvheadendFailure.OBSERVATION_EXPIRED,
    TvheadendFailure.CONNECTION_CHANGED, TvheadendFailure.TRANSPORT_UNAVAILABLE ->
        if (mayHaveApplied) DvrMutationFeedback.UNKNOWN_OUTCOME else DvrMutationFeedback.CONNECTION_UNAVAILABLE
    else -> if (mayHaveApplied) DvrMutationFeedback.UNKNOWN_OUTCOME else DvrMutationFeedback.REJECTED
}
