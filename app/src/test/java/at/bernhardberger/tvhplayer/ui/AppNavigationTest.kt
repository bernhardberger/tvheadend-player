package at.bernhardberger.tvhplayer.ui

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun inventoryContainsEveryProductionDestinationAndSettingsSection() {
        val keys = setOf<AppNavKey>(
            ChannelsKey,
            GuideKey,
            RecordingsKey,
            SettingsKey(SettingsSection.GENERAL),
            UnlockKey,
            LivePlayerKey(channelId = 42, channelName = "News"),
            RecordingPlayerKey(
                recordingId = 7,
                start = RecordingStartMode.START_OVER,
            ),
        )
        assertEquals(AppDestination.entries.toSet(), keys.mapTo(mutableSetOf()) { it.destination })
        assertEquals(
            setOf(
                SettingsSection.GENERAL,
                SettingsSection.CHANNEL_TAGS,
                SettingsSection.CONNECTION,
                SettingsSection.PLAYER,
                SettingsSection.APPLIANCE,
            ),
            SettingsSection.entries.toSet(),
        )
    }

    @Test
    fun parameterizedPlayerKeysRetainTypedState() {
        assertEquals(
            LivePlayerKey(channelId = 42, channelName = "News / HD"),
            LivePlayerKey(channelId = 42, channelName = "News / HD"),
        )
        assertEquals(
            RecordingStartMode.RESUME,
            RecordingPlayerKey(recordingId = 7).start,
        )
    }

    @Test
    fun everyRouteShapeRoundTripsThroughTheNavigationSerializer() {
        val routes = listOf<AppNavKey>(
            ChannelsKey,
            GuideKey,
            RecordingsKey,
            SettingsKey(SettingsSection.PLAYER),
            UnlockKey,
            LivePlayerKey(channelId = 42, channelName = "News / HD"),
            RecordingPlayerKey(recordingId = 7, start = RecordingStartMode.START_OVER),
        )
        val serializer = ListSerializer(AppNavKey.serializer())

        assertEquals(
            routes,
            Json.decodeFromString(serializer, Json.encodeToString(serializer, routes)),
        )
    }

    @Test
    fun invalidPlayerIdentifiersAreRejectedBeforeNavigationOrRestoration() {
        assertThrows(IllegalArgumentException::class.java) {
            LivePlayerKey(channelId = 0, channelName = "Invalid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecordingPlayerKey(recordingId = -1)
        }
        val encoded = Json.encodeToString(
            LivePlayerKey.serializer(),
            LivePlayerKey(channelId = 42, channelName = "News"),
        ).replace("\"channelId\":42", "\"channelId\":0")
        assertThrows(IllegalArgumentException::class.java) {
            Json.decodeFromString(LivePlayerKey.serializer(), encoded)
        }
    }

    @Test
    fun topLevelNavigationRetainsSiblingStateWithoutCreatingBackHistory() {
        val stack = mutableListOf<AppNavKey>(
            ChannelsKey,
            GuideKey,
            SettingsKey(SettingsSection.PLAYER),
        )

        stack.navigateTopLevel(GuideKey)

        assertEquals(GuideKey, stack.last())
        assertEquals(1, stack.count { it == GuideKey })
        assertEquals(SettingsSection.PLAYER, stack.lastSettingsSection())
        assertFalse(stack.hasTransientDestinationBelowTop())
    }

    @Test
    fun topLevelNavigationDiscardsTransientDestinationsBeforeSelectingSibling() {
        val settings = SettingsKey(SettingsSection.PLAYER)
        val stack = mutableListOf<AppNavKey>(
            ChannelsKey,
            settings,
            LivePlayerKey(channelId = 42, channelName = "News"),
            UnlockKey,
        )

        stack.navigateTopLevel(ChannelsKey)

        assertEquals(listOf(settings, ChannelsKey), stack)
        assertFalse(stack.hasTransientDestinationBelowTop())
    }

    @Test
    fun transientDestinationsPushPopAndRootReplacementIsAtomic() {
        val stack = mutableListOf<AppNavKey>(ChannelsKey, GuideKey)
        val player = LivePlayerKey(channelId = 42, channelName = "News")

        stack.pushTransient(player)
        stack.pushTransient(UnlockKey)
        assertTrue(stack.hasTransientDestinationBelowTop())
        assertEquals(UnlockKey, stack.removeLast())
        assertEquals(player, stack.removeLast())

        stack.replaceRoot(SettingsKey(SettingsSection.CONNECTION))
        assertEquals(
            listOf(SettingsKey(SettingsSection.CONNECTION)),
            stack,
        )
    }
}
