package at.bernhardberger.tvhplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.DvrDiskSpaceState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.displayedDiskSpace

/** "412 GB free of 2 TB", or null when the server has reported nothing worth showing. */
@Composable
fun recordingSpaceText(state: DvrDiskSpaceState): String? {
    val space = state.displayedDiskSpace() ?: return null
    val context = LocalContext.current
    return stringResource(R.string.recording_space_value,
        formatFileSize(space.freeBytes.coerceAtLeast(0L), context), formatFileSize(space.totalBytes, context))
}
