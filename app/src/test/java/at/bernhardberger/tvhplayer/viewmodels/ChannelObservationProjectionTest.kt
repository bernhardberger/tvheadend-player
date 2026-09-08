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
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.LegacyCredentialSource
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.dataStore
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    @Test
    fun scopeSurvivesMetadataPublicationsAndStillTracksCatalogAuthorityAndTagSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = contextWithoutAndroidRuntime()
        // The Context DataStore delegate is process-wide, including across JVM test classes.
        context.dataStore.edit { it.clear() }
        val settings = ChannelTagSettingsStore(context)
        settings.selectTag(firstTag.id)
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

        session.publish(observation(ChannelRepositoryState.Stale(changedCatalog)))
        runCurrent()
        assertFalse(model.scope.value.channelCatalogCurrent)
        // Change selection while retained, avoiding the separate current-catalog APP10 race.
        settings.selectTag(secondTag.id)
        model.scope.first { it.scope.activeTagId == secondTag.id }
        runCurrent()
        assertEquals(listOf(nine, changedSeven), model.channels.value)
        settings.setScopeVisible(firstTag.id, false, setOf(firstTag.id, secondTag.id))
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
            context = context,
            session = session,
            profileStore = TvheadendServerProfileStore(context),
            legacyCredentials = LegacyCredentialSource(context),
            playerSettings = PlayerSettingsStore(InMemoryPreferencesDataStore()),
            ioDispatcher = dispatcher,
        )
        val model = AppConnectionViewModel(session, owner)
        models.put("connection", model)
        runCurrent()
        assertEquals(CurrentChannelReadiness.Ready(catalog.channels), model.currentChannelReadiness.value)

        publishUnrelatedMetadata(session) {
            runCurrent()
            assertEquals(CurrentChannelReadiness.Ready(catalog.channels), model.currentChannelReadiness.value)
        }

        session.publish(observation(ChannelRepositoryState.Current(catalog), SessionState.Disconnected))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Waiting, model.currentChannelReadiness.value)
        session.publish(observation(ChannelRepositoryState.Current(catalog)))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Ready(catalog.channels), model.currentChannelReadiness.value)

        session.publish(observation(ChannelRepositoryState.Stale(catalog), SessionState.Disconnected))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Browsable(catalog.channels), model.currentChannelReadiness.value)
        session.publish(observation(ChannelRepositoryState.Synchronizing(catalog)))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Browsable(catalog.channels), model.currentChannelReadiness.value)

        val replacement = ChannelCatalog.create(channels = listOf(seven))
        session.publish(observation(ChannelRepositoryState.Current(replacement)))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Ready(listOf(seven)), model.currentChannelReadiness.value)
        session.publish(observation(ChannelRepositoryState.Empty))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Waiting, model.currentChannelReadiness.value)
        session.publish(observation(ChannelRepositoryState.Current(ChannelCatalog.create())))
        runCurrent()
        assertEquals(CurrentChannelReadiness.Ready(emptyList()), model.currentChannelReadiness.value)
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
