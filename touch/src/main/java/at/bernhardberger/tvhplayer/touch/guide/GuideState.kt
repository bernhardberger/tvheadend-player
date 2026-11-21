package at.bernhardberger.tvhplayer.touch.guide

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelTag

/** The Guide's two views, as tabs under the top bar. */
internal enum class GuideView(@param:StringRes val label: Int) {
    Overview(R.string.guide_overview),
    ByChannel(R.string.guide_by_channel),
}

/**
 * A program's " • " line in the design's order (touch-design §7): the episode number, the time facts, the
 * year, and the genre last, so a narrow line, which drops whole parts from its end, loses the genre first.
 */
internal fun programLine(number: String?, times: List<String?>, year: String?, genre: String?): List<String> =
    listOfNotNull(number) + times.filterNotNull() + listOfNotNull(year, genre)

/** A closed row's line: what is left of a running program, else its length. */
@Composable
internal fun GuideProgram.rowLine(): List<String> = programLine(number, listOf(timeLeftText() ?: length), null, genre)

/** The opened program's line; the phone writes the time range with the length there instead. */
@Composable
internal fun GuideProgram.openLine(phone: Boolean): List<String> =
    if (phone) {
        programLine(null, listOf(timeRange, length), null, null)
    } else {
        programLine(null, listOf(length ?: timeLeftText()), year, genre)
    }

@Composable
private fun GuideProgram.timeLeftText(): String? = timeLeft?.let { stringResource(R.string.channels_time_left, it) }

/** The canvas the Guide lays out for. */
internal enum class GuideLayout {
    Phone, Upright, Wide;

    val compact: Boolean get() = this == Phone
}

/**
 * The Guide on supplied state. The producer picks the channels for the tag, the programs for the
 * selected day and jump time, and formats every text for the canvas it is shown on; the screen
 * keeps the view, the channel picked in By channel, the opened program and the opened Details.
 */
internal data class GuideState(
    val channels: List<GuideChannel>,
    val tags: List<ChannelTag> = emptyList(),
    val selectedTagId: String? = null,
    /** The day tabs and menu: "Today", "Tomorrow", "Tue 17". */
    val days: List<String>,
    val selectedDay: Int = 0,
    /** The selected day written out, "Sunday, November 15". */
    val date: String,
    /** The overview's jump times after Now, "8:15 PM". */
    val jumps: List<String> = emptyList(),
    /** 0 is Now, then [jumps] by index plus one. */
    val selectedJump: Int = 0,
    /** Details the producer has for a program, by program id. */
    val details: Map<String, ProgramDetails> = emptyMap(),
)

internal data class GuideChannel(
    val id: String,
    val name: String,
    val logo: Painter? = null,
    /** The logo's tile tint, supplied by the caller; null uses the neutral surface. */
    val logoTint: Color? = null,
    /** From the jump time on in the overview; the selected day in By channel. Empty: no guide data. */
    val programs: List<GuideProgram> = emptyList(),
    /** A gap after [programs] in the overview. */
    val gap: GuideGap? = null,
)

/** No programs until [until] ("6:00 AM"), from [start]. */
internal data class GuideGap(val start: String, val until: String)

internal data class GuideProgram(
    val id: String,
    val channelId: String,
    val title: String,
    /** "8:00 PM". */
    val start: String,
    /** The facts of its " • " line, which [programLine] puts in order: "S1 E6", "60m", "2024", "Documentary". */
    val number: String? = null,
    val length: String? = null,
    val year: String? = null,
    val genre: String? = null,
    /** "8:00–9:00 PM". */
    val timeRange: String? = null,
    val episode: String? = null,
    val synopsis: String? = null,
    /** A still for rows and the overview's lead. */
    val still: Painter? = null,
    /** Key art for the opened program; the still stands in. */
    val art: Painter? = null,
    /** On air now: how far it has run and what is left ("13m"). */
    val progress: Float? = null,
    val timeLeft: String? = null,
    val recording: Boolean = false,
    val scheduled: Boolean = false,
    /** The date heading a By channel day starts here, "Monday, November 16". */
    val section: String? = null,
    /** The item playing in the player: its bar is orange wherever it shows (section 13). */
    val watching: Boolean = false,
) {
    val onNow: Boolean get() = progress != null
}

/** Section 9's details of one program; a missing part is left out. */
internal data class ProgramDetails(
    val id: String,
    val channelId: String,
    val channel: String,
    val channelLogo: Painter? = null,
    val logoTint: Color? = null,
    val title: String,
    val episode: String? = null,
    /** The facts of the line under the title, in [programLine]'s order: "1h 27m", "2024", "Drama". */
    val length: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val ageRating: String? = null,
    val timeRange: String,
    val progress: Float? = null,
    val timeLeft: String? = null,
    val recording: Boolean = false,
    /** A still: across the tablet's page. */
    val picture: Painter? = null,
    /** Key art: the phone's picture card; the still stands in. */
    val art: Painter? = null,
    val synopsis: String? = null,
    val otherAirings: List<ProgramAiring> = emptyList(),
    val credits: List<ProgramCredit> = emptyList(),
    val facts: List<ProgramFact> = emptyList(),
    /** The item playing in the player: its bar is orange (section 13). */
    val watching: Boolean = false,
) {
    val onNow: Boolean get() = progress != null
}

internal data class ProgramAiring(
    val id: String,
    val logo: Painter? = null,
    val logoTint: Color? = null,
    /** "Tomorrow 7:50 PM". */
    val time: String,
    /** As parts: "Amber Stage", "S2 E5", "Low Water". */
    val line: List<String>,
    val scheduled: Boolean = false,
)

internal data class ProgramCredit(val role: CreditRole, val names: List<String>)

internal enum class CreditRole(@param:PluralsRes val label: Int) {
    Cast(R.plurals.guide_cast),
    Director(R.plurals.guide_directors),
    Writer(R.plurals.guide_writers),
}

internal data class ProgramFact(val key: FactKey, val value: String)

internal enum class FactKey(@param:StringRes val label: Int) {
    Genre(R.string.guide_genre),
    Season(R.string.guide_season),
    Episode(R.string.guide_episode),
    Year(R.string.guide_year),
    AgeRating(R.string.guide_age_rating),
    FirstAired(R.string.guide_first_aired),
    Broadcast(R.string.guide_broadcast),
    Categories(R.string.guide_categories),
}

internal class GuideActions(
    val onWatch: (channelId: String) -> Unit,
    val onRecord: (programId: String) -> Unit,
    /** The Record split button's arrow: series and other ways to record. */
    val onRecordOptions: (programId: String) -> Unit,
    /** Details opened; the producer may load more for [GuideState.details]. */
    val onDetails: (programId: String) -> Unit = {},
    val onDay: (Int) -> Unit,
    val onJump: (Int) -> Unit,
    val onTag: (String?) -> Unit,
    /** Details' Show in Guide; the Guide itself already shows the program. */
    val onShowInGuide: (programId: String) -> Unit = {},
    val onAiring: (programId: String) -> Unit = {},
)
