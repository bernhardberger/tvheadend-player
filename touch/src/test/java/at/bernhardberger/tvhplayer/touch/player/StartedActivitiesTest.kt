package at.bernhardberger.tvhplayer.touch.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StartedActivitiesTest {
    private sealed interface Step
    private data object Start : Step
    private data class Stop(val changingConfigurations: Boolean = false) : Step

    @Test
    fun playbackLeavesAndReturnsOnceAcrossBothActivities() {
        val cases = listOf(
            "Channels opened and left" to listOf(Start, Stop()) to "FB",
            // The player starts before Channels stops, and Channels before the player stops.
            "into the player and back" to listOf(Start, Start, Stop(), Start, Stop(), Stop()) to "FB",
            "home from the player" to listOf(Start, Start, Stop(), Stop()) to "FB",
            "Channels rotated" to listOf(Start, Stop(changingConfigurations = true), Start, Stop()) to "FB",
            "rotated behind the player" to listOf(Start, Start, Stop(changingConfigurations = true), Start) to "F",
            "away and back" to listOf(Start, Stop(), Start) to "FBF",
        )
        cases.forEach { (named, expected) ->
            val (name, steps) = named
            val calls = StringBuilder()
            val count = StartedActivities(onFirstStart = { calls.append('F') }, onLastStop = { calls.append('B') })
            steps.forEach { step ->
                when (step) {
                    Start -> count.started()
                    is Stop -> count.stopped(step.changingConfigurations)
                }
            }
            assertEquals(name, expected, calls.toString())
        }
    }
}
