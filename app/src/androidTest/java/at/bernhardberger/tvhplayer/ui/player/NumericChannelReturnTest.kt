@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.ControlledAudioPlayer
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.LegacyCredentialSource
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.testing.testSessionObservation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Offline production-screen input; fake playback acceptance is not rendered-video evidence. */
@RunWith(Parameterized::class)
class NumericChannelReturnTest(private val tagged: Boolean, private val completion: String) {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun numericAToBToAReturnsThroughProductionAdmission() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tag = ChannelTag.create(ChannelTagId(1), name = "Pair")
        val channels = (1L..3L).map { id ->
            Channel.create(ChannelId(id), name = "Offline $id",
                number = if (completion == "three-digit") 100L + id else id,
                tagIds = if (id <= 2L) listOf(tag.id) else emptyList())
        }
        val session = FakeTvheadendSession(testSessionObservation(channels = channels, tags = listOf(tag)))
        repeat(3) { session.scriptLivePlaybackSuccess() }
        val settings = PlayerSettingsStore(context)
        val tags = ChannelTagSettingsStore(context)
        tags.selectTag(if (tagged) tag.id else null)
        val profileStore = TvheadendServerProfileStore(context)
        profileStore.storeAnonymous("offline.invalid", 9982)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val profiles = AppProfileOwner(context, session, profileStore, LegacyCredentialSource(context), settings, Dispatchers.IO)
        val profileJob = scope.launch { profiles.run() }
        profiles.serverProfile.filterNotNull().first()
        val models = ViewModelStore()
        val controlled = withContext(Dispatchers.Main) { ControlledAudioPlayer() }
        val coordinator = createTvheadendPlaybackCoordinator(controlled.player)
        val lifetime = coordinator.launchIn(scope)
        val runtime = withContext(Dispatchers.Main) {
            AppPlaybackRuntime(controlled.player, session, coordinator, settings, profiles, scope)
        }
        val video = withContext(Dispatchers.Main) { VideoPlayerViewModel(runtime, session) }
        val catalog = withContext(Dispatchers.Main) { ChannelsViewModel(session, tags) }
        models.put("video", video)
        models.put("channels", catalog)
        catalog.channels.first { it.size == if (tagged) 2 else 3 }
        val selection = ChannelSelectionStore().apply { setSelected(ChannelId(1)) }
        val images = ImageLoader(context)
        val visible = mutableStateOf(true)
        try {
            composeRule.setContent {
                if (visible.value) TVHeadendPlayerTheme {
                    VideoPlayerScreen(video, selection, LastPlayedChannelStore(context), settings,
                        catalog, images, session, ChannelId(1), "Offline 1", {}, {})
                }
            }
            composeRule.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
            for (id in listOf(2L, 1L)) {
                val digits = if (completion == "three-digit") "10$id" else "$id"
                composeRule.onRoot().performKeyInput {
                    digits.forEach { digit ->
                        pressKey(when (digit) { '0' -> Key.Zero; '1' -> Key.One; else -> Key.Two })
                    }
                    if (completion == "confirm") pressKey(Key.DirectionCenter)
                }
                composeRule.mainClock.advanceTimeBy(if (completion == "three-digit") 300 else 1_600)
                composeRule.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(id)) }
                assertEquals(ChannelId(id), selection.selectedId.value)
            }
        } finally {
            composeRule.runOnIdle { visible.value = false }
            composeRule.waitForIdle()
            withContext(Dispatchers.Main) { models.clear() }
            lifetime.shutdown(2.seconds)
            lifetime.join()
            profileJob.cancelAndJoin()
            session.shutdown()
            withContext(Dispatchers.Main) { runtime.detach(); controlled.player.release() }
            scope.cancel()
            images.shutdown()
            tags.selectTag(null)
            profileStore.clearProfile()
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "tagged={0}, completion={1}")
        fun cases() = listOf(false, true).flatMap { tagged ->
            listOf("confirm", "timeout", "three-digit").map { arrayOf<Any>(tagged, it) }
        }
    }
}
