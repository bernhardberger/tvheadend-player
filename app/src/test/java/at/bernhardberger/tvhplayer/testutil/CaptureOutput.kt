package at.bernhardberger.tvhplayer.testutil

import android.graphics.Bitmap
import java.io.File

/**
 * The one gate for diagnostic PNG output. Routine runs render and assert but encode nothing;
 * capture runs (`-Ptvhplayer.captureTests=all|only`, see `app/build.gradle.kts`) set
 * `tvhplayer.writeMotionCaptures` and get the files.
 */
object CaptureOutput {
    val enabled: Boolean get() = java.lang.Boolean.getBoolean("tvhplayer.writeMotionCaptures")

    /** Writes [bitmap] as PNG to [file], creating its folder, only in a capture run. */
    fun writePng(file: File, bitmap: Bitmap) {
        if (!enabled) return
        file.absoluteFile.parentFile?.mkdirs()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encode failed" } }
    }

    /** Writes a capture's metadata sidecar next to its PNG, only in a capture run. */
    fun writeText(file: File, text: () -> String) {
        if (!enabled) return
        file.absoluteFile.parentFile?.mkdirs()
        file.writeText(text())
    }
}
