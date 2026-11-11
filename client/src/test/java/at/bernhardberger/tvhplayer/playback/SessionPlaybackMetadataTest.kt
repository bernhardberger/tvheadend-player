@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi::class,
    at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
internal class SessionPlaybackMetadataTest : SessionPlaybackFixture() {
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

}
