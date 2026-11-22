package at.bernhardberger.tvhplayer.touch.channels

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationReselections
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.NEUTRAL_ACCENT_RGB
import at.bernhardberger.tvhplayer.images.ChannelAccents
import at.bernhardberger.tvhplayer.images.LogoInkTransformation
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.touch.R
import androidx.compose.material3.Text
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.touch.testutil.LogoMark
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.assertLogoFitsItsShare
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.testutil.captureTouchWindows
import at.bernhardberger.tvhplayer.touch.testutil.markedLogos
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.ArtworkImage
import at.bernhardberger.tvhplayer.touch.ui.ChannelLogoTile
import at.bernhardberger.tvhplayer.touch.ui.LocalChannelAccents
import at.bernhardberger.tvhplayer.touch.ui.TouchDarkColors
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.transformations
import coil3.size.Size
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletChannels() {
        val list = channelsFixture(playing = true)
        val state = mutableStateOf(ChannelsState.Ready(list))
        val taps = MutableSharedFlow<Destination>(extraBufferCapacity = 1)
        var played: String? = null
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
                CompositionLocalProvider(LocalDestinationReselections provides taps) {
                    ChannelsScreen(
                        state.value, fixtureActions(onPlay = { played = it }),
                        lastWatchedId = "amber-stage",
                        pane = { id, modifier ->
                            Text(state.value.list.rows.first { it.id == id }.name, modifier.testTag("channel-pane"))
                        },
                    )
                }
            }
        }
        val pane = compose.onNodeWithTag("channel-pane")
        val amberRow = compose.onNode(isSelected() and hasText("Amber Stage"))
        compose.onNode(hasClickAction() and hasText("Amber Stage")).performClick()
        amberRow.assertIsDisplayed()
        pane.assertIsDisplayed()
        capture("tablet-channels", TouchCanvas.TABLET)
        val ridge = compose.onNode(
            hasText("Ridge Earth") and !hasTestTag("channel-pane"), useUnmergedTree = true,
        )
        val watchRidgeLabel = "Continue watching Ridge Earth"
        val watchRidge = compose.onNodeWithContentDescription(watchRidgeLabel)
        val ridgeLogo = compose.onNode(
            hasTestTag("channel-logo-tile") and hasAnyAncestor(hasClickAction() and hasContentDescription(watchRidgeLabel)),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertTrue("Watch is on the logo tile in Wide", ridgeLogo.contains(watchRidge.fetchSemanticsNode().boundsInRoot.center))
        watchRidge.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("ridge-earth", played)
            played = null
        }
        amberRow.assertIsDisplayed()
        ridge.performTouchInput { click() }
        compose.runOnIdle { assertNull("Selecting the pane must not tune", played) }
        compose.onNode(isSelected() and hasText("Ridge Earth")).assertIsDisplayed()
        capture("tablet-channels-playing-selected", TouchCanvas.TABLET)
        compose.onNode(hasClickAction() and hasText("Amber Stage")).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(list.rows.lastIndex)
        ridge.assertIsNotDisplayed()
        compose.runOnIdle { check(taps.tryEmit(Destination.Channels)) }
        ridge.assertIsDisplayed()
        amberRow.assertIsDisplayed()
        ridge.performTouchInput { click() }

        val longTitle = "Harbor Kickoff: " + "Harbor FC meet Amber Town in a floodlit stadium. ".repeat(8)
        compose.runOnIdle {
            state.value = ChannelsState.Ready(
                list.copy(rows = list.rows.map {
                    if (it.id == "harbor-sport") it.copy(now = it.now?.copy(title = longTitle)) else it
                }),
            )
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(longTitle, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().isLineEllipsized(0))
        compose.onNodeWithContentDescription("Recording", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Recording scheduled", useUnmergedTree = true).assertIsDisplayed()

        val harbor = compose.onNodeWithText("Harbor Sport", useUnmergedTree = true)
        val tiles = compose.onAllNodes(hasTestTag("channel-logo-tile"), useUnmergedTree = true)
        val withColumn = ridge.getBoundsInRoot().left
        compose.runOnIdle { state.value = ChannelsState.Ready(list.withoutLogos("ridge-earth")) }
        assertEquals(withColumn, ridge.getBoundsInRoot().left)
        assertEquals(harbor.getBoundsInRoot().left, ridge.getBoundsInRoot().left)
        val name = ridge.fetchSemanticsNode().boundsInRoot
        val tile = tiles.fetchSemanticsNodes().single {
            name.center.y in it.boundsInRoot.top..it.boundsInRoot.bottom && it.boundsInRoot.right < name.left
        }
        assertTrue("The row without a logo still has a leading tile", tile.boundsInRoot.right < name.left)
        capture("tablet-channels-some-logos", TouchCanvas.TABLET)
        compose.runOnIdle { state.value = ChannelsState.Ready(list.withoutLogos()) }
        assertTrue("Fallback tiles remain when the entire list has no logos", tiles.fetchSemanticsNodes().isNotEmpty())
        assertEquals(withColumn, ridge.getBoundsInRoot().left)
        assertEquals(harbor.getBoundsInRoot().left, ridge.getBoundsInRoot().left)
        capture("tablet-channels-no-logos", TouchCanvas.TABLET)

        compose.runOnIdle { state.value = ChannelsState.Ready(list.copy(rows = list.rows.drop(1), selectedTagId = "sport")) }
        amberRow.assertIsDisplayed()
        compose.runOnIdle { state.value = ChannelsState.Ready(list.copy(rows = emptyList(), selectedTagId = "news")) }
        pane.assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.channels_empty_tag)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun lastWatchedTakesOverFromAutomaticSelectionButNotFromAKeptPick() {
        val list = channelsFixture(playing = true)
        val state = mutableStateOf(ChannelsState.Ready(list))
        val lastWatched = mutableStateOf<String?>(null)
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
                ChannelsScreen(state.value, fixtureActions(), lastWatchedId = lastWatched.value)
            }
        }
        val ridge = compose.onNode(isSelected() and hasText("Ridge Earth"))
        val amber = compose.onNode(isSelected() and hasText("Amber Stage"))
        val harbor = compose.onNode(isSelected() and hasText("Harbor Sport"))
        ridge.assertIsDisplayed()
        compose.runOnIdle { lastWatched.value = "harbor-sport" }
        ridge.assertIsDisplayed()

        compose.runOnIdle { state.value = ChannelsState.Ready(list.copy(rows = list.rows.drop(1), selectedTagId = "sport")) }
        harbor.assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Amber Stage")).performClick()
        amber.assertIsDisplayed()
        compose.runOnIdle { state.value = ChannelsState.Ready(list) }
        amber.assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    fun uprightChannels() {
        val state = ChannelsState.Ready(channelsFixture(playing = true))
        val width = mutableStateOf(800.dp)
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
                ChannelsScreen(state, fixtureActions(), Modifier.width(width.value))
            }
        }
        capture("tablet-upright-channels", TouchCanvas.TABLET_UPRIGHT)

        // The wide canvas can hit the old track's upper cap for both labels and hide the defect.
        compose.runOnIdle { width.value = 700.dp }
        val shortTimeLeft = compose.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo(0.66f, 0f..1f)), useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val longTimeLeft = compose.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo(0.16f, 0f..1f)), useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertEquals("Progress starts align for 30m and 1h 28m left", shortTimeLeft.left, longTimeLeft.left, 0f)
        assertEquals("Progress ends align for 30m and 1h 28m left", shortTimeLeft.right, longTimeLeft.right, 0f)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneChannels() {
        var opened: String? = null
        var played: String? = null
        val openEnabled = mutableStateOf(true)
        val fixture = channelsFixture(playing = true).withoutLogos("northline-news")
        val rows = fixture.rows.map {
            when (it.id) {
                "harbor-lights" -> it.copy(next = it.next?.copy(title = "Dockside Sessions: Portside"))
                "lantern-hour" -> it.copy(now = null)
                else -> it
            }
        }
        val selected = mutableStateOf<String?>(null)
        val playingRecords = mutableStateOf(false)
        var stockTrack = Color.Unspecified
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
                val track = ProgressIndicatorDefaults.linearTrackColor
                SideEffect { stockTrack = track }
                val displayedRows = rows.map {
                    if (playingRecords.value && it.playback != null) it.copy(recording = ChannelRecording.Program) else it
                }
                val list = if (selected.value == null) fixture.copy(rows = displayedRows)
                else fixture.copy(rows = rows.filter { it.id == "northline-news" }, selectedTagId = selected.value)
                ChannelsScreen(
                    ChannelsState.Ready(list),
                    fixtureActions(
                        onPlay = { played = it },
                        onOpen = if (openEnabled.value) ({ opened = it }) else null,
                        onTag = { selected.value = it },
                    ),
                )
            }
        }
        val amber = compose.onNode(hasClickAction() and hasText("Amber Stage"))
        amber.assertIsDisplayed()
        val allCount = compose.activity.resources.getQuantityString(R.plurals.channels_count, rows.size, rows.size)
        compose.onNodeWithText(allCount).assertIsDisplayed()
        val playing = rows.single { it.playback != null }
        compose.onNodeWithContentDescription("Continue watching ${playing.name}").assertIsDisplayed()
        val playingState = SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, compose.activity.getString(R.string.channels_playing),
        )
        compose.onNode(hasText(playing.name) and hasClickAction() and playingState).assertIsDisplayed()
        val bars = compose.onNode(playingState, useUnmergedTree = true)
        bars.assertIsDisplayed()
        val progressPixels = compose.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo(0.71f, 0f..1f)), useUnmergedTree = true,
        ).captureToImage().toPixelMap()
        assertEquals(stockTrack.toArgb(), progressPixels[progressPixels.width * 4 / 5, progressPixels.height / 2].toArgb())
        compose.onNodeWithText("Amber Stage", useUnmergedTree = true).performTouchInput { click() }
        val missingGuideRow = compose.onNode(hasClickAction() and hasText("Lantern Hour")).getBoundsInRoot()
        val populatedRow = amber.getBoundsInRoot()
        assertEquals(populatedRow.bottom - populatedRow.top, missingGuideRow.bottom - missingGuideRow.top)
        compose.runOnIdle {
            assertEquals("amber-stage", opened)
            assertNull("Tapping the channel name must not tune it", played)
            opened = null
        }
        compose.onNodeWithContentDescription(watchLabel("Amber Stage")).performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("amber-stage", played)
            assertNull("Watch must not open the channel page", opened)
            openEnabled.value = false
        }
        amber.assertDoesNotExist()
        compose.onNodeWithContentDescription("Continue watching ${playing.name}").performClick()
        compose.runOnIdle { assertEquals("ridge-earth", played) }
        compose.onNodeWithText("9:00 PM", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Dockside Sessions", substring = true).assertIsDisplayed()
        compose.onNodeWithText("10:00 PM", substring = true).assertDoesNotExist()
        compose.onNodeWithText("The Reading Room", substring = true).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(SharedR.string.no_epg)).assertIsDisplayed()
        capture("phone-channels", TouchCanvas.PHONE)
        compose.runOnIdle { playingRecords.value = true }
        val barsBounds = bars.fetchSemanticsNode().boundsInRoot
        val nameBounds = compose.onNodeWithText(playing.name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val recordingBounds = compose.onAllNodes(hasContentDescription("Recording"), useUnmergedTree = true)
            .fetchSemanticsNodes().minBy { it.boundsInRoot.top }.boundsInRoot
        assertTrue(nameBounds.right <= barsBounds.left && barsBounds.right <= recordingBounds.left)
        capture("phone-channels-playing-recording", TouchCanvas.PHONE)
        compose.runOnIdle { playingRecords.value = false }
        val allFilterLabel = filterLabel(R.string.channels_all)
        val imageRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)
        compose.onNode(hasContentDescription(allFilterLabel) and imageRole, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(allFilterLabel).performClick()
        compose.onNodeWithText("News").assertIsDisplayed()
        compose.captureTouchWindows("slice2", "phone-channels-tag-menu", TouchCanvas.PHONE)
        compose.onNodeWithText("News").performClick()
        compose.onNodeWithContentDescription(
            compose.activity.getString(R.string.channels_filter, "News"),
        ).assertIsDisplayed()
        val count = compose.activity.resources.getQuantityString(R.plurals.channels_count, 1, 1)
        compose.onNodeWithText(compose.activity.getString(R.string.channels_count_with_tag, count, "News"))
            .assertIsDisplayed()
        capture("phone-channels-selected-tag", TouchCanvas.PHONE)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h500dp-port-xhdpi")
    fun keptScrollClampsAfterRemovalAndStartsAtTheTopInAnotherTag() {
        val list = channelsFixture()
        val channels = mutableStateOf(list)
        val visible = mutableStateOf(true)
        val taps = MutableSharedFlow<Destination>(extraBufferCapacity = 1)
        showKeptChannels(channels, visible, taps)
        val rows = compose.onNode(hasScrollToIndexAction())
        val first = compose.onNodeWithText(list.rows.first().name)
        rows.performScrollToIndex(8)
        first.assertIsNotDisplayed()
        compose.runOnIdle { check(taps.tryEmit(Destination.Channels)) }
        first.assertIsDisplayed()
        rows.performScrollToIndex(8)
        first.assertIsNotDisplayed()

        compose.runOnIdle { channels.value = list.copy(rows = list.rows.take(2)) }
        first.assertIsDisplayed()
        compose.runOnIdle { channels.value = list }
        rows.performScrollToIndex(8)
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { visible.value = true }
        first.assertIsNotDisplayed()

        val newTag = list.copy(rows = list.rows.filterNot { it.id == "amber-stage" }, selectedTagId = "news")
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle {
            channels.value = newTag
            visible.value = true
        }
        first.assertIsDisplayed()
        rows.performScrollToIndex(7)
        first.assertIsNotDisplayed()
        compose.runOnIdle { channels.value = list }
        first.assertIsDisplayed()
    }

    private fun showKeptChannels(
        channels: State<ChannelList>,
        visible: State<Boolean>,
        taps: MutableSharedFlow<Destination> = MutableSharedFlow(),
    ) {
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            MaterialTheme(colorScheme = TouchDarkColors) {
                if (visible.value) {
                    holder.SaveableStateProvider("Channels") {
                        CompositionLocalProvider(LocalDestinationReselections provides taps) {
                            ChannelsScreen(ChannelsState.Ready(channels.value), fixtureActions())
                        }
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletLoading() {
        captureLoading("tablet-loading", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneLoading() {
        captureLoading("phone-loading", TouchCanvas.PHONE)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletUnreachable() {
        show(ChannelsState.Unreachable)
        capture("tablet-unreachable", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneSignInFailed() {
        show(ChannelsState.SignInFailed)
        capture("phone-sign-in-failed", TouchCanvas.PHONE)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletOffline() {
        val list = channelsFixture()
        show(ChannelsState.Offline(list, retryIn = "12 s")) { id, modifier ->
            Text(list.rows.first { it.id == id }.name, modifier)
        }
        compose.onNodeWithText("Harbor Sport").assertIsDisplayed()
        compose.onNodeWithContentDescription("Recording", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Recording scheduled", useUnmergedTree = true).assertDoesNotExist()
        capture("tablet-offline", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneOffline() {
        show(ChannelsState.Offline(channelsFixture(), retryIn = "12 s"))
        capture("phone-offline", TouchCanvas.PHONE)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun aLogoOfAnyShapeFitsItsShareOfTheTileCentered() {
        val logos = markedLogos()
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
                Column {
                    logos.forEachIndexed { index, logo ->
                        ChannelLogoTile(logo, null, Modifier.size(112.dp, 56.dp).testTag("tile-$index"))
                    }
                }
            }
        }
        logos.indices.forEach { index ->
            compose.onNodeWithTag("tile-$index").captureToImage().toPixelMap().assertLogoFitsItsShare("logo $index")
        }
    }

    @Test
    fun serverLogoTilesShowTheirFallbackOnlyWhenTheLogoIsMissingOrFails() {
        val failing = AppArtworkSource(null, ArtworkId(1))
        val loading = AppArtworkSource(null, ArtworkId(2))
        val picture = AppArtworkSource(null, ArtworkId(3))
        val loadedColor = Color.Magenta
        val bitmap = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(loadedColor.toArgb()) }
        val release = CompletableDeferred<Unit>()
        val images = ImageLoaderRule(Interceptor { chain ->
            release.await()
            if (chain.request.data == failing) ErrorResult(null, chain.request, IllegalStateException("Missing picon"))
            else SuccessResult(bitmap.asImage(), chain.request, DataSource.MEMORY)
        })
        images.apply(object : Statement() {
            override fun evaluate() {
                compose.mainClock.autoAdvance = false
                val pictureRequest = ImageRequest.Builder(compose.activity).data(picture).size(30, 30).build()
                compose.setContent {
                    MaterialTheme(colorScheme = TouchDarkColors) {
                        Column {
                            ArtworkImage(null, pictureRequest, Modifier.size(30.dp))
                            listOf("missing" to null, "failing" to failing, "loading" to loading).forEach { (tag, model) ->
                                ChannelLogoTile(null, null, Modifier.size(112.dp, 56.dp).testTag(tag), model = model)
                            }
                        }
                    }
                }
                compose.waitForIdle()
                assertTrue("the picture starts in the first layout", picture in images.requested)
                assertTrue("the logo starts in the same layout as the picture", loading in images.requested)
                compose.mainClock.autoAdvance = true
                val ground = TouchDarkColors.surfaceContainerHigh
                val missingCenter = tileCenter("missing")
                assertNotEquals("an absent model shows the fallback icon", setOf(ground), missingCenter)
                assertEquals(setOf(failing, loading, picture), images.requested.toSet())
                assertEquals("a pending logo shows only the ground", setOf(ground), tileCenter("loading"))
                assertEquals("a pending failure shows only the ground", setOf(ground), tileCenter("failing"))

                compose.runOnIdle { release.complete(Unit) }
                assertEquals("a failed logo shows the same icon as an absent model", missingCenter, tileCenter("failing"))
                assertEquals("a successful load shows the logo", setOf(loadedColor), tileCenter("loading"))
            }
        }, Description.EMPTY).evaluate()
    }

    private fun tileCenter(tag: String): Set<Color> {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return buildSet {
            for (x in pixels.width / 2 - 4..pixels.width / 2 + 4) {
                for (y in pixels.height / 2 - 4..pixels.height / 2 + 4) add(pixels[x, y])
            }
        }
    }

    @Test
    fun serverLogoTilesFitTheVisibleShapeRatherThanThePiconCanvas() {
        val wide = AppArtworkSource(null, ArtworkId(1))
        val square = AppArtworkSource(null, ArtworkId(2))
        val images = ImageLoaderRule(Interceptor { chain ->
            val picon = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply { color = LogoMark.toArgb() }
            val canvas = Canvas(picon)
            if (chain.request.data == wide) canvas.drawRect(0f, 70f, 400f, 170f, paint)
            else canvas.drawRect(150f, 70f, 250f, 170f, paint)
            val drawn = chain.request.transformations.fold(picon) { bitmap, transform ->
                transform.transform(bitmap, Size(400, 240))
            }
            SuccessResult(drawn.asImage(), chain.request, DataSource.MEMORY)
        })
        images.apply(object : Statement() {
            override fun evaluate() {
                compose.setContent {
                    MaterialTheme(colorScheme = TouchDarkColors) {
                        Column {
                            listOf("wide" to wide, "square" to square).forEach { (tag, model) ->
                                ChannelLogoTile(null, null, Modifier.size(112.dp, 56.dp).testTag(tag), model = model)
                            }
                        }
                    }
                }
                listOf("wide", "square").forEach { tag ->
                    compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                        .assertLogoFitsItsShare(tag, fillsWidth = tag == "wide")
                }
            }
        }, Description.EMPTY).evaluate()
    }

    @Test
    fun serverLogoTilesUseStoredThenSampledAccentsUnlessOverridden() {
        val guide = ServerGuide(Instant.fromEpochSeconds(0))
        val session = FakeTvheadendSession(guide.connected())
        val observation = session.observation.value
        val artwork = ArtworkModels(session)
        val storedModel = artwork.logo(guide.logo, observation) as AppArtworkSource
        val freshModel = artwork.logo(ChannelIcon.ImageCache(ArtworkId(123)), observation) as AppArtworkSource
        val referenceModel = artwork.logo(ChannelIcon.ImageCache(ArtworkId(124)), observation)
        val storedRgb = 0x317657
        val neutral = Color(0xFF000000.toInt() or NEUTRAL_ACCENT_RGB)
        val fixtureTint = Color(0xFF682A65)
        val store = ChannelAccentStore()
        runTest { store.put("stored", storedRgb) }
        val accents = ChannelAccents(store, Dispatchers.Unconfined) { _, id ->
            if (id == storedModel.id) "stored" else "fresh"
        }
        val releaseSample = CompletableDeferred<Unit>()
        val picon = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFC81818.toInt()) }
        val freshInkKeys = mutableListOf<String?>()
        val images = ImageLoaderRule(Interceptor { chain ->
            if (!chain.request.memoryCachePolicy.writeEnabled) releaseSample.await()
            else if (chain.request.data == freshModel) freshInkKeys += chain.request.transformations.singleOrNull()?.cacheKey
            SuccessResult(picon.asImage(), chain.request, DataSource.MEMORY)
        })
        fun verifyTiles() {
            val expectedFresh = mutableStateOf(neutral)
            compose.mainClock.autoAdvance = false
            compose.setContent {
                MaterialTheme(colorScheme = TouchDarkColors) {
                    CompositionLocalProvider(LocalChannelAccents provides accents) {
                        Column {
                            @Composable
                            fun tile(tag: String, model: Any?, tint: Color? = null) =
                                ChannelLogoTile(null, tint, Modifier.size(112.dp, 56.dp).testTag(tag), model = model)

                            tile("stored", storedModel)
                            tile("stored-reference", storedModel, Color(0xFF000000.toInt() or storedRgb))
                            tile("fresh", freshModel)
                            tile("fresh-reference", referenceModel, expectedFresh.value)
                            tile("no-logo", null, fixtureTint)
                            tile("override", storedModel, fixtureTint)
                            CompositionLocalProvider(LocalChannelAccents provides null) {
                                tile("override-reference", storedModel, fixtureTint)
                            }
                        }
                    }
                }
            }
            compose.mainClock.advanceTimeBy(48)
            val slot = compose.onNodeWithTag("fresh").captureToImage()
            fun inkKey(ground: Color): String = LogoInkTransformation(
                ground.toArgb(), (slot.width * 0.64f).roundToInt(), (slot.height * 0.50f).roundToInt(), trim = true,
            ).cacheKey
            fun ground(tag: String): Color {
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                return pixels[pixels.width / 8, pixels.height / 4]
            }
            assertEquals("stored channel colour is painted on the first frame", ground("stored-reference"), ground("stored"))
            assertEquals("pending sample uses neutral tint", ground("fresh-reference"), ground("fresh"))
            assertEquals("no logo leaves the neutral ground", TouchDarkColors.surfaceContainerHigh, ground("no-logo"))
            assertEquals("explicit tint overrides the stored accent", ground("override-reference"), ground("override"))
            assertEquals("one request on the initial neutral ground", listOf(inkKey(neutral)), freshInkKeys)

            compose.runOnIdle { releaseSample.complete(Unit) }
            compose.runOnIdle {
                val rgb = checkNotNull(accents.stored(freshModel)) { "the shared sampler did not store a colour" }
                assertNotEquals(NEUTRAL_ACCENT_RGB, rgb)
                expectedFresh.value = Color(0xFF000000.toInt() or rgb)
            }
            compose.mainClock.advanceTimeBy(48)
            val expectedKeys = listOf(inkKey(neutral), inkKey(expectedFresh.value))
            assertEquals("ink uses the settled accent before the fade finishes", expectedKeys, freshInkKeys)
            compose.mainClock.advanceTimeBy(304)
            assertEquals("fade frames do not request new ink variants", expectedKeys, freshInkKeys)
            assertEquals("tile settles on the shared sampler's colour", ground("fresh-reference"), ground("fresh"))
        }
        images.apply(object : Statement() {
            override fun evaluate() = verifyTiles()
        }, Description.EMPTY).evaluate()
    }

    private fun show(
        state: ChannelsState,
        actions: ChannelsActions = fixtureActions(),
        pane: @Composable (String, Modifier) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
                ChannelsScreen(state, actions, pane = pane)
            }
        }
    }

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch("slice2", name, canvas)

    private fun watchLabel(channel: String) = compose.activity.getString(R.string.channels_watch_named, channel)

    private fun filterLabel(tag: Int) = compose.activity.getString(R.string.channels_filter, compose.activity.getString(tag))

    private fun captureLoading(name: String, canvas: TouchCanvas) {
        compose.mainClock.autoAdvance = false
        show(ChannelsState.Loading)
        compose.mainClock.advanceTimeBy(640)
        capture(name, canvas)
    }

    private fun fixtureActions(
        onPlay: (String) -> Unit = {},
        onOpen: ((String) -> Unit)? = null,
        onTag: (String?) -> Unit = {},
    ) = ChannelsActions(
        onPlay = onPlay,
        onOpenChannel = onOpen,
        onTag = onTag,
        onRetry = {},
        onOpenConnectionSettings = {},
    )
}
