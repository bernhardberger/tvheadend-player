package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ChannelSettingsNotice
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import at.bernhardberger.tvhplayer.ui.screens.recordings.PendingRecordingAction
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingConfirmationDialog
import kotlin.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Overlays that request their initial focus as they appear must draw it at first
 * idle: the target holds focus and looks as it does when focus arrives later, not
 * as an unfocused control. Composing each overlay in the first composition is the
 * case that reproduces the recording confirmation's missing focus fill (def2ada)
 * under Robolectric; opening it over an already focused host does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayInitialFocusDrawTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun televisionFeature() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
    }

    /** The already fixed recording confirmation, which calibrates this check. */
    @Test fun recordingConfirmation() = assertInitialFocusDrawn("recording-confirmation-back") {
        RecordingConfirmationDialog(PendingRecordingAction.DELETE, "Title", true, {}, {})
    }

    /** Record in programme info: Cancel is the safe initial action. */
    @Test fun programmeRecordingConfirmation() = assertInitialFocusDrawn("programme-recording-cancel") {
        ProgrammeRecordingConfirmation(
            state = LiveInfoRecordingState.Confirming(programme().programmeRecordingTarget(session())),
            onActivate = {},
            onDismiss = {},
        )
    }

    @Test fun recordingMarker() {
        val navigation = RecordingMarkerNavigation()
        val markers = listOf(0L, 60_000L, 120_000L)
        navigation.show(markers, positionMs = 30_000L)
        assertInitialFocusDrawn("recording-marker-target") {
            Box(Modifier.padding(top = 200.dp).fillMaxWidth().height(8.dp)) {
                RecordingMarkerOverlay(
                    navigation = navigation,
                    markers = markers,
                    onSeek = {},
                    displayDurationMs = 180_000L,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Test fun recoveryOverlay() = assertInitialFocusDrawn("tv-recovery-primary") {
        TvRecoveryOverlay(
            visible = true,
            message = "Connection lost",
            primaryActionLabel = "Retry",
            onPrimaryAction = {},
            secondaryActionLabel = "Close",
            onSecondaryAction = {},
        )
    }

    /** The page focuses its selected row, not the header's Back. */
    @Test fun displayModePage() = optionsPage(
        PlaybackOptionsPage.DISPLAY,
        hasText(string(R.string.display_mode_16_9)) and isSelected(),
    )

    @Test fun statsPage() = optionsPage(
        PlaybackOptionsPage.STATS,
        hasText(string(R.string.stats_for_nerds)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
    )

    /** The selected track's row, reached through the list's scroll-and-wait branch. */
    @Test fun audioTrackPage() = optionsPage(
        PlaybackOptionsPage.AUDIO,
        hasTestTag("playback-options-track-a2"),
        audioTracks = listOf(
            PlaybackOptionTrack("a1", "English"),
            PlaybackOptionTrack("a2", "Deutsch", selected = true),
        ),
    )

    /** Without a selected subtitle the Off row takes focus. */
    @Test fun subtitlesPage() = optionsPage(
        PlaybackOptionsPage.SUBTITLES,
        hasTestTag("playback-options-subtitles-off"),
        subtitleTracks = listOf(PlaybackOptionTrack("s1", "English")),
    )

    /** Mirrors the recording player's info effect, which focuses the reading pane as it opens. */
    @Test fun recordingInfoReadingPane() = assertInitialFocusDrawn(hasTestTag("player-info-reading")) {
        val readingFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { readingFocus.requestFocus() } }
        PlaybackOptionsOverlayFrame(
            paneTitle = "Info",
            panelTag = "recording-info-panel",
            panelWidth = PlaybackInfoPanelWidth,
        ) {
            PlayerInfoReadingContent(
                title = "Title",
                subtitle = "Channel",
                body = "Synopsis",
                readingFocus = readingFocus,
                modifier = Modifier.padding(PlaybackInfoPanelPadding),
                footer = {},
            )
        }
    }

    /** A failed channel settings load shows Retry, which takes the screen's initial focus. */
    @Test fun channelSettingsRetry() = assertInitialFocusDrawn(hasText(string(R.string.retry))) {
        ChannelSettingsNotice(loaded = false, failed = true, onRetry = {}, initialFocusEnabled = true)
    }

    private fun optionsPage(
        page: PlaybackOptionsPage,
        expected: SemanticsMatcher,
        audioTracks: List<PlaybackOptionTrack> = emptyList(),
        subtitleTracks: List<PlaybackOptionTrack> = emptyList(),
    ) = assertInitialFocusDrawn(expected) {
        PlaybackOptionsSheetContent(
            page = page,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            tracksResolving = false,
            aspectRatio = AspectRatioMode.FORCE_16_9,
            statsVisible = false,
            onPageChange = {},
            onAudioTrackSelected = {},
            onSubtitleTrackSelected = {},
            onAspectRatioChange = {},
            onStatsVisibleChange = {},
        )
    }

    /**
     * Captures the first idle frame, then moves focus away and back to the same node.
     * The first capture must match the refocused one, which differs from the unfocused one.
     */
    private fun assertInitialFocusDrawn(expectedTag: String, overlay: @Composable () -> Unit) =
        assertInitialFocusDrawn(hasTestTag(expectedTag), overlay)

    private fun assertInitialFocusDrawn(expected: SemanticsMatcher, overlay: @Composable () -> Unit) {
        lateinit var view: View
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(1.dp).testTag(ELSEWHERE).focusable())
                    overlay()
                }
            }
        }
        compose.waitForIdle()
        val focused = compose.onNode(isFocused()).fetchSemanticsNode()
        assertTrue("Focused node matches ${expected.description}", expected.matches(focused))
        val bounds = focused.boundsInRoot
        val target = compose.onNode(SemanticsMatcher("node ${focused.id}") { it.id == focused.id })
        val initial = capture(view)

        compose.onNodeWithTag(ELSEWHERE).requestFocus()
        compose.waitForIdle()
        val unfocused = capture(view)
        target.requestFocus()
        compose.waitForIdle()
        target.assertIsFocused()
        val refocused = capture(view)

        val focusLook = differingShare(unfocused, refocused, bounds)
        assertTrue("Focus changes the target's look, share $focusLook", focusLook > 0.05f)
        val initialLook = differingShare(initial, refocused, bounds)
        assertTrue("First idle frame draws the target focused, differing share $initialLook", initialLook < 0.01f)
        listOf(initial, unfocused, refocused).forEach(Bitmap::recycle)
    }

    private fun capture(view: View): Bitmap = compose.runOnIdle {
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /** Share of pixels within [bounds] whose colour differs between [a] and [b]. */
    private fun differingShare(a: Bitmap, b: Bitmap, bounds: Rect): Float {
        val xs = bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(a.width)
        val ys = bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(a.height)
        val differing = ys.sumOf { y ->
            xs.count { x ->
                val p = a.getPixel(x, y)
                val q = b.getPixel(x, y)
                (0..16 step 8).any { shift -> kotlin.math.abs((p shr shift and 0xFF) - (q shr shift and 0xFF)) > 8 }
            }
        }
        return differing.toFloat() / (xs.count() * ys.count())
    }

    private fun string(id: Int): String =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun programme() = EpgEvent.create(
        id = EventId(42L),
        channelId = ChannelId(1L),
        start = Instant.fromEpochSeconds(1_000L),
        stop = Instant.fromEpochSeconds(4_600L),
        title = "Title",
    )

    private fun session() = FakeSessionObservation(
        SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.ALLOWED,
                ),
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        ),
    ).captureCurrentSession()

    private companion object {
        const val ELSEWHERE = "elsewhere-stand-in"
    }
}
