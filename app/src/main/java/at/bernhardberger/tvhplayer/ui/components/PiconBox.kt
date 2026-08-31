package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage

@Composable
fun PiconBox(
    imageLoader: ImageLoader,
    piconPath: String?,
    modifier: Modifier = Modifier,
    currentSession: CurrentSessionObservation? = null,
    serverTag: String = "default",
    contentScale: ContentScale = ContentScale.Fit,
) {
    val piconUrl = remember(currentSession, serverTag, piconPath) {
        currentSession?.let {
            at.bernhardberger.tvhplayer.core.resolvePiconModel(it, serverTag, piconPath)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (piconUrl == null) {
            PiconPlaceholder(modifier = Modifier.fillMaxSize(0.5f))
        } else {
            SubcomposeAsyncImage(
                model = piconUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                loading = { PiconPlaceholder(modifier = Modifier.fillMaxSize(0.5f)) },
                error = { PiconPlaceholder(modifier = Modifier.fillMaxSize(0.5f)) },
            )
        }
    }
}

@Composable
fun PiconPlaceholder(
    modifier: Modifier = Modifier,
    initials: String? = null,
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
