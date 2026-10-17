package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.guide.EpgSearchDialog
import java.io.File
import kotlin.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class GuideSearchDetailsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchResultDetailsBackRestoresSameResult() {
        val channel = Channel.create(ChannelId(1), name = "Test channel")
        val events = (1..2).map { EpgEvent.create(EventId(it.toLong()), channel.id,
            Instant.fromEpochSeconds(100), Instant.fromEpochSeconds(200), title = "Result $it") }
        val session = FakeTvheadendSession(SessionObservation.create(
            SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            ChannelRepositoryState.Current(ChannelCatalog.create(channels = listOf(channel))),
            EpgRepositoryState.Current(EpgSnapshot.create(events)),
            DvrRepositoryState.Current(DvrSnapshot.create())))
        val observation = session.observation.value
        val result = EpgSearchResult.Available.create(events, requireNotNull(observation.currentSession))
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, observation.currentSession?.generationIdentity) }
        lateinit var back: OnBackPressedDispatcher
        compose.setContent {
            back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            var details by remember { mutableStateOf<EpgEvent?>(null) }
            var restore by remember { mutableStateOf<EventId?>(null) }
            val mutationScope = androidx.compose.runtime.rememberCoroutineScope()
            val mutationInFlight = remember { mutableStateOf<DvrMutationAction?>(null) }
            TVHeadendPlayerTheme {
                if (details == null) EpgSearchDialog(PaddingValues(), "Result", result, false, true,
                    channelName = { channel.name }, onQueryChange = {}, onSearch = {},
                    onOpenDetails = { details = it }, onDismiss = {},
                    restoreFocusTo = restore, onFocusRestored = { restore = null })
                details?.let { event ->
                    ProgrammeDetailsHost(event, observation, observation, { observation }, { 150L },
                        fromGuide = true, showContextMenu = false, onShowDetails = {},
                        mutations = DvrMutationActions(session.dvrRepository), notices = notices,
                        mutationScope = mutationScope, mutationInFlight = mutationInFlight,
                        onPlay = { _, _ -> }, onPlayRecording = null,
                        onClose = { restore = event.id; details = null },
                        settingsStore = at.bernhardberger.tvhplayer.settings.UiSettingsStore(ApplicationProvider.getApplicationContext<Application>()))
                }
            }
        }
        compose.onNodeWithText("Result 2").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("epg-search-field").assertDoesNotExist()
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("Result 2").assertIsFocused()
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
    }

    @Test fun hiddenSearchEntryKeepsProductionWindowAndSessionGuards() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, ".git").exists() }
        val screen = File(root, "app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/EpgGridScreen.kt").readText()
        assertTrue(screen.contains("if (showSearchDialog && !detailsFromSearch)"))
        assertTrue(screen.contains("if (detailsFromSearch) {\n                closeDetails()\n            }\n            closeSearch()"))
        assertTrue(screen.contains("restoreSearchResultFocus = searchEventId"))
        assertTrue(screen.contains(".semantics { if (detailsEvent != null) hideFromAccessibility() }"))
    }
}
