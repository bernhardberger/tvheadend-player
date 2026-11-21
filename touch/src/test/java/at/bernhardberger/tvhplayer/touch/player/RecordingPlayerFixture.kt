package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import at.bernhardberger.tvhplayer.core.HumanTrackLabel
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.recordingSkipAction
import at.bernhardberger.tvhplayer.playback.RecordingSkipAction
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.touch.channels.channelsFixture
import at.bernhardberger.tvhplayer.touch.guide.CreditRole
import at.bernhardberger.tvhplayer.touch.guide.FactKey
import at.bernhardberger.tvhplayer.touch.guide.ProgramAiring
import at.bernhardberger.tvhplayer.touch.guide.ProgramCredit
import at.bernhardberger.tvhplayer.touch.guide.ProgramDetails
import at.bernhardberger.tvhplayer.touch.guide.ProgramFact
import at.bernhardberger.tvhplayer.touch.recordings.FixtureClock
import at.bernhardberger.tvhplayer.touch.recordings.RecordingProgress
import at.bernhardberger.tvhplayer.touch.recordings.kickoffPicked
import at.bernhardberger.tvhplayer.touch.testutil.TouchFixtureArt
import java.time.Instant

internal const val RECORDING_LENGTH = 6_300_000L
internal const val RECORDING_BEFORE_AD = 3_150_000L
internal const val RECORDING_INSIDE_AD = 3_222_000L
internal val fixtureAd = RecordingMark(RecordingMarkKind.AD_BREAK, 3_180_000L, 3_720_000L)
internal val fixtureAdToken = RecordingSkipAction(fixtureAd, fixtureAd.endMs)

internal fun fixtureAdOffer(position: Long, mode: AdBreakMode?): RecordingSkipAction? =
    fixtureAdToken.takeIf {
        recordingSkipAction(listOf(fixtureAd), position, PlayerSettings(adBreakMode = mode ?: AdBreakMode.ASK)) != null
    }

internal fun recordingPlayerFixture(position: State<Long>) = RecordingPlayerState(
    key = "harbor-kickoff",
    channel = channelsFixture().rows.first { it.name == "Harbor Sport" },
    title = "Harbor Kickoff",
    episode = "Westvale – Ridge Town",
    artwork = BitmapPainter(TouchFixtureArt.art("harbor-kickoff").asImageBitmap()),
    recordedOn = "Wed, Nov 11",
    endsAt = "9:40 PM",
    frame = BitmapPainter(TouchFixtureArt.art("still-harbor-kickoff").asImageBitmap()),
    durationMs = RECORDING_LENGTH,
    positionMs = position,
    marks = listOf(
        RecordingMark(RecordingMarkKind.PROGRAM_START, 180_000L),
        RecordingMark(RecordingMarkKind.AD_BREAK, 600_000L, 840_000L),
        RecordingMark(RecordingMarkKind.CHAPTER, 1_440_000L),
        fixtureAd,
        RecordingMark(RecordingMarkKind.CHAPTER, 4_920_000L, name = "Final whistle and interviews"),
        RecordingMark(RecordingMarkKind.CUT, 5_520_000L, 5_700_000L),
        RecordingMark(RecordingMarkKind.PROGRAM_END, 6_120_000L),
    ),
    tracks = PlayerTracks(
        audio = listOf(
            HumanTrackLabel("English • Stereo", "English", null, "Stereo"),
            HumanTrackLabel("English • 5.1", "English", null, "5.1"),
        ),
        playingAudio = 0,
        subtitles = listOf(HumanTrackLabel("English", "English", null, "DVB")),
    ),
)

internal fun recordingInfoFixture(running: Boolean): RecordingPlayerInfo {
    val item = kickoffPicked()
    val next = ProgramDetails(
        id = "kickoff-saturday", channelId = "harbor-sport", channel = "Harbor Sport",
        title = item.title, episode = "Kingsbridge – Northport", timeRange = "Sat, Nov 14 • 8:15–10:00 PM",
        length = "1h 45m", genre = item.details.genre,
        synopsis = "Kingsbridge welcome Northport for the weekend’s late match.",
    )
    val program = next.copy(
        id = item.id, episode = item.episode, timeRange = "Wed, Nov 11 • 8:30–10:15 PM",
        otherAirings = listOf(ProgramAiring(next.id, time = "Sat, Nov 14 • 8:15 PM", line = listOf(next.channel))),
        credits = listOf(ProgramCredit(CreditRole.Presenters, listOf("Alex Vale", "Morgan Reed"))),
        facts = listOf(ProgramFact(FactKey.Broadcast, "New")),
    )
    val showing = if (running) item.copy(
        state = RecordingProgress.Recording(Instant.parse("2026-11-11T21:40:00Z"), .6f),
        details = item.details.copy(facts = item.details.facts.map {
            if (it.first == "Recorded") it.first to "Wed, Nov 11 • 8:30–9:23 PM" else it
        }),
    ) else item
    val clock = if (running) FixtureClock.copy(now = Instant.parse("2026-11-11T21:23:00Z")) else FixtureClock
    return RecordingPlayerInfo(showing, clock, program, mapOf(next.id to next))
}
