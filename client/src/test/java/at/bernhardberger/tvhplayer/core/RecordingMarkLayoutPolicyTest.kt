package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction
import at.bernhardberger.tvhplayer.core.RecordingMarkKind.AD_BREAK
import at.bernhardberger.tvhplayer.core.RecordingMarkKind.CHAPTER
import at.bernhardberger.tvhplayer.core.RecordingMarkKind.CUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RecordingMarkLayoutPolicyTest {
    @Test
    fun stopsAreTheStartEachMarkStartAndEachIntervalEnd() {
        val ad = RecordingMark(AD_BREAK, 20, 40)
        val cut = RecordingMark(CUT, 50, 55)
        val chapter = RecordingMark(CHAPTER, 60, name = "Summit")
        assertEquals(
            listOf(
                RecordingMarkStop(0), RecordingMarkStop(20, ad), RecordingMarkStop(40, ad, atEnd = true),
                RecordingMarkStop(50, cut), RecordingMarkStop(55, cut, atEnd = true), RecordingMarkStop(60, chapter),
            ),
            recordingMarkStops(listOf(ad, cut, chapter), 100),
        )
    }

    @Test
    fun anIntervalEndingBeyondAGrowingRecordingHasNoEndStopYet() {
        for (kind in listOf(AD_BREAK, CUT)) {
            val mark = RecordingMark(kind, 20, 140)
            assertEquals(listOf(RecordingMarkStop(0), RecordingMarkStop(20, mark)), recordingMarkStops(listOf(mark), 100))
            assertEquals(listOf(RecordingMarkStop(0), RecordingMarkStop(20, mark)), recordingMarkStops(listOf(mark), 140))
            assertEquals(RecordingMarkStop(140, mark, atEnd = true), recordingMarkStops(listOf(mark), 141).last())
        }
        for ((kind, start) in listOf(AD_BREAK to 0L, CUT to 40L)) {
            val mark = RecordingMark(kind, start, 300)
            val expected = if (start == 0L) listOf(RecordingMarkStop(0, mark))
                else listOf(RecordingMarkStop(0), RecordingMarkStop(start, mark))
            assertEquals(expected, recordingMarkStops(listOf(mark), 100))
        }
    }

    @Test
    fun aMarkStartingWhereAnIntervalEndsTakesThatStop() {
        val ad = RecordingMark(AD_BREAK, 20, 40)
        val chapter = RecordingMark(CHAPTER, 40, name = "Summit")
        val next = RecordingMark(AD_BREAK, 40, 60)
        assertEquals(
            listOf(RecordingMarkStop(0), RecordingMarkStop(20, ad), RecordingMarkStop(40, chapter)),
            recordingMarkStops(listOf(ad, chapter), 100),
        )
        assertEquals(
            listOf(RecordingMarkStop(0), RecordingMarkStop(20, ad), RecordingMarkStop(40, next), RecordingMarkStop(60, next, atEnd = true)),
            recordingMarkStops(listOf(ad, next), 100),
        )
        val cut = RecordingMark(CUT, 10, 20)
        assertEquals(
            listOf(RecordingMarkStop(0), RecordingMarkStop(10, cut), RecordingMarkStop(20, ad), RecordingMarkStop(40, ad, atEnd = true)),
            recordingMarkStops(listOf(cut, ad), 100),
        )
    }

    @Test
    fun intervalsEndingTogetherLeaveOneEndStopLedByPriority() {
        val cut = RecordingMark(CUT, 10, 40)
        val ad = RecordingMark(AD_BREAK, 20, 40)
        assertEquals(RecordingMarkStop(40, ad, atEnd = true), recordingMarkStops(listOf(cut, ad), 100).last())
        assertEquals(listOf(0L, 10L, 20L, 40L), recordingMarkStops(listOf(cut, ad), 100).map { it.positionMs })
    }

    @Test
    fun overlappingIntervalsStopOnceAtTheMergedEnd() {
        for (action in listOf(DvrCutpointAction.COMMERCIAL_BREAK, DvrCutpointAction.CUT)) {
            val merged = recordingMarks(listOf(DvrCutpoint(20.seconds, 40.seconds, action), DvrCutpoint(30.seconds, 70.seconds, action)))
            val mark = merged.single()
            assertEquals(
                listOf(RecordingMarkStop(0), RecordingMarkStop(20_000, mark), RecordingMarkStop(70_000, mark, atEnd = true)),
                recordingMarkStops(merged, 100_000),
            )
        }
    }

    @Test
    fun marksStartingTogetherMakeOneStopLedByPriorityThenName() {
        val chapter = RecordingMark(CHAPTER, 20, name = "Summit")
        val cut = RecordingMark(CUT, 20, 30)
        val ad = RecordingMark(AD_BREAK, 20, 25)
        assertEquals(RecordingMarkStop(20, ad), recordingMarkStops(listOf(chapter, cut, ad), 100)[1])
        val unnamed = RecordingMark(CHAPTER, 50)
        val named = RecordingMark(CHAPTER, 50, name = "Valley")
        assertEquals(RecordingMarkStop(50, named), recordingMarkStops(listOf(unnamed, named), 100)[1])
    }

    @Test
    fun aMarkAtZeroIsTheStartStop() {
        for (mark in listOf(RecordingMark(AD_BREAK, 0, 30), RecordingMark(CHAPTER, 0, name = "Opening"))) {
            val expected = if (mark.kind == CHAPTER) listOf(RecordingMarkStop(0, mark))
                else listOf(RecordingMarkStop(0, mark), RecordingMarkStop(30, mark, atEnd = true))
            assertEquals(expected, recordingMarkStops(listOf(mark), 100))
        }
    }

    @Test
    fun marksStartingAtOrBeyondTheDurationMakeNoRow() {
        val marks = listOf(RecordingMark(CHAPTER, 100, name = "Later"), RecordingMark(AD_BREAK, 150, 200))
        assertTrue(recordingMarkStops(marks, 100).isEmpty())
    }

    @Test
    fun hintsKeepTheirGapAndPriorityWinsCollisions() {
        val hints = listOf(
            RecordingMarkHint(100f, 10f, CHAPTER),
            RecordingMarkHint(115f, 20f, AD_BREAK),
            RecordingMarkHint(140f, 10f, CUT),
            RecordingMarkHint(300f, 10f, CHAPTER),
        )
        // The ad badge claims its room first; the chapter and the cut are within 14 px of it.
        assertEquals(setOf(1, 3), visibleRecordingMarkHints(hints, gapPx = 14f, trackWidthPx = 1000f))
        // With the smaller open gap the cut, exactly 10 px clear of the badge, fits; the chapter does not.
        assertEquals(setOf(1, 2, 3), visibleRecordingMarkHints(hints, gapPx = 10f, trackWidthPx = 1000f))
    }

    @Test
    fun denseHintsThinLeftToRightWithinAKind() {
        val hints = (0 until 10).map { RecordingMarkHint(20f + it * 10f, 4f, CHAPTER) }
        // 4 px wide and 14 px apart edge to edge: every second one fits.
        assertEquals(setOf(0, 2, 4, 6, 8), visibleRecordingMarkHints(hints, gapPx = 14f, trackWidthPx = 1000f))
    }

    @Test
    fun hintsUnderThePillHide() {
        val hints = listOf(
            RecordingMarkHint(100f, 10f, AD_BREAK),
            RecordingMarkHint(150f, 10f, CHAPTER),
            RecordingMarkHint(220f, 10f, CUT),
        )
        assertEquals(setOf(2), visibleRecordingMarkHints(hints, gapPx = 10f, trackWidthPx = 1000f, blockedPx = 60f..200f))
        assertEquals(setOf(0, 1, 2), visibleRecordingMarkHints(hints, gapPx = 10f, trackWidthPx = 1000f))
    }

    @Test
    fun aCutOutranksAChapterWithoutAnAdNearby() {
        // The chapter stands first from the left; the cut still wins the collision.
        val hints = listOf(RecordingMarkHint(100f, 6f, CHAPTER), RecordingMarkHint(110f, 14f, CUT))
        assertEquals(setOf(1), visibleRecordingMarkHints(hints, gapPx = 14f, trackWidthPx = 1000f))
    }

    @Test
    fun symbolsThinWhereTheyAreDrawnInsideTheTrack() {
        // An ad badge at the start is drawn from 0 to 20, not from -10: the chapter at 28 is within 10 px.
        val start = listOf(RecordingMarkHint(0f, 20f, AD_BREAK), RecordingMarkHint(30f, 4f, CHAPTER))
        assertEquals(0f, start[0].leftPx(200f))
        assertEquals(setOf(0), visibleRecordingMarkHints(start, gapPx = 10f, trackWidthPx = 200f))
        // At the end it is drawn from 180 to 200: the chapter ending at 174 is within 10 px.
        val end = listOf(RecordingMarkHint(200f, 20f, AD_BREAK), RecordingMarkHint(172f, 4f, CHAPTER))
        assertEquals(180f, end[0].leftPx(200f))
        assertEquals(setOf(0), visibleRecordingMarkHints(end, gapPx = 10f, trackWidthPx = 200f))
        // Held inside the track, a symbol at the end reaches back into the pill's room and hides.
        val underPill = listOf(RecordingMarkHint(195f, 20f, CUT))
        assertEquals(emptySet<Int>(), visibleRecordingMarkHints(underPill, gapPx = 10f, trackWidthPx = 200f, blockedPx = 0f..183f))
    }
}
