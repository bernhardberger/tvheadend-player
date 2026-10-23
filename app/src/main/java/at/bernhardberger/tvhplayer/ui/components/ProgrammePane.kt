package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.ui.TvGridColumnWidth
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.player.programmeFactsSegments

/**
 * The pane beside a list is the kit's four-column card, 268 by 151, and the headline below keeps
 * to its width: one column, as JetFit's details card, rather than a picture over wider text.
 */
internal val ProgrammePaneWidth = TvGridColumnWidth * 4 + TvGridGutter * 3

/** Between the pane's picture and the headline hanging from it: the kit's content gap. */
internal val ProgrammePanePictureGap = 20.dp

/** The kit's gap between the title group and the description. */
internal val ProgrammeHeadlineSynopsisGap = 20.dp

/** Between the facts and time lines of the headline. */
internal val ProgrammeHeadlineLineGap = 8.dp

/** The facts line, as tall as its outlined age rating. */
internal val ProgrammeHeadlineFactsLineHeight = 22.dp

/** The logo's room in the pane's picture: as wide as on the logo cards, half the picture tall. */
private const val PictureLogoWidthFraction = 0.6f
private const val PictureLogoHeightFraction = 0.5f

/**
 * The pane's picture, one size and place whatever the headline below holds: the programme's
 * [art], else the [logo] on the logo's ambient colour, when the channel ([accent]) is known.
 * The eyebrow below already names the channel, so the artwork goes without its logo plate.
 */
@Composable
internal fun ProgrammePanePicture(
    art: ArtworkId?,
    logo: ArtworkId?,
    accent: ChannelId?,
    logoTag: String,
    modifier: Modifier = Modifier,
) {
    if (art != null && LocalArtworkContext.current.currentSession != null) {
        ProgrammeArtwork(art, channelIcon = null, progress = null, modifier)
    } else {
        // The logo cards' ambient colour; with no text on it, it keeps its strength to the bottom.
        Box(modifier.clip(MaterialTheme.shapes.small).background(TvSurfaceColors.containerLowest)) {
            if (accent != null) ChannelAccentFill(logo, accent, Modifier.fillMaxSize(), bottomShare = 1f)
            PiconBox(logo,
                Modifier.align(Alignment.Center).fillMaxWidth(PictureLogoWidthFraction).fillMaxHeight(PictureLogoHeightFraction)
                    .testTag(logoTag))
        }
    }
}

/**
 * A programme's headline, in the details page's order: who and what, its facts, its time and the
 * synopsis, then the [footer] in the quietest tone. Channels and Recordings fill the same parts
 * from their own data. With [fitSynopsis] the synopsis takes only whole lines of what the rest
 * leaves, up to [synopsisLines]. While [loading], the title and its time hold their lines.
 * Every part is tagged `<tagPrefix>-<part>`.
 */
@Composable
internal fun ProgrammeHeadline(
    eyebrow: @Composable () -> Unit,
    title: String,
    tagPrefix: String,
    synopsisLines: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    facts: List<String> = emptyList(),
    rating: String? = null,
    timing: String? = null,
    progress: Float? = null,
    synopsis: String? = null,
    fitSynopsis: Boolean = false,
    loading: Boolean = false,
    titleStyle: TextStyle = MaterialTheme.typography.headlineLarge,
    synopsisStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    footer: (@Composable (Modifier) -> Unit)? = null,
) {
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        eyebrow()
        if (loading) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                SkeletonLine(Modifier.padding(top = 8.dp).testTag("$tagPrefix-loading"), titleStyle, widthFraction = 0.7f)
                SkeletonLine(Modifier.padding(top = ProgrammeHeadlineLineGap), typography.labelMedium, widthFraction = 0.35f)
            }
            return@Column
        }
        Text(title, style = titleStyle, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).testTag("$tagPrefix-title"))
        subtitle?.takeIf(String::isNotBlank)?.let {
            // Regular weight, as the kit's dialog keeps everything under its title; white, as it
            // names the episode with the title.
            Text(it, style = typography.bodyLarge, color = TvTextColors.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp).testTag("$tagPrefix-subtitle"))
        }
        if (facts.isNotEmpty() || rating != null) {
            ProgrammeFacts(facts, rating, "$tagPrefix-rating",
                Modifier.padding(top = ProgrammeHeadlineLineGap).testTag("$tagPrefix-facts"))
        }
        if (timing != null) {
            Row(Modifier.padding(top = ProgrammeHeadlineLineGap).testTag("$tagPrefix-metadata"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(timing, style = typography.labelMedium, color = TvTextColors.tertiary, maxLines = 1)
                // Shorter rather than pushed off the end by a long time line.
                if (progress != null) ProgressStrip(progress, Modifier.weight(1f, fill = false).width(160.dp))
            }
        }
        synopsis?.takeIf(String::isNotBlank)?.let { text ->
            val synopsisText = @Composable { lines: Int ->
                Text(text, style = synopsisStyle, color = TvTextColors.secondary,
                    maxLines = lines, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = ProgrammeHeadlineSynopsisGap).testTag("$tagPrefix-synopsis"))
            }
            if (!fitSynopsis) synopsisText(synopsisLines) else BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                // Only whole lines in what the rest leaves, and none rather than a cut one.
                val lineHeight = with(LocalDensity.current) { synopsisStyle.lineHeight.toDp() }
                val lines = minOf(synopsisLines, ((maxHeight - ProgrammeHeadlineSynopsisGap) / lineHeight).toInt())
                if (lines > 0) synopsisText(lines)
            }
        }
        // A lone title keeps the footer close; otherwise it starts a group of its own.
        val details = subtitle?.isNotBlank() == true || facts.isNotEmpty() || rating != null || timing != null ||
            synopsis?.isNotBlank() == true
        footer?.invoke(Modifier.padding(top = if (details) ProgrammeHeadlineSynopsisGap else ProgrammeHeadlineLineGap))
    }
}

/** The facts and age rating of a programme, on one line. */
@Composable
private fun ProgrammeFacts(metadata: List<String>, rating: String?, ratingTag: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (metadata.isNotEmpty()) {
            Text(programmeFactsSegments(metadata), Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelMedium, color = TvTextColors.tertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (rating != null) ProgrammeAgeRatingBox(rating, ratingTag)
    }
}

/** The first summary or description that doesn't repeat the title or a supplied subtitle. */
internal fun programmeSynopsis(title: String?, subtitle: String?, summary: String?, description: String?): String? =
    sequenceOf(summary, description)
        .mapNotNull { it?.replace("\\r\\n", "\n")?.replace("\\n", "\n")?.trim() }
        .firstOrNull { it.isNotBlank() && it != title?.trim() && it != subtitle?.trim() }
