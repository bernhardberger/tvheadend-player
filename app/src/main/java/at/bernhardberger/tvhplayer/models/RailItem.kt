package at.bernhardberger.tvhplayer.models

import androidx.compose.runtime.Composable

data class RailItem<T>(
    val route: T,
    val label: String,
    val icon: @Composable () -> Unit
)
