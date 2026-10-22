package at.bernhardberger.tvhplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackErrorClassificationTest {
    @Test fun nestedCausesUseDeepestClassAndOnlyItsTopFrame() {
        val root = IllegalStateException("private diagnostic message").apply {
            stackTrace = arrayOf(
                StackTraceElement("example.Decoder", "render", "PrivateSource.kt", 42),
                StackTraceElement("example.Worker", "run", "Other.kt", 99),
            )
        }
        assertEquals(
            "IllegalStateException • Decoder.render:42",
            classifyPlaybackError(RuntimeException("outer message", RuntimeException(root))),
        )
    }

    @Test fun nullErrorAndMissingStackRemainAbsentOrClassOnly() {
        assertNull(classifyPlaybackError(null))
        assertEquals("IllegalArgumentException", classifyPlaybackError(IllegalArgumentException().apply {
            stackTrace = emptyArray()
        }))
    }

    @Test fun noCauseUsesTheExceptionItselfAndPreservesUnknownLine() {
        val error = IllegalStateException().apply {
            stackTrace = arrayOf(StackTraceElement("example.Player", "start", null, -1))
        }
        assertEquals("IllegalStateException • Player.start:-1", classifyPlaybackError(error))
    }

    @Test fun causeCycleStopsBeforeRevisitingAnException() {
        val first = IllegalStateException().apply { stackTrace = emptyArray() }
        val second = IllegalArgumentException().apply { stackTrace = emptyArray() }
        first.initCause(second)
        second.initCause(first)
        assertEquals("IllegalArgumentException", classifyPlaybackError(first))
    }

    @Test fun deepChainStopsAtThirtyTwoExceptions() {
        var error: Throwable = IllegalArgumentException()
        repeat(32) { error = RuntimeException(error).apply { stackTrace = emptyArray() } }
        assertEquals("RuntimeException", classifyPlaybackError(error))
    }

    @Test fun nonSymbolStackMetadataIsNotRetained() {
        val error = IllegalStateException().apply {
            stackTrace = arrayOf(StackTraceElement("invalid/owner", "private message", "PrivateSource.kt", 1))
        }
        assertEquals("IllegalStateException • ?.?:1", classifyPlaybackError(error))
    }

    @Test fun urlShapedOwnerIsRejectedEvenWithASymbolSuffix() {
        val error = IllegalStateException().apply {
            stackTrace = arrayOf(
                StackTraceElement("https://example.invalid/private.secretToken", "run", null, 3),
            )
        }
        assertEquals("IllegalStateException • ?.run:3", classifyPlaybackError(error))
    }

    @Test fun preconditionHelperFramesNameTheirCaller() {
        val error = IllegalStateException().apply {
            stackTrace = arrayOf(
                StackTraceElement("androidx.media3.common.util.Assertions", "checkState", null, 10),
                StackTraceElement("example.Renderer", "render", null, 77),
            )
        }
        assertEquals("IllegalStateException • Renderer.render:77", classifyPlaybackError(error))
    }

    @Test fun onlyPreconditionFramesFallBackToTheTopFrame() {
        val error = IllegalArgumentException().apply {
            stackTrace = arrayOf(
                StackTraceElement("com.google.common.base.Preconditions", "checkArgument", null, 5),
            )
        }
        assertEquals("IllegalArgumentException • Preconditions.checkArgument:5", classifyPlaybackError(error))
    }
}
