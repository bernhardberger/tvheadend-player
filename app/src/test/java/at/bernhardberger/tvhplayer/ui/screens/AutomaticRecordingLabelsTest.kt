package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.ui.components.label
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class AutomaticRecordingLabelsTest {
    @Test fun timerDaySummariesNameCommonSets() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("Every day", formatTimerRecordingDays(context, DayOfWeek.entries.toSet()))
        assertEquals("Weekdays", formatTimerRecordingDays(context, DayOfWeek.entries.take(5).toSet()))
        assertEquals("Weekends",
            formatTimerRecordingDays(context, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
        assertEquals("Mon, Wed, Sun", formatTimerRecordingDays(context,
            setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        assertEquals("No day", formatTimerRecordingDays(context, emptySet()))
    }

    @Test fun automaticRecordingSummariesUseKindPluralsAllAndEmpty() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val all = automaticRecordingMatches
        assertEquals("In the guide: 6 episodes, all to be recorded",
            formatAutomaticRecordingPreviewSummary(context, AutomaticRecordingKind.SERIES, all))
        assertEquals("In the guide: 5 episodes, 2 to be recorded",
            formatAutomaticRecordingPreviewSummary(context, AutomaticRecordingKind.SERIES,
                all.copy(rows = all.rows.take(5), willRecordCount = 2)))
        for (kind in listOf(AutomaticRecordingKind.TITLE, AutomaticRecordingKind.KEYWORD)) {
            assertEquals("In the guide: 1 program, all to be recorded",
                formatAutomaticRecordingPreviewSummary(context, kind,
                    all.copy(rows = all.rows.take(1), willRecordCount = 1)))
            assertEquals("None in the guide yet", formatAutomaticRecordingPreviewSummary(
                context, kind, AutorecPreviewState.Matches(emptyList(), 0, false)))
        }
        for (kind in AutomaticRecordingKind.entries) {
            val singular = when (kind) {
                AutomaticRecordingKind.SERIES -> "episode"
                AutomaticRecordingKind.TIMER -> "recording"
                else -> "program"
            }
            assertEquals("Skip this $singular", context.getString(BrowseContextAction.SKIP.label(false, kind)))
            assertEquals(if (kind == AutomaticRecordingKind.TIMER) "Record this time" else "Record this $singular",
                context.getString(BrowseContextAction.UNSKIP.label(false, kind)))
            for (count in listOf(0, 1, 5)) {
                val scheduled = if (count == 0) "" else
                    "$count scheduled $singular${if (count == 1) "" else "s"} will be removed. "
                val body = scheduled + "Finished recordings stay."
                assertEquals(body, formatAutomaticRecordingDeleteBody(context, kind, count, false))
                assertEquals("$body Your unsaved changes will be discarded.",
                    formatAutomaticRecordingDeleteBody(context, kind, count, true))
            }
        }
    }
}
