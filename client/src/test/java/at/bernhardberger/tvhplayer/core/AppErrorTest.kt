@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorTest {
    /** The SDK's open values are companion constants, not enum entries. */
    private inline fun <reified T : Any> sdkConstants(): List<T> = T::class.java.fields
        .filter { Modifier.isStatic(it.modifiers) && it.type == T::class.java }
        .map { T::class.java.cast(it.get(null)) }
        .also { assertTrue("no constants found for ${T::class.simpleName}", it.isNotEmpty()) }

    private fun unavailable(failure: SessionFailure) =
        SessionState.Unavailable(failure).toConnectionUiState()

    @Test fun numbersAreUniqueAndStayInTheirGroup() {
        val all = AppErrorCondition.entries
        assertEquals(all.size, all.map { it.number }.toSet().size)
        for (condition in all) {
            val group = when (condition.kind) {
                AppErrorKind.CONNECTION -> 1
                AppErrorKind.CHANNEL, AppErrorKind.LIVE_TV -> 2
                AppErrorKind.RECORDING -> 3
            }
            assertEquals("$condition", group, condition.number / 100)
        }
    }

    @Test fun everySessionFailureMapsToAConnectionCondition() {
        val failures = sdkConstants<SessionFailure>() +
            sdkConstants<TvheadendFailure>().map { SessionFailure.synchronizationFailed(it) }
        for (failure in failures) {
            val error = unavailable(failure).toAppError()
            assertNotNull("$failure", error)
            assertEquals("$failure", AppErrorKind.CONNECTION, error!!.condition.kind)
        }
    }

    @Test fun sessionFailuresMapToTheNumbersOfTheVocabulary() {
        fun number(failure: SessionFailure) = unavailable(failure).toAppError()!!.condition.number
        assertEquals(103, number(SessionFailure.AUTHENTICATION_REJECTED))
        assertEquals(105, number(SessionFailure.INCOMPATIBLE_SERVER))
        assertEquals(105, number(SessionFailure.SERVER_UPGRADE_REQUIRED))
        // The SDK retries these by itself.
        for (failure in listOf(
            SessionFailure.SERVER_UNREACHABLE,
            SessionFailure.NETWORK_UNAVAILABLE,
            SessionFailure.TRANSPORT_UNAVAILABLE,
            SessionFailure.METADATA_OVERFLOW,
        )) assertEquals("$failure", 110, number(failure))
        assertEquals(109, number(SessionFailure.UNEXPECTED_FAILURE))
        assertEquals(104, number(SessionFailure.synchronizationFailed(TvheadendFailure.ACCESS_DENIED)))
        assertEquals(105, number(SessionFailure.synchronizationFailed(TvheadendFailure.NOT_SUPPORTED)))
        assertEquals(110, number(SessionFailure.synchronizationFailed(TvheadendFailure.TIMEOUT)))
        assertEquals(109, number(SessionFailure.synchronizationFailed(TvheadendFailure.SERVER_REJECTED)))
    }

    @Test fun everyConnectionFailureKindMapsForEveryDisposition() {
        val dispositions = sdkConstants<SessionRecoveryDisposition>()
        for (kind in ConnectionFailureKind.entries) {
            for (disposition in dispositions) {
                val condition = kind.toAppErrorCondition(disposition)
                assertEquals("$kind $disposition", AppErrorKind.CONNECTION, condition.kind)
            }
        }
        assertEquals(AppErrorCondition.UNREACHABLE,
            ConnectionFailureKind.UNREACHABLE.toAppErrorCondition(SessionRecoveryDisposition.EXPLICIT_RETRY))
        assertEquals(AppErrorCondition.TIMEOUT,
            ConnectionFailureKind.TIMEOUT.toAppErrorCondition(SessionRecoveryDisposition.EXPLICIT_RETRY))
        assertEquals(AppErrorCondition.LOAD_FAILED,
            ConnectionFailureKind.OTHER.toAppErrorCondition(SessionRecoveryDisposition.NO_RETRY))
    }

    @Test fun connectionStatesThatAreNotFailuresHaveNoError() {
        for (state in listOf(ConnectionUiState.Connecting, ConnectionUiState.SyncingChannels,
            ConnectionUiState.Ready, ConnectionUiState.Reconnecting)) assertNull("$state", state.toAppError())
        assertEquals(107, ConnectionUiState.NeedsConfiguration.toAppError()!!.condition.number)
        assertEquals(106, ConnectionUiState.CredentialUnavailable.toAppError()!!.condition.number)
        assertEquals(108, noChannelsAppError().condition.number)
    }

    @Test fun buttonsFollowTheRecoveryDisposition() {
        val retry = listOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS)
        val settings = listOf(AppErrorAction.CONNECTION_SETTINGS)
        fun actions(kind: ConnectionFailureKind, disposition: SessionRecoveryDisposition, closable: Boolean = false) =
            ConnectionUiState.Error(kind, disposition).toAppError(closable)!!.actions

        assertEquals(retry, actions(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY))
        assertEquals(retry, actions(ConnectionFailureKind.TIMEOUT, SessionRecoveryDisposition.EXPLICIT_RETRY))
        assertEquals(retry, actions(ConnectionFailureKind.OTHER, SessionRecoveryDisposition.EXPLICIT_RETRY))
        // Upgrade-required keeps Incompatible's number but, being explicit-retry, also offers Retry.
        assertEquals(retry, actions(ConnectionFailureKind.INCOMPATIBLE_SERVER, SessionRecoveryDisposition.EXPLICIT_RETRY))
        assertEquals(settings, actions(ConnectionFailureKind.INCOMPATIBLE_SERVER, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED))
        assertEquals(settings, actions(ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED))
        assertEquals(settings, actions(ConnectionFailureKind.PERMISSION_DENIED, SessionRecoveryDisposition.NO_RETRY))
        assertEquals(settings, actions(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF))
        // The player adds Close after the others.
        assertEquals(retry + AppErrorAction.CLOSE,
            actions(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY, closable = true))
        assertEquals(settings + AppErrorAction.CLOSE,
            actions(ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED, closable = true))

        assertEquals(settings, ConnectionUiState.NeedsConfiguration.toAppError()!!.actions)
        assertEquals(settings, ConnectionUiState.CredentialUnavailable.toAppError()!!.actions)
        assertEquals(retry, noChannelsAppError().actions)
        assertEquals(retry + AppErrorAction.CLOSE, noChannelsAppError(closable = true).actions)
    }

    @Test fun conditionsWithoutGuidanceUseTheirDefaultButtons() {
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS),
            AppErrorCondition.UNREACHABLE.actions())
        assertEquals(listOf(AppErrorAction.CONNECTION_SETTINGS), AppErrorCondition.AUTHENTICATION_REJECTED.actions())
        // A future disposition is neither explicit retry nor terminal: the default applies.
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS),
            AppErrorCondition.LOAD_FAILED.actions(disposition = null))
    }

    @Test fun channelMessagesHaveNoButtonsAndAlertsCarryClose() {
        for (condition in AppErrorCondition.entries.filter { it.kind == AppErrorKind.CHANNEL }) {
            assertEquals("$condition", emptyList<AppErrorAction>(), condition.actions(closable = true))
        }
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE), AppErrorCondition.USER_LIMIT.actions())
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE), AppErrorCondition.RECORDING_INTERRUPTED.actions())
        for (condition in listOf(AppErrorCondition.RECORDING_NOT_STARTED, AppErrorCondition.RECORDING_FILE_MISSING,
            AppErrorCondition.RECORDING_UNAVAILABLE)) {
            assertEquals("$condition", listOf(AppErrorAction.CLOSE), condition.actions())
        }
    }

    @Test fun everySubscriptionIssueMapsToALiveCondition() {
        val expected = mapOf(
            SubscriptionIssue.NO_FREE_ADAPTER to 201,
            SubscriptionIssue.NO_INPUT to 202,
            SubscriptionIssue.BAD_SIGNAL to 202,
            SubscriptionIssue.WEAK_STREAM to 202,
            SubscriptionIssue.TUNING_FAILED to 202,
            SubscriptionIssue.MUX_NOT_ENABLED to 203,
            SubscriptionIssue.SCRAMBLED to 204,
            SubscriptionIssue.USER_ACCESS to 205,
            SubscriptionIssue.SUBSCRIPTION_OVERRIDDEN to 206,
            SubscriptionIssue.INVALID_TARGET to 207,
            SubscriptionIssue.UNKNOWN to 207,
            SubscriptionIssue.NO_DISK_SPACE to 207,
            SubscriptionIssue.USER_LIMIT to 208,
        )
        val known = sdkConstants<SubscriptionIssue>()
        assertEquals("a new SDK issue needs a row in the vocabulary", known.toSet(), expected.keys)
        for (issue in known) assertEquals("$issue", expected.getValue(issue), liveErrorCondition(issue).number)
        assertEquals(207, liveErrorCondition(null).number)
        assertEquals(207, AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER).toLiveErrorCondition().number)
        assertEquals(204, AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER,
            subscriptionIssue = SubscriptionIssue.SCRAMBLED).toLiveErrorCondition().number)
    }

    @Test fun recordingPlaybackMapsEveryReasonAndAdmission() {
        for (reason in AppPlaybackFailureReason.entries) {
            val condition = recordingErrorCondition(null, AppPlaybackState.Failed(reason))
            assertEquals("$reason", AppErrorKind.RECORDING, condition.kind)
        }
        val readFailed = AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED)
        val other = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)
        assertEquals(301, recordingErrorCondition(null, readFailed).number)
        assertEquals(301, recordingErrorCondition(null, other).number)
        assertEquals(302, recordingErrorCondition(RecordingPlaybackAdmission.GrowingDeferred, null).number)
        assertEquals(304, recordingErrorCondition(RecordingPlaybackAdmission.TargetUnavailable, readFailed).number)
        assertEquals(304, recordingErrorCondition(RecordingPlaybackAdmission.ObservationExpired, other).number)
        assertEquals(304, recordingErrorCondition(null, null).number)
        assertEquals(101, recordingErrorCondition(null, null, initialConnectionFailure = true).number)
        // Completed and Growing are built inside the SDK; they report the playback failure.
        assertEquals(
            setOf("Completed", "Growing", "GrowingDeferred", "TargetUnavailable", "ObservationExpired"),
            RecordingPlaybackAdmission::class.java.classes.map { it.simpleName }.toSet(),
        )
    }

    @Test fun recordingAlertAlwaysOffersCloseAndRetriesAnInitialConnectionFailure() {
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS, AppErrorAction.CLOSE),
            recordingAppError(null, null, initialConnectionFailure = true).actions)
        assertEquals(listOf(AppErrorAction.CLOSE),
            recordingAppError(RecordingPlaybackAdmission.TargetUnavailable, null).actions)
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE),
            recordingAppError(null, AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED)).actions)
    }
}
