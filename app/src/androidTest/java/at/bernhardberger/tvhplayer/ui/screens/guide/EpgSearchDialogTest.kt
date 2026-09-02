package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso.pressBack
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgSearchResult
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EpgSearchDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun initialFocusAndBackUnwindEditingBeforeDismissingSearch() {
        var dismissCount = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                EpgSearchDialog(
                    contentPadding = PaddingValues(),
                    query = "",
                    result = null,
                    searching = false,
                    searchEnabled = true,
                    channelName = { null },
                    onQueryChange = {},
                    onSearch = {},
                    onOpenDetails = {},
                    onDismiss = { dismissCount++ },
                )
            }
        }

        composeRule.onNodeWithTag("epg-search-field")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        dispatchBack()
        composeRule.onNodeWithTag("epg-search-field").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, dismissCount) }

        dispatchBack()
        val editingWasStillActive = composeRule.runOnIdle { dismissCount == 0 }
        if (editingWasStillActive) dispatchBack()
        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test
    fun restoredResultFocusDispatchesDetailsFromDpadCenter() {
        val event = event(id = 27)
        var openedEvent: EpgEvent? = null
        var focusRestored = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                EpgSearchDialog(
                    contentPadding = PaddingValues(),
                    query = "News",
                    result = EpgSearchResult.Available.create(listOf(event)),
                    searching = false,
                    searchEnabled = true,
                    channelName = { "Channel 7" },
                    restoreFocusTo = event.id,
                    onFocusRestored = { focusRestored = true },
                    onQueryChange = {},
                    onSearch = {},
                    onOpenDetails = { openedEvent = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("epg-search-result-27")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle {
            assertTrue(focusRestored)
            assertSame(event, openedEvent)
        }
    }

    @Test
    fun controlsAndResultAreReachableFromInitialFieldWithDpad() {
        val event = event(id = 41)
        var searchCount = 0
        var openedEvent: EpgEvent? = null
        composeRule.setContent {
            TVHeadendPlayerTheme {
                EpgSearchDialog(
                    contentPadding = PaddingValues(),
                    query = "News",
                    result = EpgSearchResult.Available.create(listOf(event)),
                    searching = false,
                    searchEnabled = true,
                    channelName = { "Channel 7" },
                    onQueryChange = {},
                    onSearch = { searchCount++ },
                    onOpenDetails = { openedEvent = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("epg-search-field")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("epg-search-submit")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("epg-search-close")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("epg-search-submit")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("epg-search-result-41")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle {
            assertEquals(1, searchCount)
            assertSame(event, openedEvent)
        }
    }

    @Test
    fun blankQueryRoutesBetweenFieldAndCloseWithoutDisabledSearch() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                EpgSearchDialog(
                    contentPadding = PaddingValues(),
                    query = "",
                    result = null,
                    searching = false,
                    searchEnabled = true,
                    channelName = { null },
                    onQueryChange = {},
                    onSearch = {},
                    onOpenDetails = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("epg-search-field")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("epg-search-close")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("epg-search-field").assertIsFocused()
    }

    @Test
    fun disconnectedResultsRouteUpToCloseWithoutDisabledSearch() {
        val event = event(id = 63)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                EpgSearchDialog(
                    contentPadding = PaddingValues(),
                    query = "News",
                    result = EpgSearchResult.Available.create(listOf(event)),
                    searching = false,
                    searchEnabled = false,
                    channelName = { "Channel 7" },
                    onQueryChange = {},
                    onSearch = {},
                    onOpenDetails = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("epg-search-field")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("epg-search-close")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("epg-search-result-63")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("epg-search-close").assertIsFocused()
    }

    @Test
    fun loadingMovesFocusFromDisabledSearchToClose() {
        var searching by mutableStateOf(false)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                EpgSearchDialog(
                    contentPadding = PaddingValues(),
                    query = "News",
                    result = null,
                    searching = searching,
                    searchEnabled = true,
                    channelName = { null },
                    onQueryChange = {},
                    onSearch = { searching = true },
                    onOpenDetails = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("epg-search-field")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("epg-search-submit")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithTag("epg-search-close").assertIsFocused()
    }

    private fun dispatchBack() {
        pressBack()
        composeRule.waitForIdle()
    }

    private fun event(id: Long): EpgEvent = EpgEvent.create(
        id = EventId(id),
        channelId = ChannelId(7),
        start = Instant.fromEpochSeconds(1_800_000_000),
        stop = Instant.fromEpochSeconds(1_800_003_600),
        title = "Evening News",
    )
}
