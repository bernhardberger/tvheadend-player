package at.bernhardberger.tvhplayer.htsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DvrProgressProtocolTest {
    @Test
    fun capabilityRequiresVersionTwentySevenAndRespectsRecorderAccess() {
        val rights = listOf(false, true, null)

        listOf<Int?>(null, 26).forEach { version ->
            rights.forEach { dvrAccess ->
                assertEquals(
                    "version=$version dvrAccess=$dvrAccess",
                    RecordingProgressCapability.Unsupported,
                    recordingProgressCapability(connected(version, dvrAccess)),
                )
            }
        }

        listOf(27, 43).forEach { version ->
            assertEquals(
                RecordingProgressCapability.ReadOnly,
                recordingProgressCapability(connected(version, dvrAccess = false)),
            )
            listOf(true, null).forEach { dvrAccess ->
                assertEquals(
                    "version=$version dvrAccess=$dvrAccess",
                    RecordingProgressCapability.Full,
                    recordingProgressCapability(connected(version, dvrAccess)),
                )
            }
        }
    }

    @Test
    fun everyNonConnectedStateIsDisconnected() {
        listOf(
            ConnectionState.Disconnected,
            ConnectionState.Connecting("host", 9982),
            ConnectionState.Error(IllegalStateException("offline")),
        ).forEach { state ->
            assertEquals(
                RecordingProgressCapability.Disconnected,
                recordingProgressCapability(state),
            )
        }
    }

    @Test
    fun ordinaryRequestUsesExactMethodFieldsAndKeepSentinel() {
        assertEquals(
            RecordingProgressRequest(
                method = DVR_PROGRESS_UPDATE_METHOD,
                fields = mapOf(
                    "id" to 42,
                    "playposition" to 901L,
                    "playcount" to DVR_PLAY_COUNT_KEEP,
                ),
            ),
            recordingProgressRequest(
                entryId = 42,
                playPositionSeconds = 901L,
                setWatched = false,
            ),
        )
    }

    @Test
    fun watchedRequestSendsZeroPositionAndPlayCountOneTogether() {
        assertEquals(
            RecordingProgressRequest(
                method = "updateDvrEntry",
                fields = mapOf<String, Any?>(
                    "id" to 42,
                    "playposition" to 0L,
                    "playcount" to 1,
                ),
            ),
            recordingProgressRequest(
                entryId = 42,
                playPositionSeconds = 0L,
                setWatched = true,
            ),
        )
    }

    @Test
    fun negativeIdOrPositionIsRejected() {
        assertNull(recordingProgressRequest(-1, 0L, setWatched = false))
        assertNull(recordingProgressRequest(0, -1L, setWatched = false))
    }

    @Test
    fun zeroIdAndPositionAreAccepted() {
        assertEquals(
            mapOf(
                "id" to 0,
                "playposition" to 0L,
                "playcount" to DVR_PLAY_COUNT_KEEP,
            ),
            recordingProgressRequest(0, 0L, setWatched = false)?.fields,
        )
    }

    @Test
    fun arbitrarilyLargeNonNegativePositionIsAccepted() {
        assertEquals(
            Long.MAX_VALUE,
            recordingProgressRequest(1, Long.MAX_VALUE, setWatched = false)
                ?.fields
                ?.get("playposition"),
        )
    }

    @Test
    fun successTakesPrecedenceOverEveryFailureField() {
        assertEquals(
            RecordingProgressUpdateResult.Accepted,
            recordingProgressReplyResult(
                reply(
                    "success" to 1,
                    "noaccess" to 1,
                    "connlimit" to 1,
                    "error" to "METHOD NOT FOUND: permission denied",
                ),
            ),
        )
    }

    @Test
    fun bareNoAccessIsPermissionDenied() {
        assertEquals(
            RecordingProgressUpdateResult.PermissionDenied,
            recordingProgressReplyResult(reply("noaccess" to 1)),
        )
    }

    @Test
    fun noAccessWithConnectionLimitIsRejected() {
        assertEquals(
            RecordingProgressUpdateResult.Rejected,
            recordingProgressReplyResult(reply("noaccess" to 1, "connlimit" to 1)),
        )
    }

    @Test
    fun unsupportedErrorVariantsAreCaseInsensitive() {
        listOf(
            "METHOD NOT FOUND",
            "Unknown Method: updateDvrEntry",
        ).forEach { error ->
            assertEquals(
                error,
                RecordingProgressUpdateResult.Unsupported,
                recordingProgressReplyResult(reply("error" to error)),
            )
        }
    }

    @Test
    fun permissionErrorVariantsAreCaseInsensitive() {
        listOf(
            "PERMISSION denied",
            "Access Denied",
            "Operation is NOT ALLOWED",
        ).forEach { error ->
            assertEquals(
                error,
                RecordingProgressUpdateResult.PermissionDenied,
                recordingProgressReplyResult(reply("error" to error)),
            )
        }
    }

    @Test
    fun arbitraryMissingAndMalformedFailuresAreRejected() {
        listOf(
            reply("error" to "entry is locked"),
            reply(),
            reply("success" to "yes", "noaccess" to emptyList<Any>(), "error" to 7),
        ).forEach { message ->
            assertEquals(
                RecordingProgressUpdateResult.Rejected,
                recordingProgressReplyResult(message),
            )
        }
    }

    private fun connected(version: Int?, dvrAccess: Boolean?) =
        ConnectionState.Connected("host", 9982, version, dvrAccess)

    private fun reply(vararg fields: Pair<String, Any?>) =
        HtspMessage(method = null, seq = 1, fields = mapOf(*fields))
}
