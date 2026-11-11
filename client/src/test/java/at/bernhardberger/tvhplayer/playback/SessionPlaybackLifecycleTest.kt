@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.Player
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
internal class SessionPlaybackLifecycleTest : SessionPlaybackFixture() {
    @Test fun recordingOpenedOverLiveStopsTheLiveTargetBeforeItsSource() = exercise {
        live(false)
        val stops = collectSessionStops()
        val states = collectStates()
        playerLog.clear()
        recording()
        assertEquals("stop", playerLog.first())
        assertEquals("items=1", playerLog.last())
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
        // Not the viewer's stop: the screen stays open and Idle never shows between the targets.
        assertEquals(emptyList<Unit>(), stops)
        assertFalse(states.toString(), AppPlaybackState.Idle in states)
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
    }

    @Test fun channelStartedOverARecordingStopsTheRecordingBeforeItsSource() = exercise {
        recording()
        val stops = collectSessionStops()
        val states = collectStates()
        playerLog.clear()
        live(false)
        assertEquals("stop", playerLog.first())
        assertEquals("items=1", playerLog.last())
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(null, runtime.recordingSelection.value)
        assertEquals(emptyList<Unit>(), stops)
        assertFalse(states.toString(), AppPlaybackState.Idle in states)
    }

    @Test fun targetsOfTheSameKindReplaceEachOtherWithoutAStop() = exercise {
        val initial = session.observation.value
        session.publish(SessionObservation.create(sessionState = initial.sessionState,
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(
                Channel.create(ChannelId(1)), Channel.create(ChannelId(2))))),
            epgState = initial.epgState,
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(
                DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED),
                DvrEntry.create(DvrEntryId(2), state = DvrEntryState.COMPLETED))))))
        live(false)
        playerLog.clear()
        answerNextSubscribe(2)
        assertTrue(runtime.playLive(requireNotNull(currentLivePlaybackSelection(
            session.observation.value, ChannelId(2))))?.isStarted == true)
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(playerLog.toString(), "stop" in playerLog)
        recording()
        playerLog.clear()
        assertTrue(runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(
            session.observation.value, DvrEntryId(2))), RecordingPlaybackStart.START_OVER)?.isStarted == true)
        settle()
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(2)), runtime.activeTarget.value)
        assertFalse(playerLog.toString(), "stop" in playerLog)
    }

    @Test fun aChannelThatFailsAfterTheRecordingStoppedLeavesNoTargetBehind() = exercise {
        val initial = session.observation.value
        val requested = liveSelection()
        for (noSession in listOf(false, true)) {
            session.publish(if (noSession) SessionObservation.create() else SessionObservation.create(
                sessionState = initial.sessionState,
                channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList())),
                epgState = initial.epgState,
                dvrState = initial.dvrState,
            ))
            val unavailable = runtime.playLive(requested)
            val expected = if (noSession) PlaybackTargetResult.NOT_READY else PlaybackTargetResult.TARGET_UNAVAILABLE
            assertEquals(expected, unavailable)
            assertTrue(runtime.state.value is AppPlaybackState.Failed)
            val failure = runtime.state.value as AppPlaybackState.Failed
            assertEquals(expected, failure.targetResult)
            assertEquals(AppPlaybackFailureReason.OTHER, failure.reason)
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), failure.target)
            assertNull(runtime.activeTarget.value)
            runtime.stop()
        }
        session.publish(initial)
        recording()
        val stops = collectSessionStops()
        playerLog.clear()
        // The SDK binds the channel only after the recording stopped.
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val result = runtime.playLive(liveSelection())
        settle()
        assertEquals(PlaybackTargetResult.TARGET_UNAVAILABLE, result)
        assertEquals("stop", playerLog.first())
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(null, runtime.recordingSelection.value)
        assertTrue(runtime.state.value.toString(), runtime.state.value is AppPlaybackState.Failed)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), (runtime.state.value as AppPlaybackState.Failed).target)
        assertFalse(runtime.videoPresentation.value.visible)
        assertEquals(emptyList<Unit>(), stops)
        // Real install failure retires the target epoch; Stop must still clear the runtime failure.
        await { wrapper.playerError != null }
        assertNull(wrapper.currentMediaItem)
        assertEquals(labels.error(SessionPlaybackErrorKind.CHANNEL_UNAVAILABLE), wrapper.playerError?.message)
        wrapper.stop()
        await { runtime.state.value == AppPlaybackState.Idle && wrapper.playerError == null }
        assertNull(wrapper.currentMediaItem)
        assertEquals(listOf(Unit), stops)
    }

    @Test fun aRecordingThatCannotBindLeavesTheChannelPlaying() = exercise {
        live(false)
        val picture = runtime.videoPresentation.value
        playerLog.clear()
        session.scriptRecordingPlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val result = runtime.playRecording(
            requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
            RecordingPlaybackStart.START_OVER,
        )
        settle()
        assertFalse(result.toString(), result?.isStarted == true)
        assertFalse(playerLog.toString(), "stop" in playerLog)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(picture, runtime.videoPresentation.value)
        assertEquals(0, connection.unsubscribeCount)
    }

    /*
     * A start cancelled after it stopped the other kind settles as that stop would have: no
     * target, Idle, no source left in the player, and not the viewer's stop.
     */

    @Test fun aRecordingCancelledWhileTheChannelStopsLeavesNothingBehind() = exercise {
        live(false)
        val stops = collectSessionStops()
        val states = collectStates()
        val start = recordingStart()
        onPlayerLog = { if (it == "stop") start.cancel() }
        start.start()
        await { start.isCompleted }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertCancelledStartSettled(states, stops)
    }

    @Test fun aRecordingCancelledWhileItsSourceIsSetLeavesNothingBehind() = exercise {
        live(false)
        val stops = collectSessionStops()
        val states = collectStates()
        val start = recordingStart()
        onPlayerLog = { if (it == "items=1") start.cancel() }
        start.start()
        await { start.isCompleted }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertCancelledStartSettled(states, stops)
    }

    @Test fun aRecordingCancelledWithNothingPlayingLeavesNothingBehind() = exercise {
        val states = collectStates()
        val start = recordingStart()
        onPlayerLog = { if (it == "items=1") start.cancel() }
        start.start()
        await { start.isCompleted }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        assertFalse(states.toString(), states.any { it is AppPlaybackState.Failed })
        assertEquals(playerLog.toString(), 0, player.mediaItemCount)
    }

    @Test fun aChannelCancelledWhileItsSourceIsSetOverARecordingLeavesNothingBehind() = exercise {
        recording()
        val stops = collectSessionStops()
        val states = collectStates()
        answerNextSubscribe(1)
        val start = scope.async(start = CoroutineStart.LAZY) { runtime.playLive(liveSelection()) }
        onPlayerLog = { if (it == "items=1") start.cancel() }
        start.start()
        await { start.isCompleted }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertCancelledStartSettled(states, stops)
    }

    @Test fun aNewerStartCancelledBeforeItsTurnSettlesTheRecordingCancelledForIt() = exercise {
        live(false)
        val stops = collectSessionStops()
        val states = collectStates()
        val start = recordingStart()
        var newer: Deferred<PlaybackTargetResult?>? = null
        onPlayerLog = { entry ->
            if (entry == "items=1" && newer == null) {
                // A newer channel queues behind this start; both are cancelled before it runs.
                val intent = runtime.notePlaybackIntent()
                newer = scope.async(start = CoroutineStart.UNDISPATCHED) { runtime.playLive(liveSelection(), intent) }
                newer?.cancel()
                start.cancel()
            }
        }
        start.start()
        await { start.isCompleted && newer?.isCompleted == true }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertTrue(requireNotNull(newer).isCancelled)
        assertCancelledStartSettled(states, stops)
    }

    @Test fun aRecordingStartCancelledForANewerChannelShowsNoFailure() = exercise {
        live(false)
        playerLog.clear()
        val states = collectStates()
        val start = recordingStart()
        val newer = java.util.concurrent.atomic.AtomicReference<Deferred<PlaybackTargetResult?>>()
        onPlayerLog = { entry ->
            if (entry == "items=1" && newer.get() == null) {
                // The viewer chose a channel meanwhile; its start queues behind this one.
                val intent = runtime.notePlaybackIntent()
                answerNextSubscribe(2)
                newer.set(scope.async { runtime.playLive(liveSelection(), intent) })
                start.cancel()
            }
        }
        start.start()
        await { start.isCompleted && newer.get()?.isCompleted == true }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertTrue(newer.get().await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(states.toString(), states.any { it is AppPlaybackState.Failed || it == AppPlaybackState.Idle })
        assertEquals(1, player.mediaItemCount)
        // The recording's source is stopped before the channel's, so the channel gets fresh decoders.
        val recordingSource = playerLog.indexOf("items=1")
        val channelSource = playerLog.lastIndexOf("items=1")
        assertTrue(playerLog.toString(), recordingSource < channelSource)
        assertTrue(playerLog.toString(), "stop" in playerLog.subList(recordingSource, channelSource))
        assertTrue(player.playWhenReady)
    }

    @Test fun sessionStopTearsDownLiveWithoutTimeshiftAndAnnouncesOnce() = exercise {
        live(false)
        val stops = collectSessionStops()
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_STOP))
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 1 }
        settle()
        assertEquals(1, stops.size)
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
    }

    @Test fun playbackRequestedAfterAQueuedStopWinsAndTheScreenStaysOpen() = exercise {
        live(false)
        val stops = collectSessionStops()
        lateinit var install: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            // A later request (for example CH+ or a picked recording) queues behind the Stop.
            install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value,
                    DvrEntryId(1))), RecordingPlaybackStart.START_OVER)
            }
            settle()
        }
        await { install.isCompleted }
        settle()
        // The queued Stop lost to the newer request: the newer target replaces A and the
        // showing player is not told to close.
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
        assertTrue(install.await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun channelAcceptedBeforeAQueuedStopRunsCancelsTheStop() = exercise {
        live(false)
        val stops = collectSessionStops()
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            // CH+ accepted by the live screen; its playLive waits for the zap to settle.
            runtime.notePlaybackIntent()
        }
        settle()
        assertEquals(0, connection.unsubscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun intentNotedAfterTheStopRanButBeforeTheScreenCollectsKeepsTheScreenOpen() = exercise {
        live(false)
        val screen = StandardTestDispatcher()
        val stops = mutableListOf<Unit>()
        scope.launch(screen, start = CoroutineStart.UNDISPATCHED) { runtime.sessionStops.collect { stops += it } }
        wrapper.stop()
        await { runtime.activeTarget.value == null }
        settle()
        // The screen has not taken the event yet when the viewer tunes (zap settle window).
        runtime.notePlaybackIntent()
        screen.scheduler.runCurrent()
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
        assertEquals(emptyList<Unit>(), stops)
    }

    /*
     * Warm opening actions start nothing, so each notes viewing intent itself (AppRoot: a click
     * on the already-playing channel, a warm return). Only the order of the opening action and
     * the session Stop decides; the screen's entry follows the opening action either way.
     */

    @Test fun warmClickAfterAQueuedStopKeepsThePlayerItOpensPlaying() = exercise {
        live(false)
        warmOpeningAfterAQueuedStopKeepsThePlayerOpen(AppPlaybackTarget.Live(ChannelId(1)))
    }

    @Test fun warmReturnAfterAQueuedStopKeepsTheRecordingPlaying() = exercise {
        recording()
        warmOpeningAfterAQueuedStopKeepsThePlayerOpen(AppPlaybackTarget.Recording(DvrEntryId(1)))
    }

    @Test fun warmClickBeforeAQueuedStopClosesWithTheStop() = exercise {
        live(false)
        warmOpeningBeforeAQueuedStopClosesWithIt()
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
    }

    @Test fun warmPlayerEnteredWhileAStopIsQueuedClosesWithTheStop() = exercise {
        recording() // a warm return to the recording's player, then the Stop
        warmOpeningBeforeAQueuedStopClosesWithIt()
    }

    @Test fun channelStartedFromBrowseWithAStopQueuedBehindItStopsAndItsPlayerCloses() = exercise {
        recording() // warm recording while browsing
        answerNextSubscribe(1)
        lateinit var start: Deferred<PlaybackTargetResult?>
        lateinit var stops: List<Unit>
        var entry = 0L
        whileCommandsBlocked {
            start = scope.async { runtime.playLive(liveSelection()) } // the click, in flight
            settle()
            wrapper.stop()
            settle()
            entry = requireNotNull(runtime.enterPlayerScreen()) // its player composes first
            stops = collectSessionStops()
        }
        await { start.isCompleted }
        settle()
        assertTrue(start.await()?.isStarted == true)
        // The SDK may not have sent the subscribe yet when the Stop ends the target; either
        // way no subscription outlives it (the SDK unsubscribes asynchronously).
        await { connection.subscribeCount == connection.unsubscribeCount }
        settle()
        assertEquals(connection.subscribeCount, connection.unsubscribeCount)
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(1, stops.size)
        assertTrue(runtime.isPlaybackIntentStopped(entry))
    }

    @Test fun recordingStartedWithAStopQueuedBehindItStopsAndItsPlayerRestoresNothing() = exercise {
        live(false)
        lateinit var install: Deferred<PlaybackTargetResult?>
        lateinit var restore: Deferred<PlaybackTargetResult?>
        lateinit var stops: List<Unit>
        whileCommandsBlocked {
            install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value,
                    DvrEntryId(1))), RecordingPlaybackStart.START_OVER)
            }
            settle()
            wrapper.stop()
            settle()
            assertNotNull(runtime.enterPlayerScreen())
            stops = collectSessionStops()
            restore = scope.async { restoreRecording() } // the recording screen's restore
            settle()
        }
        await { install.isCompleted && restore.isCompleted }
        settle()
        assertTrue(install.await()?.isStarted == true)
        assertEquals(null, restore.await())
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        assertEquals(1, stops.size)
    }

    @Test fun queuedStopThatIsSkippedLeavesTheEnteredPlayerPlaying() = exercise {
        live(false)
        var entry = 0L
        whileCommandsBlocked {
            runtime.stopFromSession { false } // the session detached before the Stop ran
            settle()
            entry = requireNotNull(runtime.enterPlayerScreen())
        }
        settle()
        val stops = collectSessionStops()
        settle()
        assertEquals(0, connection.unsubscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(runtime.isPlaybackIntentStopped(entry))
        assertEquals(emptyList<Unit>(), stops)
        // The registration ended with the skipped Stop: the next entry notes intent again.
        assertTrue(requireNotNull(runtime.enterPlayerScreen()) > entry)
        whileCommandsBlocked {
            val cancelled = runtime.stopFromSession { true }
            settle()
            entry = requireNotNull(runtime.enterPlayerScreen())
            cancelled.cancel()
            cancelled.join()
        }
        settle()
        assertEquals(0, connection.unsubscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(runtime.isPlaybackIntentStopped(entry))
        assertEquals(emptyList<Unit>(), stops)
        assertTrue(requireNotNull(runtime.enterPlayerScreen()) > entry)
    }

    @Test fun secondStopPendingUnderTheSameGenerationStillHoldsTheEntry() = exercise {
        live(false)
        val stops = collectSessionStops()
        lateinit var probe: Deferred<Long?>
        whileCommandsBlocked {
            runtime.stopFromSession { false } // skipped when it runs
            probe = scope.async { commands.serialize(onClosed = { null }) { runtime.enterPlayerScreen() } }
            runtime.stopFromSession { true } // still pending when the probe enters
            settle()
        }
        await { probe.isCompleted }
        settle()
        assertTrue(runtime.isPlaybackIntentStopped(requireNotNull(probe.await())))
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(1, stops.size)
    }

    @Test fun zapAfterAPendingStopSkipsTheStopAndPlays() = exercise {
        live(false)
        val stops = collectSessionStops()
        var zapIntent = 0L
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            assertNotNull(runtime.enterPlayerScreen())
            zapIntent = runtime.notePlaybackIntent() // CH+ after the Stop
        }
        settle()
        assertEquals(0, connection.unsubscribeCount)
        answerNextSubscribe(2)
        val zap = runtime.playLive(liveSelection(), zapIntent)
        assertTrue(zap?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun screenCollectingAfterItsPendingStopRanStillCloses() = exercise {
        live(false)
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            assertNotNull(runtime.enterPlayerScreen())
        }
        settle()
        assertEquals(null, runtime.activeTarget.value)
        // The screen's CloseOnSessionStop subscribes only now.
        assertEquals(1, collectSessionStops().size)
    }

    @Test fun stopPressedWhileTheAcceptedChannelStartsStopsIt() = exercise {
        live(false)
        val stops = collectSessionStops()
        val intent = runtime.notePlaybackIntent() // the live screen accepts the channel
        answerNextSubscribe(2)
        lateinit var zap: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            zap = scope.async { runtime.playLive(liveSelection(), intent) } // the settled zap
            settle()
            wrapper.stop() // arrives while the old target is still the active one
            settle()
        }
        await { zap.isCompleted }
        settle()
        assertTrue(zap.await()?.isStarted == true)
        // Whether the SDK resubscribes the same channel is timing-dependent; either way the
        // target the zap left behind is stopped.
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(1, stops.size)
        assertTrue(runtime.isPlaybackIntentStopped(intent))
    }

    @Test fun recordingRestoreQueuedBehindASessionStopDoesNotResurrectTheRecording() = exercise {
        recording()
        val stops = collectSessionStops()
        lateinit var restore: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            restore = scope.async { restoreRecording() }
            settle()
        }
        await { restore.isCompleted }
        settle()
        assertEquals(null, restore.await())
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        assertEquals(1, stops.size)
    }

    @Test fun recordingRestoreAfterTheStopButtonDoesNotResurrectTheRecording() = exercise {
        recording()
        runtime.stop()
        assertEquals(null, restoreRecording())
        settle()
        assertEquals(null, runtime.activeTarget.value)
    }

    @Test fun recordingRestoreWithoutAnExplicitStopSinceTheLastIntentRestores() = exercise {
        // Process restore: nothing plays yet and nobody stopped.
        assertTrue(restoreRecording()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
        // Stopped, then the viewer asked for playback again: a later restore works again.
        runtime.stop()
        runtime.notePlaybackIntent()
        assertTrue(restoreRecording()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
    }

    @Test fun stopQueuedAfterASelectionWithdrawsItsDelayedStartWithoutAFailure() =
        listOf(false to false, true to false, true to true).forEach { (retireBeforeStop, newerClick) ->
            exercise {
                live(false)
                val stops = collectSessionStops()
                var intent = -1L
                var clickIntent: Long? = null
                var clickedStart: Deferred<PlaybackTargetResult?>? = null
                lateinit var zap: Deferred<PlaybackTargetResult?>
                whileCommandsBlocked {
                    // The live screen accepts a channel (one intent); its start waits for the zap to settle.
                    intent = runtime.notePlaybackIntent()
                    runtime.noteLiveSelection(intent)
                    // An explicit channel click notes intent before joining the older browse start,
                    // without registering another live selection until its own start runs.
                    if (newerClick) clickIntent = runtime.notePlaybackIntent()
                    settle()
                    wrapper.stop() // the viewer's session Stop, after the selection
                    settle()
                    // Cancellation of the old fresh-decoder install can retire its target before
                    // the queued Stop acquires the serializer, even if a newer click hid the selection.
                    if (retireBeforeStop) retireWithState(AppPlaybackState.Starting)
                    // The zap settles: the delayed start carries the selection's intent and mints none.
                    zap = scope.async { runtime.playLive(liveSelection(), intent) }
                    clickedStart = clickIntent?.let { current ->
                        scope.async {
                            zap.await() // Explicit entry joins the admitted browse start first.
                            runtime.playLive(liveSelection(), current)
                        }
                    }
                    settle()
                }
                await { zap.isCompleted }
                clickedStart?.let { await { it.isCompleted } }
                settle()
                // Stop ran and closed the screen once; neither delayed start installed or failed.
                assertEquals(null, zap.await())
                assertEquals(null, clickedStart?.await())
                assertEquals(1, stops.size)
                assertTrue(runtime.isPlaybackIntentStopped(intent))
                clickIntent?.let { assertTrue(runtime.isPlaybackIntentStopped(it)) }
                assertEquals(1, connection.subscribeCount)
                await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
                assertEquals(null, runtime.activeTarget.value)
                assertEquals(AppPlaybackState.Idle, runtime.state.value)
                assertEquals(playerLog.toString(), 0, player.mediaItemCount)
            }
        }

    @Test fun enteringALivePlayerAfterAUserStopStartsNothing() = exercise {
        live(false)
        runtime.stop() // the Stop button (or a session Stop) completes before the screen enters
        settle()
        assertNull(runtime.enterPlayerScreen())
        assertEquals(null, restoreRecording())
        settle()
        assertEquals(1, connection.subscribeCount)
        assertEquals(null, runtime.activeTarget.value)
        // A launch request (appliance entry, startup autoplay) is new intent: the live
        // player it opens stays open and starts its channel.
        runtime.notePlaybackIntent()
        val entry = requireNotNull(runtime.enterPlayerScreen())
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val start = scope.async { runtime.playLive(liveSelection(), entry) }
        await { connection.subscribeCount == 2 }
        connection.awaitCollectionRegistered()
        connection.emit(started)
        await { start.isCompleted }
        assertTrue(start.await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun enteringARecordingPlayerAfterASessionStopRestoresNothing() = exercise {
        recording()
        val stops = collectSessionStops()
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 1 }
        assertNull(runtime.enterPlayerScreen())
        assertEquals(null, restoreRecording())
        settle()
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
    }

    @Test fun enteringAndReenteringWithoutAUserStopEachAdvancePlaybackIntent() = exercise {
        live(false)
        val opening = runtime.notePlaybackIntent()
        val entry = requireNotNull(runtime.enterPlayerScreen())
        assertTrue(entry > opening)
        assertFalse(runtime.isPlaybackIntentStopped(entry))
        val reentry = requireNotNull(runtime.enterPlayerScreen())
        assertTrue(reentry > entry)
        assertFalse(runtime.isPlaybackIntentStopped(reentry))
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun automaticStopNeitherClosesAReenteredPlayerNorBlocksReconnectRetryOrRestore() = exercise {
        live(false)
        val stops = collectSessionStops()
        runtime.stopAfterLoss() // connection lost
        settle()
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
        assertNotNull(runtime.enterPlayerScreen())
        // Reconnected: the live screen's automatic retry resubscribes.
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val retry = scope.async { runtime.retryLive() }
        await { connection.subscribeCount == 2 }
        connection.awaitCollectionRegistered()
        connection.emit(started)
        await { retry.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun recordingRestoreAfterAnAutomaticStopRestores() = exercise {
        recording()
        runtime.stopAfterLoss()
        settle()
        assertEquals(null, runtime.activeTarget.value)
        assertTrue(restoreRecording()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
    }

    @Test fun sessionStopTearsDownTimeshiftAndRecordingTargets() = exercise {
        val stops = collectSessionStops()
        live(true)
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 1 }
        recording()
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_STOP))
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 2 }
        settle()
        assertEquals(2, stops.size)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
    }

    @Test fun sessionStopWithoutAScreenCollectingClosesNoLaterPlayer() = exercise {
        recording()
        wrapper.stop()
        await { runtime.activeTarget.value == null }
        settle()
        // A player opened later without new intent closes itself; one opened by a new
        // request (a click) must not close on the earlier stop.
        assertNull(runtime.enterPlayerScreen())
        runtime.notePlaybackIntent()
        assertNotNull(runtime.enterPlayerScreen())
        val stops = collectSessionStops()
        settle()
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun queuedStopAfterCloseDoesNotStop() = exercise {
        live(false)
        val stops = collectSessionStops()
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(0, connection.unsubscribeCount)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun closeLeavesRawPlayerAliveAndRejectsFurtherCommands() = exercise {
        recording()
        val requests = focusRequests
        observation.cancel()
        wrapper.close()
        wrapper.close()
        wrapper.pause()
        settle()
        assertTrue(player.playWhenReady)
        assertEquals(requests, focusRequests)
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
        player.pause()
        assertFalse(player.playWhenReady)
    }

}
