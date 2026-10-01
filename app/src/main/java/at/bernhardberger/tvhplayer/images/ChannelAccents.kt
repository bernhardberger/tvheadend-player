package at.bernhardberger.tvhplayer.images

import coil3.request.ErrorResult
import at.bernhardberger.tvhplayer.core.NEUTRAL_ACCENT_RGB
import at.bernhardberger.tvheadend.sdk.core.ArtworkFailure
import at.bernhardberger.tvheadend.sdk.android.TvheadendArtworkLoadException
import android.content.Context
import androidx.palette.graphics.Palette
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.selectAccentRgb
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Channel colours taken from the channels' picons and kept in [store] under the SDK's artwork cache
 * key ([cacheKey]), so a colour is known in the first frame once it was sampled, also after a restart.
 * [sample] is the one sampler for callers and for the fill-in pass ([fillIn]); a key is never sampled
 * twice at the same time. The defaults keep nothing beyond the process and have no keys.
 */
class ChannelAccents(
    private val store: ChannelAccentStore = ChannelAccentStore(),
    private val cacheKey: (CurrentSessionObservation, ArtworkId) -> String? = { _, _ -> null },
) {
    private val sampling = ConcurrentHashMap<String, Mutex>()
    /** Keys whose picon did not load for now: not tried again in this process until the store is cleared. */
    private val failed = ConcurrentHashMap.newKeySet<String>()

    /** The stored colour of [picon], from memory. Null before the store was read and for an unknown key. */
    fun stored(picon: AppArtworkSource): Int? = cacheKey(picon.currentSession, picon.id)?.let { store[it] }

    /**
     * The colour of [picon]: the stored one, else sampled now and stored. A picon without a usable
     * colour, one the server does not have and one that does not decode give and store the neutral
     * colour. Null when the picon did not load for now, which is not stored, or when its session was
     * replaced meanwhile. Without a key the colour is sampled for this call only.
     */
    suspend fun sample(imageLoader: ImageLoader, context: Context, picon: AppArtworkSource): Int? {
        val key = cacheKey(picon.currentSession, picon.id) ?: return samplePicon(imageLoader, context, picon)
        store.load()
        sampling.computeIfAbsent(key) { Mutex() }.withLock {
            store[key]?.let { return it }
            if (key in failed) return null
            val rgb = samplePicon(imageLoader, context, picon)
            // The key holds for the session that loaded the picon: a result that outlived it is dropped.
            if (cacheKey(picon.currentSession, picon.id) != key) return null
            if (rgb == null) failed += key else store.put(key, rgb)
            return rgb
        }
    }

    /**
     * Reads the store, then samples every channel picon of the current catalogue that has no stored
     * colour, one after the other. Runs while the session is current and again when the session, the
     * set of picons or, by a clear, the store changes; a clear also forgets the failed loads. Never returns.
     */
    suspend fun fillIn(observation: StateFlow<SessionObservation>, imageLoader: ImageLoader, context: Context) {
        store.load()
        var clearsSeen = store.clears.value
        observation
            .map { it.currentSession?.generationIdentity to (it.channelState as? ChannelRepositoryState.Current)?.catalog }
            // The observation also changes with the guide and the recordings; the picons are read only
            // from a new catalogue or session.
            .distinctUntilChanged { old, new -> old.first === new.first && old.second === new.second }
            .map { (session, catalog) ->
                if (session == null || catalog == null) null
                else FillInTarget(session, catalog.channels.mapNotNullTo(LinkedHashSet()) { it.icon })
            }
            .distinctUntilChanged()
            .combine(store.clears) { target, clears -> target to clears }
            .collectLatest { (target, clears) ->
                // A clear starts over, also for the picons that did not load before.
                if (clears != clearsSeen) {
                    failed.clear()
                    clearsSeen = clears
                }
                if (target == null) return@collectLatest
                profileTrace("P50:accentPass:start:${target.picons.size}") { }
                var sampled = 0
                for (picon in target.picons) {
                    val session = observation.value.currentSession ?: break
                    val key = cacheKey(session, picon) ?: continue
                    if (store[key] != null || key in failed) continue
                    if (sample(imageLoader, context, AppArtworkSource(session, picon)) != null) sampled++
                }
                profileTrace("P50:accentPass:end:$sampled") { }
            }
    }
}

private data class FillInTarget(val session: SessionGenerationIdentity, val picons: Set<ArtworkId>)

/** Neutral for a picon there is no picture of; null when the picon does not load for now. */
private suspend fun samplePicon(imageLoader: ImageLoader, context: Context, picon: AppArtworkSource): Int? =
    withContext(Dispatchers.Default) {
        val request = ImageRequest.Builder(context)
            .data(picon)
            // Palette cannot read hardware bitmaps, and 64 px is plenty for a dominant colour.
            .allowHardware(false)
            .size(64, 64)
            // The small bitmap is of no use to anything that draws the picon.
            .memoryCachePolicy(CachePolicy.READ_ONLY)
            .build()
        val result = imageLoader.execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: run {
            val failure = ((result as? ErrorResult)?.throwable as? TvheadendArtworkLoadException)?.failure
            profileTrace("P50:accentFail:${failure ?: "undecodable"}") { }
            // A picon the server does not have, or one that does not decode, has no colour to wait
            // for; whatever else fails may load another time.
            return@withContext if (failure == null || failure == ArtworkFailure.FILE_UNAVAILABLE) NEUTRAL_ACCENT_RGB else null
        }
        profileTrace("P50:accentSample") {
            val palette = Palette.from(bitmap).clearFilters().generate()
            // Deliberately not dominantSwatch first: for a logo on a white plate that is white.
            selectAccentRgb(
                listOf(
                    palette.vibrantSwatch?.rgb?.and(0xFFFFFF),
                    palette.lightVibrantSwatch?.rgb?.and(0xFFFFFF),
                    palette.darkVibrantSwatch?.rgb?.and(0xFFFFFF),
                    palette.mutedSwatch?.rgb?.and(0xFFFFFF),
                    palette.dominantSwatch?.rgb?.and(0xFFFFFF),
                ),
            )
        }
    }
