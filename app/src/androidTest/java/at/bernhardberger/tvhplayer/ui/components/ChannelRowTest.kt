package at.bernhardberger.tvhplayer.ui.components

import android.os.Bundle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import kotlin.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import at.bernhardberger.tvhplayer.testing.ImageLoaderRule
import org.junit.Rule
import org.junit.Test

class ChannelRowTest {

    @get:Rule val images = ImageLoaderRule()
    @get:Rule
    val composeTestRule = createComposeRule()

    /** On air from 0 to 1,000 s; a row's progress is its share at the row's now. */
    private val runningEvent = EpgEvent.create(id = EventId(1), channelId = ChannelId(1),
        start = Instant.fromEpochSeconds(0), stop = Instant.fromEpochSeconds(1_000), title = "Večerní zprávy")

    @Test
    fun showsChannelAndProgramme_andConfirmsOnClick() {
        var confirmed = false
        composeTestRule.setContent {
            TVHeadendPlayerTheme {
                ChannelRow(
                    modifier = Modifier.testTag("row"),
                    number = 3,
                    name = "ČT1 HD",
                    programTitle = "Večerní zprávy",
                    programme = runningEvent, nowSec = 500,
                    piconPath = null,
                    onFocus = {},
                    onConfirm = { confirmed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("3 ČT1 HD").assertIsDisplayed()
        composeTestRule.onNodeWithText("Večerní zprávy").assertIsDisplayed()

        composeTestRule.onNodeWithTag("row")
            .requestFocus()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun statusMarkersPreserveProgressWidth_andCoexistWithLongTitle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val channelRecording = context.getString(R.string.channel_recording_spoken)
        val programmeRecording = context.getString(R.string.recording_state_recording)
        val playing = context.getString(R.string.player_on_now)
        val ownEntry = DvrEntry.create(
            id = DvrEntryId(1), channelId = runningEvent.channelId, eventId = runningEvent.id,
            state = DvrEntryState.RECORDING,
        )
        composeTestRule.setContent {
            TVHeadendPlayerTheme {
                Column {
                    repeat(3) { index ->
                        ChannelRow(
                            modifier = Modifier
                                .width(400.dp)
                                .testTag("status-row-$index"),
                            number = index + 1L,
                            name = if (index == 2) {
                                "Short title"
                            } else {
                                "A very long localized channel title with a regional station name that must ellipsize"
                            },
                            programTitle = "Večerní zprávy",
                            programme = runningEvent, nowSec = 500,
                            piconPath = null,
                            channelRecording = index != 1,
                            programmeRecording = ownEntry.takeIf { index == 2 },
                            playingNow = index != 1,
                            onFocus = {},
                            onConfirm = {},
                        )
                    }
                }
            }
        }

        // Standard native row: the bare picon sits on the row's 16dp edge inset.
        composeTestRule.onAllNodesWithTag("channel-picon", useUnmergedTree = true)[0]
            .assertLeftPositionInRootIsEqualTo(16.dp)
        composeTestRule.onAllNodesWithTag(
            "channel-playing-indicator",
            useUnmergedTree = true,
        )[0].assertIsDisplayed()
        composeTestRule.onNodeWithTag("status-row-0")
            .assertContentDescriptionEquals(channelRecording, playing)
            .assertIsSelected()
        composeTestRule.onNodeWithTag("status-row-1").assertIsNotSelected()
        composeTestRule.onNodeWithTag("status-row-2")
            .assertContentDescriptionEquals(programmeRecording, playing)

        fun inRow(index: Int) = hasAnyAncestor(hasTestTag("status-row-$index"))
        val channelTitle = composeTestRule.onNode(hasTestTag("channel-title") and inRow(0), useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        channelTitle.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Long channel title ellipsizes", layouts.single().isLineEllipsized(0))
        val channelMark = composeTestRule.onNode(
            hasTestTag("channel-recording-indicator") and inRow(0), useUnmergedTree = true,
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val channelTitleBounds = channelTitle.fetchSemanticsNode().boundsInRoot
        assertTrue(channelMark.left >= channelTitleBounds.right)
        assertEquals(channelTitleBounds.center.y, channelMark.center.y, 1f)

        val programmeChannelBounds = composeTestRule.onNode(
            hasTestTag("channel-title") and inRow(2), useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val programmeMark = composeTestRule.onNode(
            hasContentDescription(programmeRecording) and inRow(2), useUnmergedTree = true,
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(programmeMark.left >= programmeChannelBounds.right)
        assertEquals(programmeChannelBounds.center.y, programmeMark.center.y, 1f)

        val progressNodes = composeTestRule.onAllNodesWithTag("channel-progress")
        val plainWidth = progressNodes[1].fetchSemanticsNode().boundsInRoot.width
        for (index in listOf(0, 2)) {
            assertEquals(plainWidth, progressNodes[index].fetchSemanticsNode().boundsInRoot.width, 0.5f)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dpadMovesBetweenRowsWithoutAContainerFocusStop() {
        var focusedNumber = 0
        composeTestRule.setContent {
            TVHeadendPlayerTheme {
                Column {
                    repeat(2) { index ->
                        val number = index + 1
                        ChannelRow(
                            modifier = Modifier.testTag("row-$number"),
                            number = number.toLong(),
                            name = "Channel $number",
                            programTitle = "Programme $number",
                            programme = runningEvent, nowSec = 500,
                            piconPath = null,
                            onFocus = { focusedNumber = number },
                            onConfirm = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("row-1").requestFocus()
        composeTestRule.onNodeWithTag("row-1").assertIsFocused()
        composeTestRule.onNodeWithTag("row-1").assertIsNotSelected()
        composeTestRule.onNodeWithTag("row-1").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onNodeWithTag("row-2").assertIsFocused()
        assertEquals(2, focusedNumber)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun focusSelectionRecomposesVisibleChannelRowsWithinBound() {
        val rowNumbers = (1..CHANNEL_RECOMPOSITION_VISIBLE_ROWS).toList()
        val selectedNumber = mutableIntStateOf(1)
        val compositionCounts = IntArray(rowNumbers.size)
        composeTestRule.setContent {
            TVHeadendPlayerTheme {
                LazyColumn {
                    items(rowNumbers, key = { it }) { number ->
                        SideEffect { compositionCounts[number - 1]++ }
                        ChannelRow(
                            modifier = Modifier.testTag("measured-row-$number"),
                            number = number.toLong(),
                            name = "Channel $number",
                            programTitle = "Programme $number",
                            programme = runningEvent, nowSec = 500,
                            piconPath = null,
                            onFocus = { selectedNumber.intValue = number },
                            onConfirm = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("measured-row-1").requestFocus()
        composeTestRule.onNodeWithTag("measured-row-1").assertIsFocused()
        composeTestRule.runOnIdle { compositionCounts.fill(0) }

        composeTestRule.onNodeWithTag("measured-row-1").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onNodeWithTag("measured-row-2").assertIsFocused()

        var recomposedRows = 0
        var maxRowRecompositions = 0
        composeTestRule.runOnIdle {
            assertEquals(2, selectedNumber.intValue)
            recomposedRows = compositionCounts.count { it > 0 }
            maxRowRecompositions = compositionCounts.max()
            assertTrue(
                "At most the old and new focused rows should recompose: " +
                    compositionCounts.contentToString(),
                recomposedRows <= CHANNEL_RECOMPOSITION_MAX_CHANGED_ROWS,
            )
            assertTrue(
                "A channel row recomposed $maxRowRecompositions times for one focus move",
                maxRowRecompositions <= CHANNEL_RECOMPOSITION_PER_ROW_BUDGET,
            )
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            CHANNEL_EVIDENCE_STATUS_CODE,
            Bundle().apply {
                putString("channelsVisibleRows", rowNumbers.size.toString())
                putString("channelsRecomposedRows", recomposedRows.toString())
                putString("channelsMaxRowRecompositions", maxRowRecompositions.toString())
            },
        )
    }

    @Test
    fun ambientProgressDoesNotDrawAStopMarkerAtTheTrackEnd() {
        composeTestRule.setContent {
            TVHeadendPlayerTheme {
                ChannelRow(
                    modifier = Modifier.width(400.dp),
                    number = 1,
                    name = "Channel",
                    programTitle = "Programme",
                    programme = runningEvent, nowSec = 250,
                    piconPath = null,
                    onFocus = {},
                    onConfirm = {},
                )
            }
        }

        val image = composeTestRule.onNodeWithTag("channel-progress").captureToImage()
        val pixels = image.toPixelMap()
        val y = image.height / 2
        val fill = pixels[image.width / 8, y]
        val track = pixels[image.width / 2, y]
        val trackEnd = pixels[image.width - 2, y]

        assertNotEquals(fill, trackEnd)
        for (x in image.width / 2 until image.width - 4) {
            assertEquals("unexpected marker at x=$x", track, pixels[x, y])
        }
    }
}

private const val CHANNEL_EVIDENCE_STATUS_CODE = 2
private const val CHANNEL_RECOMPOSITION_VISIBLE_ROWS = 6
private const val CHANNEL_RECOMPOSITION_MAX_CHANGED_ROWS = 2
private const val CHANNEL_RECOMPOSITION_PER_ROW_BUDGET = 1
