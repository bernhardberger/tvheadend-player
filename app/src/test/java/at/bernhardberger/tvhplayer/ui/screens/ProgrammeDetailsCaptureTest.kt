package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusRequester
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.ui.player.*
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.guide.ProgrammeDetailsPanel
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingDetailsPanel
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Matched before/after production details hosts; fictional fixture programme, no services. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgrammeDetailsCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val oldLocale = Locale.getDefault()
    private val oldZone = TimeZone.getDefault()
    private lateinit var view: View
    private lateinit var loader: ImageLoader
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()
    private val start = Instant.parse("2026-09-15T19:00:00Z")
    private val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = ArtworkId(1))
    private val description = ("A winter expedition beyond the Arctic Circle. The crew follows the changing light across the ridge, discovering the wildlife and people of the far north. ").repeat(8)

    @Before fun prepare() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        loader = FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(2) to FixtureArt.art("ridge-light")))
    }
    @After fun release() {
        Locale.setDefault(oldLocale)
        TimeZone.setDefault(oldZone)
        loader.shutdown()
    }

    @Test fun guideArtLongDescription() = capture("guide-art-long", guide = true)
    @Test fun guideScheduled() = capture("guide-scheduled", guide = true, scheduled = true)
    @Test fun guideWithoutArt() = capture("guide-no-art", guide = true, art = false)
    @Test fun recordingResume() = capture("recording-resume", guide = false)
    @Test fun recordingFailed() = capture("recording-failed", guide = false, failed = true)
    @Test fun playerGeometryMatchesModalHosts() = capture("player-geometry", guide = false, player = true)
    @Test fun guideWithoutButtonsTrapsPanelFocusAndBackCloses() = emptyPanel(guide = true)
    @Test fun recordingWithoutButtonsTrapsPanelFocusAndBackCloses() = emptyPanel(guide = false)

    @Test fun guideBackFromMoreInfoReturnsToDetailsThenCloses() {
        var shown by mutableStateOf(true)
        val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds,
            title = "Ridge Light", subtitle = "Beyond the Arctic Circle", description = description)
        compose.setContent {
            TVHeadendPlayerTheme {
                if (shown) ProgrammeDetailsPanel(event, channel, null, { start.epochSeconds - 60 },
                    true, {}, { shown = false }, loader, session)
            }
        }
        val moreInfo = ApplicationProvider.getApplicationContext<Application>().getString(R.string.details_read_more)
        compose.onNodeWithText(moreInfo).performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(moreInfo).assertDoesNotExist()
        val back = { compose.runOnIdle { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() } }
        // Like the player: Back leaves the full description first, with focus back on More info.
        back()
        compose.onNodeWithText(moreInfo).assertIsFocused()
        back()
        compose.onNodeWithTag("programme-details-panel").assertDoesNotExist()
        compose.runOnIdle { assertEquals(false, shown) }
    }

    @Test fun guideActionThatSurvivesAnUpdateKeepsFocusAfterAnEmptyStart() {
        var canModify by mutableStateOf(false)
        var now by mutableStateOf(start.epochSeconds - 60)
        val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds, title = "Ridge Light")
        compose.setContent {
            TVHeadendPlayerTheme {
                ProgrammeDetailsPanel(event, channel, null, { now }, canModify, {}, {}, loader, session)
            }
        }
        compose.onNodeWithTag("programme-details-panel").assertIsFocused()
        compose.runOnIdle { canModify = true }
        compose.onNodeWithText("Record").assertIsFocused()
        // The programme starts: Watch joins ahead of Record, and the focused Record stays focused.
        compose.runOnIdle { now = start.epochSeconds + 60 }
        compose.onNodeWithText("Watch").assertExists()
        compose.onNodeWithText("Record").assertIsFocused()
    }

    private fun emptyPanel(guide: Boolean) {
        var shown by mutableStateOf(true)
        var canModify by mutableStateOf(false)
        lateinit var backDispatcher: OnBackPressedDispatcher
        val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds, title = "Ridge Light")
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = event.title, state = DvrEntryState.SCHEDULED)
        compose.setContent {
            TVHeadendPlayerTheme {
                backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                Box(Modifier.fillMaxSize()) {
                    Button(onClick = {}) { Text("Underlying action") }
                    if (shown) {
                        if (guide) ProgrammeDetailsPanel(event, channel, null, { start.epochSeconds - 60 },
                            canModify, {}, { shown = false }, loader, session)
                        else RecordingDetailsPanel(entry, canModify, false, null, true,
                            {}, {}, {}, {}, { shown = false }, loader, session, channel)
                    }
                }
            }
        }
        val panel = compose.onNodeWithTag(if (guide) "programme-details-panel" else "recording-details-panel")
        panel.assertIsFocused()
        compose.onNodeWithText("Close").assertDoesNotExist()
        // A newly available action takes over, then its removal returns focus to the panel.
        compose.runOnIdle { canModify = true }
        compose.onNodeWithText(if (guide) "Record" else "Cancel recording").assertIsFocused()
        compose.runOnIdle { canModify = false }
        panel.assertIsFocused()
        listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight).forEach { direction ->
            panel.performKeyInput { pressKey(direction) }
            panel.assertIsFocused()
        }
        // Robolectric key injection does not dispatch platform Back to the activity/dialog.
        compose.runOnIdle {
            if (guide) (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            else backDispatcher.onBackPressed()
        }
        panel.assertDoesNotExist()
        compose.runOnIdle { assertEquals(false, shown) }
    }

    private fun capture(name: String, guide: Boolean, scheduled: Boolean = false, art: Boolean = true, failed: Boolean = false, player: Boolean = false) {
        val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds,
            title = "Ridge Light", subtitle = "Beyond the Arctic Circle", description = description,
            image = if (art) "imagecache/2" else null)
        val entry = DvrEntry.create(DvrEntryId(1), eventId = event.id, channelId = channel.id,
            title = event.title, subtitle = event.subtitle, description = description, channelName = channel.name,
            start = event.start, stop = event.stop, image = event.image, playPosition = 3723.seconds,
            subscriptionError = if (failed) DvrSubscriptionError.NO_DISK_SPACE else null,
            state = if (scheduled) DvrEntryState.SCHEDULED else if (failed) DvrEntryState.COMPLETED_ERROR else DvrEntryState.COMPLETED)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (player) ProgrammeInfoSheetFrame {
                            ProgramDetails(ProgramDetailsState(), event, emptyList(), start.epochSeconds + 600, channel.name.orEmpty(),
                                { shown, modifier -> ProgrammeHero(shown.image, channel.id, "101", channel.icon, loader, session, modifier) },
                                { null }, true, remember { FocusRequester() }, remember { FocusRequester() }, {})
                        }
                        else if (guide) ProgrammeDetailsPanel(event, channel,
                            entry.takeIf { scheduled }, { start.epochSeconds + if (scheduled) -60 else 600 }, true, {}, {}, loader, session)
                        else RecordingDetailsPanel(entry, true, true, null, true,
                            {}, {}, {}, {}, {}, loader, session, channel)
                    }
                }
            }
        }
        compose.waitForIdle()
        if (!player) compose.onNodeWithText("Close").assertDoesNotExist()
        if (failed) compose.onNodeWithTag("recording-details-delete").assertIsFocused()
        // xhdpi: all three real hosts share x130/x562, y156 and 268dp slots.
        val reading = compose.onNodeWithTag("details-information", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("details-actions", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(260f, reading.left, .1f)
        assertEquals(1124f, actions.left, .1f)
        assertEquals(312f, reading.top, .1f)
        assertEquals(reading.top, actions.top, .1f)
        assertEquals(536f, reading.width, .1f)
        assertEquals(reading.width, actions.width, .1f)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            if (guide) ShadowDialog.getLatestDialog().window!!.decorView.draw(canvas)
        }
        val directory = File("build/outputs/details-unify").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
