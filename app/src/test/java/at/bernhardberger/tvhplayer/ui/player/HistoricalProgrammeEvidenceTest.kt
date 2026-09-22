package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoricalProgrammeEvidenceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private lateinit var zone: TimeZone
    @Before fun before() { zone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun after() { TimeZone.setDefault(zone) }

    @Test fun retainedHistoryPreviewCrossingCancelAndMissingData() = evidence("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun retainedHistoryGermanLargeText() = evidence("de", 1.3f)

    private fun evidence(locale: String, fontScale: Float) {
        fun event(id: Long, start: Long, end: Long, title: String) = EpgEvent.create(
            id = EventId(id), channelId = ChannelId(1), title = title,
            start = Instant.fromEpochSeconds(start), stop = Instant.fromEpochSeconds(end),
        )
        val previous = event(1, 0, 3_600, "Die Reise durch die Berge – Geschichten aus den entlegensten Regionen Europas")
        val current = event(2, 3_600, 7_200, "Zeit im Bild")
        val observation = SessionObservation.create(epgState = EpgRepositoryState.Current(
            EpgSnapshot.create(events = listOf(current), historicalEvents = listOf(previous)),
        ))
        assertNull(observation.event(previous.id))
        assertNull(currentProgrammeEvent(observation, previous))
        val fixture = TimeshiftTestFixture(120.minutes).apply {
            updateHistory(20.minutes, 90.minutes, estimatedLiveEdgeTime = Instant.fromEpochSeconds(5_400))
        }
        val state = fixture.state.value.toAppPresentation(fixture.playbackPosition(65.minutes)).copy(paused = true)
        fun window(position: Int) = programmeWindow(state, state.timeline!!.select(position.minutes)) {
            observation.eventAt(ChannelId(1), it)
        }!!
        val actual = window(65)
        var selected by mutableStateOf<ProgrammeWindow?>(window(59))
        var targetMinutes by mutableStateOf(59)
        var preview by mutableStateOf(true)
        var standalone by mutableStateOf(false)
        var serverNow by mutableStateOf(5_400L)
        var committedHistory by mutableStateOf(false)
        val ended = if (locale == "de") "Endete um 01:00" else "Ended at 01:00"
        compose.setContent {
            view = LocalView.current
            val context = LocalContext.current
            val loader = remember { ImageLoader.Builder(context).diskCache(null).build() }
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        if (standalone) TimeshiftSeekPreview(
                            state = state, decision = TimeshiftSeekDecision(targetMinutes.minutes.inWholeMilliseconds,
                                (targetMinutes - 65).minutes.inWholeMilliseconds, false),
                            programmeWindow = selected,
                            headerContent = { modifier -> PlayerIdentityHeader(
                                imageLoader = loader, piconPath = null, eyebrow = "1 Documentary",
                                title = selected?.event?.title.orEmpty(),
                                support = endedProgrammeSupport(selected?.event, serverNow)
                                    ?: selected?.let { programmeWindowClockLabels(it.event).let { (start, end) -> "$start - $end" } }
                                    ?: androidx.compose.ui.res.stringResource(at.bernhardberger.tvhplayer.R.string.player_programme_timing_unavailable),
                                clock = "01:30", clockSupport = null, modifier = modifier,
                                clockStatus = { PlayerStatusTags(true, timeshift = state, recordingNow = true) },
                                tags = PlayerHeaderTags(title = "player-programme-title"),
                            ) },
                        ) else OverlayControlsTv(
                            imageLoader = loader, channelNumber = 1, channelName = "Documentary", piconPath = null,
                            nowEvent = current, nextEvent = null, nowSec = serverNow, controlsVisible = true, optionsOpen = false,
                            channelRecordingNow = true,
                            onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                            timeshiftState = if (preview) at.bernhardberger.tvhplayer.core.projectedTimeshiftState(
                                state, targetMinutes.minutes.inWholeMilliseconds) else state,
                            committedTimeshiftState = state, committedWindow = if (committedHistory) window(59) else actual,
                            programmeWindow = if (preview) selected else if (committedHistory) window(59) else actual, previewing = preview, paused = true,
                            timeshiftFeedback = null, onToggleTimeshiftPause = {}, onSeekTimeshift = {}, onGoLive = {},
                        )
                    }
                }
            }
        }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.onNodeWithTag("player-programme-title").assertTextEquals(previous.title!!)
        compose.onNodeWithText(ended).assertExists()
        assertEquals(59f / 60, compose.onNodeWithTag("player-seekbar").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current, 0.00001f)
        compose.onNodeWithTag("player-pause").assertDoesNotExist()
        capture("$locale-font$fontScale-full-previous", "player-seekbar")
        compose.runOnIdle { serverNow = 3_599 }
        compose.onNodeWithText(ended).assertDoesNotExist()
        compose.onNodeWithText("00:00 - 01:00").assertExists()
        compose.runOnIdle { serverNow = 3_600 }
        compose.onNodeWithText(ended).assertExists()
        compose.runOnIdle { preview = false; committedHistory = true }
        compose.onNodeWithText(ended).assertExists()
        compose.runOnIdle { preview = true; committedHistory = false; serverNow = 5_400 }
        compose.runOnIdle { selected = window(60); targetMinutes = 60 }
        compose.onNodeWithTag("player-programme-title").assertTextEquals(current.title!!)
        compose.onNodeWithText(ended).assertDoesNotExist()
        assertEquals(0f, compose.onNodeWithTag("player-seekbar").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        capture("$locale-font$fontScale-full-crossing", "player-seekbar")
        compose.runOnIdle { selected = null }
        compose.onNodeWithTag("player-programme-title").assertDoesNotExist()
        compose.onNodeWithText(ended).assertDoesNotExist()
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        capture("$locale-font$fontScale-full-missing", "player-seekbar")
        compose.runOnIdle { selected = window(59); targetMinutes = 59; preview = false }
        compose.onNodeWithTag("player-programme-title").assertTextEquals(current.title!!)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        capture("$locale-font$fontScale-cancel-actual", "player-seekbar")
        compose.runOnIdle { preview = true; standalone = true }
        compose.onNodeWithTag("player-programme-title").assertTextEquals(previous.title!!)
        capture("$locale-font$fontScale-standalone-previous", "caller-owned root; nonfocusable preview")
        compose.onNodeWithText(ended).assertExists()
        compose.runOnIdle { serverNow = 3_599 }
        compose.onNodeWithText(ended).assertDoesNotExist()
        compose.onNodeWithText("00:00 - 01:00").assertExists()
        compose.runOnIdle { serverNow = 3_600 }
        compose.onNodeWithText(ended).assertExists()
        compose.runOnIdle { selected = window(60); targetMinutes = 60 }
        compose.onNodeWithTag("player-programme-title").assertTextEquals(current.title!!)
        capture("$locale-font$fontScale-standalone-crossing", "caller-owned root; nonfocusable preview")
        compose.onNodeWithText(ended).assertDoesNotExist()
        compose.runOnIdle { selected = null }
        compose.onNodeWithText(ended).assertDoesNotExist()
        val unavailable = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
            .getString(at.bernhardberger.tvhplayer.R.string.player_programme_timing_unavailable)
        compose.onNodeWithText(unavailable).assertExists()
    }

    private fun capture(name: String, focus: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/historical-programme-evidence").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            File(directory, "$name.txt").writeText("canvas=960x540\ndensity=1.0\nlocale=${name.substringBefore('-')}\nfontScale=${if (name.contains("1.3")) "1.3" else "1.0"}\nzone=UTC\nfocus=$focus\nbackdrop=white\nSDK fixture retained adjacent events; production composables; paused fake state\n")
            bitmap.recycle()
        }
    }
}
