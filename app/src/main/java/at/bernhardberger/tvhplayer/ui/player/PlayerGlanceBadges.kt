package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.GlanceBadge
import at.bernhardberger.tvhplayer.core.GlanceBadgeKind
import at.bernhardberger.tvhplayer.ui.components.StatusBadge
import at.bernhardberger.tvhplayer.ui.components.StatusBadgeTone

@Composable
internal fun glanceBadgeSpoken(badge: GlanceBadge): String = when (badge.kind) {
    GlanceBadgeKind.RASTER -> stringResource(R.string.player_raster_spoken, badge.value)
    GlanceBadgeKind.AUDIO -> stringResource(R.string.player_audio_spoken, badge.value)
    GlanceBadgeKind.AD -> stringResource(R.string.audio_description)
}

@Composable
internal fun PlayerGlanceBadges(badges: List<GlanceBadge>, modifier: Modifier = Modifier) {
    // The same gap as adjacent RecordingBadge status badges.
    Row(modifier.testTag("player-stream-badges").clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        badges.forEach { badge ->
            StatusBadge(
                if (badge.kind == GlanceBadgeKind.AD) stringResource(R.string.player_ad_short) else badge.value,
                StatusBadgeTone.NEUTRAL,
                contentDescription = null,
                compact = true,
            )
        }
    }
}
