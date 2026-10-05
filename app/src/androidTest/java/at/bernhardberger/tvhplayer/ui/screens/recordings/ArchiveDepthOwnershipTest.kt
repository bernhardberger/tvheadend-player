package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.TabContentMotion
import at.bernhardberger.tvhplayer.ui.components.TabContent
import at.bernhardberger.tvhplayer.ui.components.rememberTabContentMotion
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import coil3.ImageLoader
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArchiveDepthOwnershipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun departingArchiveCannotFocusPublishOrActivateAfterItsFrameWait() {
        val archives = (1..2).map { id ->
            val path = "recording$id.ts"
            val entry = DvrEntry.create(
                id = DvrEntryId(id.toLong()), title = "Episode $id", state = DvrEntryState.COMPLETED,
                start = Instant.fromEpochSeconds(100), stop = Instant.fromEpochSeconds(200), path = path,
                files = listOf(DvrRecordingFile(fileId = null, path = path, start = null, stop = null, sizeBytes = null)),
            )
            buildDvrArchive(listOf(entry))
        }
        val navigation = archives.map {
            DepthNavigationState(DepthStack(listOf(DepthFrame(archiveLevelId(emptyList())))))
        }
        val selected = mutableIntStateOf(0)
        val opened = mutableListOf<DvrEntryId>()
        lateinit var motion: TabContentMotion
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent {
                val context = LocalContext.current
                val loader = remember(context) { ImageLoader.Builder(context).build() }
                TVHeadendPlayerTheme {
                    motion = rememberTabContentMotion(selected.intValue)
                    TabContent(motion, selected.intValue, state = { selected.intValue }) { index, owner ->
                        val requester = remember { FocusRequester() }
                        ArchiveDepthContent(
                            root = archives[index], navigation = navigation[index],
                            contentPadding = PaddingValues(24.dp),
                            isCurrent = owner.isCurrent, initialFocusEnabled = owner.isCurrent,
                            backEnabled = owner.isCurrent, contentFocus = requester,
                            onModeFocus = {}, onDrawerFocus = null,
                            onOpenRecording = { opened += it.id },
                            imageLoader = loader, currentSession = null, piconForEntry = { null },
                        )
                    }
                }
            }
            // Retain the actual native row action before its initial focus frame runs.
            val outgoingAction = requireNotNull(compose.onNodeWithTag("recording-list-entry-1")
                .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
            val outgoingStack = compose.runOnIdle { navigation[0].stack }
            compose.runOnIdle {
                assertTrue(opened.isEmpty())
                motion.select(1, listOf(0, 1))
                selected.intValue = 1
            }
            compose.mainClock.advanceTimeBy(96)
            compose.waitForIdle()
            compose.onNodeWithTag("recording-list-entry-2").assertIsFocused()
            compose.onNodeWithTag("recording-metadata-pane", useUnmergedTree = true)
                .assertHasNoClickAction().assert(isFocusable().not())
            compose.runOnIdle {
                assertEquals(outgoingStack, navigation[0].stack)
                assertEquals("recording:2", navigation[1].stack.active.focusedItemId)
                outgoingAction()
                assertTrue("Focus and stale actions must not open a recording", opened.isEmpty())
            }
            compose.onNodeWithTag("recording-list-entry-2").performKeyInput {
                pressKey(Key.DirectionCenter)
            }
            compose.mainClock.advanceTimeBy(32)
            compose.runOnIdle { assertEquals(listOf(DvrEntryId(2)), opened) }
            compose.mainClock.advanceTimeBy(1_200)
            compose.onNodeWithTag("recording-list-entry-2").assertIsFocused()
            compose.runOnIdle {
                assertEquals(outgoingStack, navigation[0].stack)
                assertEquals(listOf(DvrEntryId(2)), opened)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
