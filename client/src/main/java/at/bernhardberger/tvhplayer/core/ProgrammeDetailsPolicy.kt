package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission

enum class DetailsStatus { ON_NOW, RECORDING, RECORDING_ERRORS, SCHEDULED, MISSED, CANCELLED, FAILED }

fun detailsStatus(event: EpgEvent?, recording: DvrEntry?, nowSec: Long): DetailsStatus? =
    when (recording?.state) {
        DvrEntryState.RECORDING -> DetailsStatus.RECORDING
        DvrEntryState.SCHEDULED -> DetailsStatus.SCHEDULED
        // Tvheadend is still recording; the errors so far do not make it a failure yet.
        DvrEntryState.RECORDING_ERROR -> DetailsStatus.RECORDING_ERRORS
        DvrEntryState.MISSED -> DetailsStatus.MISSED
        DvrEntryState.INVALID -> DetailsStatus.CANCELLED
        DvrEntryState.COMPLETED_ERROR -> DetailsStatus.FAILED
        else -> DetailsStatus.ON_NOW.takeIf {
            event != null && nowSec >= event.start.epochSeconds && nowSec < event.stop.epochSeconds
        }
    }

fun programmeDetailsActions(
    event: EpgEvent,
    nowSec: Long,
    recording: DvrEntry?,
    recordingAdmission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
    canWatchChannel: Boolean,
    liveProgramme: Boolean = true,
    playing: Boolean = false,
    behindLive: Boolean = false,
    ruleAvailable: Boolean = false,
): List<BrowseContextAction> = buildList {
    if (playing) {
        add(BrowseContextAction.CONTINUE_WATCHING)
        // Continue watching returns to where the viewer is; behind live, live is its own choice.
        if (behindLive) add(BrowseContextAction.WATCH_LIVE)
    } else if (canWatchChannel) add(BrowseContextAction.WATCH)
    addAll(programmeActions(event, nowSec, recording,
        canModifyRecordings = canModifyRecordings,
        recordingAdmission = recordingAdmission).mapNotNull {
        programmeDetailsAction(it, event, nowSec, recording, liveProgramme)
    })
    addAll(recordingRuleActions(recording, canModifyRecordings, ruleAvailable))
    if (canWatchChannel) add(BrowseContextAction.SHOW_GUIDE)
}

private fun programmeDetailsAction(action: ProgrammeAction, event: EpgEvent, nowSec: Long,
    recording: DvrEntry?, liveProgramme: Boolean): BrowseContextAction? = when (action) {
    ProgrammeAction.WATCH -> null
    ProgrammeAction.RECORD -> BrowseContextAction.RECORD.takeIf { liveProgramme }
    ProgrammeAction.RECORD_SERIES -> BrowseContextAction.RECORD_SERIES.takeIf { liveProgramme }
    ProgrammeAction.RECORD_TITLE -> BrowseContextAction.RECORD_TITLE.takeIf { liveProgramme }
    ProgrammeAction.STOP_RECORDING -> BrowseContextAction.STOP
    ProgrammeAction.CANCEL_RECORDING -> BrowseContextAction.CANCEL.takeUnless { recording?.isRuleRecording == true }
    ProgrammeAction.WATCH_FROM_START -> if (recording == null) null else
        if (nowSec < event.stop.epochSeconds) BrowseContextAction.START_OVER else BrowseContextAction.PLAY
}

fun recordingDetailsActions(
    recording: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
    canWatchChannel: Boolean,
    playing: Boolean = false,
    nowSec: Long,
    ruleAvailable: Boolean = false,
): List<BrowseContextAction> = buildList {
    if (!playing && recording.state == DvrEntryState.SCHEDULED && canWatchChannel) add(BrowseContextAction.WATCH)
    addAll(recordingContextActions(recording, admission, canModifyRecordings, playing, ruleAvailable = ruleAvailable)
        .filter { it != BrowseContextAction.DETAILS })
    if (canWatchChannel && recording.state in listOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING,
            DvrEntryState.RECORDING_ERROR) && recording.stop?.epochSeconds?.let { it > nowSec } == true) {
        add(BrowseContextAction.SHOW_GUIDE)
    }
}

/**
 * Other unended airings of [event] on any channel: series link URI,
 * otherwise the same title (trimmed, case-insensitive). Simulcasts remain separate, at most [limit].
 */
fun otherAirings(event: EpgEvent, events: List<EpgEvent>, nowSec: Long, limit: Int = 20,
    channelNumber: (ChannelId) -> Long? = { null }): List<EpgEvent> {
    val seriesLink = event.seriesLinkUri?.takeIf(String::isNotBlank)
    val title = event.title?.trim()?.takeIf(String::isNotEmpty)
    val matches: (EpgEvent) -> Boolean = when {
        seriesLink != null -> { other -> other.seriesLinkUri == seriesLink }
        title != null -> { other -> other.title?.trim().equals(title, ignoreCase = true) }
        else -> return emptyList()
    }
    return events.filter { it.id != event.id && it.stop.epochSeconds > nowSec && matches(it) }
        .sortedWith(compareBy<EpgEvent> { it.start }
            .thenBy { it.channelId?.let(channelNumber) ?: Long.MAX_VALUE })
        .take(limit)
}

enum class DetailsFactKind { STARTS, STARTED, RECORDED, LENGTH, LEFT }
data class DetailsFact(val kind: DetailsFactKind, val seconds: Long)

fun programmeDetailsFacts(event: EpgEvent, nowSec: Long): List<DetailsFact> = buildList {
    add(DetailsFact(if (nowSec >= event.stop.epochSeconds) DetailsFactKind.STARTED else DetailsFactKind.STARTS,
        event.start.epochSeconds))
    add(DetailsFact(DetailsFactKind.LENGTH, (event.stop - event.start).inWholeSeconds.coerceAtLeast(0)))
    if (nowSec >= event.start.epochSeconds && nowSec < event.stop.epochSeconds) {
        add(DetailsFact(DetailsFactKind.LEFT, event.stop.epochSeconds - nowSec))
    }
}

/**
 * [admission] is the SDK's admission of [recording]; the time left follows the Resume it offers.
 * The length is what was recorded ([recordingLengthSeconds]), not the scheduled length.
 */
fun recordingDetailsFacts(recording: DvrEntry, admission: RecordingPlaybackAdmission?, nowSec: Long): List<DetailsFact> = buildList {
    recording.start?.let { add(DetailsFact(DetailsFactKind.RECORDED, it.epochSeconds)) }
    val length = recordingLengthSeconds(recording, nowSec)?.takeIf { it > 0 } ?: return@buildList
    add(DetailsFact(DetailsFactKind.LENGTH, length))
    offeredResumePosition(admission)?.inWholeSeconds?.takeIf { it > 0 && it < length }?.let {
        add(DetailsFact(DetailsFactKind.LEFT, length - it))
    }
}

/** One button of the details page's action row: an action of its own, or several behind one button. */
sealed interface DetailsRowButton {
    val actions: List<BrowseContextAction>

    data class Direct(val action: BrowseContextAction) : DetailsRowButton {
        override val actions get() = listOf(action)
    }

    /** "Record…": the ways to record this programme, in a choice of their own. */
    data class RecordChoice(override val actions: List<BrowseContextAction>) : DetailsRowButton

    /** "More": the remaining actions, in the context menu. */
    data class More(override val actions: List<BrowseContextAction>) : DetailsRowButton
}

private val continueActions = setOf(BrowseContextAction.WATCH, BrowseContextAction.CONTINUE_WATCHING,
    BrowseContextAction.RESUME)
private val fromStartActions = setOf(BrowseContextAction.START_OVER, BrowseContextAction.PLAY)
private val recordChoiceActions = listOf(BrowseContextAction.RECORD, BrowseContextAction.RECORD_SERIES,
    BrowseContextAction.RECORD_TITLE)
private val recordingStateActions = setOf(BrowseContextAction.STOP, BrowseContextAction.CANCEL,
    BrowseContextAction.SKIP, BrowseContextAction.UNSKIP, BrowseContextAction.DELETE, BrowseContextAction.REMOVE_ENTRY)

private class DetailsRowParts(val play: List<BrowseContextAction>, val recording: List<BrowseContextAction>,
    val rest: List<BrowseContextAction>)

private fun detailsRowParts(actions: List<BrowseContextAction>): DetailsRowParts {
    val play = actions.filter { it in continueActions } + actions.filter { it in fromStartActions } +
        actions.filter { it == BrowseContextAction.WATCH_LIVE }
    // One recording button: the recording's own action, else the ways to record it.
    val recording = actions.filter { it in recordingStateActions }
        .ifEmpty { recordChoiceActions.filter { it in actions } }
    return DetailsRowParts(play, recording, actions.filter { it !in play && it !in recording })
}

/**
 * The details row for a page offering [actions]: play first (continue here, from the start, live),
 * then the recording button, then the rest as one button, or behind More when there are several.
 */
fun detailsActionRow(actions: List<BrowseContextAction>): List<DetailsRowButton> {
    val parts = detailsRowParts(actions)
    return parts.play.map(DetailsRowButton::Direct) +
        (if (parts.recording.size > 1 && parts.recording.all { it in recordChoiceActions })
            listOf(DetailsRowButton.RecordChoice(parts.recording)) else parts.recording.map(DetailsRowButton::Direct)) +
        (if (parts.rest.size > 1) listOf(DetailsRowButton.More(parts.rest)) else parts.rest.map(DetailsRowButton::Direct))
}

private val pairedActions = listOf(
    setOf(BrowseContextAction.SKIP, BrowseContextAction.UNSKIP),
    setOf(BrowseContextAction.MARK_WATCHED, BrowseContextAction.MARK_UNWATCHED),
    setOf(BrowseContextAction.RECORD, BrowseContextAction.CANCEL, BrowseContextAction.STOP),
)

/**
 * The button that takes focus in [row] after [last] was focused or chosen: the button holding it,
 * else the one holding what replaced it, else the button in its place (the recording button, or
 * More or the single last one for the rest), else [initial]'s button, else the first.
 */
fun detailsRowFocus(row: List<DetailsRowButton>, last: BrowseContextAction?,
    initial: BrowseContextAction?): DetailsRowButton? {
    fun holding(action: BrowseContextAction?) = action?.let { row.firstOrNull { button -> it in button.actions } }
    val successor = when (last) {
        BrowseContextAction.RECORD_SERIES, BrowseContextAction.RECORD_TITLE -> BrowseContextAction.EDIT_RULE
        else -> null
    }
    val pair = pairedActions.firstOrNull { last in it }
    val parts = detailsRowParts(row.flatMap { it.actions })
    val place = when (last) {
        null -> null
        in recordChoiceActions, in recordingStateActions -> parts.recording
        in continueActions, in fromStartActions, BrowseContextAction.WATCH_LIVE -> null
        else -> parts.rest
    }?.firstOrNull()
    return holding(last) ?: holding(successor) ?: pair?.firstNotNullOfOrNull { holding(it) }
        ?: holding(place) ?: holding(initial) ?: row.firstOrNull()
}
