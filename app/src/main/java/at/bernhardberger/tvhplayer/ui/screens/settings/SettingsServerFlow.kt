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
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.existingServerProfile
import at.bernhardberger.tvhplayer.ui.SettingsSection
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
    private val activeId get() = list?.activeId
    var switchingNow by mutableStateOf(false)
        private set
    private var switchJob: kotlinx.coroutines.Job? = null
    private var switchRevision = 0L
    fun cancelSwitch() { switchJob?.cancel() }

    val actions = ServerProfileActions(
        onUse = use@ { profile ->
            if (profile.id == activeId) cancelSwitch()
            else if (profile.authenticationMode == ServerProfileAuthenticationMode.PASSWORD &&
                !profile.credentialsAvailable)
                enterPassword(switchJob, switchRevision, scope, { switchRevision }) {
                    setup = PASSWORD + profile.id.value
                }
            else if (switchingNow) use(profile)
            else if (interruption() == PlaybackInterruption.NONE) use(profile) else switching = profile.id.value
        },
        onEnterPassword = { profile ->
            enterPassword(switchJob, switchRevision, scope, { switchRevision }) {
                setup = PASSWORD + profile.id.value
            }
        },
        onSetUp = { if (!switchingNow) setup = EDIT + it.id.value },
        onName = { if (!switchingNow) setup = NAME + it.id.value },
        onRemove = { if (!switchingNow) removing = it.id.value },
        onAdd = { if (!switchingNow) setup = ADD },
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
        leaveServerLevel(navigation, profile.id)
        switchingNow = true
        val previous = switchJob
        val revision = ++switchRevision
        previous?.cancel()
        switchJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.ATOMIC) {
            try {
                previous?.join()
                // Waits for a closed setup's restore, which would otherwise reconnect the old server.
                owner.switchActive(profile.id) { stopPlaying(interruption, stopPlayback) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notices()?.let { it.post(Notice.ServerProfileWriteFailed, it.context()) }
            } finally { if (revision == switchRevision) switchingNow = false }
        }
    }

    private fun remove(profile: ServerProfileSummary) {
        val remaining = list?.profiles.orEmpty().filterNot { it.id == profile.id }
        val next = remaining.firstOrNull { it.id == activeId } ?: remaining.firstOrNull()
        leaveServerLevel(navigation, profile.id)
        pendingFocus = next?.let { serverProfileRowId(it.id) }
        pendingEnter = false
        // Removing the last server leaves no connection; the app root then shows first-run setup.
        val active = profile.id == activeId
        scope.launch {
            if (active) stopPlaying(interruption, stopPlayback)
            owner.remove(profile.id)
        }
    }

    /** A server that is already stored replaces the setup with its level and the notice. */
    private fun showExisting(id: ServerProfileId) {
        setup = null
        // Editing starts one level deeper than adding. Return to the list before selecting
        // the duplicate, otherwise the pending selection can never enter its server level.
        if (list?.profiles?.any { navigation.stack.active.levelId == serverProfileRowId(it.id) } == true)
            navigation.pop()
        pendingFocus = serverProfileRowId(id)
        pendingEnter = true
    }

    /** Another stored server with this address and user opens its level with the notice instead. */
    private fun showIfStored(host: String, port: Int, username: String, editing: ServerProfileId?): Boolean {
        val others = list?.profiles?.filterNot { it.id == editing } ?: return false
        val existing = existingServerProfile(others, host, port, username.ifBlank { null })
        return if (existing == null) false else {
            notices()?.let { it.post(Notice.ServerProfileAlreadyExists, it.context()) }
            showExisting(existing)
            true
        }
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
            if (target == null || navigation.stack.active.levelId != SettingsSection.CONNECTION.name)
                return@LaunchedEffect
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
        return if (request == ADD) ({
            AddServer(owner, interruption, { stopPlaying(interruption, stopPlayback) }, ::showExisting,
                onSaved = { setup = null; focusActiveRow() },
                existingProfile = { host, port, username -> showIfStored(host, port, username, editing = null) },
                onAddName = { setup = activeId?.let { ADDED_NAME + it.value } })
        }) else {
            val target = findProfile(request.substringAfter(':'), list)
            if (target == null) {
                LaunchedEffect(request) { setup = null }
                null
            } else when {
                request.startsWith(EDIT) -> ({
                    EditServer(target, SetupMode.EDIT, owner, activeId, interruption,
                        { stopPlaying(interruption, stopPlayback) }, ::showExisting,
                        onSaved = { setup = null },
                        existingProfile = { host, port, username -> showIfStored(host, port, username, target.id) })
                })
                request.startsWith(PASSWORD) -> ({
                    EditServer(target, SetupMode.PASSWORD, owner, activeId, interruption,
                        { stopPlaying(interruption, stopPlayback) }, ::showExisting,
                        // Entering the password makes it the server in use, like "Use this server".
                        onSaved = { setup = null; leaveServerLevel(navigation, target.id) },
                        existingProfile = { host, port, username -> showIfStored(host, port, username, target.id) })
                })
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
    }

    @Composable
    fun dialogContent(): (@Composable () -> Unit)? {
        findProfile(switching, list)?.let { profile ->
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
        return findProfile(removing, list)?.let { profile ->
            val name = serverProfileTitle(profile)
            val active = profile.id == activeId
            {
                RemoveServerDialog(name, active,
                    onRemove = {
                        removing = null
                        remove(profile)
                    },
                    onKeep = { removing = null })
            }
        }
    }
}

private fun findProfile(id: String?, list: ServerProfileListResult.Available?): ServerProfileSummary? =
    id?.let { value -> list?.profiles?.firstOrNull { it.id.value == value } }

private fun enterPassword(
    previous: kotlinx.coroutines.Job?,
    revision: Long,
    scope: CoroutineScope,
    currentRevision: () -> Long,
    onReady: () -> Unit,
) {
    previous?.cancel()
    scope.launch {
        previous?.join()
        if (revision == currentRevision()) onReady()
    }
}

/**
 * Another server's session ends what plays, and warm playback would otherwise retune its
 * channel on whichever session comes next. Stopped here, it stays stopped like any other stop.
 */
private suspend fun stopPlaying(interruption: () -> PlaybackInterruption, stopPlayback: suspend () -> Unit) {
    if (interruption() != PlaybackInterruption.NONE) stopPlayback()
}

private fun leaveServerLevel(navigation: DepthNavigationState, id: ServerProfileId) {
    if (navigation.stack.active.levelId == serverProfileRowId(id)) navigation.pop()
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
