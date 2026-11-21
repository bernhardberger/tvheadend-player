package at.bernhardberger.tvhplayer.touch.player

import androidx.activity.ComponentActivity
import android.content.res.Configuration
import android.os.Looper
import android.view.WindowInsets
import androidx.annotation.OptIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointsResult
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvhplayer.di.SdkRuntimeOwner
import at.bernhardberger.tvhplayer.images.ChannelAccents
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.playback.currentRecordingPlaybackSelection
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.MainActivity
import at.bernhardberger.tvhplayer.touch.di.Application
import at.bernhardberger.tvhplayer.touch.recordings.RecordingProgress
import at.bernhardberger.tvhplayer.touch.testutil.FakeConnection
import at.bernhardberger.tvhplayer.touch.testutil.MemoryPreferences
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.testutil.captureTouchWindows
import at.bernhardberger.tvhplayer.touch.ui.TouchNotice
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.notifications.NoticeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.presentation.R as SharedR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingPlayerScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val overlapErrors = ErrorCollector()

    private val position = mutableLongStateOf(RECORDING_BEFORE_AD)
    private val hasMarks = mutableStateOf(true)
    private val markerRevision = mutableStateOf<Long?>(null)
    private val choice = mutableStateOf<AdBreakMode?>(null)
    private val dismissed = mutableStateOf(false)
    private val notice = mutableStateOf<Notice?>(null)
    private val seeks = mutableListOf<Long>()
    private val choices = mutableListOf<PlayerChoice>()

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun recordingInfoOpensAnAiringAndBackReturnsBeforeClosing() {
        show(info = recordingInfoFixture(running = false))
        button(R.string.player_info).performClick()
        val channelHead = hasText("Harbor Sport") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        val airing = compose.onNode(hasText("Sat, Nov 14 • 8:15 PM") and hasClickAction())
        airing.assertIsDisplayed()
        val inInfo = hasAnyAncestor(hasTestTag("recording-info"))
        compose.onAllNodes(inInfo and hasClickAction()).assertCountEquals(1)
        compose.onNode(inInfo and hasTestTag("recording-details-picture"), useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(inInfo and SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertDoesNotExist()
        captureWindows("tablet-recording-info", TouchCanvas.TABLET)
        airing.performClick()
        compose.runOnIdle { assertEquals(listOf(PlayerChoice.Details("kickoff-saturday")), choices) }
        compose.onNodeWithText("Kingsbridge – Northport").assertIsDisplayed()
        compose.onNode(channelHead).assertIsDisplayed()
        compose.onNodeWithTag("player-info-logo", useUnmergedTree = true).assertDoesNotExist()
        captureWindows("tablet-recording-info-airing", TouchCanvas.TABLET)
        back()
        airing.assertIsDisplayed()
        compose.onNode(channelHead).assertIsDisplayed()
        back()
        airing.assertDoesNotExist()
        button(R.string.player_info).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun uprightRecordingInfoIsInThePlayerBottomSheet() {
        show(info = recordingInfoFixture(running = true))
        button(R.string.player_info).performClick()
        tag("recording-info").assertIsDisplayed()
        button(R.string.side_sheet_close).assertDoesNotExist()
        captureWindows("phone-recording-info", TouchCanvas.PHONE)
    }

    @Test
    @Config(qualifiers = "en-rUS-w892dp-h412dp-land-xhdpi")
    fun sidewaysRecordingInfoIsInThePlayerSideSheet() {
        show(info = recordingInfoFixture(running = false))
        button(R.string.player_info).performClick()
        tag("recording-info").assertIsDisplayed()
        button(R.string.side_sheet_close).assertIsDisplayed()
        captureWindows("phone-recording-landscape-info", TouchCanvas.PHONE_LANDSCAPE)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletMarkerSeekKeepsSheetAndDragSeeksOnlyOnLift() {
        show()
        val canvas = TouchCanvas.TABLET
        capture("tablet-recording", canvas)
        button(R.string.player_markers).performClick()
        captureWindows("tablet-recording-markers", canvas)
        val mark = 1_440_000L
        tag("recording-marker-$mark").performClick()
        compose.runOnIdle { assertEquals(listOf(mark), seeks); seeks.clear() }
        tag("recording-markers").assertIsDisplayed()
        back()
        val bar = tag("player-timeline")
        bar.performTouchInput {
            down(Offset(width / 2f, center.y))
            moveTo(Offset(width * (3_432_000f / RECORDING_LENGTH), center.y))
        }
        assertAboveMarkers(tag("recording-drag-label"), "tablet drag label")
        capture("tablet-recording-drag", canvas)
        bar.performTouchInput { moveTo(Offset(width * (3_744_000f / RECORDING_LENGTH), center.y)) }
        assertAboveMarkers(tag("recording-drag-label"), "tablet snapped drag label")
        capture("tablet-recording-drag-snap", canvas)
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }
        bar.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(fixtureAd.endMs), seeks) }

        compose.runOnIdle { markerRevision.value = 1L }
        val markerCenter = compose.onNode(
            hasTestTag("recording-marker-symbol") and hasContentDescription("Final whistle and interviews") and
                hasClickAction(),
        )
            .fetchSemanticsNode().boundsInRoot.center
        val picture = tag("player-picture")
        val point = Offset(58f, 95f)
        val shown = picture.redAt(point)
        compose.runOnIdle { seeks.clear() }
        compose.mainClock.autoAdvance = false
        picture.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        val fading = picture.redAt(point)
        tag("player-top").assertDoesNotExist()
        compose.onRoot().performTouchInput { click(markerCenter) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue("A fading marker must not seek", choices.isEmpty() && seeks.isEmpty()) }
        picture.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.autoAdvance = true
        val hidden = picture.redAt(point)
        assertTrue("Recording controls fade: $shown, $fading, $hidden", fading > minOf(shown, hidden) &&
            fading < maxOf(shown, hidden))
        picture.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { hasMarks.value = false }
        button(R.string.player_markers).assertDoesNotExist()
        button(R.string.player_channels).assertDoesNotExist()
        button(R.string.player_record).assertDoesNotExist()
        assertRecordingRouteHidesSystemBars()
    }

    @OptIn(UnstableApi::class)
    @kotlin.OptIn(TvheadendTestingApi::class)
    private fun withRecordingRoute(
        marked: Boolean,
        twoAudioTracks: Boolean = false,
        holdSettings: MutableStateFlow<Boolean>? = null,
        exercise: (PlayerActivity, AppPlaybackRuntime) -> Unit,
    ) {
        val context = compose.activity.applicationContext
        val connection = FakeConnection()
        val player = ExoPlayer.Builder(context).build()
        val server = ServerGuide(Instant.parse("2026-11-11T21:00:00Z"))
        val session = if (marked) markedSession(connection, server) else connection.session
        val coordinator = createTvheadendPlaybackCoordinator(
            if (marked) offlinePlayer(player, twoAudioTracks) else player,
            workDispatcher = Dispatchers.Main.immediate,
        )
        val settings = holdSettings?.let { PlayerSettingsStore(heldSettings(it)) } ?: PlayerSettingsStore(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val runtime = AppPlaybackRuntime(
            player, session, coordinator, settings, connection.owner, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings(),
            connection.noticeCenter,
        )
        val owner = SdkRuntimeOwner.create(session, runtime, connection.owner, coordinator, player, scope)
        startKoin {
            modules(module {
                single { owner }
                single { settings }
                single { connection.owner }
                single { connection.noticeCenter }
                single { connection.browseSession }
                single { ChannelAccents(ChannelAccentStore(), connection.session.artwork::cacheKey) }
                single(Application) { scope }
                single { connection.channels() }
                viewModel { connection.presentation() }
                viewModel { connection.recordings() }
                viewModel { connection.guide() }
            })
        }
        try {
            val id = if (marked) server.film.id else DvrEntryId(1)
            val intent = RecordingRoute(id, recordingStart(marked)).intent(context)
            Robolectric.buildActivity(PlayerActivity::class.java, intent).setup().use { controller ->
                settleRoute(player.takeIf { marked })
                exercise(controller.get(), runtime)
            }
        } finally {
            owner.requestClose()
            shadowOf(Looper.getMainLooper()).idle()
            stopKoin()
            connection.close()
        }
    }

    private fun heldSettings(hold: MutableStateFlow<Boolean>): DataStore<Preferences> {
        val store = MemoryPreferences()
        return object : DataStore<Preferences> by store {
            override val data = combine(store.data, hold) { prefs, held -> prefs.takeUnless { held } }.filterNotNull()
        }
    }

    private fun MainActivity.watchRecording(id: DvrEntryId, startOver: Boolean = false) {
        ReflectionHelpers.callInstanceMethod<Unit>(
            this, "watchRecording",
            ReflectionHelpers.ClassParameter.from(String::class.java, id.value.toString()),
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, startOver),
        )
    }

    @Test
    @OptIn(UnstableApi::class)
    fun browseRecreationStillHandsOffThePendingRecording() {
        val hold = MutableStateFlow(false)
        withRecordingRoute(marked = true, holdSettings = hold) { _, runtime ->
            val replacement = ServerGuide(Instant.parse("2026-11-11T21:00:00Z")).diary.id
            Robolectric.buildActivity(MainActivity::class.java).setup().use { browse ->
                hold.value = true
                browse.get().watchRecording(replacement, startOver = true)
                settleRoute(runtime.player)
                assertTrue(runtime.activeTarget.value != AppPlaybackTarget.Recording(replacement))
                browse.recreate()
                hold.value = false
                settleRoute(runtime.player)
                assertEquals(AppPlaybackTarget.Recording(replacement), runtime.activeTarget.value)
                val intent = shadowOf(browse.get().application).nextStartedActivity
                assertEquals(replacement, intent?.recordingRoute()?.id)
            }
        }
    }

    @Test
    @OptIn(UnstableApi::class)
    fun recordingEofDoesNotStopAQueuedReplacement() {
        val hold = MutableStateFlow(false)
        withRecordingRoute(marked = true, holdSettings = hold) { activity, runtime ->
            val replacement = ServerGuide(Instant.parse("2026-11-11T21:00:00Z")).diary.id
            val session = org.koin.core.context.GlobalContext.get().get<TvheadendSession>()
            val selection = checkNotNull(currentRecordingPlaybackSelection(session.observation.value, replacement))
            hold.value = true
            activity.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                runtime.playRecording(selection, recordingStart(true))
            }
            runtime.player.seekTo(runtime.player.duration)
            settleRoute(runtime.player)
            assertEquals(AppPlaybackState.Finished, runtime.state.value)
            hold.value = false
            settleRoute(runtime.player)
            assertEquals(AppPlaybackTarget.Recording(replacement), runtime.activeTarget.value)
            assertTrue(!activity.isFinishing)
        }
    }

    @Test
    @OptIn(UnstableApi::class)
    fun browseWatchFromBeginningRestartsTheActiveRecording() = withRecordingRoute(marked = true) { _, runtime ->
        val active = runtime.activeTarget.value as AppPlaybackTarget.Recording
        runtime.pause()
        runtime.seekTo(24_000L)
        settleRoute(runtime.player)
        Robolectric.buildActivity(MainActivity::class.java).setup().use { browse ->
            browse.get().watchRecording(active.recordingId)
            settleRoute(runtime.player)
            assertEquals("Resume must not restart the active recording", 24_000L, runtime.player.currentPosition)
            browse.get().watchRecording(active.recordingId, startOver = true)
            settleRoute(runtime.player)
            assertTrue("Watch from beginning must restart playback", runtime.player.currentPosition in 0L..999L)
        }
    }

    private fun assertRecordingRouteHidesSystemBars() = withRecordingRoute(marked = false) { activity, _ ->
        val configuration = activity.resources.configuration
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, configuration.orientation)
        val insets = checkNotNull(activity.window.decorView.windowInsetsController)
        val bars = WindowInsets.Type.systemBars()
        fun requestedBars() = ReflectionHelpers.callInstanceMethod<Int>(insets, "getRequestedVisibleTypes") and bars
        assertEquals("The landscape recording route should request hidden system bars", 0, requestedBars())
        activity.onPictureInPictureModeChanged(true, configuration)
        settleRoute()
        insets.show(bars)
        activity.onPictureInPictureModeChanged(false, configuration)
        settleRoute()
        assertEquals("Expanding the recording should request hidden system bars again", 0, requestedBars())
    }

    @kotlin.OptIn(TvheadendTestingApi::class)
    private fun markedSession(connection: FakeConnection, server: ServerGuide): TvheadendSession {
        val sdk = connection.session
        sdk.publish(server.connected())
        return object : TvheadendSession by sdk {
            override fun bindRecordingPlayback(currentSession: CurrentSessionObservation, recordingId: DvrEntryId) =
                TvheadendTestResultFactory.boundCompletedRecordingPlayback(sdk, currentSession, recordingId, cutpoints = {
                    DvrCutpointsResult.Available.create(
                        listOf(DvrCutpoint(10.seconds, 20.seconds, DvrCutpointAction.COMMERCIAL_BREAK)),
                    )
                })
        }
    }

    @OptIn(UnstableApi::class)
    private fun offlinePlayer(player: ExoPlayer, twoAudioTracks: Boolean): ExoPlayer = object : ExoPlayer by player {
        // Preserve the installed recording identity; the SDK fake has no media bytes.
        private fun silence(source: MediaSource): MediaSource {
            fun track() = SilenceMediaSource.Factory().setDurationUs(60.seconds.inWholeMicroseconds)
                .createMediaSource().apply { updateMediaItem(source.mediaItem) }
            return if (twoAudioTracks) MergingMediaSource(track(), track()) else track()
        }

        override fun setMediaSource(mediaSource: MediaSource) = player.setMediaSource(silence(mediaSource))
        override fun setMediaSource(mediaSource: MediaSource, startPositionMs: Long) =
            player.setMediaSource(silence(mediaSource), startPositionMs)
        override fun setMediaSource(mediaSource: MediaSource, resetPosition: Boolean) =
            player.setMediaSource(silence(mediaSource), resetPosition)
    }

    @OptIn(UnstableApi::class)
    private fun settleRoute(player: ExoPlayer? = null) {
        shadowOf(Looper.getMainLooper()).idle()
        player?.let { shadowOf(it.playbackLooper).idle() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    @OptIn(UnstableApi::class)
    fun recordingRouteMarkerSeeksWithoutResumingOrClosingTheSheet() = withRecordingRoute(marked = true) { activity, runtime ->
        runtime.pause()
        settleRoute(runtime.player)
        val symbol = compose.onNode(hasTestTag("recording-marker-symbol") and hasClickAction())
        symbol.performTouchInput { click() }
        settleRoute(runtime.player)
        assertEquals(10_000L, runtime.player.currentPosition)
        assertTrue(!runtime.player.playWhenReady)
        runtime.player.seekTo(0)
        settleRoute(runtime.player)
        button(R.string.player_markers).performClick()
        val row = tag("recording-marker-10000")
        val staleRevision = checkNotNull(runtime.recordingMarkerRevision.value)
        row.performTouchInput { click() }
        settleRoute(runtime.player)
        assertEquals(10_000L, runtime.player.currentPosition)
        assertTrue(!runtime.player.playWhenReady)
        tag("recording-markers").assertIsDisplayed()
        val replacement = ServerGuide(Instant.parse("2026-11-11T21:00:00Z")).diary.id
        val session = org.koin.core.context.GlobalContext.get().get<TvheadendSession>()
        val selection = checkNotNull(currentRecordingPlaybackSelection(session.observation.value, replacement))
        activity.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runtime.playRecording(selection, recordingStart(true))
        }
        settleRoute(runtime.player)
        runtime.pause()
        runtime.seekTo(30_000L)
        settleRoute(runtime.player)
        assertEquals(AppPlaybackTarget.Recording(replacement), runtime.activeTarget.value)
        assertTrue(staleRevision != runtime.recordingMarkerRevision.value)
        runtime.seekRecordingMarker(10_000L, staleRevision)
        settleRoute(runtime.player)
        assertEquals("A marker action from the previous recording must not seek", 30_000L, runtime.player.currentPosition)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    @OptIn(UnstableApi::class)
    fun recordingRouteSelectsAudioThroughTheRuntime() = withRecordingRoute(
        marked = true,
        twoAudioTracks = true,
    ) { activity, runtime ->
        val second = runtime.player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.last().mediaTrackGroup
        button(R.string.player_audio).performClick()
        // The two synthetic audio tracks have identical labels; select the second listed track.
        compose.onAllNodes(hasText(activity.getString(SharedR.string.track_stereo)) and hasClickAction())
            .assertCountEquals(2)[1].performClick()
        settleRoute(runtime.player)
        assertEquals(listOf(0), runtime.player.trackSelectionParameters.overrides[second]?.trackIndices)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    @OptIn(UnstableApi::class)
    fun recordingRouteDismissAndAcceptActOnTheRuntimeOffers() = withRecordingRoute(marked = true) { _, runtime ->
        // Enter an ad as ordinary playback (not a deliberate runtime seek).
        runtime.player.seekTo(12_000L)
        settleRoute(runtime.player)
        tapPicture()
        tag("recording-ad-prompt").assertIsDisplayed()
        button(SharedR.string.server_keep_watching).performClick()
        settleRoute(runtime.player)
        assertEquals(null, runtime.recordingSkipPrompt.value)
        tag("recording-ad-prompt").assertDoesNotExist()
        tapPicture()
        assertTrue(runtime.recordingControlsSkip.value != null)
        button(SharedR.string.skip_ad_break).performClick()
        settleRoute(runtime.player)
        assertTrue(runtime.player.currentPosition >= 20_000L)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletAskOfferStaysThroughoutBreakAndDismissesOnlyBarePrompt() {
        show()
        val canvas = TouchCanvas.TABLET
        compose.runOnIdle { position.longValue = RECORDING_INSIDE_AD }
        assertAboveMarkers(button(SharedR.string.skip_ad_break), "tablet Skip ad break", clearanceDp = 8)
        capture("tablet-recording-ad", canvas)
        tapPicture()
        assertNoLandscapeTimeline()
        capture("tablet-recording-skip", canvas)
        compose.runOnIdle { position.longValue = fixtureAd.endMs - 1L }
        tag("recording-ad-prompt").assertIsDisplayed()
        button(SharedR.string.server_keep_watching).performClick()
        tag("recording-ad-prompt").assertDoesNotExist()
        tapPicture()
        button(SharedR.string.skip_ad_break).performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(RecordingSkipChoice.Dismiss(fixtureAdToken), RecordingSkipChoice.Accept(fixtureAdToken, true)),
                choices,
            )
            position.longValue = fixtureAd.endMs
        }
        button(SharedR.string.skip_ad_break).assertDoesNotExist()

        tapPicture()
        compose.runOnIdle {
            choice.value = AdBreakMode.AUTOMATIC
            notice.value = Notice.AutomaticAdBreakSkip(fixtureAd.startMs)
        }
        capture("tablet-recording-skipped", canvas)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletRecordingOptionsChooseAnExplicitOverride() {
        show()
        val canvas = TouchCanvas.TABLET
        button(R.string.player_settings).performClick()
        captureWindows("tablet-recording-options", canvas)
        compose.onNodeWithText(compose.activity.getString(R.string.player_stream_profile)).assertDoesNotExist()
        button(SharedR.string.ad_breaks_setting).performClick()
        captureWindows("tablet-recording-options-adbreaks", canvas)
        button(SharedR.string.ad_breaks_automatic).performClick()
        compose.runOnIdle { assertEquals(listOf(PlayerChoice.AdBreaks(AdBreakMode.AUTOMATIC)), choices) }
    }

    @Test
    @Config(qualifiers = "en-rUS-w892dp-h412dp-land-xhdpi")
    fun landscapePhoneKeepsMarkersInASideSheet() {
        show()
        val canvas = TouchCanvas.PHONE_LANDSCAPE
        assertAboveMarkers(tag("player-program"), "landscape phone program")
        assertLandscapeProgramIntact()
        capture("phone-recording-landscape", canvas)
        button(R.string.player_markers).performClick()
        captureWindows("phone-recording-landscape-markers", canvas)
        back()
        compose.runOnIdle { position.longValue = RECORDING_INSIDE_AD }
        assertAboveMarkers(tag("player-program"), "landscape phone program during ads")
        assertLandscapeProgramIntact()
        assertAboveMarkers(button(SharedR.string.skip_ad_break), "landscape phone Skip ad break", clearanceDp = 8)
        capture("phone-recording-landscape-ad", canvas)
        tapPicture()
        assertNoLandscapeTimeline()
        capture("phone-recording-landscape-skip", canvas)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun portraitPhoneHasInlineMarkersAndBottomSheetOptions() {
        show()
        val canvas = TouchCanvas.PHONE
        button(R.string.player_markers).assertDoesNotExist()
        tag("recording-markers").assertIsDisplayed()
        capture("phone-recording", canvas)
        compose.runOnIdle { position.longValue = RECORDING_INSIDE_AD }
        capture("phone-recording-ad", canvas)
        compose.runOnIdle { position.longValue = RECORDING_BEFORE_AD }
        button(R.string.player_settings).performClick()
        captureWindows("phone-recording-options", canvas)
    }

    private fun show(info: RecordingPlayerInfo? = null) {
        val fixture = recordingPlayerFixture(position)
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                val offer = fixtureAdOffer(position.longValue, choice.value)
                PlayerScreen(
                    fixture.copy(
                        marks = fixture.marks.takeIf { hasMarks.value }.orEmpty(),
                        markerRevision = markerRevision.value,
                        adBreakPick = choice.value,
                        controlsSkip = offer,
                        skipPrompt = offer.takeIf { !dismissed.value && choice.value != AdBreakMode.AUTOMATIC },
                        info = info,
                        endsAt = fixture.endsAt.takeIf { info == null || info.item.state is RecordingProgress.Recording },
                    ),
                    onAction = {}, onSeek = { seeks += it }, onBrightness = {},
                    onChoice = {
                        if (it is RecordingSkipChoice.Dismiss) dismissed.value = true
                        choices += it
                    },
                ) { modifier ->
                    val formatter = NoticeFormatter(LocalContext.current)
                    notice.value?.let(formatter::format)?.let { TouchNotice(it.headline, it.detail, modifier) }
                }
            }
        }
    }

    private fun tapPicture() = tag("player-picture").performTouchInput { click(Offset(width * .25f, height * .28f)) }

    private fun assertLandscapeProgramIntact() {
        val episode = compose.onNodeWithText("Westvale – Ridge Town")
        val layouts = mutableListOf<TextLayoutResult>()
        episode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val line = layouts.single()
        val visibleHeight = episode.fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "Episode line is clipped (visible=$visibleHeight px, line bottom=${line.getLineBottom(0)} px)",
            !line.didOverflowHeight && visibleHeight + .5f >= line.getLineBottom(0),
        )
        val artwork = tag("player-program-artwork").fetchSemanticsNode().boundsInRoot
        assertEquals("Thumbnail keeps its full aspect ratio", 68f / 112f, artwork.height / artwork.width, .005f)
        val program = tag("player-program").fetchSemanticsNode().boundsInRoot
        val transport = tag("player-transport").fetchSemanticsNode().boundsInRoot
        assertTrue("Program must sit below transport (gap=${program.top - transport.bottom} px)", program.top > transport.bottom)
    }

    private fun assertAboveMarkers(node: SemanticsNodeInteraction, label: String, clearanceDp: Int = 0) {
        val end = node.fetchSemanticsNode().boundsInRoot.bottom
        val start = compose.onAllNodesWithTag("recording-marker-symbol", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }.filterNot { it.isEmpty }.minOf { it.top }
        val gapDp = (start - end) / compose.activity.resources.displayMetrics.density
        if (gapDp <= 0 || gapDp < clearanceDp) {
            overlapErrors.addError(AssertionError("$label must clear marker symbols by $clearanceDp dp (gap=$gapDp dp)"))
        }
    }

    private fun assertNoLandscapeTimeline() {
        if (compose.onAllNodesWithTag("player-timeline").fetchSemanticsNodes().isNotEmpty()) {
            overlapErrors.addError(AssertionError("Hidden landscape recording controls must remove the timeline"))
        }
    }
    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    private fun tag(name: String) = compose.onNodeWithTag(name)
    private fun button(id: Int) = compose.activity.getString(id).let {
        compose.onNode(hasClickAction() and (hasText(it) or hasContentDescription(it)))
    }

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch("slice3-player", name, canvas)
    private fun captureWindows(name: String, canvas: TouchCanvas) =
        compose.captureTouchWindows("slice3-player", name, canvas)
}
