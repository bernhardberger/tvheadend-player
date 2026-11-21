package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvLogoPlateColor
import at.bernhardberger.tvhplayer.ui.TvTextColors

@Composable
internal fun Modifier.returnToFullScreenAction(returnsToPlayback: Boolean): Modifier {
    if (!returnsToPlayback) return this
    val label = stringResource(R.string.return_to_full_screen)
    // The enclosing TV Material item supplies the actual click callback.
    return semantics { onClick(label = label, action = null) }
}

@Composable
internal fun CardPlaybackPlate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(TvLogoPlateColor, MaterialTheme.shapes.extraSmall).padding(3.dp)) {
        CompositionLocalProvider(LocalContentColor provides TvTextColors.primary, content = content)
    }
}
