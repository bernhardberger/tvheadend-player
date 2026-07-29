package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.htsp.ChannelTagUi
import at.bernhardberger.tvhplayer.ui.CompactChannelCardWidth
import at.bernhardberger.tvhplayer.ui.TvNavigationRailGradientLateAlpha
import at.bernhardberger.tvhplayer.ui.TvNavigationRailGradientRunout
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha

@Composable
fun ChannelTagSelector(
    tags: List<ChannelTagUi>,
    activeTagId: Int?,
    onSelectTag: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    allChannelsVisible: Boolean = true,
    activeFocusRequester: FocusRequester = remember { FocusRequester() },
    onMoveToContent: () -> Boolean = { false },
) {
    val allChannelsLabel = stringResource(R.string.all_channels)
    val scopes = remember(tags, allChannelsVisible, allChannelsLabel) {
        buildList {
            if (allChannelsVisible) add(null to allChannelsLabel)
            addAll(tags.map { it.id to it.name })
        }
    }
    if (scopes.isEmpty()) return

    val activeIndex = scopes.indexOfFirst { it.first == activeTagId }.coerceAtLeast(0)
    val layoutDirection = LocalLayoutDirection.current
    val edgeVeilState = remember(scopes) { TabEdgeVeilState() }
    val shellFocusProtection = LocalNavigationVeilFocusProtection.current
    SideEffect {
        edgeVeilState.reportProtectedBounds(activeIndex, shellFocusProtection)
    }
    DisposableEffect(shellFocusProtection) {
        onDispose { shellFocusProtection?.invoke(null) }
    }
    val scheme = MaterialTheme.colorScheme
    val tabColors = TabDefaults.pillIndicatorTabColors(
        contentColor = scheme.onSurface.copy(alpha = TvTextSecondaryAlpha),
        inactiveContentColor = scheme.onSurface.copy(alpha = TvTextSecondaryAlpha),
        selectedContentColor = scheme.onSurface,
        focusedContentColor = scheme.inverseOnSurface,
        focusedSelectedContentColor = scheme.inverseOnSurface,
        disabledContentColor = scheme.onSurface.copy(alpha = TvTextDisabledAlpha),
        disabledInactiveContentColor = scheme.onSurface.copy(alpha = TvTextDisabledAlpha),
        disabledSelectedContentColor = scheme.onSurface.copy(alpha = TvTextDisabledAlpha),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer(activeFocusRequester)
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    onMoveToContent()
            }
            .onGloballyPositioned(edgeVeilState::updateViewportBounds)
            .navigationEdgeVeil(
                width = TvNavigationRailGradientRunout,
                startAlpha = TvNavigationRailGradientLateAlpha,
                availableWidthPx = {
                    edgeVeilState.availableVeilWidthPx(
                        activeIndex = activeIndex,
                        layoutDirection = layoutDirection,
                        maximumWidthPx = TvNavigationRailGradientRunout.toPx(),
                    )
                },
            ),
    ) {
        TabRow(
            selectedTabIndex = activeIndex,
            modifier = Modifier.fillMaxWidth(),
        ) {
            scopes.forEachIndexed { index, (tagId, label) ->
                Tab(
                    selected = index == activeIndex,
                    onFocus = {
                        if (tagId != activeTagId) onSelectTag(tagId)
                    },
                    onClick = {
                        if (tagId != activeTagId) onSelectTag(tagId)
                        onMoveToContent()
                    },
                    colors = tabColors,
                    modifier = Modifier
                        .onFocusChanged { focusState ->
                            edgeVeilState.updateFocusedIndex(
                                index = index,
                                focused = focusState.isFocused,
                                activeIndex = activeIndex,
                                shellFocusProtection = shellFocusProtection,
                            )
                        }
                        .onGloballyPositioned { coordinates ->
                            edgeVeilState.updateTabBounds(index, coordinates)
                            edgeVeilState.reportProtectedBounds(
                                activeIndex,
                                shellFocusProtection,
                            )
                        }
                        .then(
                            if (index == activeIndex) {
                                Modifier.focusRequester(activeFocusRequester)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(
                                horizontal = TvSpacing16,
                                vertical = TvSpacing8,
                            )
                            .widthIn(max = CompactChannelCardWidth),
                    )
                }
            }
        }
    }
}

private class TabEdgeVeilState {
    private val viewportBounds = mutableStateOf(Rect.Zero)
    private val tabBounds = mutableStateMapOf<Int, Rect>()
    private val focusedIndex = mutableIntStateOf(NoFocusedTab)

    fun updateViewportBounds(coordinates: LayoutCoordinates) {
        viewportBounds.value = coordinates.unclippedBoundsInRoot()
    }

    fun updateTabBounds(index: Int, coordinates: LayoutCoordinates) {
        tabBounds[index] = coordinates.unclippedBoundsInRoot()
    }

    fun updateFocusedIndex(
        index: Int,
        focused: Boolean,
        activeIndex: Int,
        shellFocusProtection: ((Rect?) -> Unit)?,
    ) {
        if (focused) {
            focusedIndex.intValue = index
        } else if (focusedIndex.intValue == index) {
            focusedIndex.intValue = NoFocusedTab
        }
        reportProtectedBounds(activeIndex, shellFocusProtection)
    }

    fun reportProtectedBounds(
        activeIndex: Int,
        shellFocusProtection: ((Rect?) -> Unit)?,
    ) {
        val activeBounds = tabBounds[activeIndex]
        val focusedBounds = tabBounds[focusedIndex.intValue]
        shellFocusProtection?.invoke(
            when {
                activeBounds == null -> focusedBounds
                focusedBounds == null -> activeBounds
                else -> activeBounds.expandToInclude(focusedBounds)
            },
        )
    }

    fun availableVeilWidthPx(
        activeIndex: Int,
        layoutDirection: LayoutDirection,
        maximumWidthPx: Float,
    ): Float {
        val viewport = viewportBounds.value
        val active = tabBounds[activeIndex] ?: return 0f
        val focusedTabIndex = focusedIndex.intValue
        val focused = tabBounds[focusedTabIndex]
        val hasDepartingInactiveTab = tabBounds.any { (index, bounds) ->
            index != activeIndex && index != focusedTabIndex && when (layoutDirection) {
                LayoutDirection.Ltr -> bounds.left < viewport.left && bounds.right > viewport.left
                LayoutDirection.Rtl -> bounds.right > viewport.right && bounds.left < viewport.right
            }
        }
        if (!hasDepartingInactiveTab) return 0f

        val activeSpace = active.spaceFromLeadingEdge(viewport, layoutDirection)
        val focusedSpace = focused?.spaceFromLeadingEdge(viewport, layoutDirection)
        val spaceBeforeProtected = if (focusedSpace == null) {
            activeSpace
        } else {
            minOf(activeSpace, focusedSpace)
        }
        return spaceBeforeProtected.coerceIn(0f, maximumWidthPx)
    }

    private companion object {
        const val NoFocusedTab = -1
    }
}

private fun LayoutCoordinates.unclippedBoundsInRoot(): Rect =
    Rect(offset = positionInRoot(), size = Size(size.width.toFloat(), size.height.toFloat()))

private fun Rect.expandToInclude(other: Rect): Rect = Rect(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom),
)

private fun Rect.spaceFromLeadingEdge(
    viewport: Rect,
    layoutDirection: LayoutDirection,
): Float = when (layoutDirection) {
    LayoutDirection.Ltr -> left - viewport.left
    LayoutDirection.Rtl -> viewport.right - right
}

@Composable
fun UnavailableTagNotice(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.active_tag_unavailable),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}
