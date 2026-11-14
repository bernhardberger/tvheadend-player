package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.di.PlayerStatsLevel

import at.bernhardberger.tvhplayer.testutil.pressKey
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.settings.AudioFormatPreference
import at.bernhardberger.tvhplayer.settings.audioLanguagesWithSlot
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.player.PlaybackOptionTrack
import at.bernhardberger.tvhplayer.ui.player.PlaybackOptionsSheetContent
import at.bernhardberger.tvhplayer.ui.player.PlaybackOptionsSheet
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class AudioPreferenceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun englishSettings() = settings("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeLanguagePicker() = settings("de", 1.3f)
    @Test fun englishAutomaticAudio() = audio()

    @Test fun mountedSheetNeedsNoKoinAndObservesTracksWithOneListener() {
        val listeners = mutableSetOf<androidx.media3.common.Player.Listener>()
        val player = java.lang.reflect.Proxy.newProxyInstance(
            androidx.media3.common.Player::class.java.classLoader,
            arrayOf(androidx.media3.common.Player::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getCurrentTracks" -> androidx.media3.common.Tracks.EMPTY
                "getTrackSelectionParameters" -> androidx.media3.common.TrackSelectionParameters.DEFAULT
                "addListener" -> { listeners.add(args!![0] as androidx.media3.common.Player.Listener); Unit }
                "removeListener" -> { listeners.remove(args!![0] as androidx.media3.common.Player.Listener); Unit }
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "OptionsTestPlayer"
                else -> error("Unexpected player call: ${method.name}")
            }
        } as androidx.media3.common.Player
        var mounted by mutableStateOf(true)
        var automatic by mutableStateOf(false)
        var choices = 0
        compose.setContent {
            if (mounted) TVHeadendPlayerTheme {
                PlaybackOptionsSheet(PlaybackOptionsPage.AUDIO, player, false, AspectRatioMode.FIT, PlayerStatsLevel.OFF,
                    {}, {}, {}, audioAutomatic = automatic, onAutomaticAudio = { choices++; automatic = true })
            }
        }
        compose.runOnIdle { assertEquals(1, listeners.size) }
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused().assertIsNotSelected()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, choices)
        compose.onNodeWithTag("playback-options-track-automatic").assertIsSelected()
        compose.runOnIdle { mounted = false }
        compose.waitForIdle()
        assertTrue(listeners.isEmpty())
    }

    private fun settings(locale: String, scale: Float) {
        var ui by mutableStateOf(SettingsPlayerUiState(audioLanguages = listOf("en")))
        var selections = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, scale)) {
                TVHeadendPlayerTheme {
                    val navigation = rememberDepthNavigationState("root")
                    val levels = listOf(
                        settingsLevel("root", "Settings", listOf(settingsRow("audio", "Open audio", child = SettingsSection.AUDIO.name))),
                        settingsAudioLevel(ui, {}, { ui = ui.copy(audioDescription = it) }),
                    ) + settingsAudioPreferenceLevels(ui,
                        { slot, language -> selections++; ui = ui.copy(audioLanguages = audioLanguagesWithSlot(ui.audioLanguages, slot, language)); navigation.pop() },
                        { selections++; ui = ui.copy(audioFormat = it); navigation.pop() },
                        { selections++; ui = ui.copy(subtitleLanguage = it); navigation.pop() })
                    Box(Modifier.fillMaxSize()) { SettingsScreenNavigation(navigation, levels) }
                }
            }
        }
        val first = if (locale == "de") "Audiosprache" else "Audio language"
        val format = if (locale == "de") "Audioformat" else "Audio format"
        val description = if (locale == "de") "Audiodeskription" else "Audio description"
        val passthrough = if (locale == "de") "Audio-Passthrough" else "Audio passthrough"
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithText(first).assertIsFocused() // The page opens on its first row.
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithText(if (locale == "de") "Englisch" else "English").assertIsFocused().assertIsSelected()
        assertEquals(0, selections)
        compose.pressKey(Key.Back)
        compose.onNodeWithText(first).assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        assertEquals(0, selections)
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, selections)
        compose.onNodeWithText(first).assertIsFocused()
        compose.pressKey(Key.DirectionDown) // second slot is available, third is not yet available
        compose.onNodeWithText(if (locale == "de") "Zweite Audiosprache" else "Second audio language").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithText(format).assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithText(if (locale == "de") "Automatisch" else "Automatic").assertIsFocused().assertIsSelected()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        assertEquals(AudioFormatPreference.PREFER_DOLBY, ui.audioFormat)
        compose.onNodeWithText(format).assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithText(passthrough).assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithText(description).assertIsFocused().assertIsOff()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithText(description).assertIsFocused().assertIsOn()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithText(if (locale == "de") "Untertiteleinstellungen des Systems verwenden" else "Follow system caption settings").assertIsFocused().assertIsSelected()
        compose.pressKey(Key.Back)
        assertEquals(2, selections)

        // A fully populated configuration exercises the progressive third row and scroll geometry.
        compose.runOnIdle { ui = ui.copy(audioLanguages = listOf("en", "de", "fr")) }
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithText(if (locale == "de") "Untertitelsprache" else "Subtitle language").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithText(if (locale == "de") "Untertiteleinstellungen des Systems verwenden" else "Follow system caption settings")
            .assertIsFocused().assertIsSelected()
        compose.pressKey(Key.Back)
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithText(description).assertIsFocused()
        // The longer list may scroll the third row out while description has focus; it stays reachable.
        repeat(3) { compose.pressKey(Key.DirectionUp) } // Audio passthrough, Audio format, Third audio language
        compose.onNodeWithText(if (locale == "de") "Dritte Audiosprache" else "Third audio language").assertIsFocused().assertIsDisplayed()
    }

    private fun audio() {
        var page by mutableStateOf(PlaybackOptionsPage.AUDIO)
        var automatic by mutableStateOf(true)
        var automaticCalls = 0
        var explicitCalls = 0
        var tracks by mutableStateOf(listOf(PlaybackOptionTrack("de", "Deutsch • AC-3", "", true)))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    PlaybackOptionsSheetContent(page, tracks, emptyList(), false, AspectRatioMode.FIT, PlayerStatsLevel.OFF,
                        { page = it }, { explicitCalls++; automatic = false }, {}, {}, {},
                        audioAutomatic = automatic, onAutomaticAudio = { automaticCalls++; automatic = true })
                }
            }
        }
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("playback-options-track-de").assertIsNotSelected()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, explicitCalls)
        compose.onNodeWithTag("playback-options-track-de").assertIsSelected()
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, automaticCalls)
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused().assertIsSelected()
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("playback-options-audio").assertIsFocused()
        compose.runOnIdle { tracks = emptyList() }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(2, automaticCalls)
    }

}
