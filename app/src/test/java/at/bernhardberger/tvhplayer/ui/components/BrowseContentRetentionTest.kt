package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.lang.ref.WeakReference
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrowseContentRetentionTest {
    @get:Rule val compose = createComposeRule()

    private class Payload(val revision: Int) {
        // Stand in for a metadata snapshot without server data or SDK internals.
        val bytes = ByteArray(64 * 1024)
    }

    @Test fun sameTabRefreshesReleaseObsoletePayloadsWhileStillMounted() {
        val payload = mutableStateOf(Payload(0))
        val obsolete = mutableListOf<WeakReference<Payload>>()
        compose.setContent {
            val motion = rememberBrowseContentMotion("channels")
            BrowseTabContent(motion, "channels", state = { payload.value }) { frame, _ ->
                BasicText("Revision ${frame.revision}")
            }
        }
        repeat(40) { revision ->
            compose.runOnIdle {
                obsolete += WeakReference(payload.value)
                payload.value = Payload(revision + 1)
            }
            compose.waitForIdle()
        }
        compose.onNodeWithText("Revision 40").assertExists()
        assertReleased(obsolete)
    }

    @Test fun interruptedReturnKeepsOnlyCurrentVisitAndReleasesDepartedPayloads() {
        val selected = mutableIntStateOf(0)
        val payload = mutableStateOf(Payload(0))
        val obsolete = mutableListOf<WeakReference<Payload>>()
        val owners = mutableSetOf<BrowseTabOwner>()
        lateinit var motion: BrowseContentMotion
        compose.setContent {
            motion = rememberBrowseContentMotion(selected.intValue)
            BrowseTabContent(motion, selected.intValue, state = { payload.value }) { frame, owner ->
                SideEffect { owners += owner }
                BasicText("Revision ${frame.revision}")
            }
        }
        compose.waitForIdle()
        val firstOwner = owners.single()
        compose.mainClock.autoAdvance = false
        repeat(40) { revision ->
            compose.runOnIdle {
                obsolete += WeakReference(payload.value)
                val next = (revision + 1) % 2
                motion.select(next, listOf(0, 1))
                selected.intValue = next
                payload.value = Payload(revision + 1)
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            compose.onNodeWithText("Revision ${revision + 1}").assertExists()
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithText("Revision 40").assertExists()
        assertFalse(firstOwner.isCurrent)
        assertEquals(1, owners.count { it.isCurrent })
        assertReleased(obsolete)
    }

    private fun assertReleased(obsolete: List<WeakReference<Payload>>) {
        // Permit a few framework/current-transition references, but never a history
        // proportional to metadata updates. Keep the content mounted during collection.
        repeat(10) {
            Runtime.getRuntime().gc()
            if (obsolete.count { it.get() != null } <= 4) return
            Thread.sleep(50)
        }
        val retained = obsolete.count { it.get() != null }
        assertTrue("Retained $retained of 40 obsolete browse payloads", retained <= 4)
    }
}
