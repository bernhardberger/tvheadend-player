package at.bernhardberger.tvhplayer.appliance

/** Append new functions: this order is also the display order. */
enum class CompanionFunction { STARTUP, LIVE_TV, GUIDE }

sealed interface ButtonBinding {
    data object Default : ButtonBinding
    data class Custom(val keyCode: Int) : ButtonBinding
    data object NotSet : ButtonBinding
}

fun CompanionFunction.defaultKeys(): Set<Int> = when (this) {
    CompanionFunction.STARTUP -> emptySet()
    CompanionFunction.LIVE_TV -> setOf(170)
    CompanionFunction.GUIDE -> setOf(172, ApplianceEntryPolicy.TCL_TV_KEY_CODE)
}

data class MovedButton(val function: CompanionFunction, val keyCode: Int)
data class FunctionChange(val functions: CompanionFunctions, val moved: List<MovedButton>)

data class CompanionFunctions(
    val startup: Boolean = true,
    val liveTv: ButtonBinding = ButtonBinding.Default,
    val guide: ButtonBinding = ButtonBinding.Default,
) {
    fun binding(function: CompanionFunction): ButtonBinding = when (function) {
        CompanionFunction.STARTUP -> error("Startup has no button")
        CompanionFunction.LIVE_TV -> liveTv
        CompanionFunction.GUIDE -> guide
    }

    fun keys(function: CompanionFunction): Set<Int> = when (val binding = binding(function)) {
        ButtonBinding.Default -> function.defaultKeys()
        is ButtonBinding.Custom -> setOf(binding.keyCode)
        ButtonBinding.NotSet -> emptySet()
    }

    fun owner(keyCode: Int): CompanionFunction? = CompanionFunction.entries
        .firstOrNull { it != CompanionFunction.STARTUP && keyCode in keys(it) }

    /** Capture and restore both move keys, never duplicate them. */
    fun assign(function: CompanionFunction, binding: ButtonBinding): FunctionChange {
        val normalized = if (binding is ButtonBinding.Custom && function.defaultKeys().singleOrNull() == binding.keyCode)
            ButtonBinding.Default else binding
        var next = withBinding(function, normalized)
        val moved = mutableListOf<MovedButton>()
        for (other in CompanionFunction.entries.filter { it != CompanionFunction.STARTUP && it != function }) {
            val removed = keys(other).intersect(next.keys(function))
            moved += removed.map { MovedButton(other, it) }
            if (removed.isNotEmpty()) {
                val remaining = keys(other) - removed
                // GUIDE is the only multi-key default. Losing either leaves one custom key.
                next = next.withBinding(other, remaining.singleOrNull()?.let { ButtonBinding.Custom(it) }
                    ?: ButtonBinding.NotSet)
            }
        }
        return FunctionChange(next, moved)
    }

    private fun withBinding(function: CompanionFunction, binding: ButtonBinding): CompanionFunctions = when (function) {
        CompanionFunction.STARTUP -> error("Startup has no button")
        CompanionFunction.LIVE_TV -> copy(liveTv = binding)
        CompanionFunction.GUIDE -> copy(guide = binding)
    }
}

/** Deliberately small en-US table; private/vendor keys are not guessed. */
fun buttonName(keyCode: Int): String = when (keyCode) {
    170 -> "TV button"
    172 -> "Guide button"
    183 -> "Red button"
    184 -> "Green button"
    185 -> "Yellow button"
    186 -> "Blue button"
    165 -> "Info button"
    166 -> "Channel up button"
    167 -> "Channel down button"
    85 -> "Play/pause button"
    178 -> "Input button"
    82 -> "Menu button"
    176 -> "Settings button"
    173 -> "Recordings button"
    else -> "Button $keyCode"
}

fun CompanionFunctions.buttonLabel(function: CompanionFunction): String =
    if (binding(function) == ButtonBinding.Default && function == CompanionFunction.GUIDE) "Guide button"
    else keys(function).singleOrNull()?.let(::buttonName) ?: "Not set"

fun CompanionFunctions.allSetBody(defaultHome: Boolean): String {
    val ways = buildList {
        if (startup) add("at startup")
        if (defaultHome) add("on HOME")
        val buttons = listOf(CompanionFunction.LIVE_TV, CompanionFunction.GUIDE)
            .filter { keys(it).isNotEmpty() }.map { buttonLabel(it) }
        if (buttons.isNotEmpty()) {
            val label = if (buttons == listOf("TV button", "Guide button")) "the TV and Guide buttons"
                else buttons.joinToString(" and ")
            add("with $label")
        }
    }
    if (ways.isEmpty()) return "Nothing is set to open Tvheadend Player."
    val joined = if (ways.size == 1) ways.single() else ways.dropLast(1).joinToString(", ") + " and " + ways.last()
    return "This TV opens Tvheadend Player $joined."
}
