// The parts a recording's and a program's details pages share; sizes are the drawings' (round 37).
@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R

@Composable
internal fun DetailsBackButton(label: String, onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.connect_back_outline), label)
    }
}

/** A channel's logo on its tinted tile, as the Channels list draws it; the caller gives the size. */
@Composable
internal fun ChannelLogoTile(
    logo: Painter?,
    tint: Color?,
    modifier: Modifier,
    shape: RoundedCornerShape = LogoShape,
    /** The server logo's image model, drawn when [logo] is null; the no-logo icon stands in until it loads. */
    model: Any? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val hasLogo = logo != null || model != null
    val accent = if (tint == null) rememberChannelAccent(model) else null
    val fill = if (!hasLogo || (tint == null && accent == null)) {
        Modifier.background(scheme.surfaceContainerHigh)
    } else {
        Modifier.drawBehind {
            val ground = tint ?: accent?.animated?.value ?: scheme.surfaceContainerHigh
            drawRect(Brush.linearGradient(listOf(ground, lerp(ground, Color.Black, LOGO_SHADE))))
        }
    }
    Box(
        modifier.clip(shape).then(fill).border(1.dp, scheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        ChannelLogoImage(logo, model) {
            Icon(painterResource(R.drawable.play_tv_outline), null, tint = scheme.onSurfaceVariant)
        }
    }
}

/**
 * A channel's logo fitted, whatever its shape, into the same share of the slot it stands in, centered by the
 * caller's Box (section 13); [fallback] stands in while a server logo loads or when it fails.
 */
@Composable
internal fun ChannelLogoImage(logo: Painter?, model: Any?, fallback: @Composable () -> Unit = {}) {
    ArtworkImage(
        logo, model, Modifier.fillMaxWidth(LOGO_WIDTH_SHARE).fillMaxHeight(LOGO_HEIGHT_SHARE), fallback = fallback,
    )
}

/** The scrim over a page's top picture: it keeps the bar readable and fades the picture into the page. */
@Composable
internal fun DetailsPictureScrim(modifier: Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.55f), 0.3f to Color.Transparent,
                0.5f to Color.Transparent, 1f to MaterialTheme.colorScheme.surface,
            ),
        ),
    )
}

@Composable
internal fun DetailsEpisode(text: String?) {
    text ?: return
    Text(
        text, Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun DetailsSynopsis(text: String?, modifier: Modifier) {
    text ?: return
    Text(
        text, modifier, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun DetailsSectionTitle(title: String) {
    Text(
        title, Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

/** A titled table of facts, filled column by column; nothing is drawn without facts. */
@Composable
internal fun DetailsFacts(title: String, facts: List<Pair<String, String>>, columns: Int, keyWidth: Dp) {
    if (facts.isEmpty()) return
    DetailsSectionTitle(title)
    val rows = (facts.size + columns - 1) / columns
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        facts.chunked(rows).forEach { column ->
            Column(Modifier.weight(1f)) {
                column.forEach { (key, value) -> Fact(key, value, keyWidth) }
            }
        }
        repeat(columns - (facts.size + rows - 1) / rows) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun Fact(key: String, value: String, keyWidth: Dp) {
    Row(Modifier.heightIn(min = 28.dp).padding(vertical = 4.dp)) {
        Text(
            key, Modifier.width(keyWidth), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val LogoShape = RoundedCornerShape(12.dp)
private const val LOGO_SHADE = 0.35f
private const val LOGO_WIDTH_SHARE = 0.76f
private const val LOGO_HEIGHT_SHARE = 0.62f
