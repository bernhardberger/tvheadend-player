package at.bernhardberger.tvhplayer.core

/** What of the playback card's heading survives its height cap. */
internal data class PlaybackCardFit(val band: Float, val titleLines: Int, val episodeLines: Int)

/**
 * Fits the playback card under [cap]. The picture band shrinks first, down to [band]'s start;
 * then the episode line drops lines, letting the band grow back as far as it can; then the title
 * drops lines, keeping one. [height] is everything but the band for the given line counts; what it
 * holds besides title and episode never gives way, so the result can still exceed [cap].
 */
internal fun fitPlaybackCard(
    cap: Float,
    band: ClosedFloatingPointRange<Float>,
    titleLines: Int,
    episodeLines: Int,
    height: (titleLines: Int, episodeLines: Int) -> Float,
): PlaybackCardFit {
    var title = titleLines.coerceAtLeast(1)
    var episode = episodeLines.coerceAtLeast(0)
    while (true) {
        val fitted = (cap - height(title, episode)).coerceAtMost(band.endInclusive)
        if (fitted >= band.start || (episode == 0 && title == 1)) {
            return PlaybackCardFit(fitted.coerceAtLeast(band.start), title, episode)
        }
        if (episode > 0) episode-- else title--
    }
}

/** The playback card's colours taken from its picture's or logo's hue. */
internal class PlaybackCardTint(val surface: Int, val halo: Int, val accent: Int)

/**
 * Derives the card's colours from a details tint ([detailsTintRgb]'s result), which keeps the
 * seed's hue. A tint without a usable hue (grey art or logo) gives none: the card stays neutral.
 */
internal fun playbackCardTint(detailsTint: Int): PlaybackCardTint? {
    val (hue, saturation, _) = rgbToHsv(detailsTint)
    if (saturation < MIN_TINT_SATURATION) return null
    return PlaybackCardTint(
        surface = hsvToRgb(hue, .18f, .135f),
        halo = hsvToRgb(hue, saturation.coerceIn(.35f, .60f), .62f),
        accent = normalizeAccentRgb(detailsTint),
    )
}

private const val MIN_TINT_SATURATION = .1f
