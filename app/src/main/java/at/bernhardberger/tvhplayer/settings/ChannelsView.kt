package at.bernhardberger.tvhplayer.settings

/** How the Channels screen presents its channels, in View options order. */
enum class ChannelsLayout {
    IMMERSIVE,
    LIST,
    GRID,
}

/**
 * The Channels screen's view, remembered for the whole app. Each layout keeps its own art switch,
 * so switching layouts never changes another layout's look.
 */
data class ChannelsView(
    val layout: ChannelsLayout = ChannelsLayout.IMMERSIVE,
    /**
     * The layouts whose art switch is on. Cards show channel logos by default; the list starts
     * with the focused programme's picture behind it.
     */
    val programArt: Set<ChannelsLayout> = setOf(ChannelsLayout.LIST),
) {
    /** Whether cards and the backdrop behind them show programme pictures rather than logos. */
    val showsProgramArt: Boolean get() = layout in programArt

    fun toggledProgramArt(): ChannelsView =
        copy(programArt = if (layout in programArt) programArt - layout else programArt + layout)
}

/** Stored values to a view; anything missing or unknown keeps its default. */
fun resolveChannelsView(layout: String?, programArt: (ChannelsLayout) -> Boolean?): ChannelsView = ChannelsView(
    layout = ChannelsLayout.entries.firstOrNull { it.name == layout } ?: ChannelsView().layout,
    programArt = ChannelsLayout.entries.filterTo(mutableSetOf()) { programArt(it) ?: (it in ChannelsView().programArt) },
)
