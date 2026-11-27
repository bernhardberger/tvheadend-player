package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent as NativeKey
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.RecordingEndKind
import at.bernhardberger.tvhplayer.core.RecordingEndOffer
import at.bernhardberger.tvhplayer.core.RecordingEndStart
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.testutil.changeState
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.TwoColumnDialog
import at.bernhardberger.tvhplayer.ui.components.TwoColumnDialogButton
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land")
class RecordingEndScreenTest {
    @get:Rule val compose = createComposeRule()
    private val first = episode(2, "Second episode")
    private val second = episode(3, "Third episode")
    private val live = RecordingEndOffer.WatchLive(ChannelId(1), null)
    private val offers = mutableStateOf(listOf(first, second, live))
    private val shown = mutableStateOf(true)
    private val played = mutableListOf<RecordingEndOffer>()
    private var closed = 0
    private lateinit var back: OnBackPressedDispatcher
    private lateinit var view: View

    @Test fun firstHeldOkOnlyStopsCountdownAndTheNextOkPlays() {
        show()
        card(0).assertIsFocused()
        compose.runOnIdle { center(NativeKey.ACTION_UP) }
        compose.settleFrames(1)
        countdown().assertExists()
        compose.runOnIdle { center(NativeKey.ACTION_DOWN) }
        compose.settleFrames(1)
        compose.runOnIdle {
            center(NativeKey.ACTION_DOWN, repeat = 1, flags = NativeKey.FLAG_LONG_PRESS)
            center(NativeKey.ACTION_DOWN, repeat = 2)
            center(NativeKey.ACTION_UP)
        }
        compose.settleFrames(1)
        countdown().assertDoesNotExist()
        compose.onNodeWithTag("end-test-menu").assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<RecordingEndOffer>(), played) }
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf(first), played) }
    }

    @Test fun removingFirstPreservesShownTimeAndFocusAcrossHiding() {
        show()
        compose.settleFrames(1, 4_000)
        compose.changeState { shown.value = false }
        compose.settleFrames(1, 12_000)
        compose.changeState { offers.value = listOf(second, live); shown.value = true }
        compose.settleFrames(3)
        card(0).assertIsFocused()
        compose.settleFrames(1, 4_000)
        compose.runOnIdle { assertEquals(emptyList<RecordingEndOffer>(), played) }
        compose.settleFrames(1, 2_000)
        compose.runOnIdle { assertEquals(listOf(second), played) }
    }

    @Test fun navigationStopsAtEdgesAndMenuBackRestoresFocus() {
        show(auto = false)
        countdown().assertDoesNotExist()
        compose.onNodeWithTag("recording-skip-countdown", useUnmergedTree = true).assertDoesNotExist()
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionRight)
        card(0).assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.changeState { offers.value = listOf(live, first, second) }
        compose.settleFrames(3)
        compose.pressKey(Key.DirectionDown)
        card(1).assertIsFocused()
        compose.pressKey(Key.Menu, settleFrames = 3)
        compose.onNodeWithTag("end-test-menu").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.settleFrames(3)
        compose.onNodeWithTag("end-test-menu").assertDoesNotExist()
        card(1).assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf(first), played) }
    }

    @Test fun onlyLiveNeverCountsDownAndEmptyCloses() {
        offers.value = listOf(live)
        show()
        countdown().assertDoesNotExist()
        compose.changeState { offers.value = emptyList() }
        compose.settleFrames(2)
        compose.runOnIdle { assertEquals(1, closed); assertEquals(emptyList<RecordingEndOffer>(), played) }
    }

    private fun show(auto: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            view = LocalView.current
            TVHeadendPlayerTheme {
                RecordingEndScreen(
                    ended = DvrEntry.create(DvrEntryId(1), title = "First episode"),
                    offers = offers.value,
                    observation = SessionObservation.create(),
                    origin = "Recordings",
                    autoContinue = auto,
                    onPlay = { played += it },
                    onClose = { closed++ },
                    nowSec = { 1_700_000_000 },
                    shown = shown.value,
                    menu = { _, dismiss ->
                        val focus = remember { FocusRequester() }
                        TwoColumnDialog("Actions", "", dismiss, dismissLevel = true) {
                            TwoColumnDialogButton("Watch", dismiss, Modifier.focusRequester(focus).testTag("end-test-menu"))
                            LaunchedEffect(Unit) { focus.requestFocus() }
                        }
                    },
                )
            }
        }
        compose.settleFrames(3)
    }

    private fun card(index: Int) = compose.onNodeWithTag("recording-end-card-$index")
    private fun countdown() = compose.onNodeWithTag("recording-end-countdown")

    private fun center(action: Int, repeat: Int = 0, flags: Int = 0) {
        view.dispatchKeyEvent(NativeKey(0L, 0L, action, NativeKey.KEYCODE_DPAD_CENTER, repeat,
            0, 0, 0, flags))
    }

    private fun episode(id: Long, title: String) = RecordingEndOffer.Recording(
        DvrEntry.create(DvrEntryId(id), title = title), RecordingEndKind.NEXT_EPISODE, RecordingEndStart.FromBeginning,
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
@OptIn(TvheadendTestingApi::class)
class RecordingEndScreenCaptureTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val images = ImageLoaderRule()
    private lateinit var defaultZone: TimeZone

    @Before fun useUtc() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() {
        TimeZone.setDefault(defaultZone)
    }

    @Test fun oneOffer() = capture("E4-1-offer", 1)
    @Test fun twoOffers() = capture("E4-2-offers", 2)
    @Test fun fourOffers() = capture("E4-4-offers", 4)
    @Test fun stress() = capture("E4-stress", 4, stress = true)

    private fun capture(name: String, count: Int, stress: Boolean = false) {
        val now = Instant.parse("2026-11-27T16:52:00Z")
        val logo = ArtworkId(1)
        val art = ArtworkId(2)
        val channel = Channel.create(ChannelId(1),
            name = if (stress) "Ridge Earth Documentary and Natural History" else "Ridge Earth",
            icon = ChannelIcon.ImageCache(logo))
        val title = if (stress) "Ridge Light: The remarkable journey along the mountain ridge before the first snow" else "Ridge Light"
        val still = FixtureArt.art("still-ridge-light").asImageBitmap()
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), mapOf(
            logo to FixtureArt.picon("ridge-earth"), art to FixtureArt.art("ridge-light"),
        )))
        val ended = DvrEntry.create(DvrEntryId(1), title = title, channelId = channel.id)
        val part = DvrEntry.create(DvrEntryId(2), title = title,
            subtitle = if (stress) "The first mountain snowfall on the north ridge" else "The first mountain snowfall",
            channelId = channel.id, image = art, start = now - 12.minutes, stop = now + 73.minutes,
            state = DvrEntryState.RECORDING,
            files = listOf(TvheadendTestResultFactory.recordingFile(
                serverPath = "part.ts", start = now - 12.minutes, stop = now + 7.minutes)))
        val next = DvrEntry.create(DvrEntryId(3), title = title, subtitle = "Through the winter forest",
            channelId = channel.id, image = art, start = now - 1440.minutes, stop = now - 1380.minutes,
            episode = EpgEpisode(null, null, 3, null, 12, null, null, null, null),
            files = listOf(TvheadendTestResultFactory.recordingFile(
                serverPath = "episode.ts", start = now - 1440.minutes, stop = now - 1380.minutes)))
        val other = DvrEntry.create(DvrEntryId(4), title = "Tide Watch", channelId = channel.id,
            start = now - 10080.minutes)
        val cards = listOf(
            RecordingEndOffer.Recording(part, RecordingEndKind.CHAIN_PART, RecordingEndStart.AtTime(now)),
            RecordingEndOffer.Recording(next, RecordingEndKind.NEXT_EPISODE, RecordingEndStart.Resume(760.seconds)),
            RecordingEndOffer.Recording(other, RecordingEndKind.OTHER_EPISODE, RecordingEndStart.FromBeginning),
            RecordingEndOffer.WatchLive(channel.id, null),
        ).take(count)
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(ended, part, next, other))),
        )
        val session = FakeSessionObservation(observation).captureCurrentSession()
        lateinit var view: View
        lateinit var focus: FocusManager
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            focus = LocalFocusManager.current
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme { ProvideArtworkSession(session, observation) {
                    Box(Modifier.fillMaxSize()) {
                        Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        RecordingEndScreen(ended, cards, observation, "Recordings", true, {}, {}, { _, _ -> },
                            nowSec = { now.epochSeconds })
                    }
                } }
            }
        }
        compose.settleFrames(10)
        val card = compose.onNodeWithTag("recording-end-card-0")
        card.assertIsFocused()
        compose.runOnIdle { write(view, "$name-focused") }
        compose.changeState { focus.clearFocus(force = true) }
        compose.settleFrames(3)
        card.assertIsNotFocused()
        compose.runOnIdle { write(view, "$name-unfocused") }
    }

    private fun write(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        CaptureOutput.writePng(File("../artifacts/review-32-sliceB/captures/$name.png"), bitmap)
        bitmap.recycle()
    }
}
