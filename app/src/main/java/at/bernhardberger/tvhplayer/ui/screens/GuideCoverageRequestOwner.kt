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
        channelIds: List<ChannelId>,
        windowStartSec: Long,
        acquire: suspend (List<ChannelId>) -> Unit,
    ): GuideCoverageRequestToken {
        val orderedChannelIds = channelIds.distinct()
        if (orderedChannelIds.isEmpty()) return GuideCoverageRequestToken(emptyMap())

        val existingRequests = orderedChannelIds.mapNotNull(requests::get).distinctBy { it.generation }
        val existing = existingRequests.singleOrNull()
        if (
            existing != null &&
            existing.windowStartSec == windowStartSec &&
            existing.job.isActive &&
            orderedChannelIds.all { requests[it] === existing }
        ) {
            return GuideCoverageRequestToken(
                orderedChannelIds.associateWith { existing.generation }
            )
        }

        existingRequests.forEach { request ->
            requests.entries.removeAll { it.value.generation == request.generation }
            request.job.cancel()
        }

        val generations = mutableMapOf<ChannelId, Long>()
        val generation = ++nextGeneration
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                withTimeout(timeoutMillis) {
                    acquire(orderedChannelIds)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Repository failures settle this request; connection state owns user feedback.
            } finally {
                complete(generation)
            }
        }
        val request = OwnedRequest(generation, windowStartSec, job)
        orderedChannelIds.forEach { channelId ->
            requests[channelId] = request
            generations[channelId] = generation
        }
        onPendingChanged()
        job.start()
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
        }.distinct()
        if (jobs.isNotEmpty()) onPendingChanged()
        jobs.forEach(Job::cancel)
    }

    fun cancelAll() {
        if (requests.isEmpty()) return
        val jobs = requests.values.map(OwnedRequest::job).distinct()
        requests.clear()
        onPendingChanged()
        jobs.forEach(Job::cancel)
    }

    fun dispose() {
        val jobs = requests.values.map(OwnedRequest::job).distinct()
        requests.clear()
        jobs.forEach(Job::cancel)
    }

    private fun complete(generation: Long) {
        if (requests.none { it.value.generation == generation }) return
        requests.entries.removeAll { it.value.generation == generation }
        onPendingChanged()
    }
}
