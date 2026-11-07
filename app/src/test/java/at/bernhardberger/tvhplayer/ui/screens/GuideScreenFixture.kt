package at.bernhardberger.tvhplayer.ui.screens


import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import at.bernhardberger.tvhplayer.ui.components.StatusIconSize
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagPreferences
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.GuideRows
import at.bernhardberger.tvhplayer.settings.GuideView
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.GuidePosition
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.rememberPreparedBrowseData
import at.bernhardberger.tvhplayer.ui.components.awaitBrowsePreparation
import at.bernhardberger.tvhplayer.ui.components.awaitBrowsePreparationLane
import at.bernhardberger.tvhplayer.ui.components.browsePreparationLane
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabelRes
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineTimeRuler
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadlineSettleMs
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadlineRulerGap
import at.bernhardberger.tvhplayer.ui.screens.guide.guideHeadlineHeight
import at.bernhardberger.tvhplayer.ui.screens.guide.guideFocusWindowStart
import at.bernhardberger.tvhplayer.ui.screens.guide.guideTimePositionPx
import at.bernhardberger.tvhplayer.ui.screens.guide.guideVisibleWindowSec
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineChannelRow
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineChannelHeader
import at.bernhardberger.tvhplayer.ui.screens.guide.guideRowTitleBottom
import at.bernhardberger.tvhplayer.ui.screens.guide.guideTimelineRowHeight
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession

/**
 * Offline native production Guide composition shared by [GuideVisualLayoutTest] and
 * [GuideNavigationTest]: the fake session, player, rules and the [show] scene.
 */
abstract class GuideScreenFixture {
    @get:Rule val images = ImageLoaderRule()
    // v2 keeps effects on the UI scheduler after Guide's Dispatchers.Default work completes.
    @get:Rule val compose = createComposeRule()
    val models = ViewModelStore()
    val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val position = GuidePositionStore()
    lateinit var player: ExoPlayer
    lateinit var loader: ImageLoader
    lateinit var view: View
    lateinit var session: FakeTvheadendSession
    lateinit var model: ChannelsViewModel
    lateinit var uiSettings: UiSettingsStore
    lateinit var oldZone: TimeZone
    lateinit var oldLocale: Locale
    var lineColor = 0
    var backgroundColor = 0
    /** [guideHeadlineHeight] at the shown font scale, in px, which the test's density makes dp. */
    var headlineHeight = 0f
    /** The shown rows' [guideTimelineRowHeight] and [guideRowTitleBottom], in dp. */
    var rowHeight = 0f
    var rowTitleBottom = 0f
    var rulerHeight = 0f
    var previousRulerHeight = 0f
    var callsAtEntry = 0
    lateinit var readyObservation: SessionObservation
    var playbackRequests = 0
    var navigationRequests = 0
    var guideActive by mutableStateOf(true)
    var browseReturn by mutableStateOf(false)

    val hour = Instant.parse("2026-10-04T20:00:00Z").epochSeconds
    val now = hour + 15 * 60

    // Geometry fixtures enter their saved edge through the unchanged out-of-group player return.
    fun savedTimelineReturn() = PlayerReturnFocus(ChannelId(99), ChannelId(1))

    @Before fun prepare() {
        oldZone = TimeZone.getDefault()
        oldLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        assertTrue(SystemClock.setCurrentTimeMillis(now * 1000))
        assertEquals(now * 1000, SystemClock.uptimeMillis())
        loader = ImageLoader.Builder(context()).diskCache(null)
            .components {
                add(Interceptor { chain ->
                    assertTrue(chain.request.data is AppArtworkSource)
                    SuccessResult(Bitmap.createBitmap(88, 20, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(PICON_COLOR)
                    }.asImage(), chain.request, DataSource.MEMORY)
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .build()
        images.install(loader)
    }

    @After fun release() {
        models.clear()
        runtimeScope.cancel()
        if (::player.isInitialized) player.release()
        if (::loader.isInitialized) loader.shutdown()
        TimeZone.setDefault(oldZone)
        Locale.setDefault(oldLocale)
    }

    fun publishEvents(events: List<EpgEvent>, snapshot: EpgSnapshot, covered: Boolean) {
        compose.runOnIdle {
            val observation = session.observation.value
            session.publish(SessionObservation.create(
                sessionState = observation.sessionState,
                channelState = observation.channelState,
                epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                    events = events, historicalEvents = snapshot.historicalEvents,
                    coverages = if (covered) snapshot.coverages else emptyList(),
                )),
                dvrState = observation.dvrState,
                dvrConfigurationsState = observation.dvrConfigurationsState,
            ))
        }
    }

    fun show(scale: Float = 1f, direction: LayoutDirection = LayoutDirection.Ltr, history: Boolean = true,
        lastOffset: Int = 5, eventHour: Long = hour, historyOffset: Int = -1, longProgramme: Boolean = false,
        longProgrammeHours: Int = 5, longProgrammeSuccessor: Boolean = false, initialCoverage: Boolean = true,
        beforeCoverageBatch: (suspend (List<ChannelId>, Instant) -> Unit)? = null, headline: Boolean = false,
        rows: GuideRows = GuideRows.REGULAR,
        groupWithoutFirst: Boolean = false, genres: Boolean = false, tags: Boolean = true,
        recordings: List<DvrEntry> = emptyList(), shortSecondMinutes: Int? = null, connecting: Boolean = false,
        entryCoverage: Boolean = true, tagName: String = "Favourites", activeTag: Boolean = false,
        initialGenre: ProgrammeCategory = ProgrammeCategory.ALL, subtitle: String? = null,
        playingChannel: ChannelId? = null, activeGroup: ChannelTagId? = null, playerReturn: PlayerReturnFocus? = null,
        lastPlayedChannel: ChannelId? = null, rating: EpgRating? = null, events: List<EpgEvent>? = null,
        channelNames: List<String>? = null, uiPreferences: DataStore<Preferences> = preferences(),
        preparationBlocker: @Composable () -> Unit = {}) {
        val channels = channelNames?.mapIndexed { index, name ->
            Channel.create(ChannelId(index + 1L), name = name, number = index + 1L, icon = ChannelIcon.ImageCache(ArtworkId(index + 1)),
                tagIds = listOf(ChannelTagId(1)))
        } ?: (1L..12L).map { id ->
            Channel.create(ChannelId(id), name = if (id == 2L) "Kultur und Dokumentationen aus aller Welt HD" else
                listOf("Ridge Earth HD", "Harbor Sport HD", "Northline News", "Kite Kids", "Lantern Hour")[(id.toInt() - 1) % 5],
                number = id, icon = if (id == 3L) null else ChannelIcon.ImageCache(ArtworkId(id.toInt())),
                tagIds = if (!tags) emptyList() else if (groupWithoutFirst && id > 1L) listOf(ChannelTagId(1), ChannelTagId(2)) else listOf(ChannelTagId(1)))
        }
        fun event(channel: Channel, offset: Int) = EpgEvent.create(
            EventId(channel.id.value * 100 + offset + 10), channelId = channel.id,
            start = Instant.fromEpochSeconds(eventHour + offset * 3600),
            stop = if (shortSecondMinutes != null && channel.id == ChannelId(1) && offset == 1) {
                Instant.fromEpochSeconds(eventHour + 3600 + shortSecondMinutes * 60L)
            } else Instant.fromEpochSeconds(eventHour + (offset + if (longProgramme && channel.id == ChannelId(1) && offset == 0) longProgrammeHours else 1) * 3600),
            title = title(channel.id.value.toInt(), offset), summary = "Synthetic offline programme metadata.",
            subtitle = subtitle.takeIf { channel.id == ChannelId(1) && offset == 0 },
            rating = rating.takeIf { channel.id == ChannelId(1) && offset == 0 },
            // Sport on the first channel, no genre code on the second, children's programmes after.
            contentGenre = if (genres) ContentGenre.fromContentType(when (channel.id.value) { 1L -> 0x40L; 2L -> null; else -> 0x50L }) else null,
        )
        readyObservation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels,
                if (!tags) emptyList() else listOf(ChannelTag.create(ChannelTagId(1), name = tagName, channelIds = channels.map { it.id })) +
                    if (groupWithoutFirst) listOf(ChannelTag.create(ChannelTagId(2), name = "News",
                        channelIds = channels.drop(1).map { it.id })) else emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                events = events ?: channels.filterNot { it.id == ChannelId(3) }.flatMap { ch ->
                    if (longProgramme && ch.id == ChannelId(1)) {
                        listOf(event(ch, 0)) + if (longProgrammeSuccessor) listOf(event(ch, longProgrammeHours)) else emptyList()
                    } else (0..lastOffset).map { event(ch, it) }
                },
                historicalEvents = if (history) channels.filterNot { it.id == ChannelId(3) }.map { event(it, historyOffset) } else emptyList(),
                coverages = if (initialCoverage) channels.map {
                    EpgCoverage.create(it.id, Instant.fromEpochSeconds(hour), Instant.fromEpochSeconds(hour + 7 * 86400))
                } else emptyList(),
            )),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(recordings)),
            dvrConfigurationsState = DvrConfigurationsState.Current.create(emptyList()),
        )
        // While connecting no channel has arrived yet.
        session = FakeTvheadendSession(if (!connecting) readyObservation else SessionObservation.create(
            sessionState = SessionState.Connecting,
            channelState = ChannelRepositoryState.Empty,
            epgState = readyObservation.epgState,
            dvrState = readyObservation.dvrState,
            dvrConfigurationsState = readyObservation.dvrConfigurationsState,
        ))
        // Observe and suspend only the released fake's request boundary; its settlements
        // and current-session checks still come from the SDK, with no production seam.
        val guideSession = if (beforeCoverageBatch == null) session else object : TvheadendSession by session {
            override val epgRepository = object : EpgRepository by session.epgRepository {
                override suspend fun acquireCoverageBatch(currentSession: CurrentSessionObservation,
                    channelIds: List<ChannelId>, through: Instant): EpgCoverageBatchResult {
                    beforeCoverageBatch(channelIds, through)
                    return session.epgRepository.acquireCoverageBatch(currentSession, channelIds, through)
                }
            }
        }
        val settings = PlayerSettingsStore(preferences())
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        if (playingChannel != null) runtimeScope.launch { profiles.run() }
        val tagSettings = ChannelTagSettingsStore(preferences())
        if (activeTag) runBlocking { tagSettings.save(ChannelTagPreferences(activeTagId = ChannelTagId(1))) }
        if (activeGroup != null) runBlocking { tagSettings.save(ChannelTagPreferences(activeTagId = activeGroup)) }
        model = ChannelsViewModel(session, tagSettings)
        models.put("channels", model)
        player = ExoPlayer.Builder(context()).build()
        val runtime = AppPlaybackRuntime(player, session, createTvheadendPlaybackCoordinator(player).also {
            if (playingChannel != null) it.launchIn(runtimeScope)
        }, settings, profiles,
            runtimeScope, TvheadendAudioOutputProvider(context()), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings(),
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
        if (playingChannel != null) {
            @OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)
            session.scriptLivePlaybackSuccess()
            runtimeScope.launch { runtime.playLive(checkNotNull(at.bernhardberger.tvhplayer.playback.currentLivePlaybackSelection(
                session.observation.value, playingChannel))) }
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                runtime.activeTarget.value == at.bernhardberger.tvhplayer.playback.AppPlaybackTarget.Live(playingChannel)
            }
        }
        val selection = ChannelSelectionStore()
        val lastPlayed = LastPlayedChannelStore(context())
        if (lastPlayedChannel != null) runBlocking { lastPlayed.setChannelId(lastPlayedChannel) }
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        uiSettings = UiSettingsStore(uiPreferences)
        // These tests pin the rows they measure; a test passing the default view writes nothing.
        if (!headline || rows != GuideView().rows) runBlocking { uiSettings.setGuideView(GuideView(rows, headline = headline)) }
        val noticeModule = module {
            single { notices }
            single { uiSettings }
            single { profiles }
            single<TvheadendSession> { session }
        }
        compose.setContent {
            preparationBlocker()
            KoinIsolatedContext(koinApplication { modules(noticeModule) }) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale), LocalLayoutDirection provides direction) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    lineColor = MaterialTheme.colorScheme.primary.toArgb()
                    backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                    headlineHeight = guideHeadlineHeight().value
                    with(LocalDensity.current) {
                        val typography = MaterialTheme.typography
                        rulerHeight = (typography.labelSmall.lineHeight.toDp() + 4.dp).value
                        previousRulerHeight = maxOf(28.dp, typography.labelSmall.lineHeight.toDp() + 12.dp).value
                    }
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SideRail(currentRoute = AppDestination.GUIDE,
                            rootRoute = AppDestination.HOME, availableDestinations = setOf(AppDestination.HOME, AppDestination.CHANNELS, AppDestination.GUIDE, AppDestination.RECORDINGS, AppDestination.SETTINGS),
                            onRootBack = { navigationRequests++ }, onNavigate = { navigationRequests++ }) { padding, drawer ->
                            val view = uiSettings.settings.collectAsState(UiSettings()).value.guideView
                            rowHeight = guideTimelineRowHeight(view.rows).value
                            rowTitleBottom = guideRowTitleBottom(view.rows).value
                            var genre by remember { mutableStateOf(initialGenre) }
                            EpgGridScreen(contentPadding = padding, initialFocusEnabled = !drawer, view = view, channelViewModel = model,
                                guideActive = guideActive,
                                browseReturn = browseReturn,
                                playerReturn = playerReturn,
                                genre = genre, onGenreChange = { genre = it },
                                session = guideSession, playerSession = runtime, selection = selection, guidePositionStore = position,
                                lastPlayedStore = lastPlayed, onPlay = { _, _ -> playbackRequests++ },
                                onPlayRecording = { playbackRequests++ }, notices = notices)
                        }
                    }
                }
            }
        }
        }
        if (!connecting) {
            if (playerReturn != null) {
                // savedTimelineReturn fixtures request header focus for geometry setup only, not return-policy evidence.
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("guide-date-button").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("guide-date-button").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            }
            dateFocused()
            assertNotNull("The fixture must exercise a current SDK capability, not cached-only rows", model.observation.value.currentSession)
            // The entry's coverage request reaches the session off the main thread; count from
            // after it, or a busy machine records it late as if navigation had asked.
            if (entryCoverage && beforeCoverageBatch == null) {
                compose.waitUntil(5_000) { FakeSessionCall.EPG_ACQUIRE_COVERAGE_BATCH in session.calls }
                val entryCalls = session.calls.toList()
                assertEquals("Entry asks for coverage once", 1, entryCalls.count { it == FakeSessionCall.EPG_ACQUIRE_COVERAGE_BATCH })
                callsAtEntry = entryCalls.size
                return
            }
        }
        callsAtEntry = session.calls.size
    }

    fun assertReadableFocus(direction: LayoutDirection = LayoutDirection.Ltr) {
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val ruler = bounds("epg-time-ruler")
        // Only the edge beside the channel column and the top edge fade; the others keep 8dp.
        val leftInset = if (direction == LayoutDirection.Ltr) 48f else 8f
        val rightInset = if (direction == LayoutDirection.Ltr) 8f else 48f
        assertTrue("Focus left $focus", focus.left >= ruler.left + leftInset)
        assertTrue("Focus right $focus", focus.right <= ruler.right - rightInset)
        assertTrue("Focus bottom $focus", focus.bottom <= bounds("epg-timeline-content").bottom - 8f)
        val first = compose.onAllNodesWithTag("epg-channel-row-1").fetchSemanticsNodes().singleOrNull()
        if (first == null || first.boundsInRoot.top < bounds("epg-timeline-content").top) {
            assertTrue("Focus top $focus", focus.top >= bounds("epg-timeline-content").top + 48f)
        }
    }

    fun assertNearBackground(pixel: Int, tolerance: Int) {
        assertTrue(kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(backgroundColor)) <= tolerance)
        assertTrue(kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(backgroundColor)) <= tolerance)
        assertTrue(kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(backgroundColor)) <= tolerance)
    }

    fun nativeFocusBounds(bitmap: Bitmap): Rect {
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val scan = Rect(focus.center.x - focus.width * .53f, focus.center.y - focus.height * .53f,
            focus.center.x + focus.width * .53f, focus.center.y + focus.height * .53f)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in scan.top.toInt().coerceAtLeast(0)..scan.bottom.toInt().coerceAtMost(bitmap.height - 1)) {
            for (x in scan.left.toInt().coerceAtLeast(0)..scan.right.toInt().coerceAtMost(bitmap.width - 1)) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) > 180 && android.graphics.Color.green(pixel) > 180 && android.graphics.Color.blue(pixel) > 180) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        assertTrue("Native focused surface must be visible", right >= left && bottom >= top)
        return Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
    }

    fun capture(name: String, scale: Float, direction: LayoutDirection,
        outputs: String = "build/outputs/browse-cohesion/guide"): Bitmap {
        assertNoPlayback()
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val density = context().resources.displayMetrics.density
        assertEquals((960 * density).toInt(), bitmap.width)
        assertEquals((540 * density).toInt(), bitmap.height)
        val directory = File(outputs)
        CaptureOutput.writePng(File(directory, "$name.png"), bitmap)
        // Built eagerly: the focus and ruler lookups also fail the test when the scene is not in the expected state.
        val note = (
            "canvas=960x540dp; density=$density; locale=${Locale.getDefault()}; direction=$direction; fontScale=$scale; timezone=UTC\n" +
                "syntheticClockStart=2026-10-04T20:15:00Z; captureClockMillis=${SystemClock.uptimeMillis()}\n" +
                "focus=${compose.onNode(isFocused()).fetchSemanticsNode().config.let { it.getOrElseNullable(SemanticsProperties.Text) { null } ?: it.getOrElseNullable(SemanticsProperties.ContentDescription) { null } }}; " +
                "bounds=${compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot}; position=${position.position.value}\n" +
                "ruler=${bounds("epg-time-ruler")}; nativeFocusPixels=${nativeFocusBounds(bitmap)}\n" +
                "styleGeometry=rulerHeightDp=$rulerHeight; previousRulerHeightDp=$previousRulerHeight; headlineHeightDp=$headlineHeight\n" +
                "headerRefinement=inline number/logo or number/name; gutter=104dp; headerInset=12dp; numberGap=8dp; cellGap=4dp; nameType=titleSmall14/20 single line; picon=max48x28dp Fit (dense rows)\n" +
                "production=TVHeadendPlayerTheme + SideRail + EpgGridScreen; native D-pad focus; fake SDK; intercepted synthetic picons\n" +
                "background=opaque theme background, not live video; static Robolectric SDK34 native graphics only\n" +
                "playbackRequests=$playbackRequests; navigationRequests=$navigationRequests; sdkCalls=${session.calls.size}\n"
            )
        CaptureOutput.writeText(File(directory, "$name.txt")) { note }
        return bitmap
    }

    fun assertNoPlayback(additionalCoverageCalls: Int = 0) {
        assertEquals(0, playbackRequests)
        assertEquals(0, navigationRequests)
        assertEquals(List(additionalCoverageCalls) { FakeSessionCall.EPG_ACQUIRE_COVERAGE_BATCH }, session.calls.drop(callsAtEntry))
        assertFalse(FakeSessionCall.BIND_LIVE_PLAYBACK in session.calls)
        assertNull(player.currentMediaItem)
        assertFalse(player.playWhenReady)
    }

    fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    // A key reaches the screen once it has prepared what it shows, as on a device where that takes milliseconds.
    fun key(key: Key) {
        compose.awaitBrowsePreparation()
        keyWhilePreparing(key)
    }
    fun keyWhilePreparing(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
    fun dateButtonText(day: String, startSec: Long) = context().getString(R.string.epg_date_button, day,
        at.bernhardberger.tvhplayer.ui.common.formatClock(
            java.time.Instant.ofEpochSecond(startSec).atZone(java.time.ZoneOffset.UTC), Locale.getDefault()))
    fun dateButtonShows(day: String, startSec: Long) =
        compose.onAllNodes(hasTestTag("guide-date-button") and hasText(dateButtonText(day, startSec))).fetchSemanticsNodes().isNotEmpty()
    // A window holding now names Today and Now instead of its start.
    fun dateButtonNowText() = context().getString(R.string.epg_date_button,
        context().getString(R.string.today), context().getString(R.string.now))
    fun assertDateButtonNow() =
        compose.onNodeWithTag("guide-date-button").assert(hasText(dateButtonNowText()))
    fun dateFocused() = tagFocused("guide-date-button")
    fun tagFocused(tag: String) = focused(hasTestTag(tag), tag)
    fun focused(text: String) = focused(hasText(text), text)
    fun focused(matcher: SemanticsMatcher, text: String) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodes(matcher and isFocused()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(matcher and isFocused()).assertIsDisplayed().assertIsFocused()
        } catch (failure: Throwable) {
            // Failure-only synthetic fixture evidence; retain the original cause and deadline.
            println("Guide focus failure: expected=$text position=${position.position.value}")
            if (::session.isInitialized) {
                println("Guide EPG=${session.observation.value.epgState.javaClass.simpleName} " +
                    "currentSession=${session.observation.value.currentSession != null} calls=${session.calls}")
            }
            println(runCatching { compose.onAllNodes(isFocused(), useUnmergedTree = true).printToString() }
                .getOrElse { "Cannot read focused nodes: ${it.javaClass.simpleName}" })
            println(runCatching { compose.onAllNodes(matcher, useUnmergedTree = true).printToString() }
                .getOrElse { "Cannot read expected nodes: ${it.javaClass.simpleName}" })
            val focusedTags = runCatching {
                compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map {
                    it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } ?: "<untagged>"
                }
            }.getOrElse { listOf("<unavailable: ${it.javaClass.simpleName}>") }
            throw AssertionError("Guide focus: expected=$text, focusedTags=$focusedTags, position=${position.position.value}", failure)
        }
    }
    fun title(channel: Int, offset: Int) = if (channel == 2 && offset == 0)
        "Wunder der Natur: Eine außergewöhnliche Reise durch die nördlichen Landschaften" else "Channel $channel • Programme $offset"
    fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    fun context(): Application = ApplicationProvider.getApplicationContext()

    companion object {
        const val PICON_COLOR = 0xff27a69a.toInt()
    }
}
