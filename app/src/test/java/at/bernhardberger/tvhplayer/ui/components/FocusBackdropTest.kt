package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The browse backdrop's pacing on a manual clock: settle, interval, load wait and layer count. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class FocusBackdropTest {
    @get:Rule val compose = createComposeRule()

    private var target by mutableStateOf("A")
    private val reveals = mutableListOf<Pair<String, Long>>()
    private var layers by mutableIntStateOf(0)
    private var mostLayers = 0

    /** Shows backdrops that report ready after [loadMs] of their target, or never for null. */
    private fun show(loadMs: (String) -> Long?) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FocusBackdrop({ target }, onReveal = { reveals += it to compose.mainClock.currentTime }) { shown, onReady ->
                DisposableEffect(Unit) {
                    mostLayers = maxOf(mostLayers, ++layers)
                    onDispose { layers-- }
                }
                LaunchedEffect(Unit) {
                    delay(loadMs(shown) ?: return@LaunchedEffect)
                    onReady()
                }
            }
        }
    }

    private fun advance(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    /** Moves focus; on the paused clock the backdrop sees the change once it is applied. */
    private fun focus(item: String) {
        target = item
        Snapshot.sendApplyNotifications()
    }

    @Test fun aSlowBackdropAndAQuickSuccessorStillChangeAtMostOncePerInterval() {
        show { if (it == "B") 400 else 0 }
        advance(1_000)
        focus("B")
        advance(400)
        // C replaces B as focus settles again, just after B has finished loading.
        focus("C")
        advance(2_000)
        assertEquals(listOf("A", "B", "C"), reveals.map { it.first })
        reveals.zipWithNext { (_, before), (_, after) ->
            assertTrue("Changes ${after - before}ms apart", after - before >= BrowseMotionPolicy.backdropIntervalMs)
        }
    }

    @Test fun focusSettlesBeforeTheBackdropChangesAndOneThatNeverLoadsShowsAfterTheWait() {
        show { if (it == "never") null else 0 }
        advance(1_000)
        for (step in listOf("1", "2", "3", "4", "5")) {
            focus(step)
            advance(100)
        }
        assertEquals("Nothing changes while focus moves", listOf("A"), reveals.map { it.first })
        advance(BrowseMotionPolicy.backdropSettleMs)
        assertEquals(listOf("A", "5"), reveals.map { it.first })
        advance(1_000)

        focus("never")
        val changed = compose.mainClock.currentTime
        advance(BrowseMotionPolicy.backdropSettleMs + BrowseMotionPolicy.backdropLoadTimeoutMs - 100)
        assertEquals("The current backdrop stays while the next one loads", "5", reveals.last().first)
        advance(200)
        assertEquals("never", reveals.last().first)
        assertTrue(reveals.last().second - changed >= BrowseMotionPolicy.backdropLoadTimeoutMs)
        assertEquals("Only the shown backdrop and the next one are composed", 2, mostLayers)
    }
}
