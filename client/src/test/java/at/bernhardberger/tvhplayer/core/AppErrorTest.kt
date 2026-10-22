@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
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

@OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
class AppErrorTest {
    /** The SDK's open values are companion constants, not enum entries. */
    private inline fun <reified T : Any> sdkConstants(): List<T> = T::class.java.fields
        .filter { Modifier.isStatic(it.modifiers) && it.type == T::class.java }
        .map { T::class.java.cast(it.get(null)) }
        .also { assertTrue("no constants found for ${T::class.simpleName}", it.isNotEmpty()) }

    private fun unavailable(failure: SessionFailure) =
        SessionState.Unavailable(failure).toConnectionUiState()

    private val retry = listOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS)
    private val settings = listOf(AppErrorAction.CONNECTION_SETTINGS)

    /** In a player alert a connection condition keeps its first fix and gains Close; one retrying by itself has only Close. */
    private fun List<AppErrorAction>.inAlert(closable: Boolean, condition: AppErrorCondition) = when {
        !closable -> this
        condition == AppErrorCondition.RECONNECTING -> listOf(AppErrorAction.CLOSE)
        else -> listOf(first(), AppErrorAction.CLOSE)
    }

    /** Buttons of each connection condition when the SDK gives no guidance, from the vocabulary table. */
    private val defaultButtons = mapOf(
        AppErrorCondition.UNREACHABLE to retry,
        AppErrorCondition.TIMEOUT to retry,
        AppErrorCondition.AUTHENTICATION_REJECTED to settings,
        AppErrorCondition.PERMISSION_DENIED to settings,
        AppErrorCondition.INCOMPATIBLE_SERVER to settings,
        AppErrorCondition.CREDENTIAL_UNAVAILABLE to settings,
        AppErrorCondition.NOT_CONFIGURED to settings,
        AppErrorCondition.NO_CHANNELS to retry,
        AppErrorCondition.LOAD_FAILED to retry,
        AppErrorCondition.RECONNECTING to settings,
    )

    /** Real SDK admissions, built through its test factory. */
    private val completed = TvheadendTestResultFactory.completedRecordingAdmission(null, RecordingProgressCapability.SUPPORTED)

    private val growing = TvheadendTestResultFactory.growingRecordingAdmission(null, RecordingProgressCapability.SUPPORTED)

    private fun entry(state: DvrEntryState?, files: Int? = null, dataSize: Long? = null) = DvrEntry.create(
        DvrEntryId(1),
        state = state,
        files = files?.let { count -> List(count) { DvrRecordingFile.create(fileId = it.toLong() + 1) } },
        dataSizeBytes = dataSize,
    )

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
        // The player keeps the first fix and adds Close: an alert has at most two buttons.
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE),
            actions(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY, closable = true))
        assertEquals(settings + AppErrorAction.CLOSE,
            actions(ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED, closable = true))

        assertEquals(settings, ConnectionUiState.NeedsConfiguration.toAppError()!!.actions)
        assertEquals(settings, ConnectionUiState.CredentialUnavailable.toAppError()!!.actions)
        assertEquals(retry, noChannelsAppError().actions)
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE), noChannelsAppError(closable = true).actions)
    }

    /** Rule 3 over every failure kind, every SDK disposition and both surfaces, read through [primaryRecoveryAction]. */
    @Test fun everyKindAndDispositionTakesItsButtonsFromThePrimaryRecoveryAction() {
        val dispositions = sdkConstants<SessionRecoveryDisposition>()
        for (kind in ConnectionFailureKind.entries) for (closable in listOf(false, true)) {
            for (disposition in dispositions) {
                val state = ConnectionUiState.Error(kind, disposition)
                val error = state.toAppError(closable)!!
                val expected = when (state.primaryRecoveryAction()) {
                    // The owner keeps a connection that retries by itself to Connection settings only.
                    ConnectionRecoveryAction.RETRY -> if (error.condition == AppErrorCondition.RECONNECTING) settings else retry
                    ConnectionRecoveryAction.SETTINGS,
                    ConnectionRecoveryAction.NONE -> settings
                }
                assertEquals("$kind $disposition closable=$closable", expected.inAlert(closable, error.condition), error.actions)
            }
            // Without guidance every condition the kind can read as keeps its own default.
            for (condition in dispositions.map { kind.toAppErrorCondition(it) }.toSet()) {
                assertEquals("$kind $condition no guidance closable=$closable",
                    defaultButtons.getValue(condition).inAlert(closable, condition), condition.actions(recovery = null, closable = closable))
            }
        }
    }

    @Test fun guidanceOverridesTheDefaultOfEveryConnectionCondition() {
        for (condition in AppErrorCondition.entries.filter { it.kind == AppErrorKind.CONNECTION }) {
            val retryOrOwn = if (condition == AppErrorCondition.RECONNECTING) settings else retry
            assertEquals("$condition", retryOrOwn, condition.actions(ConnectionRecoveryAction.RETRY))
            assertEquals("$condition", settings, condition.actions(ConnectionRecoveryAction.SETTINGS))
            assertEquals("$condition", settings, condition.actions(ConnectionRecoveryAction.NONE))
        }
        // No channels under a no-retry or automatic-backoff session offers no Retry.
        assertEquals(settings, AppErrorCondition.NO_CHANNELS.actions(
            ConnectionUiState.Error(ConnectionFailureKind.OTHER, SessionRecoveryDisposition.NO_RETRY).primaryRecoveryAction()))
        assertEquals(settings, AppErrorCondition.NO_CHANNELS.actions(
            ConnectionUiState.Error(ConnectionFailureKind.OTHER, SessionRecoveryDisposition.AUTOMATIC_BACKOFF).primaryRecoveryAction()))
    }

    @Test fun conditionsWithoutGuidanceUseTheirDefaultButtons() {
        val connection = AppErrorCondition.entries.filter { it.kind == AppErrorKind.CONNECTION }
        assertEquals(connection.toSet(), defaultButtons.keys)
        for (condition in connection) {
            assertEquals("$condition", defaultButtons.getValue(condition), condition.actions())
            assertEquals("$condition", defaultButtons.getValue(condition).inAlert(closable = true, condition),
                condition.actions(closable = true))
            assertTrue("$condition", condition.actions(closable = true).size <= 2)
        }
    }

    @Test fun channelMessagesHaveNoButtonsAndAlertsCarryClose() {
        for (condition in AppErrorCondition.entries.filter { it.kind == AppErrorKind.CHANNEL }) {
            assertEquals("$condition", emptyList<AppErrorAction>(), condition.actions(closable = true))
        }
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE), AppErrorCondition.USER_LIMIT.actions())
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE), AppErrorCondition.RECORDING_INTERRUPTED.actions())
        for (condition in listOf(AppErrorCondition.RECORDING_NOT_STARTED, AppErrorCondition.RECORDING_FILE_MISSING,
            AppErrorCondition.RECORDING_UNAVAILABLE, AppErrorCondition.RECORDING_DEFERRED)) {
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
            val condition = recordingErrorCondition(null, null, AppPlaybackState.Failed(reason))
            assertEquals("$reason", AppErrorKind.RECORDING, condition!!.kind)
        }
        // A new SDK admission needs a row here and in the vocabulary.
        assertEquals(
            setOf("Completed", "Growing", "GrowingDeferred", "TargetUnavailable", "ObservationExpired"),
            RecordingPlaybackAdmission::class.java.classes.map { it.simpleName }.toSet(),
        )
    }

    @Test fun recordingConditionsFollowTheSdkAdmission() {
        val readFailed = AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED)
        val other = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)
        fun number(entry: DvrEntry?, admission: RecordingPlaybackAdmission?, failure: AppPlaybackState.Failed? = null) =
            recordingErrorCondition(entry, admission, failure)?.number

        // The SDK defers an active recording whose file is not MPEG-TS.
        assertEquals(305, number(entry(DvrEntryState.RECORDING), RecordingPlaybackAdmission.GrowingDeferred))
        // Not admitted: what the entry shows decides.
        assertEquals(302, number(entry(DvrEntryState.SCHEDULED), RecordingPlaybackAdmission.TargetUnavailable))
        // File evidence: the server marks the file missing, or a running entry has no file at all.
        assertEquals(303, number(entry(DvrEntryState.FILE_MISSING), RecordingPlaybackAdmission.TargetUnavailable))
        assertEquals(303, number(entry(DvrEntryState.RECORDING), RecordingPlaybackAdmission.TargetUnavailable))
        assertEquals(303, number(entry(DvrEntryState.RECORDING, files = 0), RecordingPlaybackAdmission.TargetUnavailable))
        // Several files of a running recording only play once it is finished.
        assertEquals(305, number(entry(DvrEntryState.RECORDING, files = 2), RecordingPlaybackAdmission.TargetUnavailable))
        // One existing file but not admitted (blank path or a replaced incarnation): it changed.
        assertEquals(304, number(entry(DvrEntryState.RECORDING, files = 1), RecordingPlaybackAdmission.TargetUnavailable))
        // A deferred recording whose entry is gone is gone, not deferred.
        assertEquals(304, number(null, RecordingPlaybackAdmission.GrowingDeferred))
        // A running entry in an error state follows a running one.
        assertEquals(303, number(entry(DvrEntryState.RECORDING_ERROR), RecordingPlaybackAdmission.TargetUnavailable))
        assertEquals(305, number(entry(DvrEntryState.RECORDING_ERROR, files = 2), RecordingPlaybackAdmission.TargetUnavailable))
        assertEquals(304, number(entry(DvrEntryState.RECORDING_ERROR, files = 1), RecordingPlaybackAdmission.TargetUnavailable))
        // A failed or missed entry without a playable file has none; one with a file that is not admitted changed.
        for (state in listOf(DvrEntryState.COMPLETED_ERROR, DvrEntryState.MISSED)) {
            assertEquals("$state", 303, number(entry(state), RecordingPlaybackAdmission.TargetUnavailable))
            assertEquals("$state", 303, number(entry(state, files = 0), RecordingPlaybackAdmission.TargetUnavailable))
            assertEquals("$state", 303, number(entry(state, files = 1, dataSize = 0), RecordingPlaybackAdmission.TargetUnavailable))
            assertEquals("$state", 304, number(entry(state, files = 1), RecordingPlaybackAdmission.TargetUnavailable))
            // An admitted failed or missed recording plays without an alert.
            assertNull("$state", number(entry(state, files = 1), completed))
        }
        assertNull(number(entry(DvrEntryState.RECORDING_ERROR, files = 1), growing))
        for (state in sdkConstants<DvrEntryState>() - setOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING,
            DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED_ERROR, DvrEntryState.MISSED,
            DvrEntryState.FILE_MISSING)) {
            assertEquals("$state", 304, number(entry(state), RecordingPlaybackAdmission.TargetUnavailable))
        }
        assertEquals(304, number(entry(null), RecordingPlaybackAdmission.TargetUnavailable))
        assertEquals(304, number(null, RecordingPlaybackAdmission.TargetUnavailable, readFailed))
        assertEquals(302, number(entry(DvrEntryState.SCHEDULED), RecordingPlaybackAdmission.TargetUnavailable, other))
        assertEquals(304, number(entry(DvrEntryState.COMPLETED), RecordingPlaybackAdmission.ObservationExpired, other))
        assertEquals(304, number(null, RecordingPlaybackAdmission.ObservationExpired))
        // Playback failures of a playable or unresolved recording; Failed(OTHER) stays an interruption.
        assertEquals(301, number(entry(DvrEntryState.COMPLETED), completed, readFailed))
        assertEquals(301, number(entry(DvrEntryState.RECORDING), growing, other))
        assertEquals(301, number(null, null, readFailed))
        assertEquals(301, number(null, null, other))
        // This device's player failed: the recording is still there, the TV couldn't play it.
        val deviceFault = readFailed.copy(deviceFault = true)
        assertEquals(306, number(entry(DvrEntryState.COMPLETED), completed, deviceFault))
        assertEquals(306, number(null, null, deviceFault))
        assertEquals(304, number(null, RecordingPlaybackAdmission.TargetUnavailable, deviceFault))
        // An admitted recording whose entry is gone, or an entry the SDK did not admit.
        assertEquals(304, number(null, completed))
        assertEquals(304, number(null, growing))
        assertEquals(304, number(entry(DvrEntryState.COMPLETED), null))
        // Playing and loading are not errors.
        assertNull(number(entry(DvrEntryState.COMPLETED), completed))
        assertNull(number(entry(DvrEntryState.RECORDING), growing))
        assertNull(number(null, null))
        assertNull(recordingAppError(entry(DvrEntryState.COMPLETED), completed, null))
    }

    /** Same predicates as `RecordingPlayerScreen`: an error exists exactly when its terminal error shows. */
    @Test fun recordingErrorExistsExactlyWhenThePlayerShowsItsTerminalError() {
        val entries = listOf(null, entry(null)) + sdkConstants<DvrEntryState>().map { entry(it) }
        val admissions = listOf(null, completed, growing, RecordingPlaybackAdmission.GrowingDeferred,
            RecordingPlaybackAdmission.TargetUnavailable, RecordingPlaybackAdmission.ObservationExpired)
        val failures = listOf(null) + AppPlaybackFailureReason.entries.map { AppPlaybackState.Failed(it) }
        for (entry in entries) for (admission in admissions) for (failure in failures) for (connectionAvailable in
            listOf(true, false)) {
            val playbackAvailable = entry != null && when (admission) {
                is RecordingPlaybackAdmission.Completed,
                is RecordingPlaybackAdmission.Growing -> true
                RecordingPlaybackAdmission.GrowingDeferred,
                RecordingPlaybackAdmission.ObservationExpired,
                RecordingPlaybackAdmission.TargetUnavailable,
                null -> false
            }
            val recordingResolved = entry != null || admission != null || failure != null
            val initialConnectionFailure = !recordingResolved && !connectionAvailable
            // The unresolved connection is the player alert's own (PlaybackStatusPresentationTest).
            if (initialConnectionFailure) continue
            val terminalErrorVisible = recordingResolved && (!playbackAvailable || failure != null)

            val label = "entry=${entry?.state} $admission $failure connected=$connectionAvailable"
            val condition = recordingErrorCondition(entry, admission, failure)
            val error = recordingAppError(entry, admission, failure)
            assertEquals(label, terminalErrorVisible, condition != null)
            assertEquals(label, condition, error?.condition)
            if (error != null) assertEquals(label, AppErrorAction.CLOSE, error.actions.last())
        }
    }

    @Test fun recordingAlertAlwaysOffersClose() {
        assertEquals(listOf(AppErrorAction.CLOSE),
            recordingAppError(entry(DvrEntryState.SCHEDULED), RecordingPlaybackAdmission.TargetUnavailable, null)!!.actions)
        assertEquals(listOf(AppErrorAction.CLOSE),
            recordingAppError(entry(DvrEntryState.RECORDING), RecordingPlaybackAdmission.GrowingDeferred, null)!!.actions)
        assertEquals(listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE),
            recordingAppError(null, null, AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED))!!.actions)
    }

    @Test fun onlyAFailureOfThisDevicesPlayerShowsThePlayersOwnError() {
        val failed = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER, playerErrorCode = "ERROR_CODE_DECODER_INIT_FAILED")
        assertEquals("DECODER_INIT_FAILED", failed.copy(deviceFault = true).deviceFaultDetail())
        assertEquals(
            "DECODER_INIT_FAILED (4001)",
            failed.copy(deviceFault = true, playerErrorNumber = 4001).deviceFaultDetail(),
        )
        assertNull(failed.deviceFaultDetail())
        assertNull(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER, deviceFault = true).deviceFaultDetail())
        assertNull(AppPlaybackState.Playing.deviceFaultDetail())
    }

    @Test
    fun channelsAlertIsTheFailureOrNoChannelsWhileLoadingStaysPassive() {
        fun channels(state: ConnectionUiState, catalogCurrent: Boolean = true) =
            state.forEmptyChannelPresentation(catalogCurrent).browseAppError(noChannels = true)
        val retryAndSettings = listOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS)
        assertEquals(AppError(AppErrorCondition.NO_CHANNELS, retryAndSettings), channels(ConnectionUiState.Ready))
        assertNull(channels(ConnectionUiState.Ready, catalogCurrent = false))
        assertEquals(
            AppError(AppErrorCondition.UNREACHABLE, retryAndSettings),
            channels(ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY)),
        )
        assertEquals(
            AppError(AppErrorCondition.RECONNECTING, listOf(AppErrorAction.CONNECTION_SETTINGS)),
            channels(ConnectionUiState.Error(ConnectionFailureKind.TIMEOUT, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)),
        )
        assertEquals(
            AppError(AppErrorCondition.NOT_CONFIGURED, listOf(AppErrorAction.CONNECTION_SETTINGS)),
            channels(ConnectionUiState.NeedsConfiguration),
        )
        for (passive in listOf(ConnectionUiState.Connecting, ConnectionUiState.SyncingChannels, ConnectionUiState.Reconnecting)) {
            assertNull(channels(passive))
        }
        // Only Channels and the Guide treat an empty Ready catalog as 108.
        assertNull(ConnectionUiState.Ready.browseAppError())
    }
}
