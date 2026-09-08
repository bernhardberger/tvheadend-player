package at.bernhardberger.tvhplayer.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.ParcelFileDescriptor
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Opt-in, offline production composition; the owning emulator script restores its clock. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalTestApi::class)
class ChannelsScreenshotTest(private val scenario: String) {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun captureProductionChannels() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("channelsCaptures") == "true")
        assertEquals("Google", Build.MANUFACTURER)
        assertEquals("sdk_google_atv64_amati_x86_64_16k", Build.MODEL)
        assertEquals("emu64xa16k", Build.DEVICE)
        assertEquals("sdk_google_atv64_amati_x86_64_16k", Build.PRODUCT)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("en-US", instrumentation.targetContext.resources.configuration.locales[0].toLanguageTag())
        assertEquals(1f, instrumentation.targetContext.resources.configuration.fontScale)
        val previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("cmd alarm set-time ${NOW * 1000}"),
            ).use { it.readBytes() }
            assertTrue("Capture requires the isolated emulator clock", System.currentTimeMillis() / 1000 in NOW..NOW + 2)
            val longText = scenario == "long-mixed"
            val tagNames = if (scenario == "tags-overflow") {
                listOf("News", "Sport", "Documentaries", "Films", "International", "Culture", "Regional", "Children", "Music", "Radio")
            } else listOf("News", "Documentaries", "Sport", "Empty filter")
            val tags = tagNames.mapIndexed { index, name ->
                ChannelTag.create(id = ChannelTagId(index + 1L), name = name, index = index.toLong())
            }
            val names = listOf("World News HD", "Documentary HD", "Sports Live", "Cinema", "Culture", "Regional News", "Music Live", "Science HD")
            val channels = names.mapIndexed { index, name ->
                Channel.create(
                    id = ChannelId(index + 1L), number = index + 1L,
                    name = if (longText && index == 1) "International Documentary and Natural History Television HD" else name,
                    icon = if (longText && index % 2 == 0) null else "imagecache/${index + 1}",
                    tagIds = tags.filter { it.name != "Empty filter" }.map { it.id },
                )
            }
            val events = channels.filterNot { longText && it.id.value % 2L == 1L }.flatMap { channel ->
                listOf(
                    EpgEvent.create(
                        id = EventId(channel.id.value * 10), channelId = channel.id,
                        start = Instant.fromEpochSeconds(NOW - 1800), stop = Instant.fromEpochSeconds(NOW + 1800),
                        title = if (longText) "A journey through the world's most extraordinary mountain landscapes and remote communities" else "A journey through the Alps",
                        description = "Explore the high mountains, their wildlife and the people who live in this extraordinary landscape. Follow the changing seasons from quiet valleys to snow-covered peaks.",
                    ),
                    EpgEvent.create(
                        id = EventId(channel.id.value * 10 + 1), channelId = channel.id,
                        start = Instant.fromEpochSeconds(NOW + 1800), stop = Instant.fromEpochSeconds(NOW + 3600),
                        title = if (longText) "The world beneath the ice: discoveries from the most remote research stations" else "The world beneath the ice",
                    ),
                )
            }
            val catalog = ChannelCatalog.create(channels = channels, tags = tags)
            val observation = SessionObservation.create(
                sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
                channelState = ChannelRepositoryState.Current(catalog),
                epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = events)),
                dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = if (scenario == "playing-recording") {
                    listOf(DvrEntry.create(
                        id = DvrEntryId(1), channelId = ChannelId(2), title = "A journey through the Alps",
                        start = Instant.fromEpochSeconds(NOW - 1800), stop = Instant.fromEpochSeconds(NOW + 1800),
                        state = DvrEntryState.RECORDING,
                    ))
                } else emptyList())),
            )
            val artwork = Bitmap.createBitmap(160, 96, Bitmap.Config.ARGB_8888).apply {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                Canvas(this).apply {
                    paint.color = android.graphics.Color.rgb(29, 76, 100)
                    drawRoundRect(0f, 0f, 160f, 96f, 12f, 12f, paint)
                    paint.color = android.graphics.Color.WHITE
                    paint.textSize = 34f
                    paint.textAlign = Paint.Align.CENTER
                    drawText("TV", 80f, 61f, paint)
                }
            }
            lateinit var inputMode: InputModeManager
            composeRule.setContent {
                inputMode = LocalInputModeManager.current
                val context = LocalContext.current
                val imageLoader = remember {
                    ImageLoader.Builder(context).components {
                        add(object : Mapper<AppArtworkSource, Bitmap> {
                            override fun map(data: AppArtworkSource, options: Options) = artwork
                        })
                    }.build()
                }
                var selectedId by remember { mutableStateOf<ChannelId?>(ChannelId(2)) }
                var activeTag by remember { mutableStateOf<ChannelTagId?>(null) }
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SideRail(currentRoute = AppDestination.CHANNELS, showEpgMenu = true, onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                            ChannelsScreenContent(
                                contentPadding = padding, initialFocusEnabled = !drawerActive,
                                channelScopeState = resolveChannelScopeState(ChannelRepositoryState.Current(catalog), activeTag),
                                observation = observation, tagNotice = false, selectedId = selectedId,
                                imageLoader = imageLoader,
                                playingChannelId = if (scenario == "playing-recording") ChannelId(2) else null,
                                connectionUiState = if (scenario == "reconnecting") ConnectionUiState.Reconnecting else ConnectionUiState.Ready,
                                onSelectChannel = { selectedId = it }, onSelectTag = { activeTag = it },
                                onDismissTagNotice = {}, onRetryConnection = {}, onOpenConnectionSettings = {}, onPlay = { _, _ -> },
                            )
                        }
                    }
                }
            }
            composeRule.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
            composeRule.waitUntilExactlyOneExists(hasTestTag("channel-row-2") and isFocused(), 5_000)
            val focus = when (scenario) {
                "tags-overflow" -> {
                    composeRule.onNodeWithText("All channels").requestFocus()
                    repeat(5) { composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
                    composeRule.onNodeWithText("International")
                }
                "empty-filter" -> {
                    composeRule.onNodeWithText("All channels").requestFocus()
                    repeat(4) { composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
                    composeRule.onNodeWithText("Empty filter")
                }
                else -> composeRule.onNodeWithTag("channel-row-2")
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
            focus.assertIsFocused().assertIsDisplayed()
            if (scenario == "empty-filter") {
                composeRule.onNodeWithTag("channel-row-2").assertDoesNotExist()
                composeRule.onNodeWithText(instrumentation.targetContext.getString(R.string.empty_channel_tag)).assertIsDisplayed()
            } else {
                composeRule.onNodeWithTag("channels-detail-channel").assertTextEquals(channels[1].name!!)
            }
            if (scenario == "playing-recording") {
                focus.assertIsSelected().assertContentDescriptionEquals("Currently playing", "Recording now")
                composeRule.onNodeWithTag("channel-recording-indicator", useUnmergedTree = true).assertIsDisplayed()
            }
            if (scenario == "populated") focus.assertIsNotSelected()
            if (scenario == "reconnecting") composeRule.onNodeWithText(instrumentation.targetContext.getString(R.string.status_disconnected_reconnecting)).assertIsDisplayed()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            assertEquals(1920, bitmap.width)
            assertEquals(1080, bitmap.height)
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "channels-captures")
            assertTrue(directory.isDirectory || directory.mkdirs())
            File(directory, "$scenario.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            TimeZone.setDefault(previousZone)
        }
    }

    companion object {
        private const val NOW = 1_788_609_600L
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun scenarios() = listOf("populated", "playing-recording", "long-mixed", "tags-overflow", "reconnecting", "empty-filter")
    }
}
