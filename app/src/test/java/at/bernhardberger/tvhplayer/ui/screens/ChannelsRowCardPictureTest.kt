package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ArtworkLoader
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.images.ArtworkLogoCorner
import at.bernhardberger.tvhplayer.images.ArtworkLogoGround
import at.bernhardberger.tvhplayer.images.ArtworkLogoPlace
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.components.ArtworkContext
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.ArtworkCornerLogo
import at.bernhardberger.tvhplayer.ui.components.LocalArtworkContext
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.transformations
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The card's logo and programme stay until its picture has arrived and faded in over them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelsRowCardPictureTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()

    private val picture = CompletableDeferred<(coil3.request.ImageRequest) -> ImageResult>()
    private val channelOnlyRecording = mutableStateOf(false)
    private val programmeRecording = mutableStateOf(false)

    @Test fun aCardKeepsItsLogoAndProgrammeWhileThePictureLoads() {
        show()
        assertFallback()
        compose.onNodeWithTag("$CARD-art-picon", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun aPictureFadesInOverTheLogoAndProgrammeBeforeTheyLeave() {
        show(recording = true)
        compose.onNodeWithTag("recording-badge", useUnmergedTree = true).assertIsDisplayed()
        val recordingDescription = ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.recording_state_recording)
        compose.onNode(hasContentDescription(recordingDescription), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("channel-recording-indicator", useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        picture.complete { SuccessResult(bitmap(), it, DataSource.DISK) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        compose.onNodeWithTag("$CARD-art-picon", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("$CARD-now", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription(recordingDescription), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("channel-recording-indicator", useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(PlayerMotion.MediumMs + 100L)
        compose.waitForIdle()
        compose.onNodeWithTag("$CARD-now", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("$CARD-picon", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun aPictureFromMemoryNeedsNoFade() {
        picture.complete { SuccessResult(bitmap(), it, DataSource.MEMORY_CACHE) }
        show()
        compose.onNodeWithTag("$CARD-now", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("$CARD-art-picon", useUnmergedTree = true).assertExists()
    }

    @Test fun aFailedLogoLeavesTheChannelNameOnThePicture() {
        picture.complete { SuccessResult(bitmap(), it, DataSource.MEMORY_CACHE) }
        show(logoFails = true)

        compose.onNodeWithTag("$CARD-art", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("$CARD-art-name", useUnmergedTree = true).assertTextEquals(CHANNEL_NAME)
        compose.onNodeWithTag("$CARD-art-picon", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun aFailedLogoLeavesTheChannelNameOnACardWithoutAPicture() {
        show(programmePicture = false, logoFails = true)

        compose.onNodeWithTag("$CARD-picon", useUnmergedTree = true).assertTextEquals(CHANNEL_NAME)
    }

    @Test fun darkLogoKeepsItsInkOnAShadeRegardlessOfItsFirstGround() {
        val lightFirstGround = mutableStateOf(ArtworkLogoGround.LIGHT_PICTURE)
        val darkFirst = ArtworkId(21)
        val lightFirst = ArtworkId(22)
        val inkByLogo = mutableMapOf<ArtworkId, Int>()
        val darkLogo = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val lightLogo = darkLogo.copy(Bitmap.Config.ARGB_8888, true).apply { eraseColor(Color.WHITE) }
        val replaced = mutableStateOf(false)
        var olderVariant = false
        val fake = FakeTvheadendSession(readyObservation())
        val session = object : TvheadendSession by fake {
            override val artwork = object : ArtworkLoader by fake.artwork {
                override fun cacheKey(currentSession: CurrentSessionObservation, artworkId: ArtworkId) =
                    "tone-test:${artworkId.value}"
            }
        }
        val artwork = ArtworkContext(
            session, mutableStateOf(fake.captureCurrentSession()), mutableStateOf(fake.observation.value),
        )
        images.install(ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
            .components {
                add(Interceptor { chain ->
                    val request = chain.request
                    val id = (request.data as AppArtworkSource).id
                    val source = if (id == lightFirst && replaced.value && !olderVariant) lightLogo else darkLogo
                    val logo = request.transformations.fold(source) { bitmap, transform ->
                        transform.transform(bitmap, coil3.size.Size.ORIGINAL)
                    }
                    inkByLogo[id] = logo.getPixel(logo.width / 2, logo.height / 2)
                    SuccessResult(logo.asImage(), request, DataSource.MEMORY_CACHE)
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .diskCache(null)
            .build())
        compose.setContent {
            TVHeadendPlayerTheme {
                CompositionLocalProvider(LocalArtworkContext provides artwork) {
                    Box(Modifier.size(220.dp, 124.dp)) {
                        ArtworkCornerLogo(ChannelIcon.ImageCache(darkFirst),
                            ArtworkLogoPlace(ArtworkLogoCorner.BOTTOM_START, ArtworkLogoGround.SHADE), scale = 1f)
                        key(replaced.value) {
                            ArtworkCornerLogo(ChannelIcon.ImageCache(lightFirst),
                                ArtworkLogoPlace(ArtworkLogoCorner.TOP_END, lightFirstGround.value), scale = 1f)
                        }
                    }
                }
            }
        }
        compose.runOnIdle { lightFirstGround.value = ArtworkLogoGround.SHADE }
        compose.runOnIdle {
            assertEquals("dark ground first", Color.BLACK, inkByLogo[darkFirst])
            assertEquals("white ground first", Color.BLACK, inkByLogo[lightFirst])
            replaced.value = true
        }
        compose.runOnIdle {
            assertEquals("replaced logo keeps its white ink", Color.WHITE, inkByLogo[lightFirst])
            // The other ground can still have an older revision of the logo in memory.
            olderVariant = true
            lightFirstGround.value = ArtworkLogoGround.LIGHT_PICTURE
        }
        compose.runOnIdle {
            olderVariant = false
            lightFirstGround.value = ArtworkLogoGround.SHADE
        }
        compose.runOnIdle { assertEquals("older variant does not change the shade", Color.WHITE, inkByLogo[lightFirst]) }
    }

    @Test fun aChannelOnlyRecordingUsesThePictureCornerWithoutAddingCardHeight() {
        picture.complete { SuccessResult(bitmap(), it, DataSource.MEMORY_CACHE) }
        show()
        val card = compose.onNodeWithTag(CARD, useUnmergedTree = true)
        val bounds = card.getUnclippedBoundsInRoot()
        compose.runOnIdle { channelOnlyRecording.value = true }
        assertEquals(bounds, card.getUnclippedBoundsInRoot())
        compose.onNodeWithTag("$CARD-identity", useUnmergedTree = true).assertDoesNotExist()
        val badge = assertChannelBadge()
        val position = badge.getUnclippedBoundsInRoot()
        compose.runOnIdle { programmeRecording.value = true }
        badge.assert(hasContentDescription(ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.recording_state_recording)))
        assertEquals(position, badge.getUnclippedBoundsInRoot())
        assertEquals(bounds, card.getUnclippedBoundsInRoot())
    }

    @Test fun aLogoLedCardUsesAChannelBadgeWithoutChangingItsLayout() {
        show(programmePicture = false)
        val card = compose.onNodeWithTag(CARD, useUnmergedTree = true)
        val logo = compose.onNodeWithTag("$CARD-picon", useUnmergedTree = true)
        val cardBounds = card.getUnclippedBoundsInRoot()
        val logoBounds = logo.getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithTag("recording-badge", useUnmergedTree = true)
        badge.assertDoesNotExist()

        compose.runOnIdle { channelOnlyRecording.value = true }

        assertChannelBadge()
        assertEquals(cardBounds, card.getUnclippedBoundsInRoot())
        assertEquals(logoBounds, logo.getUnclippedBoundsInRoot())
    }

    @Test fun aFailedPictureLeavesTheLogoAndProgramme() {
        channelOnlyRecording.value = true
        show()
        picture.complete { ErrorResult(null, it, IllegalStateException("Synthetic picture failure")) }
        compose.mainClock.advanceTimeBy(PlayerMotion.MediumMs + 100L)
        compose.waitForIdle()
        assertFallback()
        assertChannelBadge()
        compose.onNodeWithTag("$CARD-identity", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("$CARD-art", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("$CARD-art-picon", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun assertFallback() {
        compose.onNodeWithTag("$CARD-picon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("$CARD-now", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun assertChannelBadge() = compose.onNodeWithTag("recording-badge", useUnmergedTree = true)
        .assertIsDisplayed().assert(hasContentDescription(ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.channel_recording_spoken))).also {
            compose.onNodeWithTag("channel-recording-indicator", useUnmergedTree = true).assertDoesNotExist()
        }

    private fun readyObservation() = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
        )),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )

    private fun show(recording: Boolean = false, programmePicture: Boolean = true, logoFails: Boolean = false) {
        val session = FakeSessionObservation(readyObservation()).captureCurrentSession()
        images.install(ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
            .components {
                add(Interceptor { chain ->
                    val model = chain.request.data as AppArtworkSource
                    when {
                        model.id == PICTURE -> picture.await()(chain.request)
                        logoFails -> ErrorResult(null, chain.request, IllegalStateException("Synthetic logo failure"))
                        else -> SuccessResult(bitmap(), chain.request, DataSource.MEMORY_CACHE)
                    }
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .diskCache(null)
            .build())
        val channel = Channel.create(ChannelId(1), name = CHANNEL_NAME, number = 101,
            icon = ChannelIcon.ImageCache(ArtworkId(1)))
        val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(NOW - 60),
            Instant.fromEpochSeconds(NOW + 600), title = "Northern Lights", image = PICTURE)
        compose.setContent {
            TVHeadendPlayerTheme {
                ProvideArtworkSession(session) {
                    Box(Modifier.testTag(CARD)) {
                        ChannelsRowCard(
                            channel = channel,
                            event = event,
                            nowSec = NOW,
                            playbackIndicator = ChannelPlaybackIndicator.NONE,
                            channelRecording = recording || channelOnlyRecording.value,
                            onClick = {},
                            onLongClick = {},
                            programmeLoading = false,
                            programmePicture = programmePicture,
                            modifier = Modifier,
                            channelName = false,
                            programmeRecording = if (recording || programmeRecording.value) DvrEntry.create(DvrEntryId(1),
                                eventId = event.id, state = DvrEntryState.RECORDING) else null,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bitmap() = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888).apply {
        eraseColor(0xFF336699.toInt())
    }.asImage()

    private companion object {
        val PICTURE = ArtworkId(7)
        const val NOW = 1_800_000_000L
        const val CARD = "channels-immersive-1"
        const val CHANNEL_NAME = "Ridge Earth HD"
    }
}
