package at.bernhardberger.tvhplayer.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.*
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import org.koin.dsl.module
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeActionsTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val now = 1_800_000_000L
    private val channel = Channel.create(ChannelId(1), name = "Test channel")
    private lateinit var back: androidx.activity.OnBackPressedDispatcher

    @Test fun recentChannelsEndCardOpensChannelsWithoutTuning() {
        var opened = false
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> fail("end card tuned") }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> },
                onBrowseChannels = { opened = true })
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("home-all-channels").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun recentChannelsEndCardCaptionMatchesTheTileNames() {
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
        fun caption(text: String): Triple<androidx.compose.ui.text.TextLayoutInput, Float, Float> {
            val node = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("home-section-RECENT_CHANNELS")),
                useUnmergedTree = true)
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
                .action?.invoke(results)
            val bounds = node.getUnclippedBoundsInRoot()
            return Triple(results.single().layoutInput, (bounds.bottom - bounds.top).value, bounds.top.value)
        }
        val tile = caption("Test channel")
        val end = caption("All channels")
        assertEquals(tile.first.style.fontSize, end.first.style.fontSize)
        assertEquals(tile.first.style.fontWeight, end.first.style.fontWeight)
        assertEquals(tile.first.style.lineHeight, end.first.style.lineHeight)
        assertEquals(tile.first.style.letterSpacing, end.first.style.letterSpacing)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Center, end.first.style.textAlign)
        assertEquals(tile.first.style.textAlign, end.first.style.textAlign)
        assertEquals(2, end.first.maxLines)
        assertEquals(tile.second, end.second, 0.5f)
        assertEquals("captions start at the same line under equal pictures", tile.third, end.third, 0.5f)
    }

    @Test fun rowReentryRestoresItemIdentityAfterAnEarlierItemLeaves() = recentRowRefresh(false)

    @Test fun rowReentryUsesClampedPreviousIndexWhenItsItemLeaves() = recentRowRefresh(true)

    @Test fun lateRunningRecordingPreservesLandedFocusBeforeAnyHomeKey() = lateRunningRecording(false)

    @Test fun lateRunningRecordingDoesNotReplaceTheViewersChosenCard() = lateRunningRecording(true)

    @Test fun earlierOnNowItemPreservesLandedFocusBeforeAnyHomeKey() = earlierOnNowItem(false)

    @Test fun earlierOnNowItemDoesNotReplaceTheViewersChosenCard() = earlierOnNowItem(true)

    @Test fun drawerEntryKeyUpEndsInitialFocusFollowing() = lateRecordingAfterOwnershipBoundary(true)

    @Test fun ownershipLossEndsInitialFocusFollowingWithoutAHomeKey() = lateRecordingAfterOwnershipBoundary(false)

    private fun lateRecordingAfterOwnershipBoundary(drawerFirst: Boolean) {
        var enabled by mutableStateOf(!drawerFirst)
        var input by mutableStateOf(HomeInput(listOf(channel), emptyMap(), emptyList(),
            listOf(channel.id), emptyList(), { null }, null, now))
        val drawerFocus = androidx.compose.ui.focus.FocusRequester()
        compose.setContent { TVHeadendPlayerTheme {
            androidx.compose.foundation.layout.Box {
                HomeScreenContent(input, SessionObservation.create(), PaddingValues(), enabled,
                    onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                    onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
                if (drawerFirst) androidx.tv.material3.Button(onClick = {}, modifier = androidx.compose.ui.Modifier
                    .focusRequester(drawerFocus).testTag("test-drawer")
                    .onPreviewKeyEvent {
                        if (it.key == Key.DirectionRight && it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                            enabled = true
                            true
                        } else false
                    }) { androidx.tv.material3.Text("Drawer") }
            }
        } }
        if (drawerFirst) {
            compose.runOnIdle { drawerFocus.requestFocus() }
            compose.onNodeWithTag("test-drawer").assertIsFocused()
                .performKeyInput { keyDown(Key.DirectionRight) }
            compose.onNodeWithTag("home-recent-0").assertIsFocused()
                .performKeyInput { keyUp(Key.DirectionRight) }
        } else {
            compose.onNodeWithTag("home-recent-0").assertIsFocused()
            compose.runOnIdle { enabled = false }
            compose.runOnIdle { enabled = true }
            compose.onNodeWithTag("home-recent-0").assertIsFocused()
        }
        compose.runOnIdle {
            input = input.copy(dvrEntries = listOf(DvrEntry.create(DvrEntryId(7), channelId = channel.id,
                title = "Running recording", state = DvrEntryState.RECORDING,
                start = Instant.fromEpochSeconds(now - 300), stop = Instant.fromEpochSeconds(now + 900))))
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
    }

    private fun earlierOnNowItem(viewerMoved: Boolean) {
        val channels = (1L..14L).map { Channel.create(ChannelId(it), name = "Channel $it", number = it) }
        val events = channels.associate { it.id to EpgEvent.create(EventId(it.id.value), it.id,
            Instant.fromEpochSeconds(now - 600), Instant.fromEpochSeconds(now + 600),
            title = "Programme ${it.id.value}") }
        var input by mutableStateOf(HomeInput(channels, events.filterKeys { it.value >= 12 }, emptyList(),
            emptyList(), emptyList(), { null }, null, now))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
        if (viewerMoved) {
            compose.onNodeWithTag("home-onnow-0").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        }
        compose.runOnIdle { input = input.copy(currentEvents = events) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag(if (viewerMoved) "home-onnow-12" else "home-onnow-11").assertIsFocused()
    }

    private fun lateRunningRecording(viewerMoved: Boolean) {
        val second = Channel.create(ChannelId(2), name = "Second")
        var input by mutableStateOf(HomeInput(listOf(channel, second), emptyMap(), emptyList(),
            listOf(channel.id, second.id), emptyList(), { null }, null, now))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
        leaveTouchMode()
        if (viewerMoved) {
            compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("home-recent-1").assertIsFocused()
        }
        val tag = if (viewerMoved) "home-recent-1" else "home-recent-0"
        val top = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value
        compose.runOnIdle {
            input = input.copy(dvrEntries = listOf(DvrEntry.create(DvrEntryId(7), channelId = channel.id,
                title = "Running recording", state = DvrEntryState.RECORDING,
                start = Instant.fromEpochSeconds(now - 300), stop = Instant.fromEpochSeconds(now + 900))))
        }
        compose.mainClock.autoAdvance = false
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag(tag).assertIsFocused()
            assertEquals("Inserted activity must not move the focused card", top,
                compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value, 1f)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun restoredMotionStartsAtItsDestinationInComposition() {
        var first = true
        var destination: HomeSectionKind? = null
        compose.setContent {
            val motion = rememberHomePageMotion(HomeSectionKind.UPCOMING) { 300f }
            if (first) { destination = motion.to; first = false }
        }
        compose.runOnIdle { assertEquals(HomeSectionKind.UPCOMING, destination) }
    }

    private fun leaveTouchMode() {
        compose.runOnIdle {
            org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(
                compose.activity.window.decorView.parent, "ensureTouchModeLocally",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, false))
        }
    }

    @Test fun firstSectionsReplaceFocusedEmptyActionWithoutOpeningDrawer() = emptyToSections(null)

    @Test fun firstSectionsDoNotTakeFocusFromTheHeader() = emptyToSections("home-view-button")

    @Test fun firstSectionsDoNotTakeFocusFromTheRail() = emptyToSections("test-rail")

    private fun emptyToSections(viewerTarget: String?) {
        leaveTouchMode()
        var sections by mutableStateOf(emptyList<HomeSection>())
        val empty = androidx.compose.ui.focus.FocusRequester()
        val rail = androidx.compose.ui.focus.FocusRequester()
        val header = androidx.compose.ui.focus.FocusRequester()
        lateinit var drawer: androidx.tv.material3.DrawerState
        compose.setContent { TVHeadendPlayerTheme {
            drawer = androidx.tv.material3.rememberDrawerState(androidx.tv.material3.DrawerValue.Closed)
            CompositionLocalProvider(LocalBrowseDrawerState provides drawer, LocalBrowseNavigationFocus provides rail) {
                androidx.compose.foundation.layout.Box {
                    HomeContent(sections, PaddingValues(), homeInitialFocus(sections), {}, emptyFocus = empty,
                        viewRequester = header,
                        emptyContent = {
                            androidx.tv.material3.Button({}, modifier = androidx.compose.ui.Modifier
                                .focusRequester(empty).testTag("empty-action")) { androidx.tv.material3.Text("Browse channels") }
                        })
                    androidx.tv.material3.Button({}, modifier = androidx.compose.ui.Modifier.focusRequester(rail)
                        .onFocusChanged { if (it.isFocused) drawer.setValue(androidx.tv.material3.DrawerValue.Open) }
                        .testTag("test-rail")) { androidx.tv.material3.Text("Home") }
                }
            }
        } }
        compose.onNodeWithTag("empty-action").assertIsFocused()
        if (viewerTarget != null) compose.runOnIdle {
            (if (viewerTarget == "test-rail") rail else header).requestFocus()
        }
        if (viewerTarget != null) compose.onNodeWithTag(viewerTarget).assertIsFocused()
        compose.runOnIdle { sections = listOf(HomeSection.OnNow(listOf(OnNowItem(channel,
            EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(now - 60),
                Instant.fromEpochSeconds(now + 600), title = "Programme"))))) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag(viewerTarget ?: "home-onnow-0").assertIsFocused()
        compose.runOnIdle { assertEquals(if (viewerTarget == "test-rail") androidx.tv.material3.DrawerValue.Open
            else androidx.tv.material3.DrawerValue.Closed, drawer.currentValue) }
    }

    private fun onNowSections() = listOf(HomeSection.OnNow(listOf(OnNowItem(channel,
        EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(now - 60),
            Instant.fromEpochSeconds(now + 600), title = "Programme")))))

    @Test fun aHandOffThatNeverLandsCannotBePressedAndMovesFocusToTheHeader() {
        leaveTouchMode()
        var sections by mutableStateOf(emptyList<HomeSection>())
        var pressed = 0
        val empty = androidx.compose.ui.focus.FocusRequester()
        compose.setContent { TVHeadendPlayerTheme {
            // A section that is not on the page can never take the entry focus.
            HomeContent(sections, PaddingValues(),
                HomeFocusKey(HomeSectionKind.UPCOMING, "missing", 0).takeIf { sections.isNotEmpty() }, {}, emptyFocus = empty,
                emptyContent = {
                    androidx.tv.material3.Button({ pressed++ }, modifier = androidx.compose.ui.Modifier
                        .focusRequester(empty).testTag("empty-action")) { androidx.tv.material3.Text("Browse channels") }
                })
        } }
        compose.onNodeWithTag("empty-action").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("The visible action must work", 1, pressed) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { sections = onNowSections() }
        repeat(5) { compose.waitForIdle(); compose.mainClock.advanceTimeByFrame() }
        // Hidden: gone from the semantics tree, still holding focus.
        compose.onNodeWithTag("empty-action").assertDoesNotExist()
        compose.onAllNodes(isFocused()).assertCountEquals(0)
        compose.onRoot().performKeyInput { keyDown(Key.Enter) }
        repeat(3) { compose.waitForIdle(); compose.mainClock.advanceTimeByFrame() }
        compose.onRoot().performKeyInput { keyUp(Key.Enter) }
        repeat(3) { compose.waitForIdle(); compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle { assertEquals("The hidden action must not", 1, pressed) }
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("home-view-button").assertIsFocused()
        compose.onNodeWithTag("empty-action").assertDoesNotExist()
    }

    @Test fun firstSectionsReplacingAFocusedErrorDoNotOpenTheDrawer() {
        leaveTouchMode()
        var input by mutableStateOf(HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), emptyList(), { null }, null, now))
        val rail = androidx.compose.ui.focus.FocusRequester()
        lateinit var drawer: androidx.tv.material3.DrawerState
        compose.setContent { TVHeadendPlayerTheme {
            drawer = androidx.tv.material3.rememberDrawerState(androidx.tv.material3.DrawerValue.Closed)
            CompositionLocalProvider(LocalBrowseDrawerState provides drawer, LocalBrowseNavigationFocus provides rail) {
                androidx.compose.foundation.layout.Box {
                    HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                        onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                        onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> }, catalogCurrent = true)
                    androidx.tv.material3.Button({}, modifier = androidx.compose.ui.Modifier.focusRequester(rail)
                        .onFocusChanged { if (it.isFocused) drawer.setValue(androidx.tv.material3.DrawerValue.Open) }
                        .testTag("test-rail")) { androidx.tv.material3.Text("Home") }
                }
            }
        } }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("test-rail").assertIsNotFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
        compose.runOnIdle { input = input.copy(channels = listOf(channel), recentChannelIds = listOf(channel.id)) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
        compose.runOnIdle { assertEquals(androidx.tv.material3.DrawerValue.Closed, drawer.currentValue) }
    }

    @Test fun aRecordingReplacingTheRowBelowKeepsTheFocusedRowInPlace() {
        val second = Channel.create(ChannelId(2), name = "Second")
        val scheduled = DvrEntry.create(DvrEntryId(7), channelId = channel.id, title = "Soon",
            state = DvrEntryState.SCHEDULED, start = Instant.fromEpochSeconds(now + 600), stop = Instant.fromEpochSeconds(now + 1800))
        var input by mutableStateOf(HomeInput(listOf(channel, second), emptyMap(), emptyList(),
            listOf(channel.id, second.id), listOf(scheduled), { null }, null, now))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("home-upcoming-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
        val top = compose.onNodeWithTag("home-recent-0").getUnclippedBoundsInRoot().top.value
        compose.runOnIdle {
            input = input.copy(dvrEntries = listOf(DvrEntry.create(DvrEntryId(7), channelId = channel.id, title = "Soon",
                state = DvrEntryState.RECORDING, start = Instant.fromEpochSeconds(now - 300), stop = Instant.fromEpochSeconds(now + 900))))
        }
        compose.mainClock.autoAdvance = false
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("home-recent-0").assertIsFocused()
            assertEquals("The row must hold its place", top,
                compose.onNodeWithTag("home-recent-0").getUnclippedBoundsInRoot().top.value, 1f)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("home-upcoming-0").assertDoesNotExist()
        compose.onNodeWithTag("home-activity-0").assertExists()
    }

    @Test fun aRowInsertedAfterTheCaptionCollapsedKeepsTheFocusedRowInPlace() {
        val second = Channel.create(ChannelId(2), name = "Second")
        val scheduled = DvrEntry.create(DvrEntryId(7), channelId = channel.id, title = "Soon",
            state = DvrEntryState.SCHEDULED, start = Instant.fromEpochSeconds(now + 600), stop = Instant.fromEpochSeconds(now + 1800))
        var input by mutableStateOf(HomeInput(listOf(channel, second), emptyMap(), emptyList(),
            listOf(channel.id, second.id), listOf(scheduled), { null }, null, now))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("home-upcoming-0").assertIsFocused()
        val top = compose.onNodeWithTag("home-upcoming-0").getUnclippedBoundsInRoot().top.value
        compose.runOnIdle {
            input = input.copy(dvrEntries = listOf(scheduled, DvrEntry.create(DvrEntryId(8), channelId = second.id, title = "Running",
                state = DvrEntryState.RECORDING, start = Instant.fromEpochSeconds(now - 300), stop = Instant.fromEpochSeconds(now + 900))))
        }
        compose.mainClock.autoAdvance = false
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("home-upcoming-0").assertIsFocused()
            assertEquals("The row must hold its place", top,
                compose.onNodeWithTag("home-upcoming-0").getUnclippedBoundsInRoot().top.value, 1f)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("home-activity-0").assertExists()
    }

    @Test fun dataArrivingBeforeEntryLandsStillReachesThePriorityCard() {
        var input by mutableStateOf(HomeInput(listOf(channel), emptyMap(), emptyList(),
            listOf(channel.id), emptyList(), { null }, null, now))
        compose.mainClock.autoAdvance = false
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.runOnIdle {
            input = input.copy(dvrEntries = listOf(DvrEntry.create(DvrEntryId(7), channelId = channel.id,
                title = "Running recording", state = DvrEntryState.RECORDING,
                start = Instant.fromEpochSeconds(now - 300), stop = Instant.fromEpochSeconds(now + 900))))
        }
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("home-activity-0").assertIsFocused()
    }

    private fun recentRowRefresh(removeFocused: Boolean) {
        val channels = (1L..3L).map { Channel.create(ChannelId(it), name = "Channel $it") }
        val recents = mutableStateOf(channels.map { it.id })
        val scheduled = DvrEntry.create(DvrEntryId(1), title = "Upcoming", state = DvrEntryState.SCHEDULED,
            start = Instant.fromEpochSeconds(now + 600), stop = Instant.fromEpochSeconds(now + 1800))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(HomeInput(channels, emptyMap(), emptyList(), recents.value, listOf(scheduled), { null }, null, now),
                SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("home-recent-1").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("home-upcoming-0").assertIsFocused()
        compose.runOnIdle { recents.value = recents.value.filter { it != ChannelId(if (removeFocused) 2 else 1) } }
        compose.onNodeWithTag("home-upcoming-0").performKeyInput { pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag(if (removeFocused) "home-recent-1" else "home-recent-0").assertIsFocused()
    }

    @Test fun finishedRecordingPlaybackHasNeutralBadgeAndViewerProgress() {
        showRecordingPlayback(DvrEntryState.COMPLETED)
        compose.onNodeWithTag("channel-recording-indicator", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Recording", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("30m left", useUnmergedTree = true).assertExists()
        compose.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
            androidx.compose.ui.semantics.ProgressBarRangeInfo(0.25f, 0f..1f)), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun recordingPlaybackWhileStillRecordingKeepsRecordingIndicator() {
        showRecordingPlayback(DvrEntryState.RECORDING)
        compose.onNodeWithTag("channel-recording-indicator", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithTag("recording-badge", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("channel-playing-indicator", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Recording", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("30m left", useUnmergedTree = true).assertExists()
    }

    private fun showRecordingPlayback(state: DvrEntryState) {
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = "Recorded programme",
            state = state, start = Instant.fromEpochSeconds(now - 2400), stop = Instant.fromEpochSeconds(now))
        val position = mutableStateOf<RecordingViewerPosition?>(RecordingViewerPosition(entry.id, 600_000, 2_400_000))
        compose.setContent { TVHeadendPlayerTheme {
            CompositionLocalProvider(LocalWatchingTarget provides AppPlaybackTarget.Recording(entry.id),
                LocalWatchingIndicator provides ChannelPlaybackIndicator.PLAYING, LocalRecordingPosition provides position) {
                HomeActivityCard(channel, null, entry, true, now, androidx.compose.ui.unit.Dp(400f), "Playing recording", {},
                    playingRecording = true)
            }
        } }
    }

    @Test fun recordingOnlyPrePaddingUsesEntryTitleAndStartInsteadOfLiveProgramme() {
        val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(now - 600),
            Instant.fromEpochSeconds(now + 600), title = "Live programme")
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = "Recorded programme",
            state = DvrEntryState.RECORDING, start = Instant.fromEpochSeconds(now + 300),
            stop = Instant.fromEpochSeconds(now + 3900))
        compose.setContent { TVHeadendPlayerTheme {
            at.bernhardberger.tvhplayer.ui.components.HomeActivityCard(channel, event, entry, false, now,
                androidx.compose.ui.unit.Dp(400f), "Recording", {})
        } }
        compose.onNodeWithText("Recorded programme", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Live programme", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Starts at ${at.bernhardberger.tvhplayer.ui.common.formatHm(now + 300)}",
            useUnmergedTree = true).assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Watch", useUnmergedTree = true).assertExists()
    }

    @Test fun playingAndRecordingKeepsTheLiveProgramme() {
        val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(now - 600),
            Instant.fromEpochSeconds(now + 600), title = "Live programme")
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = "Recorded programme",
            state = DvrEntryState.RECORDING, start = Instant.fromEpochSeconds(now + 300),
            stop = Instant.fromEpochSeconds(now + 3900))
        compose.setContent { TVHeadendPlayerTheme {
            at.bernhardberger.tvhplayer.ui.components.HomeActivityCard(channel, event, entry, true, now,
                androidx.compose.ui.unit.Dp(400f), "Playing", {})
        } }
        compose.onNodeWithText("Live programme", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Recorded programme", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Back to player", useUnmergedTree = true).assertExists()
        compose.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
            androidx.compose.ui.semantics.ProgressBarRangeInfo(0.5f, 0f..1f)), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun groupButtonSelectsSharedScopeAndViewOptionsContainsOnlySections() {
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        var selected: ChannelTagId? = null
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> fail("scope selection tuned") }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> },
                groups = listOf(at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup(null, null, listOf(channel)),
                    at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup(ChannelTagId(8), "Favorites", listOf(channel))),
                activeTagId = ChannelTagId(8),
                onSelectTag = { selected = it })
        } }
        compose.onNodeWithTag("home-loading").assertDoesNotExist()
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        compose.onNodeWithTag("home-view-CURRENT_ACTIVITY").assertIsFocused()
        compose.onNodeWithText("Right now").assertExists()
        compose.onNodeWithTag("home-tag-8").assertDoesNotExist()
        compose.onNodeWithTag("home-view-CURRENT_ACTIVITY").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("home-view-button").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.Enter) }
        compose.onNodeWithTag("channels-tags-8").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(ChannelTagId(8), selected) }
        compose.onNodeWithTag("home-tags-button").assertIsFocused()
    }

    @Test fun panelBackAndTagSelectionNeverRefocusACard() {
        val memory = mutableStateOf<HomeFocusKey?>(null)
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> }, focusMemory = memory,
                groups = listOf(at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup(null, null, listOf(channel)),
                    at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup(ChannelTagId(8), "Favorites", listOf(channel))),
                activeTagId = ChannelTagId(8))
        } }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        fun closeAndCheck(panel: String, opener: String, key: Key) {
            compose.runOnIdle { memory.value = null }
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag(panel).performKeyInput { pressKey(key) }
            repeat(5) {
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag(opener).assertIsFocused()
                compose.runOnIdle { assertNull("Closing a panel must not report card focus", memory.value) }
            }
            compose.mainClock.autoAdvance = true
        }
        closeAndCheck("home-view-CURRENT_ACTIVITY", "home-view-button", Key.Back)
        compose.onNodeWithTag("home-view-button").performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.Enter) }
        closeAndCheck("channels-tags-8", "home-tags-button", Key.Back)
        compose.onNodeWithTag("home-tags-button").performKeyInput { pressKey(Key.Enter) }
        closeAndCheck("channels-tags-8", "home-tags-button", Key.Enter)
    }

    @Test fun entryRevealsOneSectionPerFrameAndLateSectionsAppearImmediately() {
        val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(now - 60),
            Instant.fromEpochSeconds(now + 600), title = "Programme")
        var sections by mutableStateOf(listOf<HomeSection>(
            HomeSection.RecentChannels(listOf(RecentChannelItem(channel))),
            HomeSection.OnNow(listOf(OnNowItem(channel, event))),
            HomeSection.Upcoming(listOf(UpcomingItem(DvrEntry.create(DvrEntryId(1), title = "Scheduled",
                state = DvrEntryState.SCHEDULED, start = Instant.fromEpochSeconds(now + 600),
                stop = Instant.fromEpochSeconds(now + 1200)))))))
        compose.mainClock.autoAdvance = false
        compose.setContent { TVHeadendPlayerTheme {
            HomeContent(sections, PaddingValues(), homeInitialFocus(sections), {})
        } }
        compose.onNodeWithTag("home-section-RECENT_CHANNELS").assertExists()
        compose.onNodeWithTag("home-section-ON_NOW").assertDoesNotExist()
        compose.onNodeWithTag("home-section-UPCOMING").assertDoesNotExist()
        // The first clock frame starts the effect; its frame callback invalidates the next composition.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("home-section-ON_NOW").assertExists()
        compose.onNodeWithTag("home-section-UPCOMING").assertDoesNotExist()
        compose.onNodeWithTag("home-recent-0").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("home-section-UPCOMING").assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("home-section-RECENT_CHANNELS") or
            hasTestTag("home-section-ON_NOW") or hasTestTag("home-section-UPCOMING"))).assertCountEquals(1)
        compose.runOnIdle { sections = sections + HomeSection.Genres(listOf(GenreItem(ProgrammeCategory.ALL))) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("home-section-GENRES").assertExists()
    }

    @Test fun restoredShortLastRowHasNoEntryClampJump() {
        leaveTouchMode()
        val sections = listOf<HomeSection>(HomeSection.RecentChannels(listOf(RecentChannelItem(channel))),
            HomeSection.OnNow(listOf(OnNowItem(channel, EpgEvent.create(EventId(1), channel.id,
                Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600), title = "Programme")))),
            HomeSection.Genres(listOf(GenreItem(ProgrammeCategory.ALL))),
            HomeSection.Upcoming(listOf(UpcomingItem(DvrEntry.create(DvrEntryId(1), title = "Scheduled",
                state = DvrEntryState.SCHEDULED, start = Instant.fromEpochSeconds(now + 600),
                stop = Instant.fromEpochSeconds(now + 1200))))))
        compose.mainClock.autoAdvance = false
        compose.setContent { TVHeadendPlayerTheme {
            HomeContent(sections, PaddingValues(), HomeFocusKey(HomeSectionKind.UPCOMING, "recording:1", 0), {})
        } }
        sections.forEach { compose.onNodeWithTag("home-section-${it.kind.name}").assertExists() }
        // First layout/focus frames, before the reveal counter has finished. Do not settle first.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("home-upcoming-0").assertIsFocused()
        val first = compose.onNodeWithTag("home-section-UPCOMING").getUnclippedBoundsInRoot()
        repeat(40) { frame ->
            compose.mainClock.advanceTimeByFrame()
            assertEquals("Last-row bounds must stay stable after initial layout, frame $frame", first,
                compose.onNodeWithTag("home-section-UPCOMING").getUnclippedBoundsInRoot())
        }
    }

    @Test fun deepEntryComposesItsPrecedingSectionsAndRapidDownOnlyTargetsRenderedCards() {
        val sections = listOf<HomeSection>(HomeSection.RecentChannels(listOf(RecentChannelItem(channel))),
            HomeSection.Genres(listOf(GenreItem(ProgrammeCategory.ALL))),
            HomeSection.Upcoming(listOf(UpcomingItem(DvrEntry.create(DvrEntryId(1), title = "Scheduled",
                state = DvrEntryState.SCHEDULED, start = Instant.fromEpochSeconds(now + 600),
                stop = Instant.fromEpochSeconds(now + 1200))))))
        compose.mainClock.autoAdvance = false
        compose.setContent { TVHeadendPlayerTheme {
            HomeContent(sections, PaddingValues(), HomeFocusKey(HomeSectionKind.UPCOMING, "recording:1", 0), {})
        } }
        sections.forEach { compose.onNodeWithTag("home-section-${it.kind.name}").assertExists() }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("home-upcoming-0").assertIsFocused()
        val before = compose.onNodeWithTag("home-upcoming-0").getUnclippedBoundsInRoot()
        repeat(5) {
            compose.onNodeWithTag("home-upcoming-0").performKeyInput { pressKey(Key.DirectionDown) }
            compose.mainClock.advanceTimeByFrame()
        }
        compose.onNodeWithTag("home-upcoming-0").assertIsFocused()
        assertEquals(before, compose.onNodeWithTag("home-upcoming-0").getUnclippedBoundsInRoot())
    }

    @Test fun optionsToggleAllSectionsAndBackReturnsToHeaderWithoutTuning() {
        var enabled by mutableStateOf(true)
        var input by mutableStateOf(HomeInput(listOf(channel), emptyMap(), emptyList(),
            listOf(channel.id), emptyList(), { null }, null, now))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), enabled,
                onPlay = { _, _ -> fail("options tuned") }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> },
                onGenre = {}, onRecordings = { _, _ -> },
                onSectionVisibleChange = { section, visible -> input = input.copy(hiddenSections =
                    if (visible) input.hiddenSections - section else input.hiddenSections + section) })
        } }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        compose.onNodeWithTag("home-view-CURRENT_ACTIVITY").assertIsFocused()
        repeat(HomeSectionKind.entries.size) { index ->
            compose.onNodeWithTag("home-view-${HomeSectionKind.entries[index].name}")
                .performKeyInput { pressKey(Key.Enter); if (index < HomeSectionKind.entries.lastIndex) pressKey(Key.DirectionDown) }
        }
        compose.runOnIdle { assertEquals(HomeSectionKind.entries.toSet(), input.hiddenSections) }
        compose.onNodeWithTag("home-view-UPCOMING").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("home-view-button").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("home-recovery").assertIsFocused()
        compose.runOnIdle { enabled = false }
        compose.runOnIdle { enabled = true }
        compose.onNodeWithTag("home-recovery").assertIsFocused()
    }

    @Test fun rapidOptionsChangesSurviveDelayedStoreWritesWithoutRenderedPreferencesCatchingUp() {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = at.bernhardberger.tvhplayer.settings.UiSettingsStore(
            at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore(beforeUpdate = { release.await() }))
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            val settings by store.settings.collectAsState(at.bernhardberger.tvhplayer.settings.UiSettings())
            val saveScope = rememberCoroutineScope()
            HomeScreenContent(input.copy(hiddenSections = settings.hiddenHomeSections), SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> fail("options tuned") }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> },
                onSectionVisibleChange = { section, visible -> saveScope.launch { store.setHomeSectionVisible(section, visible) } })
        } }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        compose.onNodeWithTag("home-view-CURRENT_ACTIVITY").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter); pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("home-view-RECENT_CHANNELS").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle {
            assertTrue(kotlinx.coroutines.runBlocking { store.settings.first().hiddenHomeSections.isEmpty() })
            release.complete(Unit)
        }
        compose.waitUntil(5_000) {
            kotlinx.coroutines.runBlocking { store.settings.first().hiddenHomeSections.size == 2 }
        }
        compose.runOnIdle {
            assertEquals(setOf(HomeSectionKind.CURRENT_ACTIVITY, HomeSectionKind.RECENT_CHANNELS),
                kotlinx.coroutines.runBlocking { store.settings.first().hiddenHomeSections })
        }
    }

    @Test fun channelsWithoutHistoryRecordingsOrEpgAreNotReportedAsNoChannels() {
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), emptyList(), emptyList(), { null }, null, now)
        val observation = SessionObservation.create(
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(emptyList())),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(emptyList())),
        )
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithText("Start watching").assertExists()
        compose.onNodeWithText("Channels you watch and recordings will appear here.").assertExists()
        compose.onNodeWithText("Connection settings").assertDoesNotExist()
        compose.onNodeWithText("Browse channels").assertIsFocused()
        compose.onNodeWithTag("home-view-button").assertExists()
    }

    private fun channelsAwaitingRows(input: () -> HomeInput, observation: () -> SessionObservation) {
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input(), observation(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
    }

    @Test fun savedChannelsWaitingForTheGuideShowLoadingThenItsRowsWithoutAnEmptyHome() {
        val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(now - 60),
            Instant.fromEpochSeconds(now + 600), title = "Programme")
        val catalog = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel)))
        var restored by mutableStateOf(false)
        channelsAwaitingRows(
            input = { HomeInput(listOf(channel), if (restored) mapOf(channel.id to event) else emptyMap(), emptyList(), emptyList(), emptyList(), { null }, null, now) },
            observation = { SessionObservation.create(channelState = catalog,
                epgState = if (restored) EpgRepositoryState.Current(EpgSnapshot.create(listOf(event))) else EpgRepositoryState.Empty,
                dvrState = if (restored) DvrRepositoryState.Current(DvrSnapshot.create(emptyList())) else DvrRepositoryState.Empty) },
        )
        // A usual cold start ends here, without anything to see.
        compose.onNodeWithTag("home-loading").assertDoesNotExist()
        compose.onNodeWithText("Loading…").assertDoesNotExist()
        compose.onNodeWithText("Start watching").assertDoesNotExist()
        compose.onNodeWithTag("home-recovery").assertDoesNotExist()
        compose.onNodeWithTag("home-view-button").assertIsFocused()
        compose.mainClock.advanceTimeBy(1_700)
        compose.onNodeWithTag("home-loading").assertExists()
        compose.onNodeWithText("Loading…").assertExists()
        compose.onNodeWithTag("home-recovery").assertDoesNotExist()
        compose.onNodeWithTag("home-view-button").assertIsFocused()
        compose.runOnIdle { restored = true }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("home-loading").assertDoesNotExist()
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
    }

    @Test fun rowsThatNeverArriveEndInTheEmptyHomeInsteadOfLoadingForever() {
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), emptyList(), emptyList(), { null }, null, now)
        val observation = SessionObservation.create(channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))))
        channelsAwaitingRows({ input }, { observation })
        compose.mainClock.advanceTimeBy(9_000)
        compose.onNodeWithTag("home-loading").assertExists()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithTag("home-loading").assertDoesNotExist()
        compose.onNodeWithText("Start watching").assertExists()
        compose.onNodeWithText("Still connecting…").assertDoesNotExist()
        compose.onNodeWithText("Connection settings").assertDoesNotExist()
        compose.onNodeWithText("Browse channels").assertExists()
        compose.onNodeWithTag("home-view-button").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Browse channels").assertIsFocused()
    }

    @Test fun delayedEntryRestoreDoesNotTakeFocusFromAnOpeningDrawer() {
        var enabled by mutableStateOf(false)
        lateinit var drawer: androidx.tv.material3.DrawerState
        val drawerFocus = androidx.compose.ui.focus.FocusRequester()
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent {
            drawer = androidx.tv.material3.rememberDrawerState(androidx.tv.material3.DrawerValue.Closed)
            CompositionLocalProvider(at.bernhardberger.tvhplayer.ui.components.LocalBrowseDrawerState provides drawer) {
                TVHeadendPlayerTheme {
                    androidx.compose.foundation.layout.Box {
                        HomeScreenContent(input, SessionObservation.create(), PaddingValues(), enabled,
                            onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                            onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
                        androidx.tv.material3.Button(onClick = {}, modifier = androidx.compose.ui.Modifier
                            .then(androidx.compose.ui.Modifier.focusRequester(drawerFocus)).testTag("test-drawer")) {
                            androidx.tv.material3.Text("Drawer")
                        }
                    }
                }
            }
        }
        compose.runOnIdle { drawerFocus.requestFocus() }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { enabled = true }
        compose.runOnIdle {
            drawer.setValue(androidx.tv.material3.DrawerValue.Open)
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithTag("test-drawer").assertIsFocused()
    }

    @Test fun loadingOffersSettingsAfterTenSecondsWithoutTakingHeaderFocus() {
        var current by mutableStateOf(false)
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input.copy(channels = if (current) listOf(channel) else emptyList()), SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> }, catalogCurrent = current)
        } }
        compose.onNodeWithTag("home-view-button").assertIsFocused()
        compose.mainClock.advanceTimeBy(1_700)
        compose.onNodeWithText("Loading channels…").assertExists()
        compose.onNodeWithTag("home-recovery").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(7_300)
        compose.onNodeWithTag("home-recovery").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithText("Still connecting…").assertExists()
        compose.onNodeWithText("Connection settings").assertExists()
        compose.onNodeWithTag("home-view-button").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("home-recovery").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("home-view-button").assertIsFocused()
        compose.runOnIdle { current = true }
        compose.onNodeWithText("Still connecting…").assertDoesNotExist()
        compose.onNodeWithText("Connection settings").assertDoesNotExist()
    }

    @Test fun delayedSettingsTakesFocusOnlyWhenHomeHasNoFocusedControl() {
        lateinit var focus: androidx.compose.ui.focus.FocusManager
        var opened = false
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            focus = androidx.compose.ui.platform.LocalFocusManager.current
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> }, catalogCurrent = false,
                onOpenConnectionSettings = { opened = true })
        } }
        compose.onNodeWithTag("home-view-button").assertIsFocused()
        compose.runOnIdle { focus.clearFocus(force = true) }
        compose.mainClock.advanceTimeBy(10_100)
        compose.onNodeWithTag("home-recovery").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun firstHomeFrameAfterANewSessionWithEqualChannelsDrawsOnNow() {
        val now = kotlin.time.Clock.System.now().epochSeconds
        fun observation() = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(listOf(EpgEvent.create(EventId(1), channel.id,
                Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600), title = "On now")))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()))
        val session = FakeTvheadendSession(observation())
        val vm = at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel(session,
            at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore(at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore()))
        val viewModels = androidx.lifecycle.ViewModelStore().apply { put("channels", vm) }
        compose.runOnIdle {
            val previous = requireNotNull(vm.scope.value.observation)
            session.publish(SessionObservation.create(sessionState = SessionState.Disconnected, channelState = previous.channelState))
            session.publish(FakeTvheadendSession(observation()).observation.value)
            assertEquals(previous.channelState, session.observation.value.channelState)
            assertNotNull(session.observation.value.currentSession)
            assertNotSame(previous.currentSession, session.observation.value.currentSession)
        }
        val dependencies = org.koin.dsl.koinApplication { modules(module {
            single { at.bernhardberger.tvhplayer.settings.UiSettingsStore(at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore()) }
        }) }
        compose.setContent { org.koin.compose.KoinIsolatedContext(dependencies) {
            DisposableEffect(Unit) { onDispose { viewModels.clear(); dependencies.close() } }
            TVHeadendPlayerTheme {
                HomeScreen(PaddingValues(), true, vm, null, null,
                    onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                    onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> },
                    lastPlayedStore = at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore(
                        androidx.test.core.app.ApplicationProvider.getApplicationContext()))
            }
        } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("home-header").assertExists()
        compose.onNodeWithTag("home-onnow-0").assertIsDisplayed()
    }

    @Test fun savedHiddenSectionsNeverRenderOrEnterRootFocusMemoryWhileSettingsLoad() {
        val now = kotlin.time.Clock.System.now().epochSeconds
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val saved = androidx.datastore.preferences.core.preferencesOf(
            androidx.datastore.preferences.core.booleanPreferencesKey("homeSectionCURRENT_ACTIVITY") to false)
        val preferences = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data = kotlinx.coroutines.flow.flow { release.await(); emit(saved) }
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences) = transform(saved)
        }
        val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(now - 60),
            Instant.fromEpochSeconds(now + 600), title = "On now")
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(listOf(event)))))
        val vm = at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel(session,
            at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore(at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore()))
        val viewModels = androidx.lifecycle.ViewModelStore().apply { put("channels", vm) }
        val memory = mutableStateOf<HomeFocusKey?>(null)
        val dependencies = org.koin.dsl.koinApplication { modules(module {
            single { at.bernhardberger.tvhplayer.settings.UiSettingsStore(preferences) }
        }) }
        compose.setContent { org.koin.compose.KoinIsolatedContext(dependencies) {
            DisposableEffect(Unit) { onDispose { viewModels.clear(); dependencies.close() } }
            TVHeadendPlayerTheme {
                HomeScreen(PaddingValues(), true, vm, channel.id, null,
                    onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                    onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> }, focusMemory = memory,
                    lastPlayedStore = at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore(
                        androidx.test.core.app.ApplicationProvider.getApplicationContext()))
            }
        } }
        compose.onNodeWithTag("home-view-button").assertDoesNotExist()
        compose.onNodeWithTag("home-activity-0").assertDoesNotExist()
        compose.runOnIdle { assertNull(memory.value); release.complete(Unit) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("home-onnow-0") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-activity-0").assertDoesNotExist()
        compose.runOnIdle { assertEquals(HomeSectionKind.ON_NOW, memory.value?.section) }
    }

    @Test fun retryLeftReachesTheRailAndUpReachesViewOptions() {
        val rail = androidx.compose.ui.focus.FocusRequester()
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            CompositionLocalProvider(at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus provides rail) {
                androidx.compose.foundation.layout.Row {
                    androidx.tv.material3.Button(onClick = {}, modifier = androidx.compose.ui.Modifier
                        .focusRequester(rail).testTag("test-rail")) { androidx.tv.material3.Text("Home") }
                    HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                        connectionUiState = ConnectionUiState.Error(at.bernhardberger.tvhplayer.data.ConnectionFailureKind.UNREACHABLE,
                            SessionRecoveryDisposition.EXPLICIT_RETRY),
                        onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                        onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
                }
            }
        } }
        compose.onNodeWithText("Retry").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("home-view-button").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Retry").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("test-rail").assertIsFocused()
    }

    @Test fun recordingsOlderThanSevenDaysDoNotClaimTheLibraryIsEmpty() {
        emptyHomeWithHiddenSections(setOf(HomeSectionKind.RECENT_CHANNELS, HomeSectionKind.ON_NOW),
            listOf(DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED,
                start = Instant.fromEpochSeconds(now - 8 * 24 * 3600))))
        compose.onNodeWithText("No recordings yet.").assertDoesNotExist()
        compose.onNodeWithText("Channels you watch and recordings will appear here.").assertExists()
    }

    @Test fun hiddenExplainingSectionsStillShowTheViewOptionsFallback() {
        emptyHomeWithHiddenSections(HomeSectionKind.entries.toSet())
        compose.onNodeWithText("Sections are hidden. Use View options to show them.").assertExists()
        compose.onNodeWithTag("home-recovery").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("home-view-CURRENT_ACTIVITY").assertIsFocused()
    }

    private fun emptyHomeWithHiddenSections(hidden: Set<HomeSectionKind>, entries: List<DvrEntry> = emptyList()) {
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), emptyList(), entries, { null }, null, now, hidden)
        val observation = SessionObservation.create(
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries)))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
    }

    @Test fun staleHomeOwnerRejectsOkAndMenuBeforeRecomposition() {
        var current = true
        val owner = at.bernhardberger.tvhplayer.ui.components.TabOwner { current }
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent { CompositionLocalProvider(at.bernhardberger.tvhplayer.ui.components.LocalTabOwner provides owner) {
            TVHeadendPlayerTheme {
                HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                    onPlay = { _, _ -> fail("stale frame tuned") }, onPlayRecording = { _, _ -> fail("stale frame played") },
                    onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> fail("stale frame navigated") },
                    onGenre = { fail("stale frame navigated") }, onRecordings = { _, _ -> fail("stale frame navigated") })
            }
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
        compose.runOnIdle { current = false }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionCenter); pressKey(Key.Menu) }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
    }

    @Test fun removingEarlierCardKeepsTheFocusedChannelIdentity() {
        val second = Channel.create(ChannelId(2), name = "Second")
        val third = Channel.create(ChannelId(3), name = "Third")
        var input by mutableStateOf(HomeInput(listOf(channel, second, third), emptyMap(), emptyList(),
            listOf(channel.id, second.id, third.id), emptyList(), { null }, null, now))
        val tuned = mutableListOf<ChannelId>()
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("home-recent-1").assertIsFocused()
        compose.runOnIdle { input = input.copy(recentChannelIds = listOf(second.id, third.id)) }
        compose.onNodeWithTag("home-recent-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(second.id), tuned) }
    }

    @Test fun removingFocusedSectionRestoresTheFirstRemainingCard() {
        var input by mutableStateOf(HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id),
            emptyList(), { null }, HomePlaying.LiveChannel(channel.id), now))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-activity-0").assertIsFocused()
        compose.runOnIdle { input = input.copy(playing = null) }
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
    }

    @Test fun removingLastVisitedCardDoesNotStealHeaderFocus() {
        val second = Channel.create(ChannelId(2), name = "Second")
        var input by mutableStateOf(HomeInput(listOf(channel, second), emptyMap(), emptyList(),
            listOf(channel.id, second.id), emptyList(), { null }, null, now))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("home-tags-button").assertIsFocused()
        compose.runOnIdle { input = input.copy(recentChannelIds = listOf(second.id)) }
        compose.onNodeWithTag("home-tags-button").assertIsFocused()
    }

    @Test fun outOfScopeContinueWatchingOpensFromOkAndMenu() {
        recordingHome(false, { fail("must not tune") }, { }, outsideScope = true)
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.Menu) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
    }

    @Test fun outOfScopeActivityRecordingFallsBackFromOkAndOpensMenu() {
        recordingHome(false, { fail("must not tune") }, { }, playing = true, outsideScope = true)
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.Menu) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
    }

    @Test fun recordingShelfMenuOpensWithoutPlayingAndBackRestoresFocus() {
        recordingHome(false, { fail("menu tuned") }, { fail("menu played") })
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.Menu) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("home-shelf-0").assertIsFocused()
    }

    @Test fun missingWarmRecordingFallsBackToItsCurrentEntry() {
        recordingHome(true, { }, { }, playing = true)
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused()
    }

    @OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun onNowMenuOpensWithoutTuningAndBackRestoresFocus() {
        val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(now - 60),
            Instant.fromEpochSeconds(now + 600), title = "Now")
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(event))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create())))
        val observation = session.observation.value
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, observation.currentSession?.generationIdentity) }
        val preferences = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data = kotlinx.coroutines.flow.MutableStateFlow(androidx.datastore.preferences.core.emptyPreferences())
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences) =
                transform(data.value).also { data.value = it }
        }
        val dependencies = org.koin.dsl.koinApplication { modules(module {
            single<TvheadendSession> { session }; single { notices }
            single { at.bernhardberger.tvhplayer.settings.UiSettingsStore(preferences) }
        }) }
        val input = HomeInput(listOf(channel), mapOf(channel.id to event), listOf(event), emptyList(), emptyList(),
            observation::recordingPlaybackAdmission, null, now)
        compose.setContent { org.koin.compose.KoinIsolatedContext(dependencies) {
            back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            DisposableEffect(dependencies) { onDispose { dependencies.close() } }
            TVHeadendPlayerTheme {
                HomeScreenContent(input, observation, PaddingValues(), true,
                    onPlay = { _, _ -> fail("menu tuned") }, onPlayRecording = { _, _ -> fail("menu played") },
                    onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
            }
        } }
        compose.onNodeWithTag("home-onnow-0").assertIsFocused().performKeyInput { pressKey(Key.Menu) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
    }

    @Test fun currentLiveOkUsesExistingLiveEntryPath() {
        val tuned = mutableListOf<ChannelId>()
        val observation = SessionObservation.create(channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))))
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), emptyList(), emptyList(), { null },
            HomePlaying.LiveChannel(channel.id), now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> fail("must not restart") },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun activeRecordingOkOffersChoiceAndWatchLiveTunes() {
        val tuned = mutableListOf<ChannelId>()
        recordingHome(running = true, onWatch = { tuned += it }, onPlay = { fail("must not start recording") })
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun outOfScopeRunningRecordingWatchLiveTunesThroughTheRecordingPath() {
        val tuned = mutableListOf<ChannelId>()
        recordingHome(running = true, onWatch = { fail("must not use the scoped channel path") },
            onPlay = { fail("must not start recording") }, outsideScope = true, onWatchRecording = { tuned += it })
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun continueWatchingOkOffersResumeAndBackRestoresCard() {
        val starts = mutableListOf<RecordingPlaybackStart>()
        recordingHome(running = false, onWatch = { fail("must not tune") }, onPlay = { starts += it })
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.RESUME), starts) }
    }

    @Test fun outOfScopeRunningRecordingMenuWatchesThroughTheRecordingPath() {
        val tuned = mutableListOf<ChannelId>()
        recordingHome(running = true, onWatch = { fail("must not use scoped channel path") },
            onPlay = { fail("must not play recording") }, outsideScope = true, onWatchRecording = { tuned += it })
        compose.onNodeWithTag("home-activity-0").performKeyInput { pressKey(Key.Menu) }
        compose.onNode(hasText("Watch") and hasAnyAncestor(hasTestTag("browse-context-menu"))).assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown); pressKey(Key.DirectionDown)
        }
        compose.onNodeWithText("Watch channel").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @OptIn(TvheadendTestingApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    private fun recordingHome(running: Boolean, onWatch: (ChannelId) -> Unit, onPlay: (RecordingPlaybackStart) -> Unit, playing: Boolean = false, outsideScope: Boolean = false,
        onWatchRecording: ((ChannelId) -> Unit)? = null, scheduled: Boolean = false) {
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = "Test recording",
            state = if (scheduled) DvrEntryState.SCHEDULED else if (running) DvrEntryState.RECORDING else DvrEntryState.COMPLETED,
            start = Instant.fromEpochSeconds(if (scheduled) now + 600 else now - 1800), stop = Instant.fromEpochSeconds(if (running || scheduled) now + 1800 else now),
            playPosition = 120.seconds,
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "test.ts",
                start = Instant.fromEpochSeconds(now - 1800), stop = Instant.fromEpochSeconds(now))))
        val initialObservation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(entry))),
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED)
        val session = FakeTvheadendSession(initialObservation)
        val observation = session.observation.value
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, observation.currentSession?.generationIdentity) }
        val dependencies = module { single<TvheadendSession> { session }; single { notices } }
        val application = org.koin.dsl.koinApplication { modules(dependencies) }
        val input = HomeInput(if (outsideScope) emptyList() else listOf(channel), emptyMap(), emptyList(), emptyList(), listOf(entry), observation::recordingPlaybackAdmission,
            if (playing) HomePlaying.Recording(entry.id) else null, now)
        compose.setContent { org.koin.compose.KoinIsolatedContext(application) {
            back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            androidx.compose.runtime.DisposableEffect(application) { onDispose { application.close() } }
            TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { id, _ -> onWatch(id) }, onPlayRecording = { _, start -> onPlay(start) },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> },
                onWatchRecordingChannel = { id, _ -> (onWatchRecording ?: onWatch)(id) })
        } } }
    }

    @Test fun currentRecordingOkReturnsToWarmSession() {
        val returns = mutableListOf<DvrEntryId>()
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), emptyList(), { null },
            HomePlaying.Recording(DvrEntryId(9)), now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> fail("must not tune") }, onPlayRecording = { _, _ -> fail("must not restart") },
                onReturnToRecording = { id, _ -> returns += id; true }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-activity-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(DvrEntryId(9)), returns) }
    }

    @Test fun upcomingEndCardOpensSchedule() {
        val modes = mutableListOf<DvrLibraryMode>()
        val entry = DvrEntry.create(DvrEntryId(1), title = "Tomorrow", state = DvrEntryState.SCHEDULED,
            start = Instant.fromEpochSeconds(now + 3600), stop = Instant.fromEpochSeconds(now + 7200))
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), listOf(entry), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { mode, _ -> modes += mode })
        } }
        compose.onNodeWithTag("home-upcoming-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("home-all-scheduled").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(DvrLibraryMode.SCHEDULE), modes) }
    }

    @OptIn(TvheadendTestingApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun completedRecordingStartsAndEndCardOpensArchive() {
        val starts = mutableListOf<at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart>()
        val modes = mutableListOf<DvrLibraryMode>()
        val entry = DvrEntry.create(DvrEntryId(1), title = "Yesterday", state = DvrEntryState.COMPLETED,
            start = Instant.fromEpochSeconds(now - 3600), stop = Instant.fromEpochSeconds(now - 1800),
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "test.ts",
                start = Instant.fromEpochSeconds(now - 3600), stop = Instant.fromEpochSeconds(now - 1800))))
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))))
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), listOf(entry), observation::recordingPlaybackAdmission, null, now)
        assertNotNull(observation.currentSession)
        assertEquals(RecordingOpenAction.START_OVER, recordingOpenAction(observation.recordingPlaybackAdmission(entry)))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, start -> starts += start },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { mode, _ -> modes += mode })
        } }
        compose.onNodeWithTag("home-shelf-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, starts.size) }
        compose.onNodeWithTag("home-shelf-0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("home-all-recordings").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals(listOf(at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart.START_OVER), starts)
            assertEquals(listOf(DvrLibraryMode.ARCHIVE), modes)
        }
    }

    @Test fun fakeInputRendersRecentAndOkTunesWithoutFocusTuning() {
        val tuned = mutableListOf<ChannelId>()
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
        compose.runOnIdle { assertTrue(tuned.isEmpty()) }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun onNowOkAndGenreOkDispatchTheirCallbacks() {
        val tuned = mutableListOf<ChannelId>()
        val genres = mutableListOf<ProgrammeCategory>()
        val event = EpgEvent.create(EventId(1), channelId = channel.id,
            start = Instant.fromEpochSeconds(now - 600), stop = Instant.fromEpochSeconds(now + 600), title = "Test programme",
            contentGenre = ContentGenre.fromContentType(0x40))
        val input = HomeInput(listOf(channel), mapOf(channel.id to event), listOf(event), emptyList(), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = { genres += it }, onRecordings = { _, _ -> })
        } }
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter); pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("home-genre-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned); assertEquals(listOf(ProgrammeCategory.SPORT), genres) }
    }

    private fun onNowRow(
        count: Int,
        observation: SessionObservation = SessionObservation.create(),
        window: androidx.compose.ui.platform.WindowInfo? = null,
    ): MutableState<HomeInput> {
        val channels = (1L..count).map { Channel.create(ChannelId(it), name = "Channel $it", number = it) }
        val events = channels.associate { it.id to EpgEvent.create(EventId(it.id.value), it.id,
            Instant.fromEpochSeconds(now - 600), Instant.fromEpochSeconds(now + 600),
            title = "Programme ${it.id.value}") }
        val input = mutableStateOf(HomeInput(channels, events, emptyList(), emptyList(), emptyList(), { null }, null, now))
        compose.setContent { TVHeadendPlayerTheme {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalWindowInfo provides (window ?: androidx.compose.ui.platform.LocalWindowInfo.current),
            ) {
                HomeScreenContent(input.value, observation, PaddingValues(), true,
                    onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                    onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> })
            }
        } }
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
        return input
    }

    private fun onNowInfo(text: String) =
        compose.onAllNodes(hasAnyAncestor(hasTestTag("home-on-now-block")) and hasText(text, substring = true), useUnmergedTree = true)

    private fun horizontalKey(action: Int, repeat: Int = 0) {
        compose.onRoot().performKeyPress(androidx.compose.ui.input.key.KeyEvent(android.view.KeyEvent(
            0L, 0L, action, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, repeat)))
        compose.waitForIdle()
    }

    @Test fun rightAtTheLastCardStaysOnIt() {
        onNowRow(2)
        compose.onNodeWithTag("home-onnow-0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
    }

    @Test fun onNowInfoFollowsEachPressAndWaitsForAHeldKeyToRest() {
        onNowRow(6)
        onNowInfo("Programme 1").assertCountEquals(1)
        horizontalKey(android.view.KeyEvent.ACTION_DOWN)
        horizontalKey(android.view.KeyEvent.ACTION_UP)
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        onNowInfo("Programme 2").assertCountEquals(1)
        horizontalKey(android.view.KeyEvent.ACTION_DOWN)
        horizontalKey(android.view.KeyEvent.ACTION_DOWN, repeat = 1)
        compose.onNodeWithTag("home-onnow-3").assertIsFocused()
        compose.mainClock.advanceTimeBy(BrowseMotionPolicy.backdropSettleMs * 2)
        onNowInfo("Programme 3").assertCountEquals(1)
        onNowInfo("Programme 4").assertCountEquals(0)
        compose.mainClock.autoAdvance = false
        horizontalKey(android.view.KeyEvent.ACTION_UP)
        compose.mainClock.advanceTimeBy(BrowseMotionPolicy.backdropSettleMs - 50)
        onNowInfo("Programme 3").assertCountEquals(1)
        onNowInfo("Programme 4").assertCountEquals(0)
        compose.mainClock.advanceTimeBy(100)
        onNowInfo("Programme 4").assertCountEquals(1)
        compose.mainClock.autoAdvance = true
    }

    @Test fun aReturnWithFocusStillOnTheRailShowsTheRememberedCardsInfo() {
        val items = (1L..6).map { id ->
            val item = Channel.create(ChannelId(id), name = "Channel $id", number = id)
            OnNowItem(item, EpgEvent.create(EventId(id), item.id, Instant.fromEpochSeconds(now - 600),
                Instant.fromEpochSeconds(now + 600), title = "Programme $id"))
        }
        compose.setContent { TVHeadendPlayerTheme {
            HomeContent(listOf(HomeSection.OnNow(items)), PaddingValues(),
                HomeFocusKey(HomeSectionKind.ON_NOW, "onnow:4", 3), {},
                focusEnabled = false, observation = SessionObservation.create())
        } }
        compose.waitForIdle()
        onNowInfo("Programme 4").assertCountEquals(1)
        onNowInfo("Programme 1").assertCountEquals(0)
    }

    @Test fun aHoldWhoseReleaseGotLostInAnotherWindowEndsWhenThatWindowTakesOver() {
        val focused = mutableStateOf(true)
        onNowRow(6, window = object : androidx.compose.ui.platform.WindowInfo {
            override val isWindowFocused get() = focused.value
        })
        horizontalKey(android.view.KeyEvent.ACTION_DOWN)
        horizontalKey(android.view.KeyEvent.ACTION_DOWN, repeat = 1)
        val card = "home-onnow-2"
        compose.onNodeWithTag(card).assertIsFocused()
        compose.mainClock.advanceTimeBy(BrowseMotionPolicy.backdropSettleMs * 2)
        onNowInfo("Programme 3").assertCountEquals(0)
        compose.runOnIdle { focused.value = false }
        compose.waitForIdle()
        compose.runOnIdle { focused.value = true }
        compose.mainClock.advanceTimeBy(BrowseMotionPolicy.backdropSettleMs * 2)
        compose.onNodeWithTag(card).assertIsFocused()
        onNowInfo("Programme 3").assertCountEquals(1)
    }

    @Test fun aNewProgrammeOnTheFocusedCardUpdatesInPlace() {
        val input = onNowRow(2)
        onNowInfo("Programme 1").assertCountEquals(1)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            input.value = input.value.copy(currentEvents = input.value.currentEvents + (ChannelId(1) to EpgEvent.create(
                EventId(99), ChannelId(1), Instant.fromEpochSeconds(now), Instant.fromEpochSeconds(now + 600),
                title = "Following programme")))
        }
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val shown = listOf("Programme 1", "Following programme").sumOf { onNowInfo(it).fetchSemanticsNodes().size }
            assertEquals("the old and new programme are never both on screen", 1, shown)
        }
        onNowInfo("Following programme").assertCountEquals(1)
        compose.mainClock.autoAdvance = true
    }

    @Test fun onNowNextLineMarksAScheduledRecording() {
        val next = EpgEvent.create(EventId(50), ChannelId(1),
            Instant.fromEpochSeconds(now + 600), Instant.fromEpochSeconds(now + 1200), title = "Later")
        onNowRow(1, SessionObservation.create(
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(next))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(
                DvrEntry.create(id = DvrEntryId(9), eventId = next.id, state = DvrEntryState.SCHEDULED)))),
        ))
        onNowInfo("Later").assertCountEquals(1)
        compose.onNode(hasAnyAncestor(hasTestTag("home-on-now-block")) and hasTestTag("channels-next-scheduled"), useUnmergedTree = true)
            .assertExists()
    }
}
