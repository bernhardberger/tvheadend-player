@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)


package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.Key
import at.bernhardberger.tvhplayer.testutil.assertMainSettled
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.testutil.pressBack
import androidx.media3.common.Tracks
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import kotlinx.coroutines.runBlocking
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class LivePlayerChromeNavigationTest : LivePlayerChromeFixture() {
    @Test fun holdBackStopsWithNoLayer() = holdStops { key(Key.Back); assertFalse(exists("player-banner")) }

    @Test fun backDownRenewsQuickListUntilReleaseRestoresSelection() {
        screen()
        playerReady()
        assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), audioTrack("a2", "de", false))))
        val start = player.trackSelectionParameters
        press(Key.MediaAudioTrack)
        press(Key.DirectionDown)
        compose.mainClock.advanceTimeBy(PLAYBACK_QUICK_LIST_AUTO_CLOSE_MS - 200)
        compose.waitForIdle()
        assertTrue(player.trackSelectionParameters.overrides.isNotEmpty())
        compose.onRoot().performKeyInput { keyDown(Key.Back) }
        // Apply the key's snapshot update and restart the timer before crossing its old deadline.
        settleFrames()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertTrue("Back down renews the list across its previous deadline", exists("playback-options-track-list"))
        compose.onRoot().performKeyInput { keyUp(Key.Back) }
        settleFrames()
        assertFalse(exists("playback-options-track-list"))
        assertEquals(start, player.trackSelectionParameters)
    }

    @Test fun confirmationRemovedDuringBackPressDoesNotCloseInfo() = removedConfirmation(held = false)

    @Test fun confirmationRemovedDuringHeldBackDoesNotCloseInfo() = removedConfirmation(held = true)

    @Test fun numberEntryExpiringDuringBackPressDoesNotHideBannerOrClosePlayer() {
        var closes = 0
        screen(onClose = { closes++ })
        press(Key.Nine)
        assertTrue(compose.onAllNodesWithText("9").fetchSemanticsNodes().isNotEmpty())
        compose.runOnUiThread {
            view.dispatchKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_BACK))
        }
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        assertTrue("the number entry times out", compose.onAllNodesWithText("9").fetchSemanticsNodes().isEmpty())
        val bannerBeforeRelease = exists("player-banner")
        compose.runOnUiThread {
            view.dispatchKeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN,
                AndroidKeyEvent.KEYCODE_BACK, 1, 0, 0, 0, AndroidKeyEvent.FLAG_LONG_PRESS))
            view.dispatchKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_BACK))
        }
        settleFrames()
        assertEquals(bannerBeforeRelease, exists("player-banner"))
        assertEquals(0, closes)
        assertTrue(runtime.activeTarget.value != null)
    }

    @Test fun relatedInfoPageReplacingBackOwnerDoesNotGoBackOnRelease() {
        screen()
        publishMatchingFutureProgramme()
        key(Key.Info)
        pressBack(::hardwareBack) {
            key(Key.DirectionDown)
            compose.onNodeWithTag("details-card-12").assertIsFocused()
            key(Key.DirectionCenter)
            compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithTag("details-record").assertIsFocused()
        }
        settle()
        assertTrue(exists("programme-details-page"))
        compose.onNodeWithTag("details-record").assertIsFocused()
        assertTrue(runtime.activeTarget.value != null)
    }

    @Test fun watchingBackPressDoesNotStopOrCloseInfoAppearingBeforeRepeat() {
        var closes = 0
        screen(onClose = { closes++ })
        compose.runOnUiThread {
            view.dispatchKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_BACK))
        }
        key(Key.Info)
        assertTrue(exists("programme-details-page"))
        compose.runOnUiThread {
            view.dispatchKeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN,
                AndroidKeyEvent.KEYCODE_BACK, 1, 0, 0, 0, AndroidKeyEvent.FLAG_LONG_PRESS))
            view.dispatchKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_BACK))
        }
        settle()
        assertTrue(exists("programme-details-page"))
        assertEquals(0, closes)
        assertTrue(runtime.activeTarget.value != null)
    }

    @Test fun canceledBackKeepsNestedInfoConfirmationOpen() {
        screen()
        publishInfoRecording(DvrEntryState.RECORDING)
        key(Key.Info)
        compose.onNodeWithTag("details-stop").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("two-column-first").assertIsFocused()
        compose.runOnUiThread {
            view.dispatchKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_BACK))
            view.dispatchKeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_UP,
                AndroidKeyEvent.KEYCODE_BACK, 0, 0, 0, 0, AndroidKeyEvent.FLAG_CANCELED))
        }
        settle()
        compose.onNodeWithTag("two-column-first").assertIsFocused()
        assertTrue(runtime.activeTarget.value != null)
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
    }

    @Test fun holdBackOverScheduleContextMenuClosesOnlyTheMenu() = holdGoesBack({
        key(Key.Guide); key(Key.Menu); assertTrue(exists("browse-context-menu"))
    }) {
        assertFalse(exists("browse-context-menu"))
        assertTrue(exists("channel-schedule-panel"))
    }

    @Test fun holdBackStopsOverControls() = holdStops { key(Key.DirectionDown); assertTrue(exists("player-actions")) }

    @Test fun holdBackOverInfoClosesOnlyInfo() = holdGoesBack({
        key(Key.Info); assertTrue(exists("programme-details-page"))
    }) { assertFalse(exists("programme-details-page")); assertFalse(exists("player-actions")) }

    @Test fun holdBackOverScheduleClosesOnlySchedule() = holdGoesBack({
        key(Key.Guide); assertTrue(exists("channel-schedule-panel"))
    }) { assertFalse(exists("channel-schedule-panel")); assertFalse(exists("player-actions")) }

    @Test fun holdBackOverOptionsClosesOnlyOptions() = holdGoesBack({
        key(Key.Menu); assertTrue(exists("playback-options-display"))
    }) { assertFalse(exists("playback-options-display")); assertEquals(listOf("player-settings"), focused()) }

    @Test fun holdBackOverOptionsDetailReturnsOnlyToOptionsRoot() = holdGoesBack({
        key(Key.Menu)
        compose.onNodeWithTag("playback-options-display").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertFalse(exists("playback-options-display"))
    }) { assertTrue(exists("playback-options-display")); assertFalse(exists("player-actions")) }

    @Test fun holdBackOverQuickListClosesOnlyTheList() = holdGoesBack({
        playerReady()
        assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), audioTrack("a2", "de", false))))
        key(Key.MediaAudioTrack); assertTrue(exists("playback-options-track-list"))
    }) { assertFalse(exists("playback-options-track-list")); assertFalse(exists("player-actions")) }

    @Test fun holdBackDuringNumberEntryOnlyClearsEntry() = holdGoesBack({
        press(Key.Nine)
        assertTrue(compose.onAllNodesWithText("9").fetchSemanticsNodes().isNotEmpty())
    }) { assertTrue(compose.onAllNodesWithText("9").fetchSemanticsNodes().isEmpty()); assertTrue(exists("player-banner")) }

    @Test fun holdBackOverChannelListClosesOnlyTheDrawer() = holdGoesBack({
        key(Key(android.view.KeyEvent.KEYCODE_TV_CONTENTS_MENU)); assertTrue(railOpen())
    }) { assertFalse(railOpen()); assertFalse(exists("player-actions")) }

    @Test fun holdBackStopsOverBanner() = holdStops { assertTrue(exists("player-banner")) }

    @Test fun holdBackStopsOverStats() = holdStops {
        key(Key.Menu)
        compose.onNodeWithTag("playback-options-stats").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        // The Stats page opens on its selected Off row; Down moves to the Stream path row, which Center applies.
        key(Key.DirectionDown)
        val streamPath = context.getString(at.bernhardberger.tvhplayer.R.string.stats_stream_path)
        compose.onNode(isFocused() and hasText(streamPath)).assertExists()
        key(Key.DirectionCenter)
        assertEquals(at.bernhardberger.tvhplayer.di.PlayerStatsLevel.STREAM_PATH, statsStore.level.value)
        key(Key.Back); key(Key.Back); key(Key.Back)
        assertTrue(compose.onAllNodesWithText("Stats for nerds").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun holdBackStopsDuringPendingSeek() {
        var closes = 0
        timeshiftPlaying(onClose = { closes++ }, mapPosition = true) {
            key(Key.Back)
            press(Key.DirectionLeft)
            assertTrue(exists("timeshift-seek-preview"))
            holdBack()
            assertEquals(1, closes)
            assertEquals(null, runtime.activeTarget.value)
        }
    }

    @Test fun holdBackInNestedInfoConfirmationOnlyTakesTheSafeBack() {
        var closes = 0
        screen(onClose = { closes++ })
        publishInfoRecording(DvrEntryState.RECORDING)
        for (origin in listOf(Key.Info, Key.Guide)) {
            key(origin)
            if (origin == Key.Guide) {
                compose.onNodeWithTag("schedule-row-11").assertIsFocused()
                key(Key.DirectionCenter)
            }
            compose.onNodeWithTag("details-stop").performSemanticsAction(SemanticsActions.RequestFocus)
            key(Key.DirectionCenter)
            compose.onNodeWithTag("two-column-first").assertIsFocused().assertTextEquals("Keep recording")
            holdBack()
            assertEquals(0, closes)
            assertTrue(runtime.activeTarget.value != null)
            assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
            assertEquals(listOf("details-stop"), focused())
            key(Key.Back)
            if (origin == Key.Guide) key(Key.Back)
        }
    }

    @Test fun shortHardwareBackWaitsForReleaseBeforeHidingBanner() {
        var closes = 0
        screen(onClose = { closes++ })
        assertTrue(exists("player-banner"))
        pressBack(::hardwareBack) {
            settleFrames()
            assertTrue(exists("player-banner"))
            assertEquals(0, closes)
        }
        settle()
        assertFalse(exists("player-banner"))
        assertFalse(exists("player-actions"))
        assertEquals(0, closes)
    }

    @Test fun scheduleFollowsTunedChannelAndContainsPlaybackKeys() {
        screen()
        val now = System.currentTimeMillis() / 1000
        session.publish(observation(now, listOf(
            EpgEvent.create(EventId(11), ChannelId(1), Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600), title = "Channel A"),
            EpgEvent.create(EventId(21), ChannelId(2), Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600), title = "Channel B"))))
        settle()
        key(Key.ChannelUp)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        key(Key.Guide)
        compose.onNodeWithTag("schedule-row-21").assertIsFocused()
        compose.onNodeWithTag("schedule-row-11").assertDoesNotExist()
        val playing = player.playWhenReady
        listOf(Key.MediaPlayPause, Key.MediaStop, Key.MediaRewind, Key.MediaFastForward).forEach(::key)
        assertEquals(playing, player.playWhenReady)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        compose.onNodeWithTag("schedule-row-21").assertIsFocused()
        key(Key.Back)
        assertFalse(exists("channel-schedule-panel"))
        assertFalse(exists("player-pause"))
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-schedule").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("schedule-row-21").assertIsFocused()
        key(Key.Back)
        compose.onNodeWithTag("player-schedule").assertIsFocused()
    }

    @Test fun remoteOverlayBackReturnsToItsBareOrControlsOrigin() {
        screen()
        val shortcuts = listOf(Key.Info, Key.Guide,
            Key(android.view.KeyEvent.KEYCODE_TV_CONTENTS_MENU),
            Key(android.view.KeyEvent.KEYCODE_TV_NUMBER_ENTRY), Key(android.view.KeyEvent.KEYCODE_BOOKMARK))
        for (shortcut in shortcuts) for (controls in listOf(false, true)) {
            if (controls) key(Key.DirectionDown)
            key(shortcut)
            assertTrue("overlay opened for $shortcut", exists("programme-details-page") ||
                exists("channel-schedule-panel") || railOpen())
            key(Key.Back)
            assertEquals("origin restored for $shortcut", controls, exists("player-pause"))
            if (!controls) key(Key.DirectionDown)
            compose.onNodeWithTag(if (controls && shortcut == Key.Info) "player-info" else "player-pause").assertIsFocused()
            key(Key.Back)
        }
    }

    @Test fun bareInfoCoveredByGuideOrSelfClosingLeavesNoReturnFocus() {
        screen()
        key(Key.Info)
        key(Key.Guide)
        key(Key.Back)
        assertTrue(exists("programme-details-page"))
        key(Key.Back)
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
        key(Key.Back)
        key(Key.Info)
        key(Key.DirectionCenter) // Continue watching closes Info through its callback.
        assertFalse(exists("programme-details-page"))
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
    }

    @Test fun infoUnderAGuideClosedByAZapLeavesNoReturnFocus() {
        screen()
        key(Key.DirectionDown)
        key(Key.Info)
        key(Key.Guide)
        assertTrue(exists("channel-schedule-panel"))
        key(Key.ChannelUp)
        assertFalse(exists("channel-schedule-panel"))
        assertFalse(exists("programme-details-page"))
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
    }

    @Test fun guideFromControlsRestoresTheFocusedButton() {
        screen()
        key(Key.DirectionDown)
        for (button in listOf("player-pause", "player-info", "player-settings", "player-schedule")) {
            compose.onNodeWithTag(button).performSemanticsAction(SemanticsActions.RequestFocus)
            settleFrames()
            key(Key.Guide)
            assertTrue(exists("channel-schedule-panel"))
            key(Key.Back)
            compose.onNodeWithTag(button).assertIsFocused()
        }
    }

    @Test fun guideCancelsNumberEntryBeforeItsTimeoutCanTune() {
        screen()
        session.publish(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(
                Channel.create(ChannelId(1), name = "Channel 1", number = 1),
                Channel.create(ChannelId(2), name = "Channel 2", number = 2),
                Channel.create(ChannelId(3), name = "Channel 20", number = 20),
            ))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = emptyList())),
        ))
        settleFrames()
        val target = runtime.activeTarget.value
        press(Key.Two) // A valid match with a longer candidate waits before tuning channel 2.
        assertTrue(compose.onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty())
        assertEquals(target, runtime.activeTarget.value)
        press(Key.Guide)
        repeat(4) { settle() }
        assertTrue(exists("channel-schedule-panel"))
        assertTrue(compose.onAllNodesWithText("2").fetchSemanticsNodes().isEmpty())
        assertEquals(target, runtime.activeTarget.value)
    }

    @Test fun downFromTheActionRowOpensRowsAndUpFromTheTopOrBackReturnsToTheAction() {
        screen()
        key(Key.DirectionDown)
        assertEquals(listOf("player-pause"), focused())
        for (close in listOf(Key.DirectionUp, Key.Back)) {
            key(Key.DirectionRight)
            key(Key.DirectionRight)
            key(Key.DirectionRight)
            key(Key.DirectionRight)
            assertEquals(listOf("player-settings"), focused())
            key(Key.DirectionDown)
            assertEquals("browser opens on the playing channel", listOf("player-channel-card-1"), focused())
            assertTrue("the now-playing strip stands above the rows", exists("player-now-playing"))
            // This screen's channel never leaves tuning.
            compose.onNode(hasTestTag("channel-tuning-indicator") and hasAnyAncestor(hasTestTag("player-now-playing")),
                useUnmergedTree = true).assertExists("the strip carries the playback marker")
            assertFalse("the programme info page does not open", exists("programme-details-page"))
            assertEquals("the opening key tunes nothing", AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            if (close == Key.Back) {
                key(Key.DirectionRight)
                assertEquals("cards browse without tuning", listOf("player-channel-card-2"), focused())
                assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            }
            key(close)
            assertFalse(railOpen())
            assertEquals(listOf("player-settings"), focused())
            key(Key.DirectionLeft)
            key(Key.DirectionLeft)
            key(Key.DirectionLeft)
            key(Key.DirectionLeft)
        }
        assertEquals(listOf("player-pause"), focused())
    }

    @Test fun holdOnACardOpensSharedDetailsAndBackRestoresTheRailCard() {
        screen()
        publishRailProgrammes()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        assertEquals(listOf("player-channel-card-1"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("player-channel-card-2"), focused())
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter); advanceEventTime(1_200); keyUp(Key.DirectionCenter) }
        settle()
        assertTrue("the programme's shared details open", exists("programme-details-page"))
        assertEquals("nothing tunes", AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        val action = focused()
        key(Key.DirectionUp)
        assertTrue("Up does not close details", exists("programme-details-page"))
        assertEquals(action, focused())
        key(Key.Back)
        assertFalse(exists("programme-details-page"))
        assertEquals("one Back returns to the invoking card", listOf("player-channel-card-2"), focused())
        assertTrue(railOpen())
        key(Key.DirectionCenter)
        assertEquals("the first short OK after closing tunes channel 2", AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        key(Key.Back)
        assertFalse(railOpen())
        assertEquals("subsequent Back from the rail returns to Pause", listOf("player-pause"), focused())
    }

    @Test fun accessibilityLongClickAfterCompletedOkDoesNotSwallowFirstDetailsOk() {
        screen()
        publishRailProgrammes()
        key(Key.DirectionDown); key(Key.DirectionDown)
        key(Key.DirectionRight); key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(AppPlaybackTarget.Live(ChannelId(3)), runtime.activeTarget.value)
        key(Key.DirectionLeft)
        compose.onNodeWithTag("player-channel-card-2").performSemanticsAction(SemanticsActions.OnLongClick)
        settle()
        assertTrue(exists("programme-details-page"))
        key(Key.DirectionCenter)
        assertEquals("a fresh OK activates Watch", AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun reopenedRailPreviewsItsRememberedCardFromTheFirstFrame() {
        screen()
        publishRailProgrammes()
        openRows()
        key(Key.DirectionRight)
        assertEquals(listOf("player-channel-card-2"), focused())
        key(Key.Back)
        assertFalse(railOpen())
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        repeat(6) {
            compose.mainClock.advanceTimeByFrame()
            val previewText = compose.onAllNodes(hasAnyAncestor(hasTestTag("player-zap-preview")), useUnmergedTree = true)
                .fetchSemanticsNodes().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            assertTrue("frame $it previews the remembered card, not the playing one: $previewText",
                previewText.isEmpty() || "Second channel now" in previewText)
        }
        compose.mainClock.autoAdvance = true
        settle()
        assertEquals(listOf("player-channel-card-2"), focused())
    }

    @Test fun theRailPreviewDescribesTheFocusedCardAfterEveryMove() {
        screen()
        publishRailProgrammes()
        openRows()
        fun previewText() = compose.onAllNodes(hasAnyAncestor(hasTestTag("player-zap-preview")), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        assertFalse("card 1 is not described by its neighbour: ${previewText()}", "Second channel now" in previewText())
        key(Key.DirectionRight)
        assertEquals(listOf("player-channel-card-2"), focused())
        assertTrue("card 2 is described: ${previewText()}", "Second channel now" in previewText())
        key(Key.DirectionLeft)
        assertEquals(listOf("player-channel-card-1"), focused())
        assertFalse("back on card 1: ${previewText()}", "Second channel now" in previewText())
    }

    @Test fun recentCardTunedElsewhereReopensTheRowOnItsFirstCardFromTheFirstFrame() {
        // The singleton preferences DataStore owns a real Dispatchers.IO file writer.
        runBlocking { LastPlayedChannelStore(context).run { setChannelId(ChannelId(3)); setChannelId(ChannelId(2)) } }
        screen()
        publishRailProgrammes()
        openRows()
        key(Key.DirectionDown)
        assertEquals(listOf("player-channel-card-2"), focused())
        key(Key.Back)
        key(Key.ChannelUp)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertEquals(listOf("player-pause"), focused())
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        repeat(6) {
            compose.mainClock.advanceTimeByFrame()
            val previewText = compose.onAllNodes(hasAnyAncestor(hasTestTag("player-zap-preview")), useUnmergedTree = true)
                .fetchSemanticsNodes().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            assertFalse("frame $it previews the tuned channel that left the row: $previewText", "Second channel now" in previewText)
        }
        compose.mainClock.autoAdvance = true
        settle()
        val recentRow = hasAnyAncestor(hasTestTag("player-channel-row-RECENT"))
        compose.onNode(hasTestTag("player-channel-card-2") and recentRow).assertDoesNotExist()
        compose.onNode(isFocused() and recentRow).assertExists()
    }

    @Test fun withoutRecentChannelsTheRailHasOneRowAndDownStaysOnIt() {
        screen()
        key(Key.DirectionDown); key(Key.DirectionDown)
        assertEquals(listOf("player-channel-card-1"), focused())
        assertFalse(exists("player-channel-row-RECENT"))
        key(Key.DirectionDown)
        assertEquals(listOf("player-channel-card-1"), focused())
        key(Key.Back)
        assertFalse(railOpen())
        assertEquals(listOf("player-pause"), focused())
    }

    @Test fun upRevealLandsOnPauseAndArtworkRemainsPassive() {
        screen()
        key(Key.DirectionUp)
        assertEquals("Up reveals controls onto Pause", listOf("player-pause"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-pause"), focused())
        assertFalse(compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        assertFalse("no browser opens", railOpen())
        assertTrue(identityDescription().contains("Name 1"))
    }

    @Test fun recentRowRemembersItsSelectionAndReopensDirectlyOnCards() {
        // Channel 2 played last, before channel 3.
        // The singleton preferences DataStore owns a real Dispatchers.IO file writer.
        runBlocking { LastPlayedChannelStore(context).run { setChannelId(ChannelId(3)); setChannelId(ChannelId(2)) } }
        screen()
        openRows()
        key(Key.DirectionDown)
        assertEquals("newest recent channel", listOf("player-channel-card-2"), focused())
        assertEquals("browsing tunes nothing", AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        key(Key.Back)
        assertFalse("Back closes the browser", railOpen())
        assertEquals("and returns to Pause", listOf("player-pause"), focused())
        assertTrue("the controls stay", exists("player-actions"))
        key(Key.DirectionDown)
        assertEquals("remembered tab opens on cards", listOf("player-channel-card-2"), focused())
    }

    @Test fun shortOkTunesDirectlyAndKeepsBrowsingContext() {
        screen()
        publishRailProgrammes()
        openRows()
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(exists("programme-details-page"))
        assertTrue(railOpen())
        assertEquals(listOf("player-channel-card-2"), focused())
    }

    @Test fun channelUpDoesNotTuneWhileTheRowsAreOpen() {
        screen()
        openRows()
        key(Key.ChannelUp)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertTrue("the browser stays open", railOpen())
        key(Key.Back)
        assertFalse(railOpen())
        key(Key.ChannelUp)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }
}
