package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrConfig
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DvrLibraryPolicyTest {
    @Test
    fun entriesAreGroupedAndOrderedForTelevisionBrowsing() {
        val entries = listOf(
            entry(1, DvrState.COMPLETED, 400),
            entry(2, DvrState.SCHEDULED, 300),
            entry(3, DvrState.RECORDING, 200),
            entry(4, DvrState.FAILED, 100),
            entry(5, DvrState.CANCELLED, 500),
        )

        val groups = groupRecordings(entries)

        assertEquals(listOf(3, 2), groups.getValue(DvrSection.UPCOMING_ACTIVE).map { it.id })
        assertEquals(listOf(1), groups.getValue(DvrSection.COMPLETED).map { it.id })
        assertEquals(listOf(5, 4), groups.getValue(DvrSection.FAILED_CANCELLED).map { it.id })
    }

    @Test
    fun oneUsableConfigIsAutomaticAndSeveralRequireSelection() {
        val first = DvrConfig("one", "Default", enabled = true)
        val disabled = DvrConfig("off", "Disabled", enabled = false)
        assertEquals(
            DvrConfigChoice.Automatic("one"),
            chooseDvrConfig(listOf(first, disabled)),
        )

        val choice = chooseDvrConfig(
            listOf(first, DvrConfig("two", "Films", enabled = true))
        )
        assertTrue(choice is DvrConfigChoice.RequiresSelection)
    }

    @Test
    fun absentConfigMetadataDefersToServerDefault() {
        assertEquals(DvrConfigChoice.Automatic(null), chooseDvrConfig(emptyList()))
    }

    private fun entry(id: Int, state: DvrState, start: Long) = DvrEntry(
        id = id,
        eventId = null,
        channelId = 1,
        start = start,
        stop = start + 60,
        title = "Recording $id",
        state = state,
    )
}
