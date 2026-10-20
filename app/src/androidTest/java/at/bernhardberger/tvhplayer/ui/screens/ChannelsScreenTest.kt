package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.ui.test.onRoot
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.ui.captureBrowseFrame
import androidx.compose.ui.test.assertIsDisplayed

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isDisplayed
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
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.testing.testSessionObservation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseDrawerState
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.rememberDrawerState
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import at.bernhardberger.tvhplayer.testing.ImageLoaderRule
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
class ChannelsScreenTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pendingInitialRestoreDoesNotOverrideLiveDrawerOwnership() {
        lateinit var updateChannels: (List<Channel>) -> Unit
        composeRule.setContent {
            TVHeadendPlayerTheme {
                val drawerState = rememberDrawerState(DrawerValue.Open)
                Column {
                    Button(onClick = {}) { Text("Drawer focus") }
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalBrowseDrawerState provides drawerState) {
                            TestChannelsContent(
                                initialChannels = emptyList(),
                                tags = emptyList(),
                                initialSelectedId = ChannelId(2),
                                // Simulate the old composition value before drawer feedback.
                                initialFocusEnabled = true,
                                onSelection = {},
                                onUpdateChannelsReady = { updateChannels = it },
                            )
                        }
                    }
                }
            }
        }
        val drawer = composeRule.onNodeWithText("Drawer focus")
        drawer.requestFocus().assertIsFocused()
        composeRule.runOnIdle { updateChannels(channels(1..12)) }
        composeRule.waitForIdle()
        row(2).assertExists()
        drawer.assertIsFocused()
    }

    @Test
    fun pendingRecoveryActionDoesNotOverrideLiveDrawerOwnership() {
        lateinit var showRecovery: () -> Unit
        lateinit var settleDrawerFeedback: () -> Unit
        lateinit var closeDrawer: () -> Unit
        composeRule.setContent {
            TVHeadendPlayerTheme {
                val drawerState = rememberDrawerState(DrawerValue.Open)
                var connectionState by remember {
                    mutableStateOf<ConnectionUiState>(ConnectionUiState.Connecting)
                }
                var initialFocusEnabled by remember { mutableStateOf(true) }
                SideEffect {
                    showRecovery = { connectionState = ConnectionUiState.NeedsConfiguration }
                    settleDrawerFeedback = { initialFocusEnabled = false }
                    closeDrawer = {
                        drawerState.setValue(DrawerValue.Closed)
                        initialFocusEnabled = true
                    }
                }
                Column {
                    Button(onClick = {}) { Text("Drawer focus") }
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalBrowseDrawerState provides drawerState) {
                            TestChannelsContent(
                                initialChannels = emptyList(),
                                tags = emptyList(),
                                initialSelectedId = null,
                                initialFocusEnabled = initialFocusEnabled,
                                connectionUiState = connectionState,
                                onSelection = {},
                                onUpdateChannelsReady = {},
                            )
                        }
                    }
                }
            }
        }
        val drawer = composeRule.onNodeWithText("Drawer focus")
        drawer.requestFocus().assertIsFocused()
        composeRule.runOnIdle { showRecovery() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.open_connection_settings))
            .assertExists()
        drawer.assertIsFocused()
        composeRule.runOnIdle { settleDrawerFeedback() }
        composeRule.runOnIdle { closeDrawer() }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.open_connection_settings))
            .assertIsFocused()
    }

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
    fun groupEntryStartsAtTheFirstChannel() = assertGroupEntry(playingId = null)

    @Test
    fun groupEntryPrefersPlayingChannelOnlyWhenItBelongsToTheGroup() =
        assertGroupEntry(playingId = ChannelId(40))

    @Test
    fun groupEntryPositionsPlayingChannelThatBelongsToTheGroup() =
        assertGroupEntry(playingId = ChannelId(70))

    private fun assertGroupEntry(playingId: ChannelId?) {
        val international = tag(1, "International")
        val selections = mutableListOf<ChannelId>()
        setChannelsContent(
            channels = (1..80).map { id -> channel(id, international.id.takeIf { id >= 61 }) },
            tags = listOf(international),
            initialSelectedId = ChannelId(1),
            playingChannelId = playingId,
            onSelection = selections::add,
        )
        waitForFocus(1)
        composeRule.runOnIdle { selections.clear() }
        val target = playingId?.value?.takeIf { it >= 61 } ?: 61L
        pickGroup("channels-group-${international.id.value}", steps = 1)
        waitForFocus(target)
        row(target).assertIsDisplayed()
        composeRule.onNodeWithTag("channels-detail-channel").assertTextEquals("Channel $target")
        composeRule.runOnIdle {
            assertTrue("Only the entered channel is published", selections.isNotEmpty() && selections.all { it == ChannelId(target) })
        }
    }

    @Test
    fun eachGroupReturnsToItsOwnChannel() {
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
        )
        waitForFocus(1)
        pickGroup("channels-group-${news.id.value}", steps = 1)
        waitForFocus(1)
        row(1).pressDown()
        waitForFocus(2)

        pickGroup("channels-group-${sports.id.value}", steps = 1)
        waitForFocus(3)
        pickGroup("channels-group-${news.id.value}", steps = -1)

        waitForFocus(2)
        composeRule.onNodeWithTag("channels-detail-channel").assertTextEquals("Channel 2")
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
    fun catalogueRemovalDoesNotStealFocusFromTheHeader() {
        lateinit var updateChannels: (List<Channel>) -> Unit
        val news = tag(1, "News")
        setChannelsContent(
            channels = listOf(channel(1, news.id), channel(2, news.id), channel(3, news.id)),
            tags = listOf(news),
            initialSelectedId = ChannelId(2),
            onUpdateChannelsReady = { updateChannels = it },
        )
        waitForFocus(2)
        groupButton.requestFocus().assertIsFocused()

        composeRule.runOnIdle { updateChannels(listOf(channel(1), channel(3))) }

        groupButton.assertIsFocused()
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
            updateChannels(listOf(channel(3, number = 1), channel(2), channel(1, number = 3)))
        }

        row(2).assertIsFocused()
        composeRule.onNodeWithTag("channels-detail-channel").assertTextEquals("Channel 2")
        row(2).pressDown()

        row(1).assertIsFocused()
        composeRule.runOnIdle { assertEquals(ChannelId(1), selections.last()) }
    }

    @Test
    fun interruptedPageDoesNotPublishAnUnfocusedChannel() {
        val news = tag(1, "News")
        val selections = mutableListOf<ChannelId>()
        setChannelsContent(
            channels = (1..30).map { channel(it, news.id) },
            tags = listOf(news),
            initialSelectedId = ChannelId(1),
            onSelection = selections::add,
        )
        waitForFocus(1)
        composeRule.runOnIdle { selections.clear() }
        composeRule.mainClock.autoAdvance = false
        try {
            // Up from the first row reaches the header before the page lands.
            composeRule.onRoot().performKeyInput {
                pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN))
                pressKey(Key.DirectionUp)
            }
            composeRule.mainClock.advanceTimeBy(1_000)
            groupButton.assertIsFocused()
            composeRule.runOnIdle {
                assertTrue("Cancelled paging must not publish a channel that never received focus", selections.isEmpty())
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun aPagePublishesTheChannelThatActuallyReceivesFocus() {
        val selections = mutableListOf<ChannelId>()
        setChannelsContent(channels = channels(1..3), initialSelectedId = ChannelId(1), onSelection = selections::add)
        waitForFocus(1)
        composeRule.runOnIdle { selections.clear() }
        row(1).performKeyInput { pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN)) }
        waitForFocus(3)
        row(3).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(selections.isNotEmpty() && selections.all { it == ChannelId(3) }) }
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
        row(1).performKeyInput { pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN)) }
        composeRule.waitUntil(5_000) { selections.isNotEmpty() }
        val onePageId = selections.last().value
        waitForFocus(onePageId)
        row(onePageId).performKeyInput { pressKey(Key(KeyEvent.KEYCODE_CHANNEL_UP)) }
        waitForFocus(1)
        composeRule.runOnIdle { selections.clear() }
        // Two requests before a rendered frame must advance beyond one page, while
        // only the acknowledged final row is published. Partial rows can change the
        // next page size as scrolling begins, so pixel-visible rows are not its input.
        composeRule.mainClock.autoAdvance = false
        try {
            row(1).performKeyInput {
                pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN))
                pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN))
            }
            composeRule.mainClock.advanceTimeBy(2_000)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitUntil(5_000) { selections.isNotEmpty() }
        val finalId = selections.last().value
        waitForFocus(finalId)
        row(finalId).assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue("Two page presses must advance from the pending destination", finalId > onePageId)
            assertTrue(selections.all { it == ChannelId(finalId) })
        }
        composeRule.onNodeWithTag("channels-detail-channel")
            .assertTextEquals("Channel $finalId")
    }

    @Test
    fun pageMotionMovesRowsUpOnDownAndDownOnUpThroughFocusHandoff() {
        setChannelsContent(channels = channels(1..23), initialSelectedId = ChannelId(1))
        waitForFocus(1)
        composeRule.mainClock.autoAdvance = false
        try {
            fun page(direction: Int, name: String, frames: Int = 100): Int {
                var previous = channelRowTops()
                captureBrowseFrame(composeRule.onRoot(), "channels-$name-start")
                composeRule.onRoot().performKeyInput {
                    val key = Key(if (direction > 0) KeyEvent.KEYCODE_CHANNEL_DOWN else KeyEvent.KEYCODE_CHANNEL_UP)
                    keyDown(key)
                    keyUp(key)
                }
                var movingFrames = 0
                repeat(frames) { frame ->
                    composeRule.mainClock.advanceTimeByFrame()
                    val current = channelRowTops()
                    val deltas = current.mapNotNull { (id, top) -> previous[id]?.let { top - it } }
                    assertTrue("direction=$direction frame=$frame previous=$previous current=$current", deltas.all { it * direction <= 1f })
                    if (deltas.any { it * direction < -1f }) movingFrames++
                    if (frame in listOf(2, 5, 10, 20, frames - 1)) {
                        captureBrowseFrame(composeRule.onRoot(), "channels-$name-frame-$frame")
                    }
                    previous = current
                }
                return movingFrames
            }
            fun focusedId() = composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().single()
                .config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("channel-row-").toInt()
            for (direction in listOf(1, -1)) {
                val end = if (direction > 0) 23 else 1
                var pages = 0
                var movingPages = 0
                while (focusedId() != end && pages < 10) {
                    val before = focusedId()
                    if (page(direction, "direction-$direction-page-${pages++}") >= 3) movingPages++
                    assertTrue("Page must advance focus", (focusedId() - before) * direction > 0)
                }
                assertEquals(end, focusedId())
                assertTrue("Multiple pages must visibly move", movingPages >= 2)
                if (direction > 0) {
                    assertTrue("Up must be moving before interruption", page(-1, "interrupted-up", 8) >= 3)
                    assertTrue("Down reversal must visibly move rows up", page(1, "reverse-down") >= 3)
                    assertEquals(23, focusedId())
                }
            }
            assertTrue("Down must be moving before interruption", page(1, "interrupted-down", 8) >= 3)
            assertTrue("Reversal must visibly move upward-page rows down", page(-1, "reverse-up") >= 3)
            assertEquals(1, focusedId())
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        waitForFocus(1)
    }

    @Test fun externalSelectionDoesNotStealFocusFromTheHeader() {
        lateinit var select: (ChannelId) -> Unit
        setChannelsContent(
            channels = channels(1..3),
            tags = listOf(tag(1, "News")),
            initialSelectedId = ChannelId(1),
            onUpdateSelectionReady = { select = it },
        )
        waitForFocus(1)
        groupButton.requestFocus().assertIsFocused()
        composeRule.runOnIdle { select(ChannelId(3)) }
        groupButton.assertIsFocused()
        row(3).assertIsNotFocused()
    }

    private fun channelRowTops(): Map<String, Float> {
        val viewport = composeRule.onNodeWithTag("channels-list").fetchSemanticsNode().boundsInRoot
        return composeRule
        .onAllNodes(androidx.compose.ui.test.SemanticsMatcher("channel row") {
            it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }
                .startsWith("channel-row-")
        }).fetchSemanticsNodes().filter {
            it.boundsInRoot.height > 0f && it.boundsInRoot.top >= viewport.top &&
                it.boundsInRoot.top < viewport.bottom &&
                composeRule.onNodeWithTag(it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]).isDisplayed()
        }.associate {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] to it.boundsInRoot.top
        }
    }

    @Composable
    private fun TestChannelsContent(
        initialChannels: List<Channel>,
        tags: List<ChannelTag>,
        initialSelectedId: ChannelId?,
        initialFocusEnabled: Boolean,
        connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
        onSelection: (ChannelId) -> Unit,
        onUpdateChannelsReady: ((List<Channel>) -> Unit) -> Unit,
        onUpdateSelectionReady: ((ChannelId) -> Unit) -> Unit = {},
        playingChannelId: ChannelId? = null,
    ) {
        val context = LocalContext.current
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
            onUpdateSelectionReady { selectedId = it }
        }

        ChannelsScreenContent(
            view = ChannelsView(ChannelsLayout.LIST),
            initialFocusEnabled = initialFocusEnabled,
            channelScopeState = scopeState,
            observation = testSessionObservation(channels = currentChannels, tags = tags),
            tagNotice = false,
            selectedId = { selectedId },
            playingChannelId = playingChannelId,
            connectionUiState = connectionUiState,
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
        onUpdateSelectionReady: ((ChannelId) -> Unit) -> Unit = {},
        playingChannelId: ChannelId? = null,
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
                    onUpdateSelectionReady = onUpdateSelectionReady,
                    playingChannelId = playingChannelId,
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

    private val groupButton get() = composeRule.onNodeWithTag("channels-group-button")

    /** Opens the group picker from the header and applies the group [steps] rows from the current one, Up when negative. */
    private fun pickGroup(groupTag: String, steps: Int) {
        groupButton.requestFocus().assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntilExactlyOneExists(
            isFocused() and hasAnyAncestor(hasTestTag("channels-group-panel")),
            timeoutMillis = 5_000,
        )
        repeat(abs(steps)) {
            composeRule.onRoot().performKeyInput { pressKey(if (steps < 0) Key.DirectionUp else Key.DirectionDown) }
        }
        composeRule.waitUntilExactlyOneExists(hasTestTag(groupTag) and isFocused(), timeoutMillis = 5_000)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    }

    private fun SemanticsNodeInteraction.pressDown() =
        performKeyInput { pressKey(Key.DirectionDown) }

    private fun channels(ids: IntRange) = ids.map(::channel)

    private fun channel(id: Int, tagId: ChannelTagId? = null, number: Long = id.toLong()) = Channel.create(
        id = ChannelId(id.toLong()),
        name = "Channel $id",
        number = number,
        tagIds = tagId?.let(::listOf).orEmpty(),
    )

    private fun tag(id: Int, name: String) = ChannelTag.create(
        id = ChannelTagId(id.toLong()),
        name = name,
        index = id.toLong(),
    )
}
