package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.SemanticsProperties
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi",
    instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseContextMenuScreenTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private lateinit var back: OnBackPressedDispatcher
    private lateinit var view: View
    private var layering: at.bernhardberger.tvhplayer.ui.components.BrowseContentLayering? = null
    private var plays = 0
    private val artworkRequests = java.util.Collections.synchronizedList(mutableListOf<at.bernhardberger.tvhplayer.core.AppArtworkSource>())
    private var guideRequest: ChannelId? = null
    private var guideProgramme: EpgEvent? = null
    private var start: RecordingPlaybackStart? = null
    private var watchedChannel: ChannelId? = null
    private var playedChannel: ChannelId? = null
    private var goLives = 0
    private val playerEntry = at.bernhardberger.tvhplayer.ui.PlayerEntryHandoff()

    /** Starts a player request that stays pending, as while the player is still opening. */
    private fun requestPlayer() {
        playerEntry.request(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)) {
            kotlinx.coroutines.awaitCancellation()
        }
    }

    @Test fun channelMenuShowsCurrentProgrammeArtwork() = menuArtwork(recordings = false, artwork = true)
    @Test fun channelMenuWithoutArtworkShowsPicon() = menuArtwork(recordings = false, artwork = false)
    @Test fun channelMenuWithoutProgrammeShowsPicon() = menuArtwork(recordings = false, artwork = false, noProgramme = true)
    @Test fun guideMenuShowsProgrammeArtwork() = menuArtwork(recordings = false, artwork = true, guideMenu = true)
    @Test fun recordingMenuShowsRecordingArtwork() = menuArtwork(recordings = true, artwork = true)
    @Test fun recordingMenuWithoutArtworkShowsPicon() = menuArtwork(recordings = true, artwork = false)
    @Test fun recordingMenuUsesFanartWhenImageIsAbsent() = menuArtwork(recordings = true, artwork = false, fanartOnly = true)

    private fun menuArtwork(recordings: Boolean, artwork: Boolean, guideMenu: Boolean = false,
        noProgramme: Boolean = false, fanartOnly: Boolean = false) {
        show(recordings, guideMenu = guideMenu, artwork = artwork, noProgramme = noProgramme,
            imageProbe = true, fanartOnly = fanartOnly)
        if (!guideMenu) compose.onNodeWithTag(if (recordings) "recording-list-entry-1" else "channel-row-1")
            .requestFocus().performKeyInput { pressKey(Key.Menu) }
        val hasArtwork = artwork || fanartOnly
        val tag = if (hasArtwork) "browse-context-artwork" else "browse-context-picon"
        compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onNodeWithTag(if (hasArtwork) "browse-context-picon" else "browse-context-artwork").assertDoesNotExist()
        if (hasArtwork) compose.onNodeWithTag("browse-context-plate").assertIsDisplayed()
        else compose.onNodeWithTag("browse-context-plate").assertDoesNotExist()
        if (recordings || noProgramme) compose.onNodeWithTag("browse-context-progress").assertDoesNotExist()
        if (noProgramme) compose.onNodeWithText("Watch channel").assertIsFocused()
        assertMoreOptionsGeometry()
        compose.mainClock.advanceTimeBy(500)
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        assertEquals("Authenticated image result at menu centre",
            if (hasArtwork) android.graphics.Color.RED else android.graphics.Color.CYAN,
            bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt()))
        bitmap.recycle()
        if (noProgramme) capture("channel-only")
        else if (!hasArtwork && !recordings) capture("channels-picon")
    }

    @Test fun channelMenuShowsChannelAndTimeBelowProgramme() {
        show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        val inMenu = hasAnyAncestor(hasTestTag("browse-context-menu"))
        compose.onNodeWithTag("browse-context-menu").assert(
            SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.IsDialog))
        val menuBounds = compose.onNodeWithTag("browse-context-menu").getUnclippedBoundsInRoot()
        assertEquals(960f, (menuBounds.right - menuBounds.left).value, 1f)
        val subtitle = compose.onNode(hasText("101 Ridge Earth HD • On now • until ", substring = true) and inMenu)
        val programme = compose.onNode(hasText("Ridge Light") and inMenu)
        subtitle.assertExists()
        assertTrue(programme.getUnclippedBoundsInRoot().bottom <= subtitle.getUnclippedBoundsInRoot().top)
        assertMoreOptionsGeometry()
        capture("channels-header", "u15-menu-headers")
    }

    @Test fun relatedFocusDoesNotReplaceDetailsOrItsRecordTarget() {
        show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-3").assertIsFocused()
        compose.onNodeWithTag("programme-details-page").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.PaneTitle, "Ridge Light"))
        compose.onNodeWithTag("details-card-3").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-record").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        compose.onNodeWithTag("details-record").assertIsFocused()
    }

    @Test fun airingHoldAndMenuConsumeReleaseAndRestoreTheSameCardWithoutScrolling() {
        show(recordings = false, realRail = true)
        openAirings()
        val card = compose.onNodeWithTag("details-card-4")
        compose.onNodeWithTag("details-card-3").performKeyInput { pressKey(Key.DirectionRight) }
        card.assertIsFocused()
        val scroll = detailsScroll()
        val bounds = card.fetchSemanticsNode().boundsInRoot
        card.performKeyPress(activation(android.view.KeyEvent.ACTION_DOWN))
        card.performKeyPress(activation(android.view.KeyEvent.ACTION_DOWN, 1))
        compose.onNodeWithText("Watch channel").assertIsFocused()
        listOf("Program details", "Record", "Show in Guide").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNode(hasText("100 Ridge Earth SD • ", substring = true) and hasAnyAncestor(hasTestTag("browse-context-menu"))).assertExists()
        compose.onNodeWithTag("browse-context-artwork").assertExists()
        compose.onNodeWithTag("nav-channels").assertDoesNotExist()
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        compose.onNodeWithText("Watch channel").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        card.assertIsFocused()
        assertEquals(scroll, detailsScroll())
        assertEquals(bounds, card.fetchSemanticsNode().boundsInRoot)
        card.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Watch channel").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        card.assertIsFocused()
        assertEquals(scroll, detailsScroll())
    }

    @Test fun airingMenuDetailsPushesTheAiringAndGuideActsOnThatPage() {
        show(recordings = false)
        openAirings()
        compose.onNodeWithTag("details-card-3").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-card-4").performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details", substring = false).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-show_guide").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(EventId(4), guideProgramme?.id)
        assertEquals(ChannelId(2), guideRequest)
    }

    @Test fun airingMenuDetailsBackReturnsToHostingCardWithoutMenuOrBrowseFlashEveryFrame() {
        show(recordings = false, realRail = true)
        val browse = resumeBitmap()
        var x = 0; var y = 0; var brightest = 0
        for (px in 212 until 900) for (py in 20 until 220) {
            val light = android.graphics.Color.red(browse.getPixel(px, py))
            if (light > brightest) { brightest = light; x = px; y = py }
        }
        browse.recycle()
        assertTrue("Rendered browse heading probe", brightest > 100)
        openAirings()
        compose.onNodeWithTag("details-card-3").performKeyInput { pressKey(Key.DirectionRight) }
        val card = compose.onNodeWithTag("details-card-4")
        val scroll = detailsScroll()
        card.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details", substring = false).requestFocus().assertIsFocused()
        compose.mainClock.autoAdvance = false
        fun assertCoveredFrames(returning: Boolean = false) {
            repeat(24) { frame ->
                compose.mainClock.advanceTimeByFrame()
                if (returning) compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
                val light = probeLight(x, y)
                // Full-screen art under the scrim reaches about .45 of the heading here; the heading
                // itself showing through would stay near it.
                assertTrue("Hosting details covers browse on frame $frame: $light/$brightest", light < brightest * .6f)
            }
        }
        try {
            compose.onNodeWithText("Program details", substring = false).performKeyInput { pressKey(Key.DirectionCenter) }
            assertCoveredFrames()
            compose.onNodeWithTag("details-watch").assertIsFocused()
            compose.runOnIdle { back.onBackPressed() }
            assertCoveredFrames(returning = true)
            card.assertIsFocused()
            assertEquals(scroll, detailsScroll())
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertEquals(0, plays)
    }

    @Test fun airingMenuRecordUsesTheAiringAndReturnsToItsCard() = airingMenuRecordReturnsToCard()
    private fun airingMenuRecordReturnsToCard() {
        val session = show(recordings = false)
        openAirings()
        compose.onNodeWithTag("details-card-3").performKeyInput { pressKey(Key.DirectionRight) }
        val card = compose.onNodeWithTag("details-card-4")
        val scroll = detailsScroll()
        card.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Record", substring = false).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        card.assertIsFocused()
        assertEquals(scroll, detailsScroll())
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
    }

    private fun openAirings() {
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-3").assertIsFocused()
    }

    private fun detailsScroll(): Float = compose.onNodeWithTag("details-page-scroll", useUnmergedTree = true)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun guideMenuShowsChannelAndTimeBelowProgrammeTitle() {
        show(recordings = false, guideMenu = true)
        val title = compose.onNodeWithText("Ridge Light")
        val channel = compose.onNodeWithText("101 Ridge Earth HD • On now • until ", substring = true)
        assertTrue(title.getUnclippedBoundsInRoot().bottom <= channel.getUnclippedBoundsInRoot().top)
        // The same order as the Channels menu: Watch first.
        compose.onNodeWithText("Watch").assertIsFocused()
        assertMoreOptionsGeometry()
        capture("guide-header", "u15-menu-headers")
    }

    @Test fun runningGuideMenuShowsWatchAndEmbeddedArtworkProgress() {
        show(recordings = false, guideMenu = true)
        compose.onNodeWithText("Watch", substring = false).assertExists()
        compose.onNodeWithText("Watch channel").assertDoesNotExist()
        compose.onNodeWithText("On now • until ", substring = true).assertExists()
        compose.onNodeWithTag("browse-context-progress").assertIsDisplayed().assertHeightIsEqualTo(3.dp)
        compose.onNodeWithTag("browse-context-artwork").assertWidthIsEqualTo(268.dp)
        compose.onNodeWithTag("browse-context-plate").assertIsDisplayed()
        assertMoreOptionsGeometry()
    }

    private fun assertMoreOptionsGeometry() {
        val item = compose.onNodeWithTag("browse-context-item")
        val actions = compose.onNodeWithTag("two-column-actions")
        item.assertWidthIsEqualTo(268.dp).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        actions.assertWidthIsEqualTo(268.dp)
        val itemBounds = item.getUnclippedBoundsInRoot()
        val actionBounds = actions.getUnclippedBoundsInRoot()
        assertEquals(130f, itemBounds.left.value, 0.5f)
        assertEquals(562f, actionBounds.left.value, 0.5f)
        assertTwoColumnContainerCentred()
    }

    /** Kit Two Column: both columns share one top, and the taller column sets the container centred on screen. */
    private fun assertTwoColumnContainerCentred() {
        val actions = compose.onNodeWithTag("two-column-actions").getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag("two-column-content").getUnclippedBoundsInRoot()
        assertEquals(content.top.value, actions.top.value, 0.5f)
        assertEquals(270f, (content.top.value + maxOf(content.bottom.value, actions.bottom.value)) / 2f, 0.5f)
    }

    @Test fun programmeDescriptionJoinsEpisodeAndSummaryOnSeparateLines() {
        show(recordings = false, guideMenu = true, episodeSubtitle = "Into the Polar Night",
            summary = "A winter expedition.", description = "Longer description.")
        compose.onNodeWithText("Into the Polar Night\nA winter expedition.").assertIsDisplayed()
        compose.onNodeWithText("Longer description.").assertDoesNotExist()
    }

    @Test fun programmeDescriptionFallsBackWhenSummaryIsBlank() {
        show(recordings = false, guideMenu = true, summary = " ", description = "A winter expedition.")
        compose.onNodeWithText("A winter expedition.").assertIsDisplayed()
    }

    @Test fun recordingDescriptionJoinsEpisodeAndFallbackDescription() {
        show(recordings = true, episodeSubtitle = "Into the Polar Night", summary = null,
            description = "A winter expedition.")
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Into the Polar Night\nA winter expedition.").assertIsDisplayed()
        assertMoreOptionsGeometry()
    }

    @Test fun futureGuideMenuShowsWatchChannelAndNoProgress() {
        show(recordings = false, guideMenu = true, futureProgramme = true)
        compose.onNodeWithText("Watch channel").assertExists()
        compose.onNodeWithText("Watch", substring = false).assertDoesNotExist()
        compose.onNodeWithText("On now • until ", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("browse-context-progress").assertDoesNotExist()
        compose.onNodeWithTag("browse-context-plate").assertIsDisplayed()
        capture("guide-future", "u15-context")
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun runningGuideMenuUsesGermanTimeAndWatchLabels() {
        show(recordings = false, guideMenu = true)
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        compose.onNodeWithText(resources.getString(at.bernhardberger.tvhplayer.R.string.context_watch)).assertExists()
        compose.onNodeWithText("Läuft • bis ", substring = true).assertExists()
        compose.onNodeWithTag("browse-context-progress").assertIsDisplayed()
        capture("guide-german", "u15-context")
    }

    @Test fun channelsHoldMenuBackAndWatch() {
        show(recordings = false)
        exercise("channel-row-1", "Watch", "channels")
        assertEquals(1, plays)
    }

    @Test fun recordingMenuTakesNoKeysWhileItsPlaybackIsOpening() {
        show(recordings = true, entryHandoff = true)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Resume").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
        // The menu stays up until the player replaces browse; Delete is three steps down.
        compose.onRoot().performKeyInput { repeat(3) { pressKey(Key.DirectionDown) }; pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-confirmation-back").assertDoesNotExist()
        compose.onNodeWithText("Resume").assertIsFocused()
        assertEquals(1, plays)
    }

    @Test fun programmeMenuTakesNoKeysWhileItsPlaybackIsOpening() = channelMenuWhileOpening(noProgramme = false)
    @Test fun channelMenuTakesNoKeysWhileItsPlaybackIsOpening() = channelMenuWhileOpening(noProgramme = true)

    private fun channelMenuWhileOpening(noProgramme: Boolean) {
        val session = show(recordings = false, noProgramme = noProgramme, entryHandoff = true)
        val watch = if (noProgramme) "Watch channel" else "Watch"
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText(watch).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
        compose.onRoot().performKeyInput { repeat(3) { pressKey(Key.DirectionDown) }; pressKey(Key.DirectionCenter) }
        assertEquals(null, guideRequest)
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        compose.onNodeWithText(watch).assertIsFocused()
        assertEquals(1, plays)
    }

    @Test fun programmeMenuWatchLiveGoesLiveOnTheWatchedChannel() = watchLiveFromMenu(noProgramme = false)
    @Test fun channelMenuWatchLiveGoesLiveOnTheWatchedChannel() = watchLiveFromMenu(noProgramme = true)

    private fun watchLiveFromMenu(noProgramme: Boolean) {
        show(recordings = false, noProgramme = noProgramme, watching = true, behindLive = true)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Continue watching").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Watch live").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals(1, goLives)
            assertEquals(1, plays)
            assertEquals(ChannelId(1), playedChannel)
        }
    }

    @Test fun programmeMenuContinueWatchingReturnsWithoutGoingLive() = continueFromMenu(noProgramme = false)
    @Test fun channelMenuContinueWatchingReturnsWithoutGoingLive() = continueFromMenu(noProgramme = true)

    private fun continueFromMenu(noProgramme: Boolean) {
        show(recordings = false, noProgramme = noProgramme, watching = true, behindLive = true)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Continue watching").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        // The watched channel's own request returns to its player; nothing goes live or starts a recording.
        compose.runOnIdle {
            assertEquals(0, goLives)
            assertEquals(1, plays)
            assertEquals(ChannelId(1), playedChannel)
            assertEquals(null, start)
        }
    }

    @Test fun programmeMenuFromAGoneSessionDropsContinueAndLive() = staleSessionMenu(noProgramme = false)
    @Test fun channelMenuFromAGoneSessionDropsContinueAndLive() = staleSessionMenu(noProgramme = true)

    private fun staleSessionMenu(noProgramme: Boolean) {
        val session = show(recordings = false, noProgramme = noProgramme, watching = true, behindLive = true)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Continue watching").assertIsFocused()
        compose.onNodeWithText("Watch live").assertExists()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { session.replaceGeneration(session.observation.value) }
        // The first frame with the new session, before the menu's close takes effect.
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Continue watching").assertDoesNotExist()
        compose.onNodeWithText("Watch live").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Continue watching").assertDoesNotExist()
        compose.onNodeWithText("Watch live").assertDoesNotExist()
        assertEquals(0, goLives)
    }

    @Test fun channelMenuRestoresOriginOnlyAfterExitAndConsumesKeysWhileClosing() {
        show(recordings = false)
        val row = compose.onNodeWithTag("channel-row-1").requestFocus()
        row.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Watch").assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { back.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        row.assertIsNotFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(0, plays)
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        row.assertIsFocused()
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
    }

    @Test fun recordingsHoldMenuBackAndResume() {
        show(recordings = true)
        exercise("recording-list-entry-1", "Resume", "recordings")
        assertEquals(1, plays)
        assertEquals(RecordingPlaybackStart.RESUME, start)
    }

    @Test @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun recordingResumeDialogCapture() = recordingOkShowsResumeAndBackRestoresRow()

    @Test fun recordingOkShowsResumeAndBackRestoresRow() {
        val now = Instant.parse("2026-09-15T19:00:00Z").epochSeconds
        val resumeEnd = "Ends at ${at.bernhardberger.tvhplayer.ui.common.formatClock(now + 28 * 60)}"
        val startOverEnd = "Ends at ${at.bernhardberger.tvhplayer.ui.common.formatClock(now + 90 * 60)}"
        show(recordings = true, realRail = true, episodeSubtitle = "The winter ridge", fixedNow = now)
        val row = compose.onNodeWithTag("recording-list-entry-1").requestFocus()
        row.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused()
        compose.onNodeWithText("Resume from 1:02:00").assertIsFocused()
        compose.onNode(hasText("The winter ridge") and hasAnyAncestor(hasTestTag("centred-dialog-content"))).assertExists()
        compose.onNode(hasText("101 Ridge Earth HD • ", substring = true) and hasAnyAncestor(hasTestTag("centred-dialog-content"))).assertExists()
        compose.onNodeWithText("Watched", substring = true).assertDoesNotExist()
        assertResumePiconGeometry(picon = true)
        compose.onNodeWithText(resumeEnd).assertExists()
        compose.onNodeWithTag("recording-resume").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, resumeEnd))
        compose.onNodeWithTag("recording-start-over").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, startOverEnd))
        val card = compose.onNodeWithTag("centred-dialog-card").assertWidthIsEqualTo(528.dp)
            .getUnclippedBoundsInRoot()
        assertEquals(480f, (card.left.value + card.right.value) / 2f, 0.5f)
        assertEquals(270f, (card.top.value + card.bottom.value) / 2f, 0.5f)
        compose.onNodeWithTag("centred-dialog-content").assertWidthIsEqualTo(480.dp)
        val resume = compose.onNodeWithTag("recording-resume").getUnclippedBoundsInRoot()
        val startOver = compose.onNodeWithTag("recording-start-over").getUnclippedBoundsInRoot()
        assertEquals(resume.top.value, startOver.top.value, 0.5f)
        assertEquals(12f, (startOver.left - resume.right).value, 0.5f)
        compose.onNodeWithTag("centred-dialog-scrim").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog))
        compose.onNodeWithTag("recording-resume").performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        assertEquals(0, plays)
        compose.onNodeWithTag("recording-resume").assertIsFocused()
        capture("resume", "u15-dialogs")
        compose.runOnIdle { back.onBackPressed() }
        row.assertIsFocused()
        row.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
        compose.onNodeWithText(startOverEnd).assertExists()
        compose.onNodeWithText(resumeEnd).assertDoesNotExist()
        capture("resume-start-over", "u15-dialogs")
        compose.onNodeWithTag("recording-start-over").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
        assertEquals(RecordingPlaybackStart.START_OVER, start)
    }

    @Test fun recordingResumeWithoutPiconStartsTextAtTheContentEdge() {
        show(recordings = true, realRail = true, picon = false)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertResumePiconGeometry(picon = false)
        capture("resume-no-picon", "u15-dialogs")
    }

    @Test fun recordingResumeLoadsItsLogoWithTheSessionOfItsRecordingWhateverTheRootProvides() {
        val rootSession = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        ))
        val session = show(recordings = true, rootArtwork = rootSession)
        val own = requireNotNull(session.observation.value.currentSession)
        val root = requireNotNull(rootSession.observation.value.currentSession)
        assertNotSame(own, root)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume-picon").assertExists()
        compose.waitForIdle()

        val logos = synchronized(artworkRequests) { artworkRequests.filter { it.id == ArtworkId(1) } }
        assertTrue("the resume dialog drew the channel logo", logos.any { it.currentSession === own })
        assertEquals(emptyList<Any>(), logos.filter { it.currentSession === root })
    }

    private fun assertResumePiconGeometry(picon: Boolean) {
        val content = compose.onNodeWithTag("centred-dialog-content").getUnclippedBoundsInRoot()
        val text = compose.onNodeWithTag("centred-dialog-text").getUnclippedBoundsInRoot()
        val eyebrow = compose.onNode(hasText("101 Ridge Earth HD • ", substring = true) and
            hasAnyAncestor(hasTestTag("centred-dialog-content"))).getUnclippedBoundsInRoot()
        val title = compose.onNode(hasText("Ridge Light: The remarkable journey along the mountain ridge in winter") and
            hasAnyAncestor(hasTestTag("centred-dialog-content"))).getUnclippedBoundsInRoot()
        assertEquals(content.left.value, title.left.value, 0.5f)
        if (picon) {
            val logo = compose.onNodeWithTag("recording-resume-picon")
                .assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(20.dp).getUnclippedBoundsInRoot()
            assertEquals(content.left.value, logo.left.value, 0.5f)
            assertEquals(8f, (eyebrow.left - logo.right).value, 0.5f)
            assertEquals((eyebrow.top.value + eyebrow.bottom.value) / 2f, (logo.top.value + logo.bottom.value) / 2f, 0.5f)
            compose.mainClock.advanceTimeBy(500)
            val bitmap = resumeBitmap()
            assertEquals("No plate behind the transparent picon corner",
                bitmap.getPixel((content.left.value * 2).toInt(), (content.top.value * 2).toInt()),
                bitmap.getPixel((logo.left.value * 2).toInt(), (logo.top.value * 2).toInt()))
            bitmap.recycle()
        } else {
            compose.onNodeWithTag("recording-resume-picon").assertDoesNotExist()
            assertEquals(content.left.value, text.left.value, 0.5f)
            assertEquals(content.right.value, text.right.value, 0.5f)
            assertEquals(content.left.value, eyebrow.left.value, 0.5f)
        }
    }

    @Test fun recordingResumeWithoutDurationOmitsEndTime() {
        show(recordings = true, unknownDuration = true)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Ends at", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("recording-resume").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithTag("recording-resume").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Ends at", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("recording-start-over").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test fun recordingResumeBackRestoresHardwareDpadAndBackBehavior() {
        show(recordings = true, realRail = true)
        val row = compose.onNodeWithTag("recording-list-entry-1").requestFocus()
        fun checkNavigation(): Int {
            hardwareKey(android.view.KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithTag("recording-list-entry-2").assertIsFocused()
            hardwareKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            row.assertIsFocused()
            hardwareKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
            compose.onNodeWithTag("nav-channels").assertIsFocused()
            hardwareKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            row.assertIsFocused()
            hardwareBack()
            return compose.onNode(isFocused()).fetchSemanticsNode().id
        }
        val baselineBackFocus = checkNavigation()
        row.requestFocus()
        hardwareKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("recording-resume").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        row.assertIsFocused()
        assertEquals(baselineBackFocus, checkNavigation())
    }

    @Test fun recordingResumeDimsRailAndKeepsFocusInsideCard() {
        show(recordings = true, realRail = true)
        val row = compose.onNodeWithTag("recording-list-entry-1").requestFocus()
        compose.mainClock.advanceTimeBy(500)
        val before = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(before)) }
        row.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionLeft); pressKey(Key.DirectionDown); pressKey(Key.DirectionUp)
        }.assertIsFocused()
        compose.onNodeWithTag("recording-resume").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown); pressKey(Key.DirectionUp); pressKey(Key.DirectionRight)
        }.assertIsFocused()
        compose.mainClock.advanceTimeBy(500)
        val after = resumeBitmap()
        // Sample a rail glyph, then compare the actual composited window with the original rail.
        var brightest = 0
        var x = 0
        var y = 0
        for (px in 24 until 112) for (py in 100 until 980) {
            val light = android.graphics.Color.red(before.getPixel(px, py))
            if (light > brightest) { brightest = light; x = px; y = py }
        }
        assertTrue("Rail glyph was rendered", brightest > 100)
        assertEquals("Rail covered by the single 60% scrim", brightest * 0.4f,
            android.graphics.Color.red(after.getPixel(x, y)).toFloat(), 2f)
        before.recycle()
        after.recycle()
    }

    @Test fun channelRecordSchedulesDirectlyAndReturnsToRow() {
        val session = show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Record").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        compose.onNodeWithTag("channel-row-1").assertIsFocused()
        assertEquals(0, plays)
    }

    @Test fun deleteFromDetailsKeepsItsBackdropEveryFrame() = confirmationFrames(recordings = true, details = true)
    @Test fun deleteFromMenuCrossfadesWithoutBrowseFlashEveryFrame() = confirmationFrames(recordings = true, details = false)
    @Test fun cancelFromDetailsKeepsItsBackdropEveryFrame() = confirmationFrames(recordings = false, details = true)
    @Test fun cancelFromMenuCrossfadesWithoutBrowseFlashEveryFrame() = confirmationFrames(recordings = false, details = false)
    @Test fun deleteKeepNeverReopensMenuEveryFrame() = confirmationFrames(recordings = true, details = false, keep = true)
    @Test fun cancelRecordNeverReopensMenuEveryFrame() = confirmationFrames(recordings = false, details = false, keep = true)

    private fun confirmationFrames(recordings: Boolean, details: Boolean, keep: Boolean = false) {
        val session = show(recordings, realRail = true, fixedNow = if (recordings) 1_789_480_800L else null,
            channelRecording = !recordings, recordingState = if (recordings) DvrEntryState.COMPLETED else DvrEntryState.SCHEDULED)
        val browse = resumeBitmap()
        compose.onNodeWithTag(if (recordings) "recording-list-entry-1" else "channel-row-1")
            .requestFocus().performKeyInput { pressKey(Key.Menu) }
        if (details) compose.onNodeWithText(if (recordings) "Details" else "Program details")
            .requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        val target = if (details) compose.onNodeWithTag(if (recordings) "details-delete" else "details-cancel")
            else compose.onNodeWithText(if (recordings) "Delete" else "Cancel recording")
        target.requestFocus()
        compose.mainClock.advanceTimeBy(500)
        val original = if (details) resumeBitmap() else browse
        // Above the card: details eyebrow, browse heading (picker), or rail glyph (menu).
        // Probe the heading for the picker: opaque-layer registration alone can hide the rail.
        val xs = if (details) 96 until 900 else 24 until 112
        val ys = if (details) 125 until 260 else 100 until 980
        var x = 0; var y = 0; var brightest = 0
        for (px in xs) for (py in ys) {
            val light = android.graphics.Color.red(original.getPixel(px, py))
            if (light > brightest) { brightest = light; x = px; y = py }
        }
        assertTrue("Rendered backdrop probe", brightest > 100)
        fun assertFrames(exiting: Boolean = false) {
            repeat(24) { frame ->
                compose.mainClock.advanceTimeBy(16)
                val light = probeLight(x, y)
                if (exiting && !details) compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
                else if (details) assertTrue("Details remain drawn on frame $frame: $light/$brightest",
                    light >= brightest * .38f)
                else assertTrue("Browse stays covered on frame $frame: $light/$brightest",
                    light <= brightest * .5f + 3)
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            target.performKeyInput { pressKey(Key.DirectionCenter) }
            assertFrames()
            compose.onNodeWithTag("centred-dialog-card").assertExists()
            if (recordings) capture(if (details) "delete-over-details" else "delete-from-menu", "u15-confirmations")
            if (keep) compose.onNodeWithTag(if (recordings) "recording-confirmation-back" else "two-column-first").assertIsFocused().performKeyInput {
                pressKey(Key.DirectionCenter)
            }
            else compose.runOnIdle { back.onBackPressed() }
            assertFrames(exiting = true)
            if (details) target.assertIsFocused()
            else compose.onNodeWithTag(if (recordings) "recording-list-entry-1" else "channel-row-1").assertIsFocused()
            if (!recordings) assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
            if (!recordings) assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        } finally {
            compose.mainClock.autoAdvance = true
            if (original !== browse) original.recycle()
            browse.recycle()
        }
    }

    @Test fun removedRecordingClosesResumeWithoutChangingTheNextContextMenu() {
        val session = show(recordings = true)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused()
        compose.runOnIdle {
            val previous = session.observation.value
            session.publish(SessionObservation.create(
                sessionState = previous.sessionState,
                channelState = previous.channelState,
                epgState = previous.epgState,
                dvrState = DvrRepositoryState.Current(DvrSnapshot.create(
                    previous.dvrSnapshotForDisplay!!.entries.filter { it.id != DvrEntryId(1) })),
                recordingProgressCapability = previous.recordingProgressCapability,
            ))
        }
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        compose.onNodeWithTag("recording-list-entry-2").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Play recording").assertIsFocused()
        compose.onNodeWithText("Details").assertExists()
        assertEquals(0, plays)
    }

    @Test fun recordingWatchedToTheEndClosesResume() {
        val session = show(recordings = true)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused()
        compose.runOnIdle {
            val previous = session.observation.value
            // The SDK marks a recording watched to the end by counting a play and clearing its position.
            val watched = previous.dvrSnapshotForDisplay!!.entries.map { entry ->
                if (entry.id != DvrEntryId(1)) entry else DvrEntry.create(entry.id, channelId = entry.channelId,
                    eventId = entry.eventId, title = entry.title, folder = emptyList(), channelName = entry.channelName,
                    state = entry.state, playPosition = 0.seconds, playCount = 1, start = entry.start, stop = entry.stop)
            }
            session.publish(SessionObservation.create(previous.sessionState, previous.channelState, previous.epgState,
                DvrRepositoryState.Current(DvrSnapshot.create(watched)),
                recordingProgressCapability = previous.recordingProgressCapability))
        }
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        assertEquals(0, plays)
    }

    @Test fun scheduledConfirmationBackRestoresCancelAction() {
        show(recordings = true, recordingState = DvrEntryState.SCHEDULED)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-cancel").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Keep recording").assertIsFocused()
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        compose.onNodeWithText("Keep recording").assertIsFocused()
        capture("cancel-recording", "u15-dialogs")
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-cancel").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("recording-list-entry-1").assertIsFocused()
    }

    @Test fun channelsShowGuideUsesTheOriginatingChannel() {
        show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Show in Guide").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(ChannelId(1), guideRequest)
        assertEquals(0, plays)
    }

    @Test fun contextPublicationPreservesSurvivingActionFocus() {
        val session = show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Show in Guide").requestFocus().assertIsFocused()
        compose.runOnIdle {
            val previous = session.observation.value
            session.publish(SessionObservation.create(previous.sessionState, previous.channelState, previous.epgState,
                DvrRepositoryState.Current(DvrSnapshot.create(listOf(DvrEntry.create(DvrEntryId(9),
                    eventId = EventId(1), channelId = ChannelId(1), state = DvrEntryState.SCHEDULED))))))
        }
        compose.onNodeWithText("Cancel recording").assertExists()
        compose.onNodeWithText("Show in Guide").assertIsFocused()
    }

    @Test fun missingMenuChannelClosesInsteadOfTrappingChannels() {
        val session = show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("browse-context-menu").assertExists()
        compose.runOnIdle {
            val previous = session.observation.value
            session.publish(SessionObservation.create(previous.sessionState,
                ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(2),
                    name = "Ridge Earth SD", number = 100, icon = ChannelIcon.ImageCache(ArtworkId(1)))))), previous.epgState, previous.dvrState))
        }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        compose.onNodeWithTag("channel-row-2").assertIsFocused()
    }

    @Test fun absentChannelAtMenuEntryCallsClose() {
        var closes = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                ChannelContextMenu(ChannelId(99), SessionObservation.create(), { 0L },
                    mutationScope = androidx.compose.runtime.rememberCoroutineScope(),
                    mutationInFlight = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) },
                    onPlay = { _, _ -> }, onShowGuide = { _, _ -> }, onClose = { closes++ })
            }
        }
        compose.runOnIdle { assertEquals(1, closes) }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
    }

    @Test fun directRecordAfterPoppingDetailsKeepsItsActionFocused() {
        show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-3").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-card-3").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-record").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        compose.onNodeWithTag("details-record").assertIsFocused()
    }

    @Test fun recordingMenuDetailsBackNeverReopensMenuEveryFrame() = menuDetailsFrames(recordings = true)

    @Test fun channelMenuDetailsBackNeverReopensMenuEveryFrame() = menuDetailsFrames(recordings = false)

    private fun menuDetailsFrames(recordings: Boolean) {
        show(recordings = recordings, realRail = true, fixedNow = if (recordings) 1_789_480_800L else null)
        val browse = resumeBitmap()
        // The browse page's top left (tabs, first rows), not the rail: opaque-layer registration can hide the rail
        // even when neither the menu nor the details background is actually drawn.
        var x = 0; var y = 0; var brightest = 0
        // Above Recordings' focused row: its focus growth restarts after Back, so it is no stable probe.
        val probeBottom = if (recordings) compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot.top.toInt() - 8 else 220
        for (px in 212 until 900) for (py in 20 until minOf(220, probeBottom)) {
            val light = android.graphics.Color.red(browse.getPixel(px, py))
            if (light > brightest) { brightest = light; x = px; y = py }
        }
        browse.recycle()
        assertTrue("Rendered browse heading probe", brightest > 100)
        val row = compose.onNodeWithTag(if (recordings) "recording-list-entry-1" else "channel-row-1").requestFocus()
        row.performKeyPress(activation(android.view.KeyEvent.ACTION_DOWN))
        row.performKeyPress(activation(android.view.KeyEvent.ACTION_DOWN, 1))
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        val detailsLabel = if (recordings) "Details" else "Program details"
        compose.onNodeWithText(detailsLabel).requestFocus()
        fun assertCoveredFrames(direction: String) {
            repeat(24) { frame ->
                compose.mainClock.advanceTimeByFrame()
                val light = probeLight(x, y)
                assertTrue("$direction covers browse heading on frame $frame: $light/$brightest",
                    light <= brightest * .4f)
                row.assertIsNotFocused()
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText(detailsLabel).performKeyInput { pressKey(Key.DirectionCenter) }
            assertCoveredFrames("Menu to details")
            compose.onNodeWithTag(if (recordings) "details-resume" else "details-watch").assertIsFocused()
            compose.runOnIdle { back.onBackPressed() }
            val lights = mutableListOf<Int>()
            repeat(24) {
                compose.mainClock.advanceTimeByFrame()
                lights += probeLight(x, y)
                compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
            }
            // The details page's blurred art is not black, so opaque means well below the browse probe.
            assertTrue("Details starts opaque: $lights", lights.first() < brightest * .3f)
            assertTrue("Exit is a fade, not a flash: $lights", lights.any { it in (brightest / 4)..(brightest * 3 / 4) })
            assertTrue("Browse must not flash then darken: $lights", lights.zipWithNext().all { (a, b) -> b >= a - 2 })
            assertTrue("Browse is restored: $lights", lights.last() >= brightest - 2)
            compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
            row.assertIsFocused()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertEquals(0, plays)
    }

    @Test fun directRecordingDetailsBackFadesMonotonicallyToBrowseEveryFrame() {
        show(recordings = true, recordingState = DvrEntryState.SCHEDULED, realRail = true)
        val browse = resumeBitmap()
        var x = 0; var y = 0; var brightest = 0
        // Above the focused row: its focus growth restarts after Back, so it is no stable probe.
        val rowTop = compose.onNodeWithTag("recording-list-entry-1").fetchSemanticsNode().boundsInRoot.top.toInt()
        for (px in 212 until 900) for (py in 20 until minOf(220, rowTop - 8)) {
            val light = android.graphics.Color.red(browse.getPixel(px, py))
            if (light > brightest) { brightest = light; x = px; y = py }
        }
        browse.recycle()
        assertTrue("Rendered browse heading probe", brightest > 100)
        val row = compose.onNodeWithTag("recording-list-entry-1").requestFocus()
        row.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.mainClock.autoAdvance = false
        val lights = mutableListOf<Int>()
        try {
            compose.runOnIdle { back.onBackPressed() }
            repeat(24) {
                compose.mainClock.advanceTimeByFrame()
                lights += probeLight(x, y)
                compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
            }
            // The details page's blurred art is not black, so opaque means well below the browse probe.
            assertTrue("Details starts opaque: $lights", lights.first() < brightest * .3f)
            assertTrue("Exit is a fade, not a flash: $lights", lights.any { it in (brightest / 4)..(brightest * 3 / 4) })
            assertTrue("Browse must not flash then darken: $lights", lights.zipWithNext().all { (a, b) -> b >= a - 2 })
            assertTrue("Browse is restored: $lights", lights.last() >= brightest - 2)
            row.assertIsFocused()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun recordingDetailsConsumesRepeatedBackDuringFadeAndRestoresAfterExit() {
        show(recordings = true, recordingState = DvrEntryState.SCHEDULED)
        var escapedBack = 0
        val fallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { escapedBack++ }
        }
        compose.runOnIdle { back.addCallback(fallback) }
        val row = compose.onNodeWithTag("recording-list-entry-1")
        row.requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { back.onBackPressed() }
        compose.mainClock.advanceTimeBy(96)
        row.assertIsNotFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(0, escapedBack)
        assertEquals(0, plays)
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        row.assertIsFocused()
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        compose.runOnIdle { fallback.remove() }
    }

    @Test fun recordingsDetailsBackRestoresTheOriginatingRow() {
        show(recordings = true)
        val row = compose.onNodeWithTag("recording-list-entry-1")
        row.requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-resume").assertIsFocused()
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        compose.onNodeWithTag("details-resume").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        row.assertIsFocused()
        assertEquals(0, plays)
    }

    @Test fun channelsStackedAiringShowGuideCarriesPageProgramme() {
        show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-3").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-show_guide").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(EventId(3), guideProgramme?.id)
        assertEquals(guideProgramme?.channelId, guideRequest)
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
    }

    @Test fun channelsDetailsStackBackAndOpeningReleaseRestoreExactOrigin() {
        show(recordings = false)
        val row = compose.onNodeWithTag("channel-row-1").requestFocus()
        row.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        assertEquals(0, plays)
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-record").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-3").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        // The stacked airing's own row lists the other airings, never itself.
        compose.onNodeWithTag("details-card-3").assertDoesNotExist()
        val heading = compose.onNodeWithText("Other airings of ", substring = true).getBoundsInRoot()
        org.junit.Assert.assertTrue("$heading peeks above the safe edge", heading.bottom <= compose.onRoot().getUnclippedBoundsInRoot().bottom - 48.dp)
        capture("stacked-from-airing", "u15-about")
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-card-3").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-record").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        row.assertIsFocused()
        assertEquals(0, plays)
    }

    @Test fun hardwareBackPopsStackedDetailsOnceAndLeavesReadingFirst() {
        show(recordings = false)
        val row = compose.onNodeWithTag("channel-row-1").requestFocus()
        row.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        // Watch → row → Right ×2 → OK stacks that airing's details.
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-3").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-card-4").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-card-5").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onNodeWithTag("details-card-5").assertDoesNotExist()
        // One hardware Back pops the stacked page onto the same card; it never just drops focus.
        hardwareBack()
        compose.onNodeWithTag("details-card-5").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("details-card-1").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-watch").assertIsFocused().assertIsDisplayed()
        hardwareBack()
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        row.assertIsFocused()
        assertEquals(0, plays)
    }

    @Test fun backFromAStackedAiringCrossfadesToThePreviousPageWithoutScrolling() {
        show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-3").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        fun scrollValues() = compose.onAllNodes(hasTestTag("details-page-scroll"),
            useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.VerticalScrollAxisRange].value() }
        compose.mainClock.autoAdvance = false
        hardwareBack()
        val frames = mutableListOf(scrollValues())
        repeat((PlayerMotion.MediumMs + 96) / 16) {
            compose.mainClock.advanceTimeByFrame()
            frames += scrollValues()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settled = scrollValues().single()
        assertTrue("the previous page must be back at its airings: $frames", settled > 0f)
        // The page being left stays at its top while it fades out. The one returned to is placed in
        // its first, still transparent frame (its peek height needs one layout) and then never moves.
        assertTrue("the previous page must never be shown scrolling: $frames",
            frames.drop(2).flatten().all { it == 0f || it == settled })
        compose.onNodeWithTag("details-card-3").assertIsFocused()
    }

    @Test fun hardwareBackFromAboutOrRowReturnsToTheActionsBeforeClosing() {
        show(recordings = false)
        val row = compose.onNodeWithTag("channel-row-1").requestFocus()
        row.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-about-description").assertIsFocused()
        hardwareBack()
        compose.onNodeWithTag("details-watch").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-3").assertIsFocused()
        hardwareBack()
        compose.onNodeWithTag("details-watch").assertIsFocused().assertIsDisplayed()
        hardwareBack()
        row.assertIsFocused()
    }

    /** The device path: the key goes through the window first; only an unconsumed Back reaches the dispatcher. */
    private fun hardwareBack() = hardwareKey(android.view.KeyEvent.KEYCODE_BACK)

    private fun hardwareKey(keyCode: Int) {
        compose.runOnIdle {
            val activity = view.context as android.app.Activity
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
        }
        compose.waitForIdle()
    }

    @Test fun channelsDetailsCanPlayItsCompletedRecording() {
        val session = show(recordings = false, channelRecording = true)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            val previous = session.observation.value
            val event = requireNotNull(previous.event(EventId(1)))
            session.publish(SessionObservation.create(previous.sessionState, previous.channelState,
                EpgRepositoryState.Current(EpgSnapshot.create(listOf(EpgEvent.create(event.id, event.channelId,
                    event.start, Instant.parse("2026-09-15T19:00:00Z") - 60.seconds,
                    title = event.title)))), previous.dvrState,
                recordingProgressCapability = previous.recordingProgressCapability))
        }
        compose.onNodeWithTag("details-play").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
        assertEquals(RecordingPlaybackStart.START_OVER, start)
    }

    @Test fun channelsMenuPlaysTheRecordingOfAnEndedProgrammeRightAfterWatch() {
        val session = show(recordings = false, channelRecording = true)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Play recording").assertDoesNotExist()
        // As in channelsDetailsCanPlayItsCompletedRecording: the programme has ended, so its recording plays.
        compose.runOnIdle {
            val previous = session.observation.value
            val event = requireNotNull(previous.event(EventId(1)))
            session.publish(SessionObservation.create(previous.sessionState, previous.channelState,
                EpgRepositoryState.Current(EpgSnapshot.create(listOf(EpgEvent.create(event.id, event.channelId,
                    event.start, Instant.parse("2026-09-15T19:00:00Z") - 60.seconds,
                    title = event.title)))), previous.dvrState,
                recordingProgressCapability = previous.recordingProgressCapability))
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Play recording").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Watch channel").requestFocus().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Play recording").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
        // Only the recording route sets a start; live playback would leave it unset.
        assertEquals(RecordingPlaybackStart.START_OVER, start)
    }

    @Test fun scheduledDetailsWatchChannelUsesLivePlayback() {
        show(recordings = true, recordingState = DvrEntryState.SCHEDULED)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(ChannelId(1), watchedChannel)
        assertEquals(0, plays)
    }

    @Test fun offlineChannelsMenuWatchPlaysTheChannel() {
        show(recordings = false, offline = true)
        compose.onNodeWithTag("channel-row-1", useUnmergedTree = true).requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.waitForIdle()
        compose.onNodeWithText("Watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
    }

    @Test fun offlineChannelsMenuDetailsWatchPlaysTheChannel() {
        show(recordings = false, offline = true)
        compose.onNodeWithTag("channel-row-1", useUnmergedTree = true).requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
    }

    @Test fun offlineDetailsOfferRecordingPlaybackOnlyOnceTheSessionIsReady() {
        val session = show(recordings = false, channelRecording = true, offline = true)
        compose.onNodeWithTag("channel-row-1", useUnmergedTree = true).requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        // As in channelsDetailsCanPlayItsCompletedRecording: the programme has ended, so its recording plays.
        fun publish(ready: Boolean) = compose.runOnIdle {
            val previous = session.observation.value
            val event = requireNotNull(previous.event(EventId(1)))
            val epg = EpgSnapshot.create(listOf(EpgEvent.create(event.id, event.channelId,
                event.start, Instant.parse("2026-09-15T19:00:00Z") - 60.seconds, title = event.title)))
            val catalog = requireNotNull(previous.channelCatalogForDisplay)
            val dvr = requireNotNull(previous.dvrSnapshotForDisplay)
            session.publish(SessionObservation.create(
                sessionState = if (ready) SessionState.Ready(ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)) else SessionState.Connecting,
                channelState = if (ready) ChannelRepositoryState.Current(catalog) else ChannelRepositoryState.Stale(catalog),
                epgState = if (ready) EpgRepositoryState.Current(epg) else EpgRepositoryState.Stale(epg),
                dvrState = if (ready) DvrRepositoryState.Current(dvr) else DvrRepositoryState.Stale(dvr),
                dvrConfigurationsState = previous.dvrConfigurationsState,
                recordingProgressCapability = previous.recordingProgressCapability,
            ))
        }
        publish(ready = false)
        compose.waitForIdle()
        compose.onNodeWithTag("details-watch").assertExists()
        compose.onNodeWithTag("details-play").assertDoesNotExist()
        publish(ready = true)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("details-play").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("details-play").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
    }

    @Test fun offlineGuideDetailsWatchPlaysTheChannel() {
        show(recordings = false, guideMenu = true, guideDetails = true, offline = true)
        compose.waitForIdle()
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
    }

    @Test fun offlineScheduledDetailsWatchChannelUsesLivePlayback() {
        show(recordings = true, recordingState = DvrEntryState.SCHEDULED, offline = true)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(ChannelId(1), watchedChannel)
        assertEquals(0, plays)
    }

    @Test fun deniedDvrPermissionKeepsRecordingPlaybackButRemovesDelete() {
        show(recordings = true, dvrWrite = CapabilityAccess.DENIED)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Resume").assertIsFocused()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test fun deniedDvrPermissionRemovesChannelRecord() {
        show(recordings = false, dvrWrite = CapabilityAccess.DENIED)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Watch").assertIsFocused()
        compose.onNodeWithText("Record").assertDoesNotExist()
    }

    private fun exercise(rowTag: String, first: String, capture: String) {
        val row = compose.onNodeWithTag(rowTag, useUnmergedTree = true).requestFocus()
        row.performKeyPress(activation(android.view.KeyEvent.ACTION_DOWN))
        row.performKeyPress(activation(android.view.KeyEvent.ACTION_DOWN, 1))
        compose.onNodeWithText(first).assertIsFocused()
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        assertEquals("held release must not activate playback", 0, plays)
        compose.onNodeWithText(first).assertIsFocused()
        listOf(Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight).forEach {
            compose.onRoot().performKeyInput { pressKey(it) }
            compose.onNodeWithText(first).assertIsFocused()
        }
        capture(capture)
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        row.assertIsFocused()
        row.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText(first).assertIsFocused()
        assertEquals(0, plays)
        compose.onNodeWithText(first).performKeyInput { pressKey(Key.DirectionCenter) }
    }

    private fun activation(action: Int, repeat: Int = 0) = KeyEvent(android.view.KeyEvent(
        0, 0, action, android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeat,
    ))

    private fun capture(name: String, output: String = "u15-context") {
        compose.mainClock.advanceTimeBy(500)
        val bitmap = if (output == "u15-dialogs" && name.startsWith("resume")) resumeBitmap()
            else Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).also { bitmap ->
                compose.runOnIdle { view.draw(Canvas(bitmap)) }
            }
        val directory = File("build/outputs/$output").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun resumeBitmap(): Bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).also { bitmap ->
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            view.draw(canvas)
        }
    }

    /** Draw the same window coordinate, clipping raster work to the pixel asserted by frame loops. */
    private fun probeLight(x: Int, y: Int): Int {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            canvas.translate(-x.toFloat(), -y.toFloat())
            view.draw(canvas)
        }
        val light = android.graphics.Color.red(bitmap.getPixel(0, 0))
        bitmap.recycle()
        return light
    }

    @Test fun fullscreenMenuHidesRailUntilDirectRecordClosesIt() {
        show(recordings = false, realRail = true)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("nav-channels").assertDoesNotExist()
        compose.onNodeWithText("Watch").performKeyInput { pressKey(Key.DirectionLeft) }.assertIsFocused()
        capture("channels-fullscreen-widebutton")
        val middle = compose.onNodeWithText("Watch").getUnclippedBoundsInRoot()
            .let { ((it.top.value + it.bottom.value) / 2f * 2f).toInt() }
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        // 268dp native button, enlarged 1.1x; the right middle fills, both right corners curve in.
        assertTrue(android.graphics.Color.red(bitmap.getPixel(1680, middle)) > 200)
        assertTrue(android.graphics.Color.red(bitmap.getPixel(1680, middle - 50)) < 80)
        assertTrue(android.graphics.Color.red(bitmap.getPixel(1680, middle + 50)) < 80)
        bitmap.recycle()
        compose.onNodeWithText("Record").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        compose.onNodeWithTag("nav-channels").assertExists()
        compose.onNodeWithTag("channel-row-1").assertIsFocused()
    }

    @Test fun channelImmediateRecordReturnsDirectlyToRowEveryFrame() = channelReturnEveryFrame("Record")
    @Test fun channelStopKeepReturnsDirectlyToRowEveryFrame() = channelReturnEveryFrame("Keep")
    @Test fun channelStopConfirmReturnsDirectlyToRowEveryFrame() = channelReturnEveryFrame("Stop")
    @Test fun channelDetailsBackReturnsDirectlyToRowEveryFrame() = channelReturnEveryFrame("Back")
    @Test fun recordingStopKeepReturnsDirectlyToCardEveryFrame() = channelReturnEveryFrame("Keep", recordings = true)
    @Test fun recordingStopConfirmReturnsDirectlyToCardEveryFrame() = channelReturnEveryFrame("Stop", recordings = true)
    @Test fun recordingDetailsBackReturnsDirectlyToCardEveryFrame() = channelReturnEveryFrame("Back", recordings = true)

    private fun channelReturnEveryFrame(action: String, recordings: Boolean = false) {
        show(recordings = recordings, realRail = true, scopeTabs = true,
            channelRecording = action == "Keep" || action == "Stop", recordingState = DvrEntryState.RECORDING)
        val rowTag = if (recordings) "recording-list-entry-1" else "channel-row-1"
        // Enter as the remote does, so cold-entry bookkeeping matches the production screen.
        // Channels enters its first row itself.
        if (recordings) compose.onNodeWithTag(rowTag).requestFocus()
        compose.onNodeWithTag(rowTag).assertIsFocused().performKeyInput { pressKey(Key.Menu) }
        when (action) {
            "Back" -> {
                compose.onNodeWithText(if (recordings) "Details" else "Program details").requestFocus()
                    .performKeyInput { pressKey(Key.DirectionCenter) }
                if (!recordings) compose.onNodeWithTag("details-watch").assertIsFocused()
            }
            "Keep", "Stop" -> {
                compose.onNodeWithText("Stop recording").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
                compose.onNodeWithText(if (action == "Keep") "Keep recording" else "Stop recording")
                    .requestFocus().assertIsFocused()
            }
            else -> compose.onNodeWithText("Record").requestFocus().assertIsFocused()
        }
        val before = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.id }.toSet()
        val row = compose.onNodeWithTag(rowTag).fetchSemanticsNode().id
        val frames = mutableListOf<List<String>>()
        var returned = false
        compose.mainClock.autoAdvance = false
        try {
            if (action == "Back") hardwareBack() else hardwareKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
            repeat(36) {
                compose.mainClock.advanceTimeByFrame()
                probeLight(0, 0)
                val focused = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
                frames += focused.map { "${it.id}:${it.config.getOrElse(SemanticsProperties.TestTag) { "" }}:${it.config.getOrElse(SemanticsProperties.Text) { emptyList() }}" }
                assertTrue("Unexpected focus at frame $it: $frames", focused.all { it.id == row || (!returned && it.id in before) })
                returned = returned || focused.any { it.id == row }
                val overlayGone = compose.runOnIdle { requireNotNull(layering).let {
                    it.details.isEmpty() && it.modalScrims.isEmpty()
                } }
                if (overlayGone) assertTrue("No row focus after overlay removal at frame $it: $frames", focused.any { it.id == row })
            }
            compose.onNodeWithTag(rowTag).assertIsFocused()
        } finally { compose.mainClock.autoAdvance = true }
        assertEquals(0, plays)
    }

    private fun show(recordings: Boolean, dvrWrite: CapabilityAccess = CapabilityAccess.ALLOWED,
        recordingState: DvrEntryState = DvrEntryState.COMPLETED, channelRecording: Boolean = false,
        guideMenu: Boolean = false, artwork: Boolean = true, noProgramme: Boolean = false,
        imageProbe: Boolean = false, fanartOnly: Boolean = false, realRail: Boolean = false,
        futureProgramme: Boolean = false, picon: Boolean = true,
        summary: String? = "A winter expedition beyond the Arctic Circle follows the changing light across the ridge.",
        description: String? = null, episodeSubtitle: String? = null, unknownDuration: Boolean = false,
        fixedNow: Long? = null, profiles: Boolean = false, scopeTabs: Boolean = false,
        offline: Boolean = false, guideDetails: Boolean = false, entryHandoff: Boolean = false,
        rootArtwork: FakeTvheadendSession? = null, watching: Boolean = false,
        behindLive: Boolean = false): FakeTvheadendSession {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val now = fixedNow?.let(Instant::fromEpochSeconds) ?: Instant.parse("2026-09-15T19:00:00Z")
        check(android.os.SystemClock.setCurrentTimeMillis(now.toEpochMilliseconds()))
        val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = if (picon) ChannelIcon.ImageCache(ArtworkId(1)) else null)
        val otherChannel = Channel.create(ChannelId(2), name = "Ridge Earth SD", number = 100, icon = ChannelIcon.ImageCache(ArtworkId(1)))
        val event = EpgEvent.create(EventId(1), channel.id,
            if (futureProgramme) now + 1800.seconds else now - 1800.seconds,
            if (futureProgramme) now + 3600.seconds else now + 1800.seconds,
            title = "Ridge Light", image = if (artwork) ArtworkId(2) else null, genre = "Documentary",
            summary = summary, description = description, subtitle = episodeSubtitle)
        // Later airings of the same programme, for the details page's Other airings row.
        val airings = (3L..6L).map { id ->
            EpgEvent.create(EventId(id), if (id % 2 == 0L) otherChannel.id else channel.id, now + (id * 86400).seconds,
                now + (id * 86400 + 3600).seconds, title = "Ridge Light", subtitle = "Part $id", image = ArtworkId(2))
        }
        val next = EpgEvent.create(EventId(2), channel.id, event.stop, event.stop + 3600.seconds,
            title = "Northern Journey", image = ArtworkId(2),
            summary = "Seasonal mountain light, forests and weather moving over a ridge.",
            description = "Seasonal mountain light, forests and weather moving over a ridge, from the first snow to the thaw. ".repeat(3))
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, eventId = event.id,
            title = if (recordingState == DvrEntryState.COMPLETED) "Ridge Light: The remarkable journey along the mountain ridge in winter" else "Ridge Light",
            folder = emptyList(),
            summary = summary, description = description, subtitle = episodeSubtitle,
            channelName = channel.name, state = recordingState, playPosition = 3720.seconds,
            start = if (recordingState == DvrEntryState.SCHEDULED) now + 3600.seconds else now - 7200.seconds,
            stop = if (unknownDuration) null else if (recordingState == DvrEntryState.SCHEDULED) now + 9000.seconds else now - 1800.seconds,
            image = if (artwork) ArtworkId(2) else null, fanartImage = if (fanartOnly) ArtworkId(2) else null)
        val otherEntry = DvrEntry.create(DvrEntryId(2), channelId = channel.id, title = "Ridge Light: Episode 2",
            folder = emptyList(), channelName = channel.name, state = DvrEntryState.COMPLETED,
            start = now - 3500.seconds, stop = now - 100.seconds, image = ArtworkId(2))
        val catalog = ChannelCatalog.create(listOf(otherChannel, channel),
            if (scopeTabs) listOf(ChannelTag.create(ChannelTagId(1), name = "Favourites", channelIds = listOf(channel.id))) else emptyList())
        val epg = EpgSnapshot.create(if (noProgramme) emptyList() else listOf(event, next) + airings)
        val dvr = DvrSnapshot.create(if (recordings) listOf(otherEntry, entry) else if (channelRecording) listOf(entry) else emptyList())
        // Offline: saved metadata before the session is ready, as at a warm start without the server.
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = if (offline) SessionState.Connecting
                else SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = dvrWrite)),
            channelState = if (offline) ChannelRepositoryState.Stale(catalog) else ChannelRepositoryState.Current(catalog),
            epgState = if (offline) EpgRepositoryState.Stale(epg) else EpgRepositoryState.Current(epg),
            dvrState = if (offline) DvrRepositoryState.Stale(dvr) else DvrRepositoryState.Current(dvr),
            dvrConfigurationsState = DvrConfigurationsState.Current.create(if (profiles) listOf(
                DvrConfiguration(DvrConfigId("one"), "One", ""), DvrConfiguration(DvrConfigId("two"), "Two", "")) else emptyList()),
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED,
        ))
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, session.observation.value.currentSession?.generationIdentity) }
        val dependencies = module {
            single<TvheadendSession> { session }; single { notices }
            single { at.bernhardberger.tvhplayer.settings.UiSettingsStore(context) }
        }
        val application = org.koin.dsl.koinApplication { modules(dependencies) }
        images.install(FixtureArt.imageLoader(context, if (imageProbe) mapOf(
            ArtworkId(1) to Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) },
            ArtworkId(2) to Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) },
        ) else mapOf(ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(2) to FixtureArt.art("ridge-light")),
            onRequest = { artworkRequests += it }))
        val state = RecordingsScreenState().apply {
            if (recordingState == DvrEntryState.SCHEDULED || recordingState == DvrEntryState.RECORDING)
                mode.value = at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE
        }
        compose.setContent {
            org.koin.compose.KoinIsolatedContext(application) {
                androidx.compose.runtime.DisposableEffect(application) {
                    onDispose { application.close() }
                }
                TVHeadendPlayerTheme { at.bernhardberger.tvhplayer.ui.components.ProvideAppArtwork(rootArtwork ?: session) {
                    back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    view = LocalView.current
                    val observation = session.observation.collectAsState().value
                    // Watching: channel 1 plays, at live or rewound ten minutes, and the player runtime can go live.
                    val livePosition = androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(at.bernhardberger.tvhplayer.ui.components.LiveViewerPosition(
                            channel.id, now.epochSeconds - if (behindLive) 600 else 0, behindLive))
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        at.bernhardberger.tvhplayer.ui.LocalPlayerEntryHandoff provides playerEntry.takeIf { entryHandoff },
                        at.bernhardberger.tvhplayer.ui.components.LocalWatchingChannel provides channel.id.takeIf { watching },
                        at.bernhardberger.tvhplayer.ui.components.LocalWatchingTarget provides
                            at.bernhardberger.tvhplayer.playback.AppPlaybackTarget.Live(channel.id).takeIf { watching },
                        at.bernhardberger.tvhplayer.ui.components.LocalWatchingIndicator provides if (watching)
                            at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator.PLAYING
                            else at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator.NONE,
                        at.bernhardberger.tvhplayer.ui.components.LocalLivePosition provides livePosition.takeIf { watching },
                        at.bernhardberger.tvhplayer.ui.components.LocalWatchingGoLive provides
                            (suspend { goLives++; Unit }).takeIf { watching }) {
                    Box(Modifier.fillMaxSize().background(androidx.tv.material3.MaterialTheme.colorScheme.background)) {
                        val content: @androidx.compose.runtime.Composable (Boolean) -> Unit = { drawerActive ->
                        layering = at.bernhardberger.tvhplayer.ui.components.LocalBrowseContentLayering.current
                        if (guideMenu) ProgrammeDetailsHost(
                            openingEvent = event, openingObservation = observation, observation = observation,
                            currentObservation = { session.observation.value }, nowSecProvider = { now.epochSeconds },
                            showContextMenu = !guideDetails, onShowDetails = {},
                            mutations = DvrMutationActions(session.dvrRepository), notices = notices,
                            mutationScope = androidx.compose.runtime.rememberCoroutineScope(),
                            mutationInFlight = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) },
                            onPlay = { _, _ -> plays++; requestPlayer() }, onPlayRecording = {}, onClose = {},
                         ) else if (recordings) RecordingsScreenContent(
                             initialFocusEnabled = !drawerActive,
                            observation = observation, state = state,
                             dvrMutationActions = DvrMutationActions(session.dvrRepository), notices = notices,
                             nowSecProvider = { now.epochSeconds },
                            onPlayRecording = { _, intent -> plays++; start = intent; requestPlayer() },
                            onWatchChannel = { channelId, _ -> watchedChannel = channelId },
                         ) else ChannelsScreenContent(
                             view = ChannelsView(ChannelsLayout.LIST),
                             initialFocusEnabled = !drawerActive,
                            channelScopeState = resolveChannelScopeState(observation.channelState, null),
                            observation = observation, tagNotice = false, selectedId = { channel.id },
                            playingChannelId = null, connectionUiState = ConnectionUiState.Ready,
                            onSelectChannel = {}, onSelectTag = {}, onDismissTagNotice = {},
                            onRetryConnection = {}, onOpenConnectionSettings = {},
                            onPlay = { id, _ -> plays++; playedChannel = id; requestPlayer() },
                            onShowGuide = { channelId, programme -> guideRequest = channelId; guideProgramme = programme },
                            onPlayRecording = { plays++; start = RecordingPlaybackStart.START_OVER; requestPlayer() },
                        )
                        }
                        if (realRail) at.bernhardberger.tvhplayer.ui.components.SideRail(
                            currentRoute = at.bernhardberger.tvhplayer.ui.AppDestination.CHANNELS,
                            showEpgMenu = true, onRootBack = {}, onNavigate = {},
                        ) { _, drawerActive -> content(drawerActive) } else content(false)
                    }
                    }
                } }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(if (guideDetails) "details-watch" else if (guideMenu) "browse-context-menu" else if (recordings) "recording-list-entry-1" else "channel-row-1").fetchSemanticsNodes().isNotEmpty()
        }
        return session
    }
}
