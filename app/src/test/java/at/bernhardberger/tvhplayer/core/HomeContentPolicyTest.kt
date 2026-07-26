package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrFile
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentPolicyTest {
    private val nowSec = 1_000L

    @Test
    fun initialFocusUsesHeroWhenPresentOtherwiseStatusAction() {
        val empty = HomeDashboardModel(hero = emptyList(), rows = emptyList())
        assertEquals(HomeFocusTarget.STATUS_ACTION, homeInitialFocusTarget(empty))

        val withHero = empty.copy(
            hero = listOf(
                heroSlide(kind = HomeSlideKind.ON_NOW, channelId = 1, title = "News"),
            ),
        )
        assertEquals(HomeFocusTarget.HERO, homeInitialFocusTarget(withHero))
    }

    @Test
    fun heroEmptyWithRowsStillReportsStatusAction() {
        // Records the state that strands focus if the empty-state block is also hidden.
        val model = HomeDashboardModel(
            hero = emptyList(),
            rows = listOf(
                HomeRow(
                    kind = HomeRowKind.SCHEDULED,
                    items = listOf(
                        HomeCardItem(
                            key = "rec-9",
                            channelId = 1,
                            channelNumber = 1,
                            channelName = "ORF1",
                            piconPath = null,
                            accentSeed = 0,
                            title = "Later",
                            remainingMinutes = null,
                            progress = null,
                            startSec = 2_000,
                            stopSec = 2_100,
                            recordingId = 9,
                            recordingNow = false,
                            playable = false,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(HomeFocusTarget.STATUS_ACTION, homeInitialFocusTarget(model))
        assertTrue(model.rows.isNotEmpty())
    }

    @Test
    fun heroPutsActiveSessionFirst() {
        val channels = mapOf(
            1 to channel(1, "ORF1"),
            2 to channel(2, "ORF2"),
        )
        val model = buildHomeDashboard(
            channelsById = channels,
            activeServiceId = 2,
            activeRecordingId = null,
            activeProgrammeTitle = "Live match",
            lastWatchedChannelId = 1,
            recentChannelIds = listOf(1, 2),
            onNowEvents = listOf(
                channels.getValue(1) to event(10, 1, 900, 1_100, "News"),
                channels.getValue(2) to event(11, 2, 900, 1_100, "Live match"),
            ),
            nextEvents = emptyMap(),
            recordings = emptyList(),
            nowSec = nowSec,
        )
        assertEquals(HomeSlideKind.LIVE, model.hero.first().kind)
        assertEquals(2, model.hero.first().channelId)
        assertEquals("Live match", model.hero.first().title)
    }

    @Test
    fun heroFallsBackToLastWatchedWhenNothingPlaying() {
        val channels = mapOf(1 to channel(1, "ORF1"))
        val model = buildHomeDashboard(
            channelsById = channels,
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = 1,
            recentChannelIds = listOf(1),
            onNowEvents = listOf(
                channels.getValue(1) to event(10, 1, 900, 1_100, "Evening news"),
            ),
            nextEvents = mapOf(1 to event(11, 1, 1_100, 1_200, "Weather")),
            recordings = emptyList(),
            nowSec = nowSec,
        )
        assertEquals(1, model.hero.size)
        assertEquals(HomeSlideKind.CONTINUE, model.hero.first().kind)
        assertEquals(1, model.hero.first().channelId)
        assertEquals("Evening news", model.hero.first().title)
        assertEquals("Weather", model.hero.first().nextTitle)
        assertTrue(model.hero.first().progress != null && model.hero.first().progress!! > 0f)
    }

    @Test
    fun heroSlideWithoutEpgUsesChannelNameAndNoProgress() {
        val channels = mapOf(1 to channel(1, "ORF1 HD"))
        val model = buildHomeDashboard(
            channelsById = channels,
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = 1,
            recentChannelIds = listOf(1),
            onNowEvents = emptyList(),
            nextEvents = emptyMap(),
            recordings = emptyList(),
            nowSec = nowSec,
        )
        val slide = model.hero.single()
        assertEquals(HomeSlideKind.CONTINUE, slide.kind)
        assertEquals("ORF1 HD", slide.title)
        assertNull(slide.progress)
        assertNull(slide.startSec)
        assertNull(slide.stopSec)
        assertNull(slide.nextTitle)
        assertTrue(slide.playable)
    }

    @Test
    fun heroSkipsBlankActiveSessionWhenChannelsNotSynced() {
        val model = buildHomeDashboard(
            channelsById = emptyMap(),
            activeServiceId = 42,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = null,
            recentChannelIds = emptyList(),
            onNowEvents = emptyList(),
            nextEvents = emptyMap(),
            recordings = emptyList(),
            nowSec = nowSec,
        )
        assertTrue(model.hero.isEmpty())
        assertEquals(HomeFocusTarget.STATUS_ACTION, homeInitialFocusTarget(model))
    }

    @Test
    fun heroIsCappedAtFourSlides() {
        val channels = (1..6).associateWith { channel(it, "Ch$it") }
        val recent = (1..6).toList()
        val onNow = recent.map { id ->
            channels.getValue(id) to event(id, id, 900, 1_100, "Show $id")
        }
        val model = buildHomeDashboard(
            channelsById = channels,
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = 1,
            recentChannelIds = recent,
            onNowEvents = onNow,
            nextEvents = emptyMap(),
            recordings = listOf(
                dvr(100, 1, DvrState.RECORDING, "Rec A", start = 900, stop = 1_200, withFile = true),
                dvr(101, 2, DvrState.RECORDING, "Rec B", start = 910, stop = 1_200, withFile = true),
            ),
            nowSec = nowSec,
        )
        assertEquals(HOME_HERO_LIMIT, model.hero.size)
        assertTrue(model.hero.size <= 4)
    }

    @Test
    fun rowsExcludeChannelsAlreadyShownAbove() {
        val channels = mapOf(
            1 to channel(1, "ORF1"),
            2 to channel(2, "ORF2"),
            3 to channel(3, "ORF3"),
        )
        val model = buildHomeDashboard(
            channelsById = channels,
            activeServiceId = 1,
            activeRecordingId = null,
            activeProgrammeTitle = "News",
            lastWatchedChannelId = 1,
            recentChannelIds = listOf(1, 2),
            onNowEvents = listOf(
                channels.getValue(1) to event(10, 1, 900, 1_100, "News"),
                channels.getValue(2) to event(11, 2, 900, 1_100, "Sport"),
                channels.getValue(3) to event(12, 3, 900, 1_100, "Film"),
            ),
            nextEvents = emptyMap(),
            recordings = emptyList(),
            nowSec = nowSec,
        )
        val heroIds = model.hero.map { it.channelId }.toSet()
        assertTrue(1 in heroIds)
        // Recent channel 2 is promoted into the hero as ON_NOW, so it is not repeated.
        assertTrue(2 in heroIds)

        // Empty rows are omitted entirely, not rendered with zero items.
        assertNull(model.rows.firstOrNull { it.kind == HomeRowKind.RECENT })

        val onNow = model.rows.firstOrNull { it.kind == HomeRowKind.ON_NOW }
        // Channels 1–2 are hero; 2 is also recent; only 3 remains for on-now.
        assertEquals(listOf(3), onNow?.items?.map { it.channelId }.orEmpty())

        val allChannelIds = model.hero.map { it.channelId } +
            model.rows.flatMap { row -> row.items.map { it.channelId } }
        assertEquals(allChannelIds.toSet().size, allChannelIds.size)
    }

    @Test
    fun recordingRowsAreDroppedWhenRecordingsAreNotAllowed() {
        val channels = mapOf(1 to channel(1, "ORF1"))
        val recordings = listOf(
            dvr(9, 1, DvrState.RECORDING, "Live rec", start = 900, stop = 1_200, withFile = true),
            dvr(10, 1, DvrState.COMPLETED, "Done", start = 700, stop = 800, withFile = true),
            dvr(11, 1, DvrState.SCHEDULED, "Later", start = 2_000, stop = 2_100),
        )
        val model = buildHomeDashboard(
            channelsById = channels,
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = 1,
            recentChannelIds = listOf(1),
            onNowEvents = emptyList(),
            nextEvents = emptyMap(),
            recordings = recordings,
            nowSec = nowSec,
            allowRecordings = false,
        )
        assertTrue(model.hero.none { it.kind == HomeSlideKind.RECORDING })
        assertTrue(model.rows.none { it.kind == HomeRowKind.RECORDINGS })
        assertTrue(model.rows.none { it.kind == HomeRowKind.SCHEDULED })
        assertEquals(HomeSlideKind.CONTINUE, model.hero.single().kind)
    }

    @Test
    fun allowRecordingsFalseUsesNonRecordingFallbackInsteadOfEmptyHero() {
        // Recording-only content with allowRecordings=false must not leave rows without a hero
        // (the post-filter failure mode). Rebuilding with the flag yields an empty dashboard.
        val recordings = listOf(
            dvr(9, 1, DvrState.RECORDING, "Live rec", start = 900, stop = 1_200, withFile = true),
        )
        val allowed = buildHomeDashboard(
            channelsById = emptyMap(),
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = null,
            recentChannelIds = emptyList(),
            onNowEvents = emptyList(),
            nextEvents = emptyMap(),
            recordings = recordings,
            nowSec = nowSec,
            allowRecordings = true,
        )
        assertEquals(listOf(9), allowed.hero.mapNotNull { it.recordingId })
        assertTrue(allowed.hero.isNotEmpty())

        val denied = buildHomeDashboard(
            channelsById = emptyMap(),
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = null,
            recentChannelIds = emptyList(),
            onNowEvents = emptyList(),
            nextEvents = emptyMap(),
            recordings = recordings,
            nowSec = nowSec,
            allowRecordings = false,
        )
        assertTrue(denied.hero.isEmpty())
        assertTrue(denied.rows.isEmpty())
        assertEquals(HomeFocusTarget.STATUS_ACTION, homeInitialFocusTarget(denied))
    }

    @Test
    fun channelAccentSeedIsStableForTheSameName() {
        assertEquals(channelAccentSeed("ORF1 HD"), channelAccentSeed("ORF1 HD"))
        assertEquals(channelAccentSeed("ServusTV"), channelAccentSeed("ServusTV"))
        val a = channelAccentSeed("ORF1 HD")
        val b = channelAccentSeed("ORF2 HD")
        assertTrue(a in 0..359)
        assertTrue(b in 0..359)
        assertTrue(a != b)
        // Exact fold values — not String.hashCode().
        assertEquals(expectedAccent("ORF1 HD"), a)
        assertEquals(expectedAccent("ORF2 HD"), b)
        assertEquals(expectedAccent("ServusTV"), channelAccentSeed("ServusTV"))
    }

    @Test
    fun recentChannelsDedupAndCap() {
        val pushed = pushRecentChannelId(listOf(2, 3, 1), channelId = 1, limit = 3)
        assertEquals(listOf(1, 2, 3), pushed)
        val capped = (1..12).fold(emptyList<Int>()) { acc, id ->
            pushRecentChannelId(acc, id, limit = 8)
        }
        assertEquals(8, capped.size)
        assertEquals(12, capped.first())
    }

    @Test
    fun upcomingScheduledAreNotMarkedPlayable() {
        val model = buildHomeDashboard(
            channelsById = emptyMap(),
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = null,
            recentChannelIds = emptyList(),
            onNowEvents = emptyList(),
            nextEvents = emptyMap(),
            recordings = listOf(
                dvr(9, 1, DvrState.SCHEDULED, "Later", start = 2_000, stop = 2_100),
            ),
            nowSec = nowSec,
        )
        val scheduled = model.rows.single { it.kind == HomeRowKind.SCHEDULED }.items
        assertEquals(1, scheduled.size)
        assertFalse(scheduled.first().playable)
        assertEquals(2_000L, scheduled.first().startSec)
        assertEquals(2_100L, scheduled.first().stopSec)
        assertTrue(model.hero.isEmpty())
    }

    @Test
    fun recordingCardsCarryTimeBounds() {
        val model = buildHomeDashboard(
            channelsById = mapOf(1 to channel(1, "ORF1")),
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = null,
            recentChannelIds = emptyList(),
            onNowEvents = emptyList(),
            nextEvents = emptyMap(),
            recordings = listOf(
                dvr(3, 1, DvrState.RECORDING, "Live rec", start = 900, stop = 1_200, withFile = true),
                dvr(1, 1, DvrState.COMPLETED, "Done", start = 700, stop = 800, withFile = true),
            ),
            nowSec = nowSec,
        )
        // Recording-now is in the hero; completed remains in the row with bounds.
        val completed = model.rows.single { it.kind == HomeRowKind.RECORDINGS }.items.single()
        assertEquals(1, completed.recordingId)
        assertEquals(700L, completed.startSec)
        assertEquals(800L, completed.stopSec)
        assertNull(completed.remainingMinutes)

        val liveHero = model.hero.single { it.recordingId == 3 }
        assertTrue(liveHero.progress != null)
        // Recording-now also appears only in hero here; if it were a row card it would
        // carry remaining minutes — assert the completed path at minimum has bounds.
        assertTrue(completed.startSec != null && completed.stopSec != null)
    }

    @Test
    fun recordingsRowContainsOnlyPlayableCompletedAndRecordingNow() {
        fun entry(id: Int, state: DvrState, withFile: Boolean) = dvr(
            id = id,
            channelId = 1,
            state = state,
            title = "Recording $id",
            start = 900L + id,
            stop = 1_100L + id,
            withFile = withFile,
        )
        val model = buildHomeDashboard(
            channelsById = mapOf(1 to channel(1, "ORF1")),
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = null,
            recentChannelIds = emptyList(),
            onNowEvents = emptyList(),
            nextEvents = emptyMap(),
            recordings = listOf(
                entry(1, DvrState.COMPLETED, withFile = true),
                entry(2, DvrState.COMPLETED, withFile = false),
                entry(3, DvrState.RECORDING, withFile = true),
                entry(4, DvrState.FAILED, withFile = true),
            ),
            nowSec = nowSec,
        )
        // Hero takes playable recording-now first when nothing else is available.
        assertEquals(listOf(3), model.hero.mapNotNull { it.recordingId })
        assertTrue(model.hero.single().playable)
        val recRow = model.rows.firstOrNull { it.kind == HomeRowKind.RECORDINGS }
        // Recording 3 is in the hero; completed-with-file remains in the row.
        assertEquals(listOf(1), recRow?.items?.mapNotNull { it.recordingId }.orEmpty())
    }

    @Test
    fun heroOmitsUnplayableRecordingNowWithoutFile() {
        val model = buildHomeDashboard(
            channelsById = mapOf(1 to channel(1, "ORF1")),
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = null,
            recentChannelIds = emptyList(),
            onNowEvents = emptyList(),
            nextEvents = emptyMap(),
            recordings = listOf(
                dvr(3, 1, DvrState.RECORDING, "No file yet", start = 900, stop = 1_200, withFile = false),
            ),
            nowSec = nowSec,
        )
        assertTrue(model.hero.none { it.recordingId == 3 })
    }

    @Test
    fun onNowUsesCurrentEventsOnly() {
        val channel = channel(4, "Servus")
        val current = event(1, 4, 900, 1_100, "Live show")
        val past = event(2, 4, 700, 800, "Past")
        val model = buildHomeDashboard(
            channelsById = mapOf(4 to channel),
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            lastWatchedChannelId = null,
            recentChannelIds = emptyList(),
            onNowEvents = listOf(channel to current, channel to past),
            nextEvents = emptyMap(),
            recordings = emptyList(),
            nowSec = nowSec,
        )
        // Fallback promotes current on-now into the hero when there is no history.
        assertEquals(listOf("Live show"), model.hero.map { it.title })
        assertTrue(model.rows.none { it.kind == HomeRowKind.ON_NOW })
    }

    @Test
    fun remainingMinutesIsCeilOfSecondsLeft() {
        val live = channel(1, "ORF1")
        val other = channel(2, "ORF2")
        // Active live on channel 1; channel 2 only appears in the on-now row.
        val model = buildHomeDashboard(
            channelsById = mapOf(1 to live, 2 to other),
            activeServiceId = 1,
            activeRecordingId = null,
            activeProgrammeTitle = "Live",
            lastWatchedChannelId = 1,
            recentChannelIds = emptyList(),
            onNowEvents = listOf(
                live to event(1, 1, 900, 1_100, "Live"),
                other to event(2, 2, 900, 1_061, "Other"),
            ),
            nextEvents = emptyMap(),
            recordings = emptyList(),
            nowSec = nowSec,
        )
        val onNowItem = model.rows.single { it.kind == HomeRowKind.ON_NOW }.items.single()
        assertEquals(2, onNowItem.channelId)
        // 61 seconds left -> 2 minutes (ceil).
        assertEquals(2, onNowItem.remainingMinutes)
    }

    private fun expectedAccent(name: String): Int {
        var hash = 0
        for (ch in name) {
            hash = (hash * 31 + ch.code) and 0x7fff_ffff
        }
        return hash % 360
    }

    private fun channel(id: Int, name: String) = ChannelUi(
        id = id,
        name = name,
        number = id,
        icon = null,
    )

    private fun event(
        eventId: Int,
        channelId: Int,
        start: Long,
        stop: Long,
        title: String,
    ) = EpgEventEntry(
        eventId = eventId,
        channelId = channelId,
        start = start,
        stop = stop,
        title = title,
    )

    private fun dvr(
        id: Int,
        channelId: Int,
        state: DvrState,
        title: String,
        start: Long,
        stop: Long,
        withFile: Boolean = false,
        channelName: String = "ORF1",
    ) = DvrEntry(
        id = id,
        eventId = id,
        channelId = channelId,
        start = start,
        stop = stop,
        title = title,
        state = state,
        channelName = channelName,
        files = if (withFile) listOf(DvrFile(path = "/recording-$id.ts")) else emptyList(),
    )

    private fun heroSlide(
        kind: HomeSlideKind,
        channelId: Int,
        title: String,
    ) = HomeHeroSlide(
        kind = kind,
        channelId = channelId,
        channelNumber = channelId,
        channelName = "Ch$channelId",
        piconPath = null,
        accentSeed = 0,
        title = title,
        subtitle = null,
        startSec = null,
        stopSec = null,
        progress = null,
        nextTitle = null,
        nextStartSec = null,
        recordingId = null,
        playable = true,
    )
}
