package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.*
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.CountDownLatch
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.player.NowPlayingStrip
import at.bernhardberger.tvhplayer.ui.screens.ProgrammeContextMenu
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadline
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadlineProgramme
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingListRow
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class, TvheadendTestingApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)
class WatchingProgressTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val locale = Locale.getDefault()
    private val zone = TimeZone.getDefault()
    private lateinit var view: View
    private var viewerColor = 0
    private var ambientColor = 0
    private var backgroundColor = 0
    private val start = Instant.parse("2026-09-15T19:00:00Z")
    private val now = start.epochSeconds + 1800
    private val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = ArtworkId(1))
    private val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds,
        title = "Ridge Light", subtitle = "Beyond the Arctic Circle", image = ArtworkId(2),
        summary = "A winter expedition follows the changing light across the ridge.")
    private val previousEvent = EpgEvent.create(EventId(2), channel.id, start - 3600.seconds, start,
        title = "Before the Ridge")
    private val recording = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = event.title,
        channelName = channel.name, start = start, stop = event.stop, state = DvrEntryState.COMPLETED,
        image = event.image, playPosition = 60.seconds)
    private val observation = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).observation.value
    private val position = mutableStateOf<RecordingViewerPosition?>(RecordingViewerPosition(recording.id, 1800000, 7200000))
    private val livePosition = mutableStateOf<LiveViewerPosition?>(LiveViewerPosition(channel.id, now))
    private val indicator = mutableStateOf(ChannelPlaybackIndicator.PLAYING)
    private val target = mutableStateOf<AppPlaybackTarget?>(AppPlaybackTarget.Live(channel.id))

    @Before fun prepare() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(2) to FixtureArt.art("ridge-light"))))
    }
    @After fun restore() { Locale.setDefault(locale); TimeZone.setDefault(zone) }

    @Test fun watchingUsesViewerColorAndOtherItemsUseAmbientColor() {
        assertEquals(Color.Yellow, progressColor(true, Color.Cyan, Color.Yellow))
        assertEquals(Color.Cyan, progressColor(false, Color.Cyan, Color.Yellow))
    }

    @Test fun recordingUsesTheSamePlayingPausedAndStoppedPolicyAsLive() {
        val item = AppPlaybackTarget.Recording(recording.id)
        assertEquals(ChannelPlaybackIndicator.PLAYING, playbackIndicator(item, item, AppPlaybackState.Playing, true))
        assertEquals(ChannelPlaybackIndicator.PAUSED, playbackIndicator(item, item, AppPlaybackState.Buffering, false))
        assertEquals(ChannelPlaybackIndicator.NONE, playbackIndicator(item, item, AppPlaybackState.Idle, true))
        assertEquals(ChannelPlaybackIndicator.NONE, playbackIndicator(item, AppPlaybackTarget.Live(channel.id), AppPlaybackState.Playing, true))
        assertEquals(null, RecordingViewerPosition(recording.id, 0, -1).fraction)
        assertEquals(1f, RecordingViewerPosition(recording.id, 9000, 1000).fraction)
    }

    @Test fun guideHeadlineMarksOnlyThePlayingProgramme() {
        show("guide")
        marker().assertExists()
        val bounds = marker().fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        assertTrue("The passive headline's marker must have a visible neutral tint",
            (bounds.top.toInt() until bounds.bottom.toInt()).any { y ->
                (bounds.left.toInt() until bounds.right.toInt()).any { x ->
                    android.graphics.Color.red(bitmap.getPixel(x, y)) > 100
                }
            })
        bitmap.recycle()
        compose.runOnIdle { target.value = AppPlaybackTarget.Live(ChannelId(2)) }
        marker().assertDoesNotExist()
    }
    @Test fun programmeDetailsReplacePlayIconAndRetainPauseMark() {
        show("programme")
        marker().assertExists()
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.runOnIdle { indicator.value = ChannelPlaybackIndicator.PAUSED }
        compose.onNodeWithTag("channel-paused-indicator", useUnmergedTree = true).assertExists()
        assertOrangeProgress()
    }
    @Test fun contextMenuMarksThePlayingProgramme() { show("menu"); marker().assertExists() }
    @Test fun detailsMarkBelongsToTheActionTarget() {
        show("mixed")
        compose.onAllNodesWithTag("channel-playing-indicator", useUnmergedTree = true).assertCountEquals(1)
        marker().assert(hasAnyAncestor(hasTestTag("details-watch")))
        compose.runOnIdle { target.value = AppPlaybackTarget.Recording(recording.id) }
        compose.onAllNodesWithTag("channel-playing-indicator", useUnmergedTree = true).assertCountEquals(1)
        marker().assert(hasAnyAncestor(hasTestTag("details-play")))
    }
    @Test fun inactiveLiveDetailsAreAmbientEvenWithAWarmTarget() {
        indicator.value = ChannelPlaybackIndicator.NONE
        show("programme")
        marker().assertDoesNotExist()
        assertProgressColor(ambientColor)
    }
    @Test fun tuningGuideIsNotViewerProgress() {
        indicator.value = ChannelPlaybackIndicator.TUNING
        show("guide")
        assertProgressColor(ambientColor)
    }
    @Test fun unrelatedRecordingDoesNotReadSample() {
        var reads = 0
        val sample = object : State<RecordingViewerPosition?> {
            override val value: RecordingViewerPosition? get() { reads++; return position.value }
        }
        compose.setContent {
            CompositionLocalProvider(LocalRecordingPosition provides sample,
                LocalWatchingTarget provides AppPlaybackTarget.Recording(recording.id),
                LocalWatchingIndicator provides ChannelPlaybackIndicator.PLAYING) {
                recordingViewerPosition(null)
                recordingViewerPosition(DvrEntryId(99))
            }
        }
        compose.runOnIdle { assertEquals(0, reads) }
    }
    @Test fun contextSynopsisDoesNotMoveWhenMarkChanges() {
        show("menu")
        val description = compose.onNodeWithText("${event.subtitle}\n${event.summary}", useUnmergedTree = true)
        val top = description.fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { indicator.value = ChannelPlaybackIndicator.NONE }
        assertEquals(top, description.fetchSemanticsNode().boundsInRoot.top)
    }
    @Test fun guideUsesPausedAndRewoundViewerFraction() = liveFractions("guide")
    @Test fun contextUsesPausedAndRewoundViewerFraction() = liveFractions("menu")
    @Test fun detailsUsePausedAndRewoundViewerFraction() = liveFractions("programme")
    @Test fun zapUsesPausedAndRewoundViewerFraction() = liveFractions("zap")
    @Test fun unknownLivePositionDoesNotInventViewerProgress() {
        livePosition.value = liveViewerPosition(channel.id, AppTimeshiftState(available = true, paused = true,
            timingKnown = false))
        show("programme")
        progressBar().assertDoesNotExist()
        marker().assertExists()
        compose.onNodeWithText("19:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("21:00", useUnmergedTree = true).assertExists()
    }
    @Test fun detailsTimelineVisibilityDoesNotReadLiveSample() {
        var reads = 0
        var visible = false
        val sample = object : State<LiveViewerPosition?> {
            override val value: LiveViewerPosition? get() { reads++; return livePosition.value }
        }
        compose.setContent {
            CompositionLocalProvider(LocalLivePosition provides sample,
                LocalWatchingChannel provides channel.id,
                LocalWatchingTarget provides AppPlaybackTarget.Live(channel.id),
                LocalWatchingIndicator provides ChannelPlaybackIndicator.PAUSED) {
                visible = detailsTimelineVisible(event, event.stop.epochSeconds + 1)
            }
        }
        compose.runOnIdle { assertTrue(visible); assertEquals(0, reads) }
    }
    @Test fun unrelatedLiveProgrammeDoesNotReadSample() {
        var reads = 0
        val sample = object : State<LiveViewerPosition?> {
            override val value: LiveViewerPosition? get() { reads++; return livePosition.value }
        }
        compose.setContent {
            CompositionLocalProvider(LocalLivePosition provides sample,
                LocalWatchingChannel provides ChannelId(99),
                LocalWatchingTarget provides AppPlaybackTarget.Live(ChannelId(99)),
                LocalWatchingIndicator provides ChannelPlaybackIndicator.PLAYING) {
                programmeProgress(event, now)
                watchingIndicator(event, now)
            }
        }
        compose.runOnIdle { assertEquals(0, reads) }
    }
    @Test fun compactCardUsesPausedAndRewoundViewerFraction() = liveFractions("card")
    @Test fun channelRowUsesPausedAndRewoundViewerFraction() = liveFractions("channel-row")
    @Test fun channelsHeadlineUsesPausedAndRewoundViewerFraction() = liveFractions("channels-headline")
    @Test fun zapShowsTheProgrammeAtTheRewoundPosition() {
        livePosition.value = LiveViewerPosition(channel.id, start.epochSeconds - 1800)
        indicator.value = ChannelPlaybackIndicator.PAUSED
        show("zap")
        compose.onNodeWithText(previousEvent.title!!, useUnmergedTree = true).assertExists()
        assertFraction(.5f)
        assertOrangeProgress()
    }
    @Test fun zapFallsBackToCurrentProgrammeWhenHistoryIsMissing() {
        livePosition.value = LiveViewerPosition(channel.id, start.epochSeconds - 1800)
        show("zap-no-history")
        compose.onNodeWithText(event.title!!, useUnmergedTree = true).assertExists()
    }
    @Test fun guideAtLiveEdgeHasNoHiddenFirstFrame() = liveEdgeFractions("guide")
    @Test fun contextAtLiveEdgeHasNoHiddenFirstFrame() = liveEdgeFractions("menu")
    @Test fun detailsAtLiveEdgeHasNoHiddenFirstFrame() = liveEdgeFractions("programme")
    @Test fun zapAtLiveEdgeHasNoHiddenFirstFrame() = liveEdgeFractions("zap")

    private fun liveEdgeFractions(surface: String) {
        livePosition.value = null // New target: the first sample has not arrived yet.
        show(surface)
        progressBar().assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(0.25f, 0f..1f)))
        assertOrangeProgress()
        compose.runOnIdle {
            livePosition.value = liveViewerPosition(channel.id, AppTimeshiftState())
        }
        progressBar().assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(0.25f, 0f..1f)))
        assertOrangeProgress()
        compose.runOnIdle {
            livePosition.value = liveViewerPosition(channel.id,
                AppTimeshiftState(available = true, paused = true, serverBehindLiveMs = 60_000, timingKnown = false))
        }
        progressBar().assertDoesNotExist()
        // A zap resets the sample, rather than carrying the prior target's unknown timing.
        compose.runOnIdle {
            target.value = AppPlaybackTarget.Live(ChannelId(2))
        }
        compose.runOnIdle {
            livePosition.value = null
            target.value = AppPlaybackTarget.Live(channel.id)
        }
        progressBar().assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(0.25f, 0f..1f)))
        assertOrangeProgress()
    }
    @Test fun liveSampleRequiresCorrelatedWallClockEvidence() {
        val fixture = TimeshiftTestFixture(7200.seconds)
        fixture.updateHistory(0.seconds, 1800.seconds, estimatedLiveEdgeTime = Instant.fromEpochSeconds(now))
        val sample = fixture.state.value.toAppPresentation(fixture.playbackPosition(900.seconds))
        assertEquals(LiveViewerPosition(channel.id, start.epochSeconds + 900), liveViewerPosition(channel.id, sample))
        assertEquals(LiveViewerPosition(channel.id, null), liveViewerPosition(channel.id, sample.copy(timingKnown = false)))
        assertEquals(LiveViewerPosition(channel.id, null), liveViewerPosition(channel.id, sample.copy(playbackTarget = null)))
        assertEquals(null, liveViewerPosition(channel.id, AppTimeshiftState()))
        assertEquals(null, liveViewerPosition(channel.id, AppTimeshiftState(available = true,
            serverBehindLiveMs = 0L, timingKnown = false)))
        fixture.replaceSubscription()
        fixture.updateHistory(0.seconds, 1800.seconds, estimatedLiveEdgeTime = Instant.fromEpochSeconds(now))
        assertEquals(LiveViewerPosition(channel.id, null), liveViewerPosition(channel.id,
            sample.copy(timeline = fixture.state.value.toAppPresentation().timeline)))
    }

    @Test fun providerResetsRecordingSamplesOnTargetChange() = providerScenario(liveFailure = false)
    @Test fun providerKeepsFailedLiveAmbient() = providerScenario(liveFailure = true)

    private fun providerScenario(liveFailure: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry.createUnsafe(this) }
        val base = ExoPlayer.Builder(context).build()
        val gate = CountDownLatch(1)
        val listeners = mutableListOf<Player.Listener>()
        var ready = false
        var positionMs = 900_000L
        val player = object : ExoPlayer by base {
            override fun getPlaybackState() = if (ready) Player.STATE_READY else base.playbackState
            override fun isPlaying() = ready && base.playWhenReady
            override fun getCurrentPosition() = positionMs
            override fun getDuration() = 7_200_000L
            override fun addListener(listener: Player.Listener) { listeners += listener; base.addListener(listener) }
            override fun removeListener(listener: Player.Listener) { listeners -= listener; base.removeListener(listener) }
        }
        fun loading(item: MediaItem): MediaSource = ProgressiveMediaSource.Factory {
            object : BaseDataSource(false) {
                override fun open(dataSpec: DataSpec): Long { gate.await(); throw java.io.IOException("closed") }
                override fun read(buffer: ByteArray, offset: Int, length: Int) = C.RESULT_END_OF_INPUT
                override fun getUri(): android.net.Uri? = null
                override fun close() = Unit
            }
        }.createMediaSource(item)
        val second = DvrEntry.create(DvrEntryId(2), state = DvrEntryState.COMPLETED, title = "Second recording")
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(event))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(recording, second))),
        )).apply { scriptRecordingPlaybackSuccess(); scriptLivePlaybackSuccess(); scriptStreamProfiles(emptyList()) }
        val settings = PlayerSettingsStore(object : DataStore<Preferences> {
            private val state = MutableStateFlow(emptyPreferences())
            override val data: Flow<Preferences> get() = state
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                transform(state.value).also { state.value = it }
        })
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        val coordinator = createTvheadendPlaybackCoordinator(object : ExoPlayer by player {
            override fun setMediaSource(mediaSource: MediaSource) = base.setMediaSource(if (liveFailure) mediaSource else loading(mediaSource.mediaItem))
            override fun setMediaSource(mediaSource: MediaSource, startPositionMs: Long) =
                base.setMediaSource(if (liveFailure) mediaSource else loading(mediaSource.mediaItem), startPositionMs)
            override fun setMediaSource(mediaSource: MediaSource, resetPosition: Boolean) =
                base.setMediaSource(if (liveFailure) mediaSource else loading(mediaSource.mediaItem), resetPosition)
        }).also { it.launchIn(scope) }
        val runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None,
            PlaybackRuntimePolicy.fromPlayerSettings(object : PlaybackTrace {}))
        val seen = mutableListOf<Pair<AppPlaybackTarget?, RecordingViewerPosition?>>()
        var publishedIndicator = ChannelPlaybackIndicator.NONE
        var publishedColor: Color? = null
        fun install(id: DvrEntryId) {
            val job = scope.async { runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, id)),
                RecordingPlaybackStart.START_OVER) }
            compose.waitUntil(10_000) { job.isCompleted }
            compose.runOnIdle {
                ready = true
                listeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY); it.onIsPlayingChanged(true) }
            }
            compose.waitUntil(5_000) { runtime.state.value == AppPlaybackState.Playing }
        }
        try {
            scope.launch { profiles.run() }
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            compose.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    TVHeadendPlayerTheme {
                        ProvideWatchingProgress(runtime) {
                            val active = LocalWatchingTarget.current
                            val sample = LocalRecordingPosition.current?.value
                            val current = LocalWatchingIndicator.current
                            val color = progressColor(watchingIndicator(event, now).isPlayingItem)
                            val ambient = MaterialTheme.colorScheme.primary
                            SideEffect {
                                seen += active to sample
                                publishedIndicator = current
                                publishedColor = color
                                ambientColor = ambient.toArgb()
                            }
                        }
                    }
                }
            }
            if (!liveFailure) {
                install(recording.id)
                compose.waitUntil(5_000) { seen.any { it.second?.recordingId == recording.id } }
                compose.runOnIdle { positionMs = 120_000L; ready = false }
                install(second.id)
                compose.waitUntil(5_000) { seen.any { it.second?.recordingId == second.id } }
                compose.runOnIdle {
                    val next = seen.filter { it.first == AppPlaybackTarget.Recording(second.id) }
                    assertTrue(next.isNotEmpty())
                    assertEquals(null, next.first().second)
                    assertTrue(next.all { it.second == null || it.second == RecordingViewerPosition(second.id, 120_000L, 7_200_000L) })
                    base.pause()
                }
                compose.waitUntil(5_000) { publishedIndicator == ChannelPlaybackIndicator.PAUSED }
                compose.runOnIdle { positionMs = 60_000L }
                compose.mainClock.advanceTimeBy(1100)
                compose.waitUntil(5_000) { seen.last().second?.positionMs == 60_000L }
                compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
                compose.runOnIdle { positionMs = 30_000L }
                compose.mainClock.advanceTimeBy(2100)
                compose.runOnIdle { assertEquals(60_000L, seen.last().second?.positionMs) }
                compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
                compose.waitUntil(5_000) { seen.last().second?.positionMs == 30_000L }
                return
            }
            val live = scope.async { runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, channel.id))) }
            compose.waitUntil(10_000) {
                // Profile resolution resumes on the Android looper, not Compose's frame clock.
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                live.isCompleted
            }
            assertEquals("Live install", at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult.STARTED,
                runBlocking { live.await() })
            compose.runOnIdle {
                listeners.toList().forEach { it.onPlayerError(PlaybackException("scripted failure", null, PlaybackException.ERROR_CODE_DECODING_FAILED)) }
            }
            compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Failed }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(AppPlaybackTarget.Live(channel.id), runtime.activeTarget.value)
                assertEquals(ChannelPlaybackIndicator.NONE, publishedIndicator)
                assertEquals(ambientColor, publishedColor?.toArgb())
            }
            live.cancel()
        } finally {
            gate.countDown()
            scope.cancel()
            base.release()
        }
    }

    private fun liveFractions(surface: String) {
        indicator.value = ChannelPlaybackIndicator.PAUSED
        livePosition.value = LiveViewerPosition(channel.id, start.epochSeconds + 1200)
        show(surface)
        assertFraction(1f / 6f)
        if (surface != "menu") assertProgressColor(viewerColor, dimmed = surface == "card")
        compose.runOnIdle { livePosition.value = LiveViewerPosition(channel.id, start.epochSeconds + 600) }
        assertFraction(1f / 12f)
        if (surface != "menu") assertProgressColor(viewerColor, dimmed = surface == "card")
        compose.runOnIdle { target.value = AppPlaybackTarget.Live(ChannelId(99)) }
        assertFraction(.25f)
        if (surface != "menu") assertProgressColor(ambientColor, dimmed = surface == "card")
    }

    private fun assertFraction(value: Float) = progressBar().assert(
        SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(value, 0f..1f)))
    @Test fun nowPlayingStripKeepsTheSharedMark() { show("zap"); marker().assertExists() }
    @Test fun pausedZapKeepsOrangeProgressWithoutALogoPlate() {
        indicator.value = ChannelPlaybackIndicator.PAUSED
        show("zap")
        compose.onNodeWithTag("channel-paused-indicator", useUnmergedTree = true).assertExists()
        assertOrangeProgress()
        assertNoPlate()
    }
    @Test fun zapWithoutLogoKeepsChannelNumberFallback() {
        show("zap", picon = null)
        compose.onNodeWithText("101", useUnmergedTree = true).assertIsDisplayed()
        assertNoPlate()
    }
    @Test fun recordingRowMarksPlaybackRatherThanSelection() {
        show("row")
        marker().assertExists()
        compose.runOnIdle { target.value = null }
        marker().assertDoesNotExist()
    }
    @Test fun playingRecordingDetailsFollowPositionAndIgnoreOtherRecordings() {
        show("recording")
        marker().assertExists()
        progress(.25f)
        compose.runOnIdle { position.value = RecordingViewerPosition(recording.id, 3600000, 7200000) }
        progress(.5f)
        compose.runOnIdle { indicator.value = ChannelPlaybackIndicator.PAUSED }
        progress(.5f)
        compose.onNodeWithTag("channel-paused-indicator", useUnmergedTree = true).assertExists()
        assertOrangeProgress()
        compose.runOnIdle { position.value = RecordingViewerPosition(recording.id, 1800000, 7200000) }
        progress(.25f)
        assertOrangeProgress()
        compose.runOnIdle { position.value = RecordingViewerPosition(DvrEntryId(99), 5400000, 7200000) }
        compose.onNodeWithTag("details-progress", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test @Category(VisualCapture::class) fun guidePlayingCapture() = captureSurface("guide")
    @Test @Category(VisualCapture::class) fun detailsUnknownPositionCapture() {
        livePosition.value = LiveViewerPosition(channel.id, null)
        indicator.value = ChannelPlaybackIndicator.PAUSED
        show("programme")
        save("details-unknown-position")
    }
    @Test @Category(VisualCapture::class) fun pausedCompactCardCapture() = pausedChannelCapture("card")
    @Test @Category(VisualCapture::class) fun pausedChannelRowCapture() = pausedChannelCapture("channel-row")
    @Test @Category(VisualCapture::class) fun pausedChannelsHeadlineCapture() = pausedChannelCapture("channels-headline")
    @Test @Category(VisualCapture::class) fun rewoundPreviousProgrammeCapture() {
        livePosition.value = LiveViewerPosition(channel.id, start.epochSeconds - 1800)
        indicator.value = ChannelPlaybackIndicator.PAUSED
        show("zap")
        save("zap-previous-programme")
    }
    private fun pausedChannelCapture(surface: String) {
        livePosition.value = LiveViewerPosition(channel.id, start.epochSeconds + 600)
        indicator.value = ChannelPlaybackIndicator.PAUSED
        show(surface)
        save("$surface-paused")
    }
    @Test @Category(VisualCapture::class) fun programmePlayingCapture() = captureSurface("programme", "details-watch")
    @Test @Category(VisualCapture::class) fun menuPlayingCapture() = captureSurface("menu")
    @Test @Category(VisualCapture::class) fun zapPlayingCapture() = captureSurface("zap")
    @Test @Category(VisualCapture::class) fun recordingRowPlayingCapture() = captureSurface("row", "recording-list-entry-1")
    @Test @Category(VisualCapture::class) fun recordingDetailsPlayingCapture() = captureSurface("recording", "details-resume")
    @Test @Category(VisualCapture::class) fun pausedZapCapture() {
        indicator.value = ChannelPlaybackIndicator.PAUSED
        show("zap")
        save("zap-paused")
    }
    @Test @Category(VisualCapture::class) fun pausedRecordingCapture() {
        indicator.value = ChannelPlaybackIndicator.PAUSED
        show("recording")
        save("recording-paused")
    }
    @Test @Category(VisualCapture::class) fun zapNumberFallbackCapture() {
        show("zap", picon = null)
        save("zap-number-fallback")
    }

    private fun marker() = compose.onNodeWithTag("channel-playing-indicator", useUnmergedTree = true)
    private fun progress(value: Float) = compose.onNodeWithTag("details-progress", useUnmergedTree = true)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(value, 0f..1f)))
    private fun progressBar() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        useUnmergedTree = true)
    private fun assertNoPlate() {
        val bar = progressBar().fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        // Inside the logo slot's horizontal padding, above the strip, not the rounded corner.
        assertEquals(backgroundColor, bitmap.getPixel(bar.left.toInt() + 2, bar.top.toInt() - 15))
        bitmap.recycle()
    }
    private fun assertOrangeProgress() {
        assertProgressColor(viewerColor)
    }
    private fun assertProgressColor(expected: Int, dimmed: Boolean = false) {
        val node = progressBar().fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val fraction = node.config[SemanticsProperties.ProgressBarRangeInfo].current
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        // Rounded artwork/strip corners can antialias an individual pixel in a short fill.
        val expectedHsv = FloatArray(3).also { android.graphics.Color.colorToHSV(expected, it) }
        assertTrue("Expected progress color $expected", (bounds.left.toInt() until
            (bounds.left + bounds.width * fraction).toInt()).any {
            val actual = bitmap.getPixel(it, bounds.center.y.toInt())
            // The TV CompactCard dims its description slot; compare hue, not opaque RGB there.
            if (dimmed) {
                val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(actual, it) }
                kotlin.math.abs(hsv[0] - expectedHsv[0]) < 5f && hsv[1] > .4f
            } else actual == expected
        })
        bitmap.recycle()
    }

    private fun show(surface: String, picon: ArtworkId? = channel.icon) {
        if (surface == "recording" || surface == "row") target.value = AppPlaybackTarget.Recording(recording.id)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f),
                LocalWatchingTarget provides target.value,
                LocalWatchingChannel provides (target.value as? AppPlaybackTarget.Live)?.channelId,
                LocalWatchingIndicator provides indicator.value, LocalRecordingPosition provides position,
                LocalLivePosition provides livePosition) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    viewerColor = MaterialTheme.colorScheme.tertiary.toArgb()
                    ambientColor = MaterialTheme.colorScheme.primary.toArgb()
                    backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                    ProvideArtworkSession(observation.currentSession) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            when (surface) {
                                "mixed" -> ProgrammeDetailsPage(event, recording, channel, now,
                                    listOf(BrowseContextAction.WATCH, BrowseContextAction.PLAY), {}, {})
                                "guide" -> GuideHeadline({ GuideHeadlineProgramme(channel, event) }, observation, null, false,
                                    { now }, Modifier.padding(48.dp))
                                "programme", "recording" -> ProgrammeDetailsPage(event.takeIf { surface == "programme" },
                                    recording.takeIf { surface == "recording" }, channel, now,
                                    listOf(if (surface == "recording") BrowseContextAction.RESUME else BrowseContextAction.WATCH,
                                        BrowseContextAction.SHOW_GUIDE), {}, {})
                                "menu" -> ProgrammeContextMenu(event, channel, null, true, false, now, false,
                                    observation.currentSession, observation, false, { _, _ -> }, {})
                                "zap", "zap-no-history" -> NowPlayingStrip("101", channel.name.orEmpty(), picon, event, now,
                                    Modifier.padding(48.dp), indicator.value,
                                    programmeAt = { at -> previousEvent.takeIf {
                                        surface != "zap-no-history" && at >= it.start.epochSeconds && at < it.stop.epochSeconds } })
                                "card" -> CompactZapCard(channel, 101, event, now, indicator.value, false, {},
                                    Modifier.padding(48.dp).width(196.dp))
                                "channel-row" -> ChannelRow(number = 101, name = channel.name.orEmpty(), programTitle = event.title.orEmpty(),
                                    programme = event, nowSec = now, piconPath = picon, playbackIndicator = indicator.value,
                                    onFocus = {}, onConfirm = {}, modifier = Modifier.padding(48.dp).width(396.dp))
                                "channels-headline" -> at.bernhardberger.tvhplayer.ui.screens.ChannelsHeadline(channel, event, null,
                                    false, false, now, 3, false, Modifier.padding(48.dp).width(600.dp))
                                "row" -> Column(Modifier.padding(48.dp).width(480.dp)) {
                                    RecordingListRow(recording, channel.icon, 60.seconds, false, onClick = {})
                                    RecordingListRow(DvrEntry.create(DvrEntryId(2), title = "Other recording"), null, null, true, onClick = {})
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun captureSurface(surface: String, focusTag: String? = null) {
        show(surface)
        if (focusTag != null) compose.onNodeWithTag(focusTag).performSemanticsAction(SemanticsActions.RequestFocus)
        save("$surface-${if (surface == "guide" || surface == "zap") "passive" else "focused"}")
        if (focusTag != null) {
            compose.onNodeWithTag(focusTag).performKeyInput { pressKey(if (surface == "row") Key.DirectionDown else Key.DirectionRight) }
            compose.onNodeWithTag(focusTag).assertIsNotFocused()
            save("$surface-unfocused")
        }
    }

    private fun save(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("build/outputs/now-playing").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
