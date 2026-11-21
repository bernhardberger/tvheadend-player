package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvIconButtonSize
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGap
import at.bernhardberger.tvhplayer.ui.components.recordingMarkColor
import at.bernhardberger.tvhplayer.ui.components.recordingStatusSpoken

/** Material disabled content alpha, used for a pause control that stays focusable. */
internal const val PauseUnavailableIconAlpha = TvTextDisabledAlpha

private data class PlayerAction(
    val tag: String,
    val label: String,
    @DrawableRes val icon: Int?,
    val onClick: (() -> Unit)?,
    val focus: FocusRequester?,
    /** The current state announced after the label. */
    val state: String? = null,
)

@Composable
internal fun PlayerActionRow(
    pauseFocus: FocusRequester,
    settingsFocus: FocusRequester,
    /** Play/Pause is always in the row; the caller decides what it does when playback cannot pause. */
    onTogglePause: () -> Unit,
    onSettings: () -> Unit,
    onStop: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    stopFocus: FocusRequester? = null,
    onRecord: (() -> Unit)? = null,
    recordFocus: FocusRequester? = null,
    recordState: DvrEntryState? = null,
    onActionFocused: (String) -> Unit = {},
    /** Dims the pause control, which stays focusable and clickable, and announces why it cannot pause. */
    pauseUnavailableReason: String? = null,
    onInfo: () -> Unit = {},
    infoFocus: FocusRequester? = null,
    onSchedule: (() -> Unit)? = null,
    scheduleAvailable: Boolean = true,
    scheduleFocus: FocusRequester? = null,
    /** Audio stands beside Info only while its list has a choice to make. */
    tracks: PlayerTrackButtons = PlayerTrackButtons(),
    onAudioList: () -> Unit = {},
    audioFocus: FocusRequester? = null,
    skip: PlayerSkipAction? = null,
    skipFocus: FocusRequester? = null,
) {
    val playPause = stringResource(if (paused) R.string.play else R.string.pause)
    val settings = stringResource(R.string.nav_settings)
    val stop = stringResource(R.string.stop_playback)
    val audio = stringResource(R.string.audio_quick_list)
    val actions = listOfNotNull(
        PlayerAction("player-pause", playPause,
            if (paused) R.drawable.ic_play_arrow else R.drawable.ic_pause, onTogglePause, pauseFocus),
        PlayerAction("player-stop", stop, R.drawable.ic_stop, onStop, stopFocus),
        onRecord?.let { PlayerAction("player-record", stringResource(R.string.record),
            R.drawable.ic_fiber_manual_record, it, recordFocus, recordingStatusSpoken(recordState, rule = null)) },
        tracks.audio?.let {
            PlayerAction("player-audio", audio, R.drawable.ic_audiotrack, onAudioList, audioFocus, it)
        },
        PlayerAction("player-info", stringResource(R.string.details_program), R.drawable.ic_info, onInfo, infoFocus),
        onSchedule?.let { PlayerAction("player-schedule", stringResource(R.string.channel_schedule), R.drawable.ic_event_list, it, scheduleFocus) },
        PlayerAction("player-settings", settings, R.drawable.ic_settings, onSettings, settingsFocus),
    )
    // Playback actions stand at the start; track lists, details and Settings at the end.
    val playbackEnd = if (onRecord == null) 2 else 3
    val endStart = actions[playbackEnd].tag
    val skipPresses = remember { mutableMapOf<Key, Any>() }
    val skipFocused = remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TvIconButtonSize)
            .onPreviewKeyEvent { event ->
                if (event.key !in RecordingSkipActivationKeys) return@onPreviewKeyEvent false
                // The row outlives Skip. Do not let its held OK activate the fallback Play/Pause.
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (event.nativeKeyEvent.repeatCount == 0) skipPresses.remove(event.key)
                        false
                    }
                    KeyEventType.KeyUp -> {
                        val pressed = skipPresses[event.key]
                        if (pressed != null && (!skipFocused.value || pressed !== skip?.identity)) {
                            skipPresses.remove(event.key)
                            true
                        } else false
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rowActions = if (skip == null) actions else actions.toMutableList().apply {
            add(playbackEnd, PlayerAction("recording-skip", skip.label, null, skip.onClick, skipFocus))
        }
        rowActions.forEach { action ->
            key(action.tag) {
                if (action.tag == endStart) Spacer(Modifier.weight(1f))
                PlayerActionButton(
                    action, recordState, pauseUnavailableReason, scheduleAvailable,
                    onActionFocused, onInteraction, skip, skipPresses, skipFocused,
                )
            }
        }
    }
}

@Composable
private fun PlayerActionButton(
    playerAction: PlayerAction,
    recordState: DvrEntryState?,
    pauseUnavailableReason: String?,
    scheduleAvailable: Boolean,
    onActionFocused: (String) -> Unit,
    onInteraction: () -> Unit,
    skip: PlayerSkipAction?,
    skipPresses: MutableMap<Key, Any>,
    skipFocused: MutableState<Boolean>,
) {
    val action = playerAction.onClick ?: return
    val tag = playerAction.tag
    val state = playerAction.state
    val actionModifier = Modifier
        .testTag(tag)
        .then(playerAction.focus?.let { Modifier.focusRequester(it) } ?: Modifier)
        .onFocusChanged { if (it.isFocused) { onActionFocused(tag); onInteraction() } }
        .then(if (tag == "player-pause" && pauseUnavailableReason != null) {
            Modifier.semantics { stateDescription = pauseUnavailableReason }
        } else Modifier)
        .then(if (state != null) Modifier.semantics { stateDescription = state } else Modifier)
    val onClick = { onInteraction(); action() }
    val icon = playerAction.icon
    if (icon == null) {
        PlayerRowSkipButton(
            playerAction.label, requireNotNull(skip).identity, onClick, actionModifier, skipPresses, skipFocused,
        )
    } else {
        PlayerRowIconButton(
            tag, playerAction.label, icon, onClick, actionModifier,
            recordState, pauseUnavailableReason, scheduleAvailable,
        )
    }
}

@Composable
private fun PlayerRowIconButton(
    tag: String,
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier,
    recordState: DvrEntryState?,
    pauseUnavailableReason: String?,
    scheduleAvailable: Boolean,
) {
    val recording = tag == "player-record" && recordState != null
    IconButton(
        onClick = onClick,
        colors = if (recording) IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = recordingMarkColor(focused = false),
            focusedContentColor = recordingMarkColor(focused = true),
        ) else IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (tag == "player-pause") 1f else 0.88f),
        ),
        modifier = modifier.size(TvIconButtonSize),
    ) {
        val dimmed = tag == "player-pause" && pauseUnavailableReason != null ||
            tag == "player-schedule" && !scheduleAvailable
        val glyphModifier = if (dimmed) Modifier.graphicsLayer { alpha = PauseUnavailableIconAlpha } else Modifier
        Icon(
            painterResource(icon),
            contentDescription = label,
            modifier = glyphModifier.size(IconButtonDefaults.MediumIconSize),
        )
    }
}

@Composable
private fun PlayerRowSkipButton(
    label: String,
    identity: Any,
    onClick: () -> Unit,
    modifier: Modifier,
    skipPresses: MutableMap<Key, Any>,
    skipFocused: MutableState<Boolean>,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = PlayerChromeTokens.chipScrim,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = modifier.height(TvIconButtonSize)
            .onFocusChanged { skipFocused.value = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.key !in RecordingSkipActivationKeys) false else when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (event.nativeKeyEvent.repeatCount == 0) skipPresses[event.key] = identity
                        skipPresses[event.key] !== identity
                    }
                    KeyEventType.KeyUp -> skipPresses.remove(event.key) !== identity
                    else -> false
                }
            },
    ) {
        Icon(
            painterResource(R.drawable.ic_skip_next),
            contentDescription = null,
            modifier = Modifier.size(IconButtonDefaults.MediumIconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1, softWrap = false)
    }
}
