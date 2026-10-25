package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.common.formatDayMonth
import at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes
import java.time.LocalDate

@Composable
fun playerMinutesLeft(minutes: Int, spoken: Boolean = false): String = stringResource(
    R.string.player_minutes_left, formatDurationMinutes(minutes.toLong(), LocalResources.current, spoken),
)

@Composable
fun playerRecordedDate(date: LocalDate): String = formatDayMonth(date, LocalConfiguration.current.locales[0])
