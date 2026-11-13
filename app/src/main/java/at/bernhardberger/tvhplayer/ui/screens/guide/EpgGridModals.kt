package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrEventEntries
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.programmeArtwork
import at.bernhardberger.tvhplayer.core.recordingElapsedProgress
import at.bernhardberger.tvhplayer.ui.components.ConfirmationChoice
import at.bernhardberger.tvhplayer.ui.components.ConfirmationDialog
import at.bernhardberger.tvhplayer.ui.components.ConfirmationSubject
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.programmeProgress
import at.bernhardberger.tvhplayer.ui.screens.recordings.StopConfirmationTimes
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingConfirmationAirTime
import at.bernhardberger.tvhplayer.ui.screens.recordings.stopConfirmationSentence
import at.bernhardberger.tvhplayer.ui.screens.recordings.stopConfirmationTimes

@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@Composable
internal fun ConfirmProgrammeActionDialog(
    action: ProgrammeAction,
    event: EpgEvent,
    channel: Channel?,
    nowSec: Long,
    currentSession: CurrentSessionObservation?,
    observation: SessionObservation?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    entries: DvrEventEntries? = null,
    configurations: List<DvrConfiguration> = emptyList(),
    signedInUsername: String? = null,
    visibility: androidx.compose.animation.core.MutableTransitionState<Boolean> = remember {
        androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
    },
) {
    val target = programmeConfirmationTarget(entries, configurations,
        stringResource(R.string.recording_profile_default), signedInUsername)
    val stopping = action == ProgrammeAction.STOP_RECORDING
    val times = entries?.selected?.let { stopConfirmationTimes(it, nowSec) }
        ?: StopConfirmationTimes((nowSec - event.start.epochSeconds).coerceAtLeast(0L),
            (event.stop.epochSeconds - nowSec).takeIf { it > 0L })
    val resources = LocalContext.current.resources
    ProvideArtworkSession(currentSession, observation) {
        ConfirmationDialog(
            visibility = visibility,
            question = stringResource(if (stopping) R.string.stop_recording_confirm_title else R.string.cancel_recording_confirm_title),
            subject = ConfirmationSubject.Programme(
                name = event.title.orEmpty(), image = programmeArtwork(event),
                channelIcon = channel?.icon, channelNumber = channel?.number, channelName = channel?.name.orEmpty(),
                airTime = recordingConfirmationAirTime(event.start.epochSeconds, event.stop.epochSeconds, nowSec),
                progress = if (stopping) entries?.selected?.let { recordingElapsedProgress(it, nowSec) }
                    ?: programmeProgress(event, nowSec) else null,
                target = target?.let { stringResource(it.first, it.second) },
            ),
            sentence = if (stopping) stopConfirmationSentence(times, resources, spoken = false)
                else stringResource(R.string.cancel_recording_confirm_body),
            spokenSentence = if (stopping) stopConfirmationSentence(times, resources, spoken = true) else null,
            safe = ConfirmationChoice(stringResource(R.string.keep_recording), R.drawable.ic_check, "two-column-first"),
            action = ConfirmationChoice(programmeActionLabel(action),
                if (stopping) R.drawable.ic_stop else R.drawable.ic_close, "two-column-second"),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            onPreviewKeyEvent = onPreviewKeyEvent,
        )
    }
}

@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
internal fun programmeConfirmationTarget(
    entries: at.bernhardberger.tvheadend.sdk.core.DvrEventEntries?,
    configurations: List<DvrConfiguration>,
    defaultProfileLabel: String,
    signedInUsername: String? = null,
): Pair<Int, String>? {
    if (entries == null || entries.otherActive.isEmpty()) return null
    val selected = entries.selected
    val profileId = selected.configId?.value?.takeIf(String::isNotBlank)
    if (entries.otherActive.any { it.configId?.value?.takeIf(String::isNotBlank) != profileId }) {
        val name = configurations.firstOrNull { it.id == selected.configId }?.name?.takeIf(String::isNotBlank)
            ?: defaultProfileLabel
        return R.string.recording_confirmation_profile to name
    }
    val owner = selected.owner?.takeIf(String::isNotBlank) ?: selected.creator?.takeIf(String::isNotBlank)
        ?: return null
    if (owner == signedInUsername) return null
    return R.string.recording_confirmation_owner to owner
}

@Composable
internal fun programmeActionLabel(action: ProgrammeAction): String = stringResource(
    when (action) {
        ProgrammeAction.WATCH -> R.string.watch
        ProgrammeAction.RECORD -> R.string.record
        ProgrammeAction.CANCEL_RECORDING -> R.string.cancel_recording
        ProgrammeAction.STOP_RECORDING -> R.string.stop_recording
        ProgrammeAction.WATCH_FROM_START -> R.string.watch_from_start
    }
)
