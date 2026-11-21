package at.bernhardberger.tvhplayer.touch.guide

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgRepository
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
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(TvheadendTestingApi::class)
class GuideScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val details = mutableListOf<String?>()
    private val watched = mutableListOf<String>()
    private val cancelled = mutableListOf<String>()
    private val stopped = mutableListOf<String>()

    @Test
    @Config(qualifiers = TABLET)
    fun tabletOverviewOpensAProgramInPlaceAndAChannelHeadOpensItsDay() {
        show(overviewFixture())
        capture("tablet-guide-overview", TouchCanvas.TABLET)

        // A tap opens the program where it is and plays nothing; only on-air programs offer Watch.
        val row = compose.onAllNodesWithText("Coastal Miles")[0]
        val titleTop = row.getBoundsInRoot().top
        row.performClick()
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertIsDisplayed()
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

        compose.onNodeWithContentDescription("Ridge Earth, by channel").performClick()
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
        capture("tablet-guide-channel", TouchCanvas.TABLET)

        compose.onNodeWithText("Watch").performClick()
        assertEquals(listOf("ridge-earth"), watched)

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
        show(byChannelFixture(phone = true), GuideView.ByChannel, open = RIDGE_LIGHT_ID)
        capture("phone-guide-channel", TouchCanvas.PHONE)

        // A tap on another program opens it instead.
        compose.onNodeWithText("Snowline").performClick()
        compose.onAllNodes(hasText("Winter on the ridge", substring = true)).assertCountEquals(0)
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

    private fun showDestination(connection: FakeConnection, onCoverage: suspend (Instant) -> Unit) {
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
        val guide = GuideViewModel(session, EmptyCoroutineContext)
        val channels = connection.channels()
        val presentation = connection.presentation()
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                val dvr = viewModel { DvrControls(session, connection.noticeCenter) }
                GuideDestination(
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
        val actions = GuideActions(
            onWatch = { watched += it },
            onRecord = {},
            onRecordOptions = {},
            onDetails = {
                details += it
                shownDetailsId = it
            },
            onDay = {},
            onJump = {},
            onTag = {},
            onCancel = { cancelled += it },
            onStop = { stopped += it },
        )
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                val current = state()
                GuideScreen(
                    current.copy(details = current.details?.takeIf { it.id == shownDetailsId }), actions,
                    initialView = view, initialChannelId = channel, initiallyOpen = open,
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
