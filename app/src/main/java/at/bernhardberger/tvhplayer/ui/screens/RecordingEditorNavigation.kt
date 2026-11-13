package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class AutomaticRecordingEditorLevel {
    FIRST, UPCOMING, WORDS, WORDS_ENTRY, CHANNEL_TIME, CHANNEL, DAYS, START_WINDOW,
    AFTER_HOUR, AFTER_MINUTE, BEFORE_HOUR, BEFORE_MINUTE, KEEP, MORE,
    DUPLICATE, PRIORITY, PADDING_BEFORE, PADDING_AFTER, BROADCAST, MAXIMUM, PROFILE, SEARCH_IN,
}

internal enum class AutomaticRecordingEditorConfirmation { LEAVE, DELETE }

internal data class AutomaticRecordingEditorPage(
    val level: AutomaticRecordingEditorLevel,
    val focusedRow: String,
)

/** Navigation and focus only; each task continues to own its typed draft and mutations. */
internal class RecordingEditorNavigation(
    initialRow: String,
    val tagPrefix: String,
    val canNavigate: () -> Boolean,
    private val deletionAllowed: Boolean,
) {
    var pages by mutableStateOf(listOf(AutomaticRecordingEditorPage(AutomaticRecordingEditorLevel.FIRST, initialRow)))
        private set
    var focusRevision by mutableStateOf(0)
        private set
    private var pendingConfirmation by mutableStateOf<AutomaticRecordingEditorConfirmation?>(null)
    var confirmation: AutomaticRecordingEditorConfirmation?
        get() = pendingConfirmation
        set(value) {
            if (!canNavigate()) return
            if (value == AutomaticRecordingEditorConfirmation.DELETE && !deletionAllowed) return
            pendingConfirmation = value
        }
    val leaveConfirmation get() = confirmation == AutomaticRecordingEditorConfirmation.LEAVE
    var textEntryEditing by mutableStateOf(false)
    val page get() = pages.last()
    var focusedRow: String
        get() = page.focusedRow
        set(row) {
            if (page.focusedRow != row) pages = pages.dropLast(1) + page.copy(focusedRow = row)
        }

    fun open(level: AutomaticRecordingEditorLevel, selectedRow: String) {
        if (!canNavigate()) return
        pages = pages + AutomaticRecordingEditorPage(level, selectedRow)
        textEntryEditing = false
        focusRevision++
    }

    /** True only when Back should close an unchanged first level. */
    fun back(changed: Boolean): Boolean {
        if (!canNavigate()) return false
        return when {
            confirmation != null -> {
                pendingConfirmation = null
                focusRevision++
                false
            }
            textEntryEditing -> {
                textEntryEditing = false
                false
            }
            pages.size > 1 -> {
                pages = pages.dropLast(1)
                focusRevision++
                false
            }
            changed -> {
                pendingConfirmation = AutomaticRecordingEditorConfirmation.LEAVE
                false
            }
            else -> true
        }
    }

    fun returnTo(level: AutomaticRecordingEditorLevel) {
        val index = pages.indexOfLast { it.level == level }
        require(index >= 0)
        pages = pages.take(index + 1)
        focusRevision++
    }

    fun restoreFocus(row: String? = null) {
        if (row != null) focusedRow = row
        focusRevision++
    }

    fun firstLevel(row: String) {
        pages = listOf(AutomaticRecordingEditorPage(AutomaticRecordingEditorLevel.FIRST, row))
        focusRevision++
    }
}
