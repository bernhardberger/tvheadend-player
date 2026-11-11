package at.bernhardberger.tvhplayer.settings

import at.bernhardberger.tvheadend.sdk.core.SessionCommandResult
import at.bernhardberger.tvheadend.sdk.core.SessionState

import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Connection-form operations backed by the process-owned SDK profile store. */
interface ConnectionProfileEditor {
    val serverSettings: Flow<ServerSettings>

    suspend fun loadServerForEditing(
        applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
    )

    suspend fun saveServer(host: String, htspPort: Int)

    suspend fun savePasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
    )

    suspend fun clearProfile()
}

class AppProfileOwner(
    private val session: TvheadendSession,
    private val profileStore: ServerProfileStore,
    private val playerSettings: PlayerSettingsStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val readProfileForEditing: suspend (ServerProfileId) -> ServerProfileEditReadResult,
    private val noticeCenter: () -> NoticeCenter? = { null },
) : ConnectionProfileEditor {
    constructor(
        session: TvheadendSession,
        profileStore: TvheadendServerProfileStore,
        playerSettings: PlayerSettingsStore,
        ioDispatcher: CoroutineDispatcher,
        noticeCenter: () -> NoticeCenter? = { null },
    ) : this(
        session = session,
        profileStore = profileStore,
        playerSettings = playerSettings,
        ioDispatcher = ioDispatcher,
        readProfileForEditing = profileStore::loadForEditing,
        noticeCenter = noticeCenter,
    )

    private val serverMutex = Mutex()
    private val streamMutex = Mutex()
    // Taken only after playback has stopped: discovery must remain able to settle a
    // reconnect retune while stop waits for the playback command serializer.
    private val switchIdentity = Mutex()
    private val mutableSwitchInProgress = MutableStateFlow(false)
    private val switchLifecycle = Any()
    private var switchOwnerClosed = false
    private var activeSwitch: Job? = null
    /** Trial and fallback share a session, but neither is the in-use connection's status. */
    val switchInProgress: StateFlow<Boolean> = mutableSwitchInProgress.asStateFlow()
    private val mutableLiveProfile = MutableStateFlow<ServerProfileId?>(null)
    /** The published session identity; absent until a trial is committed or restored. */
    val liveProfile: StateFlow<ServerProfileId?> = mutableLiveProfile.asStateFlow()
    private var setupTrialConnection = false

    /** Setup holds the connection lock, but keeps this guard across its test-to-save handoff. */
    fun setSetupTrialConnection(trial: Boolean) = synchronized(switchLifecycle) {
        setupTrialConnection = trial
        mutableLiveProfile.value = publishedProfileId(
            mutableServerProfile.value, trial || mutableSwitchInProgress.value,
        )
    }

    /**
     * Serializes setup tests (including their non-cancellable cleanup) with saved-profile
     * commands. A setup releases its test before submitting a save. Once a command is admitted,
     * its caller holds this lock until the process-owned command has actually completed.
     */
    val connectionChanges = Mutex()
    private val commands = Channel<ProfileCommand>(
        capacity = Channel.UNLIMITED,
        onUndeliveredElement = { command ->
            command.releaseSensitiveMaterial()
            command.completion.completeExceptionally(
                CancellationException("Profile owner stopped before command execution"),
            )
        },
    )
    private val mutableServerProfile = MutableStateFlow<ServerProfileReadResult?>(null)
    private val mutableConfigurationGeneration = MutableStateFlow(0L)
    val configurationGeneration: StateFlow<Long> = mutableConfigurationGeneration.asStateFlow()
    private val mutableStreamProfiles =
        MutableStateFlow<StreamProfilesResult>(StreamProfilesResult.Failed(TvheadendFailure.NOT_READY))
    private val mutableSelectedStreamProfileId = MutableStateFlow<StreamProfileId?>(null)
    private val mutableSettledStreamProfilesFor =
        MutableStateFlow<CurrentSessionObservation?>(null)
    private var availableForObservation: CurrentSessionObservation? = null

    val serverProfile: StateFlow<ServerProfileReadResult?> = mutableServerProfile.asStateFlow()
    private val mutableProfiles = MutableStateFlow<ServerProfileListResult?>(null)
    val profiles: StateFlow<ServerProfileListResult?> = mutableProfiles.asStateFlow()
    var audioProfileId: String? = null
        private set
    override val serverSettings: Flow<ServerSettings> = serverProfile
        .filterNotNull()
        .map(ServerProfileReadResult::toServerSettings)
        .distinctUntilChanged()
    val streamProfiles: StateFlow<StreamProfilesResult> = mutableStreamProfiles.asStateFlow()
    val selectedStreamProfileId: StateFlow<StreamProfileId?> =
        mutableSelectedStreamProfileId.asStateFlow()

    suspend fun run() = coroutineScope {
        val discoveryJob = launch { observeStreamProfiles() }
        try {
            withContext(NonCancellable) { initializeServerProfile() }
            for (command in commands) {
                withContext(NonCancellable) { handleCommand(command) }
            }
        } finally {
            val attempt = synchronized(switchLifecycle) {
                switchOwnerClosed = true
                activeSwitch
            }
            withContext(NonCancellable) {
                commands.cancel()
                attempt?.cancelAndJoin()
                discoveryJob.cancelAndJoin()
                mutableSwitchInProgress.value = false
                mutableLiveProfile.value = null
            }
        }
    }

    override suspend fun loadServerForEditing(
        applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
    ) {
        val id = (profiles.value as? ServerProfileListResult.Available)?.activeId ?: return
        loadServerForEditing(id, applyAvailable)
    }

    suspend fun loadServerForEditing(
        id: ServerProfileId,
        applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
    ) = serverMutex.withLock {
        readServerForEditing(id, readProfileForEditing, profiles, applyAvailable)
    }

    override suspend fun saveServer(host: String, htspPort: Int) {
        validateServerAddress(host, htspPort)
        submit(SaveServerCommand(host, htspPort))
    }

    override suspend fun savePasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
    ) {
        validateServerAddress(host, htspPort)
        submit(SavePasswordServerCommand(host, htspPort, username, password))
    }

    override suspend fun clearProfile() {
        submit(ClearServerProfileCommand())
    }

    suspend fun selectStreamProfile(profileId: StreamProfileId?) {
        submit(SelectStreamProfileCommand(profileId))
    }

    suspend fun addAnonymous(host: String, port: Int, label: String? = null): SaveServerProfileResult {
        validateServerAddress(host, port)
        val normalizedLabel = ServerProfileSummary.normalizeLabel(label)
        return mutate {
            val list = profileStore.profiles() as? ServerProfileListResult.Available
                ?: return@mutate SaveServerProfileResult.Unavailable
            duplicate(list, host, port, null, noticeCenter)?.let {
                return@mutate SaveServerProfileResult.AlreadyExists(it)
            }
            saveResult(refreshAfterWrite(profileStore.addAnonymous(host, port, normalizedLabel, activate = true)))
        }
    }

    suspend fun addPassword(
        host: String,
        port: Int,
        username: String,
        password: String,
        label: String? = null,
    ): SaveServerProfileResult {
        validateServerAddress(host, port)
        val normalizedLabel = ServerProfileSummary.normalizeLabel(label)
        return mutate {
            val list = profileStore.profiles() as? ServerProfileListResult.Available
                ?: return@mutate SaveServerProfileResult.Unavailable
            duplicate(list, host, port, username, noticeCenter)?.let {
                return@mutate SaveServerProfileResult.AlreadyExists(it)
            }
            saveResult(refreshAfterWrite(
                profileStore.addPassword(host, port, username, password, normalizedLabel, activate = true),
            ))
        }
    }

    suspend fun updateAnonymous(id: ServerProfileId, host: String, port: Int, label: String?): SaveServerProfileResult {
        validateServerAddress(host, port)
        val normalizedLabel = ServerProfileSummary.normalizeLabel(label)
        return mutate {
            val list = profileStore.profiles() as? ServerProfileListResult.Available
                ?: return@mutate SaveServerProfileResult.Unavailable
            duplicate(list, host, port, null, noticeCenter, excluding = id)?.let {
                return@mutate SaveServerProfileResult.AlreadyExists(it)
            }
            saveResult(refreshAfterWrite(
                profileStore.updateAnonymous(id, host, port, normalizedLabel), list.activeId == id,
            ))
        }
    }

    suspend fun updatePassword(
        id: ServerProfileId,
        host: String,
        port: Int,
        username: String,
        password: String,
        label: String?,
    ): SaveServerProfileResult {
        validateServerAddress(host, port)
        val normalizedLabel = ServerProfileSummary.normalizeLabel(label)
        return mutate {
            val list = profileStore.profiles() as? ServerProfileListResult.Available
                ?: return@mutate SaveServerProfileResult.Unavailable
            duplicate(list, host, port, username, noticeCenter, excluding = id)?.let {
                return@mutate SaveServerProfileResult.AlreadyExists(it)
            }
            saveResult(refreshAfterWrite(
                profileStore.updatePassword(id, host, port, username, password, normalizedLabel), list.activeId == id,
            ))
        }
    }

    /** Labels do not change the connection or require readable credentials. */
    suspend fun rename(id: ServerProfileId, label: String?): ServerProfileWriteResult {
        val normalizedLabel = ServerProfileSummary.normalizeLabel(label)
        return mutate {
            val result = profileStore.updateLabel(id, normalizedLabel)
            mutableProfiles.value = profileStore.profiles()
            result
        }
    }

    suspend fun setActive(id: ServerProfileId): ServerProfileWriteResult = mutate {
        if ((mutableProfiles.value as? ServerProfileListResult.Available)?.activeId == id) {
            return@mutate ServerProfileWriteResult.Applied(id)
        }
        refreshAfterWrite(profileStore.setActive(id))
    }

    /**
     * Tests the target on the shared connection; persistence changes only after sign-in succeeds.
     * The switch's own outcome (switched, can't connect, sign-in rejected) goes to [onOutcome] when
     * the caller shows it itself, else to the notice center; a failed write is always a notice.
     */
    suspend fun switchActive(
        id: ServerProfileId,
        onOutcome: ((Notice) -> Unit)? = null,
        beforeConnect: suspend () -> Unit = {},
    ): ServerProfileWriteResult =
        connectionChanges.withLock {
            val list = availableProfiles() ?: return@withLock unavailableSwitch(noticeCenter)
            if (list.activeId == id) return@withLock ServerProfileWriteResult.Applied(id)
            val target = loadSwitchTarget(id) ?: return@withLock unavailableSwitch(noticeCenter)
            val previous = profileStore.loadActive()
            val previousId = list.activeId
            val generation = configurationGeneration.value
            val caller = currentCoroutineContext()
            var persisted = false
            var committed = false
            var failureNotice: Notice? = null
            val switchedNotice = switchedProfileNotice(list, id)
            beginSwitch(caller)
            try {
                beforeConnect()
                switchIdentity.lock()
                try {
                    session.disconnect()
                    val before = session.observation.value
                    if (session.connect(target.profile) != SessionCommandResult.STARTED) {
                        failureNotice = connectionFailedNotice(list, id)
                        return@withLock ServerProfileWriteResult.Unavailable
                    }
                    val outcome = awaitSwitchOutcome(session, before)
                    currentCoroutineContext().ensureActive()
                    if (configurationGeneration.value != generation)
                        return@withLock ServerProfileWriteResult.Unavailable
                    if (outcome is SessionState.Unavailable) {
                        failureNotice = failedSwitchNotice(list, id, outcome)
                        return@withLock ServerProfileWriteResult.Unavailable
                    }
                    withContext(NonCancellable) {
                        val result = profileStore.setActive(id)
                        if (result is ServerProfileWriteResult.Applied) {
                            persisted = true
                            // With no previous selection there is no rollback API or connection to
                            // return to: once persistence starts, that successful selection wins.
                            if (previousId != null) caller.ensureActive()
                            publishSwitchedProfile(id, target)
                            committed = true
                        } else {
                            postSwitchNotice(noticeCenter, Notice.ServerProfileWriteFailed)
                        }
                        result
                    }
                } finally {
                    try {
                        if (shouldRestoreSwitch(committed, caller, persisted, previousId) {
                                switchGenerationMatches(configurationGeneration, generation, committed) })
                            withContext(NonCancellable) {
                                restoreSwitch(persisted, previousId, previous, caller, onOutcome, failureNotice)
                        } else if (committed && caller.isActive) {
                            // The retained outcome: no rollback ran. Generic connection notices stay suppressed
                            // until the switch ends, so this is the only one.
                            reportSwitch(onOutcome, switchedNotice)
                        }
                    } finally {
                        switchIdentity.unlock()
                    }
                }
            } finally {
                endSwitch()
            }
        }

    private fun reportSwitch(onOutcome: ((Notice) -> Unit)?, notice: Notice?) {
        if (onOutcome != null) notice?.let(onOutcome) else postSwitchNotice(noticeCenter, notice)
    }

    private fun availableProfiles() = profiles.value as? ServerProfileListResult.Available

    private suspend fun loadSwitchTarget(id: ServerProfileId) =
        profileStore.load(id) as? ServerProfileReadResult.Available

    private fun beginSwitch(caller: CoroutineContext) = synchronized(switchLifecycle) {
        if (switchOwnerClosed) throw CancellationException("Profile owner stopped before switch execution")
        activeSwitch = caller[Job]
        mutableSwitchInProgress.value = true
        mutableLiveProfile.value = null
    }

    private fun endSwitch() = synchronized(switchLifecycle) {
        activeSwitch = null
        mutableSwitchInProgress.value = false
        mutableLiveProfile.value = publishedProfileId(mutableServerProfile.value, setupTrialConnection)
    }

    private suspend fun publishSwitchedProfile(id: ServerProfileId, target: ServerProfileReadResult.Available) {
        playerSettings.useProfile(id)
        val profiles = profileStore.profiles()
        // No suspension follows the generation change: a partial publication still belongs to
        // the original generation and must roll back, just like a failed persistence operation.
        mutableConfigurationGeneration.value += 1
        audioProfileId = id.value
        mutableProfiles.value = profiles
        mutableServerProfile.value = target
    }

    private suspend fun restoreSwitch(
        persisted: Boolean,
        previousId: ServerProfileId?,
        previous: ServerProfileReadResult,
        caller: CoroutineContext,
        onOutcome: ((Notice) -> Unit)?,
        failureNotice: Notice?,
    ) {
        val restored = if (persisted) {
            readBackSwitchRollback(profileStore, previousId, mutableProfiles, noticeCenter)
        } else previous
        applyServerProfile(restored)
        // Cleanup must finish, but cancellation has no user-facing outcome.
        if (caller.isActive) reportSwitch(onOutcome, failureNotice)
    }

    suspend fun remove(id: ServerProfileId): ServerProfileWriteResult = mutate { removeProfile(id) }

    private suspend fun refreshAfterWrite(
        result: ServerProfileWriteResult,
        affectsActive: Boolean = true,
    ): ServerProfileWriteResult {
        if (result is ServerProfileWriteResult.Applied && affectsActive) mutableConfigurationGeneration.value += 1
        mutableProfiles.value = profileStore.profiles()
        if (result is ServerProfileWriteResult.Applied &&
            (mutableProfiles.value as? ServerProfileListResult.Available)?.activeId == result.id) {
            applyServerProfile(profileStore.loadActive())
        }
        return result
    }

    private suspend fun removeProfile(id: ServerProfileId): ServerProfileWriteResult {
        val active = (mutableProfiles.value as? ServerProfileListResult.Available)?.activeId == id ||
            (mutableServerProfile.value as? ServerProfileReadResult.Available)?.id == id
        if (active) {
            mutableConfigurationGeneration.value += 1
            session.disconnect()
            audioProfileId = null
            playerSettings.useProfile(null)
            mutableServerProfile.value = ServerProfileReadResult.Missing
        }
        val listResult = profileStore.profiles()
        val list = listResult as? ServerProfileListResult.Available
        val summary = list?.profiles?.find { it.id == id }
        if (summary == null) {
            mutableProfiles.value = listResult
            if (active) applyServerProfile(profileStore.loadActive())
            return if (list == null) ServerProfileWriteResult.Unavailable else ServerProfileWriteResult.NotFound
        }
        session.cache.clear(summary)
        val result = profileStore.remove(id)
        mutableProfiles.value = profileStore.profiles()
        if (result is ServerProfileWriteResult.Applied) {
            try {
                playerSettings.removeProfile(id)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            } finally {
                noticeCenter()?.let { center ->
                    center.post(Notice.ServerProfileRemoved(id, serverProfileDisplayName(summary)), center.context())
                }
            }
        } else if (active) {
            applyServerProfile(profileStore.loadActive())
        }
        return result
    }

    private suspend fun <T> mutate(operation: suspend () -> T): T {
        val command = MutateServerCommand(operation)
        submit(command)
        @Suppress("UNCHECKED_CAST")
        return command.result as T
    }

    suspend fun selectedStreamProfileIdFor(
        observation: CurrentSessionObservation,
    ): StreamProfileId? {
        combine(mutableSettledStreamProfilesFor, session.observation) { settledFor, current ->
            settledFor === observation || current.currentSession !== observation
        }.first { it }
        return streamMutex.withLock {
            mutableSelectedStreamProfileId.value.takeIf {
                availableForObservation === observation &&
                    session.observation.value.currentSession === observation
            }
        }
    }

    private suspend fun submit(command: ProfileCommand) {
        var submitted = false
        suspend fun submitAndAwait() {
            currentCoroutineContext().ensureActive()
            // Non-suspending admission has no prompt-cancellation gap between delivery and
            // recording ownership. A cancelled sender must still await an admitted command.
            commands.trySend(command).getOrThrow()
            submitted = true
            // Admission transfers ownership to run(); cancellation cannot release the session
            // to a setup test while that command is still changing it.
            withContext(NonCancellable) { command.completion.await() }
        }
        try {
            if (command is ServerProfileCommand) connectionChanges.withLock { submitAndAwait() }
            else submitAndAwait()
        } finally {
            // A caller cancelled while waiting for setup cleanup never reached the channel.
            if (!submitted) command.releaseSensitiveMaterial()
        }
    }

    private suspend fun handleCommand(command: ProfileCommand) {
        var failure: Exception? = null
        val generationBefore = mutableConfigurationGeneration.value
        try {
            when (command) {
                is SaveServerCommand -> commitServer(command.host, command.port)
                is SavePasswordServerCommand -> commitPasswordServer(
                    command.host,
                    command.port,
                    command.username,
                    command.password,
                )
                is ClearServerProfileCommand -> clearServerProfile()
                is SelectStreamProfileCommand -> commitStreamProfileSelection(command.profileId)
                is MutateServerCommand<*> -> serverMutex.withLock { command.execute() }
            }
        } catch (error: Exception) {
            try {
                if (command is ServerProfileCommand &&
                    (command !is MutateServerCommand<*> || mutableConfigurationGeneration.value != generationBefore)) {
                    if (mutableConfigurationGeneration.value == generationBefore) {
                        mutableConfigurationGeneration.value += 1
                    }
                    recoverServerProfile()
                }
            } catch (recoveryError: Throwable) {
                command.releaseSensitiveMaterial()
                command.completion.completeExceptionally(recoveryError)
                throw recoveryError
            }
            failure = error
        } catch (error: Throwable) {
            command.releaseSensitiveMaterial()
            command.completion.completeExceptionally(error)
            throw error
        }
        command.releaseSensitiveMaterial()
        if (failure == null) {
            command.completion.complete(Unit)
        } else {
            command.completion.completeExceptionally(failure)
        }
    }

    private suspend fun commitServer(host: String, htspPort: Int) = serverMutex.withLock {
        val list = profileStore.profiles() as? ServerProfileListResult.Available ?: error("Server profiles unavailable")
        val id = list.activeId
        duplicate(list, host, htspPort, null, noticeCenter, excluding = id)?.let {
            if (id == null) check(refreshAfterWrite(profileStore.setActive(it)) is ServerProfileWriteResult.Applied)
            return@withLock
        }
        val result = if (id == null) profileStore.addAnonymous(host, htspPort, activate = true)
        else profileStore.updateAnonymous(id, host, htspPort, list.profiles.find { it.id == id }?.label)
        check(refreshAfterWrite(result) is ServerProfileWriteResult.Applied)
    }

    private suspend fun commitPasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
    ) = serverMutex.withLock {
        val list = profileStore.profiles() as? ServerProfileListResult.Available ?: error("Server profiles unavailable")
        val id = list.activeId
        duplicate(list, host, htspPort, username, noticeCenter, excluding = id)?.let {
            if (id == null) check(refreshAfterWrite(profileStore.setActive(it)) is ServerProfileWriteResult.Applied)
            return@withLock
        }
        val result = if (id == null) profileStore.addPassword(host, htspPort, username, password, activate = true)
        else profileStore.updatePassword(
            id, host, htspPort, username, password, list.profiles.find { it.id == id }?.label,
        )
        check(refreshAfterWrite(result) is ServerProfileWriteResult.Applied)
    }

    private suspend fun clearServerProfile() = serverMutex.withLock {
        val id = (mutableProfiles.value as? ServerProfileListResult.Available)?.activeId ?: return@withLock
        check(removeProfile(id) is ServerProfileWriteResult.Applied)
    }

    private suspend fun commitStreamProfileSelection(
        profileId: StreamProfileId?,
    ) = streamMutex.withLock {
        val observation = availableForObservation ?: return@withLock
        val available = mutableStreamProfiles.value as? StreamProfilesResult.Available
            ?: return@withLock
        if (session.observation.value.currentSession !== observation) return@withLock
        if (profileId != null && available.profiles.none { it.id == profileId }) return@withLock

        playerSettings.setStreamProfile(profileId)
        if (
            session.observation.value.currentSession === observation &&
            availableForObservation === observation &&
            mutableStreamProfiles.value === available
        ) {
            mutableSelectedStreamProfileId.value = profileId
        }
    }

    private suspend fun observeStreamProfiles() {
        session.observation
            .map { it.currentSession }
            .distinctUntilChanged { previous, current -> previous === current }
            .collectLatest { observation ->
                streamMutex.withLock {
                    availableForObservation = null
                    mutableSettledStreamProfilesFor.value = null
                    mutableSelectedStreamProfileId.value = null
                    mutableStreamProfiles.value = StreamProfilesResult.Failed(TvheadendFailure.NOT_READY)
                }
                if (observation == null) return@collectLatest

                val result = try {
                    withContext(ioDispatcher) { session.getStreamProfiles(observation) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    StreamProfilesResult.Failed(TvheadendFailure.TRANSPORT_UNAVAILABLE)
                }
                if (session.observation.value.currentSession !== observation) {
                    return@collectLatest
                }

                // Discovery may finish during a trial connection. Resolve preferences only once
                // its identity has committed (or the previous connection has been restored).
                switchIdentity.withLock { streamMutex.withLock {
                    if (session.observation.value.currentSession !== observation) {
                        return@withLock
                    }
                    val selected = if (result is StreamProfilesResult.Available) {
                        try {
                            playerSettings.resolveStreamProfileSelection(result.profiles) {
                                session.observation.value.currentSession === observation
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    if (session.observation.value.currentSession !== observation) {
                        return@withLock
                    }
                    availableForObservation = observation.takeIf {
                        result is StreamProfilesResult.Available
                    }
                    mutableSelectedStreamProfileId.value = selected
                    mutableStreamProfiles.value = result
                    mutableSettledStreamProfilesFor.value = observation
                } }
            }
    }

    private suspend fun initializeServerProfile() {
        try {
            serverMutex.withLock {
                mutableProfiles.value = profileStore.profiles()
                applyServerProfile(profileStore.loadActive())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            try {
                session.disconnect()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The safe profile state still has to become observable.
            }
            mutableServerProfile.value = ServerProfileReadResult.Unavailable
        }
    }

    private suspend fun recoverServerProfile() {
        val readback = try {
            mutableProfiles.value = profileStore.profiles()
            profileStore.loadActive()
        } catch (_: Exception) {
            null
        }
        if (readback != null) {
            try {
                applyServerProfile(readback)
                return
            } catch (_: Exception) {
                // Fall through to a safe disconnected presentation.
            }
        }
        try {
            session.disconnect()
        } catch (_: Exception) {
            // The released profile is still unavailable to the app.
        }
        mutableServerProfile.value = ServerProfileReadResult.Unavailable
        mutableLiveProfile.value = null
    }

    private suspend fun applyServerProfile(profile: ServerProfileReadResult) {
        val id = (profile as? ServerProfileReadResult.Available)?.id
        mutableLiveProfile.value = null
        audioProfileId = null
        session.disconnect()
        playerSettings.useProfile(id)
        when (profile) {
            is ServerProfileReadResult.Available -> session.connect(profile.profile)
            ServerProfileReadResult.Missing,
            ServerProfileReadResult.Unavailable -> Unit
        }
        audioProfileId = id?.value
        mutableServerProfile.value = profile
        setSetupTrialConnection(false)
    }
}

private sealed class ProfileCommand {
    val completion = CompletableDeferred<Unit>()

    open fun releaseSensitiveMaterial() = Unit

    final override fun toString(): String = "ProfileCommand(<redacted>)"
}

private fun switchGenerationMatches(generationState: StateFlow<Long>, generation: Long, committed: Boolean): Boolean =
    generationState.value == generation || committed && generationState.value == generation + 1

private fun unavailableSwitch(noticeCenter: () -> NoticeCenter?): ServerProfileWriteResult.Unavailable {
    noticeCenter()?.let { it.post(Notice.ServerProfileWriteFailed, it.context()) }
    return ServerProfileWriteResult.Unavailable
}

private fun shouldRestoreSwitch(
    committed: Boolean,
    caller: CoroutineContext,
    persisted: Boolean,
    previousId: ServerProfileId?,
    generationMatches: () -> Boolean,
): Boolean =
    when {
        // Without an old selection, failed publication must reconcile the persisted target,
        // even though a completed switch cannot roll back to "no connection".
        !committed -> generationMatches()
        !caller.isActive -> switchCanRestore(persisted, previousId) && generationMatches()
        else -> false
    }

private fun switchCanRestore(persisted: Boolean, previousId: ServerProfileId?): Boolean =
    !persisted || previousId != null

private fun saveResult(result: ServerProfileWriteResult): SaveServerProfileResult {
    return when (result) {
        is ServerProfileWriteResult.Applied -> SaveServerProfileResult.Applied(result.id)
        ServerProfileWriteResult.NotFound -> SaveServerProfileResult.NotFound
        ServerProfileWriteResult.Unavailable -> SaveServerProfileResult.Unavailable
    }
}

private sealed class ServerProfileCommand : ProfileCommand()

private class SaveServerCommand(
    val host: String,
    val port: Int,
) : ServerProfileCommand()

private class SavePasswordServerCommand(
    val host: String,
    val port: Int,
    username: String,
    password: String,
) : ServerProfileCommand() {
    var username = username
        private set
    var password = password
        private set
    private var released = false

    override fun releaseSensitiveMaterial() = synchronized(this) {
        if (released) return@synchronized
        released = true
        username = ""
        password = ""
    }
}

private class ClearServerProfileCommand : ServerProfileCommand()

private class MutateServerCommand<T>(operation: suspend () -> T) : ServerProfileCommand() {
    private var operation: (suspend () -> T)? = operation
    var result: T? = null
        private set
    suspend fun execute() { result = checkNotNull(operation).invoke() }
    override fun releaseSensitiveMaterial() { operation = null }
}

sealed interface SaveServerProfileResult {
    data class Applied(val id: ServerProfileId) : SaveServerProfileResult
    data class AlreadyExists(val id: ServerProfileId) : SaveServerProfileResult
    data object NotFound : SaveServerProfileResult
    data object Unavailable : SaveServerProfileResult
}

private class SelectStreamProfileCommand(
    val profileId: StreamProfileId?,
) : ProfileCommand()

fun ServerProfileReadResult.toServerSettings(): ServerSettings = when (this) {
    is ServerProfileReadResult.Available -> serverSettingsForEditing(
        host = host,
        htspPort = port,
        passwordConfigured = authenticationMode == ServerProfileAuthenticationMode.PASSWORD,
    )
    ServerProfileReadResult.Missing,
    ServerProfileReadResult.Unavailable -> ServerSettings()
}
