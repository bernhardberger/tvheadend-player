package at.bernhardberger.tvhplayer.ui.components

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowsePreparationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pendingPreparationDoesNotBlockNewIntentOrPublishAnOldAuthority() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val authority = mutableStateOf("Guide")
        try {
            compose.setContent {
                TVHeadendPlayerTheme {
                    val prepared = rememberPreparedBrowseData(authority.value, authority.value) { captured ->
                        assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                        if (captured == "Guide") {
                            started.countDown()
                            assertTrue(release.await(10, TimeUnit.SECONDS))
                        }
                        captured
                    }
                    Column {
                        Text("Requested ${authority.value}")
                        if (prepared == null) BrowsePreparationPending(PaddingValues(), true)
                        else Text("Ready ${prepared.value}")
                    }
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            compose.onNodeWithTag("browse-preparing").assertIsFocused()
            compose.runOnIdle { authority.value = "Recordings" }
            compose.runOnIdle { authority.value = "Settings" }
            compose.onNodeWithText("Requested Settings").assertIsDisplayed()
            compose.onNodeWithTag("browse-preparing").assertIsFocused()
            release.countDown()
            compose.waitUntil(5_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Ready Settings"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Ready Guide").assertDoesNotExist()
            compose.onNodeWithText("Ready Recordings").assertDoesNotExist()
        } finally {
            release.countDown()
        }
    }

    @Test
    fun metadataChurnPublishesProgressAndThenTheNewestQueuedInput() {
        val started = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val latestStarted = CountDownLatch(1)
        val releaseLatest = CountDownLatch(1)
        val input = mutableIntStateOf(1)
        val computed = Collections.synchronizedList(mutableListOf<Int>())
        try {
            compose.setContent {
                TVHeadendPlayerTheme {
                    val result = rememberPreparedBrowseData("same session", input.intValue) { captured ->
                        computed += captured
                        if (captured == 1) {
                            started.countDown()
                            assertTrue(releaseFirst.await(10, TimeUnit.SECONDS))
                        } else {
                            latestStarted.countDown()
                            assertTrue(releaseLatest.await(10, TimeUnit.SECONDS))
                        }
                        captured * 10
                    }
                    Text(result?.let { "${it.input}:${it.value}" } ?: "Pending")
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            compose.runOnIdle { input.intValue = 2 }
            compose.runOnIdle { input.intValue = 3 }
            compose.waitForIdle()
            releaseFirst.countDown()
            assertTrue(latestStarted.await(5, TimeUnit.SECONDS))
            compose.onNodeWithText("1:10").assertIsDisplayed()
            releaseLatest.countDown()
            compose.waitUntil(5_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("3:30"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(listOf(1, 3), computed.toList())
        } finally {
            releaseFirst.countDown()
            releaseLatest.countDown()
        }
    }

    @Test
    fun requestReplacementRetainsDisplayButAuthorityReplacementClearsIt() {
        val authority = mutableStateOf("A")
        val request = mutableIntStateOf(1)
        val startedOld = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        val startedLatest = CountDownLatch(1)
        val releaseLatest = CountDownLatch(1)
        val startedAuthority = CountDownLatch(1)
        val releaseAuthority = CountDownLatch(1)
        try {
            compose.setContent {
                val owner = authority.value
                val key = request.intValue
                val result = rememberPreparedBrowseData(owner, key, requestKey = key) { captured ->
                    when {
                        owner == "B" -> {
                            startedAuthority.countDown()
                            assertTrue(releaseAuthority.await(10, TimeUnit.SECONDS))
                        }
                        captured == 2 -> {
                            startedOld.countDown()
                            assertTrue(releaseOld.await(10, TimeUnit.SECONDS))
                        }
                        captured == 3 -> {
                            startedLatest.countDown()
                            assertTrue(releaseLatest.await(10, TimeUnit.SECONDS))
                        }
                    }
                    "$owner:$captured"
                }
                Text(result?.let { "${it.value}:${it.requestKey == key}" } ?: "Pending")
            }
            compose.waitUntil(5_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("A:1:true")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { request.intValue = 2 }
            compose.waitUntil(5_000) { startedOld.count == 0L }
            compose.onNodeWithText("A:1:false").assertIsDisplayed()
            compose.runOnIdle { request.intValue = 3 }
            // Apply the replacement to the producer before releasing obsolete work.
            compose.waitForIdle()
            releaseOld.countDown()
            compose.waitUntil(5_000) { startedLatest.count == 0L }
            compose.onNodeWithText("A:1:false").assertIsDisplayed()
            releaseLatest.countDown()
            compose.waitUntil(5_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("A:3:true")).fetchSemanticsNodes().isNotEmpty()
            }
            // The request key deliberately stays the same across the new authority.
            compose.runOnIdle { authority.value = "B" }
            compose.waitUntil(5_000) { startedAuthority.count == 0L }
            compose.onNodeWithText("Pending").assertIsDisplayed()
            compose.onNodeWithText("A:3:true").assertDoesNotExist()
            releaseAuthority.countDown()
            compose.waitUntil(5_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("B:3:true")).fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            releaseOld.countDown()
            releaseLatest.countDown()
            releaseAuthority.countDown()
        }
    }
}
