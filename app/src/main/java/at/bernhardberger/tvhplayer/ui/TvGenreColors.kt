package at.bernhardberger.tvhplayer.ui

import androidx.compose.ui.graphics.Color
import at.bernhardberger.tvhplayer.core.ProgrammeCategory

/**
 * A Guide genre family's colors: [base] marks the family in the Genre panel and [tint] fills a
 * programme (12% of the base over containerHigh), focused or not. Programmes without a genre code
 * stay plain.
 */
class TvGenreColors(val base: Color, val tint: Color) {
    /** Half the sRGB value preserves hue/saturation and clears 3:1 on the native light focus card. */
    val focusedBase: Color get() = Color(base.red * 0.5f, base.green * 0.5f, base.blue * 0.5f, base.alpha)
}

private val GenreFilmDrama = TvGenreColors(Color(0xFFC8B7FF), Color(0xFF3B3B45))
private val GenreNews = TvGenreColors(Color(0xFF7699FB), Color(0xFF313745))
private val GenreEntertainment = TvGenreColors(Color(0xFFFABECB), Color(0xFF413C3F))
private val GenreSport = TvGenreColors(Color(0xFF73F094), Color(0xFF314238))
private val GenreChildren = TvGenreColors(Color(0xFFFEE657), Color(0xFF424131))
private val GenreMusic = TvGenreColors(Color(0xFFFD80C4), Color(0xFF42343E))
private val GenreArtsCulture = TvGenreColors(Color(0xFFB872CD), Color(0xFF39333F))
private val GenreSocietyPolitics = TvGenreColors(Color(0xFFD9B88B), Color(0xFF3D3B37))
private val GenreEducationFactual = TvGenreColors(Color(0xFF5EC6B6), Color(0xFF2E3D3D))
private val GenreLifestyleLeisure = TvGenreColors(Color(0xFFA9BD40), Color(0xFF373C2E))

/** The family's colors, or null for a programme without a genre code. */
fun tvGenreColors(category: ProgrammeCategory?): TvGenreColors? = when (category) {
    ProgrammeCategory.FILM_DRAMA -> GenreFilmDrama
    ProgrammeCategory.NEWS -> GenreNews
    ProgrammeCategory.ENTERTAINMENT -> GenreEntertainment
    ProgrammeCategory.SPORT -> GenreSport
    ProgrammeCategory.CHILDREN -> GenreChildren
    ProgrammeCategory.MUSIC -> GenreMusic
    ProgrammeCategory.ARTS_CULTURE -> GenreArtsCulture
    ProgrammeCategory.SOCIETY_POLITICS -> GenreSocietyPolitics
    ProgrammeCategory.EDUCATION_FACTUAL -> GenreEducationFactual
    ProgrammeCategory.LIFESTYLE_LEISURE -> GenreLifestyleLeisure
    ProgrammeCategory.ALL, null -> null
}
