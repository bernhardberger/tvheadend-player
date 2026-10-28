package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerProfile
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ProvideAppArtwork
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Channels rows come from a scope republished after the observation changed, so during a
 * profile switch they can meet the next profile's observation: their saved logos stay those of
 * the observation the scope was resolved from.
 */
@OptIn(ExperimentalTvheadendApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class ChannelScopeArtworkTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val requests = mutableListOf<AppArtworkSource>()

    @Test fun immersiveBackdropCanLoadARemoteLogoBeforeReady() {
        val icon = ChannelIcon.parse("https://192.0.2.1/backdrop.png") as ChannelIcon.RemoteUrl
        val models = showBackdrop(icon)
        compose.onNodeWithTag("channels-logo-glow").assertExists()
        assertTrue(models.isNotEmpty())
        assertTrue(models.all { it == icon.url })
    }

    @Test fun immersiveBackdropWaitsForAnImageCacheLogoWithoutASavedCopy() {
        val models = showBackdrop(ChannelIcon.ImageCache(ArtworkId(1)))
        compose.onNodeWithTag("channels-logo-glow").assertDoesNotExist()
        assertTrue(models.isEmpty())
    }

    private fun showBackdrop(icon: ChannelIcon): List<Any> {
        val models = java.util.Collections.synchronizedList(mutableListOf<Any>())
        images.install(ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
            .components {
                add(Interceptor { chain ->
                    models += chain.request.data
                    ErrorResult(null, chain.request, IllegalStateException("Fixture missing image"))
                })
            }.coroutineContext(Dispatchers.Main.immediate).diskCache(null).build())
        compose.setContent {
            TVHeadendPlayerTheme {
                ChannelsImmersiveBackground(
                    channels = listOf(Channel.create(ChannelId(1), icon = icon)),
                    channelId = { ChannelId(1) }, observation = SessionObservation.create(),
                    nowSec = { 0L }, logoTop = 0.dp, endInset = 0.dp, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
        return models
    }

    @Test fun rowsOfTheFirstProfilesScopeNeverShowTheSecondProfilesSavedLogos() {
        val session = FakeTvheadendSession()
        session.artwork.scriptCached(FIRST)
        session.publish(observation(), FIRST)
        val first = session.observation.value
        session.artwork.scriptCached(SECOND)
        session.publish(observation(), SECOND)

        show(session, scopeFrom = first)

        assertTrue("the rows asked for their logos", requests.isNotEmpty())
        assertTrue("no row has the second profile's saved copy", requests.none { it.saved != null })
    }

    @Test fun rowsOfTheShownProfilesScopeShowItsSavedLogos() {
        val session = FakeTvheadendSession()
        session.artwork.scriptCached(FIRST)
        session.publish(observation(), FIRST)

        show(session, scopeFrom = session.observation.value)

        assertTrue(requests.isNotEmpty())
        assertTrue(requests.all { it.saved != null })
    }

    private fun show(session: FakeTvheadendSession, scopeFrom: SessionObservation) {
        images.install(ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
            .components {
                add(Interceptor { chain ->
                    requests += chain.request.data as AppArtworkSource
                    SuccessResult(Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).asImage(), chain.request, DataSource.MEMORY)
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .diskCache(null)
            .build())
        val latest = session.observation.value
        compose.setContent {
            TVHeadendPlayerTheme {
                ProvideAppArtwork(session) {
                    ChannelsScreenContent(
                        view = ChannelsView(ChannelsLayout.LIST),
                        channelScopeState = resolveChannelScopeState(scopeFrom.channelState, null).copy(observation = scopeFrom),
                        observation = latest,
                        tagNotice = false,
                        selectedId = { null },
                        playingChannelId = null,
                        connectionUiState = ConnectionUiState.Connecting,
                        onSelectChannel = {},
                        onSelectTag = {},
                        onDismissTagNotice = {},
                        onRetryConnection = {},
                        onOpenConnectionSettings = {},
                        onPlay = { _, _ -> },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun observation() = SessionObservation.create(
        sessionState = SessionState.Synchronizing,
        channelState = ChannelRepositoryState.Stale(ChannelCatalog.create((1..3).map { n ->
            Channel.create(ChannelId(n.toLong()), name = "Channel $n", number = n.toLong(), icon = ChannelIcon.ImageCache(ArtworkId(n)))
        })),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )

    private companion object {
        val FIRST = ServerProfile("first.tvheadend.test")
        val SECOND = ServerProfile("second.tvheadend.test")
    }
}
