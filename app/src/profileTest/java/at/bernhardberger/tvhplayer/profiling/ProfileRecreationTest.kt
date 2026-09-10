package at.bernhardberger.tvhplayer.profiling

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ProfileRecreationTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyProfileActivity>()

    @Test fun recreationKeepsCatalogBoundToItsLiveSessionAndClosesEachOwnerOnce() {
        compose.waitUntilAtLeastOneExists(hasText("Offline channel 1", substring = true), 15_000)
        lateinit var oldCatalog: ChannelsViewModel
        lateinit var oldSession: FakeTvheadendSession
        compose.activityRule.scenario.onActivity { activity ->
            oldCatalog = ViewModelProvider(activity)[ChannelsViewModel::class.java]
            oldSession = session(activity)
            assertSame(oldSession.observation, oldCatalog.observation)
            assertEquals(60, oldCatalog.channels.value.size)
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntilAtLeastOneExists(hasText("Offline channel 1", substring = true), 15_000)
        lateinit var newSession: FakeTvheadendSession
        compose.activityRule.scenario.onActivity { activity ->
            val catalog = ViewModelProvider(activity)[ChannelsViewModel::class.java]
            newSession = session(activity)
            assertNotSame(oldCatalog, catalog)
            assertNotSame(oldSession, newSession)
            assertSame(newSession.observation, catalog.observation)
            assertEquals(60, catalog.channels.value.size)
        }
        compose.waitUntil(10_000) { oldSession.calls.count { it == FakeSessionCall.SHUTDOWN } == 1 }
        assertEquals(0, newSession.calls.count { it == FakeSessionCall.SHUTDOWN })
        compose.activityRule.scenario.close()
        compose.waitUntil(10_000) { newSession.calls.count { it == FakeSessionCall.SHUTDOWN } == 1 }
        assertEquals(1, oldSession.calls.count { it == FakeSessionCall.SHUTDOWN })
    }

    // Inspect only this isolated fixture's owner; no production storage or new app test API.
    private fun session(activity: JourneyProfileActivity): FakeTvheadendSession {
        val owner = activity.javaClass.getDeclaredField("runtimeOwner")
            .apply { isAccessible = true }.get(activity)!!
        return owner.javaClass.getDeclaredField("session")
            .apply { isAccessible = true }.get(owner) as FakeTvheadendSession
    }
}
