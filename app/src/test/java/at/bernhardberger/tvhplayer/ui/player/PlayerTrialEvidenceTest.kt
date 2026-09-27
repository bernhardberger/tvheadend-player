package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.playback.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import java.io.File
import java.util.Locale
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerTrialEvidenceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var loader: ImageLoader
    private lateinit var brightStill: Bitmap
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
        // Every image request terminates here: deterministic art, no server or network access.
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(44, 66, 80))
            val paint = Paint().apply { color = android.graphics.Color.rgb(125, 158, 164) }
            drawCircle(480f, 95f, 55f, paint)
            paint.color = android.graphics.Color.rgb(38, 87, 75)
            drawRect(0f, 225f, 640f, 360f, paint)
        }
        // Wide transparent logo fixture, distinct from programme artwork (not a downloaded logo).
        val logo = Bitmap.createBitmap(512, 144, Bitmap.Config.ARGB_8888)
        Canvas(logo).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 100f; isFakeBoldText = true }
            drawText("ORF 1 HD", 8f, 108f, paint)
        }
        brightStill = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        Canvas(brightStill).apply {
            drawColor(android.graphics.Color.rgb(232, 240, 246))
            val paint = Paint().apply { color = android.graphics.Color.rgb(255, 247, 218) }
            drawCircle(800f, 70f, 55f, paint)
            paint.color = android.graphics.Color.rgb(188, 206, 184)
            drawRect(0f, 350f, 960f, 540f, paint)
        }
        loader = ImageLoader.Builder(app).components {
            add(Interceptor { chain ->
                val art = if ((chain.request.data as? AppArtworkSource)?.id == ArtworkId.parse("imagecache/2")) logo else bitmap
                SuccessResult(art.asImage(), chain.request, DataSource.MEMORY)
            })
        }.build()
    }

    @Test fun passiveBarBadgesAndChipHaveMergedSemanticsAndNoFocusActions() {
        compose.setContent { TVHeadendPlayerTheme {
            Column {
                PlayerInfoBar(info(false), badges(true), null, loader, session, Modifier.fillMaxWidth().testTag("bar"))
                PlayerStatusChip(checkNotNull(playerStatus()), Modifier.testTag("chip"))
                PlayerGlanceBadges(glanceBadges(audioDescription = true, subtitles = true, teletext = true), Modifier.width(400.dp).testTag("badges"))
            }
        } }
        val bar = compose.onNodeWithTag("bar").fetchSemanticsNode()
        val spoken = bar.config[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue(spoken.contains("Channel 101, ORF 1 HD"))
        assertTrue(spoken.contains("Now:"))
        assertTrue(spoken.contains("Recording scheduled"))
        assertTrue(spoken.contains("1080 lines"))
        assertTrue(bar.children.isEmpty())
        compose.onNodeWithContentDescription("Audio description, Subtitles, Teletext available").assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun chipWithoutIndicatorStartsItsLabelAtTheStartPadding() {
        compose.setContent { TVHeadendPlayerTheme {
            Column {
                PlayerStatusChip(checkNotNull(playerStatus()), Modifier.testTag("live"))
                PlayerStatusChip(checkNotNull(playerStatus(paused = true)), Modifier.testTag("paused"))
                androidx.tv.material3.Text("Live", style = androidx.tv.material3.MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.testTag("live-label"))
                androidx.tv.material3.Text("Paused", style = androidx.tv.material3.MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.testTag("paused-label"))
            }
        } }
        fun boundsWidth(tag: String): Float = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { it.right.value - it.left.value }
        // NONE: only the 8 dp paddings around the label; no 16 dp indicator box and 4 dp gap.
        assertEquals(boundsWidth("live-label") + 16f, boundsWidth("live"), 0.6f)
        assertEquals(boundsWidth("paused-label") + 16f + 16f + 4f, boundsWidth("paused"), 0.6f)
    }

    @Test fun quickZapPreviewCrossfadeKeepsTheOutgoingProgrammeAndOnlyTheIncomingIsAnnounced() {
        fun snapshot(id: Long, title: String) = QuickZapPreviewSnapshot(ChannelId(id), title, "20:15–21:45", null, null, null)
        var shown by mutableStateOf<QuickZapPreviewSnapshot?>(snapshot(1, "Old"))
        val rendered = mutableMapOf<Long, String>()
        val active = mutableSetOf<Long>()
        compose.setContent { TVHeadendPlayerTheme {
            QuickZapTrayPreviewFrame(shown) { preview ->
                val id = preview.channelId.value
                rendered[id] = preview.title
                DisposableEffect(id) {
                    active += id
                    onDispose { active -= id }
                }
                androidx.tv.material3.Text(preview.title)
            }
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            shown = snapshot(2, "New")
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(48)
        assertEquals("mid-crossfade both previews are composed", setOf(1L, 2L), active.toSet())
        assertEquals("the outgoing preview keeps its own programme", "Old", rendered[1L])
        assertEquals("New", rendered[2L])
        // The accessibility (merged) tree: the leaving preview's semantics are cleared.
        compose.onAllNodesWithText("Old").assertCountEquals(0)
        compose.onAllNodesWithText("New").assertCountEquals(1)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(setOf(2L), active.toSet())
    }

    @Test fun quickZapPreviewWaitsForFocusToRestAndRepeatedStepsRestartTheWait() {
        var focused by mutableStateOf<ChannelId?>(ChannelId(1))
        var settled: ChannelId? = null
        fun focus(id: ChannelId) = compose.runOnIdle {
            focused = id
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.setContent { settled = rememberSettled(focused, QuickZapPreviewSettleMs) }
        compose.waitForIdle()
        assertEquals("the opening card previews at once", ChannelId(1), settled)
        compose.mainClock.autoAdvance = false
        focus(ChannelId(2))
        compose.mainClock.advanceTimeByFrame()
        assertNull("moving hides the preview immediately", settled)
        compose.mainClock.advanceTimeBy(QuickZapPreviewSettleMs - 50)
        focus(ChannelId(3))
        compose.mainClock.advanceTimeBy(QuickZapPreviewSettleMs - 50)
        assertNull("a repeat step restarts the wait", settled)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(ChannelId(3), settled)
        focus(ChannelId(4))
        compose.mainClock.advanceTimeByFrame()
        focus(ChannelId(3))
        compose.mainClock.advanceTimeByFrame()
        assertNull("returning to the previous card waits again", settled)
        compose.mainClock.advanceTimeBy(QuickZapPreviewSettleMs - 50)
        assertNull("the full wait restarts", settled)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(ChannelId(3), settled)
    }

    @Test fun tickingSecondsDoNotChangeLiveRegionAnnouncementButKindDoes() {
        var status by mutableStateOf(checkNotNull(playerStatus(behindLiveSeconds = 203)))
        compose.setContent { TVHeadendPlayerTheme { PlayerStatusChip(status, Modifier.testTag("chip")) } }
        fun announcement() = compose.onNodeWithTag("chip").fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        val before = announcement()
        compose.onNodeWithTag("chip").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.runOnIdle { status = checkNotNull(playerStatus(behindLiveSeconds = 204)) }
        assertEquals(before, announcement())
        compose.runOnIdle { status = checkNotNull(playerStatus(paused = true, behindLiveSeconds = 204)) }
        assertNotEquals(before, announcement())
        assertEquals(listOf("Paused"), announcement())
    }

    @Test fun detailsStartAtFirstRowAndDpadReachesLastWithoutClickActions() {
        compose.setContent { TVHeadendPlayerTheme {
            StreamSignalDetailsPage(streamSignalDetails(stream = (1..12).map { StreamSignalRow("Metric $it", "$it") }), Modifier.width(400.dp).height(240.dp))
        } }
        compose.onNodeWithContentDescription("Metric 1: 1").assertIsFocused()
        repeat(11) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) } }
        compose.onNodeWithContentDescription("Metric 12: 12").assertIsFocused().assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test fun emptyDetailsHaveDeterministicReadingFocus() {
        compose.setContent { TVHeadendPlayerTheme { StreamSignalDetailsPage(emptyMap(), Modifier.width(400.dp)) } }
        compose.onNodeWithContentDescription("No stream details available").assertIsFocused()
    }

    @Test fun detailsKeepFocusOnUpdatesAndMoveToNearestWhenFocusedRowDisappears() {
        var rows by mutableStateOf(listOf(StreamSignalRow("A", "1"), StreamSignalRow("B", "2"), StreamSignalRow("C", "3")))
        compose.setContent { TVHeadendPlayerTheme { StreamSignalDetailsPage(streamSignalDetails(stream = rows), Modifier.width(480.dp)) } }
        compose.onNodeWithContentDescription("A: 1").assertIsFocused()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("B: 2").assertIsFocused()
        compose.runOnIdle { rows = rows.drop(1).map { if (it.label == "B") it.copy(value = "22") else it } }
        compose.onNodeWithContentDescription("B: 22").assertIsFocused()
        compose.runOnIdle { rows = rows.drop(1) }
        compose.onNodeWithContentDescription("C: 3").assertIsFocused()
        compose.runOnIdle { rows = listOf(StreamSignalRow("A", "1")) + rows }
        compose.onNodeWithContentDescription("C: 3").assertIsFocused()
    }

    @Test fun changedProblemPublishesNewAnnouncement() {
        var problem by mutableStateOf("Unavailable")
        compose.setContent { TVHeadendPlayerTheme { PlayerStatusChip(checkNotNull(playerStatus(problem = problem))) } }
        compose.onNodeWithContentDescription("Unavailable").assertExists()
        compose.runOnIdle { problem = "Connection lost" }
        compose.onNodeWithContentDescription("Connection lost").assertExists()
        compose.onNodeWithContentDescription("Unavailable").assertDoesNotExist()
    }

    @Test fun statusTextAlignsAcrossIndicatorsAndBehindDurationSupportsHours() {
        compose.mainClock.autoAdvance = false
        compose.setContent { TVHeadendPlayerTheme { Column {
            listOf(playerStatus(), playerStatus(paused = true), playerStatus(tuning = true), playerStatus(buffering = true),
                playerStatus(behindLiveSeconds = 3723), recordingNowStatus(true)).filterNotNull().forEach { PlayerStatusChip(it) }
        } } }
        compose.mainClock.advanceTimeBy(256)
        val labels = listOf("Paused", "Tuning…", "Buffering…", "1h 2m behind live", "REC")
        fun start(label: String) = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        val starts = labels.map(::start)
        // Labels behind an indicator align; the Live chip has no indicator, so its label
        // starts at the chip's 8 dp start padding instead of after an empty 16 dp + 4 dp slot.
        assertEquals(1, starts.distinct().size)
        with(compose.density) {
            assertEquals(8.dp.toPx(), start("Live"), 0.5f)
            assertEquals((8.dp + 16.dp + 4.dp).toPx(), starts.first(), 0.5f)
        }
    }

    @Test fun scrimRetainsContrastUnderClusterAndFeathersToTransparentAtHostEdges() {
        lateinit var view: View
        compose.setContent { TVHeadendPlayerTheme {
            view = LocalView.current
            Box(Modifier.size(400.dp, 200.dp).background(androidx.compose.ui.graphics.Color.White)) {
                PlayerTopEndScrim(100.dp, 100.dp, Modifier.fillMaxSize())
            }
        } }
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        fun value(x: Int, y: Int) = android.graphics.Color.red(bitmap.getPixel(x, y))
        assertEquals(255, value(100, 50))
        assertTrue(value(400, 50) in (value(790, 50) + 1)..254)
        assertTrue(value(790, 50) < value(790, 190))
        assertTrue(value(790, 220) > value(790, 190))
        assertEquals(255, value(790, 250))
        bitmap.recycle()
    }

    @Test fun blankHeroIdentityHasLocalizedNeutralFallback() {
        compose.setContent { TVHeadendPlayerTheme { ProgrammeHero(null, ChannelId(101), "", null, loader, session) } }
        compose.onNodeWithText("Channel", useUnmergedTree = true).assertExists()
    }

    @Test fun recordedAndUnnumberedIdentityHaveNoNextOrNowAndUseSpokenWords() {
        compose.setContent { TVHeadendPlayerTheme {
            PlayerInfoBar(info(true).copy(channelNumber = ""), emptyList(), null, loader, session, Modifier.fillMaxWidth().testTag("bar"))
        } }
        val spoken = compose.onNodeWithTag("bar").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue(spoken.startsWith("ORF 1 HD"))
        assertTrue(spoken.contains("38 minutes left"))
        assertTrue(spoken.contains("20:15 to 21:45"))
        assertFalse(spoken.contains("Now:"))
        assertFalse(spoken.contains("Next"))
        assertFalse(spoken.contains(" · ORF"))
        compose.onNodeWithTag("trial-scheduled-marker", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun scheduledMarkerIsHollowNotTheFilledRecGlyph() {
        lateinit var view: View
        compose.setContent { TVHeadendPlayerTheme {
            view = LocalView.current
            PlayerInfoBar(info(false), emptyList(), null, loader, session, Modifier.fillMaxWidth())
        } }
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        fun red(x: Int, y: Int): Boolean {
            val pixel = bitmap.getPixel(x, y)
            return android.graphics.Color.red(pixel) > 120 && android.graphics.Color.red(pixel) > android.graphics.Color.green(pixel) * 1.5
        }
        val pixels = buildList { for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (red(x, y)) add(x to y) }
        assertTrue(pixels.isNotEmpty())
        val left = pixels.minOf { it.first }; val right = pixels.maxOf { it.first }
        val top = pixels.minOf { it.second }; val bottom = pixels.maxOf { it.second }
        assertTrue(right - left in 16..20)
        assertTrue(bottom - top in 16..20)
        assertFalse(red((left + right) / 2, (top + bottom) / 2))
        assertTrue(red((left + right) / 2, top + 1))
        bitmap.recycle()
    }

    @Test fun infoAtLargeFontRetainsAllAccessibilityBadges() {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(2f, 1.3f)) { TVHeadendPlayerTheme {
            PlayerInfoBar(info(false), badges(true), null, loader, session, Modifier.width(864.dp).testTag("bar"))
        } } }
        val spoken = compose.onNodeWithTag("bar").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")
        listOf("DD+ 5.1", "Audio description", "Subtitles", "Teletext available").forEach { assertTrue(it, spoken.contains(it)) }
    }

    @Test fun badgeBlockUsesBottomEndOfAllocatedSpaceWithFourDpPadding() {
        compose.setContent { TVHeadendPlayerTheme {
            PlayerGlanceBadges(glanceBadges(audioDescription = true), Modifier.size(300.dp, 120.dp).testTag("badges"))
        } }
        val container = compose.onNodeWithTag("badges").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("AD", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(8f, container.right - label.right, 1f)
        assertTrue(container.bottom - label.bottom in 0f..8f)
    }

    @Test fun repeatedLabelsAcrossSectionsHaveIndependentFocusKeys() {
        compose.setContent { TVHeadendPlayerTheme {
            StreamSignalDetailsPage(streamSignalDetails(stream = listOf(StreamSignalRow("Rate", "Video")),
                source = listOf(StreamSignalRow("Rate", "Tuner"))), Modifier.width(400.dp))
        } }
        compose.onNodeWithContentDescription("Rate: Video").assertIsFocused()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Rate: Tuner").assertIsFocused()
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun largeGermanDetailsScrollLastWrappedRowIntoView() {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(2f, 1.3f)) { TVHeadendPlayerTheme {
            StreamSignalDetailsPage(formatStreamSignalDetails(diagnostics(true)), Modifier.width(480.dp).height(444.dp))
        } } }
        repeat(7) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) } }
        compose.onNodeWithContentDescription("Verworfene Bilder: 0").assertIsFocused().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLabelsDateAndSpokenPluralUseProductionResources() {
        lateinit var details: Map<StreamSignalSection, List<StreamSignalRow>>
        var date = ""; var one = ""; var many = ""
        compose.setContent { TVHeadendPlayerTheme {
            details = formatStreamSignalDetails(diagnostics(true))
            date = trialRecordedDate(LocalDate.of(2026, 9, 12))
            one = trialMinutesLeft(1, spoken = true); many = trialMinutesLeft(38, spoken = true)
            PlayerGlanceBadges(glanceBadges(audioDescription = true, subtitles = true, teletext = true), Modifier.width(300.dp))
        } }
        compose.onNodeWithText("AD", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("UT", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("VT", useUnmergedTree = true).assertExists()
        assertEquals("12. Sep.", date)
        assertEquals("Noch 1 Minute", one)
        assertEquals("Noch 38 Minuten", many)
        assertEquals(listOf("Dienst"), details.getValue(StreamSignalSection.SOURCE).map { it.label })
        assertEquals(listOf("Puffer", "Verworfene Bilder"), details.getValue(StreamSignalSection.HEALTH).map { it.label })
    }

    @Test fun productionDetailsUseSampleMimeAndOnlyPublishedSourceAndReception() {
        lateinit var dvb: Map<StreamSignalSection, List<StreamSignalRow>>
        lateinit var iptv: Map<StreamSignalSection, List<StreamSignalRow>>
        lateinit var recording: Map<StreamSignalSection, List<StreamSignalRow>>
        compose.setContent { TVHeadendPlayerTheme {
            dvb = formatStreamSignalDetails(diagnostics(true))
            iptv = formatStreamSignalDetails(diagnostics(false))
            recording = formatStreamSignalDetails(diagnostics(true).copy(source = AppPlaybackSource.RECORDING))
        } }
        assertEquals(listOf(StreamSignalRow("Service", "ORF 1 HD")), dvb[StreamSignalSection.SOURCE])
        assertTrue(dvb.getValue(StreamSignalSection.STREAM).first().value!!.contains("H.264"))
        assertTrue(dvb.getValue(StreamSignalSection.STREAM)[1].value!!.contains("Dolby Digital Plus"))
        assertFalse(dvb.values.flatten().any { it.value.orEmpty().contains("avc1") || it.value == "DVB-S2" })
        assertTrue(dvb.getValue(StreamSignalSection.RECEPTION).any { it.value == "82 %" })
        assertFalse(iptv.containsKey(StreamSignalSection.RECEPTION))
        assertFalse(recording.containsKey(StreamSignalSection.RECEPTION))
        assertFalse(recording.containsKey(StreamSignalSection.SOURCE))
    }

    @Test fun failedPreviewArtworkCollapsesToTextOnlyAndMetadataPrecedesTitle() {
        val failing = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>()).components {
            add(Interceptor { chain -> ErrorResult(null, chain.request, IllegalStateException("fixture image unavailable")) })
        }.build()
        compose.setContent { TVHeadendPlayerTheme { Column(Modifier.width(800.dp)) {
            QuickZapPreview("Failed title", "Failed metadata", null, null, "imagecache/1", failing, session)
            QuickZapPreview("Absent title", "Absent metadata", null, null, null, loader, session)
        } } }
        compose.waitForIdle()
        val failed = compose.onNodeWithText("Failed title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val absent = compose.onNodeWithText("Absent title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithText("Failed metadata", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(absent.left, failed.left)
        assertTrue(metadata.bottom <= failed.top)
    }

    @Test fun english() = captures("en", 1f)
    @Test fun englishLargeText() = captures("en", 1.3f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun german() = captures("de", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeText() = captures("de", 1.3f)

    private enum class Scene { STATUS, BRIGHT_SCRIM, BADGES, BADGES_SCAN, INFO, INFO_NO_LOGO, RECORDED, HERO_ART, HERO_FALLBACK, HERO_NEUTRAL, PREVIEW_ART, PREVIEW_TEXT, DETAILS_DVB, DETAILS_IPTV }

    private fun captures(locale: String, fontScale: Float) {
        lateinit var view: View
        var scene by mutableStateOf(Scene.STATUS)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(TvSurfaceColors.containerLowest, TvSurfaceColors.containerHigh)))) {
                        if (scene == Scene.BRIGHT_SCRIM) {
                            Image(brightStill.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            var clusterWidth by remember { mutableStateOf(120.dp) }
                            var clusterHeight by remember { mutableStateOf(140.dp) }
                            val density = LocalDensity.current
                            PlayerTopEndScrim(clusterWidth + 48.dp, clusterHeight + 48.dp, Modifier.fillMaxSize())
                            PlayerTopStatusCluster("20:37", playerStatus(), Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 48.dp)
                                .onSizeChanged { with(density) { clusterWidth = it.width.toDp(); clusterHeight = it.height.toDp() } }, recordingNowStatus(true))
                        }
                    Box(Modifier.fillMaxSize().padding(48.dp)) {
                        when (scene) {
                            Scene.BRIGHT_SCRIM -> Unit
                            Scene.STATUS -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf(playerStatus(), playerStatus(behindLiveSeconds = 203), playerStatus(paused = true, behindLiveSeconds = 203),
                                        playerStatus(tuning = true), playerStatus(buffering = true), recordingNowStatus(true),
                                        playerStatus(live = false, growingRecording = true), playerStatus(problem = if (locale == "de") "Stream nicht verfügbar" else "Stream unavailable"))
                                        .filterNotNull().forEach { PlayerStatusChip(it) }
                                }
                                PlayerTopStatusCluster("20:37", playerStatus(), Modifier.align(Alignment.TopEnd), recordingNowStatus(true))
                            }
                            Scene.BADGES_SCAN -> PlayerGlanceBadges(badges(true, locale, VideoScan.INTERLACED), Modifier.width(800.dp))
                            Scene.BADGES -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                Text("DVB · SNR %", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                PlayerGlanceBadges(badges(true, locale), Modifier.width(800.dp))
                                Text("DVB · SNR dB", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                PlayerGlanceBadges(glanceBadges(1080, "video/avc", "audio/aac", 2, liveFrontend = true, absoluteSnrDecibels = 12.3, locale = Locale.forLanguageTag(locale)), Modifier.width(800.dp))
                                Text("IPTV", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                PlayerGlanceBadges(badges(false, locale), Modifier.width(800.dp))
                            }
                            Scene.INFO, Scene.INFO_NO_LOGO, Scene.RECORDED -> PlayerInfoBar(info(scene == Scene.RECORDED), badges(scene != Scene.RECORDED, locale),
                                if (scene == Scene.INFO_NO_LOGO) null else ArtworkId.parse("imagecache/2"), loader, session, Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                            Scene.HERO_ART, Scene.HERO_FALLBACK, Scene.HERO_NEUTRAL -> ProgrammeHero(
                                if (scene == Scene.HERO_ART) "imagecache/1" else null, ChannelId(101), "101",
                                if (scene == Scene.HERO_NEUTRAL) null else ArtworkId.parse("imagecache/2"), loader, session,
                            )
                            Scene.PREVIEW_ART, Scene.PREVIEW_TEXT -> QuickZapPreview(
                                "Die außergewöhnliche Reise durch die österreichischen Alpen", "20:15–21:45 · ${trialMinutesLeft(38)} · Natur",
                                "Eine Reise durch die Bergwelt mit ihren Menschen und Geschichten. Entdecken Sie die Landschaft aus einer neuen Perspektive.",
                                "21:45 Nachrichten", if (scene == Scene.PREVIEW_ART) "imagecache/1" else null, loader, session, Modifier.fillMaxWidth(),
                            )
                            Scene.DETAILS_DVB, Scene.DETAILS_IPTV -> StreamSignalDetailsPage(formatStreamSignalDetails(diagnostics(scene == Scene.DETAILS_DVB)), Modifier.width(480.dp).fillMaxHeight())
                        }
                    }
                    }
                }
            }
        }
        for (next in Scene.entries) {
            compose.runOnIdle { scene = next }
            compose.waitForIdle()
            // Advance deterministic Compose time for artwork/focus, never reach a live service.
            compose.mainClock.advanceTimeBy(256)
            compose.waitForIdle()
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            val directory = File("../artifacts/ui-trial").apply { mkdirs() }
            val name = "${next.name.lowercase(Locale.ROOT)}-$locale-font$fontScale.png"
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Composable private fun info(recorded: Boolean) = PlayerInfoBarData("101", "ORF 1 HD",
        "Die außergewöhnliche Reise durch die österreichischen Alpen", "20:15–21:45", "Drama · S3 E4",
        "Eine neue Perspektive auf Menschen und ihre Geschichten", 38, if (recorded) null else "21:45 Nachrichten", !recorded,
        if (recorded) trialRecordedDate(LocalDate.of(2026, 9, 12)) else null)

    private fun badges(dvb: Boolean, locale: String = "en", scan: VideoScan? = null) = glanceBadges(1080, "video/avc", "audio/eac3", 6,
        audioDescription = true, subtitles = true, teletext = true, liveFrontend = dvb, relativeSnrPercent = 82.0,
        locale = Locale.forLanguageTag(locale), scan = scan)

    @OptIn(SubscriptionInfrastructureApi::class)
    private fun diagnostics(dvb: Boolean): AppPlaybackDiagnostics {
        var live = LiveSubscriptionDiagnostics.update(null, SubscriptionEvent.Started(null, null, SubscriptionCondition.NO_DETAIL, null,
            LiveSubscriptionSource.create(null, null, null, null, "ORF 1 HD")))
        if (dvb) live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Signal(53739, 12300, null, null, 0, 0, false))
        live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Queue(0, 0, 0, 0, 0, 0))
        return AppPlaybackDiagnostics(source = AppPlaybackSource.LIVE_TV,
            video = AppPlaybackFormatDiagnostics("avc1.640028", resolution = "1920 × 1080", frameRate = 25f, sampleMimeType = "video/avc", width = 1920, height = 1080),
            audio = AppPlaybackFormatDiagnostics("ec-3", channelCount = 6, sampleMimeType = "audio/eac3"), live = live)
    }
}
