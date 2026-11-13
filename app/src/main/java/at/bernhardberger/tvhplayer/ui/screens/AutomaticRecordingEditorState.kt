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
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult

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
    private val deleteRule: (suspend () -> DvrMutationResult<*>)? = null,
) {
    val baseline = initial
    var draft by mutableStateOf(initial)
        private set
    val navigation = RecordingEditorNavigation(
        initialRow = if (!editing && prefilled && initial.cannotSubmit() == null) "main" else "matches",
        tagPrefix = "automatic-recording",
        canNavigate = { !busy && !closed },
        deletionAllowed = editing,
    )
    private var loadedPreview by mutableStateOf<AutorecPreviewState>(AutorecPreviewState.Loading)
    val preview: AutorecPreviewState
        get() = (loadedPreview as? AutorecPreviewState.Matches)?.let { matches ->
            matches.copy(willRecordCount = matches.rows.count { row ->
                row.state !in setOf(AutorecEpisodeState.DUPLICATE, AutorecEpisodeState.REMOVED) && episodeEnabled(row)
            })
        } ?: loadedPreview
    var enabledChanges by mutableStateOf<Map<EventId, Boolean>>(emptyMap())
        private set
    var busy by mutableStateOf(false)
        private set
    var deleting by mutableStateOf(false)
        private set
    var closed by mutableStateOf(false)
        private set
    var selectedHour by mutableStateOf(0)
    private var previewJob: Job? = null
    private var saveJob: Job? = null
    private var previewRevision = 0

    val changedFields get() = draft.changedFields(baseline)
    val changed get() = draft != baseline || enabledChanges.isNotEmpty()
    val mainProblem: RecordingSubmitUnavailable?
        get() = draft.cannotSubmit(if (editing) baseline else null).takeUnless {
            it == RecordingSubmitUnavailable.UNCHANGED && enabledChanges.isNotEmpty()
        }

    init {
        require(!editing || deleteRule != null)
        refreshPreview()
    }

    fun updateDraft(restoreFocusTo: String? = null, update: (AutorecDraft) -> AutorecDraft) {
        if (busy || closed) return
        val next = update(draft)
        if (next == draft) return
        val keepMatches = next.changedFields(draft).isEmpty()
        draft = next
        refreshPreview(keepMatches)
        if (restoreFocusTo != null) {
            navigation.restoreFocus(restoreFocusTo)
        }
    }

    fun applyChoice(update: (AutorecDraft) -> AutorecDraft) {
        if (busy || closed) return
        updateDraft(update = update)
        navigation.textEntryEditing = false
        if (navigation.page.level in setOf(
                 AutomaticRecordingEditorLevel.AFTER_MINUTE, AutomaticRecordingEditorLevel.BEFORE_MINUTE,
            )) navigation.returnTo(AutomaticRecordingEditorLevel.START_WINDOW)
        else back()
    }

    fun back() {
        if (navigation.back(changed)) close()
    }

    fun confirmDestructiveAction() {
        if (busy || closed) return
        when (navigation.confirmation) {
            AutomaticRecordingEditorConfirmation.LEAVE -> close()
            AutomaticRecordingEditorConfirmation.DELETE -> {
                val remove = requireNotNull(deleteRule)
                navigation.confirmation = null
                busy = true
                deleting = true
                saveJob = scope.launch {
                    try {
                        when (remove()) {
                            is DvrMutationResult.Failed -> navigation.restoreFocus()
                            else -> close()
                        }
                    } finally {
                        busy = false
                        deleting = false
                    }
                }
            }
            null -> Unit
        }
    }

    fun episodeEnabled(row: AutorecPreviewRow): Boolean = if (editing) {
        enabledChanges[row.eventId] ?: (row.state !in setOf(AutorecEpisodeState.SKIPPED, AutorecEpisodeState.LEFT_OUT))
    } else row.eventId !in draft.excludedEventIds

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
        navigation.confirmation = null
        busy = true
        val submittedDraft = draft
        val submittedChoices = enabledChanges
        saveJob = scope.launch {
            try {
                when (saveDraft(submittedDraft, submittedChoices)) {
                    is AutorecSaveOutcome.RuleFailed -> {
                        navigation.firstLevel("main")
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
        if (!keepMatches || loadedPreview !is AutorecPreviewState.Matches) loadedPreview = AutorecPreviewState.Loading
        previewJob = scope.launch {
            val result = loadPreview(requestedDraft, baseline.takeIf { editing }, now(), zone)
            if (revision == previewRevision && !closed) loadedPreview = result
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
