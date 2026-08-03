package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.core.ChannelTag
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ChannelTagSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabsCommitServerTagOnFocus() {
        var selectedTagId by mutableStateOf<Int?>(null)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ChannelTagSelector(
                    tags = listOf(ChannelTag(id = 7, name = "News", index = 1)),
                    activeTagId = selectedTagId,
                    onSelectTag = { selectedTagId = it },
                )
            }
        }

        composeRule.onNodeWithText("All channels").assertIsDisplayed()
        composeRule.onNodeWithText("News").assertIsDisplayed()
        assertEquals(
            Role.Tab,
            composeRule.onNodeWithText("News")
                .fetchSemanticsNode().config[SemanticsProperties.Role],
        )
        composeRule.onNodeWithText("All channels").requestFocus()
        composeRule.onNodeWithText("All channels").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithText("News").assertIsFocused()
        composeRule.runOnIdle { assertEquals(7, selectedTagId) }
    }

    @Test
    fun lateralEntryRestoresTheActiveTab() {
        var movedToContent = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                Row {
                    Button(onClick = {}) { Text("Before scopes") }
                    ChannelTagSelector(
                        tags = listOf(ChannelTag(id = 7, name = "News", index = 1)),
                        activeTagId = 7,
                        onSelectTag = {},
                        onMoveToContent = {
                            movedToContent += 1
                            true
                        },
                        modifier = Modifier.width(300.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Before scopes").requestFocus().performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithText("News").assertIsFocused()
        composeRule.onNodeWithText("News").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.runOnIdle { assertEquals(1, movedToContent) }
    }

    @Test
    fun downAndOkMoveFocusToRestoredContent() {
        lateinit var contentFocus: FocusRequester
        composeRule.setContent {
            contentFocus = remember { FocusRequester() }
            TVHeadendPlayerTheme {
                Column {
                    ChannelTagSelector(
                        tags = listOf(ChannelTag(id = 7, name = "News", index = 1)),
                        activeTagId = 7,
                        onSelectTag = {},
                        onMoveToContent = contentFocus::requestFocus,
                    )
                    Button(
                        onClick = {},
                        modifier = Modifier.focusRequester(contentFocus),
                    ) {
                        Text("Restored content")
                    }
                }
            }
        }

        composeRule.onNodeWithText("News").requestFocus().performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.onNodeWithText("Restored content").assertIsFocused()

        composeRule.onNodeWithText("News").requestFocus().performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithText("Restored content").assertIsFocused()
    }

    @Test
    fun chooserOmitsAllChannelsWhenThatScopeIsDisabled() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ChannelTagSelector(
                    tags = listOf(ChannelTag(id = 7, name = "News", index = 1)),
                    activeTagId = 7,
                    allChannelsVisible = false,
                    onSelectTag = {},
                )
            }
        }

        composeRule.onNodeWithText("News").assertIsDisplayed()
        composeRule.onNodeWithText("All channels").assertDoesNotExist()
    }

    @Test
    fun tabsFallBackToFirstVisibleScopeWhenActiveTagDisappears() {
        var tags by mutableStateOf(
            (1..20).map { id -> ChannelTag(id = id, name = "Tag $id", index = id) }
        )
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ChannelTagSelector(
                    tags = tags,
                    activeTagId = 20,
                    onSelectTag = {},
                )
            }
        }

        composeRule.onNodeWithText("Tag 20").requestFocus().assertIsFocused()
        composeRule.runOnIdle { tags = tags.dropLast(1) }
        composeRule.onNodeWithText("All channels").assertIsFocused()
    }

    @Test
    fun overflowingInactiveTabsDoNotPaintOverTheBacking() {
        var selectedTagId by mutableStateOf<Int?>(5)
        val tags = longTags()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ChannelTagSelector(
                    tags = tags,
                    activeTagId = selectedTagId,
                    onSelectTag = { selectedTagId = it },
                    modifier = Modifier
                        .width(260.dp)
                        .height(80.dp)
                        .background(Color.Red)
                        .testTag("overflowing-scopes"),
                )
            }
        }

        composeRule.onNodeWithText(tags.last().name).requestFocus().assertIsFocused()
        composeRule.waitForIdle()

        val overflowPixels = composeRule.onNodeWithTag("overflowing-scopes")
            .captureToImage()
            .toPixelMap()
        val sampleY = overflowPixels.height - 2
        assertEquals(1f, overflowPixels[1, sampleY].red, 0.06f)
        assertEquals(1f, overflowPixels[overflowPixels.width - 2, sampleY].red, 0.06f)

        composeRule.onNodeWithText("All channels").requestFocus().assertIsFocused()
        composeRule.waitForIdle()
        val firstTabPixels = composeRule.onNodeWithTag("overflowing-scopes")
            .captureToImage()
            .toPixelMap()
        assertEquals(1f, firstTabPixels[1, sampleY].red, 0.06f)
    }

    @Test
    fun overflowingInactiveTabsDoNotPaintOverTheBackingInRtl() {
        val tags = longTags()
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                TVHeadendPlayerTheme {
                    ChannelTagSelector(
                        tags = tags,
                        activeTagId = 5,
                        onSelectTag = {},
                        modifier = Modifier
                            .width(260.dp)
                            .height(80.dp)
                            .background(Color.Red)
                            .testTag("rtl-overflowing-scopes"),
                    )
                }
            }
        }

        composeRule.onNodeWithText(tags.last().name).requestFocus().assertIsFocused()
        composeRule.waitForIdle()

        val pixels = composeRule.onNodeWithTag("rtl-overflowing-scopes")
            .captureToImage()
            .toPixelMap()
        val sampleY = pixels.height - 2
        assertEquals(1f, pixels[pixels.width - 2, sampleY].red, 0.06f)
        assertEquals(1f, pixels[1, sampleY].red, 0.06f)
    }

    @Test
    fun focusedTabIsProtectedBeforeSelectionStateCatchesUp() {
        var requestedTagId: Int? = null
        val tags = longTags()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ChannelTagSelector(
                    tags = tags,
                    activeTagId = 5,
                    onSelectTag = { requestedTagId = it },
                    modifier = Modifier.width(260.dp),
                )
            }
        }

        composeRule.onNodeWithText(tags.last().name).requestFocus().performKeyInput {
            pressKey(Key.DirectionLeft)
        }

        composeRule.onNodeWithText(tags[tags.lastIndex - 1].name).assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(4, requestedTagId)
        }
    }

    private fun longTags(): List<ChannelTag> = (1..5).map { id ->
        ChannelTag(
            id = id,
            name = "Long channel scope $id",
            index = id,
        )
    }
}
