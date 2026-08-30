package at.bernhardberger.tvhplayer.settings

import android.content.Context
import at.bernhardberger.tvheadend.sdk.android.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.android.ServerProfileOperationResult
import at.bernhardberger.tvheadend.sdk.android.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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

/** Process owner for the one persisted server profile and current-generation stream selection. */
fun interface CredentialEditLease {
    fun release()
}

internal interface ConnectionProfileEditor {
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
        credentialLease: CredentialEditLease,
    )

    suspend fun clearProfile()
}

class AppProfileOwner internal constructor(
    private val context: Context,
    private val session: TvheadendSession,
    private val profileStore: TvheadendServerProfileStore,
    private val legacyCredentials: LegacyCredentialSource,
    private val playerSettings: PlayerSettingsStore,
    private val ioDispatcher: CoroutineDispatcher,
) : ConnectionProfileEditor {
    private val serverMutex = Mutex()
    private val streamMutex = Mutex()
    private val commands = Channel<ProfileCommand>(
        onUndeliveredElement = ProfileCommand::releaseSensitiveMaterial,
    )
    private val mutableServerProfile = MutableStateFlow<ServerProfileReadResult?>(null)
    private val mutableStreamProfiles =
        MutableStateFlow<StreamProfilesResult>(StreamProfilesResult.NotReady)
    private val mutableSelectedStreamProfileId = MutableStateFlow<StreamProfileId?>(null)
    private val mutableSettledStreamProfilesFor =
        MutableStateFlow<CurrentSessionObservation?>(null)
    private var availableForObservation: CurrentSessionObservation? = null

    val serverProfile: StateFlow<ServerProfileReadResult?> = mutableServerProfile.asStateFlow()
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
            commands.close()
            discoveryJob.cancelAndJoin()
        }
    }

    override suspend fun loadServerForEditing(
        applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
    ) = serverMutex.withLock {
        when (val result = profileStore.loadProfileForEditing()) {
            is ServerProfileEditReadResult.Anonymous -> applyAvailable(
                result.host,
                result.port,
                "",
                "",
            )
            is ServerProfileEditReadResult.Password -> applyAvailable(
                result.host,
                result.port,
                result.username,
                result.password,
            )
            ServerProfileEditReadResult.Missing,
            ServerProfileEditReadResult.Unavailable,
            -> Unit
        }
    }

    override suspend fun saveServer(host: String, htspPort: Int) {
        submit(SaveServerCommand(host, htspPort))
    }

    suspend fun savePasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
    ) {
        submit(
            SavePasswordServerCommand(
                host,
                htspPort,
                username,
                password,
                CredentialEditLease {},
            ),
        )
    }

    override suspend fun savePasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
        credentialLease: CredentialEditLease,
    ) {
        submit(SavePasswordServerCommand(host, htspPort, username, password, credentialLease))
    }

    override suspend fun clearProfile() {
        submit(ClearServerProfileCommand())
    }

    suspend fun selectStreamProfile(profileId: StreamProfileId?) {
        submit(SelectStreamProfileCommand(profileId))
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
        commands.send(command)
        command.completion.await()
    }

    private suspend fun handleCommand(command: ProfileCommand) {
        var failure: Exception? = null
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
            }
        } catch (error: Exception) {
            try {
                if (command is ServerProfileCommand) recoverServerProfile()
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
        mutableServerProfile.value = null
        val operation = profileStore.storeAnonymous(host, htspPort)
        val readback = profileStore.loadProfile()
        val verified =
            operation == ServerProfileOperationResult.SUCCESS &&
                readback.matchesEndpoint(host, htspPort, ServerProfileAuthenticationMode.ANONYMOUS)
        check(verified)
        clearLegacyProfileMaterial()
        applyServerProfile(readback)
    }

    private suspend fun commitPasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
    ) = serverMutex.withLock {
        mutableServerProfile.value = null
        val operation = profileStore.storePassword(host, htspPort, username, password)
        val readback = profileStore.loadProfile()
        val verified =
            operation == ServerProfileOperationResult.SUCCESS &&
                readback.matchesEndpoint(host, htspPort, ServerProfileAuthenticationMode.PASSWORD)
        check(verified)
        clearLegacyProfileMaterial()
        applyServerProfile(readback)
    }

    private suspend fun clearServerProfile() = serverMutex.withLock {
        mutableServerProfile.value = null
        val operation = profileStore.clearProfile()
        val readback = profileStore.loadProfile()
        val verified =
            operation == ServerProfileOperationResult.SUCCESS &&
                readback == ServerProfileReadResult.Missing
        check(verified)
        clearLegacyProfileMaterial()
        applyServerProfile(readback)
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
                    mutableStreamProfiles.value = StreamProfilesResult.NotReady
                }
                if (observation == null) return@collectLatest

                val result = try {
                    withContext(ioDispatcher) { session.getStreamProfiles(observation) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    StreamProfilesResult.TransportUnavailable
                }
                if (session.observation.value.currentSession !== observation) {
                    return@collectLatest
                }

                streamMutex.withLock {
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
                }
            }
    }

    private suspend fun initializeServerProfile() {
        try {
            serverMutex.withLock {
                applyServerProfile(loadOrMigrateServerProfile())
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
            profileStore.loadProfile()
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
    }

    private suspend fun loadOrMigrateServerProfile(): ServerProfileReadResult {
        val current = profileStore.loadProfile()
        if (current is ServerProfileReadResult.Available) {
            clearLegacyProfileMaterial()
            return current
        }
        if (current == ServerProfileReadResult.Unavailable) return current

        val legacy = context.loadLegacyServerProfile(legacyCredentials::loadPassword)
            ?.normalizedForMigration()
            ?: return current
        val operation = when (val password = legacy.password) {
            LegacyPassword.Empty -> profileStore.storeAnonymous(legacy.host, legacy.port)
            is LegacyPassword.Available -> profileStore.storePassword(
                legacy.host,
                legacy.port,
                legacy.username,
                password.value,
            )
            LegacyPassword.Unavailable -> return current
        }
        val readback = profileStore.loadProfile()
        if (operation == ServerProfileOperationResult.SUCCESS && readback.matchesLegacyProfile(legacy)) {
            clearLegacyProfileMaterial()
        }
        return readback
    }

    private suspend fun clearLegacyProfileMaterial() {
        legacyCredentials.clearCiphertext()
        context.clearLegacyServerEndpoint()
        legacyCredentials.deleteObsoleteKey()
    }

    private suspend fun applyServerProfile(profile: ServerProfileReadResult) {
        when (profile) {
            is ServerProfileReadResult.Available -> session.connect(profile.profile)
            ServerProfileReadResult.Missing,
            ServerProfileReadResult.Unavailable -> session.disconnect()
        }
        mutableServerProfile.value = profile
    }
}

private sealed class ProfileCommand {
    val completion = CompletableDeferred<Unit>()

    open fun releaseSensitiveMaterial() = Unit

    final override fun toString(): String = "ProfileCommand(<redacted>)"
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
    private val credentialLease: CredentialEditLease,
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
        credentialLease.release()
    }
}

private class ClearServerProfileCommand : ServerProfileCommand()

private class SelectStreamProfileCommand(
    val profileId: StreamProfileId?,
) : ProfileCommand()

internal fun LegacyServerProfile.normalizedForMigration(): LegacyServerProfile? {
    val normalized = copy(host = host.trim(), username = username.trim())
    val complete = normalized.host.isNotEmpty() && normalized.port in 1..65_535 && when {
        normalized.username.isEmpty() -> normalized.password == LegacyPassword.Empty
        else -> normalized.password is LegacyPassword.Available &&
            normalized.password.value.isNotBlank()
    }
    return normalized.takeIf { complete }
}

internal fun ServerProfileReadResult.matchesLegacyProfile(legacy: LegacyServerProfile): Boolean =
    this is ServerProfileReadResult.Available &&
        host == legacy.host &&
        port == legacy.port &&
        authenticationMode == if (legacy.password is LegacyPassword.Available) {
            ServerProfileAuthenticationMode.PASSWORD
        } else {
            ServerProfileAuthenticationMode.ANONYMOUS
        }

private fun ServerProfileReadResult.matchesEndpoint(
    host: String,
    port: Int,
    authenticationMode: ServerProfileAuthenticationMode,
): Boolean = this is ServerProfileReadResult.Available &&
    this.host == host.trim() &&
    this.port == port &&
    this.authenticationMode == authenticationMode

private fun ServerProfileReadResult.toServerSettings(): ServerSettings = when (this) {
    is ServerProfileReadResult.Available -> serverSettingsForEditing(
        host = host,
        htspPort = port,
        passwordConfigured = authenticationMode == ServerProfileAuthenticationMode.PASSWORD,
    )
    ServerProfileReadResult.Missing,
    ServerProfileReadResult.Unavailable -> ServerSettings()
}
