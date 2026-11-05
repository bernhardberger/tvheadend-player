package at.bernhardberger.tvhplayer.appliance

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FunctionStoreTest {
    @Test fun defaultsStartupCustomMoveRestoreAndRemoveRoundTripAcrossStoreInstances() {
        val context = RuntimeEnvironment.getApplication()
        val store = FunctionStore(context)
        assertEquals(CompanionFunctions(), store.functions)
        store.setStartup(false)
        val move = store.assign(CompanionFunction.LIVE_TV, ButtonBinding.Custom(172))
        assertEquals(move.functions.copy(startup = false), FunctionStore(context).functions)
        assertEquals(ButtonBinding.NotSet, FunctionStore(context).functions.guide)
        val restore = FunctionStore(context).assign(CompanionFunction.GUIDE, ButtonBinding.Default)
        assertEquals(restore.functions, FunctionStore(context).functions)
        assertEquals(ButtonBinding.NotSet, FunctionStore(context).functions.liveTv)
        store.assign(CompanionFunction.GUIDE, ButtonBinding.NotSet)
        assertEquals(CompanionFunctions(false, ButtonBinding.NotSet, ButtonBinding.NotSet), FunctionStore(context).functions)
        store.assign(CompanionFunction.LIVE_TV, ButtonBinding.Default)
        assertEquals(ButtonBinding.Default, FunctionStore(context).functions.liveTv)
        assertEquals(-1, context.getSharedPreferences("companion_functions", 0).getInt("live_tv", 0))
        assertEquals(setOf(170, 4001), FunctionStore(context).functions.keys(CompanionFunction.LIVE_TV))
    }
}
