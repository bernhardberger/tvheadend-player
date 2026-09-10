@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.profiling

import androidx.media3.common.Format
import androidx.media3.container.NalUnitUtil
import at.bernhardberger.tvhplayer.BuildConfig

/** Numeric/allowlisted metadata only: never emit source labels, URLs or channel identifiers. */
internal fun profileFirstVideoFrame(epoch: Long, format: Format?, adapterName: String?) {
    if (!BuildConfig.PROFILE_TRACE) return
    profileTrace("P44:firstFrame:$epoch") {}
    val codec = when (format?.sampleMimeType) {
        "video/avc" -> "avc"
        "video/hevc" -> "hevc"
        "video/mpeg2" -> "mpeg2"
        else -> "unknown"
    }
    // This describes SPS coding capability, not whether the programme contains interlaced motion.
    val coding = if (codec == "avc") avcCoding(format) else "unknown"
    val source = when {
        adapterName?.contains("IPTV", ignoreCase = true) == true -> "iptvLabel"
        adapterName?.contains("DVB", ignoreCase = true) == true -> "dvbLabel"
        else -> "unknown"
    }
    profileTrace("P44:video:$epoch:$codec:${format?.width}x${format?.height}:${format?.frameRate}:$coding:$source") {}
}

private fun avcCoding(format: Format?): String {
    for (bytes in format?.initializationData.orEmpty()) {
        if (bytes.size !in 4..4096) continue
        val offset = when {
            bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 1.toByte() -> 3
            bytes.size > 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
                bytes[2] == 0.toByte() && bytes[3] == 1.toByte() -> 4
            else -> 0
        }
        if (bytes[offset].toInt() and 31 != 7) continue
        return try {
            if (NalUnitUtil.parseSpsNalUnit(bytes, offset, bytes.size).frameMbsOnlyFlag) {
                "frameOnly"
            } else {
                "fieldCapable"
            }
        } catch (_: RuntimeException) {
            // Diagnostic parsing must never change playback on absent/malformed initialization data.
            "unknown"
        }
    }
    return "unknown"
}
