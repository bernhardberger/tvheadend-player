// The overview: the tablet's magazine of channel columns and the phone's one row per channel.
// Sizes are the drawings' (round 37).
@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.guide

import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.ScrollToStartOnReselect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelRecording
import at.bernhardberger.tvhplayer.touch.channels.RecordingBadge
import at.bernhardberger.tvhplayer.touch.ui.channelLogoSlot
import at.bernhardberger.tvhplayer.touch.ui.PartsLine
import at.bernhardberger.tvhplayer.touch.ui.rememberStandalonePicture
import at.bernhardberger.tvhplayer.touch.ui.SkeletonLines
import at.bernhardberger.tvhplayer.touch.ui.SkeletonRow

/** The day menu and the jump switch above the overview. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OverviewBar(state: GuideState, actions: GuideActions, compact: Boolean, modifier: Modifier) {
    // The phone's bar holds the day menu and three jump times in 380dp: tighter padding keeps every label whole.
    val padding = if (compact) CompactButtonPadding else ButtonDefaults.ContentPadding
    Row(
        modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuButton(
            R.drawable.calendar_month_outline, state.days, state.selectedDay, actions.onDay, contentPadding = padding,
        )
        if (compact) Spacer(Modifier.weight(1f))
        val jumps = listOf(stringResource(R.string.guide_now)) + state.jumps
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            jumps.forEachIndexed { index, jump ->
                FilledTonalToggleButton(
                    checked = index == state.selectedJump,
                    onCheckedChange = { actions.onJump(index) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        jumps.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = padding,
                ) {
                    Text(jump, maxLines = 1)
                }
            }
        }
    }
}

/** The tablet's magazine: one column per channel, each scrolling on its own. */
@Composable
internal fun OverviewColumns(
    state: GuideState,
    layout: GuideLayout,
    openId: String?,
    onOpen: (String?) -> Unit,
    onChannel: (String) -> Unit,
    actions: GuideActions,
    onDetails: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val channels = state.channels
    val width = if (layout == GuideLayout.Wide) 264.dp else 232.dp
    val list = rememberOverviewListState(channels, actions.onOverviewViewport)
    list.ScrollToStartOnReselect(Destination.Guide)
    LazyRow(
        Modifier.fillMaxSize(), state = list,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(channels.size, key = { channels[it].id }) { index ->
            val channel = channels[index]
            val scroll = rememberScrollState()
            Column(Modifier.width(width).fillParentMaxHeight()) {
                ColumnHead(channel) { onChannel(channel.id) }
                if (channel.overviewPending || state.dayLoading && channel.programs.isEmpty()) {
                    OverviewRowSkeleton(compact = false)
                    return@Column
                }
                Column(
                    Modifier.fillMaxHeight().verticalScroll(scroll).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    channel.programs.forEachIndexed { position, program ->
                        val shape = segmentShape(position, channel.programs.size + if (channel.gap != null) 1 else 0)
                        ColumnItem(program, position == 0, program.id == openId, shape, actions, onOpen, onDetails)
                    }
                    channel.gap?.let { GapItem(it, segmentShape(channel.programs.size, channel.programs.size + 1)) }
                }
            }
        }
    }
}

/** The column's head opens the channel's page. */
@Composable
private fun ColumnHead(channel: GuideChannel, onClick: () -> Unit) {
    val description = stringResource(R.string.guide_open_channel, channel.name)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val logo = channelLogoSlot(
            channel.logo, channel.logoTint, Modifier.size(72.dp, 40.dp), RoundedCornerShape(10.dp), channel.logoModel,
        )
        logo?.invoke()
        Text(
            channel.name, Modifier.weight(1f).padding(start = if (logo != null) 12.dp else 0.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Icon(painterResource(R.drawable.connect_next_outline), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A program's title, and REC after it when no picture carries the recording's mark. */
@Composable
private fun TitleWithRecording(title: String, style: TextStyle, maxLines: Int, recording: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, Modifier.weight(1f, fill = false), style = style, maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (recording) RecordingBadge(ChannelRecording.Program)
    }
}

@Composable
private fun ColumnItem(
    program: GuideProgram,
    lead: Boolean,
    open: Boolean,
    shape: RoundedCornerShape,
    actions: GuideActions,
    onOpen: (String?) -> Unit,
    onDetails: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = { onOpen(if (open) null else program.id) },
        modifier = rememberOpenedRowReveal(open),
        shape = shape,
        color = if (open) scheme.surfaceContainerHigh else scheme.surfaceContainer,
    ) {
        Column {
            val leadNow = lead && program.onNow
            val picture = if (leadNow) rememberStandalonePicture(program.still, program.stillModel) else null
            // Without its picture the leading program keeps the bar and REC under and after its text (section 13).
            val bare = leadNow && picture == null
            if (picture != null) {
                ProgramPicture(
                    picture, Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp).fillMaxWidth(),
                    program.progress, program.recording, watching = program.watching,
                )
            }
            Row(Modifier.padding(start = 12.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)) {
                Text(
                    program.start, Modifier.width(68.dp).padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1,
                )
                Column(Modifier.weight(1f)) {
                    val type = MaterialTheme.typography
                    TitleWithRecording(
                        program.title, if (lead) type.titleMedium else type.titleSmall, maxLines = 2,
                        recording = bare && program.recording,
                    )
                    PartsLine(
                        if (open) program.openLine() else program.rowLine(),
                        style = if (lead) type.bodyMedium else type.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
                if (program.scheduled) ScheduledIcon(program.ruleSchedule)
            }
            program.progress?.takeIf { bare }?.let {
                ProgramBar(
                    it, program.recording,
                    Modifier.padding(start = 12.dp, end = 10.dp, bottom = 10.dp).fillMaxWidth(),
                    program.watching,
                )
            }
            if (open) OpenInColumn(program, actions, phone = false) { onDetails(program.id) }
        }
    }
}

/** Opened in a narrow column: the synopsis and the actions, which wrap to a second line. */
@Composable
private fun OpenInColumn(program: GuideProgram, actions: GuideActions, phone: Boolean, onDetails: () -> Unit) {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        program.episode?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        program.synopsis?.let {
            Text(
                it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The tablet column writes Details as text beside Record, the phone row keeps its icon, as drawn; both
        // need the tighter padding for Details to share Record's line, and it wraps under Record where it cannot.
        ProgramActions(
            program.channelId, program.id, program.onNow, program.record, actions, Modifier.padding(top = 12.dp),
            onDetails, program.ruleSchedule,
            detailsIcon = phone, compact = true,
        )
    }
}

@Composable
private fun GapItem(gap: GuideGap, shape: RoundedCornerShape) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)) {
            Text(
                gap.start, Modifier.width(76.dp).padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
            Column {
                Text(
                    stringResource(R.string.guide_no_programs), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.guide_back_at, gap.until), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The segmented list's shapes as plain corners: large at the ends of a column, small between. */
private fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val outer: Dp = 16.dp
    val inner: Dp = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner, topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner, bottomEnd = if (index == count - 1) outer else inner,
    )
}

/** The phone's overview: a row per channel with what is on at the jump time, opening in place. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OverviewRows(
    state: GuideState,
    openId: String?,
    onOpen: (String?) -> Unit,
    actions: GuideActions,
    onDetails: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val channels = state.channels
    val rows = remember(channels, state.dayLoading) {
        if (state.dayLoading) channels else channels.filter { it.programs.isNotEmpty() || it.overviewHasLead }
    }
    val list = rememberOverviewListState(rows, actions.onOverviewViewport)
    list.ScrollToStartOnReselect(Destination.Guide)
    LazyColumn(
        Modifier.fillMaxSize(), state = list,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(rows, key = { _, channel -> channel.id }) { index, channel ->
            val program = channel.programs.firstOrNull()
            if (program == null) {
                OverviewRowSkeleton(compact = true)
                return@itemsIndexed
            }
            val open = program.id == openId
            val shape = segmentShape(index, rows.size)
            val picture = rememberStandalonePicture(program.still, program.stillModel)
            Surface(
                modifier = rememberOpenedRowReveal(open),
                shape = shape,
                color = MaterialTheme.colorScheme.run { if (open) surfaceContainerHigh else surfaceContainer },
            ) {
                Column {
                    ListItem(
                        onClick = { onOpen(if (open) null else program.id) },
                        colors = ListItemDefaults.segmentedColors(containerColor = Color.Transparent),
                        leadingContent = channelLogoSlot(
                            channel.logo, channel.logoTint, Modifier.size(88.dp, 48.dp),
                            model = channel.logoModel, showSlot = state.overviewHasLogos,
                        ),
                        overlineContent = { PartsLine(listOf(channel.name, program.start)) },
                        supportingContent = { OverviewSupportingContent(program, picture != null) },
                        trailingContent = picture?.let {
                            {
                                ProgramPicture(
                                    it, Modifier.width(96.dp), program.progress, program.recording,
                                    watching = program.watching,
                                )
                            }
                        },
                    ) {
                        // Without the thumbnail, REC stands after the title as in a Channels row.
                        TitleWithRecording(
                            program.title, MaterialTheme.typography.titleMedium, maxLines = 1,
                            recording = picture == null && program.recording,
                        )
                    }
                    if (open) {
                        Box(Modifier.padding(horizontal = 4.dp)) {
                            OpenInColumn(program, actions, phone = true) { onDetails(program.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewSupportingContent(program: GuideProgram, hasPicture: Boolean) {
    Column {
        PartsLine(program.rowLine(), style = MaterialTheme.typography.bodyMedium)
        // Without its picture, the row keeps the progress bar under the text.
        program.progress?.takeUnless { hasPicture }?.let {
            ProgramBar(
                it, program.recording,
                Modifier.padding(top = 4.dp).fillMaxWidth(), program.watching,
            )
        }
    }
}

@Composable
private fun rememberOverviewListState(channels: List<GuideChannel>, onViewport: ((List<String>) -> Unit)?) =
    rememberLazyListState().also { state ->
        val latestChannels = rememberUpdatedState(channels)
        val emit = rememberUpdatedState(onViewport)
        LaunchedEffect(state) {
            snapshotFlow {
                val rows = latestChannels.value
                val visible = state.layoutInfo.visibleItemsInfo
                visible.takeIf { items ->
                    items.any { item ->
                        rows.getOrNull(item.index)?.let { it.id == item.key && it.overviewPending } == true
                    }
                }?.mapNotNull { it.key as? String }
            }.collect { ids -> if (ids != null) emit.value?.invoke(ids) }
        }
    }

@Composable
private fun OverviewRowSkeleton(compact: Boolean) {
    SkeletonRow(0, 1, if (compact) 88.dp else 72.dp) { SkeletonLines(0) }
}

@Composable
private fun rememberOpenedRowReveal(open: Boolean): Modifier {
    if (!open) return Modifier
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    // Use the expanded bounds, not the closed row's bounds from before remeasurement.
    return remember(requester, scope) {
        var requested = false
        Modifier.bringIntoViewRequester(requester).onGloballyPositioned {
            if (!requested) {
                requested = true
                scope.launch { requester.bringIntoView() }
            }
        }
    }
}
