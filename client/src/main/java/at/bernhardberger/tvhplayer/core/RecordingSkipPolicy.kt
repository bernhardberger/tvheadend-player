package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.SkipLanding

/** [automaticAdSkip] is the ad break an automatic skip jumped over. */
data class RecordingSkipDecision(val seekToMs: Long? = null, val automaticAdSkip: RecordingMark? = null)

fun recordingSkipLanding(mark: RecordingMark, landing: SkipLanding): Long =
    (mark.endMs - landing.beforeEndMs).coerceAtLeast(mark.startMs)

/** From its landing point on, skipping an ad break would not move forward, so it is no longer current. */
private fun RecordingMark.skippableAt(positionMs: Long, landing: SkipLanding): Boolean =
    kind == RecordingMarkKind.AD_BREAK && contains(positionMs) && positionMs < recordingSkipLanding(this, landing)

fun recordingMuted(cutpoints: List<DvrCutpoint>, positionMs: Long): Boolean = cutpoints.any {
    it.action == DvrCutpointAction.MUTE && positionMs >= it.start.inWholeMilliseconds && positionMs < it.end.inWholeMilliseconds
}

/** One installed recording's entry history. Deliberate seeks suppress entry until that span exits. */
class RecordingSkipPolicy {
    private var occupied = emptySet<RecordingMark>()
    private var deliberateMinimum: Long? = null
    private var deliberateMaximum: Long? = null
    private val skippedAds = mutableListOf<RecordingMark>()
    var offeredAdBreak: RecordingMark? = null
        private set
    var currentAdBreak: RecordingMark? = null
        private set

    fun dismissPrompt() { offeredAdBreak = null }

    fun deliberateSeek(marks: List<RecordingMark>, positionMs: Long, landing: SkipLanding = SkipLanding.END) {
        deliberateMinimum = positionMs
        deliberateMaximum = positionMs
        occupied = marks.filter { it.contains(positionMs) }.toSet()
        currentAdBreak = occupied.firstOrNull { it.skippableAt(positionMs, landing) }
        offeredAdBreak = null
    }

    fun evaluate(marks: List<RecordingMark>, positionMs: Long, playing: Boolean, settings: PlayerSettings): RecordingSkipDecision {
        deliberateMinimum = deliberateMinimum?.coerceAtMost(positionMs)
        deliberateMaximum = deliberateMaximum?.coerceAtLeast(positionMs)
        val inside = marks.filter { it.contains(positionMs) }.toSet()
        currentAdBreak = inside.firstOrNull { it.skippableAt(positionMs, settings.skipLanding) }
        if (offeredAdBreak?.skippableAt(positionMs, settings.skipLanding) != true ||
            settings.adBreakMode != AdBreakMode.ASK) offeredAdBreak = null
        // Pausing does not forget entry history; a first resume inside a span still enters it.
        occupied = inside.filter { mark ->
            (deliberateMinimum?.let { mark.contains(it) } == true &&
                deliberateMaximum?.let { mark.contains(it) } == true) ||
                occupied.any { previous -> previous.kind == mark.kind &&
                    previous.startMs < mark.endMs && mark.startMs < previous.endMs }
        }.toSet()
        if (!playing) return RecordingSkipDecision()
        val entered = inside - occupied
        val cut = entered.firstOrNull { it.kind == RecordingMarkKind.CUT && settings.skipCuts }
        if (cut != null) {
            occupied = occupied + cut
            return RecordingSkipDecision(recordingSkipLanding(cut, settings.skipLanding))
        }
        occupied = inside
        val ad = entered.firstOrNull { it.kind == RecordingMarkKind.AD_BREAK } ?: return RecordingSkipDecision()
        return when (settings.adBreakMode) {
            AdBreakMode.ASK -> {
                offeredAdBreak = ad.takeIf { it.skippableAt(positionMs, settings.skipLanding) }
                RecordingSkipDecision()
            }
            AdBreakMode.AUTOMATIC -> {
                // A completion refetch may extend the same break; don't skip it a second time.
                if (skippedAds.any { it.startMs < ad.endMs && ad.startMs < it.endMs } ||
                    !ad.skippableAt(positionMs, settings.skipLanding)) RecordingSkipDecision()
                else {
                    skippedAds.add(ad)
                    if (currentAdBreak == ad) currentAdBreak = null
                    RecordingSkipDecision(recordingSkipLanding(ad, settings.skipLanding), automaticAdSkip = ad)
                }
            }
            AdBreakMode.OFF -> RecordingSkipDecision()
        }
    }

    fun skipAdBreak(expected: RecordingMark, settings: PlayerSettings): Long? {
        if (expected != currentAdBreak || settings.adBreakMode == AdBreakMode.OFF) return null
        offeredAdBreak = null
        // The landing point ends the break as current, before the seek's position arrives.
        currentAdBreak = null
        return recordingSkipLanding(expected, settings.skipLanding)
    }
}
