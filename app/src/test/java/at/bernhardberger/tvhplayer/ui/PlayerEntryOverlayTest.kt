@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.navigation3.runtime.NavBackStack
import kotlinx.serialization.serializer
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.currentRecordingPlaybackSelection
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsStorageViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.CountDownLatch

/**
 * Watch from an overlay goes straight into the player: the context menu or details page that is
 * up when the action is pressed fades out with the browse shell, and its screen is never shown
 * alone on the way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(TvheadendTestingApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
class PlayerEntryOverlayTest {
    @get:Rule val compose = createComposeRule()
    private val savedState = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val catalog = ChannelCatalog.create((1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) })
    private lateinit var player: ExoPlayer
    private lateinit var view: View
    private lateinit var session: FakeTvheadendSession
    private lateinit var runtime: AppPlaybackRuntime
    private val loadGate = CountDownLatch(1)

    @After fun after() {
        loadGate.countDown()
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    @Test fun watchFromTheContextMenuFadesTheMenuIntoThePlayer() = watchFromOverlay(details = false)

    @Test fun watchFromDetailsFadesTheDetailsIntoThePlayer() = watchFromOverlay(details = true)

    @Test fun resumeFromRecordingDetailsKeepsItsPresentationWhileFadingIntoThePlayer() =
        watchFromOverlay(details = true, recordings = true)

    @Test fun continueFromWarmRecordingDetailsKeepsItsPresentationWhileFadingIntoThePlayer() =
        watchFromOverlay(details = true, recordings = true, warm = true)

    @Test fun watchChannelFromAMenuWithoutProgrammeFadesTheMenuIntoThePlayer() =
        watchFromOverlay(details = false, programme = false)

    @Test fun recordingRouteReturnsToTheWarmPlayerWithoutRestartingOrResumePrompt() {
        recordingRow()
        val bindings = session.calls.count { it == FakeSessionCall.BIND_RECORDING_PLAYBACK }
        val selection = runtime.recordingSelection.value
        assertEquals(DvrEntryId(7), warmRecordingId(runtime.activeTarget.value, selection, session.observation.value.currentSession))
        compose.onNodeWithTag("recording-list-entry-7").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithTag("player-banner", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(7)), runtime.activeTarget.value)
            assertEquals(selection, runtime.recordingSelection.value)
            assertEquals(bindings, session.calls.count { it == FakeSessionCall.BIND_RECORDING_PLAYBACK })
        }

        compose.runOnIdle {
            val stack = savedState.performSave().values.flatten().filterIsInstance<android.os.Bundle>()
                .mapNotNull { runCatching { decodeFromSavedState(serializer<NavBackStack<AppNavKey>>(), it) }.getOrNull() }
                .single()
            assertEquals(recordingPlayerKey(DvrEntryId(7), RecordingPlaybackStart.RESUME), stack.last())
        }
    }

    @Test fun recordingStoppedAfterRenderingUsesTheNormalResumeChoice() = staleRecordingReturn(replace = false)

    @Test fun recordingReplacedAfterRenderingUsesTheSelectedRowsResumeChoice() = staleRecordingReturn(replace = true)

    private fun staleRecordingReturn(replace: Boolean) {
        recordingRow()
        // Keep the rendered action, then change playback before its activation without using a new composition's action.
        val activate = compose.onNodeWithTag("recording-list-entry-7").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        var activated = false
        var bindings = 0
        compose.runOnIdle {
            scope.launch {
                if (replace) runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(8))),
                    RecordingPlaybackStart.RESUME)
                else runtime.stop()
                bindings = session.calls.count { it == FakeSessionCall.BIND_RECORDING_PLAYBACK }
                activate()
                activated = true
            }
        }
        compose.waitUntil(10_000) { activated }
        settle()
        compose.onNodeWithTag("recording-resume").assertExists()
        compose.onNodeWithTag("player-banner", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(bindings, session.calls.count { it == FakeSessionCall.BIND_RECORDING_PLAYBACK }) }
    }

    private fun recordingRow() {
        app(programme = true, recordings = true)
        compose.runOnIdle {
            scope.launch {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(7))),
                    RecordingPlaybackStart.RESUME)
            }
        }
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Recording(DvrEntryId(7)) }
        compose.onNodeWithTag("nav-recordings").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("recording-list-entry-7")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("recording-list-entry-7").requestFocus()
        settle()
    }

    private fun watchFromOverlay(details: Boolean, programme: Boolean = true, recordings: Boolean = false, warm: Boolean = false) {
        if (warm) recordingRow() else app(programme, recordings)
        if (recordings && !warm) {
            compose.onNodeWithTag("nav-recordings").performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("recording-list-entry-7")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("recording-list-entry-7").requestFocus()
            settle()
        }
        val browse = frame()
        key(Key.Menu)
        compose.onNodeWithTag("browse-context-menu").assertExists()
        if (details) {
            compose.onNode(hasText(if (recordings) "Details" else "Program details") and hasClickAction())
                .performSemanticsAction(SemanticsActions.OnClick)
            settle()
            compose.onNodeWithTag(if (warm) "details-continue_watching" else if (recordings) "details-resume" else "details-watch").assertExists()
        }
        val overlay = frame()
        fun detailActions() = compose.onAllNodes(hasAnyAncestor(hasTestTag("details-actions")), useUnmergedTree = true)
            .fetchSemanticsNodes().let { nodes ->
                nodes.flatMap { node -> if (SemanticsProperties.Text in node.config)
                    node.config[SemanticsProperties.Text].map { it.text } else emptyList() } to
                    nodes.filter { SemanticsActions.OnClick in it.config }
                        .map { it.config[SemanticsProperties.TestTag] to it.boundsInRoot.center }
            }
        val pressedActions = if (details) detailActions() else null
        var departureSamples = 0

        compose.mainClock.autoAdvance = false
        val watch = if (details) hasTestTag(if (warm) "details-continue_watching" else if (recordings) "details-resume" else "details-watch")
            else hasText(if (programme) "Watch" else "Watch channel") and hasClickAction() and
                hasAnyAncestor(hasTestTag("browse-context-menu"))
        compose.onNode(watch).performSemanticsAction(SemanticsActions.OnClick)
        val frames = (1..(PlayerMotion.DestinationMs + 100) / 16).map {
            compose.mainClock.advanceTimeByFrame()
            if (details) {
                val departingActions = detailActions()
                if (departingActions.first.isNotEmpty()) {
                    assertEquals("departure frame $it keeps action labels and positions", pressedActions, departingActions)
                    departureSamples++
                }
            }
            frame()
        }
        if (details) assertTrue("sampled the departing details actions", departureSamples > 0)
        compose.mainClock.autoAdvance = true
        settle()
        compose.onNodeWithTag(if (recordings) "player-banner" else "player-page-first", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        val shown = frame()
        val fits = frames.map { drawn -> residual(drawn, overlay, shown) to residual(drawn, browse, shown) }
        assertTrue("the overlay is still up on the first frame: ${fits.first()}", fits.first().let { (o, b) -> o < b / 2 })
        fits.forEachIndexed { index, (withOverlay, alone) ->
            // Once the shell has faded out both fits are the player alone and agree.
            assertTrue("frame ${index + 1} shows the browse screen without its overlay ($withOverlay vs $alone)",
                withOverlay < alone + 0.5)
        }
    }

    private fun app(programme: Boolean, recordings: Boolean = false) {
        // The app reads the wall clock to find the programme on air, so it spans every time.
        val event = EpgEvent.create(EventId(1), ChannelId(1), AIRS_FROM, AIRS_UNTIL, title = "Ridge Light")
        val entries = if (recordings) listOf(7L, 8L).map { id -> DvrEntry.create(
            DvrEntryId(id), title = "Recording $id", state = DvrEntryState.COMPLETED,
            start = AIRS_FROM, stop = AIRS_FROM + 3600.seconds, folder = emptyList(), playPosition = 754.seconds,
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "recording.ts", start = AIRS_FROM,
                stop = AIRS_FROM + 3600.seconds)),
        ) } else emptyList()
        session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(catalog),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(if (programme) listOf(event) else emptyList())),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries)),
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED,
        )).apply { scriptLivePlaybackSuccess(); if (recordings) scriptRecordingPlaybackSuccess() }
        val settings = PlayerSettingsStore(InMemoryData())
        val profiles = AppProfileOwner(session, FakeServerProfileStore(ServerProfileReadResult.anonymous("offline.invalid", 9982)),
            settings, Dispatchers.IO, readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        player = ExoPlayer.Builder(context).build()
        val base = player
        val coordinatorPlayer = if (!recordings) base else object : ExoPlayer by base {
            override fun setMediaSource(mediaSource: MediaSource) = base.setMediaSource(loading(mediaSource.mediaItem))
            override fun setMediaSource(mediaSource: MediaSource, startPositionMs: Long) =
                base.setMediaSource(loading(mediaSource.mediaItem), startPositionMs)
            override fun setMediaSource(mediaSource: MediaSource, resetPosition: Boolean) =
                base.setMediaSource(loading(mediaSource.mediaItem), resetPosition)
        }
        val coordinator = createTvheadendPlaybackCoordinator(coordinatorPlayer).also { it.launchIn(scope) }
        runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings())
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        val dependencies = module {
            single<TvheadendSession> { session }
            single { runtime }
            single { profiles }
            single { settings }
            single { ChannelTagSettingsStore(InMemoryData()) }
            // These journeys walk the list's rows.
            single { UiSettingsStore(InMemoryData()).apply { runBlocking { setChannelsView(ChannelsView(ChannelsLayout.LIST)) } } }
            single { LastPlayedChannelStore(context) }
            single { ChannelSelectionStore() }
            single { GuidePositionStore() }
            single { notices }
            single { ConnectionNoticeSource(session.observation, profiles.configurationGeneration, notices) }
            viewModel { AppConnectionViewModel(get(), get(), get()) }
            viewModel { ChannelsViewModel(get(), get()) }
            viewModel { VideoPlayerViewModel(get(), get()) }
            viewModel { SettingsStorageViewModel(session.cache, get(), ChannelAccentStore()) }
            viewModel { SettingsPlayerViewModel(get(), get(), get(), get(), get()) }
        }
        val server = ServerSettings(host = "offline.invalid")
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalSaveableStateRegistry provides savedState) {
            KoinIsolatedContext(koinApplication { modules(dependencies) }) {
                TVHeadendPlayerTheme {
                    AppRoot(
                        startupState = MainStartupState.Ready(server, autoStartPlayback = false),
                        runtimeServerSettings = server,
                        applianceLaunchRequests = ApplianceLaunchRequests(),
                        onPlayerVisibilityChanged = {},
                        onRequestExit = {},
                    )
                }
            }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("channel-row-1")).fetchSemanticsNodes().isNotEmpty()
        }
        settle()
        compose.waitUntil(10_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodes(hasTestTag("nav-channels") and isFocused()).fetchSemanticsNodes().isNotEmpty()) {
            key(Key.DirectionRight)
        }
        compose.onNodeWithTag("channel-row-1").assertExists()
        settle()
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
        settle()
    }

    private fun loading(item: MediaItem): MediaSource = ProgressiveMediaSource.Factory {
        object : BaseDataSource(false) {
            override fun open(dataSpec: DataSpec): Long {
                loadGate.await()
                throw java.io.IOException("closed")
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
            override fun getUri(): Uri? = null
            override fun close() = Unit
        }
    }.createMediaSource(item)

    private fun settle() {
        repeat(10) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    /** The luminance drawn now, every fourth pixel each way. */
    private fun frame(): FloatArray = compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val values = ArrayList<Float>()
        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                val pixel = bitmap.getPixel(x, y)
                values += 0.299f * android.graphics.Color.red(pixel) + 0.587f * android.graphics.Color.green(pixel) +
                    0.114f * android.graphics.Color.blue(pixel)
            }
        }
        bitmap.recycle()
        values.toFloatArray()
    }

    /**
     * How far [drawn] is from the best blend of [leaving] with the shown player [entering]: a
     * cross-fade frame is close to one built from the screen it actually fades out.
     */
    private fun residual(drawn: FloatArray, leaving: FloatArray, entering: FloatArray): Double {
        // Least squares for drawn ≈ a·leaving + b·entering + c.
        val columns = arrayOf(leaving, entering, FloatArray(drawn.size) { 1f })
        val normal = Array(3) { i -> DoubleArray(3) { j -> dot(columns[i], columns[j]) } }
        val right = DoubleArray(3) { i -> dot(columns[i], drawn) }
        val weights = solve(normal, right)
        var sum = 0.0
        for (k in drawn.indices) {
            val fit = weights[0] * leaving[k] + weights[1] * entering[k] + weights[2]
            sum += (drawn[k] - fit) * (drawn[k] - fit)
        }
        return kotlin.math.sqrt(sum / drawn.size)
    }

    private fun dot(a: FloatArray, b: FloatArray): Double {
        var sum = 0.0
        for (k in a.indices) sum += a[k].toDouble() * b[k]
        return sum
    }

    /** Cramer's rule, with a tiny ridge so identical columns stay solvable. */
    private fun solve(m: Array<DoubleArray>, v: DoubleArray): DoubleArray {
        val a = Array(3) { i -> DoubleArray(3) { j -> m[i][j] + if (i == j) 1e-6 * m[i][i] + 1e-9 else 0.0 } }
        fun det(x: Array<DoubleArray>) =
            x[0][0] * (x[1][1] * x[2][2] - x[1][2] * x[2][1]) -
                x[0][1] * (x[1][0] * x[2][2] - x[1][2] * x[2][0]) +
                x[0][2] * (x[1][0] * x[2][1] - x[1][1] * x[2][0])
        val d = det(a)
        return DoubleArray(3) { c -> det(Array(3) { i -> DoubleArray(3) { j -> if (j == c) v[i] else a[i][j] } }) / d }
    }

    private companion object {
        val AIRS_FROM = Instant.parse("2000-01-01T00:00:00Z")
        val AIRS_UNTIL = Instant.parse("2100-01-01T00:00:00Z")
    }

    private class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }
}
