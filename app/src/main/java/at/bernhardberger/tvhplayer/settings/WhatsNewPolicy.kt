package at.bernhardberger.tvhplayer.settings

/** A missing/loading stored version does not display a marker. */
internal fun hasUnseenWhatsNew(seenVersion: Int?, currentVersion: Int): Boolean =
    seenVersion != null && seenVersion < currentVersion

/** The update hint expires after fourteen days; loading and invalid timestamps stay quiet. */
internal fun whatsNewHintShown(
    seenVersion: Int?,
    currentVersion: Int,
    lastUpdateTime: Long?,
    now: Long,
): Boolean = hasUnseenWhatsNew(seenVersion, currentVersion) && lastUpdateTime != null &&
    lastUpdateTime > 0 && now >= lastUpdateTime && now - lastUpdateTime < 14L * 24 * 60 * 60 * 1000

/** Missing preferences on a fresh install are seen; existing installs discover the new reader. */
internal fun whatsNewUnseen(
    seenVersion: Int?,
    currentVersion: Int,
    firstInstallTime: Long,
    lastUpdateTime: Long,
): Boolean = if (seenVersion == null) firstInstallTime != lastUpdateTime else hasUnseenWhatsNew(seenVersion, currentVersion)
