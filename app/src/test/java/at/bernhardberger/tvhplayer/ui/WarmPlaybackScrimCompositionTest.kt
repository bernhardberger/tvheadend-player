package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.shouldMountPersistentPlayerSurface
import at.bernhardberger.tvhplayer.core.shouldShowWarmPlaybackScrim
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WarmPlaybackScrimCompositionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun warmPlaybackBehindOrdinaryRouteHasOneScrimAtPointSevenSixAlpha() {
        verify(hasActivePlayback = true, isPlayerRoute = false,
            expectedPixel = Color.Black.copy(alpha = 0.76f).compositeOver(Color.White).toArgb(),
            expectedScrims = 1, captureName = "shell-warm-ordinary")
    }

    @Test fun settingsWithoutActivePlaybackUsesThemedBackgroundWithoutScrim() {
        verify(hasActivePlayback = false, isPlayerRoute = false,
            expectedPixel = 0xFF111416.toInt(), expectedScrims = 0,
            captureName = "shell-settings-no-playback")
    }

    @Test fun playerRouteDoesNotUseGlobalWarmPlaybackScrim() {
        verify(hasActivePlayback = true, isPlayerRoute = true,
            expectedPixel = Color.White.toArgb(), expectedScrims = 0,
            captureName = "shell-player-route")
    }

    private fun verify(
        hasActivePlayback: Boolean,
        isPlayerRoute: Boolean,
        expectedPixel: Int,
        expectedScrims: Int,
        captureName: String,
    ) {
        lateinit var view: View
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                MainStartupComposition(
                    state = MainStartupCompositionState(MainStartupPresentation.Inactive,
                        SettingsKey(SettingsSection.GENERAL), true),
                    onBack = {}, onAction = {}, registerActivityKeyContract = { {} },
                    showWarmPlaybackScrim = shouldShowWarmPlaybackScrim(hasActivePlayback, isPlayerRoute),
                    persistentSurface = {
                        if (shouldMountPersistentPlayerSurface(hasActivePlayback, isPlayerRoute)) {
                            Box(Modifier.fillMaxSize().background(Color.White))
                        }
                    },
                    navigation = { _, _ ->
                        Box(Modifier.align(Alignment.TopStart).size(32.dp).background(Color.Red))
                    },
                    notices = {
                        Box(Modifier.align(Alignment.BottomEnd).size(32.dp).background(Color.Green))
                    },
                )
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithTag("warm-playback-scrim").assertCountEquals(expectedScrims)
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        assertEquals(expectedPixel, bitmap.getPixel(480, 270))
        assertEquals(expectedPixel, bitmap.getPixel(959, 0))
        assertEquals(expectedPixel, bitmap.getPixel(0, 539))
        assertEquals(Color.Red.toArgb(), bitmap.getPixel(16, 16))
        assertEquals(Color.Green.toArgb(), bitmap.getPixel(944, 524))
        val directory = File("build/outputs/settings-c-captures").apply { mkdirs() }
        File(directory, "$captureName.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
