package at.bernhardberger.tvhplayer.touch.player

import android.content.res.Configuration
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import at.bernhardberger.tvhplayer.touch.ui.ConfirmDvrChange
import at.bernhardberger.tvhplayer.touch.ui.DvrControls
import at.bernhardberger.tvhplayer.touch.ui.ProgramRecordControl
import at.bernhardberger.tvhplayer.touch.ui.programRecordControl
import at.bernhardberger.tvhplayer.ui.screens.programmeRecordKeyAction
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel

internal data class LiveRecordOffer(
    val eventId: EventId,
    val control: ProgramRecordControl,
    val generation: SessionGenerationIdentity,
)

internal fun LivePlayerState.withControls(
    channel: ChannelRow,
    brightness: Float,
    paused: Boolean,
    options: PlayerOptions,
    schedule: ChannelSchedule?,
    record: LiveRecordOffer?,
): LivePlayerState = copy(
    channel = channel, brightness = brightness, paused = paused,
    tracks = options.tracks, options = options.options,
    offered = offered + options.offered + listOfNotNull(record?.let { PlayerControl.Record }),
    schedule = schedule, liveRecord = record,
)

/** The TV Record key uses the displayed program, not necessarily the one on air. */
internal fun liveRecordOffer(
    channel: ChannelId,
    programs: ShownPrograms,
    observation: SessionObservation,
    nowSec: Long,
): LiveRecordOffer? {
    val generation = observation.currentSession?.generationIdentity
    if (generation == null || observation.epgState !is EpgRepositoryState.Current) return null
    return programs.now?.let { observation.event(it.id) }?.takeIf { it.channelId == channel }?.let { event ->
        programRecordControl(programmeRecordKeyAction(event, observation, nowSec))?.let {
            LiveRecordOffer(event.id, it, generation)
        }
    }
}

internal fun TvheadendSession.currentLiveRecord(channel: ChannelId?, programs: ShownPrograms, nowSec: Long) =
    channel?.let { liveRecordOffer(it, programs, observation.value, nowSec) }

internal fun playerChoices(
    dvr: DvrControls,
    info: PlayerInfo,
    options: PlayerOptions,
    channels: ChannelsViewModel,
    currentLive: () -> LiveRecordOffer?,
): (PlayerChoice) -> Unit = choose@{ choice ->
    if (dvr.choose(choice, currentLive)) return@choose
    when (choice) {
        is PlayerChoice.Day -> info.onDay(choice.index)
        is PlayerChoice.ChannelTag -> channels.selectTag(choice.id?.let { ChannelTagId(it.toLong()) })
        else -> options.choose(choice)
    }
}

internal fun DvrControls.choose(choice: PlayerChoice, currentLive: () -> LiveRecordOffer?): Boolean {
    when (choice) {
        is PlayerChoice.Record -> actOnProgram(choice.programId, choice.control)
        is PlayerChoice.LiveRecord -> if (choice.offer == currentLive()) {
            actOnProgram(choice.offer.eventId.value.toString(), choice.offer.control)
        }
        else -> return false
    }
    return true
}

private fun DvrControls.actOnProgram(id: String, control: ProgramRecordControl) {
    when (control) {
        ProgramRecordControl.Record -> record(id)
        ProgramRecordControl.Cancel -> cancel(id)
        ProgramRecordControl.Stop -> stopProgram(id)
    }
}

@Composable
internal fun PlayerDvrConfirmation(controls: DvrControls, inWindow: Boolean) {
    if (inWindow) {
        LaunchedEffect(controls, controls.pending) { controls.dismiss() }
    } else {
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        ConfirmDvrChange(controls, hideSystemBars = landscape)
    }
}

@Composable
internal fun RecordAction(
    offer: LiveRecordOffer,
    label: Boolean,
    enabled: Boolean,
    colors: ButtonColors,
    onClick: () -> Unit,
) {
    val icon = offer.control.icon
    val name = offer.control.label
    if (label) {
        ActionButton(icon, name, true, enabled, colors, false, onClick)
    } else {
        IconButton(
            onClick = onClick, enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = colors.contentColor, disabledContentColor = colors.disabledContentColor,
            ),
        ) {
            Icon(painterResource(icon), stringResource(name), Modifier.size(20.dp))
        }
    }
}
