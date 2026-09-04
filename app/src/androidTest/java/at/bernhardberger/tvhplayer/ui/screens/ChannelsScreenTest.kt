package at.bernhardberger.tvhplayer.ui.screens

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.waitUntilExactlyOneExists
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.testing.testSessionObservation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ChannelsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun initialRestoreUsesLongLazyKeyAndFocusesSelectedChannel() {
        setChannelsContent(
            channels = channels(1..12),
            initialSelectedId = ChannelId(10),
        )

        waitForFocus(10)
        composeRule.onNodeWithTag("channels-detail-channel").assertTextEquals("Channel 10")
    }

    @Test
    fun missingSelectedChannelFallsBackAndRestorationFinishes() {
        val selections = mutableListOf<ChannelId>()
        setChannelsContent(
            channels = channels(1..3),
            initialSelectedId = ChannelId(99),
            onSelection = selections::add,
        )

        waitForFocus(1)
        composeRule.runOnIdle { selections.clear() }
        row(1).performKeyInput { pressKey(Key.DirectionDown) }

        row(2).assertIsFocused()
        composeRule.onNodeWithTag("channels-detail-channel").assertTextEquals("Channel 2")
        composeRule.runOnIdle { assertEquals(ChannelId(2), selections.last()) }
    }

    @Test
    fun returningToTagRestoresItsLastFocusedChannel() {
        val news = tag(1, "News")
        val sports = tag(2, "Sports")
        setChannelsContent(
            channels = listOf(
                channel(1, news.id),
                channel(2, news.id),
                channel(3, sports.id),
            ),
            tags = listOf(news, sports),
            initialSelectedId = ChannelId(1),
            initialFocusEnabled = false,
        )

        composeRule.onNodeWithText("News").requestFocus().pressDown()
        waitForFocus(1)
        row(1).pressDown()
        row(2).assertIsFocused()

        composeRule.onNodeWithText("Sports").requestFocus().pressDown()
        waitForFocus(3)
        composeRule.onNodeWithText("News").requestFocus().pressDown()

        waitForFocus(2)
        composeRule.onNodeWithTag("channels-detail-channel").assertTextEquals("Channel 2")
    }

    @Test
    fun rapidTagDownRequestsOnlyRestoreTheLatestScope() {
        val news = tag(1, "News")
        val sports = tag(2, "Sports")
        val selections = mutableListOf<ChannelId>()
        setChannelsContent(
            channels = listOf(channel(1, news.id), channel(2, sports.id)),
            tags = listOf(news, sports),
            initialSelectedId = ChannelId(1),
            initialFocusEnabled = false,
            onSelection = selections::add,
        )

        composeRule.onNodeWithText("All channels").requestFocus().performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionDown)
        }

        waitForFocus(2)
        composeRule.onNodeWithTag("channels-detail-channel").assertTextEquals("Channel 2")
        composeRule.runOnIdle {
            assertEquals(ChannelId(2), selections.last())
            val finalTargetIndex = selections.indexOf(ChannelId(2))
            assertTrue(selections.drop(finalTargetIndex).all { it == ChannelId(2) })
        }
    }

    @Test
    fun removedFocusedChannelRestoresFallbackAndKeepsDetailFollowingFocus() {
        lateinit var updateChannels: (List<Channel>) -> Unit
        setChannelsContent(
            channels = channels(1..3),
            initialSelectedId = ChannelId(2),
            onUpdateChannelsReady = { updateChannels = it },
        )
        waitForFocus(2)

        composeRule.runOnIdle { updateChannels(listOf(channel(1), channel(3))) }
        waitForFocus(1)
        row(1).pressDown()

        row(3).assertIsFocused()
        composeRule.onNodeWithTag("channels-detail-channel").assertTextEquals("Channel 3")
    }

    @Test
    fun catalogueRemovalDoesNotStealFocusFromActiveTag() {
        lateinit var updateChannels: (List<Channel>) -> Unit
        setChannelsContent(
            channels = channels(1..3),
            initialSelectedId = ChannelId(2),
            onUpdateChannelsReady = { updateChannels = it },
        )
        waitForFocus(2)
        val allChannels = composeRule.onNodeWithText("All channels")
        allChannels.requestFocus().assertIsFocused()

        composeRule.runOnIdle { updateChannels(listOf(channel(1), channel(3))) }

        allChannels.assertIsFocused()
    }

    @Test
    fun retainedFocusedChannelSurvivesCatalogueReorderAndSelectionKeepsFollowingFocus() {
        lateinit var updateChannels: (List<Channel>) -> Unit
        val selections = mutableListOf<ChannelId>()
        setChannelsContent(
            channels = channels(1..3),
            initialSelectedId = ChannelId(2),
            onSelection = selections::add,
            onUpdateChannelsReady = { updateChannels = it },
        )
        waitForFocus(2)
        composeRule.runOnIdle {
            selections.clear()
            updateChannels(listOf(channel(3), channel(2), channel(1)))
        }

        row(2).assertIsFocused()
        composeRule.onNodeWithTag("channels-detail-channel").assertTextEquals("Channel 2")
        row(2).pressDown()

        row(1).assertIsFocused()
        composeRule.runOnIdle { assertEquals(ChannelId(1), selections.last()) }
    }

    @Test
    fun rapidPageKeysAdvanceFromLatestRequestedTarget() {
        val selections = mutableListOf<ChannelId>()
        setChannelsContent(
            channels = channels(1..30),
            initialSelectedId = ChannelId(1),
            onSelection = selections::add,
        )
        waitForFocus(1)
        composeRule.runOnIdle { selections.clear() }

        row(1).performKeyInput {
            pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN))
            pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            selections.size >= 2 && composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle {
            assertTrue(selections.size >= 2)
            assertTrue(selections[selections.lastIndex - 1].value < selections.last().value)
        }
        var finalId = 0L
        composeRule.runOnIdle { finalId = selections.last().value }
        waitForFocus(finalId)
        composeRule.onNodeWithTag("channels-detail-channel")
            .assertTextEquals("Channel $finalId")
    }

    @Composable
    private fun TestChannelsContent(
        initialChannels: List<Channel>,
        tags: List<ChannelTag>,
        initialSelectedId: ChannelId?,
        initialFocusEnabled: Boolean,
        onSelection: (ChannelId) -> Unit,
        onUpdateChannelsReady: ((List<Channel>) -> Unit) -> Unit,
    ) {
        val context = LocalContext.current
        val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
        var currentChannels by remember { mutableStateOf(initialChannels) }
        var activeTagId by remember { mutableStateOf<ChannelTagId?>(null) }
        var selectedId by remember { mutableStateOf(initialSelectedId) }
        val catalog = remember(currentChannels, tags) {
            ChannelCatalog.create(channels = currentChannels, tags = tags)
        }
        val scopeState = resolveChannelScopeState(
            channelState = ChannelRepositoryState.Current(catalog),
            activeTagId = activeTagId,
        )
        SideEffect {
            onUpdateChannelsReady { currentChannels = it }
        }

        ChannelsScreenContent(
            initialFocusEnabled = initialFocusEnabled,
            channelScopeState = scopeState,
            observation = testSessionObservation(channels = currentChannels, tags = tags),
            tagNotice = false,
            selectedId = selectedId,
            imageLoader = imageLoader,
            playingChannelId = null,
            connectionUiState = ConnectionUiState.Ready,
            onSelectChannel = {
                selectedId = it
                onSelection(it)
            },
            onSelectTag = { activeTagId = it },
            onDismissTagNotice = {},
            onRetryConnection = {},
            onOpenConnectionSettings = {},
            onPlay = { _, _ -> },
        )
    }

    private fun setChannelsContent(
        channels: List<Channel>,
        tags: List<ChannelTag> = emptyList(),
        initialSelectedId: ChannelId?,
        initialFocusEnabled: Boolean = true,
        onSelection: (ChannelId) -> Unit = {},
        onUpdateChannelsReady: ((List<Channel>) -> Unit) -> Unit = {},
    ) {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestChannelsContent(
                    initialChannels = channels,
                    tags = tags,
                    initialSelectedId = initialSelectedId,
                    initialFocusEnabled = initialFocusEnabled,
                    onSelection = onSelection,
                    onUpdateChannelsReady = onUpdateChannelsReady,
                )
            }
        }
    }

    private fun waitForFocus(id: Long) {
        val tag = "channel-row-$id"
        composeRule.waitUntilExactlyOneExists(
            hasTestTag(tag) and isFocused(),
            timeoutMillis = 5_000,
        )
        row(id).assertIsFocused()
    }

    private fun row(id: Long) = composeRule.onNodeWithTag("channel-row-$id")

    private fun SemanticsNodeInteraction.pressDown() =
        performKeyInput { pressKey(Key.DirectionDown) }

    private fun channels(ids: IntRange) = ids.map(::channel)

    private fun channel(id: Int, tagId: ChannelTagId? = null) = Channel.create(
        id = ChannelId(id.toLong()),
        name = "Channel $id",
        number = id.toLong(),
        tagIds = tagId?.let(::listOf).orEmpty(),
    )

    private fun tag(id: Int, name: String) = ChannelTag.create(
        id = ChannelTagId(id.toLong()),
        name = name,
        index = id.toLong(),
    )
}
