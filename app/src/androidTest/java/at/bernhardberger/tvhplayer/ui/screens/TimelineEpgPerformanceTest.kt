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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineChannelHeader
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineProgrammeCell
import coil3.ImageLoader
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class TimelineEpgPerformanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun syntheticThreeHundredTimelineRowsRemainVirtualizedAndScrollable() {
        val channels = (1..300).map { number ->
            Channel.create(
                id = ChannelId(number.toLong()),
                name = "Channel $number",
                number = number.toLong(),
            )
        }

        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                LazyColumn(Modifier.testTag("timeline-rows")) {
                    items(channels, key = { it.id.value }) { channel ->
                        Box(Modifier.width(190.dp).height(76.dp)) {
                            TimelineChannelHeader(
                                channel = channel,
                                number = channel.number?.toInt(),
                                imageLoader = imageLoader,
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("timeline-rows").performScrollToIndex(299)
        composeRule.onNodeWithText("300  Channel 300").assertIsDisplayed()
    }

    @Test
    fun consecutiveShortProgrammeCardsDoNotOverlap() {
        val channel = Channel.create(ChannelId(1), name = "Channel", number = 1)
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

        val firstBounds = composeRule.onNode(
            hasClickAction() and hasAnyAncestor(hasTestTag("first-short-programme")),
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        val secondBounds = composeRule.onNode(
            hasClickAction() and hasAnyAncestor(hasTestTag("second-short-programme")),
            useUnmergedTree = true,
        )
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

    private fun event(id: Int, start: Long, stop: Long) = EpgEvent.create(
        id = EventId(id.toLong()),
        channelId = ChannelId(1),
        start = Instant.fromEpochSeconds(start),
        stop = Instant.fromEpochSeconds(stop),
        title = "Event $id",
    )
}
