package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage

data class HtspPiconData(
    val serverTag: String,
    val path: String,
    val ttlMs: Long
)

@Composable
fun PiconBox(
    imageLoader: ImageLoader,
    serverTag: String = "default",
    piconPath: String?,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier
        .width(92.dp)
        .height(64.dp),
) {
    val piconUrl = remember(serverTag, piconPath) {
        at.bernhardberger.tvhplayer.core.resolvePiconModel(serverTag, piconPath)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (piconUrl == null) {
            PiconPlaceholder()
        } else {
            SubcomposeAsyncImage(
                model = piconUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                loading = { PiconPlaceholder() },
                error = { PiconPlaceholder() },
            )
        }
    }
}

@Composable
fun PiconPlaceholder(
    initials: String? = null,
    modifier: Modifier = Modifier.fillMaxSize(0.5f),
) {
    if (!initials.isNullOrBlank()) {
        androidx.tv.material3.Text(
            text = initials,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.LiveTv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}
