package at.bernhardberger.tvhplayer.touch.recordings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.IntRect
import at.bernhardberger.tvhplayer.touch.testutil.assertLogoFitsItsShare
import at.bernhardberger.tvhplayer.touch.testutil.markedLogos
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
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
        assertLine("Kingsbridge – Northport • 8:15 PM • 1h 45m • Harbor Sport")
        assertLine("12:15 PM • 45m • Northline News")
        capture("tablet-recordings-root", TouchCanvas.TABLET)

        pickTheHalfWatchedMatch()
        resumeButton().assertIsDisplayed()
        compose.onNodeWithText("All recordings").assertIsDisplayed()
        capture("tablet-recordings-picked", TouchCanvas.TABLET)

        captureDeleteConfirmation("tablet-recordings-delete-confirm", TouchCanvas.TABLET)
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
        show()
        openViewOptions()
        capture("tablet-recordings-view", TouchCanvas.TABLET)
        compose.onNodeWithText("Group by").performClick()
        compose.onNodeWithText("The server’s folder tree").assertIsDisplayed()
        capture("tablet-recordings-view-group", TouchCanvas.TABLET)

        // Back inside the sheet returns to its rows; a choice applies and returns there too.
        back()
        compose.onNodeWithText("Sort").assertIsDisplayed()
        compose.onNodeWithText("Group by").performClick()
        compose.onNodeWithText("Folders").performClick()
        assertEquals(DvrArchiveGrouping.FOLDERS, state.value.view.grouping)
        compose.onNodeWithText("Show").assertIsDisplayed()

        // A tap outside closes the sheet; so does system Back.
        compose.onRoot().performTouchInput { click(Offset(width * 0.3f, height * 0.5f)) }
        assertSheetClosed()
        compose.onNodeWithText("Recording now").assertIsDisplayed()
        openViewOptions()
        back()
        assertSheetClosed()
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
        assertLine("Kingsbridge – Northport • Sat, Nov 14 • 1h 45m • Harbor Sport")
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
        // Too narrow for all four: the channel and then the length go, whole.
        assertLine("Kingsbridge – Northport • 8:15 PM")
        capture("phone-recordings-all", TouchCanvas.PHONE)

        compose.onNodeWithContentDescription("View options").performClick()
        compose.onNodeWithText("Group by").assertIsDisplayed()
        captureSheetOver("phone-recordings-view", TouchCanvas.PHONE)
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
        capture("phone-recording-details", TouchCanvas.PHONE)

        captureDeleteConfirmation("phone-recordings-delete-confirm", TouchCanvas.PHONE)
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

    private fun show(initialPath: List<String> = emptyList()) {
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
                RecordingsScreen(state.value, actions, initialPath = initialPath)
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

    private fun pickTheHalfWatchedMatch() =
        compose.onNodeWithText("Westvale – Ridge Town", substring = true).performClick()

    private fun resumeButton() = compose.onNodeWithContentDescription("Resume from", substring = true)

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
