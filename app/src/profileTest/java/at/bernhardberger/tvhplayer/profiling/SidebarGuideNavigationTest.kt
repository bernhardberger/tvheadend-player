package at.bernhardberger.tvhplayer.profiling

import android.view.KeyEvent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.requestFocus
import java.io.File
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelected
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
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvhplayer.core.guideWindowBounds
import at.bernhardberger.tvhplayer.stores.GuidePosition
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SidebarGuideNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyProfileActivity>()
    private val focusHistory = mutableListOf<String>()

    @Test fun channelViewportSurvivesDrawerRoundTripWithAMiddleRowSelected() {
        compose.waitForIdle()
        repeat(2) { key(Key.DirectionDown) }
        val middleRow = hasText("Offline channel 3", substring = true) and isFocused()
        val before = compose.onNode(middleRow).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(channelTitleText(1, "Offline channel 1")).assertIsDisplayed()
        key(Key.DirectionLeft)
        compose.onNodeWithText("Channels").assertIsFocused()
        compose.mainClock.autoAdvance = false
        try {
            key(Key.DirectionRight)
            repeat(30) {
                compose.mainClock.advanceTimeByFrame()
                val top = compose.onNode(
                    hasText("Offline channel 3", substring = true) and hasClickAction(),
                ).fetchSemanticsNode().boundsInRoot.top
                assertEquals("drawer return frame $it preserves the channel viewport", before.top, top, 1f)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNode(middleRow).assertIsFocused()
        val after = compose.onNode(middleRow).fetchSemanticsNode().boundsInRoot
        assertEquals("drawer return preserves the channel viewport", before.top, after.top, 1f)
    }

    @Test fun guideNumbersFollowMetadataAndFilteredScopeWithinTheSameVisit() {
        openGuideSidebar()
        val entries = compose.activity.guideCompositionEntries
        fun publishChannels(transform: (List<Channel>) -> List<Channel>) {
            compose.activityRule.scenario.onActivity { activity ->
                val source = session(activity)
                val old = source.observation.value
                val catalog = checkNotNull(old.channelCatalogForDisplay)
                source.publish(SessionObservation.create(
                    sessionState = old.sessionState,
                    channelState = ChannelRepositoryState.Current(
                        ChannelCatalog.create(transform(catalog.channels), catalog.tags),
                    ),
                    epgState = old.epgState,
                    dvrState = old.dvrState,
                ))
            }
            compose.waitForIdle()
        }
        publishChannels { channels ->
            channels.filter { it.id.value <= 3 }.map { channel ->
                Channel.create(channel.id, name = channel.name,
                    number = 40L - channel.id.value * 10L, tagIds = channel.tagIds)
            }
        }
        compose.onNodeWithText(channelTitleText(30, "Offline channel 1")).assertIsDisplayed()
        publishChannels { channels ->
            channels.map { channel ->
                Channel.create(channel.id, name = when (channel.id.value) {
                    1L -> "Zeta"
                    2L -> "Alpha"
                    else -> "Beta"
                }, tagIds = channel.tagIds)
            }
        }
        // Numberless scopes retain the existing channel-ID ordering, not name order.
        compose.onNodeWithText(channelTitleText(3, "Beta")).assertIsDisplayed()
        compose.onNodeWithText(channelTitleText(1, "Zeta")).assertIsDisplayed()
        key(Key.DirectionRight) // Selected All channels scope.
        compose.onNodeWithText("All channels").assertIsFocused()
        key(Key.DirectionRight) // Group A.
        compose.waitUntilAtLeastOneExists(hasText("Group A") and isSelected(), 3_000)
        compose.onNodeWithText("Group A").assertIsFocused()
        key(Key.DirectionRight) // Group B contains Zeta and Beta, in ID order.
        compose.waitUntilAtLeastOneExists(hasText("Group B") and isSelected(), 3_000)
        compose.onNodeWithText("Group B").assertIsFocused()
        compose.onNodeWithText(channelTitleText(2, "Beta")).assertIsDisplayed()
        compose.onNodeWithText(channelTitleText(1, "Zeta")).assertIsDisplayed()
        publishChannels { channels ->
            channels.map { channel ->
                Channel.create(channel.id, name = channel.name,
                    number = 99L.takeIf { channel.id == ChannelId(1) }, tagIds = channel.tagIds)
            }
        }
        compose.onNodeWithText("Beta").assertIsDisplayed()
        compose.onNodeWithText(channelTitleText(99, "Zeta")).assertIsDisplayed()
        compose.runOnIdle { assertEquals(entries, compose.activity.guideCompositionEntries) }
    }

    @Test fun firstGuideVisitRestoresNonzeroViewportAndProgramme() {
        compose.waitForIdle()
        val nowSec = System.currentTimeMillis() / 1000L
        compose.activityRule.scenario.onActivity { activity ->
            val snapshot = checkNotNull(session(activity).observation.value.epgSnapshotForDisplay)
            val event = snapshot.events.first {
                it.channelId == ChannelId(13) &&
                    it.start.epochSeconds <= nowSec && it.stop.epochSeconds > nowSec
            }
            activity.guidePosition.save(GuidePosition(
                channelId = checkNotNull(event.channelId),
                eventId = event.id,
                eventStartSec = event.start.epochSeconds,
                windowStartSec = guideWindowBounds(nowSec, ZoneId.systemDefault()).earliestStartSec,
                firstVisibleColumn = 12,
            ))
        }
        openGuideSidebar()
        compose.onNodeWithText(channelTitleText(13, "Offline channel 13")).assertIsDisplayed()
        compose.onNodeWithText(channelTitleText(1, "Offline channel 1")).assertDoesNotExist()
        enterProgramme()
        assertTrue(focusedDescription().startsWith("Offline channel 13,"))
        val restored = focusedDescription()
        back()
        key(Key.DirectionUp)
        key(Key.DirectionDown)
        enterProgramme()
        assertEquals(restored, focusedDescription())
    }

    @Test fun visitedChannelsSurvivesAlternationButIsReleasedOnGuideClose() {
        compose.waitForIdle()
        val initialEntries = compose.activity.channelCompositionEntries
        openGuideSidebar()
        repeat(3) {
            key(Key.DirectionUp)
            compose.onNodeWithText("Channels").assertIsFocused()
            key(Key.DirectionDown)
            compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
        }
        compose.runOnIdle { assertEquals(initialEntries, compose.activity.channelCompositionEntries) }
        key(Key.DirectionRight)
        compose.onNode(hasText("All channels") and isFocused()).assertIsFocused()
        back()
        key(Key.DirectionUp)
        compose.onNodeWithText("Channels").assertIsFocused()
        compose.runOnIdle { assertEquals(initialEntries + 1, compose.activity.channelCompositionEntries) }
    }

    @Test fun leavingPairDoesNotReconstructHiddenGuideOnBackToChannels() {
        openGuideSidebar()
        val entries = compose.activity.guideCompositionEntries
        assertTrue(entries > 0)
        key(Key.DirectionDown)
        compose.onNodeWithText("Recordings").assertIsFocused()
        back()
        compose.onNodeWithText("Channels").assertIsFocused()
        compose.runOnIdle { assertEquals(entries, compose.activity.guideCompositionEntries) }
        key(Key.DirectionDown)
        compose.runOnIdle { assertEquals(entries + 1, compose.activity.guideCompositionEntries) }
    }

    @Test fun firstRightEntryReachesGuideScopeWithoutChangingSelection() {
        openGuideSidebar()
        key(Key.DirectionRight)
        compose.onNode(hasText("All channels") and isFocused()).assertIsFocused().assertIsSelected()
    }

    @Test fun guideScopeReversalsRemainCoherentAndDownEntersSelectedScope() =
        assertBrowseScopeEntry(guide = true, Key.DirectionDown)

    @Test fun guideScopeReversalsRemainCoherentAndOkEntersSelectedScope() =
        assertBrowseScopeEntry(guide = true, Key.DirectionCenter)

    @Test fun channelsScopeReversalsRemainCoherentAndDownEntersSelectedScope() =
        assertBrowseScopeEntry(guide = false, Key.DirectionDown)

    @Test fun channelsScopeReversalsRemainCoherentAndOkEntersSelectedScope() =
        assertBrowseScopeEntry(guide = false, Key.DirectionCenter)

    @Test fun channelsTagFocusCommitsSelectionAcrossRepeatedReversals() {
        compose.waitUntilAtLeastOneExists(hasText("Offline channel 1", substring = true), 15_000)
        compose.onNodeWithText("All channels").requestFocus()
        key(Key.DirectionRight)
        repeat(10) {
            compose.waitUntilExactlyOneExists(hasText("Group A") and isFocused() and isSelected(), 5_000)
            key(Key.DirectionRight)
            compose.waitUntilExactlyOneExists(hasText("Group B") and isFocused() and isSelected(), 5_000)
            key(Key.DirectionLeft)
        }
        compose.waitUntilExactlyOneExists(hasText("Group A") and isFocused() and isSelected(), 5_000)
    }

    private fun assertBrowseScopeEntry(guide: Boolean, entryKey: Key) {
        if (guide) {
            openGuideSidebar()
            key(Key.DirectionRight)
        } else {
            compose.waitUntilAtLeastOneExists(hasText("Offline channel 1", substring = true), 15_000)
            compose.onNodeWithText("All channels").requestFocus()
        }
        compose.onNode(hasText("All channels") and isFocused()).assertIsSelected()
        compose.mainClock.autoAdvance = false
        try {
            val journey = listOf(
                Key.DirectionRight to "Group A", Key.DirectionRight to "Group B",
                Key.DirectionLeft to "Group A", Key.DirectionRight to "Group B",
                Key.DirectionLeft to "Group A",
            )
            journey.forEachIndexed { step, (direction, label) ->
                compose.onRoot().performKeyInput { keyDown(direction); keyUp(direction) }
                repeat(4) { frame ->
                    compose.mainClock.advanceTimeByFrame()
                    val tab = compose.onNode(hasText(label) and isFocused())
                    tab.assertIsFocused()
                    val pixels = tab.captureToImage().toPixelMap()
                    val pill = pixels[pixels.width / 2, pixels.height / 5]
                    assertTrue("Focused scope pill must accompany selection at $step/$frame", pill.red > 0.85f && pill.green > 0.85f && pill.blue > 0.85f)
                    if (frame == 1 || frame == 3) {
                        val context = InstrumentationRegistry.getInstrumentation().targetContext
                        val directory = File(context.getExternalFilesDir(null), "p49-guide-motion").apply { mkdirs() }
                        File(directory, "guide-$guide-entry-$entryKey-step-$step-frame-$frame.png").outputStream().use {
                            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    }
                }
                // Focus styling is synchronous; persisted scope selection reaches the
                // composable through DataStore/Flow. Check that commit independently
                // without skipping the intermediate focus frames above.
                compose.mainClock.autoAdvance = true
                compose.waitUntilExactlyOneExists(hasText(label) and isFocused() and isSelected(), 5_000)
                compose.mainClock.autoAdvance = false
            }
            compose.onRoot().performKeyInput { keyDown(entryKey); keyUp(entryKey) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        if (guide) {
            assertTrue("Selected Group A must enter channel 2 programme: ${focusedDescription()}", focusedDescription().startsWith("Offline channel 2,"))
        } else {
            compose.onNode(hasText("Offline channel 2", substring = true) and isFocused()).assertIsFocused()
        }
    }

    @Test fun firstGuideRightDuringDrawerOpeningReachesItsSelectedScope() {
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithText("Channels").assertIsFocused()
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.mainClock.advanceTimeBy(16)
            compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            compose.mainClock.advanceTimeBy(64)
            compose.onNode(hasText("All channels") and isFocused()).assertIsFocused().assertIsSelected()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun rightDuringGuideExitEntersTheSelectedChannel() {
        openGuideSidebar()
        compose.mainClock.autoAdvance = false
        try {
            key(Key.DirectionUp)
            compose.mainClock.advanceTimeByFrame()
            key(Key.DirectionRight)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        compose.onNode(hasText("Offline channel 1", substring = true) and isFocused()).assertIsFocused()
    }

    @Test fun reopeningDrawerCancelsPendingChannelsEntry() {
        openGuideSidebar()
        compose.mainClock.autoAdvance = false
        try {
            key(Key.DirectionUp)
            compose.mainClock.advanceTimeByFrame()
            key(Key.DirectionRight)
            compose.mainClock.advanceTimeByFrame()
            key(Key.DirectionLeft)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        compose.onNodeWithText("Channels").assertIsFocused()
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
        val entries = compose.activity.guideCompositionEntries
        key(Key.DirectionRight) // Closing on Channels releases the retained Guide scene.
        // Guide and Channels deliberately share browse selection by channel identity.
        compose.onNode(hasText(later.substringBefore(','), substring = true) and isFocused()).assertIsFocused()
        back()
        compose.onNodeWithText("Channels").assertIsFocused()
        compose.onNode(hasContentDescription(later)).assertDoesNotExist()
        key(Key.DirectionDown)
        compose.runOnIdle { assertEquals(entries + 1, compose.activity.guideCompositionEntries) }
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
