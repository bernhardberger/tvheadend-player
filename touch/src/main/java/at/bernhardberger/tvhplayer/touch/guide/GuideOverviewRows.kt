package at.bernhardberger.tvhplayer.touch.guide

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
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
    val marks: Map<EpgEvent, GuideOverviewMarks>,
)

/** One publication's prepared rows, including those already visited in this Overview. */
internal class GuideOverviewRows(
    val viewport: Set<String> = emptySet(),
    previous: GuideOverviewRows? = null,
) {
    private val previousRows = previous?.mapped.orEmpty()
    private val previousSchedule = previous?.schedule
    private val previousProgressInputs = previous?.progressInputs
    private val mapped = mutableMapOf<String, Pair<GuideOverviewRowKey, GuideChannel>>()
    private var schedule: GuideSchedule? = null
    private var progressInputs: Pair<Long, String?>? = null
    var formattedCount = 0
        private set

    fun includes(index: Int, phoneIndex: Int?, id: String): Boolean =
        index < FIRST_GUIDE_ROWS || (phoneIndex != null && phoneIndex < FIRST_GUIDE_ROWS) ||
            id in viewport || id in previousRows

    fun schedule(channels: List<Channel>, events: List<EpgEvent>, clock: GuideClock, playing: String?): GuideSchedule {
        progressInputs = clock.nowSec to playing
        return (previousSchedule?.takeIf { it.matches(channels, events, clock) }
            ?: GuideSchedule(channels, events, clock)).also { schedule = it }
    }

    fun marksUnchanged(input: GuideMappingInput): Boolean = mapped.values.all { (key, _) ->
        key.marks.all { (event, marks) ->
            input.observation.overviewMarks(event, input.clock.nowSec, input.artwork) == marks
        }
    }

    fun getOrMap(
        key: GuideOverviewRowKey,
        gapText: (Long) -> String,
        format: (EpgEvent, GuideProgram?) -> GuideProgram,
    ): GuideChannel {
        val (oldKey, oldRow) = previousRows[key.head.id] ?: (null to null)
        if (oldKey == key && progressInputs == previousProgressInputs) {
            val prepared = checkNotNull(oldRow)
            mapped[key.head.id] = key to prepared
            return prepared
        }
        val oldEvents = oldKey?.marks?.keys.orEmpty().associateBy { it.id }
        val oldPrograms = oldRow?.programs.orEmpty().associateBy { it.id }
        var formatted = false
        fun program(event: EpgEvent): GuideProgram {
            val existing = oldPrograms[event.id.value.toString()].takeIf { oldEvents[event.id] == event }
            if (existing == null) formatted = true
            return format(event, existing)
        }
        val newRow = key.head.copy(
            programs = key.overview.map(::program),
            gap = key.gap?.let { (start, stop) ->
                if (key.gap == oldKey?.gap) oldRow?.gap else GuideGap(gapText(start), gapText(stop))
            },
        )
        if (formatted || (key != oldKey && newRow != oldRow)) formattedCount++
        val row = oldRow?.takeIf { it == newRow } ?: newRow
        mapped[key.head.id] = key to row
        return row
    }
}

/** Reuse the same epoch index on viewport and clock passes until the EPG, scope or local day changes. */
internal class GuideSchedule(
    private val channels: List<Channel>,
    private val events: List<EpgEvent>,
    clock: GuideClock,
) {
    private val zone = clock.zone
    private val date = clock.today
    private val ids = channels.mapTo(HashSet()) { it.id }
    val byChannel: Map<ChannelId?, List<EpgEvent>> = events.asSequence()
        .filter { it.channelId in ids && it.stop > it.start }
        .distinctBy { it.id }
        .sortedBy { it.start }
        .groupBy { it.channelId }
    val days = guideDays(byChannel.values.flatten(), clock)

    fun matches(channels: List<Channel>, events: List<EpgEvent>, clock: GuideClock): Boolean =
        this.channels === channels && this.events === events && zone == clock.zone && date == clock.today
}
