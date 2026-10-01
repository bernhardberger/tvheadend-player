package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.getOrNull
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.ui.common.formatClock
import kotlin.time.Duration.Companion.minutes
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bar row's geometry: for one player kind and chrome layout the bar starts and ends at the same
 * x in every state, the Banner keeps the state cell's room and the controls hand it to the bar, a
 * tune never dips the fill, busy never changes the chrome's bounds or glyphs, and the step readout
 * sits just above the thumb.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerTimelineGeometryTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_800L
    private val airing = event(42L, now - 1_800, now + 1_800)
    private val previous = event(41L, now - 5_400, now - 1_800)
    private val airingWindow = ProgrammeWindow(airing, Instant.fromEpochSeconds(now), 0.5f, 0.2f, 0.9f, 0.9f, true)
    private val previousWindow = ProgrammeWindow(previous, Instant.fromEpochSeconds(now - 2_400), 0.8f, 0.0f, 1f, null, true)

    // Geometry uses coordinates relative to its live edge, with the target present as in SDK samples.
    private val buffered = sdkSampleAtLive(nowSec = now, start = 80)
        .copy(bufferStartMs = -600_000, positionMs = 0, liveEdgeMs = 0)

    /** Timing just known after a tune: the position sits at the start of a buffer that has begun to grow. */
    private val justTuned = AppTimeshiftState(available = true, bufferStartMs = 0, positionMs = 0, liveEdgeMs = 10_000, timingKnown = true)

    private class Frame(val name: String, val mode: PlayerChromeMode, val timeline: PlayerChromeTimeline, val cell: PlayerStateCell = PlayerStateCell.PLAYING,
                        val busy: PlayerBusyStatus? = null, val info: PlayerInfoBarData? = null)

    private fun event(id: Long, start: Long, stop: Long) = EpgEvent.create(
        EventId(id), ChannelId(1), Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(stop), title = "Programme $id",
    )

    private fun live(
        timeshift: AppTimeshiftState = buffered,
        committed: AppTimeshiftState = timeshift,
        window: ProgrammeWindow? = null,
        shown: ProgrammeWindow? = window,
        programme: EpgEvent? = airing,
        tuning: Boolean = false,
        previewing: Boolean = false,
        step: TimeshiftSeekDecision? = null,
        available: Boolean = true,
        fresh: Boolean = false,
        feedback: String? = null,
        channel: ChannelId = ChannelId(1),
        nowSec: Long = now,
        timeshiftExpected: Boolean = committed.available,
    ) = PlayerChromeTimeline.Live(
        timeshift = timeshift, nowSec = nowSec, programme = programme, committedTimeshift = committed,
        timeshiftExpected = timeshiftExpected, committedWindow = window,
        programmeWindow = shown, previewing = previewing, step = step, stepDeltaMs = step?.deltaMs, liveStart = fresh,
        liveAvailable = available, feedback = feedback, motionKey = channel, tuning = tuning,
    )

    /** Live states in the order a viewer meets them; [banner] picks the layout's own way to step. */
    private fun liveFrames(banner: Boolean): List<Frame> {
        val steady = if (banner) PlayerChromeMode.BANNER else PlayerChromeMode.CONTROLS
        val stepping = if (banner) PlayerChromeMode.BANNER_STEP else PlayerChromeMode.CONTROLS
        val behind = buffered.copy(positionMs = -30_000)
        return listOf(
            Frame("tuning", steady, live(buffered.copy(timingKnown = false), tuning = true), busy = PlayerBusyStatus.TUNING),
            Frame("airing", steady, live(buffered.copy(timingKnown = false), fresh = true)),
            Frame("timing known, no window", steady, live(justTuned, fresh = true)),
            Frame("window", steady, live(buffered, window = airingWindow)),
            Frame("behind live", steady, live(behind, window = airingWindow)),
            Frame("timing unknown, not a live start", steady, live(behind.copy(timingKnown = false, playbackTarget = null))),
            Frame("distance faded by a step", stepping, live(behind, window = airingWindow, previewing = true, step = TimeshiftSeekDecision(-30_000, -30_000, false))),
            Frame("step across a programme boundary", stepping,
                live(buffered.copy(positionMs = -400_000), committed = behind, window = airingWindow, shown = previousWindow,
                    previewing = true, step = TimeshiftSeekDecision(-400_000, -370_000, false))),
            Frame("step to live", stepping, live(buffered, committed = behind, window = airingWindow, previewing = true, step = TimeshiftSeekDecision(0, 30_000, true))),
            Frame("no EPG", steady, live(buffered, programme = null, window = programmeWindow(buffered) { null })),
            Frame("no EPG, no timeshift", steady, live(AppTimeshiftState(available = false), programme = null)),
            Frame("buffering", steady, live(behind, window = airingWindow), busy = PlayerBusyStatus.BUFFERING),
            Frame("paused", steady, live(behind, window = airingWindow), PlayerStateCell.PAUSED),
            Frame("timeshift unavailable", steady, live(AppTimeshiftState(available = false))),
            Frame("feedback", steady, live(behind, window = airingWindow, feedback = "Reached the available buffer limit")),
            Frame("playback unavailable", steady, live(available = false)),
        )
    }

    // 1. The bar does not move.

    @Test fun theLiveBannersBarKeepsItsBoundsInEveryState() = assertLiveBoundsStable(banner = true, fontScales = listOf(1f))

    @Test fun theLiveControlsBarKeepsItsBoundsInEveryState() = assertLiveBoundsStable(banner = false, fontScales = listOf(1f))

    @Test fun theLiveBannersBarKeepsItsBoundsAtTheLargestFontScales() = assertLiveBoundsStable(banner = true, fontScales = listOf(1.3f, 1.5f))

    @Test fun theLiveControlsBarKeepsItsBoundsAtTheLargestFontScales() = assertLiveBoundsStable(banner = false, fontScales = listOf(1.3f, 1.5f))

    private fun assertLiveBoundsStable(banner: Boolean, fontScales: List<Float>) {
        val frames = liveFrames(banner)
        var index by mutableStateOf(0)
        var scale by mutableStateOf(fontScales.first())
        compose.setContent {
            Scaled(scale) { LiveChrome(frames[index]) }
        }
        for (fontScale in fontScales) {
            compose.runOnIdle { scale = fontScale }
            val bounds = frames.indices.map { at ->
                compose.runOnIdle { index = at }
                settle()
                "${frames[at].name} at font $fontScale" to bounds("player-timeline-track")
            }
            assertSameBar(bounds)
        }
    }

    // 1b. The bar row is start, bar, end. Live's distance behind live is drawn above the end clock, only
    // behind live, in a box that never moves; the state is announced from it with nothing drawn too.

    @Test fun theBannerDrawsTheDistanceOnlyBehindLiveAndAnnouncesTheStateInEveryLiveState() = assertLiveStatus(banner = true)

    @Test fun theControlsDrawTheDistanceOnlyBehindLiveAndAnnounceTheStateInEveryLiveState() = assertLiveStatus(banner = false)

    private fun assertLiveStatus(banner: Boolean) {
        val frames = liveFrames(banner)
        // Where playback is: a step's target never changes it, tuning lands at live. Null draws nothing.
        val behind = setOf("behind live", "distance faded by a step", "step across a programme boundary", "step to live",
            "buffering", "paused", "feedback", "timing unknown, not a live start")
        val expected = frames.associate { it.name to when (it.name) {
            in behind -> "0:30 behind live"
            "timing known, no window" -> "0:10 behind live"
            else -> null
        } }
        var index by mutableStateOf(0)
        var timeshiftColor = Color.Unspecified
        var widths: TimelineEndpointWidths? = null
        var cell = 0.dp
        compose.setContent {
            LiveChrome(frames[index])
            TVHeadendPlayerTheme {
                timeshiftColor = MaterialTheme.colorScheme.tertiary
                widths = rememberTimelineEndpointWidths(TimelineKind.LIVE)
                cell = playerStateCellSize()
            }
        }
        var box: Rect? = null
        for (at in frames.indices) {
            compose.runOnIdle { index = at }
            settle()
            val name = frames[at].name
            assertFalse("'$name': the row has no tag box", exists("player-end-tag-slot"))
            if (name == "playback unavailable") {
                assertFalse("'$name': no state to announce", exists("player-live-state"))
                assertFalse("'$name': no end clock either", exists("player-end-clock"))
                continue
            }
            assertEquals("'$name': the distance", expected.getValue(name), distance())
            assertEquals("'$name': the announced state", if (expected.getValue(name) == null) "Live" else "Behind live", liveState())
            compose.onNodeWithTag("player-live-state", useUnmergedTree = true)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            val row = bounds("player-timeline-labels")
            val track = bounds("player-timeline-track")
            val bounds = bounds("player-live-state")
            box?.let { assertEquals("'$name': the distance's box does not move", it, bounds) }
            box = bounds
            assertTrue("'$name': the box has a size, so its announcement is heard with nothing drawn", bounds.width > 0f && bounds.height > 0f)
            // Painted above its parent's bounds, it is still displayed: no ancestor clips it.
            compose.onNodeWithTag("player-live-state", useUnmergedTree = true).assertIsDisplayed()
            assertEquals("'$name': the box ends with the row", row.right, bounds.right, 0.5f)
            assertEquals("'$name': at one offset above the bar row", row.top - px(8.dp), bounds.bottom, 0.5f)
            // Start, bar, end: the start label's box, the bar and the end clock's box fill the row.
            val stateCell = if (banner) px(cell + 8.dp) else 0f
            assertEquals("'$name': the bar starts after the start box", row.left + stateCell + px(widths!!.leading + 8.dp), track.left, 1f)
            assertEquals("'$name': the end box is all that follows the bar", row.right - px(widths!!.trailing + 8.dp), track.right, 1f)
            assertEquals("'$name': the box is as wide as the widest distance", px(widths!!.distance), bounds.width, 1f)
            if (exists("player-end-clock")) {
                assertEquals("'$name': the end clock ends with the row", row.right, bounds("player-end-clock").right, 0.5f)
            }
            if (exists("player-distance")) {
                val drawn = bounds("player-distance")
                val end = if (exists("player-end-clock")) bounds("player-end-clock").right else row.right
                assertEquals("'$name': the distance's right edge is the end clock's", end, drawn.right, 0.5f)
                assertTrue("'$name': above the bar row", drawn.bottom <= row.top)
                assertEquals("'$name': at one offset above the bar row", row.top - px(8.dp), drawn.bottom, 0.5f)
                val style = compose.onNodeWithTag("player-distance", useUnmergedTree = true).fetchSemanticsNode().let { node ->
                    mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                        .also { node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!(it) }.single().layoutInput.style
                }
                assertEquals("'$name': in the bar's timeshift colour", timeshiftColor, style.color)
                assertEquals("'$name': tabular digits", "tnum", style.fontFeatureSettings)
            }
        }
        assertNotNull(box)
    }

    // 1c. The info block's last line ends before the distance, whatever that line is and wherever playback is.

    @Test fun theBannersLastInfoLineEndsBeforeTheDistance() = assertLastInfoLineClear(banner = true)

    @Test fun theControlsLastInfoLineEndsBeforeTheDistance() = assertLastInfoLineClear(banner = false)

    private fun assertLastInfoLineClear(banner: Boolean) {
        val long = "A very long line of programme text that runs on and on past everything else the bar shows, ".repeat(4)
        // The last line and its text: Next; without Next the subtitle; without guide data the title.
        val infos = listOf(
            Triple("a long Next line", "player-info-last-text", PlayerInfoBarData("1", "One", "Programme", "00:00–01:00", subtitle = "Subtitle", next = long)),
            Triple("a long subtitle without Next", "player-info-subtitle", PlayerInfoBarData("1", "One", "Programme", "00:00–01:00", subtitle = long)),
            Triple("a long title without guide data", "player-info-title", PlayerInfoBarData("1", "One", long, "")),
        )
        val mode = if (banner) PlayerChromeMode.BANNER else PlayerChromeMode.CONTROLS
        val behindLive = buffered.copy(positionMs = -30_000)
        var info by mutableStateOf(infos.first().third)
        var behind by mutableStateOf(true)
        var scale by mutableStateOf(1f)
        compose.setContent {
            Scaled(scale) { LiveChrome(Frame("", mode, live(if (behind) behindLive else buffered, window = airingWindow), info = info)) }
        }
        for (fontScale in listOf(1f, 1.3f)) {
            compose.runOnIdle { scale = fontScale }
            var offset: Float? = null
            for ((name, tag, data) in infos) {
                compose.runOnIdle { info = data; behind = true }
                settle()
                val case = "$name at font $fontScale"
                val drawn = bounds("player-distance")
                val box = bounds("player-live-state")
                val line = bounds(tag)
                val row = bounds("player-timeline-labels")
                assertEquals("$case: the distance ends with the end clock", bounds("player-end-clock").right, drawn.right, 0.5f)
                assertTrue("$case: the line ends before the distance's box and its gap: $line vs $box",
                    line.right <= box.left - px(16.dp) + 0.5f)
                assertTrue("$case: the line is cut by the reserve, not shorter: $line vs $box", line.right > box.left - px(16.dp) - px(24.dp))
                // The lines keep their places, so without Next the last place is empty and the distance
                // stands in it, below the line.
                if (tag != "player-info-last-text") {
                    assertTrue("$case: the distance is below the line, in Next's empty place: $drawn vs $line", drawn.top >= line.bottom - 0.5f)
                } else assertTrue("$case: the distance is on the last line's height: $drawn vs $line",
                    drawn.top >= line.top - 0.5f && drawn.bottom <= line.bottom + 0.5f)
                val above = row.top - drawn.bottom
                offset?.let { assertEquals("$case: at the same offset above the bar row", it, above, 0.5f) }
                offset = above
                compose.runOnIdle { behind = false }
                settle()
                assertFalse("$case: nothing is drawn at the live edge", exists("player-distance"))
                assertEquals("$case: the reserve stays at the live edge, so the line does not re-wrap", line, bounds(tag))
            }
        }
    }

    @Test fun aLastInfoLineThatFitsKeepsItsBoundsWithTheReserve() {
        var reserve by mutableStateOf(0.dp)
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            TVHeadendPlayerTheme {
                val info = liveInfoBarData(1, "One", airing, airing, false, now, "Programme")
                val loader = remember { ImageLoader(context) }
                PlayerInfoBar(info, emptyList(), Modifier.testTag("player-info-bar"), lastLineEndReserve = reserve) {
                    PlayerIdentityCard(PlayerChromeContent("", info), loader, null, it)
                }
            }
        }
        compose.waitForIdle()
        val tags = listOf("player-info-bar", "player-info-title", "player-info-last-row", "player-info-last-text")
        val resting = tags.map { bounds(it) }
        compose.runOnIdle { reserve = 74.dp }
        compose.waitForIdle()
        assertEquals("no info bound changes with the reserve", resting, tags.map { bounds(it) })
    }

    @Test fun aRecordingsBarRowShowsItsLengthAndOnlyGrowingRecordingsShowBehindLive() {
        var growing by mutableStateOf(false)
        var mode by mutableStateOf(PlayerChromeMode.BANNER)
        var target by mutableStateOf<Long?>(null)
        var paused by mutableStateOf(false)
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingChromeFixture(mode = if (target != null && mode == PlayerChromeMode.BANNER) PlayerChromeMode.BANNER_STEP else mode,
                    growing = growing, targetMs = target, originMs = target?.let { 1_800_000 }, paused = paused)
            }
        }
        for (stillRecording in listOf(false, true)) for (layer in listOf(PlayerChromeMode.BANNER, PlayerChromeMode.CONTROLS)) {
            var pausedFrames = 0
            for (step in listOf(null, 1_830_000L, 3_000_000L, null)) {
                // The frame after the steps is the paused row.
                compose.runOnIdle {
                    val wasStepping = target != null
                    growing = stillRecording; mode = layer; target = step; paused = step == null && wasStepping
                }
                settle()
                val case = "${if (stillRecording) "growing" else "finished"} recording, $layer, step $step${if (paused) ", paused" else ""}"
                if (paused) {
                    pausedFrames++
                    if (layer == PlayerChromeMode.BANNER) compose.onNodeWithTag("player-state", useUnmergedTree = true).assertContentDescriptionIs("Paused")
                }
                assertEquals("$case: only a growing recording shows distance from its head",
                    if (stillRecording) "30:00 behind live" else null, distance())
                assertEquals("$case: only a growing recording announces a live state",
                    if (stillRecording) "Behind live" else null, liveState())
                assertFalse("$case: no tag box", exists("player-end-tag-slot"))
                assertEquals("$case: the length alone, without a mark", "1:00:00", text("player-end-clock"))
                assertEquals("$case: the length ends with the row", bounds("player-timeline-labels").right, bounds("player-end-clock").right, 0.5f)
                val texts = compose.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(hasTestTag("player-timeline-labels")), useUnmergedTree = true)
                    .fetchSemanticsNodes().flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.joinToString(" ") { it.text }
                assertFalse("$case: no recording dot in the row: $texts", texts.contains("●"))
                // The badge in the eyebrow, not the bar row, says the recording still runs.
                assertEquals("$case: the badge", stillRecording, exists("player-rec-badge"))
                assertEquals("$case: the info bar speaks it: ${infoDescription()}", stillRecording, infoDescription().contains("Recording now"))
            }
            assertEquals("${if (stillRecording) "growing" else "finished"} recording, $layer: a paused row was shown", 1, pausedFrames)
        }
    }

    @Test fun aRecordingBannersBarKeepsItsBoundsInEveryState() = assertRecordingBoundsStable(banner = true, fontScales = listOf(1f))

    @Test fun aRecordingControlsBarKeepsItsBoundsInEveryState() = assertRecordingBoundsStable(banner = false, fontScales = listOf(1f))

    @Test fun aRecordingBannersBarKeepsItsBoundsAtTheLargestFontScales() = assertRecordingBoundsStable(banner = true, fontScales = listOf(1.3f, 1.5f))

    @Test fun aRecordingControlsBarKeepsItsBoundsAtTheLargestFontScales() = assertRecordingBoundsStable(banner = false, fontScales = listOf(1.3f, 1.5f))

    @Test fun aGrowingRecordingOnlyDrawsBehindLiveWithAKnownDistanceFromItsHead() {
        var mode by mutableStateOf(PlayerChromeMode.BANNER)
        var position by mutableStateOf(3_397_000L)
        var duration by mutableStateOf<Long?>(3_600_000L)
        var growing by mutableStateOf(true)
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingChromeFixture(mode = mode, positionMs = position, durationMs = duration, growing = growing)
            }
        }
        for (layer in listOf(PlayerChromeMode.BANNER, PlayerChromeMode.CONTROLS)) {
            compose.runOnIdle { mode = layer; position = 3_397_000; duration = 3_600_000; growing = true }
            settle()
            assertEquals("3:23 behind live", distance())
            val track = bounds("player-timeline-track")
            val box = bounds("player-live-state")
            compose.runOnIdle { position = 3_598_000 }
            settle()
            assertNull("$layer: no annotation within the live-head tolerance", distance())
            assertEquals("Live", liveState())
            assertEquals(box, bounds("player-live-state"))
            compose.runOnIdle { duration = null }
            settle()
            assertNull("$layer: unknown length is not a live-head claim", liveState())
            assertNull(distance())
            compose.runOnIdle { duration = 3_600_000; position = 3_397_000; growing = false }
            settle()
            assertNull("$layer: completed recordings have no behind-live label", distance())
            assertNull(liveState())
            assertEquals("1:00:00", text("player-end-clock"))
            assertEquals("$layer: the track never moves", track, bounds("player-timeline-track"))
        }
    }

    @Test fun theEnglishBehindLiveLabelFitsAcrossTheHourBoundary() = assertBehindLiveLabelFits("behind live")

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun theGermanBehindLiveLabelFitsAcrossTheHourBoundary() = assertBehindLiveLabelFits("hinter Live")

    private fun assertBehindLiveLabelFits(wording: String) {
        var recording by mutableStateOf(false)
        var scale by mutableStateOf(1f)
        var behind by mutableStateOf(3_599_000L)
        compose.setContent {
            Scaled(scale) {
                if (recording) {
                    TVHeadendPlayerTheme {
                        RecordingChromeFixture(growing = true, durationMs = 7_200_000, positionMs = 7_200_000 - behind)
                    }
                } else LiveChrome(Frame("", PlayerChromeMode.CONTROLS,
                    live(buffered.copy(bufferStartMs = -7_200_000, positionMs = -behind), window = airingWindow)))
            }
        }
        for (isRecording in listOf(false, true)) for (fontScale in listOf(1f, 1.3f, 1.5f)) {
            compose.runOnIdle { recording = isRecording; scale = fontScale }
            var box: Rect? = null
            var track: Rect? = null
            for ((ms, formatted) in listOf(3_599_000L to "59:59", 3_600_000L to "1:00:00", 3_730_000L to "1:02:10")) {
                compose.runOnIdle { behind = ms }
                settle()
                val case = "$wording, recording=$isRecording, font=$fontScale, $formatted"
                assertEquals(case, "$formatted $wording", distance())
                val node = compose.onNodeWithTag("player-distance", useUnmergedTree = true).fetchSemanticsNode()
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!(layouts)
                assertFalse("$case: full wording fits without truncation", layouts.single().hasVisualOverflow)
                assertEquals("$case: flush with the end label", bounds("player-end-clock").right, node.boundsInRoot.right, 0.5f)
                box?.let { assertEquals("$case: fixed label box", it, bounds("player-live-state")) }
                track?.let { assertEquals("$case: fixed track", it, bounds("player-timeline-track")) }
                box = bounds("player-live-state")
                track = bounds("player-timeline-track")
            }
        }
    }

    private class RecordingFrame(
        val name: String,
        val positionMs: Long = 1_800_000,
        val durationMs: Long? = 3_600_000,
        val growing: Boolean = false,
        val targetMs: Long? = null,
        val paused: Boolean = false,
        val buffering: Boolean = false,
    )

    private fun assertRecordingBoundsStable(banner: Boolean, fontScales: List<Float>) {
        val frames = listOf(
            RecordingFrame("mid recording"),
            RecordingFrame("position ticks", positionMs = 1_801_000),
            RecordingFrame("under an hour in", positionMs = 59_000, durationMs = 7_200_000),
            RecordingFrame("past an hour of a long recording", positionMs = 3_700_000, durationMs = 14_500_000),
            RecordingFrame("length unknown", durationMs = null),
            RecordingFrame("growing", positionMs = 1_800_000, durationMs = 2_400_000, growing = true),
            RecordingFrame("growing, at the head", positionMs = 2_399_000, durationMs = 2_400_000, growing = true),
            RecordingFrame("step", targetMs = 1_830_000),
            RecordingFrame("step to the head of a growing recording", durationMs = 2_400_000, growing = true, targetMs = 2_399_000),
            RecordingFrame("paused", paused = true),
            RecordingFrame("buffering", buffering = true),
        )
        var index by mutableStateOf(0)
        var scale by mutableStateOf(fontScales.first())
        compose.setContent {
            Scaled(scale) {
                val frame = frames[index]
                RecordingChromeFixture(
                    mode = when {
                        !banner -> PlayerChromeMode.CONTROLS
                        frame.targetMs != null -> PlayerChromeMode.BANNER_STEP
                        else -> PlayerChromeMode.BANNER
                    },
                    positionMs = frame.positionMs, durationMs = frame.durationMs, growing = frame.growing,
                    targetMs = frame.targetMs, originMs = frame.targetMs?.let { frame.positionMs },
                    paused = frame.paused, buffering = frame.buffering,
                )
            }
        }
        for (fontScale in fontScales) {
            compose.runOnIdle { scale = fontScale }
            val bounds = frames.indices.map { at ->
                compose.runOnIdle { index = at }
                settle()
                "${frames[at].name} at font $fontScale" to bounds("player-timeline-track")
            }
            assertSameBar(bounds)
        }
    }

    private fun assertSameBar(bounds: List<Pair<String, Rect>>) {
        val (firstName, reference) = bounds.first()
        for ((name, rect) in bounds) {
            assertEquals("bar start in '$name' vs '$firstName'", reference.left, rect.left, 0.5f)
            assertEquals("bar end in '$name' vs '$firstName'", reference.right, rect.right, 0.5f)
        }
    }

    // 2. The Banner keeps the state cell's room; the controls give it to the bar, moving with the rise.

    @Test fun theControlsBarStartsBeforeTheBannersByTheStateCellAndItsGapAndEndsWithIt() {
        var mode by mutableStateOf(PlayerChromeMode.BANNER)
        val timeline = live(window = airingWindow)
        compose.setContent { LiveChrome(Frame("", mode, timeline)) }
        settle()
        val banner = bounds("player-timeline-track")
        val cell = bounds("player-state")
        assertNotNull(cell)
        assertTrue("the Banner has its state cell", exists("player-state"))

        compose.runOnIdle { mode = PlayerChromeMode.CONTROLS }
        settle()
        val controls = bounds("player-timeline-track")
        assertFalse("the controls have no state cell", exists("player-state"))
        assertEquals("the bar extends left into the cell and its gap", banner.left - controls.left, cell.width + px(8.dp), 1f)
        assertEquals("its end stays", banner.right, controls.right, 0.5f)

        compose.runOnIdle { mode = PlayerChromeMode.BANNER }
        settle()
        assertEquals("back in the Banner the cell's room returns", banner.left, bounds("player-timeline-track").left, 0.5f)
        assertEquals(banner.right, bounds("player-timeline-track").right, 0.5f)
        assertTrue(exists("player-state"))
    }

    @Test fun theBarMovesIntoTheStateCellsRoomWithTheHandoverAndNotInstantly() {
        var mode by mutableStateOf(PlayerChromeMode.BANNER)
        val timeline = live(window = airingWindow)
        compose.setContent { LiveChrome(Frame("", mode, timeline)) }
        settle()
        val banner = bounds("player-timeline-track").left
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { mode = PlayerChromeMode.CONTROLS }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("the first frame still has the Banner's layout", banner, bounds("player-timeline-track").left, 1f)
        // Two frames pass before the rise starts; then it takes the panel duration.
        compose.mainClock.advanceTimeBy(PlayerMotion.PanelMs / 2L + 50)
        val midway = bounds("player-timeline-track").left
        compose.mainClock.advanceTimeBy(PlayerMotion.PanelMs.toLong() + 200)
        compose.waitForIdle()
        val settled = bounds("player-timeline-track").left
        assertTrue("midway is between the layouts: $settled < $midway < $banner", midway < banner - 0.5f && midway > settled + 0.5f)
    }

    // 3. A zap shows the new channel's schedule from its selection until the window takes over.

    @Test fun aZapShowsTheNewChannelsProgrammeInTheBannerFromSelectionToWindow() = assertZap(banner = true, epg = true, timeshift = true)

    @Test fun aZapShowsTheNewChannelsProgrammeInTheControlsFromSelectionToWindow() = assertZap(banner = false, epg = true, timeshift = true)

    @Test fun aZapToAChannelWithoutGuideShowsTheSlotInTheBanner() = assertZap(banner = true, epg = false, timeshift = true)

    @Test fun aZapToAChannelWithoutGuideShowsTheSlotInTheControls() = assertZap(banner = false, epg = false, timeshift = true)

    @Test fun aZapToAChannelWithoutTimeshiftKeepsTheScheduleInTheBanner() = assertZap(banner = true, epg = true, timeshift = false)

    @Test fun aZapToAChannelWithoutTimeshiftKeepsTheScheduleInTheControls() = assertZap(banner = false, epg = true, timeshift = false)

    /**
     * Presentation sequence (liveStart is supplied, not driven by the screen): the previous channel's programme window, then the new channel's
     * identity with `tuning`, the masked unavailable state before the tune is confirmed, the confirmed
     * start with unknown timing, SDK samples with known timing and a playback target but no window yet,
     * and finally the window. From the selection on, every frame shows the new channel's schedule at
     * now (its programme, or without one the half-hour slot), never empty, never the previous fill and
     * without sliding into it; the window takes over at the same fill. The info bar's programme and Next
     * line come from the screen's rule ([displayedProgrammeEvent], [displayedNextEvent]) for the same
     * state and agree with the bar in every frame: the new channel's programme, or without a guide
     * "Programme information unavailable" (the slot is never programme information), never the previous.
     */
    private fun assertZap(banner: Boolean, epg: Boolean, timeshift: Boolean) {
        val layout = if (banner) "Banner" else "controls"
        val mode = if (banner) PlayerChromeMode.BANNER else PlayerChromeMode.CONTROLS
        val zapNow = 2_700L
        // 75% into the new channel's programme, or halfway through the 00:30–01:00 slot.
        val newAiring = event(77L, zapNow - 2_700, zapNow + 900).takeIf { epg }
        val expected = if (epg) 0.75f else 0.5f
        val endClock = formatClock(newAiring?.stop?.epochSeconds ?: 3_600L)
        val masked = AppTimeshiftState()
        val sampled = sdkSampleAtLive().takeIf { timeshift } ?: AppTimeshiftState(available = false)
        val newWindow = if (epg) ProgrammeWindow(newAiring, Instant.fromEpochSeconds(zapNow), expected, 0.7f, expected, expected, true)
            else ProgrammeWindow(null, Instant.fromEpochSeconds(zapNow), expected, 0.4f, expected, expected, true,
                Instant.fromEpochSeconds(1_800), Instant.fromEpochSeconds(3_600))
        val newNext = event(78L, zapNow + 900, zapNow + 4_500).takeIf { epg }
        val unavailable = "Programme information unavailable"
        fun info(timeline: PlayerChromeTimeline.Live, channel: String, next: EpgEvent?) = liveInfoBarData(
            if (channel == "One") 1 else 2, channel,
            displayedProgrammeEvent(false, timeline.committedWindow, timeline.committedWindow, timeline.committedTimeshift,
                timeline.programme, timeline.liveStart),
            displayedNextEvent(false, timeline.committedWindow, timeline.committedTimeshift, next, timeline.liveStart, timeline.programme) { next },
            false, timeline.nowSec, unavailable,
        )
        fun zapped(state: AppTimeshiftState, tuning: Boolean = false, window: ProgrammeWindow? = null) = live(
            state, programme = newAiring, tuning = tuning, fresh = true, window = window, channel = ChannelId(2),
            nowSec = zapNow, timeshiftExpected = timeshift,
        )
        fun frame(name: String, timeline: PlayerChromeTimeline.Live, channel: String = "Two", next: EpgEvent? = newNext) =
            Frame(name, mode, timeline, info = info(timeline, channel, next))
        val frames = listOfNotNull(
            frame("previous channel", live(buffered, window = airingWindow.copy(liveFraction = null), nowSec = zapNow),
                channel = "One", next = event(43L, now + 1_800, now + 3_600)),
            frame("selected, tuning", zapped(masked, tuning = true)),
            frame("masked unavailable", zapped(masked)),
            frame("confirmed, timing unknown", zapped(sampled.copy(timingKnown = false))).takeIf { timeshift },
            frame("timing known with a target, no window", zapped(sampled)),
            frame("window", zapped(sampled, window = newWindow)).takeIf { timeshift },
        )
        var index by mutableStateOf(0)
        compose.setContent { LiveChrome(frames[index]) }
        settle()
        assertEquals("$layout: the previous channel's fill", 0.5f, fillFraction()!!, 0.02f)
        assertTrue("$layout: the previous channel's programme", infoDescription().contains("${airing.title}"))
        compose.mainClock.autoAdvance = false
        for (at in 1 until frames.size) {
            compose.runOnIdle { index = at }
            compose.waitForIdle()
            val fractions = (0 until 30).map {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                fillFraction()
            }
            val name = "$layout '${frames[at].name}'"
            assertTrue("$name has a fill in every frame: $fractions", fractions.all { it != null })
            assertTrue("$name shows the new channel's schedule at now in every frame, never the previous " +
                "fill or a slide: $fractions", fractions.all { kotlin.math.abs(it!! - expected) < 0.02f })
            assertEquals("$name: the start clock", formatClock(newAiring?.start?.epochSeconds ?: 1_800L), leadingText())
            assertEquals("$name: the end clock", endClock, text("player-end-clock"))
            assertNull("$name: at the live edge no distance is drawn", distance())
            assertEquals("$name: the state lands at live", "Live", liveState())
            val timeline = frames[at].timeline as PlayerChromeTimeline.Live
            val barEvent = timeline.scheduleProgress()?.event ?: timeline.committedWindow?.event
            assertEquals("$name: the bar shows the new channel's programme", newAiring, barEvent)
            val info = infoDescription()
            assertTrue("$name: the info bar agrees with the bar: $info", info.contains("${barEvent?.title ?: unavailable}"))
            assertFalse("$name: never the previous channel's programme: $info", info.contains("${airing.title}"))
            if (newNext != null) assertTrue("$name: the new channel's Next line: $info", info.contains("${newNext.title}"))
        }
    }

    /** A real SDK sample at the live edge: known timing, a playback target and a mapping. */
    private fun sdkSampleAtLive(nowSec: Long = 2_700, start: Int = 50): AppTimeshiftState {
        val fixture = at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture(120.minutes)
        fixture.updateHistory(start.minutes, 90.minutes, estimatedLiveEdgeTime = Instant.fromEpochSeconds(nowSec))
        val state = with(fixture) { state.value.toAppPresentation(playbackPosition(90.minutes)) }
        assertTrue(state.available && state.timingKnown)
        assertNotNull("a real sample carries its playback target", state.playbackTarget)
        return state
    }

    @Test fun timingUnavailableAfterALiveStartNeverFadesTheScheduleInfo() {
        val unknown = buffered.copy(timingKnown = false, playbackTarget = null)
        compose.setContent { LiveChrome(Frame("", PlayerChromeMode.CONTROLS, live(unknown, fresh = true))) }
        compose.mainClock.autoAdvance = false
        repeat(5) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
        val titleBeforeNotice = titlePixels()
        // Cross the 1.5 s delay and finish the subsequent alpha animation too.
        settle()
        assertTrue("the title is actually painted, not merely in semantics", brightest("player-info-title") > 0.5f)
        org.junit.Assert.assertArrayEquals("the same title remains painted after the notice delay", titleBeforeNotice, titlePixels())
        assertFalse("the automatic notice must not replace the programme", exists("player-window-title"))
    }

    @Test fun aTimingGapHoldsTheAxisFillClocksAndDistanceAcrossLayers() {
        val behind = buffered.copy(positionMs = -30_000, paused = true)
        var gap by mutableStateOf(false)
        var mode by mutableStateOf(PlayerChromeMode.BANNER)
        compose.setContent { LiveChrome(Frame("", mode,
            if (gap) live(behind.copy(timingKnown = false, playbackTarget = null)) else live(behind, window = airingWindow),
            PlayerStateCell.PAUSED)) }
        settle()
        val fill = fillFraction()!!
        val start = leadingText()
        val end = text("player-end-clock")
        compose.runOnIdle { gap = true }
        for (layer in listOf(PlayerChromeMode.BANNER, PlayerChromeMode.CONTROLS, PlayerChromeMode.BANNER)) {
            compose.runOnIdle { mode = layer }
            settle()
            assertEquals("$layer holds fill", fill, fillFraction()!!, 0.002f)
            assertEquals(start, leadingText())
            assertEquals(end, text("player-end-clock"))
            assertEquals("0:30 behind live", text("player-distance"))
            if (exists("player-seekbar")) assertFalse("held rendering must not grant seeking",
                compose.onNodeWithTag("player-seekbar").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
        }
    }

    @Test fun aNewLiveRequestOnTheSameChannelDoesNotInheritAHeldRow() {
        val behind = buffered.copy(positionMs = -30_000, paused = true)
        var gap by mutableStateOf(false)
        var request by mutableStateOf(1L)
        var mode by mutableStateOf(PlayerChromeMode.BANNER)
        compose.setContent { LiveChrome(Frame("", mode,
            (if (gap) live(behind.copy(timingKnown = false, playbackTarget = null))
             else live(behind, window = airingWindow)).copy(motionKey = ChannelId(1) to request),
            PlayerStateCell.PAUSED)) }
        settle()
        compose.runOnIdle { gap = true }
        settle()
        assertNotNull("the same request holds its coordinates", fillFraction())
        assertEquals("0:30 behind live", text("player-distance"))
        compose.runOnIdle { request++ }
        for (layer in listOf(PlayerChromeMode.BANNER, PlayerChromeMode.CONTROLS)) {
            compose.runOnIdle { mode = layer }
            settle()
            assertTrue(exists("player-timeline-track"))
            assertNull("a new request has no held coordinates", fillFraction())
            assertNull("an unknown distance draws nothing", distance())
            assertNull("and claims no state", liveState())
            assertEquals("", leadingText())
            assertFalse(exists("player-end-clock"))
        }
    }

    @Test fun unknownTimingWithNoHeldRowKeepsAnEmptyTrackAndClaimsNoState() {
        compose.setContent { LiveChrome(Frame("", PlayerChromeMode.CONTROLS,
            live(AppTimeshiftState(available = true, paused = true, timingKnown = false), programme = null), PlayerStateCell.PAUSED)) }
        settle()
        assertTrue(exists("player-timeline-track"))
        assertNull("an unknown distance draws nothing", distance())
        assertNull("and claims no state", liveState())
        assertFalse("no seek actions", compose.onNodeWithTag("player-seekbar").fetchSemanticsNode().config
            .contains(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
    }

    @Test fun expectedTimeshiftWithNoSampleOrHeldRowDoesNotClaimLive() {
        compose.setContent { LiveChrome(Frame("", PlayerChromeMode.BANNER,
            live(AppTimeshiftState(), programme = null).copy(timeshiftExpected = true), PlayerStateCell.PAUSED)) }
        settle()
        assertTrue(exists("player-timeline-track"))
        assertNull("an unknown distance draws nothing", distance())
        assertNull("and does not claim live", liveState())
    }

    @Test fun eachLayerSpeaksEachClockOnce() {
        var mode by mutableStateOf(PlayerChromeMode.BANNER)
        compose.setContent { LiveChrome(Frame("", mode, live(window = airingWindow))) }
        for (layer in listOf(PlayerChromeMode.BANNER, PlayerChromeMode.CONTROLS)) {
            compose.runOnIdle { mode = layer }
            settle()
            val spoken = compose.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(hasTestTag("player-timeline-labels")))
                .fetchSemanticsNodes().joinToString(" ") { node ->
                    node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString(" ") + " " +
                        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text }
                }
            for (clock in programmeWindowClockLabels(airingWindow).toList()) {
                assertEquals("$layer speaks $clock once: $spoken", 1, Regex(Regex.escape(clock)).findAll(spoken).count())
            }
        }
    }

    private var rootView: android.view.View? = null

    private fun titlePixels(): IntArray {
        val rect = bounds("player-info-title")
        val view = requireNotNull(rootView)
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
        val width = rect.width.toInt()
        val pixels = IntArray(width * rect.height.toInt())
        bitmap.getPixels(pixels, 0, width, rect.left.toInt(), rect.top.toInt(), width, rect.height.toInt())
        bitmap.recycle()
        return pixels
    }

    /**
     * The brightest drawn pixel within [tag]'s bounds, 0 (nothing drawn) to 1 (opaque white), outside
     * the readout chip [exceptChip]: what the chip draws over these bounds is not this node's.
     */
    private fun brightest(tag: String, exceptChip: Rect? = null): Float =
        // The chip's tag sits inside its 8dp padding; a pixel it only partly covers is still its own.
        probe(bounds(tag), exceptChip?.inflate(px(8.dp) + 1f)) { r, g, b -> maxOf(r, g, b) }

    /**
     * The most coloured drawn pixel within [rect] of the root, 0 (black to white, such as the footer
     * and the readout's chip) to 1: the distance's orange is the only colour above the bar row.
     */
    private fun mostColoured(rect: Rect): Float = probe(rect) { r, g, b -> maxOf(r, g, b) - minOf(r, g, b) }

    /** The largest [value] of a drawn pixel's red, green and blue (0 to 255) within [rect], scaled by its alpha to 0..1. */
    private fun probe(rect: Rect, except: Rect? = null, value: (Int, Int, Int) -> Int): Float {
        val view = requireNotNull(rootView)
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
        var brightest = 0f
        for (x in rect.left.toInt().coerceAtLeast(0) until rect.right.toInt().coerceAtMost(bitmap.width))
            for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt().coerceAtMost(bitmap.height)) {
                if (except != null && except.contains(androidx.compose.ui.geometry.Offset(x + 0.5f, y + 0.5f))) continue
                val c = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(c) / 255f
                brightest = maxOf(brightest, value(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c)) / 255f * alpha)
            }
        bitmap.recycle()
        return brightest
    }

    /** The info bar's spoken description: its texts clear their own semantics and speak through it. */
    private fun infoDescription() = compose.onNodeWithTag("player-info-bar").fetchSemanticsNode()
        .config[SemanticsProperties.ContentDescription].joinToString(". ")

    /** Live's drawn distance behind live; null where nothing is drawn. */
    private fun distance(): String? = if (exists("player-distance")) text("player-distance") else null

    /** The state live TV announces from its distance box; null where there is no box or it is silent. */
    private fun liveState(): String? = if (!exists("player-live-state")) null else
        compose.onNodeWithTag("player-live-state", useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString { it.text }

    /** The start label: the first text in the bar row's start box, before the track. */
    private fun leadingText(): String {
        val track = bounds("player-timeline-track")
        val row = bounds("player-timeline-labels")
        return compose.onAllNodes(androidx.compose.ui.test.hasText(":", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.right <= track.left + 1f && it.boundsInRoot.center.y in row.top..row.bottom }
            .joinToString { it.config[SemanticsProperties.Text].joinToString { t -> t.text } }
    }

    /** How far the drawn fill reaches along the bar: the furthest end of the elapsed and available fills. */
    private fun fillFraction(): Float? {
        val fills = listOf("player-timeline-fill", "player-timeline-interactive-fill").flatMap { tag ->
            compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
        }
        if (fills.isEmpty()) return null
        val track = bounds("player-timeline-track")
        return (fills.maxOf { it.boundsInRoot.right } - track.left) / track.width
    }

    // 4. The centred ring never replaces playback glyphs or changes chrome bounds.

    @Test fun thePauseButtonKeepsItsIconAndBoundsWhileTheVideoIsBusy() {
        var busy by mutableStateOf<PlayerBusyStatus?>(null)
        var cell by mutableStateOf(PlayerStateCell.PLAYING)
        compose.setContent { LiveChrome(Frame("", PlayerChromeMode.CONTROLS, live(window = airingWindow), cell, busy = busy)) }
        settle()
        val tags = listOf("player-timeline-track", "player-info-title", "player-info-last-row", "player-pause", "player-stop", "player-settings", "player-identity-card")
        val before = tags.associateWith(::bounds)
        val focusedBefore = compose.onNode(isFocused()).fetchSemanticsNode().id
        for (next in listOf(PlayerBusyStatus.TUNING, PlayerBusyStatus.BUFFERING, null)) {
            compose.runOnIdle { busy = next }
            settle()
            assertEquals(next != null, exists("player-busy-indicator"))
            assertFalse("no ring in Pause", exists("player-pause-busy"))
            val pause = compose.onNodeWithTag("player-pause").fetchSemanticsNode().config
            assertFalse("no busy announcement", pause.contains(SemanticsProperties.StateDescription))
            assertFalse("no busy live region", pause.contains(SemanticsProperties.LiveRegion))
            assertTrue("still focusable", pause.contains(SemanticsProperties.Focused))
            compose.onNodeWithTag("player-pause").assertContentDescriptionIs("Pause")
            assertEquals("busy never moves focus", focusedBefore, compose.onNode(isFocused()).fetchSemanticsNode().id)
            tags.forEach { assertEquals("$next: $it does not move", before[it], bounds(it)) }
        }
        compose.runOnIdle { cell = PlayerStateCell.PAUSED }
        settle()
        compose.onNodeWithTag("player-pause").assertContentDescriptionIs("Play")
        assertFalse(exists("player-pause-busy"))
        assertFalse(compose.onNodeWithTag("player-pause").fetchSemanticsNode().config.contains(SemanticsProperties.StateDescription))
    }

    @Test fun theBannerKeepsItsPlaybackGlyphAndBoundsWhileTheVideoIsBusy() {
        var busy by mutableStateOf<PlayerBusyStatus?>(null)
        compose.setContent { LiveChrome(Frame("", PlayerChromeMode.BANNER, live(window = airingWindow), busy = busy)) }
        settle()
        val tags = listOf("player-timeline-track", "player-info-title", "player-info-last-row", "player-state")
        val before = tags.associateWith(::bounds)
        for (next in listOf(PlayerBusyStatus.TUNING, PlayerBusyStatus.BUFFERING, null)) {
            compose.runOnIdle { busy = next }
            settle()
            assertEquals(next != null, exists("player-busy-indicator"))
            compose.onNodeWithTag("player-state", useUnmergedTree = true).assertContentDescriptionIs("Playing")
            assertFalse(exists("player-pause"))
            assertFalse(exists("player-pause-busy"))
            tags.forEach { assertEquals("$next: $it does not move", before[it], bounds(it)) }
        }
    }

    // 5. The step readout: clear above the drawn thumb in the controls; in the Banner, which draws no
    // thumb, on the bar row's top as before, with the info bar kept at full alpha.

    @Test fun theLiveBannerStepReadoutRestsOnTheBarWithTheInfoShown() = assertLiveReadout(banner = true)

    @Test fun theLiveControlsStepReadoutClearsTheThumbAndTheInfoText() = assertLiveReadout(banner = false)

    @Test fun aRecordingsBannerStepReadoutRestsOnTheBarWithTheInfoShown() = assertRecordingReadout(banner = true)

    @Test fun aRecordingsControlsStepReadoutClearsTheThumbAndTheInfoText() = assertRecordingReadout(banner = false)

    private fun assertLiveReadout(banner: Boolean) {
        var scale by mutableStateOf(1f)
        var mode by mutableStateOf(if (banner) PlayerChromeMode.BANNER else PlayerChromeMode.CONTROLS)
        val step = live(buffered.copy(positionMs = -30_000), committed = buffered, window = airingWindow,
            previewing = true, step = TimeshiftSeekDecision(-30_000, -30_000, false).takeIf { banner })
        compose.setContent {
            Scaled(scale) { LiveChrome(Frame("", mode, step)) }
        }
        settle()
        if (!banner) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            settle()
        }
        for (fontScale in listOf(1f, 1.3f)) {
            compose.runOnIdle { scale = fontScale }
            settle()
            val name = "live ${if (banner) "Banner" else "controls"} at font $fontScale"
            if (banner) assertBannerReadout(name) { mode = it } else assertReadoutClear(name)
        }
    }

    private fun assertRecordingReadout(banner: Boolean) {
        var scale by mutableStateOf(1f)
        var mode by mutableStateOf(if (banner) PlayerChromeMode.BANNER else PlayerChromeMode.CONTROLS)
        compose.setContent {
            Scaled(scale) {
                TVHeadendPlayerTheme {
                    RecordingChromeFixture(mode = mode, targetMs = 1_830_000, originMs = 1_800_000)
                }
            }
        }
        settle()
        if (!banner) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            settle()
        }
        for (fontScale in listOf(1f, 1.3f)) {
            compose.runOnIdle { scale = fontScale }
            settle()
            val name = "recording ${if (banner) "Banner" else "controls"} at font $fontScale"
            if (banner) assertBannerReadout(name) { mode = it } else assertReadoutClear(name)
        }
    }

    private val infoTextTags = listOf("player-info-title", "player-info-eyebrow", "player-info-last-text")

    /**
     * The Banner draws no thumb, so its step readout keeps the place it had at 5e096f0: the chip's bottom
     * on the bar row's top, centred on its target within the bar. The info bar stays at full alpha: every
     * info text line is as bright during the step as in the resting Banner.
     */
    private fun assertBannerReadout(name: String, setMode: (PlayerChromeMode) -> Unit) {
        compose.runOnIdle { setMode(PlayerChromeMode.BANNER) }
        settle()
        val resting = infoTextTags.filter(::exists).associateWith(::brightest)
        assertTrue("$name: the info bar has text", resting.isNotEmpty() && resting.values.all { it > 0.5f })
        compose.runOnIdle { setMode(PlayerChromeMode.BANNER_STEP) }
        settle()
        val chip = bounds("timeshift-preview-target")
        val track = bounds("player-timeline-track")
        assertFalse("$name: the Banner draws no thumb", exists("player-seekbar-thumb"))
        assertEquals("$name: the chip's bottom is on the bar row's top", track.top, chip.bottom, 1f)
        assertTrue("$name: clamped to the bar", chip.left >= track.left - 1f && chip.right <= track.right + 1f)
        for ((tag, before) in resting) {
            assertTrue("$name: $tag stays shown", exists(tag))
            assertEquals("$name: $tag keeps full alpha", before, brightest(tag, exceptChip = chip), 0.05f)
        }
    }

    /**
     * The readout chip's bottom is [ReadoutThumbGap] (at least 8dp) above the focused controls' drawn
     * thumb; it is centred on its target within the bar. The info bar ends 8dp above the bar row, so
     * the chip reaches into its bottom lines' band: every info text line in that band is faded out (no
     * visible pixel), none is overlapped while shown.
     */
    private fun assertReadoutClear(name: String) {
        val chip = bounds("timeshift-preview-target")
        val track = bounds("player-timeline-track")
        val thumbTop = bounds("player-seekbar-thumb").top
        assertTrue("$name: 8dp is the gap", ReadoutThumbGap >= 8.dp)
        assertEquals("$name: the chip's bottom is the gap above the thumb's top", thumbTop - px(ReadoutThumbGap), chip.bottom, 1.5f)
        assertTrue("$name: centred within the bar", chip.center.x >= track.left - 0.5f && chip.center.x <= track.right + 0.5f)
        assertTrue("$name: clamped to the bar", chip.left >= track.left - 1f && chip.right <= track.right + 1f)
        val texts = infoTextTags.filter(::exists).map { it to bounds(it) }
        assertTrue("$name: the info bar has text above", texts.isNotEmpty())
        assertTrue("$name: the pixel probe sees the chip's own text", brightest("timeshift-preview-target") > 0.5f)
        for ((tag, text) in texts) {
            if (chip.top >= text.bottom - 0.5f) continue
            assertEquals("$name: $tag in the chip's band (chip top ${chip.top}, text bottom ${text.bottom}) is not visible",
                0f, brightest(tag, exceptChip = chip), 0.1f)
        }
    }

    // 5b. The readout's chip over the distance: the distance is not drawn at all, decided by geometry.

    @Test fun theBannerDrawsNoDistanceWhileTheStepReadoutLiesOverIt() = assertReadoutCoversDistance(banner = true)

    @Test fun theControlsDrawNoDistanceWhileTheStepReadoutLiesOverIt() = assertReadoutCoversDistance(banner = false)

    /**
     * Over an hour behind live the distance is wider than the chip reaches. A step previewing near the
     * bar's right end puts the chip over part of it: no pixel of the distance is painted beside the
     * chip. The same step previewing far from the end leaves it painted. Its node, its box and the
     * state the box announces are the same in both, and the bar does not move.
     */
    private fun assertReadoutCoversDistance(banner: Boolean) {
        val layer = if (banner) "Banner" else "controls"
        val deep = buffered.copy(bufferStartMs = -7_200_000)
        val committed = deep.copy(positionMs = -3_730_000)
        val target = deep.copy(positionMs = -3_700_000)
        var near by mutableStateOf(false)
        compose.setContent {
            LiveChrome(Frame("", if (banner) PlayerChromeMode.BANNER_STEP else PlayerChromeMode.CONTROLS, live(
                target, committed = committed, window = airingWindow.copy(positionFraction = 0.98f),
                shown = airingWindow.copy(positionFraction = if (near) 0.985f else 0.3f, availableStartFraction = 0f, availableEndFraction = 1f),
                previewing = true, step = TimeshiftSeekDecision(-3_700_000, 30_000, false).takeIf { banner })))
        }
        settle()
        if (!banner) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            settle()
        }
        val clearChip = bounds("timeshift-preview-target")
        val text = bounds("player-distance")
        val box = bounds("player-live-state")
        val track = bounds("player-timeline-track")
        assertEquals("1:02:10 behind live", distance())
        assertFalse("$layer: far from the end the chip is clear of the distance: $clearChip vs $text", clearChip.overlaps(text))
        assertTrue("$layer: clear of the chip, the distance is painted in its colour", mostColoured(text) > 0.4f)
        assertEquals("Behind live", liveState())

        compose.runOnIdle { near = true }
        settle()
        val chip = bounds("timeshift-preview-target")
        assertTrue("$layer: near the end the chip lies over the distance: $chip vs $text", chip.overlaps(text))
        assertTrue("$layer: and leaves part of it beside the chip: $chip vs $text", text.right > chip.right + px(8.dp))
        assertEquals("$layer: the distance's text keeps its place", text, bounds("player-distance"))
        assertEquals("$layer: no pixel of the distance is painted beside the chip", 0f,
            mostColoured(Rect(chip.right, text.top, text.right, text.bottom)), 0.05f)
        assertEquals("$layer: nor anywhere else in its place", 0f, mostColoured(text), 0.05f)
        assertEquals("$layer: the state's box keeps its bounds", box, bounds("player-live-state"))
        assertEquals("$layer: and its announcement", "Behind live", liveState())
        assertEquals("$layer: the distance stays in the tree", "1:02:10 behind live", distance())
        assertEquals("$layer: the bar does not move", track, bounds("player-timeline-track"))

        compose.runOnIdle { near = false }
        settle()
        assertEquals("$layer: the chip is back where it was", clearChip, bounds("timeshift-preview-target"))
        assertTrue("$layer: clear of the chip again, the distance is painted again", mostColoured(text) > 0.4f)
    }

    // 6. Widths, measured once per kind.

    @Test fun theLabelBoxesHaveWidthsMeasuredPerKindFromTheWidestText() {
        var live: TimelineEndpointWidths? = null
        var recording: TimelineEndpointWidths? = null
        var large: TimelineEndpointWidths? = null
        compose.setContent {
            TVHeadendPlayerTheme {
                live = rememberTimelineEndpointWidths(TimelineKind.LIVE)
                recording = rememberTimelineEndpointWidths(TimelineKind.RECORDING)
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    large = rememberTimelineEndpointWidths(TimelineKind.LIVE)
                }
            }
        }
        compose.waitForIdle()
        println("timeline label boxes at font 1.0: live start=${live!!.leading} end=${live!!.trailing} distance=${live!!.distance}; " +
            "recording start=${recording!!.leading} end=${recording!!.trailing}; " +
            "live at 1.3: start=${large!!.leading} end=${large!!.trailing} distance=${large!!.distance}")
        assertEquals("live's end clock box mirrors its start clock box", live!!.leading, live!!.trailing)
        assertEquals("a recording's length box mirrors its position box, with no room for a mark", recording!!.leading, recording!!.trailing)
        assertTrue("the distance has room for its wording", live!!.distance > live!!.trailing)
        assertEquals("growing recordings reserve the same behind-live label", live!!.distance, recording!!.distance)
        assertTrue("a recording's labels are the wider ones", recording!!.leading > live!!.leading && recording!!.trailing > live!!.trailing)
        assertTrue("the boxes grow with the font", large!!.leading > live!!.leading && large!!.trailing > live!!.trailing && large!!.distance > live!!.distance)
    }

    // 7. The action row fades in inside its focus-expanded buffer, so nothing clips the focused control.

    @Test fun theActionRowsRevealFadeIsTheOneLayerWithTheExpandedFocusBuffer() {
        var mode by mutableStateOf(PlayerChromeMode.BANNER)
        compose.setContent { LiveChrome(Frame("", mode, live(window = airingWindow))) }
        settle()
        compose.runOnIdle { mode = PlayerChromeMode.CONTROLS }
        compose.waitForIdle()
        // Mid-reveal: the handover has started and the actions are still fading in.
        compose.mainClock.advanceTimeBy(PlayerMotion.FastMs + PlayerMotion.ShortMs / 2L)
        compose.waitForIdle()
        compose.onNodeWithTag("player-pause").assertIsFocused()
        assertActionAlphaInsideTheExpandedBuffer("mid-reveal")
        settle()
        assertActionAlphaInsideTheExpandedBuffer("settled")
    }

    /**
     * Alpha below 1 renders into an offscreen buffer the size of its layer. The action row has exactly
     * one graphics layer, and that layer is 8dp larger than the row on every side: the reveal alpha and
     * the emphasis alpha share the buffer that holds the focused button's scale and glow.
     */
    private fun assertActionAlphaInsideTheExpandedBuffer(name: String) {
        val node = compose.onNodeWithTag("player-actions").fetchSemanticsNode()
        val layers = node.layoutInfo.getModifierInfo().filter { it.modifier.javaClass.simpleName.contains("GraphicsLayer") }
        assertEquals("$name: one alpha layer on the action row: ${layers.map { it.modifier }}", 1, layers.size)
        val buffer = layers.single().coordinates.size
        val row = node.size
        assertEquals("$name: the layer's buffer is 8dp wider on each side", row.width + 2 * px(8.dp), buffer.width.toFloat(), 1f)
        assertEquals("$name: and 8dp taller on each side", row.height + 2 * px(8.dp), buffer.height.toFloat(), 1f)
    }

    // Harness.

    @androidx.compose.runtime.Composable
    private fun Scaled(fontScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        rootView = androidx.compose.ui.platform.LocalView.current
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) { content() }
    }

    @androidx.compose.runtime.Composable
    private fun LiveChrome(frame: Frame) {
        rootView = androidx.compose.ui.platform.LocalView.current
        val context = LocalContext.current
        TVHeadendPlayerTheme {
            Box(Modifier.fillMaxSize()) {
            PlayerChrome(
                mode = frame.mode,
                content = PlayerChromeContent("20:15", frame.info ?: liveInfoBarData(1, "One", airing, airing, false, now, "Programme"),
                    state = frame.cell),
                timeline = frame.timeline,
                actions = PlayerChromeActions(active = frame.mode == PlayerChromeMode.CONTROLS, paused = frame.cell == PlayerStateCell.PAUSED),
                imageLoader = remember { ImageLoader(context) },
                currentSession = null,
                onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
            )
            PlayerBusyIndicator(frame.busy, Modifier.align(Alignment.Center))
            }
        }
    }

    private fun settle() {
        compose.mainClock.autoAdvance = false
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun px(value: androidx.compose.ui.unit.Dp) = with(compose.density) { value.toPx() }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertContentDescriptionIs(expected: String) {
        assertEquals(listOf(expected), fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
    }
}
