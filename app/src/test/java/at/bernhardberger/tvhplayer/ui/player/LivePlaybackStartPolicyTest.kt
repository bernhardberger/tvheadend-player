package at.bernhardberger.tvhplayer.ui.player

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackStartPolicyTest {
    private val repositoryRoot = generateSequence(
        File(requireNotNull(System.getProperty("user.dir"))),
    ) { it.parentFile }.first { File(it, ".git").exists() }

    @Test
    fun initialPlaybackIsResolvedOnlyAfterTargetRequestCompletes() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/at/bernhardberger/tvhplayer/ui/player/VideoPlayerScreen.kt",
        ).readText()
        val requestBlock = source.substringAfter("val playbackSelection =")
            .substringBefore("LaunchedEffect(playingLiveChannelId")
        val requestIndex = requestBlock.indexOf("videoPlayerViewModel.playChannel(playbackSelection)")
        val resolvedIndex = requestBlock.indexOf("initialPlaybackResolved = true")

        assertTrue(requestIndex >= 0)
        assertTrue(resolvedIndex > requestIndex)
    }
}
