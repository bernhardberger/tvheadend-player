package at.bernhardberger.tvhplayer.ui.common

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class AppErrorTextTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val text get() = AppErrorText(context)

    private fun id(name: String) = context.resources.getIdentifier(name, "string", context.packageName)

    @Test fun everyConditionHasATitleAndReasonNamedByItsNumber() {
        val formatted = setOf(AppErrorCondition.RECONNECTING, AppErrorCondition.RECORDING_NOT_STARTED)
        for (condition in AppErrorCondition.entries) {
            assertEquals("$condition", id("error_${condition.number}_title"), condition.titleRes())
            assertTrue("$condition", condition.titleRes() != 0)
            assertTrue("$condition", text.title(condition).isNotBlank())
            assertTrue("$condition", text.reason(condition).isNotBlank())
            // No reason is ever shown with an unfilled placeholder.
            assertFalse("$condition", text.reason(condition).contains('%'))
            if (condition !in formatted) {
                assertEquals("$condition", context.getString(id("error_${condition.number}_reason")), text.reason(condition))
            }
        }
    }

    @Test fun hintsAreSharedAndOnlyCountdownStartTimeAndFinishHaveNone() {
        val withoutHint = AppErrorCondition.entries.filter { text.hint(it) == null }
        assertEquals(setOf(AppErrorCondition.RECONNECTING, AppErrorCondition.RECORDING_NOT_STARTED,
            AppErrorCondition.RECORDING_DEFERRED), withoutHint.toSet())
        for (condition in AppErrorCondition.entries - withoutHint.toSet()) {
            assertTrue("$condition", context.resources.getResourceEntryName(condition.hintRes()!!).startsWith("error_hint_"))
        }
        val shared = text.hint(AppErrorCondition.NO_SIGNAL)
        assertEquals("Try again later or choose another channel.", shared)
        for (condition in listOf(AppErrorCondition.NO_FREE_ADAPTER, AppErrorCondition.CHANNEL_TAKEN_OVER,
            AppErrorCondition.STREAM_STOPPED)) assertEquals(AppErrorCondition.NO_SIGNAL.hintRes(), condition.hintRes())
        assertEquals(AppErrorCondition.SCRAMBLED.hintRes(), AppErrorCondition.CHANNEL_ACCESS_DENIED.hintRes())
        assertEquals("Turn it on in Tvheadend, or choose another channel.", text.hint(AppErrorCondition.MUX_NOT_ENABLED))
    }

    @Test fun aDeferredRecordingSaysWhenItCanPlay() {
        assertEquals("Can’t play yet", text.title(AppErrorCondition.RECORDING_DEFERRED))
        assertEquals("This recording can be played once it’s finished.", text.reason(AppErrorCondition.RECORDING_DEFERRED))
        assertEquals("Try again in a moment.", text.hint(AppErrorCondition.RECORDING_INTERRUPTED))
    }

    @Test fun everyActionHasALabel() {
        assertEquals("Retry", text.label(AppErrorAction.RETRY))
        assertEquals("Connection settings", text.label(AppErrorAction.CONNECTION_SETTINGS))
        assertEquals("Close", text.label(AppErrorAction.CLOSE))
        assertEquals(AppErrorAction.entries.size, AppErrorAction.entries.map { it.labelRes() }.toSet().size)
    }

    @Test fun countdownAndStartTimeFillTheirSentences() {
        assertEquals("Reconnecting in 5 s…", text.reason(AppErrorCondition.RECONNECTING, reconnectInSeconds = 5))
        assertEquals("Reconnecting…", text.reason(AppErrorCondition.RECONNECTING))
        // A due or overdue attempt has no countdown left to show.
        assertEquals("Reconnecting…", text.reason(AppErrorCondition.RECONNECTING, reconnectInSeconds = 0))
        assertEquals("Reconnecting…", text.reason(AppErrorCondition.RECONNECTING, reconnectInSeconds = -3))
        assertEquals("It starts at 8:15 PM.", text.reason(AppErrorCondition.RECORDING_NOT_STARTED, startsAt = "8:15 PM"))
        assertTrue(text.reason(AppErrorCondition.RECORDING_NOT_STARTED).isNotBlank())
    }
}
