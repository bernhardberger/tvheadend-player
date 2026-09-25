package at.bernhardberger.tvhplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class AudioInterruptionPolicyTest(
    private val content: AudioInterruptionContent,
    private val interruption: AudioInterruption,
    private val playing: Boolean,
    private val resumeOwned: Boolean,
    private val expected: AudioInterruptionAction,
) {
    @Test fun interruptionMatrix() {
        assertEquals(expected, audioInterruptionAction(content, interruption, playing, resumeOwned))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}, {1}, playing={2}, owned={3}: {4}")
        fun cases(): List<Array<Any>> = buildList {
            for (content in AudioInterruptionContent.entries) {
                for (event in AudioInterruption.entries) {
                    for (playing in listOf(false, true)) {
                        for (owned in listOf(false, true)) {
                            val lossAction = when (content) {
                                AudioInterruptionContent.LIVE -> AudioInterruptionAction.MUTE
                                AudioInterruptionContent.LIVE_TIMESHIFT, AudioInterruptionContent.RECORDING -> AudioInterruptionAction.PAUSE
                                AudioInterruptionContent.NONE -> AudioInterruptionAction.NONE
                            }
                            val gainAction = when (content) {
                                AudioInterruptionContent.LIVE -> AudioInterruptionAction.UNMUTE
                                AudioInterruptionContent.LIVE_TIMESHIFT, AudioInterruptionContent.RECORDING -> AudioInterruptionAction.RESUME
                                AudioInterruptionContent.NONE -> AudioInterruptionAction.NONE
                            }
                            val expected = when (event) {
                                AudioInterruption.TRANSIENT_LOSS_CAN_DUCK -> AudioInterruptionAction.NONE
                                AudioInterruption.GAIN -> if (owned) gainAction else AudioInterruptionAction.NONE
                                else -> if (playing) lossAction else AudioInterruptionAction.NONE
                            }
                            add(arrayOf(content, event, playing, owned, expected))
                        }
                    }
                }
            }
        }
    }
}
