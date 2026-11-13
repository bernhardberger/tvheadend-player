package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvhplayer.core.TimerDraft
import at.bernhardberger.tvhplayer.core.cannotSubmit
import at.bernhardberger.tvhplayer.core.changedFields
import at.bernhardberger.tvhplayer.core.timerHourChoices
import at.bernhardberger.tvhplayer.core.timerMinuteChoices
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** UI-lifetime scope supplied by the host; dispose cancels this task's pending mutation. */
internal class TimerRecordingEditorState(
    initial: TimerDraft,
    private val scope: CoroutineScope,
    private val saveDraft: suspend (TimerDraft, Instant) -> DvrMutationResult<*>,
    val now: () -> Instant,
    val zone: ZoneId,
    private val onClose: () -> Unit = {},
    private val deleteTimer: (suspend () -> DvrMutationResult<*>)? = null,
) {
    val baseline = initial
    var draft by mutableStateOf(initial)
        private set
    val editing get() = baseline.existingTimer
    var busy by mutableStateOf(false)
        private set
    var deleting by mutableStateOf(false)
        private set
    var closed by mutableStateOf(false)
        private set
    val navigation = RecordingEditorNavigation(
        "channel-time", "timer-recording", { !busy && !closed }, editing,
    )
    var selectedHour by mutableStateOf(initial.start.hour)
        private set
    private var mutation: Job? = null
    val changed get() = draft != baseline
    val changedFields get() = draft.changedFields(baseline)
    val mainProblem get() = draft.cannotSubmit(zone, now(), baseline.takeIf { editing })
    val today: LocalDate get() = java.time.Instant.ofEpochSecond(now().epochSeconds).atZone(zone).toLocalDate()
    val ending get() = navigation.page.level in setOf(
        AutomaticRecordingEditorLevel.END_HOUR, AutomaticRecordingEditorLevel.END_MINUTE,
    )
    val currentTime get() = if (ending) draft.end else draft.start

    init { require(!editing || deleteTimer != null) }

    fun updateDraft(restoreFocusTo: String? = null, update: (TimerDraft) -> TimerDraft) {
        if (!navigation.canNavigate()) return
        draft = update(draft)
        if (restoreFocusTo != null) navigation.restoreFocus(restoreFocusTo)
    }

    fun applyChoice(update: (TimerDraft) -> TimerDraft) {
        if (!navigation.canNavigate()) return
        updateDraft(update = update)
        navigation.textEntryEditing = false
        if (navigation.page.level in setOf(
                AutomaticRecordingEditorLevel.START_MINUTE, AutomaticRecordingEditorLevel.END_MINUTE,
            )) navigation.returnTo(AutomaticRecordingEditorLevel.CHANNEL_TIME)
        else back()
    }

    fun openClock(end: Boolean) {
        val time = if (end) draft.end else draft.start
        val first = draft.timerHourChoices(end).first { draft.timerMinuteChoices(it, end).isNotEmpty() }
        val hour = time.hour.takeIf { draft.timerMinuteChoices(it, end).isNotEmpty() } ?: first
        selectedHour = time.hour
        navigation.open(if (end) AutomaticRecordingEditorLevel.END_HOUR else
            AutomaticRecordingEditorLevel.START_HOUR, if (time.minute % 5 != 0) "current-time" else "hour-$hour")
    }

    fun openMinutes(hour: Int) {
        val choices = draft.timerMinuteChoices(hour, ending)
        if (choices.isEmpty()) return
        val current = currentTime
        selectedHour = hour
        val row = when {
            current.hour == hour && current.minute % 5 != 0 -> "current-minute"
            current in choices -> "minute-${current.minute}"
            else -> "minute-${choices.first().minute}"
        }
        navigation.open(if (ending) AutomaticRecordingEditorLevel.END_MINUTE else
            AutomaticRecordingEditorLevel.START_MINUTE, row)
    }

    fun back() {
        if (navigation.back(changed)) close()
    }

    fun submit() {
        if (!navigation.canNavigate()) return
        val submittedAt = now()
        if (draft.cannotSubmit(zone, submittedAt, baseline.takeIf { editing }) != null) {
            navigation.firstLevel("main")
            return
        }
        val submitted = draft
        runMutation { saveDraft(submitted, submittedAt) }
    }

    fun confirmDestructiveAction() {
        if (!navigation.canNavigate()) return
        when (navigation.confirmation) {
            AutomaticRecordingEditorConfirmation.LEAVE -> close()
            AutomaticRecordingEditorConfirmation.DELETE -> runMutation(deletingTimer = true) {
                requireNotNull(deleteTimer)()
            }
            null -> Unit
        }
    }

    fun dispose() { mutation?.cancel() }

    fun resetMore() = updateDraft(restoreFocusTo = "priority") {
        it.copy(priority = baseline.priority, configId = baseline.configId,
            paddingBefore = if (it.repeating) it.paddingBefore else baseline.paddingBefore,
            paddingAfter = if (it.repeating) it.paddingAfter else baseline.paddingAfter)
    }

    private fun runMutation(deletingTimer: Boolean = false, action: suspend () -> DvrMutationResult<*>) {
        navigation.confirmation = null
        busy = true
        deleting = deletingTimer
        mutation = scope.launch {
            try {
                when (action()) {
                    is DvrMutationResult.Failed -> if (deletingTimer) navigation.restoreFocus()
                        else navigation.firstLevel("main")
                    else -> close()
                }
            } finally {
                busy = false
                deleting = false
            }
        }
    }

    private fun close() {
        closed = true
        onClose()
    }
}
