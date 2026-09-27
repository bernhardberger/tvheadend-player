package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.GlanceBadge
import at.bernhardberger.tvhplayer.core.GlanceBadgeKind
import at.bernhardberger.tvhplayer.ui.TvPanelBrowseAlpha
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

@Composable
internal fun badgeLabel(badge: GlanceBadge, spoken: Boolean): String = when (badge.kind) {
    GlanceBadgeKind.AD -> stringResource(if (spoken) R.string.trial_ad else R.string.trial_ad_short)
    GlanceBadgeKind.SUB -> stringResource(if (spoken) R.string.trial_sub else R.string.trial_sub_short)
    GlanceBadgeKind.TXT -> stringResource(if (spoken) R.string.trial_txt else R.string.trial_txt_short)
    GlanceBadgeKind.SNR_PERCENT -> stringResource(if (spoken) R.string.trial_snr_percent_spoken else R.string.trial_snr_percent, badge.value)
    GlanceBadgeKind.SNR_DB -> stringResource(if (spoken) R.string.trial_snr_db_spoken else R.string.trial_snr_db, badge.value)
    GlanceBadgeKind.RASTER -> if (spoken) stringResource(R.string.trial_raster_spoken, badge.value) else badge.value
    GlanceBadgeKind.VIDEO -> if (spoken) stringResource(R.string.trial_video_spoken, badge.value) else badge.value
    GlanceBadgeKind.AUDIO -> if (spoken) stringResource(R.string.trial_audio_spoken, badge.value) else badge.value
}

/** Measures the localized, scaled text before composing rows, so omitted badges have no semantics. */
@Composable
fun PlayerGlanceBadges(badges: List<GlanceBadge>, modifier: Modifier = Modifier) {
    val labels = badges.map { badgeLabel(it, false) }
    val spoken = badges.map { badgeLabel(it, true) }
    val style = MaterialTheme.typography.labelMedium
    val measure = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomEnd) {
        val gap = with(density) { PlayerTrialTokens.badgeGap.roundToPx() }
        val padding = with(density) { 8.dp.roundToPx() }
        val widths = labels.map { measure.measure(AnnotatedString(it), style, maxLines = 1).size.width + padding }
        val rows = at.bernhardberger.tvhplayer.core.glanceBadgeRows(widths, constraints.maxWidth, gap)
        val description = rows.flatten().joinToString(", ") { spoken[it] }
        Column(Modifier.clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PlayerTrialTokens.badgeGap)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(PlayerTrialTokens.badgeGap)) {
                    row.forEach { index ->
                        Box(Modifier.background(TvSurfaceColors.containerHigh.copy(alpha = TvPanelBrowseAlpha), PlayerTrialTokens.chipShape)
                            .heightIn(min = PlayerTrialTokens.badgeHeight).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Text(labels[index], style = style, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
