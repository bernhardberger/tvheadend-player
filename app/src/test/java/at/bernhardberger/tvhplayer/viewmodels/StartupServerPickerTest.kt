package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupServerPickerTest {
    @Test fun coldChoiceHoldsReadyAndAutoplayUntilBackOrSelectionAndDoesNotRepeat() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val models = ViewModelStore()
        try {
            val stored = FakeServerProfileStore()
            stored.addAnonymous("one.home.arpa", 9982, null, true)
            stored.addAnonymous("two.home.arpa", 9982, null, false)
            val owner = AppProfileOwner(FakeTvheadendSession(), stored,
                PlayerSettingsStore(InMemoryPreferencesDataStore()), dispatcher, { ServerProfileEditReadResult.Missing })
            backgroundScope.launch { owner.run() }
            runCurrent(); owner.setAskAtStartup(true)
            val settings = UiSettingsStore(InMemoryPreferencesDataStore())
            settings.setAutoStartPlayback(true)
            val handle = SavedStateHandle()
            val vm = MainStartupViewModel(owner, settings, handle, applianceBuild = false)
            models.put("startup", vm); runCurrent()
            assertTrue(vm.startupPickerVisible.value)
            assertTrue(vm.state.value is MainStartupState.ResolvingLocal)
            assertEquals(ApplianceLaunchState.Idle, vm.applianceLaunchRequests.state.value)
            // Back and selecting the in-use avatar share this no-mutation completion.
            vm.finishStartupPicker(); runCurrent()
            assertFalse(vm.startupPickerVisible.value)
            assertTrue(vm.state.value is MainStartupState.Ready)
            assertFalse(MainStartupViewModel.shouldCreateStartupRequest(handle))
            val restored = MainStartupViewModel(owner, settings, handle, applianceBuild = false)
            models.put("restored", restored); runCurrent()
            assertFalse(restored.startupPickerVisible.value)
            val selected = MainStartupViewModel(owner, settings, SavedStateHandle(), applianceBuild = false)
            models.put("selected", selected); runCurrent()
            assertTrue(selected.startupPickerVisible.value)
            val profiles = owner.profiles.value as at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult.Available
            owner.setActive(profiles.profiles.last().id)
            selected.finishStartupPicker(); runCurrent()
            assertEquals("two.home.arpa", selected.runtimeServerSettings.value?.host)
            val duplicate = MainStartupViewModel(owner, settings, SavedStateHandle(), applianceBuild = false)
            models.put("duplicate", duplicate); runCurrent()
            duplicate.openStartupProfile(profiles.profiles.first().id.value); runCurrent()
            assertEquals(profiles.profiles.first().id.value, duplicate.startupSettingsProfileId)
            assertEquals(ApplianceLaunchState.Idle, duplicate.applianceLaunchRequests.state.value)
        } finally { models.clear(); Dispatchers.resetMain() }
    }

    @Test fun defaultOffHiddenThresholdAndApplianceNeverAsk() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val models = ViewModelStore()
        try {
            val stored = FakeServerProfileStore()
            stored.addAnonymous("one.home.arpa", 9982, null, true)
            stored.addAnonymous("two.home.arpa", 9982, null, false)
            val owner = AppProfileOwner(FakeTvheadendSession(), stored,
                PlayerSettingsStore(InMemoryPreferencesDataStore()), dispatcher, { ServerProfileEditReadResult.Missing })
            backgroundScope.launch { owner.run() }; runCurrent()
            val settings = UiSettingsStore(InMemoryPreferencesDataStore())
            fun check(name: String, appliance: Boolean) {
                val vm = MainStartupViewModel(owner, settings, SavedStateHandle(), applianceBuild = appliance)
                models.put(name, vm); runCurrent()
                assertFalse(vm.startupPickerVisible.value)
                assertTrue(vm.state.value is MainStartupState.Ready)
            }
            check("default", false)
            owner.setAskAtStartup(true)
            check("appliance", true)
            val id = (owner.profiles.value as at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult.Available).profiles.last().id
            owner.setShowInSwitcher(id, false)
            check("hidden", false)
        } finally { models.clear(); Dispatchers.resetMain() }
    }
}
