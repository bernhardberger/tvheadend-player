package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TvSpacing16

/** A list group label, announced in its original casing. */
@Composable
fun ListSectionLabel(text: String, modifier: Modifier = Modifier, first: Boolean = false) {
    Text(
        text.uppercase(LocalConfiguration.current.locales[0]),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = TvSpacing16, top = if (first) 0.dp else TvSpacing16)
            .semantics { heading(); contentDescription = text },
    )
}
