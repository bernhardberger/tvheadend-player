package at.bernhardberger.tvhplayer.touch.guide

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.CompositionLocalProvider
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgRepository
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvhplayer.touch.R
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
import at.bernhardberger.tvhplayer.touch.ui.DestinationFrame
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationReselections
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.screens.recordings.StopConfirmationTimes
import at.bernhardberger.tvhplayer.ui.screens.recordings.stopConfirmationSentence
import java.util.TimeZone
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.Duration
import android.content.res.Configuration
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.common.formatDayHeader
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
        // Open the program without leaving a native press ripple in the next captures.
        row.performSemanticsAction(SemanticsActions.OnClick)
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

        // Removing the unrelated first channel does not change which program is open.
        row.performClick()
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(channels = state.value.channels.drop(1)) }
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertIsDisplayed()
        compose.runOnIdle { state.value = fixture.withoutLogo("ridge-earth") }
        back()

        compose.onNode(head).performClick()
        compose.onNodeWithText("Sunday, November 15").assertIsDisplayed()
        compose.onNodeWithText("Six Bridges and a River That Changed Its Mind").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletOverviewRequestsLaterColumnsWhileCoverageIsPending() {
        val fixture = overviewFixture()
        val head = fixture.channels.first()
        val program = head.programs.first()
        val later = head.copy(programs = listOf(program))
        val seed = List(8) { index ->
            GuideChannel("empty-$index", "Channel ${index + 1}", gap = if (index == 0) GuideGap("8:00", "9:00") else null)
        }
        val state = mutableStateOf(
            fixture.copy(channels = seed + later.copy(programs = emptyList(), overviewPending = true), dayLoading = true),
        )
        show(fixture, state = { state.value }, onOverviewViewport = { visible ->
            if (later.id in visible) state.value = state.value.copy(channels = seed + later)
        })

        val noPrograms = compose.activity.getString(R.string.guide_no_programs)
        val list = compose.onNode(
            hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange),
        )
        list.assertIsDisplayed()
        compose.onNodeWithText(noPrograms).assertDoesNotExist()
        list.performScrollToIndex(seed.size)
        compose.onNodeWithText(program.title).assertIsDisplayed()
        list.performScrollToIndex(0)
        compose.onNodeWithText(noPrograms).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(dayLoading = false) }
        compose.onNodeWithText(noPrograms).assertIsDisplayed()
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
        state = fixture.copy(channels = fixture.channels.map { channel ->
            channel.copy(day = (channel.day ?: channel.programs).filterNot { it.id == RIDGE_LIGHT_ID })
        })
        compose.onNodeWithText("60m • 2024 • Documentary").assertDoesNotExist()
        state = fixture
        compose.onNodeWithText("60m • 2024 • Documentary").assertDoesNotExist()

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
    @Config(qualifiers = TABLET)
    fun anOpenedProgramDoesNotReopenAfterLeavingItsList() {
        val fixture = overviewFixture()
        var state by mutableStateOf(fixture)
        show(fixture, state = { state })
        compose.onAllNodesWithText("Coastal Miles")[0].performClick()
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertIsDisplayed()
        val pending = fixture.copy(channels = fixture.channels.map { channel ->
            if (channel.programs.any { it.title == "Coastal Miles" }) {
                channel.copy(programs = emptyList(), overviewPending = true)
            } else channel
        })
        state = pending
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertDoesNotExist()
        state = fixture
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertIsDisplayed()
        state = pending
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertDoesNotExist()
        state = fixture.copy(channels = fixture.channels.map { channel ->
            channel.copy(programs = channel.programs.filterNot { it.title == "Coastal Miles" })
        })
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertDoesNotExist()
        state = fixture
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertDoesNotExist()
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
    @Config(qualifiers = "en-rUS-w412dp-h558dp-port-xhdpi")
    fun reselectingGuideReturnsFromDetailsBeforeScrollingTheSelectedDay() {
        motionScale.scaleFactor = 0f
        val taps = MutableSharedFlow<Destination>(extraBufferCapacity = 1)
        val fixture = byChannelFixture(phone = true)
        val channel = fixture.channels.first()
        val program = channel.programs.last()
        val first = glassHarborDetails().copy(id = program.id)
        val airing = first.otherAirings.first()
        val returnAiring = airing.copy(id = first.id, time = "Today 8:00 PM")
        val second = first.copy(id = airing.id, title = "The next showing", otherAirings = listOf(returnAiring))
        show(
            fixture.copy(details = mapOf(GuidePageKey.Details(first.id, 0) to first)), extraDetails = second,
            view = GuideView.ByChannel, channel = channel.id, taps = taps,
        )
        val list = compose.onNode(
            hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        )
        list.performScrollToIndex(channel.programs.lastIndex)
        fun position() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithText(program.title).performSemanticsAction(SemanticsActions.OnClick)
        val detailsButton = compose.onNodeWithText("Details")
        detailsButton.performScrollTo()
        val scrolled = position()
        assertTrue(scrolled > 0f)
        detailsButton.performSemanticsAction(SemanticsActions.OnClick)
        fun openAiring() {
            compose.onNodeWithText(airing.time).performScrollTo().performClick()
            compose.onNodeWithText(second.title).assertIsDisplayed()
        }
        openAiring()
        back()
        compose.onNodeWithText(first.title).assertExists()
        back()
        compose.onNodeWithText("By channel").assertIsSelected()
        val afterBack = position()
        detailsButton.performScrollTo()
        val beforeReselect = position()
        detailsButton.performSemanticsAction(SemanticsActions.OnClick)
        openAiring()
        compose.onNodeWithText(returnAiring.time).performScrollTo().performClick()
        compose.onNodeWithText(first.title).assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText(second.title).assertIsDisplayed()
        compose.runOnIdle { check(taps.tryEmit(Destination.Guide)) }
        compose.onNodeWithText("By channel").assertIsSelected()
        val afterReselect = position()
        compose.runOnIdle { check(taps.tryEmit(Destination.Guide)) }
        assertEquals(0f, position(), 0f)
        assertEquals("Scroll after ordinary Back and after reselecting Guide",
            listOf(scrolled, beforeReselect), listOf(afterBack, afterReselect))
    }

    @Test
    @Config(qualifiers = PHONE, instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.common.TimeConverterKt"])
    fun phoneChannelDayStaysOpenAndScrolledDuringBackgroundPublications() {
        val connection = FakeConnection()
        val now = Instant.parse("2027-01-15T12:00:00Z")
        val fixture = ServerGuide(now)
        val end = fixture.snapshot.events.last().stop.epochSeconds
        val schedule = fixture.snapshot.events + List(12) { index ->
            val start = end + index * 900
            EpgEvent.create(
                EventId(100L + index), fixture.first.id,
                Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(start + 900), title = "Programme $index",
            )
        }
        val worker = StandardTestDispatcher()
        fun finishMapping() {
            compose.waitForIdle()
            worker.scheduler.runCurrent()
            compose.waitForIdle()
        }
        try {
            assertTrue(SystemClock.setCurrentTimeMillis(now.toEpochMilliseconds()))
            connection.session.publish(fixture.connected(epg = EpgSnapshot.create(schedule)))
            showDestination(connection, mappingDispatcher = worker) {}
            finishMapping()
            compose.onNodeWithText("By channel").performClick()
            finishMapping()

            val list = compose.onNode(
                hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
            )
            val loading = compose.onNodeWithContentDescription(compose.activity.getString(SharedR.string.epg_loading))
            fun position() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            val last = schedule.last()
            list.performScrollToIndex(schedule.lastIndex)
            compose.onNodeWithText(last.title.orEmpty()).performClick()
            val detailsButton = compose.onNodeWithText("Details")
            detailsButton.performScrollTo()
            val scrolled = position()
            assertTrue(scrolled > 0f)
            fun assertDayHeld() {
                loading.assertDoesNotExist()
                assertEquals("The open list keeps its scroll position", scrolled, position(), 0f)
                detailsButton.assertIsDisplayed()
            }

            val revised = listOf("Updated programme", "Revised programme").map { title ->
                EpgSnapshot.create(schedule.dropLast(1) +
                    EpgEvent.create(last.id, fixture.first.id, last.start, last.stop, title = title))
            }
            revised.forEach { epg ->
                compose.runOnIdle { connection.session.publish(fixture.connected(epg = epg)) }
                assertDayHeld()
                finishMapping()
                assertDayHeld()
                compose.onNodeWithText(epg.events.last().title.orEmpty()).assertIsDisplayed()
            }
            compose.runOnIdle {
                connection.session.publish(fixture.connected(fixture.withRecordingSize(4096), epg = revised.last()))
            }
            assertDayHeld()
            finishMapping()
            assertDayHeld()
        } finally {
            connection.close()
        }
    }

    @Test
    @Config(qualifiers = PHONE, instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.common.TimeConverterKt"])
    fun aMissingProgramClosesDetailsWithoutReopeningOnASubsequentPublication() {
        motionScale.scaleFactor = 0f
        val connection = FakeConnection()
        val now = Instant.parse("2027-01-15T12:00:00Z")
        val server = ServerGuide(now)
        val airing = EpgEvent.create(
            EventId(800), server.second.id, Instant.parse("2027-01-15T15:00:00Z"),
            Instant.parse("2027-01-15T16:00:00Z"), title = "Evening News",
        )
        val replacement = EpgEvent.create(
            EventId(801), server.second.id, Instant.parse("2027-01-15T17:00:00Z"),
            Instant.parse("2027-01-15T18:00:00Z"), title = "Evening News",
        )
        try {
            assertTrue(SystemClock.setCurrentTimeMillis(now.toEpochMilliseconds()))
            connection.session.publish(server.connected(epg = EpgSnapshot.create(server.snapshot.events + airing)))
            val guide = showDestination(connection) {}
            compose.onNodeWithText("By channel").performClick()
            compose.onNodeWithText("Evening News").performClick()
            compose.onNodeWithText("Details").performClick()
            compose.onNodeWithText("Overview").assertDoesNotExist()
            compose.onNodeWithText("Other airings of Evening News").assertExists()
            compose.runOnIdle { connection.session.publish(server.connected()) }
            val afterRemoval = compose.runOnIdle {
                guide.mapped.value?.state?.details?.values?.single()?.otherAirings?.map { it.id }
            }
            compose.runOnIdle {
                connection.session.publish(server.connected(epg = EpgSnapshot.create(server.snapshot.events + replacement)))
            }
            val afterAddition = compose.runOnIdle {
                guide.mapped.value?.state?.details?.values?.single()?.otherAirings?.map { it.id }
            }
            assertEquals("Open Details receives removed and added airings", listOf(emptyList(), listOf("801")),
                listOf(afterRemoval, afterAddition))
            compose.onNodeWithText("Other airings of Evening News").assertExists()
            val nextAiring = compose.runOnIdle {
                guide.mapped.value?.state?.details?.values?.single()?.otherAirings?.single()?.time
            }
            compose.onNodeWithText(checkNotNull(nextAiring)).performScrollTo().performClick()
            val secondPageChannel = compose.onNode(hasText("Channel Two") and !hasClickAction())
            secondPageChannel.assertExists()
            compose.runOnIdle { connection.session.publish(server.connected()) }
            compose.onNodeWithText("About Evening News").assertExists()
            compose.onNodeWithText("Overview").assertDoesNotExist()
            compose.runOnIdle {
                connection.session.publish(server.connected(epg = EpgSnapshot.create(server.snapshot.events + replacement)))
            }
            compose.onNodeWithText("About Evening News").assertExists()
            secondPageChannel.assertDoesNotExist()
            compose.runOnIdle {
                connection.session.publish(server.connected(epg = EpgSnapshot.create(server.snapshot.events.drop(1))))
            }
            compose.onNodeWithText("Overview").assertIsDisplayed()
            compose.runOnIdle { connection.session.publish(server.connected()) }
            compose.onNodeWithText("Overview").assertIsDisplayed()
        } finally {
            connection.close()
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h558dp-port-xhdpi")
    fun predictiveBackPreviewsTheGuideWithoutReportingItsNavigationBar() {
        motionScale.scaleFactor = 0f
        val fixture = phoneOverviewFixture()
        val program = fixture.channels.last().programs.first()
        val page = glassHarborDetails().copy(id = program.id, title = program.title)
        show(fixture.copy(details = mapOf(GuidePageKey.Details(page.id, 0) to page)), inShell = true)
        val dispatcher = compose.activity.onBackPressedDispatcher
        val bar = compose.onNode(hasText("Guide") and hasClickAction())
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(fixture.channels.lastIndex)
        compose.onNodeWithText(program.title).performSemanticsAction(SemanticsActions.OnClick)
        val detailsButton = compose.onNodeWithText("Details")
        detailsButton.performScrollTo()
        fun position() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val scrolled = position()
        assertTrue(scrolled > 0f)
        detailsButton.performSemanticsAction(SemanticsActions.OnClick)
        bar.assertDoesNotExist()
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.onNodeWithText("Overview").assertExists()
        bar.assertDoesNotExist()
        assertEquals("Scroll during predictive preview", scrolled, position(), 0f)
        back()
        bar.assertIsDisplayed()
        assertEquals(scrolled, position(), 0f)
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
        val saved24Hour = AppClock.systemUses24Hour
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
            val revised = EpgSnapshot.create(fixture.refreshedEpg("Revised Friday programme").events + tomorrow)
            compose.runOnIdle { connection.session.publish(fixture.connected(epg = revised)) }
            compose.onNodeWithText("Today").performClick()
            val returned = compose.onNodeWithText("Evening News").isDisplayed()
            assertEquals("Heading changes and Today returns before the worker runs", listOf(true, true),
                listOf(dateChanged, returned))
            finishMapping()
            compose.onNodeWithText("Revised Friday programme").assertIsDisplayed()
            compose.runOnIdle { AppClock.systemUses24Hour = !AppClock.systemUses24Hour }
            assertFalse("A format change waits for reformatted rows",
                compose.onNodeWithText("Revised Friday programme").isDisplayed())
            finishMapping()
            compose.onNodeWithText("Revised Friday programme").assertIsDisplayed()
            compose.onNodeWithText("Weather").performClick()
            val cancel = compose.onNodeWithText(compose.activity.getString(SharedR.string.cancel_recording))
            cancel.assertDoesNotExist()
            compose.runOnIdle {
                connection.session.publish(fixture.connected(
                    entries = fixture.dvr.entries + fixture.scheduling(900L, 2),
                    epg = revised,
                ))
            }
            compose.onNodeWithText("Details").assertIsDisplayed()
            finishMapping()
            cancel.assertIsDisplayed()
        } finally {
            AppClock.systemUses24Hour = saved24Hour
            connection.close()
        }
    }

    @Test
    @Config(instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.common.TimeConverterKt"])
    fun byChannelDoesNotShowYesterdaysRowsUnderTodaysHeading() {
        val connection = FakeConnection()
        val savedZone = TimeZone.getDefault()
        val before = Instant.parse("2027-01-15T23:59:58Z")
        val worker = StandardTestDispatcher()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertTrue(SystemClock.setCurrentTimeMillis(before.toEpochMilliseconds()))
            connection.session.publish(ServerGuide(before).connected())
            showDestination(connection, mappingDispatcher = worker) {}
            compose.waitForIdle()
            worker.scheduler.runCurrent()
            compose.onNodeWithText("By channel").performClick()
            compose.waitForIdle()
            worker.scheduler.runCurrent()
            compose.onNodeWithText("Evening News").assertIsDisplayed()
            assertTrue(SystemClock.setCurrentTimeMillis(before.toEpochMilliseconds() + 5_000))
            compose.mainClock.advanceTimeBy(5_016)
            assertEquals("Yesterday's day must not survive midnight while mapping is held",
                false, compose.onNodeWithText("Evening News").isDisplayed())
        } finally {
            TimeZone.setDefault(savedZone)
            connection.close()
        }
    }

    @Test
    @Config(instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.common.TimeConverterKt"])
    fun guideDoesNotServeRowsFromThePreviousTagOrGenerationWhileMappingIsHeld() {
        val connection = FakeConnection()
        val now = Instant.parse("2027-01-15T12:00:00Z")
        val worker = StandardTestDispatcher()
        try {
            assertTrue(SystemClock.setCurrentTimeMillis(now.toEpochMilliseconds()))
            val fixture = ServerGuide(now)
            connection.session.publish(fixture.connected())
            val channelObservation = MutableStateFlow(connection.session.observation.value)
            val channelSession = object : TvheadendSession by connection.session {
                override val observation = channelObservation
            }
            val channels = ChannelsViewModel(
                channelSession, connection.channelTagSettings(), connection.owner.liveProfile,
            )
            showDestination(connection, mappingDispatcher = worker, channels = channels) {}
            compose.waitForIdle()
            worker.scheduler.runCurrent()
            compose.onNodeWithText("Evening News").assertIsDisplayed()
            compose.onNodeWithText("All channels").performClick()
            compose.onNodeWithText("News", substring = false).performClick()
            val hiddenAfterTag = !compose.onNodeWithText("Evening News").isDisplayed()
            worker.scheduler.runCurrent()
            compose.onNodeWithText("Evening News").assertIsDisplayed()
            compose.runOnIdle {
                connection.session.replaceGeneration(fixture.connected(epg = fixture.refreshedEpg("Reconnected News")))
            }
            val hiddenAfterReconnect = !compose.onNodeWithText("Evening News").isDisplayed()
            assertEquals("Previous tag and generation rows are hidden",
                listOf(true, true), listOf(hiddenAfterTag, hiddenAfterReconnect))
            worker.scheduler.runCurrent()
            assertFalse("The new guide must wait for its own channel scope",
                compose.onNodeWithText("Reconnected News").isDisplayed())
            compose.runOnIdle { channelObservation.value = connection.session.observation.value }
            compose.waitForIdle()
            worker.scheduler.runCurrent()
            compose.onNodeWithText("Reconnected News").assertIsDisplayed()
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
    fun guideRestoresItsPlaceAndLetsAnExpiredDayFallBackToToday() {
        val now = Instant.parse("2027-01-15T12:00:00Z")
        assertTrue(SystemClock.setCurrentTimeMillis(now.toEpochMilliseconds()))
        var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        compose.setContent { content?.invoke() }
        GuideView.entries.forEach { view ->
            assertRestoredGuideDay(view, now) { body ->
                compose.runOnIdle { content = { key(view) { body() } } }
            }
        }
    }

    private fun assertRestoredGuideDay(view: GuideView, now: Instant, setContent: (@Composable () -> Unit) -> Unit) {
        val connection = FakeConnection()
        val fixture = ServerGuide(now)
        val zone = ZoneId.systemDefault()
        val today = GuideClock(now.epochSeconds, zone).today
        val tomorrow = today.plusDays(1)
        val missingDay = if (view == GuideView.Overview) today.plusDays(90) else tomorrow
        val tomorrowThrough = Instant.fromEpochSeconds(tomorrow.plusDays(1).atStartOfDay(zone).toEpochSecond())
        val missingThrough = Instant.fromEpochSeconds(missingDay.plusDays(1).atStartOfDay(zone).toEpochSecond())
        val channelId = fixture.second.id.value.toString()
        val start = Instant.fromEpochSeconds(tomorrow.atStartOfDay(zone).toEpochSecond())
        val future = EpgEvent.create(EventId(800), fixture.second.id, start,
            Instant.fromEpochSeconds(start.epochSeconds + 3600), title = "Kept day programme")
        val current = EpgEvent.create(EventId(801), fixture.second.id, now, fixture.snapshot.events.first().stop,
            title = "Current day programme")
        val firstSnapshot = fixture.connected(epg = EpgSnapshot.create(listOf(current)))
        val refilled = fixture.connected(epg = EpgSnapshot.create(listOf(current, future)))
        var release = CompletableDeferred<Unit>()
        val requestedWindows = mutableListOf<Instant>()
        val session = connection.withCoverage { through ->
            requestedWindows += through
            if (through == tomorrowThrough || through == missingThrough) release.await()
        }
        val factory = viewModelFactory {
            initializer {
                GuideViewModel(
                    session, createSavedStateHandle(), EmptyCoroutineContext, mappingDispatcher = Dispatchers.Main.immediate,
                )
            }
        }
        val kept = GuideSelection(view = view, day = tomorrow, channelId = channelId)
        try {
            motionScale.scaleFactor = 0f
            val savedState = Bundle()
            Robolectric.buildActivity(ComponentActivity::class.java).create().use { original ->
                val guide = ViewModelProvider.create(original.get(), factory)[GuideViewModel::class]
                guide.selectPage(view, channelId)
                guide.selectDay(tomorrow)
                guide.selectJump(1L)
                guide.openDetails(fixture.snapshot.events.first().id.value.toString())
                original.saveInstanceState(savedState)
            }
            Robolectric.buildActivity(ComponentActivity::class.java).create(savedState).use { restored ->
                val guide = ViewModelProvider.create(restored.get(), factory)[GuideViewModel::class]
                assertEquals(
                    "A fresh Guide restores the tab, day and channel, but not the jump or Details",
                    kept,
                    guide.selection.value,
                )
                connection.session.publish(firstSnapshot)
                val channels = connection.channels()
                val presentation = connection.presentation()
                setContent {
                    MaterialTheme(colorScheme = MobileDarkColors) {
                        val dvr = viewModel { DvrControls(connection.session, connection.noticeCenter) }
                        DestinationFrame(initialDestination = Destination.Guide) {
                            GuideDestination(
                                guide, channels, presentation, listOf(Destination.Guide), playingId = null,
                                onWatch = null, dvr = dvr, onOpenConnections = {}, onSignIn = {},
                            )
                        }
                    }
                }
                compose.onNodeWithText(compose.activity.getString(view.label)).assertIsSelected()
                if (view == GuideView.ByChannel) compose.onNodeWithText("Channel Two").assertIsSelected()
                compose.runOnIdle {
                    assertEquals(
                        "$view must not forget a kept day while coverage is pending",
                        kept,
                        guide.selection.value,
                    )
                    assertEquals(tomorrowThrough, requestedWindows.last())
                }
                val loading = compose.onNodeWithContentDescription(compose.activity.getString(SharedR.string.epg_loading))
                val locale = compose.activity.resources.configuration.locales[0]
                loading.assertIsDisplayed()
                compose.onNodeWithText(current.title!!).assertDoesNotExist()
                compose.runOnIdle {
                    connection.session.publish(refilled)
                    release.complete(Unit)
                }
                val keptDayLabel = if (view == GuideView.Overview) {
                    compose.activity.getString(SharedR.string.channel_schedule_tomorrow)
                } else formatDayHeader(tomorrow, locale)
                compose.onNodeWithText(keptDayLabel).assertIsDisplayed()
                compose.onNodeWithText(future.title!!).assertIsDisplayed()
                compose.runOnIdle { assertEquals(kept, guide.selection.value) }

                compose.runOnIdle {
                    release = CompletableDeferred()
                    if (missingDay != tomorrow) guide.selectDay(missingDay)
                    connection.session.replaceGeneration(firstSnapshot)
                }
                loading.assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals("The replacement session is still loading the kept day",
                        kept.copy(day = missingDay), guide.selection.value)
                    if (view == GuideView.ByChannel) release.complete(Unit)
                }
                if (view == GuideView.Overview) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
                compose.onNodeWithText("Today").assertIsDisplayed()
                compose.onNodeWithText(current.title!!).assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals(
                        "The day is forgotten once coverage settles without it", kept.copy(day = null), guide.selection.value,
                    )
                    if (view == GuideView.Overview) {
                        assertEquals("Do not retry an unreachable day", 1, requestedWindows.count { it == missingThrough })
                    }
                    guide.selectDay(today.minusDays(1))
                }
                compose.onNodeWithText(current.title!!).assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals("An expired day is forgotten", kept.copy(day = null), guide.selection.value)
                }
            }
        } finally {
            release.complete(Unit)
            connection.close()
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
    @Config(instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.common.TimeConverterKt"])
    fun aStoppedGuideMapsOnlyTheLatestInputsWhenItStartsAgain() {
        val connection = FakeConnection()
        val now = Instant.parse("2027-01-15T12:00:55Z")
        val fixture = ServerGuide(now)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        var shown by mutableStateOf(true)
        val worker = StandardTestDispatcher()
        var dispatchedPass: Job? = null
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatchedPass = context[Job]
                worker.dispatch(context, block)
            }
        }
        fun finishMapping() {
            compose.waitForIdle()
            worker.scheduler.runCurrent()
            compose.waitForIdle()
        }
        withMappingTrace {
            try {
                assertTrue(SystemClock.setCurrentTimeMillis(now.toEpochMilliseconds()))
                compose.runOnIdle {
                    owner.lifecycle.currentState = Lifecycle.State.STARTED
                    connection.session.publish(fixture.connected())
                }
                val guide = showDestination(
                    connection, shown = { shown }, mappingDispatcher = dispatcher, lifecycleOwner = owner,
                ) {}
                finishMapping()
                compose.onNodeWithText("Evening News").assertIsDisplayed()
                assertEquals(1, guideMappings())
                val previous = guide.mapped.value
                val publishedPass = dispatchedPass
                compose.runOnIdle {
                    connection.session.publish(fixture.connected(epg = fixture.refreshedEpg("Interrupted News")))
                }
                val interrupted = compose.runOnIdle {
                    // Main is suspended in this pass while its worker is held on virtual time.
                    val pass = checkNotNull(dispatchedPass)
                    assertTrue("The new pass reached the worker dispatcher", pass !== publishedPass && pass.isActive)
                    owner.lifecycle.currentState = Lifecycle.State.CREATED
                    connection.session.publish(fixture.connected(epg = fixture.refreshedEpg("Missed News")))
                    pass
                }
                finishMapping()
                assertTrue("Stopping cancels the dispatched pass", interrupted.isCancelled)
                assertTrue("Stopping keeps the last published result", previous === guide.mapped.value)
                assertTrue(SystemClock.setCurrentTimeMillis(now.toEpochMilliseconds() + 5_000))
                compose.mainClock.advanceTimeBy(5_016)
                compose.runOnIdle {
                    connection.session.publish(fixture.connected(epg = fixture.refreshedEpg("Latest News")))
                }
                finishMapping()
                compose.runOnIdle {
                    assertEquals("A stopped Guide must not map on the minute tick", 1, guideMappings())
                    assertTrue("No abandoned result was published", previous === guide.mapped.value)
                    owner.lifecycle.currentState = Lifecycle.State.STARTED
                }
                finishMapping()
                compose.onNodeWithText("Latest News").assertIsDisplayed()
                assertEquals("Resume maps the latest data and minute together", 2, guideMappings())
                compose.runOnIdle { shown = false }
                compose.onAllNodesWithText("Latest News").assertCountEquals(0)
                compose.runOnIdle {
                    connection.session.publish(fixture.connected(epg = fixture.refreshedEpg("New News")))
                }
                compose.runOnIdle {
                    assertEquals("A hidden Guide does not map a new publication", 2, guideMappings())
                    shown = true
                }
                finishMapping()
                compose.onNodeWithText("New News").assertIsDisplayed()
                assertEquals("Re-entry maps the latest publication once", 3, guideMappings())
            } finally {
                connection.close()
            }
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
                    connection.session.publish(fixture.connected(epg = fixture.refreshedEpg("Updated News")))
                    request(connection.session.observation.value)
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
        val viewModel = GuideViewModel(
            connection.session, SavedStateHandle(), EmptyCoroutineContext, mappingDispatcher = worker,
        )
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
        lifecycleOwner: LifecycleOwner? = null,
        channels: ChannelsViewModel = connection.channels(),
        onCoverage: suspend (Instant) -> Unit,
    ): GuideViewModel {
        val session = connection.withCoverage(onCoverage)
        val guide = GuideViewModel(
            session, SavedStateHandle(), EmptyCoroutineContext, mappingDispatcher = mappingDispatcher,
        )
        val presentation = connection.presentation()
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                val dvr = viewModel { DvrControls(session, connection.noticeCenter) }
                CompositionLocalProvider(LocalLifecycleOwner provides (lifecycleOwner ?: LocalLifecycleOwner.current)) {
                    if (shown()) DestinationFrame(initialDestination = Destination.Guide) {
                        GuideDestination(
                            guide, channels, presentation, listOf(Destination.Guide), playingId = null, onWatch = null,
                            dvr = dvr, onOpenConnections = {}, onSignIn = {},
                        )
                    }
                }
            }
        }
        return guide
    }

    private fun FakeConnection.withCoverage(onCoverage: suspend (Instant) -> Unit): TvheadendSession =
        object : TvheadendSession by session {
            override val epgRepository = object : EpgRepository by session.epgRepository {
                override suspend fun acquireCoverageBatch(
                    currentSession: CurrentSessionObservation,
                    channelIds: List<ChannelId>,
                    through: Instant,
                ) = session.epgRepository.acquireCoverageBatch(currentSession, channelIds, through).also {
                    onCoverage(through)
                }
            }
        }

    private fun show(
        fixture: GuideState,
        view: GuideView = GuideView.Overview,
        channel: String? = null,
        open: String? = null,
        detailsId: String? = null,
        taps: MutableSharedFlow<Destination> = MutableSharedFlow(),
        state: () -> GuideState = { fixture },
        onOverviewViewport: ((List<String>) -> Unit)? = null,
        extraDetails: ProgramDetails? = null,
        inShell: Boolean = false,
        over: @Composable () -> Unit = {},
    ) {
        var visit = 0L
        var stack by mutableStateOf(listOfNotNull(detailsId?.let { GuidePageKey.Details(it, visit++) }))
        val openDetails: (String?) -> Unit = {
            details += it
            stack = if (it == null) stack.dropLast(1) else stack + GuidePageKey.Details(it, visit++)
        }
        var selectedDay by mutableStateOf<Int?>(null)
        var selectedView by mutableStateOf(view)
        var selectedChannel by mutableStateOf(channel)
        val actions = GuideActions(
            onWatch = { watched += it },
            onRecord = {},
            onRecordOptions = {},
            onDetails = openDetails,
            onAiring = extraDetails?.let { openDetails },
            onDay = { selectedDay = it },
            onJump = {},
            onTag = {},
            onCancel = { cancelled += it },
            onStop = { stopped += it },
            onView = { selectedView = it },
            onOverviewViewport = onOverviewViewport,
            onChannel = {
                selectedChannels += it
                selectedChannel = it
                selectedView = GuideView.ByChannel
            },
        )
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                val current = state()
                val pages = current.details.values + listOfNotNull(extraDetails)
                DestinationFrame(initialDestination = Destination.Guide) {
                    val screen: @Composable () -> Unit = {
                        GuideScreen(
                            current.copy(
                                details = stack.mapNotNull { key ->
                                    pages.firstOrNull { it.id == key.programId }?.let { key to it }
                                }.toMap(),
                                selectedDay = selectedDay ?: current.selectedDay,
                            ), actions,
                            view = selectedView, channelId = selectedChannel, initiallyOpen = open,
                            onCloseAllDetails = { stack = emptyList() },
                        )
                    }
                    if (inShell) screen()
                    else CompositionLocalProvider(LocalDestinationReselections provides taps, content = screen)
                }
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
