package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.components.backKeyCycleFocus
import at.bernhardberger.tvhplayer.ui.components.rememberBackKeyCycle
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseContentLayering
import at.bernhardberger.tvhplayer.ui.components.PlaybackOptionsOverlayFrame
import at.bernhardberger.tvhplayer.ui.components.browseDetailsViewport
import at.bernhardberger.tvhplayer.ui.player.OptionsHeader
import at.bernhardberger.tvhplayer.ui.player.OptionsPageHost
import at.bernhardberger.tvhplayer.ui.player.optionsPageKeys
import at.bernhardberger.tvhplayer.ui.player.optionsPageEndKey
import at.bernhardberger.tvhplayer.ui.components.PanelListEdges
import at.bernhardberger.tvhplayer.ui.player.PlaybackOptionRow
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelHeaderTop
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelPadding
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelWidth
import at.bernhardberger.tvhplayer.ui.player.containedFocus
import kotlinx.coroutines.flow.first

/** A line of a browse side panel: a quiet section heading, a divider, a choice or a switch. */
internal sealed interface BrowsePanelEntry {
    data class Section(val title: String) : BrowsePanelEntry
    /** Sets a lone row apart from the section above it, where a heading would only repeat its label. */
    data object Divider : BrowsePanelEntry
    /**
     * A row; [checked] makes it a switch, [level] a row that opens a level (see [BrowseLevelsPanel])
     * and [dot] marks it with a small color dot before the label. [leading] and [trailing] put a
     * short value such as a clock before or after the label; a row that is not [enabled] is drawn
     * disabled and skipped by focus. [modifier] lets the panel follow or move the row's focus.
     * [opensLevel] only draws the chevron and sends Right to [onClick], for a panel that keeps its
     * own levels.
     */
    data class Choice(
        val key: String,
        val label: String,
        val selected: Boolean,
        val onClick: () -> Unit,
        val icon: Int? = null,
        val checked: Boolean? = null,
        val supporting: String? = null,
        val opensLevel: Boolean = false,
        val dot: Color? = null,
        val leading: String? = null,
        val trailing: String? = null,
        val enabled: Boolean = true,
        val modifier: Modifier = Modifier,
        val level: BrowsePanelLevel? = null,
        /** False for a row whose OK does something else ([onClick]); Right still opens the [level]. */
        val levelOnOk: Boolean = true,
    ) : BrowsePanelEntry
}

/** What a root row opens in a [BrowseLevelsPanel]: a header naming it, and its own lines. */
internal class BrowsePanelLevel(
    val title: String,
    val entries: List<BrowsePanelEntry>,
    /** Shown under the title and, unless the row has a [BrowsePanelEntry.Choice.supporting] text, on the root row. */
    val currentValue: String? = null,
    val radio: Boolean = true,
    val focusKey: String? = null,
)

/**
 * The floating side panel over the dimmed browse shell, the rail included. It holds focus until
 * Back closes it; the release of the key that opened it never reaches a row. Back runs [onClose],
 * so a panel with a second pane or levels can step back to the first instead. A panel with levels
 * passes [showHeader] false and draws each level's own header inside [OptionsPageHost].
 */
@Composable
internal fun BrowseSidePanel(
    title: String,
    panelTag: String,
    onClose: () -> Unit,
    width: Dp = PlaybackPanelWidth,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier,
    atRoot: Boolean = true,
    content: @Composable () -> Unit,
) {
    val layering = LocalBrowseContentLayering.current
    val scrimKey = remember { Any() }
    val scrim = remember { mutableFloatStateOf(TvScrimModalAlpha) }
    DisposableEffect(layering) {
        layering?.modalScrims?.set(scrimKey, scrim)
        onDispose { layering?.modalScrims?.remove(scrimKey) }
    }
    var openingReleasePending by remember { mutableStateOf(true) }
    val backCycle = rememberBackKeyCycle()
    BackHandler { onClose() }
    PlaybackOptionsOverlayFrame(
        paneTitle = title,
        panelTag = panelTag,
        panelWidth = width,
        modifier = modifier.optionsPageKeys(atRoot = atRoot, onBack = onClose)
            .backKeyCycleFocus(backCycle)
            .browseDetailsViewport()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back) {
                    return@onPreviewKeyEvent backCycle.handle(event.type == KeyEventType.KeyDown,
                        event.nativeKeyEvent.repeatCount, event.nativeKeyEvent.isCanceled, onBack = onClose)
                }
                val activation = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (!activation || !openingReleasePending) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    openingReleasePending = false
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) openingReleasePending = false
                    true
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(top = PlaybackPanelHeaderTop)) {
            if (showHeader) OptionsHeader(title = title, currentValue = null, onBack = null)
            content()
        }
    }
}

/**
 * The one browse side panel. [entries] are its root; a [BrowsePanelEntry.Choice] with a
 * [BrowsePanelEntry.Choice.level] shows its current value and a chevron, and OK or Right opens
 * the level in the same panel, as in the player's options sheet: the header names the level, a
 * choice applies at once and the level stays on the row just pressed. Left or Back returns to
 * the root with focus on its opener, or closes the root. Left/Right mirror in RTL.
 * Without any level the panel is one flat list under the [title].
 */
@Composable
internal fun BrowseLevelsPanel(
    title: String,
    panelTag: String,
    onClose: () -> Unit,
    entries: List<BrowsePanelEntry>,
    radio: Boolean = false,
    focusKey: String? = null,
) {
    if (entries.none { it is BrowsePanelEntry.Choice && it.level != null }) {
        BrowseSidePanel(title, panelTag, onClose) { BrowsePanelList(entries, radio, focusKey) }
    } else {
        BrowsePanelLevels(title, panelTag, onClose, entries, radio, focusKey)
    }
}

@Composable
private fun BrowsePanelLevels(
    title: String,
    panelTag: String,
    onClose: () -> Unit,
    entries: List<BrowsePanelEntry>,
    radio: Boolean,
    focusKey: String?,
) {
    var openKey by remember { mutableStateOf<String?>(null) }
    var opener by remember { mutableStateOf<String?>(null) }
    fun levelOf(key: String?) = entries.firstNotNullOfOrNull { entry ->
        (entry as? BrowsePanelEntry.Choice)?.takeIf { it.key == key }?.level
    }
    val shown = openKey?.takeIf { levelOf(it) != null }
    BrowseSidePanel(
        title = title,
        panelTag = panelTag,
        onClose = { if (shown != null) openKey = null else onClose() },
        showHeader = false,
        atRoot = shown == null,
    ) {
        OptionsPageHost(shown, { if (it == null) 0 else 1 }, Modifier.fillMaxSize()) { key ->
            val level = levelOf(key)
            Column {
                OptionsHeader(title = level?.title ?: title, currentValue = level?.currentValue, onBack = null)
                if (level == null) {
                    val root = entries.map { it.openingItsLevel { opened -> openKey = opened; opener = opened } }
                    BrowsePanelList(root, radio, opener ?: focusKey)
                } else {
                    BrowsePanelList(level.entries, level.radio, level.focusKey)
                }
            }
        }
    }
}

/** A root row with a level shows the level's value and a chevron, and opens it on Right (and on OK unless it has its own). */
@Composable
private fun BrowsePanelEntry.openingItsLevel(open: (String) -> Unit): BrowsePanelEntry {
    if (this !is BrowsePanelEntry.Choice || level == null) return this
    val opens = { open(key) }
    return copy(
        opensLevel = true,
        supporting = supporting ?: level.currentValue,
        onClick = if (levelOnOk) opens else onClick,
        modifier = if (levelOnOk) modifier else modifier.rightOpens(opens),
    )
}

/** Right opens on its first press and is always consumed, ahead of the row's own handling. */
@Composable
private fun Modifier.rightOpens(open: () -> Unit): Modifier {
    val endKey = optionsPageEndKey()
    return onPreviewKeyEvent { event ->
        if (event.key != endKey) {
            false
        } else {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) open()
            true
        }
    }
}

/**
 * One renderer for every panel list; the checked choice, or the [focusKey] choice when given,
 * takes focus as the panel opens, unless [takeFocus] is false. [radio] marks choices with radio
 * buttons, otherwise the checked one carries a trailing check.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BrowsePanelList(
    entries: List<BrowsePanelEntry>,
    radio: Boolean,
    focusKey: String? = null,
    takeFocus: Boolean = true,
) {
    // Only enabled choices take part in focus, so Up and Down step over disabled ones.
    val choices = entries.filterIsInstance<BrowsePanelEntry.Choice>().filter { it.enabled }
    val requesterStore = remember { mutableMapOf<String, FocusRequester>() }
    val choiceKeys = choices.map { it.key }
    val requesters = remember(choiceKeys) { choiceKeys.map { requesterStore.getOrPut(it) { FocusRequester() } } }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        val target = choices.indexOfFirst { it.key == focusKey }.takeIf { it >= 0 }
            ?: choices.indexOfFirst { it.selected }.coerceAtLeast(0)
        val lazyIndex = entries.indexOf(choices.getOrNull(target) ?: return@LaunchedEffect)
        // Wait one frame so the row is ready to draw its focused state.
        withFrameNanos { }
        // The list opens at its top; only a checked row below the panel scrolls into view.
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == lazyIndex }) {
            listState.scrollToItem(lazyIndex)
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == lazyIndex } }.first { it }
        }
        runCatching { requesters[target].requestFocus() }
    }
    PanelListEdges(listState) { edges ->
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(TvSpacing8),
            contentPadding = PaddingValues(
                start = PlaybackPanelPadding,
                end = PlaybackPanelPadding,
                // A section heading brings its own room below the title.
                top = 4.dp + if (entries.firstOrNull() is BrowsePanelEntry.Choice) BrowsePanelTitleGap else 0.dp,
                bottom = PlaybackPanelPadding,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(edges)
                .focusGroup(),
        ) {
            items(entries, key = { entry ->
                when (entry) {
                    is BrowsePanelEntry.Section -> "section-${entry.title}"
                    BrowsePanelEntry.Divider -> "divider"
                    is BrowsePanelEntry.Choice -> entry.key
                }
            }) { entry ->
                when (entry) {
                    is BrowsePanelEntry.Section -> Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextTertiaryAlpha),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() },
                    )
                    BrowsePanelEntry.Divider -> Box(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(1.dp)
                            .background(MaterialTheme.colorScheme.borderVariant),
                    )
                    is BrowsePanelEntry.Choice -> {
                        val index = choices.indexOf(entry)
                        PlaybackOptionRow(
                            label = entry.label,
                            selected = entry.selected,
                            radio = radio,
                            onClick = entry.onClick,
                            leadingIcon = entry.icon,
                            checked = entry.checked,
                            supportingLabel = entry.supporting,
                            showChevron = entry.opensLevel,
                            leadingDot = entry.dot,
                            leadingLabel = entry.leading,
                            trailingLabel = entry.trailing,
                            enabled = entry.enabled,
                            modifier = Modifier
                                .then(if (index < 0) Modifier.focusProperties { canFocus = false }
                                    else Modifier.containedFocus(requesters[index], requesters, index, lazy = true))
                                .then(entry.modifier)
                                .testTag(entry.key),
                        )
                    }
                }
            }
        }
    }
}

/** The kit drawer's room between its title and the first item. */
private val BrowsePanelTitleGap = 16.dp
