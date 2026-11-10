package at.bernhardberger.tvhplayer.testutil

import android.os.Looper
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf

/** Drains ready Main work without advancing frames, timers, or the wall clock. */
fun assertMainSettled(message: String, condition: () -> Boolean) {
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue(message, condition())
}
