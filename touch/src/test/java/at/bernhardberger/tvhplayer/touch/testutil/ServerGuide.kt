package at.bernhardberger.tvhplayer.touch.testutil

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
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

    private companion object {
        const val NEWS_TAG = 7L
        const val FIRST = 1L
        const val SECOND = 2L
    }
}
