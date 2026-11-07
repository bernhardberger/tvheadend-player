package at.bernhardberger.tvhplayer.settings

/** A missing/loading stored version does not display a marker. */
internal fun hasUnseenWhatsNew(seenVersion: Int?, currentVersion: Int): Boolean =
    seenVersion != null && seenVersion < currentVersion

/** Missing preferences on a fresh install are seen; existing installs discover the new reader. */
internal fun whatsNewUnseen(
    seenVersion: Int?,
    currentVersion: Int,
    firstInstallTime: Long,
    lastUpdateTime: Long,
): Boolean = if (seenVersion == null) firstInstallTime != lastUpdateTime else hasUnseenWhatsNew(seenVersion, currentVersion)
