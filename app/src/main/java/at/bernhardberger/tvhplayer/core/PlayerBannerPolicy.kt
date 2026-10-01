package at.bernhardberger.tvhplayer.core

/**
 * A programme boundary on the watched channel shows the Banner,
 * but only with the chrome hidden, at the live edge and outside a seek preview.
 * [previousEventId] and [currentEventId] are the airing programme before and after
 * on the same channel; a channel change is a zap, not a programme change.
 */
fun programmeChangeBannerDue(
    previousEventId: Long?,
    currentEventId: Long?,
    chromeHidden: Boolean,
    atLiveEdge: Boolean,
    seekPreview: Boolean,
): Boolean = previousEventId != null && currentEventId != null && previousEventId != currentEventId &&
    chromeHidden && atLiveEdge && !seekPreview

/**
 * The Banner's hide timer starts on the first visible video frame
 * of the confirmed tune. Only a service whose tracks are positively audio-only has no frame
 * to wait for, so its playing state stands in; missing diagnostics never do.
 */
fun bannerFramePresented(
    tuneConfirmed: Boolean,
    videoFrameVisible: Boolean,
    playing: Boolean,
    audioOnly: Boolean,
): Boolean = tuneConfirmed && (videoFrameVisible || (playing && audioOnly))
