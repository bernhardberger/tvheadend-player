package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@OptIn(ExperimentalTestApi::class)
class PlaybackOptionsSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rootAndDetailRestoreSemanticFocusAndContainEveryEdge() {
        var page by mutableStateOf(PlaybackOptionsPage.ROOT)
        setOptionsContent(
            page = { page },
            onPageChange = { page = it },
            subtitleTracks = listOf(
                track("sub-en", "English"),
                track("sub-de", "Deutsch", selected = true),
            ),
        )

        composeRule.onNodeWithTag("playback-options-audio").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionLeft) }
            .assertIsFocused()

        composeRule.onNodeWithTag("playback-options-subtitles").requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("playback-options-track-sub-de")
            .assertIsFocused()
            .assertIsSelected()
            .performKeyInput { pressKey(Key.DirectionRight) }
            .assertIsFocused()

        composeRule.onNodeWithTag("playback-options-header-back")
            .requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(PlaybackOptionsPage.ROOT, page) }
        composeRule.onNodeWithTag("playback-options-subtitles").assertIsFocused()

        composeRule.onNodeWithTag("playback-options-stats").requestFocus()
            .performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionRight) }
            .assertIsFocused()
    }

    @Test
    fun loadingAndEmptyMessagesArePassiveWithSafeFocus() {
        var page by mutableStateOf(PlaybackOptionsPage.AUDIO)
        var tracksResolving by mutableStateOf(true)
        setOptionsContent(
            page = { page },
            onPageChange = { page = it },
            tracksResolving = { tracksResolving },
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
        )

        composeRule.onNodeWithTag("playback-options-track-loading")
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule.onNodeWithText("No audio tracks available.").assertDoesNotExist()
        composeRule.onNodeWithTag("playback-options-header-back").assertIsFocused()

        composeRule.runOnIdle { page = PlaybackOptionsPage.SUBTITLES }
        composeRule.onNodeWithTag("playback-options-subtitles-off")
            .assertIsFocused()
            .assertIsNotSelected()
        composeRule.onNodeWithTag("playback-options-track-loading")
            .assertIsDisplayed()
            .assertHasNoClickAction()

        composeRule.runOnIdle {
            page = PlaybackOptionsPage.AUDIO
            tracksResolving = false
        }
        composeRule.onNodeWithTag("playback-options-track-loading").assertDoesNotExist()
        composeRule.onNodeWithTag("playback-options-track-empty")
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule.onNodeWithTag("playback-options-header-back").assertIsFocused()

        composeRule.runOnIdle { page = PlaybackOptionsPage.SUBTITLES }
        composeRule.onNodeWithTag("playback-options-subtitles-off")
            .assertIsFocused()
            .assertIsSelected()
        composeRule.onNodeWithTag("playback-options-track-empty")
            .assertIsDisplayed()
            .assertHasNoClickAction()
    }

    @Test
    fun selectedTrackIsScrolledIntoCompositionAndDisappearanceUsesFallback() {
        var page by mutableStateOf(PlaybackOptionsPage.AUDIO)
        var tracks by mutableStateOf(
            List(18) { index ->
                track(
                    key = "audio-$index",
                    label = "Tonspur mit ausführlicher deutscher Bezeichnung $index",
                    selected = index == 16,
                )
            }
        )
        setOptionsContent(
            page = { page },
            onPageChange = { page = it },
            audioTracks = { tracks },
        )

        composeRule.onNodeWithTag("playback-options-track-audio-16")
            .assertIsDisplayed()
            .assertIsFocused()

        composeRule.runOnIdle { tracks = tracks.filterNot { it.key == "audio-16" } }
        composeRule.onNodeWithTag("playback-options-track-audio-0")
            .assertIsDisplayed()
            .assertIsFocused()
        composeRule.onNodeWithText("Audio track").assertIsDisplayed()

        composeRule.runOnIdle { page = PlaybackOptionsPage.ROOT }
        composeRule.onNodeWithText("Choose audio track").assertIsDisplayed()
        composeRule.onNodeWithText("No audio tracks available.").assertDoesNotExist()
    }

    @Test
    fun resolvingTracksUpdateWithoutChangingTheOpenPage() {
        var tracksResolving by mutableStateOf(true)
        var tracks by mutableStateOf(emptyList<PlaybackOptionTrack>())
        setOptionsContent(
            page = { PlaybackOptionsPage.AUDIO },
            onPageChange = {},
            tracksResolving = { tracksResolving },
            audioTracks = { tracks },
        )

        composeRule.onNodeWithTag("playback-options-track-loading").assertIsDisplayed()

        composeRule.runOnIdle {
            tracksResolving = false
            tracks = listOf(track("audio-main", "Deutsch", selected = true))
        }
        composeRule.onNodeWithTag("playback-options-track-audio-main")
            .assertIsDisplayed()
            .assertIsFocused()

        composeRule.runOnIdle { tracks = emptyList() }
        composeRule.onNodeWithTag("playback-options-track-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("playback-options-header-back").assertIsFocused()
        composeRule.onNodeWithText("Audio track").assertIsDisplayed()
    }

    @Test
    fun media3TrackChangesAreObservedOnlyWhileTheSheetIsMounted() {
        val player = ObservableTestPlayer(Tracks.EMPTY)
        var mounted by mutableStateOf(true)
        var tracksResolving by mutableStateOf(true)
        composeRule.setContent {
            if (mounted) {
                TVHeadendPlayerTheme {
                    PlaybackOptionsSheet(
                        page = PlaybackOptionsPage.AUDIO,
                        player = player.player,
                        tracksResolving = tracksResolving,
                        aspectRatio = AspectRatioMode.FIT,
                        statsVisible = false,
                        showSimpleTvExit = false,
                        fullOptionsAvailable = true,
                        onPageChange = {},
                        onAspectRatioChange = {},
                        onStatsVisibleChange = {},
                        onSimpleTvExit = {},
                    )
                }
            }
        }

        composeRule.runOnIdle { assertEquals(1, player.listenerCount) }
        composeRule.onNodeWithTag("playback-options-track-loading").assertIsDisplayed()

        composeRule.runOnIdle {
            tracksResolving = false
            player.update(audioTracks())
        }
        composeRule.onNodeWithTag(audioTrackTag()).assertIsFocused()

        composeRule.runOnIdle {
            player.update(audioTracks(includeLeadingVideo = true))
        }
        composeRule.onNodeWithTag(audioTrackTag()).assertIsFocused()

        composeRule.runOnIdle { player.update(Tracks.EMPTY) }
        composeRule.onNodeWithTag("playback-options-track-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("playback-options-header-back").assertIsFocused()

        composeRule.runOnIdle { mounted = false }
        composeRule.waitForIdle()
        assertEquals(0, player.listenerCount)
    }

    @Test
    fun activeSimpleTvRootContainsOnlyPermittedCategoriesAndOwnerExit() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                PlaybackOptionsSheetContent(
                    page = PlaybackOptionsPage.ROOT,
                    audioTracks = listOf(track("audio-main", "Deutsch", selected = true)),
                    subtitleTracks = emptyList(),
                    tracksResolving = false,
                    aspectRatio = AspectRatioMode.FIT,
                    statsVisible = false,
                    showSimpleTvExit = true,
                    fullOptionsAvailable = false,
                    onPageChange = {},
                    onAudioTrackSelected = {},
                    onSubtitleTrackSelected = {},
                    onAspectRatioChange = {},
                    onStatsVisibleChange = {},
                    onSimpleTvExit = {},
                )
            }
        }

        composeRule.onNodeWithTag("playback-options-audio").assertIsFocused()
        composeRule.onNodeWithTag("playback-options-subtitles").assertIsDisplayed()
        composeRule.onNodeWithTag("playback-options-display").assertDoesNotExist()
        composeRule.onNodeWithTag("playback-options-stats").assertDoesNotExist()
        composeRule.onNodeWithTag("playback-options-simple-tv-exit").assertIsDisplayed()
    }

    @Test
    fun statsRowExposesTruthfulSwitchSemantics() {
        var statsVisible by mutableStateOf(false)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                PlaybackOptionsSheetContent(
                    page = PlaybackOptionsPage.ROOT,
                    audioTracks = listOf(track("audio-main", "Deutsch", selected = true)),
                    subtitleTracks = emptyList(),
                    tracksResolving = false,
                    aspectRatio = AspectRatioMode.FIT,
                    statsVisible = statsVisible,
                    showSimpleTvExit = false,
                    fullOptionsAvailable = true,
                    onPageChange = {},
                    onAudioTrackSelected = {},
                    onSubtitleTrackSelected = {},
                    onAspectRatioChange = {},
                    onStatsVisibleChange = { statsVisible = it },
                    onSimpleTvExit = {},
                )
            }
        }

        composeRule.onNodeWithTag("playback-options-stats")
            .assertIsOff()
            .requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
            .assertIsOn()
    }

    @Test
    fun closingOptionsRestoresTheInvokingMoreActionAfterControlsRecompose() {
        var optionsOpen by mutableStateOf(false)
        var restoreOptionsFocus by mutableStateOf(false)
        composeRule.setContent {
            val context = LocalContext.current
            val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
            TVHeadendPlayerTheme {
                PlayerControlsLayer(
                    visible = true,
                    modalVisible = optionsOpen,
                ) {
                    RecordingOverlayControls(
                        imageLoader = imageLoader,
                        piconPath = null,
                        title = "Recording",
                        subtitle = null,
                        channelName = "Channel",
                        positionMs = 30_000L,
                        durationMs = 120_000L,
                        growing = false,
                        nowSec = 0L,
                        isPlaying = true,
                        controlsVisible = true,
                        optionsOpen = false,
                        onTogglePlayPause = {},
                        onSeek = {},
                        onStopPlayback = {},
                        onUserInteraction = {},
                        showStop = true,
                        onOpenOptions = { optionsOpen = true },
                        onOpenInfo = {},
                        restoreOptionsFocus = restoreOptionsFocus,
                        onOptionsFocusRestored = { restoreOptionsFocus = false },
                    )
                }
                if (optionsOpen) {
                    PlaybackOptionsSheetContent(
                        page = PlaybackOptionsPage.ROOT,
                        audioTracks = listOf(track("audio-main", "Deutsch", selected = true)),
                        subtitleTracks = emptyList(),
                        tracksResolving = false,
                        aspectRatio = AspectRatioMode.FIT,
                        statsVisible = false,
                        showSimpleTvExit = false,
                        fullOptionsAvailable = true,
                        onPageChange = {},
                        onAudioTrackSelected = {},
                        onSubtitleTrackSelected = {},
                        onAspectRatioChange = {},
                        onStatsVisibleChange = {},
                        onSimpleTvExit = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("recording-playback-options")
            .requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("recording-playback-options").assertDoesNotExist()
        composeRule.onNodeWithTag("recording-seekbar").assertDoesNotExist()
        composeRule.onNodeWithTag("playback-options-audio").assertIsFocused()

        composeRule.runOnIdle {
            restoreOptionsFocus = true
            optionsOpen = false
        }
        composeRule.onNodeWithTag("recording-playback-options").assertIsFocused()
        composeRule.runOnIdle { assertFalse(restoreOptionsFocus) }
    }

    @Test
    fun panelAndLongTextStayInsideSafeBoundsAtSupportedFontScales() {
        var fontScale by mutableFloatStateOf(1f)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
            ) {
                TVHeadendPlayerTheme {
                    Box(
                        Modifier
                            .size(width = 960.dp, height = 540.dp)
                            .testTag("playback-options-test-viewport"),
                    ) {
                        PlaybackOptionsSheetContent(
                            page = PlaybackOptionsPage.AUDIO,
                            audioTracks = List(14) { index ->
                                track(
                                    key = "long-$index",
                                    label = "Ausführliche deutschsprachige Tonspurbezeichnung $index",
                                    supportingLabel = "Mehrkanalton mit zusätzlichen Metadaten",
                                    selected = index == 12,
                                )
                            },
                            subtitleTracks = emptyList(),
                            tracksResolving = false,
                            aspectRatio = AspectRatioMode.FIT,
                            statsVisible = false,
                            showSimpleTvExit = false,
                            fullOptionsAvailable = true,
                            onPageChange = {},
                            onAudioTrackSelected = {},
                            onSubtitleTrackSelected = {},
                            onAspectRatioChange = {},
                            onStatsVisibleChange = {},
                            onSimpleTvExit = {},
                        )
                    }
                }
            }
        }

        assertPanelBounds()
        composeRule.onNodeWithTag("playback-options-overlay").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Audio track"),
        )
        composeRule.onNodeWithTag("playback-options-title").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
        composeRule.onNodeWithTag("playback-options-track-long-12")
            .assertIsDisplayed()
            .assertIsFocused()
        composeRule.onNodeWithTag(
            "playback-options-track-support-long-12",
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
        assertTrackInsidePanel("playback-options-track-long-12")

        composeRule.runOnIdle { fontScale = 1.3f }
        assertPanelBounds()
        composeRule.onNodeWithTag("playback-options-track-long-12")
            .assertIsDisplayed()
            .assertIsFocused()
        assertTrackInsidePanel("playback-options-track-long-12")
    }

    private fun setOptionsContent(
        page: () -> PlaybackOptionsPage,
        onPageChange: (PlaybackOptionsPage) -> Unit,
        tracksResolving: () -> Boolean = { false },
        audioTracks: List<PlaybackOptionTrack> = listOf(
            track("audio-main", "Deutsch", selected = true),
        ),
        audioTracksProvider: (() -> List<PlaybackOptionTrack>)? = null,
        subtitleTracks: List<PlaybackOptionTrack> = emptyList(),
        subtitleTracksProvider: (() -> List<PlaybackOptionTrack>)? = null,
    ) {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                PlaybackOptionsSheetContent(
                    page = page(),
                    audioTracks = audioTracksProvider?.invoke() ?: audioTracks,
                    subtitleTracks = subtitleTracksProvider?.invoke() ?: subtitleTracks,
                    tracksResolving = tracksResolving(),
                    aspectRatio = AspectRatioMode.FIT,
                    statsVisible = false,
                    showSimpleTvExit = false,
                    fullOptionsAvailable = true,
                    onPageChange = onPageChange,
                    onAudioTrackSelected = {},
                    onSubtitleTrackSelected = {},
                    onAspectRatioChange = {},
                    onStatsVisibleChange = {},
                    onSimpleTvExit = {},
                )
            }
        }
    }

    private fun setOptionsContent(
        page: () -> PlaybackOptionsPage,
        onPageChange: (PlaybackOptionsPage) -> Unit,
        tracksResolving: () -> Boolean = { false },
        audioTracks: () -> List<PlaybackOptionTrack>,
        subtitleTracks: () -> List<PlaybackOptionTrack> = { emptyList() },
    ) = setOptionsContent(
        page = page,
        onPageChange = onPageChange,
        tracksResolving = tracksResolving,
        audioTracksProvider = audioTracks,
        subtitleTracksProvider = subtitleTracks,
    )

    private fun assertPanelBounds() {
        composeRule.waitForIdle()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val viewport = composeRule.onNodeWithTag("playback-options-test-viewport")
            .fetchSemanticsNode().boundsInRoot
        val panel = composeRule.onNodeWithTag("playback-options-overlay")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(root.width, viewport.width, 1f)
        assertEquals(root.height, viewport.height, 1f)
        assertEquals(16f / 9f, viewport.width / viewport.height, 0.001f)
        assertTrue(panel.top >= viewport.top + viewport.height * (32f / 540f))
        assertTrue(panel.bottom <= viewport.bottom - viewport.height * (108f / 540f))
        assertTrue(panel.left >= viewport.left)
        assertTrue(panel.right <= viewport.right - viewport.width * (48f / 960f))
        assertTrue(panel.width < viewport.width / 2f)
    }

    private fun assertTrackInsidePanel(tag: String) {
        val panel = composeRule.onNodeWithTag("playback-options-overlay")
            .fetchSemanticsNode().boundsInRoot
        val trackBounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue(trackBounds.top >= panel.top)
        assertTrue(trackBounds.bottom <= panel.bottom)
        assertTrue(trackBounds.left >= panel.left)
        assertTrue(trackBounds.right <= panel.right)
    }

    private fun track(
        key: String,
        label: String,
        supportingLabel: String? = null,
        selected: Boolean = false,
    ) = PlaybackOptionTrack(
        key = key,
        label = label,
        supportingLabel = supportingLabel,
        selected = selected,
    )

    private fun audioTracks(includeLeadingVideo: Boolean = false): Tracks {
        val audioGroup = TrackGroup(
            "audio",
            Format.Builder()
                .setId("audio-main")
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setLanguage("de")
                .build(),
        )
        val groups = buildList {
            if (includeLeadingVideo) {
                val videoGroup = TrackGroup(
                    "video",
                    Format.Builder()
                        .setId("video-main")
                        .setSampleMimeType(MimeTypes.VIDEO_H264)
                        .build(),
                )
                add(
                    Tracks.Group(
                        videoGroup,
                        false,
                        intArrayOf(C.FORMAT_HANDLED),
                        booleanArrayOf(true),
                    )
                )
            }
            add(
                Tracks.Group(
                    audioGroup,
                    false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(true),
                )
            )
        }
        return Tracks(groups)
    }

    private fun audioTrackTag(): String =
        "playback-options-track-${C.TRACK_TYPE_AUDIO}:audio:audio-main"

    private class ObservableTestPlayer(initialTracks: Tracks) : InvocationHandler {
        private val listeners = mutableSetOf<Player.Listener>()
        private var currentTracks = initialTracks

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            this,
        ) as Player

        val listenerCount: Int
            get() = listeners.size

        fun update(tracks: Tracks) {
            currentTracks = tracks
            listeners.toList().forEach { it.onTracksChanged(tracks) }
        }

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? =
            when (method.name) {
                "getCurrentTracks" -> currentTracks
                "addListener" -> {
                    listeners += requireNotNull(args?.first()) as Player.Listener
                    Unit
                }
                "removeListener" -> {
                    listeners -= requireNotNull(args?.first()) as Player.Listener
                    Unit
                }
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ObservableTestPlayer"
                else -> defaultValue(method.returnType)
            }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }
}
