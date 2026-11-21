package at.bernhardberger.tvhplayer.touch.testutil

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A server's channels and guide as the SDK hands them over: two channels, News tagging only the
 * first, and on the first four programs in a row around [now] (on now, next and two after).
 */
internal class ServerGuide(val now: Instant) {
    val news = ChannelTag.create(ChannelTagId(NEWS_TAG), name = "News")
    val first = Channel.create(ChannelId(FIRST), name = "Channel One", tagIds = listOf(news.id))
    val second = Channel.create(ChannelId(SECOND), name = "Channel Two")
    val catalog = ChannelCatalog.create(listOf(first, second), listOf(news))
    val programs = listOf("Evening News", "Weather", "Late Film", "Night Talk")

    val snapshot = EpgSnapshot.create(
        programs.mapIndexed { index, title ->
            val start = now - 30.minutes + (index * 60).minutes
            EpgEvent.create(EventId(index + 1L), first.id, start, start + 60.minutes, title = title)
        },
    )

    /**
     * The server's recordings: a running one on the first channel, a finished unwatched film two
     * days ago in Films, a finished watched one yesterday in Docs, and a scheduled one that the
     * archive never lists.
     */
    val recordingNow = recording(RECORDING_NOW, "Evening News", first, now - 30.minutes, DvrEntryState.RECORDING)
    val film = recording(FILM, "Harbor Lights", first, now - 2.days, DvrEntryState.COMPLETED, folder = "Films")
    val diary = recording(DIARY, "Mountain Diary", second, now - 1.days, DvrEntryState.COMPLETED, "Docs", 1)
    val scheduled = recording(SCHEDULED, "Late Film", first, now + 90.minutes, DvrEntryState.SCHEDULED)
    val dvr = DvrSnapshot.create(entries = listOf(recordingNow, film, diary, scheduled))

    private fun recording(
        id: Long,
        title: String,
        channel: Channel,
        start: Instant,
        state: DvrEntryState,
        folder: String? = null,
        playCount: Long = 0,
    ) = DvrEntry.create(
        id = DvrEntryId(id), channelId = channel.id, start = start, stop = start + 60.minutes, title = title,
        state = state, folder = folder?.let(::listOf), playCount = playCount,
    )

    private companion object {
        const val RECORDING_NOW = 11L
        const val FILM = 12L
        const val DIARY = 13L
        const val SCHEDULED = 14L
        const val NEWS_TAG = 7L
        const val FIRST = 1L
        const val SECOND = 2L
    }
}
