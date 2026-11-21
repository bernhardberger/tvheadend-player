// Section 9's program details as a page: beside the rail on the tablet, the whole window on the phone.
// Sizes are the drawings' (round 37).
@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.guide

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelActionLabel
import at.bernhardberger.tvhplayer.touch.ui.ChannelLogoTile
import at.bernhardberger.tvhplayer.touch.ui.DetailsBackButton
import at.bernhardberger.tvhplayer.touch.ui.DetailsEpisode
import at.bernhardberger.tvhplayer.touch.ui.DetailsFacts
import at.bernhardberger.tvhplayer.touch.ui.DetailsPictureScrim
import at.bernhardberger.tvhplayer.touch.ui.DetailsSectionTitle
import at.bernhardberger.tvhplayer.touch.ui.DetailsSynopsis
import at.bernhardberger.tvhplayer.touch.ui.PartsLine

@Composable
internal fun ProgramDetailsPage(
    details: ProgramDetails,
    layout: GuideLayout,
    actions: GuideActions,
    onBack: () -> Unit,
) {
    when (layout) {
        GuideLayout.Wide -> WidePage(details, actions, onBack)
        GuideLayout.Upright -> UprightPage(details, actions, onBack)
        GuideLayout.Phone -> PhonePage(details, actions, onBack)
    }
}

@Composable
private fun WidePage(details: ProgramDetails, actions: GuideActions, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        details.picture?.let {
            Box(Modifier.align(Alignment.TopEnd).size(720.dp, 405.dp)) {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                DetailsPictureScrim(Modifier.fillMaxSize())
                // As drawn, the picture also fades into the page on its start side.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to MaterialTheme.colorScheme.surface, 0.45f to Color.Transparent),
                    ),
                )
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(PAGE_BAR_HEIGHT + 32.dp))
            Column(Modifier.widthIn(max = 560.dp)) {
                Lead(details, MaterialTheme.typography.displayMedium)
                Timing(details, Modifier.widthIn(max = 480.dp).padding(top = 20.dp))
                Actions(details, actions, Modifier.padding(top = 20.dp), equalWidth = false)
                DetailsSynopsis(details.synopsis, Modifier.padding(top = 20.dp))
            }
            Sections(details, actions, columns = 2, keyWidth = 132.dp, Modifier.widthIn(max = 680.dp))
            Spacer(Modifier.height(24.dp))
        }
        PageBar(details, onBack, transparent = true)
    }
}

@Composable
private fun UprightPage(details: ProgramDetails, actions: GuideActions, onBack: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pictureHeight = maxWidth / PICTURE_RATIO
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(pictureHeight)) {
                details.picture?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                DetailsPictureScrim(Modifier.fillMaxSize())
            }
            // As drawn, the title sits over the lower half of the picture.
            Column(Modifier.padding(top = pictureHeight * TITLE_OVER_PICTURE).padding(horizontal = 24.dp)) {
                Lead(details, MaterialTheme.typography.displayMedium)
                Timing(details, Modifier.widthIn(max = 480.dp).padding(top = 20.dp))
                Actions(details, actions, Modifier.padding(top = 20.dp), equalWidth = false)
                DetailsSynopsis(details.synopsis, Modifier.widthIn(max = 560.dp).padding(top = 20.dp))
                Sections(details, actions, columns = 2, keyWidth = 132.dp, Modifier.widthIn(max = 680.dp))
                Spacer(Modifier.height(24.dp))
            }
        }
        PageBar(details, onBack, transparent = true)
    }
}

@Composable
private fun PhonePage(details: ProgramDetails, actions: GuideActions, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageBar(details, onBack, transparent = false)
        ProgramDetailsColumn(
            details, actions,
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp),
        )
    }
}

/**
 * The phone's single column of details. The player's Info sheet shows it too ([inPlayer]): the program
 * is already playing and in view, so Record stands alone, without Watch or Show in Guide.
 */
@Composable
internal fun ProgramDetailsColumn(
    details: ProgramDetails,
    actions: GuideActions,
    modifier: Modifier,
    inPlayer: Boolean = false,
) {
    Column(modifier) {
        ProgramPicture(details.art ?: details.picture, Modifier.fillMaxWidth(), strip = false)
        Lead(details, MaterialTheme.typography.headlineMedium, Modifier.padding(top = 16.dp))
        Timing(details, Modifier.padding(top = 16.dp))
        val record = actions.onRecord
        val recordOptions = actions.onRecordOptions
        if (inPlayer) {
            if (record != null && recordOptions != null) {
                RecordButton(
                    { record(details.id) }, { recordOptions(details.id) }, Modifier.padding(top = 16.dp), leads = true,
                )
            }
        } else {
            Actions(details, actions, Modifier.padding(top = 16.dp), equalWidth = true)
        }
        DetailsSynopsis(details.synopsis, Modifier.padding(top = 20.dp))
        Sections(details, actions, columns = 1, keyWidth = 116.dp, Modifier)
        Spacer(Modifier.height(24.dp))
    }
}

/** Back, the channel and whether it is on now; over the picture on the tablet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageBar(details: ProgramDetails, onBack: () -> Unit, transparent: Boolean) {
    TopAppBar(
        navigationIcon = { DetailsBackButton(stringResource(R.string.recordings_back), onBack) },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ChannelLogoTile(
                    details.channelLogo, details.logoTint, Modifier.size(56.dp, 32.dp), RoundedCornerShape(8.dp),
                )
                Text(
                    details.channel, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (details.onNow) OnNowBadge()
            }
        },
        colors = if (transparent) {
            TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        } else {
            TopAppBarDefaults.topAppBarColors()
        },
    )
}

@Composable
private fun Lead(
    details: ProgramDetails,
    titleStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(details.title, Modifier.semantics { heading() }, style = titleStyle)
        DetailsEpisode(details.episode)
        val metadata = programLine(null, listOf(details.length), details.year, details.genre)
        if (metadata.isNotEmpty() || details.ageRating != null) {
            Row(
                Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (metadata.isNotEmpty()) {
                    PartsLine(
                        metadata, Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline,
                    )
                }
                details.ageRating?.let { AgeRating(it) }
            }
        }
    }
}

/** The age rating in an outlined box, as drawn; Material has no outlined label for it. */
@Composable
private fun AgeRating(rating: String) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        rating,
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(horizontal = 5.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The time, and while on air the bar and what is left. */
@Composable
private fun Timing(details: ProgramDetails, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            details.timeRange, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
        details.progress?.let {
            ProgramProgress(it, details.timeLeft, details.recording, Modifier.weight(1f), details.watching)
        }
    }
}

/** Watch (on now), Record with its series arrow, Show in Guide; the phone gives Show in Guide its own line. */
@Composable
private fun Actions(details: ProgramDetails, actions: GuideActions, modifier: Modifier, equalWidth: Boolean) {
    val watch = actions.onWatch?.takeIf { details.onNow }
    val record = actions.onRecord
    val recordOptions = actions.onRecordOptions?.takeIf { record != null }
    val showInGuide = actions.onShowInGuide
    if (watch == null && recordOptions == null && showInGuide == null) return
    val showInGuideButton: @Composable () -> Unit = {
        if (showInGuide != null) {
            TextButton(onClick = { showInGuide(details.id) }) {
                ChannelActionLabel(R.drawable.calendar_month_outline, stringResource(R.string.channels_show_in_guide))
            }
        }
    }
    if (equalWidth) {
        Column(modifier) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val share = Modifier.weight(1f).heightIn(min = 48.dp)
                if (watch != null) WatchButton(share) { watch(details.channelId) }
                if (record != null && recordOptions != null) {
                    RecordButton({ record(details.id) }, { recordOptions(details.id) }, share, leads = watch == null)
                }
            }
            showInGuideButton()
        }
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (watch != null) WatchButton { watch(details.channelId) }
            if (record != null && recordOptions != null) {
                RecordButton({ record(details.id) }, { recordOptions(details.id) }, leads = watch == null)
            }
            showInGuideButton()
        }
    }
}

@Composable
private fun Sections(details: ProgramDetails, actions: GuideActions, columns: Int, keyWidth: Dp, modifier: Modifier) {
    Column(modifier) {
        OtherAirings(details, actions)
        DetailsFacts(
            stringResource(R.string.guide_cast_and_crew),
            details.credits.map { pluralStringResource(it.role.label, it.names.size) to it.names.joinToString(", ") },
            columns = 1, keyWidth = keyWidth,
        )
        DetailsFacts(
            stringResource(R.string.recordings_details),
            details.facts.map { stringResource(it.key.label) to it.value },
            columns = columns, keyWidth = keyWidth,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OtherAirings(details: ProgramDetails, actions: GuideActions) {
    if (details.otherAirings.isEmpty()) return
    DetailsSectionTitle(stringResource(R.string.guide_other_airings, details.title))
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        details.otherAirings.forEachIndexed { index, airing ->
            val shapes = ListItemDefaults.segmentedShapes(index, details.otherAirings.size)
            val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            val leading: @Composable () -> Unit = {
                ChannelLogoTile(airing.logo, airing.logoTint, Modifier.size(64.dp, 36.dp), RoundedCornerShape(8.dp))
            }
            val supporting: @Composable () -> Unit = { PartsLine(airing.line) }
            val trailing: (@Composable () -> Unit)? = if (airing.scheduled) {
                { ScheduledIcon() }
            } else {
                null
            }
            val onAiring = actions.onAiring
            if (onAiring != null) {
                ListItem(
                    onClick = { onAiring(airing.id) }, shapes = shapes, colors = colors, leadingContent = leading,
                    supportingContent = supporting, trailingContent = trailing,
                ) { Text(airing.time, maxLines = 1) }
            } else {
                ListItem(
                    shapes = shapes, colors = colors, leadingContent = leading,
                    supportingContent = supporting, trailingContent = trailing,
                ) { Text(airing.time, maxLines = 1) }
            }
        }
    }
}

private val PAGE_BAR_HEIGHT = 64.dp
private const val TITLE_OVER_PICTURE = 0.72f
