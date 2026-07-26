package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimelineEpgPerformanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun syntheticThreeHundredTimelineRowsRemainVirtualizedAndScrollable() {
        val channels = (1..300).map { number ->
            ChannelUi(
                id = number,
                name = "Channel $number",
                number = number,
                icon = null,
            )
        }

        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                LazyColumn(Modifier.testTag("timeline-rows")) {
                    items(channels, key = { it.id }) { channel ->
                        Box(Modifier.width(190.dp).height(76.dp)) {
                            TimelineChannelHeader(
                                channel = channel,
                                number = channel.number,
                                imageLoader = imageLoader,
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("timeline-rows").performScrollToIndex(299)
        composeRule.onNodeWithText("Channel 300").assertIsDisplayed()
    }

    @Test
    fun consecutiveShortProgrammeCardsDoNotOverlap() {
        val channel = ChannelUi(id = 1, name = "Channel", number = 1, icon = null)
        val first = event(id = 1, start = 0, stop = 5 * 60)
        val second = event(id = 2, start = 5 * 60, stop = 10 * 60)
        val cardWidth = 600.dp / 36f

        composeRule.setContent {
            val selectedFocus = remember { FocusRequester() }
            TVHeadendPlayerTheme {
                Box(Modifier.width(600.dp).height(76.dp)) {
                    TimelineProgrammeCell(
                        event = first,
                        channel = channel,
                        recording = null,
                        nowSec = 0,
                        selected = false,
                        focusRequester = selectedFocus,
                        onFocused = {},
                        onOpenDetails = {},
                        onMoveFocus = { false },
                        width = cardWidth,
                        modifier = Modifier
                            .testTag("first-short-programme")
                            .width(cardWidth)
                            .fillMaxHeight(),
                    )
                    TimelineProgrammeCell(
                        event = second,
                        channel = channel,
                        recording = null,
                        nowSec = 0,
                        selected = false,
                        focusRequester = selectedFocus,
                        onFocused = {},
                        onOpenDetails = {},
                        onMoveFocus = { false },
                        width = cardWidth,
                        modifier = Modifier
                            .testTag("second-short-programme")
                            .offset(x = cardWidth)
                            .width(cardWidth)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        val firstBounds = composeRule.onNodeWithTag("first-short-programme")
            .fetchSemanticsNode().boundsInRoot
        val secondBounds = composeRule.onNodeWithTag("second-short-programme")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(firstBounds.right <= secondBounds.left)
    }

    @Test
    fun recordingConfirmationDefaultsToSafeBackAction() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ConfirmProgrammeActionDialog(
                    action = ProgrammeAction.RECORD,
                    programmeTitle = "Eine außergewöhnlich lange deutsche Sendungsbezeichnung",
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Record “Eine außergewöhnlich lange deutsche Sendungsbezeichnung”?"
        )
            .assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsFocused()
    }

    private fun event(id: Int, start: Long, stop: Long) = EpgEventEntry(
        eventId = id,
        channelId = 1,
        start = start,
        stop = stop,
        title = "Event $id",
    )
}
