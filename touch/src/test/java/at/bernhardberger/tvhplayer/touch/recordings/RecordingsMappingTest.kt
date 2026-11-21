package at.bernhardberger.tvhplayer.touch.recordings

import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import java.time.ZoneOffset
import java.util.Locale
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(TvheadendTestingApi::class)
class RecordingsMappingTest {
    private val guide = ServerGuide(Instant.fromEpochSeconds(NOW))
    private val clock = RecordingClock(java.time.Instant.ofEpochSecond(NOW), ZoneOffset.UTC, Locale.US)
    private val texts = RecordingsTexts(
        noChannel = "No channel", noGenre = "No genre", recordingNow = "Recording now",
        genre = { null }, episode = { null }, section = { it.toString() }, size = { "$it B" },
        keepUntil = { _, _ -> null },
    )
    private val unreachable = ConnectionUiState.Error(
        ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY,
    )

    @Test
    fun connectionViewAndListChooseTheState() {
        data class Case(
            val name: String,
            val connection: ConnectionUiState,
            val dvr: DvrRepositoryState,
            val view: DvrArchiveView = DvrArchiveView(),
            val degraded: Boolean = false,
            val expected: (RecordingsState) -> Boolean,
        )
        val all = DvrRepositoryState.Current(guide.dvr)
        val cases = listOf(
            Case("password unreadable", ConnectionUiState.CredentialUnavailable, all) {
                it.availability == RecordingsAvailability.SignInNeeded
            },
            Case("connecting, nothing known", ConnectionUiState.Connecting, DvrRepositoryState.Empty) {
                it.availability == RecordingsAvailability.Loading
            },
            Case("unreachable, nothing saved", unreachable, DvrRepositoryState.Empty) {
                (it.availability as? RecordingsAvailability.Failed)?.error?.condition == AppErrorCondition.UNREACHABLE
            },
            Case("no recordings", ConnectionUiState.Ready, DvrRepositoryState.Current(DvrSnapshot.create())) {
                it.availability == RecordingsAvailability.Current && it.root.recordings.isEmpty()
            },
            // Running recordings are never hidden by Show, so the filter is tried on the finished film alone.
            Case(
                "filter leaves nothing", ConnectionUiState.Ready,
                DvrRepositoryState.Current(DvrSnapshot.create(listOf(guide.film))),
                DvrArchiveView(show = DvrArchiveShow.WATCHED),
            ) { it.availability == RecordingsAvailability.Current && it.root.recordings.isEmpty() },
            Case("offline with the saved list", unreachable, DvrRepositoryState.Stale(guide.dvr), degraded = true) {
                it.availability == RecordingsAvailability.Offline && it.titles() == FLAT_NEWEST
            },
            Case("newest first, dated, without the scheduled one", ConnectionUiState.Ready, all) {
                it.titles() == FLAT_NEWEST && it.root.recordings.all { row -> row.section != null }
            },
            Case("by name, undated", ConnectionUiState.Ready, all, DvrArchiveView(sort = DvrArchiveSort.NAME)) {
                it.titles() == FLAT_NEWEST.sorted() && it.root.recordings.all { row -> row.section == null }
            },
            Case(
                "unwatched only", ConnectionUiState.Ready, all, DvrArchiveView(show = DvrArchiveShow.UNWATCHED),
            ) { it.titles() == listOf("Evening News", "Harbor Lights") },
            Case(
                "a folder per channel", ConnectionUiState.Ready, all,
                DvrArchiveView(grouping = DvrArchiveGrouping.CHANNELS),
            ) { it.root.recordings.isEmpty() && it.root.folders.map { f -> f.name }.toSet() == CHANNELS },
        )
        cases.forEach { case ->
            val observation = SessionObservation.create(
                channelState = ChannelRepositoryState.Current(guide.catalog),
                dvrState = case.dvr,
            )
            val state = recordingsState(observation, case.view, case.connection, case.degraded, clock, texts)
            assertEquals(case.name, true, case.expected(state))
        }
    }

    @Test
    fun foldersOpenOneLevelBehindRecordingNow() {
        val observation = SessionObservation.create(
            channelState = ChannelRepositoryState.Current(guide.catalog),
            dvrState = DvrRepositoryState.Current(guide.dvr),
        )
        val view = DvrArchiveView(grouping = DvrArchiveGrouping.FOLDERS)
        val root = recordingsState(observation, view, ConnectionUiState.Ready, false, clock, texts).root

        val now = root.folders.first()
        assertEquals(true, now.recordingNow)
        assertEquals(listOf("Evening News"), now.recordings.map { it.title })
        val films = root.folders.single { it.name == "Films" }
        assertEquals(listOf("Harbor Lights"), films.recordings.map { it.title })
        assertEquals(emptyList<RecordingFolder>(), films.folders)
        // The running recording reads as recording on its channel until its stop.
        val running = now.recordings.single()
        assertEquals("Channel One", running.channel)
        assertEquals(true, running.state is RecordingProgress.Recording)
    }

    private fun RecordingsState.titles() = root.recordings.map { it.title }

    private companion object {
        const val NOW = 1_800_000_000L
        val FLAT_NEWEST = listOf("Evening News", "Mountain Diary", "Harbor Lights")
        val CHANNELS = setOf("Channel One", "Channel Two")
    }
}
