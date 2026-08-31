package at.bernhardberger.tvhplayer.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import at.bernhardberger.tvhplayer.core.ProductProfile
import at.bernhardberger.tvhplayer.core.SimpleTvRoute
import at.bernhardberger.tvhplayer.core.allowsRoute
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SimpleTvRouteGuardEffectTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun recordingDeepLinkStopsItsOwnerBeforeRedirectingToLive() {
        val events = mutableListOf<String>()
        var profile by mutableStateOf(
            ProductProfile.Appliance(timeshiftAllowed = false)
        )
        val finishStop = CompletableDeferred<Unit>()
        var recordingActive by mutableStateOf(true)

        composeRule.setContent {
            val backStack = rememberAppNavBackStack(RecordingPlayerKey(recordingId = 1))
            SimpleTvRouteGuardEffect(
                destination = backStack.lastOrNull(),
                profile = profile,
                recordingActive = recordingActive,
                stopRecording = {
                    events += "stop-started"
                    recordingActive = false
                    finishStop.await()
                    events += "stop-finished"
                },
                redirectToLive = {
                    events += "redirect-live"
                    backStack.replaceRoot(LivePlayerKey(channelId = 1, channelName = "Live"))
                },
            )
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.popNavigation() },
                entryProvider = entryProvider {
                    entry<RecordingPlayerKey> {
                    if (profile.allowsRoute(SimpleTvRoute.RECORDING_PLAYER)) {
                        Box(Modifier.fillMaxSize().testTag(RECORDING_ROUTE))
                    }
                }
                    entry<LivePlayerKey> {
                    Box(Modifier.fillMaxSize().testTag(LIVE_ROUTE))
                }
                },
            )
        }

        composeRule.waitUntil { events.contains("stop-started") }
        composeRule.runOnIdle {
            profile = ProductProfile.Appliance(timeshiftAllowed = true)
        }
        composeRule.runOnIdle {
            assertEquals(listOf("stop-started"), events)
            finishStop.complete(Unit)
        }
        composeRule.onNodeWithTag(LIVE_ROUTE).assertIsDisplayed()
        composeRule.onNodeWithTag(RECORDING_ROUTE).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(
                listOf("stop-started", "stop-finished", "redirect-live"),
                events,
            )
        }
    }
}

private const val RECORDING_ROUTE = "simple-tv-recording-route"
private const val LIVE_ROUTE = "simple-tv-live-route"
