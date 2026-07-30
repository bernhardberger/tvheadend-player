package at.bernhardberger.tvhplayer.ui

import androidx.annotation.StringRes
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.SubscriptionFailureKind

@StringRes
internal fun subscriptionFailureMessageResource(kind: SubscriptionFailureKind): Int = when (kind) {
    SubscriptionFailureKind.INVALID_TARGET -> R.string.tvh_target_invalid
    SubscriptionFailureKind.NO_FREE_ADAPTER -> R.string.tvh_no_free_adapter
    SubscriptionFailureKind.MUX_NOT_ENABLED -> R.string.tvh_mux_not_enabled
    SubscriptionFailureKind.TUNING_FAILED -> R.string.tvh_tuning_failed
    SubscriptionFailureKind.BAD_SIGNAL -> R.string.tvh_bad_signal
    SubscriptionFailureKind.SCRAMBLED -> R.string.tvh_scrambled
    SubscriptionFailureKind.OVERRIDDEN -> R.string.tvh_subscription_overridden
    SubscriptionFailureKind.NO_INPUT -> R.string.tvh_no_input
}
