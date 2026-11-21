// The pane and the two pages share their private parts; sizes are the drawings' (round 37).
@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.recordings

import at.bernhardberger.tvhplayer.touch.ui.PlayingBars

import at.bernhardberger.tvhplayer.touch.ui.ChannelLogoTile
import at.bernhardberger.tvhplayer.touch.ui.rememberStandalonePicture
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.DetailsBackButton
import at.bernhardberger.tvhplayer.touch.ui.DetailsEpisode
import at.bernhardberger.tvhplayer.touch.ui.DetailsFacts
import at.bernhardberger.tvhplayer.touch.ui.DetailsPictureScrim
import at.bernhardberger.tvhplayer.touch.ui.DetailsSynopsis
import at.bernhardberger.tvhplayer.touch.ui.RecordingRemoveControl
import at.bernhardberger.tvhplayer.touch.ui.joinParts
import at.bernhardberger.tvhplayer.ui.common.formatSpokenDuration
import at.bernhardberger.tvhplayer.presentation.R as SharedR

/** The picked recording beside the tablet's list. */
@Composable
internal fun RecordingPane(item: RecordingItem, clock: RecordingClock, actions: RecordingsActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelTile(item, Modifier.size(64.dp, 36.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        item.channel?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        Text(
                            whenText(item, clock), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Title(item, Modifier.padding(top = 16.dp), MaterialTheme.typography.headlineSmall)
                DetailsEpisode(item.episode)
                Facts(item)
            }
            rememberStandalonePicture(item.picture, item.pictureModel)?.let {
                Picture(it, Modifier.width(240.dp).clip(RoundedCornerShape(16.dp)))
            }
        }
        Progress(item.state, clock, Modifier.padding(top = 16.dp))
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayButtons(item, actions, equalWidth = false)
            Spacer(Modifier.weight(1f))
            ItemIcons(item, actions)
        }
        DetailsSynopsis(item.details.synopsis, Modifier.padding(top = 20.dp))
        Facts(item.details, columns = 1, keyWidth = 132.dp)
    }
}

/** The picked recording as a page of its own on the upright tablet and the phone. */
@Composable
internal fun RecordingPage(
    item: RecordingItem,
    clock: RecordingClock,
    layout: RecordingsLayout,
    actions: RecordingsActions,
    onBack: () -> Unit,
) {
    if (layout.compact) PhonePage(item, clock, actions, onBack) else UprightPage(item, clock, actions, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UprightPage(item: RecordingItem, clock: RecordingClock, actions: RecordingsActions, onBack: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val picture = rememberStandalonePicture(item.picture, item.pictureModel)
        val pictureHeight = maxWidth / PICTURE_RATIO
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (picture != null) {
                Box(Modifier.fillMaxWidth().height(pictureHeight)) {
                    Picture(picture, Modifier.fillMaxSize())
                    DetailsPictureScrim(Modifier.fillMaxSize())
                }
            }
            // As drawn, the text starts over the lower quarter of the picture; without one, under the bar.
            val top = if (picture != null) pictureHeight * TITLE_OVER_PICTURE else BAR_HEIGHT + 32.dp
            Column(Modifier.padding(top = top).padding(horizontal = 24.dp)) {
                Column(Modifier.widthIn(max = TEXT_WIDTH)) {
                    Title(item, Modifier, MaterialTheme.typography.displaySmall)
                    DetailsEpisode(item.episode)
                    Facts(item)
                    Progress(item.state, clock, Modifier.padding(top = 16.dp))
                    Row(Modifier.padding(top = 16.dp)) { PlayButtons(item, actions, equalWidth = false) }
                    DetailsSynopsis(item.details.synopsis, Modifier.padding(top = 20.dp))
                }
                Facts(item.details, columns = 2, keyWidth = 132.dp)
                Spacer(Modifier.height(24.dp))
            }
        }
        TopAppBar(
            navigationIcon = { DetailsBackButton(stringResource(R.string.recordings_back), onBack) },
            title = {
                PageChannel(
                    item, clock, Modifier.size(64.dp, 36.dp), MaterialTheme.typography.titleLarge, withDate = true,
                )
            },
            actions = { ItemIcons(item, actions) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhonePage(item: RecordingItem, clock: RecordingClock, actions: RecordingsActions, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { DetailsBackButton(stringResource(R.string.recordings_back), onBack) },
            title = {
                PageChannel(
                    item, clock, Modifier.size(56.dp, 32.dp), MaterialTheme.typography.titleMedium, withDate = false,
                )
            },
            actions = { ItemIcons(item, actions) },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp),
        ) {
            val picture = rememberStandalonePicture(item.picture, item.pictureModel)
            picture?.let { Picture(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) }
            RecordingDetailsBody(
                item, whenText(item, clock),
                whenModifier = if (picture != null) Modifier.padding(top = 16.dp) else Modifier,
                beforeSynopsis = {
                    Progress(item.state, clock, Modifier.padding(top = 16.dp))
                    Row(Modifier.padding(top = 16.dp)) { PlayButtons(item, actions, equalWidth = true) }
                },
            )
        }
    }
}

/** Single-column recording details; the surrounding page owns its artwork and controls. */
@Composable
internal fun RecordingDetailsBody(
    item: RecordingItem,
    whenLine: String,
    whenModifier: Modifier = Modifier,
    beforeSynopsis: (@Composable () -> Unit)? = null,
    afterSynopsis: (@Composable () -> Unit)? = null,
    programmeFacts: List<Pair<String, String>> = emptyList(),
) {
    Text(
        whenLine, whenModifier,
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Title(item, Modifier.padding(top = 4.dp), MaterialTheme.typography.headlineMedium)
    DetailsEpisode(item.episode)
    Facts(item)
    beforeSynopsis?.invoke()
    DetailsSynopsis(item.details.synopsis, Modifier.padding(top = 20.dp))
    afterSynopsis?.invoke()
    Facts(item.details, columns = 1, keyWidth = 116.dp, programmeFacts = programmeFacts)
    Spacer(Modifier.height(24.dp))
}

/** The start in full for the pane and the page, "Wed, Nov 11 • 8:30 PM". */
@Composable
private fun whenText(item: RecordingItem, clock: RecordingClock): String =
    item.start?.let { joinParts(listOf(dayText(recordingDay(it, clock)), recordingTime(it, clock))) }.orEmpty()

@Composable
private fun PageChannel(
    item: RecordingItem,
    clock: RecordingClock,
    tile: Modifier,
    style: TextStyle,
    withDate: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChannelTile(item, tile)
        item.channel?.let {
            Text(it, Modifier.padding(start = 12.dp), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (withDate) {
            Text(
                whenText(item, clock), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ChannelTile(item: RecordingItem, modifier: Modifier) {
    if (item.channelLogo == null && item.channelLogoModel == null) return
    ChannelLogoTile(item.channelLogo, item.logoTint, modifier, RoundedCornerShape(8.dp), item.channelLogoModel)
}

@Composable
private fun Picture(picture: Painter, modifier: Modifier) {
    Image(
        picture, null,
        modifier.aspectRatio(PICTURE_RATIO).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .testTag("recording-details-picture"),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun Title(item: RecordingItem, modifier: Modifier, style: TextStyle) {
    if (item.playing) {
        Row(
            modifier.semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.title, Modifier.weight(1f, fill = false), style = style)
            PlayingBars()
        }
    } else {
        Text(item.title, modifier.semantics { heading() }, style = style)
    }
}

@Composable
private fun Facts(item: RecordingItem) {
    val facts = listOfNotNull(item.length?.let { durationText(it) }, item.details.size, item.details.genre)
    Text(
        joinParts(facts), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun Progress(state: RecordingProgress, clock: RecordingClock, modifier: Modifier) {
    val label = when (state) {
        is RecordingProgress.PartlyWatched -> stringResource(R.string.recordings_time_left, durationText(state.left))
        is RecordingProgress.Recording -> stringResource(R.string.recordings_until, recordingTime(state.until, clock))
        else -> return
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (state is RecordingProgress.Recording) {
            RecPill()
            Spacer(Modifier.width(12.dp))
        }
        RecordingBar(
            state, Modifier.weight(1f).height(4.dp), track = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Text(
            label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayButtons(item: RecordingItem, actions: RecordingsActions, equalWidth: Boolean) {
    if (item.remove == RecordingRemoveControl.RemoveEntry) {
        actions.onDelete?.let { RecordingActionButton(item.remove) { it(item.id) } }
        return
    }
    val onPlay = actions.onPlay?.takeIf { item.play.isNotEmpty() } ?: return
    val width = if (equalWidth) Modifier.fillMaxWidth() else Modifier
    val height = if (equalWidth) Modifier.heightIn(min = 48.dp) else Modifier
    val padding = if (equalWidth) ButtonDefaults.SmallContentPadding else ButtonDefaults.ContentPadding
    Row(width, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val share = if (equalWidth) Modifier.weight(1f).then(height) else Modifier
        item.play.forEach { action ->
            when (action) {
                RecordingPlayAction.StartOver -> FilledTonalButton(
                    onClick = { onPlay(item.id, true) }, modifier = share,
                    contentPadding = padding,
                ) {
                    Text(
                        stringResource(R.string.recordings_watch_from_beginning), maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                is RecordingPlayAction.Resume, RecordingPlayAction.Watch ->
                    PrimaryPlayButton(action as? RecordingPlayAction.Resume, share) { onPlay(item.id, false) }
            }
        }
    }
}

@Composable
private fun PrimaryPlayButton(resume: RecordingPlayAction.Resume?, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) {
        Icon(painterResource(R.drawable.channels_play), null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        val label = if (resume == null) R.string.recordings_watch else R.string.recordings_resume
        val spoken = resume?.let {
            val duration = formatSpokenDuration(it.position.inWholeSeconds, LocalContext.current.resources)
            stringResource(SharedR.string.recording_resume_from, duration)
        }
        Text(
            stringResource(label),
            if (spoken == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = spoken },
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemIcons(item: RecordingItem, actions: RecordingsActions) {
    val remove = item.remove
    when (remove) {
        RecordingRemoveControl.Stop -> actions.onStop?.let { RecordingActionButton(remove) { it(item.id) } }
        RecordingRemoveControl.Delete -> actions.onDelete?.let { delete ->
            val label = stringResource(remove.label)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(label) } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = { delete(item.id) }) {
                    Icon(painterResource(remove.icon), label)
                }
            }
        }
        RecordingRemoveControl.RemoveEntry, null -> Unit // Remove entry goes with the labeled actions.
    }
    actions.onMore?.let { onMore ->
        IconButton(onClick = { onMore(item.id) }) {
            Icon(painterResource(R.drawable.recordings_more), stringResource(R.string.recordings_more))
        }
    }
}

@Composable
private fun RecordingActionButton(control: RecordingRemoveControl, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(painterResource(control.icon), null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(control.label))
    }
}

@Composable
private fun Facts(
    details: RecordingDetails,
    columns: Int,
    keyWidth: Dp,
    programmeFacts: List<Pair<String, String>> = emptyList(),
) = DetailsFacts(
    stringResource(R.string.recordings_details), programmeFacts + details.facts, columns, keyWidth,
)

private const val PICTURE_RATIO = 16f / 9f
private val TEXT_WIDTH = 576.dp
private const val TITLE_OVER_PICTURE = 0.75f
private val BAR_HEIGHT = 64.dp
