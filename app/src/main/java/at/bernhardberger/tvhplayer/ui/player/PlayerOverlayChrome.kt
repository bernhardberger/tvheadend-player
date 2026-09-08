package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTopPadding

internal val playerChannelsCueHeight
    @Composable @ReadOnlyComposable get() = with(LocalDensity.current) {
        maxOf(24.dp, MaterialTheme.typography.labelLarge.lineHeight.toDp())
    }

/** Same bottom anchor as the composed action band, without hidden focusable controls. */
@Composable
@ReadOnlyComposable
internal fun playerSeekPreviewBottomPadding(channelsAvailable: Boolean) =
    TvOverlayBottomPadding + TvOverlayActionButtonSize + 16.dp +
        if (channelsAvailable) playerChannelsCueHeight else 0.dp

@Composable
internal fun PlayerOverlayChrome(
    headerContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    footerPadding: PaddingValues = PaddingValues(
        start = TvOverlaySidePadding, end = TvOverlaySidePadding,
        top = TvOverlayFooterGradientRunout, bottom = TvOverlayBottomPadding,
    ),
    footerContent: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        headerContent(
            Modifier
                .align(Alignment.TopCenter)
                .background(topGradient)
                .padding(
                    start = TvOverlaySidePadding,
                    end = TvOverlaySidePadding,
                    top = TvOverlayTopPadding,
                    bottom = TvOverlayHeaderGradientRunout,
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(bottomGradient)
                .padding(footerPadding),
            content = footerContent,
        )
    }
}
