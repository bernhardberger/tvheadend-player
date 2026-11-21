package at.bernhardberger.tvhplayer.touch.recordings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import at.bernhardberger.tvhplayer.touch.testutil.TouchFixtureArt
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping

// The round-37 drawings' recordings (mockups html27 flat/folders, html13 week rows, html33 upright list).

internal val FixtureSpace = RecordingSpace("1.2 TB", "4.0 TB", usedFraction = 0.7f)

/** The id of the half-watched match every drawing picks. */
internal const val PickedId = "kickoff-nov-11"

/**
 * All recordings, newest first, on the evening of Mon, Nov 16; [harborLights] and [glassHarbor] add the rows
 * the taller canvases draw.
 */
internal fun flatFixture(
    harborLights: Boolean = false,
    glassHarbor: Boolean = false,
    view: DvrArchiveView = DvrArchiveView(),
) = RecordingsState(
        view = view,
        clock = FixtureClock,
        space = FixtureSpace,
        root = RecordingFolder(
            id = "root", name = "Recordings", recordingCount = 116,
            recordings = listOfNotNull(
                kickoffLive().copy(section = day("Today")),
                recording("field-desk", "Field Desk", "still-field-desk-street", NorthlineNews, at(16, 12, 15), 45),
                extraTime("extra-yesterday", at(15, 22, 15), RecordingProgress.Watched).copy(section = day("Yesterday")),
                recording(
                    "harbor-lights-live", "Harbor Lights Live", "still-harbor-lights-live", HarborLights,
                    at(15, 20, 0), 120,
                ).takeIf { harborLights },
                kickoffSaturday().copy(section = day("Sat, Nov 14")),
                kickoffPicked().copy(section = day("Wed, Nov 11")),
                extraTime("extra-nov-11", at(11, 22, 15), RecordingProgress.Watched),
                recording("glass-harbor", "Glass Harbor", "still-glass-harbor", AmberStage, at(10, 21, 0), 87).copy(
                    episode = "The Lantern Room", section = day("Tue, Nov 10"),
                    state = RecordingProgress.PartlyWatched(Duration.ofMinutes(62), 0.29f),
                ).takeIf { glassHarbor },
            ),
        ),
    )

/** Grouped by folders, from the root down to Sport › Football › Harbor Sport › Week 46. */
internal fun foldersFixture() = RecordingsState(
    view = DvrArchiveView(grouping = DvrArchiveGrouping.FOLDERS),
    clock = FixtureClock,
    space = FixtureSpace,
    root = RecordingFolder(
        id = "root", name = "Recordings", recordingCount = 116,
        folders = listOf(
            RecordingFolder(
                "now", "Recording now", recordingNow = true,
                recordings = listOf(kickoffLive()),
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
                                    RecordingFolder("week-46", "Week 46", recordings = weekRows(), size = "27 GB"),
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
    view = DvrArchiveView(show = DvrArchiveShow.UNWATCHED),
    clock = FixtureClock,
    space = FixtureSpace,
    root = RecordingFolder("root", "Recordings"),
)

/** No recordings on the server. */
internal fun noRecordingsFixture() = RecordingsState(
    view = DvrArchiveView(),
    clock = FixtureClock,
    space = RecordingSpace("4.0 TB", "4.0 TB", usedFraction = 0f),
    root = RecordingFolder("root", "Recordings"),
)

private fun weekRows() = listOf(
    kickoffLive().copy(id = "week-kickoff-live"),
    kickoffSaturday().copy(id = "week-kickoff-14"),
    extraTime("week-extra-14", at(14, 22, 15), RecordingProgress.Unwatched),
    kickoffPicked(),
    extraTime("week-extra-11", at(11, 22, 15), RecordingProgress.Watched),
    recording("week-preview", "Harbor Kickoff", "harbor-kickoff", HarborSport, at(9, 19, 0), 30)
        .copy(episode = "Cup preview", state = RecordingProgress.Watched),
)

private fun kickoffLive() =
    recording("kickoff-live", "Harbor Kickoff", "still-harbor-kickoff", HarborSport, at(16, 20, 30), 105)
        .copy(episode = "Northport – Eastmoor", state = RecordingProgress.Recording(at(16, 22, 15), 0.16f))

private fun kickoffSaturday() =
    recording("kickoff-saturday", "Harbor Kickoff", "harbor-kickoff", HarborSport, at(14, 20, 15), 105)
        .copy(episode = "Kingsbridge – Northport")

private fun kickoffPicked() =
    recording(PickedId, "Harbor Kickoff", "still-harbor-kickoff", HarborSport, at(11, 20, 30), 105)
        .copy(
            episode = "Westvale – Ridge Town",
            state = RecordingProgress.PartlyWatched(Duration.ofMinutes(52), 0.5f),
            play = listOf(RecordingPlayAction.Resume(kotlin.time.Duration.parse("53m")), RecordingPlayAction.StartOver),
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

private fun extraTime(id: String, start: Instant, state: RecordingProgress) =
    recording(id, "Extra Time", "still-river-court", HarborSport, start, 45).copy(state = state)

/** The drawings' evening: Mon, Nov 16, during the live match. */
internal val FixtureClock = RecordingClock(
    now = Instant.parse("2026-11-16T21:00:00Z"), zone = ZoneOffset.UTC, locale = Locale.US,
    // The drawings' "8:30 PM", in the JDK's short style the captures were drawn with.
    time = { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US).format(it) },
)

private fun at(dayOfNovember: Int, hour: Int, minute: Int): Instant =
    LocalDateTime.of(2026, 11, dayOfNovember, hour, minute).toInstant(ZoneOffset.UTC)

private fun day(label: String) = RecordingSection(label, RowDates.DayLabel)

private class Channel(val id: String, val name: String, val tint: Color)

private val HarborSport = Channel("harbor-sport", "Harbor Sport", Color(0xFF1E3D48))
private val NorthlineNews = Channel("northline-news", "Northline News", Color(0xFF1E2848))
private val HarborLights = Channel("harbor-lights", "Harbor Lights", Color(0xFF483D1E))
private val AmberStage = Channel("amber-stage", "Amber Stage", Color(0xFF483D1E))

private val pictures = mutableMapOf<String, BitmapPainter>()

private fun painter(path: String, decode: () -> android.graphics.Bitmap) =
    pictures.getOrPut(path) { BitmapPainter(decode().asImageBitmap()) }

private fun recording(id: String, title: String, art: String, channel: Channel, start: Instant, minutes: Long) =
    RecordingItem(
        id = id,
        title = title,
        channel = channel.name,
        channelLogo = painter("picon:${channel.id}") { TouchFixtureArt.picon(channel.id) },
        logoTint = channel.tint,
        picture = painter("art:$art") { TouchFixtureArt.art(art) },
        start = start,
        length = Duration.ofMinutes(minutes),
    )

/** A folder whose content no drawing opens: counted, with placeholders for its subfolders. */
private fun filled(id: String, name: String, folders: Int, recordings: Int, size: String) = RecordingFolder(
    id, name, recordingCount = recordings, size = size,
    folders = List(folders) { RecordingFolder("$id-$it", "$name ${it + 1}") },
)
