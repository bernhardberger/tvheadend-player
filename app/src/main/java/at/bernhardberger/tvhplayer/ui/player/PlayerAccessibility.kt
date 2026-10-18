package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal fun Modifier.playerRootSemantics(label: String): Modifier =
    semantics { contentDescription = label }

/**
 * The live player root's label and focus target. Not [active] during the startup reveal: the root
 * then takes no focus and stays out of the semantics tree, while its passive Banner keeps its own.
 */
internal fun Modifier.livePlayerRoot(label: String, active: Boolean): Modifier =
    if (active) playerRootSemantics(label).focusable() else this
