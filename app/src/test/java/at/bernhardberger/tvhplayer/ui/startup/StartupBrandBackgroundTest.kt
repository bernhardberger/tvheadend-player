package at.bernhardberger.tvhplayer.ui.startup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsProperties
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import org.junit.Rule
import org.junit.experimental.categories.Category
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StartupBrandBackgroundTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val images = ImageLoaderRule()

    @Test
    @Category(VisualCapture::class)
    @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun presentationStartupSlots() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = ArtworkId(1)
        images.install(FixtureArt.imageLoader(context, mapOf(id to FixtureArt.picon("ridge-earth"))))
        val observation = artworkSession()
        val ridge = Channel.create(ChannelId(1), name = "Ridge Earth", number = 1, icon = ChannelIcon.ImageCache(id))
        var channel by mutableStateOf<Channel?>(null)
        var status by mutableStateOf("Connecting")
        var fresh by mutableStateOf(true)
        lateinit var view: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                    ProvideArtworkSession(observation) {
                        key(fresh) { ChannelStartupSurface(channel, status, freshStart = fresh) }
                    }
                }
            }
        }
        compose.settleFrames(5)
        capture(view, "brand-waiting-fresh")
        compose.runOnIdle { channel = ridge }
        compose.settleFrames(5)
        capture(view, "channel-connecting-saved-fresh")
        compose.runOnIdle { fresh = false; channel = ridge }
        compose.settleFrames(5)
        capture(view, "channel-connecting-wake")
        compose.runOnIdle { status = "Starting" }
        compose.settleFrames(4)
        capture(view, "channel-starting")
        compose.runOnIdle { channel = Channel.create(ChannelId(8), name = "Little Orbit", number = 8) }
        compose.settleFrames(4)
        capture(view, "channel-without-logo")
        compose.runOnIdle { channel = null; fresh = true; status = "Syncing channels and guide" }
        compose.settleFrames(4)
        capture(view, "brand-only")
    }

    @Test fun reducedMotionKeepsTheStatusWithoutARingOrGlow() {
        var motion by mutableStateOf(true)
        compose.setContent {
            TVHeadendPlayerTheme {
                ChannelStartupSurface(null, "Connecting", freshStart = true, motionEnabled = motion)
            }
        }
        assertTrue(compose.onAllNodes(
            androidx.compose.ui.test.hasTestTag("startup-background-pending") or
                androidx.compose.ui.test.hasTestTag("startup-background-prepared"), useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty())
        compose.runOnIdle { motion = false }
        compose.onNodeWithTag("main-startup-loader", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("startup-background-pending", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("startup-background-prepared", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Connecting", useUnmergedTree = true).assertExists()
    }

    @OptIn(ExperimentalCoroutinesApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun logosWaitForReadinessThenReplaceSequentiallyWithoutAnInterveningMark() {
        with(StartupLogoFixture()) {
        compose.runOnIdle { channel = second }
        compose.settleFrames(1)
        compose.onNodeWithTag("startup-logo-Ridge Earth", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("main-startup-mark", useUnmergedTree = true).assertDoesNotExist()
        dispatcher.scheduler.runCurrent()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("startup-logo-Ridge Earth", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("startup-logo-Harbor Sport", useUnmergedTree = true).assertDoesNotExist()
        compose.settleFrames(3)
        compose.onNodeWithTag("startup-logo-Harbor Sport", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("main-startup-mark", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { motion = false; channel = first }
        compose.mainClock.advanceTimeUntil(timeoutMillis = 128) {
            dispatcher.scheduler.runCurrent()
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("startup-logo-Ridge Earth"),
                useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("startup-logo-Ridge Earth", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("main-startup-mark", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun confirmationKeepsTheLogoAndUpdatesMetadataInPlace() {
        with(StartupLogoFixture()) {
        val requestsBeforeConfirmation = logoRequests
        val statusBefore = compose.onNodeWithTag("main-startup-status", useUnmergedTree = true).fetchSemanticsNode().config
        compose.runOnIdle {
            channel = Channel.create(first.id, name = first.name, icon = first.icon)
            session.publish(artworkObservation(), profile)
        }
        compose.settleFrames(3, frameMillis = 16)
        dispatcher.scheduler.runCurrent()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("confirming the same logo does not request it again", requestsBeforeConfirmation, logoRequests)
        val statusAfter = compose.onNodeWithTag("main-startup-status", useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals(statusBefore[SemanticsProperties.Text], statusAfter[SemanticsProperties.Text])
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite, statusAfter[SemanticsProperties.LiveRegion])
        compose.runOnIdle { channel = Channel.create(first.id, name = "Ridge Earth HD", icon = first.icon) }
        compose.settleFrames(3, frameMillis = 16)
        compose.onNodeWithText("Ridge Earth HD", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("startup-logo-Ridge Earth HD", useUnmergedTree = true).assertExists()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    private inner class StartupLogoFixture {
        val dispatcher = StandardTestDispatcher()
        val first = Channel.create(ChannelId(1), name = "Ridge Earth", icon = ChannelIcon.ImageCache(ArtworkId(1)))
        val second = Channel.create(ChannelId(2), name = "Harbor Sport", icon = ChannelIcon.ImageCache(ArtworkId(2)))
        var logoRequests = 0
        var channel by mutableStateOf(first)
        var motion by mutableStateOf(true)
        val profile = at.bernhardberger.tvheadend.sdk.core.ServerProfile("offline.invalid")
        val session = at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession().apply {
            artwork.scriptCached(profile)
            publish(artworkObservation(SessionState.Synchronizing), profile)
        }
        init {
        val context = ApplicationProvider.getApplicationContext<Context>()
        images.install(FixtureArt.imageLoader(context, mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"),
            ArtworkId(2) to FixtureArt.picon("harbor-sport"),
        ), onRequest = { logoRequests++ }).newBuilder().coroutineContext(dispatcher).build())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                at.bernhardberger.tvhplayer.ui.components.ProvideAppArtwork(session) {
                    ChannelStartupSurface(channel, "Starting", motionEnabled = motion)
                }
            }
        }
        compose.settleFrames(1)
        compose.onNodeWithTag("main-startup-mark", useUnmergedTree = true).assertExists()
        dispatcher.scheduler.runCurrent()
        compose.settleFrames(4)
        compose.onNodeWithTag("startup-logo-Ridge Earth", useUnmergedTree = true).assertExists()
        dispatcher.scheduler.runCurrent()
        compose.mainClock.advanceTimeByFrame()
        }
    }

    private fun artworkSession() = FakeSessionObservation(artworkObservation()).captureCurrentSession()

    private fun artworkObservation(state: SessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED,
            dvrWrite = at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED,
        ))) = SessionObservation.create(
        sessionState = state,
        channelState = at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState.Current(
            at.bernhardberger.tvheadend.sdk.core.ChannelCatalog.create(emptyList())),
        epgState = at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState.Current(
            at.bernhardberger.tvheadend.sdk.core.EpgSnapshot.create()),
        dvrState = at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState.Current(
            at.bernhardberger.tvheadend.sdk.core.DvrSnapshot.create()),
    )

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun warmEntryHasNoGlowAndAnnouncesStatusChanges() {
        var status by mutableStateOf("Connecting")
        val longName = "A very long fictional channel name ".repeat(8)
        compose.setContent {
            TVHeadendPlayerTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    ChannelStartupSurface(Channel.create(ChannelId(1), name = longName), status, freshStart = false)
                }
            }
        }
        compose.onNodeWithTag("main-startup-loader", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("startup-background-pending", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("startup-background-prepared", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { status = "Starting" }
        compose.settleFrames(4)
        val config = compose.onNodeWithTag("main-startup-status", useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
        assertEquals("Starting", config[SemanticsProperties.Text].single().text)
        compose.onNodeWithText(longName, substring = true, useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                action(layouts)
                assertEquals(2, layouts.single().lineCount)
                assertTrue("long channel names remain ellipsized in their slot", layouts.single().isLineEllipsized(1))
            }
        val nameBounds = compose.onNodeWithText(longName, substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val ringBounds = compose.onNodeWithTag("main-startup-loader", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("the name cannot overlap the busy ring", nameBounds.bottom < ringBounds.top)
        compose.runOnIdle { status = "Starting " + "a long localized status message ".repeat(8) }
        compose.settleFrames(4)
        compose.onNodeWithTag("main-startup-status", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                action(layouts)
                assertEquals(2, layouts.single().lineCount)
            }
        val statusBounds = compose.onNodeWithTag("main-startup-status", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val screenBounds = compose.onNodeWithTag("main-startup-root").fetchSemanticsNode().boundsInRoot
        assertTrue("status stays below the busy ring", statusBounds.top > ringBounds.bottom)
        assertTrue("status stays on screen", statusBounds.bottom < screenBounds.bottom)
    }

    private fun capture(view: View, name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertEquals(1920, bitmap.width)
            assertEquals(1080, bitmap.height)
            CaptureOutput.writePng(File("build/outputs/presentation/startup-$name.png"), bitmap)
            bitmap.recycle()
        }
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun lightOnlyPlateHasOneDensityIndependentFrameWithinTwoMiB() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val atlas = BitmapFactory.decodeResource(resources, R.drawable.startup_background_plate,
            BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 },
        )!!
        try {
            assertEquals(960, atlas.width)
            assertEquals(540, atlas.height)
            assertEquals(2_073_600, atlas.allocationByteCount)
            assertTrue(atlas.allocationByteCount <= 2 * 1024 * 1024)
            for (y in 0 until atlas.height) for (x in 0 until atlas.width) {
                val pixel = atlas.getPixel(x, y)
                assertTrue(((pixel shr 16) and 255) >= 15)
                assertTrue(((pixel shr 8) and 255) >= 16)
                assertTrue((pixel and 255) >= 20)
            }
        } finally {
            atlas.recycle()
        }
    }
}
