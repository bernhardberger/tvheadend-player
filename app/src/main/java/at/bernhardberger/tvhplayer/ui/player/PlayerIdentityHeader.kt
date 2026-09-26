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
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.common.formatClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import coil3.ImageLoader

data class PlayerHeaderTags(
    val picon: String? = null,
    val eyebrow: String? = null,
    val title: String? = null,
    val support: String? = null,
    val clock: String? = null,
    val clockSupport: String? = null,
)

/**
 * What the header identifies. A new [channel] crossfades the picon, the text and the
 * status under the clock in place; a new [programme] on the same channel crossfades
 * the text only.
 */
data class PlayerHeaderIdentity(val channel: Any?, val programme: Any?)

/** What the status row under the clock shows; see [PlayerStatusTags]. */
data class PlayerHeaderStatus(
    val paused: Boolean,
    val timeshift: AppTimeshiftState = AppTimeshiftState(),
    val recordingPlayback: Boolean = false,
    val growing: Boolean = false,
    val recordingNow: Boolean = false,
    val playbackPresented: Boolean = true,
)

/**
 * @param identity animates changes of channel and programme; null keeps the header static.
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
    status: PlayerHeaderStatus? = null,
    identity: PlayerHeaderIdentity? = null,
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
        HeaderIdentityMotion(
            state = HeaderIdentityText(identity, eyebrow, title, support),
            animated = identity != null,
            key = { it.identity },
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
            // The clock keeps time across a zap; the status under it belongs to the channel.
            HeaderIdentityMotion(
                state = HeaderClockStatus(identity?.channel, status, clockSupport),
                animated = identity != null,
                key = { it.channel },
                modifier = Modifier,
                contentAlignment = Alignment.TopEnd,
            ) { shown ->
                Column(horizontalAlignment = Alignment.End) {
                    shown.status?.let {
                        Spacer(Modifier.height(TvOverlayHeaderStatusGap))
                        PlayerStatusTags(
                            paused = it.paused,
                            timeshift = it.timeshift,
                            recordingPlayback = it.recordingPlayback,
                            growing = it.growing,
                            recordingNow = it.recordingNow,
                            playbackPresented = it.playbackPresented,
                        )
                    }
                    shown.support?.takeIf { shown.status == null }?.let {
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
    }
}

private data class HeaderPicon(val channel: Any?, val path: ArtworkId?)

/** Values only, so an outgoing copy keeps showing its own channel's status as it fades. */
private data class HeaderClockStatus(
    val channel: Any?,
    val status: PlayerHeaderStatus?,
    val support: String?,
)

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
 * Crossfades header identity in place when [key] changes: in over 150 ms, out over
 * 100 ms, size snapping, no travel, so a zap keeps the header layout still. Outgoing
 * content leaves semantics at once, so its tags never duplicate the incoming ones.
 * Content with the same key updates in place.
 */
@Composable
private fun <S : Any> HeaderIdentityMotion(
    state: S,
    animated: Boolean,
    key: (S) -> Any?,
    modifier: Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (S) -> Unit,
) {
    if (!animated) {
        Box(modifier) { content(state) }
        return
    }
    val transition = updateTransition(state, label = "player-header-identity")
    transition.AnimatedContent(
        modifier = modifier,
        contentAlignment = contentAlignment,
        contentKey = key,
        transitionSpec = {
            (fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate)) togetherWith
                fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate))).using(null)
        },
    ) { shown ->
        Box(Modifier.semanticsWhileShown(!leaving)) { content(shown) }
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
