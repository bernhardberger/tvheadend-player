package at.bernhardberger.tvhplayer.touch.recordings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.IntRect
import at.bernhardberger.tvhplayer.touch.testutil.assertLogoFitsItsShare
import at.bernhardberger.tvhplayer.touch.testutil.markedLogos
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.testutil.captureTouchWindows
import at.bernhardberger.tvhplayer.touch.ui.DvrChangeDialog
import at.bernhardberger.tvhplayer.touch.ui.RecordingRemoveControl
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf(flatFixture())
    private val deleted = mutableListOf<String>()
    private val stopped = mutableListOf<String>()
    private val played = mutableListOf<Pair<String, Boolean>>()
    private var asking by mutableStateOf(false)

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletPickShowsDetailsBesideTheList() {
        show()
        compose.onNodeWithText("Pick a recording to see it here").assertIsDisplayed()
        // A wide row carries every part.
        assertLongEpisodeKeepsItsFacts()
        assertLine("8:15 PM • 1h 45m • Kingsbridge – Northport • Harbor Sport")
        capture("tablet-recordings-root", TouchCanvas.TABLET)

        pickTheHalfWatchedMatch()
        resumeButton().assertIsDisplayed()
        compose.onNodeWithText("All recordings").assertIsDisplayed()
        capture("tablet-recordings-picked", TouchCanvas.TABLET)

        captureDeleteConfirmation("tablet-recordings-delete-confirm", TouchCanvas.TABLET)

        asking = false
        state.value = flatFixture().withPlayingRecording()
        val playing = playingMark()
        compose.onAllNodes(playing).assertCountEquals(2) // Selected row and its pane.
        assertPlayingActions()
        capture("tablet-recording-pane-playing", TouchCanvas.TABLET)

        state.value = flatFixture().withPlayingRecording("field-desk")
        compose.onAllNodes(playing).assertCountEquals(1)
        compose.onNodeWithText("Field Desk").assertIsNotSelected()
        compose.onNode(hasClickAction() and hasText("Westvale – Ridge Town", substring = true)).assertIsSelected()
        capture("tablet-recordings-playing", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun aRecordingOffersOnlyItsOwnRemoveControl() {
        state.value = flatFixture().withRemove(RecordingRemoveControl.RemoveEntry)
        show()
        pickTheHalfWatchedMatch()
        compose.onNodeWithContentDescription("Delete").assertDoesNotExist()
        compose.onNodeWithText("Remove entry").performClick()
        assertEquals(listOf(PickedId), deleted)
        resumeButton().assertDoesNotExist()

        state.value = flatFixture().withRemove(RecordingRemoveControl.Stop)
        compose.onNodeWithContentDescription("Delete").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(SharedR.string.stop_recording)).performClick()
        assertEquals(listOf(PickedId), stopped)

        // Without an action, the row does not show a disabled substitute.
        state.value = flatFixture().withRemove(null)
        compose.onNodeWithText("Remove entry").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun playButtonsFollowTheOfferedActionsNotTheBar() {
        show()
        pickTheHalfWatchedMatch()
        resumeButton().performClick()
        compose.onNodeWithText("Watch from beginning").performClick()
        assertEquals(listOf(PickedId to false, PickedId to true), played)

        // The same half-watched bar doesn't confer permission to Resume or start over.
        state.value = flatFixture().withPlay(emptyList())
        resumeButton().assertDoesNotExist()
        compose.onNodeWithText("Watch from beginning").assertDoesNotExist()
        compose.onNodeWithText("Watch").assertDoesNotExist()
        state.value = flatFixture().withPlay(listOf(RecordingPlayAction.Watch))
        compose.onNodeWithText("Watch").performClick()
        assertEquals(PickedId to false, played.last())
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletViewOptionsChangeTheGrouping() {
        fun sheetBack() = compose.runOnUiThread {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.setInTouchMode(true)
        show()
        val options = compose.onNodeWithText("View options").assertIsDisplayed()
        val pane = compose.onNodeWithTag("recordings-pane").fetchSemanticsNode().boundsInRoot
        assertEquals("View options ends at the pane keyline", pane.right, options.fetchSemanticsNode().boundsInRoot.right)
        openViewOptions()
        val sheet = hasAnyAncestor(isDialog())
        compose.onNodeWithText("Group by").assert(sheet)
        // The test harness still enumerates inactive windows; inspect the dialog's tree only.
        compose.onNode(hasText("Recordings") and sheet).assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").assertIsNotFocused()
        compose.captureTouchWindows(GROUP, "tablet-recordings-view", TouchCanvas.TABLET)
        compose.onNodeWithText("Group by").performClick()
        compose.onNodeWithText("The server’s folder tree").assertIsDisplayed()
        compose.captureTouchWindows(GROUP, "tablet-recordings-view-group", TouchCanvas.TABLET)

        // Back inside the sheet returns to its rows; a choice applies and returns there too.
        sheetBack()
        compose.onNodeWithText("Sort").assertIsDisplayed()
        compose.onNodeWithText("Group by").performClick()
        compose.onNodeWithText("Folders").performClick()
        assertEquals(DvrArchiveGrouping.FOLDERS, state.value.view.grouping)
        compose.onNodeWithText("Show").assertIsDisplayed()

        // A tap outside closes the sheet without focusing the opener in touch mode.
        compose.onNode(isDialog()).performTouchInput { click(Offset(width * 0.3f, height * 0.5f)) }
        assertSheetClosed()
        compose.onNodeWithText("View options").assertIsNotFocused()
        compose.onNodeWithText("Recording now").assertIsDisplayed()

        instrumentation.setInTouchMode(false)
        try {
            // Robolectric's instrumentation flag seeds new windows, but the activity is already attached.
            compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
            openViewOptions()
            compose.onNodeWithContentDescription("Close").assertIsFocused()
            sheetBack()
            assertSheetClosed()
            compose.onNodeWithText("View options").assertIsFocused()
        } finally {
            instrumentation.setInTouchMode(true)
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletFolderOpensOneLevelAndTheHeadShowsTheWay() {
        state.value = foldersFixture()
        show()
        compose.onNodeWithText("116 recordings in 7 folders").assertIsDisplayed()
        capture("tablet-recordings-folder", TouchCanvas.TABLET)

        listOf("Sport", "Football", "Harbor Sport", "Week 46").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("Recordings › Sport › Football › Harbor Sport").assertIsDisplayed()
        assertLine("Sat, Nov 14 • 1h 45m • Kingsbridge – Northport • Harbor Sport")
        compose.onNodeWithText("Basketball").assertDoesNotExist()
        pickTheHalfWatchedMatch()
        resumeButton().assertIsDisplayed()
        capture("tablet-recordings", TouchCanvas.TABLET)

        back()
        compose.onNodeWithText("Recordings › Sport › Football").assertIsDisplayed()
        compose.onNodeWithText("Week 46").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletWithoutRecordings() {
        state.value = noRecordingsFixture()
        show()
        compose.onNodeWithText("No recordings yet").assertIsDisplayed()
        capture("tablet-recordings-empty", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletFirstLoad() {
        state.value = flatFixture().copy(availability = RecordingsAvailability.Loading)
        show()
        compose.onNodeWithText("Loading recordings…").assertIsDisplayed()
        // The view options and the pane's placeholder come with the list.
        compose.onNodeWithContentDescription("View options").assertDoesNotExist()
        compose.onNodeWithText("Pick a recording to see it here").assertDoesNotExist()
        capture("tablet-recordings-loading", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    fun uprightPickOpensAPage() {
        state.value = flatFixture(harborLights = true, glassHarbor = true)
        show()
        capture("tablet-upright-recordings", TouchCanvas.TABLET_UPRIGHT)

        pickTheHalfWatchedMatch()
        compose.onNodeWithText("All recordings").assertDoesNotExist()
        resumeButton().assertIsDisplayed()
        capture("tablet-upright-recording", TouchCanvas.TABLET_UPRIGHT)

        back()
        compose.onNodeWithText("All recordings").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneListAndViewOptions() {
        state.value = flatFixture(harborLights = true)
        show()
        assertLongEpisodeKeepsItsFacts()
        capture("phone-recordings-all", TouchCanvas.PHONE)

        // Open the sheet without starting a native press ripple on the screen behind it.
        compose.onNodeWithContentDescription("View options").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Group by").assertIsDisplayed()
        captureSheetOver("phone-recordings-view", TouchCanvas.PHONE)

        compose.runOnUiThread {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        state.value = flatFixture().withPlayingRecording()
        compose.onAllNodes(playingMark()).assertCountEquals(1)
        capture("phone-recordings-playing", TouchCanvas.PHONE)

        state.value = flatFixture().withPlayingRecording("field-desk", title = FieldDeskEpisode)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(FieldDeskEpisode, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("The long title must give way to the playing bars", layouts.single().isLineEllipsized(0))
        compose.onNode(playingMark(), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phonePickOpensADetailsPage() {
        state.value = foldersFixture()
        show(initialPath = WeekPath)
        compose.onNodeWithText("Week 46").assertIsDisplayed()
        capture("phone-recordings", TouchCanvas.PHONE)

        pickTheHalfWatchedMatch()
        compose.onNodeWithText("Week 46").assertDoesNotExist()
        compose.onNodeWithText("Watch from beginning").assertIsDisplayed()
        assertActionRow(resumeButton(), stacked = false)
        capture("phone-recording-details", TouchCanvas.PHONE)

        captureDeleteConfirmation("phone-recordings-delete-confirm", TouchCanvas.PHONE)

        asking = false
        state.value = foldersFixture().withPlayingRecording()
        capture("phone-recording-details-playing", TouchCanvas.PHONE)
        compose.onNodeWithTag("recording-play-icon", useUnmergedTree = true).assertIsDisplayed()
        assertActionRow(continueButton(), stacked = false)
        assertFullSingleLineLabel(continueButton())
        assertPlayingActions()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneDetailsStacksPlayingActionsAtLargerFontScale() {
        state.value = foldersFixture().withPlayingRecording()
        show(initialPath = WeekPath, fontScale = 1.3f)
        pickTheHalfWatchedMatch()
        assertActionRow(continueButton(), stacked = true)
        assertFullSingleLineLabel(continueButton())
        compose.onNodeWithTag("recording-play-icon", useUnmergedTree = true).assertIsDisplayed()
        val delete = compose.activity.getString(SharedR.string.context_delete)
        val more = compose.activity.getString(R.string.recordings_more)
        val primaryTop = continueButton().fetchSemanticsNode().boundsInRoot.top
        for (label in listOf(delete, more)) {
            val icon = compose.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Toolbar action must remain above playback actions", icon.bottom < primaryTop)
        }
        capture("phone-recording-details-playing-stacked", TouchCanvas.PHONE)
    }

    private fun assertActionRow(primary: SemanticsNodeInteraction, stacked: Boolean) {
        val secondary = compose.onNodeWithText("Watch from beginning")
        val first = primary.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val second = secondary.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val picture = compose.onNodeWithTag("recording-details-picture").fetchSemanticsNode().boundsInRoot
        assertEquals(picture.left, first.left)
        assertEquals(picture.right, second.right)
        if (stacked) {
            assertTrue("Secondary must be below primary: $first, $second", first.bottom <= second.top)
            assertEquals(first.left, second.left)
            assertEquals(first.right, second.right)
        } else {
            assertEquals("Actions must share a row", first.top, second.top)
            assertTrue("Actions must not overlap", first.right <= second.left)
        }
        assertFullSingleLineLabel(secondary)
    }

    private fun assertFullSingleLineLabel(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals("A label that fits should not wrap", 1, layout.lineCount)
        assertEquals(
            "The action label must fit the button",
            layout.layoutInput.text.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true),
        )
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneFirstLoad() {
        state.value = flatFixture().copy(availability = RecordingsAvailability.Loading)
        show()
        compose.onNodeWithText("Loading recordings…").assertIsDisplayed()
        compose.onNodeWithContentDescription("View options").assertDoesNotExist()
        capture("phone-recordings-loading", TouchCanvas.PHONE)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneFilterEmptyOffersTheWayOut() {
        state.value = unwatchedEmptyFixture()
        show()
        compose.onNodeWithText("Everything here has been played").assertIsDisplayed()
        capture("phone-recordings-empty", TouchCanvas.PHONE)

        compose.onNodeWithText("Show all").performClick()
        assertEquals(DvrArchiveShow.ALL, state.value.view.show)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun aLogoStandingInForThePictureFitsItsShareOfTheSlot() {
        // Two rows without a picture, each on a ground color found nowhere else, so its slot can be found.
        val grounds = listOf(Color(0xFF00FF00), Color(0xFF0000FF))
        val fixture = flatFixture()
        val standIns = markedLogos().zip(grounds) { logo, ground -> logo to ground }
        state.value = fixture.copy(
            root = fixture.root.copy(
                recordings = fixture.root.recordings.mapIndexed { index, item ->
                    standIns.getOrNull(index)?.let { (logo, ground) ->
                        item.copy(
                            picture = null, channelLogo = logo, logoTint = ground, state = RecordingProgress.Unwatched,
                        )
                    } ?: item
                },
            ),
        )
        show()
        val screen = compose.onRoot().captureToImage().toPixelMap()
        grounds.forEachIndexed { index, ground ->
            var slot = IntRect(screen.width, screen.height, 0, 0)
            for (x in 0 until screen.width) for (y in 0 until screen.height) {
                if (screen[x, y] == ground) {
                    slot = IntRect(
                        minOf(slot.left, x), minOf(slot.top, y), maxOf(slot.right, x + 1), maxOf(slot.bottom, y + 1),
                    )
                }
            }
            screen.assertLogoFitsItsShare("stand-in $index", slot)
        }
    }

    private fun show(initialPath: List<String> = emptyList(), fontScale: Float? = null) {
        val actions = RecordingsActions(
            onView = { view ->
                val regrouped = if (view.grouping == DvrArchiveGrouping.FOLDERS) foldersFixture() else flatFixture()
                state.value = regrouped.copy(view = view)
            },
            onPlay = { id, fromStart -> played += id to fromStart },
            onDelete = { deleted += it },
            onMore = {},
            onStop = { stopped += it },
        )
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                    RecordingsScreen(state.value, actions, initialPath = initialPath)
                }
                if (asking) {
                    val control = RecordingRemoveControl.Delete
                    DvrChangeDialog(
                        control.confirmTitle, control.confirmBody, SharedR.string.cancel, control.confirmButton,
                        {}, { asking = false },
                    )
                }
            }
        }
    }

    /** Paint the shared dialog for capture only; TouchAppTest covers the Delete action and confirmation. */
    private fun captureDeleteConfirmation(name: String, canvas: TouchCanvas) {
        asking = true
        compose.captureTouchWindows(GROUP, name, canvas)
    }

    /** The picked recording offering [remove], as the shared rule decides it. */
    private fun RecordingsState.withRemove(remove: RecordingRemoveControl?) = copy(
        root = root.copy(recordings = root.recordings.map { if (it.id == PickedId) it.copy(remove = remove) else it }),
    )

    private fun RecordingsState.withPlay(play: List<RecordingPlayAction>) = copy(
        root = root.copy(recordings = root.recordings.map { if (it.id == PickedId) it.copy(play = play) else it }),
    )

    private fun RecordingsState.withPlayingRecording(id: String = PickedId, title: String? = null): RecordingsState {
        fun mark(folder: RecordingFolder): RecordingFolder = folder.copy(
            folders = folder.folders.map(::mark),
            recordings = folder.recordings.map { item ->
                if (item.id != id) item else item.copy(
                    playing = true,
                    title = title ?: item.title,
                    play = listOfNotNull(
                        RecordingPlayAction.ContinueWatching,
                        RecordingPlayAction.StartOver.takeIf { RecordingPlayAction.StartOver in item.play },
                    ),
                    state = if (id == "field-desk") RecordingProgress.PartlyWatched(java.time.Duration.ofMinutes(25), 0.44f)
                    else item.state,
                )
            },
        )
        return copy(root = mark(root))
    }

    private fun playingMark() = SemanticsMatcher.expectValue(
        SemanticsProperties.StateDescription, compose.activity.getString(R.string.channels_playing),
    )

    private fun assertPlayingActions() {
        val label = compose.activity.getString(SharedR.string.player_continue_watching)
        resumeButton().assertDoesNotExist()
        // Verify the actions without leaving native press ripples in the next capture.
        compose.onNodeWithText(label).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Watch from beginning").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(PickedId to false, PickedId to true), played.takeLast(2))
    }

    private fun pickTheHalfWatchedMatch() =
        compose.onNodeWithText("Westvale – Ridge Town", substring = true).performClick()

    private fun resumeButton() = compose.onNodeWithContentDescription("Resume from", substring = true)

    private fun continueButton() =
        compose.onNodeWithText(compose.activity.getString(SharedR.string.player_continue_watching))

    private fun assertLongEpisodeKeepsItsFacts() {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(FieldDeskEpisode.substringBefore(':'), substring = true, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val text = layout.layoutInput.text.text
        val visible = text.take(layout.getLineEnd(0, visibleEnd = true)).replace('\u202f', ' ').replace('\u00a0', ' ')
        assertTrue("Visible line must keep time and length: $visible", visible.startsWith("12:15 PM • 45m • E91"))
        assertTrue("Only the episode may be ellipsized: $text", layout.isLineEllipsized(0) && !text.contains("Northline News"))
    }

    // Times and lengths keep their parts together with (narrow) no-break spaces.
    private fun assertLine(line: String) {
        compose.onNode(
            SemanticsMatcher("line \"$line\"") { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.replace('\u202F', ' ').replace('\u00A0', ' ') == line }
            },
        ).assertIsDisplayed()
    }

    private fun openViewOptions() {
        compose.onNodeWithText("View options").performClick()
        compose.onNodeWithText("Group by").assertIsDisplayed()
    }

    private fun assertSheetClosed() = compose.onAllNodes(hasText("Group by")).assertCountEquals(0)

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch(GROUP, name, canvas)

    // The bottom sheet has its own window in Robolectric: draw it over captureTouch's full canvas.
    private fun captureSheetOver(name: String, canvas: TouchCanvas) {
        capture(name, canvas)
        compose.waitForIdle()
        val window = checkNotNull(ShadowDialog.getLatestDialog().window)
        compose.runOnIdle {
            val root = window.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            ViewCompat.dispatchApplyWindowInsets(
                root,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, canvas.statusBarDp * 2, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAVIGATION_PX))
                    .setVisible(WindowInsetsCompat.Type.systemBars(), true)
                    .build(),
            )
        }
        compose.waitForIdle()
        val output = File(checkNotNull(System.getProperty("touch.captureDir")), "$GROUP/$name.png")
        val frame = BitmapFactory.decodeFile(output.path).copy(Bitmap.Config.ARGB_8888, true)
        compose.runOnIdle {
            val decor = window.decorView
            Canvas(frame).apply {
                translate(0f, (height - decor.height).toFloat())
                decor.draw(this)
            }
        }
        output.outputStream().use { check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        frame.recycle()
    }

    private companion object {
        const val GROUP = "slice3"
        const val NAVIGATION_PX = 48
    }
}
