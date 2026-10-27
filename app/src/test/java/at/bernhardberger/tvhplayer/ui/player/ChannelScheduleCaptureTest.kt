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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.testutil.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.*
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlin.time.Instant
import org.json.JSONObject
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
            ArtworkId(3) to FixtureArt.picon("amber-stage"),
            ArtworkId(4) to FixtureArt.art("kettle-hour"),
            ArtworkId(5) to FixtureArt.art("glass-harbor"),
        )))
    }

    @After fun restore() { TimeZone.setDefault(zone) }

    @Test fun englishStates() = capture("en", listOf("live", "behind-live", "future", "tomorrow-gap", "end", "failed", "loading", "loading-more", "empty", "offline", "no-art"))
    @Test fun englishBackToTabs() = capture("en", listOf("back-to-tabs"))
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLongTitle() = capture("de", listOf("long-title"))

    @Test fun englishDetailsOverPlayer() = details("en", listOf("playing", "other-art", "other-no-art", "long-title"))
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanDetailsLongTitle() = details("de", listOf("long-title"))

    /** The details page a schedule row opens over the video: the watched programme, or another one framed. */
    private fun details(locale: String, scenes: List<String>) {
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
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                key(scene) {
                    val playing = scene == "playing"
                    val event = EpgEvent.create(EventId(if (playing) 2 else 3), ChannelId(1),
                        Instant.fromEpochSeconds(if (playing) now - 1800 else now + 1800),
                        Instant.fromEpochSeconds(if (playing) now + 1800 else now + 5400),
                        title = if (scene == "long-title") {
                            if (locale == "de") "Ridge Light – Auf den Spuren der letzten unberührten Bergwälder Europas"
                            else "Ridge Light – Following the last untouched mountain forests of Europe"
                        } else "Ridge Light",
                        subtitle = "Beyond the Arctic Circle",
                        description = "Light returns to the ridge after the polar night. A crew follows the first weeks of spring " +
                            "as herders move north and the forest wakes beneath the snow.",
                        image = ArtworkId(2).takeUnless { scene == "other-no-art" })
                    val actions = listOfNotNull(BrowseContextAction.CONTINUE_WATCHING.takeIf { playing },
                        BrowseContextAction.RECORD, BrowseContextAction.SHOW_GUIDE)
                    ProvideArtworkSession(session) {
                        PlayerDetailsSurface {
                            ProgrammeDetailsPage(event, null, channel, now, actions, {}, {})
                        }
                    }
                }
            }
        }
        val output = File("../build/review/schedule-polish").apply { mkdirs() }
        for (name in scenes) {
            compose.runOnIdle { scene = name }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1500)
            compose.waitForIdle()
            compose.runOnIdle {
                val root = requireNotNull(view).rootView
                val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(image))
                File(output, "$locale-details-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
        }
    }

    /**
     * Amber Stage from the fictional receiver EPG on Tuesday evening, 15 September, during The Kettle Hour. The
     * fixture's Tuesday, Wednesday and Monday repeat as a week so every day tab holds a full day. The on-air episode
     * records, the series' Parcel Office episodes tonight are scheduled from the fixture's DVR, and the failed scene's
     * earlier Parcel Office episode failed in the timeshift buffer behind the viewer.
     */
    private fun capture(locale: String, scenes: List<String>) {
        val fixture = JSONObject(checkNotNull(javaClass.getResourceAsStream("/fixture-epg/epg.json")).use { it.readBytes() }.toString(Charsets.UTF_8))
        val vienna = ZoneId.of(fixture.getString("timezone"))
        TimeZone.setDefault(TimeZone.getTimeZone(vienna))
        fun at(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 9, day, hour, minute).atZone(vienna).toEpochSecond()
        val programmes = fixture.getJSONArray("events").let { all -> List(all.length()) { all.getJSONObject(it) } }
            .filter { it.getString("channel_id") == "ch-amber-stage" }
        fun startOf(programme: JSONObject) = OffsetDateTime.parse(programme.getString("start")).toEpochSecond()
        val week = (0..6).flatMap { day ->
            val source = (day + 1) % 3
            programmes.filter { OffsetDateTime.parse(it.getString("start")).atZoneSameInstant(vienna).dayOfMonth == 14 + source }
                .map { Triple(it, startOf(it) + (day + 1 - source) * 86_400L, it.getLong("duration_min") * 60) }
        }
        val now = at(15, 19, 5)
        val onAir = at(15, 18, 47)
        var scene by mutableStateOf(scenes.first())
        var view: View? = null
        val still = FixtureArt.art("still-kettle-hour").asImageBitmap()
        val channel = Channel.create(ChannelId(9), name = "Amber Stage", number = 9, icon = ArtworkId(3))
        val session = FakeSessionObservation(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).captureCurrentSession()
        fun event(id: Long, programme: JSONObject, start: Long, duration: Long): EpgEvent {
            val title = programme.getString("title")
            return EpgEvent.create(EventId(id), channel.id, Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(start + duration),
                title = if (scene == "long-title" && start == onAir) "The Kettle Hour – Brühe, Brot und Pfannengerichte für ganz gewöhnliche Wochentagabende" else title,
                subtitle = programme.optString("subtitle").ifEmpty { null }, summary = programme.getString("synopsis"),
                genre = programme.getString("genre"), contentGenre = ContentGenre.fromContentType(contentType(programme.getString("genre"))),
                image = when (title) { "The Kettle Hour" -> ArtworkId(4); "Glass Harbor" -> ArtworkId(5); else -> null }?.takeUnless { scene == "no-art" })
        }
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                key(scene) {
                    val viewer = when (scene) { "behind-live" -> now - 1800; "failed" -> now - 3000; else -> now }
                    // Only a timeshift buffer keeps ended programmes; it reaches two hours back here.
                    val historyStart = if (scene in listOf("behind-live", "failed")) now - 7200 else now
                    val events = when (scene) {
                        "loading", "empty" -> emptyList()
                        else -> week.mapIndexed { index, (programme, start, duration) -> event(index + 1L, programme, start, duration) }
                            .filter { it.stop.epochSeconds > historyStart }
                            .filterNot { scene == "tomorrow-gap" && it.start.epochSeconds in at(16, 2, 30) until at(16, 6, 0) }
                            .filterNot { scene == "loading-more" && it.start.epochSeconds >= at(17, 0, 0) }
                    }
                    CompositionLocalProvider(LocalWatchingChannel provides channel.id,
                        LocalWatchingIndicator provides ChannelPlaybackIndicator.PLAYING) {
                    ProvideArtworkSession(session) {
                        PlaybackOptionsOverlayFrame(panelWidth = 480.dp, showScrim = false) {
                            ChannelSchedule(channel, events, now, viewer,
                                scene in listOf("loading", "loading-more"), scene == "offline",
                                recording = {
                                    when (it.start.epochSeconds) {
                                        onAir -> DvrEntryState.RECORDING
                                        at(15, 17, 10) -> DvrEntryState.COMPLETED_ERROR.takeIf { scene == "failed" }
                                        at(15, 20, 15), at(15, 21, 0), at(16, 14, 0) -> DvrEntryState.SCHEDULED
                                        else -> null
                                    }
                                },
                                onOpen = {}, onClose = {}, onRetry = {},
                                initialEvent = when (scene) {
                                    "back-to-tabs" -> events.first { it.start.epochSeconds >= at(18, 15, 0) }
                                    "future" -> events.first { it.start.epochSeconds >= at(18, 0, 0) }
                                    "tomorrow-gap" -> events.first { it.start.epochSeconds == at(16, 6, 0) }
                                    "end" -> events.last()
                                    else -> scheduleInitialEvent(events, viewer, now)
                                }, viewerBehindMs = (now - viewer) * 1000)
                        }
                    }
                    }
                }
            }
        }
        val output = File("../build/review/schedule-polish").apply { mkdirs() }
        for (name in scenes) {
            compose.runOnIdle { scene = name }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            if (name == "back-to-tabs") {
                compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                compose.onRoot().performKeyInput { pressKey(Key.Back) }
                compose.onNode(isSelected()).assertIsFocused()
                compose.mainClock.advanceTimeBy(1000)
                compose.waitForIdle()
            }
            compose.runOnIdle {
                val root = requireNotNull(view).rootView
                val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(image))
                File(output, "$locale-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
        }
    }

    /** The DVB content type a server would send for the fixture's free-text genre, as in the Guide's fixture scene. */
    private fun contentType(genre: String): Long = when {
        genre.startsWith("news") -> 0x20L
        genre.endsWith("film") || genre.startsWith("drama") || genre.startsWith("comedy") -> 0x10L
        genre.startsWith("quiz") || genre.startsWith("talk") || genre == "interview" -> 0x30L
        genre.startsWith("cookery") || "travel" in genre || genre.startsWith("lifestyle") -> 0xA0L
        genre.startsWith("magazine") -> 0x80L
        else -> error("Unmapped fixture genre $genre")
    }
}
