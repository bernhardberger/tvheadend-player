package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TargetAspectTest {
    @get:Rule val compose = createComposeRule()

    private val one = ChannelId(1)
    private val two = ChannelId(2)
    private val withChannel = DvrEntryId(1)
    private val withoutChannel = DvrEntryId(2)
    private val channelOfRecording = mapOf(withChannel to one)

    private val target = mutableStateOf<AppPlaybackTarget?>(AppPlaybackTarget.Live(one))
    private lateinit var aspect: TargetAspect

    private fun store(identity: Boolean) = PlayerSettingsStore(
        InMemoryPreferencesDataStore(
            if (identity) preferencesOf(stringPreferencesKey("explicit_audio_profile_v1") to "server")
            else preferencesOf(),
        ),
    )

    private fun show(store: PlayerSettingsStore) {
        compose.setContent {
            aspect = rememberTargetAspect(target.value, store) { channelOfRecording[it] }
        }
    }

    private fun choose(mode: AspectRatioMode) {
        compose.runOnIdle { aspect.select(mode) }
        compose.waitUntil { compose.runOnIdle { aspect.mode == mode } }
        compose.waitForIdle()
    }

    private fun switchTo(next: AppPlaybackTarget?) {
        compose.runOnIdle { target.value = next }
        compose.waitForIdle()
    }

    private fun mode() = compose.runOnIdle { aspect.mode }

    private fun stored(store: PlayerSettingsStore, channel: ChannelId) =
        runBlocking { store.channelAspects.choices.first().modeFor(channel) }

    @Test fun aChoiceIsStoredForThePlayingChannelAndAZapSwitchesToTheNewChannelsMode() {
        val store = store(identity = true)
        show(store)
        assertEquals(AspectRatioMode.FIT, mode())

        choose(AspectRatioMode.FORCE_4_3)
        assertEquals(AspectRatioMode.FORCE_4_3, stored(store, one))
        assertEquals(AspectRatioMode.FIT, stored(store, two))

        switchTo(AppPlaybackTarget.Live(two))
        assertEquals("another channel starts at Auto", AspectRatioMode.FIT, mode())
        choose(AspectRatioMode.FORCE_16_9)

        switchTo(AppPlaybackTarget.Live(one))
        assertEquals(AspectRatioMode.FORCE_4_3, mode())
        switchTo(AppPlaybackTarget.Live(two))
        assertEquals(AspectRatioMode.FORCE_16_9, mode())

        switchTo(AppPlaybackTarget.Live(one))
        choose(AspectRatioMode.FIT)
        assertEquals("Auto forgets the entry", AspectRatioMode.FIT, stored(store, one))
        assertEquals(AspectRatioMode.FIT, mode())
    }

    @Test fun aRecordingUsesAndChangesItsChannelsMode() {
        val store = store(identity = true)
        show(store)
        switchTo(AppPlaybackTarget.Recording(withChannel))
        choose(AspectRatioMode.FORCE_16_9)
        assertEquals(AspectRatioMode.FORCE_16_9, stored(store, one))

        switchTo(AppPlaybackTarget.Live(one))
        assertEquals("the live channel shares it", AspectRatioMode.FORCE_16_9, mode())
    }

    @Test fun aRecordingWithoutAChannelHoldsTheChoiceUntilTheTargetChanges() {
        val store = store(identity = true)
        show(store)
        switchTo(AppPlaybackTarget.Recording(withoutChannel))
        choose(AspectRatioMode.FORCE_4_3)
        assertEquals("applies to the surface and options now", AspectRatioMode.FORCE_4_3, mode())
        assertEquals("nothing is persisted", AspectRatioMode.FIT, stored(store, one))

        switchTo(AppPlaybackTarget.Live(two))
        assertEquals(AspectRatioMode.FIT, mode())
        switchTo(AppPlaybackTarget.Recording(withoutChannel))
        assertEquals("a new playback of it starts at Auto again", AspectRatioMode.FIT, mode())
    }

    @Test fun withoutAServerIdentityTheChoiceIsHeldInMemoryForTheCurrentTarget() {
        val store = store(identity = false)
        show(store)
        choose(AspectRatioMode.FORCE_16_9)
        assertEquals(AspectRatioMode.FORCE_16_9, mode())
        assertEquals(AspectRatioMode.FIT, stored(store, one))

        switchTo(AppPlaybackTarget.Live(two))
        assertEquals(AspectRatioMode.FIT, mode())
    }
}
