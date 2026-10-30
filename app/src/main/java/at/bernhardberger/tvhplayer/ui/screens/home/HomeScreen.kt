package at.bernhardberger.tvhplayer.ui.screens.home

import at.bernhardberger.tvhplayer.ui.components.LocalBrowseDrawerState
import androidx.tv.material3.DrawerValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvBrowseTrailingInset
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.ui.components.RoundIconButton
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
import java.time.Instant
import java.time.ZoneId

/**
 * Home's landing page, still only its header: no heading, the quiet date and wall clock at the
 * end, and View options as the one working control. The header is part of the page rather than a
 * fixed bar, so the content rows added next scroll it away. Its button takes the initial focus,
 * which keeps the screen's first stop deterministic, and the start edge leads to the rail.
 */
@Composable
internal fun HomeScreen(
    contentPadding: PaddingValues,
    initialFocusEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val ltr = layoutDirection == LayoutDirection.Ltr
    val rail = LocalBrowseNavigationFocus.current ?: FocusRequester.Default
    val viewFocus = remember { FocusRequester() }
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    val nowSec = rememberCurrentEpochSeconds()()
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val drawerState = LocalBrowseDrawerState.current
    val focusEnabled = rememberUpdatedState(initialFocusEnabled)
    LaunchedEffect(initialFocusEnabled) {
        if (!initialFocusEnabled) return@LaunchedEffect
        // Compose has measured the header by the next frame, so the request lands on the button.
        withFrameNanos { }
        // Widget focus changes precede composition's drawerActive feedback.
        // A queued initial focus must not take focus back from an opening drawer.
        if (drawerState?.currentValue == DrawerValue.Open || !focusEnabled.value) return@LaunchedEffect
        runCatching { viewFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        TopLevelBrowseHeader(
            title = stringResource(R.string.nav_home),
            modifier = Modifier
                .padding(
                    start = startPadding + TvBrowseLeadingInset,
                    end = maxOf(endPadding, TvBrowseTrailingInset),
                )
                .heightIn(min = TvBrowseHeaderHeight)
                .testTag("home-header"),
            actions = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TvSpacing8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatRecordingDate(nowSec, dateOnly = true, locale = locale),
                        style = MaterialTheme.typography.titleMedium,
                        color = TvTextColors.tertiary,
                        maxLines = 1,
                    )
                    Text(
                        text = formatClock(Instant.ofEpochSecond(nowSec).atZone(zone), locale),
                        style = MaterialTheme.typography.titleMedium,
                        color = TvTextColors.tertiary,
                        maxLines = 1,
                    )
                }
                RoundIconButton(
                    icon = painterResource(R.drawable.ic_tune),
                    label = stringResource(R.string.channels_view_options),
                    onClick = { },
                    focusRequester = viewFocus,
                    onFocused = { },
                    modifier = Modifier
                        .focusProperties {
                            up = FocusRequester.Cancel
                            // Nothing lies below the header yet; the rows arrive in a later slice.
                            down = FocusRequester.Cancel
                            if (ltr) {
                                left = rail
                                right = FocusRequester.Cancel
                            } else {
                                right = rail
                                left = FocusRequester.Cancel
                            }
                        }
                        .testTag("home-view-button"),
                )
            },
        )
    }
}
