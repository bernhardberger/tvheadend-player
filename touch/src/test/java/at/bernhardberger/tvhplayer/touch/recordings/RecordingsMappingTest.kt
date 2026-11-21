package at.bernhardberger.tvhplayer.touch.recordings

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.touch.player.playingRecordingId

import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingStream
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import java.time.ZoneOffset
import java.time.Duration
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.ui.common.formatClock
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(TvheadendTestingApi::class)
class RecordingsMappingTest {
    private val guide = ServerGuide(Instant.fromEpochSeconds(NOW))
    private val clock = RecordingClock(java.time.Instant.ofEpochSecond(NOW), ZoneOffset.UTC, Locale.US) {
        formatClock(it, Locale.US, uses24Hour = false)
    }
    private val texts = FixtureRecordingsTexts
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

    @Test
    fun watchStateFollowsTheAdmittedResumePosition() {
        val partiallyWatched = DvrArchiveView(show = DvrArchiveShow.PARTIALLY_WATCHED)
        val entries = listOf(
            watchEntry(),
            watchEntry(position = 15),
            watchEntry(count = 1),
            watchEntry(state = DvrEntryState.RECORDING),
        )
        val expected = listOf(
            RecordingProgress.Unwatched,
            RecordingProgress.PartlyWatched(Duration.ofMinutes(45), 0.25f),
            RecordingProgress.Watched,
            RecordingProgress.Recording(java.time.Instant.ofEpochSecond(NOW + 30 * 60), 0.5f),
        )
        entries.zip(expected).forEach { (entry, progress) ->
            val item = mapped(entry)
            assertEquals(entry.state.toString(), progress, item.state)
            val left = recordingLine(item, RowContext(RowDates.None), clock).filterIsInstance<RecordingLinePart.Left>()
            assertEquals(listOfNotNull((progress as? RecordingProgress.PartlyWatched)?.let {
                RecordingLinePart.Left(it.left)
            }), left)
            val filtered = recordingsState(
                watchObservation(entry), partiallyWatched, ConnectionUiState.Ready, false, clock, texts,
            )
            assertEquals(
                progress is RecordingProgress.PartlyWatched || progress is RecordingProgress.Recording,
                filtered.root.recordings.isNotEmpty(),
            )
        }
    }

    @Test
    @OptIn(ExperimentalTvheadendApi::class)
    fun detailsOfferOnlyAdmittedPlaybackActions() {
        val watch = listOf(RecordingPlayAction.Watch)
        val resume = listOf(RecordingPlayAction.Resume(15.minutes), RecordingPlayAction.StartOver)
        val cases = listOf(
            watchEntry() to watch,
            watchEntry(position = 15) to resume,
            watchEntry(count = 1) to watch,
            watchEntry(state = DvrEntryState.RECORDING) to watch,
            watchEntry(state = DvrEntryState.RECORDING, position = 15) to
                listOf(RecordingPlayAction.Watch, RecordingPlayAction.StartOver),
            watchEntry(state = DvrEntryState.SCHEDULED) to emptyList(),
            watchEntry(state = DvrEntryState.COMPLETED_ERROR) to watch,
            watchEntry(state = DvrEntryState.COMPLETED_ERROR, hasFile = false) to emptyList(),
            watchEntry(hasFile = false) to watch,
        )
        cases.forEach { (entry, expected) ->
            val observation = watchObservation(entry)
            assertEquals(entry.state.toString(), expected, recordingPlayActions(entry,
                observation.recordingPlaybackAdmission(entry)))
        }
        for (position in listOf(null, 15)) {
            val entry = watchEntry(position = position)
            val item = recordingsState(
                watchObservation(entry), DvrArchiveView(), ConnectionUiState.Ready, false, clock, texts,
                playingRecordingId = entry.id,
            ).root.recordings.single()
            val expected = listOfNotNull(
                RecordingPlayAction.ContinueWatching,
                RecordingPlayAction.StartOver.takeIf { position != null },
            )
            assertEquals("Playing actions at saved position $position", expected, item.play)
        }
    }

    @Test
    fun detailsExposeWatchAndFileSummaryForACompletedRecording() {
        val item = mapped(watchEntry())
        assertEquals(listOf(RecordingPlayAction.Watch), item.play)
        assertEquals(Duration.ofMinutes(60), item.length)
        assertEquals("64 B", item.details.size)
    }

    private fun mapped(entry: DvrEntry): RecordingItem = recordingsState(
        watchObservation(entry), DvrArchiveView(), ConnectionUiState.Ready, false, clock, texts,
    ).root.recordings.single()

    @Test
    fun onlyTheActiveRecordingIsMarkedPlaying() {
        val id = guide.film.id
        val recording = AppPlaybackTarget.Recording(id)
        val cases = listOf(
            Triple(recording, AppPlaybackState.Starting, id),
            Triple(recording, AppPlaybackState.Playing, id),
            Triple(recording, AppPlaybackState.Buffering, id),
            Triple(recording, AppPlaybackState.Idle, null),
            Triple(recording, AppPlaybackState.Finished, null),
            Triple(recording, AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER), null),
            Triple(AppPlaybackTarget.Live(ChannelId(1)), AppPlaybackState.Playing, null),
            Triple(null, AppPlaybackState.Playing, null),
        )
        for ((target, playback, expected) in cases) {
            val state = recordingsState(
                guide.connected(), DvrArchiveView(), ConnectionUiState.Ready, false, clock, texts,
                playingRecordingId = playingRecordingId(target, playback),
            )
            assertEquals(
                playback.toString(), listOfNotNull(expected?.value?.toString()),
                state.root.recordings.filter { it.playing }.map { it.id },
            )
        }
    }

    @OptIn(ExperimentalTvheadendApi::class)
    private fun watchObservation(entry: DvrEntry): SessionObservation {
        val connected = guide.connected(listOf(entry))
        return SessionObservation.create(
            sessionState = connected.sessionState,
            channelState = connected.channelState,
            epgState = connected.epgState,
            dvrState = connected.dvrState,
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED,
        )
    }

    @OptIn(ExperimentalTvheadendApi::class)
    private fun watchEntry(
        state: DvrEntryState = DvrEntryState.COMPLETED,
        position: Int? = null,
        count: Long = 0,
        hasFile: Boolean = true,
    ): DvrEntry {
        val running = state == DvrEntryState.RECORDING
        val start = guide.now - if (running) 30.minutes else 60.minutes
        val file = if (running) {
            TvheadendTestResultFactory.recordingFile(fileId = 1, serverPath = "synthetic.ts", start = start)
        } else DvrRecordingFile.create(
            fileId = 1, start = start, stop = guide.now.takeUnless { running }, sizeBytes = 64,
            streams = listOf(
                DvrRecordingStream(SubscriptionStreamType.H264, height = 1080),
                DvrRecordingStream(SubscriptionStreamType.AAC, language = "eng"),
                DvrRecordingStream(SubscriptionStreamType.AC3, language = "eng"),
                DvrRecordingStream(SubscriptionStreamType.DVB_SUBTITLE, language = "eng"),
            ),
        )
        return DvrEntry.create(
            id = guide.film.id, state = state, start = if (running) start else start + 10.minutes,
            stop = start + 60.minutes,
            files = listOf(file).takeIf { hasFile }, playPosition = position?.minutes, playCount = count,
            streamErrors = 1, dataErrors = 2,
        )
    }

    @Test
    fun recordingsCarryTheirChannelsLogoAndTheirPicture() {
        val connected = guide.connected()
        val state = recordingsState(
            connected, DvrArchiveView(), ConnectionUiState.Ready, false, clock, texts, ArtworkModels(tvheadend = null),
        )
        val logo = AppArtworkSource(connected.currentSession, (guide.logo as ChannelIcon.ImageCache).artworkId)
        val byTitle = state.root.recordings.associateBy { it.title }
        val film = byTitle.getValue("Harbor Lights")
        assertEquals(logo, film.channelLogoModel)
        assertEquals(AppArtworkSource(connected.currentSession, guide.picture), film.pictureModel)
        // The second channel has no logo and the diary no picture.
        val diary = byTitle.getValue("Mountain Diary")
        assertEquals(null, diary.channelLogoModel)
        assertEquals(null, diary.pictureModel)
    }

    @Test
    fun rowsKeepTheirPictureSlotWhileOneRowHasSomethingToShow() {
        fun item(logo: Any? = null, picture: Any? = null) =
            RecordingItem("r", "Title", start = null, length = null, channelLogoModel = logo, pictureModel = picture)
        val bare = item()
        val cases = listOf(
            "no row has a picture or a logo" to (RecordingFolder("root", "", recordings = listOf(bare, bare)) to false),
            "one row has a logo" to (RecordingFolder("root", "", recordings = listOf(bare, item(logo = "logo"))) to true),
            "one row has a picture" to (RecordingFolder("root", "", recordings = listOf(item(picture = "p"), bare)) to true),
            "a folder tile" to (
                RecordingFolder("root", "", folders = listOf(RecordingFolder("f", "Films")), recordings = listOf(bare)) to true
                ),
        )
        cases.forEach { (name, case) -> assertEquals(name, case.second, case.first.showsThumbnails) }
    }

    private fun RecordingsState.titles() = root.recordings.map { it.title }

    private companion object {
        const val NOW = 1_800_000_000L
        val FLAT_NEWEST = listOf("Evening News", "Mountain Diary", "Harbor Lights")
        val CHANNELS = setOf("Channel One", "Channel Two")
    }
}
