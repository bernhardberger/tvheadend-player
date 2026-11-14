package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvhplayer.di.PlayerStatsLevel
import at.bernhardberger.tvhplayer.playback.*
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaybackStatsOverlayTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun defaultDvbPathKeepsTheCompactContractAndIsOnePassiveRegion() {
        setStats(liveDiagnostics())
        val node = composeRule.onNodeWithTag("playback-stats-overlay")
        node.assertHasNoClickAction().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val density = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        assertTrue(bounds.bottom / density <= 300f)
        for (name in listOf("Tuner", "Tvheadend", "This app", "Decoder", "Screen")) {
            composeRule.onNodeWithText(name, useUnmergedTree = true).assertExists()
        }
        composeRule.onNodeWithText("North ridge multiplex", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun recordingOmitsServerEvidenceEvenWhenItsNameLooksLikeATuner() {
        setStats(AppPlaybackDiagnostics(source = AppPlaybackSource.RECORDING, state = AppPlaybackState.Playing))
        composeRule.onNodeWithText("Tuner", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Tvheadend", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Decoder", useUnmergedTree = true).assertExists()
    }

    @Test fun debugAddsRawSourceNamesAndAllQueueEvidence() {
        setStats(liveDiagnostics(), PlayerStatsLevel.DEBUG)
        composeRule.onNodeWithText("North ridge multiplex", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Queue:", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("250.0 ms", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test fun aCoveredCardDoesNotCompeteWithTheModalForAccessibility() {
        setStats(liveDiagnostics(), accessible = false)
        composeRule.onNodeWithTag("playback-stats-overlay")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Tuner").assertDoesNotExist()
    }

    private fun setStats(diagnostics: AppPlaybackDiagnostics, level: PlayerStatsLevel = PlayerStatsLevel.STREAM_PATH,
        accessible: Boolean = true) {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.requiredSize(960.dp, 540.dp)) {
                    PlaybackStatsOverlay(diagnostics, AspectRatioMode.FIT,
                        Modifier.offset(58.dp, 100.dp), level = level, accessible = accessible)
                }
            }
        }
    }

    @OptIn(SubscriptionInfrastructureApi::class)
    private fun liveDiagnostics(): AppPlaybackDiagnostics {
        var live = LiveSubscriptionDiagnostics.update(null, SubscriptionEvent.Started(
            source = LiveSubscriptionSource.create("DVB-T adapter", "North ridge multiplex", "Ridge network", "Ridge", "Ridge Earth"),
            streams = null, codecMetadata = null))
        live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Signal(
            relativeSnr = 49_152, absoluteSnr = 31_250, relativeSignal = 49_152, absoluteSignal = -63_500,
            bitErrorRate = 42, uncorrectedBlockCount = 7, frontendStatusReported = true,
            frontendState = LiveFrontendState.create(signalDetected = true, partiallySynchronized = true, locked = true)))
        live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Queue(
            packetCount = 1_500, byteCount = 2_000_000, delay = 250_000,
            bFrameDropCount = 4, pFrameDropCount = 3, iFrameDropCount = 2))
        return AppPlaybackDiagnostics(source = AppPlaybackSource.LIVE_TV, state = AppPlaybackState.Playing, live = live,
            video = AppPlaybackFormatDiagnostics("H.264", "1920×1080", 50f),
            audio = AppPlaybackFormatDiagnostics("E-AC-3", language = "English", channelCount = 6, sampleRateHz = 48_000))
    }
}
