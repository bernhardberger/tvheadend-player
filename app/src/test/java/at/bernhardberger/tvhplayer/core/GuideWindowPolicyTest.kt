package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class GuideWindowPolicyTest {
    @Test
    fun appSelectsSevenDaySdkCoveragePolicy() {
        assertEquals(7.days, GUIDE_EPG_COVERAGE_POLICY.futureHorizon)
    }

    @Test
    fun guideWindowFitsInsideTheSdkFutureHorizon() {
        val zone = ZoneId.of("Europe/Berlin")
        val openedAtSec = LocalDateTime.of(2026, 2, 10, 12, 0)
            .atZone(zone)
            .toEpochSecond()

        val bounds = guideWindowBounds(openedAtSec, zone)

        assertEquals(openedAtSec, bounds.earliestStartSec)
        assertEquals(
            openedAtSec + 7.days.inWholeSeconds - GUIDE_VISIBLE_WINDOW_SEC,
            bounds.latestStartSec,
        )
    }

    @Test
    fun calendarDayMovementPreservesLocalHourAcrossDst() {
        val zone = ZoneId.of("Europe/Berlin")
        val springStart = LocalDateTime.of(2026, 3, 28, 20, 0).atZone(zone).toEpochSecond()
        val springBounds = guideWindowBounds(springStart, zone)

        val springTarget = moveGuideWindowByDays(springStart, 1, springBounds, zone)

        assertEquals(20, java.time.Instant.ofEpochSecond(springTarget).atZone(zone).hour)
        assertEquals(23 * 3600L, springTarget - springStart)

        val autumnStart = LocalDateTime.of(2026, 10, 24, 20, 0).atZone(zone).toEpochSecond()
        val autumnBounds = guideWindowBounds(autumnStart, zone)

        val autumnTarget = moveGuideWindowByDays(autumnStart, 1, autumnBounds, zone)

        assertEquals(20, java.time.Instant.ofEpochSecond(autumnTarget).atZone(zone).hour)
        assertEquals(25 * 3600L, autumnTarget - autumnStart)
    }

    @Test
    fun calendarDayMovementCannotEscapeCoverageBounds() {
        val zone = ZoneId.of("UTC")
        val openedAtSec = LocalDateTime.of(2026, 2, 10, 12, 0)
            .atZone(zone)
            .toEpochSecond()
        val bounds = guideWindowBounds(openedAtSec, zone)

        assertEquals(
            bounds.earliestStartSec,
            moveGuideWindowByDays(bounds.earliestStartSec, -1, bounds, zone),
        )
        assertEquals(
            bounds.latestStartSec,
            moveGuideWindowByDays(bounds.latestStartSec, 1, bounds, zone),
        )
    }

    @Test
    fun coverageWaitEndsWhenAcquisitionStopsWithoutCurrentCoverage() {
        assertTrue(
            shouldWaitForGuideCoverage(
                connectionReady = true,
                hasCurrentSnapshot = true,
                acquisitionPending = true,
                coverageSettled = false,
            )
        )
        assertTrue(
            shouldWaitForGuideCoverage(
                connectionReady = true,
                hasCurrentSnapshot = false,
                acquisitionPending = true,
                coverageSettled = false,
            )
        )
        assertFalse(
            shouldWaitForGuideCoverage(
                connectionReady = true,
                hasCurrentSnapshot = true,
                acquisitionPending = false,
                coverageSettled = false,
            )
        )
        assertFalse(
            shouldWaitForGuideCoverage(
                connectionReady = true,
                hasCurrentSnapshot = false,
                acquisitionPending = false,
                coverageSettled = false,
            )
        )
        assertFalse(
            shouldWaitForGuideCoverage(
                connectionReady = true,
                hasCurrentSnapshot = true,
                acquisitionPending = true,
                coverageSettled = true,
            )
        )
        assertFalse(
            shouldWaitForGuideCoverage(
                connectionReady = false,
                hasCurrentSnapshot = true,
                acquisitionPending = true,
                coverageSettled = false,
            )
        )
    }

    @Test
    fun channelNavigationFindsTheFirstUnsettledDestinationPage() {
        assertEquals(
            6,
            firstUnsettledGuidePageIndex(
                currentChannelIndex = 4,
                targetChannelIndex = 11,
                channelCount = 12,
                pageSize = 6,
                coverageSettled = { it < 6 },
            ),
        )
        assertEquals(
            5,
            firstUnsettledGuidePageIndex(
                currentChannelIndex = 7,
                targetChannelIndex = 0,
                channelCount = 12,
                pageSize = 6,
                coverageSettled = { it >= 6 },
            ),
        )
    }

    @Test
    fun cachedDestinationEventDoesNotSettleItsTwelveChannelPage() {
        val channelIds = (1L..12L).map(::ChannelId)
        val through = Instant.fromEpochSeconds(10_800L)
        val cached = event(
            id = 70,
            channelId = 7,
            start = 7_200L,
            stop = 10_800L,
            title = "Partially cached",
        )
        val partialCoverages = channelIds.map { channelId ->
            EpgCoverage.create(
                channelId = channelId,
                coveredFrom = Instant.fromEpochSeconds(0L),
                coveredTo = if (channelId == ChannelId(7)) {
                    Instant.fromEpochSeconds(7_200L)
                } else {
                    through
                },
            )
        }

        assertTrue(
            indexTimelineEventsByChannel(listOf(cached), 0L, through.epochSeconds)
                .visibleEventsByChannel[ChannelId(7)]
                .orEmpty()
                .isNotEmpty()
        )
        assertFalse(
            guideChannelPageCoverageSettled(
                channelIds = channelIds.drop(6),
                coverages = partialCoverages,
                requestedThrough = through,
            )
        )
        assertTrue(
            guideChannelPageCoverageSettled(
                channelIds = channelIds.drop(6),
                coverages = channelIds.map { channelId ->
                    EpgCoverage.create(
                        channelId = channelId,
                        coveredFrom = Instant.fromEpochSeconds(0L),
                        coveredTo = through,
                    )
                },
                requestedThrough = through,
            )
        )
    }

    @Test
    fun frontierWaitsForEveryRequestedChannelAfterOriginSettles() {
        val channelIds = (1L..6L).map(::ChannelId)
        val through = Instant.fromEpochSeconds(10_800L)
        val coverages = channelIds.map { channelId ->
            EpgCoverage.create(
                channelId = channelId,
                coveredFrom = Instant.fromEpochSeconds(0L),
                coveredTo = if (channelId == channelIds.last()) {
                    Instant.fromEpochSeconds(7_200L)
                } else {
                    through
                },
            )
        }

        assertTrue(epgFrontierSettled(coverages.first(), through))
        assertFalse(
            guideChannelPageCoverageSettled(
                channelIds = channelIds,
                coverages = coverages,
                requestedThrough = through,
            )
        )
    }

    @Test
    fun cachedJumpTargetWaitsForItsWholeSemanticPage() {
        val channelIds = (1L..6L).map(::ChannelId)
        val through = Instant.fromEpochSeconds(10_800L)
        val rows = channelIds.mapIndexed { index, channelId ->
            EpgFocusColumn(
                channelId = channelId,
                events = if (index == 0) {
                    listOf(event(10, channelId.value, 7_200L, 10_800L, "Cached"))
                } else {
                    emptyList()
                },
            )
        }
        val partialCoverages = channelIds.mapIndexed { index, channelId ->
            EpgCoverage.create(
                channelId = channelId,
                coveredFrom = Instant.fromEpochSeconds(0L),
                coveredTo = if (index == 5) Instant.fromEpochSeconds(7_200L) else through,
            )
        }
        val settledCoverages = channelIds.map { channelId ->
            EpgCoverage.create(
                channelId = channelId,
                coveredFrom = Instant.fromEpochSeconds(0L),
                coveredTo = through,
            )
        }

        assertEquals(
            GuideCoverageFocusResolution.Wait,
            resolveGuideWindowFocus(
                rows = rows,
                preferredChannelId = channelIds.first(),
                targetSec = 7_200L,
                requestedChannelIds = channelIds.toSet(),
                coverages = partialCoverages,
                requestedThrough = through,
                connectionReady = true,
                hasCurrentSnapshot = true,
                acquisitionPending = true,
            ),
        )
        assertEquals(
            GuideCoverageFocusResolution.Select(EpgFocusTarget(0, EventId(10))),
            resolveGuideWindowFocus(
                rows = rows,
                preferredChannelId = channelIds.first(),
                targetSec = 7_200L,
                requestedChannelIds = channelIds.toSet(),
                coverages = settledCoverages,
                requestedThrough = through,
                connectionReady = true,
                hasCurrentSnapshot = true,
                acquisitionPending = false,
            ),
        )
        assertEquals(
            GuideCoverageFocusResolution.Select(EpgFocusTarget(5, EventId(10))),
            resolveGuideWindowFocus(
                rows = rows.reversed(),
                preferredChannelId = channelIds.first(),
                targetSec = 7_200L,
                requestedChannelIds = channelIds.toSet(),
                coverages = settledCoverages,
                requestedThrough = through,
                connectionReady = true,
                hasCurrentSnapshot = true,
                acquisitionPending = false,
            ),
        )
        assertEquals(
            GuideCoverageFocusResolution.Release,
            resolveGuideWindowFocus(
                rows = rows,
                preferredChannelId = channelIds.first(),
                targetSec = 7_200L,
                requestedChannelIds = channelIds.toSet(),
                coverages = partialCoverages,
                requestedThrough = through,
                connectionReady = true,
                hasCurrentSnapshot = true,
                acquisitionPending = false,
            ),
        )
    }

    @Test
    fun deferredOriginWaitsForExactEventAcrossSessionReorderAndTimesOut() {
        val originChannelId = ChannelId(1)
        val originEventId = EventId(11)
        val reorderedRows = listOf(
            EpgFocusColumn(ChannelId(2), emptyList()),
            EpgFocusColumn(
                originChannelId,
                listOf(event(11, 1, 0L, 3_600L, "Origin")),
            ),
        )

        assertEquals(
            GuideDeferredOriginResolution.Wait,
            resolveGuideFrontierOrigin(
                rows = reorderedRows.mapIndexed { index, row ->
                    if (index == 1) row.copy(events = emptyList()) else row
                },
                channelId = originChannelId,
                eventId = originEventId,
                connectionReady = true,
                hasCurrentSnapshot = true,
                timedOut = false,
            ),
        )
        assertEquals(
            GuideDeferredOriginResolution.Restore(EpgFocusTarget(1, originEventId)),
            resolveGuideFrontierOrigin(
                rows = reorderedRows,
                channelId = originChannelId,
                eventId = originEventId,
                connectionReady = true,
                hasCurrentSnapshot = true,
                timedOut = false,
            ),
        )
        assertEquals(
            GuideDeferredOriginResolution.Release,
            resolveGuideFrontierOrigin(
                rows = reorderedRows,
                channelId = originChannelId,
                eventId = originEventId,
                connectionReady = false,
                hasCurrentSnapshot = false,
                timedOut = true,
            ),
        )
    }

    @Test
    fun verticalNavigationSettlesCrossedPagesInOrder() {
        assertEquals(
            6,
            firstUnsettledGuidePageIndex(
                currentChannelIndex = 2,
                targetChannelIndex = 14,
                channelCount = 18,
                pageSize = 6,
                coverageSettled = { it >= 12 },
            ),
        )
        assertEquals(
            11,
            firstUnsettledGuidePageIndex(
                currentChannelIndex = 14,
                targetChannelIndex = 2,
                channelCount = 18,
                pageSize = 6,
                coverageSettled = { it < 6 },
            ),
        )
        assertEquals(
            null,
            firstUnsettledGuidePageIndex(
                currentChannelIndex = 2,
                targetChannelIndex = 5,
                channelCount = 18,
                pageSize = 6,
                coverageSettled = { false },
            ),
        )
    }

    @Test
    fun timelineIndexRetainsOnlyVisibleMatchingEventsAndBoundedMetadata() {
        val visible = event(id = 2, channelId = 1, start = 60, stop = 120, title = "News")
        val filtered = event(id = 3, channelId = 1, start = 120, stop = 180, title = "Film")
        val later = event(id = 4, channelId = 2, start = 10_000, stop = 11_000, title = "News")

        val index = indexTimelineEventsByChannel(
            events = listOf(
                event(id = 1, channelId = 1, start = 0, stop = 60, title = "News"),
                visible,
                filtered,
                later,
            ),
            windowStartSec = 60,
            windowEndSec = 180,
            matches = { it.title == "News" },
        )

        assertEquals(listOf(visible), index.visibleEventsByChannel[ChannelId(1)])
        assertFalse(index.visibleEventsByChannel.containsKey(ChannelId(2)))
        assertTrue(index.channelsWithEvents.containsAll(listOf(ChannelId(1), ChannelId(2))))
        assertTrue(index.channelsWithMatchingEvents.containsAll(listOf(ChannelId(1), ChannelId(2))))
    }

    private fun event(
        id: Long,
        channelId: Long,
        start: Long,
        stop: Long,
        title: String,
    ) = EpgEvent.create(
        id = EventId(id),
        channelId = ChannelId(channelId),
        start = Instant.fromEpochSeconds(start),
        stop = Instant.fromEpochSeconds(stop),
        title = title,
    )
}
