package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
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
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ProvideAppArtwork
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
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
            Channel.create(ChannelId(n.toLong()), name = "Channel $n", number = n.toLong(), icon = ArtworkId(n))
        })),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )

    private companion object {
        val FIRST = ServerProfile("first.tvheadend.test")
        val SECOND = ServerProfile("second.tvheadend.test")
    }
}
