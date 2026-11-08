package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
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
    val resumeFocus = remember { FocusRequester() }
    val liveFocus = remember { FocusRequester() }
    val startFocus = remember { FocusRequester() }
    val resumePosition = offeredResumePosition(admission)
    val positionMs = resumePosition?.inWholeMilliseconds ?: 0L
    val firstFocus = if (resumePosition != null) resumeFocus else startFocus
    var startFocused by remember(entry.id) { mutableStateOf(resumePosition == null) }
    var liveFocused by remember(entry.id) { mutableStateOf(false) }
    val durationMs = recordingDetailsFacts(entry, admission, nowSec)
        .firstOrNull { it.kind == DetailsFactKind.LENGTH }?.seconds?.times(1000L)
    val resumeEnd = recordingEndsAtSec(nowSec, positionMs, durationMs)
        ?.let { stringResource(R.string.recording_ends_at, formatClock(it)) }.orEmpty()
    val startEnd = recordingEndsAtSec(nowSec, 0L, durationMs)
        ?.let { stringResource(R.string.recording_ends_at, formatClock(it)) }.orEmpty()
    val spokenResume = stringResource(R.string.recording_resume_from,
        formatDurationMinutes(positionMs / 60_000L, resources, spoken = true))
    CentredDialog(
        title = entry.title.orEmpty(),
        secondaryText = programmeEpisodeLine(entry.episode, entry.subtitle, resources),
        text = "",
        eyebrow = {
            ProvideArtworkSession(observation.currentSession, observation) {
                ProgrammeEyebrow(channel?.icon, listOfNotNull(
                    (channel?.let { channelTitleText(it.number, it.name.orEmpty()) }
                        ?: entry.channelName)?.takeIf(String::isNotBlank),
                    entry.start?.epochSeconds?.formatDateTime(LocalConfiguration.current.locales[0]),
                    (if (liveFocused) "" else if (startFocused) startEnd else resumeEnd).takeIf(String::isNotBlank),
                ).joinToString(" • "), Modifier.fillMaxWidth(), Modifier.testTag("recording-resume-picon"))
            }
        },
        onDismiss = onClose,
        backEnabled = backEnabled,
    ) {
        LaunchedEffect(firstFocus) { withFrameNanos { }; firstFocus.requestFocus() }
        if (resumePosition != null) Button(
            onClick = { onPlay(RecordingPlaybackStart.RESUME) },
            modifier = Modifier.height(40.dp).focusRequester(resumeFocus)
                .onFocusChanged { if (it.isFocused) { startFocused = false; liveFocused = false } }
                .semantics { contentDescription = spokenResume; if (resumeEnd.isNotBlank()) stateDescription = resumeEnd }
                .focusProperties { left = FocusRequester.Cancel; right = startFocus }
                .testTag("recording-resume"),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.recording_resume_from, formatPlaybackDuration(positionMs)), maxLines = 1, softWrap = false)
        }
        val startModifier = Modifier.height(40.dp).focusRequester(startFocus)
                .onFocusChanged { if (it.isFocused) { startFocused = true; liveFocused = false } }
                .semantics { if (startEnd.isNotBlank()) stateDescription = startEnd }
                .focusProperties { left = if (resumePosition != null) resumeFocus else FocusRequester.Cancel; right = if (onWatchLive != null) liveFocus else FocusRequester.Cancel }
                .testTag("recording-start-over")
        val startContent: @Composable RowScope.() -> Unit = {
            Icon(painterResource(if (resumePosition == null) R.drawable.ic_play_arrow else R.drawable.ic_replay), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(if (resumePosition == null) R.string.recording_choice_play else R.string.context_start_over), maxLines = 1, softWrap = false)
        }
        if (resumePosition == null) Button(
            onClick = { onPlay(RecordingPlaybackStart.START_OVER) }, modifier = startModifier,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding, content = startContent,
        ) else OutlinedButton(
            onClick = { onPlay(RecordingPlaybackStart.START_OVER) }, modifier = startModifier,
            contentPadding = OutlinedButtonDefaults.ButtonWithIconContentPadding, content = startContent,
        )
        if (onWatchLive != null) OutlinedButton(
            onClick = onWatchLive,
            modifier = Modifier.height(40.dp).focusRequester(liveFocus)
                .onFocusChanged { if (it.isFocused) { startFocused = false; liveFocused = true } }
                .focusProperties { left = startFocus; right = FocusRequester.Cancel }
                .testTag("recording-watch-live"),
            contentPadding = OutlinedButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_live_tv_outlined), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.context_watch_live), maxLines = 1, softWrap = false)
        }
    }
}
