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
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
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

    /**
     * The README scene: Ridge Earth's schedule beside the playing picture at 20:15 on 2026-10-04, UTC, with Ridge
     * Light 20:00-21:00 and then Tide Watch, as on the other README screens. One full day repeats for a week.
     */
    @Test fun presentation() = presentationScene(store = false)

    @Test @Config(qualifiers = "en-rUS-w960dp-h540dp-land-xhdpi")
    fun storeSchedule() = presentationScene(store = true)

    private fun presentationScene(store: Boolean) {
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext<Application>(), mapOf(
            ArtworkId(1) to FixtureArt.picon(if (store) "harbor-lights" else "ridge-earth"),
            ArtworkId(2) to FixtureArt.art(if (store) "harbor-lights-live" else "ridge-light"),
            ArtworkId(3) to FixtureArt.art("still-tide-watch"),
        )))
        val hour = Instant.parse("2026-10-04T20:00:00Z").epochSeconds
        val now = hour + 15 * 60
        val channel = Channel.create(ChannelId(if (store) 7 else 1), name = if (store) "Harbor Lights" else "Ridge Earth",
            number = if (store) 7 else 1, icon = ChannelIcon.ImageCache(ArtworkId(1)))
        // Title, minutes after 20:00, length, genre and art: one full repeating day without gaps.
        val evening = if (store) listOf(
            listOf("Harbor Lights Live", -60, 180, "Music", ArtworkId(2)),
            listOf("Backstage at the Harbor", 120, 60, "Music", null),
            listOf("After the Encore", 180, 60, "Music", null),
            listOf("Night Sessions", 240, 360, "Music", null),
            listOf("The Morning Set", 600, 240, "Music", null),
            listOf("Live from the Archive", 840, 300, "Music", null),
            listOf("Soundcheck", 1140, 240, "Music", null),
        ) else listOf(
            listOf("Foundry Hours", -120, 60, "Documentary", null),
            listOf("The Seedkeepers of Moss Lantern Valley", -60, 60, "Nature", null),
            listOf("Ridge Light", 0, 60, "Documentary", ArtworkId(2)),
            listOf("Tide Watch", 60, 60, "Nature", ArtworkId(3)),
            listOf("Snowline", 120, 45, "Nature", null),
            listOf("Six Bridges and a River That Changed Its Mind", 165, 90, "Documentary", null),
            listOf("Ridge at Night", 255, 105, "Nature", null),
            listOf("Night Landscapes", 360, 240, "Nature", null),
            listOf("Morning on the Ridge", 600, 180, "Nature", null),
            listOf("Field Notes from the High Valleys", 780, 180, "Documentary", null),
            listOf("Rivers of the North", 960, 180, "Documentary", null),
            listOf("Mountain Seasons", 1140, 180, "Nature", null),
        )
        val events = (0 until 7).flatMap { day ->
            evening.mapIndexed { index, (title, offset, minutes, genre, art) ->
                val start = hour + day * 86_400L + (offset as Int) * 60L
                EpgEvent.create(EventId(day * 100L + index + 1), channel.id, Instant.fromEpochSeconds(start),
                    Instant.fromEpochSeconds(start + (minutes as Int) * 60L), title = title as String, genre = genre as String,
                    subtitle = "Winter on the ridge".takeIf { day == 0 && title == "Ridge Light" },
                    image = art as ArtworkId?)
            }
        }.filter { it.stop.epochSeconds > now }
        val still = FixtureArt.art(if (store) "still-harbor-lights-live" else "still-ridge-light").asImageBitmap()
        val session = FakeSessionObservation(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).captureCurrentSession()
        var view: View? = null
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                CompositionLocalProvider(LocalWatchingChannel provides channel.id,
                    LocalWatchingIndicator provides ChannelPlaybackIndicator.PLAYING) {
                    ProvideArtworkSession(session) {
                        PlaybackOptionsOverlayFrame(panelWidth = 480.dp, showScrim = false) {
                            ChannelSchedule(channel, events, now, now, loading = false, offline = false, recording = { event ->
                                    // Tonight's Tide Watch is scheduled, as in the Guide.
                                    DvrEntryState.SCHEDULED.takeIf { !store && event.id == EventId(4) }
                                }, onOpen = {}, onClose = {}, onRetry = {}, viewerBehindMs = 0)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.onNodeWithText(if (store) "Harbor Lights Live" else "Ridge Light", useUnmergedTree = true).assertExists()
        compose.runOnIdle {
            val root = requireNotNull(view).rootView
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            Assert.assertEquals(1920, image.width)
            Assert.assertEquals(1080, image.height)
            val output = File("build/outputs/presentation").apply { mkdirs() }
            File(output, if (store) "store-schedule.png" else "player-schedule.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
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
        val channel = Channel.create(ChannelId(9), name = "Amber Stage", number = 9, icon = ChannelIcon.ImageCache(ArtworkId(3)))
        val session = FakeSessionObservation(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).captureCurrentSession()
        fun event(id: Long, programme: JSONObject, start: Long, duration: Long): EpgEvent {
            val title = programme.getString("title")
            return EpgEvent.create(EventId(id), channel.id, Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(start + duration),
                title = title,
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
