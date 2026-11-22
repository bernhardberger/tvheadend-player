package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvCompactProgressStripHeight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackMarker
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.AppChannelNumbers
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.progressColor
import at.bernhardberger.tvhplayer.ui.components.programmeProgress
import at.bernhardberger.tvhplayer.ui.components.watchingIndicator
import at.bernhardberger.tvhplayer.ui.components.isPlayingItem
import at.bernhardberger.tvhplayer.ui.components.liveViewerEpochSec
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import kotlin.math.pow

/** Passive identity of the playing channel, independent of the programme being browsed. */
@Composable
internal fun NowPlayingStrip(
    channelId: ChannelId,
    channelNumber: String?,
    channelName: String,
    picon: ChannelIcon?,
    programme: EpgEvent?,
    nowSec: Long,
    modifier: Modifier = Modifier,
    playbackIndicator: ChannelPlaybackIndicator = ChannelPlaybackIndicator.NONE,
    programmeAt: (Long) -> EpgEvent? = { null },
) {
    val at = liveViewerEpochSec(channelId, nowSec)
    val displayed = if (at != null && at < (programme?.start?.epochSeconds ?: nowSec))
        programmeAt(at) ?: programme else programme
    val current = displayed?.takeIf { it.isRunning(nowSec) || (at != null && it.isRunning(at)) }
    val rail = LocalPlayerRailHandoff.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val alphas = StripAlphas(rail, LocalPlayerPageScrim.current)
    val plateMotion = if (rail == null) Modifier else Modifier.graphicsLayer {
        railIdentityTransform(rail, RailLogoTravel, size.height / 2f, logo = true, fromStrip = true, rtl = rtl)
    }
    Row(
        modifier.stripBaseline(rail).testTag("player-now-playing").semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NowPlayingPlateGap),
    ) {
        NowPlayingPlate(picon, channelNumber, modifier = plateMotion, trimIdentityPicon = rail != null,
            plateAlpha = alphas.surround, markAlpha = if (picon == null) alphas.surround else alphas.identity,
            decorativeNumber = true) {
            val progress = programmeProgress(current, nowSec)
            if (progress != null) ProgressStrip(
                progress = progress,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().stripAlpha(alphas.surround),
                height = TvCompactProgressStripHeight,
                color = progressColor(watchingIndicator(current, nowSec).isPlayingItem),
            )
        }
        Column(Modifier.widthIn(max = 200.dp).width(IntrinsicSize.Max).stripLabelTravel(rail, picon != null, rtl)) {
            // The playing card's marker: this strip is what plays, not what the rail or details show.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(channelTitleText(channelNumber?.toLongOrNull(), channelName),
                    Modifier.weight(1f, fill = false).stripAlpha(alphas.identity),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.stripAlpha(alphas.caption)) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        ChannelPlaybackMarker(playbackIndicator, size = 16.dp)
                    }
                }
            }
            Text(
                current?.title.orEmpty(), modifier = Modifier.stripAlpha(alphas.caption),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal val NowPlayingPlateWidth = 48.dp
internal val NowPlayingPlateHeight = 27.dp
internal val NowPlayingPlateGap = 12.dp
private val StripChannelBaseline = HorizontalAlignmentLine(::minOf)

private class StripAlphas(rail: QuickZapRailState?, scrim: PlayerPageScrim?) {
    private val strip = {
        val chrome = if (rail?.stripFollowsChromeAlpha == true) scrim?.alpha?.invoke() ?: 1f else 1f
        chrome * (rail?.stripPresence?.value ?: 1f)
    }
    val surround = { (rail?.expansion?.value ?: 1f) * strip() }
    val identity = {
        val e = rail?.expansion?.value ?: 1f
        val handoff = if (rail?.morphIdentity == true) ((e - StripArrival) / StripHandoffWindow).coerceIn(0f, 1f) else e
        handoff * strip()
    }
    val caption = {
        val e = rail?.expansion?.value ?: 1f
        val reveal = if (rail?.morphIdentity == true) ((3f * e - 1f) / 2f).coerceIn(0f, 1f).pow(1.5f) else e
        reveal * strip()
    }
}

private fun Modifier.stripBaseline(rail: QuickZapRailState?): Modifier {
    if (rail == null) return this
    return layout { measurable, constraints ->
        val row = measurable.measure(constraints)
        val baseline = row[StripChannelBaseline] - row.height / 2f
        layout(row.width, row.height) {
            rail.stripBaselineFromCenter = baseline
            row.placeRelative(0, 0)
        }
    }
}

private fun Modifier.stripLabelTravel(rail: QuickZapRailState?, hasPicon: Boolean, rtl: Boolean): Modifier {
    if (rail == null) return this
    return layout { measurable, constraints ->
        val column = measurable.measure(constraints)
        val baseline = column[FirstBaseline]
        layout(column.width, column.height, mapOf(StripChannelBaseline to baseline)) {
            column.placeRelativeWithLayer(0, 0) {
                railIdentityTransform(rail, railTextTravel(hasPicon), baseline.toFloat(),
                    logo = false, fromStrip = true, rtl = rtl)
            }
        }
    }
}

private fun Modifier.stripAlpha(read: () -> Float) = graphicsLayer {
    alpha = read()
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/**
 * The playing channel's logo plate, or its number/placeholder without a logo. The plate carries [strip], the
 * progress strip; without it the strip would float under the logo. [contentAlpha] dims only the mark.
 */
@Composable
internal fun NowPlayingPlate(
    picon: ChannelIcon?,
    channelNumber: String?,
    modifier: Modifier = Modifier,
    contentAlpha: Float = 1f,
    trimIdentityPicon: Boolean = false,
    plateAlpha: (() -> Float)? = null,
    markAlpha: (() -> Float)? = null,
    decorativeNumber: Boolean = false,
    strip: @Composable BoxScope.() -> Unit = {},
) {
    val plate = if (plateAlpha == null) Modifier.background(TvSurfaceColors.containerLowest)
        else Modifier.drawBehind { drawRect(TvSurfaceColors.containerLowest.copy(alpha = plateAlpha())) }
    Box(modifier.size(NowPlayingPlateWidth, NowPlayingPlateHeight).clip(PlayerChromeTokens.chipShape).then(plate),
        contentAlignment = Alignment.Center) {
        val mark = when {
            markAlpha != null -> Modifier.stripAlpha { contentAlpha * markAlpha() }
            contentAlpha < 1f -> Modifier.graphicsLayer { alpha = contentAlpha }
            else -> Modifier
        }
        if (picon != null && trimIdentityPicon) {
            // Ink the same size/ground as the identity, then scale the painted result: one cache key.
            IdentityPicon(picon, Modifier.requiredSize(ChipWidth, ChipHeight).then(mark).graphicsLayer {
                scaleX = StripIdentityScale
                scaleY = StripIdentityScale
            })
        } else if (picon != null || !AppChannelNumbers.shown) PiconBox(picon, mark.padding(3.dp))
        else Text(channelNumber.orEmpty(), if (decorativeNumber) mark.clearAndSetSemantics {} else mark,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        strip()
    }
}
