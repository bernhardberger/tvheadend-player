package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.BrowseContentMotion
import at.bernhardberger.tvhplayer.ui.components.BrowseTabContent
import at.bernhardberger.tvhplayer.ui.components.rememberBrowseContentMotion
import coil3.ImageLoader
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FolderMetadataPaneTest {
    @get:Rule val compose = createComposeRule()

    @Test fun departingPreviewCannotPublishOrFocusAfterItsFrameWait() {
        val folders = (1..2).map { id ->
            val path = "Folder $id/file.ts"
            val entry = DvrEntry.create(
                id = DvrEntryId(id.toLong()), title = "Episode $id", state = DvrEntryState.COMPLETED,
                start = Instant.fromEpochSeconds(100), stop = Instant.fromEpochSeconds(200), path = path,
                files = listOf(DvrRecordingFile(fileId = null, path = path, start = null, stop = null, sizeBytes = null)),
            )
            buildDvrArchive(listOf(entry)).folders.single()
        }
        val selected = mutableIntStateOf(0)
        val published = mutableListOf<DvrEntryId>()
        lateinit var motion: BrowseContentMotion
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent {
                val context = LocalContext.current
                val loader = remember(context) { ImageLoader.Builder(context).build() }
                TVHeadendPlayerTheme {
                    motion = rememberBrowseContentMotion(selected.intValue)
                    BrowseTabContent(motion, selected.intValue, state = { folders[selected.intValue] }) { folder, _ ->
                        val requester = remember { FocusRequester() }
                        FolderMetadataPane(
                            folder = folder, imageLoader = loader, currentSession = null,
                            piconForEntry = { null }, previewFocus = requester,
                            selectedPreviewId = null, restoreFocus = true,
                            onPreviewFocusChanged = {}, onPreviewRecordingFocused = { published += it },
                            onMoveToFolder = {}, onOpenRecording = {},
                        )
                    }
                }
            }
            compose.onNodeWithTag("folder-preview-recording-1").assertExists()
            compose.runOnIdle {
                assertTrue(published.isEmpty())
                motion.select(1, listOf(0, 1))
                selected.intValue = 1
            }
            compose.mainClock.advanceTimeBy(96)
            compose.waitForIdle()
            compose.onNodeWithTag("folder-preview-recording-2").assertIsFocused()
            // Native focus and restoration may both acknowledge the newest entry.
            assertEquals(setOf(DvrEntryId(2)), published.toSet())
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
