package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrDiskSpace
import at.bernhardberger.tvheadend.sdk.core.DvrDiskSpaceState

/**
 * The recording storage figures worth showing, or null when there is nothing to show.
 * A stale value stays visible while the server is re-read; no value, or a total that
 * cannot anchor "free of", hides the surface instead of showing a placeholder.
 */
fun DvrDiskSpaceState.displayedDiskSpace(): DvrDiskSpace? =
    when (this) {
        is DvrDiskSpaceState.Current -> diskSpace
        is DvrDiskSpaceState.Stale -> diskSpace
        is DvrDiskSpaceState.Synchronizing -> staleDiskSpace
        DvrDiskSpaceState.Unknown -> null
    }?.takeIf { it.totalBytes > 0 }
