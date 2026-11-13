package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.days

private const val THREE_MONTH_PERIOD = 3L
private const val SIX_MONTH_PERIOD = 6L

enum class RecordingKeepUntilChoice {
    CURRENT, ONE_WEEK, ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR, UNTIL_SPACE_NEEDED, FOREVER,
}

data class RecordingKeepUntilOption(
    val choice: RecordingKeepUntilChoice,
    val removal: DvrRemoval?,
    val expirySec: Long?,
    val selected: Boolean,
)

/** Dates reflect the server's whole-day expiry, not the requested calendar target. */
fun recordingKeepUntilOptions(recording: DvrEntry, nowSec: Long, zone: ZoneId): List<RecordingKeepUntilOption> {
    val now = java.time.Instant.ofEpochSecond(nowSec).atZone(zone)
    val stop = recording.stop?.epochSeconds
    val options = RecordingKeepUntilChoice.entries.map { choice ->
        val removal = when (choice) {
            RecordingKeepUntilChoice.CURRENT -> recording.removal?.takeUnless { it == DvrRemoval.ServerDefault }
            RecordingKeepUntilChoice.UNTIL_SPACE_NEEDED -> DvrRemoval.UntilSpaceNeeded
            RecordingKeepUntilChoice.FOREVER -> DvrRemoval.Forever
            RecordingKeepUntilChoice.ONE_WEEK,
            RecordingKeepUntilChoice.ONE_MONTH,
            RecordingKeepUntilChoice.THREE_MONTHS,
            RecordingKeepUntilChoice.SIX_MONTHS,
            RecordingKeepUntilChoice.ONE_YEAR -> recordingKeepUntilTarget(choice, now)?.let { target ->
                stop?.let { recordingKeepUntilDays(it, target) }
            }
        }
        val expiry = (removal as? DvrRemoval.Days)?.let { days -> stop?.plus(days.days * 1.days.inWholeSeconds) }
        RecordingKeepUntilOption(choice, removal, expiry, selected = false)
    }
    val selected = recording.removal?.let { current ->
        options.firstOrNull { it.choice != RecordingKeepUntilChoice.CURRENT && it.removal == current }?.choice
    } ?: RecordingKeepUntilChoice.CURRENT
    return options.filter {
        it.choice != RecordingKeepUntilChoice.CURRENT || selected == RecordingKeepUntilChoice.CURRENT
    }.map { it.copy(selected = it.choice == selected) }
}

private fun recordingKeepUntilTarget(choice: RecordingKeepUntilChoice, now: ZonedDateTime): Long? = when (choice) {
    RecordingKeepUntilChoice.ONE_WEEK -> now.plusWeeks(1)
    RecordingKeepUntilChoice.ONE_MONTH -> now.plusMonths(1)
    RecordingKeepUntilChoice.THREE_MONTHS -> now.plusMonths(THREE_MONTH_PERIOD)
    RecordingKeepUntilChoice.SIX_MONTHS -> now.plusMonths(SIX_MONTH_PERIOD)
    RecordingKeepUntilChoice.ONE_YEAR -> now.plusYears(1)
    RecordingKeepUntilChoice.CURRENT,
    RecordingKeepUntilChoice.UNTIL_SPACE_NEEDED,
    RecordingKeepUntilChoice.FOREVER -> null
}?.toEpochSecond()

private fun recordingKeepUntilDays(stopSec: Long, targetSec: Long): DvrRemoval.Days? {
    val seconds = targetSec - stopSec
    val day = 1.days.inWholeSeconds
    val days = (seconds / day + if (seconds % day > 0) 1 else 0).coerceAtLeast(1)
    // The final two positive Int values are Tvheadend's non-day removal policies.
    return days.takeIf { it <= Int.MAX_VALUE.toLong() - 2 }?.let { DvrRemoval.Days(it.toInt()) }
}
