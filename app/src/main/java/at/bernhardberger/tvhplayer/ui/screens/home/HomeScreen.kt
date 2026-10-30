package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Home's landing page. The page itself, including the scroll-away header, lives in
 * [HomeContent]; this slice still renders it with no sections, so View options stays the one
 * deterministic stop until the data slice hands the page real sections and focus state.
 */
@Composable
internal fun HomeScreen(
    contentPadding: PaddingValues,
    initialFocusEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    HomeContent(
        sections = emptyList(),
        contentPadding = contentPadding,
        initialFocus = null,
        onFocused = {},
        modifier = modifier,
        focusEnabled = initialFocusEnabled,
    )
}
