package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun TvRecoveryOverlay(
    visible: Boolean,
    message: String,
    hint: String? = null,
    opaque: Boolean = true,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(visible, retryLabel) {
        if (visible && retryLabel != null) {
            runCatching { retryFocus.requestFocus() }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (opaque) Color.Black else Color.Black.copy(alpha = 0.86f))
                .padding(48.dp)
                .focusable(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (onRetry == null) {
                CircularProgressIndicator(color = Color.White)
            }
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = if (onRetry == null) 24.dp else 0.dp)
                    .widthIn(max = 680.dp)
                    .semantics { heading() },
            )
            if (hint != null) {
                Text(
                    text = hint,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .widthIn(max = 680.dp),
                )
            }
            if (retryLabel != null && onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .focusRequester(retryFocus),
                ) {
                    Text(retryLabel)
                }
            }
        }
    }
}
