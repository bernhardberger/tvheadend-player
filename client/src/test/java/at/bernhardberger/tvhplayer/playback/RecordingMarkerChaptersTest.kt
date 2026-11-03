@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.Chapter
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.PlaybackBindingResult
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecordingMarkerChaptersTest {
    @Test
    @OptIn(TvheadendTestingApi::class, ExperimentalCoroutinesApi::class)
    fun lateChaptersAndMarkerSeeksAreGatedByInstallationItemIdentityAndRevision() = runTest {
        val entry = DvrEntry.create(id = DvrEntryId(4), state = DvrEntryState.COMPLETED)
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
            )),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))),
        ))
        val binding = (TvheadendTestResultFactory.boundCompletedRecordingPlayback(
            session, session.captureCurrentSession(), entry.id,
        ) as PlaybackBindingResult.Bound).binding
        val installedItem = MediaItem.Builder().setMediaId("recording").build()
        var currentItem = installedItem
        var tracks = Tracks.EMPTY
        var trackReads = 0
        var installing = false
        val seeks = mutableListOf<Long>()
        val player = Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> currentItem
                "getCurrentTracks" -> { trackReads++; tracks }
                "getDuration" -> 20_000L
                "isCurrentMediaItemSeekable" -> true
                "isCommandAvailable" -> args!![0] == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
                "seekTo" -> { seeks.add(args!!.single() as Long); Unit }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer
        val markers = RecordingMarkers(player, session, backgroundScope, PlaybackTargetCommandSerialization()) { installing }
        try {
            markers.observeRecordingMarkersLocked(binding)
            runCurrent()
            assertTrue(markers.recordingChapterStartsMs.value.isEmpty())
            val emptyRevision = markers.recordingMarkerRevision.value
            tracks = Tracks(listOf(group("chapters", Format.Builder()
                .setMetadata(Metadata(chapter(4_000))).build())))
            markers.onTracksChanged()
            assertEquals(listOf(4_000L), markers.recordingChapterStartsMs.value)
            val revision = markers.recordingMarkerRevision.value
            assertTrue(revision > emptyRevision)

            installing = true
            tracks = Tracks(listOf(group("replacement", Format.Builder()
                .setMetadata(Metadata(chapter(8_000))).build())))
            val readsBeforeInstallCallback = trackReads
            markers.onTracksChanged()
            markers.seekRecordingMarker(4_000L, revision)
            assertEquals(readsBeforeInstallCallback, trackReads)
            assertEquals(listOf(4_000L), markers.recordingChapterStartsMs.value)
            assertEquals(revision, markers.recordingMarkerRevision.value)
            assertTrue(seeks.isEmpty())
            installing = false

            currentItem = installedItem.buildUpon().build()
            assertNotSame(installedItem, currentItem)
            markers.onTracksChanged()
            markers.seekRecordingMarker(4_000L, revision)
            assertEquals(readsBeforeInstallCallback, trackReads)
            assertEquals(listOf(4_000L), markers.recordingChapterStartsMs.value)
            assertEquals(revision, markers.recordingMarkerRevision.value)
            assertTrue(seeks.isEmpty())

            currentItem = installedItem
            markers.seekRecordingMarker(4_000L, emptyRevision)
            markers.seekRecordingMarker(8_000L, revision)
            assertTrue(seeks.isEmpty())
            markers.seekRecordingMarker(4_000L, revision)
            assertEquals(listOf(4_000L), seeks)
        } finally {
            markers.clearRecordingMarkersAndJoin()
        }
    }

    @Test
    fun chapterStartsComeFromAllTrackFormatsWithoutDuplicatesOrHiddenChapters() {
        val first = Format.Builder().setMetadata(Metadata(
            chapter(90), chapter(20), chapter(40, hidden = true), chapter(0),
        )).build()
        val second = Format.Builder().setMetadata(Metadata(chapter(20), chapter(30))).build()
        val tracks = Tracks(listOf(
            group("video", first, second), group("audio", first),
            group("without-metadata", Format.Builder().build()),
        ))
        assertEquals(listOf(0L, 20L, 30L, 90L), recordingChapterStarts(tracks))
    }

    @Test
    fun emptyTracksAndHiddenOnlyMetadataProduceNoChapters() {
        assertTrue(recordingChapterStarts(Tracks.EMPTY).isEmpty())
        val format = Format.Builder().setMetadata(Metadata(chapter(20, hidden = true))).build()
        assertTrue(recordingChapterStarts(Tracks(listOf(group("hidden", format)))).isEmpty())
    }

    private fun chapter(startMs: Long, hidden: Boolean = false): Chapter = Chapter.Builder()
        .setStartTimeMs(startMs).setEndTimeMs(startMs + 10).setHidden(hidden).build()

    private fun group(id: String, vararg formats: Format) = Tracks.Group(
        TrackGroup(id, *formats), false, IntArray(formats.size), BooleanArray(formats.size),
    )
}
