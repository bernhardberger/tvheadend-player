package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.MaterialTheme

/** Shared compact under-card channel caption, retaining the theme's medium weight. */
@Composable
internal fun channelCaptionStyle(): TextStyle = MaterialTheme.typography.labelMedium
