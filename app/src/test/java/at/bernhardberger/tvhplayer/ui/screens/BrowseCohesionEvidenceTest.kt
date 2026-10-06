package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import org.koin.compose.KoinApplication
import org.koin.dsl.module

import android.app.Application
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvScreenPadding
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Comparable, offline production-composable evidence, not replacement screen mocks.
 * Native focus is reached by D-pad. Captures prove static composition only, not video,
 * overscan, focus feel or remote-repeat behavior on a physical TV.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseCohesionEvidenceTest {
    @get:Rule val compose = createComposeRule()

    private val models = ViewModelStore()
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var view: View
    private lateinit var session: FakeTvheadendSession
    private lateinit var imageLoader: ImageLoader
    private lateinit var originalZone: TimeZone
    private lateinit var originalLocale: Locale
    private var fixtureEpochMillis = 0L
    private var initialSdkCalls = 0
    private var playbackRequests = 0
    private var navigationRequests = 0

    @Before
    fun prepare() {
        originalZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        fixtureEpochMillis = System.currentTimeMillis()
        imageLoader = FixtureArt.imageLoader(context(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"),
            ArtworkId(2) to FixtureArt.picon("harbor-sport"),
            ArtworkId(4) to FixtureArt.picon("kite-kids"),
            ArtworkId(5) to FixtureArt.picon("lantern-hour"),
            ArtworkId(101) to FixtureArt.art("ridge-light"),
            ArtworkId(102) to FixtureArt.art("harbor-kickoff"),
            ArtworkId(103) to FixtureArt.art("still-tide-watch"),
        ))
    }

    @After
    fun release() {
        models.clear()
        runtimeScope.cancel()
        if (::player.isInitialized) player.release()
        if (::imageLoader.isInitialized) imageLoader.shutdown()
        TimeZone.setDefault(originalZone)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun channelsPopulatedAndMissingEpgDoNotTune() {
        showChannels(1f)
        awaitFocus(hasText("All channels"))
        key(Key.DirectionDown)
        capture("channels-current", 1f, "Channels current programme; first row", hasTestTag("channel-row-1"))
        compose.onNodeWithTag("channels-detail-channel", useUnmergedTree = true)
            .assertTextEquals("Ridge Earth HD")
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        awaitFocus(hasTestTag("channel-row-3"))
        compose.onNode(
            hasText("No EPG") and hasAnyAncestor(hasTestTag("channel-row-3")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        capture("channels-missing-epg", 1f, "Channels third row with no EPG", hasTestTag("channel-row-3"))
    }

    @Test
    fun channelsLargeTextKeepsLongRowFocusedWithoutTuning() {
        showChannels(1.3f)
        awaitFocus(hasText("All channels"))
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        capture("channels-long-font1.3", 1.3f, "Channels long channel and programme text", hasTestTag("channel-row-2"))
    }

    @Test
    fun guideCurrentProgrammeOpensDetailsWithoutPlaybackOrMutation() {
        showGuide(1f)
        key(Key.DirectionDown)
        capture("guide-current", 1f, "Guide first channel current programme", hasText(CURRENT_TITLE))
        key(Key.DirectionCenter)
        compose.onNodeWithText("Watch").assertIsDisplayed()
        capture("guide-details", 1f, "Guide current programme details; description body", hasTestTag("programme-details-body"), dialog = true)
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun guideLargeTextKeepsCurrentLongProgrammeFocused() {
        showGuide(1.3f)
        key(Key.DirectionDown)
        awaitFocus(hasText(CURRENT_TITLE))
        key(Key.DirectionDown)
        capture("guide-long-font1.3", 1.3f, "Guide second channel current long programme", hasText(LONG_TITLE))
    }

    @Test
    fun recordingsRootAndFolderBrowsingDoNotPlayOrMutate() {
        showRecordings(1f)
        capture("recordings-root", 1f, "Archive root; Documentaries folder", hasTestTag("recordings-folder-Documentaries"))
        key(Key.DirectionCenter)
        capture("recordings-folder", 1f, "Archive inside Documentaries; first recording", hasTestTag("recording-list-entry-1"))
        compose.onNodeWithTag("recording-metadata-pane", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun recordingsLargeTextDetailsOfferResumeWithoutDispatch() {
        showRecordings(1.3f)
        awaitFocus(hasTestTag("recordings-folder-Documentaries"))
        key(Key.DirectionCenter)
        awaitFocus(hasTestTag("recording-list-entry-1"))
        key(Key.DirectionCenter)
        compose.onNodeWithTag("recording-details-panel").assertIsDisplayed()
        compose.onNodeWithContentDescription("Resume from 1 hour, 2 minutes, 3 seconds").assertIsDisplayed()
        capture("recordings-resume-font1.3", 1.3f, "Long recording details; Resume 1:02:03 action", hasTestTag("recording-details-resume"))
    }

    @Test
    fun recordingsEmptyLibraryKeepsArchiveTabReachable() {
        showRecordings(1f, empty = true)
        // Library preparation runs off-main; a composed frame alone is not readiness.
        awaitFocus(hasText("Archive"))
        compose.onNodeWithText("No recordings are available.").assertIsDisplayed()
        capture("recordings-empty", 1f, "Empty recording library; Archive tab", hasText("Archive"))
    }

    private fun showChannels(scale: Float) {
        val observation = observation()
        session = FakeTvheadendSession(observation)
        shell(AppDestination.CHANNELS, scale) { padding, drawerActive ->
            var selected by remember { mutableStateOf<ChannelId?>(null) }
            var activeTag by remember { mutableStateOf<ChannelTagId?>(null) }
            ChannelsScreenContent(
                contentPadding = padding,
                initialFocusEnabled = !drawerActive,
                channelScopeState = resolveChannelScopeState(observation.channelState, activeTag),
                observation = observation,
                tagNotice = false,
                selectedId = { selected },
                imageLoader = imageLoader,
                playingChannelId = null,
                connectionUiState = ConnectionUiState.Ready,
                onSelectChannel = { selected = it },
                onSelectTag = { activeTag = it },
                onDismissTagNotice = {},
                onRetryConnection = { error("Unexpected connection retry") },
                onOpenConnectionSettings = { error("Unexpected connection settings") },
                onPlay = { _, _ -> playbackRequests++ },
            )
        }
    }

    private fun showGuide(scale: Float) {
        session = FakeTvheadendSession(observation())
        val settings = PlayerSettingsStore(preferences())
        val profiles = AppProfileOwner(
            session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing },
        )
        val model = ChannelsViewModel(session, ChannelTagSettingsStore(preferences()))
        models.put("channels", model)
        player = ExoPlayer.Builder(context()).build()
        val runtime = AppPlaybackRuntime(
            player, session, createTvheadendPlaybackCoordinator(player), settings, profiles,
            runtimeScope, TvheadendAudioOutputProvider(context()), PlaybackAudioFocus.None,
            PlaybackRuntimePolicy.fromPlayerSettings(),
        )
        val selection = ChannelSelectionStore()
        val position = GuidePositionStore()
        val lastPlayed = LastPlayedChannelStore(context())
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        val noticeModule = module {
            single { notices }
            single { profiles }
            single<at.bernhardberger.tvheadend.sdk.core.TvheadendSession> { session }
        }
        shell(AppDestination.GUIDE, scale) { padding, drawerActive ->
            KoinApplication(application = { modules(noticeModule) }) {
            EpgGridScreen(
                contentPadding = padding,
                initialFocusEnabled = !drawerActive,
                channelViewModel = model,
                session = session,
                selection = selection,
                playerSession = runtime,
                guidePositionStore = position,
                lastPlayedStore = lastPlayed,
                imageLoader = imageLoader,
                onPlay = { _, _ -> playbackRequests++ },
                onPlayRecording = { playbackRequests++ },
            )
            }
        }
        awaitFocus(hasText("All channels"))
        compose.runOnIdle { initialSdkCalls = session.calls.size }
    }

    private fun showRecordings(scale: Float, empty: Boolean = false) {
        val observation = observation(emptyRecordings = empty)
        session = FakeTvheadendSession(observation)
        val actions = DvrMutationActions(session.dvrRepository)
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(0L, session.observation.value.currentSession?.generationIdentity)
        }
        shell(AppDestination.RECORDINGS, scale) { padding, drawerActive ->
            RecordingsScreenContent(
                observation = observation,
                contentPadding = padding,
                initialFocusEnabled = !drawerActive,
                backEnabled = !drawerActive,
                imageLoader = imageLoader,
                dvrMutationActions = actions,
                notices = notices,
                onRetry = { error("Unexpected recording retry") },
                onPlayRecording = { _, _ -> playbackRequests++ },
            )
        }
    }

    private fun shell(
        destination: AppDestination,
        scale: Float,
        content: @Composable (PaddingValues, Boolean) -> Unit,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    // Deliberate no-video backdrop shared by all three destinations.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SideRail(
                            currentRoute = destination,
                            showEpgMenu = true,
                            onRootBack = { navigationRequests++ },
                            onNavigate = { navigationRequests++ },
                            content = content,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun observation(emptyRecordings: Boolean = false): SessionObservation {
        val names = listOf(
            "Ridge Earth HD", "Harbor Sport International from Around the World HD", "Northline News",
            "Kite Kids", "Lantern Hour", "River Court", "Little Orbit", "Harbor Lights",
        )
        val channels = names.mapIndexed { index, name ->
            Channel.create(
                id = ChannelId(index + 1L), name = name, number = index + 1L,
                icon = ArtworkId(index + 1).takeIf { index != 2 },
                tagIds = listOf(ChannelTagId(if (index < 3) 1 else 2)),
            )
        }
        val tags = listOf(
            ChannelTag.create(ChannelTagId(1), name = "Favourites", channelIds = channels.take(3).map { it.id }),
            ChannelTag.create(ChannelTagId(2), name = "Entertainment", channelIds = channels.drop(3).map { it.id }),
        )
        val now = fixtureEpochMillis / 1000
        val events = channels.filterNot { it.id == ChannelId(3) }.flatMap { channel ->
            (0..4).map { offset ->
                EpgEvent.create(
                    id = EventId(channel.id.value * 100 + offset), channelId = channel.id,
                    start = Instant.fromEpochSeconds(now - 20 * 60 + offset * 3600),
                    stop = Instant.fromEpochSeconds(now + 40 * 60 + offset * 3600),
                    title = when {
                        offset > 0 -> "${channel.name}: Next Programme $offset"
                        channel.id == ChannelId(1) -> CURRENT_TITLE
                        channel.id == ChannelId(2) -> LONG_TITLE
                        else -> "An Evening on ${channel.name}"
                    },
                    summary = DESCRIPTION,
                    image = if (channel.id == ChannelId(1)) "imagecache/101" else if (channel.id == ChannelId(2)) "imagecache/102" else null,
                    genre = "Documentary",
                    nextEventId = if (offset < 4) EventId(channel.id.value * 100 + offset + 1) else null,
                )
            }
        }
        return SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels, tags)),
            epgState = EpgRepositoryState.Current(
                EpgSnapshot.create(
                    events = events,
                    coverages = channels.map {
                        EpgCoverage.create(
                            channelId = it.id,
                            coveredFrom = Instant.fromEpochSeconds(now - 86400),
                            coveredTo = Instant.fromEpochSeconds(now + 7 * 86400),
                        )
                    },
                ),
            ),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(if (emptyRecordings) emptyList() else recordings(now))),
            recordingProgressCapability = RecordingProgressCapability.UNSUPPORTED,
        )
    }

    private fun recordings(now: Long): List<DvrEntry> = listOf(
        "Documentaries/storm-coast.ts" to "Tide Watch: The Remarkable Journey Along the Storm Coast in Deep Winter",
        "Documentaries/blue-planet.ts" to CURRENT_TITLE,
        "Documentaries/mountain-paths.ts" to "Mountain Paths: The Last Alpine Villages",
        "News/evening-report.ts" to "Evening Report",
        "News/week-in-review.ts" to "The Week in Review",
        "Sport/cup-final.ts" to "The Cup Final",
        "weekend-concert.ts" to "The Weekend Concert",
    ).mapIndexed { index, (path, title) ->
        DvrEntry.create(
            id = DvrEntryId(index + 1L), title = title,
            start = Instant.fromEpochSeconds(now - (index + 1) * 86400L),
            stop = Instant.fromEpochSeconds(now - (index + 1) * 86400L + 90 * 60),
            state = DvrEntryState.COMPLETED,
            path = path,
            files = listOf(DvrRecordingFile(fileId = null, path = path, start = null, stop = null, sizeBytes = 2_400_000_000L)),
            playPosition = if (index == 0) 3723.seconds else null,
            channelName = when (index) { 0 -> "Tide Watch"; 1, 2 -> "Ridge Earth HD"; 5 -> "Harbor Sport"; else -> "Northline News" },
            image = when (index) { 0 -> "imagecache/103"; 1 -> "imagecache/101"; 5 -> "imagecache/102"; else -> null },
            subtitle = if (index == 0) "Six weeks with the coastguard as the winter storms arrive" else null,
            description = DESCRIPTION,
        )
    }

    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private fun context(): Application = ApplicationProvider.getApplicationContext()

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun awaitFocus(target: SemanticsMatcher) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(target and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(target and isFocused()).assertIsFocused().assertIsDisplayed()
    }

    private fun capture(
        name: String,
        scale: Float,
        scenario: String,
        focus: SemanticsMatcher,
        dialog: Boolean = false,
    ) {
        awaitFocus(focus)
        compose.runOnIdle {
            assertEquals("Browsing/details must not dispatch playback", 0, playbackRequests)
            assertEquals("Browsing/details must not navigate away", 0, navigationRequests)
            assertEquals("Browsing/details must not issue SDK commands", initialSdkCalls, session.calls.size)
            assertFalse("Guide runtime must not tune", FakeSessionCall.BIND_LIVE_PLAYBACK in session.calls)
            if (::player.isInitialized) {
                assertEquals(null, player.currentMediaItem)
                assertFalse(player.playWhenReady)
            }
        }
        val rail = compose.onNodeWithTag("global-drawer-surface").fetchSemanticsNode().boundsInRoot
        assertEquals("Production closed push rail", 80f, rail.width, 0.01f)
        val bitmap = draw(dialog)
        assertEquals(960, bitmap.width)
        assertEquals(540, bitmap.height)
        compose.waitForIdle()
        val settled = draw(dialog)
        assertTrue("$name captured mid-animation", bitmap.sameAs(settled))
        settled.recycle()
        val directory = File("build/outputs/browse-cohesion/implemented").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        File(directory, "$name.txt").writeText(
            """
            baseRevision=f3180df
            captureSource=ux/browse-cohesion implementation worktree
            canvas=960x540 dp; pixels=960x540; density=1.0 (mdpi)
            locale=en-US; resourceQualifier=en; fontScale=$scale; timezone=UTC
            shell=TVHeadendPlayerTheme + SideRail; closedPushRail=80dp; no extra insets
            shellContentPadding=start ${TvScreenPadding.calculateLeftPadding(LayoutDirection.Ltr)}, end ${TvScreenPadding.calculateRightPadding(LayoutDirection.Ltr)}, top ${TvScreenPadding.calculateTopPadding()}, bottom ${TvScreenPadding.calculateBottomPadding()}
            scenario=$scenario
            assertedNativeFocus=${focus.description}; D-pad input, no painted focus override
            background=synthetic opaque MaterialTheme.colorScheme.background; no video or playback surface
            artwork=deliberately absent in SDK fixtures; no remote images
            clockBasis=relative-to-run fixtures; screens sample System.currentTimeMillis; not fixed wall-clock
            fixtureEpochMillis=$fixtureEpochMillis; fixtureUtc=${java.time.Instant.ofEpochMilli(fixtureEpochMillis)}
            captureEpochMillis=${System.currentTimeMillis()}
            rendering=Robolectric SDK34 native View.draw(Bitmap); dialogDecorComposited=$dialog; no platform window compositor
            sdkCallsBeforeBrowse=$initialSdkCalls; sdkCallsAtCapture=${session.calls.size}; playbackRequests=$playbackRequests; navigationRequests=$navigationRequests
            sdkCallKindsAtCapture=${session.calls}
            evidenceLimit=static production composition only; not physical-TV focus feel, motion, overscan, remote repeat or SurfaceView proof
            """.trimIndent() + "\n",
        )
        bitmap.recycle()
    }

    private fun draw(dialog: Boolean): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            if (dialog) {
                val decor = checkNotNull(ShadowDialog.getLatestDialog()?.window?.decorView)
                assertEquals(view.width, decor.width)
                assertEquals(view.height, decor.height)
                decor.draw(canvas)
            }
        }
        return bitmap
    }

    private companion object {
        const val CURRENT_TITLE = "Ridge Light"
        const val LONG_TITLE = "Harbor Kickoff: The Remarkable Journey to the Final Across the Northern Coast"
        const val DESCRIPTION = "Follow a small team of naturalists through remote landscapes as they explore " +
            "the changing seasons, meet the people who call these places home and discover the wildlife " +
            "that survives at the edge of the world. This extended programme includes journeys by sea, " +
            "mountain and frozen river, with stories from local communities and the science behind the spectacle."
    }
}
