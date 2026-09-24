package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.ChannelsKey
import at.bernhardberger.tvhplayer.ui.MainStartupComposition
import at.bernhardberger.tvhplayer.ui.MainStartupCompositionState
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
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

/**
 * Standard Channels evidence: the production [ChannelsScreenContent] on the real
 * navigation shell, 960x540 logical canvas, with fake channel/EPG/DVR state and a
 * deterministic synthetic backdrop. Captures prove composition only; focus feel,
 * readability over motion and overscan remain physical-TV gates.
 *
 * Capture layout: `artifacts/channels-standard/<locale>-font<scale>-<state>-<background>.png`
 * at 960x540 px (density 1.0). Synthetic bright/dark video is behind the production
 * MainStartupComposition, SideRail and Channels, including all real scrim layers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelsStandardLayoutEvidenceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var videoColor by mutableStateOf(Color.White)
    private var playbackIndicator by mutableStateOf(ChannelPlaybackIndicator.PLAYING)
    private var defaultZone: TimeZone? = null

    @Before fun pinClock() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreClock() {
        defaultZone?.let(TimeZone::setDefault)
    }

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun playbackMarkerSlotAndRecordingStayStableAcrossPauseAndTune() {
        channelsShell(1f)
        enterAndFocus(PLAYING)
        val slot = inRow(PLAYING, "channel-playing-indicator").bounds()
        val recording = inRow(PLAYING, "channel-recording-indicator").bounds()
        val title = inRow(PLAYING, "channel-title").bounds()
        capture("en-font1-playing-row-current-bright-video")
        compose.runOnIdle { playbackIndicator = ChannelPlaybackIndicator.PAUSED }
        assertEquals(slot, inRow(PLAYING, "channel-paused-indicator").bounds())
        assertEquals(recording, inRow(PLAYING, "channel-recording-indicator").bounds())
        assertEquals(title, inRow(PLAYING, "channel-title").bounds())
        capture("en-font1-paused-row-current-bright-video")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { playbackIndicator = ChannelPlaybackIndicator.TUNING }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        assertEquals(slot, inRow(PLAYING, "channel-tuning-indicator").bounds())
        assertEquals(recording, inRow(PLAYING, "channel-recording-indicator").bounds())
        capture("en-font1-tuning-row-current-96ms-bright-video")
        compose.runOnIdle { playbackIndicator = ChannelPlaybackIndicator.NONE }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(240)
        compose.onAllNodesWithTag("channel-tuning-indicator", useUnmergedTree = true).assertCountEquals(0)
        inRow(PLAYING, "channel-recording-indicator").assertExists()
    }

    // ---- behaviour ----

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun entryLandsOnTheActiveTagDownEntersTheAnchorAndBackReturns() {
        channelsShell(1f)

        tab("All channels").assertIsFocused()

        key(Key.DirectionDown)
        // Nothing selected or remembered yet: the anchor is the first channel in scope.
        row(1).assertIsFocused()
        detail("channels-detail-channel").assertTextEquals(name(1))

        key(Key.Back)
        tab("All channels").assertIsFocused()

        // An actual tag change previews the playing channel of the new scope; Down agrees.
        key(Key.DirectionRight)
        tab("Favourites").assertIsFocused()
        key(Key.DirectionDown)
        row(PLAYING).assertIsFocused()
        detail("channels-detail-channel").assertTextEquals(name(PLAYING))

        key(Key.Back)
        tab("Favourites").assertIsFocused()
        key(Key.DirectionDown)
        row(PLAYING).assertIsFocused()
    }

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun tagConfirmEntersAnchorWithoutActivatingItOnTheSamePress() {
        channelsShell(1f)
        tab("All channels").assertIsFocused()
        key(Key.DirectionCenter)
        row(1).assertIsFocused()
        assertEquals("relocation key does not tune", 0, playbackRequests)
        key(Key.DirectionCenter)
        assertEquals("next confirm activates the native row", 1, playbackRequests)
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun standardRowsFollowTheAcceptedGeometryOnTheShellInsets() {
        channelsShell(1f)
        // Focus stays on the tag row so every asserted row is at its unscaled layout
        // size; the library's focus scale is checked separately at the end.
        tab("All channels").assertIsFocused()

        // Tags on the row axis, at the top inset.
        val tabs = tab("All channels").bounds()
        assertEquals("tag row x", 116f, tabs.left, 1f)
        assertEquals("tag row y", 32f, tabs.top, 1f)

        // The list column spans the focus reserve and runs to the screen bottom.
        val list = compose.onNodeWithTag("channels-list").bounds()
        assertEquals("list column x", 104f, list.left, .5f)
        assertEquals("list column width", 364f, list.width, .5f)
        assertEquals("list runs to the screen bottom", 540f, list.bottom, .5f)

        // Rows: 340dp wide at x116, first at y80, 4dp gaps. Height is the library's
        // standard two-line minimum and grows with text scale rather than clipping.
        val first = row(1).bounds()
        val second = row(2).bounds()
        assertEquals("row x", 116f, first.left, .5f)
        assertEquals("row y", 80f, first.top, .5f)
        assertEquals("row width", 340f, first.width, .5f)
        assertTrue("row respects the library standard minimum (${first.height})", first.height >= 64f)
        assertEquals("row stride", first.height + 4f, second.top - first.top, .5f)

        // Library standard anatomy: 16dp content inset, 60x36 bare picon, 8dp slot gap.
        val picon = inRow(1, "channel-picon").bounds()
        assertEquals("picon x", first.left + 16f, picon.left, .5f)
        assertEquals("picon width", 60f, picon.width, .5f)
        assertEquals("picon height", 36f, picon.height, .5f)
        val title = inRow(1, "channel-title").bounds()
        val subtitle = inRow(1, "channel-programme-start").bounds()
        val progress = inRow(1, "channel-progress").bounds()
        assertEquals("title x", first.left + 84f, title.left, .5f)
        assertEquals("native headline type size", 16f, inRow(1, "channel-title").textLayout().layoutInput.style.fontSize.value, .01f)
        assertEquals("accepted supporting type size", 14f, inRow(1, "channel-programme-title").textLayout().layoutInput.style.fontSize.value, .01f)
        assertEquals("programme line shares the text column", title.left, subtitle.left, .5f)
        assertEquals("progress shares the text column", title.left, progress.left, .5f)
        assertEquals("text column runs to the trailing inset", first.right - 16f, progress.right, .5f)
        assertEquals("progress height", 2f, progress.height, .5f)
        assertEquals("progress 3dp under the programme line", subtitle.bottom + 3f, progress.top, .5f)
        assertTrue("programme line under the title", subtitle.top >= title.bottom - .5f)

        // Markers sit beside the title and appear only when they apply, so the
        // programme line and progress keep one width on every row.
        val playing = inRow(PLAYING, "channel-playing-indicator").bounds()
        val recording = inRow(PLAYING, "channel-recording-indicator").bounds()
        assertTrue("markers inside the row", recording.right <= row(PLAYING).bounds().right - 16f + .5f)
        assertTrue("markers beside the headline", playing.center.y in inRow(PLAYING, "channel-title").bounds().top..inRow(PLAYING, "channel-title").bounds().bottom)
        assertTrue("playing marker precedes the recording dot", playing.right <= recording.left + .5f)
        assertEquals(progress.width, inRow(PLAYING, "channel-progress").bounds().width, .5f)
        val plainTitle = inRow(1, "channel-title").bounds()
        assertTrue(
            "a status marker shortens only the title line",
            inRow(PLAYING, "channel-title").bounds().right < plainTitle.right,
        )

        // The focused row keeps the library's own focus scale, uncancelled, and the
        // list reserves it horizontally instead of clipping.
        enterAndFocus(1)
        val focusedPicon = inRow(1, "channel-picon").bounds()
        assertTrue("focused content is scaled (${focusedPicon.width})", focusedPicon.width > 60f)
        val listColumn = compose.onNodeWithTag("channels-list").bounds()
        val scaleOverflow = (focusedPicon.width / 60f - 1f) * first.width / 2f
        assertTrue("focus scale fits the list column", first.left - scaleOverflow >= listColumn.left)

        // Details anchored at x492, 420 wide, on the bottom safe inset.
        val details = compose.onNodeWithTag("channels-details").bounds()
        assertEquals("details x", 492f, details.left, .5f)
        assertEquals("details width", 420f, details.width, .5f)
        assertEquals("details bottom inset", 508f, details.bottom, .5f)
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun theDetailsColumnCrossfadesBetweenChannelsAndSettlesOnOne() {
        channelsShell(1f)
        enterAndFocus(1)
        detail("channels-detail-channel").assertTextEquals(name(1))

        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(ChannelsDetailCrossfadeMillis / 2L)
        // Both programmes are on screen during the dissolve, and the outgoing copy
        // keeps the channel it was written for instead of adopting the new row's EPG.
        compose.onAllNodesWithText(name(1)).assertCountEquals(1)
        compose.onAllNodesWithText(name(2)).assertCountEquals(1)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onAllNodesWithTag("channels-detail-channel", useUnmergedTree = true).assertCountEquals(1)
        detail("channels-detail-channel").assertTextEquals(name(2))
        row(2).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun longTitlesYieldOnlyTheProgrammeNameAndMissingEpgKeepsTheAnchors() {
        channelsShell(1f)
        enterAndFocus(PLAYING)

        val long = row(LONG_TEXT).bounds()
        val start = inRow(LONG_TEXT, "channel-programme-start").bounds()
        val title = inRow(LONG_TEXT, "channel-programme-title").bounds()
        assertEquals("start time keeps the text origin", long.left + 84f, start.left, .5f)
        assertEquals("title gets all space after start", start.right, title.left, .5f)
        assertEquals("title can use the trailing edge", long.right - 16f, title.right, .5f)
        assertTrue("long programme naturally ellipsizes", inRow(LONG_TEXT, "channel-programme-title").textLayout().isLineEllipsized(0))
        compose.onAllNodesWithTag("channel-programme-left", useUnmergedTree = true).assertCountEquals(0)

        // No EPG: the title line alone, no time segments and no progress, same anchors.
        val missing = row(NO_EPG).bounds()
        inRow(NO_EPG, "channel-programme-title").assertTextEquals("No EPG")
        compose.onAllNodesWithTag("channel-programme-start", useUnmergedTree = true)
            .fetchSemanticsNodes().none { it.boundsInRoot.top in missing.top..missing.bottom }
            .let { assertTrue("no start time on a channel without EPG", it) }
        compose.onAllNodesWithTag("channel-progress", useUnmergedTree = true)
            .fetchSemanticsNodes().none { it.boundsInRoot.top in missing.top..missing.bottom }
            .let { assertTrue("no progress on a channel without EPG", it) }
        assertTrue("row height without EPG (${missing.height})", missing.height >= 64f)
        assertEquals(
            "title anchor without EPG",
            missing.left + 84f,
            inRow(NO_EPG, "channel-title").bounds().left,
            .5f,
        )
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun focusedRowStaysAboveTheFadeWhileLaterRowsRunOutBeneathIt() {
        channelsShell(1f)
        enterAndFocus(PLAYING)

        // The standard row stride still composes a partial row into the fade band.
        val rows = compose.onAllNodes(hasTestTag("channel-title"), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("list draws a partial row beneath the fade", rows.any { it.boundsInRoot.bottom > 492f })

        // Walk to the end: focus never enters the fade, and keeps its breathing room.
        repeat(CHANNEL_COUNT) { key(Key.DirectionDown) }
        row(CHANNEL_COUNT).assertIsFocused()
        val last = row(CHANNEL_COUNT).bounds()
        assertTrue("focused last row bottom ${last.bottom} must clear the fade with 8dp room", last.bottom <= 540f - 56f + 2f)
        assertEquals("list still runs to the bottom", 540f, compose.onNodeWithTag("channels-list").bounds().bottom, .5f)

        // Right from a row has no focus target in the details text.
        key(Key.DirectionRight)
        row(CHANNEL_COUNT).assertIsFocused()
        detail("channels-detail-channel").assertTextEquals(name(CHANNEL_COUNT))
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun enlargedTextGrowsRowsWithoutOverlap() {
        channelsShell(1.3f)
        enterAndFocus(PLAYING)

        val first = row(1).bounds()
        val second = row(2).bounds()
        val title = inRow(1, "channel-title").bounds()
        val subtitle = inRow(1, "channel-programme-start").bounds()
        val progress = inRow(1, "channel-progress").bounds()
        assertTrue("rows grow beyond the standard minimum for large text (${first.height})", first.height > 64f)
        assertTrue("subtitle below title", subtitle.top >= title.bottom - .5f)
        assertTrue("progress below subtitle", progress.top >= subtitle.bottom - .5f)
        assertTrue("progress inside the row", progress.bottom <= first.bottom + .5f)
        assertTrue("rows do not overlap", second.top >= first.bottom + 4f - .5f)
    }

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun fiveDescriptionLinesFitAboveNextAtNormalScale() = checkDescription(1f)

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun fiveDescriptionLinesFitAboveNextAtEnlargedScale() = checkDescription(1.3f)

    private fun checkDescription(scale: Float) {
        channelsShell(scale)
        enterAndFocus(LONG_TEXT)
        assertDescriptionFits()
    }

    private fun assertDescriptionFits() {
        val description = detail("channels-detail-description")
        val layout = description.textLayout()
        assertEquals("five actual rendered description lines", 5, layout.lineCount)
        assertTrue("fifth line remains inside the measured text", layout.getLineBottom(4) <= description.bounds().height + 1f)
        assertTrue("next programme is below the description", detail("channels-detail-next").bounds().top >= description.bounds().bottom + 7f)
        assertTrue("description below progress", description.bounds().top >= detail("channels-detail-progress").bounds().bottom + 7f)
        assertTrue("block grows upward within the details pane", detail("channels-detail-channel").bounds().top >= compose.onNodeWithTag("channels-details").bounds().top)
        assertTrue("next programme remains inside bottom safe inset", detail("channels-detail-next").bounds().bottom <= 508.5f)
    }

    // ---- captures ----

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi") fun captureEnglishNormal() = captureSet("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi") fun captureGermanNormal() = captureSet("de", 1f)
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi") fun captureEnglishEnlarged() = captureSet("en", 1.3f)

    private fun captureSet(locale: String, fontScale: Float) {
        channelsShell(fontScale)
        val prefix = "$locale-font$fontScale"
        val tabLabel = if (locale == "de") "Alle Sender" else "All channels"

        tab(tabLabel).assertIsFocused()
        captureBackgrounds("$prefix-entry-tag-focused")

        enterAndFocus(PLAYING)
        captureBackgrounds("$prefix-row-focused-playing-recording")

        key(Key.DirectionDown)
        row(LONG_TEXT).assertIsFocused()
        assertDescriptionFits()
        captureBackgrounds("$prefix-long-programme-five-lines")

        key(Key.DirectionDown)
        row(NO_EPG).assertIsFocused()
        captureBackgrounds("$prefix-missing-epg")

        repeat(CHANNEL_COUNT) { key(Key.DirectionDown) }
        row(CHANNEL_COUNT).assertIsFocused()
        captureBackgrounds("$prefix-bottom-row-focused")

        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-channels").assertIsFocused()
        captureBackgrounds("$prefix-expanded-drawer")
    }

    private fun captureBackgrounds(name: String) {
        compose.runOnIdle { videoColor = Color.White }
        compose.waitForIdle()
        capture("$name-bright-video")
        compose.runOnIdle { videoColor = Color(0xFF182330) }
        compose.waitForIdle()
        capture("$name-dark-video")
        compose.runOnIdle { videoColor = Color.White }
        compose.waitForIdle()
    }

    // ---- fixture ----

    private var selected by mutableStateOf<ChannelId?>(null)
    private var playbackRequests = 0
    /** The production screen samples System.currentTimeMillis itself; align the fixture with it. */
    private val fixtureNow: Long = System.currentTimeMillis() / 1000L
    private var activeTag by mutableStateOf<ChannelTagId?>(null)

    private fun channelsShell(fontScale: Float) {
        val observation = observation()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    val context = LocalContext.current
                    val imageLoader = remember(context) { testImageLoader(context) }
                    val scope = remember(activeTag) {
                        resolveChannelScopeState(observation.channelState, activeTag)
                    }
                    MainStartupComposition(
                        state = MainStartupCompositionState(MainStartupPresentation.Inactive, ChannelsKey, true),
                        onBack = {}, onAction = {}, registerActivityKeyContract = { {} },
                        showWarmPlaybackScrim = true,
                        persistentSurface = { Box(Modifier.fillMaxSize().background(videoColor)) },
                        navigation = { _, _ ->
                        SideRail(
                            currentRoute = AppDestination.CHANNELS,
                            showEpgMenu = true,
                            onRootBack = {},
                            onNavigate = {},
                        ) { padding, drawerActive ->
                            ChannelsScreenContent(
                                contentPadding = padding,
                                initialFocusEnabled = !drawerActive,
                                channelScopeState = scope,
                                observation = observation,
                                tagNotice = false,
                                selectedId = { selected },
                                imageLoader = imageLoader,
                                playingChannelId = ChannelId(PLAYING.toLong()),
                                playbackIndicator = playbackIndicator,
                                connectionUiState = ConnectionUiState.Ready,
                                onSelectChannel = { selected = it },
                                onSelectTag = { activeTag = it },
                                onDismissTagNotice = {},
                                onRetryConnection = {},
                                onOpenConnectionSettings = {},
                                 onPlay = { _, _ -> playbackRequests++ },
                            )
                        }
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun observation(): SessionObservation {
        val channels = (1..CHANNEL_COUNT).map { n ->
            Channel.create(
                id = ChannelId(n.toLong()),
                name = name(n),
                number = n.toLong(),
                icon = "imagecache/${(n - 1) % 3 + 1}",
                tagIds = buildList {
                    if (n <= 6) add(ChannelTagId(1))
                    if (n in 7..10) add(ChannelTagId(2))
                },
            )
        }
        val tags = listOf(
            ChannelTag.create(ChannelTagId(1), name = "Favourites", channelIds = channels.take(6).map { it.id }),
            ChannelTag.create(ChannelTagId(2), name = "Sport", channelIds = channels.drop(6).take(4).map { it.id }),
        )
        val events = buildList {
            for (n in 1..CHANNEL_COUNT) {
                if (n == NO_EPG) continue
                val start = programmeStart(n)
                val stop = programmeStop(n)
                add(
                    EpgEvent.create(
                        id = EventId(n * 10L),
                        channelId = ChannelId(n.toLong()),
                        start = Instant.fromEpochSeconds(start),
                        stop = Instant.fromEpochSeconds(stop),
                        title = programmeTitle(n),
                        summary = ("A programme summary long enough to exercise the bounded description " +
                            "lines of the focused details block, wrapping across several lines at 14sp so " +
                            "five complete lines remain available above the next programme. ").repeat(4),
                        genre = "Drama",
                        nextEventId = EventId(n * 10L + 1),
                    ),
                )
                add(
                    EpgEvent.create(
                        id = EventId(n * 10L + 1),
                        channelId = ChannelId(n.toLong()),
                        start = Instant.fromEpochSeconds(stop),
                        stop = Instant.fromEpochSeconds(stop + 45 * 60),
                        title = "Late Evening News and Weather Report for Region $n",
                    ),
                )
            }
        }
        val recordings = listOf(PLAYING, RECORDING_ONLY).map { n ->
            DvrEntry.create(
                id = DvrEntryId(n.toLong()),
                channelId = ChannelId(n.toLong()),
                state = DvrEntryState.RECORDING,
                title = programmeTitle(n),
            )
        }
        return SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.ALLOWED,
                ),
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels, tags)),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events)),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(recordings)),
        )
    }

    /** Staggered current programmes: channel n began (32 - n) minutes before the fixture clock. */
    private fun programmeStart(n: Int): Long = fixtureNow - (32 - n) * 60L
    private fun programmeStop(n: Int): Long = programmeStart(n) + 90 * 60L

    private fun name(n: Int): String = when (n) {
        1 -> "ORF 1 HD"
        2 -> "ORF 2 HD"
        3 -> "ZDF HD"
        LONG_TEXT -> "Ein außergewöhnlich langer Sendername der niemals in eine Zeile passt HD"
        NO_EPG -> "Regional TV"
        6 -> "Das Erste HD"
        7 -> "arte HD"
        8 -> "3sat HD"
        9 -> "ServusTV HD"
        10 -> "ProSieben Austria"
        11 -> "RTL Austria"
        12 -> "Sky Sport Austria 1"
        13 -> "ORF Sport +"
        else -> "Channel $n"
    }

    private fun programmeTitle(n: Int): String = when (n) {
        LONG_TEXT -> "The Extraordinarily Long Programme Title That Cannot Possibly Fit In One Row"
        1 -> "Zeit im Bild"
        2 -> "Universum: Wildes Österreich"
        3 -> "heute journal"
        6 -> "Tagesthemen"
        else -> "Programme on ${name(n)}"
    }

    /**
     * Test-only picon source: the production ImageLoader components stay untouched; here
     * every catalog icon maps to generated PNG bytes with a distinct aspect ratio (5:3,
     * 1:1, 4:1) so ContentScale.Fit is visible in the captures. Coil runs on the main
     * looper so the compose rule's idle also settles the image loads.
     */
    private fun testImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(PiconBytesMapper()) }
            .coroutineContext(Dispatchers.Main.immediate)
            .fetcherCoroutineContext(Dispatchers.Main.immediate)
            .decoderCoroutineContext(Dispatchers.Main.immediate)
            .diskCache(null)
            .build()

    private class PiconBytesMapper : Mapper<AppArtworkSource, ByteArray> {
        override fun map(data: AppArtworkSource, options: Options): ByteArray {
            val variant = data.selector.removePrefix("imagecache/").toInt()
            val (width, height, tint) = when (variant) {
                1 -> Triple(120, 72, 0xFFE8F1F5.toInt())
                2 -> Triple(72, 72, 0xFFF6C453.toInt())
                else -> Triple(240, 60, 0xFF8FD3FF.toInt())
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), height / 6f, height / 6f, paint)
            paint.color = 0xFF1D2022.toInt()
            paint.textSize = height * 0.55f
            paint.isFakeBoldText = true
            canvas.drawText("TV$variant", width * 0.12f, height * 0.7f, paint)
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
    }

    // ---- helpers ----

    /** Down from the tags enters the first-channel anchor; further Downs reach row [n]. */
    private fun enterAndFocus(n: Int) {
        key(Key.DirectionDown)
        row(1).assertIsFocused()
        repeat(n - 1) { key(Key.DirectionDown) }
        row(n).assertIsFocused()
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun tab(label: String) = compose.onNodeWithText(label)
    private fun row(n: Int) = compose.onNodeWithTag("channel-row-$n")
    private fun detail(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    /** Unmerged descendant [tag] of channel row [n]; rows merge their semantics. */
    private fun inRow(n: Int, tag: String): SemanticsNodeInteraction {
        val rowBounds = row(n).bounds()
        val all = compose.onAllNodesWithTag(tag, useUnmergedTree = true)
        val indices = all.fetchSemanticsNodes().withIndex()
            .filter { it.value.boundsInRoot.center.y in rowBounds.top..rowBounds.bottom }
            .map { it.index }
        check(indices.size == 1) { "expected one '$tag' in row $n, found ${indices.size}" }
        return all[indices.single()]
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun drawShell(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun capture(name: String) {
        val bitmap = drawShell()
        compose.waitForIdle()
        assertTrue("$name was captured mid-animation", bitmap.sameAs(drawShell()))
        val directory = File(CAPTURE_DIRECTORY).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        File(directory, "$name.txt").writeText(
            "canvas=960x540 logical; pixels=${bitmap.width}x${bitmap.height}; density=1.0; " +
                "locale=${name.substringBefore("-font")}; fontScale=${name.substringAfter("-font").substringBefore("-")}; " +
                "focus/scenario=${name.substringAfter("-font").substringAfter("-").removeSuffix("-bright-video").removeSuffix("-dark-video")}; " +
                "background=${if (name.endsWith("bright-video")) "white #FFFFFF" else "dark #182330"}; " +
                "production=MainStartupComposition+SideRail+ChannelsScreenContent; " +
                "synthetic video; static offline composition only\n",
        )
    }

    private companion object {
        const val CAPTURE_DIRECTORY = "../artifacts/channels-standard"
        const val CHANNEL_COUNT = 14
        const val PLAYING = 3
        const val LONG_TEXT = 4
        const val NO_EPG = 5
        const val RECORDING_ONLY = 6
    }
}
