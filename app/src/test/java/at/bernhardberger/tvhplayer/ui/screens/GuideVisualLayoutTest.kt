package at.bernhardberger.tvhplayer.ui.screens


import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.settleFrames
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
import kotlinx.coroutines.test.StandardTestDispatcher

/** Offline native production composition; never a replacement Guide or a playback test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi",
    instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideVisualLayoutTest : GuideScreenFixture() {
    /** The README scene: the fixture pack's line-up on 2026-10-04 with Ridge Light on air at 20:15, xhdpi. */
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun presentation() = presentationScene(store = false)

    /** Store guide: the news row separates this blue studio scene from the live match. */
    @Test @Config(qualifiers = "en-rUS-w960dp-h540dp-land-xhdpi")
    @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun storeGuide() = presentationScene(store = true)

    private fun presentationScene(store: Boolean) {
        val names = listOf("Ridge Earth", "Harbor Sport", "Northline News", "Kite Kids", "Ion Frame", "Amber Stage", "Harbor Lights")
        val picons = listOf("ridge-earth", "harbor-sport", "northline-news", "kite-kids", "ion-frame", "amber-stage", "harbor-lights")
        val art = listOf("ridge-light", "harbor-kickoff", "northline-tonight", "field-desk", "paper-kite-tales", "little-orbit",
            "ion-wake", "lantern-road", "kettle-hour", "glass-harbor", "harbor-lights-live", "river-court")
        // Channel, start and stop in minutes from 20:00, title, DVB content type, synopsis.
        val schedule = listOf(
            Triple(1, -60 to 0, Triple("Tide Watch", 0x91L, "Coastal wildlife along a tidal estuary, wading birds and seals.")),
            Triple(1, 0 to 60, Triple("Ridge Light", 0x91L, "Seasonal mountain light, forests and weather moving over a ridge.")),
            Triple(1, 60 to 120, Triple("Tide Watch", 0x91L, "Coastal wildlife along a tidal estuary, wading birds and seals.")),
            Triple(2, -30 to 105, Triple("Harbor Kickoff", 0x43L, "Match coverage from a floodlit stadium as Harbor FC meet Amber Town.")),
            Triple(2, 105 to 210, Triple("River Court", 0x44L, "Indoor basketball, River Court against North Quay.")),
            Triple(3, -45 to 0, Triple("Skyglass Weather", 0x20L, "Weather presentation.")),
            Triple(3, 0 to 45, Triple("Northline Tonight", 0x21L, "Evening studio news from a regional desk with two anchors.")),
            Triple(3, 45 to 90, Triple("Field Desk", 0x23L, "Field reporting from a flooded riverside road.")),
            Triple(3, 90 to 105, Triple("Skyglass Weather", 0x20L, "Weather presentation.")),
            Triple(3, 105 to 180, Triple("Northline Tonight", 0x21L, "Evening studio news from a regional desk with two anchors.")),
            Triple(4, -50 to 5, Triple("Little Orbit", 0x55L, "A gentle science table about orbits using marbles and a bowl.")),
            Triple(4, 5 to 50, Triple("Paper Kite Tales", 0x55L, "2D animation about a paper kite over painted hills.")),
            Triple(4, 50 to 120, Triple("Little Orbit", 0x55L, "A gentle science table about orbits using marbles and a bowl.")),
            Triple(5, -40 to 25, Triple("Ion Wake", 0x12L, "Sci-fi anime: a courier rides a magnetic rail through Ion Harbor.")),
            Triple(5, 25 to 95, Triple("Lantern Road", 0x13L, "Fantasy anime: a cartographer maps a valley from a lantern sky-bridge.")),
            Triple(5, 95 to 160, Triple("Ion Wake", 0x12L, "Sci-fi anime: a courier rides a magnetic rail through Ion Harbor.")),
            Triple(6, -60 to 30, Triple("The Kettle Hour", 0xA5L, "A warm kitchen programme about broths, bread and weeknight pans.")),
            Triple(6, 30 to 150, Triple("Glass Harbor", 0x10L, "A quiet drama about night-shift workers in a harbour glass workshop.")),
            Triple(7, -60 to 120, Triple("Harbor Lights Live", 0x63L, "A small-hall concert with a quartet.")),
        )
        val pictures = mapOf("Ridge Light" to "ridge-light", "Harbor Kickoff" to "harbor-kickoff", "Northline Tonight" to "northline-tonight",
            "Field Desk" to "field-desk", "Paper Kite Tales" to "paper-kite-tales", "Little Orbit" to "little-orbit",
            "Ion Wake" to "ion-wake", "Lantern Road" to "lantern-road", "The Kettle Hour" to "kettle-hour",
            "Glass Harbor" to "glass-harbor", "Harbor Lights Live" to "harbor-lights-live", "River Court" to "river-court")
        val events = schedule.mapIndexed { index, (channel, minutes, programme) ->
            val (title, contentType, synopsis) = programme
            EpgEvent.create(EventId(1000L + index), ChannelId(channel.toLong()),
                Instant.fromEpochSeconds(hour + minutes.first * 60L), Instant.fromEpochSeconds(hour + minutes.second * 60L),
                title = title, summary = synopsis,
                subtitle = "Winter on the ridge".takeIf { title == "Ridge Light" },
                image = pictures[title]?.let { ArtworkId(101 + art.indexOf(it)) },
                // Ridge Light carries the same XMLTV genre as on the Channels card and details page, so the headline says Documentary.
                genre = "Documentary".takeIf { title == "Ridge Light" },
                contentGenre = ContentGenre.fromContentType(contentType).takeUnless { title == "Ridge Light" })
        }
        loader.shutdown()
        loader = at.bernhardberger.tvhplayer.testutil.FixtureArt.imageLoader(context(), buildMap {
            picons.forEachIndexed { index, name -> put(ArtworkId(index + 1), at.bernhardberger.tvhplayer.testutil.FixtureArt.picon(name)) }
            art.forEachIndexed { index, name ->
                // The title-free studio still leaves the Guide's native header chips clear.
                val image = if (store && name == "northline-tonight") "still-northline-studio" else name
                put(ArtworkId(101 + index), at.bernhardberger.tvhplayer.testutil.FixtureArt.art(image))
            }
        })
        images.install(loader)
        // Harbor Kickoff is recording now and the 21:00 Tide Watch on Ridge Earth is scheduled.
        val recordings = listOf(
            DvrEntry.create(DvrEntryId(1), eventId = events[3].id, channelId = ChannelId(2), state = DvrEntryState.RECORDING),
            DvrEntry.create(DvrEntryId(2), eventId = events[2].id, channelId = ChannelId(1), state = DvrEntryState.SCHEDULED),
        )
        show(headline = true, rows = GuideRows.DENSE, history = false, channelNames = names, events = events, tagName = "Favorites", recordings = recordings)
        key(Key.DirectionDown)
        focused("Ridge Light")
        if (store) {
            repeat(2) { key(Key.DirectionDown) }
            focused("Northline Tonight")
            // Let the settled headline's image request and its subsequent crossfade each finish.
            compose.settleFrames(20)
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        capture(if (store) "store-guide" else "guide", 1f, LayoutDirection.Ltr, outputs = "build/outputs/presentation").recycle()
    }

    @Test fun normalGeometryAndContinuousNowAboveNativeFocus() {
        show()
        val ruler = bounds("epg-time-ruler")
        assertEquals(242f, ruler.left, .1f)
        assertEquals(718f, ruler.width, .1f)
        assertEquals(73f, ruler.top, .1f)
        assertEquals(rulerHeight, ruler.height, .1f)
        val first = bounds("epg-channel-row-1")
        assertEquals(130f, first.left, .1f)
        // The header draws no title; Tags names the whole channel set.
        compose.onNode(hasText(context().getString(R.string.epg_title)) and hasAnyAncestor(hasTestTag("channels-header")), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("channels-tags-button").assertContentDescriptionEquals(context().getString(
            R.string.header_button_value_description, context().getString(R.string.browse_tags), context().getString(R.string.all_channels)))
        assertEquals("Shorter ruler and removed spacer move the first row up",
            108f - (previousRulerHeight - rulerHeight + 4f) + 5f, first.top, .1f)
        assertEquals("Dense rows by default", 56f, first.height, .1f)
        assertEquals(first.top + first.height + 4f, bounds("epg-channel-row-2").top, .1f)
        assertEquals(130f, bounds("epg-channel-header-1").left, .1f)
        assertEquals(234f, bounds("epg-channel-header-1").right, .1f)
        compose.onNodeWithText("Channels", substring = false).assertDoesNotExist()
        key(Key.DirectionDown)
        focused(title(1, 0))
        val bitmap = capture("guide-normal", 1f, LayoutDirection.Ltr)
        assertChannelHeader(bitmap, 1f)
        val start = checkNotNull(position.position.value).windowStartSec
        val x = ruler.left + guideTimePositionPx(now, start, start + 3 * 3600, ruler.width)
        assertLine(bitmap, x, 162, 166, 222, 226, 282, 286, 342)
        val cell = compose.onNodeWithText(title(1, 0)).fetchSemanticsNode().boundsInRoot
        val boundary = bounds("epg-ruler-boundary-${hour}")
        assertEquals("The 20:00 boundary line is where its programmes start", cell.left, boundary.left, .5f)
        // Now (20:15) carries its clock pill over the 20:00 label, so the label checks use 21:00.
        rulerLabel("20:00").assertIsNotDisplayed()
        val label = rulerLabel("21:00").fetchSemanticsNode()
        val labelBoundary = bounds("epg-ruler-boundary-${hour + 3600}")
        assertEquals("The boundary spans the ruler band", ruler.top, boundary.top, .5f)
        assertEquals("The boundary spans the ruler band", ruler.bottom, boundary.bottom, .5f)
        assertEquals("The label starts at its boundary, after the label's 6dp inset", labelBoundary.left + 6f,
            label.boundsInRoot.left, .5f)
        assertEquals("The shared baseline line sits centred in the band", ruler.center.y,
            (label.boundsInRoot.top + label.boundsInRoot.bottom) / 2, 1f)
        val firstRow = bounds("epg-channel-row-1")
        val secondRow = bounds("epg-channel-row-2")
        assertEquals("One 4dp gutter between rows", 4f, secondRow.top - firstRow.bottom, .1f)
        assertEquals("Cells fill their row", firstRow.height, cell.height, .1f)
        val dotY = ruler.bottom.toInt() - 4
        assertEquals("Ruler dot and the continuous line share one mapping", lineColor, bitmap.getPixel(x.toInt(), dotY))
        assertEquals("No line above the lifted pill", backgroundColor, bitmap.getPixel(x.toInt(), 62))
        assertReadableFocus()
        bitmap.recycle()
        liftedNowPill(1f, showGuide = false)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("programme-details-page").assertIsDisplayed()
        compose.onNodeWithText(context().getString(R.string.close)).assertDoesNotExist()
        assertNoPlayback()
    }

    @Test fun aCellShowsOnlyItsStartTimeAndSpeaksItsWholeRange() {
        show(shortSecondMinutes = 3)
        val cell = hasContentDescription(title(1, 0), substring = true)
        compose.onNode(hasTestTag("guide-cell-time") and hasAnyAncestor(cell), useUnmergedTree = true)
            .assertIsDisplayed().assertTextEquals(formatHm(hour))
        compose.onNode(cell).assert(hasContentDescription(formatHm(hour + 3600), substring = true))
        // A three-minute programme has no room even for its start; its title still shows.
        val short = hasContentDescription(title(1, 1), substring = true)
        compose.onNode(hasTestTag("guide-cell-time") and hasAnyAncestor(short), useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNode(short).assert(hasContentDescription(formatHm(hour + 3600 + 3 * 60), substring = true))
        assertNoPlayback()
    }

    @Test fun theNowLineStaysSolidUnderTheTopFade() {
        show()
        key(Key.DirectionDown)
        focused(title(1, 0))
        repeat(7) { key(Key.DirectionDown) }
        focused(title(9, 0))
        val viewport = bounds("epg-timeline-content")
        assertTrue("The first row has scrolled under the top fade",
            compose.onAllNodesWithTag("epg-channel-row-1").fetchSemanticsNodes().singleOrNull()
                ?.let { it.boundsInRoot.top < viewport.top } ?: true)
        val bitmap = capture("guide-now-line-scrolled", 1f, LayoutDirection.Ltr)
        val ruler = bounds("epg-time-ruler")
        val start = checkNotNull(position.position.value).windowStartSec
        val x = ruler.left + guideTimePositionPx(now, start, start + 3 * 3600, ruler.width)
        // Cells fade to the backdrop at the top edge; the line beside them keeps its full color.
        assertNearBackground(bitmap.getPixel(750, viewport.top.toInt()), 8)
        assertEquals(lineColor, bitmap.getPixel(x.toInt(), viewport.top.toInt() + 1))
        assertEquals(lineColor, bitmap.getPixel(x.toInt(), viewport.top.toInt() + 24))
        bitmap.recycle()
    }

    @Test fun theShortRulerKeepsTheLiftedNowPillAtLargeText() = liftedNowPill(1.3f)

    @Test fun theHeadlineOffNowPillClearsTheWideTagsButton() = headlineOffPillClearance(1f)

    @Test fun theHeadlineOffNowPillClearsTheWideTagsButtonAtLargeText() = headlineOffPillClearance(1.3f)

    private fun headlineOffPillClearance(scale: Float) {
        val start = now - if (scale == 1f) 7200 else 4800
        position.save(GuidePosition(ChannelId(1), EventId(110), hour, start, 0))
        show(scale = scale, activeTag = true, tagName = "Documentaries and international current affairs",
            playerReturn = savedTimelineReturn())
        val tags = compose.onNodeWithTag("channels-tags-button")
        tags.assertIsNotFocused()
        val resting = bounds("channels-tags-button")
        val pill = bounds("epg-now-pill")
        assertTrue("Now must sit under Tags horizontally: $pill / $resting",
            pill.center.x in resting.left..resting.right)
        assertTrue("The pill clears the resting Tags button: $pill / $resting", pill.top >= resting.bottom)
        tags.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        tags.assertIsFocused()
        val focused = bounds("channels-tags-button")
        // TV Material scales the drawing inside the semantics node, not its layout bounds.
        val focusedBottom = focused.bottom + focused.height * ((1.1 - 1.0) / 2).toFloat()
        assertTrue("The pill clears the scaled Tags button: $pill / bottom=$focusedBottom", pill.top >= focusedBottom)
        if (scale == 1f) capture("guide-headline-off-focused-tags-font$scale", scale, LayoutDirection.Ltr).recycle()
    }

    private fun liftedNowPill(scale: Float, showGuide: Boolean = true) {
        if (showGuide) show(scale = scale)
        val ruler = bounds("epg-time-ruler")
        assertEquals("Ruler follows labelSmall line height plus 4dp", rulerHeight, ruler.height, .5f)
        val pill = bounds("epg-now-pill")
        val label = rulerLabel("21:00").fetchSemanticsNode().boundsInRoot
        assertTrue("The pill sits above the labels: pill=$pill label=$label", pill.center.y < label.center.y)
        assertEquals("The pill retains its 4dp lift", 4f, label.center.y - pill.center.y, .5f)
        assertTrue("The lifted pill stays on screen", pill.top >= 0f)
        assertTrue("The pill draws above the band", pill.top < ruler.top)
        val stem = bounds("epg-now-stem")
        assertEquals("The line starts below the pill", pill.bottom, stem.top, .5f)
        assertEquals("The line runs to the ruler's bottom", ruler.bottom, stem.bottom, .5f)
        val bitmap = capture("guide-lifted-pill-font$scale", scale, LayoutDirection.Ltr)
        assertEquals("The pill paints above the ruler without clipping", lineColor,
            bitmap.getPixel(pill.center.x.toInt(), pill.top.toInt() + 1))
        bitmap.recycle()
        // The 20:00 label beside Now (20:15) would touch the pill, so it is left out.
        if (scale == 1f) rulerLabel("20:00").assertIsNotDisplayed()
    }

    @Test fun theDateButtonSaysNowWhileTheWindowHoldsItAndOtherwiseItsHalfHour() {
        show()
        assertDateButtonNow()
        compose.onNodeWithTag("guide-date-button").assert(hasContentDescription(context().getString(
            R.string.epg_date_button_description, context().getString(R.string.today), context().getString(R.string.now))))
        key(Key.DirectionDown)
        focused(title(1, 0))
        // The fourth programme (23:00) moves the window past now, to a start off the half hour.
        repeat(3) { offset ->
            key(Key.DirectionRight)
            focused(title(1, offset + 1))
        }
        focused(title(1, 3))
        val start = checkNotNull(position.position.value).windowStartSec
        assertTrue("The window starts at ${Instant.fromEpochSeconds(start)}", start % 1800 != 0L && start > now)
        compose.waitUntil(5_000) { dateButtonShows("Today", start - start % 1800) }
        assertNoPlayback()
    }

    @Test fun theGridOpensOnTheProgrammeOnAirHalfAnHourAfterTheWindowStarts() {
        // Channel 1's programme runs 20:00 to 22:00, past the window's readable end.
        show(longProgramme = true, longProgrammeHours = 2)
        key(Key.DirectionDown)
        focused(title(1, 0))
        val start = checkNotNull(position.position.value).windowStartSec
        // Now (20:15) less half an hour is 19:45, down to the half hour 19:30.
        assertEquals("Half an hour before now, down to the half hour", hour - 1800, start)
        val share = (now - start).toFloat() / (3 * 3600)
        assertTrue("Now sits in the window's first third: $share", share in .15f..0.34f)
        // Up and Down keep now: the programmes on air, with no window jump.
        key(Key.DirectionDown)
        focused(title(2, 0))
        key(Key.DirectionUp)
        focused(title(1, 0))
        assertEquals(start, checkNotNull(position.position.value).windowStartSec)
        assertReadableFocus()
        assertNoPlayback()
    }

    @Test fun aFocusedProgrammeThatEndsWhileTheWindowKeepsItForNowMovesClearOfTheFade() {
        // 19:30-20:30 is on air at 20:15 and starts with the 19:30 window, under its fade.
        show(eventHour = hour - 1800)
        key(Key.DirectionDown)
        focused(title(1, 0))
        val start = checkNotNull(position.position.value).windowStartSec
        assertEquals("The window keeps now for the programme on air", hour - 1800, start)
        assertReadableFocus()
        // It ends with no new EPG data: only the clock moves on.
        assertTrue(SystemClock.setCurrentTimeMillis((hour + 31 * 60) * 1000))
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        focused(title(1, 0))
        assertReadableFocus()
        assertTrue("The window moved to the ended programme",
            checkNotNull(position.position.value).windowStartSec < start)
        assertNoPlayback()
    }

    @Test fun aConnectingGuideShowsItsSkeletonAndNoLoadingText() {
        show(connecting = true)
        // The preparation anchor draws a skeleton too; the capture needs the screen's own, which
        // takes focus once its background preparation lands.
        compose.waitForIdle()
        compose.onNodeWithTag("browse-preparing", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("guide-skeleton", useUnmergedTree = true).assertIsDisplayed()
            .assertContentDescriptionEquals(context().getString(R.string.epg_loading))
        compose.onAllNodesWithText(context().getString(R.string.epg_loading)).assertCountEquals(0)
        compose.onAllNodesWithText(context().getString(R.string.loading)).assertCountEquals(0)
        compose.onNodeWithTag("guide-empty-state").assertDoesNotExist()
    }

    @Test fun theSkeletonKeepsTheGridWhereTheReadyGuidePutsItAtLargeText() = skeletonHandsOverInPlace(1.3f)

    @Test fun theSkeletonKeepsTheHeadlineAndTheGridWhereTheReadyGuidePutsThemAtLargeText() =
        skeletonHandsOverInPlace(1.3f, headline = true)

    private fun skeletonHandsOverInPlace(scale: Float, headline: Boolean = false) {
        show(scale = scale, connecting = true, headline = headline)
        compose.waitForIdle()
        // A new session starts a new preparation, which shows the whole-screen skeleton, header
        // row included, for at least the frame that sees the session.
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { preparationContext = StandardTestDispatcher() }
        compose.runOnIdle { session.publish(readyObservation) }
        compose.mainClock.advanceTimeByFrame()
        val preparing = hasTestTag("guide-skeleton") and hasAnyAncestor(hasTestTag("browse-preparing"))
        val skeletonTop = compose.onNode(preparing, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val skeletonHeadline = if (!headline) null else compose.onNode(hasTestTag("guide-headline") and
            hasAnyAncestor(hasTestTag("browse-preparing")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = true
        releasePreparation()
        dateFocused()
        compose.onNodeWithTag("browse-preparing", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("epg-time-ruler").assertExists()
        assertEquals("The ruler stays where the skeleton drew it at font $scale", skeletonTop, bounds("epg-time-ruler").top, .5f)
        if (skeletonHeadline != null) {
            val ready = bounds("guide-headline")
            assertEquals("The headline starts where the skeleton's did", skeletonHeadline.top, ready.top, .5f)
            assertEquals("The headline ends where the skeleton's did", skeletonHeadline.bottom, ready.bottom, .5f)
        }
    }

    @Test fun middleRowFocusRingStaysInsideItsRow() {
        show()
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionDown)
        focused(title(2, 0))
        val bitmap = capture("guide-middle-focus", 1f, LayoutDirection.Ltr)
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val native = nativeFocusBounds(bitmap)
        assertEquals("The focused cell keeps its row height", focus.height, native.height, 1f)
        assertEquals("The ring starts at the row's top", focus.top, native.top, 1f)
        assertEquals("The ring ends at the row's bottom", focus.bottom, native.bottom, 1f)
        val edgeX = native.right.toInt() - 1
        val edgeY = focus.center.y.toInt()
        val edgePixel = bitmap.getPixel(edgeX, edgeY)
        assertTrue("Adjacent programme must not cover the focus ring at ($edgeX,$edgeY)",
            android.graphics.Color.red(edgePixel) > 180 && android.graphics.Color.green(edgePixel) > 180 &&
                android.graphics.Color.blue(edgePixel) > 180)
        bitmap.recycle()
        assertNoPlayback()
    }

    @Test fun logoAndNoLogoHeadersKeepSingleLineContentAccessibleAsFontScaleGrows() {
        val logoName = "Kultur und Dokumentationen aus aller Welt HD"
        val noLogoName = "Northline News: International Headlines and Analysis"
        val logoChannel = Channel.create(ChannelId(1), name = logoName, number = 1001, icon = ChannelIcon.ImageCache(ArtworkId(1)))
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
                assertEquals(104f, header.fetchSemanticsNode().boundsInRoot.width, .1f)
                assertEquals("Row grows with font scale $fontScale", rowHeight, header.fetchSemanticsNode().boundsInRoot.height, .1f)
            }
            compose.onNodeWithText(logoName, useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("epg-channel-picon-2", useUnmergedTree = true).assertDoesNotExist()
            val logo = bounds("epg-channel-picon-1")
            val logoNumber = compose.onNodeWithText("1001", useUnmergedTree = true)
            val numberText = textLayout(logoNumber.fetchSemanticsNode())
            assertEquals("1001", numberText.layoutInput.text.text)
            assertEquals(1, numberText.lineCount)
            assertFalse("Four-digit number must not truncate at $fontScale", numberText.hasVisualOverflow)
            assertFalse(numberText.isLineEllipsized(0))
            val logoNumberBounds = logoNumber.fetchSemanticsNode().boundsInRoot
            assertEquals(bounds("epg-channel-header-1").left + 12f, logoNumberBounds.left, .1f)
            assertEquals(logoNumberBounds.right + 8f, logo.left, .1f)
            assertEquals("Logo yields width to the complete channel number", minOf(48f, 80f - logoNumberBounds.width - 8f), logo.width, .1f)
            assertEquals(28f, logo.height, .1f)
            // Odd pixel row/text heights can centre an even-height logo half a pixel away.
            assertEquals(bounds("epg-channel-header-1").center.y, logo.center.y, .5f)
            val name = compose.onNodeWithText(noLogoName, useUnmergedTree = true)
            val text = textLayout(name.fetchSemanticsNode())
            assertEquals(1, text.lineCount)
            assertTrue("Long no-logo names ellipsize on their only line", text.isLineEllipsized(0))
            val nameBounds = name.fetchSemanticsNode().boundsInRoot
            val numberNode = compose.onNodeWithText("4567", useUnmergedTree = true)
            val number = numberNode.fetchSemanticsNode().boundsInRoot
            val noLogoNumberText = textLayout(numberNode.fetchSemanticsNode())
            assertFalse("No-logo four-digit number must not truncate at $fontScale", noLogoNumberText.hasVisualOverflow)
            assertFalse(noLogoNumberText.isLineEllipsized(0))
            assertEquals(bounds("epg-channel-header-2").left + 12f, number.left, .1f)
            assertEquals(number.right + 8f, nameBounds.left, .1f)
            assertEquals(bounds("epg-channel-header-2").right - 12f, nameBounds.right, .1f)
            assertEquals(number.center.y, nameBounds.center.y, .5f)
            assertEquals(bounds("epg-channel-header-2").center.y, nameBounds.center.y, .5f)
            assertTrue("The complete name line fits at $fontScale", text.getLineBottom(0) <= nameBounds.height)
            assertTrue(nameBounds.bottom <= bounds("epg-channel-header-2").bottom - 4f)
        }
    }

    @Test fun hiddenNumbersLeaveGuideLogosNamesAndUnnamedFallback() {
        val channels = listOf(
            Channel.create(ChannelId(1), name = "Ridge Earth", number = 1001, icon = ChannelIcon.ImageCache(ArtworkId(1))),
            Channel.create(ChannelId(2), name = "Northline News", number = 4567),
            Channel.create(ChannelId(3), name = "", number = 89),
        )
        val currentSession = headerSession()
        compose.setContent {
            TVHeadendPlayerTheme {
                Column {
                    channels.forEach { channel ->
                        Box(Modifier.height(guideTimelineRowHeight())) {
                            ProvideArtworkSession(currentSession) {
                                TimelineChannelHeader(channel, channel.number)
                            }
                        }
                    }
                }
            }
        }
        try {
            compose.onNodeWithText("1001", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("4567", useUnmergedTree = true).assertExists()
            compose.runOnIdle { at.bernhardberger.tvhplayer.ui.components.AppChannelNumbers.shown = false }
            compose.onNodeWithText("1001", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("4567", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("epg-channel-picon-1", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Northline News", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("89", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("epg-channel-header-1").assertContentDescriptionEquals("Ridge Earth")
            compose.onNodeWithTag("epg-channel-header-2").assertContentDescriptionEquals("Northline News")
            compose.onNodeWithTag("epg-channel-header-3").assertContentDescriptionEquals("89")
            compose.runOnIdle { at.bernhardberger.tvhplayer.ui.components.AppChannelNumbers.shown = true }
            compose.onNodeWithText("1001", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("4567", useUnmergedTree = true).assertExists()
        } finally {
            compose.runOnIdle { at.bernhardberger.tvhplayer.ui.components.AppChannelNumbers.shown = true }
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
                Box(Modifier.width(732.dp).background(androidx.compose.ui.graphics.Color.Black)) {
                    TimelineChannelRow(channel, 0, 1, null, requesters, 0, 7200,
                        { 0 }, events, true, ConnectionUiState.Ready, false,
                        { null }, {}, {})
                }
            }
        }
        val first = compose.onNodeWithText("First programme").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Second programme").fetchSemanticsNode().boundsInRoot
        assertEquals("Time allocations still start at the timeline edge", 112f, first.left, .1f)
        assertEquals(310f, first.width, .1f)
        assertEquals(first.right, second.left, .1f)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val y = first.center.y.toInt()
            val gapColor = bitmap.getPixel(422, y)
            val cellColor = bitmap.getPixel(419, y)
            assertNotEquals("Surfaces and timeline gap must be visibly distinct", gapColor, cellColor)
            assertEquals("No row track: the gutter shows the page", android.graphics.Color.BLACK, gapColor)
            for (x in 420 until 424) assertEquals("Exactly four gap pixels at x=$x", gapColor, bitmap.getPixel(x, y))
            assertEquals(cellColor, bitmap.getPixel(424, y))
            assertEquals("First surface has the same 2dp leading inset", gapColor, bitmap.getPixel(113, y))
            assertEquals(cellColor, bitmap.getPixel(114, y))
            bitmap.recycle()
        }
    }

    @Test fun nowPillShowsTheClockAtNowAndHidesTheLabelItWouldCover() {
        val nowSec = androidx.compose.runtime.mutableLongStateOf(1750L)
        compose.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.width(732.dp).padding(top = 12.dp)) {
                    TimelineTimeRuler(0, 7200, { nowSec.longValue })
                }
            }
        }
        val ruler = bounds("epg-time-ruler")
        val pill = bounds("epg-now-pill")
        compose.onNode(hasText(formatClock(1750L)) and hasAnyAncestor(hasTestTag("epg-now-pill")), useUnmergedTree = true)
            .assertIsDisplayed()
        assertEquals("The pill is centred on now", ruler.left + guideTimePositionPx(1750L, 0, 7200, ruler.width),
            pill.center.x, 1f)
        assertTrue("The lifted pill stays in the visible area", pill.top >= 0f && pill.bottom <= ruler.bottom)
        assertTrue("The pill draws above the band", pill.top < ruler.top)
        assertTrue("The covered label starts under the pill",
            ruler.left + guideTimePositionPx(1800L, 0, 7200, ruler.width) < pill.right)
        compose.onNodeWithText(formatHm(1800L), useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNodeWithText(formatHm(0L), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(formatHm(3600L), useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { nowSec.longValue = 7300L }
        compose.onNodeWithTag("epg-now-pill", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(formatHm(1800L), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun remoteLogoBeforeReadyFallsBackToNameOnFailure() {
        val icon = ChannelIcon.parse("https://192.0.2.1/guide-logo.png") as ChannelIcon.RemoteUrl
        val models = mutableListOf<Any>()
        loader.shutdown()
        loader = ImageLoader.Builder(context()).memoryCache(null).diskCache(null).components {
            add(Interceptor { chain ->
                models += chain.request.data
                ErrorResult(null, chain.request, IllegalStateException("Fixture missing logo"))
            })
        }.coroutineContext(Dispatchers.Main.immediate).build()
        images.install(loader)
        compose.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.height(guideTimelineRowHeight())) {
                    TimelineChannelHeader(Channel.create(ChannelId(1), name = "Northline News", icon = icon), 1001)
                }
            }
        }
        compose.onNodeWithText("Northline News", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("epg-channel-picon-1", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(listOf(icon.url), models)
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
                        Channel.create(ChannelId(1), name = name.value, number = 1001, icon = ChannelIcon.ImageCache(icon.value)),
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
        compose.runOnIdle { loadingGate.complete(Unit) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(name.value, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(name.value, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("epg-channel-picon-1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("epg-channel-header-1").assertContentDescriptionEquals("1001 ${name.value}")

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
        compose.runOnIdle { name.value = "  " }
        compose.onNodeWithTag("epg-channel-header-1").assertContentDescriptionEquals("1001")
    }

    @Test fun loadingEmptyAndErrorRowStatesFitTheDenseLane() {
        val channel = Channel.create(ChannelId(1), name = "Northline News", number = 1)
        val state = mutableStateOf<ConnectionUiState>(ConnectionUiState.Ready)
        val cached = mutableStateOf(false)
        val requesters = mutableMapOf<EventId, FocusRequester>()
        compose.setContent {
            TVHeadendPlayerTheme {
                TimelineChannelRow(channel, 0, 1, null, requesters, 0, 10800,
                    { 0 }, emptyList(), cached.value, state.value, false,
                    { null }, {}, {})
            }
        }
        compose.runOnIdle { state.value = ConnectionUiState.Connecting }
        val loadingRow = bounds("epg-channel-row-1")
        assertEquals(56f, loadingRow.height, .1f)
        val loading = context().getString(R.string.epg_loading)
        val skeleton = compose.onNodeWithTag("guide-row-skeleton")
        // Passive: the placeholders take no focus.
        skeleton.assertIsDisplayed().assertContentDescriptionEquals(loading)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        val skeletonBounds = skeleton.fetchSemanticsNode().boundsInRoot
        assertTrue(skeletonBounds.top >= loadingRow.top && skeletonBounds.bottom <= loadingRow.bottom)
        compose.onNodeWithText(loading).assertDoesNotExist()
        val states = listOf(
            Triple(ConnectionUiState.Ready, false, R.string.epg_no_data),
            Triple(ConnectionUiState.Ready, true, R.string.epg_not_loaded),
            Triple(ConnectionUiState.Error(ConnectionFailureKind.PERMISSION_DENIED, SessionRecoveryDisposition.NO_RETRY), false,
                R.string.epg_permission_denied),
        )
        for ((connection, hasCachedEvents, message) in states) {
            compose.runOnIdle { state.value = connection; cached.value = hasCachedEvents }
            compose.onNodeWithTag("guide-row-skeleton").assertDoesNotExist()
            val row = bounds("epg-channel-row-1")
            assertEquals(56f, row.height, .1f)
            val text = compose.onNodeWithText(context().getString(message))
            text.assertIsDisplayed()
            assertFalse("State message must fit the dense lane", textLayout(text.fetchSemanticsNode()).hasVisualOverflow)
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
        assertTrue("Header must grow rather than clip", ruler.top > 68f)
        assertEquals("The ruler grows with labelSmall", rulerHeight, ruler.height, .5f)
        val start = checkNotNull(position.position.value).windowStartSec
        val cell = compose.onNode(hasText(title(2, 0)) and isFocused()).fetchSemanticsNode().boundsInRoot
        assertTrue("One hour occupies about half the two-hour track", cell.width > ruler.width * .45f)
        val timeText = textLayout(compose.onNode(hasText(formatHm(hour)) and hasAnyAncestor(isFocused()),
            useUnmergedTree = true).fetchSemanticsNode())
        assertFalse("The native time line must not be clipped", timeText.hasVisualOverflow)
        val x = ruler.left + guideTimePositionPx(now, start, start + 2 * 3600, ruler.width)
        assertEquals(lineColor, bitmap.getPixel(x.toInt(), cell.center.y.toInt()))
        assertReadableFocus()
        bitmap.recycle()
        compose.onNodeWithTag("guide-date-button").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        assertEnlargedHeaderFocus()
    }

    @Test @Config(qualifiers = "ar-ldrtl-w960dp-h540dp-land-mdpi")
    fun rtlKeepsPhysicalLeftEarlierAndRightLaterWithAlignedRuler() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        // History from 18:00 continues past the window's 18:30 start, so neither edge is terminal.
        show(direction = LayoutDirection.Rtl, historyOffset = -2)
        val ruler = bounds("epg-time-ruler")
        assertEquals(0f, ruler.left, .1f)
        assertEquals(718f, ruler.right, .1f)
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
        assertReadableFocus(LayoutDirection.Rtl)
        bitmap.recycle()
    }

    private fun assertEnlargedHeaderFocus() {
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        val viewOptions = bounds("channels-view-button")
        assertEquals(902f, viewOptions.right, .1f)
        val heading = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, context().getString(R.string.epg_title))).fetchSemanticsNode().boundsInRoot
        assertEquals("Actions stay centred on the header row", heading.center.y, viewOptions.center.y, .1f)
        assertEquals("The date button shares the header row", heading.center.y, bounds("guide-date-button").center.y, .1f)
        val bitmap = capture("guide-header-focus-large-de", 1.3f, LayoutDirection.Ltr)
        assertTrue("Native focused button clears the 48dp physical safe area", nativeFocusBounds(bitmap).right <= 912f)
        bitmap.recycle()
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
        assertEquals(112f, first.left, .1f)
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
        show(scale = scale, history = false, lastOffset = 0, eventHour = finalHour, playerReturn = savedTimelineReturn())
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertEquals(start, checkNotNull(position.position.value).windowStartSec)
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val ruler = bounds("epg-time-ruler")
        assertEquals("A terminal hour keeps its complete time allocation", ruler.width / hours, focus.width, 1f)
        assertEquals("The seven-day endpoint remains aligned with the ruler", ruler.right, focus.right, 1f)
        assertEquals(focus.left + 6f, rulerLabel("19:00").fetchSemanticsNode().boundsInRoot.left, 1f)
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

    @Test @Config(qualifiers = "ar-ldrtl-w960dp-h540dp-land-mdpi")
    fun boundedHistoryKeepsTheFullCellOnHorizontalAndVerticalMoves() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        val firstHour = hour - 6 * 3600
        position.save(GuidePosition(ChannelId(1), EventId(111), firstHour + 3600, firstHour, 0))
        show(direction = LayoutDirection.Rtl, history = false, eventHour = firstHour, entryCoverage = false,
            playerReturn = savedTimelineReturn())
        key(Key.DirectionDown)
        focused(title(1, 1))
        key(Key.DirectionLeft)
        focused(title(1, 0))
        val ruler = bounds("epg-time-ruler")
        val horizontal = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        assertEquals(firstHour, checkNotNull(position.position.value).windowStartSec)
        assertEquals("Left at the history bound retains the complete cell", ruler.width / 3, horizontal.width, 1f)
        assertEquals(ruler.left, horizontal.left, 1f)

        key(Key.DirectionDown)
        focused(title(2, 0))
        val vertical = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        assertEquals("Down must not shift the bounded window", firstHour, checkNotNull(position.position.value).windowStartSec)
        assertEquals("The vertical target keeps the complete cell too", horizontal.left, vertical.left, 1f)
        assertEquals(horizontal.width, vertical.width, 1f)

        key(Key.DirectionUp)
        focused(title(1, 0))
        assertEquals(horizontal, compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot)
        assertEquals(firstHour, checkNotNull(position.position.value).windowStartSec)
        assertNoPlayback()
    }

    private fun terminalRtlHistory(scale: Float, hours: Int, name: String) {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        val firstHour = hour - 6 * 3600
        position.save(GuidePosition(ChannelId(1), EventId(110), firstHour, firstHour, 0))
        // A window wholly in the past asks for no coverage.
        show(scale = scale, direction = LayoutDirection.Rtl, history = false, eventHour = firstHour, entryCoverage = false,
            playerReturn = savedTimelineReturn())
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertEquals(firstHour, checkNotNull(position.position.value).windowStartSec)
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val ruler = bounds("epg-time-ruler")
        assertEquals("RTL terminal history retains a complete hour", ruler.width / hours, focus.width, 1f)
        assertEquals("RTL history begins at the padded physical left", ruler.left, focus.left, 1f)
        assertEquals("RTL future stays adjacent to the gutter", 718f, ruler.right, .1f)
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

    @Test fun oversizedProgrammeRunsItsFocusRingOffTheScreenEdge() {
        show(longProgramme = true)
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertReadableFocus()
        val bitmap = capture("guide-long-programme", 1f, LayoutDirection.Ltr)
        val ruler = bounds("epg-time-ruler")
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        // The ring's top line, inside the cell's 2dp stroke.
        val nativeFocusPixels = (ruler.left.toInt() until bitmap.width).filter {
            android.graphics.Color.red(bitmap.getPixel(it, focus.top.toInt() + 1)) > 180
        }
        assertTrue("The ring's leading edge stays clear of the fade", nativeFocusPixels.first() >= ruler.left + 48)
        assertEquals("The screen cuts the ring; it does not close in a corner", bitmap.width - 1, nativeFocusPixels.last())
        bitmap.recycle()
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun largeGermanTextShortensATimesLabelButNeverItsClock() {
        show(scale = 1.3f)
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        listOf("06", "12", "15", "18", "20", "22").forEach { hour ->
            val clock = compose.onNode(hasText("$hour:00") and hasAnyAncestor(hasTestTag("guide-time-preset-$hour")),
                useUnmergedTree = true).fetchSemanticsNode()
            val layout = textLayout(clock)
            assertTrue("Clock $hour:00 is cut", !layout.hasVisualOverflow && !layout.isLineEllipsized(0))
            assertTrue("Clock $hour:00 is within its row",
                clock.boundsInRoot.right <= bounds("guide-time-preset-$hour").right)
        }
    }

    @Test fun headlineShowsTheSettledProgrammeAboveFourAndAHalfRows() {
        show(headline = true)
        // On the header, before the grid has focus: the programme the grid enters.
        headlineTitle(title(1, 0))
        val header = bounds("guide-date-button")
        val headline = bounds("guide-headline")
        val ruler = bounds("epg-time-ruler")
        val headerRow = bounds("channels-header")
        assertEquals("The headline starts at the header row's start", headerRow.left, headline.left, .5f)
        val drop = headlineDrop()
        assertTrue("The headline starts below the header row, lowered by less than half a row: $drop", drop in 0f..30f)
        assertEquals("The headline keeps its fixed height", headlineHeight, headline.height, .5f)
        assertEquals("Timing anchors the content to the bottom", headline.bottom, bounds("guide-headline-metadata").bottom, .5f)
        assertEquals(headerRow.bottom + drop + headlineHeight + GuideHeadlineRulerGap.value, ruler.top, .5f)
        assertTrue("Headline below the header's buttons: header=$header headline=$headline", headline.top >= header.bottom)
        assertTrue("Ruler below the headline: headline=$headline ruler=$ruler", ruler.top >= headline.bottom)
        val first = bounds("epg-channel-row-1").top
        val rows = (540f - first) / (bounds("epg-channel-row-2").top - first)
        assertEquals("Four rows and most of a fifth below the headline", 4.65f, rows, .1f)
        compose.onNodeWithTag("channels-immersive-background").assertExists()
        compose.onNodeWithTag("guide-headline").assert(hasText(title(1, 0), substring = true))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        compose.onNodeWithTag("guide-headline-time-left", useUnmergedTree = true).assertExists()
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertEquals("The headline's height does not depend on its text", headline, bounds("guide-headline"))
        key(Key.DirectionUp)
        dateFocused()
        assertNoPlayback()
        key(Key.DirectionDown)
        focused(title(1, 0))
        // Quick presses, as a held key repeats, keep the settled headline until focus rests.
        compose.mainClock.autoAdvance = false
        try {
            repeat(2) {
                compose.pressKey(Key.DirectionRight)
                compose.mainClock.advanceTimeBy(80)
            }
            // Programme 2 runs past the window, so focus reaches it a few frames after the window moves;
            // wait for it frame by frame, well inside the settle that follows the last press. The moved
            // window's rows use the controlled effect context, without advancing the held clock.
            val lastPress = compose.mainClock.currentTime - 80
            val programme2 = hasText(title(1, 2)) and isFocused()
            while (compose.onAllNodes(programme2).fetchSemanticsNodes().isEmpty() &&
                compose.mainClock.currentTime - lastPress < GuideHeadlineSettleMs - 40
            ) {
                compose.waitForIdle()
                compose.mainClock.advanceTimeByFrame()
            }
            // Focus rests on programme 2 while the headline still shows programme 0.
            compose.onNode(programme2).assertExists()
            compose.onNode(hasTestTag("guide-headline-title"), useUnmergedTree = true).assertTextEquals(title(1, 0))
            compose.mainClock.advanceTimeBy(GuideHeadlineSettleMs + 300)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        focused(title(1, 2))
        headlineTitle(title(1, 2))
        // Programme 2 is not on air, so it has no bar and no time left: the headline is as tall and
        // as placed as programme 0's, which has them, but not as wide.
        val settled = bounds("guide-headline")
        assertEquals(headline.top, settled.top, .5f)
        assertEquals(headline.bottom, settled.bottom, .5f)
        assertEquals(headline.left, settled.left, .5f)
        assertTrue("Without the time left the headline is narrower: $headline $settled", settled.right < headline.right)
        compose.onNodeWithTag("guide-headline-time-left", useUnmergedTree = true).assertDoesNotExist()
        // Programme 2 lies inside the window that opened just before now.
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
            compose.waitForIdle()
            compose.onNodeWithTag("channels-immersive-picon").assertExists()
            key(Key.DirectionUp)
            dateFocused()
            key(Key.DirectionRight)
            tagFocused("channels-tags-button")
            key(Key.Enter)
            tagFocused("channels-tags-all")
            key(Key.DirectionDown)
            tagFocused("channels-tags-1")
            // A group without the focused programme's channel.
            key(Key.DirectionDown)
            tagFocused("channels-tags-2")
            val full = checkNotNull(session.observation.value.epgSnapshotForDisplay)
            // Only the focused channel's programmes are loaded, so the new group's rows stay empty
            // until theirs arrive and the grid focuses nothing.
            val first = full.events.filter { it.channelId == ChannelId(1) }
            publishEvents(first, EpgSnapshot.create(events = first,
                historicalEvents = full.historicalEvents.filter { it.channelId == ChannelId(1) },
                coverages = full.coverages), covered = true)
            armed.set(true)
            key(Key.Enter)
            runAcquisition()
            assertTrue("Scope acquisition reached the held fake", entered.isCompleted)
            // Well past the settle while they load: still the programme, never "no information".
            compose.mainClock.advanceTimeBy(GuideHeadlineSettleMs * 4)
            compose.waitForIdle()
            compose.onNode(hasTestTag("guide-headline-title"), useUnmergedTree = true).assertTextEquals(title(1, 0))
            compose.onNodeWithTag("channels-immersive-picon").assertExists()
            // Through the new programme's settle and reveal the outgoing logo keeps drawing.
            compose.mainClock.autoAdvance = false
            publishEvents(full.events, full, covered = true)
            release.complete(Unit)
            runAcquisition()
            val next = hasTestTag("guide-headline-title") and hasText(title(2, 0))
            var framesAfterSwitch = 0
            for (frame in 0 until 600) {
                compose.waitForIdle()
                compose.mainClock.advanceTimeByFrame()
                assertTrue("logo missing at frame $frame",
                    compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isNotEmpty())
                if (compose.onAllNodes(next, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) framesAfterSwitch++
                if (framesAfterSwitch > 60) break
            }
            assertTrue("headline never moved to the new group", framesAfterSwitch > 60)
            compose.mainClock.autoAdvance = true
            // The grid takes the new group's programme on air.
            key(Key.DirectionDown)
            focused(title(2, 0))
            headlineTitle(title(2, 0))
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
        val drop = headlineDrop()
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.Enter)
        tagFocused("guide-view-rows-regular")
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        tagFocused("guide-view-headline")
        compose.onNodeWithTag("guide-view-headline").assertIsOn()
        key(Key.Enter)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("guide-headline").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("channels-immersive-background").assertDoesNotExist()
        compose.onNodeWithTag("guide-view-headline").assertIsOff()
        assertEquals("The grid takes the headline's room, retaining header focus clearance",
            bounds("channels-header").bottom + 5f, bounds("epg-time-ruler").top, .1f)
        assertEquals(drop + headlineHeight + GuideHeadlineRulerGap.value - 5f, withHeadline - bounds("epg-time-ruler").top, .5f)
        assertFalse("The choice is kept", runBlocking { uiSettings.settings.first() }.guideView.headline)
        key(Key.Back)
        tagFocused("channels-view-button")
        assertNoPlayback()
    }

    @Test fun theBottomRowShowsItsTitleAndAThirdOfTheRest() = bottomRowCut(1f, GuideRows.REGULAR)

    @Test fun theBottomRowShowsItsTitleAndAThirdOfTheRestAtLargeText() = bottomRowCut(1.3f, GuideRows.REGULAR)

    @Test fun theBottomDenseRowShowsItsTitleAndAThirdOfTheRest() = bottomRowCut(1f, GuideRows.DENSE)

    @Test fun theBottomDenseRowShowsItsTitleAndAThirdOfTheRestAtLargeText() = bottomRowCut(1.3f, GuideRows.DENSE)

    /**
     * With the headline shown, the grid moves down so the row the bottom edge cuts shows its title and
     * a third of the rest, by at most half a row; a row cut above that keeps its place.
     */
    private fun bottomRowCut(scale: Float, rows: GuideRows) {
        show(scale = scale, headline = true, rows = rows)
        headlineTitle(title(1, 0))
        val first = bounds("epg-channel-row-1")
        val stride = bounds("epg-channel-row-2").top - first.top
        assertEquals("Rows of the chosen density", rowHeight, first.height, .5f)
        val drop = headlineDrop()
        val shown = (540f - first.top).mod(stride)
        val wanted = rowTitleBottom + (rowHeight - rowTitleBottom) / 3
        val geometry = "drop=$drop shown=$shown wanted=$wanted stride=$stride"
        assertTrue("Never more than half a row: $geometry", drop in 0f..stride / 2)
        // What the grid would show unmoved decides whether it moves, and by how much.
        val unmoved = (540f - (first.top - drop)).mod(stride)
        val expected = (unmoved - wanted).mod(stride).takeIf { it <= stride / 2 } ?: 0f
        assertEquals("The drop this geometry calls for: $geometry unmoved=$unmoved", expected, drop, 1f)
        if (drop > .5f) {
            assertEquals("The cut row shows its title and a third of the rest: $geometry", wanted, shown, 1f)
        }
        assertNoPlayback()
    }

    @Test fun theHeadlineShowsTheSubtitleWithEverythingElse() {
        show(headline = true, genres = true, subtitle = "Six weeks with the coastguard")
        headlineTitle(title(1, 0))
        val headline = bounds("guide-headline")
        compose.onNodeWithTag("guide-headline-subtitle", useUnmergedTree = true)
            .assertIsDisplayed().assertTextEquals("Six weeks with the coastguard")
        for (part in listOf("channel", "title", "subtitle", "facts", "metadata")) {
            compose.onNodeWithTag("guide-headline-$part", useUnmergedTree = true).assertIsDisplayed()
            val bounds = bounds("guide-headline-$part")
            assertTrue("The $part is inside the headline: $bounds in $headline",
                bounds.top >= headline.top - .5f && bounds.bottom <= headline.bottom + .5f)
        }
        compose.onAllNodesWithTag("guide-headline-synopsis", useUnmergedTree = true).assertCountEquals(0)
        capture("guide-headline-subtitle", 1f, LayoutDirection.Ltr).recycle()
    }

    @Test fun theHeadlineShowsSubtitleAndAgeRatingAtLargeText() {
        show(scale = 1.3f, headline = true, genres = true,
            subtitle = "Six weeks with the coastguard", rating = EpgRating(12, null, null, null, null, null))
        headlineTitle(title(1, 0))
        for (part in listOf("title", "subtitle", "facts", "rating", "metadata")) {
            compose.onNodeWithTag("guide-headline-$part", useUnmergedTree = true).assertIsDisplayed()
        }
        val headline = bounds("guide-headline")
        val rating = bounds("guide-headline-rating")
        val timing = bounds("guide-headline-metadata")
        assertTrue("The rating fits fully inside the headline: $rating / $headline",
            rating.left >= headline.left && rating.right <= headline.right &&
                rating.top >= headline.top && rating.bottom <= headline.bottom)
        assertTrue("The rating stays above the timing line: $rating / $timing", rating.bottom <= timing.top)
        // Bottom-aligned, a short reservation would push the channel line out of the top, not the rating.
        val channel = compose.onNodeWithTag("guide-headline-channel", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue("The channel line starts inside the headline: $channel / $headline", channel.top.value >= headline.top - .5f)
        assertEquals("The headline retains its reservation below the header",
            bounds("channels-header").bottom + headlineDrop() + headlineHeight, headline.bottom, .5f)
    }

    // The headline stays below the header row for every group name and genre.
    @Test fun theHeadlineStaysBelowTheHeaderRowWithLongGroupAndFilter() = headlineBelowTheHeader(1f)

    /** The same below-header placement holds for long German buttons at large text. */
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun theHeadlineStaysBelowTheGermanHeaderRowAtLargeText() {
        Locale.setDefault(Locale.GERMANY)
        headlineBelowTheHeader(1.3f)
    }

    /**
     * The headline stays below the header row regardless of the [group] name or chosen [genre].
     */
    private fun headlineBelowTheHeader(
        scale: Float,
        direction: LayoutDirection = LayoutDirection.Ltr,
        group: String = "Dokumentationen, Reportagen und Wissen aus aller Welt",
        genre: ProgrammeCategory = ProgrammeCategory.SOCIETY_POLITICS,
    ) {
        show(scale = scale, direction = direction, headline = true, genres = true, activeTag = true, tagName = group,
            initialGenre = genre,
            // A long subtitle exercises the headline's width limit.
            subtitle = "Six weeks with the coastguard as the winter storms arrive on the northern islands")
        headlineTitle(title(1, 0))
        compose.onNodeWithTag("channels-tags-button").assertContentDescriptionEquals(context().getString(
            R.string.header_button_value_description, context().getString(R.string.browse_tags), group))
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(context().getString(
            R.string.header_button_value_description, context().getString(R.string.guide_filter),
            context().getString(programmeCategoryLabelRes(genre))))
        val header = bounds("channels-header")
        val headline = bounds("guide-headline")
        val subtitle = bounds("guide-headline-subtitle")
        val geometry = "headline=$headline header=$header"
        if (direction == LayoutDirection.Ltr) {
            assertEquals(header.left, headline.left, .5f)
            assertTrue("The subtitle stays inside the headline: $subtitle", subtitle.right <= headline.right + .5f)
        } else {
            assertEquals(header.right, headline.right, .5f)
            assertTrue("The subtitle stays inside the headline: $subtitle", subtitle.left >= headline.left - .5f)
        }
        assertTrue("The headline starts below the header row: $geometry", headline.top >= header.bottom - .5f)
        assertTrue("The headline is at most a hero wide", headline.width <= ImmersiveHeroWidth.value + .5f)
        assertEquals("The headline ends where it did below the row: $geometry",
            header.bottom + headlineDrop() + headlineHeight, headline.bottom, .5f)
        assertEquals(header.bottom + headlineDrop() + headlineHeight + GuideHeadlineRulerGap.value, bounds("epg-time-ruler").top, .5f)
        assertNoPlayback()
    }

    @Test fun theHeadlineTimingKeepsItsPlaceWithOrWithoutASubtitle() {
        show(headline = true, subtitle = "Six weeks with the coastguard")
        key(Key.DirectionDown)
        focused(title(1, 0))
        headlineTitle(title(1, 0))
        compose.waitForIdle()
        val withSubtitle = bounds("guide-headline-metadata")
        compose.onNodeWithTag("guide-headline-subtitle", useUnmergedTree = true).assertIsDisplayed()
        key(Key.DirectionRight)
        focused(title(1, 1))
        headlineTitle(title(1, 1))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("guide-headline-subtitle", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        val without = bounds("guide-headline-metadata")
        assertEquals("Timing stays put without a subtitle: $withSubtitle, $without", withSubtitle.bottom, without.bottom, .5f)
        assertEquals(bounds("guide-headline").bottom, without.bottom, .5f)
        assertNoPlayback()
    }

    @Test fun theHeadlinesLogoStartsBelowTheHeaderRow() = headlineLogoBelowTheRow(1f)

    @Test fun theHeadlinesLogoStartsBelowTheHeaderRowAtLargeText() = headlineLogoBelowTheRow(1.3f)

    @Test fun tallHeadlineLogoKeepsClearOfTheTimeline() {
        headlineLogoScene(120, 240)
        val logo = bounds("channels-immersive-picon")
        val ruler = bounds("epg-time-ruler")
        assertTrue("Tall logo needs at least 20dp above timeline: logo=$logo ruler=$ruler",
            ruler.top - logo.bottom >= 20f - .5f)
        assertTrue("Logo stays inside the headline's reserved space",
            logo.height <= headlineHeight - 24f - 8f + .5f)
    }

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun headlineLogoWideCapture() = headlineLogoCapture("wide", 400, 120)

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun headlineLogoSquareCapture() = headlineLogoCapture("square", 240, 240)

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun headlineLogoTallCapture() = headlineLogoCapture("tall", 120, 240)

    private fun headlineLogoCapture(shape: String, width: Int, height: Int) {
        headlineLogoScene(width, height)
        capture("guide-logo-$shape", 1f, LayoutDirection.Ltr, outputs = "build/outputs/041-guide-channels").recycle()
        File("build/outputs/041-guide-channels/guide-logo-$shape.txt").appendText(
            "art=fixture ridge-earth picon rescaled to ${width}x$height for deliberate aspect stress; Ridge Light, no programme art\n" +
                "logoFrame=${bounds("channels-immersive-picon")}; ruler=${bounds("epg-time-ruler")}\n",
        )
    }

    private fun headlineLogoScene(width: Int, height: Int) {
        // Deliberate aspect-ratio stress variants of the same fictional mark; no real picons.
        val art = at.bernhardberger.tvhplayer.testutil.FixtureArt
        val logo = Bitmap.createScaledBitmap(art.picon("ridge-earth"), width, height, true)
        loader = art.imageLoader(context(), mapOf(ArtworkId(1) to logo)).newBuilder()
            .coroutineContext(Dispatchers.Main.immediate).build()
        images.install(loader)
        show(headline = true, history = false, channelNames = listOf("Ridge Earth"), events = listOf(
            EpgEvent.create(EventId(110), ChannelId(1), Instant.fromEpochSeconds(hour),
                Instant.fromEpochSeconds(hour + 3600), title = "Ridge Light",
                summary = "Seasonal mountain light, forests and weather moving over a ridge."),
        ))
        key(Key.DirectionDown)
        focused("Ridge Light")
        compose.waitForIdle()
        compose.onNodeWithTag("channels-immersive-picon").assertExists()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    /** The channel's logo, shown without a programme picture, starts below the row however tall it grows. */
    private fun headlineLogoBelowTheRow(scale: Float) {
        show(scale = scale, headline = true)
        headlineTitle(title(1, 0))
        compose.waitForIdle()
        compose.onNodeWithTag("channels-immersive-picon").assertExists()
        val header = bounds("channels-header")
        val logo = bounds("channels-immersive-picon")
        assertTrue("The logo starts below the header row: logo=$logo header=$header", logo.top >= header.bottom - .5f)
        assertEquals("The logo keeps its gap below the row: logo=$logo header=$header", header.bottom + 24f, logo.top, .5f)
    }

    private fun headlineTitle(title: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag("guide-headline-title") and hasText(title), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun aServerDeletedRecordingHasNoGuideCellMarkOrHeadlineBadge() {
        show(headline = true, recordings = listOf(
            DvrEntry.create(DvrEntryId(1), eventId = EventId(110),
                state = DvrEntryState.REMOVED, files = emptyList()),
        ))
        key(Key.DirectionDown)
        focused(title(1, 0))
        compose.mainClock.advanceTimeBy(GuideHeadlineSettleMs + 300)
        compose.waitForIdle()
        compose.onNodeWithTag("guide-cell-failed-mark", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("recording-badge-failed", useUnmergedTree = true).assertDoesNotExist()
        val description = compose.onNode(hasContentDescription(title(1, 0), substring = true))
            .fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
            .orEmpty().joinToString()
        assertFalse(description, description.contains(context().getString(R.string.recording_state_failed)))
    }

    @Test fun aRemovedEntryDoesNotHideTheScheduledMarkForTheSameEvent() {
        show(headline = true, recordings = listOf(
            DvrEntry.create(DvrEntryId(1), eventId = EventId(110),
                state = DvrEntryState.REMOVED, files = emptyList()),
            DvrEntry.create(DvrEntryId(2), eventId = EventId(110), state = DvrEntryState.SCHEDULED),
        ))
        key(Key.DirectionDown)
        focused(title(1, 0))
        compose.onNode(
            hasTestTag("guide-cell-recording-mark") and hasAnyAncestor(hasContentDescription(title(1, 0), substring = true)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(GuideHeadlineSettleMs + 300)
        compose.waitForIdle()
        compose.onNode(
            hasTestTag("recording-badge-scheduled") and hasAnyAncestor(hasTestTag("guide-headline")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test fun aFinishedRecordingIsTheRecordedMarkAndAFailedOrMissedOneTheErrorMarkAndTheHeadlineBadgesThem() {
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

    @Test fun aRecordingScheduledByARuleSaysSoUntilItRuns() {
        val series = AutorecRuleId("series")
        show(recordings = listOf(
            DvrEntry.create(DvrEntryId(1), eventId = EventId(110), state = DvrEntryState.RECORDING, autorecRuleId = series),
            DvrEntry.create(DvrEntryId(2), eventId = EventId(111), state = DvrEntryState.SCHEDULED, autorecRuleId = series),
            DvrEntry.create(DvrEntryId(3), eventId = EventId(112), state = DvrEntryState.SCHEDULED),
        ))
        fun description(offset: Int) = compose.onNode(hasContentDescription(title(1, offset), substring = true))
            .fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }.orEmpty().joinToString()
        val rule = context().getString(R.string.recording_from_series_rule)
        assertTrue(description(1), description(1).contains(rule))
        assertFalse("A one-off is plainly scheduled", description(2).contains(rule))
        assertTrue(description(2), description(2).contains(context().getString(R.string.recording_state_scheduled)))
        assertFalse("A running recording is the dot, whatever scheduled it", description(0).contains(rule))
        assertTrue(description(0), description(0).contains(context().getString(R.string.recording_state_recording)))
        assertNoPlayback()
    }

    @Test fun aCellTooNarrowForTheMarkLeavesItOutInsteadOfShrinkingIt() {
        fun scheduled(id: Long, eventId: Long) =
            DvrEntry.create(DvrEntryId(id), eventId = EventId(eventId), state = DvrEntryState.SCHEDULED)
        // An hour earlier, so the third programme lies inside the window around now.
        show(eventHour = hour - 3600, shortSecondMinutes = 3, recordings = listOf(scheduled(1, 111), scheduled(2, 112)))
        fun mark(offset: Int) = compose.onNode(
            hasTestTag("guide-cell-recording-mark") and hasAnyAncestor(hasContentDescription(title(1, offset), substring = true)),
            useUnmergedTree = true,
        )
        mark(1).assertIsNotDisplayed()
        mark(2).assertIsDisplayed().assertWidthIsEqualTo(StatusIconSize).assertHeightIsEqualTo(StatusIconSize)
        assertNoPlayback()
    }

    @Test fun genreColorsTurnOffAndTheGenresLoseTheirDots() {
        show(genres = true)
        key(Key.DirectionRight)
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
        tagFocused("guide-filter-button")
        key(Key.Enter)
        tagFocused("guide-genre-all")
        compose.onAllNodesWithTag("option-dot", useUnmergedTree = true).assertCountEquals(0)
        assertNoPlayback()
    }

    @Test fun anOpenPanelHidesTheGridFromAccessibilityAndItsEntriesStayReadable() {
        val gridHidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility) and
            hasAnyDescendant(hasTestTag("epg-channel-row-1"))
        fun gridHiddenCount() = compose.onAllNodes(gridHidden, useUnmergedTree = true).fetchSemanticsNodes().size
        show()
        assertEquals(0, gridHiddenCount())
        key(Key.DirectionRight)
        tagFocused("channels-tags-button")
        key(Key.Enter)
        compose.onNodeWithTag("channels-tags-panel").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
        assertEquals(1, gridHiddenCount())
        key(Key.Back)
        compose.onNodeWithTag("channels-tags-panel").assertDoesNotExist()
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

    private fun assertTerminalNativeFocus(bitmap: Bitmap, direction: LayoutDirection) {
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val native = nativeFocusBounds(bitmap)
        // Check the complete ring rather than accepting a clipped rectangle: it spans the cell
        // inside its 2dp gutter halves, at the cell's own size.
        assertEquals("Complete focus ring width", focus.width - 4f, native.width, 1f)
        assertEquals("Complete focus ring height", focus.height, native.height, 1f)
        assertEquals("The ring remains centred on its time allocation", focus.center.x, native.center.x, 1f)
        if (direction == LayoutDirection.Ltr) {
            assertTrue("The ring must end inside the physical 8dp safe area: $native", native.right <= bitmap.width - 8f)
        } else {
            assertTrue("The ring must start inside the physical 8dp safe area: $native", native.left >= 8f)
        }
        val safeArea = if (direction == LayoutDirection.Ltr) bitmap.width - 8 until bitmap.width else 0 until 8
        for (y in (focus.top.toInt() - 4)..(focus.bottom.toInt() + 4)) {
            for (x in safeArea) {
                assertEquals("Terminal safe area also excludes antialiased focus pixels at ($x,$y)", backgroundColor, bitmap.getPixel(x, y))
            }
        }
    }

    /** A ruler label; cells show their start times too. */
    private fun rulerLabel(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("epg-time-ruler")), useUnmergedTree = true)

    private fun assertChannelHeader(bitmap: Bitmap, scale: Float) {
        val header = bounds("epg-channel-header-2")
        val logo = bounds("epg-channel-picon-2")
        compose.onNodeWithText("Kultur und Dokumentationen aus aller Welt HD", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("epg-channel-header-2").assertContentDescriptionEquals("2 Kultur und Dokumentationen aus aller Welt HD")
        assertFalse("Header is not a D-pad focus target", compose.onNodeWithTag("epg-channel-header-2")
            .fetchSemanticsNode().config.contains(SemanticsProperties.Focused))
        val number = compose.onNodeWithText("2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(104f, header.width, .1f)
        assertEquals(48f, logo.width, .1f)
        assertEquals(28f, logo.height, .1f)
        assertEquals(number.right + 8f, logo.left, .1f)
        assertEquals(header.left + 12f, number.left, .1f)
        assertEquals("Logo remains centred to native pixel precision", header.center.y, logo.center.y, .5f)
        assertEquals("Number remains centred to native pixel precision", header.center.y, number.center.y, .5f)
        val noLogoHeader = bounds("epg-channel-header-3")
        val name = compose.onNodeWithText("Northline News", useUnmergedTree = true)
        val nameBounds = name.fetchSemanticsNode().boundsInRoot
        val noLogoNumber = compose.onNodeWithText("3", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("epg-channel-header-3").assertContentDescriptionEquals("3 Northline News")
        val text = textLayout(name.fetchSemanticsNode())
        assertEquals(noLogoHeader.left + 12f, noLogoNumber.left, .1f)
        assertEquals(noLogoNumber.right + 8f, nameBounds.left, .1f)
        assertEquals(noLogoHeader.right - 12f, nameBounds.right, .1f)
        assertEquals("No-logo number and name share the centred row", noLogoNumber.center.y, nameBounds.center.y, .5f)
        assertEquals(noLogoHeader.center.y, nameBounds.center.y, .5f)
        assertEquals(14.sp, text.layoutInput.style.fontSize)
        assertEquals(20.sp, text.layoutInput.style.lineHeight)
        assertEquals(scale, text.layoutInput.density.fontScale, .001f)
        assertEquals("No-logo name occupies only one line", 1, text.lineCount)
        assertTrue("Name line is complete, not vertically clipped", text.getLineBottom(0) <= nameBounds.height)
        assertTrue("Name retains bottom padding", nameBounds.bottom <= noLogoHeader.bottom - 4f)
        val programme = compose.onNodeWithText(title(2, 0), useUnmergedTree = true)
        val programmeText = textLayout(programme.fetchSemanticsNode())
        assertEquals("Long native programme headline occupies only one line", 1, programmeText.lineCount)
        assertTrue("Long headlines use single-line ellipsis", programmeText.isLineEllipsized(0))
        assertTrue(programmeText.getLineBottom(0) <= programmeText.size.height)
        val programmeBounds = programme.fetchSemanticsNode().boundsInRoot
        val timeBounds = compose.onNode(hasText(formatHm(hour)) and hasAnyAncestor(hasTestTag("epg-channel-row-2")),
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

    /** How far the headline sits below the header row. */
    private fun headlineDrop(): Float = bounds("guide-headline").top - bounds("channels-header").bottom

    private fun baseline(text: androidx.compose.ui.semantics.SemanticsNode): Float {
        return text.boundsInRoot.top + textLayout(text).firstBaseline
    }

    private fun textLayout(text: androidx.compose.ui.semantics.SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        checkNotNull(text.config[SemanticsActions.GetTextLayoutResult].action).invoke(layouts)
        return layouts.single()
    }
}
