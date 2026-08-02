package at.bernhardberger.tvhplayer.htsp

import java.lang.reflect.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHtspTransportContractTest {
    @Test
    fun playbackTransportExposesIntentOperationsInsteadOfRawHtspRequests() {
        val instanceMethodSignatures = PlaybackHtspTransport::class.java.declaredMethods
            .filterNot { method -> Modifier.isStatic(method.modifiers) }
            .mapTo(mutableSetOf()) { method ->
                buildString {
                    append(method.name)
                    append('(')
                    append(method.parameterTypes.joinToString(",") { type -> type.name })
                    append("):")
                    append(method.returnType.name)
                }
            }

        assertEquals(
            "Playback transport additions must be reviewed as public SDK surface",
            setOf(
                "getState():kotlinx.coroutines.flow.StateFlow",
                "getControlEvents():kotlinx.coroutines.flow.Flow",
                "getMuxEvents():kotlinx.coroutines.flow.Flow",
                "startSubscription(long,int,int,int,java.lang.String,kotlin.coroutines.Continuation):java.lang.Object",
                "stopSubscription(long,int,kotlin.coroutines.Continuation):java.lang.Object",
                "setSubscriptionSpeed(long,int,int,kotlin.coroutines.Continuation):java.lang.Object",
                "seekSubscription(long,int,long,boolean,kotlin.coroutines.Continuation):java.lang.Object",
                "fileOpen(java.lang.String,long,java.lang.Long,kotlin.coroutines.Continuation):java.lang.Object",
                "fileRead(int,int,long,java.lang.Long,kotlin.coroutines.Continuation):java.lang.Object",
                "fileSeek(int,long,java.lang.String,long,java.lang.Long,kotlin.coroutines.Continuation):java.lang.Object",
                "fileCloseRecording(int,java.lang.Integer,long,java.lang.Long,kotlin.coroutines.Continuation):java.lang.Object",
                "currentConnectionAttemptId():long",
                "currentMuxSequenceForConnectionAttempt(long):java.lang.Long",
                "isCurrentConnectionAttemptId(long):boolean",
                "connectionAttemptStatus(long):at.bernhardberger.tvhplayer.htsp.HtspConnectionAttemptStatus",
                "commitIfCurrentConnectionAttempt(long,kotlin.jvm.functions.Function0):java.lang.Object",
                "commitIfLiveConnectionAttempt(long,kotlin.jvm.functions.Function0):java.lang.Object",
            ),
            instanceMethodSignatures,
        )
    }

    @Test
    fun playbackIntentOperationsPreserveProtocolArguments() = runTest {
        val service = RecordingHtspService()

        val started = service.startSubscription(
            expectedConnectionAttemptId = 7L,
            subscriptionId = 11,
            channelId = 42,
            timeshiftPeriodSec = 7_200,
            profile = "pass",
        )
        service.setSubscriptionSpeed(
            expectedConnectionAttemptId = 7L,
            subscriptionId = 11,
            speed = 0,
        )
        service.seekSubscription(
            expectedConnectionAttemptId = 7L,
            subscriptionId = 11,
            timeUs = 9_000_000L,
            absolute = true,
        )
        service.stopSubscription(
            expectedConnectionAttemptId = 7L,
            subscriptionId = 11,
        )

        assertEquals(3_600, started.availableTimeshiftPeriodSec)
        assertEquals(
            listOf("subscribe", "subscriptionSpeed", "subscriptionSeek", "unsubscribe"),
            service.calls.map(RequestCall::method),
        )
        assertTrue(service.calls.all { call -> call.attemptId == 7L })
        assertEquals(
            mapOf(
                "subscriptionId" to 11,
                "channelId" to 42,
                "timeshiftPeriod" to 7_200,
                "profile" to "pass",
            ),
            service.calls[0].fields,
        )
        assertEquals(
            mapOf("subscriptionId" to 11, "speed" to 0),
            service.calls[1].fields,
        )
        assertEquals(
            mapOf<String, Any?>(
                "subscriptionId" to 11,
                "time" to 9_000_000L,
                "absolute" to 1,
            ),
            service.calls[2].fields,
        )
        assertEquals(
            mapOf("subscriptionId" to 11),
            service.calls[3].fields,
        )
    }

    private data class RequestCall(
        val attemptId: Long,
        val method: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingHtspService : HtspService(Dispatchers.Unconfined) {
        val calls = mutableListOf<RequestCall>()

        override suspend fun requestForConnectionAttempt(
            expectedConnectionAttemptId: Long,
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage {
            assertEquals(5_000L, timeoutMs)
            assertTrue(flush)
            assertTrue(disconnectOnTimeout)
            calls += RequestCall(expectedConnectionAttemptId, method, fields)
            return HtspMessage(
                method = null,
                seq = null,
                fields = if (method == "subscribe") {
                    mapOf("timeshiftPeriod" to 3_600)
                } else {
                    emptyMap()
                },
            )
        }
    }
}
