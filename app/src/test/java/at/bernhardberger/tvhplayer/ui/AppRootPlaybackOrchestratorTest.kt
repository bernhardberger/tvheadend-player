package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppRootPlaybackOrchestratorTest {
    @Test
    fun currentLiveChannelNavigatesWithoutRetuningAndRearmsWarmReturn() = runTest {
        val orchestrator = AppRootPlaybackOrchestrator()
        orchestrator.activePlaybackChanged(
            activeChannelId = ChannelId(22),
            activeRecordingId = null,
        )
        orchestrator.consumeWarmPlayerTarget(
            activeChannelId = ChannelId(22),
            activeRecordingId = null,
            currentChannelReadiness = CurrentChannelReadiness.Waiting,
        )
        var playRequests = 0

        val target = orchestrator.requestLivePlayer(
            activeChannelId = ChannelId(22),
            activeRecordingId = null,
            requestedChannelId = ChannelId(22),
            requestedChannelName = "News HD",
            startPlayback = { playRequests += 1 },
        )

        assertEquals(PlayerRouteTarget.Live(ChannelId(22), "News HD"), target)
        assertEquals(0, playRequests)
        assertTrue(orchestrator.warmReturn.canReturn)
    }

    @Test
    fun supersededLiveCompletionCannotNavigateAfterTheNewerRequest() = runTest {
        val orchestrator = AppRootPlaybackOrchestrator()
        val firstStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()

        val first = async {
            orchestrator.requestLivePlayer(
                activeChannelId = null,
                activeRecordingId = null,
                requestedChannelId = ChannelId(11),
                requestedChannelName = "First",
                startPlayback = {
                    firstStarted.complete(Unit)
                    finishFirst.await()
                },
            )
        }
        firstStarted.await()
        val second = async {
            orchestrator.requestLivePlayer(
                activeChannelId = null,
                activeRecordingId = null,
                requestedChannelId = ChannelId(22),
                requestedChannelName = "Second",
                startPlayback = {
                    secondStarted.complete(Unit)
                    finishSecond.await()
                },
            )
        }
        secondStarted.await()

        finishSecond.complete(Unit)
        assertEquals(PlayerRouteTarget.Live(ChannelId(22), "Second"), second.await())
        finishFirst.complete(Unit)
        assertNull(first.await())
    }

    @Test
    fun recordingSelectionSupersedesAnInFlightLiveRequest() = runTest {
        val orchestrator = AppRootPlaybackOrchestrator()
        val liveStarted = CompletableDeferred<Unit>()
        val finishLive = CompletableDeferred<Unit>()
        val recordingStarted = CompletableDeferred<Unit>()
        val finishRecording = CompletableDeferred<Unit>()

        val live = async {
            orchestrator.requestLivePlayer(
                activeChannelId = null,
                activeRecordingId = null,
                requestedChannelId = ChannelId(11),
                requestedChannelName = "Live",
                startPlayback = {
                    liveStarted.complete(Unit)
                    finishLive.await()
                },
            )
        }
        liveStarted.await()
        val recording = async {
            orchestrator.requestRecordingPlayer(
                activeChannelId = null,
                activeRecordingId = null,
                requestedRecordingId = DvrEntryId(7),
                startPlayback = {
                    recordingStarted.complete(Unit)
                    finishRecording.await()
                },
            )
        }
        recordingStarted.await()

        finishRecording.complete(Unit)
        assertEquals(PlayerRouteTarget.Recording(DvrEntryId(7)), recording.await())
        finishLive.complete(Unit)
        assertNull(live.await())
    }

    @Test
    fun liveSelectionSupersedesAnInFlightRecordingRequest() = runTest {
        val orchestrator = AppRootPlaybackOrchestrator()
        val recordingStarted = CompletableDeferred<Unit>()
        val finishRecording = CompletableDeferred<Unit>()
        val liveStarted = CompletableDeferred<Unit>()
        val finishLive = CompletableDeferred<Unit>()

        val recording = async {
            orchestrator.requestRecordingPlayer(
                activeChannelId = null,
                activeRecordingId = null,
                requestedRecordingId = DvrEntryId(7),
                startPlayback = {
                    recordingStarted.complete(Unit)
                    finishRecording.await()
                },
            )
        }
        recordingStarted.await()
        val live = async {
            orchestrator.requestLivePlayer(
                activeChannelId = null,
                activeRecordingId = null,
                requestedChannelId = ChannelId(22),
                requestedChannelName = "Live",
                startPlayback = {
                    liveStarted.complete(Unit)
                    finishLive.await()
                },
            )
        }
        liveStarted.await()

        finishLive.complete(Unit)
        assertEquals(PlayerRouteTarget.Live(ChannelId(22), "Live"), live.await())
        finishRecording.complete(Unit)
        assertNull(recording.await())
    }

    @Test
    fun newerRecordingSelectionSupersedesAnInFlightRecordingRequest() = runTest {
        val orchestrator = AppRootPlaybackOrchestrator()
        val firstStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()

        val first = async {
            orchestrator.requestRecordingPlayer(
                activeChannelId = null,
                activeRecordingId = null,
                requestedRecordingId = DvrEntryId(7),
                startPlayback = {
                    firstStarted.complete(Unit)
                    finishFirst.await()
                },
            )
        }
        firstStarted.await()
        val second = async {
            orchestrator.requestRecordingPlayer(
                activeChannelId = null,
                activeRecordingId = null,
                requestedRecordingId = DvrEntryId(8),
                startPlayback = {
                    secondStarted.complete(Unit)
                    finishSecond.await()
                },
            )
        }
        secondStarted.await()

        finishSecond.complete(Unit)
        assertEquals(PlayerRouteTarget.Recording(DvrEntryId(8)), second.await())
        finishFirst.complete(Unit)
        assertNull(first.await())
    }

    @Test
    fun warmReturnTargetIsConsumedBeforeNavigationAndUsesCurrentMetadata() {
        val orchestrator = AppRootPlaybackOrchestrator()
        orchestrator.activePlaybackChanged(
            activeChannelId = ChannelId(22),
            activeRecordingId = null,
        )

        assertEquals(
            PlayerRouteTarget.Live(ChannelId(22), "News HD"),
            orchestrator.consumeWarmPlayerTarget(
                activeChannelId = ChannelId(22),
                activeRecordingId = null,
                currentChannelReadiness = CurrentChannelReadiness.Ready(
                    listOf(Channel.create(ChannelId(22), name = "News HD")),
                ),
            ),
        )
        assertFalse(orchestrator.warmReturn.canReturn)
        assertNull(
            orchestrator.consumeWarmPlayerTarget(
                activeChannelId = ChannelId(22),
                activeRecordingId = null,
                currentChannelReadiness = CurrentChannelReadiness.Waiting,
            ),
        )
    }





    @Test
    fun recordingWarmReturnCanBeRearmedByDeliberateBrowseNavigation() {
        val orchestrator = AppRootPlaybackOrchestrator()
        orchestrator.activePlaybackChanged(
            activeChannelId = null,
            activeRecordingId = DvrEntryId(7),
        )
        orchestrator.consumeWarmPlayerTarget(
            activeChannelId = null,
            activeRecordingId = DvrEntryId(7),
            currentChannelReadiness = CurrentChannelReadiness.Waiting,
        )

        orchestrator.browseNavigationSelected(
            activeChannelId = null,
            activeRecordingId = DvrEntryId(7),
        )

        assertTrue(orchestrator.warmReturn.canReturn)
        assertEquals(
            PlayerRouteTarget.Recording(DvrEntryId(7)),
            orchestrator.consumeWarmPlayerTarget(
                activeChannelId = null,
                activeRecordingId = DvrEntryId(7),
                currentChannelReadiness = CurrentChannelReadiness.Waiting,
            ),
        )
    }

    @Test
    fun clearingStoppedTargetRemovesWarmReturnForLiveAndRecording() {
        listOf(true, false).forEach { live ->
            val orchestrator = AppRootPlaybackOrchestrator()
            orchestrator.activePlaybackChanged(
                activeChannelId = ChannelId(22).takeIf { live },
                activeRecordingId = DvrEntryId(7).takeUnless { live },
            )
            assertTrue(orchestrator.warmReturn.canReturn)
            orchestrator.activePlaybackChanged(null, null)
            assertFalse(orchestrator.warmReturn.canReturn)
            assertNull(orchestrator.consumeWarmPlayerTarget(
                activeChannelId = null,
                activeRecordingId = null,
                currentChannelReadiness = CurrentChannelReadiness.Waiting,
            ))
        }
    }
}
