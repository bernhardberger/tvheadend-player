package at.bernhardberger.tvhplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeCategory

@Composable
fun programmeCategoryLabel(category: ProgrammeCategory): String = stringResource(
    when (category) {
        ProgrammeCategory.ALL -> R.string.programme_category_all
        ProgrammeCategory.FILM_DRAMA -> R.string.programme_category_film_drama
        ProgrammeCategory.NEWS -> R.string.programme_category_news
        ProgrammeCategory.ENTERTAINMENT -> R.string.programme_category_entertainment
        ProgrammeCategory.SPORT -> R.string.programme_category_sport
        ProgrammeCategory.CHILDREN -> R.string.programme_category_children
        ProgrammeCategory.MUSIC -> R.string.programme_category_music
        ProgrammeCategory.ARTS_CULTURE -> R.string.programme_category_arts_culture
        ProgrammeCategory.SOCIETY_POLITICS -> R.string.programme_category_society_politics
        ProgrammeCategory.EDUCATION_FACTUAL -> R.string.programme_category_education_factual
        ProgrammeCategory.LIFESTYLE_LEISURE -> R.string.programme_category_lifestyle_leisure
    }
)
