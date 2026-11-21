// Keep the list, its rows and their private visual details together; sizes are the drawings' (round 37).
@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.recordings

import at.bernhardberger.tvhplayer.touch.ui.ChannelLogoImage
import at.bernhardberger.tvhplayer.touch.ui.rememberChannelAccent
import at.bernhardberger.tvhplayer.touch.ui.rememberStandalonePicture
import android.icu.text.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.ui.PartsLine
import at.bernhardberger.tvhplayer.touch.ui.joinParts
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvRecordingContainerColor
import java.time.Duration
import java.time.ZoneId
import java.util.Date
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.ui.common.formatNarrowDuration
import androidx.compose.ui.platform.LocalResources

/** One level: its head, the folders it holds, then its recordings under their date labels. */
@Composable
internal fun RecordingList(
    level: RecordingFolder,
    clock: RecordingClock,
    oneChannel: Boolean,
    pickedId: String?,
    head: @Composable () -> Unit,
    onOpen: (String) -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        head()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (level.folders.isNotEmpty()) {
                group(level.folders, key = { "folder:${it.id}" }) { folder, index, count ->
                    FolderRow(folder, index, count) { onOpen(folder.id) }
                }
            }
            sections(level.recordings).forEach { section ->
                section.label?.let { label ->
                    item(key = "section:${section.items.first().id}") { SectionLabel(label.label) }
                }
                val context = RowContext(section.label?.dates ?: RowDates.None, oneChannel)
                group(section.items, key = { it.id }) { item, index, count ->
                    val line = recordingLine(item, context, clock)
                    RecordingRow(item, line, level.showsThumbnails, item.id == pickedId, index, count) {
                        onPick(item.id)
                    }
                }
            }
        }
    }
}

private class Section(val label: RecordingSection?, val items: List<RecordingItem>)

private fun sections(recordings: List<RecordingItem>): List<Section> {
    val sections = mutableListOf<Section>()
    var label: RecordingSection? = null
    var items = mutableListOf<RecordingItem>()
    recordings.forEach {
        if (it.section != null && items.isNotEmpty()) {
            sections += Section(label, items)
            items = mutableListOf()
        }
        if (it.section != null) label = it.section
        items += it
    }
    if (items.isNotEmpty()) sections += Section(label, items)
    return sections
}

private fun <T> LazyListScope.group(
    entries: List<T>,
    key: (T) -> Any,
    row: @Composable (T, Int, Int) -> Unit,
) {
    val count = entries.size
    entries.forEachIndexed { index, entry ->
        item(key = key(entry)) {
            Box(Modifier.padding(bottom = if (index < count - 1) ListItemDefaults.SegmentedGap else 0.dp)) {
                row(entry, index, count)
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The list head: the level's name and counts, and on a folder level the way back up. */
@Composable
internal fun LevelHead(
    view: DvrArchiveView,
    levels: List<RecordingFolder>,
    layout: RecordingsLayout,
    onUp: () -> Unit,
) {
    val level = levels.last()
    val counts = joinParts(levelCounts(view, level, root = levels.size == 1, compact = layout.compact))
    if (levels.size == 1) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(
                    if (view.grouping == DvrArchiveGrouping.FLAT) R.string.recordings_all else view.grouping.label,
                ),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.run { if (layout.compact) titleMedium else titleLarge },
            )
            Counts(counts)
        }
        return
    }
    val parent = levels[levels.size - 2]
    val names = listOf(stringResource(R.string.nav_recordings)) + levels.subList(1, levels.size - 1).map { it.name }
    val pathPattern = stringResource(R.string.recordings_path)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUp) {
            Icon(
                painterResource(R.drawable.connect_back_outline),
                stringResource(R.string.recordings_up, if (levels.size == 2) names.first() else parent.name),
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                names.reduce { a, b -> pathPattern.format(a, b) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    level.name,
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.run { if (layout.compact) titleLarge else headlineSmall },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Counts(counts)
            }
        }
    }
}

@Composable
private fun Counts(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun levelCounts(view: DvrArchiveView, level: RecordingFolder, root: Boolean, compact: Boolean): List<String> {
    val folders = level.folderCount
    val recordings = level.recordingCount
    val show = view.show.takeIf { it != DvrArchiveShow.ALL }?.let { stringResource(it.label) }
    return when {
        root && view.grouping == DvrArchiveGrouping.FLAT -> listOfNotNull(
            recordings.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recordings_count, it, it) },
            show,
            stringResource(view.sort.label),
        )
        else -> listOfNotNull(
            folders.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recordings_folder_count, it, it) },
            recordings.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recordings_count, it, it) },
            // The phone's folder head keeps to the count, as drawn.
            if (root) show else level.size.takeIf { !compact },
        )
    }
}

/** Folders the viewer files into; Recording now stands above them and is not counted. */
internal val RecordingFolder.folderCount: Int get() = folders.count { !it.recordingNow }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderRow(folder: RecordingFolder, index: Int, count: Int, onOpen: () -> Unit) {
    val supporting = joinParts(
        listOfNotNull(
            folder.folderCount.takeIf { it > 0 }
                ?.let { pluralStringResource(R.plurals.recordings_folder_count, it, it) },
            if (folder.recordingNow) {
                pluralStringResource(R.plurals.recordings_in_progress, folder.recordingCount, folder.recordingCount)
            } else {
                pluralStringResource(R.plurals.recordings_count, folder.recordingCount, folder.recordingCount)
            },
            folder.size.takeIf { !folder.recordingNow },
        ),
    )
    ListItem(
        onClick = onOpen,
        leadingContent = { FolderTile(folder.recordingNow) },
        trailingContent = { Icon(painterResource(R.drawable.connect_next_outline), null) },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = rowColors(),
        contentPadding = RowPadding,
    ) {
        Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FolderTile(recordingNow: Boolean) {
    Box(
        Modifier.size(ThumbWidth, ThumbHeight).clip(ThumbShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (recordingNow) {
            // The record mark: a filled dot, as on the TV app's folder.
            Box(Modifier.size(14.dp).clip(CircleShape).background(TvRecordingColor))
        } else {
            Icon(
                painterResource(R.drawable.recordings_folder), null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordingRow(
    item: RecordingItem,
    line: List<RecordingLinePart>,
    thumbnail: Boolean,
    selected: Boolean,
    index: Int,
    count: Int,
    onPick: () -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = onPick,
        leadingContent = if (thumbnail) ({ Thumbnail(item) }) else null,
        trailingContent = when (item.state) {
            is RecordingProgress.Recording -> ({ RecPill() })
            RecordingProgress.Watched -> ({
                Icon(
                    painterResource(R.drawable.channels_check), stringResource(R.string.recordings_watched),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            })
            else -> null
        },
        supportingContent = {
            PartsLine(line.map { partText(it) })
        },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = rowColors(),
        contentPadding = RowPadding,
    ) {
        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun partText(part: RecordingLinePart): String = when (part) {
    is RecordingLinePart.Episode -> part.name
    is RecordingLinePart.Time -> part.time
    is RecordingLinePart.Day -> dayText(part.day)
    is RecordingLinePart.Length -> durationText(part.length)
    is RecordingLinePart.Left -> stringResource(R.string.recordings_time_left, durationText(part.left))
    is RecordingLinePart.Until -> stringResource(R.string.recordings_until, part.time)
    is RecordingLinePart.Channel -> part.name
}

@Composable
internal fun dayText(day: RecordingDay): String = when (day) {
    RecordingDay.Today -> stringResource(SharedR.string.recordings_date_today)
    RecordingDay.Yesterday -> stringResource(SharedR.string.recordings_date_yesterday)
    is RecordingDay.On -> {
        val locale = LocalConfiguration.current.locales[0]
        val format = DateFormat.getInstanceForSkeleton(if (day.withYear) "yMMMEd" else "MMMEd", locale)
        format.format(Date.from(day.date.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    }
}

/** Narrow hours and minutes kept together, "1h 45m", by the shared `formatNarrowDuration`. */
@Composable
internal fun durationText(duration: Duration): String =
    formatNarrowDuration(duration.seconds, LocalResources.current)

/** The recording's picture; without one its channel's logo on the logo's tint, else the neutral tile (section 13). */
@Composable
private fun Thumbnail(item: RecordingItem) {
    val picture = rememberStandalonePicture(item.picture, item.pictureModel)
    val logo = picture == null && item.hasLogo
    val tint = item.logoTint.takeIf { logo }
    val accent = if (logo && tint == null) rememberChannelAccent(item.channelLogoModel) else null
    val neutral = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        Modifier.size(ThumbWidth, ThumbHeight).clip(ThumbShape).drawBehind {
            drawRect(tint ?: accent?.animated?.value ?: neutral)
        },
        contentAlignment = Alignment.Center,
    ) {
        val icon = @Composable {
            Icon(
                painterResource(R.drawable.video_library_outline), null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            picture != null -> Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            logo -> ChannelLogoImage(item.channelLogo, item.channelLogoModel, fallback = icon)
            else -> icon()
        }
        RecordingBar(item.state, Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp))
    }
}

/** The viewer's position (orange) or how much is recorded so far (red); nothing otherwise. */
@Composable
internal fun RecordingBar(
    state: RecordingProgress,
    modifier: Modifier,
    track: Color = Color.Black.copy(alpha = 0.45f),
) {
    val (progress, color) = when (state) {
        is RecordingProgress.PartlyWatched -> state.progress to MaterialTheme.colorScheme.tertiary
        is RecordingProgress.Recording -> state.progress to TvRecordingColor
        else -> return
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier,
        color = color,
        trackColor = track,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
internal fun RecPill() {
    // No Material badge carries a label of this size; a small Surface keeps the TV app's REC mark.
    Surface(shape = RoundedCornerShape(50), color = TvRecordingContainerColor, contentColor = Color.White) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            Text(stringResource(R.string.recordings_rec), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Segmented rows lifted off the page, as drawn (the default segmented container is the page's surface). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rowColors(container: Color = MaterialTheme.colorScheme.surfaceContainer) =
    ListItemDefaults.segmentedColors(containerColor = container)

internal val ThumbWidth = 88.dp
internal val ThumbHeight = 50.dp
internal val ThumbShape = RoundedCornerShape(8.dp)
private val RowPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
