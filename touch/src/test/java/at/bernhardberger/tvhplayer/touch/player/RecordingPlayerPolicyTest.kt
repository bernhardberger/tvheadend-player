package at.bernhardberger.tvhplayer.touch.player

import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.RecordingMarkStop
import at.bernhardberger.tvhplayer.core.recordingMarkStops
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingPlayerPolicyTest {
    private val ad = RecordingMark(RecordingMarkKind.AD_BREAK, 1_000L, 2_000L)
    private val cut = RecordingMark(RecordingMarkKind.CUT, 3_000L, 4_000L)
    private val scene = RecordingMark(RecordingMarkKind.CHAPTER, 5_000L)
    private val chapter = RecordingMark(RecordingMarkKind.CHAPTER, 6_000L, name = "Second half")
    private val marks = listOf(ad, cut, scene, chapter)
    private val stops = recordingMarkStops(marks, 7_000L)

    @Test
    fun `drag labels distinguish bare time sections and named snaps`() {
        val cases = listOf(
            1_500L to RecordingMarkStop(1_500L, ad),
            1_960L to RecordingMarkStop(2_000L, ad, atEnd = true),
            3_540L to RecordingMarkStop(3_540L, cut),
            5_040L to RecordingMarkStop(5_040L),
            5_970L to RecordingMarkStop(6_000L, chapter),
            6_490L to RecordingMarkStop(6_500L),
        )
        cases.forEach { (target, expected) ->
            assertEquals("at $target", expected, recordingDrag(target, 6_500L, 60L, stops, marks))
        }
    }

    @Test
    fun `track keeps points and clips spans to available duration`() {
        val spans = recordingTrackMarks(marks, 3_500L)
        assertEquals(listOf(ad, cut.copy(endMs = 3_500L)), spans)
        assertEquals(marks, recordingTrackMarks(marks, 7_000L))
    }
}
