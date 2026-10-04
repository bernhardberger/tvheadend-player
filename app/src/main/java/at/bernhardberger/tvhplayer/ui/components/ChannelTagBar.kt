package at.bernhardberger.tvhplayer.ui.components

import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.ChannelScopeItemMaxWidth
import at.bernhardberger.tvhplayer.ui.TvNavigationRailGradientRunout

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
    AppTabRow(
        selectedTabIndex = activeIndex,
        style = AppTabStyle.Page,
        selectedTabFocus = activeFocusRequester,
        onMoveToContent = onMoveToContent,
        modifier = modifier
            .fillMaxWidth()
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
            val selected = index == activeIndex
            AppTab(
                selected = selected,
                label = label,
                labelModifier = Modifier.widthIn(max = ChannelScopeItemMaxWidth),
                onFocus = {
                    onTagFocus()
                    edgeFadeState.updateFocusedIndex(index)
                    onSelectTag(tagId)
                },
                onClick = {
                    onSelectTag(tagId)
                    onMoveToContent()
                },
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        edgeFadeState.updateTabBounds(index, coordinates)
                    },
            )
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
            containerColor = TvSurfaceColors.containerHigh,
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
            OutlinedButton(onClick = onDismiss, modifier = Modifier.tabFocus()) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}
