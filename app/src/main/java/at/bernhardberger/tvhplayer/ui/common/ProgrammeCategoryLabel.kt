package at.bernhardberger.tvhplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.core.ProgrammeCategory

@Composable
fun programmeCategoryLabel(category: ProgrammeCategory): String = stringResource(programmeCategoryLabelRes(category))
