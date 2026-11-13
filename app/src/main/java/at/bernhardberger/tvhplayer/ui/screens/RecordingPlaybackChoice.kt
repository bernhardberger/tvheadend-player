package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.common.*
import at.bernhardberger.tvhplayer.ui.components.*

/** Shared recording entry choice. The caller retains session authority and owns playback. */
@Composable
internal fun RecordingPlaybackChoice(
    entry: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    observation: SessionObservation,
    channel: Channel?,
    nowSec: Long,
    onPlay: (RecordingPlaybackStart) -> Unit,
    onClose: () -> Unit,
    onWatchLive: (() -> Unit)? = null,
    backEnabled: Boolean = true,
) {
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val resumeFocus = remember { FocusRequester() }
    val liveFocus = remember { FocusRequester() }
    val startFocus = remember { FocusRequester() }
    val playable = recordingPlaybackOffered(admission)
    val deferred = admission == RecordingPlaybackAdmission.GrowingDeferred
    val resumePosition = offeredResumePosition(admission)
    val positionMs = resumePosition?.inWholeMilliseconds ?: 0L
    val firstFocus = when {
        !playable -> liveFocus
        resumePosition != null -> resumeFocus
        else -> startFocus
    }
    val order = listOfNotNull(resumeFocus.takeIf { resumePosition != null }, startFocus.takeIf { playable },
        liveFocus.takeIf { onWatchLive != null })
    // The stack runs top to bottom and stops at both ends; nothing leaves the card sideways.
    fun Modifier.stacked(self: FocusRequester) = focusRequester(self).focusProperties {
        val index = order.indexOf(self)
        up = order.getOrNull(index - 1) ?: FocusRequester.Cancel
        down = order.getOrNull(index + 1) ?: FocusRequester.Cancel
        left = FocusRequester.Cancel
        right = FocusRequester.Cancel
    }
    // A growing recording plays on to its end, so it counts its final length, not what is on disk.
    val durationMs = (recordingFinalLengthSeconds(entry, nowSec) ?: recordingDetailsFacts(entry, admission, nowSec)
        .firstOrNull { it.kind == DetailsFactKind.LENGTH }?.seconds)?.times(1000L)
    val resumeEnd = recordingEndsAtSec(nowSec, positionMs, durationMs)
        ?.let { stringResource(R.string.recording_ends_at, formatClock(it)) }
    val startEnd = recordingEndsAtSec(nowSec, 0L, durationMs)
        ?.let { stringResource(R.string.recording_ends_at, formatClock(it)) }
    val spokenResume = stringResource(R.string.recording_resume_from,
        formatDurationMinutes(positionMs / 60_000L, resources, spoken = true))
    val title = entry.title.orEmpty()
    CentredDialogShell(paneTitle = title, onDismiss = onClose, backEnabled = backEnabled, verticalKeys = true) {
        LaunchedEffect(firstFocus, order) {
            if (firstFocus in order) { withFrameNanos { }; firstFocus.requestFocus() }
        }
        ProvideArtworkSession(observation.currentSession, observation) {
            PlaybackChoiceCard(
                art = recordingArtwork(entry),
                picon = channel?.icon,
                channel = (channel?.let { channelTitleText(it.number, it.name.orEmpty()) } ?: entry.channelName).orEmpty(),
                airTime = recordingAirTime(entry, locale, nowSec),
                title = title,
                episode = programmeEpisodeLine(entry.episode, entry.subtitle, resources),
                recording = recordingCardState(entry, nowSec),
                waiting = if (deferred) stringResource(R.string.error_305_reason) else null,
                buttonCount = order.size,
                piconModifier = Modifier.testTag("recording-resume-picon"),
            ) {
                if (resumePosition != null) PlaybackChoiceButton(
                    stringResource(R.string.recording_resume_from, formatPlaybackDuration(positionMs)),
                    R.drawable.ic_play_arrow, resumeEnd, filled = true,
                    onClick = { onPlay(RecordingPlaybackStart.RESUME) },
                    modifier = Modifier.stacked(resumeFocus).semantics { contentDescription = spokenResume }
                        .testTag("recording-resume"),
                )
                if (playable) PlaybackChoiceButton(
                    stringResource(if (resumePosition == null) R.string.recording_choice_play else R.string.context_start_over),
                    if (resumePosition == null) R.drawable.ic_play_arrow else R.drawable.ic_replay,
                    startEnd, filled = resumePosition == null,
                    onClick = { onPlay(RecordingPlaybackStart.START_OVER) },
                    modifier = Modifier.stacked(startFocus).testTag("recording-start-over"),
                )
                if (onWatchLive != null) PlaybackChoiceButton(
                    stringResource(R.string.context_watch_live), R.drawable.ic_live_tv_outlined, null, filled = false,
                    onClick = onWatchLive,
                    modifier = Modifier.stacked(liveFocus).testTag("recording-watch-live"),
                )
            }
        }
    }
}

private fun recordingAirTime(entry: DvrEntry, locale: java.util.Locale, nowSec: Long): String? {
    val start = entry.start?.epochSeconds ?: return null
    val stop = entry.stop?.epochSeconds ?: return start.formatDateTime(locale)
    return formatTimeInterval(start, stop, withDate = true, locale = locale, nowSec = nowSec)
}

/** Only while the server is still recording: how much is on disk of what is planned. */
@Composable
private fun recordingCardState(entry: DvrEntry, nowSec: Long): PlaybackCardRecording? {
    if (!recordingIsRunning(entry)) return null
    val resources = LocalContext.current.resources
    val now = stringResource(R.string.recordings_recording_now)
    val recorded = recordingLengthSeconds(entry, nowSec)
    // The same total as "Ends at", so what is recorded never passes it.
    val finalLength = recordingFinalLengthSeconds(entry, nowSec)
    val planned = finalLength ?: recordingPlannedSeconds(entry)
    fun line(spoken: Boolean) = if (recorded == null || planned == null) now else
        "$now · " + resources.getString(R.string.recording_recorded_of,
            formatRecordedDuration(recorded, resources, spoken), formatRecordedDuration(planned, resources, spoken))
    // The bar sits under those two numbers, so it shows the same share.
    val share = if (recorded != null && finalLength != null && finalLength > 0L) {
        (recorded.toFloat() / finalLength).coerceIn(0f, 1f)
    } else recordingElapsedProgress(entry, nowSec)
    return PlaybackCardRecording(line(spoken = false), line(spoken = true), share ?: 0f)
}
