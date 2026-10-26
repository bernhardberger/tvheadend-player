package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.testutil.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.*
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant
import org.junit.*
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
class ChannelScheduleCaptureTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val zone = TimeZone.getDefault()

    @Before fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext<Application>(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"),
            ArtworkId(2) to FixtureArt.art("ridge-light"),
        )))
    }

    @After fun restore() { TimeZone.setDefault(zone) }

    @Test fun englishStates() = capture("en", listOf("live", "behind-live", "future", "tomorrow-gap", "end", "failed", "loading", "empty", "offline", "no-art"))
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLongTitle() = capture("de", listOf("long-title"))

    private fun capture(locale: String, scenes: List<String>) {
        val now = 1789246200L
        var scene by mutableStateOf(scenes.first())
        var view: View? = null
        val still = FixtureArt.art("still-ridge-light").asImageBitmap()
        val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = ArtworkId(1))
        val session = FakeSessionObservation(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).captureCurrentSession()
        fun event(id: Long, start: Long, end: Long) = EpgEvent.create(EventId(id), ChannelId(1),
            Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(end),
            title = if (scene == "long-title") "Ridge Light – Auf den Spuren der letzten unberührten Bergwälder" else "Ridge Light",
            image = ArtworkId(2).takeUnless { scene == "no-art" })
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                key(scene) {
                    val events = if (scene in listOf("loading", "empty")) emptyList() else listOf(
                        event(1, now - 5400, now - 1800), event(2, now - 1800, now + 1800),
                        event(3, now + 1800, now + 5400), event(4, now + 86400, now + 90000),
                        event(5, now + 93600, now + 97200), event(6, now + 97200, now + 100800))
                    CompositionLocalProvider(LocalWatchingChannel provides channel.id,
                        LocalWatchingIndicator provides ChannelPlaybackIndicator.PLAYING) {
                    ProvideArtworkSession(session) {
                        PlaybackOptionsOverlayFrame(panelWidth = 480.dp, showScrim = false) {
                            ChannelSchedule(channel, events, now, if (scene == "behind-live") now - 3600 else now,
                                scene == "loading", scene == "offline",
                                recording = { when (it.id.value) { 2L -> if (scene == "failed") DvrEntryState.COMPLETED_ERROR else DvrEntryState.RECORDING; 3L -> DvrEntryState.SCHEDULED; else -> null } },
                                onOpen = {}, onClose = {}, onRetry = {},
                                initialEvent = when (scene) {
                                    "future" -> events[2]
                                    "tomorrow-gap" -> events[3]
                                    "end" -> events.last()
                                    else -> scheduleInitialEvent(events, if (scene == "behind-live") now - 3600 else now, now)
                                })
                        }
                    }
                    }
                }
            }
        }
        val output = File("../build/review/channel-schedule").apply { mkdirs() }
        for (name in scenes) {
            compose.runOnIdle { scene = name }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            compose.runOnIdle {
                val root = requireNotNull(view).rootView
                val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(image))
                File(output, "$locale-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
        }
    }
}
