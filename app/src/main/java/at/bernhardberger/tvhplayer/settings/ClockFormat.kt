package at.bernhardberger.tvhplayer.settings

/** The clock the app shows: the device setting, or an explicit 12- or 24-hour override. */
enum class ClockFormat {
    SYSTEM,
    TWELVE_HOUR,
    TWENTY_FOUR_HOUR;

    fun uses24Hour(systemUses24Hour: Boolean): Boolean = when (this) {
        SYSTEM -> systemUses24Hour
        TWELVE_HOUR -> false
        TWENTY_FOUR_HOUR -> true
    }
}

fun resolveClockFormat(storedValue: String?): ClockFormat =
    ClockFormat.entries.firstOrNull { it.name == storedValue } ?: ClockFormat.SYSTEM
