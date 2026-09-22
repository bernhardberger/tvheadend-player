package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.guideDisplayEvents
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.guide.*
import coil3.ImageLoader
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideHistoryCompositionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private lateinit var zone: TimeZone
    private val context get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
    private fun label(id: Int) = context.getString(id)
    @Before fun before() { zone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun after() { TimeZone.setDefault(zone) }
    private fun event(id: Long, channel: Long, start: Long, stop: Long) = EpgEvent.create(
        id = EventId(id), channelId = ChannelId(channel), title = "Programme $id",
        start = Instant.fromEpochSeconds(start), stop = Instant.fromEpochSeconds(stop))

    @Test fun englishPastRowsAndDetails() = captureHistory("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanPastRowsAndDetails() = captureHistory("de", 1.3f)

    private fun captureHistory(locale: String, scale: Float) {
        val a = event(1, 1, 0, 3600)
        val b = event(2, 1, 3600, 7200)
        val c = event(3, 2, 0, 3600)
        val d = event(4, 2, 3600, 7200)
        val snapshot = EpgSnapshot.create(events = listOf(b, d), historicalEvents = listOf(a, c))
        val events = guideDisplayEvents(snapshot)
        val requesters = mutableMapOf<EventId, FocusRequester>()
        var details by mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            val loader = remember { ImageLoader.Builder(context).diskCache(null).build() }
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                TVHeadendPlayerTheme {
                    if (details) ProgrammeDetailsPanel(PaddingValues(), a, null, null, { 4000 }, false,
                        null, {}, { details = false }, liveProgrammeActions = false)
                    else Column(Modifier.fillMaxSize()) {
                        listOf(1L, 2L).forEach { channel -> TimelineChannelRow(
                            channel = Channel.create(ChannelId(channel), name = "Documentary $channel"),
                            channelIndex = (channel - 1).toInt(), number = channel.toInt(), selectedEventId = null,
                            eventFocusRequesters = requesters, windowStartSec = 0, windowEndSec = 7200,
                            nowSecProvider = { 4000 }, imageLoader = loader, currentSession = null,
                            events = events.filter { it.channelId == ChannelId(channel) }, hasCachedEvents = true,
                            hasMatchingCachedEvents = true, connectionUiState = ConnectionUiState.Ready,
                            coveragePending = false, recordingForEvent = { null }, onFocused = {},
                            onOpenDetails = { details = true },
                        ) }
                    }
                }
            }
        }
        compose.runOnIdle { requesters.getValue(b.id).requestFocus() }
        compose.onNodeWithText("Programme 2").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Programme 1").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Programme 3").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Programme 1").assertIsFocused()
        capture("$locale-font$scale-past-guide", "Programme 1")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText(label(R.string.close)).assertIsFocused()
        compose.onNodeWithText(label(R.string.record)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.watch_from_start)).assertDoesNotExist()
        capture("$locale-font$scale-historical-details", "Close")
    }

    @Test fun archiveAuthorityFutureRecordAndRealRecordingActions() {
        var selected by mutableStateOf(event(1, 1, 5000, 6000))
        var live by mutableStateOf(false)
        var recording by mutableStateOf<DvrEntry?>(null)
        compose.setContent { TVHeadendPlayerTheme {
            ProgrammeDetailsPanel(PaddingValues(), selected, null, recording, { 4000 }, true,
                null, {}, {}, liveProgrammeActions = live)
        } }
        compose.onNodeWithText(label(R.string.record)).assertDoesNotExist()
        compose.runOnIdle { live = true }
        compose.onNodeWithText(label(R.string.record)).assertExists()
        compose.runOnIdle { selected = event(2, 1, 0, 3600); live = false }
        compose.onNodeWithText(label(R.string.watch_from_start)).assertDoesNotExist()
        compose.runOnIdle { recording = DvrEntry.create(id = DvrEntryId(7), eventId = selected.id, state = DvrEntryState.COMPLETED) }
        compose.onNodeWithText(label(R.string.watch_from_start)).assertExists()
        compose.runOnIdle { recording = null }
        compose.onNodeWithText(label(R.string.watch_from_start)).assertDoesNotExist()
    }

    private fun capture(name: String, focus: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            val captureView = if (name.endsWith("historical-details")) {
                android.view.inspector.WindowInspector.getGlobalWindowViews().last()
            } else view
            captureView.draw(Canvas(bitmap))
            val directory = File("build/outputs/guide-history-evidence").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "$name.txt").writeText("canvas=960x540\ndensity=1\nzone=UTC\nlocale=${name.substringBefore('-')}\nfontScale=${if (name.contains("1.3")) "1.3" else "1.0"}\nfocus=$focus\nproduction rows/details; SDK snapshot archive; not full screen owner\n")
            bitmap.recycle()
        }
    }
}
