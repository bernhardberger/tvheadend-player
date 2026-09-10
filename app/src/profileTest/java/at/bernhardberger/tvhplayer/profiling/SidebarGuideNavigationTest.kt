package at.bernhardberger.tvhplayer.profiling

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SidebarGuideNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyProfileActivity>()
    private val focusHistory = mutableListOf<String>()

    @Test fun firstRightEntryReachesGuideScopeWithoutChangingSelection() {
        openGuideSidebar()
        key(Key.DirectionRight)
        compose.onNode(hasText("All channels") and isFocused()).assertIsFocused().assertIsSelected()
    }

    @Test fun sidebarUpdatesHiddenGuideAndRestoresLaterProgrammeWithinTheVisit() {
        openGuideSidebar()
        repeat(3) {
            key(Key.DirectionUp)
            compose.onNodeWithText("Channels").assertIsFocused()
            key(Key.DirectionDown)
            compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
        }
        key(Key.DirectionUp)
        compose.activityRule.scenario.onActivity { activity ->
            val session = session(activity)
            val old = session.observation.value
            val snapshot = checkNotNull(old.epgSnapshotForDisplay)
            val events = snapshot.events.map { event ->
                EpgEvent.create(
                    event.id, channelId = event.channelId, start = event.start, stop = event.stop,
                    title = "Updated ${event.title}", summary = event.summary,
                )
            }
            session.publish(SessionObservation.create(
                sessionState = old.sessionState, channelState = old.channelState,
                epgState = EpgRepositoryState.Current(EpgSnapshot.create(events, snapshot.coverages)),
                dvrState = old.dvrState,
            ))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Channels").assertIsFocused()
        key(Key.DirectionDown)
        compose.waitUntilAtLeastOneExists(hasText("Updated Programme", substring = true), 10_000)
        compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
        enterProgramme()
        assertTrue(focusedDescription().contains("Updated Programme"))
        val initial = focusedDescription()
        repeat(7) { key(Key.DirectionRight) }
        repeat(7) { key(Key.DirectionDown) }
        val later = focusedDescription()
        assertTrue("Must browse away from initial programme", initial != later)
        back()
        compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
        key(Key.DirectionUp)
        key(Key.DirectionDown)
        enterProgramme()
        assertEquals(later, focusedDescription())
        back()
        key(Key.DirectionUp)
        key(Key.DirectionRight) // Closing on Channels releases the retained Guide scene.
        back()
        compose.onNodeWithText("Channels").assertIsFocused()
        compose.onNode(hasContentDescription(later)).assertDoesNotExist()
    }

    private fun openGuideSidebar() {
        compose.waitUntilAtLeastOneExists(hasText("Offline channel 1", substring = true), 15_000)
        key(Key.DirectionLeft)
        compose.onNodeWithText("Channels").assertIsFocused()
        key(Key.DirectionDown)
        compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().config
        focusHistory += "$key: ${focus.getOrNull(SemanticsProperties.Text)} ${focusedDescription()}"
    }

    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun enterProgramme() {
        key(Key.DirectionRight)
        // Lateral entry can land on the date header; Down traverses the scope tabs to
        // the remembered programme. Already-restored programme focus needs no extra key.
        repeat(2) {
            if (!focusedDescription().contains("Programme")) key(Key.DirectionDown)
        }
        assertTrue("Expected programme focus: $focusHistory",
            focusedDescription().contains("Programme"))
    }

    private fun focusedDescription(): String = compose.onNode(isFocused()).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    private fun session(activity: JourneyProfileActivity): FakeTvheadendSession {
        val owner = activity.javaClass.getDeclaredField("runtimeOwner")
            .apply { isAccessible = true }.get(activity)!!
        return owner.javaClass.getDeclaredField("session")
            .apply { isAccessible = true }.get(owner) as FakeTvheadendSession
    }
}
