package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.existingServerProfile
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.depth.DepthItem
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val ADD = "add"
private const val EDIT = "edit:"
private const val PASSWORD = "password:"
private const val NAME = "name:"
/** The name step that follows adding; it returns to the list instead of the server's level. */
private const val ADDED_NAME = "added-name:"

/**
 * Settings > Connection's choices that leave the depth columns: the full-screen setup and name
 * steps and the switch and remove questions, each kept across recreation by its server's id.
 * After a choice Settings moves focus to the row it concerns once that row is on the list.
 */
@Stable
internal class ServerSettingsState(
    private val navigation: DepthNavigationState,
    private val owner: AppProfileOwner,
    private val interruption: () -> PlaybackInterruption,
    private val stopPlayback: suspend () -> Unit,
    private val scope: CoroutineScope,
    private val notices: () -> NoticeCenter?,
    setupState: MutableState<String?>,
    switchingState: MutableState<String?>,
    removingState: MutableState<String?>,
    pendingFocusState: MutableState<String?>,
    pendingEnterState: MutableState<Boolean>,
) {
    private var setup by setupState
    private var switching by switchingState
    private var removing by removingState
    private var pendingFocus by pendingFocusState
    private var pendingEnter by pendingEnterState

    private val list get() = owner.profiles.value as? ServerProfileListResult.Available
    private fun find(id: String?): ServerProfileSummary? = id?.let { value -> list?.profiles?.firstOrNull { it.id.value == value } }
    private val activeId get() = list?.activeId

    val actions = ServerProfileActions(
        onUse = { profile ->
            if (interruption() == PlaybackInterruption.NONE) use(profile) else switching = profile.id.value
        },
        onEnterPassword = { setup = PASSWORD + it.id.value },
        onSetUp = { setup = EDIT + it.id.value },
        onName = { setup = NAME + it.id.value },
        onShowInSwitcher = { profile, show -> scope.launch { owner.setShowInSwitcher(profile.id, show) } },
        onRemove = { removing = it.id.value },
        onAdd = { setup = ADD },
    )

    fun closeSetup() {
        setup = null
    }

    fun closeDialogs() {
        switching = null
        removing = null
    }

    /** Switches at once and returns to the list, where the row of the server now in use keeps focus. */
    private fun use(profile: ServerProfileSummary) {
        leaveServerLevel(profile.id)
        scope.launch {
            stopPlaying()
            // Waits for a closed setup's restore, which would otherwise reconnect the old server.
            owner.setActive(profile.id)
        }
    }

    /**
     * Another server's session ends what plays, and warm playback would otherwise retune its
     * channel on whichever session comes next. Stopped here, it stays stopped like any other stop.
     */
    private suspend fun stopPlaying() {
        if (interruption() != PlaybackInterruption.NONE) stopPlayback()
    }

    private fun leaveServerLevel(id: ServerProfileId) {
        if (navigation.stack.active.levelId == serverProfileRowId(id)) navigation.pop()
    }

    private fun remove(profile: ServerProfileSummary) {
        val remaining = list?.profiles.orEmpty().filterNot { it.id == profile.id }
        val next = remaining.firstOrNull { it.id == activeId } ?: remaining.firstOrNull()
        leaveServerLevel(profile.id)
        pendingFocus = next?.let { serverProfileRowId(it.id) }
        pendingEnter = false
        // Removing the last server leaves no connection; the app root then shows first-run setup.
        val active = profile.id == activeId
        scope.launch {
            if (active) stopPlaying()
            owner.remove(profile.id)
        }
    }

    /** A server that is already stored replaces the setup with its level and the notice. */
    private fun showExisting(id: ServerProfileId) {
        setup = null
        // Editing starts one level deeper than adding. Return to the list before selecting
        // the duplicate, otherwise the pending selection can never enter its server level.
        if (list?.profiles?.any { navigation.stack.active.levelId == serverProfileRowId(it.id) } == true) navigation.pop()
        pendingFocus = serverProfileRowId(id)
        pendingEnter = true
    }

    /** Another stored server with this address and user opens its level with the notice instead. */
    private fun showIfStored(host: String, port: Int, username: String, editing: ServerProfileId?): Boolean {
        val others = list?.profiles?.filterNot { it.id == editing } ?: return false
        val existing = existingServerProfile(others, host, port, username.ifBlank { null }) ?: return false
        notices()?.let { it.post(Notice.ServerProfileAlreadyExists, it.context()) }
        showExisting(existing)
        return true
    }

    private fun focusActiveRow() {
        pendingFocus = activeId?.let(::serverProfileRowId)
        pendingEnter = false
    }

    /** Moves focus, and enters the level, once the row is on the Connection list. */
    @Composable
    fun FocusPending(connection: DepthLevel?) {
        val target = pendingFocus
        val items = connection?.takeIf { it.id == SettingsSection.CONNECTION.name }?.rows?.map { it.item }.orEmpty()
        LaunchedEffect(target, items) {
            if (target == null || navigation.stack.active.levelId != SettingsSection.CONNECTION.name) return@LaunchedEffect
            val item = items.firstOrNull { it.id == target } ?: return@LaunchedEffect
            val focused = navigation.stack.focus(target, items)
            navigation.update(if (pendingEnter) focused.enter(item, items) else focused)
            pendingFocus = null
            pendingEnter = false
        }
    }

    @Composable
    fun setupContent(): (@Composable () -> Unit)? {
        val request = setup ?: return null
        if (request == ADD) return { AddServer() }
        val target = find(request.substringAfter(':'))
        if (target == null) {
            LaunchedEffect(request) { setup = null }
            return null
        }
        return when {
            request.startsWith(EDIT) -> ({ EditServer(target, SetupMode.EDIT) })
            request.startsWith(PASSWORD) -> ({ EditServer(target, SetupMode.PASSWORD) })
            else -> ({
                val added = request.startsWith(ADDED_NAME)
                SettingsServerName(
                    profile = target,
                    onSave = { label ->
                        scope.launch { owner.rename(target.id, label) }
                        setup = null
                        if (added) focusActiveRow()
                    },
                    onBack = {
                        setup = null
                        if (added) focusActiveRow()
                    },
                )
            })
        }
    }

    @Composable
    private fun AddServer(session: TvheadendSession = koinInject()) {
        val restore = rememberSavedConnectionRestore(session, owner)
        val editor = remember(restore) {
            ProfileSetupEditor(owner, target = null, activate = true, restoreActive = restore, onAlreadyExists = ::showExisting)
        }
        SettingsConnection(
            settingsStore = editor,
            session = session,
            restoreSaved = restore,
            mode = SetupMode.ADD,
            onSaved = {
                setup = null
                focusActiveRow()
            },
            interruptsPlayback = { interruption() != PlaybackInterruption.NONE },
            existingProfile = { host, port, username -> showIfStored(host, port, username, editing = null) },
            onAddName = { setup = activeId?.let { ADDED_NAME + it.value } },
            beforeTest = ::stopPlaying,
            sessionLock = owner.connectionChanges,
            connectionGeneration = { owner.configurationGeneration.value },
            confirmSwitch = { form, onSwitch, onKeep ->
                val host = form.host.trim()
                val username = form.username.trim().ifBlank { null }
                SwitchServerDialog(
                    name = username?.let { stringResource(R.string.server_user_on_host, it, host) } ?: host,
                    username = username,
                    interruption = interruption(),
                    onSwitch = onSwitch,
                    onKeep = onKeep,
                )
            },
        )
    }

    @Composable
    private fun EditServer(target: ServerProfileSummary, mode: SetupMode, session: TvheadendSession = koinInject()) {
        val restore = rememberSavedConnectionRestore(session, owner)
        val active = target.id == activeId
        val editor = remember(target.id, mode, restore) {
            ProfileSetupEditor(owner, target, activate = mode == SetupMode.PASSWORD, restoreActive = restore,
                onAlreadyExists = ::showExisting)
        }
        val name = serverProfileTitle(target)
        SettingsConnection(
            settingsStore = editor,
            session = session,
            restoreSaved = restore,
            mode = mode,
            profileName = name,
            onSaved = {
                setup = null
                // Entering the password makes it the server in use, like "Use this server".
                if (mode == SetupMode.PASSWORD) leaveServerLevel(target.id)
            },
            interruptsPlayback = { mode == SetupMode.PASSWORD && !active && interruption() != PlaybackInterruption.NONE },
            // Testing a server that isn't in use stops playback without asking (owner decision).
            // Checked before the test, so a duplicate stops nothing; the save would refuse it anyway.
            existingProfile = { host, port, username -> showIfStored(host, port, username, editing = target.id) },
            beforeTest = { if (!active) stopPlaying() },
            sessionLock = owner.connectionChanges,
            connectionGeneration = { owner.configurationGeneration.value },
            confirmSwitch = { form, onSwitch, onKeep ->
                SwitchServerDialog(name, form.username.trim().ifBlank { null }, interruption(), onSwitch, onKeep)
            },
        )
    }

    @Composable
    fun dialogContent(): (@Composable () -> Unit)? {
        find(switching)?.let { profile ->
            val username = profile.username?.takeIf {
                profile.authenticationMode == ServerProfileAuthenticationMode.PASSWORD
            }
            val name = serverProfileTitle(profile)
            return {
                SwitchServerDialog(name, username, interruption(),
                    onSwitch = {
                        switching = null
                        use(profile)
                    },
                    onKeep = { switching = null })
            }
        }
        find(removing)?.let { profile ->
            val name = serverProfileTitle(profile)
            val active = profile.id == activeId
            return {
                RemoveServerDialog(name, active,
                    onRemove = {
                        removing = null
                        remove(profile)
                    },
                    onKeep = { removing = null })
            }
        }
        return null
    }
}

@Composable
internal fun rememberServerSettingsState(
    navigation: DepthNavigationState,
    owner: AppProfileOwner,
    interruption: () -> PlaybackInterruption,
    stopPlayback: suspend () -> Unit,
): ServerSettingsState {
    val scope = rememberCoroutineScope()
    val notices = koinInjectOrNull()
    val currentInterruption by rememberUpdatedState(interruption)
    val currentStop by rememberUpdatedState(stopPlayback)
    val setup = rememberSaveable { mutableStateOf<String?>(null) }
    val switching = rememberSaveable { mutableStateOf<String?>(null) }
    val removing = rememberSaveable { mutableStateOf<String?>(null) }
    val pendingFocus = rememberSaveable { mutableStateOf<String?>(null) }
    val pendingEnter = rememberSaveable { mutableStateOf(false) }
    return remember(navigation, owner, scope) {
        ServerSettingsState(navigation, owner, { currentInterruption() }, { currentStop() }, scope, { notices },
            setup, switching, removing, pendingFocus, pendingEnter)
    }
}

/** Settings previews and tests that compose Settings without a notice host still work. */
@Composable
private fun koinInjectOrNull(): NoticeCenter? {
    val koin = org.koin.compose.getKoin()
    return remember(koin) { koin.getOrNull<NoticeCenter>() }
}
