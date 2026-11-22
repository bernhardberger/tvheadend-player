// Keep the list, row variants, and their private visual details together.
@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.PlayingBars
import at.bernhardberger.tvhplayer.touch.ui.channelLogoSlot
import at.bernhardberger.tvhplayer.touch.ui.playingContainerColor
import at.bernhardberger.tvhplayer.touch.ui.watchedProgressColor
import at.bernhardberger.tvhplayer.ui.TvOnRecordingContainerColor
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvRecordingContainerColor
import at.bernhardberger.tvhplayer.ui.common.formatNarrowDuration

@Composable
internal fun ChannelRows(
    list: ChannelList,
    layout: ChannelsLayout,
    actions: ChannelsActions,
    offline: Boolean = false,
    initiallyOpenChannelId: String? = null,
    showColumnLabels: Boolean = layout.tablet,
    emptyContent: @Composable () -> Unit = {},
) {
    // Save the tag alongside the position: rememberSaveable inputs alone are not checked on restoration.
    var savedTag by rememberSaveable { mutableStateOf(list.selectedTagId) }
    var openId by rememberSaveable { mutableStateOf(initiallyOpenChannelId) }
    val scroll = rememberLazyListState()
    val sameTag = savedTag == list.selectedTagId
    val opened = openId?.takeIf { id -> sameTag && list.rows.any { it.id == id } }
    SideEffect {
        if (!sameTag) {
            scroll.requestScrollToItem(0)
            savedTag = list.selectedTagId
        }
        if (openId != opened) openId = opened
    }

    if (list.rows.isEmpty()) {
        emptyContent()
        return
    }
    BackHandler(enabled = !offline && list.rows.any { it.id == opened && it.details != null }) { openId = null }
    LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        if (showColumnLabels) item { ColumnLabels(layout, list.hasLogos) }
        itemsIndexed(list.rows, key = { _, row -> row.id }) { index, row ->
            ChannelItem(
                row = row,
                layout = layout,
                listHasLogos = list.hasLogos,
                expanded = !offline && opened == row.id && row.details != null,
                offline = offline,
                shapes = ListItemDefaults.segmentedShapes(index, list.rows.size),
                actions = actions,
                toggle = { openId = if (opened == row.id) null else row.id },
            )
        }
    }
}

@Composable
private fun ColumnLabels(layout: ChannelsLayout, listHasLogos: Boolean) {
    val logo = if (listHasLogos) layout.logoWidth + ItemSpacing else 0.dp
    Row(
        Modifier.fillMaxWidth()
            .padding(start = RowInset + logo, end = RowInset + ItemSpacing + ExpandTarget + PlayingSlot)
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(nameWeight(layout))
        ColumnLabel(R.string.channels_on_now, nowWeight(layout))
        if (layout.showNext) ColumnLabel(R.string.channels_next, nextWeight())
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
    shapes: ListItemShapes,
    actions: ChannelsActions,
    toggle: () -> Unit,
) {
    val playing = !offline && row.playback != null
    // The playing channel takes the list item's selected container without becoming a selection control:
    // a tap still means Watch, so the row keeps button semantics.
    val container = when {
        playing -> playingContainerColor()
        expanded -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    if (expanded && row.details != null) {
        // ListItem lays its slots out in one row, so the details below the header cannot live inside it.
        val shape = ListItemDefaults.segmentedShapes(0, 1).shape
        Column(Modifier.fillMaxWidth().clip(shape).background(container)) {
            ChannelHeader(row, layout, listHasLogos, true, offline, Color.Transparent, shapes, actions.onPlay, toggle)
            ChannelExpanded(row, row.details, layout, actions)
        }
    } else {
        ChannelHeader(row, layout, listHasLogos, false, offline, container, shapes, actions.onPlay, toggle)
    }
}

@Composable
private fun ChannelHeader(
    row: ChannelRow,
    layout: ChannelsLayout,
    listHasLogos: Boolean,
    expanded: Boolean,
    offline: Boolean,
    container: Color,
    shapes: ListItemShapes,
    onPlay: ((String) -> Unit)?,
    toggle: () -> Unit,
) {
    val recording = row.recording.takeUnless { offline }
    val playback = row.playback.takeUnless { offline }
    val segmented = ListItemDefaults.segmentedColors()
    val watch = stringResource(R.string.channels_watch)
    SegmentedListItem(
        onClick = if (onPlay != null) ({ onPlay(row.id) }) else toggle,
        shapes = shapes,
        modifier = Modifier.fillMaxWidth().alpha(if (offline) SAVED_ROW_ALPHA else 1f)
            .heightIn(min = layout.rowHeight)
            .semantics {
                role = Role.Button
                // Without Watch the tap is the expand button's, which already says what it does.
                if (onPlay != null) onClick(label = watch, action = null)
            },
        enabled = !offline,
        leadingContent = channelRowLogo(row, layout, listHasLogos),
        trailingContent = { ExpandButton(row, expanded, offline, toggle) },
        verticalAlignment = Alignment.CenterVertically,
        // A saved row while offline is dimmed as a whole, not recolored to the disabled palette.
        colors = ListItemDefaults.segmentedColors(
            containerColor = container,
            disabledContainerColor = container,
            disabledContentColor = segmented.contentColor,
            disabledLeadingContentColor = segmented.leadingContentColor,
            disabledTrailingContentColor = segmented.trailingContentColor,
            disabledSupportingContentColor = segmented.supportingContentColor,
        ),
        contentPadding = PaddingValues(horizontal = RowInset, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (layout.tablet) {
                TabletRowContent(row, layout, recording, playback, offline)
            } else {
                PhoneRowContent(row, recording, playback)
            }
        }
    }
}

@Composable
private fun ExpandButton(row: ChannelRow, expanded: Boolean, offline: Boolean, toggle: () -> Unit) {
    Box(Modifier.width(ExpandTarget), contentAlignment = Alignment.CenterEnd) {
        if (row.details != null || offline) {
            val label = if (expanded) R.string.channels_collapse else R.string.channels_expand
            val scheme = MaterialTheme.colorScheme
            // ListItem counts its row padding towards the trailing touch target, but a tap there means Watch,
            // so the button keeps its own full target.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides ExpandTarget) {
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
}

@Composable
private fun RowScope.TabletRowContent(
    row: ChannelRow,
    layout: ChannelsLayout,
    recording: ChannelRecording?,
    playback: ChannelPlayback?,
    offline: Boolean,
) {
    Row(
        nameWeight(layout).padding(end = ItemSpacing), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.name, Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleMedium, maxLines = 2,
        )
        if (recording == ChannelRecording.Channel) RecordingBadge(recording, compact = true)
    }
    TabletNow(row.now, playback, recording, nowWeight(layout).padding(end = ItemSpacing))
    if (layout.showNext) {
        NextProgram(row.next, !offline, nextWeight())
    }
    Box(Modifier.width(PlayingSlot), contentAlignment = Alignment.CenterStart) {
        if (playback != null) PlayingBars()
    }
}

@Composable
private fun RowScope.PhoneRowContent(
    row: ChannelRow,
    recording: ChannelRecording?,
    playback: ChannelPlayback?,
) {
    Column(Modifier.weight(1f)) {
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

private fun channelRowLogo(row: ChannelRow, layout: ChannelsLayout, showSlot: Boolean): (@Composable () -> Unit)? {
    val size = when (layout) {
        ChannelsLayout.Wide, ChannelsLayout.Upright -> Modifier.size(112.dp, 56.dp)
        ChannelsLayout.Phone -> Modifier.size(88.dp, 48.dp)
    }
    return channelLogoSlot(row.logo, row.logoTint, size, model = row.logoModel, showSlot = showSlot)
}

// The logo sits in the list item's leading slot, so these weights split what remains after it; they keep the
// On now and Next columns where the drawings put them.
private fun RowScope.nameWeight(layout: ChannelsLayout) = Modifier.weight(if (layout.showNext) 2.04f else 2.82f)
private fun RowScope.nowWeight(layout: ChannelsLayout) = Modifier.weight(if (layout.showNext) 4.32f else 7.18f)
private fun RowScope.nextWeight() = Modifier.weight(3.64f)

private val ChannelsLayout.logoWidth
    get() = when (this) {
        ChannelsLayout.Wide, ChannelsLayout.Upright -> 112.dp
        ChannelsLayout.Phone -> 88.dp
    }

// The gap ListItem puts between its leading or trailing slot and the content.
private val ItemSpacing = 12.dp
private val ExpandTarget = 48.dp
private val RowInset = 10.dp
private val PlayingSlot = 30.dp
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
            val timeLeft = program?.timeLeft
            val track = Modifier.weight(1f, fill = false)
            ChannelProgress(
                program, playback, recording,
                (if (timeLeft == null) track.widthIn(max = 136.dp) else track).fillMaxWidth(),
            )
            if (timeLeft != null) {
                SupportingLine(
                    stringResource(R.string.channels_time_left, timeLeft),
                    Modifier.widthIn(min = timeLeftSlotWidth()),
                    maxLines = Int.MAX_VALUE,
                )
            }
        }
    }
}

@Composable
private fun timeLeftSlotWidth(): Dp {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val sample = remember(resources, locale) { formatNarrowDuration(2 * 3_600 + 46 * 60L, resources) }
    val text = stringResource(R.string.channels_time_left, sample)
    val style = MaterialTheme.typography.bodySmall
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    return remember(text, style, density, direction, measurer, locale) {
        // A space of headroom absorbs small glyph-width differences among ordinary durations.
        with(density) { measurer.measure("$text\u00a0", style, maxLines = 1).size.width.toDp() }
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
                    painterResource(program.ruleSchedule?.icon ?: R.drawable.channels_schedule),
                    stringResource(program.ruleSchedule?.markDescription ?: R.string.channels_scheduled),
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
        program?.start?.let { SupportingLine(it, Modifier.padding(top = 3.dp)) }
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
    val clock = LocalChannelProgressClock.current
    val airing = program?.airing
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        playback != null -> watchedProgressColor()
        recording == ChannelRecording.Program -> TvRecordingColor
        else -> scheme.primary
    }
    LinearProgressIndicator(
        progress = {
            if (clock == null || airing == null) fraction else {
                val duration = airing.last + 1 - airing.first
                (clock() - airing.first).coerceIn(0L, duration).toFloat() / duration
            }
        }, modifier = modifier.height(4.dp), color = fill,
        trackColor = scheme.onSurface.copy(alpha = TRACK_ALPHA), gapSize = 0.dp, drawStopIndicator = {},
    )
}

internal val LocalChannelProgressClock = staticCompositionLocalOf<(() -> Long)?> { null }

/** REC after a running recording's title; the Guide overview's leading program takes it too (section 13). */
@Composable
internal fun RecordingBadge(recording: ChannelRecording, compact: Boolean = false) {
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
private fun SupportingLine(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(
        text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

private const val TRACK_ALPHA = 0.2f
