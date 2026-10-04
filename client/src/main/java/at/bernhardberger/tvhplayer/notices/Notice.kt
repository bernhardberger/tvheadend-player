package at.bernhardberger.tvhplayer.notices

import at.bernhardberger.tvheadend.sdk.core.DvrChangeKind
import at.bernhardberger.tvheadend.sdk.core.DvrChangeOrigin
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
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

    data class DvrActionFailed(val action: DvrMutationKind, val failure: DvrMutationFeedback) : Notice {
        init { require(failure.isFailure) }
        override val key = "dvr-action"
        override val severity = NoticeSeverity.FAILURE
    }

    data class Connection(val kind: ConnectionNoticeKind) : Notice {
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
    REJECTED(true), NOT_SUPPORTED(true), TIMEOUT(true), CONNECTION_UNAVAILABLE(true),
}

fun DvrMutationResult<*>.toDvrMutationFeedback(): DvrMutationFeedback = when (this) {
    is DvrMutationResult.Confirmed -> DvrMutationFeedback.CONFIRMED
    is DvrMutationResult.AcceptedButUnconfirmed -> DvrMutationFeedback.ACCEPTED_UNCONFIRMED
    DvrMutationResult.AccessDenied -> DvrMutationFeedback.PERMISSION_DENIED
    DvrMutationResult.ConnectionLimit -> DvrMutationFeedback.CONNECTION_LIMIT
    DvrMutationResult.ServerRejected -> DvrMutationFeedback.REJECTED
    DvrMutationResult.NotSupported -> DvrMutationFeedback.NOT_SUPPORTED
    DvrMutationResult.Timeout -> DvrMutationFeedback.TIMEOUT
    DvrMutationResult.NotReady, DvrMutationResult.ObservationExpired,
    DvrMutationResult.TransportUnavailable -> DvrMutationFeedback.CONNECTION_UNAVAILABLE
}
