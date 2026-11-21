// The pane and the two pages share their private parts; sizes are the drawings' (round 37).
@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.recordings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R

/** The picked recording beside the tablet's list. */
@Composable
internal fun RecordingPane(item: RecordingItem, actions: RecordingsActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelTile(item, Modifier.size(64.dp, 36.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        item.channel?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        Text(
                            joinParts(listOf(item.day, item.time)), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Title(item, Modifier.padding(top = 16.dp), MaterialTheme.typography.headlineSmall)
                Episode(item)
                Facts(item)
            }
            Picture(item, Modifier.width(240.dp).clip(RoundedCornerShape(16.dp)))
        }
        Progress(item.state, Modifier.padding(top = 16.dp))
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayButtons(item, actions, equalWidth = false)
            Spacer(Modifier.weight(1f))
            ItemIcons(item, actions)
        }
        Synopsis(item, Modifier.padding(top = 20.dp))
        Facts(item.details, columns = 1, keyWidth = 132.dp)
    }
}

/** The picked recording as a page of its own on the upright tablet and the phone. */
@Composable
internal fun RecordingPage(
    item: RecordingItem,
    layout: RecordingsLayout,
    actions: RecordingsActions,
    onBack: () -> Unit,
) {
    if (layout.compact) PhonePage(item, actions, onBack) else UprightPage(item, actions, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UprightPage(item: RecordingItem, actions: RecordingsActions, onBack: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pictureHeight = maxWidth / PICTURE_RATIO
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(pictureHeight)) {
                Picture(item, Modifier.fillMaxSize())
                // A scrim keeps the bar and the text readable on any picture.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f), 0.3f to Color.Transparent,
                            0.5f to Color.Transparent, 1f to MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
            }
            // As drawn, the text starts over the lower quarter of the picture.
            Column(Modifier.padding(top = pictureHeight * TITLE_OVER_PICTURE).padding(horizontal = 24.dp)) {
                Column(Modifier.widthIn(max = TEXT_WIDTH)) {
                    Title(item, Modifier, MaterialTheme.typography.displaySmall)
                    Episode(item)
                    Facts(item)
                    Progress(item.state, Modifier.padding(top = 16.dp))
                    Row(Modifier.padding(top = 16.dp)) { PlayButtons(item, actions, equalWidth = false) }
                    Synopsis(item, Modifier.padding(top = 20.dp))
                }
                Facts(item.details, columns = 2, keyWidth = 132.dp)
                Spacer(Modifier.height(24.dp))
            }
        }
        TopAppBar(
            navigationIcon = { BackButton(onBack) },
            title = {
                PageChannel(item, Modifier.size(64.dp, 36.dp), MaterialTheme.typography.titleLarge, withDate = true)
            },
            actions = { ItemIcons(item, actions) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhonePage(item: RecordingItem, actions: RecordingsActions, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { BackButton(onBack) },
            title = {
                PageChannel(item, Modifier.size(56.dp, 32.dp), MaterialTheme.typography.titleMedium, withDate = false)
            },
            actions = { ItemIcons(item, actions) },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp),
        ) {
            Picture(item, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)))
            Text(
                joinParts(listOf(item.day, item.time)), Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Title(item, Modifier.padding(top = 4.dp), MaterialTheme.typography.headlineMedium)
            Episode(item)
            Facts(item)
            Progress(item.state, Modifier.padding(top = 16.dp))
            Row(Modifier.padding(top = 16.dp)) { PlayButtons(item, actions, equalWidth = true) }
            Synopsis(item, Modifier.padding(top = 20.dp))
            Facts(item.details, columns = 1, keyWidth = 116.dp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.connect_back_outline), stringResource(R.string.recordings_back))
    }
}

@Composable
private fun PageChannel(item: RecordingItem, tile: Modifier, style: TextStyle, withDate: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChannelTile(item, tile)
        item.channel?.let {
            Text(it, Modifier.padding(start = 12.dp), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (withDate) {
            Text(
                joinParts(listOf(item.day, item.time)), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ChannelTile(item: RecordingItem, modifier: Modifier) {
    val logo = item.channelLogo ?: return
    val tint = item.logoTint ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.clip(shape).background(tint)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(logo, null)
    }
}

@Composable
private fun Picture(item: RecordingItem, modifier: Modifier) {
    val picture = item.picture
    Box(
        modifier.aspectRatio(PICTURE_RATIO).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                painterResource(R.drawable.video_library_outline), null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Title(item: RecordingItem, modifier: Modifier, style: TextStyle) {
    Text(item.title, modifier.semantics { heading() }, style = style)
}

@Composable
private fun Episode(item: RecordingItem) {
    item.episode?.let {
        Text(
            it, Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Facts(item: RecordingItem) {
    val facts = listOfNotNull(item.details.genre, item.length, item.details.size)
    Text(
        joinParts(facts), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun Progress(state: RecordingProgress, modifier: Modifier) {
    val label = when (state) {
        is RecordingProgress.PartlyWatched -> stringResource(R.string.recordings_time_left, state.left)
        is RecordingProgress.Recording -> stringResource(R.string.recordings_until, state.until)
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
    val width = if (equalWidth) Modifier.fillMaxWidth() else Modifier
    val height = if (equalWidth) Modifier.heightIn(min = 48.dp) else Modifier
    Row(width, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val share = if (equalWidth) Modifier.weight(1f).then(height) else Modifier
        val resume = item.state is RecordingProgress.PartlyWatched
        Button(onClick = { actions.onPlay(item.id, false) }, modifier = share) {
            Icon(painterResource(R.drawable.channels_play), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(if (resume) R.string.recordings_resume else R.string.recordings_watch), maxLines = 1)
        }
        if (resume) {
            FilledTonalButton(
                onClick = { actions.onPlay(item.id, true) }, modifier = share,
                contentPadding = if (equalWidth) ButtonDefaults.SmallContentPadding else ButtonDefaults.ContentPadding,
            ) {
                Text(
                    stringResource(R.string.recordings_watch_from_beginning), maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ItemIcons(item: RecordingItem, actions: RecordingsActions) {
    IconButton(onClick = { actions.onDelete(item.id) }) {
        Icon(painterResource(R.drawable.connect_delete_outline), stringResource(R.string.recordings_delete))
    }
    IconButton(onClick = { actions.onMore(item.id) }) {
        Icon(painterResource(R.drawable.recordings_more), stringResource(R.string.recordings_more))
    }
}

@Composable
private fun Synopsis(item: RecordingItem, modifier: Modifier) {
    item.details.synopsis?.let {
        Text(
            it, modifier, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Facts(details: RecordingDetails, columns: Int, keyWidth: Dp) {
    val facts = listOfNotNull(
        details.genre?.let { R.string.recordings_genre to it },
        details.recorded?.let { R.string.recordings_recorded to it },
        details.size?.let { R.string.recordings_size to it },
        details.picture?.let { R.string.recordings_picture to it },
        details.audio?.let { R.string.recordings_audio to it },
        details.subtitles?.let { R.string.recordings_subtitles to it },
        details.errors?.let { R.string.recordings_errors to it },
        details.retention?.let { R.string.recordings_retention to it },
    )
    if (facts.isEmpty()) return
    Text(
        stringResource(R.string.recordings_details),
        Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
    val rows = (facts.size + columns - 1) / columns
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        facts.chunked(rows).forEach { column ->
            Column(Modifier.weight(1f)) {
                column.forEach { (key, value) -> Fact(stringResource(key), value, keyWidth) }
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

private const val PICTURE_RATIO = 16f / 9f
private val TEXT_WIDTH = 576.dp
private const val TITLE_OVER_PICTURE = 0.75f
