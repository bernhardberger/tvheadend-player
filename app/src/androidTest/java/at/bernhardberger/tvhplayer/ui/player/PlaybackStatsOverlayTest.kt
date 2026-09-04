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
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.playback.LiveFrontendState
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionDiagnostics
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionSource
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionCondition
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackFormatDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackSource
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
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
        setStats(
            locale = Locale.GERMAN,
            diagnostics = maximumDiagnostics.copy(live = maximumLiveDiagnostics),
        )

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

        composeRule.onNodeWithText("Stats for nerds").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
        composeRule.onNodeWithText("Playback").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
        composeRule.onNodeWithText("Source").assertIsDisplayed()
        composeRule.onNodeWithText("Tuner").assertIsDisplayed()
        composeRule.onNodeWithText("TVHeadend queue").assertIsDisplayed()
        composeRule.onNodeWithText("DVB-T Adapter", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("75.0%", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("250.0 ms").assertIsDisplayed()
        composeRule.onNodeWithText("admin:secret", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("playback-stats-overlay")
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
    }

    @Test
    fun absentLiveDiagnosticsOmitTunerAndQueueSections() {
        setStats(locale = Locale.GERMAN)

        composeRule.onNodeWithText("Tuner").assertDoesNotExist()
        composeRule.onNodeWithText("TVHeadend queue").assertDoesNotExist()
    }

    @Test
    fun configuredLocaleDoesNotTranslateTheCanonicalTechnicalPayload() {
        setStats(locale = Locale.GERMAN)

        composeRule.onNodeWithText("Recovering").assertIsDisplayed()
        composeRule.onNodeWithText("Wiederherstellung läuft").assertDoesNotExist()
        composeRule.onNodeWithText("buffer", substring = true, ignoreCase = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("192,000 Hz", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("59.9 fps", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("audio/eac3-joc", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Display mode").assertIsDisplayed()
        composeRule.onNodeWithText("Auto (original aspect)").assertIsDisplayed()
        composeRule.onNodeWithText("Bildformat").assertDoesNotExist()
        composeRule.onNodeWithText("Automatisch (Originalformat)").assertDoesNotExist()
    }

    @Test
    fun timeshiftValuesRemainCanonicalUnderGermanConfiguration() {
        setStats(
            locale = Locale.GERMAN,
            timeshiftState = AppTimeshiftState(
                available = true,
                bufferStartMs = -3_600_000L,
                positionMs = -30_000L,
                liveEdgeMs = 0L,
            ),
        )

        composeRule.onNodeWithText("00:30 behind live").assertIsDisplayed()
        composeRule.onNodeWithText("00:30 hinter Live").assertDoesNotExist()
    }

    private fun setStats(
        locale: Locale,
        diagnostics: AppPlaybackDiagnostics = maximumDiagnostics,
        timeshiftState: AppTimeshiftState? = null,
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

    private val maximumDiagnostics = AppPlaybackDiagnostics(
        source = AppPlaybackSource.LIVE_TV,
        state = AppPlaybackState.Recovering(
            reason = PlaybackRecoveryReason.AUDIO_RECOVERY_EXHAUSTED,
            retryDelayMillis = 1_500L,
        ),
        positionMs = 7_200_000L,
        durationMs = 14_400_000L,
        bufferedMs = 75_000L,
        video = AppPlaybackFormatDiagnostics(
            codec = "video/hevc (Main 10 profile)",
            resolution = "3840x2160",
            frameRate = 59.94f,
        ),
        audio = AppPlaybackFormatDiagnostics(
            codec = "audio/eac3-joc",
            language = "Deutsch (Österreich)",
            channelCount = 8,
            sampleRateHz = 192_000,
        ),
    )

    @OptIn(SubscriptionInfrastructureApi::class)
    private val maximumLiveDiagnostics: LiveSubscriptionDiagnostics = run {
        val source = LiveSubscriptionSource.create(
            adapterName = "DVB-T Adapter",
            muxName = "https://admin:secret@example.test/mux",
            networkName = null,
            providerName = null,
            serviceName = null,
        )
        var diagnostics = LiveSubscriptionDiagnostics.update(
            previous = null,
            event = SubscriptionEvent.Started(
                streams = null,
                codecMetadata = null,
                condition = SubscriptionCondition.NO_DETAIL,
                issue = null,
                source = source,
            ),
        )
        diagnostics = LiveSubscriptionDiagnostics.update(
            previous = diagnostics,
            event = SubscriptionEvent.Signal(
                relativeSnr = 49_152L,
                absoluteSnr = 31_250L,
                relativeSignal = 49_152L,
                absoluteSignal = -63_500L,
                bitErrorRate = 42L,
                uncorrectedBlockCount = 7L,
                frontendStatusReported = true,
                frontendState = LiveFrontendState.create(
                    signalDetected = true,
                    partiallySynchronized = true,
                    locked = true,
                ),
            ),
        )
        requireNotNull(
            LiveSubscriptionDiagnostics.update(
                previous = diagnostics,
                event = SubscriptionEvent.Queue(
                    packetCount = 1_500L,
                    byteCount = 2_000_000L,
                    delay = 250_000L,
                    bFrameDropCount = 4L,
                    pFrameDropCount = 3L,
                    iFrameDropCount = 2L,
                ),
            ),
        )
    }
}
