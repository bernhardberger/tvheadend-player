package at.bernhardberger.tvhplayer.settings

/** How the Channels screen presents its channels. */
enum class ChannelsLayout {
    LIST,
    GRID,
    IMMERSIVE,
}

/** Which picture a channel card leads with; without a programme picture it shows the logo. */
enum class ChannelsCardPicture {
    PROGRAMME,
    LOGO,
}

/** Column counts the grid offers. */
val ChannelsGridColumnCounts = listOf(3, 4)

/** One value of a View options setting. */
sealed interface ChannelsViewChoice {
    data class Layout(val layout: ChannelsLayout) : ChannelsViewChoice
    data class GridColumns(val count: Int) : ChannelsViewChoice
    data class CardPicture(val picture: ChannelsCardPicture) : ChannelsViewChoice
}

/** A View options setting and the values it offers. */
enum class ChannelsViewOption {
    LAYOUT,
    GRID_COLUMNS,
    CARD_PICTURE,
    ;

    val choices: List<ChannelsViewChoice>
        get() = when (this) {
            LAYOUT -> ChannelsLayout.entries.map(ChannelsViewChoice::Layout)
            GRID_COLUMNS -> ChannelsGridColumnCounts.map(ChannelsViewChoice::GridColumns)
            CARD_PICTURE -> ChannelsCardPicture.entries.map(ChannelsViewChoice::CardPicture)
        }
}

/** The options a layout offers of its own, in View options order. */
val ChannelsLayout.options: List<ChannelsViewOption>
    get() = when (this) {
        ChannelsLayout.LIST -> emptyList()
        ChannelsLayout.GRID -> listOf(ChannelsViewOption.GRID_COLUMNS, ChannelsViewOption.CARD_PICTURE)
        ChannelsLayout.IMMERSIVE -> listOf(ChannelsViewOption.CARD_PICTURE)
    }

/**
 * The Channels screen's view, remembered for the whole app. Values the current layout does not
 * use are kept, so switching back restores them.
 */
data class ChannelsView(
    val layout: ChannelsLayout = ChannelsLayout.LIST,
    val gridColumns: Int = 4,
    val cardPicture: ChannelsCardPicture = ChannelsCardPicture.PROGRAMME,
) {
    /** View options: the layout first, then only the current layout's options. */
    val sections: List<ChannelsViewOption> get() = listOf(ChannelsViewOption.LAYOUT) + layout.options

    fun isSelected(choice: ChannelsViewChoice): Boolean = when (choice) {
        is ChannelsViewChoice.Layout -> choice.layout == layout
        is ChannelsViewChoice.GridColumns -> choice.count == gridColumns
        is ChannelsViewChoice.CardPicture -> choice.picture == cardPicture
    }

    fun with(choice: ChannelsViewChoice): ChannelsView = when (choice) {
        is ChannelsViewChoice.Layout -> copy(layout = choice.layout)
        is ChannelsViewChoice.GridColumns -> copy(gridColumns = choice.count)
        is ChannelsViewChoice.CardPicture -> copy(cardPicture = choice.picture)
    }
}

/** Stored values to a view; anything missing or unknown keeps its default. */
fun resolveChannelsView(layout: String?, gridColumns: Int?, cardPicture: String?): ChannelsView {
    val default = ChannelsView()
    return ChannelsView(
        layout = ChannelsLayout.entries.firstOrNull { it.name == layout } ?: default.layout,
        gridColumns = gridColumns?.takeIf { it in ChannelsGridColumnCounts } ?: default.gridColumns,
        cardPicture = ChannelsCardPicture.entries.firstOrNull { it.name == cardPicture } ?: default.cardPicture,
    )
}
