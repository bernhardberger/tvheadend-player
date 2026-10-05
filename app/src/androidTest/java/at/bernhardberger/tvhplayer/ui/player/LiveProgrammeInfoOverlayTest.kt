package at.bernhardberger.tvhplayer.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class LiveProgrammeInfoOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun noEpgRemainsOpenChannelIdentifiedAndSafelyFocused() {
        setInfoOverlay(event = { null })

        composeRule.onNodeWithText("Programme information unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("7 • Das Erste").assertIsDisplayed()
        composeRule.onNodeWithText("No programme information is available for Das Erste right now.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("live-info-close").assertIsFocused()
        composeRule.onNodeWithTag("live-info-overlay").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.PaneTitle,
                "Programme information",
            )
        )
    }

    @Test
    fun epgDisappearanceTransitionsToUnavailableWithoutClosingInfo() {
        var currentEvent by mutableStateOf<EpgEvent?>(event(id = 42))
        setInfoOverlay(event = { currentEvent })

        composeRule.onNodeWithTag("live-info-record").assertIsFocused()
        composeRule.runOnIdle { currentEvent = null }

        composeRule.onNodeWithText("Programme information unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("7 • Das Erste").assertIsDisplayed()
        composeRule.onNodeWithTag("live-info-close").assertIsFocused()
    }

    @Test
    fun detailsOwnRecordActivationWithoutTheObsoleteConfirmationSheet() {
        val actions = mutableListOf<ProgrammeAction>()
        setInfoOverlay(event = { event(id = 42) }, onAction = { actions += it })

        composeRule.onNodeWithTag("live-info-record").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.runOnIdle { assertEquals(listOf(ProgrammeAction.RECORD), actions) }
        composeRule.onNodeWithTag("live-info-record").assertIsFocused()
        composeRule.onNodeWithTag("programme-recording-confirm").assertDoesNotExist()
    }

    @Test
    fun closingUnavailableInfoRecomposesControlsAndRestoresTheIdentityCard() {
        var infoOpen by mutableStateOf(false)
        var restoreInfoFocus by mutableStateOf(false)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = PlayerChromeMode.CONTROLS,
                    content = PlayerChromeContent("", liveInfoBarData(7, "Das Erste", null, null, false, 0, "")),
                    timeline = PlayerChromeTimeline.Live(
                        AppTimeshiftState(),
                        nowSec = 1_500L,
                        programme = null,
                    ),
                    actions = PlayerChromeActions(active = true, restoreFocus = "player-identity-card".takeIf { restoreInfoFocus }),
                    imageLoader = rememberFixtureImageLoader(), currentSession = null,
                    onStop = {},
                    onInteraction = {},
                    onOptions = {},
                    onInfo = { infoOpen = true },
                    onTogglePause = {},
                    onSeek = {},
                    onFocusRestored = { restoreInfoFocus = false },
                    panelOpen = infoOpen,
                )
                if (infoOpen) {
                    LiveProgrammeInfoOverlay(
                        details = { _, _ -> },
                        event = null,
                        channelIdentity = "7 • Das Erste",
                        channelName = "Das Erste",
                        recordingScheduled = false,
                        canRecord = true,
                        recordingState = LiveInfoRecordingState.Idle,
                        confirmationVisible = false,
                        restoreRecordFocus = false,
                        onRecord = {},
                        onRecordingActivate = {},
                        onRecordingDismiss = {},
                        onClose = {
                            restoreInfoFocus = true
                            infoOpen = false
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("player-identity-card").requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("player-identity-card").assertDoesNotExist()
        composeRule.onNodeWithTag("live-info-close").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("player-identity-card").assertIsFocused()
        composeRule.runOnIdle { assertFalse(restoreInfoFocus) }
    }

    private fun setInfoOverlay(
        event: () -> EpgEvent?,
        onAction: (ProgrammeAction) -> Unit = {},
    ) {
        composeRule.setContent {
            val currentEvent = event()
            TVHeadendPlayerTheme {
                Box(Modifier.size(width = 960.dp, height = 540.dp).testTag("live-info-test-viewport")) {
                    LiveProgrammeInfoOverlay(
                        details = { recordFocus, firstFocus ->
                            ProgramDetails(
                                state = remember { ProgramDetailsState() },
                                current = checkNotNull(currentEvent),
                                schedule = listOf(currentEvent),
                                nowSec = 1_500L,
                                channelIdentity = "7 • Das Erste",
                                tile = { _, _ -> },
                                recordingFor = { null },
                                canModifyRecordings = true,
                                recordFocus = recordFocus,
                                firstFocus = firstFocus,
                                onAction = onAction,
                            )
                        },
                        event = currentEvent,
                        channelIdentity = "7 • Das Erste",
                        channelName = "Das Erste",
                        recordingScheduled = false,
                        canRecord = true,
                        recordingState = LiveInfoRecordingState.Idle,
                        confirmationVisible = false,
                        restoreRecordFocus = false,
                        onRecord = {},
                        onRecordingActivate = {},
                        onRecordingDismiss = {},
                        onClose = {},
                    )
                }
            }
        }
    }

    private fun event(id: Int) = EpgEvent.create(
        id = EventId(id.toLong()),
        channelId = ChannelId(7),
        start = Instant.fromEpochSeconds(1_000L),
        stop = Instant.fromEpochSeconds(2_000L),
        title = "Programme $id",
        summary = "Summary",
        description = "Description",
    )
}
