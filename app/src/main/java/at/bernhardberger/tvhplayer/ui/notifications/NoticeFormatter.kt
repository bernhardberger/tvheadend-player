package at.bernhardberger.tvhplayer.ui.notifications

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvheadend.sdk.core.DvrChangeKind
import at.bernhardberger.tvheadend.sdk.core.DvrChangeOrigin
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvheadend.sdk.core.DvrSubscriptionError
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackNotice
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.time.Clock
import kotlin.time.Instant

internal enum class AppNoticeIcon { SCHEDULE, RECORDING, CHECK, STOP, WARNING, CANCEL, DELETE }
internal data class FormattedNotice(val headline: String, val detail: String? = null, val icon: AppNoticeIcon? = null)

/** The only resource/wording boundary for transient feedback. */
internal class NoticeFormatter(private val context: Context) {
    fun format(notice: Notice, now: Instant = Clock.System.now()): FormattedNotice = when (notice) {
        is Notice.Dvr -> formatDvr(notice, now)
        is Notice.DvrActionFailed -> FormattedNotice(context.getString(
            if (notice.failure == DvrMutationFeedback.TIMEOUT) R.string.recording_action_not_confirmed
            else R.string.recording_action_failed),
            context.getString(when (notice.failure) {
                DvrMutationFeedback.PERMISSION_DENIED -> R.string.recording_action_permission
                DvrMutationFeedback.CONNECTION_LIMIT -> R.string.recording_action_conn_limit
                DvrMutationFeedback.REJECTED -> R.string.recording_action_rejected
                DvrMutationFeedback.NOT_SUPPORTED -> R.string.recording_action_not_supported
                DvrMutationFeedback.TIMEOUT -> R.string.recording_action_timeout
                DvrMutationFeedback.CONNECTION_UNAVAILABLE -> R.string.recording_action_connection
                DvrMutationFeedback.CONFIRMED, DvrMutationFeedback.ACCEPTED_UNCONFIRMED -> error("Not an action failure")
            }), AppNoticeIcon.WARNING)
        is Notice.Connection -> FormattedNotice(context.getString(when (notice.kind) {
            ConnectionNoticeKind.LOST -> R.string.connection_notice_lost
            ConnectionNoticeKind.RESTORED -> R.string.connection_notice_restored
            ConnectionNoticeKind.LOGIN_REJECTED -> R.string.connection_notice_login_rejected
        }))
        is Notice.CacheClear -> FormattedNotice(context.getString(
            if (notice.success) R.string.cache_notice_cleared else R.string.cache_notice_failed))
        is Notice.BackgroundPlayback -> FormattedNotice(context.getString(when (notice.reason) {
            BackgroundPlaybackNotice.LIMIT_EXPIRED -> R.string.background_live_stopped
            BackgroundPlaybackNotice.TUNER_LOST -> R.string.background_tuner_lost
            BackgroundPlaybackNotice.INTERRUPTED -> R.string.background_live_interrupted
        }))
    }

    private fun formatDvr(notice: Notice.Dvr, now: Instant): FormattedNotice {
        val canceled = notice.entry.state == DvrEntryState.SCHEDULED ||
            (notice.origin as? DvrChangeOrigin.ThisClient)?.mutation == DvrMutationKind.CANCEL
        val (headline, icon) = when (notice.kind) {
            DvrChangeKind.SCHEDULED -> (if (notice.count > 1) R.string.recording_notice_series_scheduled
                else R.string.recording_notice_scheduled) to AppNoticeIcon.SCHEDULE
            DvrChangeKind.RECORDING_STARTED -> R.string.recording_notice_started to AppNoticeIcon.RECORDING
            DvrChangeKind.RECORDING_COMPLETED -> R.string.recording_notice_finished to AppNoticeIcon.CHECK
            DvrChangeKind.RECORDING_STOPPED -> R.string.recording_notice_stopped to AppNoticeIcon.STOP
            DvrChangeKind.RECORDING_ABORTED -> R.string.recording_notice_aborted to AppNoticeIcon.CANCEL
            DvrChangeKind.RECORDING_FAILED -> R.string.recording_notice_failed to AppNoticeIcon.WARNING
            DvrChangeKind.MISSED -> R.string.recording_notice_missed to AppNoticeIcon.WARNING
            DvrChangeKind.REMOVED -> if (canceled) R.string.recording_notice_canceled to AppNoticeIcon.CANCEL
                else R.string.recording_notice_deleted to AppNoticeIcon.DELETE
        }
        val entry = notice.entry
        val channel = entry.channelName?.trim()?.takeIf(String::isNotEmpty)
        val name = entry.title?.trim()?.takeIf(String::isNotEmpty) ?: channel ?: context.getString(R.string.recording_notice_fallback)
        val detail = buildList {
            add(name)
            when (notice.kind) {
                DvrChangeKind.SCHEDULED -> if (notice.count > 1) {
                    add(context.resources.getQuantityString(R.plurals.recording_notice_count, notice.count, notice.count))
                } else {
                    if (channel != null && channel != name) add(channel)
                    entry.start?.let { start ->
                        val locale = context.resources.configuration.locales[0]
                        val zone = ZoneId.systemDefault()
                        val date = java.time.Instant.ofEpochMilli(start.toEpochMilliseconds()).atZone(zone)
                        val today = java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atZone(zone).toLocalDate()
                        val day = when (date.toLocalDate()) {
                            today -> context.getString(R.string.today)
                            today.plusDays(1) -> context.getString(R.string.tomorrow)
                            else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                        }
                        add("$day ${date.format(DateTimeFormatter.ofPattern("HH:mm", locale))}")
                    }
                }
                DvrChangeKind.RECORDING_STARTED -> if (channel != null && channel != name) add(channel)
                DvrChangeKind.RECORDING_FAILED -> entry.subscriptionError?.let { add(it.label(context)) }
                else -> Unit
            }
        }.joinToString(" · ")
        val title = if (notice.kind == DvrChangeKind.REMOVED && canceled && notice.count > 1) {
            context.resources.getQuantityString(R.plurals.recording_notice_canceled_count, notice.count, notice.count)
        } else context.getString(headline)
        return FormattedNotice(title, detail, icon)
    }
}

@Composable
internal fun DvrSubscriptionError.label(): String = label(LocalContext.current)

internal fun DvrSubscriptionError.label(context: Context): String = context.getString(when (this) {
    DvrSubscriptionError.NO_FREE_ADAPTER -> R.string.tvh_no_free_adapter
    DvrSubscriptionError.SCRAMBLED -> R.string.tvh_scrambled
    DvrSubscriptionError.BAD_SIGNAL, DvrSubscriptionError.TUNING_FAILED,
    DvrSubscriptionError.MUX_NOT_ENABLED, DvrSubscriptionError.NO_SERVICE,
    DvrSubscriptionError.INVALID_SERVICE -> R.string.recording_notice_no_signal
    DvrSubscriptionError.NO_DISK_SPACE -> R.string.recording_notice_disk_full
    DvrSubscriptionError.USER_ACCESS -> R.string.recording_notice_access
    DvrSubscriptionError.USER_LIMIT -> R.string.recording_notice_limit
    DvrSubscriptionError.WEAK_STREAM -> R.string.recording_notice_weak_stream
    DvrSubscriptionError.SUBSCRIPTION_OVERRIDDEN -> R.string.recording_notice_subscription_overridden
    DvrSubscriptionError.INVALID_TARGET -> R.string.recording_notice_invalid_target
    DvrSubscriptionError.UNKNOWN -> R.string.recording_notice_unknown_error
})
