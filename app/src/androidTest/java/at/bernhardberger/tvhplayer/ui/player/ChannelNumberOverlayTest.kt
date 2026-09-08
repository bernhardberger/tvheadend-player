package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChannelNumberOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clearingAcceptedDigitsKeepsOutgoingContentSizeUntilFadeCompletes() {
        val number = mutableStateOf("123")
        composeRule.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    DebugVideoBackdrop(true, Modifier.fillMaxSize())
                    ChannelNumberOverlay(number.value, Modifier.align(Alignment.TopEnd).padding(48.dp))
                }
            }
        }
        val before = composeRule.onNodeWithText("123").fetchSemanticsNode().boundsInRoot
        capture("visible")
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { number.value = "" }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100)
        val during = composeRule.onNodeWithText("123").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertEquals(before.size, during.size)
        capture("exiting")
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("123").assertDoesNotExist()
        capture("gone")
    }

    @Test
    fun newDigitsDuringExitReplaceOldEntry() {
        val number = mutableStateOf("123")
        composeRule.setContent {
            TVHeadendPlayerTheme { ChannelNumberOverlay(number.value) }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { number.value = "" }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { number.value = "4" }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("123").assertDoesNotExist()
    }

    private fun capture(stage: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "p36-numeric-captures")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$stage.png").outputStream().use {
            check(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
