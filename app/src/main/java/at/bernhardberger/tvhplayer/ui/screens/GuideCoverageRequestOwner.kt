package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class GuideCoverageRequestToken(
    val generations: Map<ChannelId, Long>,
)

internal class GuideCoverageRequestOwner(
    private val scope: CoroutineScope,
    private val timeoutMillis: Long,
    private val onPendingChanged: () -> Unit = {},
) {
    private data class OwnedRequest(
        val generation: Long,
        val windowStartSec: Long,
        val job: Job,
    )

    private val requests = mutableMapOf<ChannelId, OwnedRequest>()
    private var nextGeneration = 0L

    init {
        require(timeoutMillis > 0L)
    }

    fun request(
        channelIds: Set<ChannelId>,
        windowStartSec: Long,
        acquire: suspend (ChannelId) -> Unit,
    ): GuideCoverageRequestToken {
        val generations = mutableMapOf<ChannelId, Long>()
        channelIds.forEach { channelId ->
            val existing = requests[channelId]
            if (
                existing != null &&
                existing.windowStartSec == windowStartSec &&
                existing.job.isActive
            ) {
                generations[channelId] = existing.generation
                return@forEach
            }

            requests.remove(channelId)?.job?.cancel()
            val generation = ++nextGeneration
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    withTimeout(timeoutMillis) {
                        acquire(channelId)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Repository failures settle this request; connection state owns user feedback.
                } finally {
                    complete(channelId, generation)
                }
            }
            requests[channelId] = OwnedRequest(generation, windowStartSec, job)
            generations[channelId] = generation
            onPendingChanged()
            job.start()
        }
        return GuideCoverageRequestToken(generations.toMap())
    }

    fun isPending(token: GuideCoverageRequestToken): Boolean =
        token.generations.any { (channelId, generation) ->
            requests[channelId]?.let { request ->
                request.generation == generation && request.job.isActive
            } == true
        }

    fun isPending(channelId: ChannelId, windowStartSec: Long): Boolean =
        requests[channelId]?.let { request ->
            request.windowStartSec == windowStartSec && request.job.isActive
        } == true

    fun cancel(token: GuideCoverageRequestToken) {
        val jobs = token.generations.mapNotNull { (channelId, generation) ->
            requests[channelId]
                ?.takeIf { it.generation == generation }
                ?.also { requests.remove(channelId) }
                ?.job
        }
        if (jobs.isNotEmpty()) onPendingChanged()
        jobs.forEach(Job::cancel)
    }

    fun cancelAll() {
        if (requests.isEmpty()) return
        val jobs = requests.values.map(OwnedRequest::job)
        requests.clear()
        onPendingChanged()
        jobs.forEach(Job::cancel)
    }

    fun dispose() {
        val jobs = requests.values.map(OwnedRequest::job)
        requests.clear()
        jobs.forEach(Job::cancel)
    }

    private fun complete(channelId: ChannelId, generation: Long) {
        if (requests[channelId]?.generation != generation) return
        requests.remove(channelId)
        onPendingChanged()
    }
}
