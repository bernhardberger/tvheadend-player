package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingMarkTrackTest {
    private val ad = RecordingMark(RecordingMarkKind.AD_BREAK, 1_000, 2_000)
    private val cut = RecordingMark(RecordingMarkKind.CUT, 5_000, 6_000)
    private val chapter = RecordingMark(RecordingMarkKind.CHAPTER, 8_000, name = "Finale")

    @Test fun noMarksOrNoLengthDrawNothing() {
        assertNull(timelineMarks(emptyList(), 10_000))
        assertNull(timelineMarks(listOf(ad), 0))
        assertNull(timelineMarks(listOf(ad), -1))
    }

    @Test fun spansAreFractionsOfTheBarAndTheSelectionIndexesThem() {
        val marks = requireNotNull(timelineMarks(listOf(ad, cut, chapter), 10_000, selected = cut, open = true))
        assertEquals(
            listOf(
                TimelineMarkSpan(RecordingMarkKind.AD_BREAK, 0.1f, 0.2f),
                TimelineMarkSpan(RecordingMarkKind.CUT, 0.5f, 0.6f),
                TimelineMarkSpan(RecordingMarkKind.CHAPTER, 0.8f, 0.8f),
            ),
            marks.spans,
        )
        assertEquals(1, marks.selected)
        assertEquals(true, marks.open)
    }

    @Test fun anEndStopSelectsItsIntervalAtTheEnd() {
        val marks = requireNotNull(timelineMarks(listOf(ad, cut), 10_000, selected = cut, open = true, selectedEnd = true))
        assertEquals(1, marks.selected)
        assertEquals(true, marks.selectedEnd)
        assertEquals(false, timelineMarks(listOf(ad), 10_000, selected = cut, selectedEnd = true)?.selectedEnd)
    }

    @Test fun aSelectionNoLongerAmongTheMarksSelectsNothing() {
        val gone = RecordingMark(RecordingMarkKind.CUT, 7_000, 7_500)
        assertNull(requireNotNull(timelineMarks(listOf(ad, cut), 10_000, selected = gone)).selected)
    }

    @Test fun aGrowingRecordingsMarkBeyondTheBarKeepsItsFraction() {
        // The layer clamps while drawing; the span keeps where the mark really lies.
        val ahead = RecordingMark(RecordingMarkKind.AD_BREAK, 9_000, 12_000)
        assertEquals(TimelineMarkSpan(RecordingMarkKind.AD_BREAK, 0.9f, 1.2f), timelineMarks(listOf(ahead), 10_000)?.spans?.single())
    }
}
