package at.bernhardberger.tvhplayer.ui.common

import android.content.res.Resources
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.RecordingExpiryTimeLeft
import at.bernhardberger.tvhplayer.core.RecordingExpiryUnit
import at.bernhardberger.tvhplayer.core.recordingExpiryTimeLeft
import java.util.Locale

/** Shared compact text and plural wording for the card badge, list segment and spoken expiry. */
fun formatRecordingExpiry(left: RecordingExpiryTimeLeft, resources: Resources, compact: Boolean = false): String {
    if (compact) {
        val text = when (left.unit) {
            RecordingExpiryUnit.MINUTES -> R.string.recording_expiry_minutes_short
            RecordingExpiryUnit.HOURS -> R.string.recording_expiry_hours_short
            RecordingExpiryUnit.DAYS -> R.string.recording_expiry_days_short
        }
        return resources.getString(text, left.amount)
    }
    val plural = when (left.unit) {
        RecordingExpiryUnit.MINUTES -> R.plurals.recording_expiry_minutes
        RecordingExpiryUnit.HOURS -> R.plurals.recording_expiry_hours
        RecordingExpiryUnit.DAYS -> R.plurals.recording_expiry_days
    }
    return resources.getQuantityString(plural, left.amount, left.amount)
}

/** The full future date stays visible beyond the compact warning window. */
fun formatRecordingExpiryDate(removalSec: Long, nowSec: Long, resources: Resources, locale: Locale): String {
    val date = resources.getString(
        R.string.recording_expires_in, formatRecordingDate(removalSec, locale = locale, nowSec = nowSec),
    )
    val relative = recordingExpiryTimeLeft(removalSec, nowSec)?.let { formatRecordingExpiry(it, resources) }
    return if (relative == null) date else resources.getString(R.string.recording_expiry_date_relative, date, relative)
}
