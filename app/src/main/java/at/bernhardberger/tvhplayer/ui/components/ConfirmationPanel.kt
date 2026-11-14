package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.isUsableAccent
import at.bernhardberger.tvhplayer.core.normalizeAccentRgb
import at.bernhardberger.tvhplayer.ui.TvCardProgressStripHeight
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors

@Composable
internal fun ConfirmationPanel(subject: ConfirmationSubject, styles: ConfirmationStyles) {
    val glow = when (subject) {
        is ConfirmationSubject.Programme -> rememberDetailsTint(subject.image, subject.channelIcon,
            tintRgb = { seed -> if (isUsableAccent(seed)) normalizeAccentRgb(seed) else null },
            neutral = Color.Transparent).value
        else -> Color.Transparent
    }
    val description = confirmationSubjectDescription(subject)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TvSurfaceColors.containerHigh)
        .drawBehind { drawLeadGlow(glow, (PanelPadding + subject.leadWidth() / 2).toPx()) }
        .padding(PanelPadding).clearAndSetSemantics { contentDescription = description }
        .testTag("confirmation-subject"),
        verticalAlignment = Alignment.CenterVertically) {
        when (subject) {
            is ConfirmationSubject.Programme -> ProgrammeLead(subject)
            is ConfirmationSubject.Folder -> ObjectLead(R.drawable.ic_folder)
            is ConfirmationSubject.Rule -> ObjectLead(subject.icon)
            is ConfirmationSubject.Server -> ServerAvatar(subject.look, ObjectLeadSize)
        }
        Spacer(Modifier.width(PanelTextGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PanelText(subject, styles)
        }
    }
}

@Composable
private fun ObjectLead(icon: Int) {
    Box(Modifier.size(ObjectLeadSize), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(40.dp), tint = TvTextColors.secondary)
    }
}

/** The accent's radial glow behind the leading element: an ellipse 1.5 panels wide and 300 dp tall. */
private fun DrawScope.drawLeadGlow(color: Color, centreX: Float) {
    if (color.alpha == 0f) return
    val radiusX = size.width * .75f
    val radiusY = 150.dp.toPx()
    val centre = Offset(centreX, size.height / 2)
    scale(1f, radiusY / radiusX, pivot = centre) {
        drawCircle(Brush.radialGradient(
            0f to color, .25f to color.copy(alpha = color.alpha * .65f), .5f to color.copy(alpha = color.alpha * .35f),
            .75f to color.copy(alpha = color.alpha * .125f), 1f to color.copy(alpha = 0f),
            center = centre, radius = radiusX,
        ), radius = radiusX, center = centre)
    }
}

@Composable
private fun ProgrammeLead(subject: ConfirmationSubject.Programme) {
    val slot = Modifier.size(ProgrammeSlotWidth, ProgrammeSlotHeight)
    if (subject.image == null && subject.channelIcon == null) {
        Box(slot.clip(MaterialTheme.shapes.small).background(TvSurfaceColors.containerLowest),
            contentAlignment = Alignment.Center) {
            channelTitleNumber(subject.channelNumber, subject.channelName)?.let {
                Text(it.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
                    color = TvTextColors.secondary, maxLines = 1)
            }
            subject.progress?.let {
                ProgressStrip(it, Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                    height = TvCardProgressStripHeight, color = recordingMarkColor(focused = false))
            }
        }
    } else {
        // A picture leaves the logo to the channel line, so the slot keeps no corner plate.
        ProgrammeArtwork(subject.image, subject.channelIcon.takeIf { subject.image == null }, subject.progress,
            slot, progressColor = recordingMarkColor(focused = false))
    }
}

@Composable
private fun PanelText(subject: ConfirmationSubject, styles: ConfirmationStyles) {
    val name = confirmationSubjectName(subject)
    Text(name, style = styles.name, color = MaterialTheme.colorScheme.onSurface, maxLines = 2,
        overflow = TextOverflow.Ellipsis)
    when (subject) {
        is ConfirmationSubject.Programme -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                subject.inlineLogo()?.let {
                    InlineLogo(it, Modifier)
                    Spacer(Modifier.width(8.dp))
                }
                Text(channelTitleText(subject.channelNumber, subject.channelName), Modifier.weight(1f, fill = false),
                    style = styles.channel, color = TvTextColors.secondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            PanelRow(subject.airTime, styles.row)
            subject.target?.let { PanelRow(it, styles.row) }
        }
        is ConfirmationSubject.Folder -> PanelRow(subject.detail, styles.row)
        is ConfirmationSubject.Rule -> PanelRow(subject.detail, styles.row, maxLines = 2)
        is ConfirmationSubject.Server -> PanelRow(subject.detail, styles.row)
    }
}

@Composable
private fun PanelRow(text: String, style: TextStyle, maxLines: Int = 1) {
    if (text.isNotBlank()) Text(text, style = style, color = TvTextColors.secondary, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis)
}

private fun confirmationSubjectDescription(subject: ConfirmationSubject): String = when (subject) {
    is ConfirmationSubject.Programme -> listOfNotNull(subject.name,
        channelTitleText(subject.channelNumber, subject.channelName), subject.airTime, subject.target)
    is ConfirmationSubject.Folder -> listOf(subject.name, subject.detail)
    is ConfirmationSubject.Rule -> listOf(subject.name, subject.detail)
    is ConfirmationSubject.Server -> listOf(subject.name, subject.detail)
}.filter(String::isNotBlank).joinToString(", ")

/** The panel's leading element width: the programme slot, or the folder icon / server avatar box. */
internal fun ConfirmationSubject.leadWidth() =
    if (this is ConfirmationSubject.Programme) ProgrammeSlotWidth else ObjectLeadSize

/** The slot shows the programme's picture, so its channel line carries the logo instead. */
internal fun ConfirmationSubject.Programme.inlineLogo(): ChannelIcon? = channelIcon?.takeIf { image != null }

internal val PanelPadding = 12.dp
internal val PanelTextGap = 16.dp
private val ProgrammeSlotWidth = 128.dp
private val ProgrammeSlotHeight = 72.dp
private val ObjectLeadSize = 48.dp
