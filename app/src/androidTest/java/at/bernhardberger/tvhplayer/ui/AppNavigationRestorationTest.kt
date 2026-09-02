package at.bernhardberger.tvhplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.ui.player.RecordingPlaybackRouteRestorationEffect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppNavigationRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun processRecreationRestoresTopLevelNestedSettingsAndTransientKeys() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var backStack: MutableList<AppNavKey>
        restorationTester.setContent {
            backStack = rememberAppNavBackStack(ChannelsKey)
        }
        composeRule.runOnIdle {
            backStack.navigateTopLevel(GuideKey)
            backStack.navigateTopLevel(SettingsKey(SettingsSection.PLAYER))
            backStack.pushTransient(LivePlayerKey(channelId = 42, channelName = "News / HD"))
        }
        val expected = backStack.toList()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle { assertEquals(expected, backStack.toList()) }
    }

    @Test
    fun processRecreationRestoresRecordingRouteIntoFreshRuntimeExactlyOnce() {
        val restorationTester = StateRestorationTester(composeRule)
        val starts = mutableListOf<Pair<DvrEntryId, RecordingPlaybackStart>>()
        var firstRuntime = true
        lateinit var backStack: MutableList<AppNavKey>
        lateinit var recompose: () -> Unit
        restorationTester.setContent {
            var recompositionToken by remember { mutableIntStateOf(0) }
            recompose = { recompositionToken += 1 }
            @Suppress("UNUSED_EXPRESSION")
            recompositionToken

            backStack = rememberAppNavBackStack(ChannelsKey)
            val runtime = remember {
                RecordingRouteRuntime(
                    selectedRecordingId = if (firstRuntime) DvrEntryId(42) else null,
                    starts = starts,
                ).also { firstRuntime = false }
            }
            (backStack.lastOrNull() as? RecordingPlayerKey)?.let { destination ->
                val recordingId = DvrEntryId(destination.recordingId)
                val playbackStart = when (destination.start) {
                    RecordingStartMode.RESUME -> RecordingPlaybackStart.RESUME
                    RecordingStartMode.START_OVER -> RecordingPlaybackStart.START_OVER
                }
                RecordingPlaybackRouteRestorationEffect(
                    recordingId = recordingId,
                    playbackStart = playbackStart,
                    restorePlayback = { runtime.restore(recordingId, playbackStart) },
                )
            }
        }
        composeRule.runOnIdle {
            backStack.pushTransient(
                RecordingPlayerKey(
                    recordingId = 42,
                    start = RecordingStartMode.START_OVER,
                )
            )
        }
        composeRule.waitForIdle()
        assertEquals(emptyList<Pair<DvrEntryId, RecordingPlaybackStart>>(), starts)

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(
            listOf(DvrEntryId(42) to RecordingPlaybackStart.START_OVER),
            starts,
        )
        composeRule.runOnIdle { recompose() }
        composeRule.waitForIdle()
        assertEquals(1, starts.size)
    }

    private class RecordingRouteRuntime(
        private var selectedRecordingId: DvrEntryId?,
        private val starts: MutableList<Pair<DvrEntryId, RecordingPlaybackStart>>,
    ) {
        suspend fun restore(
            recordingId: DvrEntryId,
            playbackStart: RecordingPlaybackStart,
        ) {
            if (selectedRecordingId == recordingId) return
            selectedRecordingId = recordingId
            starts += recordingId to playbackStart
        }
    }
}
