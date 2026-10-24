package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvBrowseTrailingInset
import at.bernhardberger.tvhplayer.ui.TvGhostFillAlpha
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate
import at.bernhardberger.tvhplayer.ui.screens.ChannelsHeader
import at.bernhardberger.tvhplayer.ui.screens.ChannelsHeaderFocus
import at.bernhardberger.tvhplayer.ui.screens.ChannelsPanel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The Guide's title row in the Channels header pattern: the title, then the date button, Tags
 * (only with a choice of channel sets) naming the active [tag], Filter (only with [showFilter])
 * naming the highlighted [genre] after its [genreDot], and View options. Down moves to the
 * programmes, the date button's start edge to the navigation rail.
 */
@Composable
internal fun GuideHeader(
    tag: String?,
    genre: String?,
    genreDot: Color?,
    windowStartSec: Long,
    nowSecProvider: () -> Long,
    focus: ChannelsHeaderFocus,
    dateFocus: FocusRequester,
    showTags: Boolean,
    showFilter: Boolean,
    navigationFocus: FocusRequester?,
    contentPadding: PaddingValues,
    startPadding: Dp,
    endPadding: Dp,
    density: Density,
    onDateClick: () -> Unit,
    onFocused: () -> Unit,
    onOpen: (ChannelsPanel) -> Unit,
    onMoveToContent: () -> Boolean,
) {
    ChannelsHeader(
        title = stringResource(R.string.epg_title),
        tag = tag,
        focus = focus,
        showTags = showTags,
        showFilter = showFilter,
        filter = genre,
        filterDot = genreDot,
        navigationFocus = navigationFocus,
        onFocused = onFocused,
        onOpen = onOpen,
        onMoveToContent = onMoveToContent,
        modifier = Modifier
            .padding(
                start = startPadding + TvBrowseLeadingInset,
                top = contentPadding.calculateTopPadding(),
                end = maxOf(endPadding, TvBrowseTrailingInset),
            )
            .heightIn(min = with(density) {
                maxOf(40.dp, MaterialTheme.typography.headlineMedium.lineHeight.toDp(),
                    MaterialTheme.typography.labelLarge.lineHeight.toDp() + 16.dp)
            }),
        leading = { startEdge ->
            GuideDateButton(
                windowStartSec = windowStartSec,
                nowSecProvider = nowSecProvider,
                onClick = onDateClick,
                modifier = startEdge
                    .focusRequester(dateFocus)
                    .onFocusChanged { if (it.isFocused) onFocused() },
            )
        },
    )
}

/**
 * A pill on the header's resting fill naming the Guide's day and time, "Today · 19:30"; OK opens
 * the day and time panel.
 */
@Composable
private fun GuideDateButton(
    windowStartSec: Long,
    nowSecProvider: () -> Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    // The ticking clock is read through a derived day, so the button recomposes only at midnight.
    val today by remember(nowSecProvider, zone) {
        derivedStateOf { Instant.ofEpochSecond(nowSecProvider()).atZone(zone).toLocalDate() }
    }
    val start = Instant.ofEpochSecond(windowStartSec).atZone(zone)
    val day = guideDayLabel(start.toLocalDate(), today, zone, locale)
    val time = formatClock(start, locale)
    val description = stringResource(R.string.epg_date_button_description, day, time)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TvGhostFillAlpha),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .semantics { contentDescription = description }
            .testTag("guide-date-button"),
    ) {
        Text(stringResource(R.string.epg_date_button, day, time), maxLines = 1)
    }
}

/** Today, tomorrow and yesterday are named, other days get their short weekday and date. */
@Composable
internal fun guideDayLabel(date: LocalDate, today: LocalDate, zone: ZoneId, locale: Locale): String =
    when (ChronoUnit.DAYS.between(today, date)) {
        0L -> stringResource(R.string.today)
        1L -> stringResource(R.string.tomorrow)
        -1L -> stringResource(R.string.yesterday)
        else -> formatRecordingDate(
            date.atStartOfDay(zone).toEpochSecond(),
            dateOnly = true,
            locale = locale,
            nowSec = today.atStartOfDay(zone).toEpochSecond(),
        )
    }
