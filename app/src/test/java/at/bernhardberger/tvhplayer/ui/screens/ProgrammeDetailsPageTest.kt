package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsPage
import coil3.ImageLoader
import coil3.compose.AsyncImage
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgrammeDetailsPageTest {
    @get:Rule val compose = createComposeRule()
    private val oldLocale = Locale.getDefault()
    private val oldZone = TimeZone.getDefault()
    private val oldClock = AppClock.systemUses24Hour
    private val oldFormat = AppClock.format
    private lateinit var loader: ImageLoader
    private lateinit var view: View
    private val start = Instant.parse("2026-09-15T19:00:00Z")
    private val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = ArtworkId(1))
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()
    private val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds,
        title = "Ridge Light", subtitle = "Beyond the Arctic Circle", image = ArtworkId(2),
        genre = "Documentary", episode = EpgEpisode(null, null, 2, null, 3, null, null, null, "S2 E3"),
        summary = "A winter expedition beyond the Arctic Circle follows the changing light across the ridge.",
        description = "The crew follows the changing light across the ridge, discovering the wildlife and people of the far north. ".repeat(4))
    private val next = EpgEvent.create(EventId(2), channel.id, event.stop, event.stop + 3600.seconds,
        title = "Ridge Light: Northern Journey", image = ArtworkId(2))
    private val later = EpgEvent.create(EventId(3), channel.id, next.stop, next.stop + 2700.seconds,
        title = "Ridge Earth Tonight")

    @Before fun prepare() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        AppClock.format = at.bernhardberger.tvhplayer.settings.ClockFormat.SYSTEM
        AppClock.systemUses24Hour = true
        loader = FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(2) to FixtureArt.art("ridge-light")))
    }
    @After fun release() {
        Locale.setDefault(oldLocale)
        TimeZone.setDefault(oldZone)
        AppClock.systemUses24Hour = oldClock
        AppClock.format = oldFormat
        loader.shutdown()
    }

    @Test fun airingArtAndInitialFocus() {
        show(); compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onNodeWithContentDescription("Starts 19:00, Length 2 h, Left 1 h 52 min").assertExists()
        compose.onNodeWithText("101 · Ridge Earth HD | Documentary").assertExists()
        capture("airing-art")
    }
    @Test fun airingMoreAndReturnFocus() {
        show()
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-record").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertIsFocused()
        compose.onNodeWithTag("details-hero").assertDoesNotExist()
        compose.onNodeWithContentDescription("Genre Documentary, Episode S2 E3").assertExists()
        val descriptionBounds = compose.onNodeWithTag("details-description").getUnclippedBoundsInRoot()
        Assert.assertTrue((descriptionBounds.right - descriptionBounds.left).value <= 560f)
        capture("airing-more")
        compose.onNodeWithTag("details-card-2").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("details-card-2").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-card-3").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-card-3").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("details-card-2").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-record").assertIsFocused()
    }
    @Test fun futureScheduledWithoutArt() { show(future = true); compose.onNodeWithTag("details-hero").assertDoesNotExist(); capture("future-scheduled-noart") }
    @Test fun recordingPartlyWatched() { show(recording = true); compose.onNodeWithTag("details-resume").assertIsFocused(); capture("recording-partly-watched") }
    @Test fun airingWarm() { show(warm = true); capture("airing-warm") }
    @Test fun airingTwelveHour() {
        AppClock.systemUses24Hour = false; show()
        compose.onNodeWithContentDescription("Starts 7:00\u00a0PM, Length 2 h, Left 1 h 52 min").assertExists()
        capture("airing-12h")
    }

    @Test fun missingContentDoesNotReserveSynopsisOrHeroSpace() {
        show(future = true)
        compose.onNodeWithTag("details-synopsis").assertDoesNotExist()
        compose.onNodeWithTag("details-hero").assertDoesNotExist()
        val title = compose.onNodeWithText("Ridge Light").getUnclippedBoundsInRoot()
        val facts = compose.onNodeWithContentDescription("Starts 19:00, Length 2 h").getUnclippedBoundsInRoot()
        Assert.assertEquals(20f, facts.top.value - title.bottom.value, 1f)
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-description").assertDoesNotExist()
        compose.onNodeWithText("Episode").assertDoesNotExist()
    }

    @Test fun largeTextKeepsActionsAndRelatedCardsReachable() {
        show(fontScale = 1.3f)
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-record").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-show_guide").assertIsFocused().assertIsDisplayed()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertIsFocused().assertIsDisplayed()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-show_guide").assertIsFocused().assertIsDisplayed()
    }

    @Test fun durationFormatterOmitsZeroPartsForDetailsAndDialogs() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        Assert.assertEquals("2 h", at.bernhardberger.tvhplayer.ui.common.formatPlaybackDuration(7200, resources))
        Assert.assertEquals("45 min", at.bernhardberger.tvhplayer.ui.common.formatPlaybackDuration(2700, resources))
        Assert.assertEquals("1 h 52 min", at.bernhardberger.tvhplayer.ui.common.formatPlaybackDuration(6720, resources))
    }

    private fun show(recording: Boolean = false, future: Boolean = false, warm: Boolean = false, fontScale: Float = 1f) {
        val shown = if (future) EpgEvent.create(event.id, channel.id, start, event.stop, title = event.title) else event
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = event.title,
            start = start, stop = event.stop, image = event.image.takeUnless { future }, summary = event.summary.takeUnless { future },
            playPosition = 3723.seconds, state = if (future) DvrEntryState.SCHEDULED else DvrEntryState.COMPLETED)
        val now = start.epochSeconds + if (future) -60 else 480
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (warm) {
                            AsyncImage(AppArtworkSource(session, ArtworkId(2)), null, loader,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .84f)))
                        }
                        ProgrammeDetailsPage(shown.takeUnless { recording }, entry.takeIf { recording || future }, channel, now,
                            if (recording) recordingDetailsActions(entry, true, true, true)
                            else programmeDetailsActions(shown, now, entry.takeIf { future }, true, true, false),
                            loader, session, {}, {}, related = if (recording) emptyList() else listOf(next, later))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("build/outputs/u15-details").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
