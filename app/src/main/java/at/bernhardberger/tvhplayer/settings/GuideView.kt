package at.bernhardberger.tvhplayer.settings

/**
 * The Guide's row density, in View options order: [REGULAR] rows carry a title and a start time,
 * [DENSE] rows only the title, so more channels fit.
 */
enum class GuideRows {
    REGULAR,
    DENSE,
}

/**
 * The Guide's view, remembered for the whole app; [headline] shows the focused programme above the grid
 * and [genreColors] tints programmes by their genre code.
 */
data class GuideView(
    val rows: GuideRows = GuideRows.REGULAR,
    val headline: Boolean = true,
    val genreColors: Boolean = true,
)

/** Stored values to a view; anything missing or unknown keeps its default. */
fun resolveGuideView(rows: String?, headline: Boolean?, genreColors: Boolean?): GuideView = GuideView(
    rows = GuideRows.entries.firstOrNull { it.name == rows } ?: GuideView().rows,
    headline = headline ?: GuideView().headline,
    genreColors = genreColors ?: GuideView().genreColors,
)
