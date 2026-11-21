package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.core.linkedRecordingForEvent

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.components.*
import at.bernhardberger.tvhplayer.ui.player.*
import at.bernhardberger.tvhplayer.ui.screens.home.HomeContent
import at.bernhardberger.tvhplayer.ui.screens.home.homeInput
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Recording, scheduled and watching marks in Channels List, Grid and Immersive (including Grid
 * and Immersive's art × names options), Home On now, Right now and recent channels, Guide, player
 * banner Now/Next, zap, player schedule and Recordings browse.
 * 960×540 dp, US English, font scale 1.0, fictional fixture art.
 * Writes PNGs and index.json to app/build/outputs/status-marks (ignored).
 */
@Category(VisualCapture::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w960dp-h540dp-land-xhdpi",
    instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTvheadendApi::class)
class StatusMarksCaptureTest(
    private val captureName: String,
    private val surface: String,
    private val scenario: String,
    private val programArt: Boolean?,
    private val channelNames: Boolean,
) : GuideScreenFixture() {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any?>> = buildList {
            fun capture(surface: String, scenario: String, art: Boolean? = null, names: Boolean = false) {
                val settings = if (art == null) "" else "-art${if (art) "on" else "off"}-names${if (names) "on" else "off"}"
                add(arrayOf("$surface$settings-$scenario", surface, scenario, art, names))
            }
            for (surface in listOf("channels-list", "channels-immersive", "home-on-now", "guide",
                "channels-grid", "home-recent", "player-banner", "player-zap", "player-schedule")) {
                for (scenario in listOf("s1-series", "s2-boundary", "s3-padding-only", "s4-timer", "s5-watching")) {
                    capture(surface, scenario)
                }
            }
            for (surface in listOf("channels-immersive", "home-on-now", "player-banner-next", "player-zap")) {
                capture(surface, "next-scheduled")
                capture(surface, "next-prepadding")
            }
            for (scenario in listOf("no-art", "no-art-recording", "no-epg")) capture("player-banner", scenario)
            for (scenario in listOf("s1-series", "s3-padding-only", "s4-timer", "s5-watching", "watch-recording")) {
                capture("home-right-now", scenario)
            }
            for (mode in listOf("running", "scheduled", "failed")) capture("recordings-browse", mode)
            capture("recordings-browse", "s5-watching")
            for (surface in listOf("channels-list", "channels-grid", "channels-immersive", "guide",
                "home-on-now", "home-right-now", "recordings-browse", "player-zap")) {
                capture(surface, "watching-other-focus")
            }
            for (surface in listOf("channels-grid", "channels-immersive")) {
                for (art in listOf(true, false)) for (names in listOf(true, false)) {
                    for (scenario in listOf("s1-series", "s4-timer", "s5-watching")) {
                        capture(surface, scenario, art = art, names = names)
                    }
                }
            }
        }
    }

    private val names = listOf("Ridge Earth", "Harbor Sport", "Northline News", "Kite Kids", "Ion Frame", "Amber Stage")
    private val logos = listOf("ridge-earth", "harbor-sport", "northline-news", "kite-kids", "ion-frame", "amber-stage")
    private val titles = listOf("Ridge Light", "Harbor Kickoff", "Northline Tonight", "Paper Kite Tales", "Ion Wake", "The Kettle Hour")
    private val art = listOf("ridge-light", "harbor-kickoff", "northline-tonight", "paper-kite-tales", "ion-wake", "kettle-hour")
    private val following = listOf("Tide Watch", "River Court", "Field Desk", "Little Orbit", "Lantern Road", "Glass Harbor")
    private val channels = names.mapIndexed { i, name ->
        Channel.create(ChannelId(i + 1L), name = name, number = i + 1L, icon = ChannelIcon.ImageCache(ArtworkId(i + 1)))
    }
    private val channel = channels.first()
    private val banner = surface == "player-banner" || surface == "player-banner-next"
    private val noArtwork = scenario == "no-art" || scenario == "no-art-recording"
    private val noEpg = scenario == "no-epg"
    private val otherFocus = scenario == "watching-other-focus"
    private val watching = (scenario == "s5-watching" || otherFocus) && surface != "recordings-browse" ||
        surface == "home-right-now" && scenario in setOf("s3-padding-only", "s4-timer")
    private val watchingRecording = scenario == "watch-recording"
    private val viewer = channel.id.takeIf { watching }
    private val boundary = scenario == "s2-boundary" || scenario == "s3-padding-only"
    private val captureNow = when {
        surface == "recordings-browse" -> hour + 23 * 60
        scenario == "next-prepadding" -> hour + 57 * 60
        boundary -> hour + 63 * 60
        else -> now
    }
    private val currentStart = if (boundary) hour + 3600 else hour
    private val current = channels.mapIndexed { i, ch ->
        EpgEvent.create(EventId(ch.id.value * 100), ch.id, Instant.fromEpochSeconds(currentStart),
            Instant.fromEpochSeconds(currentStart + 3600), title = titles[i],
            image = ArtworkId(101 + i).takeUnless { noArtwork && i == 0 },
            subtitle = "Winter on the ridge".takeIf { i == 0 },
            genre = "Documentary".takeIf { banner && i == 0 },
            episode = EpgEpisode(null, null, 2, null, 3, null, null, null, "S2 E3").takeIf { banner && i == 0 },
            summary = "Seasonal mountain light, forests and weather moving over a ridge.".takeIf { i == 0 })
    }
    private val next = channels.mapIndexed { i, ch ->
        EpgEvent.create(EventId(ch.id.value * 100 + 1), ch.id, current[i].stop, Instant.fromEpochSeconds(currentStart + 7200),
            title = following[i])
    }
    private val previous = EpgEvent.create(EventId(99), channel.id, Instant.fromEpochSeconds(currentStart - 3600),
        current.first().start, title = "Coastal Miles",
        image = ArtworkId(120).takeIf { surface == "home-right-now" })
    private val timer = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = "Ridge Earth timer",
        state = DvrEntryState.RECORDING, start = Instant.fromEpochSeconds(hour - 15 * 60),
        stop = Instant.fromEpochSeconds(hour + 30 * 60))
    private val archived = DvrEntry.create(DvrEntryId(2), channelId = channel.id, title = "Coastal Miles",
        state = DvrEntryState.COMPLETED, start = previous.start, stop = previous.stop,
        image = previous.image)
    private val scheduled = DvrEntry.create(DvrEntryId(next.first().id.value), channelId = channel.id,
        eventId = next.first().id, title = next.first().title, state = DvrEntryState.SCHEDULED,
        start = next.first().start, stop = next.first().stop)
    private val entries = when {
        surface == "recordings-browse" -> recordingLibrary()
        scenario == "no-art" || noEpg -> emptyList()
        scenario == "next-scheduled" -> listOf(scheduled)
        scenario == "next-prepadding" -> listOf(entry(next.first()))
        scenario == "watch-recording" -> listOf(archived)
        surface == "home-right-now" && otherFocus -> listOf(entry(current.first()), timer)
        else -> when (scenario) {
            "s2-boundary" -> listOf(entry(previous), entry(current.first()))
            "s3-padding-only" -> listOf(entry(previous))
            "s4-timer" -> listOf(timer)
            else -> listOf(entry(current.first()))
        }
    }
    private val observation = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = if (noEpg) emptyList() else current + next,
            historicalEvents = listOf(previous).takeUnless { noEpg }.orEmpty(),
            coverages = channels.map { EpgCoverage.create(it.id, previous.start, Instant.fromEpochSeconds(hour + 72_000)) })),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries, autorecRules =
            if (surface == "recordings-browse") listOf(AutorecRule.create(
                AutorecRuleId("ridge-light"), title = "Ridge Light", channelId = channel.id,
                seriesLinkUri = "series:ridge-light",
            )) else emptyList())),
        recordingProgressCapability = RecordingProgressCapability.SUPPORTED,
    )
    private val artworkSession = FakeSessionObservation(observation).captureCurrentSession()
    private val recordingIds = activeRecordingChannelIds(entries, captureNow)

    private fun entry(event: EpgEvent) =
        DvrEntry.create(DvrEntryId(event.id.value), channelId = event.channelId, eventId = event.id, title = event.title,
            state = DvrEntryState.RECORDING, start = event.start, stop = event.stop,
            image = event.image,
            stopExtraMinutes = 5.minutes, startExtraMinutes = 5.minutes,
            autorecRuleId = AutorecRuleId("ridge-light"))

    private fun recordingLibrary(): List<DvrEntry> = listOf(
        libraryEntry(100, 0, 0, DvrEntryState.RECORDING, recordedMinutes = 25, series = true),
        libraryEntry(1, 1, -4, DvrEntryState.RECORDING, recordedMinutes = 29, withSubtitle = false),
        libraryEntry(6, 4, -85, recordedMinutes = 77, position = 31 * 60L, removal = DvrRemoval.Days(7),
            withSubtitle = false),
        libraryEntry(7, 2, -1_475, recordedMinutes = 77),
        libraryEntry(8, 3, -1_510, recordedMinutes = 77, plays = 1),
        libraryEntry(9, 5, -1_440, recordedMinutes = 22, withSubtitle = false),
        libraryEntry(10, 0, -1_380, recordedMinutes = 77, series = true, longTitle = true),
        libraryEntry(101, 0, 75, DvrEntryState.SCHEDULED, series = true),
        libraryEntry(102, 3, 180, DvrEntryState.SCHEDULED, enabled = false, withSubtitle = false),
        libraryEntry(3, 1, -300, DvrEntryState.COMPLETED_ERROR, recordedMinutes = 12,
            error = DvrSubscriptionError.NO_DISK_SPACE),
        libraryEntry(4, 2, -400, DvrEntryState.MISSED, error = DvrSubscriptionError.NO_FREE_ADAPTER, withSubtitle = false),
    ).filterNot { scenario == "scheduled" && it.state == DvrEntryState.RECORDING }

    private fun libraryEntry(
        id: Long, index: Int, startMinutes: Int, state: DvrEntryState = DvrEntryState.COMPLETED,
        recordedMinutes: Int? = null, series: Boolean = false, enabled: Boolean = true,
        position: Long? = null, plays: Long? = null, removal: DvrRemoval? = null,
        error: DvrSubscriptionError? = null, longTitle: Boolean = false, withSubtitle: Boolean = true,
    ): DvrEntry {
        val start = hour + startMinutes * 60
        val programme = channels[index]
        val subtitle = listOf("Winter on the ridge", "Harbor FC meet Amber Town", "The regional evening desk",
            "Over the painted hills", "A courier in Ion Harbor", "Broths, bread and weeknight pans")[index]
        val synopsis = listOf(
            "Seasonal mountain light, forests and weather moving over a ridge.",
            "Match coverage from a floodlit stadium as Harbor FC meet Amber Town.",
            "Evening studio news from a regional desk with two anchors.",
            "2D animation about a paper kite over painted hills.",
            "Sci-fi anime: a courier rides a magnetic rail through Ion Harbor.",
            "A warm kitchen programme about broths, bread and weeknight pans.",
        )[index]
        val fileStart = start - 120
        return DvrEntry.create(
            DvrEntryId(id), channelId = programme.id, channelName = programme.name, state = state, enabled = enabled,
            title = titles[index] + if (longTitle) " — An expedition following winter light across the mountain forests" else "",
            subtitle = subtitle.takeIf { withSubtitle },
            description = synopsis, image = ArtworkId(101 + index),
            episode = if (index in listOf(0, 3, 4, 5)) EpgEpisode(null, null, 2, null, 3, null, null, null, "S2 E3") else null,
            contentGenre = ContentGenre.fromContentType(listOf(0x90L, 0x40L, 0x20L, 0x50L, 0x10L, 0xA0L)[index]),
            rating = if (index == 3 || index == 4) EpgRating(if (index == 3) 6 else 12, null, null, null, null, null) else null,
            start = Instant.fromEpochSeconds(start), stop = Instant.fromEpochSeconds(start + 70 * 60),
            startExtraMinutes = 2.minutes, stopExtraMinutes = 5.minutes,
            files = recordedMinutes?.let {
                listOf(DvrRecordingFile.create(
                    start = Instant.fromEpochSeconds(fileStart),
                    stop = Instant.fromEpochSeconds(fileStart + it * 60).takeUnless { state == DvrEntryState.RECORDING },
                    sizeBytes = it * 40_000_000L,
                ))
            }.orEmpty(),
            autorecRuleId = AutorecRuleId("ridge-light").takeIf { series }, subscriptionError = error,
            playPosition = position?.seconds, playCount = plays, removal = removal,
        )
    }

    @Test fun render() {
        assertTrue(SystemClock.setCurrentTimeMillis(captureNow * 1000))
        val artworkLoader = FixtureArt.imageLoader(context(), buildMap {
            logos.forEachIndexed { i, logo -> put(ArtworkId(1 + i), FixtureArt.picon(logo)) }
            art.forEachIndexed { i, picture -> put(ArtworkId(101 + i), FixtureArt.art(picture)) }
            if (surface == "home-right-now") put(ArtworkId(120), FixtureArt.art("still-coastal-miles"))
        })
        val focus = when (surface) {
            "channels-list", "channels-immersive", "channels-grid" -> {
                val fixture = ChannelsLayoutFixture(compose, images)
                val layout = when (surface) {
                    "channels-list" -> ChannelsLayout.LIST
                    "channels-grid" -> ChannelsLayout.GRID
                    else -> ChannelsLayout.IMMERSIVE
                }
                val channelsView = ChannelsView(layout, programArt = if (programArt == false) emptySet() else setOf(layout),
                    channelNames = if (channelNames) setOf(layout) else emptySet())
                fixture.show(channelsView, artworkLoader, playingLive = viewer,
                    tags = false, captureObservation = observation)
                view = fixture.view
                if (otherFocus) fixture.key(if (layout == ChannelsLayout.LIST) Key.DirectionDown else Key.DirectionRight)
                val id = if (otherFocus) 2 else 1
                val tag = if (layout == ChannelsLayout.LIST) "channel-row-$id" else "channel-card-$id"
                fixture.awaitFocus(hasTestTag(tag))
                hasTestTag(tag)
            }
            "guide" -> {
                images.install(artworkLoader)
                show(headline = true, events = current + next, captureHistory = listOf(previous), channelNames = names,
                    tags = false, recordings = entries, watching = viewer)
                key(Key.DirectionDown)
                if (otherFocus) key(Key.DirectionDown)
                hasContentDescription(if (otherFocus) "Harbor Kickoff" else "Ridge Light", substring = true)
            }
            else -> {
                images.install(artworkLoader)
                val still = FixtureArt.art("still-ridge-light").asImageBitmap()
                compose.setContent {
                    view = LocalView.current
                    val livePosition = remember { mutableStateOf(LiveViewerPosition(channel.id, captureNow, behindLive = false)) }
                    val target = when {
                        watchingRecording -> AppPlaybackTarget.Recording(archived.id)
                        surface == "recordings-browse" && (scenario == "s5-watching" || otherFocus) ->
                            AppPlaybackTarget.Recording(DvrEntryId(100))
                        else -> viewer?.let(AppPlaybackTarget::Live)
                    }
                    TVHeadendPlayerTheme {
                        CompositionLocalProvider(
                            LocalWatchingChannel provides viewer,
                            LocalWatchingTarget provides target,
                            LocalWatchingIndicator provides if (target != null) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.NONE,
                            LocalLivePosition provides livePosition.takeIf { watching },
                        ) {
                            ProvideArtworkSession(artworkSession) {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                    if (surface.startsWith("player-")) Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    when (surface) {
                                        "home-on-now", "home-recent", "home-right-now" -> Home()
                                        "player-banner", "player-banner-next" -> Banner()
                                        "recordings-browse" -> Recordings()
                                        "player-zap" -> Rail()
                                        "player-schedule" -> PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
                                            val events = listOf(previous, current.first(), next.first())
                                            ChannelSchedule(channel, events, captureNow, captureNow.takeIf { watching }, false, false,
                                                { observation.linkedRecordingForEvent(it)?.takeUnless(::recordingWasRemoved) },
                                                {}, {}, {}, initialEvent = current.first())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                when (surface) {
                    "home-on-now" -> hasTestTag(if (otherFocus) "home-onnow-1" else "home-onnow-0")
                    "home-right-now" -> hasTestTag(if (otherFocus) "home-activity-1" else "home-activity-0")
                    "home-recent" -> hasTestTag("home-recent-0")
                    "recordings-browse" -> hasTestTag(when (scenario) {
                        "failed" -> "recording-list-entry-3"
                        "scheduled" -> "recording-list-entry-101"
                        else -> if (otherFocus) "recording-list-entry-6" else "recording-list-entry-100"
                    })
                    "player-zap" -> hasTestTag(if (otherFocus) "player-channel-card-2" else "player-channel-card-1")
                    "player-schedule" -> hasTestTag("schedule-row-100")
                    else -> null
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        if (surface == "recordings-browse" && scenario !in setOf("scheduled", "failed")) {
            compose.onNodeWithTag("recording-list-entry-1").assertIsDisplayed()
            compose.onNodeWithTag("recording-list-entry-100")
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.waitForIdle()
        }
        if (otherFocus && surface in setOf("home-on-now", "home-right-now")) key(Key.DirectionRight)
        if (otherFocus && surface == "player-zap") key(Key.DirectionLeft)
        if (otherFocus && surface == "recordings-browse") {
            compose.onNodeWithTag("recording-list-entry-6")
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.waitForIdle()
        }
        if (surface == "player-zap" && watching) {
            compose.onNodeWithTag("player-now-playing").assertIsDisplayed()
            compose.onNode(hasTestTag("channel-playing-indicator") and
                hasAnyAncestor(hasTestTag("player-channel-card-1")), useUnmergedTree = true).assertIsDisplayed()
        }
        if (banner && (noArtwork || noEpg)) {
            compose.onNodeWithTag("player-info-artwork-fallback", useUnmergedTree = true).assertIsDisplayed()
        }
        val focused = focus?.let {
            compose.onNode(it and isFocused()).assertIsFocused().assertIsDisplayed().fetchSemanticsNode().config
                .let { config -> config.getOrNull(SemanticsProperties.TestTag)
                    ?: if (otherFocus) "Harbor Kickoff programme cell" else "Ridge Light programme cell" }
        } ?: "None (passive player banner)"
        write(focused)
        artworkLoader.shutdown()
    }

    @Composable
    private fun Home() {
        val playing = when {
            scenario == "watch-recording" -> HomePlaying.Recording(archived.id)
            watching -> HomePlaying.LiveChannel(channel.id)
            else -> null
        }
        val input = homeInput(channels, captureNow, channels.zip(current).associate { (channel, event) -> channel.id to event },
            next, channels.map { it.id }, entries, playing = playing)
        val sections = when (surface) {
            "home-recent" -> homeSections(input, observation).filterIsInstance<HomeSection.RecentChannels>()
            "home-right-now" -> homeSections(input, observation).filterIsInstance<HomeSection.CurrentActivity>()
            else -> listOf(HomeSection.OnNow(channels.zip(current, ::OnNowItem)))
        }
        val nowPlaying = viewer?.let { railNowPlaying(AppPlaybackTarget.Live(it), AppPlaybackState.Playing, observation, null, false) }
        SideRail(currentRoute = AppDestination.HOME, onRootBack = {}, onNavigate = {},
            rootRoute = AppDestination.HOME, availableDestinations = setOf(AppDestination.HOME, AppDestination.CHANNELS,
                AppDestination.GUIDE, AppDestination.RECORDINGS, AppDestination.SETTINGS),
            nowPlaying = nowPlaying, livePauseAvailable = true,
            programmeAt = { id, at -> observation.eventAt(id, Instant.fromEpochSeconds(at)) },
            animateNowPlaying = false, nowSecProvider = { captureNow }) { padding, _ ->
            HomeContent(sections, padding, homeInitialFocus(sections), {}, observation = observation, input = input)
        }
    }

    @Composable
    private fun Recordings() {
        val session = remember { FakeTvheadendSession(observation) }
        val live by session.observation.collectAsState()
        val state = remember {
            RecordingsScreenState(when (scenario) {
                "failed" -> DvrLibraryMode.PROBLEMS
                "scheduled" -> DvrLibraryMode.SCHEDULE
                else -> DvrLibraryMode.ARCHIVE
            }).apply {
                if (scenario == "scheduled") open(DvrLibraryMode.SCHEDULE, scheduled.id)
            }
        }
        val actions = remember { DvrMutationActions(session.dvrRepository) }
        val notices = remember { NoticeCenter(SystemClock::elapsedRealtime) { NoticeContext(0L, live.currentSession?.generationIdentity) } }
        SideRail(currentRoute = AppDestination.RECORDINGS, onRootBack = {}, onNavigate = {},
            rootRoute = AppDestination.HOME, availableDestinations = setOf(AppDestination.HOME, AppDestination.CHANNELS,
                AppDestination.GUIDE, AppDestination.RECORDINGS, AppDestination.SETTINGS)) { padding, _ ->
            CompositionLocalProvider(LocalBrowsePreparationContext provides EmptyCoroutineContext) {
                RecordingsScreenContent(live, contentPadding = padding, state = state,
                    dvrMutationActions = actions, notices = notices, nowSecProvider = { captureNow })
            }
        }
    }

    @Composable
    private fun Banner() {
        val programme = current.first().takeUnless { noEpg }
        val following = next.first().takeUnless { noEpg }
        PlayerChrome(PlayerChromeMode.BANNER,
            PlayerChromeContent(formatClock(captureNow),
                liveInfoBarData(context().resources, channel.number, names.first(), programme, following,
                    scenario == "next-scheduled", captureNow, stringResource(R.string.no_epg),
                    observation.runningRecordingForEvent(programme), observation.runningRecordingForEvent(following)),
                badges = glanceBadges(1080, "audio/ac3", 6, audioDescription = true, scan = VideoScan.INTERLACED),
                picon = channel.icon, artwork = programme?.image?.let(ChannelIcon::ImageCache),
                channelId = channel.id, channelRecording = channel.id in recordingIds),
            PlayerChromeTimeline.Live(AppTimeshiftState(), captureNow, programme), PlayerChromeActions(active = false),
            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
            modifier = Modifier.fillMaxSize())
    }

    @Composable
    private fun Rail() {
        if (watching) {
            PlayerPage<Unit>(details = null, railExpanded = true, nowPlaying = {
                NowPlayingStrip(channel.id, channel.number?.toString(), names.first(), channel.icon,
                    current.first(), captureNow, playbackIndicator = ChannelPlaybackIndicator.PLAYING)
            }, player = { ZapContent() }) {}
        } else {
            ZapContent()
        }
    }

    @Composable
    private fun ZapContent() {
        val rail = rememberQuickZapRail(expanded = true)
        val state = remember { PlayerChannelBrowserState() }
        // Focus pins to the start keyline; put the unfocused watched card on the visible side.
        val railChannels = if (otherFocus) listOf(channels[1], channels[0]) + channels.drop(2) else channels
        var selected by remember { mutableStateOf(channel.id) }
        // The capture opens directly into the fully revealed rail, without the controls' entrance.
        val scrim = LocalPlayerPageScrim.current
        SideEffect { scrim?.alpha = { 1f } }
        QuickZapControls(rail, peek = null) {}
        QuickZapRail(rail,
            channelContent = {
                PlayerChannelBrowser(state, railChannels, emptyList(), viewer, recordingIds,
                    { id -> current.firstOrNull { it.channelId == id } }, captureNow, true,
                    { selected = it }, {}, { _, _, _ -> }, {},
                    runningRecordingForEvent = observation::runningRecordingForEvent)
            },
            preview = {
                val previewChannel = channels.first { it.id == selected }
                val nowEvent = current.first { it.channelId == selected }
                val nextEvent = next.first { it.channelId == selected }
                QuickZapTrayPreview(previewChannel, nowEvent, nextEvent, captureNow,
                    Modifier.padding(horizontal = TvGridHorizontalMargin),
                    scrolling = state.scrolling,
                    nextScheduled = scenario == "next-scheduled" && selected == channel.id,
                    nextRecording = observation.runningRecordingForEvent(nextEvent),
                    nowRecording = observation.runningRecordingForEvent(nowEvent))
            },
            header = { PlayerChannelBrowserHint(state, hasRecent = false, channelsTitle = "All channels") },
        )
    }

    private fun write(focus: String) {
        val file = "$captureName.png"
        val base = File("build/outputs/status-marks")
        compose.runOnIdle {
            val root = view.rootView
            assertEquals(1920, root.width)
            assertEquals(1080, root.height)
            assertEquals(1f, root.resources.configuration.fontScale, 0f)
            assertEquals("en-US", root.resources.configuration.locales[0].toLanguageTag())
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            try {
                root.draw(Canvas(image))
                CaptureOutput.writePng(File(base, file), image)
            } finally {
                image.recycle()
            }
        }
        val index = File(base, "index.json")
        val existing = if (index.isFile) JSONArray(index.readText()) else JSONArray()
        val row = JSONObject().put("file", file).put("surface", surface).put("scenario", scenario)
            .put("focus", focus).put("note", note())
        if (programArt != null) row.put("programArt", programArt).put("channelNames", channelNames)
        val rows = ((0 until existing.length()).map { existing.getJSONObject(it) }.filterNot { it.getString("file") == file } + row)
            .sortedBy { it.getString("file") }
        CaptureOutput.writeText(index) { JSONArray(rows).toString(2) + "\n" }
    }

    private fun note(): String = captureNote() + if (banner) {
        if (noEpg) " Stream badges: 1080i, Dolby Digital 5.1 and AD."
        else " Programme facts: Documentary and S2 E3; stream badges: 1080i, Dolby Digital 5.1 and AD."
    } else ""

    private fun captureNote(): String = if (programArt != null) {
        when (scenario) {
            "s4-timer" -> "Plain channel REC badge at the card's top end; blue programme strip."
            else -> {
                "Series REC badge in the picture corner; ${if (watching) "orange viewer strip" else "red programme strip"}; no channel mark."
            }
        }
    } else if (otherFocus) when (surface) {
        "guide" -> "Playback bars stay on the watched programme title while another programme has focus."
        "home-right-now" -> "The watching card keeps both its status and return icon while a recording card has focus."
        "player-zap" -> "The Now Playing strip and the unfocused watched channel card both show playback bars."
        "recordings-browse" -> "Playback bars sit in the watched row's end column, not on the logo or in the pane."
        "channels-list" -> "The watched row has its playback bars at the end of the name line, not on the logo."
        else -> "Playback bars remain on the watched item while focus is on Harbor Sport."
    } else when (surface) {
        "home-right-now" -> when (scenario) {
            "s3-padding-only" -> "Watching Ridge Light and recording the previous Coastal Miles during post-padding: separate cards."
            "s4-timer" -> "Watching live and an unlinked manual timer on the same channel: separate cards."
            "s5-watching" -> "One watching-and-recording card for the linked programme; viewer orange wins."
            "watch-recording" -> "Watching a finished recording, not watching the current live programme."
            else -> "Standalone recording card with entry title and corner REC."
        }
        "recordings-browse" -> when (scenario) {
            "scheduled" -> "Schedule: series and skipped entries; padded stop on the timing line."
            "failed" -> "Problems: failed and missed entries; the partial file's length leads the facts."
            "watching-other-focus" -> "Browse: partly watched entry without a subtitle, with length, expiry and time left."
            else -> "Browse: running series and timer; REC in the eyebrow and length leads the facts."
        }
        "home-recent" -> when (scenario) {
            "s5-watching" -> "The Side Rail shows Ridge Earth, which is excluded from Recent channels."
            "s3-padding-only" -> "No REC on the Ridge Earth logo tile."
            else -> "Plain channel REC in the Ridge Earth logo tile's corner; no programme shown."
        }
        "player-schedule" -> when (scenario) {
            "s2-boundary" -> "Each linked programme has its own series mark; the current row has a red strip."
            "s3-padding-only" -> "Coastal Miles retains its own mark; current Ridge Light is unmarked."
            "s4-timer" -> "No channel item in this programme schedule; no linked programme marks."
            "s5-watching" -> "Ridge Light has a series mark and orange viewer strip in its schedule row."
            else -> "Ridge Light has a series mark and red strip in its schedule row."
        }
        else -> when (scenario) {
            "next-scheduled" -> "The clock follows the Next programme's text."
            "next-prepadding" -> "The Next programme's own entry is running in its pre-padding; the mark follows Next."
            "no-art" -> "Channel-logo fallback with programme facts and no recording."
            "no-art-recording" -> "Channel-logo fallback; the programme's own series REC leads the top line."
            "no-epg" -> "Channel-logo fallback with no Now or Next programme."
            "s3-padding-only" -> if (surface == "guide")
                "Current programme and headline are unmarked; history retains Coastal Miles' own mark."
                else "No mark on the current programme or channel."
            "s4-timer" -> when (surface) {
                "channels-list" -> "Plain channel dot in the row and plain REC in the pane eyebrow; blue programme strip."
                "channels-immersive" -> "Plain channel REC in the card corner and headline eyebrow; blue programme strip."
                "channels-grid", "player-zap" -> "Plain channel REC in the card corner; blue programme strip."
                "home-on-now" -> "Plain channel REC in the card corner and headline eyebrow; blue programme strip."
                else -> "Plain channel mark only; the 19:45–20:30 timer is in-window and unlinked."
            }
            else -> when (surface) {
                "player-banner" -> "The Now timing line starts with series REC; the playback timeline is unchanged."
                "player-zap" -> "The zap headline timing line and the card's top-end corner show series REC."
                "channels-list" -> "The row's trailing recording dot and the pane eyebrow REC describe the programme." +
                    if (watching) " Playback is at the row's end; viewer progress stays orange." else ""
                "channels-immersive", "home-on-now", "guide" -> "The headline eyebrow carries series REC; " +
                    if (watching) "viewer progress is orange." else "programme progress is red."
                else -> "Ridge Light has the series mark; no channel mark."
            }
        }
    }
}
