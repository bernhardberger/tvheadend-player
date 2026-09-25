package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import java.io.File
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
    @Test fun englishLargeLanguagePicker() = settings("en", 1.3f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanSettings() = settings("de", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeLanguagePicker() = settings("de", 1.3f)
    @Test fun englishAutomaticAudio() = audio("en")
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanAutomaticAudio() = audio("de")

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
                PlaybackOptionsSheet(PlaybackOptionsPage.AUDIO, player, false, AspectRatioMode.FIT, false,
                    {}, {}, {}, audioAutomatic = automatic, onAutomaticAudio = { choices++; automatic = true })
            }
        }
        compose.runOnIdle { assertEquals(1, listeners.size) }
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused().assertIsNotSelected()
        key(Key.DirectionCenter)
        assertEquals(1, choices)
        compose.onNodeWithTag("playback-options-track-automatic").assertIsSelected()
        compose.runOnIdle { mounted = false }
        compose.waitForIdle()
        assertTrue(listeners.isEmpty())
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun settings(locale: String, scale: Float) {
        var ui by mutableStateOf(SettingsPlayerUiState(audioLanguages = listOf("en")))
        var selections = 0
        lateinit var view: View
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, scale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    val navigation = rememberDepthNavigationState("root")
                    val levels = listOf(
                        settingsLevel("root", "Settings", listOf(settingsRow("player", "Open player", child = SettingsSection.PLAYER.name))),
                        settingsPlayerLevel(ui, {}, {}, {}, {}, { ui = ui.copy(audioDescription = it) }),
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
        key(Key.DirectionCenter)
        repeat(3) { key(Key.DirectionDown) }
        compose.onNodeWithText(first).assertIsFocused()
        capture(view, "$locale-player-font$scale")
        key(Key.DirectionCenter)
        compose.onNodeWithText(if (locale == "de") "Englisch" else "English").assertIsFocused().assertIsSelected()
        capture(view, "$locale-language-font$scale")
        assertEquals(0, selections)
        key(Key.Back)
        compose.onNodeWithText(first).assertIsFocused()
        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        assertEquals(0, selections)
        key(Key.DirectionCenter)
        assertEquals(1, selections)
        compose.onNodeWithText(first).assertIsFocused()
        key(Key.DirectionDown) // second slot is available, third is not yet available
        compose.onNodeWithText(if (locale == "de") "Zweite Audiosprache" else "Second audio language").assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithText(format).assertIsFocused()
        key(Key.DirectionCenter)
        compose.onNodeWithText(if (locale == "de") "Automatisch" else "Automatic").assertIsFocused().assertIsSelected()
        capture(view, "$locale-format-font$scale")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(AudioFormatPreference.PREFER_DOLBY, ui.audioFormat)
        compose.onNodeWithText(format).assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithText(description).assertIsFocused().assertIsOff()
        key(Key.DirectionCenter)
        compose.onNodeWithText(description).assertIsFocused().assertIsOn()
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        compose.onNodeWithText(if (locale == "de") "Untertiteleinstellungen des Systems verwenden" else "Follow system caption settings").assertIsFocused().assertIsSelected()
        key(Key.Back)
        assertEquals(2, selections)

        // A fully populated configuration exercises the progressive third row and scroll geometry.
        compose.runOnIdle { ui = ui.copy(audioLanguages = listOf("en", "de", "fr")) }
        key(Key.DirectionUp)
        key(Key.DirectionDown)
        compose.onNodeWithText(if (locale == "de") "Untertitelsprache" else "Subtitle Language").assertIsFocused()
        capture(view, "$locale-player-subtitle-default-font$scale")
        key(Key.DirectionCenter)
        compose.onNodeWithText(if (locale == "de") "Untertiteleinstellungen des Systems verwenden" else "Follow system caption settings")
            .assertIsFocused().assertIsSelected()
        capture(view, "$locale-subtitle-language-font$scale")
        key(Key.Back)
        key(Key.DirectionUp)
        compose.onNodeWithText(description).assertIsFocused()
        capture(view, "$locale-player-three-languages-description-font$scale")
        // The longer player list may scroll the third row out while description has focus; it stays reachable.
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        compose.onNodeWithText(if (locale == "de") "Dritte Audiosprache" else "Third audio language").assertIsFocused().assertIsDisplayed()
    }

    private fun audio(locale: String) {
        var page by mutableStateOf(PlaybackOptionsPage.AUDIO)
        var automatic by mutableStateOf(true)
        var automaticCalls = 0
        var explicitCalls = 0
        var tracks by mutableStateOf(listOf(PlaybackOptionTrack("de", "Deutsch · AC-3", "", true)))
        lateinit var view: View
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    PlaybackOptionsSheetContent(page, tracks, emptyList(), false, AspectRatioMode.FIT, false,
                        { page = it }, { explicitCalls++; automatic = false }, {}, {}, {},
                        audioAutomatic = automatic, onAutomaticAudio = { automaticCalls++; automatic = true })
                }
            }
        }
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("playback-options-track-de").assertIsNotSelected()
        capture(view, "$locale-audio-automatic")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(1, explicitCalls)
        compose.onNodeWithTag("playback-options-track-de").assertIsSelected()
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        assertEquals(1, automaticCalls)
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused().assertIsSelected()
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("playback-options-audio").assertIsFocused()
        compose.runOnIdle { tracks = emptyList() }
        key(Key.DirectionCenter)
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused()
        key(Key.DirectionCenter)
        assertEquals(2, automaticCalls)
    }

    private fun capture(view: View, name: String) = compose.runOnIdle {
        assertEquals(1920, view.width)
        assertEquals(1080, view.height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/outputs/audio-preference-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
