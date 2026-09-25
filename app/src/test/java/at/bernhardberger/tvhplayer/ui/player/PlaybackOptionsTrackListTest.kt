package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class PlaybackOptionsTrackListTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun televisionFeature() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
    }

    @Test fun english() = trackList("en", 1f, ENGLISH)

    @Test fun englishLargeText() = trackList("en", 1.3f, ENGLISH)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun german() = trackList("de", 1f, GERMAN)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeText() = trackList("de", 1.3f, GERMAN)

    private class RowTexts(
        val language: String,
        val unknownLanguage: String,
        val clearDialogue: String,
        val audioDescription: String,
    )

    private fun trackList(locale: String, fontScale: Float, texts: RowTexts) {
        lateinit var view: View
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    PlaybackOptionsSheet(
                        PlaybackOptionsPage.AUDIO, player(orfTracks()), false, AspectRatioMode.FIT, false,
                        {}, {}, {}, audioAutomatic = true, onAutomaticAudio = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused()
        val stereo = "Stereo · MPEG-1 Layer II"
        val expectedLines = mapOf(
            "main" to listOf(texts.language, stereo),
            "surround" to listOf(texts.language, "5.1 · Dolby Digital"),
            "second" to listOf(texts.unknownLanguage, stereo),
            "clear" to listOf(texts.clearDialogue, stereo),
            "described" to listOf(texts.language, texts.audioDescription, stereo),
        )
        // The fixed 236 dp list showed three rows; the list now uses the panel height.
        val rowsInView = if (fontScale > 1f) 4 else 5
        (listOf("automatic") + TRACK_IDS).take(rowsInView).forEach(::assertRowFullyInList)
        capture(view, "$locale-font$fontScale-top")

        TRACK_IDS.forEach { id ->
            key(Key.DirectionDown)
            compose.onNodeWithTag(rowTag(id)).assertIsFocused()
            assertRowFullyInList(id)
            assertEquals("Track $id lines", expectedLines.getValue(id), rowTextNodes(id).map { it.text() })
            // What a screen reader announces for the focusable row.
            val announced = compose.onNodeWithTag(rowTag(id)).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertTrue("Track $id announces $announced", announced.containsAll(expectedLines.getValue(id)))
            assertNoLineCut(id)
        }
        compose.onAllNodes(hasText("Hz", substring = true), useUnmergedTree = true).assertCountEquals(0)
        capture(view, "$locale-font$fontScale-bottom")
    }

    private fun rowTextNodes(id: String): List<SemanticsNode> {
        fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.descendants() }
        return compose.onNodeWithTag(rowTag(id), useUnmergedTree = true).fetchSemanticsNode()
            .descendants()
            .filter { SemanticsActions.GetTextLayoutResult in it.config }
            .sortedBy { it.boundsInRoot.top }
    }

    private fun SemanticsNode.text(): String =
        config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("")

    private fun assertNoLineCut(id: String) {
        rowTextNodes(id).forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
            val layout = results.single()
            assertFalse(
                "Track $id line is cut: ${layout.layoutInput.text}",
                layout.isLineEllipsized(layout.lineCount - 1),
            )
        }
    }

    private fun assertRowFullyInList(id: String) {
        val list = compose.onNodeWithTag("playback-options-track-list").getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag(rowTag(id)).getUnclippedBoundsInRoot()
        assertTrue("Row $id $row is outside the list $list", row.within(list))
    }

    private fun DpRect.within(outer: DpRect): Boolean =
        top >= outer.top && bottom <= outer.bottom && left >= outer.left && right <= outer.right

    // Track keys are "<track type>:<group id>:<format id>"; audio is track type 1.
    private fun rowTag(id: String) =
        if (id == "automatic") "playback-options-track-automatic" else "playback-options-track-1:$id:$id"

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun capture(view: View, name: String) = compose.runOnIdle {
        assertEquals(1920, view.width)
        assertEquals(1080, view.height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/outputs/playback-options-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private companion object {
        val ENGLISH = RowTexts("German", "Unknown language", "Clear dialogue", "Audio description")
        val GERMAN = RowTexts("Deutsch", "Unbekannte Sprache", "Klare Sprache", "Audiodeskription")
        val TRACK_IDS = listOf("main", "surround", "second", "clear", "described")

        // ORF1 HD in September 2026 as seen on the test TV, plus an audio description
        // track with a known language to cover the overline.
        fun orfTracks() = Tracks(
            listOf(
                audio("main", "de", MimeTypes.AUDIO_MPEG_L2, 2, C.ROLE_FLAG_MAIN, selected = true),
                audio("surround", "de", MimeTypes.AUDIO_AC3, 6, 0),
                audio("second", "mul", MimeTypes.AUDIO_MPEG_L2, 2, 0),
                audio("clear", "qaa", MimeTypes.AUDIO_MPEG_L2, 2, C.ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY),
                audio("described", "de", MimeTypes.AUDIO_MPEG_L2, 2, C.ROLE_FLAG_DESCRIBES_VIDEO),
            ),
        )

        fun audio(id: String, language: String, mime: String, channels: Int, roles: Int, selected: Boolean = false) =
            Tracks.Group(
                TrackGroup(
                    id,
                    Format.Builder()
                        .setId(id)
                        .setSampleMimeType(mime)
                        .setLanguage(language)
                        .setChannelCount(channels)
                        .setSampleRate(48_000)
                        .setRoleFlags(roles)
                        .build(),
                ),
                false,
                intArrayOf(C.FORMAT_HANDLED),
                booleanArrayOf(selected),
            )

        fun player(tracks: Tracks): Player = java.lang.reflect.Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getCurrentTracks" -> tracks
                "getTrackSelectionParameters" -> TrackSelectionParameters.DEFAULT
                "addListener", "removeListener" -> Unit
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TrackListTestPlayer"
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
    }
}
