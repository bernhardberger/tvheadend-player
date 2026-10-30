package at.bernhardberger.tvhplayer.ui.screens.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.ContentCategory
import at.bernhardberger.tvheadend.sdk.core.ContentGenre
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRating
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.HomeInput
import at.bernhardberger.tvhplayer.core.HomePlaying
import at.bernhardberger.tvhplayer.core.homeInitialFocus
import at.bernhardberger.tvhplayer.core.homeSections
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.LocalWarmPlayback
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.WarmPlaybackScrim
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.LiveViewerPosition
import at.bernhardberger.tvhplayer.ui.components.LocalLivePosition
import at.bernhardberger.tvhplayer.ui.components.LocalWatchingChannel
import at.bernhardberger.tvhplayer.ui.components.LocalWatchingIndicator
import at.bernhardberger.tvhplayer.ui.components.LocalWatchingTarget
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.railNowPlaying
import at.bernhardberger.tvhplayer.ui.screens.ChannelsLayoutFixture
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone

/**
 * S5 Home page captures: the production [HomeContent] page driven by the real
 * [homeSections] policy over the fictional fixture pack, at 20:15. Each capture focuses the same
 * card the approved mockup captured, so the PNGs compare state by state.
 */
@Category(VisualCapture::class)
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = "en-w960dp-h540dp-land-xhdpi",
    instrumentedPackages = [
        "at.bernhardberger.tvhplayer.ui.screens",
        "at.bernhardberger.tvhplayer.ui.screens.home",
        "at.bernhardberger.tvhplayer.ui.common",
    ],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
class HomeCaptureTest {
    @get:Rule
    val images = ImageLoaderRule()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var view: View
    private val out = File(System.getProperty("home.capture.out") ?: "build/outputs/home")
        .apply { mkdirs() }
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun browse() = atEvening {
        show(Kind.BROWSE)
        capture("1-default", "home-recent-0")
        key(Key.DirectionDown)
        capture("2-on-now", "home-onnow-0")
        key(Key.DirectionDown)
        capture("3-genres", "home-genre-0")
        key(Key.DirectionDown)
        capture("4-continue", "home-shelf-0")
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        capture("4b-upcoming", "home-upcoming")
    }

    @Test
    fun warm() = atEvening {
        show(Kind.WARM)
        capture("5-warm", "home-activity-0")
    }

    @Test
    fun firstRun() = atEvening {
        show(Kind.FIRST_RUN)
        capture("6-first-run", "home-onnow-0")
    }

    /** One, two and three current-activity cards, each card half the content width. */
    @Test
    fun activityOne() = activity(1)

    @Test
    fun activityTwo() = activity(2)

    @Test
    fun activityThree() = activity(3)

    private fun activity(items: Int) = atEvening {
        show(Kind.WARM, items)
        capture("act-half-$items", "home-activity-0")
    }

    private enum class Kind { BROWSE, WARM, FIRST_RUN }

    private data class Scene(
        val observation: SessionObservation,
        val input: HomeInput,
        val playing: Channel?,
        val nowSec: Long,
    )

    private fun show(kind: Kind, activityItems: Int = 2) {
        val scene = scene(kind, activityItems)
        val sections = homeSections(scene.input)
        installFixtures()
        val playing = scene.playing
        val nowPlaying = playing?.let {
            railNowPlaying(
                AppPlaybackTarget.Live(it.id),
                AppPlaybackState.Playing,
                scene.observation,
                warmRecordingId = null,
                stopWhileBrowsing = false,
            )
        }
        val livePosition = mutableStateOf(
            playing?.let { LiveViewerPosition(it.id, scene.nowSec, behindLive = false) },
        )
        val video = if (kind == Kind.WARM) FixtureArt.art("still-northline-studio") else null
        compose.setContent {
            CompositionLocalProvider(
                LocalWarmPlayback provides (kind == Kind.WARM),
                LocalWatchingTarget provides nowPlaying?.target,
                LocalWatchingChannel provides playing?.id,
                LocalWatchingIndicator provides
                    if (playing != null) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.NONE,
                LocalLivePosition provides livePosition.takeIf { playing != null },
            ) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        video?.let {
                            Image(
                                it.asImageBitmap(),
                                null,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            WarmPlaybackScrim()
                        }
                        ProvideArtworkSession(scene.observation.currentSession) {
                            SideRail(
                                currentRoute = AppDestination.HOME,
                                showEpgMenu = true,
                                onRootBack = {},
                                onNavigate = {},
                                rootRoute = AppDestination.HOME,
                                availableDestinations = setOf(
                                    AppDestination.HOME,
                                    AppDestination.CHANNELS,
                                    AppDestination.GUIDE,
                                    AppDestination.RECORDINGS,
                                    AppDestination.SETTINGS,
                                ),
                                nowPlaying = nowPlaying,
                                livePauseAvailable = true,
                                programmeAt = { id, at ->
                                    scene.observation.eventAt(id, Instant.fromEpochSeconds(at))
                                },
                                animateNowPlaying = false,
                                nowSecProvider = { scene.nowSec },
                            ) { padding, _ ->
                                HomeContent(
                                    sections = sections,
                                    contentPadding = padding,
                                    initialFocus = homeInitialFocus(sections),
                                    onFocused = {},
                                    modifier = Modifier.fillMaxSize(),
                                    observation = scene.observation,
                                    input = scene.input,
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun installFixtures() {
        images.install(
            FixtureArt.imageLoader(
                context,
                buildMap {
                    ChannelsLayoutFixture.PICONS.forEachIndexed { index, name ->
                        if (index + 1 != 14) put(ArtworkId(index + 1), FixtureArt.picon(name))
                    }
                    ChannelsLayoutFixture.ART.forEachIndexed { index, name ->
                        put(ArtworkId(101 + index), FixtureArt.art(name))
                    }
                    listOf("glass-harbor", "lantern-road", "river-court", "field-desk", "little-orbit")
                        .forEachIndexed { index, name ->
                            put(ArtworkId(201 + index), FixtureArt.art(name))
                        }
                },
            ),
        )
    }

    private fun scene(kind: Kind, activityItems: Int): Scene {
        val now = 1_791_144_900L
        val channels = ChannelsLayoutFixture.LINE_UP.mapIndexed { index, (name, _) ->
            Channel.create(
                id = ChannelId(index + 1L),
                name = if (index == 1) "Harbor Sport" else name,
                number = index + 1L,
                icon = ChannelIcon.ImageCache(ArtworkId(index + 1)),
                tagIds = listOf(ChannelTagId(1)),
            )
        }
        val titles = listOf(
            "Ridge Light", "Harbor Kickoff", "Northline Tonight", "Paper Kite Tales",
            "Ion Wake", "The Kettle Hour", "Harbor Lights Live", "Stories by Lamplight",
        )
        val nextTitles = listOf(
            "Tide Watch", "River Court", "Field Desk", "Little Orbit",
            "Lantern Road", "Glass Harbor", "Harbor Lights Live", "Night Readings",
        )
        val synopses = listOf(
            "Seasonal mountain light, forests and weather moving over a ridge.",
            "Match coverage from a floodlit stadium as Harbor FC meet Amber Town.",
            "Evening studio news from a regional desk with two anchors.",
            "2D animation about a paper kite over painted hills.",
            "Sci-fi anime: a courier rides a magnetic rail through Ion Harbor.",
            "A warm kitchen program about broths, bread and weeknight pans.",
            "A small-hall concert with a quartet.",
            "Short stories read aloud, one each evening.",
        )
        val genreCodes = listOf(
            ContentCategory.MOVIE_DRAMA, ContentCategory.SPORTS, ContentCategory.NEWS_CURRENT_AFFAIRS,
            ContentCategory.CHILDREN_YOUTH, ContentCategory.EDUCATION_SCIENCE_FACTUAL,
            ContentCategory.MUSIC_BALLET_DANCE, ContentCategory.SHOW_GAME_SHOW, null,
        )
        val currentEvents = titles.indices.map { index ->
            val channel = channels[index]
            val switch = if (index == 0) now + 45 * 60 else now + (25 + index * 5) * 60
            EpgEvent.create(
                id = EventId(channel.id.value * 100),
                channelId = channel.id,
                start = Instant.fromEpochSeconds(now - (if (index == 0) 15 else 10 + index * 7) * 60),
                stop = Instant.fromEpochSeconds(switch),
                title = titles[index],
                subtitle = "Winter on the ridge".takeIf { index == 0 },
                genre = "Documentary".takeIf { index == 0 },
                episode = EpgEpisode(null, null, 2, 5, 3, 10, null, null, "S2 E3").takeIf { index == 0 },
                copyrightYear = 2024L.takeIf { index == 0 },
                rating = EpgRating(12, "XMLTV:FSK:12", null, null, null, null).takeIf { index == 0 },
                summary = synopses[index],
                image = ArtworkId(101 + index).takeIf { index != 5 && index < 7 },
                contentGenre = genreCodes[index]?.let { ContentGenre.fromContentType((it.code shl 4).toLong()) },
                nextEventId = EventId(channel.id.value * 100 + 1),
            )
        }
        val upcomingEvents = titles.indices.map { index ->
            val channel = channels[index]
            val switch = if (index == 0) now + 45 * 60 else now + (25 + index * 5) * 60
            EpgEvent.create(
                id = EventId(channel.id.value * 100 + 1),
                channelId = channel.id,
                start = Instant.fromEpochSeconds(switch),
                stop = Instant.fromEpochSeconds(switch + 3600),
                title = nextTitles[index],
            )
        }
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.ALLOWED,
                ),
            ),
            channelState = ChannelRepositoryState.Current(
                ChannelCatalog.create(
                    channels,
                    listOf(
                        ChannelTag.create(
                            ChannelTagId(1),
                            name = "Favorites",
                            channelIds = channels.map { it.id },
                        ),
                    ),
                ),
            ),
            epgState = EpgRepositoryState.Current(
                EpgSnapshot.create(
                    events = currentEvents + upcomingEvents,
                    coverages = channels.map {
                        EpgCoverage.create(
                            channelId = it.id,
                            coveredFrom = Instant.fromEpochSeconds(now - 86400),
                            coveredTo = Instant.fromEpochSeconds(now + 7 * 86400),
                        )
                    },
                ),
            ),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = dvrEntries(kind, activityItems, now))),
        )
        val firstRun = kind == Kind.FIRST_RUN
        val warm = kind == Kind.WARM
        val recentIds = if (warm) {
            listOf(2, 4, 1, 7, 5, 12, 14, 16)
        } else {
            listOf(3, 2, 4, 1, 7, 5, 12, 14)
        }
        val input = HomeInput(
            channels = channels,
            currentEvents = currentEvents.mapNotNull { event -> event.channelId?.let { it to event } }.toMap(),
            upcomingEvents = upcomingEvents,
            recentChannelIds = if (firstRun) emptyList() else recentIds.map { ChannelId(it.toLong()) },
            dvrEntries = dvrEntries(kind, activityItems, now),
            admission = { entry ->
                resumeMinutes[entry.id]
                    ?.let {
                        TvheadendTestResultFactory.completedRecordingAdmission(
                            it.minutes,
                            RecordingProgressCapability.SUPPORTED,
                        )
                    }
            },
            playing = channels.firstOrNull { it.id == ChannelId(3) }?.let {
                HomePlaying.LiveChannel(it.id)
            }.takeIf { warm },
            nowSec = now,
        )
        return Scene(
            observation = observation,
            input = input,
            playing = channels.firstOrNull { it.id == ChannelId(3) }?.takeIf { warm },
            nowSec = now,
        )
    }

    private val resumeMinutes = mapOf(
        DvrEntryId(201) to 85L,
        DvrEntryId(202) to 12L,
        DvrEntryId(203) to 40L,
    )

    private fun dvrEntries(kind: Kind, activityItems: Int, now: Long): List<DvrEntry> {
        val warm = kind == Kind.WARM
        val firstRun = kind == Kind.FIRST_RUN
        return buildList {
            if (warm && activityItems >= 2) {
                add(running(1, 2, 200, "Harbor Kickoff", 102, now, startMin = 20, stopMin = 40))
            }
            if (warm && activityItems >= 3) {
                add(running(3, 7, 700, "Harbor Lights Live", 106, now, startMin = 25, stopMin = 30))
            }
            if (!firstRun) {
                add(
                    DvrEntry.create(
                        DvrEntryId(201),
                        channelId = ChannelId(6),
                        channelName = "Harbor Lights Live",
                        state = DvrEntryState.COMPLETED,
                        title = "Glass Harbor",
                        start = Instant.fromEpochSeconds(now - 26 * 3600),
                        stop = Instant.fromEpochSeconds(now - 24 * 3600),
                        image = ArtworkId(201),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(202),
                        channelId = ChannelId(5),
                        channelName = "The Kettle Hour",
                        state = DvrEntryState.COMPLETED,
                        title = "Lantern Road",
                        start = Instant.fromEpochSeconds(now - 50 * 3600),
                        stop = Instant.fromEpochSeconds(now - 49 * 3600),
                        image = ArtworkId(202),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(203),
                        channelId = ChannelId(2),
                        channelName = "Harbor Sport",
                        state = DvrEntryState.COMPLETED,
                        title = "River Court",
                        start = Instant.fromEpochSeconds(now - 74 * 3600),
                        stop = Instant.fromEpochSeconds(now - 73 * 3600),
                        image = ArtworkId(203),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(204),
                        channelId = ChannelId(3),
                        channelName = "Northline Tonight",
                        state = DvrEntryState.COMPLETED,
                        title = "Field Desk",
                        start = Instant.fromEpochSeconds(now - 135 * 60),
                        stop = Instant.fromEpochSeconds(now - 90 * 60),
                        image = ArtworkId(204),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(205),
                        channelId = ChannelId(4),
                        channelName = "Paper Kite Tales",
                        state = DvrEntryState.COMPLETED,
                        title = "Little Orbit",
                        start = Instant.fromEpochSeconds(now - 165 * 60),
                        stop = Instant.fromEpochSeconds(now - 135 * 60),
                        image = ArtworkId(205),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(2),
                        channelId = ChannelId(1),
                        channelName = "Ridge Light",
                        eventId = EventId(101),
                        state = DvrEntryState.SCHEDULED,
                        title = "Tide Watch",
                        start = Instant.fromEpochSeconds(now + 45 * 60),
                        stop = Instant.fromEpochSeconds(now + 105 * 60),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(4),
                        channelId = ChannelId(4),
                        channelName = "Paper Kite Tales",
                        state = DvrEntryState.SCHEDULED,
                        title = "Little Orbit",
                        start = Instant.fromEpochSeconds(now + 60 * 60),
                        stop = Instant.fromEpochSeconds(now + 120 * 60),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(5),
                        channelId = ChannelId(5),
                        channelName = "The Kettle Hour",
                        state = DvrEntryState.SCHEDULED,
                        title = "Lantern Road",
                        start = Instant.fromEpochSeconds(now + 75 * 60),
                        stop = Instant.fromEpochSeconds(now + 135 * 60),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(6),
                        channelId = ChannelId(6),
                        channelName = "Harbor Lights Live",
                        state = DvrEntryState.SCHEDULED,
                        title = "Glass Harbor",
                        start = Instant.fromEpochSeconds(now + 90 * 60),
                        stop = Instant.fromEpochSeconds(now + 150 * 60),
                    ),
                )
            }
        }
    }

    private fun running(
        id: Long,
        channelId: Long,
        eventId: Long,
        title: String?,
        image: Long,
        now: Long,
        startMin: Long,
        stopMin: Long,
    ) = DvrEntry.create(
        DvrEntryId(id),
        channelId = ChannelId(channelId),
        channelName = title,
        eventId = EventId(eventId),
        state = DvrEntryState.RECORDING,
        title = title,
        start = Instant.fromEpochSeconds(now - startMin * 60),
        stop = Instant.fromEpochSeconds(now + stopMin * 60),
        image = ArtworkId(image.toInt()),
    )

    private fun atEvening(block: () -> Unit) {
        val zone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertTrue(android.os.SystemClock.setCurrentTimeMillis(1_791_144_900_000L))
            block()
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) {
        val target: SemanticsMatcher = hasTestTag(tag)
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodes(target and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            val focused = compose.onAllNodes(isFocused(), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { it.config.toString() }
            val bitmap = draw()
            File(out, "debug-$tag.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            throw AssertionError("focus not on $tag; focused=$focused", e)
        }
    }

    private fun settle(tag: String) {
        awaitFocus(tag)
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun capture(name: String, tag: String) {
        settle(tag)
        val bitmap = draw()
        File(out, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun draw(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val root = view.rootView
            bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
        }
        return bitmap
    }
}
