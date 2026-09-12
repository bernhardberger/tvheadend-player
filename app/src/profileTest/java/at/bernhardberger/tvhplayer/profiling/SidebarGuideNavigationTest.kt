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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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

    @Test fun channelPageFocusSurvivesMetadataPublishedDuringTheScroll() {
        enterInitialChannelScope()
        key(Key.DirectionDown)
        compose.onNode(hasText("Offline channel 1", substring = true) and isFocused()).assertExists()
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN)) }
            repeat(2) { compose.mainClock.advanceTimeByFrame() }
            compose.activityRule.scenario.onActivity { activity ->
                val source = session(activity)
                val old = source.observation.value
                val catalog = checkNotNull(old.channelCatalogForDisplay)
                source.publish(SessionObservation.create(
                    sessionState = old.sessionState,
                    channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                        catalog.channels.map { Channel.create(it.id, name = "${it.name} updated", number = it.number, tagIds = it.tagIds) },
                        catalog.tags,
                    )),
                    epgState = old.epgState,
                    dvrState = old.dvrState,
                ))
            }
            repeat(100) { compose.mainClock.advanceTimeByFrame() }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        val focus = compose.onNode(isFocused()).assertIsDisplayed().fetchSemanticsNode()
        val id = focus.config.getOrNull(SemanticsProperties.TestTag).orEmpty().removePrefix("channel-row-").toLongOrNull()
        assertTrue("Paging must transfer visible native focus: $focus", id != null && id > 1)
    }

    @Test fun channelViewportSurvivesDrawerRoundTripWithAMiddleRowSelected() {
        enterInitialChannelScope()
        key(Key.DirectionDown)
        repeat(2) { key(Key.DirectionDown) }
        val middleRow = hasText("Offline channel 3", substring = true) and isFocused()
        val before = compose.onNode(middleRow).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(channelTitleText(1, "Offline channel 1")).assertIsDisplayed()
        key(Key.DirectionLeft)
        compose.onNodeWithText("Channels").assertIsFocused()
        compose.mainClock.autoAdvance = false
        try {
            key(Key.DirectionRight)
            key(Key.DirectionDown)
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
        compose.onNode(hasText("Recordings") and hasClickAction()).assertIsFocused()
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

    @Test fun channelsEntryStaysOnScopeAndBackUnwindsOneLayer() {
        compose.waitForIdle()
        key(Key.DirectionLeft)
        compose.onNodeWithText("Channels").assertIsFocused()
        key(Key.DirectionRight)
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(hasText("All channels") and isSelected()).assertIsFocused()
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        compose.onNode(hasText("Offline channel 2", substring = true) and isFocused()).assertIsFocused()
        back()
        compose.onNode(hasText("Group A") and isSelected()).assertIsFocused()
        back()
        compose.onNodeWithText("Channels").assertIsFocused()
    }

    @Test fun guideBackReturnsToSelectedScopeBeforeDrawer() {
        openGuideSidebar()
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        assertTrue(focusedDescription().startsWith("Offline channel 2,"))
        back()
        compose.onNode(hasText("Group A") and isSelected()).assertIsFocused()
        back()
        compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
    }

    @Test fun visibleProgrammeReceivesNativeFocusWithinTheKeyDispatch() {
        openGuideSidebar()
        enterProgramme()
        // Advance away from a clipped window edge before the synchronous-input check.
        key(Key.DirectionRight)
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle {
                val before = checkNotNull(compose.activity.guidePosition.position.value).eventId
                compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                val after = checkNotNull(compose.activity.guidePosition.position.value).eventId
                assertTrue("Native acknowledgement must not wait for recomposition", before != after)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
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

    @Test fun channelsImmediateTagEntryUsesLatestIntent() = immediateTagEntry(guide = false)

    @Test fun guideImmediateTagEntryUsesLatestIntent() = immediateTagEntry(guide = true)

    private fun immediateTagEntry(guide: Boolean) {
        if (guide) {
            openGuideSidebar()
            key(Key.DirectionRight)
        } else {
            compose.waitForIdle()
            compose.onNodeWithText("All channels").requestFocus()
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput {
                listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionLeft, Key.DirectionDown)
                    .forEach { keyDown(it); keyUp(it) }
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        compose.onNode(hasText("Group A") and isSelected()).assertExists()
        if (guide) {
            assertTrue("Immediate entry must reach Group A programme: ${focusedDescription()}",
                focusedDescription().startsWith("Offline channel 2,"))
        } else {
            compose.onNode(hasText("Offline channel 2", substring = true) and isFocused()).assertIsFocused()
        }
    }

    @Test fun rapidHorizontalNavigationKeepsFocusInsideGuide() {
        openGuideSidebar()
        enterProgramme()
        val initial = focusedDescription()
        compose.mainClock.autoAdvance = false
        try {
            // One rendered frame between keys, including multiple three-hour window edges.
            for (direction in listOf(Key.DirectionRight, Key.DirectionLeft, Key.DirectionRight)) {
                repeat(16) { step ->
                    compose.onRoot().performKeyInput { keyDown(direction); keyUp(direction) }
                    compose.mainClock.advanceTimeByFrame()
                    compose.onNode(isFocused() and (hasTestTag("epg-programme-viewport") or
                        hasAnyAncestor(hasTestTag("epg-programme-viewport")))).assertExists()
                }
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertTrue("The burst must actually navigate", initial != focusedDescription())
        assertTrue(focusedDescription().startsWith("Offline channel"))
    }

    @Test fun knownEmptyNextWindowKeepsOriginProgrammeAndBackWorks() {
        restoreLastProgrammeInWindow(emptyNextWindow = true)
        openGuideSidebar()
        enterProgramme()
        val origin = focusedDescription()
        repeat(3) { key(Key.DirectionRight) }
        assertEquals(origin, focusedDescription())
        back()
        compose.onNode(hasText("All channels") and isSelected()).assertIsFocused()
        back()
        compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
    }

    @Test fun centerHeldAcrossWindowHandoffDoesNotActivateNewProgramme() {
        restoreLastProgrammeInWindow(emptyNextWindow = false)
        openGuideSidebar()
        enterProgramme()
        val origin = focusedDescription()
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput {
                keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
                keyDown(Key.DirectionCenter)
            }
            compose.mainClock.autoAdvance = true
            compose.waitUntil(5_000) {
                val focused = focusedDescription()
                focused.startsWith("Offline channel 1,") && focused != origin
            }
            val replacement = focusedDescription()
            compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
            compose.waitForIdle()
            assertEquals(replacement, focusedDescription())
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("epg-programme-viewport"))).assertExists()
    }

    @Test fun interruptedCenterDoesNotSuppressTheNextProgrammeActivation() = interruptedActivation(false)

    @Test fun drawerInterruptedCenterDoesNotSuppressTheNextProgrammeActivation() = interruptedActivation(true)

    private fun interruptedActivation(viaDrawer: Boolean) {
        restoreLastProgrammeInWindow(emptyNextWindow = false)
        openGuideSidebar()
        enterProgramme()
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput {
                keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
                keyDown(Key.DirectionCenter)
            }
            if (viaDrawer) {
                compose.mainClock.autoAdvance = true
                back()
                back()
                compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
            } else {
                compose.onNodeWithText("All channels").requestFocus()
            }
            compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        if (viaDrawer) enterProgramme() else key(Key.DirectionDown)
        val programme = focusedDescription()
        assertTrue(programme.startsWith("Offline channel"))
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(hasTestTag("programme-details-body")).assertIsFocused()
        back()
        assertEquals(programme, focusedDescription())
    }

    private fun restoreLastProgrammeInWindow(emptyNextWindow: Boolean) {
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { activity ->
            val source = session(activity)
            val old = source.observation.value
            val snapshot = checkNotNull(old.epgSnapshotForDisplay)
            val start = guideWindowBounds(System.currentTimeMillis() / 1000L, ZoneId.systemDefault()).earliestStartSec
            val end = start + 3 * 3600L
            val event = snapshot.events.filter { it.channelId == ChannelId(1) && it.stop.epochSeconds <= end }
                .maxBy { it.start }
            activity.guidePosition.save(GuidePosition(ChannelId(1), event.id, event.start.epochSeconds, start, 0))
            if (emptyNextWindow) source.publish(SessionObservation.create(
                sessionState = old.sessionState, channelState = old.channelState,
                epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                    snapshot.events.filter { it.start.epochSeconds < end }, snapshot.coverages)),
                dvrState = old.dvrState,
            ))
        }
    }

    private fun assertBrowseScopeEntry(guide: Boolean, entryKey: Key) {
        if (guide) {
            openGuideSidebar()
            key(Key.DirectionRight)
        } else {
            enterInitialChannelScope()
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
                    if (frame == 1 || frame == 3) {
                        val context = InstrumentationRegistry.getInstrumentation().targetContext
                        val directory = File(context.getExternalFilesDir(null), "p49-guide-motion").apply { mkdirs() }
                        File(directory, "guide-$guide-entry-$entryKey-step-$step-frame-$frame.png").outputStream().use {
                            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    }
                }
                // Native focus is immediate while the pill travels; scope reaches the
                // composable through StateFlow. Check that commit independently
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
        enterInitialChannelScope()
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

    @Test fun rightDuringGuideExitEntersTheSelectedScope() {
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
        compose.onNode(hasText("All channels") and isSelected()).assertIsFocused()
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
        back()
        compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
        key(Key.DirectionUp)
        key(Key.DirectionDown)
        enterProgramme()
        assertEquals(later, focusedDescription())
        back()
        back()
        key(Key.DirectionUp)
        val entries = compose.activity.guideCompositionEntries
        key(Key.DirectionRight) // Closing on Channels releases the retained Guide scene.
        compose.onNodeWithText("All channels").assertIsFocused()
        key(Key.DirectionDown)
        // Guide and Channels deliberately share browse selection by channel identity.
        compose.onNode(hasText(later.substringBefore(','), substring = true) and isFocused()).assertIsFocused()
        back()
        back()
        compose.onNodeWithText("Channels").assertIsFocused()
        compose.onNode(hasContentDescription(later)).assertDoesNotExist()
        key(Key.DirectionDown)
        compose.runOnIdle { assertEquals(entries + 1, compose.activity.guideCompositionEntries) }
    }

    private fun openGuideSidebar() {
        enterInitialChannelScope()
        val initialFocus = compose.onNode(isFocused()).fetchSemanticsNode().config
        key(Key.DirectionLeft)
        assertTrue("Left must reach Channels drawer. Initial=$initialFocus; history=$focusHistory",
            compose.onAllNodes(hasText("Channels") and isFocused()).fetchSemanticsNodes().size == 1)
        key(Key.DirectionDown)
        compose.onNode(hasText("Guide") and hasClickAction()).assertIsFocused()
    }

    private fun enterInitialChannelScope() {
        compose.waitUntilAtLeastOneExists(hasText("All channels"), 15_000)
        compose.waitForIdle()
        // If the drawer took focus while settings loaded, content must not steal it.
        // Enter through the same Right key as a viewer, rather than forcing native focus.
        val drawer = compose.onAllNodes(hasText("Channels") and isFocused()).fetchSemanticsNodes()
        if (drawer.isNotEmpty()) key(Key.DirectionRight)
        compose.onNodeWithText("All channels").assertIsFocused()
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
