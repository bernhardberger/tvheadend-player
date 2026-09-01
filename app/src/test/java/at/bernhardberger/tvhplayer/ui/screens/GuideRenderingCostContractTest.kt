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

    @Test
    fun guideUsesOneCurrentPerChannelEventIndex() {
        val guide = screenSource.section(
            "fun EpgGridScreen(",
            "private fun SessionObservation.dvrEntries()",
        )

        assertEquals(1, guide.count("indexTimelineEventsByChannel(snapshotEvents)"))
        assertTrue(
            guide.contains(
                """val eventsByChannel = remember(snapshotEvents) {
        indexTimelineEventsByChannel(snapshotEvents)
    }"""
            )
        )
        assertFalse(guide.contains("snapshotEvents.filter { it.channelId == channel.id }"))
    }

    @Test
    fun guideFocusUsesCompositionTimingAndBoundsRequesterRetention() {
        val guide = screenSource.section(
            "fun EpgGridScreen(",
            "private fun SessionObservation.dvrEntries()",
        )
        val selectedTargetFocus = guide.section(
            "LaunchedEffect(\n        selectedTarget,",
            "LaunchedEffect(epgState, selectedChannel, category, frontierRequest)",
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
