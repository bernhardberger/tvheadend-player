package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
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
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
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
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadlineSettleMs
import at.bernhardberger.tvhplayer.ui.screens.guide.guideFocusWindowStart
import at.bernhardberger.tvhplayer.ui.screens.guide.guideTimePositionPx
import at.bernhardberger.tvhplayer.ui.screens.guide.guideVisibleWindowSec
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineChannelRow
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineChannelHeader
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

/** Offline native production composition; never a replacement Guide or a playback test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi",
    instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideVisualLayoutTest {
    @get:Rule val images = ImageLoaderRule()
    // v2 keeps effects on the UI scheduler after Guide's Dispatchers.Default work completes.
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val position = GuidePositionStore()
    private lateinit var player: ExoPlayer
    private lateinit var loader: ImageLoader
    private lateinit var view: View
    private lateinit var session: FakeTvheadendSession
    private lateinit var model: ChannelsViewModel
    private lateinit var uiSettings: UiSettingsStore
    private lateinit var oldZone: TimeZone
    private lateinit var oldLocale: Locale
    private var lineColor = 0
    private var backgroundColor = 0
    private var callsAtEntry = 0
    private var playbackRequests = 0
    private var navigationRequests = 0
    private val hour = Instant.parse("2026-10-04T20:00:00Z").epochSeconds
    private val now = hour + 15 * 60

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

    @Test fun normalGeometryAndContinuousNowAboveNativeFocus() {
        show()
        val ruler = bounds("epg-time-ruler")
        assertEquals(262f, ruler.left, .1f)
        assertEquals(698f, ruler.width, .1f)
        assertEquals(72f, ruler.top, .1f)
        assertEquals(100f, ruler.bottom, .1f)
        val first = bounds("epg-channel-row-1")
        assertEquals(130f, first.left, .1f)
        compose.onNodeWithText(context().getString(R.string.all_channels)).assertDoesNotExist()
        assertEquals(108f, first.top, .1f)
        assertEquals("Dense rows by default", 56f, first.height, .1f)
        assertEquals(168f, bounds("epg-channel-row-2").top, .1f)
        assertEquals(130f, bounds("epg-channel-header-1").left, .1f)
        assertEquals(254f, bounds("epg-channel-header-1").right, .1f)
        assertEquals("Ruler heading shares the 16dp channel text inset", 146f,
            compose.onNodeWithText(context().getString(R.string.epg_channels_heading)).fetchSemanticsNode().boundsInRoot.left, .1f)
        key(Key.DirectionDown)
        focused(title(1, 0))
        val bitmap = capture("guide-normal", 1f, LayoutDirection.Ltr)
        assertChannelHeader(bitmap, 1f)
        val start = checkNotNull(position.position.value).windowStartSec
        val x = ruler.left + guideTimePositionPx(now, start, start + 3 * 3600, ruler.width)
        assertLine(bitmap, x, 162, 166, 222, 226, 282, 286, 342)
        val cell = compose.onNodeWithText(title(1, 0)).fetchSemanticsNode().boundsInRoot
        val tick = compose.onNodeWithText("20:00", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("Ruler tick and cell start align, with the label's 6dp inset", cell.left + 6f, tick.left, 1f)
        val dotY = ruler.bottom.toInt() - 4
        assertEquals("Ruler dot and the continuous line share one mapping", lineColor, bitmap.getPixel(x.toInt(), dotY))
        assertEquals("No line above the ruler", backgroundColor, bitmap.getPixel(x.toInt(), 66))
        assertReadableFocus()
        bitmap.recycle()
        key(Key.DirectionCenter)
        compose.onNodeWithTag("programme-details-page").assertIsDisplayed()
        compose.onNodeWithText(context().getString(R.string.close)).assertDoesNotExist()
        assertNoPlayback()
    }

    @Test fun middleRowRetainsCompleteNativeFocusGrowth() {
        show()
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionDown)
        focused(title(2, 0))
        val bitmap = capture("guide-middle-focus", 1f, LayoutDirection.Ltr)
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val native = nativeFocusBounds(bitmap)
        assertEquals("Middle row preserves the full native 1.05 height", focus.height * 1.05f, native.height, 2f)
        assertTrue("Native focus grows above the row instead of clipping", native.top < focus.top)
        assertTrue("Native focus grows below the row instead of clipping", native.bottom > focus.bottom)
        assertEquals("Native focus remains vertically centred", focus.center.y, native.center.y, 1f)
        val edgeX = native.right.toInt() - 1
        val edgeY = focus.center.y.toInt()
        val edgePixel = bitmap.getPixel(edgeX, edgeY)
        assertTrue("Adjacent programme must not cover the native focused edge at ($edgeX,$edgeY)",
            android.graphics.Color.red(edgePixel) > 180 && android.graphics.Color.green(edgePixel) > 180 &&
                android.graphics.Color.blue(edgePixel) > 180)
        compose.onNodeWithText("Northline News", useUnmergedTree = true).assertIsDisplayed()
        bitmap.recycle()
        assertNoPlayback()
    }

    @Test fun logoAndNoLogoHeadersKeepSingleLineContentAccessibleAsFontScaleGrows() {
        val logoName = "Kultur und Dokumentationen aus aller Welt HD"
        val noLogoName = "Northline News: International Headlines and Analysis"
        val logoChannel = Channel.create(ChannelId(1), name = logoName, number = 1001, icon = ArtworkId(1))
        val noLogoChannel = Channel.create(ChannelId(2), name = noLogoName, number = 4567)
        val currentSession = headerSession()
        val scale = mutableStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale.value)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Column {
                        Box(Modifier.height(guideTimelineRowHeight())) {
                            ProvideArtworkSession(currentSession) { TimelineChannelHeader(logoChannel, 1001) }
                        }
                        Box(Modifier.height(guideTimelineRowHeight())) {
                            TimelineChannelHeader(noLogoChannel, 4567)
                        }
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 1.3f, 1.6f)) {
            compose.runOnIdle { scale.value = fontScale }
            val rowHeight = (56f * fontScale).roundToInt().toFloat()
            for ((id, description) in listOf(1 to "1001 $logoName", 2 to "4567 $noLogoName")) {
                val header = compose.onNodeWithTag("epg-channel-header-$id")
                header.assertContentDescriptionEquals(description)
                assertFalse("Header is not a D-pad focus target", header.fetchSemanticsNode().config.contains(SemanticsProperties.Focused))
                assertEquals(124f, header.fetchSemanticsNode().boundsInRoot.width, .1f)
                assertEquals("Row grows with font scale $fontScale", rowHeight, header.fetchSemanticsNode().boundsInRoot.height, .1f)
            }
            compose.onNodeWithText(logoName, useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("epg-channel-picon-2", useUnmergedTree = true).assertDoesNotExist()
            val logo = bounds("epg-channel-picon-1")
            val logoNumber = compose.onNodeWithText("1001", useUnmergedTree = true)
            val numberLayouts = mutableListOf<TextLayoutResult>()
            logoNumber.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(numberLayouts) }
            val numberText = numberLayouts.single()
            assertEquals("1001", numberText.layoutInput.text.text)
            assertEquals(1, numberText.lineCount)
            assertFalse("Four-digit number must not truncate at $fontScale", numberText.hasVisualOverflow)
            assertFalse(numberText.isLineEllipsized(0))
            val logoNumberBounds = logoNumber.fetchSemanticsNode().boundsInRoot
            assertEquals(bounds("epg-channel-header-1").left + 16f, logoNumberBounds.left, .1f)
            assertEquals(logoNumberBounds.right + 8f, logo.left, .1f)
            assertEquals("Logo yields width to the complete channel number", minOf(48f, 92f - logoNumberBounds.width - 8f), logo.width, .1f)
            assertEquals(28f, logo.height, .1f)
            // Odd pixel row/text heights can centre an even-height logo half a pixel away.
            assertEquals(bounds("epg-channel-header-1").center.y, logo.center.y, .5f)
            val name = compose.onNodeWithText(noLogoName, useUnmergedTree = true)
            val textLayouts = mutableListOf<TextLayoutResult>()
            name.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
            val text = textLayouts.single()
            assertEquals(1, text.lineCount)
            assertTrue("Long no-logo names ellipsize on their only line", text.isLineEllipsized(0))
            val nameBounds = name.fetchSemanticsNode().boundsInRoot
            val numberNode = compose.onNodeWithText("4567", useUnmergedTree = true)
            val number = numberNode.fetchSemanticsNode().boundsInRoot
            val noLogoNumberLayouts = mutableListOf<TextLayoutResult>()
            numberNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(noLogoNumberLayouts) }
            assertFalse("No-logo four-digit number must not truncate at $fontScale", noLogoNumberLayouts.single().hasVisualOverflow)
            assertFalse(noLogoNumberLayouts.single().isLineEllipsized(0))
            assertEquals(bounds("epg-channel-header-2").left + 16f, number.left, .1f)
            assertEquals(number.right + 8f, nameBounds.left, .1f)
            assertEquals(bounds("epg-channel-header-2").right - 16f, nameBounds.right, .1f)
            assertEquals(number.center.y, nameBounds.center.y, .5f)
            assertEquals(bounds("epg-channel-header-2").center.y, nameBounds.center.y, .5f)
            assertTrue("The complete name line fits at $fontScale", text.getLineBottom(0) <= nameBounds.height)
            assertTrue(nameBounds.bottom <= bounds("epg-channel-header-2").bottom - 4f)
            if (fontScale == 1.3f) {
                compose.runOnIdle {
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val file = File("build/outputs/browse-cohesion/guide/guide-four-digit-headers-font1.3.png")
                    file.parentFile.mkdirs()
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun adjacentProgrammeSurfacesKeepFourDpGapAndSymmetricTimelineInset() {
        val channel = Channel.create(ChannelId(1), name = "Northline News", number = 1)
        val requesters = mutableMapOf<EventId, FocusRequester>()
        val events = listOf(
            EpgEvent.create(EventId(1), channelId = channel.id, title = "First programme",
                start = Instant.fromEpochSeconds(0), stop = Instant.fromEpochSeconds(3600)),
            EpgEvent.create(EventId(2), channelId = channel.id, title = "Second programme",
                start = Instant.fromEpochSeconds(3600), stop = Instant.fromEpochSeconds(7200)),
        )
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                Box(Modifier.width(732.dp)) {
                    TimelineChannelRow(channel, 0, 1, null, requesters, 0, 7200,
                        { 0 }, events, true, ConnectionUiState.Ready, false,
                        { null }, {}, {})
                }
            }
        }
        val first = compose.onNodeWithText("First programme").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Second programme").fetchSemanticsNode().boundsInRoot
        assertEquals("Time allocations still start at the timeline edge", 132f, first.left, .1f)
        assertEquals(300f, first.width, .1f)
        assertEquals(first.right, second.left, .1f)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val y = first.center.y.toInt()
            val gapColor = bitmap.getPixel(432, y)
            val cellColor = bitmap.getPixel(429, y)
            assertNotEquals("Surfaces and timeline gap must be visibly distinct", gapColor, cellColor)
            for (x in 430 until 434) assertEquals("Exactly four gap pixels at x=$x", gapColor, bitmap.getPixel(x, y))
            assertEquals(cellColor, bitmap.getPixel(434, y))
            assertEquals("First surface has the same 2dp leading inset", gapColor, bitmap.getPixel(133, y))
            assertEquals(cellColor, bitmap.getPixel(134, y))
            bitmap.recycle()
        }
    }

    @Test fun unavailableLogoShowsNameAndRetriesWhenIconOrSessionChanges() {
        val name = mutableStateOf("Northline News: International Headlines and Analysis")
        val icon = mutableStateOf(ArtworkId(1))
        val currentSession = mutableStateOf<CurrentSessionObservation?>(null)
        val loadingGate = CompletableDeferred<Unit>()
        var failArtwork = true
        var requests = 0
        loader.shutdown()
        loader = ImageLoader.Builder(context()).memoryCache(null).diskCache(null)
            .components {
                add(Interceptor { chain ->
                    assertTrue(chain.request.data is AppArtworkSource)
                    requests++
                    if (requests == 1) loadingGate.await()
                    if (failArtwork) ErrorResult(null, chain.request, IllegalStateException("Synthetic logo failure"))
                    else SuccessResult(Bitmap.createBitmap(88, 20, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(PICON_COLOR)
                    }.asImage(), chain.request, DataSource.MEMORY)
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .build()
        images.install(loader)
        compose.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.height(guideTimelineRowHeight())) {
                    ProvideArtworkSession(currentSession.value) { TimelineChannelHeader(
                        Channel.create(ChannelId(1), name = name.value, number = 1001, icon = icon.value),
                        1001,
                    ) }
                }
            }
        }
        compose.onNodeWithText(name.value, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("epg-channel-picon-1", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("No session must not request artwork", 0, requests)
        assertEquals(56f, bounds("epg-channel-header-1").height, .1f)

        compose.runOnIdle { currentSession.value = headerSession() }
        compose.onNodeWithTag("epg-channel-picon-1", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(1, requests)
        compose.onNodeWithText(name.value, useUnmergedTree = true).assertDoesNotExist()
        assertEquals("Loading preserves the dense row height", 56f, bounds("epg-channel-header-1").height, .1f)
        compose.runOnIdle { loadingGate.complete(Unit) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(name.value, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(name.value, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("epg-channel-picon-1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("epg-channel-header-1").assertContentDescriptionEquals("1001 ${name.value}")
        assertEquals("Failure preserves the dense row height", 56f, bounds("epg-channel-header-1").height, .1f)
        val fallbackNumber = compose.onNodeWithText("1001", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val fallbackName = compose.onNodeWithText(name.value, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("Failed logo uses the same inline gap", fallbackNumber.right + 8f, fallbackName.left, .1f)
        assertEquals("Failed logo keeps number and name vertically centred", fallbackNumber.center.y, fallbackName.center.y, .5f)

        compose.runOnIdle { failArtwork = false; icon.value = ArtworkId(2) }
        compose.onNodeWithTag("epg-channel-picon-1", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(2, requests)
        compose.onNodeWithText(name.value, useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { failArtwork = true; icon.value = ArtworkId(1) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(name.value, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(3, requests)
        compose.runOnIdle { failArtwork = false; currentSession.value = headerSession() }
        compose.onNodeWithTag("epg-channel-picon-1", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(4, requests)
        compose.onNodeWithText(name.value, useUnmergedTree = true).assertDoesNotExist()
        assertEquals("Session replacement preserves the dense row height", 56f, bounds("epg-channel-header-1").height, .1f)
        compose.runOnIdle { name.value = "  " }
        compose.onNodeWithTag("epg-channel-header-1").assertContentDescriptionEquals("1001")
    }

    @Test fun loadingEmptyAndErrorRowStatesFitTheDenseLane() {
        val channel = Channel.create(ChannelId(1), name = "Northline News", number = 1)
        val state = mutableStateOf<ConnectionUiState>(ConnectionUiState.Ready)
        val requesters = mutableMapOf<EventId, FocusRequester>()
        compose.setContent {
            TVHeadendPlayerTheme {
                TimelineChannelRow(channel, 0, 1, null, requesters, 0, 10800,
                    { 0 }, emptyList(), false, state.value, false,
                    { null }, {}, {})
            }
        }
        val states = listOf(
            ConnectionUiState.Ready to R.string.epg_no_data,
            ConnectionUiState.Connecting to R.string.epg_loading,
            ConnectionUiState.Error(ConnectionFailureKind.PERMISSION_DENIED, SessionRecoveryDisposition.NO_RETRY) to R.string.epg_permission_denied,
        )
        for ((connection, message) in states) {
            compose.runOnIdle { state.value = connection }
            val row = bounds("epg-channel-row-1")
            assertEquals(56f, row.height, .1f)
            val text = compose.onNodeWithText(context().getString(message))
            text.assertIsDisplayed()
            val textLayouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
            assertFalse("State message must fit the dense lane", textLayouts.single().hasVisualOverflow)
            val textBounds = text.fetchSemanticsNode().boundsInRoot
            assertTrue(textBounds.top >= row.top && textBounds.bottom <= row.bottom)
        }
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun enlargedGermanTextGrowsNativeRowsAndReducesTimeCapacity() {
        Locale.setDefault(Locale.GERMANY)
        show(scale = 1.3f)
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionDown)
        focused(title(2, 0))
        val bitmap = capture("guide-large-de", 1.3f, LayoutDirection.Ltr)
        val first = bounds("epg-channel-row-1")
        assertEquals("Native text must grow the lane", 73f, first.height, .1f)
        assertChannelHeader(bitmap, 1.3f)
        val ruler = bounds("epg-time-ruler")
        assertTrue("Header must grow rather than clip", ruler.top > 72f)
        val start = checkNotNull(position.position.value).windowStartSec
        val cell = compose.onNode(hasText(title(2, 0)) and isFocused()).fetchSemanticsNode().boundsInRoot
        assertTrue("One hour occupies about half the two-hour track", cell.width > ruler.width * .45f)
        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(formatTimeInterval(hour, hour + 3600)) and hasAnyAncestor(isFocused()), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        assertFalse("The native time line must not be clipped", textLayouts.single().hasVisualOverflow)
        val x = ruler.left + guideTimePositionPx(now, start, start + 2 * 3600, ruler.width)
        assertEquals(lineColor, bitmap.getPixel(x.toInt(), cell.center.y.toInt()))
        assertReadableFocus()
        bitmap.recycle()
    }

    @Test @Config(qualifiers = "ar-ldrtl-w960dp-h540dp-land-mdpi")
    fun rtlKeepsPhysicalLeftEarlierAndRightLaterWithAlignedRuler() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        show(direction = LayoutDirection.Rtl)
        val ruler = bounds("epg-time-ruler")
        assertEquals(0f, ruler.left, .1f)
        assertEquals(698f, ruler.right, .1f)
        key(Key.DirectionDown)
        focused(title(1, 0))
        val current = compose.onNodeWithText(title(1, 0)).fetchSemanticsNode().boundsInRoot
        val later = compose.onNodeWithText(title(1, 1)).fetchSemanticsNode().boundsInRoot
        assertTrue("RTL text must not reverse chronology: current=$current later=$later", current.right <= later.left)
        key(Key.DirectionRight)
        focused(title(1, 1))
        key(Key.DirectionLeft)
        focused(title(1, 0))
        val bitmap = capture("guide-rtl", 1f, LayoutDirection.Rtl)
        val start = checkNotNull(position.position.value).windowStartSec
        val x = guideTimePositionPx(now, start, start + 3 * 3600, ruler.width)
        assertLine(bitmap, x, 222, 226, 290, 294)
        assertEquals(lineColor, bitmap.getPixel(x.toInt(), ruler.bottom.toInt() - 4))
        assertReadableFocus()
        bitmap.recycle()
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun enlargedHeaderActionsKeepNativeFocusGrowthInsideTheSafeArea() {
        Locale.setDefault(Locale.GERMANY)
        show(scale = 1.3f)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        compose.onNodeWithTag("epg-search-open").assertDoesNotExist()
        val viewOptions = bounds("channels-view-button")
        assertEquals(902f, viewOptions.right, .1f)
        val heading = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, context().getString(R.string.epg_title))).fetchSemanticsNode().boundsInRoot
        assertEquals("Actions stay centred on the header row", heading.center.y, viewOptions.center.y, .1f)
        assertEquals("The date button shares the header row", heading.center.y, bounds("guide-date-button").center.y, .1f)
        val bitmap = capture("guide-header-focus-large-de", 1.3f, LayoutDirection.Ltr)
        assertTrue("Native focused button clears the 48dp physical safe area", nativeFocusBounds(bitmap).right <= 912f)
        bitmap.recycle()
    }

    @Test fun hiddenSearchEntryKeepsHeaderNavigationAndRestorationOnVisibleActions() {
        show()
        compose.onNodeWithTag("epg-search-open").assertDoesNotExist()
        compose.onNodeWithText(context().getString(R.string.epg_search)).assertDoesNotExist()
        compose.onNodeWithText(context().getString(R.string.now)).assertDoesNotExist()
        val date = compose.onNode(isFocused()).fetchSemanticsNode().id
        key(Key.DirectionRight)
        tagFocused("channels-group-button")
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.DirectionDown)
        focused(title(1, 0))
        // Up from the top programme row lands on the date button, not on the last header action.
        key(Key.DirectionUp)
        dateFocused()
        assertEquals(date, compose.onNode(isFocused()).fetchSemanticsNode().id)
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        key(Key.DirectionRight)
        focused(title(1, 0))
        assertNoPlayback()
    }

    @Test fun horizontalAndVerticalEdgeFocusSettlesOutsideLocalFades() {
        show()
        key(Key.DirectionDown)
        focused(title(1, 0))
        repeat(2) { key(Key.DirectionRight) }
        focused(title(1, 2))
        repeat(7) { key(Key.DirectionDown) }
        focused(title(9, 2)) // The metadata-only third channel is not an invented programme.
        assertReadableFocus()
        val bitmap = capture("guide-scrolled", 1f, LayoutDirection.Ltr)
        val viewport = bounds("epg-timeline-content")
        // Alpha reaches the backdrop, including at the top after real rows have scrolled out.
        assertNearBackground(bitmap.getPixel(750, viewport.top.toInt()), 8)
        assertNearBackground(bitmap.getPixel(750, 539), 8)
        assertNearBackground(bitmap.getPixel(959, 300), 8)
        bitmap.recycle()
        val window = checkNotNull(position.position.value).windowStartSec
        key(Key.Back)
        focused(title(9, 0)) // Scrolled past now: Back returns to now on the same channel first.
        assertNotEquals(window, checkNotNull(position.position.value).windowStartSec)
        key(Key.Back)
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        assertNoPlayback(additionalCoverageCalls = 1)
    }

    @Test fun absentHistoryDoesNotPaintAnEarlierContinuationAndLeftOpensTheRail() {
        show(history = false, lastOffset = 0)
        key(Key.DirectionDown)
        focused(title(1, 0))
        val before = position.position.value
        // D32: with no earlier programme, Left leaves the grid for the rail instead of doing nothing.
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        assertEquals(before, position.position.value)
        // Right from the rail returns to the remembered programme, as it does in Channels.
        key(Key.DirectionRight)
        focused(title(1, 0))
        assertEquals(before, position.position.value)
        val bitmap = capture("guide-data-edge", 1f, LayoutDirection.Ltr)
        val focus = compose.onNode(hasText(title(1, 0)) and isFocused()).fetchSemanticsNode().boundsInRoot
        assertTrue("Known first programme is not hidden under a speculative history fade", focus.left < bounds("epg-time-ruler").left + 48f)
        val nearLeft = bitmap.getPixel(focus.left.toInt() + 12, focus.bottom.toInt() - 10)
        assertTrue(android.graphics.Color.red(nearLeft) > 180)
        val ruler = bounds("epg-time-ruler")
        val start = checkNotNull(position.position.value).windowStartSec
        val x = ruler.left + guideTimePositionPx(now, start, start + 3 * 3600, ruler.width)
        assertEquals("Terminal padding uses the same Now mapping as cells", lineColor, bitmap.getPixel(x.toInt(), focus.center.y.toInt()))
        assertEquals("Terminal padding uses the same Now mapping as the ruler", lineColor, bitmap.getPixel(x.toInt(), ruler.bottom.toInt() - 4))
        assertNoPlayback()
        bitmap.recycle()
    }

    @Test fun leftAndRightBackToTheAnchoredProgrammeStartANewAnchor() {
        // Channel 1 runs one programme from 20:00 to 22:00; channel 2 is hourly.
        show(longProgramme = true, longProgrammeHours = 2, longProgrammeSuccessor = true)
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionDown)
        focused(title(2, 0))
        key(Key.DirectionRight)
        focused(title(2, 1))
        // Up keeps 21:00 as the anchor inside the long programme.
        key(Key.DirectionUp)
        focused(title(1, 0))
        key(Key.DirectionRight)
        focused(title(1, 2))
        key(Key.DirectionLeft)
        focused(title(1, 0))
        // Left made a new focus, so Down starts from the programme's visible start, not the old 21:00.
        key(Key.DirectionDown)
        focused(title(2, 0))
        assertNoPlayback()
    }

    @Test fun aTripToTheRailKeepsTheAnchorOfTheProgrammeItReturnsTo() {
        // Channel 1 runs one programme from 20:00 to 22:00 at the earliest edge; channel 2 is hourly.
        show(history = false, longProgramme = true, longProgrammeHours = 2, longProgrammeSuccessor = true)
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionDown)
        focused(title(2, 0))
        key(Key.DirectionRight)
        focused(title(2, 1))
        key(Key.DirectionUp)
        focused(title(1, 0))
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        key(Key.DirectionRight)
        focused(title(1, 0))
        // The rail did not move the Guide's time, so Down still undoes Up.
        key(Key.DirectionDown)
        focused(title(2, 1))
        assertNoPlayback()
    }

    @Test fun leftCrossesAnEpgGapWiderThanTheEarlierWindowToTheRowsPreviousProgramme() {
        // Five empty hours exceed the three-hour window Left would otherwise search.
        show(historyOffset = -6)
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionLeft)
        focused(title(1, -6))
        val start = checkNotNull(position.position.value).windowStartSec
        assertTrue("The window moves back across the gap: $start", start < hour - 5 * 3600)
        assertEquals("Earlier navigation stays local", callsAtEntry, session.calls.size)
        key(Key.DirectionRight)
        focused(title(1, 0))
        // Back over the gap, the window reaches now and acquires coverage like any later window.
        assertNoPlayback(additionalCoverageCalls = 1)
    }

    @Test fun focusGeometryKeepsNativeGrowthClearWithoutChangingChronologyOrBounds() {
        assertEquals(10800L, guideVisibleWindowSec(650f, 32, 1f))
        assertEquals(7200L, guideVisibleWindowSec(650f, 43, 1f))
        val start = guideFocusWindowStart(7200, 10800, 0, 10800, 650f, 56f, 56f, -21600, 604800)
        assertTrue(guideTimePositionPx(10800, start, start + 10800, 650f) <= 594f)
        assertTrue(guideTimePositionPx(7200, start, start + 10800, 650f) >= 56f)
        assertEquals(-21600L, guideFocusWindowStart(-21600, -18000, -21600, 10800, 650f, 8f, 56f, -21600, 604800))
        assertEquals(0L, guideFocusWindowStart(-3600, 14400, 0, 10800, 650f, 56f, 56f, -21600, 604800))
        val a = guideTimePositionPx(180, 0, 10800, 650f)
        val b = guideTimePositionPx(420, 0, 10800, 650f)
        assertEquals(10.833333f, a, .001f)
        assertEquals(25.277778f, b, .001f)
    }

    @Test fun shortCellsAndShellCullingRetainPhysicalTimeGeometryInBothDirections() {
        val channel = Channel.create(ChannelId(1), name = "Channel", number = 1)
        val events = listOf(0L to 300L, 300L to 600L, 7200L to 10800L).mapIndexed { index, (start, stop) ->
            EpgEvent.create(EventId(index.toLong() + 1), channelId = channel.id,
                start = Instant.fromEpochSeconds(start), stop = Instant.fromEpochSeconds(stop), title = "Short $index")
        }
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val visibleWidth = mutableStateOf(784)
        val selected = mutableStateOf<EventId?>(null)
        val requesters = mutableMapOf<EventId, FocusRequester>()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                TVHeadendPlayerTheme {
                    Box(Modifier.width(784.dp)) {
                        TimelineChannelRow(channel, 0, 1, selected.value, requesters, 0, 10800,
                            { 0 }, events, true, ConnectionUiState.Ready, false,
                            { null }, {}, {}, visibleRowWidthPx = visibleWidth.value)
                    }
                }
            }
        }
        val first = compose.onNodeWithText("Short 0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Short 1").fetchSemanticsNode().boundsInRoot
        assertEquals(132f, first.left, .1f)
        assertEquals(652f / 36, first.width, 1f)
        assertTrue(first.right <= second.left)
        compose.runOnIdle { visibleWidth.value = 484 }
        compose.onNodeWithText("Short 2").assertDoesNotExist()
        compose.runOnIdle { selected.value = EventId(3) }
        compose.onNodeWithText("Short 2").assertExists()
        compose.runOnIdle { direction.value = LayoutDirection.Rtl; selected.value = null }
        compose.onNodeWithText("Short 0").assertDoesNotExist()
        compose.onNodeWithText("Short 1").assertDoesNotExist()
        compose.onNodeWithText("Short 2").assertIsDisplayed()
        compose.runOnIdle { visibleWidth.value = 784 }
        val rtlFirst = compose.onNodeWithText("Short 0").fetchSemanticsNode().boundsInRoot
        val rtlSecond = compose.onNodeWithText("Short 1").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, rtlFirst.left, .1f)
        assertEquals(first.width, rtlFirst.width, .1f)
        assertTrue(rtlFirst.right <= rtlSecond.left)
    }

    @Test fun enlargedViewportKeepsTheExistingSevenDayEndReachable() = terminalHorizon(scale = 1.3f, hours = 2, name = "guide-horizon")

    @Test fun normalViewportKeepsNativeHorizonFocusInsidePhysicalSafeArea() = terminalHorizon(scale = 1f, hours = 3, name = "guide-horizon-normal")

    private fun terminalHorizon(scale: Float, hours: Int, name: String) {
        val finalHour = hour + 7 * 86400 - 3600
        val start = finalHour - (hours - 1) * 3600
        position.save(GuidePosition(ChannelId(1), EventId(110), finalHour, start, 0))
        show(scale = scale, history = false, lastOffset = 0, eventHour = finalHour)
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertEquals(start, checkNotNull(position.position.value).windowStartSec)
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val ruler = bounds("epg-time-ruler")
        assertEquals("A terminal hour keeps its complete time allocation", ruler.width / hours, focus.width, 1f)
        assertEquals("The seven-day endpoint remains aligned with the ruler", ruler.right, focus.right, 1f)
        assertEquals(focus.left + 6f, compose.onNodeWithText("19:00", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left, 1f)
        val bitmap = capture(name, scale, LayoutDirection.Ltr)
        assertTerminalNativeFocus(bitmap, LayoutDirection.Ltr)
        bitmap.recycle()
        key(Key.DirectionRight)
        focused(title(1, 0))
        assertEquals(start, checkNotNull(position.position.value).windowStartSec)
        assertNoPlayback()
    }

    @Test @Config(qualifiers = "ar-ldrtl-w960dp-h540dp-land-mdpi")
    fun rtlHistoryEdgeKeepsNativeFocusInsidePhysicalSafeArea() = terminalRtlHistory(scale = 1f, hours = 3, name = "guide-history-rtl")

    @Test @Config(qualifiers = "ar-ldrtl-w960dp-h540dp-land-mdpi")
    fun enlargedRtlHistoryEdgeKeepsNativeFocusInsidePhysicalSafeArea() = terminalRtlHistory(scale = 1.3f, hours = 2, name = "guide-history-rtl-large")

    private fun terminalRtlHistory(scale: Float, hours: Int, name: String) {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        val firstHour = hour - 6 * 3600
        position.save(GuidePosition(ChannelId(1), EventId(110), firstHour, firstHour, 0))
        show(scale = scale, direction = LayoutDirection.Rtl, history = false, eventHour = firstHour)
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertEquals(firstHour, checkNotNull(position.position.value).windowStartSec)
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val ruler = bounds("epg-time-ruler")
        assertEquals("RTL terminal history retains a complete hour", ruler.width / hours, focus.width, 1f)
        assertEquals("RTL history begins at the padded physical left", ruler.left, focus.left, 1f)
        assertEquals("RTL future stays adjacent to the gutter", 698f, ruler.right, .1f)
        val bitmap = capture(name, scale, LayoutDirection.Rtl)
        assertTerminalNativeFocus(bitmap, LayoutDirection.Rtl)
        bitmap.recycle()
        key(Key.DirectionLeft)
        focused(title(1, 0))
        assertEquals(firstHour, checkNotNull(position.position.value).windowStartSec)
        key(Key.DirectionRight)
        focused(title(1, 1))
        assertNoPlayback()
    }

    @Test fun oversizedProgrammeKeepsNativeFocusInsideTheUnfadedViewport() {
        show(longProgramme = true)
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertReadableFocus()
        val bitmap = capture("guide-long-programme", 1f, LayoutDirection.Ltr)
        val ruler = bounds("epg-time-ruler")
        val y = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot.center.y.toInt()
        val nativeFocusPixels = (ruler.left.toInt() until ruler.right.toInt()).filter {
            android.graphics.Color.red(bitmap.getPixel(it, y)) > 180
        }
        assertTrue("Native scaled leading edge stays clear of the fade", nativeFocusPixels.first() >= ruler.left + 48)
        assertTrue("Native scaled trailing edge stays clear of the fade", nativeFocusPixels.last() < ruler.right - 48)
        bitmap.recycle()
    }

    @Test fun removedHistoryDoesNotStealHeaderFocus() {
        selectHistory()
        key(Key.DirectionUp)
        dateFocused()
        removeHistory()
        dateFocused()
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertNoPlayback()
    }

    @Test fun removedHistoryDoesNotStealDrawerFocus() {
        selectHistory()
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        removeHistory()
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        key(Key.DirectionRight)
        focused(title(1, 0))
        assertNoPlayback()
    }

    @Test fun removedHistoryWithProgrammeOwnershipFallsBackToHeader() {
        selectHistory()
        removeHistory()
        compose.onNode(isFocused()).assert(hasAnyAncestor(hasTestTag("epg-screen")))
        compose.onNode(isFocused()).assert(hasAnyAncestor(hasTestTag("epg-programme-viewport")).not())
        dateFocused()
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertNoPlayback()
    }

    @Test fun rightLeavesSevenHourProgrammeForSuccessor() = longProgrammeNavigation(scale = 1f, hours = 7, reverse = false)

    @Test fun leftLeavesSevenHourProgrammeForPredecessor() = longProgrammeNavigation(scale = 1f, hours = 7, reverse = true)

    @Test fun enlargedRightLeavesFiveHourProgrammeForSuccessor() = longProgrammeNavigation(scale = 1.3f, hours = 5, reverse = false)

    @Test fun enlargedLeftLeavesFiveHourProgrammeForPredecessor() = longProgrammeNavigation(scale = 1.3f, hours = 5, reverse = true)

    @Test fun enlargedSuccessorSurvivesEqualPublicationBeforeFrontierShift() =
        pendingSuccessorNavigation(scale = 1.3f, hours = 5, publication = SuccessorPublication.BeforeShift)

    @Test fun enlargedSuccessorSurvivesEqualPublicationAtFrontierCancellation() =
        pendingSuccessorNavigation(scale = 1.3f, hours = 5, publication = SuccessorPublication.AtCancellation)

    @Test fun normalSuccessorSurvivesEqualPublicationAtFrontierCancellation() =
        pendingSuccessorNavigation(scale = 1f, hours = 7, publication = SuccessorPublication.AtCancellation)

    @Test fun enlargedSuccessorSurvivesEqualPublicationAfterFocusSafeShift() =
        pendingSuccessorNavigation(scale = 1.3f, hours = 5, publication = SuccessorPublication.AfterFocus)

    private enum class SuccessorPublication { BeforeShift, AtCancellation, AfterFocus }

    private fun pendingSuccessorNavigation(scale: Float, hours: Int, publication: SuccessorPublication) {
        val armed = AtomicBoolean(false)
        val entered = AtomicReference<Pair<List<ChannelId>, Instant>>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val autoAdvance = compose.mainClock.autoAdvance
        var rightDown = false
        try {
            show(scale = scale, longProgramme = true, longProgrammeHours = hours, longProgrammeSuccessor = true,
                beforeCoverageBatch = { ids, through ->
                    if (armed.get()) {
                        entered.set(ids to through)
                        try {
                            release.await()
                        } finally {
                            if (!currentCoroutineContext().isActive) {
                                // A cached frontier cancels its still-pending acquisition after
                                // selecting W1. Publish here without suspending cancellation.
                                if (publication == SuccessorPublication.AtCancellation) publishEqualEpg()
                                cancelled.complete(Unit)
                            }
                        }
                    }
                })
            key(Key.DirectionDown)
            focused(title(1, 0))
            val origin = checkNotNull(position.position.value)
            compose.mainClock.autoAdvance = false
            armed.set(true)
            compose.onRoot().performKeyInput { keyDown(Key.DirectionRight) }
            rightDown = true
            // No frame advances before the Default acquisition reaches the released fake's
            // boundary. Thus the cached frontier cannot win by cancelling before entry.
            compose.waitUntil(10_000) { entered.get() != null }
            assertEquals((1L..6L).map(::ChannelId), entered.get().first)
            assertEquals(Instant.fromEpochSeconds(hour + (hours + 3) * 3600), entered.get().second)
            assertFalse(cancelled.isCompleted)
            assertEquals(origin, position.position.value)
            if (publication == SuccessorPublication.BeforeShift) publishEqualEpg()
            // pressKey advances 50ms even with autoAdvance=false. Split its down/up
            // around the barrier, keeping the same single complete key cycle.
            compose.onRoot().performKeyInput { advanceEventTime(50); keyUp(Key.DirectionRight) }
            rightDown = false
            compose.mainClock.autoAdvance = autoAdvance
            focused(title(1, hours))
            compose.waitUntil(10_000) { cancelled.isCompleted }
            assertFalse("The frontier must use cached coverage, not release the held query", release.isCompleted)
            val successor = checkNotNull(position.position.value)
            val frontierWindow = hour + hours * 3600
            assertTrue("Native focus must apply W2 after the frontier's W1", successor.windowStartSec < frontierWindow)
            assertEquals(EventId(110L + hours), successor.eventId)
            if (publication == SuccessorPublication.AfterFocus) {
                publishEqualEpg()
                compose.waitForIdle()
                focused(title(1, hours))
                assertEquals(successor, position.position.value)
            }
            assertReadableFocus()
            armed.set(false)
            key(Key.DirectionLeft)
            focused(title(1, 0))
            assertReadableFocus()
            // Cancellation happens before the fake records a query; no playback or extra
            // acquisition is permitted on the cached return to the oversized origin.
            assertNoPlayback()
        } finally {
            armed.set(false)
            release.complete(Unit)
            if (rightDown) compose.onRoot().performKeyInput { keyUp(Key.DirectionRight) }
            compose.mainClock.autoAdvance = autoAdvance
        }
    }

    private fun publishEqualEpg() {
        val observation = session.observation.value
        val snapshot = checkNotNull(observation.epgSnapshotForDisplay)
        session.publish(SessionObservation.create(
            sessionState = observation.sessionState,
            channelState = observation.channelState,
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                events = snapshot.events, historicalEvents = snapshot.historicalEvents, coverages = snapshot.coverages,
            )),
            dvrState = observation.dvrState,
            dvrConfigurationsState = observation.dvrConfigurationsState,
        ))
    }

    @Test fun enlargedDisplayAcquiresThreeHoursWithoutInitialCoverage() = enlargedCoverage(atHorizon = false)

    @Test fun enlargedHorizonAcquisitionUsesClampedThroughAndDisplayPendingKey() = enlargedCoverage(atHorizon = true)

    private fun enlargedCoverage(atHorizon: Boolean) {
        val horizon = hour + 7 * 86400
        val displayAnchor = if (atHorizon) horizon - 2 * 3600 else hour
        val eventHour = if (atHorizon) horizon - 3600 else hour
        if (atHorizon) position.save(GuidePosition(ChannelId(1), EventId(110), eventHour, displayAnchor, 0))
        val request = AtomicReference<Pair<List<ChannelId>, Instant>>()
        val settle = CompletableDeferred<Unit>()
        show(scale = 1.3f, history = false, lastOffset = 0, eventHour = eventHour, initialCoverage = false,
            beforeCoverageBatch = { ids, through ->
                request.set(ids to through)
                settle.await()
            })
        compose.waitUntil(10_000) { request.get() != null }
        compose.runOnIdle {
            val (ids, through) = checkNotNull(request.get())
            assertEquals((1L..6L).map(::ChannelId), ids)
            assertEquals(Instant.fromEpochSeconds(if (atHorizon) horizon else hour + 3 * 3600), through)
            assertTrue(checkNotNull(session.observation.value.epgSnapshotForDisplay).coverages.isEmpty())
        }
        val ticks = compose.onAllNodes(hasText("", substring = true) and hasAnyAncestor(hasTestTag("epg-time-ruler")),
            useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("The displayed two-hour window has four half-hour ticks", 4, ticks.size)
        assertEquals(bounds("epg-time-ruler").width / 4, ticks[1].boundsInRoot.left - ticks[0].boundsInRoot.left, 1f)
        val pendingRow = hasText(context().getString(R.string.epg_loading)) and hasAnyAncestor(hasTestTag("epg-channel-row-3"))
        compose.onNode(pendingRow).assertIsDisplayed()
        dateFocused()
        compose.runOnIdle { settle.complete(Unit) }
        compose.waitUntil(10_000) { compose.onAllNodes(pendingRow).fetchSemanticsNodes().isEmpty() }
        dateFocused()
        key(Key.DirectionDown)
        focused(title(1, 0))
        if (atHorizon) {
            assertEquals(displayAnchor, checkNotNull(position.position.value).windowStartSec)
            key(Key.DirectionRight)
            focused(title(1, 0))
            assertEquals(displayAnchor, checkNotNull(position.position.value).windowStartSec)
        }
        assertNoPlayback(additionalCoverageCalls = 1)
    }

    private fun longProgrammeNavigation(scale: Float, hours: Int, reverse: Boolean) {
        if (reverse) position.save(GuidePosition(ChannelId(1), EventId(110), hour, hour + (hours - 1) * 3600, 0))
        show(scale = scale, longProgramme = true, longProgrammeHours = hours, longProgrammeSuccessor = true)
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(if (reverse) Key.DirectionLeft else Key.DirectionRight)
        focused(title(1, if (reverse) -1 else hours))
        assertReadableFocus()
        key(if (reverse) Key.DirectionRight else Key.DirectionLeft)
        focused(title(1, 0))
        assertReadableFocus()
        val additionalCoverageCalls = session.calls.drop(callsAtEntry)
            .count { it == FakeSessionCall.EPG_ACQUIRE_COVERAGE_BATCH }
        // Cached frontier coverage can cancel Right's request before the fake is entered.
        // Do not constrain that scheduling: this regression uses an ordinary key cycle.
        val allowedCoverageCalls = if (reverse) 0..0 else 0..1
        assertTrue("Unexpected coverage call count: $additionalCoverageCalls", additionalCoverageCalls in allowedCoverageCalls)
        assertNoPlayback(additionalCoverageCalls = additionalCoverageCalls)
    }

    @Test fun groupPanelSelectsTheGroupAndReturnsFocusToTheGroupButton() {
        show()
        key(Key.DirectionRight)
        tagFocused("channels-group-button")
        key(Key.Enter)
        compose.onNodeWithTag("channels-group-panel").assertIsDisplayed()
        tagFocused("channels-group-all")
        key(Key.Back)
        compose.onNodeWithTag("channels-group-panel").assertDoesNotExist()
        tagFocused("channels-group-button")
        assertEquals(null, model.scope.value.scope.activeTagId)

        key(Key.Enter)
        tagFocused("channels-group-all")
        key(Key.DirectionDown)
        tagFocused("channels-group-1")
        key(Key.Enter)
        compose.onNodeWithTag("channels-group-panel").assertDoesNotExist()
        tagFocused("channels-group-button")
        assertEquals(ChannelTagId(1), model.scope.value.scope.activeTagId)
        compose.onNodeWithText(context().getString(R.string.browse_filtered_title,
            context().getString(R.string.epg_title), "Favourites")).assertIsDisplayed()
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertNoPlayback(additionalCoverageCalls = 1)
    }

    @Test fun viewOptionsSwitchBetweenDenseAndRegularRows() {
        show()
        assertEquals("Dense rows by default", 56f, bounds("epg-channel-row-1").height, .1f)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.Enter)
        compose.onNodeWithTag("guide-view-panel").assertIsDisplayed()
        tagFocused("guide-view-rows-dense")
        key(Key.DirectionDown)
        tagFocused("guide-view-rows-regular")
        key(Key.Enter)
        compose.waitUntil(5_000) { bounds("epg-channel-row-1").height > 60f }
        assertEquals("Regular rows keep the native list item height", 64f, bounds("epg-channel-row-1").height, .1f)
        compose.onNodeWithTag("guide-view-panel").assertIsDisplayed()
        key(Key.Back)
        compose.onNodeWithTag("guide-view-panel").assertDoesNotExist()
        tagFocused("channels-view-button")
        assertNoPlayback()
    }

    @Test fun theDateButtonOpensTheDayAndTimePanelOnTheCheckedDay() {
        val gridHidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility) and
            hasAnyDescendant(hasTestTag("epg-channel-row-1"))
        show()
        key(Key.Enter)
        compose.onNodeWithTag("guide-time-panel").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
        assertEquals(1, compose.onAllNodes(gridHidden, useUnmergedTree = true).fetchSemanticsNodes().size)
        tagFocused("guide-time-day-0")
        compose.onNodeWithTag("guide-time-day-0").assertIsSelected()
        compose.onNodeWithTag("guide-time-day-1").assertIsNotSelected()
        assertDateButton("Today", hour)
        assertEquals(0, playbackRequests)
    }

    @Test fun aDayAppliesAtOnceAndKeepsThePanelAndItsFocus() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        key(Key.DirectionDown)
        tagFocused("guide-time-day-1")
        key(Key.Enter)
        compose.waitUntil(5_000) { dateButtonShows("Tomorrow", hour + 24 * 3600) }
        compose.onNodeWithTag("guide-time-panel").assertIsDisplayed()
        tagFocused("guide-time-day-1")
        compose.onNodeWithTag("guide-time-day-1").assertIsSelected()
        compose.onNodeWithTag("guide-time-day-0").assertIsNotSelected()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        tagFocused("guide-time-day-1")
        key(Key.Back)
        compose.onNodeWithTag("guide-time-panel").assertDoesNotExist()
        dateFocused()
        assertDateButton("Tomorrow", hour + 24 * 3600)
        assertEquals(0, playbackRequests)
    }

    @Test fun anHourJumpsToTheCheckedDayClosesThePanelAndLeavesFocusOnTheDateButton() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        key(Key.DirectionDown)
        key(Key.Enter)
        compose.waitUntil(5_000) { dateButtonShows("Tomorrow", hour + 24 * 3600) }
        // Six more days, then the first hour of tomorrow, then 03:00.
        repeat(6 + 1 + 3) { key(Key.DirectionDown) }
        tagFocused("guide-time-hour-03")
        key(Key.Enter)
        compose.onNodeWithTag("guide-time-panel").assertDoesNotExist()
        dateFocused()
        compose.waitUntil(5_000) { dateButtonShows("Tomorrow", hour + 24 * 3600 - 17 * 3600) }
        // Past the Guide's 10 s wait for coverage after a jump: a late window request still leaves focus here.
        compose.mainClock.advanceTimeBy(11_000)
        compose.waitForIdle()
        dateFocused()
        assertEquals(0, playbackRequests)
    }

    @Test fun nowJumpsBackToTheCurrentHourFromALaterDay() {
        show()
        key(Key.Enter)
        key(Key.DirectionDown)
        key(Key.Enter)
        compose.waitUntil(5_000) { dateButtonShows("Tomorrow", hour + 24 * 3600) }
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        tagFocused("guide-time-now")
        key(Key.Enter)
        compose.onNodeWithTag("guide-time-panel").assertDoesNotExist()
        dateFocused()
        compose.waitUntil(5_000) { dateButtonShows("Today", hour) }
        assertEquals(0, playbackRequests)
    }

    @Test fun theHoursStayWithinTheWindowBounds() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        // Earliest start is six hours before the opening hour, 14:00; the week ends on day 7.
        repeat(7 + 1) { key(Key.DirectionDown) }
        tagFocused("guide-time-hour-14")
        compose.onNodeWithTag("guide-time-hour-13").assertDoesNotExist()
        key(Key.DirectionUp)
        tagFocused("guide-time-day-7")
        key(Key.Enter)
        // 20:00 is past the last start of the final day, so the window settles on 17:00.
        compose.waitUntil(5_000) { dateButtonShows("Sun, Oct 11", hour + 7 * 24 * 3600 - 3 * 3600) }
        repeat(1 + 17) { key(Key.DirectionDown) }
        tagFocused("guide-time-hour-17")
        compose.onNodeWithTag("guide-time-hour-17").assertIsSelected()
        key(Key.DirectionDown)
        tagFocused("guide-time-hour-17")
    }

    @Test fun backClosesThePanelOntoTheDateButton() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        key(Key.Back)
        compose.onNodeWithTag("guide-time-panel").assertDoesNotExist()
        dateFocused()
        assertDateButton("Today", hour)
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun theDayAndTimePanelIsGerman() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        compose.onAllNodesWithText("Tag und Uhrzeit").fetchSemanticsNodes().isNotEmpty().also(::assertTrue)
        compose.onNodeWithText("Jetzt").assertIsDisplayed()
        compose.onNodeWithText("Heute").assertIsDisplayed()
        compose.onNodeWithText("Morgen").assertIsDisplayed()
    }

    @Test fun headlineShowsTheSettledProgrammeAboveAboutFourAndAHalfRows() {
        show(headline = true)
        // On the header, before the grid has focus: the programme the grid enters.
        headlineTitle(title(1, 0))
        val header = bounds("guide-date-button")
        val headline = bounds("guide-headline")
        val ruler = bounds("epg-time-ruler")
        assertTrue("Headline below the header: header=$header headline=$headline", headline.top >= header.bottom)
        assertTrue("Ruler below the headline: headline=$headline ruler=$ruler", ruler.top >= headline.bottom)
        val first = bounds("epg-channel-row-1").top
        val rows = (540f - first) / (bounds("epg-channel-row-2").top - first)
        assertEquals("About four and a half rows below the headline", 4.5f, rows, .3f)
        compose.onNodeWithTag("channels-immersive-background").assertExists()
        compose.onNodeWithTag("guide-headline").assert(hasText(title(1, 0), substring = true))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertEquals("The headline's height does not depend on its text", headline, bounds("guide-headline"))
        capture("guide-headline", 1f, LayoutDirection.Ltr).recycle()
        // Quick presses, as a held key repeats, keep the settled headline until focus rests.
        compose.mainClock.autoAdvance = false
        try {
            repeat(2) {
                compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
                compose.mainClock.advanceTimeBy(80)
            }
            compose.onNode(hasText(title(1, 2)) and isFocused()).assertExists()
            compose.onNode(hasTestTag("guide-headline-title"), useUnmergedTree = true).assertTextEquals(title(1, 0))
            compose.mainClock.advanceTimeBy(GuideHeadlineSettleMs + 300)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        focused(title(1, 2))
        headlineTitle(title(1, 2))
        assertEquals(headline, bounds("guide-headline"))
        assertNoPlayback()
    }

    @Test fun aSlowGroupSwitchKeepsTheHeadlineAndItsArt() {
        val armed = AtomicBoolean(false)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            show(headline = true, groupWithoutFirst = true, beforeCoverageBatch = { _, _ ->
                if (armed.get()) {
                    entered.complete(Unit)
                    release.await()
                }
            })
            key(Key.DirectionDown)
            focused(title(1, 0))
            headlineTitle(title(1, 0))
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isNotEmpty() }
            key(Key.DirectionUp)
            dateFocused()
            key(Key.DirectionRight)
            tagFocused("channels-group-button")
            key(Key.Enter)
            tagFocused("channels-group-all")
            key(Key.DirectionDown)
            tagFocused("channels-group-1")
            // A group without the focused programme's channel.
            key(Key.DirectionDown)
            tagFocused("channels-group-2")
            val full = checkNotNull(session.observation.value.epgSnapshotForDisplay)
            // Only the focused channel's programmes are loaded, so the new group's rows stay empty
            // until theirs arrive and the grid focuses nothing.
            val first = full.events.filter { it.channelId == ChannelId(1) }
            publishEvents(first, EpgSnapshot.create(events = first,
                historicalEvents = full.historicalEvents.filter { it.channelId == ChannelId(1) },
                coverages = full.coverages), covered = true)
            armed.set(true)
            key(Key.Enter)
            compose.waitUntil(10_000) { entered.isCompleted }
            // Well past the settle while they load: still the programme, never "no information".
            compose.mainClock.advanceTimeBy(GuideHeadlineSettleMs * 4)
            compose.waitForIdle()
            compose.onNode(hasTestTag("guide-headline-title"), useUnmergedTree = true).assertTextEquals(title(1, 0))
            compose.onNodeWithTag("channels-immersive-picon").assertExists()
            // Through the new programme's settle and reveal the outgoing logo keeps drawing.
            compose.mainClock.autoAdvance = false
            publishEvents(full.events, full, covered = true)
            release.complete(Unit)
            val next = hasTestTag("guide-headline-title") and hasText(title(2, -1))
            var framesAfterSwitch = 0
            for (frame in 0 until 600) {
                compose.mainClock.advanceTimeByFrame()
                assertTrue("logo missing at frame $frame",
                    compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isNotEmpty())
                if (compose.onAllNodes(next, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) framesAfterSwitch++
                if (framesAfterSwitch > 60) break
            }
            assertTrue("headline never moved to the new group", framesAfterSwitch > 60)
            compose.mainClock.autoAdvance = true
            // The grid takes the new group's first programme at the window's start.
            key(Key.DirectionDown)
            focused(title(2, -1))
            headlineTitle(title(2, -1))
        } finally {
            compose.mainClock.autoAdvance = true
            armed.set(false)
            release.complete(Unit)
        }
    }

    @Test fun viewOptionsHideTheHeadlineAndItsArtAndKeepTheChoice() {
        show(headline = true)
        headlineTitle(title(1, 0))
        val withHeadline = bounds("epg-time-ruler").top
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.Enter)
        tagFocused("guide-view-rows-dense")
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        tagFocused("guide-view-headline")
        compose.onNodeWithTag("guide-view-headline").assertIsOn()
        key(Key.Enter)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("guide-headline").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("channels-immersive-background").assertDoesNotExist()
        compose.onNodeWithTag("guide-view-headline").assertIsOff()
        assertEquals("The grid takes the headline's room", 72f, bounds("epg-time-ruler").top, .1f)
        assertTrue(withHeadline > 72f)
        assertFalse("The choice is kept", runBlocking { uiSettings.settings.first() }.guideView.headline)
        key(Key.Back)
        tagFocused("channels-view-button")
        assertNoPlayback()
    }

    private fun headlineTitle(title: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag("guide-headline-title") and hasText(title), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun backReturnsAnOffNowGuideToNowOnTheSameChannelThenToTheRail() {
        show()
        key(Key.DirectionDown)
        focused(title(1, 0))
        val nowWindow = checkNotNull(position.position.value).windowStartSec
        key(Key.DirectionDown)
        focused(title(2, 0))
        repeat(4) { key(Key.DirectionRight) }
        focused(title(2, 4))
        assertNotEquals(nowWindow, checkNotNull(position.position.value).windowStartSec)
        key(Key.Back)
        focused(title(2, 0))
        assertEquals(nowWindow, checkNotNull(position.position.value).windowStartSec)
        key(Key.Back)
        tagFocused("nav-epg")
        assertNoPlayback(additionalCoverageCalls = 1)
    }

    @Test fun backToNowFocusesTheProgrammeOnAirWhenItArrivesAfterTheJump() {
        // 19:30-20:30, 20:30-21:30 ...: at 20:45 the hour's first programme is not the one on air.
        assertTrue(SystemClock.setCurrentTimeMillis((hour + 45 * 60) * 1000))
        val armed = AtomicBoolean(false)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            show(eventHour = hour - 1800, beforeCoverageBatch = { _, _ ->
                if (armed.get()) {
                    entered.complete(Unit)
                    release.await()
                }
            })
            key(Key.DirectionDown)
            key(Key.DirectionDown)
            focused(title(2, 1))
            val nowWindow = checkNotNull(position.position.value).windowStartSec
            repeat(3) { key(Key.DirectionRight) }
            focused(title(2, 4))
            assertNotEquals(nowWindow, checkNotNull(position.position.value).windowStartSec)
            val full = checkNotNull(session.observation.value.epgSnapshotForDisplay)
            publishEvents(full.events.filterNot { it.id == EventId(211) }, full, covered = false)
            armed.set(true)
            key(Key.Back)
            compose.waitUntil(10_000) { entered.isCompleted }
            publishEvents(full.events, full, covered = true)
            release.complete(Unit)
            focused(title(2, 1))
            assertEquals(nowWindow, checkNotNull(position.position.value).windowStartSec)
        } finally {
            armed.set(false)
            release.complete(Unit)
        }
    }

    @Test fun theGenreLevelOpensFromTheGroupPanelAndAGenreDimsTheOtherProgrammes() {
        show(genres = true)
        key(Key.DirectionRight)
        tagFocused("channels-group-button")
        key(Key.Enter)
        tagFocused("channels-group-all")
        compose.onNodeWithTag("guide-genre").assert(hasText("All genres"))
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        tagFocused("guide-genre")
        key(Key.Enter)
        compose.onNodeWithTag("guide-genre-panel").assertIsDisplayed()
        tagFocused("guide-genre-all")
        // The list composes only the rows in view; each family row among them carries its color.
        val familyRows = compose.onAllNodes(SemanticsMatcher("genre family row") {
            it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }.let { tag ->
                tag != null && tag.startsWith("guide-genre-") && tag != "guide-genre-panel" && tag != "guide-genre-all"
            }
        }).fetchSemanticsNodes().size
        assertTrue("Family rows are in view", familyRows >= 4)
        assertEquals("Each family carries its color", familyRows,
            compose.onAllNodesWithTag("option-dot", useUnmergedTree = true).fetchSemanticsNodes().size)
        key(Key.Back)
        compose.onNodeWithTag("channels-group-panel").assertIsDisplayed()
        tagFocused("guide-genre")
        key(Key.Enter)
        repeat(4) { key(Key.DirectionDown) }
        tagFocused("guide-genre-sport")
        key(Key.Enter)
        compose.onNodeWithTag("guide-genre-panel").assertDoesNotExist()
        tagFocused("channels-group-button")
        compose.onNodeWithText(context().getString(R.string.browse_filtered_title,
            context().getString(R.string.epg_title), "Sport")).assertIsDisplayed()
        assertEquals("The group stays", null, model.scope.value.scope.activeTagId)
        key(Key.Enter)
        compose.onNodeWithTag("guide-genre").assert(hasText("Sport"))
        key(Key.Back)
        tagFocused("channels-group-button")

        // Programmes outside Sport stay in the grid, reachable and named as such.
        fun cell(channel: Int) = hasContentDescription(title(channel, 0), substring = true)
        val outside = hasContentDescription("not Sport", substring = true)
        key(Key.DirectionDown)
        focused(title(1, 0))
        compose.onNode(cell(1) and outside).assertDoesNotExist()
        key(Key.DirectionDown)
        compose.onNode(cell(2) and outside).assertIsFocused()
        compose.onNode(cell(4) and outside).assertExists()
        assertNoPlayback()
    }

    @Test fun aFinishedRecordingIsTheDotAndAFailedOrMissedOneTheErrorMarkAndTheHeadlineBadgesThem() {
        // Past programmes come from the history fixture: channel 1 recorded, 2 failed, 4 missed.
        fun recording(id: Long, channel: Int, state: DvrEntryState) =
            DvrEntry.create(DvrEntryId(id), eventId = EventId(channel * 100L + 9), state = state)
        show(headline = true, recordings = listOf(
            recording(1, 1, DvrEntryState.COMPLETED),
            recording(2, 2, DvrEntryState.COMPLETED_ERROR),
            recording(3, 4, DvrEntryState.MISSED),
            // Channel 1's current programme is still recording, with errors.
            DvrEntry.create(DvrEntryId(4), eventId = EventId(110), state = DvrEntryState.RECORDING_ERROR),
        ))
        fun mark(tag: String, channel: Int, offset: Int) = compose.onAllNodes(
            hasTestTag(tag) and hasAnyAncestor(hasContentDescription(title(channel, offset), substring = true)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size
        fun description(channel: Int, offset: Int) = compose.onNode(hasContentDescription(title(channel, offset), substring = true))
            .fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }.orEmpty().joinToString()
        fun headlineBadge(tag: String) {
            compose.mainClock.advanceTimeBy(GuideHeadlineSettleMs + 300)
            compose.waitForIdle()
            compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("guide-headline")), useUnmergedTree = true)
                .assertIsDisplayed()
        }

        key(Key.DirectionDown)
        focused(title(1, 0))
        assertEquals("A running recording with errors keeps its dot", 1, mark("guide-cell-recording-mark", 1, 0))
        assertEquals("and adds the error mark", 1, mark("guide-cell-failed-mark", 1, 0))
        val running = description(1, 0)
        assertTrue(running, running.contains(context().getString(R.string.recording_state_recording_errors)))
        assertFalse("The dot already names the errors", running.contains(context().getString(R.string.recording_state_failed)))
        key(Key.DirectionLeft)
        focused(title(1, -1))
        assertEquals(1, mark("guide-cell-recording-mark", 1, -1))
        assertEquals(0, mark("guide-cell-failed-mark", 1, -1))
        assertTrue(description(1, -1), description(1, -1).contains(context().getString(R.string.details_recorded)))
        headlineBadge("recording-badge-recorded")
        key(Key.DirectionDown)
        focused(title(2, -1))
        assertEquals(0, mark("guide-cell-recording-mark", 2, -1))
        assertEquals(1, mark("guide-cell-failed-mark", 2, -1))
        assertTrue(description(2, -1), description(2, -1).contains(context().getString(R.string.recording_state_failed)))
        headlineBadge("recording-badge-failed")
        // Channel 3 has no guide data, so Down passes it.
        key(Key.DirectionDown)
        focused(title(4, -1))
        assertEquals(0, mark("guide-cell-recording-mark", 4, -1))
        assertEquals(1, mark("guide-cell-failed-mark", 4, -1))
        assertTrue(description(4, -1), description(4, -1).contains(context().getString(R.string.recording_notice_missed)))
        assertFalse(description(4, -1), description(4, -1).contains(context().getString(R.string.recording_state_failed)))
        headlineBadge("recording-badge-missed")
        assertNoPlayback()
    }

    @Test fun aCellTooNarrowForTheMarkLeavesItOutInsteadOfShrinkingIt() {
        fun scheduled(id: Long, eventId: Long) =
            DvrEntry.create(DvrEntryId(id), eventId = EventId(eventId), state = DvrEntryState.SCHEDULED)
        show(shortSecondMinutes = 3, recordings = listOf(scheduled(1, 111), scheduled(2, 112)))
        fun mark(offset: Int) = compose.onNode(
            hasTestTag("guide-cell-recording-mark") and hasAnyAncestor(hasContentDescription(title(1, offset), substring = true)),
            useUnmergedTree = true,
        )
        mark(1).assertIsNotDisplayed()
        mark(2).assertIsDisplayed().assertWidthIsEqualTo(StatusIconSize).assertHeightIsEqualTo(StatusIconSize)
        assertNoPlayback()
    }

    @Test fun genreColorsTurnOffAndTheGenreLevelLosesItsDots() {
        show(genres = true)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.Enter)
        repeat(3) { key(Key.DirectionDown) }
        tagFocused("guide-view-genre-colors")
        compose.onNodeWithTag("guide-view-genre-colors").assertIsOn()
        key(Key.Enter)
        compose.onNodeWithTag("guide-view-genre-colors").assertIsOff()
        assertFalse("The choice is kept", runBlocking { uiSettings.settings.first() }.guideView.genreColors)
        key(Key.Back)
        tagFocused("channels-view-button")
        key(Key.DirectionLeft)
        tagFocused("channels-group-button")
        key(Key.Enter)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        tagFocused("guide-genre")
        key(Key.Enter)
        tagFocused("guide-genre-all")
        compose.onAllNodesWithTag("option-dot", useUnmergedTree = true).assertCountEquals(0)
        assertNoPlayback()
    }

    @Test fun aServerWithoutGroupsStillOffersTheGenreThroughTheGroupButton() {
        show(genres = true, tags = false)
        key(Key.DirectionRight)
        tagFocused("channels-group-button")
        key(Key.Enter)
        compose.onNodeWithTag("channels-group-panel").assertIsDisplayed()
        tagFocused("guide-genre")
        compose.onNodeWithTag("channels-group-all").assertDoesNotExist()
        key(Key.Back)
        tagFocused("channels-group-button")
        assertNoPlayback()
    }

    @Test fun anOpenPanelHidesTheGridFromAccessibilityAndItsEntriesStayReadable() {
        val gridHidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility) and
            hasAnyDescendant(hasTestTag("epg-channel-row-1"))
        fun gridHiddenCount() = compose.onAllNodes(gridHidden, useUnmergedTree = true).fetchSemanticsNodes().size
        show()
        assertEquals(0, gridHiddenCount())
        key(Key.DirectionRight)
        tagFocused("channels-group-button")
        key(Key.Enter)
        compose.onNodeWithTag("channels-group-panel").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
        assertEquals(1, gridHiddenCount())
        key(Key.Back)
        compose.onNodeWithTag("channels-group-panel").assertDoesNotExist()
        assertEquals(0, gridHiddenCount())
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.Enter)
        compose.onNodeWithTag("guide-view-panel").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
        assertEquals(1, gridHiddenCount())
        key(Key.Back)
        compose.onNodeWithTag("guide-view-panel").assertDoesNotExist()
        assertEquals(0, gridHiddenCount())
    }

    private fun publishEvents(events: List<EpgEvent>, snapshot: EpgSnapshot, covered: Boolean) {
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

    private fun selectHistory() {
        show()
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionLeft)
        focused(title(1, -1))
    }

    private fun removeHistory() {
        compose.runOnIdle {
            val observation = session.observation.value
            val snapshot = checkNotNull(observation.epgSnapshotForDisplay)
            session.publish(SessionObservation.create(
                sessionState = observation.sessionState,
                channelState = observation.channelState,
                epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = snapshot.events, coverages = snapshot.coverages)),
                dvrState = observation.dvrState,
                dvrConfigurationsState = observation.dvrConfigurationsState,
            ))
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(title(1, -1))).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
    }

    private fun show(scale: Float = 1f, direction: LayoutDirection = LayoutDirection.Ltr, history: Boolean = true,
        lastOffset: Int = 5, eventHour: Long = hour, historyOffset: Int = -1, longProgramme: Boolean = false,
        longProgrammeHours: Int = 5, longProgrammeSuccessor: Boolean = false, initialCoverage: Boolean = true,
        beforeCoverageBatch: (suspend (List<ChannelId>, Instant) -> Unit)? = null, headline: Boolean = false,
        groupWithoutFirst: Boolean = false, genres: Boolean = false, tags: Boolean = true,
        recordings: List<DvrEntry> = emptyList(), shortSecondMinutes: Int? = null) {
        val channels = (1L..12L).map { id ->
            Channel.create(ChannelId(id), name = if (id == 2L) "Kultur und Dokumentationen aus aller Welt HD" else
                listOf("Ridge Earth HD", "Harbor Sport HD", "Northline News", "Kite Kids", "Lantern Hour")[(id.toInt() - 1) % 5],
                number = id, icon = if (id == 3L) null else ArtworkId(id.toInt()),
                tagIds = if (!tags) emptyList() else if (groupWithoutFirst && id > 1L) listOf(ChannelTagId(1), ChannelTagId(2)) else listOf(ChannelTagId(1)))
        }
        fun event(channel: Channel, offset: Int) = EpgEvent.create(
            EventId(channel.id.value * 100 + offset + 10), channelId = channel.id,
            start = Instant.fromEpochSeconds(eventHour + offset * 3600),
            stop = if (shortSecondMinutes != null && channel.id == ChannelId(1) && offset == 1) {
                Instant.fromEpochSeconds(eventHour + 3600 + shortSecondMinutes * 60L)
            } else Instant.fromEpochSeconds(eventHour + (offset + if (longProgramme && channel.id == ChannelId(1) && offset == 0) longProgrammeHours else 1) * 3600),
            title = title(channel.id.value.toInt(), offset), summary = "Synthetic offline programme metadata.",
            // Sport on the first channel, no genre code on the second, children's programmes after.
            contentGenre = if (genres) ContentGenre.fromContentType(when (channel.id.value) { 1L -> 0x40L; 2L -> null; else -> 0x50L }) else null,
        )
        session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels,
                if (!tags) emptyList() else listOf(ChannelTag.create(ChannelTagId(1), name = "Favourites", channelIds = channels.map { it.id })) +
                    if (groupWithoutFirst) listOf(ChannelTag.create(ChannelTagId(2), name = "News",
                        channelIds = channels.drop(1).map { it.id })) else emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                events = channels.filterNot { it.id == ChannelId(3) }.flatMap { ch ->
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
        model = ChannelsViewModel(session, ChannelTagSettingsStore(preferences()))
        models.put("channels", model)
        player = ExoPlayer.Builder(context()).build()
        val runtime = AppPlaybackRuntime(player, session, createTvheadendPlaybackCoordinator(player), settings, profiles,
            runtimeScope, TvheadendAudioOutputProvider(context()), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings())
        val selection = ChannelSelectionStore()
        val lastPlayed = LastPlayedChannelStore(context())
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        uiSettings = UiSettingsStore(preferences())
        // The grid's geometry without the headline; headline tests keep the default view.
        if (!headline) runBlocking { uiSettings.setGuideView(GuideView(headline = false)) }
        val noticeModule = module {
            single { notices }
            single { uiSettings }
            single { profiles }
            single<TvheadendSession> { session }
        }
        compose.setContent {
            KoinIsolatedContext(koinApplication { modules(noticeModule) }) {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale), LocalLayoutDirection provides direction) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    lineColor = MaterialTheme.colorScheme.primary.toArgb()
                    backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SideRail(currentRoute = AppDestination.GUIDE, showEpgMenu = true,
                            onRootBack = { navigationRequests++ }, onNavigate = { navigationRequests++ }) { padding, drawer ->
                            val view = uiSettings.settings.collectAsState(UiSettings()).value.guideView
                            var genre by remember { mutableStateOf(ProgrammeCategory.ALL) }
                            EpgGridScreen(contentPadding = padding, initialFocusEnabled = !drawer, view = view, channelViewModel = model,
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
        dateFocused()
        assertNotNull("The fixture must exercise a current SDK capability, not cached-only rows", model.observation.value.currentSession)
        callsAtEntry = session.calls.size
    }

    private fun assertReadableFocus() {
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val ruler = bounds("epg-time-ruler")
        assertTrue("Focus left $focus", focus.left >= ruler.left + 48f)
        assertTrue("Focus right $focus", focus.right <= ruler.right - 48f)
        assertTrue("Focus bottom $focus", focus.bottom <= 492f)
        val first = compose.onAllNodesWithTag("epg-channel-row-1").fetchSemanticsNodes().singleOrNull()
        if (first == null || first.boundsInRoot.top < bounds("epg-timeline-content").top) {
            assertTrue("Focus top $focus", focus.top >= bounds("epg-timeline-content").top + 48f)
        }
    }

    private fun assertLine(bitmap: Bitmap, x: Float, vararg ys: Int) {
        for (y in ys) {
            // Fractional chronological x antialiases both edges. Integrate their coverage
            // over the flat card/gap background instead of snapping Now to another axis.
            val background = android.graphics.Color.red(bitmap.getPixel(x.toInt() - 4, y))
            val difference = android.graphics.Color.red(lineColor) - background
            val width = (x.toInt() - 2..x.toInt() + 2).sumOf {
                (android.graphics.Color.red(bitmap.getPixel(it, y)) - background).toDouble() / difference
            }
            assertEquals("Now is above native focus at y=$y", lineColor, bitmap.getPixel(x.toInt(), y))
            assertEquals("Continuous 2px Now through cards and gaps at y=$y", 2.0, width, .08)
        }
    }

    private fun assertNearBackground(pixel: Int, tolerance: Int) {
        assertTrue(kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(backgroundColor)) <= tolerance)
        assertTrue(kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(backgroundColor)) <= tolerance)
        assertTrue(kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(backgroundColor)) <= tolerance)
    }

    private fun assertTerminalNativeFocus(bitmap: Bitmap, direction: LayoutDirection) {
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val native = nativeFocusBounds(bitmap)
        // Semantics omit the graphics-layer scale. Check the complete bright surface,
        // including its native 1.05 growth, rather than accepting a clipped rectangle.
        assertEquals("Complete native focus width", (focus.width - 4f) * 1.05f, native.width, 2f)
        assertEquals("Complete native focus height", focus.height * 1.05f, native.height, 2f)
        assertEquals("Native focus remains centred on its time allocation", focus.center.x, native.center.x, 1f)
        if (direction == LayoutDirection.Ltr) {
            assertTrue("Scaled focus must end inside the physical 48dp safe area: $native", native.right <= bitmap.width - 48f)
        } else {
            assertTrue("Scaled focus must start inside the physical 48dp safe area: $native", native.left >= 48f)
        }
        val safeArea = if (direction == LayoutDirection.Ltr) bitmap.width - 48 until bitmap.width else 0 until 48
        for (y in (focus.top.toInt() - 4)..(focus.bottom.toInt() + 4)) {
            for (x in safeArea) {
                assertEquals("Terminal safe area also excludes antialiased focus pixels at ($x,$y)", backgroundColor, bitmap.getPixel(x, y))
            }
        }
    }

    private fun nativeFocusBounds(bitmap: Bitmap): Rect {
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

    private fun capture(name: String, scale: Float, direction: LayoutDirection): Bitmap {
        assertNoPlayback()
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        assertEquals(960, bitmap.width)
        assertEquals(540, bitmap.height)
        val directory = File("build/outputs/browse-cohesion/guide").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        File(directory, "$name.txt").writeText(
            "canvas=960x540dp; density=1; locale=${Locale.getDefault()}; direction=$direction; fontScale=$scale; timezone=UTC\n" +
                "syntheticClockStart=2026-10-04T20:15:00Z; captureClockMillis=${SystemClock.uptimeMillis()}\n" +
                "focus=${compose.onNode(isFocused()).fetchSemanticsNode().config.let { it.getOrElseNullable(SemanticsProperties.Text) { null } ?: it.getOrElseNullable(SemanticsProperties.ContentDescription) { null } }}; " +
                "bounds=${compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot}; position=${position.position.value}\n" +
                "ruler=${bounds("epg-time-ruler")}; nativeFocusPixels=${nativeFocusBounds(bitmap)}\n" +
                "headerRefinement=inline number/logo or number/name; gutter=124dp; headerInset=16dp; numberGap=8dp; cellGap=4dp; nameType=titleSmall14/20 single line; picon=max48x28dp Fit (dense rows)\n" +
                "production=TVHeadendPlayerTheme + SideRail + EpgGridScreen; native D-pad focus; fake SDK; intercepted synthetic picons\n" +
                "background=opaque theme background, not live video; static Robolectric SDK34 native graphics only\n" +
                "playbackRequests=$playbackRequests; navigationRequests=$navigationRequests; sdkCalls=${session.calls.size}\n",
        )
        return bitmap
    }

    private fun assertNoPlayback(additionalCoverageCalls: Int = 0) {
        assertEquals(0, playbackRequests)
        assertEquals(0, navigationRequests)
        assertEquals(List(additionalCoverageCalls) { FakeSessionCall.EPG_ACQUIRE_COVERAGE_BATCH }, session.calls.drop(callsAtEntry))
        assertFalse(FakeSessionCall.BIND_LIVE_PLAYBACK in session.calls)
        assertNull(player.currentMediaItem)
        assertFalse(player.playWhenReady)
    }

    private fun assertChannelHeader(bitmap: Bitmap, scale: Float) {
        val header = bounds("epg-channel-header-2")
        val logo = bounds("epg-channel-picon-2")
        compose.onNodeWithText("Kultur und Dokumentationen aus aller Welt HD", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("epg-channel-header-2").assertContentDescriptionEquals("2 Kultur und Dokumentationen aus aller Welt HD")
        assertFalse("Header is not a D-pad focus target", compose.onNodeWithTag("epg-channel-header-2")
            .fetchSemanticsNode().config.contains(SemanticsProperties.Focused))
        val number = compose.onNodeWithText("2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(124f, header.width, .1f)
        assertEquals(48f, logo.width, .1f)
        assertEquals(28f, logo.height, .1f)
        assertEquals(number.right + 8f, logo.left, .1f)
        assertEquals(header.left + 16f, number.left, .1f)
        assertEquals("Logo remains centred to native pixel precision", header.center.y, logo.center.y, .5f)
        assertEquals("Number remains centred to native pixel precision", header.center.y, number.center.y, .5f)
        val noLogoHeader = bounds("epg-channel-header-3")
        val name = compose.onNodeWithText("Northline News", useUnmergedTree = true)
        val nameBounds = name.fetchSemanticsNode().boundsInRoot
        val noLogoNumber = compose.onNodeWithText("3", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("epg-channel-header-3").assertContentDescriptionEquals("3 Northline News")
        val layouts = mutableListOf<TextLayoutResult>()
        name.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single()
        assertEquals(noLogoHeader.left + 16f, noLogoNumber.left, .1f)
        assertEquals(noLogoNumber.right + 8f, nameBounds.left, .1f)
        assertEquals(noLogoHeader.right - 16f, nameBounds.right, .1f)
        assertEquals("No-logo number and name share the centred row", noLogoNumber.center.y, nameBounds.center.y, .5f)
        assertEquals(noLogoHeader.center.y, nameBounds.center.y, .5f)
        assertEquals(14.sp, text.layoutInput.style.fontSize)
        assertEquals(20.sp, text.layoutInput.style.lineHeight)
        assertEquals(scale, text.layoutInput.density.fontScale, .001f)
        assertEquals("No-logo name occupies only one line", 1, text.lineCount)
        assertTrue("Name line is complete, not vertically clipped", text.getLineBottom(0) <= nameBounds.height)
        assertTrue("Name retains bottom padding", nameBounds.bottom <= noLogoHeader.bottom - 4f)
        val programme = compose.onNodeWithText(title(2, 0), useUnmergedTree = true)
        val programmeLayouts = mutableListOf<TextLayoutResult>()
        programme.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(programmeLayouts) }
        val programmeText = programmeLayouts.single()
        assertEquals("Long native programme headline occupies only one line", 1, programmeText.lineCount)
        assertTrue("Long headlines use single-line ellipsis", programmeText.isLineEllipsized(0))
        assertTrue(programmeText.getLineBottom(0) <= programmeText.size.height)
        val programmeBounds = programme.fetchSemanticsNode().boundsInRoot
        val timeBounds = compose.onNode(hasText(formatTimeInterval(hour, hour + 3600)) and hasAnyAncestor(hasTestTag("epg-channel-row-2")),
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Native headline does not overlap its supporting time", programmeBounds.bottom <= timeBounds.top)
        assertTrue("Native dense list item keeps its bottom content padding", timeBounds.bottom <= header.bottom - 10f)
        val pixels = buildList {
            for (y in logo.top.toInt() until logo.bottom.toInt()) {
                for (x in logo.left.toInt() until logo.right.toInt()) {
                    if (bitmap.getPixel(x, y) == PICON_COLOR) add(x to y)
                }
            }
        }
        assertFalse("Production PiconBox must render the intercepted artwork", pixels.isEmpty())
        assertEquals("Wide picon fills the 48dp allocation without stretching", 48, pixels.maxOf { it.first } - pixels.minOf { it.first } + 1)
        assertEquals("88:20 artwork stays 4.4:1 inside the 48x28 box", 11, pixels.maxOf { it.second } - pixels.minOf { it.second } + 1)
        compose.onNodeWithTag("epg-channel-picon-3", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun headerSession(): CurrentSessionObservation = requireNotNull(FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
        )),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession())

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun key(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }
    private fun dateButtonText(day: String, startSec: Long) = context().getString(R.string.epg_date_button, day,
        at.bernhardberger.tvhplayer.ui.common.formatClock(
            java.time.Instant.ofEpochSecond(startSec).atZone(java.time.ZoneOffset.UTC), Locale.getDefault()))
    private fun dateButtonShows(day: String, startSec: Long) =
        compose.onAllNodes(hasTestTag("guide-date-button") and hasText(dateButtonText(day, startSec))).fetchSemanticsNodes().isNotEmpty()
    private fun assertDateButton(day: String, startSec: Long) =
        compose.onNodeWithTag("guide-date-button").assert(hasText(dateButtonText(day, startSec)))
    private fun dateFocused() = tagFocused("guide-date-button")
    private fun tagFocused(tag: String) = focused(hasTestTag(tag), tag)
    private fun focused(text: String) = focused(hasText(text), text)
    private fun focused(matcher: SemanticsMatcher, text: String) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodes(matcher and isFocused()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(matcher and isFocused()).assertIsDisplayed().assertIsFocused()
        } catch (failure: Throwable) {
            // Failure-only synthetic fixture evidence; retain the original exception and deadline.
            println("Guide focus failure: expected=$text position=${position.position.value}")
            if (::session.isInitialized) {
                println("Guide EPG=${session.observation.value.epgState.javaClass.simpleName} " +
                    "currentSession=${session.observation.value.currentSession != null} calls=${session.calls}")
            }
            println(runCatching { compose.onAllNodes(isFocused(), useUnmergedTree = true).printToString() }
                .getOrElse { "Cannot read focused nodes: ${it.javaClass.simpleName}" })
            println(runCatching { compose.onAllNodes(matcher, useUnmergedTree = true).printToString() }
                .getOrElse { "Cannot read expected nodes: ${it.javaClass.simpleName}" })
            throw failure
        }
    }
    private fun title(channel: Int, offset: Int) = if (channel == 2 && offset == 0)
        "Wunder der Natur: Eine außergewöhnliche Reise durch die nördlichen Landschaften" else "Channel $channel • Programme $offset"
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private fun context(): Application = ApplicationProvider.getApplicationContext()

    companion object {
        private const val PICON_COLOR = 0xff27a69a.toInt()
    }
}
