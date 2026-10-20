package at.bernhardberger.tvhplayer.settings

/** How the Channels screen presents its channels, in View options order. */
enum class ChannelsLayout {
    IMMERSIVE,
    LIST,
    GRID,
}

/**
 * Where View options may show programme art, each with the layouts that use it. Off, cards show
 * the channel logo and the backdrop takes the logo's colour.
 */
enum class ChannelsArtwork(val layouts: Set<ChannelsLayout>) {
    CARDS(setOf(ChannelsLayout.GRID, ChannelsLayout.IMMERSIVE)),
    // Immersive's corner art is its backdrop already.
    BACKDROP(setOf(ChannelsLayout.LIST, ChannelsLayout.GRID)),
}

/**
 * The Channels screen's view, remembered for the whole app. Art settings the current layout does
 * not use are kept, so switching back restores them.
 */
data class ChannelsView(
    val layout: ChannelsLayout = ChannelsLayout.IMMERSIVE,
    val cardArt: Boolean = false,
    val backdropArt: Boolean = true,
) {
    fun shows(artwork: ChannelsArtwork): Boolean = when (artwork) {
        ChannelsArtwork.CARDS -> cardArt
        ChannelsArtwork.BACKDROP -> backdropArt
    }

    fun toggled(artwork: ChannelsArtwork): ChannelsView = when (artwork) {
        ChannelsArtwork.CARDS -> copy(cardArt = !cardArt)
        ChannelsArtwork.BACKDROP -> copy(backdropArt = !backdropArt)
    }
}

/** Stored values to a view; anything missing or unknown keeps its default. */
fun resolveChannelsView(layout: String?, cardArt: Boolean?, backdropArt: Boolean?): ChannelsView {
    val default = ChannelsView()
    return ChannelsView(
        layout = ChannelsLayout.entries.firstOrNull { it.name == layout } ?: default.layout,
        cardArt = cardArt ?: default.cardArt,
        backdropArt = backdropArt ?: default.backdropArt,
    )
}
