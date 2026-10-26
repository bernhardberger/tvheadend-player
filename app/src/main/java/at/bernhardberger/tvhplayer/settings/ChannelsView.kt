package at.bernhardberger.tvhplayer.settings

/** How the Channels screen presents its channels, in View options order. */
enum class ChannelsLayout {
    IMMERSIVE,
    LIST,
    GRID;

    /** The list always shows the focused programme's picture, so only the card layouts switch art. */
    val hasArtSwitch: Boolean get() = this != LIST
}

/**
 * The Channels screen's view, remembered for the whole app. Each card layout keeps its own art
 * and channel names switches, so switching layouts never changes another layout's look.
 */
data class ChannelsView(
    val layout: ChannelsLayout = ChannelsLayout.IMMERSIVE,
    /** The card layouts whose art switch is on: immersive cards show programme pictures by default, grid cards logos. */
    val programArt: Set<ChannelsLayout> = DefaultProgramArt,
    /** The card layouts that name each card's channel below it; off, the logo on or in the card names it. */
    val channelNames: Set<ChannelsLayout> = emptySet(),
) {
    /** Whether cards and the backdrop behind them show programme pictures rather than logos. */
    val showsProgramArt: Boolean get() = !layout.hasArtSwitch || layout in programArt

    fun toggledProgramArt(): ChannelsView =
        copy(programArt = if (layout in programArt) programArt - layout else programArt + layout)

    /** Whether cards name their channel below them; the list always names it. */
    val showsChannelNames: Boolean get() = !layout.hasArtSwitch || layout in channelNames

    fun toggledChannelNames(): ChannelsView =
        copy(channelNames = if (layout in channelNames) channelNames - layout else channelNames + layout)
}

private val DefaultProgramArt = setOf(ChannelsLayout.IMMERSIVE)

/** Stored values to a view; anything missing or unknown keeps its default. */
fun resolveChannelsView(
    layout: String?,
    channelNames: (ChannelsLayout) -> Boolean? = { null },
    programArt: (ChannelsLayout) -> Boolean?,
): ChannelsView = ChannelsView(
    layout = ChannelsLayout.entries.firstOrNull { it.name == layout } ?: ChannelsView().layout,
    programArt = ChannelsLayout.entries.filterTo(mutableSetOf()) { it.hasArtSwitch && programArt(it) ?: (it in DefaultProgramArt) },
    channelNames = ChannelsLayout.entries.filterTo(mutableSetOf()) { it.hasArtSwitch && channelNames(it) ?: false },
)
