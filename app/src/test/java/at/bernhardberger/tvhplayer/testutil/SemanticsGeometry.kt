package at.bernhardberger.tvhplayer.testutil

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag

internal fun ComposeContentTestRule.tagExists(tag: String) =
    onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeContentTestRule.focusedTags(): List<String> =
    onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
        .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }

internal fun ComposeContentTestRule.tagBounds(tag: String, useUnmergedTree: Boolean = true) =
    onNodeWithTag(tag, useUnmergedTree = useUnmergedTree).fetchSemanticsNode().boundsInRoot
