package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun introductionContinueMovesToConnectionStep() {
        var step by mutableStateOf(OnboardingStep.INTRODUCTION)

        composeRule.setContent {
            TVHeadendPlayerTheme {
                if (step == OnboardingStep.INTRODUCTION) {
                    OnboardingIntroduction { step = OnboardingStep.CONNECTION }
                } else {
                    androidx.tv.material3.Text("Connection step")
                }
            }
        }

        composeRule.onNodeWithText("Set up TVHeadend").performClick()
        composeRule.onNodeWithText("Connection step").assertIsDisplayed()
    }
}
