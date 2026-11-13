@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)


package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.R

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import at.bernhardberger.tvhplayer.testutil.assertMainSettled
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.PauseUnavailableReason
import at.bernhardberger.tvheadend.sdk.playback.StreamIndex
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionConfirmation
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionSeekTarget
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOperationResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStream
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvheadend.sdk.playback.createSubscriptionManager
import at.bernhardberger.tvheadend.sdk.testing.ScriptedSubscriptionConnection
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class LivePlayerChromePlaybackTest : LivePlayerChromeFixture() {
    @Test fun liveScrubTicksAndCommitsAtLiveEdge() = timeshiftPlaying(mapPosition = true) { pauses ->
        fun descriptionOrNull(tag: String) = if (exists(tag)) description(tag) else null
        key(Key.MediaPause)
        assertFalse(pauses.last())
        key(Key.MediaFastForward)
        assertTrue("reaching the live edge commits and resumes", pauses.last())
        assertFalse(descriptionOrNull("timeshift-seek-preview").orEmpty().contains(
            context.getString(R.string.player_scrub_forward, 2)))
        key(Key.MediaPause)
        compose.pressKey(Key.MediaRewind, settleFrames = 2, frameMillis = 16)
        repeat(2) { compose.pressKey(Key.MediaRewind, settleFrames = 2, frameMillis = 16) }
        assertTrue(description("timeshift-seek-preview").contains(context.getString(R.string.player_scrub_backward, 8)))
        val first = compose.onNodeWithTag("player-timeline-fill", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        compose.settleFrames(40, frameMillis = 16)
        assertTrue("the automatic target moves backward while playback stays paused",
            compose.onNodeWithTag("player-timeline-fill", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width < first)
        assertFalse(pauses.last())
        compose.pressKey(Key.DirectionDown, settleFrames = 2, frameMillis = 16)
        compose.pressKey(Key.MediaRewind, settleFrames = 2, frameMillis = 16)
        assertTrue(description("player-seekbar").contains(context.getString(R.string.player_scrub_backward, 2)))
        compose.pressKey(Key.DirectionLeft, settleFrames = 2, frameMillis = 16)
        assertFalse("Left stops a scrub even with controls open",
            descriptionOrNull("player-seekbar").orEmpty().contains(context.getString(R.string.player_scrub_backward, 2)))
        key(Key.Back)
        key(Key.Back)
        compose.pressKey(Key.MediaRewind, settleFrames = 2, frameMillis = 16)
        assertTrue(description("timeshift-seek-preview").contains(context.getString(R.string.player_scrub_backward, 2)))
        key(Key.DirectionCenter)
        assertFalse("OK commits instead of leaving the ticker running",
            descriptionOrNull("timeshift-seek-preview").orEmpty().contains(context.getString(R.string.player_scrub_backward, 2)))
        compose.pressKey(Key.MediaRewind, settleFrames = 2, frameMillis = 16)
        assertTrue(description("timeshift-seek-preview").contains(context.getString(R.string.player_scrub_backward, 2)))
        key(Key.MediaPlay)
        assertFalse("Play commits instead of leaving the ticker running",
            descriptionOrNull("timeshift-seek-preview").orEmpty().contains(context.getString(R.string.player_scrub_backward, 2)))
    }

    @Test fun liveScrubRequestsTheBufferStart() = timeshiftPlaying(mapPosition = true) { pauses ->
        key(Key.MediaPause)
        assertFalse(pauses.last())
        val startTarget = sampleTimeshift().bufferStartMs + 1_000L
        repeat(5) { compose.pressKey(Key.MediaRewind, settleFrames = 2, frameMillis = 16) }
        assertTrue(description("timeshift-seek-preview").contains(context.getString(R.string.player_scrub_backward, 32)))
        compose.settleFrames(125, frameMillis = 16)
        settleFrames()
        assertFalse("reaching the buffer start stops automatic motion",
            exists("timeshift-seek-preview") && description("timeshift-seek-preview").contains(context.getString(R.string.player_scrub_backward, 32)))
        val committed = timeshiftConnection.seekTargets.last() as SubscriptionSeekTarget.Absolute
        assertEquals("the first buffered second is requested", startTarget, committed.position.inWholeMilliseconds)
    }

    @Test fun liveScrubRecordCommitsThePreviewProgrammeAndPinsInfo() = recordFromScrub(scheduled = true)

    @Test fun liveScrubRecordSchedulesThePreviewProgramme() = recordFromScrub(scheduled = false)

    private fun recordFromScrub(scheduled: Boolean) = timeshiftPlaying(mapPosition = true) {
        val now = System.currentTimeMillis() / 1_000L
        val previewProgramme = EpgEvent.create(EventId(21), ChannelId(1), Instant.fromEpochSeconds(now - 1800),
            Instant.fromEpochSeconds(now + 5), title = "Preview programme B")
        val playbackProgramme = EpgEvent.create(EventId(22), ChannelId(1), Instant.fromEpochSeconds(now + 5),
            Instant.fromEpochSeconds(now + 3600), title = "Playback programme A")
        session.publish(observation(now, listOf(previewProgramme, playbackProgramme), recordings = if (scheduled) listOf(
            DvrEntry.create(DvrEntryId(7), eventId = previewProgramme.id, state = DvrEntryState.SCHEDULED)) else emptyList()))
        runTest(subscriptionDispatcher) {
            timeshiftConnection.emit(SubscriptionEvent.Timeshift(61_000_000L, 0L, 0L, 61_000_000L,
                null, Instant.fromEpochSeconds(now + 11)))
        }
        compose.settleFrames(20, frameMillis = 16)
        settleFrames()
        assertTrue("playback starts in A: ${description("player-info-bar")}", description("player-info-bar").contains(requireNotNull(playbackProgramme.title)))
        key(Key.MediaPause)
        repeat(5) { compose.pressKey(Key.MediaRewind, settleFrames = 2, frameMillis = 16) }
        compose.settleFrames(16, frameMillis = 16)
        assertTrue("the scrub banner shows B: ${description("player-info-bar")}", description("player-info-bar").contains(requireNotNull(previewProgramme.title)))
        val requests = timeshiftConnection.seekTargets.size
        key(Key.MediaRecord)
        settleFrames()
        assertTrue("Record commits the scrub", timeshiftConnection.seekTargets.size > requests)
        val committed = timeshiftConnection.seekTargets.last() as SubscriptionSeekTarget.Absolute
        assertTrue("Record seeks into B", committed.position.inWholeMilliseconds in 1_000L..55_000L)
        if (scheduled) {
            assertEquals(requireNotNull(previewProgramme.title), texts("details-title"))
            compose.onNodeWithTag("details-cancel").assertIsFocused()
            key(Key.DirectionCenter)
            compose.onNode(hasContentDescription(requireNotNull(previewProgramme.title), substring = true)).assertExists()
            compose.onNode(hasContentDescription(requireNotNull(playbackProgramme.title), substring = true)).assertDoesNotExist()
            key(Key.Back)
            key(Key.Back)
            settleFrames()
            val currentProgramme = EpgEvent.create(EventId(23), ChannelId(1), Instant.fromEpochSeconds(now - 3600),
                Instant.fromEpochSeconds(now + 3600), title = "Current programme C")
            session.publish(observation(now, listOf(currentProgramme)))
            settleFrames()
            key(Key.Info)
            assertEquals("reopening Info does not retain the scrub's pin",
                requireNotNull(currentProgramme.title), texts("details-title"))
        } else {
            assertEquals("Record schedules the preview programme", DvrSchedule.Programme(previewProgramme.id), scheduleRequests.single().schedule)
            assertFalse(exists("details-title"))
        }
    }

    @Test fun focusedLiveSeekbarTakesOverTheScrubWithThirtySecondSteps() = timeshiftPlaying(mapPosition = true) {
        val now = System.currentTimeMillis() / 1_000L
        val programme = EpgEvent.create(EventId(21), ChannelId(1), Instant.fromEpochSeconds(now - 60),
            Instant.fromEpochSeconds(now + 40), title = "Buffered programme")
        session.publish(observation(now, listOf(programme)))
        runTest(subscriptionDispatcher) {
            timeshiftConnection.emit(SubscriptionEvent.Timeshift(61_000_000L, 0L, 0L, 61_000_000L,
                null, Instant.fromEpochSeconds(now + 1)))
        }
        compose.settleFrames(20, frameMillis = 16)
        settleFrames()
        key(Key.MediaPause)
        compose.pressKey(Key.DirectionDown, settleFrames = 2, frameMillis = 16)
        compose.onNodeWithTag("player-seekbar").requestFocus()
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.pressKey(Key.MediaRewind, settleFrames = 2, frameMillis = 16)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        val target = compose.onNodeWithTag("player-seekbar").fetchSemanticsNode().config[
            SemanticsProperties.ProgressBarRangeInfo].current * 100_000L
        compose.pressKey(Key.DirectionLeft, settleFrames = 2, frameMillis = 16)
        compose.settleFrames(12, frameMillis = 16)
        assertFalse("the automatic scrub ends", description("player-seekbar").contains(
            context.getString(R.string.player_scrub_backward, 2)))
        compose.settleFrames(14, frameMillis = 16)
        settleFrames()
        val stepped = (timeshiftConnection.seekTargets.last() as SubscriptionSeekTarget.Absolute).position.inWholeMilliseconds
        // The bar animates behind the automatic target; distinguish its 30 s step from the hidden 10 s step.
        assertEquals("the focused live bar uses a symmetric thirty-second step",
            (target - 30_000L).toDouble(), stepped.toDouble(), 2_000.0)
    }

    @Test fun mediaPauseUsesOnlyTheBannerAndScrubLeavesPlaybackUntilBackCancels() = timeshiftPlaying(mapPosition = true) { pauses ->
        key(Key.MediaPlay)
        assertFalse("a hidden Play key toggles like OK", pauses.last())
        assertTrue(exists("player-banner"))
        assertFalse(exists("player-actions"))
        repeat(2) { compose.pressKey(Key.DirectionLeft, settleFrames = 1, frameMillis = 16) }
        assertTrue("hidden back presses accumulate ten seconds each",
            compose.onAllNodesWithText("−0:20", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        key(Key.Back)
        key(Key.MediaRewind)
        settleFrames()
        assertTrue(description("timeshift-seek-preview").contains(context.getString(R.string.player_scrub_backward, 2)))
        assertFalse("previewing does not resume paused playback", pauses.last())
        key(Key.Back)
        assertFalse(pauses.last())
        assertFalse(exists("timeshift-seek-preview"))
    }

    @Test fun playerEntersWithAPassiveBannerAndDownLandsOnTheControls() {
        screen()
        assertTrue("Banner on entry", exists("player-banner"))
        assertTrue(exists("player-info-bar"))
        assertTrue(exists("player-top-cluster"))
        assertFalse("no actions in the Banner", exists("player-actions"))
        assertEquals("nothing in the Banner is focusable", 0,
            compose.onAllNodes(hasAnyAncestor(hasTestTag("player-banner")) and isFocusable()).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag("player-banner") and isFocusable()).fetchSemanticsNodes().size)

        val playWhenReady = player.playWhenReady
        key(Key.DirectionDown)
        assertFalse("the controls replace the Banner", exists("player-banner"))
        assertTrue(exists("player-actions"))
        assertTrue("controls keep the player slots", exists("player-info-bar"))
        assertTrue(exists("player-top-cluster"))
        assertEquals("the controls open on Play/Pause", listOf("player-pause"), focused())
        assertEquals("Down is consumed: the pause was not toggled", playWhenReady, player.playWhenReady)
        assertEquals("the programme artwork is passive", emptyList<String?>(),
            (compose.onAllNodes(hasAnyAncestor(hasTestTag("player-info-bar")) and isFocusable()).fetchSemanticsNodes() +
                compose.onAllNodes(hasTestTag("player-info-bar") and isFocusable()).fetchSemanticsNodes())
                .map { it.config.getOrNull(SemanticsProperties.TestTag) })
        assertEquals("the top identity is passive", 0,
            compose.onAllNodes(hasTestTag(PlayerTopIdentityTag) and isFocusable()).fetchSemanticsNodes().size)
        assertTrue("the action row exposes Info", exists("player-info"))
        assertEquals("the header clock is never focusable", 0,
            compose.onAllNodes(hasAnyAncestor(hasTestTag("player-top-cluster")) and isFocusable()).fetchSemanticsNodes().size)
        assertTrue("the header keeps only the clock, no status chip: ${texts("player-header")}",
            texts("player-header").matches(Regex("\\d{1,2}:\\d{2}( [AP]M)?")))
    }

    @Test fun theAudioButtonStandsBesideInfoOnlyWithAChoiceAndReturnsFocusFromItsList() = timeshiftPlaying {
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), subtitleTrack("s1", "de"))))
        key(Key.DirectionDown)
        assertTrue(exists("player-info"))
        assertFalse("one audio track offers no choice", exists("player-audio"))
        assertFalse(exists("player-subtitles"))
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), audioTrack("a2", "de", false), subtitleTrack("s1", "de"))))
        assertTrue(exists("player-audio"))
        assertFalse(exists("player-subtitles"))
        val button = compose.onNodeWithTag("player-audio").fetchSemanticsNode()
        assertEquals(listOf("Audio"), button.config.getOrNull(SemanticsProperties.ContentDescription))
        assertTrue("it announces the current choice: ${button.config}",
            button.config.getOrNull(SemanticsProperties.StateDescription).orEmpty().isNotEmpty())
        val audioBounds = button.boundsInRoot
        val info = compose.onNodeWithTag("player-info").fetchSemanticsNode().boundsInRoot
        assertTrue("Audio stands before Info at the end: $audioBounds vs $info", audioBounds.right <= info.left)
        // Left from Info reaches it; it then opens the Audio list, and Back returns to it.
        compose.onNodeWithTag("player-info").performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        key(Key.DirectionLeft)
        assertEquals(listOf("player-audio"), focused())
        key(Key.DirectionCenter)
        assertTrue("the Audio quick list opens", exists("playback-options-track-list"))
        assertFalse("focus moved into the quick list", focused() == listOf("player-audio"))
        key(Key.Back)
        assertEquals(listOf("player-audio"), focused())
        // OK on the checked row keeps it, closes the list and returns to the button.
        key(Key.DirectionCenter)
        assertTrue("the Audio list opens again", exists("playback-options-track-list"))
        key(Key.DirectionCenter)
        assertFalse("OK closed the list", exists("playback-options-track-list"))
        assertEquals(listOf("player-audio"), focused())
        assertTrue(exists("player-stop"))
        compose.onNodeWithTag("player-stop").performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        assertEquals(listOf("player-stop"), focused())
        key(Key.MediaAudioTrack)
        assertFalse("focus moved into the quick list: ${focused()}", focused() == listOf("player-stop"))
        key(Key.Back)
        assertEquals(listOf("player-stop"), focused())
    }

    @Test fun theSubtitlesKeyOnTheAudioPickerReturnsFocusToTheAudioButton() = timeshiftPlaying {
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), audioTrack("a2", "de", false), subtitleTrack("s1", "de"))))
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-audio").performSemanticsAction(SemanticsActions.RequestFocus)
        settleFrames()
        assertEquals(listOf("player-audio"), focused())
        key(Key.DirectionCenter)
        assertTrue("the Audio picker opens", exists("playback-options-track-list"))
        key(Key.Captions)
        assertTrue("the Subtitles list replaced it", exists("playback-options-track-list"))
        key(Key.Back)
        assertFalse(exists("playback-options-track-list"))
        assertEquals(listOf("player-audio"), focused())
    }

    @Test fun aFocusedAudioButtonThatGoesHandsFocusToInfo() = timeshiftPlaying {
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), audioTrack("a2", "de", false))))
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-audio").performSemanticsAction(SemanticsActions.RequestFocus)
        settleFrames()
        assertEquals(listOf("player-audio"), focused())
        // The choice shrinks to one audio track.
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true))))
        assertFalse(exists("player-audio"))
        assertEquals("Audio's neighbour takes focus", listOf("player-info"), focused())
    }

    @Test fun backFromTheRailReturnsToTheAudioOrInfoButtonItWasEnteredFrom() = timeshiftPlaying {
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), audioTrack("a2", "de", false))))
        key(Key.DirectionDown)
        for (button in listOf("player-audio", "player-info")) {
            compose.onNodeWithTag(button).performSemanticsAction(SemanticsActions.RequestFocus)
            settleFrames()
            key(Key.DirectionDown)
            assertTrue("Down from $button opens the rail", railOpen())
            key(Key.Back)
            assertFalse(railOpen())
            assertEquals(listOf(button), focused())
        }
    }

    @Test fun numberEntryKeepsTheControlsUpWithoutFocusAndReturnsToTheSeekbar() = timeshiftPlaying { pauses ->
        key(Key.DirectionDown)
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        val controls = setOf("player-seekbar", "player-pause", "player-stop", "player-info", "player-settings")
        // Back cancels the entry; an unknown number's hold times out. Both clear it the same way.
        for (cancel in listOf(true, false)) {
            // Channels 1 to 3: one digit is a complete number, and 9 names none.
            press(Key.Nine)
            assertEquals("the digit tuned nothing", AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            for (direction in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
                press(direction)
                assertTrue("$direction moved focus into the controls: ${focused()}", focused().none { it in controls })
                assertTrue("the controls stay up under the entry", exists("player-actions"))
                assertFalse("$direction opened the channel rail", exists("player-now-playing"))
            }
            // Nor can an accessibility service move focus into them.
            for (tag in controls) {
                compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().forEach {
                    it.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
                }
                settleFrames()
                assertTrue("$tag took focus under the entry: ${focused()}", focused().none { it in controls })
            }
            assertEquals("an accessibility service finds nothing to act on", emptyList<String>(), actionable())
            assertTrue("the entry is still up", compose.onAllNodesWithText("9").fetchSemanticsNodes().isNotEmpty())
            assertEquals("no control acted", emptyList<Boolean>(), pauses)
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            if (cancel) key(Key.Back) else repeat(2) { settle() }
            assertTrue("the entry left", compose.onAllNodesWithText("9").fetchSemanticsNodes().isEmpty())
            assertEquals("focus returned to the timeline (cancel=$cancel)", listOf("player-seekbar"), focused())
            assertTrue("the actions are back: ${actionable()}", actionable().containsAll(controls - "player-seekbar"))
        }
    }

    @Test fun playerBannerStaysWhileTheChannelIsTuning() {
        screen()
        // Well past the Banner's 5 s: no frame was presented, so it stays while tuning.
        repeat(8) { settle() }
        assertTrue(exists("player-banner"))
        assertEquals("tuning never replaces the playback glyph", "Playing", description("player-state"))
        assertEquals("Tuning", description("player-busy-indicator"))
        assertEquals("a tune lands at live: the state is announced", "Live", liveState())
        assertFalse("and no distance is drawn at the live edge", exists("player-distance"))
        assertFalse(exists("player-hidden-slot"))
        assertFalse("the bar shows no animation while tuning", exists("player-timeline-sweep"))
        assertTrue("the bar shows the new channel's schedule at now while tuning", exists("player-timeline-fill"))
        val ring = compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(compose.onRoot().fetchSemanticsNode().boundsInRoot.center, ring.boundsInRoot.center)
        key(Key.DirectionDown)
        assertFalse(exists("player-pause-busy"))
        assertEquals(ring.id, compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode().id)
        key(Key.Back)
        assertFalse(exists("player-hidden-slot"))
        assertEquals(ring.boundsInRoot, compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
    }

    @Test fun aPresentedTunesStallUsesBufferingAndResumingRemovesTheOverlay() = timeshiftPlaying {
        settle()
        assertFalse("a presented tune is not busy", exists("player-busy-indicator"))
        forcedReady = false
        compose.runOnIdle {
            playerListeners.toList().forEach {
                it.onPlaybackStateChanged(Player.STATE_BUFFERING)
                it.onIsPlayingChanged(false)
            }
        }
        assertMainSettled("the driven player reports Buffering") { runtime.state.value is AppPlaybackState.Buffering }
        settle()
        assertEquals("a stall after a presented tune is not another tune", "Buffering", description("player-busy-indicator"))
        val ring = compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode()
        key(Key.DirectionDown)
        assertTrue(exists("player-actions"))
        assertFalse(exists("player-pause-busy"))
        assertEquals(ring.id, compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode().id)
        key(Key.Back)
        assertFalse(exists("player-hidden-slot"))
        assertEquals(ring.boundsInRoot, compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        playerReady()
        assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
        settle()
        assertFalse("resuming removes the ring", exists("player-busy-indicator"))
    }

    @Test fun backWhilePausedLeavesOnlyTheChipAtTheBottomStart() {
        var closes = 0
        timeshiftPlaying(onClose = { closes++ }) { pauses ->
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            assertSubscriptionSettled("the pause command reaches the player") { false in pauses }
            settle()
            assertEquals("the state cell shows the real state", "Paused", description("player-state"))
            assertFalse("no status slot in the info bar", exists("player-status-slot"))
            key(Key.Back)
            assertFalse(exists("player-banner"))
            assertFalse(exists("player-actions"))
            assertFalse("no header either", exists("player-header"))
            assertTrue("only the chip stays", exists("player-hidden-slot"))
            assertFalse("intentional pause is never busy", exists("player-busy-indicator"))
            assertEquals("Paused", description("player-hidden-slot"))
            val chip = compose.onNodeWithTag("player-hidden-slot", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("at the bottom start: $chip in $root", chip.left < root.right * 0.1f && chip.top > root.bottom * 0.75f)
            assertEquals("nothing hidden is focusable", 0,
                compose.onAllNodes(hasAnyAncestor(hasTestTag("player-hidden-slot")) and isFocusable()).fetchSemanticsNodes().size)
            holdBack()
            assertEquals(1, closes)
            assertEquals(null, runtime.activeTarget.value)
        }
    }

    @Test fun atTheLiveEdgeTheBarRowIsPassiveAndUpStaysOnTheTimeline() {
        var behindLiveMs by mutableStateOf(0L)
        var state by mutableStateOf(PlayerStateCell.PLAYING)
        liveChrome(behindLiveMs = { behindLiveMs }, state = { state })
        for ((distance, playback) in listOf(0L to PlayerStateCell.PLAYING,
            203_000L to PlayerStateCell.PLAYING, 0L to PlayerStateCell.PAUSED)) {
            compose.runOnIdle { behindLiveMs = distance; state = playback }
            settle()
            assertEquals(listOf("player-pause"), focused())
            key(Key.DirectionUp)
            assertEquals(listOf("player-seekbar"), focused())
            key(Key.DirectionUp)
            assertEquals("artwork is passive", listOf("player-seekbar"), focused())
            key(Key.DirectionDown)
            assertEquals("Down returns to the action row", listOf("player-pause"), focused())
            assertFalse(exists("player-go-live"))
            assertFalse(exists("player-status-slot"))
            assertFalse("the controls have no state cell; the Pause button speaks for playback", exists("player-state"))
            assertFalse(exists("player-go-live-label"))
            if (distance == 0L) {
                assertEquals("Live", description("player-live-state"))
                assertFalse("nothing is drawn at the live edge", exists("player-distance"))
            } else {
                assertEquals("Behind live", description("player-live-state"))
                assertEquals("3:23 behind live", texts("player-distance"))
            }
            if (playback == PlayerStateCell.PAUSED) {
                compose.onNodeWithTag("player-pause").assertContentDescriptionEquals("Play")
            }
            assertEquals(0, compose.onAllNodes(hasTestTag("player-live-state") and isFocusable(), useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    @Test fun numberEntryLeavesTheCoveredControlsNoAccessibilityActions() {
        var entry by mutableStateOf(false)
        liveChrome(behindLiveMs = { 60_000L }, state = { PlayerStateCell.PAUSED }, numberEntryShown = { entry })
        val seekActions = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and hasTestTag("player-seekbar")
        assertTrue("seekable: the timeline offers its seek actions", compose.onAllNodes(seekActions).fetchSemanticsNodes().isNotEmpty())
        val offered = actionable()
        compose.runOnIdle { entry = true }
        settle()
        assertEquals("covered, the controls offer nothing to act on", emptyList<String>(), actionable())
        assertFalse("nor can their seek actions be reached", exists("player-seekbar") &&
            compose.onAllNodes(seekActions).fetchSemanticsNodes().isNotEmpty())
        compose.runOnIdle { entry = false }
        settle()
        assertEquals("the same actions return", offered, actionable())
    }

    @Test fun pauseWithTimeshiftOffPostsOneNoticeWithoutTimelineFeedback() {
        val data = InMemoryPreferencesDataStore()
        runTest { PlayerSettingsStore(data).setTimeshiftEnabled(false) }
        screen(settingsData = data)
        assertMainSettled("in-memory settings disable pause") { runtime.livePause.value.availability == LivePauseAvailability.OFF }
        val playing = player.playWhenReady
        for (mediaKey in listOf(Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause, Key.MediaFastForward, Key.MediaRewind)) {
            compose.pressKey(mediaKey, settleFrames = 3, frameMillis = 16)
            assertTrue(exists("player-banner"))
            assertFalse(exists("player-actions"))
            assertEquals(playing, player.playWhenReady)
        }
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
        repeat(3) { key(Key.DirectionCenter) }
        key(Key.MediaPause)
        assertEquals(Notice.PauseUnavailable(PauseUnavailableReason.SETTINGS_OFF),
            notices.state.value.pending.single().notice)
        assertFalse(exists("player-window-title"))
        assertFalse(exists("player-seek-feedback"))
        compose.onNodeWithTag("player-pause").assertIsFocused()
    }

    @Test fun pauseOnAnUnpausableChannelPostsTheChannelNoticeWithoutTimelineFeedback() {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0L)))
        }
        val manager = createSubscriptionManager(connection, subscriptionDispatcher).apply { startAdmission() }
        try {
            screen(opener = manager)
            startLiveSubscription(connection, height = 1080L, signal = 53739L)
            playerReady()
            // Media3's ExoPlayer playback thread binds the grant after collection registration.
            compose.waitUntil(5_000) { idleMainLooper(); runtime.livePause.value.availability == LivePauseAvailability.UNAVAILABLE }
            key(Key.DirectionDown)
            compose.onNodeWithTag("player-pause").assertIsFocused()
            key(Key.DirectionCenter)
            key(Key.MediaPause)
            assertEquals(Notice.PauseUnavailable(PauseUnavailableReason.CHANNEL_UNAVAILABLE),
                notices.state.value.pending.single().notice)
            assertFalse(exists("player-window-title"))
            assertFalse(exists("player-seek-feedback"))
        } finally {
            runTest(subscriptionDispatcher) { manager.closeAndJoin() }
        }
    }

    @Test fun playerBannerHidesFiveSecondsAfterThisTunesFirstFrame() {
        screen()
        playerReady()
        assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
        // Playing without a picture: no video diagnostics does not prove audio-only.
        repeat(4) { settle() }
        assertTrue("no frame of this tune yet: the Banner stays", exists("player-banner"))
        assertEquals("Playing without a frame is still tuning", "Tuning", description("player-busy-indicator"))

        playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        assertMainSettled("the driven frame is presented") { runtime.videoPresentation.value.visible }
        compose.mainClock.advanceTimeBy(4_800)
        compose.waitForIdle()
        assertTrue("before 5000 ms after the first frame", exists("player-banner"))
        assertFalse("the first frame removes tuning", exists("player-busy-indicator"))
        settle()
        assertFalse("5000 ms after the first frame the Banner hides", exists("player-banner"))
        assertFalse(exists("player-actions"))
    }

    @Test fun playerAudioOnlyServiceStartsTheBannerTimerWhenPlaying() {
        screen()
        val audioOnly = Tracks(listOf(Tracks.Group(
            TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
            false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true),
        )))
        assertTrue(tracksAudioOnly(audioOnly))
        forcedTracks = audioOnly
        playerListeners.toList().forEach { it.onTracksChanged(audioOnly) }
        playerReady()
        assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        assertTrue(exists("player-banner"))
        repeat(2) { settle() }
        assertFalse("audio-only: playing starts the Banner's 5 s", exists("player-banner"))
        assertFalse("audio-only presentation removes tuning", exists("player-busy-indicator"))
    }

    @Test fun aStoppedAndRestartedScreenShowsNoStaleBanner() = timeshiftPlaying(mapPosition = true) { pauses ->
        key(Key.MediaPause)
        key(Key.MediaRewind)
        assertTrue(exists("player-banner"))
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        settle()
        assertFalse("the Banner does not outlive the stop", exists("player-banner"))
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        repeat(8) { settle() }
        assertFalse("no stale Banner on return", exists("player-banner"))
        assertFalse(exists("player-actions"))
        assertFalse("stopping preserves pause", pauses.last())
        assertFalse("no pending scrub on return", exists("timeshift-seek-preview"))
    }

    @Test fun theLiveChromeShowsNoStreamBadgesAndStartsNoDiagnostics() {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, null)))
        }
        val manager = createSubscriptionManager(connection, subscriptionDispatcher).apply { startAdmission() }
        try {
            screen(opener = manager)
            val tracks = Tracks(listOf(
                Tracks.Group(TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
                    .setWidth(1920).setHeight(1080).build()), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
                Tracks.Group(TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3)
                    .setChannelCount(6).build()), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
            ))
            forcedTracks = tracks
            compose.runOnIdle { playerListeners.toList().forEach { it.onTracksChanged(tracks) } }
            // Media3's real ExoPlayer playback thread registers the media period's collection.
            val registered = scope.async(start = CoroutineStart.UNDISPATCHED) { connection.awaitCollectionRegistered() }
            compose.waitUntil(5_000) { idleMainLooper(); registered.isCompleted }
            runTest(subscriptionDispatcher) {
                connection.emit(SubscriptionEvent.Started(listOf(
                    SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                        language = null, compositionId = null, ancillaryId = null, width = 1920, height = 1080,
                        frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                        audioVersion = null, channelCount = null, rate = null, rdsUecp = null),
                ), null))
                // 53739 of 65535: 82 %.
                connection.emit(SubscriptionEvent.Signal(53739, 12300, null, null, 0, 0, false))
            }
            val diagnostics = runtime.diagnostics.value
            assertTrue("Stats is closed: no format or frontend diagnostics",
                diagnostics.video == null && diagnostics.audio == null && diagnostics.live == null)
            playerReady()
            assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
            settle()
            assertTrue(exists("player-banner"))
            val badges = badgeDescription("player-banner")
            for (absent in listOf("lines", "Video", "Audio", "Signal-to-noise")) {
                assertFalse("Banner '$badges' shows no badge ('$absent')", badges.contains(absent))
            }

            // Without live Pause, Center posts a notice; Down opens the controls.
            key(Key.DirectionCenter)
            assertFalse(exists("player-actions"))
            assertTrue(exists("player-banner"))
            key(Key.DirectionDown)
            assertTrue(exists("player-actions"))
            val controls = identityDescription() + " | " + badgeDescription("player-footer")
            for (absent in listOf("lines", "Video", "Audio")) {
                assertFalse("controls '$controls' show no badge ('$absent')", controls.contains(absent))
            }

            // Programme Info never starts full diagnostics. Stats for nerds still owns them.
            key(Key.Info)
            assertTrue(exists("programme-details-page"))
            assertTrue(runtime.diagnostics.value.let { it.video == null && it.audio == null && it.live == null })
            key(Key.Back)
            compose.onNodeWithTag("player-settings").performSemanticsAction(SemanticsActions.RequestFocus)
            key(Key.DirectionCenter)
            compose.onNodeWithTag("playback-options-stats").performSemanticsAction(SemanticsActions.RequestFocus)
            key(Key.DirectionCenter)
            key(Key.DirectionCenter)
            // Media3's ExoPlayer playback thread publishes the bound subscription's diagnostics.
            compose.waitUntil(5_000) { idleMainLooper(); runtime.diagnostics.value.live != null }
            key(Key.Back)
            key(Key.Back)
            assertTrue(exists("player-actions"))
            key(Key.Back)
            assertTrue(exists("playback-stats-overlay"))
            key(Key.Back)
            assertSubscriptionSettled("closing Stats clears live diagnostics") { runtime.diagnostics.value.live == null }
            assertFalse(exists("playback-stats-overlay"))
        } finally {
            runTest(subscriptionDispatcher) { manager.closeAndJoin() }
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aScreenZapKeepsTheNewProgrammeFromSelectionThroughKnownTiming() = screenZap()

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aScreenPauseDuringTuneKeepsTheBarAndTagWhenConfirmedWithoutTiming() = screenZap(pauseDuringTune = true)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aScreenReturnToTheSameChannelStartsANewLiveRequest() = screenZap(returnToSameChannel = true)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aZapFromAServerPausedChannelKeepsTheNewProgrammesLiveStart() = screenZap(pauseBeforeZap = true)

    @Test fun firstEntryKeepsTheLiveProgrammeOnEveryFrameUntilTimingIsKnown() = liveStartFrames(zap = false)

    @Test fun aZapKeepsTheNewLiveProgrammeOnEveryFrameUntilTimingIsKnown() = liveStartFrames(zap = true)

    @Test fun aZapFromPauseKeepsTheNewLiveProgrammeOnEveryFrameUntilTimingIsKnown() =
        liveStartFrames(zap = true, pauseBeforeZap = true)

    @Test fun okOnTheBannerPausesAndKeepsTheBannerWithoutTheControlsUntilResumed() = timeshiftPlaying { pauses ->
        // Android TV playback guidance (TV-PC): Center pauses and resumes; it shows progress, not the actions.
        assertTrue(exists("player-banner"))

        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertSubscriptionSettled("the pause command reaches the player") { false in pauses }
        settle()
        assertTrue("pausing keeps the Banner", exists("player-banner"))
        assertFalse("pausing opens no actions", exists("player-actions"))
        // Well past the Banner's timeout: paused, it stays until Back or resume.
        repeat(3) { settle(); compose.mainClock.advanceTimeBy(4_000); compose.waitForIdle() }
        assertTrue("paused: the Banner stays", exists("player-banner"))

        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertSubscriptionSettled("the resume command reaches the player") { pauses.lastOrNull() == true }
        assertFalse("resuming opens no actions either", exists("player-actions"))
    }

    @Test fun playerUnavailableClaimsNeitherPlayingNorLiveInTheControlsNorTheChip() {
        screen(failing = true)
        repeat(4) { settle() }
        assertTrue("centre message", exists("player-channel-unavailable"))
        assertFalse("unavailable replaces busy", exists("player-busy-indicator"))
        assertFalse("the Banner gives way to the centre message", exists("player-banner"))
        assertFalse(exists("player-hidden-slot"))
        assertFalse(exists("player-status-slot"))
        key(Key.DirectionDown)
        assertTrue("the controls open over the message", exists("player-actions"))
        assertFalse(exists("player-state"))
        assertFalse(exists("player-live-state"))
        key(Key.Back)
        assertFalse(exists("player-hidden-slot"))
        assertFalse(exists("player-state"))
    }
}
