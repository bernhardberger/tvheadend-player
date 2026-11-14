package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.di.PlayerStatsLevel
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackFormatDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackSource
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import androidx.compose.foundation.layout.offset
import at.bernhardberger.tvhplayer.testutil.settleFrames
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

import android.app.Application
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import at.bernhardberger.tvhplayer.core.deviceFaultDetail
import at.bernhardberger.tvhplayer.R
import androidx.compose.ui.res.stringResource
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession

/**
 * Design-review captures of the player header, the controls-to-panel handover and zap
 * transitions: 960×540 px, density 1.0 (mdpi),
 * locale en, font scale 1.0, textured backdrop, controls focus as composed.
 * Production composables with offline fake state; written to `captures/ui-motion/` and
 * `captures/ui-motion-zap/`
 * (ignored). Readability over motion and overscan remain physical-TV gates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerMotionCaptureTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var defaultZone: TimeZone? = null

    @Before fun pinClock() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreClock() {
        defaultZone?.let(TimeZone::setDefault)
    }

    @Test fun headerLive() {
        show { session -> live(session, behind = false) }
        capture("header-live")
    }

    @Test fun statsStreamPathStates() {
        var diagnostics by mutableStateOf(statsLiveDiagnostics())
        var level by mutableStateOf(PlayerStatsLevel.STREAM_PATH)
        var scene by mutableStateOf("dvb-healthy")
        var window by mutableStateOf(StatsWindow())
        var raster by mutableStateOf<String?>("1080i")
        // As on both screens: under the controls or the Banner the debug level draws the stream path card.
        val lowerChrome = setOf("controls", "banner")
        lateinit var layers: LivePlayerLayerState
        show { session ->
            layers = rememberLivePlayerLayerState()
            Box(Modifier.fillMaxSize()) {
                val failed = diagnostics.state as? AppPlaybackState.Failed
                val noSignal = scene == "live-no-signal"
                // As on the live screen: the channel message carries the Stats text instead of the card, and the
                // channel drawer's browse surface takes the card's place.
                if (failed == null && !noSignal && scene != "channel-drawer") PlaybackStatsOverlay(diagnostics, AspectRatioMode.FIT,
                    Modifier.offset(58.dp, 100.dp), level = presentedStatsLevel(level, scene in lowerChrome), accessible = scene !in setOf("options", "controls"),
                    raster = raster)
                if (scene == "controls") live(session, false)
                if (scene == "banner") live(session, false, mode = PlayerChromeMode.BANNER)
                if (scene == "channel-drawer") live(session, false, rail = layers)
                if (failed != null) PlayerChannelMessage(true, AppErrorCondition.CHANNEL_PLAYBACK_FAILED,
                    Modifier.align(Alignment.Center), deviceFaultDetail = failed.deviceFaultDetail(), statsFailure = failed)
                if (noSignal) PlayerChannelMessage(true,
                    at.bernhardberger.tvhplayer.core.liveErrorCondition(at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue.BAD_SIGNAL),
                    Modifier.align(Alignment.Center), statsRows = { liveStatsRows(diagnostics, window, level) })
                if (scene == "options") PlaybackOptionsSheetContent(
                    PlaybackOptionsPage.STATS, emptyList(), emptyList(), false, AspectRatioMode.FIT, level,
                    {}, {}, {}, {}, { level = it })
            }
        }
        val report = StringBuilder("canvas=960x540 dp; locale=en; fontScale=1.0; fixture=still-ridge-light\n")
        // Every stream-path row at its widest, written by the step-value formatter from extreme inputs.
        val worstRows = run {
            val full = 65_535L
            val dvb = statsLiveDiagnostics(signal = full, snr = full, queueDelayUs = 9_999_000).copy(bufferedMs = 30_000)
            fun video(codec: String, mime: String, width: Int, height: Int) = dvb.copy(video = AppPlaybackFormatDiagnostics(
                codec, "$width×$height", 23.976f, sampleMimeType = mime, width = width, height = height))
            fun row(step: StreamPathStep, d: AppPlaybackDiagnostics = dvb, w: StatsWindow = StatsWindow(),
                output: DisplayOutput? = null, requested: String? = null, declared: String = "23.98",
                timeshift: AppTimeshiftState? = null, raster: String? = null) =
                step to stepValue(step, d, w, output, requested, measuredRate = "59.94", declaredRate = declared, timeshift, raster)
            val locked = dvb
            val unlocked = statsLiveDiagnostics(locked = false, signal = full, snr = full, queueDelayUs = 9_999_000)
                .copy(bufferedMs = 30_000)
            val odd = video("hvc1.2.4.L153.B0", "video/hevc", 3840, 1600)
            fun MutableList<Triple<String, StreamPathStep, String>>.add(shape: String, row: Pair<StreamPathStep, String>) =
                add(Triple(shape, row.first, row.second))
            buildList {
                add("tuner", row(StreamPathStep.TUNER, locked)); add("tuner", row(StreamPathStep.TUNER, unlocked))
                add("queue", row(StreamPathStep.SERVER))
                listOf(9_999L, 999_999L, 99_999_999L).forEach { n ->
                    add("growth", row(StreamPathStep.TUNER, locked, StatsWindow(uncorrected = n)))
                    add("growth", row(StreamPathStep.TUNER, unlocked, StatsWindow(uncorrected = n)))
                    add("growth", row(StreamPathStep.SERVER, w = StatsWindow(serverDrops = n)))
                    add("growth", row(StreamPathStep.APP, w = StatsWindow(clientDrops = n)))
                    add("growth", row(StreamPathStep.DECODER, w = StatsWindow(decoderDrops = n)))
                }
                add("timing", row(StreamPathStep.APP, timeshift = AppTimeshiftState(available = true,
                    bufferStartMs = -10_800_000, positionMs = -10_799_000, liveEdgeMs = 0)))
                add("timing", row(StreamPathStep.APP, dvb.copy(source = AppPlaybackSource.RECORDING, live = null,
                    positionMs = 10_799_000, durationMs = 10_800_000)))
                val families = listOf("avc1.640028" to "video/avc", "hvc1.2.4.L153.B0" to "video/hevc",
                    "av01.0.08M.08" to "video/av01", "vp09.00.40.08" to "video/x-vnd.on2.vp9", "mp2v" to "video/mpeg2")
                listOf(Triple(3840, 2160, "2160p"), Triple(1920, 1080, "1080i")).forEach { (width, height, raster) ->
                    families.forEach { (codec, mime) ->
                        add("family + one rate", row(StreamPathStep.DECODER, video(codec, mime, width, height),
                            declared = "59.94", raster = raster))
                    }
                    add("two rates", row(StreamPathStep.DECODER, video("mp2v", "video/mpeg2", width, height), raster = raster))
                }
                add("two rates", row(StreamPathStep.DECODER, odd))
                add("one rate, no family", row(StreamPathStep.DECODER, odd, declared = "59.94"))
                add("growth", row(StreamPathStep.DECODER, odd, StatsWindow(decoderDrops = 99_999_999)))
                add("screen", row(StreamPathStep.SCREEN, output = DisplayOutput(3840, 2160, "23.98", "", null), requested = "59.94"))
                add("screen", row(StreamPathStep.SCREEN, output = DisplayOutput(3840, 2160, "59.94", "", null), requested = "23.98"))
                add("screen", row(StreamPathStep.SCREEN, output = DisplayOutput(3840, 2160, "59.94", "", null), requested = "59.94"))
            }.distinctBy { it.third }
        }
        fun shot(name: String, playing: Boolean = true) {
            compose.settleFrames(2)
            if (scene == "channel-drawer") {
                assertTrue("$name: no Stats card in this scene",
                    compose.onAllNodesWithTag("playback-stats-overlay").fetchSemanticsNodes().isEmpty())
                report.append("$name: card=none\n")
                capture("stats-$name", focus = "drawer's current channel", directory = "stats-stream-path")
                return
            }
            val drawn = presentedStatsLevel(level, scene in lowerChrome)
            if (!playing) {
                // The tag sits inside the message's 24 dp padding; the box is what must stay TV-safe.
                val message = compose.onNodeWithTag(PlayerChannelMessageTag).fetchSemanticsNode().boundsInRoot.inflate(24f)
                report.append("$name: card=none; message=$message\n")
                compose.onAllNodesWithTag(PlayerChannelMessageDetailTag, useUnmergedTree = true).fetchSemanticsNodes()
                    .forEach { report.append("  detail: ${it.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                        .joinToString("").replace("\n", " | ")}\n") }
                assertTrue("$name message=$message", message.left >= TvGridHorizontalMargin.value &&
                    message.right <= 960 - TvGridHorizontalMargin.value && message.top >= TvGridVerticalMargin.value &&
                    message.bottom <= 540 - TvGridVerticalMargin.value)
            } else {
                val bounds = compose.onNodeWithTag("playback-stats-overlay").fetchSemanticsNode().boundsInRoot
                report.append("$name: bounds=$bounds\n")
                if (drawn == PlayerStatsLevel.STREAM_PATH) assertTrue("$name bottom=${bounds.bottom}", bounds.bottom <= 300f)
            }
            val cardInk = mutableListOf<Pair<String, androidx.compose.ui.geometry.Rect>>()
            textInk(underCard = true).forEach { (node, layout) ->
                val input = layout.layoutInput
                // Header and audio lines wrap their content; their room is the card's content width.
                val card = compose.onNodeWithTag("playback-stats-overlay").fetchSemanticsNode().boundsInRoot
                val room = if (node.boundsInRoot.left <= card.left + 16.5f) card.width - 32f else layout.size.width.toFloat()
                fun spare(text: String) = room - androidx.compose.ui.text.ParagraphIntrinsics(text,
                    input.style, emptyList(), density = input.density,
                    fontFamilyResolver = input.fontFamilyResolver).maxIntrinsicWidth
                // Ring numbers and the flag gutter are single characters with their own fixed width.
                if (drawn == PlayerStatsLevel.STREAM_PATH && input.text.length > 1) {
                    report.append("  row: ${input.text}; spare=${spare(input.text.text)} dp\n")
                }
                if (layout.hasVisualOverflow) report.append("ellipsis: ${input.text}\n")
                cardInk += input.text.text to ink(node, layout)
            }
            if (playing && drawn == PlayerStatsLevel.DEBUG) {
                val card = compose.onNodeWithTag("playback-stats-overlay").fetchSemanticsNode().boundsInRoot
                val (left, right) = cardInk.map { it.second }.partition { it.left < card.left + 16f + 324f }
                val leftBottom = left.maxOf { it.bottom }
                val rightBottom = right.maxOf { it.bottom }
                report.append("  left column bottom=$leftBottom; right column bottom=$rightBottom\n")
                if (name == "debug-worst-height") assertTrue("the steps outgrow the details", leftBottom <= rightBottom)
            }
            if (name.endsWith("-debug") && scene in lowerChrome) {
                // Where the chrome's text and artwork draw over the card's text (the chrome is above it).
                val chrome = textInk(underCard = false).map { (node, layout) -> layout.layoutInput.text.text to ink(node, layout) } +
                    // The info bar's own texts clear their semantics; their layout boxes stand in for their ink.
                    listOf("player-info-artwork-tile", "player-info-title", "player-info-subtitle", "player-info-programme",
                        "player-info-last-text").flatMap { tag ->
                        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { tag to it.boundsInRoot }
                    }
                var overlaps = 0
                cardInk.forEach { (cardText, cardRect) ->
                    chrome.filter { it.second.overlaps(cardRect) }.forEach { (chromeText, chromeRect) ->
                        val over = cardRect.intersect(chromeRect)
                        overlaps++
                        report.append("  overlap: \"${chromeText.replace("\n", " | ")}\" $chromeRect over " +
                            "\"${cardText.replace("\n", " | ")}\" $cardRect by ${over.width}×${over.height} dp at $over\n")
                    }
                }
                report.append("  overlaps=$overlaps\n")
                assertEquals("$name: the chrome draws over no card text", 0, overlaps)
            }
            capture("stats-$name", focus = if (scene == "options") "selected Stats radio" else "none",
                directory = "stats-stream-path")
        }
        shot("dvb-healthy")
        // Each worst case in its own step's value text: the text right of the step's name, in its style and the
        // width the row leaves it beside the flag gutter.
        run {
            val texts = textInk(underCard = true)
            val values = StreamPathStep.entries.associateWith { step ->
                val name = texts.single { it.second.layoutInput.text.text == stepName(step) }.first.boundsInRoot
                texts.filter { (node, _) ->
                    node.boundsInRoot.left >= name.right && name.center.y in node.boundsInRoot.top..node.boundsInRoot.bottom
                }.minBy { it.first.boundsInRoot.left }.second
            }
            assertEquals("every step has its worst cases", StreamPathStep.entries.toSet(),
                worstRows.map { it.second }.toSet())
            values.forEach { (step, value) -> report.append("worst-case value room ($step)=${value.size.width} dp\n") }
            val misfits = mutableListOf<String>()
            val smallest = mutableMapOf<String, Pair<String, Float>>()
            worstRows.forEach { (shape, step, row) ->
                val value = values.getValue(step)
                val input = value.layoutInput
                val s = value.size.width - androidx.compose.ui.text.ParagraphIntrinsics(row, input.style, emptyList(),
                    density = input.density, fontFamilyResolver = input.fontFamilyResolver).maxIntrinsicWidth
                report.append("  worst ($shape): $row; spare=$s dp\n")
                if (s < 0f) misfits += "$row overflows by ${-s} dp"
                if (smallest[shape].let { it == null || s < it.second }) smallest[shape] = row to s
            }
            smallest.forEach { (shape, worst) -> report.append("  smallest spare ($shape): ${worst.first}; ${worst.second} dp\n") }
            assertTrue(misfits.joinToString("; "), misfits.isEmpty())
        }
        compose.runOnIdle { diagnostics = diagnostics.copy(video = diagnostics.video?.copy(
            codec = "hvc1.2.4.L153.B0", resolution = "3840×2160", frameRate = 50f, width = 3840, height = 2160)); raster = "2160p" }
        shot("longest-decoder")
        compose.runOnIdle { raster = "1080i" }
        compose.runOnIdle { diagnostics = statsLiveDiagnostics(locked = false, uncorrected = 4, serverDrops = 5) }
        shot("dvb-degraded")
        compose.runOnIdle { diagnostics = statsLiveDiagnostics(tuner = false) }
        shot("iptv")
        compose.runOnIdle { diagnostics = diagnostics.copy(source = AppPlaybackSource.RECORDING, live = null,
            positionMs = 600_000, durationMs = 3_600_000); raster = "1080p" }
        shot("recording")
        compose.runOnIdle { raster = "1080i" }
        compose.runOnIdle { diagnostics = statsLiveDiagnostics(); scene = "controls" }
        shot("controls")
        compose.runOnIdle { scene = "banner" }
        shot("banner")
        compose.runOnIdle { scene = "options" }
        shot("options-stream-path")
        // Degraded DVB with growth, so the debug card shows its flags and "last 60 s": a baseline first.
        compose.runOnIdle { scene = ""; level = PlayerStatsLevel.DEBUG; diagnostics = statsLiveDiagnostics(locked = false) }
        compose.settleFrames(2)
        compose.runOnIdle { diagnostics = statsLiveDiagnostics(locked = false, uncorrected = 4, serverDrops = 5) }
        shot("debug-degraded")
        val longService = "Ridge Earth Documentary Extended Regional Service Original-Language Programme Feed"
        compose.runOnIdle { diagnostics = statsLiveDiagnostics(locked = false, service = longService) }
        compose.settleFrames(2)
        compose.runOnIdle {
            diagnostics = statsLiveDiagnostics(locked = false, uncorrected = 4, serverDrops = 5, service = longService)
        }
        shot("debug-worst-height")
        // Degraded DVB with growth under the chrome: a baseline first, so the counters can grow.
        compose.runOnIdle { diagnostics = statsLiveDiagnostics(locked = false); scene = "controls" }
        compose.settleFrames(2)
        compose.runOnIdle { diagnostics = statsLiveDiagnostics(locked = false, uncorrected = 4, serverDrops = 5) }
        shot("controls-debug")
        compose.runOnIdle { scene = "banner" }
        shot("banner-debug")
        compose.runOnIdle { scene = "channel-drawer"; layers.openChannelDrawer() }
        compose.settleFrames(60)
        assertTrue("the drawer is open", compose.onAllNodesWithTag("player-channel-browser").fetchSemanticsNodes().isNotEmpty())
        shot("channel-drawer-debug")
        compose.runOnIdle { layers.closeChannelDrawer() }
        val longFailure = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER,
            targetResult = at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult.SHUT_DOWN,
            playerErrorCode = "ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES",
            playerErrorClassification = "androidx.media3.exoplayer.ExoPlaybackException · MediaCodecVideoRenderer.onInputFormatChanged",
            recoveryReason = at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED)
        compose.runOnIdle {
            scene = "live-failed"; level = PlayerStatsLevel.STREAM_PATH
            diagnostics = statsLiveDiagnostics().copy(state = longFailure)
        }
        shot("live-failed", playing = false)
        // This device's player: its own error leads and the error code row is not repeated.
        compose.runOnIdle {
            diagnostics = statsLiveDiagnostics().copy(state = longFailure.copy(playerErrorNumber = 4003, deviceFault = true))
        }
        shot("live-failed-device-fault", playing = false)
        // A server-reported issue: the message borrows the Tuner and Tvheadend rows.
        compose.runOnIdle {
            scene = "live-no-signal"
            diagnostics = statsLiveDiagnostics(locked = false, uncorrected = 1_234_567, serverDrops = 5)
        }
        shot("live-no-signal", playing = false)
        // Debug's worst case: every tuner and queue row, with growth and a queue change on the path rows.
        compose.runOnIdle {
            level = PlayerStatsLevel.DEBUG
            window = StatsWindow(uncorrected = 1_234_567, serverDrops = 5, queueChangeUs = -125_000)
        }
        shot("live-no-signal-debug", playing = false)
        CaptureOutput.writeText(File("../captures/stats-stream-path/bounds.txt")) { report.toString() }
    }

    /** Text nodes with their layouts, inside the Stats card or outside it. */
    private fun textInk(underCard: Boolean): List<Pair<androidx.compose.ui.semantics.SemanticsNode, androidx.compose.ui.text.TextLayoutResult>> {
        val inCard = androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("playback-stats-overlay"))
        return compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) and (if (underCard) inCard else !inCard),
            useUnmergedTree = true).fetchSemanticsNodes().flatMap { node ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.map { node to it }
        }
    }

    /** Where a text actually draws: its lines' extent, not its whole layout box (mdpi, so px are dp). */
    private fun ink(node: androidx.compose.ui.semantics.SemanticsNode, layout: androidx.compose.ui.text.TextLayoutResult):
        androidx.compose.ui.geometry.Rect {
        val origin = node.boundsInRoot.topLeft
        val lines = 0 until layout.lineCount
        return androidx.compose.ui.geometry.Rect(origin.x + lines.minOf { layout.getLineLeft(it) }, origin.y,
            origin.x + lines.maxOf { layout.getLineRight(it) }, origin.y + layout.getLineBottom(layout.lineCount - 1))
    }

    @Test fun headerBehindLive() {
        show { session -> live(session, behind = true) }
        capture("header-behind-live")
    }

    @Test fun headerRecording() {
        show { _ ->
            RecordingChromeFixture(growing = true)
        }
        capture("header-recording")
    }

    @Test fun numberEntryTransitions() {
        var number by mutableStateOf("")
        var target by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Pending)
        var settles by mutableStateOf(false)
        show { session ->
            Box(Modifier.fillMaxSize()) {
                ProvideArtworkSession(session) {
                    PlayerTopIdentitySlot { ChannelNumberOverlay(number, target, settles = settles) }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        fun change(digits: String, destination: ChannelNumberTarget, committed: Boolean = false) = compose.runOnIdle {
            number = digits
            target = destination
            settles = committed
            Snapshot.sendApplyNotifications()
        }
        fun frames(name: String, frames: List<Int>) =
            captureFrames(name, "number-entry-motion", frames, focus = "passive; no focus")

        change("1", ChannelNumberTarget.Pending)
        frames("entry", listOf(3, 6, 10))
        change("1", ChannelNumberTarget.Channel("1", "Documentary", ChannelIcon.ImageCache(ArtworkId(1))))
        frames("destination", listOf(3, 6, 9))
        change("12", ChannelNumberTarget.Channel("12", "Arts and Culture", null))
        frames("digit-push", listOf(1, 3, 6))
        change("", ChannelNumberTarget.Pending, committed = true)
        frames("commit", listOf(3, 6, 10))
        change("12", ChannelNumberTarget.Channel("12", "Arts and Culture", null))
        compose.mainClock.advanceTimeBy(1_000)
        change("", ChannelNumberTarget.Pending)
        frames("cancel", listOf(3, 6, 10))
        // A leading zero settles as the channel's own number.
        change("01", ChannelNumberTarget.Channel("1", "Documentary", ChannelIcon.ImageCache(ArtworkId(1))))
        compose.mainClock.advanceTimeBy(1_000)
        change("", ChannelNumberTarget.Pending, committed = true)
        frames("commit-leading-zero", listOf(1, 6, 10))
        change("999", ChannelNumberTarget.None)
        frames("no-channel", listOf(3, 5))
        change("", ChannelNumberTarget.Pending)
        frames("no-channel-dismissal", listOf(3, 5))
    }

    /** Controls hand over to the options panel: frames 3, 6 and 9 after opening, then settled. */
    @Test fun controlsToPanelMidTransition() {
        var open by mutableStateOf(false)
        show { session ->
            Box(Modifier.fillMaxSize()) {
                live(session, behind = false, panelOpen = open)
                PlayerPanelVisibility(Unit.takeIf { open }) {
                    PlaybackOptionsSheetContent(
                        page = PlaybackOptionsPage.ROOT,
                        audioTracks = emptyList(),
                        subtitleTracks = emptyList(),
                        tracksResolving = false,
                        aspectRatio = AspectRatioMode.FIT,
                        statsLevel = PlayerStatsLevel.OFF,
                        onPageChange = {},
                        onAudioTrackSelected = {},
                        onSubtitleTrackSelected = {},
                        onAspectRatioChange = {},
                        onStatsLevelChange = {},
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            open = true
            Snapshot.sendApplyNotifications()
        }
        captureFrames(
            "controls-to-panel", "ui-motion", listOf(3, 6, 9),
            focus = "panel entering; focus requested on its first frame",
            settledFocus = "panel root row focused",
        )
    }

    /** A zap with the controls up: the header crossfades in place (frames 3 and 6). */
    @Test fun zapWithControlsVisibleCrossfadesInPlace() {
        var zapped by mutableStateOf(false)
        show { session ->
            live(session, behind = false, zapped = zapped)
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            zapped = true
            Snapshot.sendApplyNotifications()
        }
        captureFrames("zap-visible", "ui-motion-zap", listOf(3, 6), focus = "initial controls focus (kept across the zap)")
    }

    /** Hidden controls revealed by a zap fade in at their resting place (frames 3 and 6). */
    @Test fun zapRevealsHiddenControlsInPlace() {
        lateinit var layers: LivePlayerLayerState
        var zapped by mutableStateOf(false)
        show { session ->
            layers = rememberLivePlayerLayerState()
            live(
                session, behind = false, zapped = zapped,
                mode = if (layers.core.chrome.controlsVisible) PlayerChromeMode.CONTROLS else PlayerChromeMode.HIDDEN,
                entry = layers.core.chrome.controlsEntry,
            )
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            layers.core.chrome.hideControls()
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            zapped = true
            layers.onChannelTuneRequested()
            Snapshot.sendApplyNotifications()
        }
        captureFrames(
            "zap-reveal", "ui-motion-zap", listOf(3, 6),
            focus = "controls entering; focus requested after their first frame",
        )
    }

    /**
     * Captures the given frames after the last state change, named by the test clock's
     * elapsed time since that change (whole 16 ms frames), then the settled state.
     */
    private fun captureFrames(
        prefix: String,
        directory: String,
        frames: List<Int>,
        focus: String,
        settledFocus: String = focus,
        captureSettled: Boolean = true,
    ) {
        val changedAt = compose.mainClock.currentTime
        var drawn = 0
        for (frame in frames) {
            while (drawn < frame) {
                compose.mainClock.advanceTimeByFrame()
                drawn++
            }
            val elapsed = compose.mainClock.currentTime - changedAt
            capture(
                "$prefix-${elapsed}ms", focus = focus, directory = directory,
                timing = "$elapsed ms on the test clock after the state change ($frame frames of 16 ms); " +
                    "the transition starts on the first or second of these frames",
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        if (captureSettled) capture("$prefix-settled", focus = settledFocus, directory = directory, timing = "settled (+1000 ms)")
    }

    @Composable
    private fun live(
        session: CurrentSessionObservation,
        behind: Boolean,
        zapped: Boolean = false,
        mode: PlayerChromeMode = PlayerChromeMode.CONTROLS,
        entry: PlayerControlsEntry = PlayerControlsEntry.TRAVEL,
        panelOpen: Boolean = false,
        /** The channel rail below the controls, as the live player shows it. */
        rail: LivePlayerLayerState? = null,
        downHint: String? = null,
    ) {
        val timeshift = AppTimeshiftState(
            available = true,
            bufferStartMs = -600_000,
            positionMs = if (behind) -30_000 else 0,
            liveEdgeMs = 0,
            timingKnown = true,
        )
        val programme = if (zapped) zappedProgramme else programme
        val window = ProgrammeWindow(
            programme, Instant.fromEpochSeconds(if (behind) 1_770 else 1_800),
            if (behind) 0.49f else 0.5f, 0.33f, 0.5f, 0.5f, true,
        )
        ProvideArtworkSession(session) { PlayerChrome(
            mode = mode,
            content = PlayerChromeContent("20:15", liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, if (zapped) 2 else 1,
                if (zapped) "Science and Nature" else "Documentary", programme, null, false, 1_800, ""), picon = ChannelIcon.ImageCache(ArtworkId(1))),
            timeline = PlayerChromeTimeline.Live(
                timeshift = timeshift,
                nowSec = 1_800,
                programme = programme,
                committedWindow = window,
                programmeWindow = window,
                motionKey = ChannelId(if (zapped) 2 else 1),
            ),
            actions = PlayerChromeActions(active = true, paused = behind),
            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
            entry = entry,
            panelOpen = panelOpen,
            downHint = downHint,
            decorationCoversControls = rail?.channelDrawerOpen == true,
            controlsDecoration = { _, controls ->
                if (rail == null) controls() else {
                    val browser = remember { PlayerChannelBrowserState() }
                    QuickZapPresentation(
                        expanded = rail.channelRailPresented,
                        preview = {
                            ProvideArtworkSession(session) { QuickZapTrayPreview(channels.first(), programme, null, 1_800,
                                Modifier.padding(horizontal = TvGridHorizontalMargin)) }
                        },
                        trayBleeds = true,
                        header = { PlayerChannelBrowserHint(browser, true, stringResource(R.string.all_channels)) },
                        controls = controls,
                        channelContent = {
                            ProvideArtworkSession(session) { PlayerChannelBrowser(
                                state = browser, channels = channels, recentChannels = channels.drop(1),
                                playingChannelId = ChannelId(1), recordingChannelIds = emptySet(),
                                nowEvent = { programme }, nowSec = 1_800,
                                onFocusChannel = {}, onOpenDetails = { _, _, _ -> },
                                onPickChannel = {}, onClose = {}, active = rail.channelDrawerOpen,
                            ) }
                        },
                    )
                }
            },
        ) }
    }

    private fun show(content: @Composable (CurrentSessionObservation) -> Unit) {
        images.install(piconLoader(ApplicationProvider.getApplicationContext<Application>()))
        compose.setContent {
            view = LocalView.current
            val context = LocalContext.current
            val session = remember {
                FakeSessionObservation(SessionObservation.create(
                    sessionState = SessionState.Ready(ServerCapabilities.create(
                        streaming = CapabilityAccess.ALLOWED,
                        dvrWrite = CapabilityAccess.ALLOWED,
                    )),
                    channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
                    epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
                    dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
                )).captureCurrentSession()
            }
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF3A4A5A))) {
                    Image(remember { FixtureArt.art("still-ridge-light").asImageBitmap() }, null,
                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    content(session)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(
        name: String,
        focus: String = "initial controls focus",
        directory: String = "ui-motion",
        timing: String = "settled",
    ) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("../captures/$directory")
            CaptureOutput.writePng(File(directory, "$name.png"), bitmap)
            CaptureOutput.writeText(File(directory, "$name.txt")) {
                "file=$name.png\ncanvas=${bitmap.width}x${bitmap.height}\ndensity=1.0\nlocale=en\n" +
                    "fontScale=1.0\nfixture=PlayerMotionCaptureTest; production composables, offline fake state\n" +
                    "focus=$focus\ntiming=$timing\n"
            }
            bitmap.recycle()
        }
    }

    private fun piconLoader(context: android.content.Context) = ImageLoader.Builder(context)
        .components {
            add(object : Mapper<AppArtworkSource, ByteArray> {
                override fun map(data: AppArtworkSource, options: Options): ByteArray {
                    val bitmap = FixtureArt.picon(when (data.id.value) {
                        1 -> "ridge-earth"
                        2 -> "harbor-sport"
                        else -> "northline-news"
                    })
                    return ByteArrayOutputStream().also {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }.toByteArray()
                }
            })
        }
        .coroutineContext(Dispatchers.Main.immediate)
        .fetcherCoroutineContext(Dispatchers.Main.immediate)
        .decoderCoroutineContext(Dispatchers.Main.immediate)
        .diskCache(null)
        .build()

    private val programme = EpgEvent.create(
        id = EventId(1),
        channelId = ChannelId(1),
        title = "Ridge Light: A journey through the mountains and the valleys of the Alps",
        start = Instant.fromEpochSeconds(0),
        stop = Instant.fromEpochSeconds(3_600),
    )

    private val zappedProgramme = EpgEvent.create(
        id = EventId(3),
        channelId = ChannelId(2),
        title = "Harbor Kickoff",
        start = Instant.fromEpochSeconds(900),
        stop = Instant.fromEpochSeconds(4_500),
    )

    private val next = EpgEvent.create(
        id = EventId(2),
        channelId = ChannelId(1),
        title = "Northline Tonight",
        start = Instant.fromEpochSeconds(3_600),
        stop = Instant.fromEpochSeconds(5_400),
    )

    private val channels = (1L..3L).map {
        Channel.create(id = ChannelId(it), number = it + 100,
            name = listOf("Ridge Earth HD", "Harbor Sport HD", "Northline News")[(it - 1).toInt()], icon = ChannelIcon.ImageCache(ArtworkId(it.toInt())))
    }
}
