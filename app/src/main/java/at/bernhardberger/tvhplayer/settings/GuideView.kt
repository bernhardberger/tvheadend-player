package at.bernhardberger.tvhplayer.settings

/** How tall the Guide's channel rows are, in View options order. */
enum class GuideRows {
    DENSE,
    REGULAR,
}

/**
 * The Guide's view, remembered for the whole app; [headline] shows the focused programme above the grid
 * and [genreColors] tints programmes by their genre code.
 */
data class GuideView(
    val rows: GuideRows = GuideRows.DENSE,
    val headline: Boolean = true,
    val genreColors: Boolean = true,
)

/** Stored values to a view; anything missing or unknown keeps its default. */
fun resolveGuideView(rows: String?, headline: Boolean?, genreColors: Boolean?): GuideView = GuideView(
    rows = GuideRows.entries.firstOrNull { it.name == rows } ?: GuideView().rows,
    headline = headline ?: GuideView().headline,
    genreColors = genreColors ?: GuideView().genreColors,
)
