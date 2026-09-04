package at.bernhardberger.tvhplayer.testing

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState

internal fun testSessionObservation(
    entries: List<DvrEntry> = emptyList(),
    channels: List<Channel> = emptyList(),
    tags: List<ChannelTag> = emptyList(),
    recordingProgressCapability: RecordingProgressCapability = RecordingProgressCapability.UNKNOWN,
): SessionObservation = SessionObservation.create(
    sessionState = SessionState.Ready(
        ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED,
            dvrWrite = CapabilityAccess.ALLOWED,
        )
    ),
    channelState = ChannelRepositoryState.Current(
        ChannelCatalog.create(channels = channels, tags = tags),
    ),
    epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
    dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = entries)),
    recordingProgressCapability = recordingProgressCapability,
)
