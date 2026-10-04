package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrEvent
import at.bernhardberger.tvhplayer.core.DvrEventKind
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.notifications.*
import at.bernhardberger.tvhplayer.ui.player.*
import coil3.ImageLoader
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppNoticeCaptureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var originalZone: TimeZone
    private val now = Instant.parse("2026-10-04T18:30:00Z")

    @Before fun setZone() { originalZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restoreZone() { TimeZone.setDefault(originalZone) }
    @Test fun english() = captureVariants("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeText() = captureVariants("de", 1.3f)

    private data class Variant(val name: String, val headline: String, val detail: String, val icon: AppNoticeIcon)

    private fun captureVariants(locale: String, fontScale: Float) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val title = if (locale == "de") "Die Bergwelt im Herbst" else "Mountains in Autumn"
        val entry = DvrEntry.create(DvrEntryId(1), title = title, channelName = "Documentary HD",
            start = now + 1_800.seconds, subscriptionError = DvrSubscriptionError.NO_DISK_SPACE)
        val variants = DvrEventKind.entries.map { kind ->
            val event = DvrEvent(kind, entry, count = 5)
            Variant(kind.name.lowercase(), context.getString(event.headline()), event.detail(context, now), event.icon())
        } + listOf(
            Variant("long-title", context.getString(R.string.recording_notice_scheduled),
                DvrEvent(DvrEventKind.SCHEDULED, DvrEntry.create(DvrEntryId(2),
                    title = if (locale == "de") "Eine außergewöhnliche Reise durch die verborgenen Landschaften und die faszinierende Tierwelt unserer Erde"
                        else "An extraordinary journey through the hidden landscapes and the fascinating wildlife of our planet",
                    channelName = "International Documentary HD", start = now + 86_400.seconds)).detail(context, now), AppNoticeIcon.SCHEDULE),
            Variant("action-failure", context.getString(R.string.recording_action_failed),
                context.getString(R.string.recording_action_permission), AppNoticeIcon.WARNING),
        )
        var variant by mutableStateOf(variants.first())
        var overPlayer by mutableStateOf(false)
        lateinit var view: View
        val programme = EpgEvent.create(EventId(1), ChannelId(1), now - 600.seconds, now + 2_400.seconds, title = title)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            val loader = remember { ImageLoader.Builder(context).diskCache(null).build() }
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize().background(if (overPlayer) Color(0xFFF2EEDC) else Color(0xFF111822))) {
                        if (overPlayer) PlayerChrome(
                            mode = PlayerChromeMode.BANNER,
                            content = PlayerChromeContent("18:30", liveInfoBarData(101, "Documentary HD", programme,
                                null, false, now.epochSeconds, "")),
                            timeline = PlayerChromeTimeline.Live(AppTimeshiftState(), now.epochSeconds, programme),
                            actions = PlayerChromeActions(active = false), imageLoader = loader, currentSession = null,
                            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                        AppNoticePresentation(variant.headline, variant.detail, variant.icon)
                    }
                }
            }
        }
        for (next in variants) {
            compose.runOnIdle { variant = next }
            compose.waitForIdle()
            repeat(6) {
                compose.mainClock.advanceTimeBy(100)
                compose.waitForIdle()
            }
            compose.onNodeWithTag("app-notice").assertIsDisplayed()
                .assertContentDescriptionEquals("${next.headline}. ${next.detail}")
            compose.onAllNodes(isFocused()).assertCountEquals(0)
            capture(view, "$locale-font$fontScale-${next.name}", locale, fontScale, "notice only")
        }
        compose.runOnIdle { variant = variants.first { it.icon == AppNoticeIcon.RECORDING }; overPlayer = true }
        compose.waitForIdle()
        repeat(6) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
        capture(view, "$locale-font$fontScale-over-player", locale, fontScale, "production player banner over a bright synthetic still; no SurfaceView")
    }

    private fun capture(view: View, name: String, locale: String, fontScale: Float, scene: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("../artifacts/notices").apply { mkdirs() }
            File(directory, ".gitignore").writeText("*\n")
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "$name.txt").writeText("canvas=960x540dp\npixels=${view.width}x${view.height}\ndensity=2\nlocale=$locale\nfontScale=$fontScale\nzone=UTC\nfocus=none (non-focusable notice)\n$scene\n")
            bitmap.recycle()
        }
    }
}
