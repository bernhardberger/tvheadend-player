package at.bernhardberger.tvhplayer.touch.testutil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals

/** The canvases of the round-37 drawings, at xhdpi (2 px per dp). */
enum class TouchCanvas(val qualifiers: String, val width: Int, val height: Int, val statusBarDp: Int) {
    TABLET("en-rUS-w1280dp-h800dp-land-xhdpi", 2560, 1600, 32),
    TABLET_UPRIGHT("en-rUS-w800dp-h1280dp-port-xhdpi", 1600, 2560, 32),
    PHONE("en-rUS-w412dp-h892dp-port-xhdpi", 824, 1784, 40),
}

/**
 * Writes the current screen as `<group>/<name>.png` under the `touch.captureDir` directory.
 * Robolectric supplies no system-bar insets, so the space the drawings show is reserved here.
 * The test's `@Config(qualifiers = canvas.qualifiers)` must match [canvas].
 */
fun AndroidComposeTestRule<*, out ComponentActivity>.captureTouch(group: String, name: String, canvas: TouchCanvas) {
    runOnIdle {
        val content = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val density = content.resources.displayMetrics.density
        ViewCompat.dispatchApplyWindowInsets(
            content,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (canvas.statusBarDp * density).roundToInt(), 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (24 * density).roundToInt()))
                .setVisible(WindowInsetsCompat.Type.systemBars(), true)
                .build(),
        )
    }
    // With a dialog open, onRoot() matches the dialog's root too, and a capture of several roots takes
    // Robolectric's screenshot of all windows, which draws each at its origin rather than where its
    // gravity puts it. The frame is the activity's window alone; a test draws a dialog over it once.
    val frame = isRoot() and !isDialog() and !hasAnyDescendant(isDialog())
    val bitmap = onNode(frame).captureToImage().asAndroidBitmap()
    assertEquals(canvas.width to canvas.height, bitmap.width to bitmap.height)
    val directory = File(checkNotNull(System.getProperty("touch.captureDir")), group)
    directory.mkdirs()
    File(directory, "$name.png").outputStream().use {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
}

/**
 * The fictional fixture art of `app/src/test/resources/fixture-art` (see its README for the
 * line-up and which picon, title and art belong together). Decoding needs `@GraphicsMode(NATIVE)`.
 */
object TouchFixtureArt {
    private val root = File("../app/src/test/resources/fixture-art")

    /** A channel picon by its pack name, e.g. `harbor-sport`: 400×240, transparent. */
    fun picon(name: String): Bitmap = decode("picons/$name.png")

    /** Program key art or a still by its pack name, e.g. `harbor-kickoff`. */
    fun art(name: String): Bitmap = decode("art/$name.jpg")

    private fun decode(path: String): Bitmap {
        val file = File(root, path)
        check(file.isFile) { "missing fixture art ${file.absolutePath}" }
        return checkNotNull(BitmapFactory.decodeFile(file.path)) { "undecodable fixture art $path" }
    }
}
