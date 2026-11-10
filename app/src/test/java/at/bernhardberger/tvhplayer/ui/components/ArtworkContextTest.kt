package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
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
import at.bernhardberger.tvhplayer.core.programmeArtwork
import at.bernhardberger.tvhplayer.core.programmeArtworkModel
import at.bernhardberger.tvhplayer.core.recordingArtwork
import at.bernhardberger.tvhplayer.ui.screens.ProgrammeContextMenu
import at.bernhardberger.tvhplayer.ui.screens.ChannelsImmersiveBackground
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.RemoteArtworkUrl
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun failedPanePictureShowsTheLogoButASuccessfulReplacementHasNoLogoPlate() {
        val failed = ChannelIcon.parse("https://192.0.2.1/programme.png") as ChannelIcon.RemoteUrl
        val art = mutableStateOf<ChannelIcon>(failed)
        show(FakeTvheadendSession(observation(ready, 1)), failedModel = failed.url) {
            TVHeadendPlayerTheme {
                ProgrammePanePicture(art.value, ChannelIcon.ImageCache(LOGO), ChannelId(1), "pane-logo",
                    Modifier.size(268.dp, 151.dp))
            }
        }
        compose.onNodeWithTag("pane-logo").assertExists()
        assertTrue(requests.any { it is AppArtworkSource && it.id == LOGO })
        compose.runOnIdle { art.value = requireNotNull(ChannelIcon.parse("https://192.0.2.1/replacement.png")) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeUntil { compose.onAllNodesWithTag("pane-logo").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("pane-logo").assertDoesNotExist()
    }

    @Test fun failedImmersivePictureShowsTheSameLogoFallbackAsNoPicture() {
        val failed = ChannelIcon.parse("https://192.0.2.1/programme.png") as ChannelIcon.RemoteUrl
        val art = mutableStateOf<ChannelIcon?>(failed)
        val observation = observation(ready, 1)
        val refreshed = mutableStateOf(observation)
        show(FakeTvheadendSession(observation), failedModel = failed.url) {
            TVHeadendPlayerTheme {
                ChannelsImmersiveBackground(listOf(requireNotNull(observation.channel(ChannelId(1)))),
                    { ChannelId(1) }, refreshed.value, { 0L }, 0.dp, 0.dp,
                    modifier = Modifier.size(960.dp, 540.dp), picture = { art.value })
            }
        }
        compose.onNodeWithTag("channels-logo-glow").assertExists()
        compose.mainClock.advanceTimeUntil {
            compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("channels-immersive-picon").assertExists()
        compose.runOnIdle { art.value = requireNotNull(ChannelIcon.parse("https://192.0.2.1/replacement.png")) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeUntil {
            compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("channels-logo-glow").assertDoesNotExist()
        compose.onNodeWithTag("channels-immersive-picon").assertDoesNotExist()
        val attempts = requests.count { it == failed.url }
        compose.runOnIdle {
            refreshed.value = observation(ready, 2)
            art.value = failed
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeUntil {
            compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("Returning to the picture retries its transient failure", requests.count { it == failed.url } > attempts)
    }

    @Test fun failedDetailsPictureShowsTheSameLogoFallbackAsNoPicture() {
        val remote = requireNotNull(RemoteArtworkUrl.parse("https://192.0.2.1/programme.png"))
        val observation = observation(ready, 1)
        val event = requireNotNull(observation.event(EventId(1))).withRemoteImage(remote)
        show(FakeTvheadendSession(observation), failedModel = remote.url) {
            TVHeadendPlayerTheme {
                ProgrammeDetailsPage(event, null, observation.channel(ChannelId(1)), 0L, emptyList(), {}, {})
            }
        }
        compose.onNodeWithTag("details-logo-glow").assertExists()
        compose.onNodeWithTag("details-hero-logo").assertExists()
    }

    @Test fun programmeRemotePictureUsesTheRemoteLogoPath() {
        val remote = requireNotNull(RemoteArtworkUrl.parse("https://192.0.2.1/programme.png"))
        val event = EpgEvent.create(EventId(1), ChannelId(1), Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(3600))
            .withRemoteImage(remote)
        assertEquals(remote.url, programmeArtwork(event)?.let { programmeArtworkModel(it, null) })
        // The SDK makes a cache selector and a remote URL mutually exclusive for one image.
        val cached = EpgEvent.create(event.id, event.channelId, event.start, event.stop, image = LOGO)
        assertEquals(AppArtworkSource(null, LOGO), programmeArtwork(cached)?.let { programmeArtworkModel(it, null) })
        val observation = observation(ready, guide = 1)
        show(FakeTvheadendSession(observation)) {
            ProgrammeContextMenu(event, null, null, 0, false, observation.currentSession, observation,
                false, { _, _ -> }, {})
        }
        assertEquals(listOf(remote.url), requests)
    }

    @Test fun recordingPictureAndFanartPreferCacheThenRemoteAndKeepTheLogoFallback() {
        val remote = requireNotNull(RemoteArtworkUrl.parse("https://192.0.2.1/recording.png"))
        val fanart = requireNotNull(RemoteArtworkUrl.parse("https://192.0.2.1/fanart.png"))
        val entry = DvrEntry.create(DvrEntryId(1))
        assertNull(recordingArtwork(entry))
        assertEquals(ChannelIcon.parse(remote.url), recordingArtwork(entry.withRemoteImages(remote, fanart)))
        assertEquals(ChannelIcon.parse(fanart.url), recordingArtwork(entry.withRemoteImages(null, fanart)))
        assertEquals(ChannelIcon.ImageCache(LOGO), recordingArtwork(
            DvrEntry.create(entry.id, image = LOGO).withRemoteImages(null, fanart)))
        assertEquals(ChannelIcon.ImageCache(LOGO), recordingArtwork(
            DvrEntry.create(entry.id, fanartImage = LOGO).withRemoteImages(remote, null)))
    }

    @Test fun remoteLogoLoadsBeforeReadyAndKeepsItsModelAtReady() {
        val session = FakeTvheadendSession()
        val icon = ChannelIcon.parse("https://192.0.2.1/logo.png") as ChannelIcon.RemoteUrl
        show(session) { PiconBox(icon, Modifier.size(60.dp, 36.dp), ground = null) }
        assertEquals(listOf(icon.url), requests)
        session.publish(observation(ready, guide = 1))
        compose.waitForIdle()
        assertEquals(listOf(icon.url), requests)
    }

    @Test fun remoteFailureIsFinalBeforeReadyButImageCacheStillWaitsForTheSession() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        images.install(ImageLoader.Builder(app).components {
            add(Interceptor { chain -> ErrorResult(null, chain.request, IllegalStateException("Fixture failure")) })
        }.coroutineContext(Dispatchers.Main.immediate).build())
        var remoteErrors = 0
        var imageCacheErrors = 0
        compose.setContent {
            PiconBox(ChannelIcon.parse("https://192.0.2.1/logo.png"), Modifier.size(60.dp, 36.dp),
                ground = null, onError = { remoteErrors++ })
            PiconBox(ChannelIcon.ImageCache(LOGO), Modifier.size(60.dp, 36.dp),
                ground = null, onError = { imageCacheErrors++ })
        }
        compose.waitForIdle()
        assertEquals(1, remoteErrors)
        assertEquals(0, imageCacheErrors)
    }

    @Test fun aGuideUpdateNeitherRecomposesALogoNorRequestsItAgain() {
        val session = FakeTvheadendSession(observation(ready, guide = 1))
        show(session)
        assertEquals(1, requests.size)
        val logos = logoCompositions
        val seen = observationCompositions

        repeat(3) { session.publish(observation(ready, guide = 2 + it)) }
        compose.waitForIdle()

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
        failedModel: Any? = null,
        content: @Composable () -> Unit = {
            ObservationReader(session)
            Logo()
        },
    ) {
        images.install(ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
            .components {
                add(Interceptor { chain ->
                    requests += chain.request.data
                    if (chain.request.data == failedModel) {
                        ErrorResult(null, chain.request, IllegalStateException("Fixture missing image"))
                    } else {
                        SuccessResult(Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).asImage(), chain.request, DataSource.MEMORY)
                    }
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
        PiconBox(ChannelIcon.ImageCache(LOGO), Modifier.size(60.dp, 36.dp))
    }

    // A screen of the app, which follows every observation.
    @Composable
    private fun ObservationReader(session: FakeTvheadendSession) {
        session.observation.collectAsState().value
        SideEffect { observationCompositions++ }
    }

    private fun observation(state: SessionState, guide: Int) = SessionObservation.create(
        sessionState = state,
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "One", icon = ChannelIcon.ImageCache(LOGO))))),
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
