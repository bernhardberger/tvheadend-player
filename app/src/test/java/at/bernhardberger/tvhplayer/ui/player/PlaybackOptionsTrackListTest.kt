package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.di.PlayerStatsLevel
import at.bernhardberger.tvhplayer.testutil.changeState
import at.bernhardberger.tvhplayer.testutil.pressKey
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ListTopFadeVisible
import at.bernhardberger.tvhplayer.ui.components.ListBottomFadeVisible
import at.bernhardberger.tvhplayer.ui.components.PanelListTopFade
import at.bernhardberger.tvhplayer.ui.components.PanelListBottomFade
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

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeText() = trackList("de", 1.3f, GERMAN)

    /**
     * The TV pivot scroll already keeps focus high in the list; a minimal scroll would
     * park it on the bottom edge. The panel's own inset keeps it above the band either way.
     */
    @Test fun englishLargeTextMinimalScroll() = trackList("en", 1.3f, ENGLISH, minimalScroll = true)

    /**
     * The page lands once, on the checked row even when tracks arrive after it opened. A
     * track switch then passes through states with no or another checked track; focus stays
     * on the row it is on and moves only when that row goes away.
     */
    @Test fun audioPageFocusStaysOnItsRowThroughATrackSwitch() {
        var audio by mutableStateOf(emptyList<PlaybackOptionTrack>())
        var resolving by mutableStateOf(true)
        showOptions(PlaybackOptionsPage.AUDIO, audio = { audio }, subtitles = { emptyList() }, resolving = { resolving })
        assertFocused("automatic")

        compose.changeState {
            audio = choices("a", "b", "c", selected = "b")
            resolving = false
        }
        assertFocused("b")

        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        assertEquals("c", chosen)
        listOf(null, "a", "c").forEach { selected ->
            compose.changeState { audio = choices("a", "b", "c", selected = selected) }
            assertFocused("c")
        }
        compose.changeState { audio = choices("a", "b", selected = "a") }
        assertFocused("a")
    }

    @Test fun subtitlesPageFocusStaysOnItsRowThroughATrackSwitch() {
        var subtitles by mutableStateOf(choices("x", "y", selected = "x"))
        showOptions(PlaybackOptionsPage.SUBTITLES, audio = { emptyList() }, subtitles = { subtitles }, resolving = { false })
        assertFocused("x")

        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        assertEquals("y", chosen)
        listOf(null, "x", "y").forEach { selected ->
            compose.changeState { subtitles = choices("x", "y", selected = selected) }
            assertFocused("y")
        }
    }

    @Test fun subtitleDefaultOffAndTracksHaveOneCheckedRowAndStayInDpadOrder() {
        var default by mutableStateOf(true)
        var off by mutableStateOf(false)
        var selected by mutableStateOf<String?>("x")
        var defaults = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                PlaybackOptionsSheetContent(
                    PlaybackOptionsPage.SUBTITLES, emptyList(), choices("x", "y", selected = selected),
                    false, AspectRatioMode.FIT, PlayerStatsLevel.OFF,
                    onPageChange = {}, onAudioTrackSelected = {},
                    onSubtitleTrackSelected = { default = false; off = it == null; selected = it },
                    onAspectRatioChange = {}, onStatsLevelChange = {},
                    subtitlesDefault = default, subtitlesOff = off,
                    onDefaultSubtitles = { defaults++; default = true; off = false },
                    subtitleLanguage = "de",
                )
            }
        }
        val defaultRow = compose.onNodeWithTag("playback-options-subtitles-default")
        defaultRow.assertIsFocused().assertIsSelected().assert(hasText("Default (German)"))
        compose.onNodeWithTag("playback-options-track-x").assertIsNotSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("playback-options-subtitles-off").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("playback-options-subtitles-off").assertIsSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.pressKey(Key.DirectionDown)
        assertFocused("x")
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("playback-options-track-x").assertIsSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.pressKey(Key.DirectionDown)
        assertFocused("y")
        repeat(3) { compose.pressKey(Key.DirectionUp) }
        defaultRow.assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, defaults)
        defaultRow.assertIsSelected().assertIsFocused()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
    }

    @Test fun subtitleDefaultLabelsSystemCaptionsAndHeaderStillNamesThePlayingTrack() {
        var defaults = 0
        val tracks = Tracks(listOf(Tracks.Group(
            TrackGroup("text", Format.Builder().setId("subtitles")
                .setSampleMimeType(MimeTypes.APPLICATION_DVBSUBS).setLanguage("de").build()),
            false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true),
        )))
        compose.setContent {
            TVHeadendPlayerTheme {
                PlaybackOptionsSheet(
                    PlaybackOptionsPage.SUBTITLES, player(tracks), false, AspectRatioMode.FIT, PlayerStatsLevel.OFF,
                    {}, {}, {}, audioAutomatic = true, onAutomaticAudio = {}, subtitlesDefault = true,
                    onDefaultSubtitles = { defaults++ },
                )
            }
        }
        compose.onNodeWithTag("playback-options-subtitles-default")
            .assertIsFocused().assertIsSelected().assert(hasText("Default (System captions)"))
        compose.onAllNodes(hasText("German")).assertCountEquals(2) // header and track row
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, defaults)
    }

    @Test fun subtitleQuickListBackQueuesDefaultEvenBeforeThePreviewReachesThePlayer() {
        val quickList = PlaybackQuickListSignals()
        val requests = mutableListOf<Pair<Boolean, Long?>>()
        compose.setContent {
            TVHeadendPlayerTheme {
                PlaybackOptionsSheet(
                    PlaybackOptionsPage.SUBTITLES,
                    player(Tracks(listOf(audio("text", "en", MimeTypes.TEXT_VTT, 0, 0)))),
                    false, AspectRatioMode.FIT, PlayerStatsLevel.OFF, {}, {}, {},
                    audioAutomatic = true, onAutomaticAudio = {}, subtitlesDefault = true,
                    quickList = quickList, quickListTargetEpoch = 7L,
                    onSubtitleSelection = { _, off, epoch -> requests += off to epoch },
                )
            }
        }
        compose.onNodeWithTag("playback-options-subtitles-default").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.mainClock.advanceTimeBy(PLAYBACK_QUICK_LIST_SETTLE_MS + 32)
        compose.waitForIdle()
        assertEquals(listOf(true to 7L), requests)
        compose.runOnIdle { quickList.restoreStart() }
        assertEquals(listOf(true to 7L, false to 7L), requests)
    }

    /**
     * A recording without marked ad breaks shows the row dimmed and focus passes it by; once
     * breaks are marked it opens on the recording's own choice, and its first row goes back
     * to the setting.
     */
    @Test fun adBreaksRowOpensOnlyWithMarkedBreaksAndItsFirstRowFollowsTheSettingAgain() {
        var page by mutableStateOf(PlaybackOptionsPage.ROOT)
        val selections = mutableListOf<AdBreakMode?>()
        fun option(marked: Boolean) =
            RecordingAdBreakOption(marked, AdBreakMode.ASK, AdBreakMode.AUTOMATIC, select = { selections += it })
        var adBreaks by mutableStateOf(option(marked = false))
        compose.setContent {
            TVHeadendPlayerTheme {
                PlaybackOptionsSheetContent(
                    page, emptyList(), emptyList(), false, AspectRatioMode.FIT, PlayerStatsLevel.OFF,
                    onPageChange = { page = it }, onAudioTrackSelected = {}, onSubtitleTrackSelected = {},
                    onAspectRatioChange = {}, onStatsLevelChange = {}, recording = true, adBreaks = adBreaks,
                )
            }
        }
        val row = "playback-options-ad-breaks"
        compose.onNodeWithTag(row).assertIsNotEnabled().assert(hasText("None marked"))
        compose.onNodeWithTag("playback-options-audio").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("playback-options-stats").assertIsFocused()

        compose.changeState { adBreaks = option(marked = true) }
        compose.onNodeWithTag(row).assertIsEnabled().assert(hasText("Skip automatically"))
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag(row).assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(PlaybackOptionsPage.AD_BREAKS, page)
        compose.onNodeWithTag("$row-AUTOMATIC").assertIsFocused()
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("$row-DEFAULT").assertIsFocused().assert(hasText("Default (Ask)"))
        compose.pressKey(Key.DirectionCenter)
        assertEquals(listOf<AdBreakMode?>(null), selections)
    }

    @Test fun liveStreamProfilesFollowServerOrderAndDefaultAndDisappearFromRecordings() {
        val observation = SessionObservation.create(
            SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            ChannelRepositoryState.Current(ChannelCatalog.create()),
            EpgRepositoryState.Current(EpgSnapshot.create()),
            DvrRepositoryState.Current(DvrSnapshot.create()),
        )
        val profiles = (listOf("htsp", "A long server profile name") + (3..18).map { "Profile $it" }).mapIndexed { index, name ->
            StreamProfile(StreamProfileId((index + 1).toString().padStart(32, '0')), name, "")
        }
        val available = StreamProfilesResult.Available.create(profiles, requireNotNull(observation.currentSession))
        var result by mutableStateOf<StreamProfilesResult>(StreamProfilesResult.Failed(TvheadendFailure.NOT_READY))
        var choice by mutableStateOf<String?>(profiles[1].id.value)
        var recording by mutableStateOf(false)
        var page by mutableStateOf(PlaybackOptionsPage.ROOT)
        val picks = mutableListOf<StreamProfileId?>()
        compose.setContent {
            TVHeadendPlayerTheme {
                PlaybackOptionsSheetContent(
                    page, emptyList(), emptyList(), false, AspectRatioMode.FIT, PlayerStatsLevel.OFF,
                    { page = it }, {}, {}, {}, {}, recording = recording,
                    streamProfile = LiveStreamProfileOption(result, true, profiles[0].id, choice) {
                        picks += it; choice = it?.value
                    },
                )
            }
        }
        val root = "playback-options-stream-profile"
        compose.onNodeWithTag(root).assertIsNotEnabled()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("playback-options-display").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("playback-options-stats").assertIsFocused()
        compose.changeState { result = available }
        compose.onNodeWithTag(root).assertIsEnabled().assert(hasText(profiles[1].name))
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag(root).assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("$root-${profiles[1].id.value}").assertIsFocused().assertIsSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("$root-${profiles[0].id.value}").assertIsFocused().assert(hasText("htsp"))
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("$root-default").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(listOf<StreamProfileId?>(null), picks)
        compose.onNodeWithTag("$root-default").assertIsFocused().assertIsSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.changeState { choice = profiles.last().id.value; page = PlaybackOptionsPage.ROOT }
        compose.onNodeWithTag(root).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("$root-${profiles.last().id.value}").assertIsFocused().assertIsSelected()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("$root-${profiles.last().id.value}").assertIsFocused()
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("$root-${profiles[profiles.lastIndex - 1].id.value}").assertIsFocused()
        compose.changeState { result = StreamProfilesResult.Failed(TvheadendFailure.NOT_READY) }
        compose.onNodeWithTag("$root-default").assertDoesNotExist()
        assertEquals(PlaybackOptionsPage.ROOT, page)
        compose.onNodeWithTag(root).assertIsNotEnabled()
        compose.changeState { result = available; choice = null }
        compose.onNodeWithTag(root).assert(hasText("Default (Direct streaming)"))
        compose.changeState { recording = true }
        compose.onNodeWithTag(root).assertDoesNotExist()
    }

    private var chosen: String? = null

    private fun showOptions(
        page: PlaybackOptionsPage,
        audio: () -> List<PlaybackOptionTrack>,
        subtitles: () -> List<PlaybackOptionTrack>,
        resolving: () -> Boolean,
    ) = compose.setContent {
        TVHeadendPlayerTheme {
            PlaybackOptionsSheetContent(
                page, audio(), subtitles(), resolving(), AspectRatioMode.FIT, PlayerStatsLevel.OFF,
                onPageChange = {}, onAudioTrackSelected = { chosen = it }, onSubtitleTrackSelected = { chosen = it },
                onAspectRatioChange = {}, onStatsLevelChange = {},
            )
        }
    }

    private fun choices(vararg keys: String, selected: String?) =
        keys.map { PlaybackOptionTrack(it, "Track $it", selected = it == selected) }

    private fun assertFocused(key: String) = compose.onNodeWithTag("playback-options-track-$key").assertIsFocused()

    private class RowTexts(
        val language: String,
        val multipleLanguages: String,
        val clearDialogue: String,
        val audioDescription: String,
    )

    @OptIn(ExperimentalFoundationApi::class)
    private fun trackList(locale: String, fontScale: Float, texts: RowTexts, minimalScroll: Boolean = false) {
        lateinit var view: View
        val platformSpec: Array<ProvidedValue<*>> =
            if (minimalScroll) arrayOf(LocalBringIntoViewSpec provides object : BringIntoViewSpec {}) else emptyArray()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale), *platformSpec) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    PlaybackOptionsSheet(
                        PlaybackOptionsPage.AUDIO, player(orfTracks()), false, AspectRatioMode.FIT, PlayerStatsLevel.OFF,
                        {}, {}, {}, audioAutomatic = true, onAutomaticAudio = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused()
        val stereo = "Stereo • MPEG-1 Layer II"
        val expectedLines = mapOf(
            "main" to listOf(texts.language, stereo),
            "surround" to listOf(texts.language, "5.1 • Dolby Digital"),
            "second" to listOf(texts.multipleLanguages, stereo),
            "clear" to listOf(texts.clearDialogue, stereo),
            "described" to listOf(texts.language, texts.audioDescription, stereo),
        )
        // The fixed 236 dp list showed three rows; the list now uses the panel height.
        val rowsInView = if (fontScale > 1f) 4 else 5
        (listOf("automatic") + TRACK_IDS).take(rowsInView).forEach(::assertRowFullyInList)
        assertFade(focusedId = "automatic")
        assertTrue("The ORF list continues below the panel, so its band shows", bottomFade())
        val name = "audio-menu-$locale-font$fontScale" + if (minimalScroll) "-minimal-scroll" else ""
        capture(view, "$name-top")

        TRACK_IDS.forEach { id ->
            compose.pressKey(Key.DirectionDown)
            compose.onNodeWithTag(rowTag(id)).assertIsFocused()
            assertRowFullyInList(id)
            assertFade(focusedId = id)
            assertEquals("Track $id lines", expectedLines.getValue(id), rowTextNodes(id).map { it.text() })
            // What a screen reader announces for the focusable row.
            val announced = compose.onNodeWithTag(rowTag(id)).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertTrue("Track $id announces $announced", announced.containsAll(expectedLines.getValue(id)))
            assertNoLineCut(id)
        }
        // The last row is focused: the list has reached its end and shows it unfaded.
        assertFalse("The last row is faded", bottomFade())
        compose.onAllNodes(hasText("Hz", substring = true), useUnmergedTree = true).assertCountEquals(0)
        capture(view, "$name-bottom")

        // Back up: the top fade shows while the list can scroll back and never covers focus.
        (listOf("automatic") + TRACK_IDS).dropLast(1).reversed().forEach { id ->
            compose.pressKey(Key.DirectionUp)
            compose.onNodeWithTag(rowTag(id)).assertIsFocused()
            assertRowFullyInList(id)
            assertFade(focusedId = id)
        }
        assertFalse("The first row is faded", topFade())
    }

    private fun topFade(): Boolean =
        compose.onNodeWithTag("playback-options-track-list").fetchSemanticsNode().config[ListTopFadeVisible]

    private fun bottomFade(): Boolean =
        compose.onNodeWithTag("playback-options-track-list").fetchSemanticsNode().config[ListBottomFadeVisible]

    /**
     * The bottom band shows exactly while the list can scroll forward, the top fade
     * while it can scroll back, and the focused row is never inside either.
     */
    private fun assertFade(focusedId: String) {
        val list = compose.onNodeWithTag("playback-options-track-list")
        val range = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("Bottom band at $focusedId", range.value() < range.maxValue(), bottomFade())
        assertEquals("Top fade at $focusedId", range.value() > 0f, topFade())
        val bounds = list.getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag(rowTag(focusedId)).getUnclippedBoundsInRoot()
        if (bottomFade()) {
            val bandTop = bounds.bottom - PanelListBottomFade
            assertTrue("Focused row $focusedId $row reaches into the band below $bandTop", row.bottom <= bandTop)
        }
        if (topFade()) {
            val fadeBottom = bounds.top + PanelListTopFade
            assertTrue("Focused row $focusedId $row reaches into the fade above $fadeBottom", row.top >= fadeBottom)
        }
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

    private fun capture(view: View, name: String) = compose.runOnIdle {
        assertEquals(1920, view.width)
        assertEquals(1080, view.height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        CaptureOutput.writePng(File("build/outputs/playback-panel-captures", "$name.png"), bitmap)
        bitmap.recycle()
    }

    internal companion object {
        private val ENGLISH = RowTexts("German", "Multiple languages", "Clear dialogue", "Audio description")
        private val GERMAN = RowTexts("Deutsch", "Mehrsprachig", "Klare Sprache", "Audiodeskription")
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
