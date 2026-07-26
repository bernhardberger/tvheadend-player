package at.bernhardberger.tvhplayer.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class SimpleTvCapability {
    LIVE_TV,
    CHANNEL_LIST,
    EPG,
    RECORDINGS,
    TIMESHIFT,
    STOP,
    SETTINGS,
    APP_EXIT,
    UNLOCK,
}

enum class SimpleTvRoute {
    CHANNELS,
    EPG,
    RECORDINGS,
    SETTINGS,
    PLAYER,
    RECORDING_PLAYER,
    UNLOCK,
}

/**
 * Simple TV remains a strict player-only mode.
 *
 * Only startup enablement, optional timeshift, and PIN state are configurable.
 * Granular EPG/recordings/stop/settings/app-exit flags were inert and removed.
 */
data class SimpleTvSettings(
    val enabled: Boolean = false,
    val timeshift: Boolean = false,
    val pinConfigured: Boolean = false,
)

data class SimpleTvProfile(
    val settings: SimpleTvSettings,
    val active: Boolean,
) {
    fun allows(capability: SimpleTvCapability): Boolean {
        if (!active) return true
        return when (capability) {
            SimpleTvCapability.LIVE_TV,
            SimpleTvCapability.UNLOCK -> true
            SimpleTvCapability.TIMESHIFT -> settings.timeshift
            SimpleTvCapability.CHANNEL_LIST,
            SimpleTvCapability.EPG,
            SimpleTvCapability.RECORDINGS,
            SimpleTvCapability.STOP,
            SimpleTvCapability.SETTINGS,
            SimpleTvCapability.APP_EXIT -> false
        }
    }

    fun allowsRoute(route: SimpleTvRoute): Boolean = allows(
        when (route) {
            SimpleTvRoute.CHANNELS -> SimpleTvCapability.CHANNEL_LIST
            SimpleTvRoute.EPG -> SimpleTvCapability.EPG
            SimpleTvRoute.RECORDINGS,
            SimpleTvRoute.RECORDING_PLAYER -> SimpleTvCapability.RECORDINGS
            SimpleTvRoute.SETTINGS -> SimpleTvCapability.SETTINGS
            SimpleTvRoute.PLAYER -> SimpleTvCapability.LIVE_TV
            SimpleTvRoute.UNLOCK -> SimpleTvCapability.UNLOCK
        }
    )
}

fun simpleTvProfile(settings: SimpleTvSettings, active: Boolean): SimpleTvProfile =
    SimpleTvProfile(settings, active)

fun isValidSimpleTvPin(pin: String): Boolean = pin.length == 4 && pin.all(Char::isDigit)

fun simpleTvPinHash(pin: String, salt: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    digest.update(pin.toByteArray(StandardCharsets.UTF_8))
    return digest.digest()
}

fun verifySimpleTvPin(pin: String, salt: ByteArray, expectedHash: ByteArray): Boolean =
    isValidSimpleTvPin(pin) &&
        MessageDigest.isEqual(simpleTvPinHash(pin, salt), expectedHash)
