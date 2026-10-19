package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import kotlin.time.Instant
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStatusPresentationTest {

    @Test
    fun ordinaryChannelStart_isCompactAndNonBlocking() {
        assertEquals(
            PlaybackStatusPresentation.COMPACT_TUNING,
            playbackStatusPresentation(
                connectionAvailable = true,
                playbackStarting = true,
                playbackRecovering = false,
                playbackPlaying = false,
            )
        )
    }

    @Test
    fun genuineRecovery_orConnectionLoss_isFullScreen() {
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(true, false, true, false),
        )
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(false, true, false, false),
        )
        assertEquals(
            PlaybackStatusPresentation.CHANNEL_UNAVAILABLE,
            playbackStatusPresentation(
                connectionAvailable = true,
                playbackStarting = false,
                playbackRecovering = false,
                playbackPlaying = false,
                channelCondition = AppErrorCondition.STREAM_STOPPED,
            ),
        )
    }

    @Test
    fun playingSession_hasNoWaitingPresentation() {
        assertEquals(
            PlaybackStatusPresentation.NONE,
            playbackStatusPresentation(true, false, false, true),
        )
    }

    @Test
    fun serverStop_presentsTheUnavailableCardInEveryPlayerState() {
        // READY (presented), BUFFERING or starting, and paused (presented, no play intent) alike.
        for ((starting, presented) in listOf(false to true, true to false, false to false)) {
            for (issue in listOf(null, SubscriptionIssue.BAD_SIGNAL)) {
                val presentation = playbackStatusPresentation(
                    connectionAvailable = true,
                    playbackStarting = starting,
                    playbackRecovering = false,
                    playbackPlaying = presented,
                    channelCondition = liveChannelCondition(issue, playbackFailed = false, liveInterrupted = true),
                )
                assertEquals(PlaybackStatusPresentation.CHANNEL_UNAVAILABLE, presentation)
                assertTrue(presentation != PlaybackStatusPresentation.NONE)
            }
        }
    }

    @Test
    fun serverStart_clearsTheInterruption() {
        assertEquals(
            PlaybackStatusPresentation.NONE,
            playbackStatusPresentation(
                true, false, false, true,
                channelCondition = liveChannelCondition(null, playbackFailed = false, liveInterrupted = false),
            ),
        )
        assertFalse(liveInterrupted(serverStopped = false, playingLiveChannelId = CHANNEL, shownChannelId = CHANNEL))
    }

    @Test
    fun connectionLossAndPlayerRecovery_takePrecedenceOverTheInterruption() {
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(false, false, false, true, AppErrorCondition.STREAM_STOPPED),
        )
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(true, false, true, false, AppErrorCondition.STREAM_STOPPED),
        )
    }

    @Test
    fun interruption_belongsOnlyToTheLiveChannelOnScreen() {
        assertTrue(liveInterrupted(serverStopped = true, playingLiveChannelId = CHANNEL, shownChannelId = CHANNEL))
        // Another channel is being tuned, or a recording (no live channel) is playing.
        assertFalse(liveInterrupted(serverStopped = true, playingLiveChannelId = ChannelId(2), shownChannelId = CHANNEL))
        assertFalse(liveInterrupted(serverStopped = true, playingLiveChannelId = null, shownChannelId = CHANNEL))
    }

    @Test
    fun channelCondition_isTheIssueElse207ForAFailureOrAStop() {
        assertNull(liveChannelCondition(null, playbackFailed = false, liveInterrupted = false))
        assertEquals(AppErrorCondition.STREAM_STOPPED, liveChannelCondition(null, playbackFailed = false, liveInterrupted = true))
        assertEquals(AppErrorCondition.STREAM_STOPPED, liveChannelCondition(null, playbackFailed = true, liveInterrupted = false))
        // An issue names its own condition, also over a stop or a failure.
        for ((failed, interrupted) in listOf(false to false, true to false, false to true, true to true)) {
            assertEquals(
                AppErrorCondition.NO_FREE_ADAPTER,
                liveChannelCondition(SubscriptionIssue.NO_FREE_ADAPTER, failed, interrupted),
            )
        }
    }

    @Test
    fun channelCondition_is210WhenThisDevicesPlayerFailedWithoutAnIssue() {
        assertEquals(
            AppErrorCondition.CHANNEL_PLAYBACK_FAILED,
            liveChannelCondition(null, playbackFailed = true, liveInterrupted = false, deviceFault = true),
        )
        // The server's own issue still names the cause.
        assertEquals(
            AppErrorCondition.NO_SIGNAL,
            liveChannelCondition(SubscriptionIssue.NO_INPUT, playbackFailed = true, liveInterrupted = false, deviceFault = true),
        )
        // A stream the server stopped stays the server's stop, even when this device's player failed too.
        assertEquals(
            AppErrorCondition.STREAM_STOPPED,
            liveChannelCondition(null, playbackFailed = true, liveInterrupted = true, deviceFault = true),
        )
    }

    @Test
    fun channelConditions_areTheBoxAndNeverTheAlert() {
        val channelConditions = AppErrorCondition.entries.filter { it.kind == AppErrorKind.CHANNEL }
        assertEquals(8, channelConditions.size)
        for (condition in channelConditions) {
            assertEquals(
                "$condition",
                PlaybackStatusPresentation.CHANNEL_UNAVAILABLE,
                playbackStatusPresentation(true, false, false, false, condition),
            )
            assertNull("$condition", livePlayerAlert(ConnectionState.Connected, failed(), condition))
        }
    }

    @Test
    fun userLimit_isTheAlertWithRetryAndClose() {
        val condition = liveChannelCondition(SubscriptionIssue.USER_LIMIT, playbackFailed = true, liveInterrupted = false)
        assertEquals(AppErrorCondition.USER_LIMIT, condition)
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(true, false, false, false, condition),
        )
        val alert = livePlayerAlert(ConnectionState.Connected, failed(SubscriptionIssue.USER_LIMIT), condition)
        assertEquals(
            LivePlayerAlert.Error(
                AppError(AppErrorCondition.USER_LIMIT, listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE)),
                PlaybackRetryCommand.RETRY_LIVE,
            ),
            alert,
        )
    }

    @Test
    fun connectionWhileWatching_isTheAlertWithTheCatalogButtonsAndClose() {
        val explicit = livePlayerAlert(
            ConnectionState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY),
            AppPlaybackState.Idle,
            null,
        ) as LivePlayerAlert.Error
        assertEquals(AppErrorCondition.UNREACHABLE, explicit.error.condition)
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE), explicit.error.actions)
        assertEquals(PlaybackRetryCommand.RECONNECT, explicit.retry)

        val rejected = livePlayerAlert(
            ConnectionState.Error(ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED),
            AppPlaybackState.Idle,
            null,
        ) as LivePlayerAlert.Error
        assertEquals(AppErrorCondition.AUTHENTICATION_REJECTED, rejected.error.condition)
        assertEquals(listOf(AppErrorAction.CONNECTION_SETTINGS, AppErrorAction.CLOSE), rejected.error.actions)

        val final = livePlayerAlert(
            ConnectionState.Error(ConnectionFailureKind.OTHER, SessionRecoveryDisposition.NO_RETRY),
            AppPlaybackState.Idle,
            null,
        ) as LivePlayerAlert.Error
        assertEquals(listOf(AppErrorAction.CONNECTION_SETTINGS, AppErrorAction.CLOSE), final.error.actions)
    }

    @Test
    fun automaticRecovery_isPassiveWithoutRetry() {
        // The SDK retries the connection by itself, or it is between attempts.
        for (connection in listOf(
            ConnectionState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF),
            ConnectionState.Error(ConnectionFailureKind.TIMEOUT, SessionRecoveryDisposition.AUTOMATIC_BACKOFF),
            ConnectionState.Connecting,
            ConnectionState.Disconnected,
        )) {
            val alert = livePlayerAlert(connection, AppPlaybackState.Playing, null) as LivePlayerAlert.Error
            assertEquals("$connection", AppErrorCondition.RECONNECTING, alert.error.condition)
            assertEquals("$connection", listOf(AppErrorAction.CLOSE), alert.error.actions)
        }
        // The player retunes by itself: a countdown and Close, whatever the channel reported meanwhile.
        val recovering = AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED, retryDelayMillis = 3_000L)
        for (condition in listOf(null, AppErrorCondition.NO_SIGNAL, AppErrorCondition.USER_LIMIT)) {
            assertEquals(
                "$condition",
                LivePlayerAlert.Recovering(retryDelayMillis = 3_000L),
                livePlayerAlert(ConnectionState.Connected, recovering, condition),
            )
        }
    }

    @Test
    fun exhaustedRecovery_isAChannelProblemSoPassiveWithoutRetry_unlessTheServerReportsAUserLimit() {
        // The SDK stops retrying the connection by itself and asks for the viewer's retry.
        val connection = livePlayerAlert(
            ConnectionState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY),
            AppPlaybackState.Playing,
            null,
        ) as LivePlayerAlert.Error
        assertEquals(AppErrorAction.RETRY, connection.error.actions.first())
        // Exhausted live recovery fails the target, a channel problem: the passive channel message
        // without buttons reports it. Only 208, a live TV condition, is the alert with Retry.
        val exhausted = failed(recoveryReason = PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED)
        val condition = liveChannelCondition(exhausted.subscriptionIssue, playbackFailed = true, liveInterrupted = false)
        assertEquals(AppErrorCondition.STREAM_STOPPED, condition)
        assertEquals(exhausted.toLiveErrorCondition(), condition)
        assertNull(livePlayerAlert(ConnectionState.Connected, exhausted, condition))
        val limited = failed(SubscriptionIssue.USER_LIMIT, PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED)
        val limitAlert = livePlayerAlert(ConnectionState.Connected, limited, limited.toLiveErrorCondition()) as LivePlayerAlert.Error
        assertEquals(AppErrorAction.RETRY, limitAlert.error.actions.first())
    }

    @Test
    fun aRetryThatDoesNotStartGivesTheChannelItsMessageBack() {
        for (result in listOf(null, PlaybackTargetResult.NOT_READY, PlaybackTargetResult.TARGET_UNAVAILABLE,
            PlaybackTargetResult.PLAYER_UNAVAILABLE, PlaybackTargetResult.SHUT_DOWN)) {
            assertEquals("$result", true, liveRetryRequestFailure(true, cleared = true, result = result, requestCurrent = true))
            // A newer request owns the screen now, or the retry cleared nothing: it keeps its state.
            assertNull("$result", liveRetryRequestFailure(true, cleared = true, result = result, requestCurrent = false))
            assertNull("$result", liveRetryRequestFailure(true, cleared = false, result = result, requestCurrent = true))
        }
        assertEquals(false, liveRetryRequestFailure(true, cleared = true, result = PlaybackTargetResult.STARTED, requestCurrent = true))
    }

    @Test
    fun aStartedRetryEndsAFailureThatAnOverlappingRetryGaveBack() {
        // Retry A cleared the flag and B saw it cleared; A did not start and gave the failure back.
        assertEquals(true, liveRetryRequestFailure(true, cleared = true, result = PlaybackTargetResult.NOT_READY, requestCurrent = true))
        // B then starts the same request's channel: the failure ends although B cleared nothing.
        assertEquals(false, liveRetryRequestFailure(true, cleared = false, result = PlaybackTargetResult.STARTED, requestCurrent = true))
        // A retry that retunes some other channel than the request's, or for an old request, decides nothing.
        assertNull(liveRetryRequestFailure(false, cleared = false, result = PlaybackTargetResult.STARTED, requestCurrent = true))
        assertNull(liveRetryRequestFailure(true, cleared = false, result = PlaybackTargetResult.STARTED, requestCurrent = false))
    }

    @Test
    fun alertExistsExactlyWhenThePresentationIsFullRecovery() {
        val connections = listOf(
            ConnectionState.Connected,
            ConnectionState.Connecting,
            ConnectionState.Error(ConnectionFailureKind.OTHER, SessionRecoveryDisposition.EXPLICIT_RETRY),
        )
        val states = listOf(
            AppPlaybackState.Starting,
            AppPlaybackState.Playing,
            AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L),
            failed(),
        )
        val conditions = listOf(null) + AppErrorCondition.entries.filter {
            it.kind == AppErrorKind.CHANNEL || it.kind == AppErrorKind.LIVE_TV
        }
        for (connection in connections) for (state in states) for (condition in conditions) {
            val presentation = playbackStatusPresentation(
                connectionAvailable = connection is ConnectionState.Connected,
                playbackStarting = state is AppPlaybackState.Starting,
                playbackRecovering = state is AppPlaybackState.Recovering,
                playbackPlaying = state.presented,
                channelCondition = condition,
            )
            assertEquals(
                "$connection $state $condition",
                presentation == PlaybackStatusPresentation.FULL_RECOVERY,
                livePlayerAlert(connection, state, condition) != null,
            )
        }
    }

    @Test
    fun recordingAlert_isTheCatalogConditionWithAtMostTwoButtonsAndNoRetryWithNothingToRetry() {
        val completed = DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED)
        val readFailed = AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED)
        val deviceFailed = readFailed.copy(deviceFault = true)
        fun alert(
            state: AppPlaybackState,
            entry: DvrEntry? = completed,
            admission: RecordingPlaybackAdmission? = null,
            retry: PlaybackRetryCommand = PlaybackRetryCommand.RESUME_RECORDING,
            connection: ConnectionState = ConnectionState.Connected,
        ) = recordingPlayerAlert(connection, connection != ConnectionState.Connected, state, entry, admission, retry)

        assertEquals(
            LivePlayerAlert.Error(
                AppError(AppErrorCondition.RECORDING_INTERRUPTED, listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE)),
                PlaybackRetryCommand.RESUME_RECORDING,
            ),
            alert(readFailed),
        )
        assertEquals(
            listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE),
            (alert(deviceFailed) as LivePlayerAlert.Error).error.actions,
        )
        assertEquals(AppErrorCondition.RECORDING_PLAYBACK_FAILED, (alert(deviceFailed) as LivePlayerAlert.Error).error.condition)
        // Nothing to retry, such as a failure that is not a read failure: Retry would do nothing, so only Close.
        assertEquals(
            LivePlayerAlert.Error(
                AppError(AppErrorCondition.RECORDING_INTERRUPTED, listOf(AppErrorAction.CLOSE)),
                PlaybackRetryCommand.NONE,
            ),
            alert(readFailed, retry = PlaybackRetryCommand.NONE),
        )
        val missing = DvrEntry.create(DvrEntryId(1), state = DvrEntryState.FILE_MISSING)
        assertEquals(
            AppError(AppErrorCondition.RECORDING_FILE_MISSING, listOf(AppErrorAction.CLOSE)),
            (alert(AppPlaybackState.Idle, missing, RecordingPlaybackAdmission.TargetUnavailable) as LivePlayerAlert.Error).error,
        )
    }

    @Test
    fun recordingAlert_isTheConnectionWhileUnresolvedAndTheCountdownWhileRecovering() {
        val waiting = recordingPlayerAlert(
            ConnectionState.Disconnected, true, AppPlaybackState.Idle, null, null, PlaybackRetryCommand.NONE,
        ) as LivePlayerAlert.Error
        assertEquals(AppError(AppErrorCondition.RECONNECTING, listOf(AppErrorAction.CLOSE)), waiting.error)
        val unreachable = recordingPlayerAlert(
            ConnectionState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY),
            true, AppPlaybackState.Idle, null, null, PlaybackRetryCommand.RECONNECT,
        ) as LivePlayerAlert.Error
        assertEquals(AppError(AppErrorCondition.UNREACHABLE, listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE)), unreachable.error)
        assertEquals(PlaybackRetryCommand.RECONNECT, unreachable.retry)
        val rejected = recordingPlayerAlert(
            ConnectionState.Error(ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED),
            true, AppPlaybackState.Idle, null, null, PlaybackRetryCommand.NONE,
        ) as LivePlayerAlert.Error
        assertEquals(AppError(AppErrorCondition.AUTHENTICATION_REJECTED, listOf(AppErrorAction.CONNECTION_SETTINGS, AppErrorAction.CLOSE)), rejected.error)

        assertEquals(
            LivePlayerAlert.Recovering(4_000L),
            recordingPlayerAlert(
                ConnectionState.Connected, false,
                AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, retryDelayMillis = 4_000L),
                null, null, PlaybackRetryCommand.RESUME_RECORDING,
            ),
        )
        // Still loading: nothing resolved and nothing failed.
        assertNull(recordingPlayerAlert(ConnectionState.Connected, false, AppPlaybackState.Idle, null, null, PlaybackRetryCommand.NONE))
    }

    @Test
    fun recordingAlert_carriesTheStartOnlyForARecordingThatHasNotStarted() {
        val start = Instant.fromEpochSeconds(1_792_000_000L)
        fun alert(state: DvrEntryState) = recordingPlayerAlert(
            ConnectionState.Connected, false, AppPlaybackState.Idle,
            DvrEntry.create(DvrEntryId(1), state = state, start = start),
            RecordingPlaybackAdmission.TargetUnavailable, PlaybackRetryCommand.NONE,
        ) as LivePlayerAlert.Error

        assertEquals(AppErrorCondition.RECORDING_NOT_STARTED, alert(DvrEntryState.SCHEDULED).error.condition)
        assertEquals(start, alert(DvrEntryState.SCHEDULED).startsAt)
        assertNull(alert(DvrEntryState.FILE_MISSING).startsAt)
    }

    private fun failed(issue: SubscriptionIssue? = null, recoveryReason: PlaybackRecoveryReason? = null) =
        AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER, subscriptionIssue = issue, recoveryReason = recoveryReason)

    private companion object {
        val CHANNEL = ChannelId(1)
    }
}
