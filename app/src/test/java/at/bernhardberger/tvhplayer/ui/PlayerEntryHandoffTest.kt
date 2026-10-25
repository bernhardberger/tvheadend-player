package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The handoff between an overlay's Watch and the player request: one request at a time, and an
 * overlay's deferred close only ever closes that overlay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayerEntryHandoffTest {
    @get:Rule val compose = createComposeRule()

    private val handoff = PlayerEntryHandoff()
    private val overlay = mutableStateOf<Int?>(null)
    private val playerShown = mutableStateOf(false)
    private val requests = mutableListOf<Int>()
    private val closed = mutableListOf<Int>()
    private var gate = CompletableDeferred<Unit>()

    @Test fun aRepeatedActivationWhileTheRequestIsPendingStartsNoSecondRequest() {
        show(1)

        activate(1)
        activate(1)
        activate(1)
        compose.waitForIdle()
        assertEquals("one request for the burst", listOf(1), requests)
        compose.onNodeWithTag("overlay-1").assertExists()

        resolve()
        assertEquals("the overlay closes once", listOf(1), closed)
        assertFalse("a request that stays in browse releases its departure", handoff.departing)
        show(2)
        activate(2)
        assertEquals("a later activation is a new request", listOf(1, 2), requests)
    }

    @Test fun aRequestThatOpensThePlayerLeavesItsOverlayToFadeWithBrowse() {
        show(1)
        assertFalse(handoff.departing)
        activate(1)
        assertTrue(handoff.departing)
        compose.runOnIdle { playerShown.value = true }
        resolve()

        assertTrue("the overlay is not closed behind the player: $closed", closed.isEmpty())
        assertFalse("the player request has finished", handoff.pending)
        assertTrue("the outgoing overlay still holds its presentation through the fade", handoff.departing)
        compose.onNodeWithTag("overlay-1").assertExists()
        show(null)
        assertFalse("disposing the outgoing page releases its presentation", handoff.departing)
    }

    @Test fun anOverlayDismissedWhileItsRequestIsPendingIsNotClosedAgain() {
        show(1)
        activate(1)
        // The overlay is dismissed and another one opens before the request resolves.
        show(null)
        show(2)
        assertFalse("a new page does not inherit the old page's departure", handoff.departing)

        resolve()

        assertTrue("the older overlay's close did not run: $closed", closed.isEmpty())
        compose.onNodeWithTag("overlay-2").assertExists()
    }

    @Test fun anOverlayReplacedWhileItsRequestIsPendingIsNotClosedByIt() {
        show(1)
        activate(1)
        show(2)

        resolve()

        assertFalse("closed $closed", closed.contains(1))
        compose.onNodeWithTag("overlay-2").assertExists()
    }

    private fun show(id: Int?) {
        if (!composed) {
            composed = true
            compose.setContent {
                val scope = rememberCoroutineScope()
                PlayerEntryOverlays(handoff, playerShown = { playerShown.value }) {
                    overlay.value?.let { id ->
                        key(id) {
                            val closeBehindPlayer = rememberCloseBehindPlayer()
                            Box(
                                Modifier.size(40.dp).testTag("overlay-$id").clickable {
                                    closeBehindPlayer { overlay.value = null; closed += id }
                                    handoff.request(scope) { requests += id; gate.await() }
                                },
                            )
                        }
                    }
                }
            }
        }
        compose.runOnIdle { overlay.value = id }
        compose.waitForIdle()
    }

    private var composed = false

    private fun activate(id: Int) {
        compose.onNodeWithTag("overlay-$id").performSemanticsAction(SemanticsActions.OnClick)
    }

    /** The request completes; whatever it deferred is due now. */
    private fun resolve() {
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitForIdle()
        gate = CompletableDeferred()
    }
}
