package at.bernhardberger.tvhplayer.touch.testutil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inspector.WindowInspector
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
    PHONE_LANDSCAPE("en-rUS-w892dp-h412dp-land-xhdpi", 1784, 824, 0),
}

/**
 * Writes the current screen as `<group>/<name>.png` under the `touch.captureDir` directory.
 * Robolectric supplies no system-bar insets, so the space the drawings show is reserved here.
 * The test's `@Config(qualifiers = canvas.qualifiers)` must match [canvas].
 */
fun AndroidComposeTestRule<*, out ComponentActivity>.captureTouch(group: String, name: String, canvas: TouchCanvas) {
    runOnIdle { reserveSystemBars(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0), canvas) }
    // With a dialog open, onRoot() matches the dialog's root too, and a capture of several roots takes
    // Robolectric's screenshot of all windows, which draws each at its origin rather than where its
    // gravity puts it. The frame is the activity's window alone; a test draws a dialog over it once.
    val frame = isRoot() and !isDialog() and !hasAnyDescendant(isDialog())
    val bitmap = onNode(frame).captureToImage().asAndroidBitmap()
    assertEquals(canvas.width to canvas.height, bitmap.width to bitmap.height)
    write(group, name, bitmap)
}

/**
 * [captureTouch] with the popups, sheets and dialogs open over the activity drawn in, which
 * Robolectric keeps in windows of their own and does not lay out on a screen. Each window is
 * placed as the system would by its layout parameters: a popup by its gravity and offset in the
 * activity's window, any other window by its gravity in the screen less the system bars it fits.
 * A window asking for the screen behind it dimmed gets its dim amount of black under it.
 */
fun AndroidComposeTestRule<*, out ComponentActivity>.captureTouchWindows(group: String, name: String, canvas: TouchCanvas) {
    val views = runOnIdle { WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow } }
    runOnIdle {
        views.forEach { view ->
            view.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { reserveSystemBars(it, canvas) }
        }
    }
    waitForIdle()
    val bitmap = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888)
    runOnIdle {
        val frame = Canvas(bitmap)
        val density = activity.resources.displayMetrics.density
        views.forEach { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams
            frame.save()
            if (params != null) {
                if (params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                    frame.drawColor(Color.argb((params.dimAmount * 255).roundToInt(), 0, 0, 0))
                }
                val placed = Rect()
                Gravity.apply(
                    params.gravity, view.width, view.height, windowFrame(params, canvas, density), params.x, params.y,
                    placed,
                )
                frame.translate(placed.left.toFloat(), placed.top.toFloat())
            }
            view.draw(frame)
            frame.restore()
        }
    }
    write(group, name, bitmap)
}

/**
 * Where a window is laid out: a popup (a sub-window) in its activity's window, which fills the
 * canvas; any other window in the canvas less the reserved system bars its parameters fit.
 */
private fun windowFrame(params: WindowManager.LayoutParams, canvas: TouchCanvas, density: Float): Rect {
    val frame = Rect(0, 0, canvas.width, canvas.height)
    val subWindow =
        params.type in WindowManager.LayoutParams.FIRST_SUB_WINDOW..WindowManager.LayoutParams.LAST_SUB_WINDOW
    if (subWindow) return frame
    val fits = params.fitInsetsTypes
    if (fits and WindowInsets.Type.statusBars() != 0) frame.top += statusBar(canvas, density)
    if (fits and WindowInsets.Type.navigationBars() != 0) frame.bottom -= navigationBar(density)
    return frame
}

private fun statusBar(canvas: TouchCanvas, density: Float) = (canvas.statusBarDp * density).roundToInt()

private fun navigationBar(density: Float) = (NAVIGATION_BAR_DP * density).roundToInt()

private const val NAVIGATION_BAR_DP = 24

private fun reserveSystemBars(content: View, canvas: TouchCanvas) {
    val density = content.resources.displayMetrics.density
    ViewCompat.dispatchApplyWindowInsets(
        content,
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBar(canvas, density), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationBar(density)))
            .setVisible(WindowInsetsCompat.Type.systemBars(), true)
            .build(),
    )
}

private fun write(group: String, name: String, bitmap: Bitmap) {
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
