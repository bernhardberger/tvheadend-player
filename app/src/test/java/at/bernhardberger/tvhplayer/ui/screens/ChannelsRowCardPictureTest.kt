package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
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
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
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
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
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

    @Test fun aCardKeepsItsLogoAndProgrammeWhileThePictureLoads() {
        show()
        assertFallback()
        compose.onNodeWithTag("$CARD-art-picon", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun aPictureFadesInOverTheLogoAndProgrammeBeforeTheyLeave() {
        show()
        compose.mainClock.autoAdvance = false
        picture.complete { SuccessResult(bitmap(), it, DataSource.DISK) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        compose.onNodeWithTag("$CARD-art-picon", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("$CARD-now", useUnmergedTree = true).assertExists()
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

    @Test fun aFailedPictureLeavesTheLogoAndProgramme() {
        show()
        picture.complete { ErrorResult(null, it, IllegalStateException("Synthetic picture failure")) }
        compose.mainClock.advanceTimeBy(PlayerMotion.MediumMs + 100L)
        compose.waitForIdle()
        assertFallback()
        compose.onNodeWithTag("$CARD-art", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("$CARD-art-picon", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun assertFallback() {
        compose.onNodeWithTag("$CARD-picon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("$CARD-now", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun show() {
        val session = FakeSessionObservation(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
            )),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).captureCurrentSession()
        images.install(ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
            .components {
                add(Interceptor { chain ->
                    val model = chain.request.data as AppArtworkSource
                    if (model.id == PICTURE) picture.await()(chain.request)
                    else SuccessResult(bitmap(), chain.request, DataSource.MEMORY_CACHE)
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .diskCache(null)
            .build())
        val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101,
            icon = ChannelIcon.ImageCache(ArtworkId(1)))
        val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(NOW - 60),
            Instant.fromEpochSeconds(NOW + 600), title = "Northern Lights", image = PICTURE)
        compose.setContent {
            TVHeadendPlayerTheme {
                ProvideArtworkSession(session) {
                    Box {
                        ChannelsRowCard(
                            channel = channel,
                            event = event,
                            nowSec = NOW,
                            playbackIndicator = ChannelPlaybackIndicator.NONE,
                            recording = false,
                            onClick = {},
                            onLongClick = {},
                            programmeLoading = false,
                            programmePicture = true,
                            modifier = Modifier,
                            channelName = false,
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
    }
}
