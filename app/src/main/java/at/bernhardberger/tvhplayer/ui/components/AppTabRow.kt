package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.TabRowScope
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8

internal enum class AppTabStyle { Page, Section }

/** Native TV indicators, matching item colours and restoration have one owner. */
@Composable
internal fun AppTabRow(
    selectedTabIndex: Int,
    style: AppTabStyle,
    modifier: Modifier = Modifier,
    selectedTabFocus: FocusRequester? = null,
    onUp: (() -> Unit)? = null,
    onMoveToContent: (() -> Boolean)? = null,
    tabs: @Composable AppTabScope.() -> Unit,
) {
    val selectedFocus = selectedTabFocus ?: remember { FocusRequester() }
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.wrapContentWidth(Alignment.Start)
            .focusRestorer(selectedFocus)
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.DirectionUp && onUp != null -> {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onUp()
                        true
                    }
                    event.key == Key.DirectionDown && onMoveToContent != null -> {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onMoveToContent() else true
                    }
                    else -> false
                }
            },
        indicator = { positions, focused ->
            positions.getOrNull(selectedTabIndex)?.let { position ->
                when (style) {
                    AppTabStyle.Page -> TabRowDefaults.PillIndicator(position, focused)
                    AppTabStyle.Section -> TabRowDefaults.UnderlinedIndicator(position, focused)
                }
            }
        },
    ) { AppTabScope(this, style, selectedFocus).tabs() }
}

internal class AppTabScope internal constructor(
    private val row: TabRowScope,
    private val style: AppTabStyle,
    private val selectedFocus: FocusRequester,
) {
    @Composable
    fun AppTab(
        selected: Boolean,
        label: String,
        onFocus: () -> Unit,
        modifier: Modifier = Modifier,
        labelModifier: Modifier = Modifier,
        onClick: () -> Unit = {},
        canFocus: Boolean = true,
        first: Boolean = false,
        last: Boolean = false,
    ) {
        with(row) {
            Tab(
                selected = selected,
                onFocus = onFocus,
                onClick = onClick,
                modifier = modifier.then(if (selected) Modifier.focusRequester(selectedFocus) else Modifier).focusProperties {
                    this.canFocus = canFocus
                    if (first) start = FocusRequester.Cancel
                    if (last) end = FocusRequester.Cancel
                },
                colors = when (style) {
                    AppTabStyle.Page -> TabDefaults.pillIndicatorTabColors()
                    AppTabStyle.Section -> TabDefaults.underlinedIndicatorTabColors()
                },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = TvSpacing16, vertical = TvSpacing8).then(labelModifier),
                )
            }
        }
    }
}
