package at.bernhardberger.tvhplayer.profiling

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import kotlin.time.Instant

/** SDK domain data, deliberately not channel cards or pre-indexed Guide rows. */
internal fun profileObservation(stress: Boolean, startEpochSeconds: Long): SessionObservation {
    val channelCount = if (stress) 300 else 60
    val programmesPerChannel = if (stress) 144 else 48
    val tags = listOf(
        ChannelTag.create(ChannelTagId(1), name = "Group A"),
        ChannelTag.create(ChannelTagId(2), name = "Group B"),
    )
    val channels = (1..channelCount).map { index ->
        Channel.create(
            ChannelId(index.toLong()), name = "Offline channel $index",
            number = index.toLong(), tagIds = listOf(tags[index % 2].id),
        )
    }
    val events = channels.flatMap { channel ->
        (0 until programmesPerChannel).map { slot ->
            EpgEvent.create(
                id = EventId(channel.id.value * 1000 + slot),
                channelId = channel.id,
                start = Instant.fromEpochSeconds(startEpochSeconds + slot * 1800L),
                stop = Instant.fromEpochSeconds(startEpochSeconds + (slot + 1) * 1800L),
                title = "Programme ${slot + 1} — ${channel.name}",
                summary = "Synthetic offline programme for production Guide navigation.",
            )
        }
    }
    val coverage = channels.map { channel ->
        EpgCoverage.create(
            channel.id,
            coveredFrom = Instant.fromEpochSeconds(startEpochSeconds),
            coveredTo = Instant.fromEpochSeconds(startEpochSeconds + programmesPerChannel * 1800L),
        )
    }
    return SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.DENIED),
        ),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels, tags)),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create(events, coverage)),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )
}
