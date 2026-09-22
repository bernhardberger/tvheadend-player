package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelStore
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.settings.*
import at.bernhardberger.tvhplayer.stores.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import coil3.ImageLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideHistoryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var view: View
    private lateinit var zone: TimeZone
    @Before fun before() { zone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun after() { models.clear(); scope.cancel(); if (::player.isInitialized) player.release(); TimeZone.setDefault(zone) }
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }

    @Test fun screenPastFrontierNowAndSessionReplacement() = exercise("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun screenPastFrontierGermanLargeText() = exercise("de", 1.3f)
    @Test fun pendingConfirmationCannotRecordAnArchivedEvent() = exercise("en", 1f, "confirm")
    @Test fun pendingConfigurationCannotRecordAnArchivedEvent() = exercise("en", 1f, "config")
    @Test fun pendingConfirmationClearsOnSessionReplacement() = exercise("en", 1f, "confirm", replaceSession = true)
    @Test fun pendingConfigurationClearsOnSessionReplacement() = exercise("en", 1f, "config", replaceSession = true)
    @Test fun historicalRecordingDetailsCloseOnSessionReplacement() = exercise("en", 1f, "details")

    private fun exercise(locale: String, scale: Float, transition: String? = null, replaceSession: Boolean = false) {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val hour = System.currentTimeMillis() / 3_600_000 * 3600
        val channels = listOf(Channel.create(ChannelId(1), name = "Documentary", number = 1),
            Channel.create(ChannelId(2), name = "Culture", number = 2))
        fun event(channel: Long, offset: Int) = EpgEvent.create(EventId(channel * 100 + offset + 10),
            channelId = ChannelId(channel), title = "Channel $channel hour $offset",
            start = Instant.fromEpochSeconds(hour + offset * 3600), stop = Instant.fromEpochSeconds(hour + (offset + 1) * 3600))
        val current = channels.flatMap { channel -> (0..3).map { event(channel.id.value, it) } }
        val history = channels.flatMap { channel -> (-6..-1).map { event(channel.id.value, it) } }
        var archiveFuture = false
        fun observation(withHistory: Boolean) = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = current.filterNot { archiveFuture && it.id == event(1, 1).id },
                historicalEvents = (if (withHistory) history else emptyList()) + if (archiveFuture) listOf(event(1, 1)) else emptyList(), coverages = channels.map {
                    EpgCoverage.create(channelId = it.id, coveredFrom = Instant.fromEpochSeconds(hour),
                        coveredTo = Instant.fromEpochSeconds(hour + 7 * 86400)) })),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = if (transition == "details") listOf(
                DvrEntry.create(id = DvrEntryId(42), eventId = event(1, -1).id, state = DvrEntryState.COMPLETED)) else emptyList())),
            dvrConfigurationsState = DvrConfigurationsState.Current.create(if (transition == "config") listOf(
                DvrConfiguration(DvrConfigId("one"), "One", ""), DvrConfiguration(DvrConfigId("two"), "Two", "")) else emptyList()),
        )
        val session = FakeTvheadendSession(observation(true))
        val settings = PlayerSettingsStore(preferences())
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        val model = ChannelsViewModel(session, ChannelTagSettingsStore(preferences()))
        models.put("channels", model)
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player)
        val runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope)
        val selection = ChannelSelectionStore()
        val position = GuidePositionStore()
        val lastPlayed = LastPlayedChannelStore(context)
        val loader = ImageLoader.Builder(context).diskCache(null).build()
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                TVHeadendPlayerTheme { EpgGridScreen(channelViewModel = model, session = session,
                    playerSession = runtime, selection = selection, guidePositionStore = position,
                    lastPlayedStore = lastPlayed, imageLoader = loader, onPlay = { _, _ -> }) }
            }
        }
        fun focused(title: String) {
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(title) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(title).assertIsFocused()
        }
        fun key(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Channel 1 hour 0")).fetchSemanticsNodes().isNotEmpty() }
        key(Key.DirectionDown)
        focused("Channel 1 hour 0")
        if (transition != null) {
            key(if (transition == "details") Key.DirectionLeft else Key.DirectionRight)
            focused(if (transition == "details") "Channel 1 hour -1" else "Channel 1 hour 1")
            key(Key.DirectionCenter)
            if (transition == "details") {
                compose.onNodeWithText(context.getString(R.string.watch_from_start)).assertExists()
                compose.runOnIdle { session.replaceGeneration(observation(false)) }
                compose.waitUntil(10_000) { compose.onAllNodes(hasText(context.getString(R.string.watch_from_start))).fetchSemanticsNodes().isEmpty() }
                compose.onNodeWithText(context.getString(R.string.close)).assertDoesNotExist()
                compose.waitUntil(10_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(context.getString(R.string.now)).requestFocus()
                key(Key.DirectionCenter)
                focused("Channel 1 hour 0")
            } else {
                compose.onNodeWithText(context.getString(R.string.record)).performKeyInput { pressKey(Key.DirectionCenter) }
                val dialogTitle = if (transition == "config") context.getString(R.string.recording_config_title)
                    else context.getString(R.string.record_confirm_title, "Channel 1 hour 1")
                compose.onNodeWithText(dialogTitle).assertExists()
                val before = session.calls.size
                compose.runOnIdle {
                    if (replaceSession) session.replaceGeneration(observation(true))
                    else { archiveFuture = true; session.publish(observation(true)) }
                }
                compose.waitUntil(10_000) { compose.onAllNodes(hasText(dialogTitle)).fetchSemanticsNodes().isEmpty() }
                compose.onNodeWithText(context.getString(R.string.record)).assertDoesNotExist()
                if (replaceSession) compose.onNodeWithText(context.getString(R.string.close)).assertDoesNotExist()
                else compose.onNodeWithText(context.getString(R.string.close)).assertIsFocused()
                    .performKeyInput { pressKey(Key.DirectionCenter) }
                assertEquals(before, session.calls.size)
                compose.onNodeWithText(context.getString(R.string.now)).requestFocus()
                key(Key.DirectionCenter)
                focused("Channel 1 hour 0")
            }
            return
        }
        val calls = session.calls.size
        key(Key.DirectionLeft)
        focused("Channel 1 hour -1")
        assertEquals("past navigation must not acquire future coverage", calls, session.calls.size)
        key(Key.DirectionDown)
        focused("Channel 2 hour -1")
        key(Key.DirectionUp)
        focused("Channel 1 hour -1")
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/guide-history-evidence").apply { mkdirs() }
            File(directory, "$locale-font$scale-full-past-guide.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "$locale-font$scale-full-past-guide.txt").writeText("canvas=960x540\ndensity=1\nlocale=$locale\nfontScale=$scale\nzone=UTC\nfocus=Channel 1 hour -1\nfull EpgGridScreen; real runtime/ExoPlayer/coordinator; SDK fake session\n")
            bitmap.recycle()
        }
        repeat(5) { key(Key.DirectionLeft) }
        focused("Channel 1 hour -6")
        key(Key.DirectionLeft)
        focused("Channel 1 hour -6")
        assertEquals("earliest past window stays local", calls, session.calls.size)
        compose.onNodeWithText(context.getString(R.string.now)).requestFocus()
        key(Key.DirectionCenter)
        focused("Channel 1 hour 0")
        key(Key.DirectionLeft)
        focused("Channel 1 hour -1")
        compose.runOnIdle { session.publish(observation(false)) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Channel 1 hour -1")).fetchSemanticsNodes().isEmpty() }
        focused(context.getString(R.string.now))
        compose.onNodeWithText(context.getString(R.string.now)).requestFocus()
        key(Key.DirectionCenter)
        focused("Channel 1 hour 0")
        compose.runOnIdle { session.publish(observation(true)) }
        key(Key.DirectionLeft)
        focused("Channel 1 hour -1")
        compose.runOnIdle { session.replaceGeneration(observation(false)) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Channel 1 hour -1")).fetchSemanticsNodes().isEmpty() }
        focused(context.getString(R.string.now))
        key(Key.DirectionCenter)
        focused("Channel 1 hour 0")
    }
}
