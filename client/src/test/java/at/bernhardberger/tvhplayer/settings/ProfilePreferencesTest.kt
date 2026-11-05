package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.StreamProfile
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProfilePreferencesTest {
    @Test fun recentsTargetBecomesAvailableWhenSessionsAlignAfterConfirmation() {
        val profile = ServerProfileId.generate()
        val channel = ChannelId(1)
        assertNull(at.bernhardberger.tvhplayer.stores.recentChannelToPersist(profile, channel, false))
        assertEquals(profile to channel, at.bernhardberger.tvhplayer.stores.recentChannelToPersist(profile, channel, true))
        assertNull(at.bernhardberger.tvhplayer.stores.recentChannelToPersist(null, channel, true))
        assertNull(at.bernhardberger.tvhplayer.stores.recentChannelToPersist(profile, null, true))
    }

    @Test fun delayedRecentsAndTagWritesCannotLandInTheReplacementProfile() = runTest {
        val data = InMemoryPreferencesDataStore()
        val settings = PlayerSettingsStore(data)
        val recents = LastPlayedChannelStore(data)
        val tags = ChannelTagSettingsStore(data)
        val first = ServerProfileId.generate()
        val second = ServerProfileId.generate()
        val withoutProfile = tags.settings.first()
        settings.useProfile(first)
        recents.setChannelId(ChannelId(1))
        val loaded = tags.settings.first()
        val release = CompletableDeferred<Unit>()
        val pending = async {
            release.await()
            recents.setChannelId(first, ChannelId(99))
            tags.save(loaded.copy(activeTagId = at.bernhardberger.tvheadend.sdk.core.ChannelTagId(99)))
        }
        settings.useProfile(second)
        recents.setChannelId(ChannelId(2))
        tags.save(tags.settings.first().copy(activeTagId = at.bernhardberger.tvheadend.sdk.core.ChannelTagId(2)))
        release.complete(Unit)
        pending.await()
        tags.save(withoutProfile.copy(activeTagId = at.bernhardberger.tvheadend.sdk.core.ChannelTagId(99)))
        assertEquals(listOf(ChannelId(2)), recents.recentChannelIds.first())
        assertEquals(ChannelId(2), recents.channelId.first())
        assertEquals(at.bernhardberger.tvheadend.sdk.core.ChannelTagId(2), tags.settings.first().activeTagId)
        settings.useProfile(first)
        assertEquals(listOf(ChannelId(1)), recents.recentChannelIds.first())
        assertNull(tags.settings.first().activeTagId)
    }

    @Test fun delayedAudioWriteAfterRemovalDoesNotRecreateRemovedData() = runTest {
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        val removed = ServerProfileId.generate()
        val remaining = ServerProfileId.generate()
        val audio = AudioTrackChoice("de", "audio/mpeg", 0, 2, 48000)
        settings.useProfile(removed)
        settings.audioChoices.write(removed.value, ChannelId(1), audio)
        val release = CompletableDeferred<Unit>()
        val pending = async {
            release.await()
            settings.audioChoices.write(removed.value, ChannelId(1), audio)
        }
        settings.useProfile(remaining)
        settings.removeProfile(removed)
        settings.audioChoices.write(remaining.value, ChannelId(1), audio.copy(language = "en"))
        release.complete(Unit)
        pending.await()
        assertNull(settings.audioChoices.read(removed.value, ChannelId(1)))
        assertEquals(audio.copy(language = "en"), settings.audioChoices.read(remaining.value, ChannelId(1)))
    }

    @Test fun displayNameUsesLabelOrAccountHostAndOnlyNonDefaultPortWithMiddleDot() = runTest {
        val store = FakeServerProfileStore()
        store.addAnonymous("first.invalid")
        store.addAnonymous("second.invalid", port = 9983)
        store.addPassword("first.invalid", username = "viewer", password = "synthetic")
        store.addPassword("second.invalid", port = 9983, username = "viewer", password = "synthetic", label = "  Lounge  ")
        store.addPassword("second.invalid", port = 9983, username = "guest", password = "synthetic")
        val profiles = (store.profiles() as ServerProfileListResult.Available).profiles
        assertEquals(listOf("first.invalid", "second.invalid · port 9983", "viewer on first.invalid", "Lounge",
            "guest on second.invalid · port 9983"),
            profiles.map(::serverProfileDisplayName))
        assertEquals(profiles[0].id, existingServerProfile(profiles, " first.invalid ", 9982, null))
        assertEquals(profiles[2].id, existingServerProfile(profiles, "first.invalid", 9982, "viewer"))
        assertNull(existingServerProfile(profiles, "first.invalid", 9983, "viewer"))
        assertNull(existingServerProfile(profiles, "first.invalid", 9982, "other"))
    }

    @Test fun unreadablePasswordProfileWithoutUsernameDoesNotMatchAnAnonymousProfile() = runTest {
        val store = FakeServerProfileStore()
        val id = (store.addPassword("first.invalid", username = "viewer", password = "synthetic") as
            at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult.Applied).id
        store.scriptCredentialsUnavailable(id, forgetUsername = true)
        val profiles = (store.profiles() as ServerProfileListResult.Available).profiles
        assertNull(existingServerProfile(profiles, "first.invalid", 9982, null))
        assertNull(existingServerProfile(profiles, "first.invalid", 9982, "viewer"))
    }

    @Test fun perProfileDataIsIsolatedRestoredAndRemovalDeletesOnlyRequestedProfile() = runTest {
        val data = InMemoryPreferencesDataStore(preferencesOf(longPreferencesKey("activeChannelTagIdLong") to 99L))
        val settings = PlayerSettingsStore(data)
        val recents = LastPlayedChannelStore(data)
        val tags = ChannelTagSettingsStore(data)
        val first = ServerProfileId.generate()
        val second = ServerProfileId.generate()
        val channel = ChannelId(1)
        val audio = AudioTrackChoice("de", "audio/mpeg", 0, 2, 48000)
        val stream = StreamProfileId("11111111111111111111111111111111")
        val streams = listOf(StreamProfile(stream, "Test", ""))
        settings.useProfile(first)
        assertNull(tags.settings.first().activeTagId)
        recents.setChannelId(channel)
        settings.channelAspects.set(first.value, channel, AspectRatioMode.FORCE_4_3)
        settings.audioChoices.write(first.value, channel, audio)
        settings.setStreamProfile(stream)
        tags.save(tags.settings.first().copy(activeTagId = at.bernhardberger.tvheadend.sdk.core.ChannelTagId(7)))
        settings.useProfile(second)
        assertTrue(recents.recentChannelIds.first().isEmpty())
        assertNull(recents.channelId.first())
        assertNull(tags.settings.first().activeTagId)
        assertEquals(AspectRatioMode.FIT, settings.channelAspects.choices.first().modeFor(channel))
        assertNull(settings.audioChoices.read(second.value, channel))
        assertNull(settings.resolveStreamProfileSelection(streams) { true })
        recents.setChannelId(ChannelId(2))
        settings.channelAspects.set(second.value, channel, AspectRatioMode.FORCE_16_9)
        settings.audioChoices.write(second.value, channel, audio.copy(language = "en"))
        settings.useProfile(first)
        assertEquals(listOf(channel), recents.recentChannelIds.first())
        assertEquals(channel, recents.channelId.first())
        assertEquals(at.bernhardberger.tvheadend.sdk.core.ChannelTagId(7), tags.settings.first().activeTagId)
        assertEquals(AspectRatioMode.FORCE_4_3, settings.channelAspects.choices.first().modeFor(channel))
        assertEquals(audio, settings.audioChoices.read(first.value, channel))
        assertEquals(stream, settings.resolveStreamProfileSelection(streams) { true })
        settings.removeProfile(first)
        assertTrue(recents.recentChannelIds.first().isEmpty())
        assertNull(recents.channelId.first())
        assertNull(tags.settings.first().activeTagId)
        assertEquals(AspectRatioMode.FIT, settings.channelAspects.choices.first().modeFor(channel))
        assertNull(settings.audioChoices.read(first.value, channel))
        assertNull(settings.resolveStreamProfileSelection(streams) { true })
        settings.useProfile(second)
        assertEquals(listOf(ChannelId(2)), recents.recentChannelIds.first())
        assertEquals(AspectRatioMode.FORCE_16_9, settings.channelAspects.choices.first().modeFor(channel))
        assertEquals(audio.copy(language = "en"), settings.audioChoices.read(second.value, channel))
        settings.useProfile(null)
        assertTrue(recents.recentChannelIds.first().isEmpty())
        assertEquals(ChannelAspects.None, settings.channelAspects.choices.first())
    }

    @Test fun fillingOneProfilesChoiceLimitsDoesNotEvictAnotherProfilesData() = runTest {
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        val first = ServerProfileId.generate()
        val second = ServerProfileId.generate()
        val audio = AudioTrackChoice("de", "audio/mpeg", 0, 2, 48000)
        settings.useProfile(first)
        settings.channelAspects.set(first.value, ChannelId(1), AspectRatioMode.FORCE_4_3)
        settings.audioChoices.write(first.value, ChannelId(1), audio)
        settings.useProfile(second)
        for (channel in 1L..257L) {
            settings.channelAspects.set(second.value, ChannelId(channel), AspectRatioMode.FORCE_16_9)
            settings.audioChoices.write(second.value, ChannelId(channel), audio)
        }
        assertEquals(AspectRatioMode.FIT, settings.channelAspects.choices.first().modeFor(ChannelId(1)))
        assertNull(settings.audioChoices.read(second.value, ChannelId(1)))
        settings.useProfile(first)
        assertEquals(AspectRatioMode.FORCE_4_3, settings.channelAspects.choices.first().modeFor(ChannelId(1)))
        assertEquals(audio, settings.audioChoices.read(first.value, ChannelId(1)))
    }
}
