package at.bernhardberger.tvhplayer.htsp

import at.bernhardberger.tvhplayer.core.epgRetentionWindow
import at.bernhardberger.tvhplayer.core.evictEpgOutsideWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class EpgEventBounds(
    val earliestStart: Long,
    val latestStop: Long,
) {
    companion object {
        val Empty = EpgEventBounds(
            earliestStart = Long.MAX_VALUE,
            latestStop = 0L,
        )
    }
}

internal data class EpgMetadataIngestResult(
    val totalEvents: Int,
    val perChannelBounds: Map<Int, EpgEventBounds>,
) {
    companion object {
        val Empty = EpgMetadataIngestResult(
            totalEvents = 0,
            perChannelBounds = emptyMap(),
        )
    }
}

internal sealed interface EpgMetadataEffect {
    data class EventUpserted(val event: EpgEventEntry) : EpgMetadataEffect

    data class EventDeleted(
        val channelId: Int,
        val eventId: Int,
    ) : EpgMetadataEffect
}

internal class EpgMetadataRepository {
    private val lock = Any()
    private val epgByChannel = mutableMapOf<Int, MutableStateFlow<List<EpgEventEntry>>>()

    fun epgForChannel(channelId: Int): StateFlow<List<EpgEventEntry>> = synchronized(lock) {
        flowForChannelLocked(channelId)
    }

    fun accept(
        message: HtspMessage,
        anchorSecForChannel: (Int) -> Long,
    ): EpgMetadataEffect? = synchronized(lock) {
        when (message.method) {
            "eventAdd", "eventUpdate" -> {
                val event = epgEventFromFields(message.fields) ?: return@synchronized null
                upsertLocked(event, anchorSecForChannel(event.channelId))
                EpgMetadataEffect.EventUpserted(event)
            }
            "eventDelete" -> {
                val eventId = message.int("eventId") ?: message.int("id")
                    ?: return@synchronized null
                val channelId = message.int("channelId") ?: message.int("channel")
                    ?: return@synchronized null
                epgByChannel[channelId]?.let { flow ->
                    flow.value = flow.value.filterNot { event -> event.eventId == eventId }
                }
                EpgMetadataEffect.EventDeleted(channelId = channelId, eventId = eventId)
            }
            else -> null
        }
    }

    fun ingestGetEventsReply(
        reply: HtspMessage,
        anchorSec: Long,
    ): EpgMetadataIngestResult = synchronized(lock) {
        val rawEvents = reply.fields["events"]
            ?: reply.fields["epg"]
            ?: reply.fields["entries"]
            ?: return@synchronized EpgMetadataIngestResult.Empty
        val entries = rawEvents as? List<*>
            ?: return@synchronized EpgMetadataIngestResult.Empty

        val earliestStarts = linkedMapOf<Int, Long>()
        val latestStops = linkedMapOf<Int, Long>()
        var totalEvents = 0

        for (entry in entries) {
            @Suppress("UNCHECKED_CAST")
            val fields = entry as? Map<String, Any?> ?: continue
            val event = epgEventFromFields(fields) ?: continue
            upsertLocked(event, anchorSec)
            earliestStarts[event.channelId] = min(
                earliestStarts[event.channelId] ?: Long.MAX_VALUE,
                event.start,
            )
            latestStops[event.channelId] = max(
                latestStops[event.channelId] ?: 0L,
                event.stop,
            )
            totalEvents++
        }

        if (totalEvents == 0) return@synchronized EpgMetadataIngestResult.Empty
        EpgMetadataIngestResult(
            totalEvents = totalEvents,
            perChannelBounds = earliestStarts.mapValues { (channelId, earliestStart) ->
                EpgEventBounds(
                    earliestStart = earliestStart,
                    latestStop = latestStops.getValue(channelId),
                )
            },
        )
    }

    fun clear() = synchronized(lock) {
        epgByChannel.clear()
    }

    fun removeChannel(channelId: Int) = synchronized(lock) {
        epgByChannel.remove(channelId)
    }

    fun retainChannels(channelIds: Set<Int>) = synchronized(lock) {
        epgByChannel.keys.retainAll(channelIds)
    }

    fun trimChannel(channelId: Int, anchorSec: Long): EpgEventBounds? = synchronized(lock) {
        val flow = epgByChannel[channelId] ?: return@synchronized null
        flow.value = trim(flow.value, anchorSec)
        boundsFor(flow.value)
    }

    fun trimAll(anchorSecForChannel: (Int) -> Long): Map<Int, EpgEventBounds> =
        synchronized(lock) {
            buildMap(epgByChannel.size) {
                for ((channelId, flow) in epgByChannel) {
                    flow.value = trim(flow.value, anchorSecForChannel(channelId))
                    put(channelId, boundsFor(flow.value))
                }
            }
        }

    fun nowEvent(channelId: Int, nowSec: Long): EpgEventEntry? {
        val events = synchronized(lock) { epgByChannel[channelId]?.value } ?: return null
        return events.firstOrNull { event -> event.start <= nowSec && nowSec < event.stop }
            ?: events.minByOrNull { event -> abs(event.start - nowSec) }
    }

    fun nextEvent(channelId: Int, nowSec: Long): EpgEventEntry? {
        val events = synchronized(lock) { epgByChannel[channelId]?.value } ?: return null
        return events.firstOrNull { event -> event.start > nowSec }
    }

    private fun upsertLocked(event: EpgEventEntry, anchorSec: Long) {
        val flow = flowForChannelLocked(event.channelId)
        val replaced = buildList(flow.value.size + 1) {
            var found = false
            for (existing in flow.value) {
                if (existing.eventId == event.eventId) {
                    add(event)
                    found = true
                } else {
                    add(existing)
                }
            }
            if (!found) add(event)
        }
        flow.value = trim(replaced, anchorSec)
    }

    private fun flowForChannelLocked(channelId: Int): MutableStateFlow<List<EpgEventEntry>> =
        epgByChannel.getOrPut(channelId) { MutableStateFlow(emptyList()) }

    private fun trim(events: List<EpgEventEntry>, anchorSec: Long): List<EpgEventEntry> =
        evictEpgOutsideWindow(events, epgRetentionWindow(anchorSec))

    private fun boundsFor(events: List<EpgEventEntry>): EpgEventBounds =
        if (events.isEmpty()) {
            EpgEventBounds.Empty
        } else {
            EpgEventBounds(
                earliestStart = events.minOf(EpgEventEntry::start),
                latestStop = events.maxOf(EpgEventEntry::stop),
            )
        }
}
