package at.bernhardberger.tvhplayer.touch.testutil

import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.touch.guide.GuideTexts
import java.time.LocalTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A server's channels and guide as the SDK hands them over: two channels, News tagging only the
 * first, and on the first four programs in a row around [now] (on now, next and two after); the one on now has a
 * description. The running recording and the scheduled one are linked to their programs. The first
 * channel has a logo in the server's image cache, the program on now and the finished film a picture.
 */
internal class ServerGuide(val now: Instant) {
    val news = ChannelTag.create(ChannelTagId(NEWS_TAG), name = "News")
    val logo = checkNotNull(ChannelIcon.parse("imagecache/$LOGO"))
    val picture = checkNotNull(ArtworkId.parse("imagecache/$PICTURE"))
    val first = Channel.create(ChannelId(FIRST), name = "Channel One", icon = logo, tagIds = listOf(news.id))
    val second = Channel.create(ChannelId(SECOND), name = "Channel Two")
    val catalog = ChannelCatalog.create(listOf(first, second), listOf(news))
    val programs = listOf("Evening News", "Weather", "Late Film", "Night Talk")

    val texts = GuideTexts(
        clock = { LocalTime.ofSecondOfDay(it % DAY_SEC).toString() },
        timeRange = { start, stop -> "${LocalTime.ofSecondOfDay(start % DAY_SEC)}–${LocalTime.ofSecondOfDay(stop % DAY_SEC)}" },
        duration = { "${it / 60}m" },
        day = { date, today -> if (date == today) "Today" else date.toString() },
        date = { it.toString() },
        airing = { start, _ -> start.toString() },
        number = { null },
        episode = { null },
        genre = { it.genre },
        firstAired = { it.toString() },
        count = { number, of -> if (of == null) "$number" else "$number of $of" },
        broadcast = { if (it) "New" else "Repeat" },
    )

    val snapshot = EpgSnapshot.create(
        programs.mapIndexed { index, title ->
            val start = now - 30.minutes + (index * 60).minutes
            EpgEvent.create(
                EventId(index + 1L), first.id, start, start + 60.minutes, title = title,
                description = "About $title".takeIf { index == 0 }, image = picture.takeIf { index == 0 },
            )
        },
    )

    /**
     * The server's recordings: a running one on the first channel, a finished unwatched film two
     * days ago in Films, a finished watched one yesterday in Docs, and a scheduled one that the
     * archive never lists.
     */
    val recordingNow =
        recording(RECORDING_NOW, "Evening News", first, now - 30.minutes, DvrEntryState.RECORDING, event = 1)
    val film = recording(
        FILM, "Harbor Lights", first, now - 2.days, DvrEntryState.COMPLETED, folder = "Films", image = picture,
    )
    val diary = recording(DIARY, "Mountain Diary", second, now - 1.days, DvrEntryState.COMPLETED, "Docs", 1)
    val scheduled = recording(SCHEDULED, "Late Film", first, now + 90.minutes, DvrEntryState.SCHEDULED, event = 3)
    val dvr = DvrSnapshot.create(entries = listOf(recordingNow, film, diary, scheduled))

    /** The server signed in with everything current, with [entries] as its recordings: it has a current session. */
    fun connected(entries: List<DvrEntry> = dvr.entries) = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
        ),
        channelState = ChannelRepositoryState.Current(catalog),
        epgState = EpgRepositoryState.Current(snapshot),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = entries)),
    )

    /** The recording the server schedules for the program [event] on the first channel. */
    /** A recording scheduled for program [event]; with [rule], one an automatic recording scheduled. */
    fun scheduling(id: Long, event: Int, rule: AutorecRuleId? = null, enabled: Boolean? = null) = recording(
        id, programs[event - 1], first, snapshot.events.first { it.id == EventId(event.toLong()) }.start,
        DvrEntryState.SCHEDULED, event = event.toLong(), rule = rule, enabled = enabled,
    )

    private fun recording(
        id: Long,
        title: String,
        channel: Channel,
        start: Instant,
        state: DvrEntryState,
        folder: String? = null,
        playCount: Long = 0,
        event: Long? = null,
        image: ArtworkId? = null,
        rule: AutorecRuleId? = null,
        enabled: Boolean? = null,
    ) = DvrEntry.create(
        id = DvrEntryId(id), channelId = channel.id, start = start, stop = start + 60.minutes, title = title,
        state = state, folder = folder?.let(::listOf), playCount = playCount, eventId = event?.let(::EventId),
        image = image, autorecRuleId = rule, enabled = enabled,
    )

    private companion object {
        const val RECORDING_NOW = 11L
        const val FILM = 12L
        const val DIARY = 13L
        const val SCHEDULED = 14L
        const val NEWS_TAG = 7L
        const val FIRST = 1L
        const val SECOND = 2L
        const val LOGO = 21L
        const val PICTURE = 31L
        const val DAY_SEC = 86_400L
    }
}
