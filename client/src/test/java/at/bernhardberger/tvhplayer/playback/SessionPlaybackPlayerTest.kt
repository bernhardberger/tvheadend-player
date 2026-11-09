@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi::class,
    at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class,
)

package at.bernhardberger.tvhplayer.playback
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure

import android.app.Application
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.test.utils.FakeClock
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val labels = object : SessionMetadataLabels {
    override fun genre(genre: ContentGenre): String = "Category"
    override fun channelNumber(channel: Channel): String = "Test number"
    override fun error(kind: SessionPlaybackErrorKind): String = when (kind) {
        SessionPlaybackErrorKind.RECORDING -> "Recording can't be read"
        SessionPlaybackErrorKind.CHANNEL_UNAVAILABLE -> "Channel unavailable"
        SessionPlaybackErrorKind.CHANNEL -> "Playback failed"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionPlaybackPlayerTest {
    @Test fun controllerChannelStepsRequireAShowingLivePlayerAndNeverSeekPlayback() = exercise {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val mediaSession = androidx.media3.session.MediaSession.Builder(context, wrapper).build()
        val connecting = androidx.media3.session.MediaController.Builder(context, mediaSession.token).buildAsync()
        val steps = mutableListOf<Int>()
        val stepCommands = listOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        var unregister: () -> Unit = {}
        try {
            await { connecting.isDone }
            val controller = connecting.get()
            live(false)
            settle()
            stepCommands.forEach { assertFalse(controller.isCommandAvailable(it)) }
            unregister = runtime.registerSessionChannelStep { steps += it }
            settle()
            stepCommands.forEach { assertTrue("Live player must offer channel step command $it", controller.isCommandAvailable(it)) }
            controller.seekToNext()
            await { steps.size == 1 }
            controller.seekToPrevious()
            await { steps.size == 2 }
            controller.seekToNextMediaItem()
            await { steps.size == 3 }
            controller.seekToPreviousMediaItem()
            await { steps.size == 4 }
            settle()
            assertEquals(listOf(1, -1, 1, -1), steps)
            assertEquals(0, sessionSeeks)
            assertTrue(connection.speeds.isEmpty())

            // A registration change alone must notify an already connected controller.
            unregister()
            await { stepCommands.none(controller::isCommandAvailable) }
            unregister = runtime.registerSessionChannelStep { steps += it }
            await { stepCommands.all(controller::isCommandAvailable) }
            // Route transitions can install the new owner before disposing the outgoing one.
            val unregisterOutgoing = unregister
            val incomingSteps = mutableListOf<Int>()
            unregister = runtime.registerSessionChannelStep { incomingSteps += it }
            unregisterOutgoing()
            settle()
            stepCommands.forEach { assertTrue(controller.isCommandAvailable(it)) }
            controller.seekToNext()
            await { incomingSteps == listOf(1) }
            assertEquals(listOf(1, -1, 1, -1), steps)
            recording()
            await { !controller.isCurrentMediaItemLive }
            stepCommands.forEach { assertFalse(controller.isCommandAvailable(it)) }
            live(false)
            await { stepCommands.all(controller::isCommandAvailable) }
            assertEquals(listOf(1, -1, 1, -1), steps)
            assertEquals(0, sessionSeeks)
        } finally {
            unregister()
            androidx.media3.session.MediaController.releaseFuture(connecting)
            mediaSession.release()
        }
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun mediaControllerReceivesTheSessionContractAcrossTargetAndFailureChanges() = exercise(realThreads = true) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val now = Clock.System.now()
        val initial = session.observation.value
        val event = EpgEvent.create(EventId(1), ChannelId(1), now - 30.minutes, now + 30.minutes,
            title = "Programme", subtitle = "Episode", summary = "Short description", genre = "Drama",
            episode = EpgEpisode(null, null, 2, 3, 4, 5, 6, 7, "S2 E4"), contentGenre = ContentGenre.fromContentType(0x10))
        val next = EpgEvent.create(EventId(2), ChannelId(1), now + 30.minutes, now + 90.minutes, title = "Next")
        val channels = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1),
            name = "Channel One", services = listOf(ChannelService("", "", 1, null, null, null))))))
        val recordings = DvrRepositoryState.Current(DvrSnapshot.create(listOf(DvrEntry.create(DvrEntryId(1),
            state = DvrEntryState.COMPLETED, title = "Recording", subtitle = "Recorded episode", channelName = "Recorded Channel"))))
        session.publish(SessionObservation.create(sessionState = initial.sessionState, channelState = channels,
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(event, next))), dvrState = recordings))
        val bytes = java.io.ByteArrayOutputStream().also {
            android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val publishedPlayer = SessionPlaybackPlayer(runtime, true, labels, SessionArtworkLoader { _, _ -> bytes })
        val observing = scope.launch { publishedPlayer.observe(session.observation) }
        val mediaSession = androidx.media3.session.MediaSession.Builder(context, publishedPlayer).build()
        val connecting = androidx.media3.session.MediaController.Builder(context, mediaSession.token).buildAsync()
        try {
            await { connecting.isDone }
            val modern = connecting.get()
            live(false)
            await { modern.mediaMetadata.subtitle == "Programme" }
            // Session artwork loading uses production Dispatchers.IO, then the controller's looper.
            driver.await("controller received IO-loaded artwork") { modern.mediaMetadata.artworkData != null }
            assertEquals("Channel One", modern.mediaMetadata.title)
            assertTrue(bytes.contentEquals(modern.mediaMetadata.artworkData))
            assertNull(modern.mediaMetadata.artworkUri)
            assertTrue(modern.isCurrentMediaItemLive)
            assertTrue(modern.isCurrentMediaItemDynamic)
            assertEquals(androidx.media3.common.C.TIME_UNSET, modern.duration)
            val extras = requireNotNull(modern.mediaMetadata.extras)
            // Mapping is covered below; here every published extra must survive the session boundary.
            val publishedExtras = requireNotNull(publishedPlayer.mediaMetadata.extras)
            assertEquals(publishedExtras.keySet(), extras.keySet())
            for (key in publishedExtras.keySet()) {
                assertEquals(key, publishedExtras.get(key), extras.get(key))
            }
            assertEquals("Programme", extras.getString("at.bernhardberger.tvhplayer.session.programmeTitle"))
            assertEquals("Drama", modern.mediaMetadata.genre)
            assertEquals("Drama", extras.getString("at.bernhardberger.tvhplayer.session.genre"))
            assertEquals("Drama", extras.getString("android.media.metadata.GENRE"))
            val forbidden = listOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD)
            forbidden.forEach { assertFalse(modern.isCommandAvailable(it)) }

            session.publish(SessionObservation.create(sessionState = initial.sessionState, channelState = channels,
                epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(EpgEvent.create(EventId(3),
                    ChannelId(1), event.start, event.stop, title = "Changed programme"), next))), dvrState = recordings))
            await { modern.mediaMetadata.subtitle == "Changed programme" }
            reportedDuration = 60_000
            recording()
            await { modern.mediaMetadata.title == "Recording" }
            assertEquals("Recorded episode", modern.mediaMetadata.subtitle)
            assertTrue(modern.duration > 0)

            retireWithState(AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED,
                playerErrorClassification = "private diagnostic must not escape"))
            val message = labels.error(SessionPlaybackErrorKind.RECORDING)
            await { modern.playerError?.message == message }
            // Media3 adds RELEASE to every controller: it disconnects the controller, not playback.
            assertEquals(setOf(Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA, Player.COMMAND_STOP, Player.COMMAND_RELEASE), modern.availableCommands.let { commands ->
                (0 until commands.size()).map(commands::get).toSet()
            })
            modern.stop()
            // Media3 1.11.1 caches errors at MediaSessionImpl.java:1789-1803/:1879 until a playback-state change.
            // Do not assert the controller's cached error here; the platform side is checked on the G10.
            await { runtime.state.value == AppPlaybackState.Idle && publishedPlayer.playerError == null && modern.mediaItemCount == 0 }
        } finally {
            androidx.media3.session.MediaController.releaseFuture(connecting)
            mediaSession.release()
            observing.cancel()
            publishedPlayer.close()
        }
    }

    @Test fun sessionArtworkFollowsOnlyTheCurrentTargetAndNotProgrammeTicks() = exercise(realThreads = true) {
        // Keep the real IO loader: this case checks a cancelled request completing across threads.
        val pending = kotlinx.coroutines.CompletableDeferred<ByteArray?>()
        val completed = kotlinx.coroutines.CompletableDeferred<Unit>()
        val requests = java.util.Collections.synchronizedList(mutableListOf<SessionArtworkRequest>())
        val initial = session.observation.value
        session.publish(SessionObservation.create(sessionState = initial.sessionState,
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(
                Channel.create(ChannelId(1), name = "One"), Channel.create(ChannelId(2), name = "Two")))),
            epgState = initial.epgState, dvrState = initial.dvrState))
        val observations = kotlinx.coroutines.flow.MutableStateFlow(SessionObservation.create(
            channelState = session.observation.value.channelState))
        val artworkPlayer = SessionPlaybackPlayer(runtime, true, labels, SessionArtworkLoader { request, _ ->
            requests += request
            val id = (request.target as AppPlaybackTarget.Live).channelId.value
            if (id == 1L) byteArrayOf(1)
            else if (requests.count { it.target == request.target } == 1) throw java.io.IOException("Artwork unavailable")
            else {
                // The loader resumes on IO; wait for its entire request job, not just its input.
                kotlinx.coroutines.currentCoroutineContext()[Job]!!.invokeOnCompletion { completed.complete(Unit) }
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { pending.await() }
            }
        })
        val delivered = mutableListOf<List<Byte>?>()
        artworkPlayer.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                delivered += mediaMetadata.artworkData?.toList()
            }
        })
        val observing = scope.launch { artworkPlayer.observe(observations) }
        try {
            live(false)
            settle()
            assertTrue(requests.isEmpty())
            assertNull(artworkPlayer.mediaMetadata.artworkData)
            observations.value = session.observation.value
            // SessionArtworkLoader executes on Dispatchers.IO, outside the test scheduler.
            driver.await("initial artwork loaded") { artworkPlayer.mediaMetadata.artworkData != null }
            assertTrue(delivered.contains(listOf(1.toByte())))
            assertTrue(byteArrayOf(1).contentEquals(artworkPlayer.mediaMetadata.artworkData))
            assertEquals(MediaMetadata.PICTURE_TYPE_FRONT_COVER, artworkPlayer.mediaMetadata.artworkDataType)
            assertNull(artworkPlayer.mediaMetadata.artworkUri)
            assertNull(artworkPlayer.currentMediaItem?.mediaMetadata?.artworkData)
            val before = session.observation.value
            val now = Clock.System.now()
            session.publish(SessionObservation.create(sessionState = before.sessionState,
                channelState = before.channelState, dvrState = before.dvrState,
                epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(
                    EpgEvent.create(EventId(3), ChannelId(1), now - 1.hours, now + 1.hours, title = "Next show"))))))
            observations.value = session.observation.value
            advanceTimeBy(1_100)
            settle()
            assertEquals(1, requests.size)
            live(false, channelId = ChannelId(2))
            // The request is recorded by the real IO loader.
            driver.await("second artwork requested") { requests.size == 2 }
            assertNull(artworkPlayer.mediaMetadata.artworkData)
            live(false)
            // Even a cached artwork result is delivered through Dispatchers.IO.
            driver.await("cached artwork restored") { byteArrayOf(1).contentEquals(artworkPlayer.mediaMetadata.artworkData) }
            live(false, channelId = ChannelId(2))
            // The retry also enters the real IO loader.
            driver.await("retry artwork requested") { requests.size == 3 }
            live(false)
            // The cached current target crosses IO while the stale request is still pending.
            driver.await("current artwork restored") { byteArrayOf(1).contentEquals(artworkPlayer.mediaMetadata.artworkData) }
            pending.complete(byteArrayOf(2))
            // Completion belongs to the non-cancellable request on Dispatchers.IO.
            driver.await("late artwork request completed") { completed.isCompleted }
            settle()
            assertTrue(byteArrayOf(1).contentEquals(artworkPlayer.mediaMetadata.artworkData))
            assertEquals(3, requests.size) // Successful identities are cached; failures retry.
        } finally {
            pending.complete(null)
            observing.cancel()
            artworkPlayer.close()
        }
    }

    @Test fun liveMetadataUsesChannelAsTitleAndProgrammeAsSubtitle() {
        val now = Instant.parse("2026-09-07T20:30:00Z")
        val episode = EpgEpisode(null, null, 2, 3, 4, 5, 6, 7, "S2 E4")
        val event = EpgEvent.create(EventId(1), ChannelId(1), now - 30.minutes, now + 30.minutes,
            title = "Programme", subtitle = "Episode", summary = " A\n short  summary ", genre = "Drama",
            description = "Not the summary", episode = episode, contentGenre = ContentGenre.fromContentType(0x10))
        val next = EpgEvent.create(EventId(2), ChannelId(1), now + 30.minutes, now + 90.minutes, title = "Next")
        val snapshot = SessionObservation.create(
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Station",
                number = 12, numberMinor = 3, services = listOf(ChannelService("", "", 1, null, null, null)))))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(event, next))))
        val metadata = sessionPlaybackMetadata(AppPlaybackTarget.Live(ChannelId(1)), snapshot, now,
            AppTimeshiftState(), liveStart = true, programmeWindowEnabled = true, labels = labels)
        assertEquals("Station", metadata.title)
        assertEquals("Station", metadata.displayTitle)
        assertEquals("Programme", metadata.artist)
        assertEquals("Station", metadata.station)
        assertEquals("Programme", metadata.subtitle)
        assertEquals("Drama", metadata.genre)
        assertEquals(MediaMetadata.MEDIA_TYPE_TV_CHANNEL, metadata.mediaType)
        val expected = mapOf(
            "schemaVersion" to 1L, "contentKind" to "live", "channelMediaKind" to "tv",
            "channelName" to "Station", "channelNumber" to "Test number", "programmeTitle" to "Programme",
            "episodeSubtitle" to "Episode", "episodeOnscreen" to "S2 E4", "seasonNumber" to 2L,
            "seasonCount" to 3L, "episodeNumber" to 4L, "episodeCount" to 5L, "partNumber" to 6L, "partCount" to 7L,
            "programmeStartEpochMs" to event.start.toEpochMilliseconds(), "programmeStopEpochMs" to event.stop.toEpochMilliseconds(),
            "programmeDurationMs" to 60.minutes.inWholeMilliseconds, "genre" to "Drama", "contentGenreCode" to 16L,
            "shortDescription" to "A short summary", "nextProgrammeTitle" to "Next",
            "nextProgrammeStartEpochMs" to next.start.toEpochMilliseconds(), "nextProgrammeStopEpochMs" to next.stop.toEpochMilliseconds(),
        ).mapKeys { "at.bernhardberger.tvhplayer.session.${it.key}" } + ("android.media.metadata.GENRE" to "Drama")
        val extras = requireNotNull(metadata.extras)
        assertEquals(expected, extras.keySet().associateWith { extras.get(it) })
        assertNull(metadata.durationMs)

        for ((content, kind) in listOf(2L to "radio", 0L to "other")) {
            val channel = Channel.create(ChannelId(1), name = "Station",
                services = listOf(ChannelService("", "", content, null, null, null)))
            val observation = SessionObservation.create(
                channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
                dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(
                    DvrEntry.create(DvrEntryId(1), channelId = ChannelId(1))))))
            for ((target, type) in listOf(AppPlaybackTarget.Live(ChannelId(1)) to
                if (content == 2L) MediaMetadata.MEDIA_TYPE_RADIO_STATION else null,
                AppPlaybackTarget.Recording(DvrEntryId(1)) to null)) {
                val mapped = sessionPlaybackMetadata(target, observation, now, AppTimeshiftState(), true, true, labels)
                assertEquals(type, mapped.mediaType)
                assertEquals(kind, mapped.extras?.getString("at.bernhardberger.tvhplayer.session.channelMediaKind"))
            }
        }
        assertNull(metadata.description)
        assertNull(metadata.artworkUri)

        // The committed position is in an EPG gap: do not publish either broadcast Now or Next.
        val fixture = TimeshiftTestFixture(120.minutes).apply { updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = now) }
        val slot = sessionPlaybackMetadata(AppPlaybackTarget.Live(ChannelId(1)), snapshot, now,
            fixture.state.value.toAppPresentation(fixture.playbackPosition(55.minutes)).copy(paused = true),
            liveStart = false, programmeWindowEnabled = true, labels = labels)
        assertNull(slot.subtitle)
        assertNull(slot.artist)
        assertFalse(requireNotNull(slot.extras).containsKey("at.bernhardberger.tvhplayer.session.nextProgrammeTitle"))
    }

    @Test fun recordingMetadataMapsScheduledProgrammeAndSparseValuesWithoutSentinels() {
        val now = Instant.parse("2026-09-07T20:30:00Z")
        val entry = DvrEntry.create(DvrEntryId(1), channelId = ChannelId(1), channelName = "Fallback",
            title = "Recording", subtitle = "Episode", summary = " ", description = " Full\n description ",
            start = now, stop = now + 60.minutes, episode = EpgEpisode(null, null, 1, 2, 3, 4, 5, 6, "Episode 3"),
            contentGenre = ContentGenre.fromContentType(0x40))
        val snapshot = SessionObservation.create(
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Station",
                number = 8, services = listOf(ChannelService("", "", 1, null, null, null)))))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))))
        val metadata = sessionPlaybackMetadata(AppPlaybackTarget.Recording(DvrEntryId(1)), snapshot, now,
            AppTimeshiftState(), false, true, labels)
        assertEquals("Recording", metadata.title)
        assertEquals("Recording", metadata.displayTitle)
        assertEquals("Episode", metadata.subtitle)
        assertEquals("Station", metadata.artist)
        assertEquals("Station", metadata.station)
        assertEquals("Category", metadata.genre)
        assertEquals(MediaMetadata.MEDIA_TYPE_TV_SHOW, metadata.mediaType)
        val expected = mapOf("schemaVersion" to 1L, "contentKind" to "recording", "channelMediaKind" to "tv",
            "channelName" to "Station", "channelNumber" to "Test number", "programmeTitle" to "Recording", "episodeSubtitle" to "Episode",
            "episodeOnscreen" to "Episode 3", "seasonNumber" to 1L, "seasonCount" to 2L, "episodeNumber" to 3L,
            "episodeCount" to 4L, "partNumber" to 5L, "partCount" to 6L, "programmeStartEpochMs" to now.toEpochMilliseconds(),
            "programmeStopEpochMs" to (now + 60.minutes).toEpochMilliseconds(), "programmeDurationMs" to 60.minutes.inWholeMilliseconds,
            "genre" to "Category", "contentGenreCode" to 64L, "shortDescription" to "Full description")
            .mapKeys { "at.bernhardberger.tvhplayer.session.${it.key}" } + ("android.media.metadata.GENRE" to "Category")
        val extras = requireNotNull(metadata.extras)
        assertEquals(expected, extras.keySet().associateWith { extras.get(it) })
        assertNull(metadata.description)
        assertNull(metadata.durationMs)

        val sparse = sessionPlaybackMetadata(AppPlaybackTarget.Recording(DvrEntryId(2)), snapshot, now,
            AppTimeshiftState(), false, true, labels)
        assertNull(sparse.title)
        assertNull(sparse.subtitle)
        assertNull(sparse.mediaType)
        val sparseExtras = requireNotNull(sparse.extras)
        assertEquals(mapOf("at.bernhardberger.tvhplayer.session.schemaVersion" to 1L,
            "at.bernhardberger.tvhplayer.session.contentKind" to "recording",
            "at.bernhardberger.tvhplayer.session.channelMediaKind" to "unknown"), sparseExtras.keySet().associateWith { sparseExtras.get(it) })
        // The SDK rejects reversed bounds at construction; equal and missing bounds are admitted.
        for ((start, stop) in listOf(now to null, null to now, now to now)) {
            val observation = SessionObservation.create(dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(
                DvrEntry.create(DvrEntryId(1), start = start, stop = stop)))))
            val metadata = sessionPlaybackMetadata(AppPlaybackTarget.Recording(DvrEntryId(1)), observation, now,
                AppTimeshiftState(), false, true, labels)
            for (key in listOf("programmeStartEpochMs", "programmeStopEpochMs", "programmeDurationMs")) {
                assertFalse(key, requireNotNull(metadata.extras).containsKey("at.bernhardberger.tvhplayer.session.$key"))
            }
        }
    }

    @Test fun displayAndDescriptionLimitsCountCodePointsWithoutSplittingPairs() {
        val now = Instant.parse("2026-09-07T20:30:00Z")
        for (length in listOf(255, 256, 257)) {
            val text = "x".repeat(length - 1) + "\uD83D\uDCFA" + "z"
            val event = EpgEvent.create(EventId(1), ChannelId(1), now - 30.minutes, now + 30.minutes,
                title = text, summary = "x".repeat(1_023) + "\uD83D\uDCFA" + "z",
                episode = EpgEpisode(null, null, null, null, null, null, null, null, text))
            val snapshot = SessionObservation.create(epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(event))))
            val metadata = sessionPlaybackMetadata(AppPlaybackTarget.Live(ChannelId(1)), snapshot, now,
                AppTimeshiftState(), true, true, labels)
            val value = requireNotNull(metadata.subtitle).toString()
            assertEquals(text.substring(0, text.offsetByCodePoints(0, 256)), value)
            assertEquals(value, requireNotNull(metadata.extras).getString("at.bernhardberger.tvhplayer.session.episodeOnscreen"))
            val summary = requireNotNull(metadata.extras).getString("at.bernhardberger.tvhplayer.session.shortDescription")!!
            assertEquals(1_024, summary.codePointCount(0, summary.length))
            assertTrue(summary.endsWith("\uD83D\uDCFA"))
        }
        val event = EpgEvent.create(EventId(1), ChannelId(1), now, now + 1.minutes,
            title = "x".repeat(255) + " y", summary = "x".repeat(1_023) + " y")
        val snapshot = SessionObservation.create(epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(event))))
        val metadata = sessionPlaybackMetadata(AppPlaybackTarget.Live(ChannelId(1)), snapshot, now,
            AppTimeshiftState(), true, true, labels)
        assertEquals("x".repeat(255), metadata.subtitle)
        assertEquals("x".repeat(1_023), requireNotNull(metadata.extras).getString("at.bernhardberger.tvhplayer.session.shortDescription"))
    }

    @Test fun terminalFailureRetainsTheRetiredItemAndSanitizesTheError() {
        val cases = listOf(
            Triple(AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED,
                playerErrorClassification = "private diagnostic"), "Playback failed", androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
            Triple(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER, targetResult = PlaybackTargetResult.TARGET_UNAVAILABLE),
                "Channel unavailable", androidx.media3.common.PlaybackException.ERROR_CODE_REMOTE_ERROR),
            Triple(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER, subscriptionIssue = SubscriptionIssue.NO_INPUT),
                "Channel unavailable", androidx.media3.common.PlaybackException.ERROR_CODE_REMOTE_ERROR),
            Triple(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER, subscriptionIssue = SubscriptionIssue.NO_INPUT, deviceFault = true),
                "Channel unavailable", androidx.media3.common.PlaybackException.ERROR_CODE_REMOTE_ERROR),
            Triple(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER, deviceFault = true),
                "Playback failed", androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED),
            Triple(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER),
                "Playback failed", androidx.media3.common.PlaybackException.ERROR_CODE_REMOTE_ERROR),
        )
        exercise {
            val stops = collectSessionStops()
            retireWithState(cases.first().first)
            await { wrapper.playerError != null }
            assertNull(wrapper.currentMediaItem)
            val recreated = SessionPlaybackPlayer(runtime, true, labels)
            val observing = scope.launch { recreated.observe(session.observation) }
            try {
                await { recreated.playerError != null }
                assertNull(recreated.currentMediaItem)
            } finally {
                observing.cancel()
                recreated.close()
            }
            wrapper.stop()
            await { wrapper.playerError == null }
            settle()
            assertEquals(AppPlaybackState.Idle, runtime.state.value)
            assertEquals(1, stops.size)
            for ((failure, text, code) in cases) {
                for (recordingTarget in listOf(true, false)) {
                    if (recordingTarget) recording() else live(false)
                    val title = if (recordingTarget) "Recording" else "Channel One"
                    await { wrapper.mediaMetadata.title == title }
                    retireWithState(failure)
                    await { wrapper.playerError != null }
                    assertEquals(title, wrapper.mediaMetadata.title)
                    assertEquals(if (recordingTarget) labels.error(SessionPlaybackErrorKind.RECORDING) else text,
                        wrapper.playerError?.message)
                    assertEquals(code, wrapper.playerError?.errorCode)
                    assertNull(wrapper.playerError?.cause)
                    assertTrue(requireNotNull(wrapper.playerError).extras.isEmpty)
                }
            }
            recording()
            await { wrapper.mediaMetadata.title == "Recording" }
            val failure = cases.first().first.copy(target = AppPlaybackTarget.Recording(DvrEntryId(1)))
            var errorEvents = 0
            val listener = object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { errorEvents++ }
            }
            wrapper.addListener(listener)
            retireWithState(failure)
            await { wrapper.playerError != null }
            assertEquals("Recording", wrapper.mediaMetadata.title)
            assertTrue(wrapper.isCommandAvailable(Player.COMMAND_STOP))
            assertFalse(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
            val error = wrapper.playerError
            repeat(2) { index ->
                // Raw player events force forwarding-state invalidation while failure is unchanged.
                player.volume = if (index == 0) 0.5f else 1f
                settle()
            }
            assertSame(error, wrapper.playerError)
            assertEquals(1, errorEvents)
            wrapper.removeListener(listener)
            for (state in listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 1_000), AppPlaybackState.Buffering)) {
                publishState(state)
                await { wrapper.playerError == null }
                retireWithState(failure)
                await { wrapper.playerError != null }
            }
            recording()
            await { wrapper.playerError == null && wrapper.currentMediaItem != null }
            retireWithState(failure)
            await { wrapper.playerError != null }
            val stopsBefore = stops.size
            wrapper.stop()
            await { wrapper.currentMediaItem == null && wrapper.playerError == null }
            settle()
            assertEquals(AppPlaybackState.Idle, runtime.state.value)
            assertEquals(stopsBefore + 1, stops.size)
            val afterStop = SessionPlaybackPlayer(runtime, true, labels)
            val afterStopObservation = scope.launch { afterStop.observe(session.observation) }
            try {
                settle()
                assertNull(afterStop.playerError)
                assertNull(afterStop.currentMediaItem)
            } finally {
                afterStopObservation.cancel()
                afterStop.close()
            }
            recording()
            await { wrapper.mediaMetadata.title == "Recording" }
            retireWithState(AppPlaybackState.Starting)
            settle()
            retireWithState(cases.first().first)
            await { wrapper.playerError != null }
            assertNull(wrapper.currentMediaItem)
        }
    }

    @Test fun pausedLiveMetadataUsesTheProgrammeAtPlaybackInsteadOfTheBroadcast() {
        val now = Instant.parse("2026-09-07T20:30:00Z")
        val earlier = EpgEvent.create(id = EventId(1), channelId = ChannelId(1),
            start = now - 90.minutes, stop = now - 30.minutes, title = "Earlier programme")
        val broadcast = EpgEvent.create(id = EventId(2), channelId = ChannelId(1),
            start = now - 30.minutes, stop = now + 30.minutes, title = "Broadcast programme")
        val observation = SessionObservation.create(
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(broadcast), historicalEvents = listOf(earlier))))
        val fixture = TimeshiftTestFixture(120.minutes).apply {
            updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = now)
        }
        val state = fixture.state.value.toAppPresentation(fixture.playbackPosition(55.minutes)).copy(paused = true)
        assertEquals("Earlier programme", sessionPlaybackMetadata(
            AppPlaybackTarget.Live(ChannelId(1)), observation, now, state, liveStart = false, programmeWindowEnabled = true, labels = labels).subtitle)
        assertNull(sessionPlaybackMetadata(AppPlaybackTarget.Live(ChannelId(1)), observation, now,
            state, liveStart = false, programmeWindowEnabled = false, labels = labels).subtitle)
    }

    @Test fun commandsAreAnExactAllowlistForEachTarget() {
        val read = setOf(Player.COMMAND_GET_METADATA, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_STOP)
        fun commands(target: AppPlaybackTarget?, timeshift: Boolean = false, seekable: Boolean = true): Set<Int> =
            sessionPlaybackCommands(target, timeshift, seekable).let { value ->
                (0 until value.size()).map { value[it] }.toSet()
            }
        val live = AppPlaybackTarget.Live(ChannelId(1))
        val recording = AppPlaybackTarget.Recording(DvrEntryId(1))
        assertEquals(emptySet<Int>(), commands(null))
        assertEquals(read, commands(live))
        assertEquals(read + Player.COMMAND_PLAY_PAUSE, commands(live, timeshift = true))
        assertEquals(read + Player.COMMAND_PLAY_PAUSE, commands(recording, seekable = false))
        assertEquals(read + setOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM), commands(recording))
    }

    @Test fun liveWithoutTimeshiftDoesNotPauseOrSendServerCommands() = exercise {
        live(false)
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        wrapper.pause()
        wrapper.play()
        wrapper.seekTo(10_000)
        settle()
        assertTrue(player.playWhenReady)
        assertTrue(connection.speeds.isEmpty())
        assertEquals(1, focusRequests)
    }

    @Test fun timeshiftPauseAndPlayReachServerExactlyOnceAndSeekIsUnavailable() = exercise {
        live(true)
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        wrapper.pause()
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        wrapper.play()
        await { connection.speeds == listOf(0, 100) && player.playWhenReady }
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertEquals(2, focusRequests)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun sessionPlayRetiresAPauseAwaitingTheFirstPictureWithoutServerCommands() = exercise {
        live(true, firstPicture = false)
        wrapper.pause()
        await { runtime.livePause.value.pending && !player.playWhenReady }
        val requests = focusRequests

        wrapper.play()
        await { !runtime.livePause.value.pending && player.playWhenReady }
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)
        assertEquals(requests + 1, focusRequests)
    }

    @Test fun startingLiveOffersPauseAndHoldsTheServerOnceAfterGrantAndFirstPicture() = exercise {
        startingLive()
        await { wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        wrapper.pause()
        await { runtime.livePause.value.pending && !player.playWhenReady }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)

        grant()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        playerReady()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
    }

    @Test fun sessionPlayBeforeTheGrantClearsThePendingPauseWithoutAServerResume() = exercise {
        startingLive()
        await { wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        wrapper.pause()
        await { runtime.livePause.value.pending && !player.playWhenReady }

        wrapper.play()
        await { !runtime.livePause.value.pending && player.playWhenReady }
        grant()
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
    }

    @Test fun liveWithoutAGrantAtTheFirstPictureRejectsSessionPause() = exercise {
        settings.setTimeshiftEnabled(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val install = scope.async { runtime.playLive(liveSelection()) }
        await { connection.subscribeCount == 1 }
        connection.awaitCollectionRegistered()
        connection.emit(started)
        await { install.isCompleted }
        playerReady()
        await { runtime.livePause.value.availability == LivePauseAvailability.UNAVAILABLE }
        // The session withdraws the Pause it offered while the start was undecided.
        await { wrapper.isCommandAvailable(Player.COMMAND_STOP) && !wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }

        runtime.setSessionPlayWhenReady(false) { true }.join()
        settle()
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)
        assertEquals(emptyList<Int>(), connection.speeds)
    }

    @Test fun liveWithTimeshiftOffRejectsSessionPause() = exercise {
        live(false)
        assertEquals(LivePauseAvailability.OFF, runtime.livePause.value.availability)
        runtime.setSessionPlayWhenReady(false) { true }.join()
        settle()
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)
        assertEquals(emptyList<Int>(), connection.speeds)
    }

    @Test fun sessionPauseDuringAPendingChannelChangeIsHeldForTheNewTarget() = exercise {
        live(true)
        val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        wrapper.pause()
        await { runtime.livePause.value.pending }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(runtime.livePause.value.selectionPending)

        startingLive(intent)
        assertFalse(player.playWhenReady)
        assertTrue(runtime.livePause.value.pending)
        grant(subscription = 2)
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        playerReady()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
    }

    @OptIn(ExperimentalTvheadendApi::class)
    @Test fun sessionKeepsReportingPauseWhileAPausedSeekRefreshesItsPicture() = exercise {
        settings.setTunnelingEnabled(true)
        await { player.trackSelectionParameters.tunnelingRequested }
        live(true)
        wrapper.pause()
        await { connection.speeds == listOf(0) && !player.playWhenReady && !wrapper.playWhenReady }
        val reported = mutableListOf<Boolean>()
        wrapper.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { reported += playWhenReady }
        })
        runtime.nudgePausedSeek(confirmation = PausedSeekConfirmation.KEY_FRAME_QUEUED)
        await { player.playWhenReady }
        settle()
        assertFalse(wrapper.playWhenReady)
        // The fake stream renders no frame: the refresh ends after its budget on the runtime's clock.
        advanceTimeBy(PAUSED_SEEK_NUDGE_MILLIS)
        await { !runtime.livePause.value.pausedSeekRefresh }
        settle()
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
        assertEquals(emptyList<Boolean>(), reported)
        assertEquals(listOf(0), connection.speeds)
    }

    @Test fun sessionReportsTheViewersIntentWhileAChannelChangeIsPending() = exercise {
        live(true)
        val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        await { runtime.livePause.value.selectionPending }
        settle()
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertTrue(wrapper.playWhenReady)

        wrapper.pause()
        await { !wrapper.playWhenReady }
        // The channel still installed plays on; the session reports the Pause held for the change.
        assertTrue(player.playWhenReady)
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        wrapper.play()
        await { wrapper.playWhenReady }
        assertFalse(runtime.livePause.value.pending)
        wrapper.pause()
        await { !wrapper.playWhenReady }

        // The new target installs paused and still reports (and offers) Pause until it holds.
        startingLive(intent)
        await { !runtime.livePause.value.selectionPending }
        settle()
        assertFalse(wrapper.playWhenReady)
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        grant(subscription = 2)
        playerReady()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(wrapper.playWhenReady)
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
    }

    @Test fun sessionOffersPauseForAPendingChannelChangeAndReportsWhatPlaysOnceItIsAbandoned() = exercise {
        settings.setTimeshiftEnabled(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val install = scope.async { runtime.playLive(liveSelection()) }
        await { connection.subscribeCount == 1 }
        connection.awaitCollectionRegistered()
        connection.emit(started)
        await { install.isCompleted }
        playerReady()
        await { wrapper.isCommandAvailable(Player.COMMAND_STOP) && !wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }

        // A channel change is pending: the next channel may pause, so the session offers it.
        val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        await { wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        assertTrue(wrapper.playWhenReady)
        wrapper.pause()
        await { !wrapper.playWhenReady }
        assertTrue(player.playWhenReady)

        // Its start was cancelled: the held Pause goes and the session reports the channel that plays.
        runtime.abandonLiveSelection(intent)
        await { !wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        assertTrue(wrapper.playWhenReady)
        assertTrue(player.playWhenReady)
        assertEquals(emptyList<Int>(), connection.speeds)
    }

    @Test fun startCancelledBehindTheSerializerLetsASessionPauseActOnTheChannelThatPlays() = exercise {
        live(true)
        val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        whileCommandsBlocked {
            val start = scope.async { runtime.playLive(liveSelection(), intent) }
            settle()
            start.cancel()
            await { start.isCompleted }
        }
        assertFalse(runtime.livePause.value.selectionPending)
        wrapper.pause()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun sessionPauseQueuedBeforeAChannelChangePausesNeitherChannel() = exercise {
        live(true)
        var intent = 0L
        whileCommandsBlocked {
            wrapper.pause()
            settle()
            intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)

        startingLive(intent)
        grant(subscription = 2)
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
    }

    @Test fun keyPauseQueuedBeforeAChannelChangePausesNeitherChannel() = exercise {
        live(true)
        lateinit var pause: Deferred<TimeshiftCommandResult?>
        var intent = 0L
        whileCommandsBlocked {
            pause = scope.async { runtime.pauseTimeshiftPlayback() }
            settle()
            intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        }
        await { pause.isCompleted }
        assertNull(pause.await())
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)

        startingLive(intent)
        grant(subscription = 2)
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
    }

    @Test fun keyPlayQueuedBeforeAChannelChangeResumesNothing() = exercise {
        live(true)
        wrapper.pause()
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        val requests = focusRequests
        lateinit var resume: Deferred<TimeshiftCommandResult>
        whileCommandsBlocked {
            resume = scope.async { runtime.resumeTimeshift() }
            settle()
            runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        }
        await { resume.isCompleted }
        assertEquals(TimeshiftCommandResult.ACCEPTED, resume.await())
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertEquals(requests, focusRequests)
    }

    @Test fun sessionPauseAfterARetryBeganHoldsTheRetriedChannel() = exercise {
        pauseDuringARetryHoldsTheRetriedChannel(viewerRetry = true) { wrapper.pause() }
    }

    @Test fun sessionPauseDuringTheAutomaticReconnectHoldsTheReconnectedChannel() = exercise {
        pauseDuringARetryHoldsTheRetriedChannel(viewerRetry = false) { wrapper.pause() }
    }

    @Test fun keyPauseAfterARetryBeganHoldsTheRetriedChannel() = exercise {
        pauseDuringARetryHoldsTheRetriedChannel(viewerRetry = true) {
            scope.launch { runtime.pauseTimeshiftPlayback() }
        }
    }

    @Test fun sessionPauseAfterARetryFromAFailedStartHoldsTheRetriedChannel() = exercise {
        settings.setTimeshiftEnabled(true)
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        assertFalse(runtime.playLive(liveSelection())?.isStarted == true)
        assertNull(runtime.activeTarget.value)
        session.scriptLivePlaybackSuccess(manager)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        lateinit var retry: Deferred<PlaybackTargetResult?>
        lateinit var pause: Job
        whileCommandsBlocked {
            retry = scope.async { runtime.retryLive(viewerRetry = true) }
            settle()
            pause = runtime.setSessionPlayWhenReady(false) { true }
        }
        await { retry.isCompleted && pause.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        assertFalse(player.playWhenReady)
        grant(subscription = 1)
        playerReady()
        await { connection.speeds == listOf(0) }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
    }

    @Test fun channelChangeAfterASessionPauseDuringARetryWinsOverThePause() = exercise {
        live(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        lateinit var retry: Deferred<PlaybackTargetResult?>
        var intent = 0L
        whileCommandsBlocked {
            retry = scope.async { runtime.retryLive(viewerRetry = true) }
            settle()
            wrapper.pause()
            settle()
            intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        }
        await { retry.isCompleted }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)

        startingLive(intent)
        grant(subscription = 3)
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertTrue(wrapper.playWhenReady)
    }

    @Test fun rejectedTimeshiftPauseRollsBackLocalIntent() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        var localPauseBeforeServerCommand = false
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) localPauseBeforeServerCommand = connection.speeds.isEmpty()
            }
        })
        wrapper.pause()
        await { connection.speeds == listOf(0) }
        settle()
        assertTrue(player.playWhenReady)
        assertTrue(wrapper.playWhenReady)
        assertTrue(localPauseBeforeServerCommand)
        assertEquals(1, focusRequests)
    }

    @Test fun deniedFocusDoesNotResumeServerWhenRejectedHoldKeepsMutedVideoRunning() = exercise {
        live(true)
        focusGranted = false
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        wrapper.play()
        await { connection.speeds == listOf(0) }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
    }

    @Test fun resumingFocusInterruptionSendsOnlyOneServerResume() = exercise {
        live(true)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        wrapper.play()
        await { connection.speeds == listOf(0, 100) && player.playWhenReady }
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertEquals(2, focusRequests)
    }

    @Test fun rejectedResumeAfterFocusInterruptionPausesLocallyAndSendsOneResume() = exercise {
        live(true)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        wrapper.play()
        await { connection.speeds == listOf(0, 100) && focusRequests == 2 }
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
    }

    @Test fun pauseDuringInterruptionDoesNotSendASecondServerHoldOrAutoResume() = exercise {
        live(true)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        wrapper.pause()
        settle()
        focusListener?.invoke(AudioInterruption.GAIN)
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertEquals(1, focusRequests)
        assertFalse(player.playWhenReady)
    }

    @Test fun pauseWhileInterruptionMutedRestoresSoundWithoutServerCommands() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes }
        connection.scriptSpeed(SubscriptionOperationResult.Ok(Unit))
        wrapper.pause()
        await { focusRequests == 2 }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
    }

    @Test fun pauseWhileInterruptionMutedAndFocusDeniedPreservesMuteWithoutServerPause() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes }
        focusGranted = false
        wrapper.pause()
        await { focusRequests == 2 }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(player.playWhenReady)
        assertTrue(androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
    }

    @Test fun closeDuringLocalPauseLetsServerRejectionRollbackComplete() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        val closed = CompletableDeferred<Unit>()
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && !closed.isCompleted) {
                    observation.cancel()
                    wrapper.close()
                    closed.complete(Unit)
                }
            }
        })
        wrapper.pause()
        await { closed.isCompleted && connection.speeds == listOf(0) && player.playWhenReady }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(player.playWhenReady)
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
    }

    @Test fun recordingPauseAndPlayUseRuntimeFocusPath() = exercise {
        recording()
        wrapper.pause()
        await { !player.playWhenReady }
        wrapper.play()
        await { player.playWhenReady && focusRequests == 2 }
        settle()
        assertEquals(2, focusRequests)
        assertTrue(connection.speeds.isEmpty())
    }

    @Test fun queuedPlayAfterCloseDoesNotRequestFocusOrResumeWhileRuntimeIsForeground() = exercise {
        live(true)
        wrapper.pause()
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        whileCommandsBlocked {
            wrapper.play()
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertEquals(1, focusRequests)
        assertFalse(player.playWhenReady)
        assertEquals(listOf(0), connection.speeds)
    }

    @Test fun queuedPauseAfterCloseDoesNotPauseWhileRuntimeIsForeground() = exercise {
        live(true)
        whileCommandsBlocked {
            wrapper.pause()
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertTrue(player.playWhenReady)
        assertTrue(connection.speeds.isEmpty())
        assertEquals(1, focusRequests)
    }

    @Test fun queuedSeekAfterCloseDoesNotMoveRecordingWhileRuntimeIsForeground() = exercise {
        recording()
        player.setMediaSource(SilenceMediaSource.Factory().setDurationUs(60_000_000).createMediaSource())
        player.prepare()
        player.pause()
        await { wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }
        val position = player.currentPosition
        whileCommandsBlocked {
            wrapper.seekTo(12_000)
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertEquals(position, player.currentPosition)
        assertEquals(1, focusRequests)
    }

    @Test fun recordingSeekUsesSerializedRuntimeCommandAndRequiresSeekableTimeline() = exercise {
        recording()
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        // Replace only the fixture's transport with a deterministic, seekable offline source.
        player.setMediaSource(SilenceMediaSource.Factory().setDurationUs(60_000_000).createMediaSource())
        player.prepare()
        await { wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }
        wrapper.seekTo(12_000)
        await { player.currentPosition >= 12_000 }
        assertTrue(player.currentPosition < 13_000)
        assertTrue(connection.speeds.isEmpty())
    }

    /*
     * A target of the other kind is stopped before the replacement's source is set, so the
     * replacement never plays through the old target's decoders. The same kind is replaced in place.
     */

    @Test fun recordingOpenedOverLiveStopsTheLiveTargetBeforeItsSource() = exercise {
        live(false)
        val stops = collectSessionStops()
        val states = collectStates()
        playerLog.clear()
        recording()
        assertEquals("stop", playerLog.first())
        assertEquals("items=1", playerLog.last())
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
        // Not the viewer's stop: the screen stays open and Idle never shows between the targets.
        assertEquals(emptyList<Unit>(), stops)
        assertFalse(states.toString(), AppPlaybackState.Idle in states)
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
    }

    @Test fun channelStartedOverARecordingStopsTheRecordingBeforeItsSource() = exercise {
        recording()
        val stops = collectSessionStops()
        val states = collectStates()
        playerLog.clear()
        live(false)
        assertEquals("stop", playerLog.first())
        assertEquals("items=1", playerLog.last())
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(null, runtime.recordingSelection.value)
        assertEquals(emptyList<Unit>(), stops)
        assertFalse(states.toString(), AppPlaybackState.Idle in states)
    }

    @Test fun targetsOfTheSameKindReplaceEachOtherWithoutAStop() = exercise {
        live(false)
        playerLog.clear()
        answerNextSubscribe(2)
        assertTrue(runtime.playLive(liveSelection())?.isStarted == true)
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(playerLog.toString(), "stop" in playerLog)
        recording()
        playerLog.clear()
        recording()
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
        assertFalse(playerLog.toString(), "stop" in playerLog)
    }

    @Test fun aChannelThatFailsAfterTheRecordingStoppedLeavesNoTargetBehind() = exercise {
        recording()
        val stops = collectSessionStops()
        playerLog.clear()
        // The SDK binds the channel only after the recording stopped.
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val result = runtime.playLive(liveSelection())
        settle()
        assertFalse(result.toString(), result?.isStarted == true)
        assertEquals("stop", playerLog.first())
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(null, runtime.recordingSelection.value)
        assertTrue(runtime.state.value.toString(), runtime.state.value is AppPlaybackState.Failed)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), (runtime.state.value as AppPlaybackState.Failed).target)
        assertFalse(runtime.videoPresentation.value.visible)
        assertEquals(emptyList<Unit>(), stops)
        // Real install failure retires the target epoch; Stop must still clear the runtime failure.
        await { wrapper.playerError != null }
        assertNull(wrapper.currentMediaItem)
        assertEquals(labels.error(SessionPlaybackErrorKind.CHANNEL_UNAVAILABLE), wrapper.playerError?.message)
        wrapper.stop()
        await { runtime.state.value == AppPlaybackState.Idle && wrapper.playerError == null }
        assertNull(wrapper.currentMediaItem)
        assertEquals(listOf(Unit), stops)
    }

    @Test fun aRecordingThatCannotBindLeavesTheChannelPlaying() = exercise {
        live(false)
        val picture = runtime.videoPresentation.value
        playerLog.clear()
        session.scriptRecordingPlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val result = runtime.playRecording(
            requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
            RecordingPlaybackStart.START_OVER,
        )
        settle()
        assertFalse(result.toString(), result?.isStarted == true)
        assertFalse(playerLog.toString(), "stop" in playerLog)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(picture, runtime.videoPresentation.value)
        assertEquals(0, connection.unsubscribeCount)
    }

    /*
     * A start cancelled after it stopped the other kind settles as that stop would have: no
     * target, Idle, no source left in the player, and not the viewer's stop.
     */

    @Test fun aRecordingCancelledWhileTheChannelStopsLeavesNothingBehind() = exercise {
        live(false)
        val stops = collectSessionStops()
        val states = collectStates()
        val start = recordingStart()
        onPlayerLog = { if (it == "stop") start.cancel() }
        start.start()
        await { start.isCompleted }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertCancelledStartSettled(states, stops)
    }

    @Test fun aRecordingCancelledWhileItsSourceIsSetLeavesNothingBehind() = exercise {
        live(false)
        val stops = collectSessionStops()
        val states = collectStates()
        val start = recordingStart()
        onPlayerLog = { if (it == "items=1") start.cancel() }
        start.start()
        await { start.isCompleted }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertCancelledStartSettled(states, stops)
    }

    @Test fun aRecordingCancelledWithNothingPlayingLeavesNothingBehind() = exercise {
        val states = collectStates()
        val start = recordingStart()
        onPlayerLog = { if (it == "items=1") start.cancel() }
        start.start()
        await { start.isCompleted }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        assertFalse(states.toString(), states.any { it is AppPlaybackState.Failed })
        assertEquals(playerLog.toString(), 0, player.mediaItemCount)
    }

    @Test fun aChannelCancelledWhileItsSourceIsSetOverARecordingLeavesNothingBehind() = exercise {
        recording()
        val stops = collectSessionStops()
        val states = collectStates()
        answerNextSubscribe(1)
        val start = scope.async(start = CoroutineStart.LAZY) { runtime.playLive(liveSelection()) }
        onPlayerLog = { if (it == "items=1") start.cancel() }
        start.start()
        await { start.isCompleted }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertCancelledStartSettled(states, stops)
    }

    @Test fun aNewerStartCancelledBeforeItsTurnSettlesTheRecordingCancelledForIt() = exercise {
        live(false)
        val stops = collectSessionStops()
        val states = collectStates()
        val start = recordingStart()
        var newer: Deferred<PlaybackTargetResult?>? = null
        onPlayerLog = { entry ->
            if (entry == "items=1" && newer == null) {
                // A newer channel queues behind this start; both are cancelled before it runs.
                val intent = runtime.notePlaybackIntent()
                newer = scope.async(start = CoroutineStart.UNDISPATCHED) { runtime.playLive(liveSelection(), intent) }
                newer?.cancel()
                start.cancel()
            }
        }
        start.start()
        await { start.isCompleted && newer?.isCompleted == true }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertTrue(requireNotNull(newer).isCancelled)
        assertCancelledStartSettled(states, stops)
    }

    @Test fun aRecordingStartCancelledForANewerChannelShowsNoFailure() = exercise {
        live(false)
        playerLog.clear()
        val states = collectStates()
        val start = recordingStart()
        val newer = java.util.concurrent.atomic.AtomicReference<Deferred<PlaybackTargetResult?>>()
        onPlayerLog = { entry ->
            if (entry == "items=1" && newer.get() == null) {
                // The viewer chose a channel meanwhile; its start queues behind this one.
                val intent = runtime.notePlaybackIntent()
                answerNextSubscribe(2)
                newer.set(scope.async { runtime.playLive(liveSelection(), intent) })
                start.cancel()
            }
        }
        start.start()
        await { start.isCompleted && newer.get()?.isCompleted == true }
        onPlayerLog = {}
        settle()
        assertTrue(start.isCancelled)
        assertTrue(newer.get().await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(states.toString(), states.any { it is AppPlaybackState.Failed || it == AppPlaybackState.Idle })
        assertEquals(1, player.mediaItemCount)
        // The recording's source is stopped before the channel's, so the channel gets fresh decoders.
        val recordingSource = playerLog.indexOf("items=1")
        val channelSource = playerLog.lastIndexOf("items=1")
        assertTrue(playerLog.toString(), recordingSource < channelSource)
        assertTrue(playerLog.toString(), "stop" in playerLog.subList(recordingSource, channelSource))
        assertTrue(player.playWhenReady)
    }

    private suspend fun Fixture.assertCancelledStartSettled(states: List<AppPlaybackState>, stops: List<Unit>) {
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(null, runtime.recordingSelection.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        assertFalse(states.toString(), states.any { it is AppPlaybackState.Failed })
        assertFalse(runtime.videoPresentation.value.visible)
        assertEquals(playerLog.toString(), 0, player.mediaItemCount)
        // No subscription stays open, neither the stopped channel's nor a cancelled channel's.
        await { connection.unsubscribeCount == connection.subscribeCount }
        // Not the viewer's stop: no session Stop, and the next player entry is admitted.
        assertEquals(emptyList<Unit>(), stops)
        assertNotNull(runtime.enterPlayerScreen())
    }

    @Test fun sessionStopTearsDownLiveWithoutTimeshiftAndAnnouncesOnce() = exercise {
        live(false)
        val stops = collectSessionStops()
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_STOP))
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 1 }
        settle()
        assertEquals(1, stops.size)
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
    }

    @Test fun playbackRequestedAfterAQueuedStopWinsAndTheScreenStaysOpen() = exercise {
        live(false)
        val stops = collectSessionStops()
        lateinit var install: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            // A later request (for example CH+ or a picked recording) queues behind the Stop.
            install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value,
                    DvrEntryId(1))), RecordingPlaybackStart.START_OVER)
            }
            settle()
        }
        await { install.isCompleted }
        settle()
        // The queued Stop lost to the newer request: the newer target replaces A and the
        // showing player is not told to close.
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
        assertTrue(install.await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun channelAcceptedBeforeAQueuedStopRunsCancelsTheStop() = exercise {
        live(false)
        val stops = collectSessionStops()
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            // CH+ accepted by the live screen; its playLive waits for the zap to settle.
            runtime.notePlaybackIntent()
        }
        settle()
        assertEquals(0, connection.unsubscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun intentNotedAfterTheStopRanButBeforeTheScreenCollectsKeepsTheScreenOpen() = exercise {
        live(false)
        val screen = StandardTestDispatcher()
        val stops = mutableListOf<Unit>()
        scope.launch(screen, start = CoroutineStart.UNDISPATCHED) { runtime.sessionStops.collect { stops += it } }
        wrapper.stop()
        await { runtime.activeTarget.value == null }
        settle()
        // The screen has not taken the event yet when the viewer tunes (zap settle window).
        runtime.notePlaybackIntent()
        screen.scheduler.runCurrent()
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
        assertEquals(emptyList<Unit>(), stops)
    }

    /*
     * Warm opening actions start nothing, so each notes viewing intent itself (AppRoot: a click
     * on the already-playing channel, a warm return). Only the order of the opening action and
     * the session Stop decides; the screen's entry follows the opening action either way.
     */

    @Test fun warmClickAfterAQueuedStopKeepsThePlayerItOpensPlaying() = exercise {
        live(false)
        warmOpeningAfterAQueuedStopKeepsThePlayerOpen(AppPlaybackTarget.Live(ChannelId(1)))
    }

    @Test fun warmReturnAfterAQueuedStopKeepsTheRecordingPlaying() = exercise {
        recording()
        warmOpeningAfterAQueuedStopKeepsThePlayerOpen(AppPlaybackTarget.Recording(DvrEntryId(1)))
    }

    @Test fun warmClickBeforeAQueuedStopClosesWithTheStop() = exercise {
        live(false)
        warmOpeningBeforeAQueuedStopClosesWithIt()
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
    }

    @Test fun warmPlayerEnteredWhileAStopIsQueuedClosesWithTheStop() = exercise {
        recording() // a warm return to the recording's player, then the Stop
        warmOpeningBeforeAQueuedStopClosesWithIt()
    }

    @Test fun channelStartedFromBrowseWithAStopQueuedBehindItStopsAndItsPlayerCloses() = exercise {
        recording() // warm recording while browsing
        answerNextSubscribe(1)
        lateinit var start: Deferred<PlaybackTargetResult?>
        lateinit var stops: List<Unit>
        var entry = 0L
        whileCommandsBlocked {
            start = scope.async { runtime.playLive(liveSelection()) } // the click, in flight
            settle()
            wrapper.stop()
            settle()
            entry = requireNotNull(runtime.enterPlayerScreen()) // its player composes first
            stops = collectSessionStops()
        }
        await { start.isCompleted }
        settle()
        assertTrue(start.await()?.isStarted == true)
        // The SDK may not have sent the subscribe yet when the Stop ends the target; either
        // way no subscription outlives it (the SDK unsubscribes asynchronously).
        await { connection.subscribeCount == connection.unsubscribeCount }
        settle()
        assertEquals(connection.subscribeCount, connection.unsubscribeCount)
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(1, stops.size)
        assertTrue(runtime.isPlaybackIntentStopped(entry))
    }

    @Test fun recordingStartedWithAStopQueuedBehindItStopsAndItsPlayerRestoresNothing() = exercise {
        live(false)
        lateinit var install: Deferred<PlaybackTargetResult?>
        lateinit var restore: Deferred<PlaybackTargetResult?>
        lateinit var stops: List<Unit>
        whileCommandsBlocked {
            install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value,
                    DvrEntryId(1))), RecordingPlaybackStart.START_OVER)
            }
            settle()
            wrapper.stop()
            settle()
            assertNotNull(runtime.enterPlayerScreen())
            stops = collectSessionStops()
            restore = scope.async { restoreRecording() } // the recording screen's restore
            settle()
        }
        await { install.isCompleted && restore.isCompleted }
        settle()
        assertTrue(install.await()?.isStarted == true)
        assertEquals(null, restore.await())
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        assertEquals(1, stops.size)
    }

    @Test fun queuedStopThatIsSkippedLeavesTheEnteredPlayerPlaying() = exercise {
        live(false)
        var entry = 0L
        whileCommandsBlocked {
            runtime.stopFromSession { false } // the session detached before the Stop ran
            settle()
            entry = requireNotNull(runtime.enterPlayerScreen())
        }
        settle()
        val stops = collectSessionStops()
        settle()
        assertEquals(0, connection.unsubscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(runtime.isPlaybackIntentStopped(entry))
        assertEquals(emptyList<Unit>(), stops)
        // The registration ended with the skipped Stop: the next entry notes intent again.
        assertTrue(requireNotNull(runtime.enterPlayerScreen()) > entry)
    }

    @Test fun secondStopPendingUnderTheSameGenerationStillHoldsTheEntry() = exercise {
        live(false)
        val stops = collectSessionStops()
        lateinit var probe: Deferred<Long?>
        whileCommandsBlocked {
            runtime.stopFromSession { false } // skipped when it runs
            probe = scope.async { commands.serialize(onClosed = { null }) { runtime.enterPlayerScreen() } }
            runtime.stopFromSession { true } // still pending when the probe enters
            settle()
        }
        await { probe.isCompleted }
        settle()
        assertTrue(runtime.isPlaybackIntentStopped(requireNotNull(probe.await())))
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(1, stops.size)
    }

    @Test fun zapAfterAPendingStopSkipsTheStopAndPlays() = exercise {
        live(false)
        val stops = collectSessionStops()
        var zapIntent = 0L
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            assertNotNull(runtime.enterPlayerScreen())
            zapIntent = runtime.notePlaybackIntent() // CH+ after the Stop
        }
        settle()
        assertEquals(0, connection.unsubscribeCount)
        answerNextSubscribe(2)
        val zap = runtime.playLive(liveSelection(), zapIntent)
        assertTrue(zap?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun screenCollectingAfterItsPendingStopRanStillCloses() = exercise {
        live(false)
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            assertNotNull(runtime.enterPlayerScreen())
        }
        settle()
        assertEquals(null, runtime.activeTarget.value)
        // The screen's CloseOnSessionStop subscribes only now.
        assertEquals(1, collectSessionStops().size)
    }

    @Test fun stopPressedWhileTheAcceptedChannelStartsStopsIt() = exercise {
        live(false)
        val stops = collectSessionStops()
        val intent = runtime.notePlaybackIntent() // the live screen accepts the channel
        answerNextSubscribe(2)
        lateinit var zap: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            zap = scope.async { runtime.playLive(liveSelection(), intent) } // the settled zap
            settle()
            wrapper.stop() // arrives while the old target is still the active one
            settle()
        }
        await { zap.isCompleted }
        settle()
        assertTrue(zap.await()?.isStarted == true)
        // Whether the SDK resubscribes the same channel is timing-dependent; either way the
        // target the zap left behind is stopped.
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(1, stops.size)
        assertTrue(runtime.isPlaybackIntentStopped(intent))
    }

    @Test fun recordingRestoreQueuedBehindASessionStopDoesNotResurrectTheRecording() = exercise {
        recording()
        val stops = collectSessionStops()
        lateinit var restore: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            restore = scope.async { restoreRecording() }
            settle()
        }
        await { restore.isCompleted }
        settle()
        assertEquals(null, restore.await())
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        assertEquals(1, stops.size)
    }

    @Test fun recordingRestoreAfterTheStopButtonDoesNotResurrectTheRecording() = exercise {
        recording()
        runtime.stop()
        assertEquals(null, restoreRecording())
        settle()
        assertEquals(null, runtime.activeTarget.value)
    }

    @Test fun recordingRestoreWithoutAnExplicitStopSinceTheLastIntentRestores() = exercise {
        // Process restore: nothing plays yet and nobody stopped.
        assertTrue(restoreRecording()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
        // Stopped, then the viewer asked for playback again: a later restore works again.
        runtime.stop()
        runtime.notePlaybackIntent()
        assertTrue(restoreRecording()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
    }

    @Test fun stopQueuedAfterASelectionWithdrawsItsDelayedStartWithoutAFailure() =
        listOf(false to false, true to false, true to true).forEach { (retireBeforeStop, newerClick) ->
            exercise {
                live(false)
                val stops = collectSessionStops()
                var intent = -1L
                var clickIntent: Long? = null
                var clickedStart: Deferred<PlaybackTargetResult?>? = null
                lateinit var zap: Deferred<PlaybackTargetResult?>
                whileCommandsBlocked {
                    // The live screen accepts a channel (one intent); its start waits for the zap to settle.
                    intent = runtime.notePlaybackIntent()
                    runtime.noteLiveSelection(intent)
                    // An explicit channel click notes intent before joining the older browse start,
                    // without registering another live selection until its own start runs.
                    if (newerClick) clickIntent = runtime.notePlaybackIntent()
                    settle()
                    wrapper.stop() // the viewer's session Stop, after the selection
                    settle()
                    // Cancellation of the old fresh-decoder install can retire its target before
                    // the queued Stop acquires the serializer, even if a newer click hid the selection.
                    if (retireBeforeStop) retireWithState(AppPlaybackState.Starting)
                    // The zap settles: the delayed start carries the selection's intent and mints none.
                    zap = scope.async { runtime.playLive(liveSelection(), intent) }
                    clickedStart = clickIntent?.let { current ->
                        scope.async {
                            zap.await() // Explicit entry joins the admitted browse start first.
                            runtime.playLive(liveSelection(), current)
                        }
                    }
                    settle()
                }
                await { zap.isCompleted }
                clickedStart?.let { await { it.isCompleted } }
                settle()
                // Stop ran and closed the screen once; neither delayed start installed or failed.
                assertEquals(null, zap.await())
                assertEquals(null, clickedStart?.await())
                assertEquals(1, stops.size)
                assertTrue(runtime.isPlaybackIntentStopped(intent))
                clickIntent?.let { assertTrue(runtime.isPlaybackIntentStopped(it)) }
                assertEquals(1, connection.subscribeCount)
                await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
                assertEquals(null, runtime.activeTarget.value)
                assertEquals(AppPlaybackState.Idle, runtime.state.value)
                assertEquals(playerLog.toString(), 0, player.mediaItemCount)
            }
        }

    @Test fun enteringALivePlayerAfterAUserStopStartsNothing() = exercise {
        live(false)
        runtime.stop() // the Stop button (or a session Stop) completes before the screen enters
        settle()
        assertNull(runtime.enterPlayerScreen())
        assertEquals(null, restoreRecording())
        settle()
        assertEquals(1, connection.subscribeCount)
        assertEquals(null, runtime.activeTarget.value)
        // A launch request (appliance entry, startup autoplay) is new intent: the live
        // player it opens stays open and starts its channel.
        runtime.notePlaybackIntent()
        val entry = requireNotNull(runtime.enterPlayerScreen())
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val start = scope.async { runtime.playLive(liveSelection(), entry) }
        await { connection.subscribeCount == 2 }
        connection.awaitCollectionRegistered()
        connection.emit(started)
        await { start.isCompleted }
        assertTrue(start.await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun enteringARecordingPlayerAfterASessionStopRestoresNothing() = exercise {
        recording()
        val stops = collectSessionStops()
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 1 }
        assertNull(runtime.enterPlayerScreen())
        assertEquals(null, restoreRecording())
        settle()
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
    }

    @Test fun enteringWithoutAUserStopNotesIntentThatBeatsAnEarlierQueuedStop() = exercise {
        live(false)
        val entry = requireNotNull(runtime.enterPlayerScreen())
        assertFalse(runtime.isPlaybackIntentStopped(entry))
        assertNotNull(runtime.enterPlayerScreen()) // re-entry
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun automaticStopNeitherClosesAReenteredPlayerNorBlocksReconnectRetryOrRestore() = exercise {
        live(false)
        val stops = collectSessionStops()
        runtime.stopAfterLoss() // connection lost
        settle()
        await("exactly one subscription unsubscribed") { connection.unsubscribeCount == 1 }
        assertNotNull(runtime.enterPlayerScreen())
        // Reconnected: the live screen's automatic retry resubscribes.
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val retry = scope.async { runtime.retryLive() }
        await { connection.subscribeCount == 2 }
        connection.awaitCollectionRegistered()
        connection.emit(started)
        await { retry.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun recordingRestoreAfterAnAutomaticStopRestores() = exercise {
        recording()
        runtime.stopAfterLoss()
        settle()
        assertEquals(null, runtime.activeTarget.value)
        assertTrue(restoreRecording()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
    }

    @Test fun sessionStopTearsDownTimeshiftAndRecordingTargets() = exercise {
        val stops = collectSessionStops()
        live(true)
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 1 }
        recording()
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_STOP))
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 2 }
        settle()
        assertEquals(2, stops.size)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
    }

    @Test fun sessionStopWithoutAScreenCollectingClosesNoLaterPlayer() = exercise {
        recording()
        wrapper.stop()
        await { runtime.activeTarget.value == null }
        settle()
        // A player opened later without new intent closes itself; one opened by a new
        // request (a click) must not close on the earlier stop.
        assertNull(runtime.enterPlayerScreen())
        runtime.notePlaybackIntent()
        assertNotNull(runtime.enterPlayerScreen())
        val stops = collectSessionStops()
        settle()
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun queuedStopAfterCloseDoesNotStop() = exercise {
        live(false)
        val stops = collectSessionStops()
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(0, connection.unsubscribeCount)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun metadataUsesNamesAndPublishesProgrammeAndTargetChanges() = exercise {
        val titles = mutableListOf<String?>()
        wrapper.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                titles += mediaMetadata.title?.toString()
            }
        })
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
        live(true)
        await { wrapper.mediaMetadata.title == "Channel One" }
        assertTrue(wrapper.isCurrentMediaItemLive)
        assertTrue(wrapper.isCurrentMediaItemDynamic)
        // A finite raw-player duration must never leak into the live session timeline.
        reportedDuration = 60_000
        assertNull(wrapper.mediaMetadata.artist)
        val now = Clock.System.now()
        val previous = session.observation.value
        session.publish(SessionObservation.create(
            sessionState = previous.sessionState, channelState = previous.channelState, dvrState = previous.dvrState,
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(
            events = listOf(EpgEvent.create(EventId(1), ChannelId(1), start = now - 1.hours,
                stop = now + 1.hours, title = "Programme")),
        ))))
        advanceTimeBy(1_000)
        await { wrapper.mediaMetadata.subtitle == "Programme" }
        assertEquals(androidx.media3.common.C.TIME_UNSET, wrapper.duration)
        // The fake stream has no rendered wall-clock mapping: after Pause it cannot borrow Now.
        wrapper.pause()
        await { !player.playWhenReady && !runtime.liveStartUndisturbed.value }
        advanceTimeBy(1_000) // Resample the session timeline after the pause is applied.
        await { wrapper.mediaMetadata.subtitle == null }
        assertTrue(wrapper.isCurrentMediaItemLive)
        assertTrue(wrapper.isCurrentMediaItemDynamic)
        assertFalse(runtime.liveStartUndisturbed.value)
        recording()
        assertFalse(runtime.liveStartUndisturbed.value)
        await { wrapper.mediaMetadata.title == "Recording" }
        assertEquals("Recorded Channel", wrapper.mediaMetadata.subtitle)
        assertTrue(titles.containsAll(listOf("Channel One", "Recording")))
        assertNull(wrapper.currentMediaItem?.localConfiguration)
        assertNull(wrapper.mediaMetadata.artworkUri)
        assertEquals("Recording", requireNotNull(wrapper.mediaMetadata.extras).getString("at.bernhardberger.tvhplayer.session.programmeTitle"))
        assertFalse(wrapper.isCurrentMediaItemLive)
        assertFalse(wrapper.isCurrentMediaItemDynamic)
        assertEquals(60_000L, wrapper.duration)
        val admissionField = AppPlaybackRuntime::class.java.getDeclaredField("_recordingAdmission").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val admission = admissionField.get(runtime) as kotlinx.coroutines.flow.MutableStateFlow<RecordingPlaybackAdmission?>
        admission.value = at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory.growingRecordingAdmission(
            null, RecordingProgressCapability.SUPPORTED)
        await { wrapper.isCurrentMediaItemDynamic }
        assertFalse(wrapper.isCurrentMediaItemLive)
        assertFalse(requireNotNull(wrapper.mediaMetadata.extras).containsKey("at.bernhardberger.tvhplayer.session.nextProgrammeTitle"))
        assertEquals("", requireNotNull(wrapper.currentMediaItem).mediaId)
        val stop = scope.async { runtime.stop() }
        await { stop.isCompleted && wrapper.availableCommands == Player.Commands.EMPTY }
        assertNull(wrapper.currentMediaItem)
    }

    @Test fun closeLeavesRawPlayerAliveAndRejectsFurtherCommands() = exercise {
        recording()
        val requests = focusRequests
        observation.cancel()
        wrapper.close()
        wrapper.close()
        wrapper.pause()
        settle()
        assertTrue(player.playWhenReady)
        assertEquals(requests, focusRequests)
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
        player.pause()
        assertFalse(player.playWhenReady)
    }

    // Real Media3 and the SDK's asynchronous subscription machinery require looper pumping.
    /**
     * A Retry (or the automatic reconnect) has begun but waits behind another command when the
     * viewer pauses: the retried channel starts paused and its server hold follows the first picture.
     */
    private suspend fun Fixture.pauseDuringARetryHoldsTheRetriedChannel(
        viewerRetry: Boolean,
        pause: suspend Fixture.() -> Unit,
    ) {
        live(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        lateinit var retry: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            retry = scope.async { runtime.retryLive(viewerRetry) }
            settle()
            pause()
            settle()
        }
        await { retry.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        await { !player.playWhenReady }
        assertEquals(emptyList<Int>(), connection.speeds)
        grant(subscription = 2)
        playerReady()
        await { connection.speeds == listOf(0) }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    private fun exercise(realThreads: Boolean = false, block: suspend Fixture.() -> Unit) = runTest(timeout = 15.seconds) {
        val fixture = Fixture(CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()), testScheduler)
        try {
            // Only the artwork cases run SessionArtworkLoader on production Dispatchers.IO.
            fixture.driver.exercise(this, timeoutMillis = if (realThreads) 15_000 else null,
                settle = fixture::settle) { fixture.block() }
        }
        finally {
            fixture.observation.cancel()
            fixture.wrapper.close()
            fixture.scope.cancel()
            testScheduler.runCurrent()
            fixture.runtime.detach()
            fixture.player.release()
            fixture.session.shutdown()
        }
    }

    private class Fixture(val scope: CoroutineScope, val scheduler: TestCoroutineScheduler) {
        val driver = PlaybackTestDriver(scheduler)
        private val work = StandardTestDispatcher(scheduler)
        private val context = ApplicationProvider.getApplicationContext<Application>()
        var focusRequests = 0
        var focusGranted = true
        var focusListener: ((AudioInterruption) -> Unit)? = null
        private val focus = object : PlaybackAudioFocus {
            override fun request(onInterruption: (AudioInterruption) -> Unit): Boolean {
                focusRequests++
                focusListener = onInterruption
                return focusGranted
            }
            override fun abandon() = Unit
        }
        val connection = ScriptedSubscriptionConnection()
        private val subscriptions = MutableStateFlow(0)
        val manager = createSubscriptionManager(object : SubscriptionConnection by connection {
            override suspend fun subscribe(
                id: SubscriptionId,
                channelId: SubscriptionChannelId,
                options: SubscriptionOptions,
            ): SubscriptionOperationResult<SubscriptionConfirmation> =
                connection.subscribe(id, channelId, options).also {
                    subscriptions.value = connection.subscribeCount
                }
        }, work).apply { startAdmission() }
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Channel One")))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(DvrEntry.create(
                id = DvrEntryId(1), state = DvrEntryState.COMPLETED, title = "Recording", channelName = "Recorded Channel",
            )))),
        )).apply { scriptLivePlaybackSuccess(manager); scriptRecordingPlaybackSuccess() }
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        private val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, work,
            readProfileForEditing = { ServerProfileEditReadResult.Missing }).also { owner -> scope.launch { owner.run() } }
        private val clock = FakeClock(false)
        val player = ExoPlayer.Builder(context).setClock(clock).build()
        /** The SDK's Player stops and the playlists they leave, in order: a stop releases the decoders. */
        val playerLog: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        /** Runs as each [playerLog] entry is made, so a test can cancel a start at that point. */
        var onPlayerLog: (String) -> Unit = {}
        private fun logPlayer(entry: String) {
            playerLog += entry
            onPlayerLog(entry)
        }
        private val coordinator = createTvheadendPlaybackCoordinator(object : ExoPlayer by player {
            override fun stop() {
                logPlayer("stop")
                player.stop()
            }
        }, workDispatcher = work).also { it.launchIn(scope) }
        init {
            player.addListener(object : Player.Listener {
                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) logPlayer("items=${player.mediaItemCount}")
                }
            })
        }
        /** Runtime listeners, so tests can drive STATE_READY, which the fake stream never reaches. */
        private val playerListeners = mutableListOf<Player.Listener>()
        var reportedDuration: Long? = null
        var sessionSeeks = 0
        val runtime = AppPlaybackRuntime(object : ExoPlayer by player {
            override fun seekTo(positionMs: Long) {
                sessionSeeks++
                player.seekTo(positionMs)
            }
            override fun getDuration(): Long = reportedDuration ?: player.duration
            override fun addListener(listener: Player.Listener) {
                playerListeners += listener
                player.addListener(listener)
            }
            override fun removeListener(listener: Player.Listener) {
                playerListeners -= listener
                player.removeListener(listener)
            }
        }, session, coordinator, settings, profiles, scope, TvheadendAudioOutputProvider(context), focus,
            PlaybackRuntimePolicy.fromPlayerSettings(),
            elapsedRealtime = { scheduler.currentTime },
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
        val wrapper = SessionPlaybackPlayer(runtime, programmeWindowEnabled = true, labels = labels)
        val observation = scope.launch { wrapper.observe(session.observation) }

        @Suppress("UNCHECKED_CAST")
        fun retireWithState(state: AppPlaybackState) {
            val attributed = if (state is AppPlaybackState.Failed) {
                state.copy(target = state.target ?: runtime.activeTarget.value)
            } else state
            AppPlaybackRuntime::class.java.getDeclaredField("activeTargetEpoch").let {
                it.isAccessible = true
                it.set(runtime, null)
            }
            AppPlaybackRuntime::class.java.getDeclaredField("_activeTarget").let {
                it.isAccessible = true
                (it.get(runtime) as kotlinx.coroutines.flow.MutableStateFlow<AppPlaybackTarget?>).value = null
            }
            publishState(attributed)
        }

        @Suppress("UNCHECKED_CAST")
        fun publishState(state: AppPlaybackState) {
            AppPlaybackRuntime::class.java.getDeclaredField("presentation").let {
                it.isAccessible = true
                val publisher = it.get(runtime) as PlaybackPresentationPublisher
                when (state) {
                    is AppPlaybackState.Failed -> publisher.publishFailed(state)
                    is AppPlaybackState.Recovering -> publisher.publishRecovering(state.reason, state.retryDelayMillis)
                    AppPlaybackState.Starting -> publisher.publishStarting()
                    AppPlaybackState.Buffering -> PlaybackPresentationPublisher::class.java.getDeclaredField("_state").let { field ->
                        field.isAccessible = true
                        (field.get(publisher) as kotlinx.coroutines.flow.MutableStateFlow<AppPlaybackState>).value = state
                    }
                    else -> error("Unsupported fixture state")
                }
            }
        }

        suspend fun live(timeshift: Boolean, firstPicture: Boolean = true, channelId: ChannelId = ChannelId(1)) {
            settings.setTimeshiftEnabled(timeshift)
            val previousSubscriptions = connection.subscribeCount
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, if (timeshift) 120 else 0)))
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, channelId)))
            }
            await { connection.subscribeCount > previousSubscriptions }
            connection.awaitCollectionRegistered()
            connection.emit(started)
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            if (timeshift) {
                await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                    ?.timeshiftState is LiveTimeshiftState.Available }
                connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
                await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                    ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
                await { wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            }
            // The first picture is ready: a server pause is only sent after it.
            if (firstPicture) playerReady()
            settle()
            // The session offers Stop before a test presses it, even on a loaded machine.
            await { wrapper.isCommandAvailable(Player.COMMAND_STOP) }
        }

        val started = SubscriptionEvent.Started(listOf(
            SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                language = null, compositionId = null, ancillaryId = null, width = 320, height = 240,
                frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                audioVersion = null, channelCount = null, rate = null, rdsUecp = null),
        ), null)

        fun liveSelection() = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))

        /** Installs live with timeshift requested ([intent] if given); its grant is delivered later by [grant]. */
        suspend fun startingLive(intent: Long? = null) {
            settings.setTimeshiftEnabled(true)
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
            val install = scope.async { runtime.playLive(liveSelection(), intent) }
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            await { runtime.livePause.value.availability == LivePauseAvailability.STARTING }
        }

        /** The subscription of the latest start is confirmed and granted. */
        suspend fun grant(subscription: Int = 1) {
            await { connection.subscribeCount == subscription }
            connection.awaitCollectionRegistered()
            connection.emit(started)
            await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                ?.timeshiftState is LiveTimeshiftState.Available }
            connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
            await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
            await { runtime.livePause.value.availability == LivePauseAvailability.READY }
        }

        fun playerReady() {
            playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY) }
        }

        suspend fun recording() {
            val install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                    RecordingPlaybackStart.START_OVER)
            }
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            settle()
            await { wrapper.isCommandAvailable(Player.COMMAND_STOP) }
        }

        /** A recording start that runs once started, so a test can arrange its cancellation first. */
        fun recordingStart(): Deferred<PlaybackTargetResult?> = scope.async(start = CoroutineStart.LAZY) {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                RecordingPlaybackStart.START_OVER)
        }

        /** Subscribes before returning, as a showing player screen would be. */
        suspend fun restoreRecording(): PlaybackTargetResult? = runtime.restoreRecordingRoute(
            requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
            RecordingPlaybackStart.START_OVER,
        )

        fun collectStates(): List<AppPlaybackState> = java.util.Collections.synchronizedList(
            mutableListOf<AppPlaybackState>(),
        ).also { states ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.state.collect { states += it } }
        }

        fun collectSessionStops(): List<Unit> = mutableListOf<Unit>().also { stops ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.sessionStops.collect { stops += it } }
        }

        /** The runtime's command serializer, as in AutomaticAudioRuntimeTest; no runtime test seam. */
        val commands: PlaybackTargetCommandSerialization
            get() = AppPlaybackRuntime::class.java.getDeclaredField("targetCommands").let {
                it.isAccessible = true
                it.get(runtime) as PlaybackTargetCommandSerialization
            }

        /** Answers the [count]th live subscription (scripted Ok, then Started) when it arrives. */
        fun answerNextSubscribe(count: Int) {
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
            scope.launch {
                subscriptions.first { it >= count }
                assertEquals("expected subscription before answering its start", count, subscriptions.value)
                connection.awaitCollectionRegistered()
                connection.emit(started)
            }
        }

        /** Pending session Stops registered at arrival; no runtime test seam. */
        fun pendingSessionStops(): Int =
            AppPlaybackRuntime::class.java.getDeclaredField("pendingStops").let {
                it.isAccessible = true
                it.getInt(runtime)
            }

        /** Serializer busy, session Stop arrives, then the viewer's warm opening action. */
        suspend fun warmOpeningAfterAQueuedStopKeepsThePlayerOpen(target: AppPlaybackTarget) {
            lateinit var stops: List<Unit>
            var entry = 0L
            whileCommandsBlocked {
                wrapper.stop()
                assertEquals(1, pendingSessionStops())
                val opening = runtime.notePlaybackIntent()
                entry = requireNotNull(runtime.enterPlayerScreen())
                assertEquals(opening + 1, entry) // not joined to the older pending Stop
                stops = collectSessionStops()
            }
            // Drained: the Stop has had its check, and was skipped.
            assertEquals(0, pendingSessionStops())
            assertEquals(0, connection.unsubscribeCount)
            assertEquals(target, runtime.activeTarget.value)
            assertFalse(runtime.isPlaybackIntentStopped(entry))
            assertEquals(emptyList<Unit>(), stops)
        }

        /** Serializer busy, the viewer's warm opening action, then a session Stop. */
        suspend fun warmOpeningBeforeAQueuedStopClosesWithIt() {
            lateinit var stops: List<Unit>
            var entry = 0L
            whileCommandsBlocked {
                val opening = runtime.notePlaybackIntent()
                wrapper.stop()
                assertEquals(1, pendingSessionStops())
                entry = requireNotNull(runtime.enterPlayerScreen())
                assertEquals(opening, entry) // joined: the Stop is the viewer's latest action
                assertEquals(entry, runtime.enterPlayerScreen()) // re-entry notes nothing either
                stops = collectSessionStops()
            }
            await { stops.size == 1 }
            assertEquals(null, runtime.activeTarget.value)
            assertTrue(runtime.isPlaybackIntentStopped(entry)) // the entry's own start is withdrawn
            assertEquals(1, stops.size)
        }

        suspend fun whileCommandsBlocked(block: suspend () -> Unit) {
            // Hold the existing serializer; no runtime test seam.
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val blocker = scope.launch {
                commands.serialize(onClosed = {}) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            try { block() } finally {
                release.complete(Unit)
                blocker.join()
            }
            // Drain commands queued by the wrapper before checking their effects.
            commands.serialize(onClosed = {}) {}
        }

        fun await(label: String = "session playback condition", predicate: () -> Boolean) {
            settle()
            driver.awaitCurrent(label, predicate)
        }

        fun settle() = driver.settleLive(player)

        fun advanceTimeBy(millis: Long) = driver.advanceLive(player, clock, millis)
    }
}
