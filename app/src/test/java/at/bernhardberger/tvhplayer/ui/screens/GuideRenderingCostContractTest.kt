package at.bernhardberger.tvhplayer.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideRenderingCostContractTest {
    private val repositoryRoot = generateSequence(
        File(requireNotNull(System.getProperty("user.dir"))),
    ) { it.parentFile }.first { File(it, ".git").exists() }
    private val screenSource = File(
        repositoryRoot,
        "app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/EpgGridScreen.kt",
    ).readText()
    private val contentSource = File(
        repositoryRoot,
        "app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/guide/EpgGridContent.kt",
    ).readText()
    private val modalSource = File(
        repositoryRoot,
        "app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/guide/EpgGridModals.kt",
    ).readText()
    private val appModuleSource = File(
        repositoryRoot,
        "app/src/main/java/at/bernhardberger/tvhplayer/di/AppModule.kt",
    ).readText()

    @Test
    fun appSessionUsesTheExplicitGuideCoveragePolicy() {
        assertEquals(
            1,
            appModuleSource.count("createTvheadendSession(GUIDE_EPG_COVERAGE_POLICY)"),
        )
        assertFalse(appModuleSource.contains("val session = createTvheadendSession()"))
    }

    @Test
    fun guideUsesOneWindowBoundedPerChannelEventIndex() {
        val guide = screenSource.section(
            "fun EpgGridScreen(",
            "private fun SessionObservation.dvrEntries()",
        )

        assertEquals(1, guide.count("indexTimelineEventsByChannel("))
        assertTrue(
            guide.contains(
                """val timelineEventIndex = remember(snapshotEvents, category, windowStartSec) {
        indexTimelineEventsByChannel(
            events = snapshotEvents,
            windowStartSec = windowStartSec,
            windowEndSec = windowEndSec,
            matches = { it.matchesProgrammeCategory(category) },
        )
    }"""
            )
        )
        assertFalse(guide.contains("indexTimelineEventsByChannel(snapshotEvents)"))
        assertFalse(guide.contains("snapshotEvents.filter { it.channelId == channel.id }"))
        assertTrue(
            guide.contains(
                """val through = KotlinInstant.fromEpochSeconds(
            boundedAnchorSec + GUIDE_VISIBLE_WINDOW_SEC
        )"""
            )
        )
        assertTrue(guide.contains("channelId = channels[current.channelIndex].id"))
        assertTrue(guide.contains("channelFocusRequest = ChannelFocusRequest("))
        assertTrue(guide.contains("val coverageRequest = requestVisibleWindow("))
        assertTrue(
            guide.contains(
                """val unsettledIndex = unsettledPageIndex(current.channelIndex, targetIndex)
        if (unsettledIndex != null) {
            requestChannelFocus(current, unsettledIndex, direction)
            return
        }"""
            )
        )
        assertTrue(
            guide.contains(
                """val unsettledIndex = unsettledPageIndex(
                    current.channelIndex,
                    move.target.channelIndex,
                )
                if (unsettledIndex != null) {
                    requestChannelFocus(current, unsettledIndex, step)
                    return true
                }"""
            )
        )
        assertEquals(2, guide.count("if (coverageSettled && target != null)"))
        assertTrue(
            guide.contains(
                "searchChannelIds = request.coverageRequest.generations.keys"
            )
        )
        assertTrue(guide.contains("searchChannelIds = targetPageIds"))
        assertTrue(
            guide.contains(
                "channelIds = request.coverageRequest.generations.keys.toList()"
            )
        )
        assertTrue(guide.contains("windowFocusRequest = WindowFocusRequest("))
        assertTrue(guide.contains("resolveGuideWindowFocus("))
        assertTrue(
            guide.contains(
                """fun clearAllCoverage(restoreFrontier: Boolean = false) {
        if (restoreFrontier) frontierRequest?.let(::deferFrontierOrigin)
        else pendingFrontierOrigin = null
        coverageRequests.cancelAll()"""
            )
        )
        assertTrue(
            guide.contains(
                """if (coverageSession !== currentSession) {
            coverageSession = currentSession
            clearAllCoverage(restoreFrontier = true)
        }"""
            )
        )
        assertTrue(
            guide.contains(
                "pendingFrontierOrigin != null ||"
            )
        )
        assertTrue(guide.contains("resolveGuideFrontierOrigin("))
        assertTrue(guide.contains("is GuideDeferredOriginResolution.Restore"))
        assertTrue(guide.contains("requestVisibleWindow(origin.windowStartSec, channelIndex)"))
        assertTrue(
            guide.contains(
                """windowStartSec = request.originWindowStartSec
        selection.setSelected(request.channelId)
        pendingFrontierOrigin = request.toOrigin()"""
            )
        )
        assertTrue(
            guide.contains(
                "windowFocusRequest != null"
            )
        )
        assertEquals(4, guide.count("delay(GUIDE_COVERAGE_NAVIGATION_TIMEOUT_MS)"))
    }

    @Test
    fun guideFocusUsesCompositionTimingAndBoundsRequesterRetention() {
        val guide = screenSource.section(
            "fun EpgGridScreen(",
            "private fun SessionObservation.dvrEntries()",
        )
        val selectedTargetFocus = guide.section(
            "LaunchedEffect(\n        selectedTarget,",
            "LaunchedEffect(\n        epgState,\n        focusRows,\n        channels,",
        ).substringAfter("if (initialFocusEnabled && !scopeRowFocused) {")

        assertFalse(guide.contains("delay(80)"))
        assertTrue(
            guide.contains(
                """LaunchedEffect(currentEventIds) {
        eventFocusRequesters.keys.retainAll(currentEventIds)
    }"""
            )
        )
        assertTrue(selectedTargetFocus.indexOf("withFrameNanos { }") >= 0)
        assertTrue(
            selectedTargetFocus.indexOf("withFrameNanos { }") <
                selectedTargetFocus.indexOf("requester.requestFocus()")
        )
    }

    @Test
    fun guideClockUpdatesAreReadThroughAConsumerProvider() {
        val guide = screenSource.section(
            "fun EpgGridScreen(",
            "private fun SessionObservation.dvrEntries()",
        )
        val ruler = contentSource.section(
            "internal fun TimelineTimeRuler(",
            "internal fun TimelineChannelRow(",
        )
        val row = contentSource.section(
            "internal fun TimelineChannelRow(",
            "internal fun TimelineChannelHeader(",
        )
        val details = modalSource.section(
            "internal fun ProgrammeDetailsPanel(",
            "internal fun DvrConfigDialog(",
        )
        val clock = screenSource.substringAfter("private fun rememberCurrentEpochSeconds()")

        assertEquals(1, guide.count("val nowSecProvider = rememberCurrentEpochSeconds()"))
        assertFalse(guide.contains("var nowSec by remember"))
        assertTrue(ruler.contains("val nowSec = nowSecProvider()"))
        assertTrue(row.contains("val nowSec = nowSecProvider()"))
        assertTrue(details.contains("val nowSec = nowSecProvider()"))
        assertEquals(1, clock.count("mutableLongStateOf("))
        assertTrue(clock.contains("return remember(nowSec) { { nowSec.longValue } }"))
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing section start: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex >= 0) { "Missing section end: $end" }
        return substring(startIndex, endIndex)
    }

    private fun String.count(value: String): Int = windowed(value.length).count { it == value }
}
