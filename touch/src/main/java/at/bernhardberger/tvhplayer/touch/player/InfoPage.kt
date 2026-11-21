package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.guide.ChannelDay
import at.bernhardberger.tvhplayer.touch.guide.DayBar
import at.bernhardberger.tvhplayer.touch.guide.DayLoading
import at.bernhardberger.tvhplayer.touch.guide.GuideActions
import at.bernhardberger.tvhplayer.touch.guide.GuideAvailability
import at.bernhardberger.tvhplayer.touch.guide.GuideFirstLoad
import at.bernhardberger.tvhplayer.touch.guide.GuideLayout
import at.bernhardberger.tvhplayer.touch.guide.GuideOfflineBanner
import at.bernhardberger.tvhplayer.touch.guide.GuideProgram
import at.bernhardberger.tvhplayer.touch.guide.NoSchedule
import at.bernhardberger.tvhplayer.touch.guide.ProgramDetailsColumn
import at.bernhardberger.tvhplayer.touch.ui.ChannelLogoTile
import at.bernhardberger.tvhplayer.touch.ui.ListFailure
import at.bernhardberger.tvhplayer.touch.ui.ListMessage
import at.bernhardberger.tvhplayer.touch.ui.MessageKind

/** The Info sheet's pages, hoisted in the player. */
internal class InfoPage(val stack: InfoStack, val onStack: (InfoStack) -> Unit) {
    /** Details of [id] over the current page, reported so the caller can load them. */
    fun openDetails(id: String, onPanel: (PlayerPanel?) -> Unit, onChoice: (PlayerChoice) -> Unit) {
        onStack(stack.push(id).copy(followWatching = false))
        onPanel(PlayerPanel.InfoDetails)
        onChoice(PlayerChoice.Details(id))
    }

    /** One page back: the previous Details, or the day list after the first. */
    fun back(onPanel: (PlayerPanel?) -> Unit) {
        val previous = stack.back()
        onStack(previous)
        if (previous.details.isEmpty()) onPanel(PlayerPanel.Info)
    }
}

/**
 * Info: the watched channel's day as the Guide's By channel view shows it, opened on the program being
 * watched, or that program's details in the sheet (section 6). Both use the Guide's compact phone parts,
 * as the drawings do in the tablet's side sheet too. Nothing here plays, so there is no Watch.
 */
@Composable
@Suppress("LongParameterList")
internal fun ColumnScope.InfoBody(
    panel: PlayerPanel,
    schedule: ChannelSchedule?,
    info: InfoPage,
    onPanel: (PlayerPanel?) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
    onRetry: () -> Unit,
) {
    if (schedule == null) {
        GuideFirstLoad(GuideLayout.Phone)
        return
    }
    when (val availability = schedule.availability) {
        GuideAvailability.Loading -> GuideFirstLoad(GuideLayout.Phone)
        GuideAvailability.SignInNeeded -> ListMessage(MessageKind.SignInNeeded, onSettings = null)
        is GuideAvailability.Failed -> ListFailure(availability.error, onRetry, onSettings = null)
        GuideAvailability.Offline, GuideAvailability.Current -> {
            if (availability == GuideAvailability.Offline) GuideOfflineBanner(GuideLayout.Phone, onSettings = null)
            InfoSchedule(panel, schedule, info, onPanel, onChoice)
        }
    }
}

@Composable
private fun ColumnScope.InfoSchedule(
    panel: PlayerPanel,
    schedule: ChannelSchedule,
    info: InfoPage,
    onPanel: (PlayerPanel?) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
) {
    val actions = GuideActions(
        onWatch = null,
        onRecord = null,
        onRecordOptions = null,
        onDay = { onChoice(PlayerChoice.Day(it)) },
        onJump = {},
        onTag = {},
        onShowInGuide = null,
        onAiring = { info.openDetails(it, onPanel, onChoice) },
    )
    if (panel == PlayerPanel.InfoDetails) {
        val details = info.stack.shown?.let(schedule.details)
        if (details == null) {
            NoSchedule(onWatch = null)
            return
        }
        // Each page starts at its top: an airing's Details is a new page, not the last one scrolled.
        key(details.id) {
            ProgramDetailsColumn(
                details, actions,
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                inPlayer = true,
            )
        }
        return
    }
    InfoDay(schedule, info, actions) { info.openDetails(it, onPanel, onChoice) }
}

@Composable
private fun ColumnScope.InfoDay(
    schedule: ChannelSchedule,
    info: InfoPage,
    actions: GuideActions,
    onDetails: (String) -> Unit,
) {
    DayBar(schedule.days, schedule.selectedDay, schedule.date) { actions.onDay(it) }
    when {
        schedule.dayLoading && schedule.programs.isEmpty() && schedule.availability == GuideAvailability.Current ->
            DayLoading(Modifier.weight(1f))
        schedule.programs.isEmpty() -> NoSchedule(onWatch = null)
        else -> key(schedule.selectedDay) {
            val open = if (info.stack.followWatching) schedule.watchingId else info.stack.open
            ChannelDay(
                schedule.programs, open, { info.onStack(InfoStack(it)) }, GuideLayout.Phone, actions, onDetails,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                listState = rememberLazyListState(itemIndex(schedule.programs, open)),
                watch = false,
            )
        }
    }
}

/** The sheet head's channel logo, sized as the Guide's channel head draws it. */
@Composable
internal fun InfoLogo(logo: Painter?, tint: Color?, model: Any? = null) {
    ChannelLogoTile(logo, tint, Modifier.size(64.dp, 36.dp), RoundedCornerShape(8.dp), model)
}

/** [ChannelDay]'s list index of the program [id]: each program is one item, a date heading one more. */
private fun itemIndex(programs: List<GuideProgram>, id: String?): Int {
    val at = programs.indexOfFirst { it.id == id }
    if (at < 0) return 0
    return programs.take(at).sumOf { if (it.section != null) 2 else 1 } + if (programs[at].section != null) 1 else 0
}
