package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant

/** The complete receiver-style fixture in the production guide, driven only with D-pad keys. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi",
    instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideRealisticSceneTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val position = GuidePositionStore()
    private lateinit var player: ExoPlayer
    private lateinit var loader: ImageLoader
    private lateinit var view: View
    private lateinit var session: FakeTvheadendSession
    private lateinit var oldLocale: Locale
    private lateinit var oldZone: TimeZone
    private var playbackRequests = 0
    private var navigationRequests = 0
    private val fixtureBytes = checkNotNull(javaClass.getResourceAsStream("/fixture-epg/epg.json")).use { it.readBytes() }
    private val fixture = JSONObject(fixtureBytes.toString(Charsets.UTF_8))
    private val fixtureHash = MessageDigest.getInstance("SHA-256").digest(fixtureBytes).joinToString("") { "%02x".format(it) }
    private var now = Instant.parse(fixture.getString("snapshot_time")).epochSeconds
    private val channelRecords = records("channels").sortedBy { it.getLong("number") }
    private val channels = channelRecords.mapIndexed { index, record ->
        Channel.create(ChannelId(record.getLong("number")), name = record.getString("name"), number = record.getLong("number"),
            icon = if (record.isNull("picon")) null else ArtworkId(index + 1), tagIds = listOf(ChannelTagId(1)))
    }
    private val channelIds = channelRecords.mapIndexed { index, record -> record.getString("channel_id") to channels[index].id }.toMap()
    // Numeric SDK IDs are local mappings; no events, subtitles or declared gaps are rewritten.
    private val eventsByFixtureId = records("events").mapIndexed { index, record ->
        val event = EpgEvent.create(EventId(index + 1L), channelId = channelIds.getValue(record.getString("channel_id")),
            start = Instant.parse(record.getString("start")), stop = Instant.parse(record.getString("stop")),
            title = record.getString("title"), summary = record.getString("synopsis"), genre = record.getString("genre"),
            subtitle = if (record.has("subtitle")) record.getString("subtitle") else null,
            episode = if (record.has("season")) EpgEpisode(
                id = null, seriesLinkId = null, seasonNumber = record.getLong("season"), seasonCount = null,
                episodeNumber = record.getLong("episode"), episodeCount = null, partNumber = null, partCount = null,
                onscreen = null,
            ) else null)
        require(event.stop.epochSeconds - event.start.epochSeconds == record.getLong("duration_min") * 60)
        record.getString("event_id") to event
    }.toMap()
    private val recordings = records("dvr_fixtures").mapIndexed { index, record ->
        val event = eventsByFixtureId.getValue(record.getString("event_id"))
        DvrEntry.create(DvrEntryId(index + 1L), eventId = event.id, title = record.getString("title"),
            // The fixture's terminal "failed" state is the SDK's completed-error state.
            state = when (record.getString("state")) {
                "scheduled" -> DvrEntryState.SCHEDULED
                "recording" -> DvrEntryState.RECORDING
                "completed" -> DvrEntryState.COMPLETED
                "failed" -> DvrEntryState.COMPLETED_ERROR
                else -> error("Unsupported fixture DVR state")
            },
            start = event.start, stop = event.stop, channelName = channels.single { it.id == event.channelId }.name)
    }

    @Before fun prepare() {
        oldLocale = Locale.getDefault()
        oldZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(fixture.getString("timezone")))
        loader = FixtureArt.imageLoader(context(), channels.zip(channelRecords).mapNotNull { (channel, record) ->
            // Northline World retains its declared icon ID, but its image really fails.
            channel.icon?.takeUnless { record.getString("channel_id") == "ch-northline-world" }
                ?.let { it to FixtureArt.picon(record.getString("picon").removeSuffix(".png")) }
        }.toMap())
    }

    @After fun release() {
        models.clear()
        runtimeScope.cancel()
        if (::player.isInitialized) player.release()
        if (::loader.isInitialized) loader.shutdown()
        Locale.setDefault(oldLocale)
        TimeZone.setDefault(oldZone)
    }

    @Test fun eveningLineupKeepsNumbersFallbackAndDpadFocusAccessible() {
        showAt("2026-09-15T19:40:00")
        capture("guide-fixture-evening-initial")
        moveToChannel(5)
        seekEvent(eventsByFixtureId.getValue("evt-ch-northline-news-20260915T1930"))
        compose.onNodeWithContentDescription(context().getString(R.string.recording_state_recording),
            useUnmergedTree = true).assertIsDisplayed()
        capture("guide-fixture-recording-now")

        moveToChannel(6)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Northline World") and hasAnyAncestor(hasTestTag("epg-channel-header-6")),
                useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertFailedLogoFallback()
        capture("guide-fixture-failed-logo")
        moveToChannel(9)
        capture("guide-fixture-amber-eight-minute")
        moveToChannel(14)
        capture("guide-fixture-harbor-lights-concert")
        moveToChannel(25)
        capture("guide-fixture-kids-off-air-ion-off-grid")
        moveToChannel(27)
        capture("guide-fixture-lower-channels")
    }

    @Test fun consecutiveFiveMinuteWeatherSlotsKeepDpadFocusAccessible() {
        showAt("2026-09-15T18:05:00")
        moveToChannel(7)
        for (minute in 0..25 step 5) {
            val stamp = "18%02d".format(minute)
            seekEvent(eventsByFixtureId.getValue("evt-ch-skyglass-weather-20260915T$stamp"))
            capture("guide-fixture-weather-$stamp")
        }
    }

    @Test fun sevenHourOvernightEventKeepsFocusInTheVisibleFragment() {
        showAt("2026-09-15T01:00:00")
        moveToChannel(12)
        val event = eventsByFixtureId.getValue("evt-ch-coastal-miles-20260914T2300")
        seekEvent(event)
        val window = checkNotNull(position.position.value).windowStartSec
        assertTrue("The requested overnight fragment starts off-screen", event.start.epochSeconds < window)
        assertTrue(event.stop.epochSeconds > window)
        capture("guide-fixture-coastal-seven-hour")
    }

    @Test fun declaredGapsStayEmptyWithoutBlockingDpadNavigation() {
        showAt("2026-09-15T15:00:00")
        key(Key.DirectionDown)
        focusedChannel(1)
        compose.onNodeWithTag("epg-channel-header-2").assertIsDisplayed()
        capture("guide-fixture-sparse-epg")
        moveToChannel(13)
        // Real Left/Right moves expose both sides of the neighbouring two-hour gap.
        seekEvent(eventsByFixtureId.getValue("evt-ch-lumen-live-20260915T1400"))
        seekEvent(eventsByFixtureId.getValue("evt-ch-lumen-live-20260915T1500"))
        compose.onNodeWithTag("epg-channel-header-12").assertIsDisplayed()
        capture("guide-fixture-coastal-data-gap")
    }

    @Test fun scheduledConflictPairRetainsBothScheduledBadges() {
        showAt("2026-09-16T19:30:00")
        moveToChannel(1)
        seekEvent(eventsByFixtureId.getValue("evt-ch-harbor-sport-20260916T1900"))
        for (channel in listOf(1, 3)) {
            compose.onNode(hasContentDescription(context().getString(R.string.recording_state_scheduled)) and
                hasAnyAncestor(hasTestTag("epg-channel-row-$channel")), useUnmergedTree = true).assertExists()
        }
        capture("guide-fixture-conflict-harbor-sport")
        moveToChannel(3)
        seekEvent(eventsByFixtureId.getValue("evt-ch-river-court-20260916T1900"))
        capture("guide-fixture-conflict-river-court")
    }

    @Test fun longGermanProgrammeKeepsItsFullAccessibleTitle() {
        showAt("2026-09-14T02:35:00")
        moveToChannel(20)
        val event = eventsByFixtureId.getValue("evt-ch-foundry-docs-20260914T0230")
        seekEvent(event)
        compose.onNode(isFocused()).assert(hasContentDescription(checkNotNull(event.title), substring = true))
        capture("guide-fixture-long-german")
    }

    private fun records(key: String): List<JSONObject> {
        val array = fixture.getJSONArray(key)
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun seekEvent(event: EpgEvent) {
        repeat(24) {
            val currentId = checkNotNull(position.position.value).eventId
            if (currentId == event.id) {
                focused(checkNotNull(event.title))
                return
            }
            val current = eventsByFixtureId.values.single { it.id == currentId }
            key(if (current.start > event.start) Key.DirectionLeft else Key.DirectionRight)
            focusedChannel(checkNotNull(event.channelId).value.toInt())
        }
        fail("Fixture event ${event.id} was not reached by D-pad navigation")
    }

    private fun moveToChannel(number: Int) {
        repeat(channels.size) {
            val current = position.position.value?.channelId?.value
            if (current == number.toLong()) {
                focusedChannel(number)
                return
            }
            // Empty/off-air rows are passive and are skipped by production navigation.
            key(if (current == null || current < number) Key.DirectionDown else Key.DirectionUp)
        }
        fail("Channel $number was not reached by D-pad navigation")
    }

    private fun showAt(localClock: String) {
        now = LocalDateTime.parse(localClock).atZone(ZoneId.of(fixture.getString("timezone"))).toEpochSecond()
        assertTrue(SystemClock.setCurrentTimeMillis(now * 1000))
        show()
    }

    private fun show() {
        val events = eventsByFixtureId.values.toList()
        session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels,
                listOf(ChannelTag.create(ChannelTagId(1), name = "Favourites", channelIds = channels.map { it.id })))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                events = events.filter { it.stop.epochSeconds > now },
                historicalEvents = events.filter { it.stop.epochSeconds <= now },
                coverages = channels.map { EpgCoverage.create(it.id, Instant.parse(fixture.getString("reference_start")),
                    Instant.parse(fixture.getString("coverage_stop"))) },
            )),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(recordings)),
            dvrConfigurationsState = DvrConfigurationsState.Current.create(emptyList()),
        ))
        val settings = PlayerSettingsStore(preferences())
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        val model = ChannelsViewModel(session, ChannelTagSettingsStore(preferences()))
        models.put("channels", model)
        player = ExoPlayer.Builder(context()).build()
        val runtime = AppPlaybackRuntime(player, session, createTvheadendPlaybackCoordinator(player), settings, profiles,
            runtimeScope, TvheadendAudioOutputProvider(context()), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings())
        val selection = ChannelSelectionStore()
        val lastPlayed = LastPlayedChannelStore(context())
        val notices = NoticeCenter(SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        val noticeModule = module {
            single { notices }
            single { profiles }
            single<TvheadendSession> { session }
        }
        compose.setContent {
            KoinApplication(application = { modules(noticeModule) }) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    TVHeadendPlayerTheme {
                        view = LocalView.current
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            SideRail(currentRoute = AppDestination.GUIDE, showEpgMenu = true,
                                onRootBack = { navigationRequests++ }, onNavigate = { navigationRequests++ }) { padding, drawer ->
                                EpgGridScreen(contentPadding = padding, initialFocusEnabled = !drawer, channelViewModel = model,
                                    session = session, playerSession = runtime, selection = selection, guidePositionStore = position,
                                    lastPlayedStore = lastPlayed, imageLoader = loader, onPlay = { _, _ -> playbackRequests++ },
                                    onPlayRecording = { playbackRequests++ }, notices = notices)
                            }
                        }
                    }
                }
            }
        }
        focused(context().getString(R.string.all_channels))
        assertNotNull(model.observation.value.currentSession)
    }

    private fun assertFailedLogoFallback() {
        compose.onNodeWithTag("epg-channel-header-6").assertContentDescriptionEquals("6 Northline World")
        compose.onNodeWithText("Northline World", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("epg-channel-picon-6", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        for (channel in channels) {
            val header = compose.onAllNodesWithTag("epg-channel-header-${channel.id.value}").fetchSemanticsNodes().singleOrNull() ?: continue
            assertFalse("Channel headers remain nonfocusable", header.config.contains(SemanticsProperties.Focused))
            val number = compose.onNode(hasText(channel.number.toString()) and hasAnyAncestor(hasTestTag("epg-channel-header-${channel.id.value}")),
                useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            number.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("Channel number ${channel.number} must not truncate", layouts.single().hasVisualOverflow)
            assertFalse("Channel number ${channel.number} must not ellipsize", layouts.single().isLineEllipsized(0))
        }
        val focus = compose.onNode(isFocused())
        focus.assertIsDisplayed().assertIsFocused()
        val bounds = focus.getUnclippedBoundsInRoot()
        assertTrue("Focus must be fully on the canvas: $bounds", bounds.left.value >= 0f && bounds.top.value >= 0f &&
            bounds.right.value <= 960f && bounds.bottom.value <= 540f)
        assertEquals(0, playbackRequests)
        assertEquals(0, navigationRequests)
        assertFalse(FakeSessionCall.BIND_LIVE_PLAYBACK in session.calls)
        assertNull(player.currentMediaItem)
        assertFalse(player.playWhenReady)
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        assertEquals(960, bitmap.width)
        assertEquals(540, bitmap.height)
        val directory = File("build/outputs/browse-cohesion/guide/epg-fixture").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        File(directory, "$name.txt").writeText(
            "canvas=960x540dp; density=1; locale=en_US; direction=Ltr; fontScale=1.0; timezone=${fixture.getString("timezone")}\n" +
                "sceneClock=${Instant.fromEpochSeconds(now)}; localClock=${java.time.Instant.ofEpochSecond(now).atZone(ZoneId.of(fixture.getString("timezone")))}; captureClockMillis=${SystemClock.uptimeMillis()}\n" +
                "focus=${focus.fetchSemanticsNode().config[SemanticsProperties.Text]}; bounds=$bounds; position=${position.position.value}\n" +
                "windowStart=${position.position.value?.windowStartSec?.let { Instant.fromEpochSeconds(it) }} (null before grid entry)\n" +
                "fixture=fixture-epg/epg.json; seed=${fixture.getInt("seed")}; sha256=$fixtureHash; noEventOverrides=true\n" +
                "production=TVHeadendPlayerTheme + SideRail + EpgGridScreen; native D-pad focus; released fake SDK; FixtureArt.imageLoader\n" +
                "background=opaque theme, not live video; Robolectric SDK34 native graphics; playbackRequests=$playbackRequests\n",
        )
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun focused(title: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(title) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(title) and isFocused()).assertIsDisplayed().assertIsFocused()
    }

    private fun focusedChannel(channel: Int) {
        val target = isFocused() and hasAnyAncestor(hasTestTag("epg-channel-row-$channel"))
        compose.waitUntil(10_000) { compose.onAllNodes(target).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(target).assertIsDisplayed().assertIsFocused()
    }

    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }

    private fun context(): Application = ApplicationProvider.getApplicationContext()
}
