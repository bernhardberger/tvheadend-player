@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.ControlledAudioPlayer
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.testing.testSessionObservation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import at.bernhardberger.tvhplayer.testing.ImageLoaderRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Offline production-screen input; fake playback acceptance is not rendered-video evidence. */
@RunWith(Parameterized::class)
class NumericChannelReturnTest(private val tagged: Boolean, private val completion: String) {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val composeRule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun numericAToBToAReturnsThroughProductionAdmission() = runTest(timeout = 30.seconds) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tag = ChannelTag.create(ChannelTagId(1), name = "Pair")
        val channels = (1L..3L).map { id ->
            Channel.create(ChannelId(id), name = "Offline $id",
                number = if (completion == "three-digit") 100L + id else id,
                tagIds = if (id <= 2L) listOf(tag.id) else emptyList())
        }
        val session = FakeTvheadendSession(testSessionObservation(channels = channels, tags = listOf(tag)))
        repeat(3) { session.scriptLivePlaybackSuccess() }
        val storage = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = storage) {
            folder.root.resolve("numeric-return.preferences_pb")
        }
        val settings = PlayerSettingsStore(dataStore)
        val tags = ChannelTagSettingsStore(dataStore)
        val profileStore = FakeServerProfileStore()
        profileStore.addAnonymous("offline.invalid", 9982, activate = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val profiles = AppProfileOwner(session, profileStore, settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        val models = ViewModelStore()
        val controlled = withContext(Dispatchers.Main) { ControlledAudioPlayer() }
        val coordinator = createTvheadendPlaybackCoordinator(controlled.player)
        val lifetime = coordinator.launchIn(scope)
        val runtime = withContext(Dispatchers.Main) {
            AppPlaybackRuntime(controlled.player, session, coordinator, settings, profiles, scope,
                at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider(context),
                at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus.None,
                at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy.fromPlayerSettings(),
                noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
        }
        val selection = ChannelSelectionStore().apply { setSelected(ChannelId(1)) }
        val visible = mutableStateOf(true)
        val statsStore = at.bernhardberger.tvhplayer.di.PlayerStatsStore()
        var contentSet = false
        val profileJob = scope.launch { profiles.run() }
        try {
            val profileId = profiles.liveProfile.filterNotNull().first()
            val tagId = tag.id.takeIf { tagged }
            val preferences = tags.settings.first()
            assertEquals(profileId, preferences.profileId)
            tags.save(preferences.copy(activeTagId = tagId))
            val (video, catalog) = withContext(Dispatchers.Main) {
                val playerModel = VideoPlayerViewModel(runtime, session)
                val channelModel = ChannelsViewModel(session, tags, profiles.liveProfile)
                models.put("video", playerModel)
                models.put("channels", channelModel)
                playerModel to channelModel
            }
            val ready = catalog.scope.first { it.settingsLoaded && it.channelCatalogCurrent && it.profileId == profileId }
            assertEquals(tagId, ready.scope.activeTagId)
            assertEquals(channels.take(if (tagged) 2 else 3).map { it.id }, ready.scope.visibleChannels.map { it.id })
            contentSet = true
            composeRule.setContent {
                if (visible.value) TVHeadendPlayerTheme {
                    VideoPlayerScreen(video, selection, LastPlayedChannelStore(dataStore), TargetAspect(AspectRatioMode.FIT) {},
                        catalog, session, ChannelId(1), "Offline 1", {}, {}, runtime, statsStore = statsStore)
                }
            }
            runtime.activeTarget.first { it == AppPlaybackTarget.Live(ChannelId(1)) }
            for (id in listOf(2L, 1L)) {
                val digits = if (completion == "three-digit") "10$id" else "$id"
                composeRule.onRoot().performKeyInput {
                    digits.forEach { digit ->
                        pressKey(when (digit) { '0' -> Key.Zero; '1' -> Key.One; else -> Key.Two })
                    }
                    if (completion == "confirm") pressKey(Key.DirectionCenter)
                }
                when (completion) {
                    "three-digit" -> composeRule.mainClock.advanceTimeBy(300)
                    "timeout" -> composeRule.mainClock.advanceTimeBy(1_600)
                }
                runtime.activeTarget.first { it == AppPlaybackTarget.Live(ChannelId(id)) }
                assertEquals(ChannelId(id), selection.selectedId.value)
            }
        } finally {
            try {
                if (contentSet) {
                    composeRule.runOnIdle { visible.value = false }
                    composeRule.waitForIdle()
                }
                withContext(NonCancellable + Dispatchers.Main) {
                    models.clear()
                    try {
                        // Bound teardown on the same real dispatcher as the Android resources.
                        // Entry/settling above is still driven by Compose time and state signals.
                        withTimeout(5.seconds) {
                            lifetime.shutdown(2.seconds)
                            lifetime.join()
                            profileJob.cancelAndJoin()
                            session.shutdown()
                        }
                    } finally {
                        runtime.detach()
                        controlled.player.release()
                    }
                }
            } finally {
                scope.cancel()
                storage.cancel()
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "tagged={0}, completion={1}")
        fun cases() = listOf(false, true).flatMap { tagged ->
            listOf("confirm", "timeout", "three-digit").map { arrayOf<Any>(tagged, it) }
        }
    }
}
