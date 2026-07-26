package at.bernhardberger.tvhplayer.player.htsp.reader

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.NalUnitUtil
import androidx.media3.extractor.AvcConfig
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.TrackOutput
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.player.htsp.utils.AspectRatioUtils
import timber.log.Timber
import kotlin.math.abs

@OptIn(UnstableApi::class)
internal class H264StreamReader : PlainStreamReader(C.TRACK_TYPE_VIDEO) {
    private var track: TrackOutput? = null
    private var baseFormat: Format? = null

    private var configured = false
    private var lastPixelRatio: Float = Format.NO_VALUE.toFloat()

    // aby se payload nescannoval pořád
    private var lastFormatUpdatePts: Long = Long.MIN_VALUE
    private val FORMAT_UPDATE_COOLDOWN_US = 1_000_000L // 1s
    private val RATIO_EPS = 0.0005f

    override fun createTracks(stream: HtspMessage, output: ExtractorOutput) {
        val streamIndex = stream.int("index") ?: 0
        val t = output.track(streamIndex, C.TRACK_TYPE_VIDEO)
        track = t

        val fmt = buildFormat(streamIndex, stream)
        baseFormat = fmt
        t.format(fmt)

        // Provisional SAR from coded size is not stream-confirmed yet.
        configured = false
        lastPixelRatio = fmt.pixelWidthHeightRatio
        lastFormatUpdatePts = Long.MIN_VALUE
    }

    override fun buildFormat(streamIndex: Int, stream: HtspMessage): Format {
        var initData: List<ByteArray> = emptyList()
        var pixelRatio = Format.NO_VALUE.toFloat()

        if (stream.fields.contains("meta")) {
            try {
                val avcConfig = AvcConfig.parse(ParsableByteArray(stream.bin("meta")!!))
                initData = avcConfig.initializationData

                val sps = initData.firstOrNull()
                if (sps != null && sps.isNotEmpty()) {
                    pixelRatio = parseH264PixelWidthHeightRatioFromSps(sps)
                }
            } catch (e: ParserException) {
                Timber.e("Failed to parse H264 meta, discarding: ${e.message}")
            } catch (t: Throwable) {
                Timber.e("SPS SAR parse failed: ${t.message}")
            }
        }

        val width = stream.int("width") ?: Format.NO_VALUE
        val height = stream.int("height") ?: Format.NO_VALUE

        if (
            pixelRatio != Format.NO_VALUE.toFloat() &&
            pixelRatio > 0f &&
            pixelRatio.isFinite() &&
            width > 0 &&
            height > 0
        ) {
            // Parity with HEVC: refine active-width broadcast SAR at init.
            pixelRatio = AspectRatioUtils.adjustSarForBroadcast(
                codedW = width,
                codedH = height,
                sar = pixelRatio,
            ) { Timber.d(it) }
        } else if (width > 0 && height > 0) {
            AspectRatioUtils.provisionalSarWhenUnknown(width, height)?.let {
                pixelRatio = it
                Timber.d("Provisional H.264 SAR=$it for ${width}x$height (awaiting SPS VUI)")
            }
        }

        val duration = stream.int("duration") ?: Format.NO_VALUE
        val frameRate =
            if (duration != Format.NO_VALUE)
                StreamReaderUtils.frameDurationToFrameRate(duration)
            else
                Format.NO_VALUE.toFloat()

        return Format.Builder()
            .setId(streamIndex.toString())
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(width)
            .setHeight(height)
            .apply {
                if (frameRate != Format.NO_VALUE.toFloat()) setFrameRate(frameRate)
                if (initData.isNotEmpty()) setInitializationData(initData)
                if (pixelRatio != Format.NO_VALUE.toFloat() && pixelRatio > 0f && pixelRatio.isFinite()) {
                    setPixelWidthHeightRatio(pixelRatio)
                    Timber.d("Init pixelWidthHeightRatio=$pixelRatio for ${width}x${height}")
                }
            }
            .build()
    }

    override fun consume(message: HtspMessage) {
        val payload = message.bin("payload") ?: return

        val pts = (message.fields["pts"] as? Number)?.toLong() ?: 0L
        val frameType = message.int("frametype") ?: -1

        val isKey = (frameType == -1 || frameType == 73)

        // Until stream SAR is confirmed, probe every sample (SPS can lag the first
        // few frames). After that, re-check on keyframes with a cooldown.
        val shouldProbe = !configured ||
            (isKey && lastFormatUpdatePts != Long.MIN_VALUE &&
                (pts - lastFormatUpdatePts) >= FORMAT_UPDATE_COOLDOWN_US) ||
            (isKey && lastFormatUpdatePts == Long.MIN_VALUE)

        if (shouldProbe) {
            val sps = extractFirstSpsNal(payload)
            if (sps != null && sps.isNotEmpty()) {
                val rawSar = parseH264PixelWidthHeightRatioFromSps(sps)

                val ratioValid = rawSar != Format.NO_VALUE.toFloat() &&
                        rawSar > 0f && rawSar.isFinite()

                if (ratioValid) {
                    val w = baseFormat?.width ?: -1
                    val h = baseFormat?.height ?: -1

                    val newSar = if (w > 0 && h > 0) {
                        AspectRatioUtils.adjustSarForBroadcast(
                            codedW = w,
                            codedH = h,
                            sar = rawSar
                        ) { Timber.d(it) }
                    } else rawSar

                    val ratioChanged =
                        (lastPixelRatio == Format.NO_VALUE.toFloat()) ||
                                abs(newSar - lastPixelRatio) > RATIO_EPS

                    if (ratioChanged) {
                        val t = track
                        val base = baseFormat
                        if (t != null && base != null) {
                            val updated = base.buildUpon()
                                .setPixelWidthHeightRatio(newSar)
                                .build()
                            t.format(updated)

                            baseFormat = updated
                            lastPixelRatio = newSar
                            configured = true
                            lastFormatUpdatePts = pts

                            Timber.d("Format updated: pixelWidthHeightRatio(SAR)=$newSar (raw=$rawSar)")
                        }
                    } else {
                        configured = true
                        lastFormatUpdatePts = pts
                    }
                }
            }
        }

        var bufferFlags = 0
        if (isKey) bufferFlags = bufferFlags or C.BUFFER_FLAG_KEY_FRAME

        val pba = ParsableByteArray(payload)
        track!!.sampleData(pba, payload.size)
        track!!.sampleMetadata(pts, bufferFlags, payload.size, 0, null)
    }

    override val trackType: Int
        get() = C.TRACK_TYPE_VIDEO

    private fun extractFirstSpsNal(payload: ByteArray): ByteArray? {
        var i = 0
        val limit = minOf(payload.size - 4, 4096)

        fun isStart3(pos: Int): Boolean =
            pos + 2 < payload.size &&
                    payload[pos] == 0.toByte() &&
                    payload[pos + 1] == 0.toByte() &&
                    payload[pos + 2] == 1.toByte()

        fun isStart4(pos: Int): Boolean =
            pos + 3 < payload.size &&
                    payload[pos] == 0.toByte() &&
                    payload[pos + 1] == 0.toByte() &&
                    payload[pos + 2] == 0.toByte() &&
                    payload[pos + 3] == 1.toByte()

        while (i < limit) {
            val startLen = when {
                isStart4(i) -> 4
                isStart3(i) -> 3
                else -> 0
            }
            if (startLen == 0) {
                i++
                continue
            }

            val nalStart = i + startLen
            if (nalStart >= payload.size) return null

            val nalHeader = payload[nalStart].toInt() and 0xFF
            val nalType = nalHeader and 0x1F
            if (nalType == 7) {
                // find nal end (next start code or end)
                var j = nalStart + 1
                while (j < payload.size - 3) {
                    if (isStart3(j) || isStart4(j)) break
                    j++
                }
                return payload.copyOfRange(nalStart, j)
            }

            i = nalStart
        }
        return null
    }

    private fun parseH264PixelWidthHeightRatioFromSps(spsNal: ByteArray): Float {
        return try {
            val sps = NalUnitUtil.parseSpsNalUnit(spsNal, 0, spsNal.size)
            sps.pixelWidthHeightRatio
        } catch (_: Throwable) {
            Format.NO_VALUE.toFloat()
        }
    }
}
