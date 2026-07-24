package at.bernhardberger.tvhplayer.player.htsp.utils

import kotlin.math.abs

internal object AspectRatioUtils {

    private fun bestTargetError(dar: Float): Float {
        val targets = floatArrayOf(16f / 9f, 4f / 3f)
        var best = Float.MAX_VALUE
        for (t in targets) best = minOf(best, abs(dar - t))
        return best
    }

    private fun near(a: Float, b: Float, eps: Float): Boolean =
        a.isFinite() && b.isFinite() && abs(a - b) <= eps

    fun adjustSarForBroadcast(
        codedW: Int,
        codedH: Int,
        sar: Float,
        log: ((String) -> Unit)? = null
    ): Float {
        if (codedW <= 0 || codedH <= 0) return sar
        if (!sar.isFinite() || sar <= 0f) return sar

        val darCoded = (codedW.toFloat() / codedH.toFloat()) * sar
        var bestErr = bestTargetError(darCoded)
        var bestSar = sar
        var bestActiveW = codedW.toFloat()

        val activeWidthCandidates: FloatArray = when {
            codedW == 720 && codedH == 576 -> floatArrayOf(720f, 704f, 702f, 706f)
            codedW == 720 && codedH == 480 -> floatArrayOf(720f, 704f, 711f)
            else -> floatArrayOf(codedW.toFloat())
        }

        for (aw in activeWidthCandidates) {
            val dar = (aw / codedH.toFloat()) * sar
            val err = bestTargetError(dar)
            if (err < bestErr - 1e-6f) {
                bestErr = err
                bestActiveW = aw
                // SAR' = SAR * (activeW / codedW)
                bestSar = sar * (aw / codedW.toFloat())
            }
        }

        val changed = !near(bestSar, sar, 0.0005f)
        val goodEnough = bestErr <= 0.03f // tolerance

        if (changed && goodEnough) {
            log?.invoke(
                "Adjust SAR: coded=${codedW}x$codedH sar=$sar darCoded=$darCoded -> sar'=$bestSar (activeW=$bestActiveW, err=$bestErr)"
            )
            return bestSar
        }

        log?.invoke("Keep SAR: coded=${codedW}x$codedH sar=$sar darCoded=$darCoded (bestErr=$bestErr)")
        return sar
    }
}