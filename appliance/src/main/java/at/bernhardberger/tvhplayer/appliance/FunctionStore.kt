package at.bernhardberger.tvhplayer.appliance

import android.content.Context

class FunctionStore(context: Context) {
    private val preferences = context.getSharedPreferences("companion_functions", Context.MODE_PRIVATE)

    val functions: CompanionFunctions
        get() = CompanionFunctions(preferences.getBoolean("startup", false), read("live_tv"), read("guide"))

    fun setStartup(enabled: Boolean) { preferences.edit().putBoolean("startup", enabled).apply() }

    fun assign(function: CompanionFunction, binding: ButtonBinding): FunctionChange {
        val change = functions.assign(function, binding)
        // One edit makes a move durable without a momentary duplicate assignment.
        preferences.edit().putInt("live_tv", encode(change.functions.liveTv))
            .putInt("guide", encode(change.functions.guide)).apply()
        return change
    }

    // Missing/default = -1; not set = -2; custom = Android key code (including vendor codes).
    private fun read(key: String): ButtonBinding = when (val code = preferences.getInt(key, -1)) {
        -1 -> ButtonBinding.Default
        -2 -> ButtonBinding.NotSet
        else -> ButtonBinding.Custom(code)
    }

    private fun encode(binding: ButtonBinding): Int = when (binding) {
        ButtonBinding.Default -> -1
        ButtonBinding.NotSet -> -2
        is ButtonBinding.Custom -> binding.keyCode
    }
}
