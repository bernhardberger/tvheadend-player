package at.bernhardberger.tvhplayer.settings

/** UI-only trial preference; CURRENT preserves the existing player. */
enum class PlayerChromeDesign { CURRENT, NEW }

fun resolvePlayerChromeDesign(value: String?): PlayerChromeDesign =
    PlayerChromeDesign.entries.firstOrNull { it.name == value } ?: PlayerChromeDesign.CURRENT
