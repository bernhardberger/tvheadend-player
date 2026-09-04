package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
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
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.ChannelScopeItemMaxWidth
import at.bernhardberger.tvhplayer.ui.TvNavigationRailGradientRunout
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha

@Composable
fun ChannelTagSelector(
    tags: List<ChannelTag>,
    activeTagId: ChannelTagId?,
    onSelectTag: (ChannelTagId?) -> Unit,
    modifier: Modifier = Modifier,
    allChannelsVisible: Boolean = true,
    activeFocusRequester: FocusRequester = remember { FocusRequester() },
    onMoveToContent: () -> Boolean = { false },
    onTagFocus: () -> Unit = {},
) {
    val allChannelsLabel = stringResource(R.string.all_channels)
    val scopes = remember(tags, allChannelsVisible, allChannelsLabel) {
        buildList {
            if (allChannelsVisible) add(null to allChannelsLabel)
            addAll(tags.map { it.id to it.name.orEmpty() })
        }
    }
    if (scopes.isEmpty()) return

    val activeIndex = scopes.indexOfFirst { it.first == activeTagId }.coerceAtLeast(0)
    val layoutDirection = LocalLayoutDirection.current
    val edgeFadeState = remember(scopes) { TabEdgeFadeState() }
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
    TabRow(
        selectedTabIndex = activeIndex,
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer(activeFocusRequester)
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    onMoveToContent()
            }
            .onGloballyPositioned(edgeFadeState::updateViewportBounds)
            .navigationEdgeFadeMask(
                width = TvNavigationRailGradientRunout,
                maskEnabled = {
                    edgeFadeState.availableFadeWidthPx(
                        activeIndex = activeIndex,
                        layoutDirection = layoutDirection,
                        maximumWidthPx = Float.MAX_VALUE,
                    ) > 0f
                },
                availableWidthPx = {
                    edgeFadeState.availableFadeWidthPx(
                        activeIndex = activeIndex,
                        layoutDirection = layoutDirection,
                        maximumWidthPx = TvNavigationRailGradientRunout.toPx(),
                    )
                },
            ),
    ) {
        scopes.forEachIndexed { index, (tagId, label) ->
            Tab(
                selected = index == activeIndex,
                onFocus = {
                    onTagFocus()
                    edgeFadeState.updateFocusedIndex(index)
                    if (tagId != activeTagId) onSelectTag(tagId)
                },
                onClick = {
                    if (tagId != activeTagId) onSelectTag(tagId)
                    onMoveToContent()
                },
                colors = tabColors,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        edgeFadeState.updateTabBounds(index, coordinates)
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
                        .widthIn(max = ChannelScopeItemMaxWidth),
                )
            }
        }
    }
}

private class TabEdgeFadeState {
    private val viewportBounds = mutableStateOf(Rect.Zero)
    private val tabBounds = mutableStateMapOf<Int, Rect>()
    private val focusedIndex = mutableIntStateOf(-1)

    fun updateViewportBounds(coordinates: LayoutCoordinates) {
        viewportBounds.value = coordinates.unclippedBoundsInRoot()
    }

    fun updateTabBounds(index: Int, coordinates: LayoutCoordinates) {
        tabBounds[index] = coordinates.unclippedBoundsInRoot()
    }

    fun updateFocusedIndex(index: Int) {
        focusedIndex.intValue = index
    }

    fun availableFadeWidthPx(
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
}

private fun LayoutCoordinates.unclippedBoundsInRoot(): Rect =
    Rect(offset = positionInRoot(), size = Size(size.width.toFloat(), size.height.toFloat()))

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
