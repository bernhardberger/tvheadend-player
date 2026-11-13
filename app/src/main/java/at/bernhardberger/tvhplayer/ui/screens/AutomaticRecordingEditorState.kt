package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.bernhardberger.tvhplayer.core.AutorecDraft
import at.bernhardberger.tvhplayer.core.AutorecEpisodeState
import at.bernhardberger.tvhplayer.core.AutorecPreviewRow
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.RecordingDraftField
import at.bernhardberger.tvhplayer.core.RecordingSubmitUnavailable
import at.bernhardberger.tvhplayer.core.cannotSubmit
import at.bernhardberger.tvhplayer.core.changedFields
import java.time.ZoneId
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import at.bernhardberger.tvheadend.sdk.core.EventId

internal enum class AutomaticRecordingEditorLevel {
    FIRST, UPCOMING, WORDS, CHANNEL_TIME, CHANNEL, DAYS, START_WINDOW,
    AFTER_HOUR, AFTER_MINUTE, BEFORE_HOUR, BEFORE_MINUTE, KEEP, MORE,
    DUPLICATE, PRIORITY, PADDING_BEFORE, PADDING_AFTER, BROADCAST, MAXIMUM, PROFILE, SEARCH_IN,
}

internal data class AutomaticRecordingEditorPage(
    val level: AutomaticRecordingEditorLevel,
    val focusedRow: String,
)

internal class AutomaticRecordingEditorState(
    initial: AutorecDraft,
    private val scope: CoroutineScope,
    private val loadPreview: suspend (AutorecDraft, AutorecDraft?, Instant, ZoneId) -> AutorecPreviewState,
    private val saveDraft: suspend (AutorecDraft, Map<EventId, Boolean>) -> AutorecSaveOutcome,
    val now: () -> Instant,
    val zone: ZoneId,
    val editing: Boolean = false,
    prefilled: Boolean = false,
    private val onClose: () -> Unit = {},
) {
    val baseline = initial
    var draft by mutableStateOf(initial)
        private set
    var pages by mutableStateOf(
        listOf(
            AutomaticRecordingEditorPage(
                AutomaticRecordingEditorLevel.FIRST,
                if (!editing && prefilled && initial.cannotSubmit() == null) "main" else "matches",
            ),
        ),
    )
        private set
    var preview by mutableStateOf<AutorecPreviewState>(AutorecPreviewState.Loading)
        private set
    var enabledChanges by mutableStateOf<Map<EventId, Boolean>>(emptyMap())
        private set
    var busy by mutableStateOf(false)
        private set
    var leaveConfirmation by mutableStateOf(false)
        private set
    var closed by mutableStateOf(false)
        private set
    var focusRevision by mutableStateOf(0)
        private set
    var selectedHour by mutableStateOf(0)
    private var previewJob: Job? = null
    private var saveJob: Job? = null
    private var previewRevision = 0

    val page get() = pages.last()
    var focusedRow: String
        get() = page.focusedRow
        set(row) {
            if (page.focusedRow != row) pages = pages.dropLast(1) + page.copy(focusedRow = row)
        }
    val changedFields get() = draft.changedFields(baseline)
    val changed get() = draft != baseline || enabledChanges.isNotEmpty()
    val mainProblem: RecordingSubmitUnavailable?
        get() = draft.cannotSubmit(if (editing) baseline else null).takeUnless {
            it == RecordingSubmitUnavailable.UNCHANGED && enabledChanges.isNotEmpty()
        }

    init {
        refreshPreview()
    }

    fun open(level: AutomaticRecordingEditorLevel, selectedRow: String) {
        if (busy || closed) return
        pages = pages + AutomaticRecordingEditorPage(level, selectedRow)
        focusRevision++
    }

    fun updateDraft(update: (AutorecDraft) -> AutorecDraft) {
        if (busy || closed) return
        val next = update(draft)
        if (next == draft) return
        val keepMatches = next.changedFields(draft).isEmpty()
        draft = next
        refreshPreview(keepMatches)
    }

    fun applyChoice(update: (AutorecDraft) -> AutorecDraft) {
        if (busy || closed) return
        updateDraft(update)
        back()
    }

    fun back() {
        if (busy || closed) return
        when {
            leaveConfirmation -> {
                leaveConfirmation = false
                focusRevision++
            }
            pages.size > 1 -> {
                pages = pages.dropLast(1)
                focusRevision++
            }
            changed -> leaveConfirmation = true
            else -> close()
        }
    }

    fun discard() {
        if (leaveConfirmation && !busy) close()
    }

    fun episodeEnabled(row: AutorecPreviewRow): Boolean = enabledChanges[row.eventId] ?: (
        row.eventId !in draft.excludedEventIds &&
            row.state !in setOf(AutorecEpisodeState.SKIPPED, AutorecEpisodeState.LEFT_OUT)
        )

    fun toggleEpisode(row: AutorecPreviewRow) {
        val id = row.eventId ?: return
        if (busy || row.state in setOf(AutorecEpisodeState.DUPLICATE, AutorecEpisodeState.REMOVED)) return
        if (editing) {
            val enabled = !episodeEnabled(row)
            val originallyEnabled = row.state !in setOf(AutorecEpisodeState.SKIPPED, AutorecEpisodeState.LEFT_OUT)
            enabledChanges = if (enabled == originallyEnabled) enabledChanges - id else enabledChanges + (id to enabled)
        } else {
            updateDraft {
                it.copy(
                    excludedEventIds = if (id in it.excludedEventIds) it.excludedEventIds - id
                    else it.excludedEventIds + id,
                )
            }
        }
    }

    fun submit() {
        if (busy || closed || mainProblem != null) return
        busy = true
        leaveConfirmation = false
        val submittedDraft = draft
        val submittedChoices = enabledChanges
        saveJob = scope.launch {
            try {
                when (saveDraft(submittedDraft, submittedChoices)) {
                    is AutorecSaveOutcome.RuleFailed -> {
                        pages = listOf(AutomaticRecordingEditorPage(AutomaticRecordingEditorLevel.FIRST, "main"))
                        focusRevision++
                    }
                    is AutorecSaveOutcome.Applied, is AutorecSaveOutcome.PartiallyApplied -> close()
                }
            } finally {
                busy = false
            }
        }
    }

    fun dispose() {
        previewRevision++
        previewJob?.cancel()
        saveJob?.cancel()
    }

    private fun refreshPreview(keepMatches: Boolean = false) {
        previewJob?.cancel()
        val revision = ++previewRevision
        val requestedDraft = draft
        if (!keepMatches || preview !is AutorecPreviewState.Matches) preview = AutorecPreviewState.Loading
        previewJob = scope.launch {
            val result = loadPreview(requestedDraft, baseline.takeIf { editing }, now(), zone)
            if (revision == previewRevision && !closed) preview = result
        }
    }

    private fun close() {
        closed = true
        previewRevision++
        previewJob?.cancel()
        onClose()
    }
}

internal val automaticRecordingMoreFields = setOf(
    RecordingDraftField.DUPLICATE_DETECTION, RecordingDraftField.PRIORITY,
    RecordingDraftField.PADDING_BEFORE, RecordingDraftField.PADDING_AFTER,
    RecordingDraftField.BROADCAST_TYPE, RecordingDraftField.MAXIMUM_RECORDINGS, RecordingDraftField.PROFILE,
)

internal fun resetAutomaticRecordingMoreOptions(draft: AutorecDraft, baseline: AutorecDraft): AutorecDraft = draft.copy(
    duplicateDetection = baseline.duplicateDetection,
    priority = baseline.priority,
    paddingBefore = baseline.paddingBefore,
    paddingAfter = baseline.paddingAfter,
    broadcastType = baseline.broadcastType,
    maximumRecordings = baseline.maximumRecordings,
    configId = baseline.configId,
)
