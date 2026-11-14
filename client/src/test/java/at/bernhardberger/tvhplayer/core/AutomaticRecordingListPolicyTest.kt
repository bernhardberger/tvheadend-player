package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.AutorecRule
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TimerecRule
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleId
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AutomaticRecordingListPolicyTest {
    @Test fun headlinesKindsAndTargetsUseTheEditorPolicies() {
        val rules = listOf(
            AutorecRule.create(AutorecRuleId("series"), title = "^Ridge Light$", seriesLinkUri = "ridge"),
            AutorecRule.create(AutorecRuleId("nameless-series"), name = "Named series", seriesLinkUri = "other"),
            AutorecRule.create(AutorecRuleId("title"), title = "^Little Orbit$"),
            AutorecRule.create(AutorecRuleId("keyword"), title = "orbit"),
            AutorecRule.create(AutorecRuleId("raw"), title = "news|sport"),
        )
        val timers = listOf(
            TimerecRule.create(TimerecRuleId("named"), name = "Late news", title = "Foreign format"),
            TimerecRule.create(TimerecRuleId("fallback"), name = "", title = "Web title"),
            TimerecRule.create(TimerecRuleId("nameless")),
        )
        val list = automaticRecordingList(observation(rules, timers))
        val automatic = list.items.filterIsInstance<AutomaticRecordingListItem.Automatic>()
            .associateBy { it.rule.id }
        assertEquals(listOf(AutomaticRecordingKind.SERIES, AutomaticRecordingKind.SERIES,
            AutomaticRecordingKind.TITLE, AutomaticRecordingKind.KEYWORD, AutomaticRecordingKind.KEYWORD),
            rules.map { automatic.getValue(it.id).kind })
        assertEquals(listOf("Ridge Light", "Named series", "Little Orbit", "orbit", "news|sport"),
            rules.map { automatic.getValue(it.id).headline })
        rules.forEach { assertSame(it, automatic.getValue(it.id).rule) }
        val timed = list.items.filterIsInstance<AutomaticRecordingListItem.Timer>().associateBy { it.rule.id }
        assertEquals(listOf("Late news", "Web title", ""), timers.map { timed.getValue(it.id).headline })
        timers.forEach { assertSame(it, timed.getValue(it.id).rule) }
        assertEquals(setOf(AutomaticRecordingKind.TIMER), timed.values.map { it.kind }.toSet())
    }

    @Test fun upcomingAndNextOnlyIncludeOwnEnabledNonDuplicateScheduledEntries() {
        val auto = AutorecRule.create(AutorecRuleId("own"), channelId = ChannelId(7))
        val timer = TimerecRule.create(TimerecRuleId("timer"), channelId = ChannelId(9))
        val entries = listOf(
            entry(1, auto.id, start = 300), entry(2, auto.id, start = 200),
            entry(3, auto.id, enabled = false), entry(4, auto.id, duplicate = true),
            entry(5, auto.id, state = DvrEntryState.COMPLETED),
            entry(6, AutorecRuleId("other")), entry(7, timer = timer.id, start = 400),
            entry(8, timer = timer.id, enabled = false), entry(9, timer = TimerecRuleId("other")),
            entry(10, auto.id, start = null),
        )
        val list = automaticRecordingList(observation(listOf(auto), listOf(timer), entries))
        val automatic = list.items.filterIsInstance<AutomaticRecordingListItem.Automatic>().single()
        val timed = list.items.filterIsInstance<AutomaticRecordingListItem.Timer>().single()
        assertEquals(3, automatic.upcoming)
        assertEquals(listOf(entries[1], entries[0], entries[9]), automatic.upcomingEntries)
        assertSame(entries[1], automatic.next)
        assertEquals(ChannelId(7), automatic.channelId)
        assertEquals(1, timed.upcoming)
        assertEquals(listOf(entries[6]), timed.upcomingEntries)
        assertSame(entries[6], timed.next)
        assertEquals(ChannelId(9), timed.channelId)
        assertEquals(null, automaticRecordingList(observation(listOf(auto))).items.single().next)
    }

    @Test fun orderIsStableAcrossKindsIdsAndEnabledStatesAndAcceptsAComparator() {
        val rules = listOf(
            AutorecRule.create(AutorecRuleId("z"), title = "^Alpha$", enabled = false),
            AutorecRule.create(AutorecRuleId("a"), title = "^alpha$"),
            AutorecRule.create(AutorecRuleId("series"), title = "^ALPHA$", seriesLinkUri = "series"),
            AutorecRule.create(AutorecRuleId("last"), title = "Zulu"),
        )
        val timer = TimerecRule.create(TimerecRuleId("timer"), name = "Alpha", enabled = false)
        val list = automaticRecordingList(observation(rules, listOf(timer)))
        assertEquals(listOf("series", "a", "z", "timer", "last"), list.items.map { it.id })
        assertEquals(3, list.onCount)
        assertEquals(2, list.offCount)
        assertEquals("last", automaticRecordingList(observation(rules, listOf(timer)),
            String.CASE_INSENSITIVE_ORDER.reversed()).items.first().id)
        val stale = automaticRecordingList(SessionObservation.create(dvrState = DvrRepositoryState.Stale(
            DvrSnapshot.create(autorecRules = rules, timerecRules = listOf(timer)),
        )))
        assertEquals(list.items, stale.items)
        assertEquals(true, stale.available)
        assertEquals(true, automaticRecordingList(observation()).available)
        val empty = automaticRecordingList(SessionObservation.create())
        assertEquals(emptyList<AutomaticRecordingListItem>(), empty.items)
        assertEquals(false, empty.available)
        assertEquals(0, empty.onCount)
        assertEquals(0, empty.offCount)
        val nameless = TimerecRule.create(TimerecRuleId("blank"))
        listOf(String.CASE_INSENSITIVE_ORDER, String.CASE_INSENSITIVE_ORDER.reversed()).forEach { comparator ->
            assertEquals("blank", automaticRecordingList(
                observation(rules, listOf(timer, nameless)), comparator,
            ).items.last().id)
        }
    }

    private fun observation(
        rules: List<AutorecRule> = emptyList(),
        timers: List<TimerecRule> = emptyList(),
        entries: List<DvrEntry> = emptyList(),
    ) = SessionObservation.create(dvrState = DvrRepositoryState.Current(
        DvrSnapshot.create(entries = entries, autorecRules = rules, timerecRules = timers),
    ))

    private fun entry(
        id: Long, auto: AutorecRuleId? = null, timer: TimerecRuleId? = null,
        start: Long? = 100, enabled: Boolean? = null, duplicate: Boolean? = null,
        state: DvrEntryState = DvrEntryState.SCHEDULED,
    ) = DvrEntry.create(DvrEntryId(id), autorecRuleId = auto, timerecRuleId = timer,
        start = start?.let { Instant.fromEpochSeconds(it) }, enabled = enabled, duplicate = duplicate, state = state)
}
