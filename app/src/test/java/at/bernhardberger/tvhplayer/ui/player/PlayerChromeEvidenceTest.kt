package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import android.app.Application
import at.bernhardberger.tvhplayer.testutil.FixtureArt
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
import org.junit.experimental.categories.Category
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.RecordingBadge

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerChromeEvidenceTest {
    @get:Rule val images = ImageLoaderRule()
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
        val bitmap = FixtureArt.art("ridge-light")
        val logo = FixtureArt.picon("ridge-earth")
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
        images.install(loader)
    }

    @Test fun passiveBarAndSlotHaveMergedSemanticsAndNoFocusActions() {
        compose.setContent { TVHeadendPlayerTheme {
            Column {
                InfoBar(info(false), Modifier.fillMaxWidth().testTag("bar"))
                PlayerStateCellView(PlayerStateCell.PLAYING, Modifier.testTag("chip"))
            }
        } }
        val bar = compose.onNodeWithTag("bar").fetchSemanticsNode()
        val spoken = bar.config[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue(spoken.contains("Channel 101 Ridge Earth HD"))
        assertTrue(spoken.contains("Now:"))
        assertTrue(spoken.contains("Recording scheduled"))
        assertFalse("the info bar carries no stream badges", spoken.contains("1080 lines"))
        assertTrue(bar.children.isEmpty())
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
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
            shown = snapshot(2, "Updated")
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(48)
        assertEquals("mid-crossfade both previews are composed", setOf(1L, 2L), active.toSet())
        assertEquals("the outgoing preview keeps its own programme", "Old", rendered[1L])
        assertEquals("Updated", rendered[2L])
        // The accessibility (merged) tree: the leaving preview's semantics are cleared.
        compose.onAllNodesWithText("Old").assertCountEquals(0)
        compose.onAllNodesWithText("Updated").assertCountEquals(1)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(setOf(2L), active.toSet())
    }

    @Test fun quickZapPreviewFollowsEachPressAndWaitsForAHeldKeyToRest() {
        var focused by mutableStateOf<ChannelId?>(ChannelId(1))
        var held by mutableStateOf(false)
        var shown: ChannelId? = null
        fun set(block: () -> Unit) = compose.runOnIdle {
            block()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.setContent { shown = rememberUnlessHeld(focused, held, QuickZapPreviewSettleMs) }
        compose.waitForIdle()
        assertEquals("the opening card previews at once", ChannelId(1), shown)
        compose.mainClock.autoAdvance = false
        for (id in listOf(2L, 3L, 2L)) {
            set { focused = ChannelId(id) }
            compose.mainClock.advanceTimeByFrame()
            assertEquals("each press swaps at once, however quick", ChannelId(id), shown)
        }
        set { held = true; focused = ChannelId(3) }
        compose.mainClock.advanceTimeByFrame()
        assertNull("a held key hides the preview from its first repeat", shown)
        set { focused = ChannelId(4) }
        compose.mainClock.advanceTimeBy(QuickZapPreviewSettleMs * 2)
        assertNull("and keeps it hidden while held", shown)
        set { held = false }
        compose.mainClock.advanceTimeBy(QuickZapPreviewSettleMs - 50)
        assertNull("focus rests before the preview returns", shown)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(ChannelId(4), shown)
        set { focused = ChannelId(5) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the next press swaps at once again", ChannelId(5), shown)
    }

    @Test fun crossfadingToAShorterPreviewKeepsItOnTheCardsFromTheFirstFrame() {
        fun snapshot(id: Long) = QuickZapPreviewSnapshot(ChannelId(id), "p$id", "", null, null, null)
        var shown by mutableStateOf(snapshot(1))
        compose.setContent { TVHeadendPlayerTheme { Box(Modifier.size(400.dp), contentAlignment = Alignment.BottomStart) {
            QuickZapTrayPreviewFrame(shown) {
                Box(Modifier.fillMaxWidth().height(if (it.channelId == ChannelId(1)) 200.dp else 100.dp).testTag(it.title))
            }
        } } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            shown = snapshot(2)
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("p1", useUnmergedTree = true).assertExists()
        val bottom = 400.dp
        compose.onNodeWithTag("p2").assertTopPositionInRootIsEqualTo(bottom - 100.dp)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("p1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("p2").assertTopPositionInRootIsEqualTo(bottom - 100.dp)
    }

    @Test fun tickingSecondsDoNotChangeLiveRegionAnnouncementButStateDoes() {
        var end by mutableStateOf(liveBarEnd(203_000L, "21:45")!!)
        var state by mutableStateOf(PlayerStateCell.PLAYING)
        compose.setContent { TVHeadendPlayerTheme { Column {
            EndLabel(end, remember { mutableStateOf(1f) }, Modifier.testTag("group"))
            PlayerStateCellView(state, Modifier.testTag("state"))
        } } }
        fun announcement(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        val before = announcement("group")
        compose.onNodeWithTag("group", useUnmergedTree = true).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("state", useUnmergedTree = true).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertEquals(listOf("Behind live"), before)
        compose.runOnIdle { end = liveBarEnd(204_000L, "21:45")!! }
        assertEquals("the ticking distance is never announced", before, announcement("group"))
        compose.runOnIdle { end = liveBarEnd(0L, "21:45")!! }
        assertNotEquals("reaching the edge is", before, announcement("group"))
        assertEquals(listOf("Live"), announcement("group"))
        assertEquals(listOf("Playing"), announcement("state"))
        compose.runOnIdle { state = PlayerStateCell.PAUSED }
        assertEquals(listOf("Paused"), announcement("state"))
        compose.runOnIdle { state = PlayerStateCell.PLAYING }
        assertEquals(listOf("Playing"), announcement("state"))
    }

    @Test fun blankHeroIdentityHasLocalizedNeutralFallback() {
        compose.setContent { TVHeadendPlayerTheme { ProvideArtworkSession(session) { ProgrammeHero(null, ChannelId(101), "", null) } } }
        compose.onNodeWithText("Channel", useUnmergedTree = true).assertExists()
    }

    @Test fun recordedAndUnnumberedIdentityHaveNoNextOrNowAndUseSpokenWords() {
        compose.setContent { TVHeadendPlayerTheme {
            InfoBar(info(true).copy(channelNumber = ""), Modifier.fillMaxWidth().testTag("bar"))
        } }
        val spoken = compose.onNodeWithTag("bar").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue(spoken.startsWith("Ridge Earth HD"))
        assertTrue(spoken.contains("38 minutes left"))
        assertTrue(spoken.contains("20:15 to 21:45"))
        assertFalse(spoken.contains("Now:"))
        assertFalse(spoken.contains("Next"))
        assertFalse(spoken.contains(" • Ridge"))
        compose.onNodeWithTag("player-scheduled-marker", useUnmergedTree = true).assertDoesNotExist()
    }

    /** The info bar with an empty artwork slot, passive as in the Banner. */
    @Composable
    private fun InfoBar(data: PlayerInfoBarData, modifier: Modifier = Modifier) {
        PlayerInfoBar(data, modifier) { Box(it.size(196.dp, 110.dp).background(TvSurfaceColors.containerLow)) }
    }

    @Test fun liveNextRowRunsFullWidthOnTheArtworksBottomEdge() {
        compose.setContent { TVHeadendPlayerTheme {
            Box(Modifier.fillMaxSize()) {
                InfoBar(info(false), Modifier.fillMaxWidth().align(Alignment.BottomCenter).testTag("bar"))
            }
        } }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = bounds("player-info-title")
        val text = bounds("player-info-last-text")
        val row = bounds("player-info-last-row")
        val bar = bounds("bar")
        assertEquals("Next stands on the artwork's bottom edge", bar.bottom, row.bottom, 1f)
        assertEquals("the row is as tall as its text", text.height, row.height, 1f)
        assertEquals("the row runs to the bar's end: nothing takes room at its end", bar.right, row.right, 1f)
        assertEquals("the row starts where the title starts", title.left, row.left, 1f)
    }

    @Test fun aRecordingHasNoLastRowItsEpisodeIsTheSubtitle() {
        var episode by mutableStateOf<String?>("Episode 4")
        compose.setContent { TVHeadendPlayerTheme {
            Box(Modifier.fillMaxSize()) {
                InfoBar(info(true).copy(subtitle = episode), Modifier.fillMaxWidth().align(Alignment.BottomCenter))
            }
        } }
        compose.onNodeWithTag("player-info-last-row", useUnmergedTree = true).assertDoesNotExist()
        val title = compose.onNodeWithTag("player-info-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val subtitle = compose.onNodeWithTag("player-info-subtitle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the episode stands under the title", title.bottom <= subtitle.top + 0.5f)
        compose.runOnIdle { episode = null }
        compose.onNodeWithTag("player-info-subtitle", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("player-info-last-row", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("the title keeps its place", title,
            compose.onNodeWithTag("player-info-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
    }

    @Test fun scheduledMarkerIsANeutralClockNotTheRedRecGlyph() {
        lateinit var view: View
        compose.setContent { TVHeadendPlayerTheme {
            view = LocalView.current
            InfoBar(info(false), Modifier.fillMaxWidth())
        } }
        val marker = compose.onNodeWithTag("player-scheduled-marker", useUnmergedTree = true)
            .assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
            .fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        fun red(pixel: Int) = android.graphics.Color.red(pixel) > 120 &&
            android.graphics.Color.red(pixel) > android.graphics.Color.green(pixel) * 1.5
        var drawn = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            assertFalse("red pixel at $x,$y", red(pixel))
            if (marker.contains(androidx.compose.ui.geometry.Offset(x + 0.5f, y + 0.5f)) &&
                android.graphics.Color.green(pixel) > 120) drawn++
        }
        assertTrue(drawn > 10)
        bitmap.recycle()
    }

    @Test fun theEyebrowLeadsTheInfoBarBesideTheArtwork() {
        compose.setContent { TVHeadendPlayerTheme {
            Box(Modifier.fillMaxSize()) {
                InfoBar(info(false), Modifier.fillMaxWidth().align(Alignment.BottomCenter).testTag("bar"))
            }
        } }
        val bar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("player-info-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val eyebrow = compose.onNodeWithTag("player-info-programme", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("player-info-eyebrow", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("the time and metadata lead on the bar's top, no channel line", bar.top, eyebrow.top, 0.5f)
        assertTrue("the title follows them", eyebrow.bottom <= title.top + 0.5f)
        assertEquals("the text starts a grid gutter after the slot", bar.left + with(compose.density) { (196 + 20).dp.toPx() }, title.left, 0.5f)
    }

    @Test fun topIdentityShowsTheLogoOrTheNumberInItsChipNeverBoth() {
        var withLogo by mutableStateOf(true)
        compose.setContent { TVHeadendPlayerTheme {
            ProvideArtworkSession(session) { PlayerTopIdentity(PlayerChromeContent("", info(false), picon = if (withLogo) ArtworkId.parse("imagecache/2") else null),
                Modifier.fillMaxSize()) }
        } }
        compose.onNodeWithTag("player-top-identity-logo", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("player-top-identity-number", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("player-top-identity-name", useUnmergedTree = true).assertExists()
        compose.runOnIdle { withLogo = false }
        compose.onNodeWithTag("player-top-identity-logo", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("player-top-identity-number", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("player-top-identity-name", useUnmergedTree = true).assertExists()
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLabelsDateAndSpokenPluralUseProductionResources() {
        var date = ""; var one = ""; var many = ""
        compose.setContent { TVHeadendPlayerTheme {
            date = playerRecordedDate(LocalDate.of(2026, 9, 12))
            one = playerMinutesLeft(1, spoken = true); many = playerMinutesLeft(38, spoken = true)
        } }
        assertEquals("12. Sept.", date)
        assertEquals("Noch 1 Minute", one)
        assertEquals("Noch 38 Minuten", many)
    }

    @Test fun failedPreviewArtworkCollapsesToTextOnlyAndMetadataPrecedesTitle() {
        val failing = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>()).components {
            add(Interceptor { chain -> ErrorResult(null, chain.request, IllegalStateException("fixture image unavailable")) })
        }.build()
        images.install(failing)
        compose.setContent { TVHeadendPlayerTheme { Column(Modifier.width(800.dp)) {
            ProvideArtworkSession(session) { QuickZapPreview("Failed title", "Failed metadata", null, null, ArtworkId(1)) }
            ProvideArtworkSession(session) { QuickZapPreview("Absent title", "Absent metadata", null, null, null) }
        } } }
        compose.waitForIdle()
        val failed = compose.onNodeWithText("Failed title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val absent = compose.onNodeWithText("Absent title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithText("Failed metadata", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(absent.left, failed.left)
        assertTrue(metadata.bottom <= failed.top)
    }

    @Test fun theBottomScrimStartsAtTheFootersTopAndFollowsItsHeightAndTheTopFadeEndsAt112dp() {
        lateinit var view: View
        var content by mutableStateOf(120.dp)
        compose.setContent { TVHeadendPlayerTheme {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {
                PlayerPage<Unit>(details = null, player = {
                    PlayerControlsLayer(visible = true, modalVisible = false) {
                        PlayerOverlayChrome(headerContent = { PlayerChromeHeader("", it) }) {
                            Spacer(Modifier.height(content))
                        }
                    }
                }, programme = {})
            }
        } }
        // Over white a black scrim of alpha a reads 255 * (1 - a); sampled left of the clock, 2px per dp.
        fun grey(bitmap: Bitmap, y: Int) = android.graphics.Color.red(bitmap.getPixel(200, y)).toDouble()
        listOf(120.dp, 172.dp).forEach { height ->
            content = height
            compose.waitForIdle()
            val footer = compose.onNodeWithTag("player-footer").fetchSemanticsNode().boundsInRoot
            assertEquals("$height: the footer is its 56dp run-out, its content and the bottom padding",
                1080f - (56 + height.value + TvGridVerticalMargin.value) * 2, footer.top, 0.5f)
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            val top = footer.top.toInt()
            assertEquals("$height: no scrim above the footer", 255.0, grey(bitmap, top - 2), 1.0)
            assertEquals("$height: 0.60 where the content starts", 102.0, grey(bitmap, top + 112), 4.0)
            assertEquals("$height: 0.80 52dp into the content", 51.0, grey(bitmap, top + 216), 4.0)
            assertEquals("$height: 0.92 at the bottom edge", 20.0, grey(bitmap, 1079), 4.0)
            assertEquals("$height: the top fade is 0.72 at the edge", 71.0, grey(bitmap, 0), 4.0)
            assertEquals("$height: 0.48 at 56dp", 133.0, grey(bitmap, 112), 4.0)
            assertEquals("$height: clear from 112dp", 255.0, grey(bitmap, 226), 1.0)
            bitmap.recycle()
        }
    }

    @Test fun viewportScrimPreservesRailShadingAndClearsWhenNothingIsShown() {
        lateinit var view: View
        var opacity by mutableStateOf(1f)
        val scrim = PlayerPageScrim().apply {
            alpha = { opacity }
            previewTop = { 600f }
            expansion = { 1f }
        }
        compose.setContent { TVHeadendPlayerTheme {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {
                Box(Modifier.fillMaxSize().playerScrim(scrim))
            }
        } }
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        // The clock's fade and rail veil are encoded in one gradient, not stacked paint.
        listOf(0 to 0.72f, 112 to 0.5736f, 224 to 0.36f,
            488 to 0.36f, 600 to 0.60f, 1079 to 0.92f).forEach { (y, alpha) ->
            assertEquals("rail alpha at $y", 255.0 * (1f - alpha),
                android.graphics.Color.red(bitmap.getPixel(200, y)).toDouble(), 2.0)
        }
        compose.runOnIdle { opacity = 0f }
        compose.waitForIdle()
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        listOf(0, 112, 224, 488, 600, 1079).forEach { y ->
            assertEquals("hidden at $y", android.graphics.Color.WHITE, bitmap.getPixel(200, y))
        }
        bitmap.recycle()
    }

    @Category(VisualCapture::class)
    @Test fun english() = captures("en", 1f)
    @Category(VisualCapture::class)
    @Test fun englishLargeText() = captures("en", 1.3f)
    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun german() = captures("de", 1f)
    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeText() = captures("de", 1.3f)

    private enum class Scene { STATUS, BRIGHT_SCRIM, INFO, INFO_NO_LOGO, RECORDED, HERO_ART, HERO_FALLBACK, HERO_NEUTRAL, PREVIEW_ART, PREVIEW_TEXT }

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
                            PlayerChromeHeader("20:37", Modifier.fillMaxSize())
                        }
                    Box(Modifier.fillMaxSize().padding(48.dp)) {
                        when (scene) {
                            Scene.BRIGHT_SCRIM -> Unit
                            Scene.STATUS -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val emphasis = remember { mutableStateOf(1f) }
                                    PlayerStateCell.entries.forEach { PlayerStateCellView(it) }
                                    listOfNotNull(liveBarEnd(0L, "21:45"), liveBarEnd(203_000L, "21:45"), liveBarEnd(203_000L, null))
                                        .forEach { EndLabel(it, emphasis) }
                                    listOfNotNull(recordingBarEnd(600_000L, 4_350_000L, growing = false),
                                        recordingBarEnd(600_000L, 4_350_000L, growing = true))
                                        .forEach { EndLabel(it, emphasis) }
                                    RecordingBadge(DvrEntryState.RECORDING)
                                }
                            }
                            Scene.INFO, Scene.INFO_NO_LOGO, Scene.RECORDED -> InfoBar(info(scene == Scene.RECORDED), Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                            Scene.HERO_ART, Scene.HERO_FALLBACK, Scene.HERO_NEUTRAL -> ProvideArtworkSession(session) { ProgrammeHero(
                                if (scene == Scene.HERO_ART) ArtworkId(1) else null, ChannelId(101), "101",
                                if (scene == Scene.HERO_NEUTRAL) null else ArtworkId.parse("imagecache/2"),
                            ) }
                            Scene.PREVIEW_ART, Scene.PREVIEW_TEXT -> ProvideArtworkSession(session) { QuickZapPreview(
                                "Die außergewöhnliche Reise durch die österreichischen Alpen", "20:15–21:45 • ${playerMinutesLeft(38)} • Natur",
                                "Eine Reise durch die Bergwelt mit ihren Menschen und Geschichten. Entdecken Sie die Landschaft aus einer neuen Perspektive.",
                                "21:45 Nachrichten", if (scene == Scene.PREVIEW_ART) ArtworkId(1) else null, Modifier.fillMaxWidth(),
                            ) }
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
            val directory = File("../artifacts/player-chrome").apply { mkdirs() }
            val name = "${next.name.lowercase(Locale.ROOT)}-$locale-font$fontScale.png"
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    /**
     * The end and, for a live end, the distance behind live for a complete [end], as the chrome hands
     * them over: the static part and the deferred distance. [modifier] goes to the distance.
     */
    @Composable private fun EndLabel(end: PlayerBarEnd, emphasis: State<Float>, modifier: Modifier = Modifier) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DistanceTextGap),
        ) {
            PlayerBarEndClock(end.withoutDistance(), emphasis)
            if (end.liveEnd) PlayerLiveDistance(end.withoutDistance(), rememberUpdatedState(end.distance),
                modifier.width(rememberTimelineEndpointWidths(TimelineKind.LIVE).distance))
        }
    }

    @Composable private fun info(recorded: Boolean) = PlayerInfoBarData("101", "Ridge Earth HD",
        "Die außergewöhnliche Reise durch die österreichischen Alpen", "20:15–21:45", "Drama • S3 E4",
        "Eine neue Perspektive auf Menschen und ihre Geschichten", 38, if (recorded) null else "21:45 Nachrichten", !recorded,
        if (recorded) playerRecordedDate(LocalDate.of(2026, 9, 12)) else null, spokenTimeRange = "20:15 to 21:45")

}
