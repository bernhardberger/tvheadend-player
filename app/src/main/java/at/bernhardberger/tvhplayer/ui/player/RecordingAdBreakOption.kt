package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * How the playing recording treats its ad breaks, for the options panel. [choice] is the
 * recording's own handling, null while it follows [setting]; [select] with null goes back to
 * the setting. Without [marked] ad breaks there is nothing to choose.
 */
internal class RecordingAdBreakOption(
    val marked: Boolean,
    val setting: AdBreakMode,
    val choice: AdBreakMode?,
    val select: (AdBreakMode?) -> Unit,
)

@Composable
internal fun rememberRecordingAdBreakOption(
    recording: DvrEntryId,
    marks: List<RecordingMark>,
    store: PlayerSettingsStore,
): RecordingAdBreakOption {
    val scope = rememberCoroutineScope()
    val choices by store.recordingAdBreaks.choices.collectAsStateWithLifecycle(null)
    val setting by remember(store) { store.playerSettings.map { it.adBreakMode }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(AdBreakMode.ASK)
    val profile = choices?.profile
    val select = remember(store, scope, profile, recording) {
        { mode: AdBreakMode? ->
            scope.launch {
                try {
                    store.recordingAdBreaks.set(profile, recording.value, mode)
                } catch (_: java.io.IOException) {
                    // Keep the previous choice when persistence is unavailable.
                }
            }
            Unit
        }
    }
    val marked = marks.any { it.kind == RecordingMarkKind.AD_BREAK }
    val choice = choices?.get(recording.value)
    return remember(marked, setting, choice, select) { RecordingAdBreakOption(marked, setting, choice, select) }
}
