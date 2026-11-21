package at.bernhardberger.tvhplayer.touch.ui

import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

/** A failed image's debug line names its kind of source and no more of it than scheme and host. */
class ArtworkFailureLineTest {
    @Test
    fun aFailedImageNamesItsSourceWithoutItsAddressOrIdentity() {
        val error = IOException("GET http://viewer:secret@images.example/art/42.jpg?token=abc failed")
        listOf(
            "http://viewer:secret@images.example:8080/art/42.jpg?token=abc#top" to
                "Image failed to load from remote http://images.example: java.io.IOException",
            "https://images.example/logo.png" to
                "Image failed to load from remote https://images.example: java.io.IOException",
            "not a url at all" to "Image failed to load from remote (unreadable address): java.io.IOException",
            AppArtworkSource(null, ArtworkId(42), savedKey = "saved-key") to
                "Image failed to load from image cache: java.io.IOException",
        ).forEach { (model, line) ->
            assertEquals("$model", line, artworkFailureLine(model, error))
        }
    }
}
