package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseContentLayering
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.ServerPickerPage
import at.bernhardberger.tvhplayer.ui.components.browseDetailsViewport
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import org.koin.compose.koinInject

/** The picker shares Settings' setup and confirmation owner instead of creating another flow. */
@Composable
internal fun ServerSwitcherScreen(
    initialFocusEnabled: Boolean,
    isCurrent: Boolean,
    onUsed: () -> Unit,
    onExisting: (at.bernhardberger.tvheadend.sdk.core.ServerProfileId) -> Unit,
    onRetained: () -> Unit = onUsed,
    owner: AppProfileOwner = koinInject(),
    interruption: () -> PlaybackInterruption = rememberPlaybackInterruption(),
    stopPlaying: suspend () -> Unit = rememberPlaybackStop(),
    contentPadding: PaddingValues = PaddingValues(),
    drawerOpen: Boolean = false,
    selectionPending: Boolean = false,
    onSelect: ((at.bernhardberger.tvheadend.sdk.core.ServerProfileId) -> Unit)? = null,
) {
    val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
    val state = rememberServerSettingsState(navigation, owner, interruption, stopPlaying,
        onUsed = onUsed, onExisting = onExisting, onRetained = onRetained)
    val hidden by owner.hiddenServerProfileIds.collectAsStateWithLifecycle<Set<at.bernhardberger.tvheadend.sdk.core.ServerProfileId>?>(initialValue = null)
    val list by owner.profiles.collectAsStateWithLifecycle()
    val available = list as? ServerProfileListResult.Available
    val profiles = at.bernhardberger.tvhplayer.settings.switcherProfiles(available?.profiles.orEmpty(), available?.activeId, hidden.orEmpty())
    val setup = state.setupContent()
    val dialog = state.dialogContent()
    val layering = LocalBrowseContentLayering.current
    val pageKey = remember { Any() }
    val opacity = remember { mutableFloatStateOf(1f) }
    DisposableEffect(layering, setup != null, isCurrent) {
        if (setup != null && isCurrent) layering?.details?.set(pageKey, opacity)
        onDispose { layering?.details?.remove(pageKey) }
    }
    // An admitted startup choice owns the decision until setActive and its reconnect settle.
    val busy = state.switchingNow || selectionPending
    BackHandler(enabled = busy) { }
    SideEffect { if (!isCurrent) { state.closeSetup(); state.closeDialogs() } }
    Box(Modifier.fillMaxSize()) {
        if (list == null || hidden == null) Text(stringResource(R.string.connection_overview_loading))
        else ServerPickerPage(profiles, list.activeProfile()?.id, { profile ->
                if (!busy) {
                    if (onSelect != null && profile.credentialsAvailable && profile.id != available?.activeId) onSelect(profile.id)
                    else state.actions.onUse(profile)
                }
             }, { if (!busy) state.actions.onAdd() },
             initialFocusEnabled && setup == null && dialog == null && !selectionPending,
             // Correct the collapsed shell's asymmetric safe insets from 503dp to the center
             // beside its 80dp rail (520dp). Fullscreen startup has no such correction.
             modifier = Modifier.padding(contentPadding).offset(x = if (!drawerOpen && layering != null) 17.dp else 0.dp)
                 .then(if (setup != null) Modifier.clearAndSetSemantics { } else Modifier)
                 .focusProperties { onEnter = { if (setup != null || dialog != null) cancelFocusChange() } }.focusGroup(),
             message = if (list is ServerProfileListResult.Unavailable) stringResource(R.string.server_list_unavailable) else null,
             contentFocusEnabled = setup == null && dialog == null && !selectionPending, drawerOpen = drawerOpen)
        if (setup != null) {
            BackHandler(onBack = state::closeSetup)
            Box(Modifier.browseDetailsViewport().fillMaxSize().background(MaterialTheme.colorScheme.background)) { setup() }
        }
        dialog?.invoke()
    }
}
