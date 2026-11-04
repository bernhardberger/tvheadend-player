@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.Format
import androidx.media3.common.Label
import at.bernhardberger.tvhplayer.core.RecordingChapter
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.common.Timeline
import androidx.media3.common.util.Clock
import android.os.Looper
import androidx.media3.extractor.metadata.Chapter
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointsResult
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
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.milliseconds
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.notices.Notice
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
    fun delayedCutpointsRespectInterveningDeliberateSeeksForCutsAskAndAutomatic() = runTest {
        for (mode in listOf(AdBreakMode.ASK, AdBreakMode.AUTOMATIC)) {
            for (action in listOf(DvrCutpointAction.CUT, DvrCutpointAction.COMMERCIAL_BREAK)) {
                val fixture = MarkerFixture(backgroundScope, listOf(
                    DvrCutpoint(1_000.milliseconds, 2_000.milliseconds, action),
                ))
                try {
                    fixture.markers.applySettings(PlayerSettings(adBreakMode = mode))
                    fixture.markers.observeRecordingMarkersLocked(fixture.binding)
                    runCurrent()
                    fixture.markers.deliberateSeek(1_500)
                    fixture.position = 1_500
                    fixture.response.complete(Unit)
                    runCurrent()
                    assertTrue(fixture.seeks.isEmpty())
                    assertEquals(null, fixture.markers.skipPrompt.value)
                    fixture.position = 2_000
                    fixture.markers.onPlaybackChanged()
                    runCurrent()
                    fixture.position = 1_500
                    fixture.markers.onPlaybackChanged()
                    runCurrent()
                    if (mode == AdBreakMode.ASK && action == DvrCutpointAction.COMMERCIAL_BREAK) {
                        assertEquals(1_000L, fixture.markers.skipPrompt.value?.startMs)
                    } else assertEquals(listOf(2_000L), fixture.seeks)
                } finally { fixture.markers.clearRecordingMarkersAndJoin() }
            }
        }
    }

    @Test
    @OptIn(TvheadendTestingApi::class, ExperimentalCoroutinesApi::class)
    fun queuedBoundaryHintsCannotSurviveDeliberateOrOtherPositionDiscontinuities() = runTest {
        for ((deliberate, action) in listOf(
            true to DvrCutpointAction.CUT, false to DvrCutpointAction.CUT,
            true to DvrCutpointAction.MUTE, false to DvrCutpointAction.MUTE,
        )) {
            val fixture = MarkerFixture(backgroundScope, listOf(
                DvrCutpoint(3_000.milliseconds, 3_050.milliseconds, action),
            ))
            try {
                fixture.response.complete(Unit)
                fixture.markers.observeRecordingMarkersLocked(fixture.binding)
                runCurrent()
                fixture.position = 3_000
                val message = fixture.messages.single { it.positionMs == 3_000L }
                message.target.handleMessage(message.type, message.payload)
                if (deliberate) fixture.markers.deliberateSeek(10_000)
                fixture.position = 10_000
                if (!deliberate) fixture.markers.onPositionDiscontinuity()
                runCurrent()
                assertTrue(fixture.seeks.isEmpty())
                assertEquals(0.65f, fixture.volume)

                // Even without a discontinuity notification, a future hint cannot
                // substitute for an actual position before that boundary.
                message.target.handleMessage(message.type, message.payload)
                fixture.position = 2_000
                runCurrent()
                assertTrue(fixture.seeks.isEmpty())
                assertEquals(0.65f, fixture.volume)
            } finally { fixture.markers.clearRecordingMarkersAndJoin() }
        }
    }

    @Test
    @OptIn(TvheadendTestingApi::class, ExperimentalCoroutinesApi::class)
    fun boundaryCallbackHeldUntilAfterSeekCannotUseTheNewEpoch() = runTest {
        val fixture = MarkerFixture(backgroundScope, listOf(
            DvrCutpoint(3_000.milliseconds, 3_050.milliseconds, DvrCutpointAction.CUT),
            DvrCutpoint(3_000.milliseconds, 3_050.milliseconds, DvrCutpointAction.MUTE),
        ))
        try {
            fixture.response.complete(Unit)
            fixture.markers.observeRecordingMarkersLocked(fixture.binding)
            runCurrent()
            val held = fixture.messages.single { it.positionMs == 3_000L }
            val oldMessages = fixture.messages.toList()
            fixture.markers.deliberateSeek(10_000)
            fixture.position = 10_000
            fixture.markers.onPositionDiscontinuity()
            runCurrent()
            assertTrue(oldMessages.all { it.isCanceled })
            assertEquals(listOf(3_000L, 3_050L), fixture.messages.filterNot { it.isCanceled }.map { it.positionMs })

            // This target has not executed at all until AFTER the seek and its
            // discontinuity: cancellation alone cannot retract a queued callback.
            held.target.handleMessage(held.type, held.payload)
            runCurrent()
            assertTrue(fixture.seeks.isEmpty())
            assertEquals(10_000L, fixture.position)
            assertEquals(0.65f, fixture.volume)

            // The replacement messages still handle subsequent natural entry.
            fixture.position = 3_000
            fixture.messages.last { it.positionMs == 3_000L }.let { it.target.handleMessage(it.type, it.payload) }
            runCurrent()
            assertEquals(listOf(3_050L), fixture.seeks)
            assertEquals(0.65f, fixture.volume)
        } finally { fixture.markers.clearRecordingMarkersAndJoin() }
    }

    @Test
    @OptIn(TvheadendTestingApi::class, ExperimentalCoroutinesApi::class)
    fun automaticSkipInvalidatesOtherQueuedBoundaryHints() = runTest {
        val fixture = MarkerFixture(backgroundScope, listOf(
            DvrCutpoint(3_000.milliseconds, 3_050.milliseconds, DvrCutpointAction.CUT),
            DvrCutpoint(4_000.milliseconds, 4_050.milliseconds, DvrCutpointAction.CUT),
        ))
        try {
            fixture.response.complete(Unit)
            fixture.markers.observeRecordingMarkersLocked(fixture.binding)
            runCurrent()
            fixture.position = 5_000
            val held = fixture.messages.single { it.positionMs == 3_000L }
            for (at in listOf(4_000L, 3_000L)) {
                fixture.messages.single { it.positionMs == at }.let { it.target.handleMessage(it.type, it.payload) }
            }
            runCurrent()
            assertEquals(listOf(4_050L), fixture.seeks)
            assertTrue(held.isCanceled)
            held.target.handleMessage(held.type, held.payload)
            runCurrent()
            assertEquals(listOf(4_050L), fixture.seeks)
        } finally { fixture.markers.clearRecordingMarkersAndJoin() }
    }

    @Test
    @OptIn(TvheadendTestingApi::class, ExperimentalCoroutinesApi::class)
    fun recoveryClearanceEvaluatesMuteWithoutAnyPlayerEvent() = runTest {
        val fixture = MarkerFixture(backgroundScope, listOf(
            DvrCutpoint(1_000.milliseconds, 2_000.milliseconds, DvrCutpointAction.MUTE),
        ))
        try {
            fixture.pendingRecoveryPosition.value = 20_000.milliseconds
            fixture.position = 1_500
            fixture.response.complete(Unit)
            fixture.markers.observeRecordingMarkersLocked(fixture.binding)
            runCurrent()
            assertEquals(0.65f, fixture.volume)
            fixture.pendingRecoveryPosition.value = null // expiry/abandonment, no seek or playing callback
            runCurrent()
            assertEquals(0f, fixture.volume)
            assertTrue(fixture.seeks.isEmpty())
            fixture.position = 2_000
            fixture.messages.single { it.positionMs == 2_000L }.let { it.target.handleMessage(it.type, it.payload) }
            runCurrent()
            assertEquals(0.65f, fixture.volume)
        } finally { fixture.markers.clearRecordingMarkersAndJoin() }
    }

    @Test
    @OptIn(TvheadendTestingApi::class, ExperimentalCoroutinesApi::class)
    fun sameTargetReinstallKeepsSkippedAndDeliberateHistoryButNewTargetResetsIt() = runTest {
        for (deliberate in listOf(false, true)) {
            val fixture = MarkerFixture(backgroundScope, listOf(
                DvrCutpoint(1_000.milliseconds, 2_000.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            ))
            try {
                fixture.markers.applySettings(PlayerSettings(adBreakMode = AdBreakMode.AUTOMATIC))
                fixture.markers.observeRecordingMarkersLocked(fixture.binding)
                runCurrent()
                fixture.position = 1_500
                if (deliberate) fixture.markers.deliberateSeek(fixture.position)
                fixture.response.complete(Unit)
                runCurrent()
                assertEquals(if (deliberate) emptyList<Long>() else listOf(2_000L), fixture.seeks)
                val oldMessages = fixture.messages.toList()
                fixture.currentItem = fixture.currentItem.buildUpon().build()
                fixture.position = 1_500
                fixture.markers.observeRecordingMarkersLocked(fixture.binding, continuesTarget = true)
                runCurrent()
                assertTrue(oldMessages.all { it.isCanceled })
                assertEquals(if (deliberate) emptyList<Long>() else listOf(2_000L), fixture.seeks)
                fixture.currentItem = fixture.currentItem.buildUpon().setMediaId("other-recording").build()
                fixture.position = 1_500
                fixture.markers.observeRecordingMarkersLocked(fixture.binding)
                runCurrent()
                assertEquals(if (deliberate) listOf(2_000L) else listOf(2_000L, 2_000L), fixture.seeks)
            } finally { fixture.markers.clearRecordingMarkersAndJoin() }
        }
    }

    @Test
    @OptIn(TvheadendTestingApi::class, ExperimentalCoroutinesApi::class)
    fun targetChangeRestoresVolumeCancelsMessagesAndRejectsOldSkipAndDismissCommands() = runTest {
        val fixture = MarkerFixture(backgroundScope, listOf(
            DvrCutpoint(1_000.milliseconds, 2_000.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(1_000.milliseconds, 2_000.milliseconds, DvrCutpointAction.MUTE),
        ))
        try {
            fixture.position = 1_500
            fixture.response.complete(Unit)
            fixture.markers.observeRecordingMarkersLocked(fixture.binding)
            runCurrent()
            val offer = requireNotNull(fixture.markers.skipPrompt.value)
            val oldRevision = fixture.markers.recordingMarkerRevision.value
            assertEquals(0f, fixture.volume)
            fixture.markers.dismissSkipPrompt(offer, oldRevision - 1)
            fixture.markers.skipAdBreak(offer, oldRevision - 1)
            assertEquals(offer, fixture.markers.skipPrompt.value)
            assertTrue(fixture.seeks.isEmpty())
            fixture.tracks = Tracks(listOf(group("chapters", Format.Builder()
                .setMetadata(Metadata(chapter(500, name = "Opening"))).build())))
            fixture.markers.onTracksChanged()
            val replacedRevision = fixture.markers.recordingMarkerRevision.value
            assertTrue(replacedRevision > oldRevision)
            fixture.markers.dismissSkipPrompt(offer, oldRevision)
            fixture.markers.skipAdBreak(offer, oldRevision)
            assertEquals(offer, fixture.markers.skipPrompt.value)
            assertTrue(fixture.seeks.isEmpty())
            val oldMessages = fixture.messages.toList()
            fixture.position = 0
            fixture.currentItem = fixture.currentItem.buildUpon().setMediaId("other-recording").build()
            fixture.markers.observeRecordingMarkersLocked(fixture.binding)
            assertEquals(0.65f, fixture.volume)
            assertTrue(oldMessages.all { it.isCanceled })
            runCurrent()
            fixture.position = 1_500
            fixture.markers.onPlaybackChanged()
            runCurrent()
            assertEquals(offer, fixture.markers.skipPrompt.value)
            fixture.markers.dismissSkipPrompt(offer, replacedRevision)
            fixture.markers.skipAdBreak(offer, replacedRevision)
            assertEquals(offer, fixture.markers.skipPrompt.value)
            assertTrue(fixture.seeks.isEmpty())
            oldMessages.first().let { it.target.handleMessage(it.type, it.payload) }
            runCurrent()
            assertEquals(offer, fixture.markers.skipPrompt.value)
            assertTrue(fixture.seeks.isEmpty())
            fixture.markers.dismissSkipPrompt(offer, fixture.markers.recordingMarkerRevision.value)
            assertEquals(null, fixture.markers.skipPrompt.value)
        } finally { fixture.markers.clearRecordingMarkersAndJoin() }
    }

    @Test
    @OptIn(TvheadendTestingApi::class, ExperimentalCoroutinesApi::class)
    fun noSpanRecordingSchedulesNothingAndSeparateAutomaticSkipsHaveSeparateNoticeKeys() = runTest {
        val fixture = MarkerFixture(backgroundScope, emptyList())
        try {
            fixture.response.complete(Unit)
            fixture.markers.observeRecordingMarkersLocked(fixture.binding)
            runCurrent()
            fixture.markers.onPlaybackChanged()
            runCurrent()
            assertTrue(fixture.messages.isEmpty())
            assertEquals(0, fixture.positionReads)
        } finally { fixture.markers.clearRecordingMarkersAndJoin() }
        val ads = MarkerFixture(backgroundScope, listOf(
            DvrCutpoint(1_000.milliseconds, 2_000.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(3_000.milliseconds, 4_000.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
        ))
        try {
            ads.markers.applySettings(PlayerSettings(adBreakMode = AdBreakMode.AUTOMATIC))
            ads.response.complete(Unit)
            ads.markers.observeRecordingMarkersLocked(ads.binding)
            runCurrent()
            for (start in listOf(1_000L, 3_000L)) {
                ads.position = start
                ads.messages.last { !it.isCanceled && it.positionMs == start }.let { it.target.handleMessage(it.type, it.payload) }
                runCurrent()
            }
            assertEquals(listOf(2_000L, 4_000L), ads.seeks)
            assertEquals(2, ads.notices.state.value.pending.size)
            assertEquals(2, ads.notices.state.value.pending.map { it.notice.key }.distinct().size)
        } finally { ads.markers.clearRecordingMarkersAndJoin() }
    }

    @OptIn(TvheadendTestingApi::class)
    private class MarkerFixture(scope: kotlinx.coroutines.CoroutineScope, points: List<DvrCutpoint>) {
        private val entry = DvrEntry.create(id = DvrEntryId(4), state = DvrEntryState.COMPLETED)
        private val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
            )),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))),
        ))
        val response = CompletableDeferred<Unit>()
        val binding = (TvheadendTestResultFactory.boundCompletedRecordingPlayback(
            session, session.captureCurrentSession(), entry.id,
            cutpoints = { response.await(); DvrCutpointsResult.Available.create(points) },
        ) as PlaybackBindingResult.Bound).binding
        var currentItem = MediaItem.Builder().setMediaId("recording").build()
        var tracks = Tracks.EMPTY
        var position = 0L
        var positionReads = 0
        var volume = 0.65f
        val seeks = mutableListOf<Long>()
        val messages = mutableListOf<PlayerMessage>()
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> currentItem
                "getCurrentTracks" -> tracks
                "getCurrentPosition" -> { positionReads++; position }
                "getApplicationLooper" -> Looper.getMainLooper()
                "createMessage" -> PlayerMessage({ messages.add(it) }, args!![0] as PlayerMessage.Target,
                    Timeline.EMPTY, 0, Clock.DEFAULT, Looper.getMainLooper())
                "isPlaying", "isCurrentMediaItemSeekable" -> true
                "isCommandAvailable" -> args!![0] == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
                "getVolume" -> volume
                "setVolume" -> { volume = args!![0] as Float; Unit }
                "seekTo" -> { position = args!!.single() as Long; seeks.add(position); Unit }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, null) }
        val pendingRecoveryPosition = kotlinx.coroutines.flow.MutableStateFlow<kotlin.time.Duration?>(null)
        val markers = RecordingMarkers(player, session, scope, PlaybackTargetCommandSerialization(), notices, pendingRecoveryPosition) { false }
    }

    @Test
    @OptIn(TvheadendTestingApi::class, ExperimentalCoroutinesApi::class)
    fun lateSpansSkipOncePreservePausedIntentAndRestoreMutedVolumeOnExitOrClear() = runTest {
        val entry = DvrEntry.create(id = DvrEntryId(4), state = DvrEntryState.COMPLETED)
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
            )),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))),
        ))
        val response = CompletableDeferred<Unit>()
        val points = listOf(
            DvrCutpoint(1_000.milliseconds, 2_000.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(3_000.milliseconds, 3_050.milliseconds, DvrCutpointAction.CUT),
            DvrCutpoint(5_000.milliseconds, 5_050.milliseconds, DvrCutpointAction.MUTE),
        )
        val binding = (TvheadendTestResultFactory.boundCompletedRecordingPlayback(
            session, session.captureCurrentSession(), entry.id,
            cutpoints = { response.await(); DvrCutpointsResult.Available.create(points) },
        ) as PlaybackBindingResult.Bound).binding
        val item = MediaItem.Builder().setMediaId("recording").build()
        var currentItem = item
        var position = 1_500L
        var playing = true
        var volume = 0.65f
        var installing = false
        val seeks = mutableListOf<Long>()
        val messages = mutableListOf<PlayerMessage>()
        val player = Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> currentItem
                "getCurrentTracks" -> Tracks.EMPTY
                "getDuration" -> 10_000L
                "getCurrentPosition" -> position
                "getApplicationLooper" -> Looper.getMainLooper()
                "createMessage" -> PlayerMessage({ messages.add(it) }, args!![0] as PlayerMessage.Target,
                    Timeline.EMPTY, 0, Clock.DEFAULT, Looper.getMainLooper())
                "isPlaying" -> playing
                "getVolume" -> volume
                "setVolume" -> { volume = args!![0] as Float; Unit }
                "isCurrentMediaItemSeekable" -> true
                "isCommandAvailable" -> args!![0] == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
                "seekTo" -> { position = args!!.single() as Long; seeks.add(position); Unit }
                // Any setPlayWhenReady/play/pause call fails this fixture.
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer
        val notices = NoticeCenter({ testScheduler.currentTime }) { NoticeContext(0, null) }
        val markers = RecordingMarkers(player, session, backgroundScope, PlaybackTargetCommandSerialization(), notices,
            kotlinx.coroutines.flow.MutableStateFlow<kotlin.time.Duration?>(null)) { installing }
        fun sample() { markers.onPlaybackChanged(); runCurrent() }
        fun boundary(at: Long, positionAtEvaluation: Long = at) {
            position = at
            val message = messages.last { !it.isCanceled && it.positionMs == at }
            assertEquals(Looper.getMainLooper(), message.looper)
            assertEquals(false, message.deleteAfterDelivery)
            message.target.handleMessage(message.type, message.payload)
            position = positionAtEvaluation
            runCurrent()
        }
        try {
            markers.applySettings(PlayerSettings(adBreakMode = AdBreakMode.AUTOMATIC))
            markers.observeRecordingMarkersLocked(binding)
            runCurrent()
            assertTrue(seeks.isEmpty())
            response.complete(Unit)
            sample()
            assertEquals(listOf(2_000L), seeks)
            assertEquals(Notice.AutomaticAdBreakSkip(1_000), notices.state.value.pending.single().notice)
            assertEquals(listOf(1_000L, 2_000L, 3_000L, 3_050L, 5_000L, 5_050L), messages.filterNot { it.isCanceled }.map { it.positionMs })
            val noticeId = notices.state.value.pending.single().id

            markers.deliberateSeek(1_500)
            position = 1_500
            sample()
            assertEquals(listOf(2_000L), seeks)
            position = 2_500
            sample()
            position = 1_500 // Reentry into an automatically skipped break plays it too.
            sample()
            assertEquals(listOf(2_000L), seeks)
            assertEquals(noticeId, notices.state.value.pending.single().id)

            playing = false
            position = 3_000
            sample()
            assertEquals(listOf(2_000L), seeks)
            playing = true
            boundary(3_000, positionAtEvaluation = 3_100)
            assertEquals(listOf(2_000L, 3_050L), seeks)
            assertEquals(noticeId, notices.state.value.pending.single().id)

            boundary(5_000, positionAtEvaluation = 5_100)
            assertEquals(0.65f, volume) // A delayed hint cannot mute past the actual end.
            boundary(5_000, positionAtEvaluation = 5_025)
            assertEquals(0f, volume)
            boundary(5_050)
            assertEquals(0.65f, volume)
            position = 5_025
            sample()
            val oldMessages = messages.toList()
            markers.clearRecordingMarkers()
            assertEquals(0.65f, volume)
            assertTrue(messages.all { it.isCanceled })
            val seeksBeforeStaleDelivery = seeks.toList()
            for (message in oldMessages) message.target.handleMessage(message.type, message.payload)
            runCurrent()
            assertEquals(0.65f, volume)
            assertEquals(seeksBeforeStaleDelivery, seeks)

            markers.applySettings(PlayerSettings())
            position = 1_500
            markers.observeRecordingMarkersLocked(binding)
            sample()
            for (message in oldMessages) message.target.handleMessage(message.type, message.payload)
            runCurrent()
            assertEquals(0.65f, volume)
            assertEquals(seeksBeforeStaleDelivery, seeks)
            val offered = requireNotNull(markers.skipPrompt.value)
            val revision = markers.recordingMarkerRevision.value
            markers.skipAdBreak(offered, revision - 1)
            assertEquals(listOf(2_000L, 3_050L), seeks)
            markers.dismissSkipPrompt(offered, revision - 1)
            assertEquals(offered, markers.skipPrompt.value)
            installing = true
            markers.skipAdBreak(offered, revision)
            assertEquals(listOf(2_000L, 3_050L), seeks)
            installing = false
            currentItem = item.buildUpon().build()
            markers.skipAdBreak(offered, revision)
            assertEquals(listOf(2_000L, 3_050L), seeks)
            markers.dismissSkipPrompt(offered, revision)
            assertEquals(offered, markers.skipPrompt.value)
            currentItem = item
            playing = false
            markers.skipAdBreak(offered, revision)
            assertEquals(listOf(2_000L, 3_050L, 2_000L), seeks)
            assertEquals(noticeId, notices.state.value.pending.single().id)
        } finally {
            markers.clearRecordingMarkersAndJoin()
        }
    }

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
                "getCurrentPosition" -> 0L
                "isPlaying" -> false
                "isCurrentMediaItemSeekable" -> true
                "isCommandAvailable" -> args!![0] == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
                "seekTo" -> { seeks.add(args!!.single() as Long); Unit }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer
        val markers = RecordingMarkers(player, session, backgroundScope, PlaybackTargetCommandSerialization(),
            NoticeCenter({ 0L }) { NoticeContext(0, null) },
            kotlinx.coroutines.flow.MutableStateFlow<kotlin.time.Duration?>(null)) { installing }
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
            chapter(90, name = "Finale"), chapter(20), chapter(40, hidden = true), chapter(0),
        )).build()
        val second = Format.Builder().setMetadata(Metadata(chapter(20), chapter(30))).build()
        val tracks = Tracks(listOf(
            group("video", first, second), group("audio", first),
            group("without-metadata", Format.Builder().build()),
        ))
        assertEquals(listOf(RecordingChapter(0), RecordingChapter(20), RecordingChapter(30), RecordingChapter(90, "Finale")), recordingChapters(tracks))
    }

    @Test
    fun emptyTracksAndHiddenOnlyMetadataProduceNoChapters() {
        assertTrue(recordingChapters(Tracks.EMPTY).isEmpty())
        val format = Format.Builder().setMetadata(Metadata(chapter(20, hidden = true))).build()
        assertTrue(recordingChapters(Tracks(listOf(group("hidden", format)))).isEmpty())
    }

    private fun chapter(startMs: Long, hidden: Boolean = false, name: String? = null): Chapter = Chapter.Builder()
        .setStartTimeMs(startMs).setEndTimeMs(startMs + 10).setHidden(hidden)
        .apply { if (name != null) setTitle(Label(null, name)) }.build()

    private fun group(id: String, vararg formats: Format) = Tracks.Group(
        TrackGroup(id, *formats), false, IntArray(formats.size), BooleanArray(formats.size),
    )
}
