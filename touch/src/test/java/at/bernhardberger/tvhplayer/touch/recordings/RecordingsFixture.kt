package at.bernhardberger.tvhplayer.touch.recordings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import at.bernhardberger.tvhplayer.touch.testutil.TouchFixtureArt

// The round-37 drawings' recordings (mockups html27 flat/folders, html13 week rows, html33 upright list).

internal val FixtureSpace = RecordingSpace("1.2 TB", "4.0 TB", usedFraction = 0.7f)

/** The id of the half-watched match every drawing picks. */
internal const val PickedId = "kickoff-nov-11"

/**
 * All recordings, newest first; [friday] and [tuesday] add the rows the taller canvases draw. Each
 * row's line is the drawing's text for its canvas ([phone] or tablet).
 */
internal fun flatFixture(
    friday: Boolean = false,
    tuesday: Boolean = false,
    view: RecordingsView = RecordingsView(),
    phone: Boolean = false,
) = RecordingsState(
        view = view,
        space = FixtureSpace,
        root = RecordingFolder(
            id = "root", name = "Recordings", recordingCount = 116,
            recordings = listOfNotNull(
                kickoffLive(
                    if (phone) "Northport – Eastmoor" else "Northport – Eastmoor • Harbor Sport • until 10:15 PM",
                ).copy(section = "Today"),
                recording(
                    "field-desk", "Field Desk", "still-field-desk-street", NorthlineNews, "Today", "12:15 PM", "45m",
                    "Northline News • 12:15 PM • 45m",
                ),
                kickoffYesterday(
                    if (phone) "Kingsbridge – Northport • 1h 45m" else "Kingsbridge – Northport • Harbor Sport • 1h 45m",
                ).copy(section = "Yesterday"),
                extraTime("extra-yesterday", "Yesterday", RecordingProgress.Watched, "Harbor Sport • 45m"),
                recording(
                    "harbor-lights-live", "Harbor Lights Live", "still-harbor-lights-live", HarborLights,
                    "Fri, Nov 13", "8:00 PM", "2h", "Harbor Lights • 8:00 PM • 2h",
                ).copy(section = "Fri, Nov 13").takeIf { friday },
                kickoffPicked(
                    if (phone) "Westvale – Ridge Town • 52m left" else "Westvale – Ridge Town • Harbor Sport • 52m left",
                ).copy(section = "Wed, Nov 11"),
                extraTime("extra-nov-11", "Wed, Nov 11", RecordingProgress.Watched, "Harbor Sport • 45m"),
                recording(
                    "glass-harbor", "Glass Harbor", "still-glass-harbor", AmberStage, "Tue, Nov 10", "9:00 PM", "1h 27m",
                    "S2 E3 • Amber Stage • 1h 2m left",
                ).copy(
                    episode = "S2 E3", section = "Tue, Nov 10",
                    state = RecordingProgress.PartlyWatched("1h 2m", 0.29f),
                ).takeIf { tuesday },
            ),
        ),
    )

/** Grouped by folders, from the root down to Sport › Football › Harbor Sport › Week 46. */
internal fun foldersFixture(phone: Boolean = false) = RecordingsState(
    view = RecordingsView(grouping = RecordingsGrouping.Folders),
    space = FixtureSpace,
    root = RecordingFolder(
        id = "root", name = "Recordings", recordingCount = 116,
        folders = listOf(
            RecordingFolder(
                "now", "Recording now", recordingNow = true,
                recordings = listOf(kickoffLive("Northport – Eastmoor • Harbor Sport • until 10:15 PM")),
            ),
            filled("documentaries", "Documentaries", folders = 2, recordings = 14, size = "31 GB"),
            filled("drama", "Drama", folders = 3, recordings = 22, size = "58 GB"),
            filled("kids", "Kids", folders = 0, recordings = 9, size = "6.4 GB"),
            filled("movies", "Movies", folders = 0, recordings = 17, size = "96 GB"),
            filled("music", "Music", folders = 0, recordings = 5, size = "21 GB"),
            filled("news", "News", folders = 2, recordings = 8, size = "9.2 GB"),
            RecordingFolder(
                "sport", "Sport", recordingCount = 41, size = "186 GB",
                folders = listOf(
                    RecordingFolder(
                        "football", "Football", recordingCount = 20, size = "92 GB",
                        folders = listOf(
                            RecordingFolder(
                                "harbor-sport", "Harbor Sport", recordingCount = 12, size = "54 GB",
                                folders = listOf(
                                    RecordingFolder("week-46", "Week 46", recordings = weekRows(phone), size = "27 GB"),
                                    filled("week-45", "Week 45", folders = 0, recordings = 6, size = "27 GB"),
                                ),
                            ),
                            filled("river-court", "River Court", folders = 0, recordings = 8, size = "38 GB"),
                        ),
                    ),
                    filled("basketball", "Basketball", folders = 0, recordings = 9, size = "40 GB"),
                    filled("tennis", "Tennis", folders = 0, recordings = 7, size = "30 GB"),
                    filled("cycling", "Cycling", folders = 0, recordings = 5, size = "24 GB"),
                ),
            ),
        ),
    ),
)

internal val WeekPath = listOf("sport", "football", "harbor-sport", "week-46")

/** Nothing left under Show: Unwatched. */
internal fun unwatchedEmptyFixture() = RecordingsState(
    view = RecordingsView(show = RecordingsShow.Unwatched),
    space = FixtureSpace,
    root = RecordingFolder("root", "Recordings"),
)

/** No recordings on the server. */
internal fun noRecordingsFixture() = RecordingsState(
    view = RecordingsView(),
    space = RecordingSpace("4.0 TB", "4.0 TB", usedFraction = 0f),
    root = RecordingFolder("root", "Recordings"),
)

private fun weekRows(phone: Boolean) = listOf(
    kickoffLive(if (phone) "Northport – Eastmoor • Today" else "Northport – Eastmoor • Today • until 10:15 PM")
        .copy(id = "week-kickoff-live"),
    kickoffYesterday(if (phone) "Kingsbridge – Northport • Sat, Nov 14" else "Kingsbridge – Northport • Sat, Nov 14 • 1h 45m")
        .copy(id = "week-kickoff-14", day = "Sat, Nov 14"),
    extraTime("week-extra-14", "Sat, Nov 14", RecordingProgress.Unwatched, "Sat, Nov 14 • 45m"),
    kickoffPicked(if (phone) "Westvale – Ridge Town • 52m left" else "Westvale – Ridge Town • Wed, Nov 11 • 52m left"),
    extraTime("week-extra-11", "Wed, Nov 11", RecordingProgress.Watched, "Wed, Nov 11 • 45m"),
    recording(
        "week-preview", "Harbor Kickoff", "harbor-kickoff", HarborSport, "Mon, Nov 9", "7:00 PM", "30m",
        if (phone) "Cup preview • Mon, Nov 9" else "Cup preview • Mon, Nov 9 • 30m",
    ).copy(episode = "Cup preview", state = RecordingProgress.Watched),
)

private fun kickoffLive(line: String) =
    recording("kickoff-live", "Harbor Kickoff", "still-harbor-kickoff", HarborSport, "Today", "8:30 PM", "1h 45m", line)
        .copy(episode = "Northport – Eastmoor", state = RecordingProgress.Recording("10:15 PM", 0.16f))

private fun kickoffYesterday(line: String) =
    recording("kickoff-yesterday", "Harbor Kickoff", "harbor-kickoff", HarborSport, "Yesterday", "8:30 PM", "1h 45m", line)
        .copy(episode = "Kingsbridge – Northport")

private fun kickoffPicked(line: String) =
    recording(PickedId, "Harbor Kickoff", "still-harbor-kickoff", HarborSport, "Wed, Nov 11", "8:30 PM", "1h 45m", line)
        .copy(
            episode = "Westvale – Ridge Town",
            state = RecordingProgress.PartlyWatched("52m", 0.5f),
            details = RecordingDetails(
                synopsis = "Westvale host Ridge Town under the lights with the league lead at stake. " +
                    "Full match with commentary from the Harbor Sport team.",
                genre = "Football",
                recorded = "Wed, Nov 11 • 8:30–10:15 PM",
                size = "5.1 GB",
                picture = "1080i • H.264",
                audio = "English",
                subtitles = "English",
                errors = "No errors",
                retention = "Kept until space is needed",
            ),
        )

private fun extraTime(id: String, day: String, state: RecordingProgress, line: String) =
    recording(id, "Extra Time", "still-river-court", HarborSport, day, "10:15 PM", "45m", line).copy(state = state)

private class Channel(val id: String, val name: String, val tint: Color)

private val HarborSport = Channel("harbor-sport", "Harbor Sport", Color(0xFF1E3D48))
private val NorthlineNews = Channel("northline-news", "Northline News", Color(0xFF1E2848))
private val HarborLights = Channel("harbor-lights", "Harbor Lights", Color(0xFF483D1E))
private val AmberStage = Channel("amber-stage", "Amber Stage", Color(0xFF483D1E))

private val pictures = mutableMapOf<String, BitmapPainter>()

private fun painter(path: String, decode: () -> android.graphics.Bitmap) =
    pictures.getOrPut(path) { BitmapPainter(decode().asImageBitmap()) }

private fun recording(
    id: String,
    title: String,
    art: String,
    channel: Channel,
    day: String,
    time: String,
    length: String,
    line: String,
) = RecordingItem(
    id = id,
    title = title,
    channel = channel.name,
    channelLogo = painter("picon:${channel.id}") { TouchFixtureArt.picon(channel.id) },
    logoTint = channel.tint,
    picture = painter("art:$art") { TouchFixtureArt.art(art) },
    day = day,
    time = time,
    length = length,
    line = line,
)

/** A folder whose content no drawing opens: counted, with placeholders for its subfolders. */
private fun filled(id: String, name: String, folders: Int, recordings: Int, size: String) = RecordingFolder(
    id, name, recordingCount = recordings, size = size,
    folders = List(folders) { RecordingFolder("$id-$it", "$name ${it + 1}") },
)
