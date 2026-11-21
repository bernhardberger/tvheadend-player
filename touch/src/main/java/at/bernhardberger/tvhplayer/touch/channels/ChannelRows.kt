// Keep the list, row variants, and their private visual details together.
@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.channels

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.ui.TvOnRecordingContainerColor
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvRecordingContainerColor

@Composable
internal fun ChannelRows(
    list: ChannelList,
    layout: ChannelsLayout,
    actions: ChannelsActions,
    offline: Boolean = false,
    initiallyOpenChannelId: String? = null,
) {
    var openId by rememberSaveable(list.selectedTagId) { mutableStateOf(initiallyOpenChannelId) }
    BackHandler(enabled = !offline && list.rows.any { it.id == openId && it.details != null }) { openId = null }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (layout.tablet) item { ColumnLabels(layout) }
        itemsIndexed(list.rows, key = { _, row -> row.id }) { index, row ->
            ChannelItem(
                row = row,
                layout = layout,
                listHasLogos = list.hasLogos,
                expanded = !offline && openId == row.id && row.details != null,
                offline = offline,
                positionShape = rowShape(index, list.rows.lastIndex),
                actions = actions,
                toggle = { openId = if (openId == row.id) null else row.id },
            )
        }
    }
}

@Composable
private fun ColumnLabels(layout: ChannelsLayout) {
    Row(
        Modifier.fillMaxWidth().padding(start = RowInset, end = RowInset + EndTarget + PlayingSlot).height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(identityWeight(layout))
        ColumnLabel(R.string.channels_on_now, nowWeight(layout))
        if (layout.showNext) ColumnLabel(R.string.channels_next, Modifier.weight(3.2f))
    }
}

@Composable
private fun ColumnLabel(label: Int, modifier: Modifier) {
    Text(
        stringResource(label), modifier.semantics { heading() },
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun ChannelItem(
    row: ChannelRow,
    layout: ChannelsLayout,
    listHasLogos: Boolean,
    expanded: Boolean,
    offline: Boolean,
    positionShape: RoundedCornerShape,
    actions: ChannelsActions,
    toggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val playing = !offline && row.playback != null
    val container = when {
        playing -> colors.secondaryContainer
        expanded -> colors.surfaceContainerHigh
        else -> colors.surfaceContainerLow
    }
    val shape = if (expanded) RoundedCornerShape(20.dp) else positionShape
    Column(Modifier.fillMaxWidth().alpha(if (offline) SAVED_ROW_ALPHA else 1f).clip(shape).background(container)) {
        ChannelHeader(row, layout, listHasLogos, expanded, offline, actions.onPlay, toggle)
        if (expanded && row.details != null) ChannelExpanded(row, row.details, layout, actions)
    }
}

@Composable
private fun ChannelHeader(
    row: ChannelRow,
    layout: ChannelsLayout,
    listHasLogos: Boolean,
    expanded: Boolean,
    offline: Boolean,
    onPlay: (String) -> Unit,
    toggle: () -> Unit,
) {
    val recording = row.recording.takeUnless { offline }
    val playback = row.playback.takeUnless { offline }
    Row(Modifier.fillMaxWidth().padding(horizontal = RowInset), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = layout.rowHeight)
                .clickable(
                    enabled = !offline, role = Role.Button, onClickLabel = stringResource(R.string.channels_watch),
                ) { onPlay(row.id) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (layout.tablet) {
                TabletRowContent(row, layout, listHasLogos, recording, playback, offline)
            } else {
                PhoneRowContent(row, listHasLogos, recording, playback)
            }
        }
        ExpandButton(row, expanded, offline, toggle)
    }
}

@Composable
private fun ExpandButton(row: ChannelRow, expanded: Boolean, offline: Boolean, toggle: () -> Unit) {
    Box(Modifier.width(EndTarget), contentAlignment = Alignment.CenterEnd) {
        if (row.details != null || offline) {
            val label = if (expanded) R.string.channels_collapse else R.string.channels_expand
            val scheme = MaterialTheme.colorScheme
            FilledIconButton(
                onClick = toggle, enabled = !offline,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (expanded) scheme.secondaryContainer else scheme.surfaceContainerHighest,
                    contentColor = if (expanded) scheme.onSecondaryContainer else scheme.onSurface,
                ),
            ) {
                Icon(
                    painterResource(R.drawable.channels_expand), stringResource(label, row.name),
                    Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
        }
    }
}

@Composable
private fun RowScope.TabletRowContent(
    row: ChannelRow,
    layout: ChannelsLayout,
    listHasLogos: Boolean,
    recording: ChannelRecording?,
    playback: ChannelPlayback?,
    offline: Boolean,
) {
    Row(
        identityWeight(layout).padding(end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (listHasLogos) ChannelLogo(row, layout)
        Row(
            Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.name, Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium, maxLines = 2,
            )
            if (recording == ChannelRecording.Channel) RecordingBadge(recording, compact = true)
        }
    }
    TabletNow(row.now, playback, recording, nowWeight(layout).padding(end = 12.dp))
    if (layout.showNext) {
        NextProgram(row.next, !offline, Modifier.weight(3.2f))
    }
    Box(Modifier.width(PlayingSlot), contentAlignment = Alignment.CenterStart) {
        if (playback != null) PlayingBars()
    }
}

@Composable
private fun RowScope.PhoneRowContent(
    row: ChannelRow,
    listHasLogos: Boolean,
    recording: ChannelRecording?,
    playback: ChannelPlayback?,
) {
    if (listHasLogos) ChannelLogo(row, ChannelsLayout.Phone)
    Column(Modifier.weight(1f).padding(start = if (listHasLogos) 14.dp else 0.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelName(row.name, Modifier.weight(1f))
            if (playback != null) PlayingBars()
            if (recording != null) RecordingBadge(recording, compact = recording == ChannelRecording.Channel)
        }
        val now = row.now
        val title = now?.title ?: stringResource(R.string.channels_no_program)
        val line = now?.timeLeft?.let {
            stringResource(
                R.string.channels_program_and_time_left, title, stringResource(R.string.channels_time_left, it),
            )
        } ?: title
        SupportingLine(line)
        ChannelProgress(now, playback, recording, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun ChannelName(name: String, modifier: Modifier) {
    ProvideTextStyle(MaterialTheme.typography.titleMedium) {
        ChannelLine(name, modifier)
    }
}

@Composable
private fun ChannelLogo(row: ChannelRow, layout: ChannelsLayout) {
    val shape = RoundedCornerShape(12.dp)
    val size = when (layout) {
        ChannelsLayout.Wide, ChannelsLayout.Upright -> Modifier.size(112.dp, 56.dp)
        ChannelsLayout.Phone -> Modifier.size(88.dp, 48.dp)
    }
    val scheme = MaterialTheme.colorScheme
    val tint = row.logoTint.takeIf { row.logo != null }
    val fill = if (tint == null) {
        Modifier.background(scheme.surfaceContainerHigh)
    } else {
        Modifier.background(Brush.linearGradient(listOf(tint, lerp(tint, Color.Black, LOGO_SHADE))))
    }
    Box(
        size.clip(shape).then(fill)
            .border(1.dp, scheme.outlineVariant, shape).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (row.logo == null) {
            Icon(painterResource(R.drawable.play_tv_outline), null, tint = scheme.onSurfaceVariant)
        } else {
            Image(row.logo, null)
        }
    }
}

private fun RowScope.identityWeight(layout: ChannelsLayout) = Modifier.weight(if (layout.showNext) 3f else 4.2f)
private fun RowScope.nowWeight(layout: ChannelsLayout) = Modifier.weight(if (layout.showNext) 3.8f else 5.8f)

private fun rowShape(index: Int, lastIndex: Int) = RoundedCornerShape(
    topStart = if (index == 0) 16.dp else 4.dp,
    topEnd = if (index == 0) 16.dp else 4.dp,
    bottomStart = if (index == lastIndex) 16.dp else 4.dp,
    bottomEnd = if (index == lastIndex) 16.dp else 4.dp,
)

private val RowInset = 10.dp
private val EndTarget = 54.dp
private val PlayingSlot = 30.dp
private const val LOGO_SHADE = 0.35f
private const val SAVED_ROW_ALPHA = 0.58f

@Composable
private fun TabletNow(
    program: ChannelProgram?,
    playback: ChannelPlayback?,
    recording: ChannelRecording?,
    modifier: Modifier,
) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLine(
                program?.title ?: stringResource(R.string.channels_no_program), Modifier.weight(1f, fill = false),
            )
            if (recording == ChannelRecording.Program) RecordingBadge(recording)
        }
        Row(
            Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            program?.timeRange?.let { SupportingLine(it, Modifier.widthIn(min = 110.dp)) }
            ChannelProgress(
                program, playback, recording, Modifier.weight(1f, fill = false).widthIn(max = 136.dp).fillMaxWidth(),
            )
            program?.timeLeft?.let { SupportingLine(stringResource(R.string.channels_time_left, it)) }
        }
    }
}

@Composable
private fun NextProgram(program: ChannelProgram?, showMarks: Boolean, modifier: Modifier) {
    Column(modifier.padding(end = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLine(
                program?.title ?: stringResource(R.string.channels_no_program), Modifier.weight(1f, fill = false),
            )
            if (showMarks && program?.scheduled == true) {
                Icon(
                    painterResource(R.drawable.channels_schedule), stringResource(R.string.channels_scheduled),
                    Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
        program?.let { SupportingLine(it.start, Modifier.padding(top = 3.dp)) }
    }
}

@Composable
private fun ChannelProgress(
    program: ChannelProgram?,
    playback: ChannelPlayback?,
    recording: ChannelRecording?,
    modifier: Modifier,
) {
    val fraction = (playback?.progress ?: program?.progress ?: return).coerceIn(0f, 1f)
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        playback != null -> scheme.tertiary
        recording == ChannelRecording.Program -> TvRecordingColor
        else -> scheme.primary
    }
    val track = scheme.onSurface.copy(alpha = TRACK_ALPHA)
    Canvas(modifier.height(4.dp).progressSemantics(fraction)) {
        val cap = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = cap)
        if (fraction > 0f) drawRoundRect(fill, size = Size(size.width * fraction, size.height), cornerRadius = cap)
    }
}

@Composable
private fun RecordingBadge(recording: ChannelRecording, compact: Boolean = false) {
    val descriptionId = when (recording) {
        ChannelRecording.Channel -> R.string.channels_recording_on_channel
        ChannelRecording.Program -> R.string.channels_recording
    }
    val description = stringResource(descriptionId)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description }
            .heightIn(min = if (compact) 13.dp else 18.dp)
            .background(TvRecordingContainerColor, CircleShape)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(5.dp).background(TvOnRecordingContainerColor, CircleShape))
        if (!compact) {
            Text(
                stringResource(R.string.channels_rec), color = TvOnRecordingContainerColor,
                style = MaterialTheme.typography.labelSmall, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun PlayingBars() {
    val animation = rememberInfiniteTransition(label = "Playing")
    val level by animation.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PLAYING_HALF_CYCLE_MS), RepeatMode.Reverse), label = "Bar levels",
    )
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    val playing = stringResource(R.string.channels_playing)
    Canvas(Modifier.size(22.dp).semantics { stateDescription = playing }) {
        val heights = listOf(0.32f + 0.16f * level, 0.82f - 0.22f * level, 0.47f + 0.17f * level)
        heights.forEachIndexed { index, height ->
            val x = size.width * (index + 1) / 4
            val bottom = size.height * 0.9f
            drawLine(ink, Offset(x, bottom), Offset(x, bottom - size.height * height), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun SupportingLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

private const val TRACK_ALPHA = 0.2f
private const val PLAYING_HALF_CYCLE_MS = 480
