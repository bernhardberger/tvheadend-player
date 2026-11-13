package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.screens.RecordingPlaybackChoice
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The recording playback card in each of its modes, drawn through the production choice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
@OptIn(TvheadendTestingApi::class, ExperimentalTvheadendApi::class)
class PlaybackChoiceCaptureTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val images = ImageLoaderRule()
    private lateinit var originalZone: TimeZone

    @Before fun setZone() { originalZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restoreZone() { TimeZone.setDefault(originalZone) }

    private class Scene(
        val title: String, val channel: String, val number: Long, val picon: String?, val art: String?,
        val start: String, val minutes: Long, val nowAfter: Long, val running: Boolean,
        val position: Long? = null, val season: Int? = null, val episode: Int? = null, val subtitle: String? = null,
        val path: String = "recording.ts", val watchLive: Boolean = running,
    )

    private val ridge = Scene("Ridge Light", "Ridge Earth", 1, "ridge-earth", "still-ridge-light",
        "2026-10-03T20:00:00Z", 60, 40, running = true, position = 12 * 60, season = 1, episode = 3,
        subtitle = "Winter on the ridge")
    private val kite = Scene("Paper Kite Tales", "Kite Kids", 4, "kite-kids", "still-paper-kite-tales",
        "2026-10-03T08:00:00Z", 30, 13 * 60 + 21, running = false, position = 9 * 60 + 30, season = 2, episode = 4,
        subtitle = "The windy hill")

    @Test fun stillThree() = capture("still-3", ridge)
    @Test fun stillTwo() = capture("still-2", ridge.copy(position = null, season = null, episode = null, subtitle = null))
    @Test fun finishedResume() = capture("finished-resume", kite)
    @Test fun finishedPlay() = capture("finished-play", kite.copy(position = null, season = null, episode = null, subtitle = null))
    @Test fun longTitle() = capture("long-title", ridge.copy(
        title = "Ridge Light: The remarkable journey along the mountain ridge in winter",
        subtitle = "Winter on the ridge: the long climb to the summit hut before the first snow"))
    @Test fun waiting() = capture("waiting", ridge.copy(path = "recording.mp4"))
    @Test fun poster() = capture("poster", Scene("Glass Harbor", "Amber Stage", 6, "amber-stage",
        "glass-harbor-poster-2x3", "2026-10-03T21:00:00Z", 90, 25, running = true, subtitle = "Part 2"))
    @Test fun logo() = capture("logo", Scene("Field Desk", "Northline News", 3, "northline-news", null,
        "2026-10-02T19:30:00Z", 30, 20, running = true, position = 8 * 60))
    @Test fun noLogo() = capture("nologo", Scene("Night Ferry", "Meridian Two", 14, null, null,
        "2026-10-01T22:00:00Z", 45, 15, running = true, position = 5 * 60))

    private fun Scene.copy(
        title: String = this.title, position: Long? = this.position, season: Int? = this.season,
        episode: Int? = this.episode, subtitle: String? = this.subtitle, path: String = this.path,
    ) = Scene(title, channel, number, picon, art, start, minutes, nowAfter, running, position, season, episode,
        subtitle, path, watchLive)

    private fun capture(name: String, scene: Scene) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        images.install(FixtureArt.imageLoader(context, buildMap {
            scene.picon?.let { put(ArtworkId(1), FixtureArt.picon(it)) }
            scene.art?.let { put(ArtworkId(2), FixtureArt.art(it)) }
        }))
        val start = Instant.parse(scene.start)
        val stop = Instant.fromEpochSeconds(start.epochSeconds + scene.minutes * 60)
        val now = start.epochSeconds + scene.nowAfter * 60
        val recorded = Instant.fromEpochSeconds(if (scene.running) now else stop.epochSeconds)
        val channel = Channel.create(ChannelId(1), name = scene.channel, number = scene.number,
            icon = scene.picon?.let { ChannelIcon.ImageCache(ArtworkId(1)) })
        val episode = scene.season?.let { EpgEpisode(null, null, it.toLong(), null, scene.episode?.toLong(), null, null, null, null) }
        val entry = DvrEntry.create(DvrEntryId(1), title = scene.title, channelId = channel.id,
            channelName = scene.channel, state = if (scene.running) DvrEntryState.RECORDING else DvrEntryState.COMPLETED,
            start = start, stop = stop, playPosition = scene.position?.seconds,
            subtitle = scene.subtitle, episode = episode, image = scene.art?.let { ArtworkId(2) },
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = scene.path, start = start, stop = recorded)))
        val observation = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED,
                dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))),
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED)).observation.value
        lateinit var view: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize().background(TvSurfaceColors.containerLowest)) {
                        RecordingPlaybackChoice(entry, observation.recordingPlaybackAdmission(entry), observation,
                            channel, now, {}, {}, if (scene.watchLive) ({}) else null)
                    }
                }
            }
        }
        repeat(8) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
        val first = when {
            scene.path.endsWith(".mp4") && scene.running -> "recording-watch-live"
            scene.position != null -> "recording-resume"
            else -> "recording-start-over"
        }
        compose.onNodeWithTag(first).assertIsFocused()
        val card = compose.onNodeWithTag("centred-dialog-card").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/centre-dialog-captures")
            CaptureOutput.writePng(File(directory, "play-$name.png"), bitmap)
            CaptureOutput.writeText(File(directory, "play-$name.txt")) {
                "canvas=960x540dp\npixels=${view.width}x${view.height}\ndensity=2\nlocale=en\nfontScale=1.0\nzone=UTC\n" +
                    "focus=$first\ncardDp=${card.width / 2}x${card.height / 2} at (${card.left / 2},${card.top / 2})\n" +
                    "Production RecordingPlaybackChoice over a plain surface with fixture art; offline Robolectric capture.\n"
            }
            bitmap.recycle()
        }
    }
}
