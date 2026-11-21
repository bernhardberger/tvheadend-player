package at.bernhardberger.tvhplayer.touch.channels

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import at.bernhardberger.tvhplayer.touch.testutil.TouchFixtureArt

internal fun channelsFixture(playing: Boolean = false): ChannelList = ChannelList(
    rows = listOf(
        channel(
            "ridge-earth", "Ridge Earth", Color(0xFF3D481E),
            now("Ridge Light", "8:00–9:00 PM", 0.78f, "13m"), next("Tide Watch", "9:00 PM"),
        ).copy(playback = if (playing) ChannelPlayback(0.71f) else null),
        amberStage(),
        channel(
            "harbor-sport", "Harbor Sport", Color(0xFF1E3D48),
            now("Harbor Kickoff", "8:30–10:15 PM", 0.16f, "1h 28m"), next("River Court", "10:15 PM"),
        ).copy(recording = ChannelRecording.Program),
        channel(
            "northline-news", "Northline News", Color(0xFF1E2848),
            now("Northline Tonight", "8:00–9:00 PM", 0.78f, "13m"), next("Skyglass Weather", "9:00 PM"),
        ),
        channel(
            "kite-kids", "Kite Kids", Color(0xFF471D27),
            now("Paper Kite Tales", "8:30–8:55 PM", 0.68f, "8m"), next("Little Orbit", "8:55 PM"),
        ),
        channel(
            "ion-frame", "Ion Frame", Color(0xFF471D3D),
            now("Ion Wake", "8:15–10:05 PM", 0.29f, "1h 18m"), next("Lantern Road", "10:05 PM").copy(scheduled = true),
        ),
        channel(
            "harbor-lights", "Harbor Lights", Color(0xFF483D1E),
            now("Harbor Lights Live", "8:00–10:00 PM", 0.39f, "1h 13m"), next("Dockside Sessions", "10:00 PM"),
        ),
        channel(
            "lantern-hour", "Lantern Hour", Color(0xFF47321D),
            now("Stories by Lamplight", "8:00–9:30 PM", 0.52f, "43m"), next("The Reading Room", "9:30 PM"),
        ),
        channel("little-orbit", "Little Orbit", Color(0xFF1D4747)),
        channel("glass-drama", "Glass Drama", Color(0xFF2C3438)),
    ),
    tags = listOf(ChannelTag("news", "News"), ChannelTag("sport", "Sport")),
)

private fun amberStage() = channel(
    "amber-stage", "Amber Stage", Color(0xFF483D1E),
    now("Glass Harbor", "7:50–9:17 PM", 0.66f, "30m"), next("The Kettle Hour", "9:17 PM"),
).copy(
    details = ChannelDetails(
        artwork = BitmapPainter(TouchFixtureArt.art("glass-harbor").asImageBitmap()),
        episode = "S2 E4 • The Long Tide",
        metadata = "Drama • 2024 • 1h 27m",
        synopsis = "On the night shift at a harbor glass workshop, Mara and her coworkers " +
            "face a choice that could close the workshop for good.",
        following = listOf(next("Coastal Miles", "10:05 PM"), next("Amber Late", "10:55 PM"), next("Glass Harbor", "11:40 PM")),
    ),
)

// Tile colors sampled just inside the upper-left edge of the round-37 upright tablet drawing.
private fun channel(id: String, name: String, tint: Color, now: ChannelProgram? = null, next: ChannelProgram? = null) = ChannelRow(
    id, name, logo = BitmapPainter(TouchFixtureArt.picon(id).asImageBitmap()), logoTint = tint, now = now, next = next,
    details = if (now == null) null else ChannelDetails(),
)

private fun now(title: String, range: String, progress: Float, timeLeft: String) =
    ChannelProgram(title, start = "", timeRange = range, progress = progress, timeLeft = timeLeft)

private fun next(title: String, start: String) = ChannelProgram(title, start)
