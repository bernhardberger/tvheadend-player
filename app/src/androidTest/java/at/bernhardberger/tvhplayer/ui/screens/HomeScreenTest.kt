package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.HomeCardItem
import at.bernhardberger.tvhplayer.core.HomeDashboardModel
import at.bernhardberger.tvhplayer.core.HomeHeroSlide
import at.bernhardberger.tvhplayer.core.HomeRow
import at.bernhardberger.tvhplayer.core.HomeRowKind
import at.bernhardberger.tvhplayer.core.HomeSlideKind
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val imageLoader: ImageLoader by lazy {
        ImageLoader(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun initialFocusLandsOnHeroPrimaryAction() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                HomeDashboard(
                    model = sampleModel(),
                    connectionUiState = ConnectionUiState.Ready,
                    imageLoader = imageLoader,
                    onRetryConnection = {},
                    onPlayChannel = { _, _, _ -> },
                    onPlayRecording = {},
                    onOpenRecordings = {},
                    onOpenChannels = {},
                )
            }
        }

        composeRule.onNodeWithTag("home-hero-primary").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithTag("home-row-recent").assertIsDisplayed()
        composeRule.onNodeWithTag("home-card-recent-2").assertIsDisplayed()
    }

    @Test
    fun downFromHeroReachesFirstContentRow() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                HomeDashboard(
                    model = sampleModel(),
                    connectionUiState = ConnectionUiState.Ready,
                    imageLoader = imageLoader,
                    onRetryConnection = {},
                    onPlayChannel = { _, _, _ -> },
                    onPlayRecording = {},
                    onOpenRecordings = {},
                    onOpenChannels = {},
                )
            }
        }

        composeRule.onNodeWithTag("home-hero-primary").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("home-card-recent-2").assertIsFocused()
    }

    @Test
    fun contentRowViewportReachesBothScreenEdges() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                HomeDashboard(
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        top = 32.dp,
                        end = 48.dp,
                        bottom = 32.dp,
                    ),
                    model = sampleModel(),
                    connectionUiState = ConnectionUiState.Ready,
                    imageLoader = imageLoader,
                    onRetryConnection = {},
                    onPlayChannel = { _, _, _ -> },
                    onPlayRecording = {},
                    onOpenRecordings = {},
                    onOpenChannels = {},
                )
            }
        }

        val screenBounds = composeRule.onNodeWithTag("home-screen")
            .fetchSemanticsNode().boundsInRoot
        val rowBounds = composeRule.onNodeWithTag("home-row-recent")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(screenBounds.left, rowBounds.left, 1f)
        assertEquals(screenBounds.right, rowBounds.right, 1f)
    }

    @Test
    fun emptyRowsAreOmittedFromComposition() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                HomeDashboard(
                    model = HomeDashboardModel(
                        hero = listOf(heroSlide(channelId = 1, title = "News")),
                        rows = emptyList(),
                    ),
                    connectionUiState = ConnectionUiState.Ready,
                    imageLoader = imageLoader,
                    onRetryConnection = {},
                    onPlayChannel = { _, _, _ -> },
                    onPlayRecording = {},
                    onOpenRecordings = {},
                    onOpenChannels = {},
                )
            }
        }

        composeRule.onNodeWithTag("home-hero-carousel").assertIsDisplayed()
        composeRule.onNodeWithTag("home-row-recent").assertDoesNotExist()
        composeRule.onNodeWithTag("home-row-on_now").assertDoesNotExist()
        composeRule.onNodeWithTag("home-empty-state").assertDoesNotExist()
    }

    @Test
    fun emptyStateFocusesStatusAction() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                HomeDashboard(
                    model = HomeDashboardModel(hero = emptyList(), rows = emptyList()),
                    connectionUiState = ConnectionUiState.Ready,
                    imageLoader = imageLoader,
                    onRetryConnection = {},
                    onPlayChannel = { _, _, _ -> },
                    onPlayRecording = {},
                    onOpenRecordings = {},
                    onOpenChannels = {},
                )
            }
        }

        composeRule.onNodeWithTag("home-status-action").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
    }

    private fun sampleModel() = HomeDashboardModel(
        hero = listOf(heroSlide(channelId = 1, title = "Evening news")),
        rows = listOf(
            HomeRow(
                kind = HomeRowKind.RECENT,
                items = listOf(
                    HomeCardItem(
                        key = "recent-2",
                        channelId = 2,
                        channelNumber = 2,
                        channelName = "ORF2",
                        piconPath = null,
                        title = "Sport",
                        remainingMinutes = 12,
                        progress = 0.4f,
                        startSec = 900,
                        stopSec = 1_100,
                        recordingId = null,
                        recordingNow = false,
                        playable = true,
                    ),
                ),
            ),
        ),
    )

    private fun heroSlide(channelId: Int, title: String) = HomeHeroSlide(
        kind = HomeSlideKind.CONTINUE,
        channelId = channelId,
        channelNumber = channelId,
        channelName = "Ch$channelId",
        piconPath = null,
        title = title,
        subtitle = null,
        startSec = 900,
        stopSec = 1_100,
        progress = 0.5f,
        nextTitle = "Weather",
        nextStartSec = 1_100,
        recordingId = null,
        playable = true,
    )
}
