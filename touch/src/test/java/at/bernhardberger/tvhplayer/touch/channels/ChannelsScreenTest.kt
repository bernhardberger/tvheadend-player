package at.bernhardberger.tvhplayer.touch.channels

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.NEUTRAL_ACCENT_RGB
import at.bernhardberger.tvhplayer.images.ChannelAccents
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.touch.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.assertLogoFitsItsShare
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.testutil.markedLogos
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.ChannelLogoTile
import at.bernhardberger.tvhplayer.touch.ui.LocalChannelAccents
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
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
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                ChannelsScreen(state.value, fixtureActions(optional = true), initiallyOpenChannelId = "amber-stage")
            }
        }
        capture("tablet-channels", TouchCanvas.TABLET)

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
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletOpenRowWithoutAPicture() {
        val list = channelsFixture()
        val rows = list.rows.map { if (it.id == "amber-stage") it.copy(details = it.details?.copy(artwork = null)) else it }
        show(ChannelsState.Ready(list.copy(rows = rows)), open = "amber-stage")
        capture("tablet-channels-no-picture", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    fun uprightChannels() {
        show(ChannelsState.Ready(channelsFixture(playing = true)), open = "amber-stage")
        assertCompactFollowing()
        capture("tablet-upright-channels", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneChannels() {
        var played: String? = null
        show(ChannelsState.Ready(channelsFixture()), fixtureActions(onPlay = { played = it }))

        compose.onNodeWithContentDescription("Show programs on Amber Stage").performClick()
        assertNull(played)
        compose.onNodeWithText("S2 E4 • The Long Tide").assertIsDisplayed()
        compose.onNodeWithText("Watch").assertIsDisplayed()
        compose.onNodeWithText("Record").assertDoesNotExist()
        compose.onNodeWithText("Details").assertDoesNotExist()
        compose.onNodeWithText("Show in Guide").assertDoesNotExist()
        assertCompactFollowing()
        capture("phone-channels", TouchCanvas.PHONE)

        compose.onNodeWithContentDescription("Hide programs on Amber Stage").performClick()
        compose.onNodeWithText("S2 E4 • The Long Tide").assertDoesNotExist()
        compose.onNodeWithText("Amber Stage").performClick()
        compose.runOnIdle { assertEquals("amber-stage", played) }
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
        show(ChannelsState.Offline(channelsFixture(), retryIn = "12 s"))
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
            MaterialTheme(colorScheme = MobileDarkColors) {
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
    fun serverLogoTilesUseStoredThenSampledAccentsUnlessOverridden() {
        val guide = ServerGuide(Instant.fromEpochSeconds(0))
        val session = FakeTvheadendSession(guide.connected())
        val observation = session.observation.value
        val artwork = ArtworkModels(session)
        val storedModel = artwork.logo(guide.logo, observation) as AppArtworkSource
        val freshModel = artwork.logo(ChannelIcon.ImageCache(ArtworkId(123)), observation) as AppArtworkSource
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
        val images = ImageLoaderRule(Interceptor { chain ->
            if (!chain.request.memoryCachePolicy.writeEnabled) releaseSample.await()
            SuccessResult(picon.asImage(), chain.request, DataSource.MEMORY)
        })
        fun verifyTiles() {
            val expectedFresh = mutableStateOf(neutral)
            compose.mainClock.autoAdvance = false
            compose.setContent {
                MaterialTheme(colorScheme = MobileDarkColors) {
                    CompositionLocalProvider(LocalChannelAccents provides accents) {
                        Column {
                            @Composable
                            fun tile(tag: String, model: Any?, tint: Color? = null) =
                                ChannelLogoTile(null, tint, Modifier.size(112.dp, 56.dp).testTag(tag), model = model)

                            tile("stored", storedModel)
                            tile("stored-reference", storedModel, Color(0xFF000000.toInt() or storedRgb))
                            tile("fresh", freshModel)
                            tile("fresh-reference", freshModel, expectedFresh.value)
                            tile("no-logo", null, fixtureTint)
                            tile("override", storedModel, fixtureTint)
                            CompositionLocalProvider(LocalChannelAccents provides null) {
                                tile("override-reference", storedModel, fixtureTint)
                            }
                        }
                    }
                }
            }
            fun ground(tag: String): Color {
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                return pixels[pixels.width / 8, pixels.height / 4]
            }
            assertEquals("stored channel colour is painted on the first frame", ground("stored-reference"), ground("stored"))
            assertEquals("pending sample uses neutral tint", ground("fresh-reference"), ground("fresh"))
            assertEquals("no logo leaves the neutral ground", MobileDarkColors.surfaceContainerHigh, ground("no-logo"))
            assertEquals("explicit tint overrides the stored accent", ground("override-reference"), ground("override"))

            compose.runOnIdle { releaseSample.complete(Unit) }
            compose.runOnIdle {
                val rgb = checkNotNull(accents.stored(freshModel)) { "the shared sampler did not store a colour" }
                assertNotEquals(NEUTRAL_ACCENT_RGB, rgb)
                expectedFresh.value = Color(0xFF000000.toInt() or rgb)
            }
            compose.mainClock.advanceTimeBy(352)
            assertEquals("tile settles on the shared sampler's colour", ground("fresh-reference"), ground("fresh"))
        }
        images.apply(object : Statement() {
            override fun evaluate() = verifyTiles()
        }, Description.EMPTY).evaluate()
    }

    private fun show(state: ChannelsState, actions: ChannelsActions = fixtureActions(), open: String? = null) {
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                ChannelsScreen(state, actions, initiallyOpenChannelId = open)
            }
        }
    }

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch("slice2", name, canvas)

    private fun assertCompactFollowing() {
        compose.onNodeWithText("The Kettle Hour").assertIsDisplayed()
        compose.onNodeWithText("Coastal Miles").assertIsDisplayed()
        compose.onNodeWithText("Amber Late").assertIsDisplayed()
        compose.onNodeWithText("11:40 PM").assertDoesNotExist()
    }

    private fun captureLoading(name: String, canvas: TouchCanvas) {
        compose.mainClock.autoAdvance = false
        show(ChannelsState.Loading)
        compose.mainClock.advanceTimeBy(640)
        capture(name, canvas)
    }

    private fun fixtureActions(onPlay: (String) -> Unit = {}, optional: Boolean = false) = ChannelsActions(
        onPlay = onPlay,
        onTag = {},
        onRetry = {},
        onOpenConnectionSettings = {},
        onRecordControl = if (optional) ({ _, _, _ -> }) else null,
        onDetails = if (optional) ({}) else null,
        onShowInGuide = if (optional) ({}) else null,
    )
}
