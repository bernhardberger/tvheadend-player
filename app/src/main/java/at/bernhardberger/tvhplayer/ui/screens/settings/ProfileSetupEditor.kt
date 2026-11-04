package at.bernhardberger.tvhplayer.ui.screens.settings

import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.SaveServerProfileResult
import at.bernhardberger.tvhplayer.settings.ServerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock

/**
 * The guided setup's editor for one stored server, or for a new one when [target] is null.
 * Adding activates the new server. Editing another server than the one in use reconnects the one
 * in use with [restoreActive] after the save, since the setup's test interrupted it; entering a
 * password again also makes [target] the server in use. A duplicate reaches [onAlreadyExists]
 * and fails the save, so the setup stays open until the caller shows the existing server.
 */
internal class ProfileSetupEditor(
    private val owner: AppProfileOwner,
    private val target: ServerProfileSummary?,
    private val activate: Boolean,
    private val restoreActive: suspend () -> Unit,
    private val onAlreadyExists: (ServerProfileId) -> Unit,
) : ConnectionProfileEditor {
    /** The server the setup tests against when it leaves early: the one in use. */
    override val serverSettings: Flow<ServerSettings> get() = owner.serverSettings

    override suspend fun loadServerForEditing(
        applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
    ) {
        val id = target?.id ?: return
        owner.loadServerForEditing(id, applyAvailable)
    }

    override suspend fun saveServer(host: String, htspPort: Int) {
        val target = target
        handle(
            if (target == null) owner.addAnonymous(host, htspPort)
            else owner.updateAnonymous(target.id, host, htspPort, target.label),
        )
    }

    override suspend fun savePasswordServer(host: String, htspPort: Int, username: String, password: String) {
        val target = target
        handle(
            if (target == null) owner.addPassword(host, htspPort, username, password)
            else owner.updatePassword(target.id, host, htspPort, username, password, target.label),
        )
    }

    /** The setup offers no removal; Settings removes a server from its own level. */
    override suspend fun clearProfile() = Unit

    private suspend fun handle(result: SaveServerProfileResult) {
        when (result) {
            is SaveServerProfileResult.Applied -> {
                val active = (owner.profiles.value as? ServerProfileListResult.Available)?.activeId
                when {
                    active == result.id -> Unit
                    activate -> owner.setActive(result.id)
                    else -> owner.connectionChanges.withLock { restoreActive() }
                }
            }
            is SaveServerProfileResult.AlreadyExists -> {
                onAlreadyExists(result.id)
                error("Server already stored")
            }
            SaveServerProfileResult.NotFound, SaveServerProfileResult.Unavailable -> error("Server not saved")
        }
    }
}
