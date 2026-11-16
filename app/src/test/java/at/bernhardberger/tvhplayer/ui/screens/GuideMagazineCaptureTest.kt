package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.testutil.*
import at.bernhardberger.tvhplayer.ui.*
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.screens.guide.*
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w960dp-h540dp-land-xhdpi",
    instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideMagazineCaptureTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var opened: EventId? = null
    private val now = 1_791_144_900L
    private lateinit var originalZone: TimeZone

    @Before fun fixEveningClock() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        check(android.os.SystemClock.setCurrentTimeMillis(now * 1000))
    }

    @After fun restoreZone() { TimeZone.setDefault(originalZone) }

    private val lineUp = listOf(
        listOf("Ridge Earth", "ridge-earth", "Ridge Light", "ridge-light", "Tide Watch", "Ridge Light"),
        listOf("Harbor Sport", "harbor-sport", "Harbor Kickoff", "harbor-kickoff", "River Court", "Harbor Kickoff"),
        listOf("Northline News", "northline-news", "Northline Tonight", "northline-tonight", "Field Desk", "Skyglass Weather"),
        listOf("Kite Kids", "kite-kids", "Paper Kite Tales", "paper-kite-tales", "Little Orbit", "Paper Kite Tales"),
        listOf("Ion Frame", "ion-frame", "Ion Wake", "ion-wake", "Lantern Road", "Ion Wake"),
    )
    private val columns = lineUp.mapIndexed { c, row ->
        val channel = Channel.create(id = ChannelId(c + 1L), name = row[0], number = c + 1L,
            icon = ChannelIcon.ImageCache(ArtworkId(c + 1)))
        // Consecutive boundaries: no overlaps; short bulletins, episodes and long coverage differ.
        val boundaries = listOf(
            listOf(-15, 30, 60, 180, 225, 285, 315, 375),
            listOf(-90, 15, 30, 150, 210, 255, 285, 345),
            listOf(-15, 15, 30, 60, 75, 105, 120, 150),
            listOf(-5, 15, 45, 60, 90, 105, 135, 150),
            listOf(-30, 15, 45, 165, 195, 225, 285, 315),
        )[c]
        MagazineColumn(channel, (0..6).map { e ->
            val title = when {
                c == 0 && e == 1 -> "Tide Watch: Evening Estuary"
                c == 0 && e == 2 -> "Ridge Light: Winter on the Mountain Ridge — Forests, Wildlife and the Changing Seasons"
                c == 1 && e == 1 -> "Harbor Kickoff: Match Review"
                c == 1 && e == 2 -> "River Court"
                else -> row[if (e == 0) 2 else if (e % 2 == 1) 4 else 5]
            }
            val picture = when {
                c == 1 && e == 0 -> ArtworkId(102)
                c == 1 && e == 2 -> ArtworkId(201)
                c == 0 && e == 2 -> ArtworkId(101)
                else -> null
            }
            MagazineEntry(EpgEvent.create(
                id = EventId(c * 100L + e), channelId = channel.id,
                start = Instant.fromEpochSeconds(now + boundaries[e] * 60),
                stop = Instant.fromEpochSeconds(now + boundaries[e + 1] * 60),
                title = title, image = picture,
            ), recording = when {
                c == 1 && e == 0 -> DvrEntryState.RECORDING
                c == 0 && e == 1 -> DvrEntryState.SCHEDULED
                else -> null
            })
        })
    }

    private fun show() {
        val art = buildMap {
            lineUp.forEachIndexed { c, row ->
                put(ArtworkId(c + 1), FixtureArt.picon(row[1]))
                put(ArtworkId(101 + c), FixtureArt.art(row[3]))
            }
            put(ArtworkId(201), FixtureArt.art("river-court"))
        }
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), art))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SideRail(
                            currentRoute = AppDestination.GUIDE, onRootBack = {}, onNavigate = {},
                            rootRoute = AppDestination.HOME,
                            availableDestinations = setOf(AppDestination.HOME, AppDestination.CHANNELS,
                                AppDestination.GUIDE, AppDestination.RECORDINGS, AppDestination.SETTINGS),
                        ) { padding, _ ->
                            val header = remember { ChannelsHeaderFocus() }
                            val date = remember { FocusRequester() }
                            val first = remember { FocusRequester() }
                            val rail = LocalBrowseNavigationFocus.current
                            Column(Modifier.fillMaxSize()) {
                                GuideHeader(
                                    tag = null, genre = null, genreDot = null,
                                    windowStartSec = now, windowEndSec = now + 3 * 3600, nowSecProvider = { now },
                                    focus = header, dateFocus = date, showTags = true, showFilter = true,
                                    navigationFocus = rail, contentPadding = padding,
                                    startPadding = padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                                    endPadding = 0.dp, onDateClick = {}, onFocused = {}, onOpen = {},
                                    onMoveToContent = { first.requestFocus() },
                                )
                                GuideMagazine(
                                    columns, now, { opened = it.id },
                                    Modifier.weight(1f).padding(
                                        start = padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + TvBrowseLeadingInset,
                                        top = TvSpacing16,
                                    ),
                                    navigationFocus = rail ?: FocusRequester.Default, headerFocus = date, initialFocus = first,
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.settleFrames(3)
    }

    @Test @Category(VisualCapture::class) fun magazineFrames() {
        show()
        val prefix = "magazine"
        capture("$prefix-resting")
        repeat(2) { compose.pressKey(Key.DirectionDown) }
        compose.settleFrames(2)
        capture("$prefix-down2")
        compose.pressKey(Key.DirectionRight)
        compose.settleFrames(2)
        capture("$prefix-right")
        compose.pressKey(Key.DirectionLeft)
        // Left returns to the second entry by nearest start time; four Down presses reach the sixth.
        repeat(4) { compose.pressKey(Key.DirectionDown) }
        compose.settleFrames(2)
        capture("$prefix-scrolled")
        compose.pressKey(Key.DirectionRight)
        compose.settleFrames(2)
        capture("$prefix-scrolled-right")
    }

    @Test fun horizontalMovementUsesNearestStartAndOkOpensDetails() {
        show()
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(EventId(0), opened) }
        compose.pressKey(Key.DirectionDown)
        // Column 1's second event starts at +30; column 2's nearest is its third (+30).
        compose.pressKey(Key.DirectionRight)
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(EventId(102), opened) }
        compose.pressKey(Key.DirectionLeft)
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(EventId(1), opened) }
        repeat(4) { compose.pressKey(Key.DirectionDown) }
        compose.pressKey(Key.DirectionRight)
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(EventId(106), opened) }
    }

    private fun capture(name: String) {
        // Warm native graphics layers before retaining the settled software-canvas frame.
        compose.runOnIdle {
            val root = view.rootView
            val warm = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(warm))
            warm.recycle()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val root = view.rootView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            CaptureOutput.writePng(File("build/outputs/guide-magazine/$name.png"), bitmap)
            bitmap.recycle()
        }
    }
}
