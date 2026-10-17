package at.bernhardberger.tvhplayer.viewmodels

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.datastore.preferences.core.edit
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.ChannelTagPreferences
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.dataStore
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelObservationProjectionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val models = ViewModelStore()
    private val firstTag = ChannelTag.create(ChannelTagId(1), name = "First")
    private val secondTag = ChannelTag.create(ChannelTagId(2), name = "Second")
    private val seven = Channel.create(ChannelId(7), name = "Seven", number = 7,
        tagIds = listOf(firstTag.id))
    private val nine = Channel.create(ChannelId(9), name = "Nine", number = 9,
        tagIds = listOf(secondTag.id))
    private val catalog = ChannelCatalog.create(
        channels = listOf(nine, seven),
        tags = listOf(firstTag, secondTag),
    )

    @After
    fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test fun railWarningStaysThroughAutomaticRetryAttemptsUntilConnected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val connecting = SessionObservation.create(sessionState = SessionState.Connecting)
        val session = FakeTvheadendSession(connecting)
        val owner = AppProfileOwner(session,
            at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore(
                at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult.anonymous("offline.invalid", 9982)),
            PlayerSettingsStore(InMemoryPreferencesDataStore()), dispatcher,
            readProfileForEditing = { at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        val notices = at.bernhardberger.tvhplayer.notices.NoticeCenter({ testScheduler.currentTime }) {
            at.bernhardberger.tvhplayer.notices.NoticeContext(owner.configurationGeneration.value,
                session.observation.value.currentSession?.generationIdentity)
        }
        val model = AppConnectionViewModel(session, owner,
            at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource(session.observation, owner.configurationGeneration, notices))
        models.put("connection", model)
        runCurrent()
        assertEquals(false, model.uiState.value.degraded)
        val failed = SessionObservation.create(sessionState = SessionState.Unavailable(
            at.bernhardberger.tvheadend.sdk.core.SessionFailure.TRANSPORT_UNAVAILABLE))
        val shown = listOf(failed, connecting, failed, connecting).map { state ->
            session.publish(state)
            runCurrent()
            model.uiState.value.degraded
        }
        assertEquals("the warning does not blink with each attempt", listOf(true, true, true, true), shown)
        session.publish(observation(ChannelRepositoryState.Current(catalog)))
        runCurrent()
        assertEquals(false, model.uiState.value.degraded)
    }

    @Test fun immediateRetryUsesSdkOnceAndPreservesFailurePolicyGuards() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val session = FakeTvheadendSession(observation(ChannelRepositoryState.Stale(catalog)))
        val owner = AppProfileOwner(session, TvheadendServerProfileStore(contextWithoutAndroidRuntime()),
            PlayerSettingsStore(InMemoryPreferencesDataStore()), dispatcher)
        val notices = at.bernhardberger.tvhplayer.notices.NoticeCenter({ testScheduler.currentTime }) {
            at.bernhardberger.tvhplayer.notices.NoticeContext(owner.configurationGeneration.value,
                session.observation.value.currentSession?.generationIdentity)
        }
        val model = AppConnectionViewModel(session, owner,
            at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource(session.observation, owner.configurationGeneration, notices))
        models.put("connection", model)
        val failures = listOf(at.bernhardberger.tvheadend.sdk.core.SessionFailure.TRANSPORT_UNAVAILABLE,
            at.bernhardberger.tvheadend.sdk.core.SessionFailure.AUTHENTICATION_REJECTED)
        for (failure in failures) {
            session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(failure)))
            model.reconnectNow()
            runCurrent()
        }
        assertEquals(0, session.calls.count { it == at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall.RETRY })
        for (failure in failures) {
            session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(failure)))
            model.reconnectNow(allowAutomaticBackoff = true)
            runCurrent()
        }
        assertEquals(1, session.calls.count { it == at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall.RETRY })
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        model.reconnectNow(); runCurrent()
        model.reconnectNow(allowAutomaticBackoff = true); runCurrent()
        assertEquals(1, session.calls.count { it == at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall.RETRY })
    }

    @Test
    fun scopeSurvivesMetadataPublicationsAndStillTracksCatalogAuthorityAndTagSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = contextWithoutAndroidRuntime()
        // The Context DataStore delegate is process-wide, including across JVM test classes.
        context.dataStore.edit { it.clear() }
        val settings = ChannelTagSettingsStore(context)
        settings.save(ChannelTagPreferences(activeTagId = firstTag.id))
        val session = FakeTvheadendSession(observation(ChannelRepositoryState.Stale(catalog)))
        val model = ChannelsViewModel(session, settings)
        models.put("channels", model)
        model.scope.first { !it.channelCatalogCurrent && it.scope.activeTagId == firstTag.id }
        session.publish(observation(ChannelRepositoryState.Current(catalog)))
        model.scope.first { it.channelCatalogCurrent && it.scope.activeTagId == firstTag.id }
        runCurrent()
        assertEquals(listOf(seven, nine), model.scope.value.scope.allChannels)
        assertEquals(listOf(seven), model.channels.value)

        publishUnrelatedMetadata(session) {
            runCurrent()
            assertTrue(model.scope.value.channelCatalogCurrent)
            assertEquals(listOf(seven), model.channels.value)
            assertEquals(firstTag.id, model.scope.value.scope.activeTagId)
        }

        val changedSeven = Channel.create(ChannelId(7), name = "Updated seven", number = 10,
            tagIds = listOf(secondTag.id))
        val changedTag = ChannelTag.create(firstTag.id, name = "Updated first")
        val changedCatalog = ChannelCatalog.create(
            channels = listOf(changedSeven, nine),
            tags = listOf(changedTag, secondTag),
        )
        session.publish(observation(ChannelRepositoryState.Current(changedCatalog)))
        runCurrent()
        assertEquals(listOf(nine, changedSeven), model.scope.value.scope.allChannels)
        assertEquals(listOf(changedTag, secondTag), model.scope.value.scope.tags)
        assertTrue(model.channels.value.isEmpty())

        model.selectTag(secondTag.id)
        model.scope.first { it.scope.activeTagId == secondTag.id }
        runCurrent()
        assertEquals(listOf(nine, changedSeven), model.channels.value)
        model.setScopeVisible(firstTag.id, false)
        model.scope.first { it.scope.tags == listOf(secondTag) }
        runCurrent()
        assertEquals(listOf(nine, changedSeven), model.channels.value)

        session.publish(observation(ChannelRepositoryState.Synchronizing(changedCatalog)))
        runCurrent()
        assertFalse(model.scope.value.channelCatalogCurrent)
        assertEquals(listOf(nine, changedSeven), model.channels.value)
        session.publish(observation(ChannelRepositoryState.Current(changedCatalog)))
        runCurrent()
        assertTrue(model.scope.value.channelCatalogCurrent)
        assertEquals(secondTag.id, model.scope.value.scope.activeTagId)
        assertEquals(listOf(nine, changedSeven), model.channels.value)
        models.clear()
        context.dataStore.edit { it.clear() }
    }

    @Test
    fun readinessSurvivesMetadataPublicationsAndStillTracksChannelsAuthorityAndConnection() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val session = FakeTvheadendSession(observation(ChannelRepositoryState.Current(catalog)))
        val context = contextWithoutAndroidRuntime()
        val owner = AppProfileOwner(
            session = session,
            profileStore = TvheadendServerProfileStore(context),
            playerSettings = PlayerSettingsStore(InMemoryPreferencesDataStore()),
            ioDispatcher = dispatcher,
        )
        val notices = at.bernhardberger.tvhplayer.notices.NoticeCenter({ testScheduler.currentTime }) {
            at.bernhardberger.tvhplayer.notices.NoticeContext(owner.configurationGeneration.value,
                session.observation.value.currentSession?.generationIdentity)
        }
        val source = at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource(
            session.observation, owner.configurationGeneration, notices)
        val model = AppConnectionViewModel(session, owner, source)
        models.put("connection", model)
        runCurrent()
        assertEquals(CurrentChannelReadiness.Ready(catalog.channels), model.uiState.value.currentChannelReadiness)

        publishUnrelatedMetadata(session) {
            runCurrent()
            assertEquals(CurrentChannelReadiness.Ready(catalog.channels), model.uiState.value.currentChannelReadiness)
        }

        session.publish(observation(ChannelRepositoryState.Current(catalog), SessionState.Disconnected))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Waiting, model.uiState.value.currentChannelReadiness)
        session.publish(observation(ChannelRepositoryState.Current(catalog)))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Ready(catalog.channels), model.uiState.value.currentChannelReadiness)

        session.publish(observation(ChannelRepositoryState.Stale(catalog), SessionState.Disconnected))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Browsable(catalog.channels), model.uiState.value.currentChannelReadiness)
        session.publish(observation(ChannelRepositoryState.Synchronizing(catalog)))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Browsable(catalog.channels), model.uiState.value.currentChannelReadiness)

        val replacement = ChannelCatalog.create(channels = listOf(seven))
        session.publish(observation(ChannelRepositoryState.Current(replacement)))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Ready(listOf(seven)), model.uiState.value.currentChannelReadiness)
        session.publish(observation(ChannelRepositoryState.Empty))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Waiting, model.uiState.value.currentChannelReadiness)
        session.publish(observation(ChannelRepositoryState.Current(ChannelCatalog.create())))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Ready(emptyList()), model.uiState.value.currentChannelReadiness)

        session.publish(SessionObservation.create(
            sessionState = SessionState.Synchronizing,
            channelState = ChannelRepositoryState.Synchronizing(catalog),
        ))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Browsable(catalog.channels), model.uiState.value.currentChannelReadiness)
        session.publish(observation(ChannelRepositoryState.Stale(catalog), SessionState.Connecting))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Browsable(catalog.channels), model.uiState.value.currentChannelReadiness)
        // An owner replacement withdraws the retained catalog atomically.
        session.publish(SessionObservation.create(
            sessionState = SessionState.Connecting,
            channelState = ChannelRepositoryState.Empty,
        ))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Waiting, model.uiState.value.currentChannelReadiness)
    }

    // These are propagation checks, not invocation counts: stateIn also conflates equal outputs.
    private suspend fun publishUnrelatedMetadata(
        session: FakeTvheadendSession,
        afterPublish: suspend () -> Unit,
    ) {
        repeat(3) { index ->
            val previous = session.observation.value
            val event = EpgEvent.create(
                EventId(index.toLong()), channelId = seven.id,
                start = Instant.fromEpochSeconds(1_800_000_000),
                stop = Instant.fromEpochSeconds(1_800_003_600),
            )
            session.publish(SessionObservation.create(
                sessionState = previous.sessionState,
                channelState = previous.channelState,
                epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(event))),
                dvrState = previous.dvrState,
            ))
            val epgPublication = session.observation.value
            assertSame(previous.channelState, epgPublication.channelState)
            assertSame(previous.currentSession, epgPublication.currentSession)
            assertNotEquals(previous.epgState, epgPublication.epgState)
            afterPublish()

            session.publish(SessionObservation.create(
                sessionState = epgPublication.sessionState,
                channelState = epgPublication.channelState,
                epgState = epgPublication.epgState,
                dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(
                    DvrEntry.create(DvrEntryId(index.toLong()), eventId = event.id),
                ))),
            ))
            val dvrPublication = session.observation.value
            assertSame(epgPublication.channelState, dvrPublication.channelState)
            assertSame(epgPublication.currentSession, dvrPublication.currentSession)
            assertNotEquals(epgPublication.dvrState, dvrPublication.dvrState)
            afterPublish()
        }
    }

    private fun observation(
        channels: ChannelRepositoryState,
        state: SessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED,
            dvrWrite = CapabilityAccess.ALLOWED,
        )),
    ): SessionObservation = SessionObservation.create(
        sessionState = state,
        channelState = channels,
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )

    private fun contextWithoutAndroidRuntime(): Context {
        // Same constructor-bypass technique as AppProfileOwnerTest; no Android methods are invoked.
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val context = unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(field.get(null), ProjectionTestContext::class.java) as ProjectionTestContext
        context.directory = temporaryFolder.root
        return context
    }
}

private class ProjectionTestContext private constructor() : ContextWrapper(null) {
    lateinit var directory: File
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = directory
}
