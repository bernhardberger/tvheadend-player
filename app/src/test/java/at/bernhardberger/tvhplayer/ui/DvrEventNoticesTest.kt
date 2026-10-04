package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.notifications.*
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class DvrEventNoticesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val utc = ZoneId.of("UTC")
    private fun entry(state: DvrEntryState, id: Long = 1) = DvrEntry.create(
        id = DvrEntryId(id), state = state, title = "Nature", channelName = "Documentary", start = now + 60.seconds)

    @Test fun externalClientEventsEmitOnceAndMetadataOnlyChangesAreSilent() = runTest {
        val source = FakeTvheadendSession(observation(emptyList()))
        val queue = AppNoticeQueue({ 0L }, { AppNoticeContext(0, source.observation.value.currentSession) })
        backgroundScope.launch { DvrEventNotices(context, RecentDvrIntents { now }, { now }).collect(source.observation, queue) }
        runCurrent()
        assertTrue(queue.state.value.pending.isEmpty())
        source.publish(observation(listOf(entry(DvrEntryState.SCHEDULED))))
        runCurrent()
        val scheduled = queue.state.value.pending.single()
        assertEquals(R.string.recording_notice_scheduled, scheduled.message)
        source.publish(observation(listOf(entry(DvrEntryState.SCHEDULED))))
        runCurrent()
        assertEquals(scheduled, queue.state.value.pending.single())
        source.publish(observation(listOf(entry(DvrEntryState.RECORDING))))
        runCurrent()
        assertEquals(R.string.recording_notice_started, queue.state.value.pending.single().message)
        assertEquals(AppNoticeIcon.RECORDING, queue.state.value.pending.single().icon)
    }

    @Test fun initialSnapshotWithExistingRecordingsIsSilent() = runTest {
        val source = FakeTvheadendSession(observation(listOf(entry(DvrEntryState.SCHEDULED), entry(DvrEntryState.RECORDING, 2))))
        val queue = AppNoticeQueue({ 0L }, { Unit })
        backgroundScope.launch { DvrEventNotices(context, RecentDvrIntents { now }, { now }).collect(source.observation, queue) }
        runCurrent()
        assertTrue(queue.state.value.pending.isEmpty())
    }

    @Test fun serverEventBeforeStopResultWaitsForAcceptanceWithoutLosingLaterEvents() = runTest {
        val source = FakeTvheadendSession(observation(listOf(entry(DvrEntryState.RECORDING))))
        val intents = RecentDvrIntents { now }
        val queue = AppNoticeQueue({ 0L }, { Unit })
        val result = CompletableDeferred<DvrMutationResult<Unit>>()
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> DvrMutationResult.NotReady },
            stopEntry = { _, _ ->
                source.publish(observation(listOf(entry(DvrEntryState.COMPLETED))))
                result.await()
            },
            cancelEntry = { _, _ -> DvrMutationResult.NotReady },
            deleteEntry = { _, _ -> DvrMutationResult.NotReady },
            localIntents = intents,
        )
        backgroundScope.launch { DvrEventNotices(context, intents, { now }).collect(source.observation, queue) }
        runCurrent()
        backgroundScope.launch { actions.execute(DvrMutationAction.Stop(requireNotNull(source.observation.value.currentSession), DvrEntryId(1))) }
        runCurrent()
        assertTrue(queue.state.value.pending.isEmpty())
        source.publish(observation(listOf(entry(DvrEntryState.COMPLETED), entry(DvrEntryState.RECORDING, 2))))
        runCurrent()
        result.complete(DvrMutationResult.Confirmed(Unit))
        runCurrent()
        assertEquals(listOf(R.string.recording_notice_stopped, R.string.recording_notice_started),
            queue.state.value.pending.map { it.message })
    }

    @Test fun rejectedStopDoesNotRelabelNaturalCompletion() = runTest {
        val source = FakeTvheadendSession(observation(listOf(entry(DvrEntryState.RECORDING))))
        val intents = RecentDvrIntents { now }
        val queue = AppNoticeQueue({ 0L }, { Unit })
        backgroundScope.launch { DvrEventNotices(context, intents, { now }).collect(source.observation, queue) }
        runCurrent()
        val ticket = intents.begin(requireNotNull(source.observation.value.currentSession).generationIdentity, DvrEntryId(1), DvrLocalIntentKind.STOP)
        source.publish(observation(listOf(entry(DvrEntryState.COMPLETED))))
        runCurrent()
        assertTrue(queue.state.value.pending.isEmpty())
        intents.finish(ticket, false)
        runCurrent()
        assertEquals(R.string.recording_notice_finished, queue.state.value.pending.single().message)
    }

    @Test fun profileChangeWhileAwaitingAcceptanceDiscardsTheOldEvent() = runTest {
        val source = FakeTvheadendSession(observation(listOf(entry(DvrEntryState.RECORDING))))
        val intents = RecentDvrIntents { now }
        var profile = 1L
        val queue = AppNoticeQueue({ 0L }, { AppNoticeContext(profile, source.observation.value.currentSession) })
        backgroundScope.launch { DvrEventNotices(context, intents, { now }).collect(source.observation, queue) }
        runCurrent()
        val ticket = intents.begin(requireNotNull(source.observation.value.currentSession).generationIdentity, DvrEntryId(1), DvrLocalIntentKind.STOP)
        source.publish(observation(listOf(entry(DvrEntryState.COMPLETED))))
        runCurrent()
        profile++
        intents.finish(ticket, true)
        runCurrent()
        assertTrue(queue.state.value.pending.isEmpty())
    }

    @Test fun detailsUseLocalizedDatesAndPluralCounts() {
        val de = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) })
        fun scheduled(offset: Long) = DvrEvent(DvrEventKind.SCHEDULED, DvrEntry.create(DvrEntryId(1),
            title = "Nature", channelName = "Documentary", start = now + offset.seconds))
        assertEquals("Nature · Documentary, Today 12:01", scheduled(60).detail(context, now, utc).breakable())
        assertEquals("Nature · Documentary, Tomorrow 12:00", scheduled(86_400).detail(context, now, utc).breakable())
        assertEquals("Nature · Documentary, Tue 12:00", scheduled(172_800).detail(context, now, utc).breakable())
        assertEquals("Nature · Documentary, Heute 12:01", scheduled(60).detail(de, now, utc).breakable())
        assertEquals("Nature · Documentary, Morgen 12:00", scheduled(86_400).detail(de, now, utc).breakable())
        assertEquals("Nature · Documentary, Di. 12:00", scheduled(172_800).detail(de, now, utc).breakable())
        val series = DvrEvent(DvrEventKind.SERIES_SCHEDULED, entry(DvrEntryState.SCHEDULED), 3)
        assertEquals("Nature · 3 recordings", series.detail(context, now).breakable())
        assertEquals("Nature · 3 Aufnahmen", series.detail(de, now).breakable())
    }

    @Test fun missingTitleFallsBackToChannelThenLocalizedRecordingAndMissingChannelIsOmitted() {
        val de = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) })
        fun started(title: String?, channel: String?) = DvrEvent(DvrEventKind.STARTED,
            DvrEntry.create(DvrEntryId(1), title = title, channelName = channel))
        assertEquals("Documentary", started("  ", "Documentary").detail(context, now))
        assertEquals("Recording", started(null, null).detail(context, now))
        assertEquals("Aufnahme", started(null, " ").detail(de, now))
        assertEquals("Nature", started("Nature", null).detail(context, now))
        assertEquals("Nature · Documentary", started("Nature", "Documentary").detail(context, now).breakable())
    }

    @Test fun knownSubscriptionFailuresHaveReasonsAndUnknownFailuresDoNot() {
        val known = mapOf(DvrSubscriptionError.NO_FREE_ADAPTER to R.string.tvh_no_free_adapter,
            DvrSubscriptionError.SCRAMBLED to R.string.tvh_scrambled,
            DvrSubscriptionError.BAD_SIGNAL to R.string.recording_notice_no_signal,
            DvrSubscriptionError.NO_DISK_SPACE to R.string.recording_notice_disk_full,
            DvrSubscriptionError.USER_ACCESS to R.string.recording_notice_access,
            DvrSubscriptionError.USER_LIMIT to R.string.recording_notice_limit,
            DvrSubscriptionError.WEAK_STREAM to R.string.recording_notice_weak_stream)
        fun failed(error: DvrSubscriptionError?) = DvrEvent(DvrEventKind.FAILED,
            DvrEntry.create(DvrEntryId(1), title = "Nature", subscriptionError = error)).detail(context, now).breakable()
        known.forEach { (error, reason) -> assertEquals("Nature · ${context.getString(reason)}", failed(error)) }
        assertEquals("Nature", failed(null))
        assertEquals("Nature", failed(DvrSubscriptionError.UNKNOWN))
    }

    private fun String.breakable() = replace('\u00a0', ' ')
    private fun observation(entries: List<DvrEntry>) = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries)))
}
