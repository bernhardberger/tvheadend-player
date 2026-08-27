package at.bernhardberger.tvhplayer.acceptance

import android.graphics.SurfaceTexture
import android.view.Surface

internal class AcceptancePlaybackSurface(
    private val surfaceTexture: SurfaceTexture,
    val surface: Surface,
) {
    fun close() {
        surface.release()
        surfaceTexture.release()
    }
}

internal fun createAcceptancePlaybackSurface(): AcceptancePlaybackSurface {
    val surfaceTexture = SurfaceTexture(false).apply {
        setDefaultBufferSize(1920, 1080)
    }
    return try {
        AcceptancePlaybackSurface(
            surfaceTexture = surfaceTexture,
            surface = Surface(surfaceTexture),
        )
    } catch (failure: Throwable) {
        surfaceTexture.release()
        throw failure
    }
}
