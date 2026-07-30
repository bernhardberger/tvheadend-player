package at.bernhardberger.tvhplayer.ui.player

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.player.PlaybackDiagnosticsSnapshot
import at.bernhardberger.tvhplayer.player.PlaybackDiagnosticsSource
import at.bernhardberger.tvhplayer.player.PlaybackFormatDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackOutputMode
import at.bernhardberger.tvhplayer.player.PlaybackQueueDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackSessionState
import at.bernhardberger.tvhplayer.player.PlaybackSystemDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackThermalLevel
import at.bernhardberger.tvhplayer.player.PlaybackTransportDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackTunerDiagnostics
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaybackStatsOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun maximumCanonicalPayloadFitsInside960x540AndKeepsHeadings() {
        setStats(locale = Locale.GERMAN)

        val surface = bounds("stats-test-surface")
        val overlay = bounds("playback-stats-overlay")
        assertTrue(overlay.left >= surface.left)
        assertTrue(overlay.top >= surface.top)
        assertTrue(overlay.right <= surface.right)
        assertTrue(overlay.bottom <= surface.bottom)
        assertDescendantsWithin(
            composeRule.onNodeWithTag("playback-stats-overlay", useUnmergedTree = true)
                .fetchSemanticsNode(),
            overlay,
        )

        val labelLayouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("Server frame drops")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(labelLayouts)
            }
        assertTrue(labelLayouts.single().lineCount == 1)

        composeRule.onNodeWithText("Stats for nerds").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
        composeRule.onNodeWithText("Playback").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
        composeRule.onNodeWithText("System").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
        composeRule.onNodeWithTag("playback-stats-overlay")
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
    }

    @Test
    fun configuredLocaleDoesNotTranslateTheCanonicalTechnicalPayload() {
        setStats(locale = Locale.GERMAN)

        composeRule.onNodeWithText("Recovering").assertIsDisplayed()
        composeRule.onNodeWithText("Wiederherstellung läuft").assertDoesNotExist()
        composeRule.onNodeWithText("buffer", substring = true, ignoreCase = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("12,345").assertIsDisplayed()
        composeRule.onNodeWithText("59.9 fps", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Frames").assertIsDisplayed()
        composeRule.onNodeWithText("Server frame drops").assertIsDisplayed()
        composeRule.onNodeWithText("Display mode").assertIsDisplayed()
        composeRule.onNodeWithText("Auto (original aspect)").assertIsDisplayed()
        composeRule.onNodeWithText("Bildformat").assertDoesNotExist()
        composeRule.onNodeWithText("Automatisch (Originalformat)").assertDoesNotExist()
    }

    @Test
    fun timeshiftValuesRemainCanonicalUnderGermanConfiguration() {
        setStats(
            locale = Locale.GERMAN,
            timeshiftState = TimeshiftState(
                available = true,
                bufferStartMs = -3_600_000L,
                positionMs = -30_000L,
                liveEdgeMs = 0L,
            ),
        )

        composeRule.onNodeWithText("00:30 behind live").assertIsDisplayed()
        composeRule.onNodeWithText("00:30 hinter Live").assertDoesNotExist()
    }

    @Test
    fun percentagesUseCanonicalTechnicalFormatting() {
        setStats(
            locale = Locale.GERMAN,
            diagnostics = maximumDiagnostics.copy(
                transport = PlaybackTransportDiagnostics(
                    tuner = PlaybackTunerDiagnostics(signalPercent = 12.3f),
                ),
            ),
        )

        composeRule.onNodeWithText("12.3%").assertIsDisplayed()
    }

    private fun setStats(
        locale: Locale,
        diagnostics: PlaybackDiagnosticsSnapshot = maximumDiagnostics,
        timeshiftState: TimeshiftState? = null,
    ) {
        composeRule.setContent {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val configuredConfiguration = remember(configuration, locale) {
                Configuration(configuration).apply { setLocale(locale) }
            }
            val configuredContext = remember(context, configuredConfiguration) {
                context.createConfigurationContext(configuredConfiguration)
            }
            CompositionLocalProvider(
                LocalContext provides configuredContext,
                LocalConfiguration provides configuredConfiguration,
                LocalResources provides configuredContext.resources,
            ) {
                TVHeadendPlayerTheme {
                    Box(
                        modifier = Modifier
                            .requiredSize(width = 960.dp, height = 540.dp)
                            .testTag("stats-test-surface"),
                    ) {
                        PlaybackStatsOverlay(
                            diagnostics = diagnostics,
                            aspectRatio = AspectRatioMode.FIT,
                            timeshiftState = timeshiftState,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 36.dp, end = 48.dp),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String) =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertDescendantsWithin(node: SemanticsNode, container: Rect) {
        node.children.forEach { child ->
            assertTrue(child.boundsInRoot.left >= container.left)
            assertTrue(child.boundsInRoot.top >= container.top)
            assertTrue(child.boundsInRoot.right <= container.right)
            assertTrue(child.boundsInRoot.bottom <= container.bottom)
            assertDescendantsWithin(child, container)
        }
    }

    private val maximumDiagnostics = PlaybackDiagnosticsSnapshot(
        source = PlaybackDiagnosticsSource.LIVE_TV,
        state = PlaybackSessionState.Recovering(retryDelayMillis = 1_500L),
        positionMs = 7_200_000L,
        durationMs = 14_400_000L,
        bufferedMs = 75_000L,
        video = PlaybackFormatDiagnostics(
            codec = "video/hevc (Main 10 profile)",
            resolution = "3840x2160",
            frameRate = 59.94f,
        ),
        videoDecoder = "c2.vendor.hevc.decoder.secure.long-name",
        renderedFrames = 1_234_567,
        droppedFrames = 12_345,
        audio = PlaybackFormatDiagnostics(
            codec = "audio/eac3-joc",
            language = "Deutsch (Österreich)",
            channelCount = 8,
            sampleRateHz = 192_000,
        ),
        audioDecoder = "c2.vendor.eac3.decoder.long-name",
        audioUnderruns = 12_345,
        readRateBitsPerSecond = 123_456_789L,
        transport = PlaybackTransportDiagnostics(
            tuner = PlaybackTunerDiagnostics(
                status = "LOCKED WITH A DELIBERATELY LONG FRONTEND STATUS",
                signalMilliDbm = -123_456L,
                snrMilliDb = 45_678L,
                bitErrorRate = 1_234_567L,
                uncorrectedBlocks = 9_876_543L,
            ),
            queue = PlaybackQueueDiagnostics(
                packets = 9_876_543L,
                bytes = 987_654_321L,
                delayMicros = 123_456L,
                bFrameDrops = 987_654L,
                pFrameDrops = 876_543L,
                iFrameDrops = 765_432L,
            ),
        ),
        system = PlaybackSystemDiagnostics(
            outputMode = PlaybackOutputMode(
                width = 3840,
                height = 2160,
                refreshRateHz = 59.94f,
            ),
            thermalLevel = PlaybackThermalLevel.EMERGENCY,
            appPssBytes = 987_654_321L,
            lowMemory = true,
        ),
    )
}
