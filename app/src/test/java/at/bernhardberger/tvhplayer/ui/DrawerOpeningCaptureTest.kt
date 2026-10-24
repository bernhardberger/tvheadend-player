package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.components.SideRail
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Frame by frame, the production drawer opening from content and closing again, cropped to the
 * drawer's destinations; the capture writes one sheet per direction into `build/review/drawer/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerOpeningCaptureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    /** The selected pill grows with the opening drawer: its end stays round, never cut flat. */
    @Test fun theOpeningDrawersSelectedPillKeepsItsRoundEnd() {
        showDrawer()
        frames(Key.DirectionLeft).forEachIndexed { index, frame ->
            val middle = pillEnd(frame, PillMiddle)
            val nearTop = pillEnd(frame, PillMiddle - 21)
            // A 24dp radius ends 12px short 21px above the middle; a flat cut ends level.
            assertTrue("frame ${index + 1}: pill ends at $middle in the middle and $nearTop near the top",
                middle - nearTop >= 8)
        }
        compose.onNodeWithTag("nav-channels").assertIsFocused()
    }

    @Category(VisualCapture::class)
    @Test fun drawerOpeningAndClosingFrames() {
        showDrawer()
        val opening = frames(Key.DirectionLeft)
        compose.onNodeWithTag("nav-channels").assertIsFocused()
        val closing = frames(Key.DirectionRight)
        compose.onNodeWithTag("content").assertIsFocused()

        val directory = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
            .resolve("build/review/drawer").apply { mkdirs() }
        sheet(opening).save(File(directory, "opening.png"))
        sheet(closing).save(File(directory, "closing.png"))
    }

    /** The last light pixel of the selected Channels pill on [row] of a cropped frame. */
    private fun pillEnd(frame: Bitmap, row: Int): Int =
        (0 until frame.width).last { Color(frame.getPixel(it, row)).luminance() > .5f }

    private fun showDrawer() {
        val content = FocusRequester()
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SideRail(
                        currentRoute = AppDestination.CHANNELS,
                        showEpgMenu = true,
                        onRootBack = {},
                        onNavigate = {},
                    ) { padding, _ ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            Button(onClick = {}, Modifier.align(Alignment.CenterStart).focusRequester(content).testTag("content")) {
                                Text("Content")
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { content.requestFocus() }
        compose.waitForIdle()
        compose.onNodeWithTag("content").assertIsFocused()
    }

    /** Every second 16 ms frame after [key], until the drawer has settled. */
    private fun frames(key: Key): List<Bitmap> {
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(key) }
        val frames = (0 until FrameCount).map {
            repeat(2) { compose.mainClock.advanceTimeByFrame() }
            compose.waitForIdle()
            drawer()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return frames
    }

    private fun drawer(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(full))
            bitmap = Bitmap.createBitmap(full, 0, CropTop, CropWidth, CropHeight)
        }
        return bitmap
    }

    /** Frames in reading order, five to a row, 32 ms apart. */
    private fun sheet(frames: List<Bitmap>): Bitmap {
        val columns = 5
        val rows = (frames.size + columns - 1) / columns
        val sheet = Bitmap.createBitmap(columns * (CropWidth + Gap), rows * (CropHeight + Gap), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(android.graphics.Color.MAGENTA)
        frames.forEachIndexed { index, frame ->
            canvas.drawBitmap(frame, (index % columns) * (CropWidth + Gap).toFloat(), (index / columns) * (CropHeight + Gap).toFloat(), null)
        }
        return sheet
    }

    private fun Bitmap.save(file: File) = file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }

    private companion object {
        const val FrameCount = 20
        // The brand and the four destinations at full width; the open drawer reaches 280.
        const val CropTop = 150
        const val CropWidth = 300
        const val CropHeight = 210
        const val Gap = 4
        // The Channels item's vertical middle (screen 214) in the crop.
        const val PillMiddle = 64
    }
}
