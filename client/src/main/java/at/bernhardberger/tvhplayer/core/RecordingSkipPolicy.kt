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

private val RecordingMark.entryStartMs: Long get() = if (kind == RecordingMarkKind.PROGRAM_START) 0 else startMs
private val RecordingMark.entryEndMs: Long get() = if (kind == RecordingMarkKind.PROGRAM_START) startMs else endMs
private fun RecordingMark.containsEntry(positionMs: Long): Boolean =
    positionMs >= entryStartMs && positionMs < entryEndMs

/** A controls action is position-based, including deliberate entries and dismissed offers. */
fun recordingSkipAction(marks: List<RecordingMark>, positionMs: Long, settings: PlayerSettings): RecordingMark? =
    marks.firstOrNull { it.kind == RecordingMarkKind.PROGRAM_START && it.containsEntry(positionMs) }
        ?: marks.firstOrNull {
            settings.adBreakMode != AdBreakMode.OFF && it.skippableAt(positionMs, settings.skipLanding)
        }

fun recordingMuted(cutpoints: List<DvrCutpoint>, positionMs: Long): Boolean = cutpoints.any {
    it.action == DvrCutpointAction.MUTE && positionMs >= it.start.inWholeMilliseconds && positionMs < it.end.inWholeMilliseconds
}

/** One installed recording's entry history. Deliberate seeks suppress entry until that span exits. */
class RecordingSkipPolicy {
    private var occupied = emptySet<RecordingMark>()
    private var deliberateMinimum: Long? = null
    private var deliberateMaximum: Long? = null
    private val skippedAds = mutableListOf<RecordingMark>()
    var offeredPrompt: RecordingMark? = null
        private set
    var currentAdBreak: RecordingMark? = null
        private set

    fun dismissPrompt() { offeredPrompt = null }

    fun deliberateSeek(marks: List<RecordingMark>, positionMs: Long, landing: SkipLanding = SkipLanding.END) {
        deliberateMinimum = positionMs
        deliberateMaximum = positionMs
        occupied = marks.filter { it.containsEntry(positionMs) }.toSet()
        currentAdBreak = occupied.firstOrNull { it.skippableAt(positionMs, landing) }
        offeredPrompt = null
    }

    fun evaluate(marks: List<RecordingMark>, positionMs: Long, playing: Boolean, settings: PlayerSettings): RecordingSkipDecision {
        deliberateMinimum = deliberateMinimum?.coerceAtMost(positionMs)
        deliberateMaximum = deliberateMaximum?.coerceAtLeast(positionMs)
        val inside = marks.filter { it.containsEntry(positionMs) }.toSet()
        currentAdBreak = inside.firstOrNull { it.skippableAt(positionMs, settings.skipLanding) }
        offeredPrompt = offeredPrompt?.let { previous ->
            if (previous.kind == RecordingMarkKind.PROGRAM_START) {
                previous.takeIf { settings.offerProgramStart && it in inside }
            }
            else currentAdBreak?.takeIf { current ->
                // A cutpoint refresh can revise the same break without creating another entry.
                settings.adBreakMode == AdBreakMode.ASK &&
                    previous.startMs < current.endMs && current.startMs < previous.endMs
            }
        }
        // Pausing does not forget entry history; a first resume inside a span still enters it.
        occupied = inside.filter { mark ->
            (deliberateMinimum?.let { mark.containsEntry(it) } == true &&
                deliberateMaximum?.let { mark.containsEntry(it) } == true) ||
                occupied.any { previous -> previous.kind == mark.kind &&
                    previous.entryStartMs < mark.entryEndMs && mark.entryStartMs < previous.entryEndMs }
        }.toSet()
        if (!playing) return RecordingSkipDecision()
        val entered = inside - occupied
        val cut = entered.firstOrNull { it.kind == RecordingMarkKind.CUT && settings.skipCuts }
        if (cut != null) {
            occupied = occupied + cut
            return RecordingSkipDecision(recordingSkipLanding(cut, settings.skipLanding))
        }
        val previouslyOfferedAd = offeredPrompt?.takeIf { it.kind == RecordingMarkKind.AD_BREAK }
        if (settings.offerProgramStart) entered.firstOrNull { it.kind == RecordingMarkKind.PROGRAM_START }?.let {
            offeredPrompt = it
        }
        // A suppressed or displaced Ask entry stays eligible until it leaves the break;
        // a cutpoint refresh may move its landing ahead again while the programme prompt is up.
        occupied = inside.filterNot {
            it.kind == RecordingMarkKind.AD_BREAK && settings.adBreakMode == AdBreakMode.ASK &&
                offeredPrompt?.kind == RecordingMarkKind.PROGRAM_START && (it in entered || it == previouslyOfferedAd)
        }.toSet()
        val ad = entered.firstOrNull { it.kind == RecordingMarkKind.AD_BREAK } ?: return RecordingSkipDecision()
        return when (settings.adBreakMode) {
            AdBreakMode.ASK -> {
                if (offeredPrompt?.kind != RecordingMarkKind.PROGRAM_START) {
                    offeredPrompt = ad.takeIf { it.skippableAt(positionMs, settings.skipLanding) }
                }
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

    fun acceptAction(expected: RecordingMark, marks: List<RecordingMark>, positionMs: Long, settings: PlayerSettings): Long? {
        if (expected != recordingSkipAction(marks, positionMs, settings)) return null
        return if (expected.kind == RecordingMarkKind.PROGRAM_START) {
            offeredPrompt = null
            expected.startMs
        } else skipAdBreak(expected, settings)
    }

    fun acceptPrompt(expected: RecordingMark, settings: PlayerSettings): Long? {
        return when {
            expected.kind != RecordingMarkKind.PROGRAM_START -> skipAdBreak(expected, settings)
            expected != offeredPrompt -> null
            else -> {
                offeredPrompt = null
                expected.startMs
            }
        }
    }

    fun skipAdBreak(expected: RecordingMark, settings: PlayerSettings): Long? {
        if (expected != currentAdBreak || settings.adBreakMode == AdBreakMode.OFF) return null
        offeredPrompt = null
        // The landing point ends the break as current, before the seek's position arrives.
        currentAdBreak = null
        return recordingSkipLanding(expected, settings.skipLanding)
    }
}
