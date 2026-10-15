package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes
import java.time.LocalDate

@Composable
fun playerMinutesLeft(minutes: Int, spoken: Boolean = false): String = stringResource(
    R.string.player_minutes_left, formatDurationMinutes(minutes.toLong(), LocalResources.current, spoken),
)

@Composable
internal fun playerSpokenTimeRange(range: String): String {
    val parts = range.split(Regex("\\s*[–—-]\\s*"), limit = 2)
    return if (parts.size == 2) stringResource(R.string.player_time_range_spoken, parts[0], parts[1]) else range
}

@Composable
fun playerRecordedDate(date: LocalDate): String {
    val month = stringArrayResource(R.array.player_month_short)[date.monthValue - 1]
    return stringResource(R.string.player_recorded_date, date.dayOfMonth, month)
}
