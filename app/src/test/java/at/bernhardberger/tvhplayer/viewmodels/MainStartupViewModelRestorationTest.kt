package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.SavedStateHandle
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequest
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvheadend.core.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainStartupViewModelRestorationTest {
    @Test
    fun initialActivityIntentPolicy_replaysOnlyUnfinishedRestoredBootstrapOnce() {
        val unfinishedRestore = MainStartupViewModel.InitialActivityIntentPolicy(
            allowRestoredIntent = true,
        )
        assertTrue(unfinishedRestore.shouldHandle(activityWasRestored = true))
        assertFalse(unfinishedRestore.shouldHandle(activityWasRestored = true))

        val handledRestore = MainStartupViewModel.InitialActivityIntentPolicy(
            allowRestoredIntent = false,
        )
        assertFalse(handledRestore.shouldHandle(activityWasRestored = true))

        val freshActivity = MainStartupViewModel.InitialActivityIntentPolicy(
            allowRestoredIntent = false,
        )
        assertTrue(freshActivity.shouldHandle(activityWasRestored = false))
        assertFalse(freshActivity.shouldHandle(activityWasRestored = true))
    }

    @Test
    fun retainedPayload_containsOnlyRequestIdAndBootstrapHandledMarker() {
        val savedStateHandle = SavedStateHandle()
        assertTrue(MainStartupViewModel.shouldCreateStartupRequest(savedStateHandle))
        val requests = MainStartupViewModel.createRetainedApplianceLaunchRequests(
            savedStateHandle,
        )
        requests.request()
        val pending = requests.state.value as ApplianceLaunchState.Pending
        val target = requireNotNull(
            requests.resolve(
                request = pending.request,
                readiness = CurrentChannelReadiness.Ready(
                    listOf(
                        Channel(
                            channelId = 7,
                            name = "Sensitive channel name",
                            number = null,
                            icon = null,
                        )
                    ),
                ),
                persistedId = 7,
            )
        )
        MainStartupViewModel.markStartupRequestCreationHandled(savedStateHandle)

        val retainedPayload = savedStateHandle.keys().associateWith { key ->
            savedStateHandle.get<Any?>(key)
        }
        assertEquals(2, retainedPayload.size)
        assertTrue(retainedPayload.values.all { it is Long || it is Boolean })
        assertTrue(retainedPayload.values.contains(target.request.id))
        assertTrue(retainedPayload.values.contains(true))
        assertFalse(retainedPayload.values.contains(target))
        assertFalse(retainedPayload.values.contains(target.channelId))
        assertFalse(retainedPayload.values.contains(target.channelName))

        assertTrue(
            requests.completePlayerVisibility(
                target = target,
                channelId = target.channelId,
                serviceId = target.serviceId,
                channelName = target.channelName,
            )
        )
        val terminalPayload = savedStateHandle.keys().map { key ->
            savedStateHandle.get<Any?>(key)
        }
        assertEquals(listOf(true), terminalPayload)

        val restoredHandle = SavedStateHandle(retainedPayload)
        val restoredRequests = MainStartupViewModel.createRetainedApplianceLaunchRequests(
            restoredHandle,
        )

        assertEquals(
            ApplianceLaunchState.Pending(ApplianceLaunchRequest(target.request.id)),
            restoredRequests.state.value,
        )
        assertFalse(MainStartupViewModel.shouldCreateStartupRequest(restoredHandle))
    }
}
