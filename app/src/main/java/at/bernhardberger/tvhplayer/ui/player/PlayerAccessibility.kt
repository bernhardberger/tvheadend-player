package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal fun Modifier.playerRootSemantics(label: String): Modifier =
    semantics { contentDescription = label }
