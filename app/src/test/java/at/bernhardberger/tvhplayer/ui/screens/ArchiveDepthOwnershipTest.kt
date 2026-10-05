package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import at.bernhardberger.tvhplayer.ui.screens.recordings.ArchiveDepthContent
import at.bernhardberger.tvhplayer.ui.screens.recordings.reconcileArchiveStack
import coil3.ImageLoader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class ArchiveDepthOwnershipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overlappingArchiveVisitsShareNavigationButOnlyCurrentVisitReconciles() {
        val removed = DvrArchiveFolder("Removed", listOf("Series", "Removed"), emptyList(), listOf(entry(1)))
        val oldParent = DvrArchiveFolder("Series", listOf("Series"), listOf(removed), listOf(entry(2)))
        val oldRoot = DvrArchiveFolder("", emptyList(), listOf(oldParent), emptyList())
        val newParent = oldParent.copy(folders = emptyList(), recordings = listOf(entry(2), entry(3)))
        val newRoot = oldRoot.copy(folders = listOf(newParent))
        val selected = mutableIntStateOf(0)
        val focusAllowed = mutableStateOf(true)
        val navigation = DepthNavigationState(DepthStack(listOf(
            DepthFrame("archive:", "folder:Series"),
            DepthFrame("archive:Series", "folder:Series/Removed"),
            DepthFrame("archive:Series/Removed", "recording:1"),
        )))
        val opened = mutableListOf<DvrEntryId>()
        val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent {
                TVHeadendPlayerTheme {
                    Box {
                        // Retain the departing visit's immutable tree, as BrowseTabContent does.
                        ArchiveDepthContent(
                            root = oldRoot, navigation = navigation, contentPadding = PaddingValues(24.dp),
                            isCurrent = selected.intValue == 0,
                            initialFocusEnabled = selected.intValue == 0 && focusAllowed.value,
                            backEnabled = selected.intValue == 0,
                            contentFocus = remember { FocusRequester() }, onModeFocus = {}, onDrawerFocus = null,
                            onOpenRecording = { opened += it.id }, imageLoader = loader,
                            currentSession = null, piconForEntry = { null },
                        )
                        if (selected.intValue == 2) ArchiveDepthContent(
                            root = newRoot, navigation = navigation, contentPadding = PaddingValues(24.dp),
                            isCurrent = true, initialFocusEnabled = focusAllowed.value, backEnabled = true,
                            contentFocus = remember { FocusRequester() }, onModeFocus = {}, onDrawerFocus = null,
                            onOpenRecording = { opened += it.id }, imageLoader = loader,
                            currentSession = null, piconForEntry = { null },
                        )
                    }
                }
            }
            compose.mainClock.advanceTimeBy(1_200)
            compose.onNodeWithTag("recording-list-entry-1").assertIsFocused()
            compose.runOnIdle {
                selected.intValue = 1
                focusAllowed.value = false
            }
            compose.mainClock.advanceTimeBy(32)
            compose.runOnIdle {
                navigation.update(reconcileArchiveStack(navigation.stack, newRoot))
                selected.intValue = 2
            }
            compose.mainClock.advanceTimeBy(32)
            compose.runOnIdle {
                assertEquals(listOf("archive:", "archive:Series"), navigation.stack.path)
                assertEquals("recording:2", navigation.stack.active.focusedItemId)
                focusAllowed.value = true
            }
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("recording-list-entry-2").assertIsFocused()
            // Old Archive is still mounted with its removed folder and stale row order.
            assertTrue(compose.onAllNodesWithTag("recordings-archive-list", useUnmergedTree = true)
                .fetchSemanticsNodes().size > 1)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.mainClock.advanceTimeBy(32)
            compose.runOnIdle {
                assertEquals(listOf("archive:", "archive:Series"), navigation.stack.path)
                assertEquals("recording:3", navigation.stack.active.focusedItemId)
                assertEquals(1, navigation.stack.active.focusedIndex)
                assertTrue(opened.isEmpty())
            }
            compose.onNodeWithTag("recording-list-entry-3").assertIsFocused()
            compose.mainClock.advanceTimeBy(1_200)
            compose.onNodeWithTag("recording-list-entry-3").assertIsFocused()
        } finally {
            compose.mainClock.autoAdvance = true
            loader.shutdown()
        }
    }

    private fun entry(id: Long) = DvrEntry.create(id = DvrEntryId(id), title = "Recording $id")
}
