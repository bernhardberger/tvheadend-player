package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
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
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.formatHm
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelsProgrammeDetailsTest {
    @get:Rule val compose = createComposeRule()
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()
    private val requested = mutableListOf<AppArtworkSource>()
    private var programme by mutableStateOf<EpgEvent?>(event())
    private var nextProgramme by mutableStateOf<EpgEvent?>(event(title = "Mountain Paths", start = 3600))
    private var scale by mutableStateOf(1f)
    private var artworkGate: CompletableDeferred<Unit>? = null
    private var failArtwork = false

    @Test fun artworkIsSessionBoundWithTruthfulMetadataInsideThePassiveSixteenByNineSlot() {
        show()
        detail("channels-preview-artwork").assertExists()
        assertTrue(requested.contains(AppArtworkSource(session, ArtworkId(10))))
        val image = detail("channels-preview").bounds()
        assertEquals(340f, image.width, .5f)
        assertEquals(191.25f, image.height, .5f)
        assertMetadataInside(image)
        detail("channels-detail-timing").assertTextEquals("${formatHm(0)} – ${formatHm(3600)}")
        val progress = detail("channels-detail-progress").fetchSemanticsNode()
        assertEquals(2f, progress.boundsInRoot.height, .5f)
        assertEquals(.25f, progress.config[SemanticsProperties.ProgressBarRangeInfo].current, .0001f)
        detail("channels-detail-title").assertTextEquals("Across the Blue Planet")
        detail("channels-detail-subtitle").assertTextEquals("Worlds of water")
        assertEquals(image.bottom + 4f, detail("channels-detail-title").bounds().top, .5f)
        assertEquals(detail("channels-detail-title").bounds().bottom + 4f, detail("channels-detail-subtitle").bounds().top, .5f)
        assertEquals(22f, detail("channels-detail-title").textLayout().layoutInput.style.fontSize.value, 0f)
        assertEquals(28f, detail("channels-detail-title").textLayout().layoutInput.style.lineHeight.value, 0f)
        assertEquals(16f, detail("channels-detail-subtitle").textLayout().layoutInput.style.fontSize.value, 0f)
        assertEquals(24f, detail("channels-detail-subtitle").textLayout().layoutInput.style.lineHeight.value, 0f)
        assertEquals(14f, detail("channels-detail-description").textLayout().layoutInput.style.fontSize.value, 0f)
        assertEquals(20f, detail("channels-detail-description").textLayout().layoutInput.style.lineHeight.value, 0f)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).assertCountEquals(0)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertCountEquals(0)
    }

    @Test fun loadingAndFailureKeepTheSameSlotAndContainThePiconAboveMetadata() = checkArtworkResolution(failure = true)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun enlargedLoadingAndSuccessKeepArtworkTitleAndFooterAnchors() {
        scale = 1.3f
        programme = event(title = "Die Reise der Polarlichter", subtitle = "Eine Winterexpedition durch die Arktis")
        checkArtworkResolution(failure = false)
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun enlargedLoadingAndFailureKeepArtworkTitleAndFooterAnchors() {
        scale = 1.3f
        programme = event(title = "Die Reise der Polarlichter", subtitle = "Eine Winterexpedition durch die Arktis")
        checkArtworkResolution(failure = true)
    }

    private fun checkArtworkResolution(failure: Boolean) {
        artworkGate = CompletableDeferred()
        failArtwork = failure
        show()
        val image = detail("channels-preview").bounds()
        val title = detail("channels-detail-title").bounds()
        val footer = detail("channels-detail-next").bounds()
        detail("channels-preview-fallback").assertExists()
        assertTrue(detail("channels-preview-identity").bounds().bottom < detail("channels-detail-channel").bounds().top)
        compose.runOnIdle { artworkGate!!.complete(Unit) }
        compose.waitForIdle()
        if (failure) {
            detail("channels-preview-fallback").assertExists()
            detail("channels-preview-artwork").assertDoesNotExist()
        } else {
            detail("channels-preview-artwork").assertExists()
            detail("channels-preview-fallback").assertDoesNotExist()
        }
        assertEquals(image, detail("channels-preview").bounds())
        assertEquals(title, detail("channels-detail-title").bounds())
        assertEquals(footer, detail("channels-detail-next").bounds())
        assertTextWithin(if (scale >= 1.3f) 364f else 380f)
        assertMetadataInside(image)
    }

    @Test fun noEpgUsesAnOrdinaryGlyphWithoutInventingTimingProgressOrSubtitle() {
        programme = null
        nextProgramme = null
        show(picon = null)
        detail("channels-preview-fallback").assertExists()
        detail("channels-preview-identity").assertExists()
        detail("channels-detail-title").assertTextEquals("No EPG")
        detail("channels-detail-timing").assertDoesNotExist()
        detail("channels-detail-progress").assertDoesNotExist()
        detail("channels-detail-subtitle").assertDoesNotExist()
        detail("channels-detail-next").assertDoesNotExist()
        assertTrue(requested.isEmpty())
        assertEquals(16f / 9f, detail("channels-preview").bounds().let { it.width / it.height }, .015f)
    }

    @Test fun absentSessionNeverRequestsProgrammeArtOrPicon() {
        show(currentSession = null)
        detail("channels-preview-fallback").assertExists()
        detail("channels-preview-artwork").assertDoesNotExist()
        assertTrue(requested.isEmpty())
    }

    @Test fun titlePunctuationDoesNotCreateASubtitleOrLeaveItsGap() {
        programme = event(title = "Nature: The journey — Part 1", subtitle = null, image = null)
        show()
        detail("channels-detail-title").assertTextEquals("Nature: The journey — Part 1")
        detail("channels-detail-subtitle").assertDoesNotExist()
        assertEquals(detail("channels-detail-title").bounds().bottom + 8f, detail("channels-detail-description").bounds().top, .5f)
        detail("channels-preview-fallback").assertExists()
        assertTrue(detail("channels-preview-identity").bounds().bottom < detail("channels-detail-channel").bounds().top)
    }

    @Test fun programmeChangesKeepTheArtworkSlotTitleAnchorAndBottomAlignedNext() = checkProgrammeChanges(1f)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun enlargedGermanProgrammeChangesKeepTheSameArtworkSlotAndAnchors() = checkProgrammeChanges(1.3f)

    private fun checkProgrammeChanges(fontScale: Float) {
        scale = fontScale
        show()
        val image = detail("channels-preview").bounds()
        val titleTop = detail("channels-detail-title").bounds().top
        val footer = detail("channels-detail-next").bounds()
        assertEquals(340f, image.width, .5f)
        assertEquals(191.25f, image.height, .5f)
        listOf(
            event(title = "The Remarkable Journey of the Northern Lights"),
            event(
                title = "Die außergewöhnliche Reise der Polarlichter",
                subtitle = "Eine Winterexpedition durch die Arktis",
            ),
            event(title = "Ein außergewöhnlich langer Programmtitel ".repeat(12), subtitle = "Ein gelieferter Untertitel ".repeat(12)),
            event(title = "News", subtitle = null, summary = null, image = null),
            null,
        ).forEach { updated ->
            compose.runOnIdle { programme = updated }
            compose.waitForIdle()
            assertEquals("programme changes never resize or move artwork", image, detail("channels-preview").bounds())
            assertEquals("title anchor", titleTop, detail("channels-detail-title").bounds().top, .5f)
            assertTrue("passive title never exceeds two lines", detail("channels-detail-title").textLayout().lineCount <= 2)
            assertEquals("footer anchor", footer, detail("channels-detail-next").bounds())
            assertTextWithin(if (fontScale >= 1.3f) 364f else 380f)
        }
    }

    @Test fun synopsisYieldsWholeLinesBeforeATitleThatFitsIsEllipsized() {
        show(height = 345.dp)
        val image = detail("channels-preview").bounds()
        assertEquals(3, detail("channels-detail-description").textLayout().lineCount)
        compose.runOnIdle { programme = event(title = "The Remarkable Journey of the Northern Lights") }
        compose.waitForIdle()
        assertEquals(image, detail("channels-preview").bounds())
        val title = detail("channels-detail-title").textLayout()
        assertTrue(title.lineCount > 1)
        assertFalse("synopsis yields before title", title.isLineEllipsized(title.lineCount - 1))
        val description = detail("channels-detail-description")
        val synopsis = description.textLayout()
        assertEquals(2, synopsis.lineCount)
        val finalLineHeight = synopsis.getLineBottom(1) - synopsis.getLineTop(1)
        assertTrue("a third synopsis line would collide with the footer gap",
            description.bounds().bottom + finalLineHeight + 8f > detail("channels-detail-next").bounds().top)
        assertTextWithin(345f)
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun extremeTitleAndSubtitleOmitSynopsisAndUseWholeLinesAboveTheFixedFooter() {
        programme = event(title = "Ein außergewöhnlich langer Programmtitel ".repeat(12), subtitle = "Ein gelieferter Untertitel ".repeat(12))
        scale = 1.3f
        show()
        assertTextWithin(364f)
        detail("channels-detail-description").assertDoesNotExist()
        listOf("channels-detail-title", "channels-detail-subtitle").forEach { tag ->
            val layout = detail(tag).textLayout()
            assertTrue("$tag keeps at least one supplied line", layout.lineCount >= 1)
            assertTrue("$tag ellipsizes rather than clipping", layout.isLineEllipsized(layout.lineCount - 1))
        }
        assertEquals(340f, detail("channels-preview").bounds().width, .5f)
        assertEquals(191.25f, detail("channels-preview").bounds().height, .5f)
        assertTrue(detail("channels-detail-title").textLayout().lineCount <= 2)
        assertEquals(22f, detail("channels-detail-title").textLayout().layoutInput.style.fontSize.value, 0f)
        assertEquals(16f, detail("channels-detail-subtitle").textLayout().layoutInput.style.fontSize.value, 0f)
        assertMetadataInside(detail("channels-preview").bounds())
    }

    @Test fun absentNextReservesItsAreaWithoutInventingTextOrExpandingTheSynopsis() = checkAbsentNext(1f)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun enlargedGermanAbsentNextReservesItsAreaWithoutMovingArtworkOrExpandingSynopsis() = checkAbsentNext(1.3f)

    private fun checkAbsentNext(fontScale: Float) {
        scale = fontScale
        programme = event(title = "Nachrichten", subtitle = "Schlagzeilen")
        show()
        val image = detail("channels-preview").bounds()
        val description = detail("channels-detail-description").bounds()
        val synopsisLines = detail("channels-detail-description").textLayout().lineCount
        val footer = detail("channels-detail-next").bounds()
        compose.runOnIdle { nextProgramme = null }
        compose.waitForIdle()
        detail("channels-detail-next").assertDoesNotExist()
        assertEquals(image, detail("channels-preview").bounds())
        assertEquals(description, detail("channels-detail-description").bounds())
        assertEquals(synopsisLines, detail("channels-detail-description").textLayout().lineCount)
        compose.runOnIdle { nextProgramme = event(title = "A long next programme title ".repeat(12), start = 3600) }
        compose.waitForIdle()
        assertEquals(footer.bottom, detail("channels-detail-next").bounds().bottom, .5f)
        assertTrue(detail("channels-detail-next").textLayout().isLineEllipsized(0))
        assertTextWithin(if (fontScale >= 1.3f) 364f else 380f)
    }

    @Test fun shortViewportAdaptsOnceForMinimumTypeLinesNotProgrammeContent() {
        show(height = 260.dp)
        val image = detail("channels-preview").bounds()
        val titleTop = detail("channels-detail-title").bounds().top
        assertTrue(image.height < 191.25f)
        assertTextWithin(260f)
        compose.runOnIdle { programme = event(title = "An extremely long title ".repeat(12), subtitle = "A long subtitle ".repeat(12)) }
        compose.waitForIdle()
        assertEquals(image, detail("channels-preview").bounds())
        assertEquals(titleTop, detail("channels-detail-title").bounds().top, .5f)
        assertTextWithin(260f)
        compose.runOnIdle { programme = null; nextProgramme = null }
        compose.waitForIdle()
        assertEquals(image, detail("channels-preview").bounds())
        assertEquals(titleTop, detail("channels-detail-title").bounds().top, .5f)
        detail("channels-detail-next").assertDoesNotExist()
    }

    @Test fun synopsisSkipsRepeatedSubtitleAndNormalizesSuppliedDescription() {
        assertEquals("A real synopsis\non two lines", channelsProgrammeSynopsis(event(
            subtitle = "Episode one", summary = " Episode one ", description = "A real synopsis\\non two lines",
        )))
        assertNull(channelsProgrammeSynopsis(event(subtitle = "Episode one", summary = "Episode one", description = " ")))
        assertNull(channelsProgrammeSynopsis(event(summary = "Across the Blue Planet", description = null)))
    }

    private fun show(picon: ArtworkId? = ArtworkId(1), currentSession: CurrentSessionObservation? = session, height: Dp? = null) {
        val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
            .components {
                add(Interceptor { chain ->
                    val source = chain.request.data as AppArtworkSource
                    requested += source
                    if (source.id == ArtworkId(10)) artworkGate?.await()
                    if (source.id == ArtworkId(10) && failArtwork) {
                        ErrorResult(null, chain.request, IllegalStateException("Synthetic artwork failure"))
                    } else {
                        val bitmap = if (source.id == ArtworkId(10)) FixtureArt.art("still-ridge-light")
                            else FixtureArt.picon("ridge-earth")
                        SuccessResult(bitmap.asImage(), chain.request, DataSource.MEMORY)
                    }
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .diskCache(null)
            .build()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                TVHeadendPlayerTheme {
                    Box(Modifier.size(340.dp, height ?: if (scale >= 1.3f) 364.dp else 380.dp)) {
                        ChannelsProgrammeDetails(
                            channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", icon = picon),
                            now = programme,
                            next = nextProgramme,
                            nowSec = 900,
                            imageLoader = loader,
                            currentSession = currentSession,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertMetadataInside(image: Rect) {
        listOf("channels-detail-channel", "channels-detail-timing", "channels-detail-progress").forEach { tag ->
            val bounds = detail(tag).bounds()
            assertTrue("$tag within image", bounds.left >= image.left && bounds.right <= image.right &&
                bounds.top >= image.top && bounds.bottom <= image.bottom)
        }
        val timing = detail("channels-detail-timing").textLayout()
        assertFalse("time range must not truncate", timing.didOverflowWidth || timing.didOverflowHeight)
    }

    private fun assertTextWithin(bottom: Float) {
        var previousBottom = detail("channels-preview").bounds().bottom
        listOf("channels-detail-title", "channels-detail-subtitle", "channels-detail-description", "channels-detail-next").forEach { tag ->
            if (compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) return@forEach
            val node = detail(tag)
            val bounds = node.bounds()
            val layout = node.textLayout()
            val gap = if (tag == "channels-detail-description" || tag == "channels-detail-next") 8f else 4f
            assertTrue("$tag below previous content", bounds.top >= previousBottom + gap - .5f)
            assertTrue("$tag keeps whole final line", layout.getLineBottom(layout.lineCount - 1) <= bounds.height + .5f)
            assertTrue("$tag inside bottom safe inset", bounds.bottom <= bottom + .5f)
            previousBottom = bounds.bottom
        }
        assertEquals("Next is pinned to the available column bottom", bottom, detail("channels-detail-next").bounds().bottom, .5f)
        val image = detail("channels-preview").bounds()
        assertEquals(16f / 9f, image.width / image.height, .015f)
    }

    private fun detail(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot
    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun event(
        title: String = "Across the Blue Planet",
        subtitle: String? = "Worlds of water",
        image: String? = "imagecache/10",
        summary: String? = "Follow a small team of naturalists through remote landscapes as they explore the changing seasons and discover the wildlife that survives at the edge of the world.",
        description: String? = null,
        start: Long = 0,
    ): EpgEvent = EpgEvent.create(
        EventId(1), ChannelId(1), Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(start + 3600),
        title = title, subtitle = subtitle, image = image, summary = summary, description = description,
    )
}
