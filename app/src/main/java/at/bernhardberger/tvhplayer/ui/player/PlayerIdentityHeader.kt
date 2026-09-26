package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.common.formatClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderColumnGap
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderFirstBaseline
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderPiconWidth
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderPiconHeight
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderPiconGap
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderPiconTopOffset
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderStatusGap
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderTextGap
import at.bernhardberger.tvhplayer.ui.TvOverlayTextPrimaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import coil3.ImageLoader
import kotlin.math.sign

data class PlayerHeaderTags(
    val picon: String? = null,
    val eyebrow: String? = null,
    val title: String? = null,
    val support: String? = null,
    val clock: String? = null,
    val clockSupport: String? = null,
)

/**
 * What the header identifies. A new [channel] fades the picon and the text; a new
 * [programme] on the same channel crossfades the text only.
 */
data class PlayerHeaderIdentity(val channel: Any?, val programme: Any?)

/**
 * @param identity animates changes of channel and programme; null keeps the header static.
 * @param zapDirection +1 after channel up, -1 after channel down, 0 when the channel was
 * picked directly; the identity text travels in that direction on a channel change.
 */
@Composable
fun PlayerIdentityHeader(
    imageLoader: ImageLoader,
    piconPath: ArtworkId?,
    eyebrow: String?,
    title: String,
    support: String?,
    clock: String,
    clockSupport: String?,
    modifier: Modifier = Modifier,
    currentSession: CurrentSessionObservation? = null,
    tags: PlayerHeaderTags = PlayerHeaderTags(),
    compact: Boolean = false,
    clockStatus: (@Composable () -> Unit)? = null,
    identity: PlayerHeaderIdentity? = null,
    zapDirection: Int = 0,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        val piconModifier = Modifier.padding(top = TvOverlayHeaderPiconTopOffset)
        HeaderIdentityMotion(
            state = HeaderPicon(identity?.channel, piconPath),
            animated = identity != null,
            key = { it.channel },
            channel = { it.channel },
            zapDirection = { 0 },
            modifier = piconModifier,
        ) { picon ->
            PiconBox(
                imageLoader = imageLoader,
                currentSession = currentSession,
                piconPath = picon.path,
                modifier = Modifier
                    .width(TvOverlayHeaderPiconWidth)
                    .height(TvOverlayHeaderPiconHeight)
                    .optionalTestTag(tags.picon),
            )
        }
        Spacer(Modifier.width(TvOverlayHeaderPiconGap))
        val direction by rememberUpdatedState(zapDirection)
        HeaderIdentityMotion(
            state = HeaderIdentityText(identity, eyebrow, title, support),
            animated = identity != null,
            key = { it.identity },
            channel = { it.identity?.channel },
            zapDirection = { direction },
            modifier = Modifier.weight(1f).padding(end = TvOverlayHeaderColumnGap),
        ) { text ->
            HeaderIdentityColumn(text, compact, tags, onSurface)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = clock,
                color = onSurface.copy(alpha = TvOverlayTextSecondaryAlpha),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .optionalTestTag(tags.clock)
                    .paddingFrom(FirstBaseline, before = TvOverlayHeaderFirstBaseline)
            )
            clockStatus?.let {
                Spacer(Modifier.height(TvOverlayHeaderStatusGap))
                it()
            }
            clockSupport?.takeIf { clockStatus == null }?.let {
                Spacer(Modifier.height(TvOverlayHeaderTextGap))
                Text(
                    text = it,
                    color = onSurface.copy(alpha = TvOverlayTextTertiaryAlpha),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.optionalTestTag(tags.clockSupport),
                )
            }
        }
    }
}

private data class HeaderPicon(val channel: Any?, val path: ArtworkId?)

private data class HeaderIdentityText(
    val identity: PlayerHeaderIdentity?,
    val eyebrow: String?,
    val title: String,
    val support: String?,
)

@Composable
private fun HeaderIdentityColumn(
    text: HeaderIdentityText,
    compact: Boolean,
    tags: PlayerHeaderTags,
    onSurface: Color,
) {
    // The first line's baseline shares the clock's anchor.
    val anchor = Modifier.paddingFrom(FirstBaseline, before = TvOverlayHeaderFirstBaseline)
    val title = text.title.takeIf { !compact && it.isNotBlank() }
    val support = text.support.takeIf { !compact }
    Column(Modifier.fillMaxWidth()) {
        text.eyebrow?.let {
            HeaderText(
                text = it,
                color = onSurface.copy(alpha = TvOverlayTextSecondaryAlpha),
                style = HeaderTextStyle.EYEBROW,
                modifier = anchor.optionalTestTag(tags.eyebrow),
            )
        }
        title?.let {
            if (text.eyebrow != null) Spacer(Modifier.height(TvOverlayHeaderTextGap))
            HeaderText(
                text = it,
                color = onSurface.copy(alpha = TvOverlayTextPrimaryAlpha),
                style = HeaderTextStyle.TITLE,
                modifier = Modifier
                    .then(if (text.eyebrow == null) anchor else Modifier)
                    .fillMaxWidth()
                    .optionalTestTag(tags.title)
                    .semantics { heading() },
            )
        }
        support?.let {
            if (text.eyebrow != null || title != null) Spacer(Modifier.height(TvOverlayHeaderTextGap))
            HeaderText(
                text = it,
                color = onSurface.copy(alpha = TvOverlayTextTertiaryAlpha),
                style = HeaderTextStyle.SUPPORT,
                modifier = Modifier
                    .then(if (text.eyebrow == null && title == null) anchor else Modifier)
                    .optionalTestTag(tags.support),
            )
        }
    }
}

/**
 * Crossfades header identity when [key] changes: in over 150 ms, out over 100 ms, size
 * snapping. On a channel change the incoming and outgoing content also travel 8 dp
 * in [zapDirection] (up for +1). Outgoing content leaves semantics at once, so its
 * tags never duplicate the incoming ones. Content with the same key updates in place.
 */
@Composable
private fun <S : Any> HeaderIdentityMotion(
    state: S,
    animated: Boolean,
    key: (S) -> Any?,
    channel: (S) -> Any?,
    zapDirection: () -> Int,
    modifier: Modifier,
    content: @Composable (S) -> Unit,
) {
    if (!animated) {
        Box(modifier) { content(state) }
        return
    }
    val transition = updateTransition(state, label = "player-header-identity")
    transition.AnimatedContent(
        modifier = modifier,
        contentKey = key,
        transitionSpec = {
            (fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate)) togetherWith
                fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate))).using(null)
        },
    ) { shown ->
        val travel = animateShown(
            enter = tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate),
            exit = tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate),
            label = "player-header-zap",
        )
        val leaving = leaving
        Box(
            Modifier
                .semanticsWhileShown(!leaving)
                .graphicsLayer {
                    val direction = zapDirection().sign
                    val zapped = channel(transition.currentState) != channel(transition.targetState)
                    translationY = if (direction != 0 && zapped) {
                        // Up for channel up: incoming rises from below, outgoing rises away.
                        val side = if (leaving) -1f else 1f
                        side * direction * PlayerMotion.ZapTextOffset.toPx() * (1f - travel.value)
                    } else 0f
                },
        ) { content(shown) }
    }
}

private enum class HeaderTextStyle { EYEBROW, TITLE, SUPPORT }

@Composable
internal fun endedProgrammeSupport(event: EpgEvent?, serverNowSec: Long): String? =
    event?.takeIf { it.stop.epochSeconds <= serverNowSec }?.let {
        stringResource(R.string.player_programme_ended_at, formatClock(it.stop.epochSeconds))
    }

@Composable
private fun HeaderText(
    text: String,
    color: Color,
    style: HeaderTextStyle,
    modifier: Modifier,
) {
    Text(
        text = text,
        color = color,
        style = when (style) {
            HeaderTextStyle.EYEBROW -> MaterialTheme.typography.titleMedium
            HeaderTextStyle.TITLE -> MaterialTheme.typography.headlineMedium
            HeaderTextStyle.SUPPORT -> MaterialTheme.typography.labelLarge
        },
        maxLines = if (style == HeaderTextStyle.TITLE) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private fun Modifier.optionalTestTag(tag: String?): Modifier =
    then(tag?.let { Modifier.testTag(it) } ?: Modifier)
