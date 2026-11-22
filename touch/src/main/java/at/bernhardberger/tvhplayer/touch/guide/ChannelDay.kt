// One channel's day: By channel shows it beside the channels; the player's Info sheet reuses it.
// Sizes are the drawings' (round 37).
@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.PartsLine
import at.bernhardberger.tvhplayer.touch.ui.rememberStandalonePicture
import at.bernhardberger.tvhplayer.touch.ui.RuleSchedule
import kotlinx.coroutines.launch

/**
 * The programs of one channel's day in start order, with a date heading where the night crosses
 * midnight. A tap opens a program in place ([openId]); a tap on the opened one folds it again.
 * Nothing here plays: Watch is the opened program's button; the player, already showing it, passes [watch] false.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChannelDay(
    programs: List<GuideProgram>,
    openId: String?,
    onOpen: (String?) -> Unit,
    layout: GuideLayout,
    actions: GuideActions,
    onDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp),
    watch: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val reveal = remember(openId, scope) {
        val requester = BringIntoViewRequester()
        var requested = false
        Modifier.bringIntoViewRequester(requester).onGloballyPositioned {
            if (!requested) {
                requested = true
                scope.launch { requester.bringIntoView() }
            }
        }
    }
    LazyColumn(
        modifier, state = listState, contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        programs.forEachIndexed { index, program ->
            program.section?.let { section ->
                item(key = "section-${program.id}") {
                    Text(
                        section, Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = program.id) {
                if (program.id == openId) {
                    OpenProgram(program, layout, actions, watch, reveal, onCollapse = { onOpen(null) }) {
                        onDetails(program.id)
                    }
                } else {
                    DayRow(program, layout, dayShapes(programs, index)) { onOpen(program.id) }
                }
            }
        }
    }
}

/** Segments run from one date heading to the next, as the drawings group them. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun dayShapes(programs: List<GuideProgram>, index: Int) = run {
    val start = (index downTo 0).first { it == 0 || programs[it].section != null }
    val end = (index + 1 until programs.size).firstOrNull { programs[it].section != null } ?: programs.size
    ListItemDefaults.segmentedShapes(index - start, end - start)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayRow(
    program: GuideProgram,
    layout: GuideLayout,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val wide = layout == GuideLayout.Wide
    ListItem(
        onClick = onClick,
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            Text(
                program.start, Modifier.width(if (layout == GuideLayout.Upright) 62.dp else 72.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
        supportingContent = { PartsLine(program.rowLine()) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (program.scheduled) ScheduledIcon(program.ruleSchedule)
                rememberStandalonePicture(program.still, program.stillModel)?.let {
                    ProgramPicture(it, Modifier.size(if (wide) 96.dp else 72.dp, if (wide) 54.dp else 40.dp))
                }
                Icon(painterResource(R.drawable.guide_expand_more), null)
            }
        },
    ) {
        Text(program.title, maxLines = if (wide) 1 else 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The program opened in its row: picture, time, episode, the bar while on air, synopsis, actions. */
@Composable
private fun OpenProgram(
    program: GuideProgram,
    layout: GuideLayout,
    actions: GuideActions,
    watch: Boolean,
    modifier: Modifier,
    onCollapse: () -> Unit,
    onDetails: () -> Unit,
) {
    val onNow = program.onNow && watch
    val wide = layout == GuideLayout.Wide
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rememberStandalonePicture(program.art ?: program.still, program.stillModel)?.let {
                        ProgramPicture(it, Modifier.width(layout.openPictureWidth), strip = false)
                    }
                    Column(Modifier.weight(1f)) {
                        OpenHead(program, layout)
                        // The compact card keeps the facts beside the picture and the progress bar below it.
                        if (layout.compact) OpenLine(program, Modifier.padding(top = 4.dp))
                        if (wide) {
                            OpenFacts(program, Modifier.padding(top = 4.dp))
                            ProgramActions(
                                program.channelId, program.id, onNow, program.record, actions,
                                Modifier.padding(top = 16.dp), onDetails, program.ruleSchedule,
                            )
                        }
                    }
                }
                when (layout) {
                    GuideLayout.Wide -> Unit
                    GuideLayout.Upright -> OpenFacts(program, Modifier.padding(top = 12.dp))
                    GuideLayout.Phone -> program.progress?.let {
                        ProgramProgress(
                            it, program.timeLeft, program.recording, Modifier.padding(top = 12.dp), program.watching,
                        )
                    }
                }
                program.synopsis?.let {
                    Text(
                        it, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!wide) {
                    ProgramActions(
                        program.channelId, program.id, onNow, program.record, actions, Modifier.padding(top = 16.dp),
                        onDetails, program.ruleSchedule, compact = true,
                    )
                }
            }
            IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(painterResource(R.drawable.guide_expand_less), stringResource(R.string.guide_collapse))
            }
        }
    }
}

private val GuideLayout.openPictureWidth
    get() = when (this) {
        GuideLayout.Wide -> 280.dp
        GuideLayout.Upright -> 148.dp
        GuideLayout.Phone -> 120.dp
    }

@Composable
private fun OpenHead(program: GuideProgram, layout: GuideLayout) {
    // Only the head line shares its height with the collapse button; the title below runs to the edge, as drawn.
    FlowRow(
        Modifier.padding(end = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (program.onNow) OnNowBadge()
        Text(
            program.timeRange ?: program.start,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
    Text(
        program.title, Modifier.padding(top = 8.dp),
        style = if (layout.compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
    program.episode?.let {
        Text(
            it, Modifier.padding(top = 2.dp),
            style = if (layout.compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OpenFacts(program: GuideProgram, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        OpenLine(program, Modifier)
        program.progress?.let {
            ProgramProgress(
                it, program.timeLeft, program.recording, Modifier.padding(top = 12.dp), program.watching,
            )
        }
    }
}

@Composable
private fun OpenLine(program: GuideProgram, modifier: Modifier) {
    val line = program.openLine()
    if (line.isNotEmpty()) {
        PartsLine(
            line, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
internal fun ScheduledIcon(ruleSchedule: RuleSchedule?) {
    Icon(
        painterResource(ruleSchedule?.icon ?: R.drawable.channels_schedule),
        stringResource(ruleSchedule?.markDescription ?: R.string.channels_scheduled),
        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The compact By channel bar: the day picker, and the date at the end. The player's Info sheet shows it too. */
@Composable
internal fun DayBar(days: List<String>, selectedDay: Int, date: String, margin: Dp = 12.dp, onDay: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = margin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuButton(R.drawable.calendar_month_outline, days, selectedDay, onDay)
        Spacer(Modifier.weight(1f))
        Text(
            date, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
