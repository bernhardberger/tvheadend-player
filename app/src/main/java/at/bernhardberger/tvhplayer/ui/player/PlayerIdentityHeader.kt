package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderMinHeight
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderPiconGap
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderPiconHeight
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderPiconWidth
import at.bernhardberger.tvhplayer.ui.TvOverlayTextPrimaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import coil3.ImageLoader

data class PlayerHeaderTags(
    val picon: String? = null,
    val eyebrow: String? = null,
    val title: String? = null,
    val support: String? = null,
    val clock: String? = null,
    val clockSupport: String? = null,
)

@Composable
fun PlayerIdentityHeader(
    imageLoader: ImageLoader,
    piconPath: String?,
    eyebrow: String?,
    title: String,
    support: String?,
    clock: String,
    clockSupport: String?,
    modifier: Modifier = Modifier,
    currentSession: CurrentSessionObservation? = null,
    tags: PlayerHeaderTags = PlayerHeaderTags(),
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = TvOverlayHeaderMinHeight),
        verticalAlignment = Alignment.Top,
    ) {
        PiconBox(
            imageLoader = imageLoader,
            currentSession = currentSession,
            piconPath = piconPath,
            modifier = Modifier
                .width(TvOverlayHeaderPiconWidth)
                .height(TvOverlayHeaderPiconHeight)
                .optionalTestTag(tags.picon),
        )
        Spacer(Modifier.width(TvOverlayHeaderPiconGap))
        Column(Modifier.weight(1f).padding(end = TvOverlayHeaderColumnGap)) {
            eyebrow?.let {
                HeaderText(
                    text = it,
                    color = onSurface.copy(alpha = TvOverlayTextSecondaryAlpha),
                    style = HeaderTextStyle.EYEBROW,
                    modifier = Modifier
                        .optionalTestTag(tags.eyebrow)
                        .paddingFrom(FirstBaseline, before = TvOverlayHeaderFirstBaseline)
                )
            }
            HeaderText(
                text = title,
                color = onSurface.copy(alpha = TvOverlayTextPrimaryAlpha),
                style = HeaderTextStyle.TITLE,
                modifier = Modifier
                    .optionalTestTag(tags.title)
                    .semantics { heading() }
                    .then(
                        if (eyebrow == null) {
                            Modifier.paddingFrom(
                                FirstBaseline,
                                before = TvOverlayHeaderFirstBaseline,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
            support?.let {
                HeaderText(
                    text = it,
                    color = onSurface.copy(alpha = TvOverlayTextTertiaryAlpha),
                    style = HeaderTextStyle.SUPPORT,
                    modifier = Modifier.optionalTestTag(tags.support),
                )
            }
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
            clockSupport?.let {
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

private enum class HeaderTextStyle { EYEBROW, TITLE, SUPPORT }

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
