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
 * switch, so switching layouts never changes another layout's look.
 */
data class ChannelsView(
    val layout: ChannelsLayout = ChannelsLayout.IMMERSIVE,
    /** The card layouts whose art switch is on; cards show channel logos by default. */
    val programArt: Set<ChannelsLayout> = emptySet(),
) {
    /** Whether cards and the backdrop behind them show programme pictures rather than logos. */
    val showsProgramArt: Boolean get() = !layout.hasArtSwitch || layout in programArt

    fun toggledProgramArt(): ChannelsView =
        copy(programArt = if (layout in programArt) programArt - layout else programArt + layout)
}

/** Stored values to a view; anything missing or unknown keeps its default. */
fun resolveChannelsView(layout: String?, programArt: (ChannelsLayout) -> Boolean?): ChannelsView = ChannelsView(
    layout = ChannelsLayout.entries.firstOrNull { it.name == layout } ?: ChannelsView().layout,
    programArt = ChannelsLayout.entries.filterTo(mutableSetOf()) { it.hasArtSwitch && programArt(it) ?: false },
)
