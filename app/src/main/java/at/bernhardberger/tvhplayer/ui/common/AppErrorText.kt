package at.bernhardberger.tvhplayer.ui.common

import android.content.Context
import androidx.annotation.StringRes
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.AppErrorCondition

/**
 * The only wording boundary for numbered errors. A condition reads as its title, then why it
 * happened (the reason), then what helps (an optional hint shared by conditions with the same
 * advice). Its catalog number stays internal and is never shown.
 */
internal class AppErrorText(private val context: Context) {
    fun title(condition: AppErrorCondition): String = context.getString(condition.titleRes())

    /**
     * [reconnectInSeconds] is the countdown to the SDK's next attempt, where it gives one;
     * [startsAt] is the already formatted start of a recording that has not begun.
     */
    fun reason(
        condition: AppErrorCondition,
        reconnectInSeconds: Int? = null,
        startsAt: String? = null,
    ): String = when {
        condition == AppErrorCondition.RECONNECTING -> reconnecting(reconnectInSeconds)
        condition == AppErrorCondition.RECORDING_NOT_STARTED && startsAt != null ->
            context.getString(R.string.error_302_reason, startsAt)
        condition == AppErrorCondition.RECORDING_NOT_STARTED -> context.getString(R.string.error_302_reason_untimed)
        else -> context.getString(condition.reasonRes())
    }

    /**
     * The passive text while something reconnects by itself, with no number of its own: the
     * countdown to the next attempt where [inSeconds] is known and positive, else the plain one.
     */
    fun reconnecting(inSeconds: Int?): String =
        if (inSeconds != null && inSeconds > 0) {
            context.getString(R.string.error_110_reason, inSeconds)
        } else {
            context.getString(R.string.error_110_reason_pending)
        }

    /** The countdown line under the plain [reconnecting] text while the next attempt is [inSeconds] away. */
    fun nextAttempt(inSeconds: Int): String = context.getString(R.string.error_reconnect_next_attempt, inSeconds)

    /** What helps, or null where it would only repeat the reason (a countdown, a start time, a finish). */
    fun hint(condition: AppErrorCondition): String? = condition.hintRes()?.let(context::getString)

    /** The reason and hint as one paragraph, as the alerts and the channel message show them. */
    fun paragraph(condition: AppErrorCondition, startsAt: String? = null): String =
        listOfNotNull(reason(condition, startsAt = startsAt), hint(condition)).joinToString(" ")


    fun label(action: AppErrorAction): String = context.getString(action.labelRes())
}

@StringRes
internal fun AppErrorCondition.titleRes(): Int = when (this) {
    AppErrorCondition.UNREACHABLE -> R.string.error_101_title
    AppErrorCondition.TIMEOUT -> R.string.error_102_title
    AppErrorCondition.AUTHENTICATION_REJECTED -> R.string.error_103_title
    AppErrorCondition.PERMISSION_DENIED -> R.string.error_104_title
    AppErrorCondition.INCOMPATIBLE_SERVER -> R.string.error_105_title
    AppErrorCondition.CREDENTIAL_UNAVAILABLE -> R.string.error_106_title
    AppErrorCondition.NOT_CONFIGURED -> R.string.error_107_title
    AppErrorCondition.NO_CHANNELS -> R.string.error_108_title
    AppErrorCondition.LOAD_FAILED -> R.string.error_109_title
    AppErrorCondition.RECONNECTING -> R.string.error_110_title
    AppErrorCondition.NOT_TVHEADEND -> R.string.error_111_title
    AppErrorCondition.NO_NETWORK -> R.string.error_112_title
    AppErrorCondition.NO_FREE_ADAPTER -> R.string.error_201_title
    AppErrorCondition.NO_SIGNAL -> R.string.error_202_title
    AppErrorCondition.MUX_NOT_ENABLED -> R.string.error_203_title
    AppErrorCondition.SCRAMBLED -> R.string.error_204_title
    AppErrorCondition.CHANNEL_ACCESS_DENIED -> R.string.error_205_title
    AppErrorCondition.CHANNEL_TAKEN_OVER -> R.string.error_206_title
    AppErrorCondition.STREAM_STOPPED -> R.string.error_207_title
    AppErrorCondition.USER_LIMIT -> R.string.error_208_title
    AppErrorCondition.CHANNEL_PLAYBACK_FAILED -> R.string.error_210_title
    AppErrorCondition.RECORDING_INTERRUPTED -> R.string.error_301_title
    AppErrorCondition.RECORDING_NOT_STARTED -> R.string.error_302_title
    AppErrorCondition.RECORDING_FILE_MISSING -> R.string.error_303_title
    AppErrorCondition.RECORDING_UNAVAILABLE -> R.string.error_304_title
    AppErrorCondition.RECORDING_DEFERRED -> R.string.error_305_title
    AppErrorCondition.RECORDING_PLAYBACK_FAILED -> R.string.error_306_title
}

/**
 * The plain reason. Private because 110 and 302 are format strings: only [AppErrorText.reason]
 * substitutes the countdown and start time or falls back to their plain sentences.
 */
@StringRes
private fun AppErrorCondition.reasonRes(): Int = when (this) {
    AppErrorCondition.UNREACHABLE -> R.string.error_101_reason
    AppErrorCondition.TIMEOUT -> R.string.error_102_reason
    AppErrorCondition.AUTHENTICATION_REJECTED -> R.string.error_103_reason
    AppErrorCondition.PERMISSION_DENIED -> R.string.error_104_reason
    AppErrorCondition.INCOMPATIBLE_SERVER -> R.string.error_105_reason
    AppErrorCondition.CREDENTIAL_UNAVAILABLE -> R.string.error_106_reason
    AppErrorCondition.NOT_CONFIGURED -> R.string.error_107_reason
    AppErrorCondition.NO_CHANNELS -> R.string.error_108_reason
    AppErrorCondition.LOAD_FAILED -> R.string.error_109_reason
    AppErrorCondition.RECONNECTING -> R.string.error_110_reason
    AppErrorCondition.NOT_TVHEADEND -> R.string.error_111_reason
    AppErrorCondition.NO_NETWORK -> R.string.error_112_reason
    AppErrorCondition.NO_FREE_ADAPTER -> R.string.error_201_reason
    AppErrorCondition.NO_SIGNAL -> R.string.error_202_reason
    AppErrorCondition.MUX_NOT_ENABLED -> R.string.error_203_reason
    AppErrorCondition.SCRAMBLED -> R.string.error_204_reason
    AppErrorCondition.CHANNEL_ACCESS_DENIED -> R.string.error_205_reason
    AppErrorCondition.CHANNEL_TAKEN_OVER -> R.string.error_206_reason
    AppErrorCondition.STREAM_STOPPED -> R.string.error_207_reason
    AppErrorCondition.USER_LIMIT -> R.string.error_208_reason
    AppErrorCondition.CHANNEL_PLAYBACK_FAILED -> R.string.error_210_reason
    AppErrorCondition.RECORDING_INTERRUPTED -> R.string.error_301_reason
    AppErrorCondition.RECORDING_NOT_STARTED -> R.string.error_302_reason
    AppErrorCondition.RECORDING_FILE_MISSING -> R.string.error_303_reason
    AppErrorCondition.RECORDING_UNAVAILABLE -> R.string.error_304_reason
    AppErrorCondition.RECORDING_DEFERRED -> R.string.error_305_reason
    AppErrorCondition.RECORDING_PLAYBACK_FAILED -> R.string.error_306_reason
}

/** Hints are shared: conditions with the same advice point at one string. */
@StringRes
internal fun AppErrorCondition.hintRes(): Int? = when (this) {
    AppErrorCondition.UNREACHABLE -> R.string.error_hint_check_server
    AppErrorCondition.TIMEOUT -> R.string.error_hint_try_in_a_moment
    AppErrorCondition.AUTHENTICATION_REJECTED -> R.string.error_hint_check_sign_in
    AppErrorCondition.PERMISSION_DENIED -> R.string.error_hint_check_account_permissions
    AppErrorCondition.INCOMPATIBLE_SERVER -> R.string.error_hint_update_tvheadend
    AppErrorCondition.CREDENTIAL_UNAVAILABLE -> R.string.error_hint_enter_sign_in_again
    AppErrorCondition.NOT_CONFIGURED -> R.string.error_hint_add_server
    AppErrorCondition.NO_CHANNELS -> R.string.error_hint_check_channels_and_permissions
    AppErrorCondition.LOAD_FAILED -> R.string.error_hint_try_again_or_check_settings
    AppErrorCondition.RECONNECTING -> null
    AppErrorCondition.NOT_TVHEADEND -> R.string.error_hint_check_address
    AppErrorCondition.NO_NETWORK -> R.string.error_hint_check_tv_network
    AppErrorCondition.NO_FREE_ADAPTER -> R.string.error_hint_try_later_or_other_channel
    AppErrorCondition.NO_SIGNAL -> R.string.error_hint_try_later_or_other_channel
    AppErrorCondition.MUX_NOT_ENABLED -> R.string.error_hint_turn_on_mux_or_other_channel
    AppErrorCondition.SCRAMBLED -> R.string.error_hint_other_channel
    AppErrorCondition.CHANNEL_ACCESS_DENIED -> R.string.error_hint_other_channel
    AppErrorCondition.CHANNEL_TAKEN_OVER -> R.string.error_hint_try_later_or_other_channel
    AppErrorCondition.STREAM_STOPPED -> R.string.error_hint_try_later_or_other_channel
    AppErrorCondition.USER_LIMIT -> R.string.error_hint_stop_other_devices
    AppErrorCondition.CHANNEL_PLAYBACK_FAILED -> R.string.error_hint_try_later_or_other_channel
    AppErrorCondition.RECORDING_INTERRUPTED -> R.string.error_hint_try_in_a_moment
    AppErrorCondition.RECORDING_NOT_STARTED -> null
    AppErrorCondition.RECORDING_FILE_MISSING -> R.string.error_hint_check_recording
    AppErrorCondition.RECORDING_UNAVAILABLE -> R.string.error_hint_other_recording
    AppErrorCondition.RECORDING_DEFERRED -> null
    AppErrorCondition.RECORDING_PLAYBACK_FAILED -> R.string.error_hint_try_in_a_moment
}

@StringRes
internal fun AppErrorAction.labelRes(): Int = when (this) {
    AppErrorAction.RETRY -> R.string.action_retry
    AppErrorAction.CONNECTION_SETTINGS -> R.string.action_connection_settings
    AppErrorAction.CLOSE -> R.string.action_close
}
