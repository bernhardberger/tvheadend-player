package at.bernhardberger.tvhplayer.touch.guide

import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.programmeArtwork
import at.bernhardberger.tvhplayer.core.runningRecordingForEvent
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.ProgramRecordControl
import at.bernhardberger.tvhplayer.touch.ui.RuleSchedule
import at.bernhardberger.tvhplayer.touch.ui.programRecordControl
import at.bernhardberger.tvhplayer.touch.ui.ruleSchedule
import at.bernhardberger.tvhplayer.ui.screens.isScheduled
import at.bernhardberger.tvhplayer.ui.screens.programmeRecordKeyAction

/** Seed the rows that can fit on entry, independently of the full scope size. */
internal const val FIRST_GUIDE_ROWS = 8

internal data class GuideOverviewMarks(
    val recording: Boolean,
    val scheduled: Boolean,
    val rule: RuleSchedule?,
    val control: ProgramRecordControl?,
    val picture: Any?,
)

internal fun SessionObservation.overviewMarks(event: EpgEvent, nowSec: Long, artwork: ArtworkModels?) =
    GuideOverviewMarks(
        runningRecordingForEvent(event) != null,
        isScheduled(event),
        ruleSchedule(event),
        programRecordControl(programmeRecordKeyAction(event, this, nowSec)),
        artwork?.picture(programmeArtwork(event), this),
    )

internal data class GuideOverviewRowKey(
    val head: GuideChannel,
    val overview: List<EpgEvent>,
    val gap: Pair<Long, Long>?,
    val day: List<EpgEvent>?,
    val marks: Map<EpgEvent, GuideOverviewMarks>,
)

/** Worker-local while preparing; only rows in this publication are retained. */
internal class GuideOverviewRows(
    val viewport: Set<String> = emptySet(),
    previous: GuideOverviewRows? = null,
) {
    private val previousRows = previous?.mapped.orEmpty()
    private val mapped = mutableMapOf<GuideOverviewRowKey, GuideChannel>()
    var formattedCount = 0
        private set

    fun includes(index: Int, phoneIndex: Int?, id: String): Boolean =
        index < FIRST_GUIDE_ROWS || (phoneIndex != null && phoneIndex < FIRST_GUIDE_ROWS) || id in viewport

    fun marksUnchanged(input: GuideMappingInput): Boolean = mapped.keys.all { key ->
        key.marks.all { (event, marks) ->
            input.observation.overviewMarks(event, input.clock.nowSec, input.artwork) == marks
        }
    }

    fun getOrMap(key: GuideOverviewRowKey, format: () -> GuideChannel): GuideChannel {
        val row = previousRows[key] ?: format().also { formattedCount++ }
        mapped[key] = row
        return row
    }
}
