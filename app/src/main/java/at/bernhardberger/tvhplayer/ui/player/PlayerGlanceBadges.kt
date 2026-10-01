package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.GlanceBadge
import at.bernhardberger.tvhplayer.core.GlanceBadgeKind

@Composable
internal fun badgeLabel(badge: GlanceBadge, spoken: Boolean): String = when (badge.kind) {
    GlanceBadgeKind.AD -> stringResource(if (spoken) R.string.player_ad else R.string.player_ad_short)
    GlanceBadgeKind.SUB -> stringResource(if (spoken) R.string.player_sub else R.string.player_sub_short)
    GlanceBadgeKind.TXT -> stringResource(if (spoken) R.string.player_txt else R.string.player_txt_short)
    GlanceBadgeKind.RASTER -> if (spoken) stringResource(R.string.player_raster_spoken, badge.value) else badge.value
    GlanceBadgeKind.VIDEO -> if (spoken) stringResource(R.string.player_video_spoken, badge.value) else badge.value
    GlanceBadgeKind.AUDIO -> if (spoken) stringResource(R.string.player_audio_spoken, badge.value) else badge.value
}

/** One row of outlined tags, like an age rating. Every badge is shown; the host gives the row its width first. */
@Composable
fun PlayerGlanceBadges(badges: List<GlanceBadge>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    val description = badges.map { badgeLabel(it, true) }.joinToString(", ")
    val color = MaterialTheme.colorScheme.onSurface
    Row(modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(PlayerChromeTokens.badgeGap), verticalAlignment = Alignment.CenterVertically) {
        badges.forEach { badge ->
            Box(Modifier.border(1.dp, color.copy(alpha = 0.64f), PlayerChromeTokens.badgeShape)
                .height(PlayerChromeTokens.badgeHeight).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                Text(badgeLabel(badge, false), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    softWrap = false, color = color.copy(alpha = 0.88f))
            }
        }
    }
}
