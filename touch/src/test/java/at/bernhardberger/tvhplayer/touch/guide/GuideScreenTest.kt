package at.bernhardberger.tvhplayer.touch.guide

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgRepository
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvhplayer.touch.testutil.FakeConnection
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.testutil.captureTouchWindows
import at.bernhardberger.tvhplayer.touch.ui.DvrChangeDialog
import at.bernhardberger.tvhplayer.touch.ui.DvrControls
import at.bernhardberger.tvhplayer.touch.ui.ProgramRecordControl
import at.bernhardberger.tvhplayer.touch.ui.RuleSchedule
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.screens.recordings.StopConfirmationTimes
import at.bernhardberger.tvhplayer.ui.screens.recordings.stopConfirmationSentence
import java.util.TimeZone
import java.time.ZoneOffset
import android.content.res.Configuration
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(TvheadendTestingApi::class)
class GuideScreenTest {
    private val motionScale = object : MotionDurationScale { override var scaleFactor = 1f }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = motionScale)

    private val details = mutableListOf<String?>()
    private val watched = mutableListOf<String>()
    private val cancelled = mutableListOf<String>()
    private val stopped = mutableListOf<String>()
    private val selectedChannels = mutableListOf<String>()

    @Test
    @Config(qualifiers = TABLET)
    fun tabletOverviewOpensAProgramInPlaceAndAChannelHeadOpensItsDay() {
        val fixture = overviewFixture()
        val state = mutableStateOf(fixture)
        show(fixture, state = { state.value })
        capture("tablet-guide-overview", TouchCanvas.TABLET)

        // A tap opens the program where it is and plays nothing; only on-air programs offer Watch.
        val row = compose.onAllNodesWithText("Coastal Miles")[0]
        val titleTop = row.getBoundsInRoot().top
        row.performClick()
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertIsDisplayed()
        compose.onNodeWithText("50m • Travel", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Watch")).assertCountEquals(0)
        assertEquals(emptyList<String>(), watched)
        // Details always stays: on Record's line where the column has room for both, as here.
        val record = compose.onNodeWithText("Record").getBoundsInRoot()
        val detailsButton = compose.onNodeWithText("Details").assertIsDisplayed().getBoundsInRoot()
        assertTrue(detailsButton.top < record.bottom)
        assertEquals("an already visible row stays put", titleTop, row.getBoundsInRoot().top)
        capture("tablet-guide-overview-open", TouchCanvas.TABLET)

        // Back folds it again.
        back()
        compose.onAllNodes(hasText(COASTAL_MILES_SYNOPSIS_START, substring = true)).assertCountEquals(0)

        compose.runOnIdle { state.value = fixture.withoutLogo("ridge-earth") }
        val head = hasContentDescription("Ridge Earth, by channel")
        compose.onNode(hasTestTag("channel-logo-tile") and hasAnyAncestor(head), useUnmergedTree = true)
            .assertDoesNotExist()
        assertEquals(
            compose.onNode(head).getBoundsInRoot().left,
            compose.onNode(hasText("Ridge Earth") and hasAnyAncestor(head), useUnmergedTree = true).getBoundsInRoot().left,
        )
        capture("tablet-guide-overview-no-logo", TouchCanvas.TABLET)
        compose.onNode(head).performClick()
        compose.onNodeWithText("Sunday, November 15").assertIsDisplayed()
        compose.onNodeWithText("Six Bridges and a River That Changed Its Mind").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h560dp-land-xhdpi")
    fun tabletOverviewRevealsActionsWhenTheLastVisibleProgramOpens() {
        val fixture = overviewFixture()
        val synopsis = fixture.channels[1].programs.firstNotNullOf { it.synopsis }.substringBefore(".")
        val column = fixture.channels.first().let { channel ->
            channel.copy(programs = channel.programs.take(5).map { it.copy(synopsis = synopsis) })
        }
        show(fixture.copy(channels = listOf(column)))

        val lastVisible = column.programs.map { compose.onNodeWithText(it.title) }.last { it.isDisplayed() }
        lastVisible.performClick()
        compose.onNodeWithText("Details").assertIsDisplayed()
        lastVisible.assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h558dp-port-xhdpi")
    fun phoneOverviewRevealsActionsWhenTheLastVisibleProgramOpens() {
        val fixture = phoneOverviewFixture()
        val synopsis = fixture.channels[1].programs.first().synopsis?.substringBefore(".")
        val channels = fixture.channels.map { channel ->
            channel.copy(programs = listOf(channel.programs.first().copy(synopsis = synopsis)))
        }
        show(fixture.copy(channels = channels))

        val lastVisible = channels.map { compose.onNodeWithText(it.programs.first().title) }.last { it.isDisplayed() }
        lastVisible.performClick()
        compose.onNodeWithText("Details").assertIsDisplayed()
        lastVisible.assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletProgramsOfferOnlyTheirOwnRecordingControl() {
        var asking by mutableStateOf(false)
        var askingStop by mutableStateOf(false)
        val stopTimes = StopConfirmationTimes(recordedSec = 17 * 60L, remainingSec = 88 * 60L)
        show(
            overviewFixture().withRecordControls(
                mapOf(SNOWLINE_ID to ProgramRecordControl.Cancel, HARBOR_KICKOFF_ID to ProgramRecordControl.Stop),
            ).withRuleSchedules(mapOf(TIDE_WATCH_ID to RuleSchedule.Scheduled, COASTAL_MILES_ID to RuleSchedule.Skipped)),
        ) {
            if (askingStop) {
                DvrChangeDialog(
                    SharedR.string.stop_recording_confirm_title, SharedR.string.stop_recording_confirm_body,
                    SharedR.string.keep_recording, SharedR.string.stop_recording, {}, { askingStop = false },
                    stopTimes = stopTimes,
                )
            } else if (asking) {
                DvrChangeDialog(
                    SharedR.string.cancel_recording_confirm_title, SharedR.string.cancel_recording_confirm_body,
                    SharedR.string.keep_recording, SharedR.string.cancel_recording, {}, { asking = false },
                )
            }
        }
        // A skipped program says so where Record would stand; the row marks it instead of showing a clock.
        compose.onAllNodesWithText("Coastal Miles")[0].performClick()
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertIsDisplayed()
        val skipped = compose.activity.getString(SharedR.string.autorec_editor_skipped)
        compose.onNodeWithText(skipped).assertIsDisplayed()
        compose.onNodeWithContentDescription(skipped).assertIsDisplayed()
        listOf("Record", "Cancel recording", "Stop recording").forEach {
            compose.onAllNodes(hasText(it)).assertCountEquals(0)
        }

        // A program an automatic recording scheduled says so where Record would stand, and offers neither.
        compose.onAllNodesWithText("Tide Watch")[0].performClick()
        compose.onNodeWithText(compose.activity.getString(SharedR.string.recording_state_scheduled)).assertIsDisplayed()
        listOf("Record", "Cancel recording").forEach { compose.onAllNodes(hasText(it)).assertCountEquals(0) }
        capture("tablet-guide-scheduled-series", TouchCanvas.TABLET)

        compose.onNodeWithText("Snowline").performClick()
        compose.onAllNodes(hasText("Record")).assertCountEquals(0)
        compose.onNodeWithText("Cancel recording").performClick()
        assertEquals(listOf(SNOWLINE_ID), cancelled)
        asking = true
        compose.onNodeWithText(compose.activity.getString(SharedR.string.cancel_recording_confirm_title))
            .assertIsDisplayed()
        compose.captureTouchWindows(GROUP, "tablet-guide-cancel-confirm", TouchCanvas.TABLET)
        compose.onNodeWithText(compose.activity.getString(SharedR.string.keep_recording)).performClick()

        compose.onAllNodesWithText("Harbor Kickoff")[0].performClick()
        compose.onNodeWithText(compose.activity.getString(SharedR.string.stop_recording)).performClick()
        assertEquals(listOf(HARBOR_KICKOFF_ID), stopped)
        askingStop = true
        val spoken = stopConfirmationSentence(stopTimes, compose.activity.resources, spoken = true)
        compose.onNodeWithText(spoken).assertIsDisplayed()
        compose.captureTouchWindows(GROUP, "tablet-guide-stop-confirm", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletChannelDay() {
        val fixture = byChannelFixture()
        var state by mutableStateOf(fixture)
        show(fixture, GuideView.ByChannel, channel = fixture.channels.first().id, open = RIDGE_LIGHT_ID, state = { state })
        compose.onNodeWithText("Monday, November 16").assertIsDisplayed()
        compose.onAllNodes(hasText("8:00–9:00 PM", substring = true)).assertCountEquals(1)
        compose.onNodeWithText("60m • 2024 • Documentary").assertIsDisplayed()
        capture("tablet-guide-channel", TouchCanvas.TABLET)

        compose.onNodeWithText("Watch").performClick()
        assertEquals(listOf("ridge-earth"), watched)

        swipeDay(left = true, tablet = true)
        compose.onNodeWithText(fixture.days[1]).assertIsSelected()
        compose.onNodeWithText(fixture.days[0]).performClick()
        compose.onNodeWithText(fixture.days[0]).assertIsSelected()

        val otherChannel = fixture.channels.first { it.id == "glass-drama" }
        state = fixture.copy(channels = listOf(otherChannel), selectedTagId = "other")
        compose.onNodeWithText("No schedule").assertIsDisplayed()
        compose.onNodeWithText("Watch").performClick()
        assertEquals(listOf("ridge-earth", otherChannel.id), watched)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1024dp-h558dp-land-xhdpi")
    fun tabletChannelDayRevealsActionsWhenTheLastVisibleProgramOpens() = assertChannelDayRevealsActions(phone = false)

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h558dp-port-xhdpi")
    fun phoneChannelDayRevealsActionsWhenTheLastVisibleProgramOpens() = assertChannelDayRevealsActions(phone = true)

    private fun assertChannelDayRevealsActions(phone: Boolean) {
        val fixture = byChannelFixture(phone)
        val selected = fixture.channels.first()
        val programs = selected.programs.distinctBy { it.title }.map {
            it.copy(art = selected.programs.first().art, synopsis = selected.programs.first().synopsis)
        }
        show(fixture.copy(channels = listOf(selected.copy(programs = programs))), GuideView.ByChannel, channel = selected.id)

        val lastVisible = programs.map { compose.onNodeWithText(it.title) }.last { it.isDisplayed() }
        lastVisible.performClick()
        compose.onNodeWithText("Details").assertIsDisplayed()
        lastVisible.assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletChannelWithoutGuideDataOffersWatch() {
        show(byChannelFixture(), GuideView.ByChannel, channel = "glass-drama")
        compose.onNodeWithText("No schedule").assertIsDisplayed()
        capture("tablet-guide-channel-empty", TouchCanvas.TABLET)

        compose.onNodeWithText("Watch").performClick()
        assertEquals(listOf("glass-drama"), watched)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletDetailsOpenFromTheOpenedProgramAndBackReturns() {
        show(overviewFixture())
        compose.onAllNodesWithText("Glass Harbor")[0].performClick()
        compose.onNodeWithText("Details").performClick()
        assertEquals(listOf(GLASS_HARBOR_ID), details)
        compose.onNodeWithText("Other airings of Glass Harbor").assertIsDisplayed()
        capture("tablet-details", TouchCanvas.TABLET)

        back()
        compose.onNodeWithText("Overview").assertIsDisplayed()
        compose.onAllNodes(hasText("Other airings of Glass Harbor")).assertCountEquals(0)
        assertEquals("System Back clears the producer's Details selection", listOf(GLASS_HARBOR_ID, null), details)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletOverviewWithoutSomePicturesLeadsWithTheText() {
        show(overviewFixture().withoutSomePictures())
        // Without its picture a running recording's REC stands after the title, as in a Channels row.
        compose.onNodeWithContentDescription("Recording").assertIsDisplayed()
        capture("tablet-guide-overview-no-picture", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletFirstLoad() {
        show(overviewFixture().copy(availability = GuideAvailability.Loading))
        compose.onNodeWithText("Loading guide…").assertIsDisplayed()
        // The view switch and the day bar come with the list.
        compose.onNodeWithText("Overview").assertDoesNotExist()
        compose.onNodeWithText("Now").assertDoesNotExist()
        capture("tablet-guide-loading", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = UPRIGHT)
    fun uprightOverview() {
        show(overviewFixture(upright = true))
        assertPartLinesWhole()
        assertTimeLeftStays()
        capture("tablet-upright-guide-overview", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = UPRIGHT)
    fun uprightChannelDay() {
        show(byChannelFixture(upright = true), GuideView.ByChannel, open = RIDGE_LIGHT_ID)
        assertPartLinesWhole()
        capture("tablet-upright-guide-channel", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = UPRIGHT)
    fun uprightDetails() {
        show(overviewFixture(upright = true), detailsId = GLASS_HARBOR_ID)
        capture("tablet-upright-details", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneOverviewOpensInPlaceAndTabsSwitchTheView() {
        show(phoneOverviewFixture())
        assertPartLinesWhole()
        assertTimeLeftStays()
        capture("phone-guide-overview", TouchCanvas.PHONE)

        compose.onNodeWithText("Glass Harbor").performClick()
        compose.onNodeWithText("S2 E4 • The Long Tide").assertIsDisplayed()
        capture("phone-guide-overview-open", TouchCanvas.PHONE)

        compose.onNodeWithText("By channel").performClick()
        compose.onNodeWithText("Sunday, November 15").assertIsDisplayed()
        compose.onNodeWithText("Overview").performClick()
        compose.onNodeWithText("Ridge Earth • 8:00 PM").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneOverviewWithoutSomePictures() {
        show(phoneOverviewFixture().withoutSomePictures())
        // REC does not vanish with the thumbnail.
        compose.onNodeWithContentDescription("Recording").assertIsDisplayed()
        // Nor does the time on air: Harbor Kickoff and Paper Kite Tales keep their progress under the text.
        for (progress in listOf(0.16f, 0.68f)) {
            compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(progress, 0f..1f)), useUnmergedTree = true)
                .assertIsDisplayed()
        }
        // The bar fits under the third line: a row without its thumbnail keeps the height of one with it.
        val withThumbnail = compose.onNodeWithText("Ridge Light").fetchSemanticsNode().size.height
        assertEquals(withThumbnail, compose.onNodeWithText("Harbor Kickoff").fetchSemanticsNode().size.height)
        capture("phone-guide-overview-no-picture", TouchCanvas.PHONE)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneFirstLoad() {
        show(phoneOverviewFixture().copy(availability = GuideAvailability.Loading))
        compose.onNodeWithText("Loading guide…").assertIsDisplayed()
        compose.onNodeWithText("Overview").assertDoesNotExist()
        capture("phone-guide-loading", TouchCanvas.PHONE)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneChannelDay() {
        val fixture = byChannelFixture(phone = true)
        val channel = fixture.channels.first()
        val snowline = channel.programs.first { it.title == "Snowline" }
        val programs = channel.programs.map {
            if (it.id == snowline.id) it.copy(timeRange = "10:00–10:45 PM") else it
        }
        show(
            fixture.copy(channels = listOf(channel.copy(programs = programs)) + fixture.channels.drop(1)),
            GuideView.ByChannel, open = RIDGE_LIGHT_ID,
        )
        capture("phone-guide-channel", TouchCanvas.PHONE)

        // A tap on another program opens it instead.
        compose.onNodeWithText("Snowline").performClick()
        compose.onAllNodes(hasText("Winter on the ridge", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("10:00–10:45 PM", substring = true)).assertCountEquals(1)
        compose.onNodeWithText("45m • Documentary").assertIsDisplayed()

        swipeDay(left = false)
        compose.onNodeWithText(channel.name).assertIsSelected()
        swipeDay(left = true, distance = 0.04f)
        compose.onNodeWithText(channel.name).assertIsSelected()
        assertEquals(emptyList<String>(), selectedChannels)
        swipeDay(left = true)
        val next = fixture.channels[1]
        compose.onNode(hasText(next.name) and androidx.compose.ui.test.isSelectable()).assertIsSelected()
        compose.onNodeWithText("By channel").assertIsSelected()
        assertEquals(listOf(next.id), selectedChannels)
        motionScale.scaleFactor = 0f
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText(channel.name).performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText(channel.name).assertIsSelected()
            compose.onNodeWithText("Snowline").assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onRoot().performTouchInput {
            swipe(Offset(center.x, height * 0.83f), Offset(center.x, height * 0.45f))
        }
        compose.onNodeWithText(channel.name).assertIsSelected()
        compose.onNodeWithText("Overview").performClick()
        compose.onNodeWithText("Overview").assertIsSelected()
    }

    private fun swipeDay(left: Boolean, tablet: Boolean = false, distance: Float = 0.5f) {
        compose.onRoot().performTouchInput {
            val right = width * 0.89f
            val other = right - width * (if (tablet) 0.28f else distance)
            val y = height * 0.54f
            swipe(Offset(if (left) right else other, y), Offset(if (left) other else right, y))
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneDetails() {
        show(overviewFixture(), detailsId = GLASS_HARBOR_ID)
        capture("phone-details", TouchCanvas.PHONE)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Overview").assertIsDisplayed()
        assertEquals("The page's Back button clears Details too", listOf<String?>(null), details)
    }

    @Test
    fun aNewSessionGetsCoverageWhileAnOldRequestIsStillSuspended() {
        val connection = FakeConnection()
        val server = ServerGuide(Instant.parse("2027-01-15T08:00:00Z"))
        val release = CompletableDeferred<Unit>()
        var admitted = 0
        var cancelled = false
        try {
            connection.session.publish(server.connected())
            showDestination(connection) {
                if (++admitted == 1) {
                    try {
                        release.await()
                    } finally {
                        cancelled = !currentCoroutineContext().isActive
                    }
                }
            }
            compose.runOnIdle {
                assertEquals(1, admitted)
                connection.session.replaceGeneration(server.connected())
            }
            compose.runOnIdle {
                assertEquals("Replacement session acquires the same window", 2, admitted)
                assertTrue("Old request was cancelled", cancelled)
            }
        } finally {
            release.complete(Unit)
            connection.close()
        }
    }

    @Test
    @Config(
        qualifiers = "en-rUS-w1024dp-h558dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.common.TimeConverterKt"],
    )
    fun tabletDayHeadingAndPreviouslyOpenedDayDoNotWaitForMapping() {
        val connection = FakeConnection()
        val noon = Instant.parse("2027-01-15T12:00:00Z")
        val fixture = ServerGuide(noon)
        val tomorrow = EpgEvent.create(
            EventId(800), fixture.first.id, Instant.parse("2027-01-16T12:00:00Z"),
            Instant.parse("2027-01-16T13:00:00Z"), title = "Saturday programme",
        )
        val worker = StandardTestDispatcher()
        fun finishMapping() {
            compose.waitForIdle()
            worker.scheduler.runCurrent()
            compose.waitForIdle()
        }
        try {
            assertTrue(SystemClock.setCurrentTimeMillis(noon.toEpochMilliseconds()))
            connection.session.publish(fixture.connected(epg = EpgSnapshot.create(fixture.snapshot.events + tomorrow)))
            showDestination(connection, mappingDispatcher = worker) {}
            finishMapping()
            compose.onNodeWithText("By channel").performClick()
            finishMapping()
            compose.onNodeWithText("Evening News").assertIsDisplayed()
            compose.onNodeWithText("Tomorrow").performClick()
            val dateChanged = compose.onNodeWithText("Saturday, January 16").isDisplayed()
            finishMapping()
            compose.onNodeWithText("Saturday programme").assertIsDisplayed()
            compose.runOnIdle {
                val revised = fixture.refreshedEpg("Revised Friday programme").events
                connection.session.publish(fixture.connected(epg = EpgSnapshot.create(revised + tomorrow)))
            }
            compose.onNodeWithText("Today").performClick()
            val returned = compose.onNodeWithText("Evening News").isDisplayed()
            assertEquals("Heading changes and Today returns before the worker runs", listOf(true, true),
                listOf(dateChanged, returned))
            finishMapping()
            compose.onNodeWithText("Revised Friday programme").assertIsDisplayed()
        } finally {
            connection.close()
        }
    }

    @Test
    @Config(instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.common.TimeConverterKt"])
    fun coverageFollowsLocalMidnightWithoutWaitingForUtcMidnight() {
        val savedZone = TimeZone.getDefault()
        val connection = FakeConnection()
        val before = Instant.parse("2027-01-15T22:59:58Z")
        val windows = mutableListOf<Instant>()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Vienna"))
            assertTrue(SystemClock.setCurrentTimeMillis(before.toEpochMilliseconds()))
            connection.session.publish(ServerGuide(before).connected())
            showDestination(connection) { windows += it }
            compose.runOnIdle { assertEquals(listOf(Instant.parse("2027-01-15T23:00:00Z")), windows) }
            assertTrue(SystemClock.setCurrentTimeMillis(before.toEpochMilliseconds() + 5_000))
            compose.mainClock.advanceTimeBy(5_016)
            compose.runOnIdle {
                assertEquals("Coverage and the shown local day roll together", Instant.parse("2027-01-16T23:00:00Z"), windows.last())
            }
        } finally {
            connection.close()
            TimeZone.setDefault(savedZone)
        }
    }

    @Test
    @Config(instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.common.TimeConverterKt"])
    fun unchangedGuideMapsOnceWithinAMinuteAndUpdatesAtTheBoundary() {
        val connection = FakeConnection()
        val noon = Instant.parse("2027-01-15T12:00:00Z")
        val before = noon.toEpochMilliseconds() + 29 * 60_000 + 50_000
        withMappingTrace {
            try {
                val guide = ServerGuide(noon)
                connection.session.publish(guide.connected())
                assertTrue(SystemClock.setCurrentTimeMillis(before))
                showDestination(connection) {}
                compose.onNodeWithText("Evening News").assertIsDisplayed()
                assertTrue(SystemClock.setCurrentTimeMillis(before + 5_000))
                compose.mainClock.advanceTimeBy(5_016)
                compose.runOnIdle { assertEquals("No remap within the same minute", 1, guideMappings()) }
                val entries = guide.withRecordingSize(512)
                compose.runOnIdle { connection.session.publish(guide.connected(entries = entries)) }
                compose.runOnIdle { assertEquals("Recording size does not change the Guide", 1, guideMappings()) }
                compose.runOnIdle { connection.session.publish(guide.connected(entries, epg = guide.refreshedEpg())) }
                compose.runOnIdle { assertEquals("No remap for an event outside the shown window", 1, guideMappings()) }
                compose.runOnIdle {
                    connection.session.publish(guide.connected(entries, epg = guide.refreshedEpg("Updated News")))
                }
                compose.onNodeWithText("Updated News").assertIsDisplayed()
                compose.runOnIdle { assertEquals("One mapping for one visible publication", 2, guideMappings()) }
                assertTrue(SystemClock.setCurrentTimeMillis(before + 10_000))
                compose.mainClock.advanceTimeBy(5_016)
                compose.onNodeWithText("Weather").assertIsDisplayed()
                compose.onAllNodesWithText("Updated News").assertCountEquals(0)
                compose.runOnIdle { assertEquals("The minute wakes the mapper", 3, guideMappings()) }
            } finally {
                connection.close()
            }
        }
    }

    @Test
    @Config(instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.common.TimeConverterKt"])
    fun returningToGuideShowsTheLastMappingWithoutDoingItAgain() {
        val connection = FakeConnection()
        val noon = Instant.parse("2027-01-15T12:00:00Z")
        var shown by mutableStateOf(true)
        withMappingTrace {
            try {
                assertTrue(SystemClock.setCurrentTimeMillis(noon.toEpochMilliseconds()))
                connection.session.publish(ServerGuide(noon).connected())
                showDestination(connection, shown = { shown }) {}
                compose.onNodeWithText("Evening News").assertIsDisplayed()
                compose.runOnIdle { shown = false }
                compose.onAllNodesWithText("Evening News").assertCountEquals(0)
                compose.runOnIdle { shown = true }
                compose.onNodeWithText("Evening News").assertIsDisplayed()
                compose.runOnIdle { assertEquals("Re-entry reuses the previous mapping", 1, guideMappings()) }
            } finally {
                connection.close()
            }
        }
    }

    private fun withMappingTrace(test: () -> Unit) {
        val info = compose.activity.applicationInfo
        val original = info.flags
        info.flags = original or ApplicationInfo.FLAG_DEBUGGABLE
        ShadowLog.clear()
        try {
            test()
        } finally {
            info.flags = original
        }
    }

    @Test
    fun aNewGuideInputSupersedesARequestInTheSameUiTurn() {
        val connection = FakeConnection()
        val noon = Instant.parse("2027-01-15T12:00:00Z")
        val fixture = ServerGuide(noon)
        val viewModel = connection.guide()
        val channels = connection.channels()
        val context = compose.activity
        withMappingTrace {
            try {
                compose.runOnIdle { connection.session.publish(fixture.connected()) }
                compose.runOnIdle {
                    fun request(observation: at.bernhardberger.tvheadend.sdk.core.SessionObservation) =
                        viewModel.map(
                            context,
                            GuideMappingInput(
                                channels.scope.value, ConnectionUiState.Ready, false, observation,
                                GuideSelection(), GuideClock(noon.epochSeconds, ZoneOffset.UTC),
                                Configuration(context.resources.configuration), true, null, null,
                            ),
                        )

                    request(connection.session.observation.value)
                    request(fixture.connected(epg = fixture.refreshedEpg("Updated News")))
                }
                compose.runOnIdle {
                    assertEquals("Only the latest queued Guide input maps", 1, guideMappings())
                    assertEquals("Updated News", viewModel.mapped.value?.state?.channels?.first()?.programs?.first()?.title)
                }
            } finally {
                connection.close()
            }
        }
    }

    @Test
    fun anInFlightFirstGuideCanPublishWhileEpgUpdatesAreQueued() {
        val connection = FakeConnection()
        val noon = Instant.parse("2027-01-15T12:00:00Z")
        val fixture = ServerGuide(noon)
        val worker = StandardTestDispatcher()
        val viewModel = GuideViewModel(connection.session, EmptyCoroutineContext, mappingDispatcher = worker)
        val channels = connection.channels()
        val context = compose.activity
        val receiver = CoroutineScope(Dispatchers.Unconfined)
        val shown = mutableListOf<String>()
        try {
            receiver.launch {
                viewModel.mapped.collect {
                    it?.state?.channels?.firstOrNull()?.programs?.firstOrNull()?.title?.let(shown::add)
                }
            }
            compose.runOnIdle { connection.session.publish(fixture.connected()) }
            val scope = channels.scope.value
            fun input(title: String): GuideMappingInput {
                connection.session.publish(fixture.connected(epg = fixture.refreshedEpg(title)))
                return GuideMappingInput(
                    scope, ConnectionUiState.Ready, false, connection.session.observation.value,
                    GuideSelection(), GuideClock(noon.epochSeconds, ZoneOffset.UTC),
                    Configuration(context.resources.configuration), true, null, null,
                )
            }
            val first = input("First available guide")
            val intermediate = input("Intermediate guide")
            val latest = input("Latest guide")
            assertEquals(listOf("epg"), intermediate.changesFrom(first))
            assertEquals(listOf("epg"), latest.changesFrom(intermediate))
            compose.runOnIdle { viewModel.map(context, first) }
            compose.runOnIdle {
                viewModel.map(context, intermediate)
                viewModel.map(context, latest)
            }
            worker.scheduler.runCurrent()
            compose.waitForIdle()
            worker.scheduler.runCurrent()
            compose.runOnIdle {
                assertEquals(listOf("First available guide", "Latest guide"), shown)
            }
        } finally {
            receiver.cancel()
            connection.close()
        }
    }

    private fun guideMappings() =
        ShadowLog.getLogsForTag("TouchArtwork").count { it.msg.startsWith("mapped Guide ") }

    private fun showDestination(
        connection: FakeConnection,
        shown: () -> Boolean = { true },
        mappingDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
        onCoverage: suspend (Instant) -> Unit,
    ) {
        val fake = connection.session
        val session = object : TvheadendSession by fake {
            override val epgRepository = object : EpgRepository by fake.epgRepository {
                override suspend fun acquireCoverageBatch(
                    currentSession: CurrentSessionObservation,
                    channelIds: List<ChannelId>,
                    through: Instant,
                ) = fake.epgRepository.acquireCoverageBatch(currentSession, channelIds, through).also {
                    onCoverage(through)
                }
            }
        }
        val guide = GuideViewModel(session, EmptyCoroutineContext, mappingDispatcher = mappingDispatcher)
        val channels = connection.channels()
        val presentation = connection.presentation()
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                val dvr = viewModel { DvrControls(session, connection.noticeCenter) }
                if (shown()) GuideDestination(
                    guide, channels, presentation, listOf(Destination.Guide), playingId = null, onWatch = null,
                    dvr = dvr, onOpenConnections = {}, onSignIn = {},
                )
            }
        }
    }

    private fun show(
        fixture: GuideState,
        view: GuideView = GuideView.Overview,
        channel: String? = null,
        open: String? = null,
        detailsId: String? = null,
        state: () -> GuideState = { fixture },
        over: @Composable () -> Unit = {},
    ) {
        var shownDetailsId by mutableStateOf(detailsId)
        var selectedDay by mutableStateOf<Int?>(null)
        var selectedView by mutableStateOf(view)
        var selectedChannel by mutableStateOf(channel)
        val actions = GuideActions(
            onWatch = { watched += it },
            onRecord = {},
            onRecordOptions = {},
            onDetails = {
                details += it
                shownDetailsId = it
            },
            onDay = { selectedDay = it },
            onJump = {},
            onTag = {},
            onCancel = { cancelled += it },
            onStop = { stopped += it },
            onView = { selectedView = it },
            onChannel = {
                selectedChannels += it
                selectedChannel = it
                selectedView = GuideView.ByChannel
            },
        )
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                val current = state()
                GuideScreen(
                    current.copy(
                        details = current.details?.takeIf { it.id == shownDetailsId },
                        selectedDay = selectedDay ?: current.selectedDay,
                    ), actions,
                    view = selectedView, channelId = selectedChannel, initiallyOpen = open,
                )
                over()
            }
        }
    }

    /** A line of parts too narrow for all of them drops whole parts from its end; none is cut by an ellipsis. */
    private fun assertPartLinesWhole() {
        val lines = compose.onAllNodes(hasText(" • ", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(lines.isNotEmpty())
        lines.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            assertFalse(layout.layoutInput.text.text, (0 until layout.lineCount).any(layout::isLineEllipsized))
        }
    }

    /** Ridge Light's "13m left • Documentary" is too wide for its narrow row: the genre stands last and goes. */
    private fun assertTimeLeftStays() {
        compose.onAllNodesWithText("13m left", useUnmergedTree = true).onFirst().assertIsDisplayed()
        compose.onAllNodes(hasText("13m left • Documentary"), useUnmergedTree = true).assertCountEquals(0)
    }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch(GROUP, name, canvas)

    private companion object {
        const val GROUP = "slice4"
        const val TABLET = "en-rUS-w1280dp-h800dp-land-xhdpi"
        const val UPRIGHT = "en-rUS-w800dp-h1280dp-port-xhdpi"
        const val PHONE = "en-rUS-w412dp-h892dp-port-xhdpi"
        const val COASTAL_MILES_SYNOPSIS_START = "Along the northern coast road"
    }
}
