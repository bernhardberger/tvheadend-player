package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
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
import at.bernhardberger.tvheadend.sdk.core.ServerProfile
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's artwork context follows the session, not the observation: guide updates leave logos
 * alone, and before Ready so do updates of the same profile.
 */
@OptIn(ExperimentalTvheadendApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArtworkContextTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val requests = mutableListOf<Any>()
    private var logoCompositions = 0
    private var observationCompositions = 0

    @Test fun aGuideUpdateNeitherRecomposesALogoNorRequestsItAgain() {
        val session = FakeTvheadendSession(observation(ready, guide = 1))
        show(session)
        val current = requireNotNull(session.observation.value.currentSession)
        assertEquals(1, requests.size)
        val logos = logoCompositions
        val seen = observationCompositions

        repeat(3) { session.publish(observation(ready, guide = 2 + it)) }
        compose.waitForIdle()

        assertSame(current, session.observation.value.currentSession)
        assertTrue("the guide updates reached the composition", observationCompositions > seen)
        assertEquals(logos, logoCompositions)
        assertEquals(1, requests.size)
    }

    @Test fun beforeReadyUpdatesOfTheSameProfileNeitherRecomposeALogoNorRequestItAgain() {
        val session = FakeTvheadendSession()
        session.artwork.scriptCached(FIRST)
        session.publish(observation(SessionState.Synchronizing, guide = 1), FIRST)
        show(session)
        assertNull(session.observation.value.currentSession)
        val saved = requests.single() as AppArtworkSource
        assertNotNull("the logo is the saved copy of the observation's profile", saved.saved)
        assertNotNull("whose key names the logo's stored colour", saved.savedKey)
        val logos = logoCompositions
        val seen = observationCompositions

        repeat(3) { session.publish(observation(SessionState.Synchronizing, guide = 2 + it), FIRST) }
        compose.waitForIdle()

        assertTrue("the retained updates reached the composition", observationCompositions > seen)
        assertEquals(logos, logoCompositions)
        assertEquals(listOf<Any>(saved), requests)
    }

    @Test fun beforeReadyAnotherProfilesObservationReplacesTheLogo() {
        val session = FakeTvheadendSession()
        session.artwork.scriptCached(FIRST)
        session.publish(observation(SessionState.Synchronizing, guide = 1), FIRST)
        show(session)
        val first = requests.single() as AppArtworkSource

        session.artwork.scriptCached(SECOND)
        session.publish(observation(SessionState.Synchronizing, guide = 2), SECOND)
        compose.waitForIdle()

        val second = requests.last() as AppArtworkSource
        assertEquals(2, requests.size)
        assertNotNull(second.saved)
        assertNotEquals(first, second)
    }

    @Test fun aNewSessionReachesTheLogo() {
        val session = FakeTvheadendSession(observation(SessionState.Synchronizing, guide = 1))
        show(session)
        val logos = logoCompositions

        session.publish(observation(ready, guide = 2))
        compose.waitForIdle()

        val current = requireNotNull(session.observation.value.currentSession)
        assertTrue(logoCompositions > logos)
        assertEquals(listOf<Any>(AppArtworkSource(null, LOGO), AppArtworkSource(current, LOGO)), requests)
    }

    @Test fun aPinnedObservationShowsItsSavedLogoAgainOnceItsProfileIsActiveAfterAClear() {
        val session = FakeTvheadendSession()
        session.artwork.scriptCached(FIRST)
        session.publish(observation(SessionState.Synchronizing, guide = 1), FIRST)
        val pinned = session.observation.value
        // An explicit retry: the SDK clears the profile's saved logos while disconnected.
        session.artwork.scriptCached(null)
        session.publish(observation(SessionState.Disconnected, guide = 1), FIRST)
        show(session) { ProvideArtworkSession(null, pinned) { Logo() } }
        assertNull("no saved copy while cleared", (requests.single() as AppArtworkSource).saved)

        session.artwork.scriptCached(FIRST)
        session.publish(observation(SessionState.Synchronizing, guide = 2), FIRST)
        compose.waitForIdle()

        assertNotNull("the pinned observation's saved copy is back", (requests.last() as AppArtworkSource).saved)
    }

    private fun show(
        session: FakeTvheadendSession,
        content: @Composable () -> Unit = {
            ObservationReader(session)
            Logo()
        },
    ) {
        images.install(ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
            .components {
                add(Interceptor { chain ->
                    requests += chain.request.data
                    SuccessResult(Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).asImage(), chain.request, DataSource.MEMORY)
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .diskCache(null)
            .build())
        compose.setContent {
            ProvideAppArtwork(session, content)
        }
        compose.waitForIdle()
    }

    // Reads the artwork session like every logo and picture does.
    @Composable
    private fun Logo() {
        LocalArtworkContext.current.currentSession
        SideEffect { logoCompositions++ }
        PiconBox(LOGO, Modifier.size(60.dp, 36.dp))
    }

    // A screen of the app, which follows every observation.
    @Composable
    private fun ObservationReader(session: FakeTvheadendSession) {
        session.observation.collectAsState().value
        SideEffect { observationCompositions++ }
    }

    private fun observation(state: SessionState, guide: Int) = SessionObservation.create(
        sessionState = state,
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "One", icon = LOGO)))),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create(listOf(EpgEvent.create(EventId(guide.toLong()), ChannelId(1),
            Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(3600), title = "Programme $guide")))),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )

    private companion object {
        val LOGO = ArtworkId(1)
        val FIRST = ServerProfile("first.tvheadend.test")
        val SECOND = ServerProfile("second.tvheadend.test")
        val ready = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED))
    }
}
