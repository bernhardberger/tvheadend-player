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

data class SimpleTvSettings(
    val enabled: Boolean = false,
    val epg: Boolean = false,
    val recordings: Boolean = false,
    val timeshift: Boolean = false,
    val stop: Boolean = false,
    val settings: Boolean = false,
    val appExit: Boolean = false,
    val pinConfigured: Boolean = false,
)

data class SimpleTvProfile(
    val settings: SimpleTvSettings,
    val unlocked: Boolean,
) {
    fun allows(capability: SimpleTvCapability): Boolean {
        if (!settings.enabled || unlocked) return true
        return when (capability) {
            SimpleTvCapability.LIVE_TV,
            SimpleTvCapability.CHANNEL_LIST,
            SimpleTvCapability.UNLOCK -> true
            SimpleTvCapability.EPG -> settings.epg
            SimpleTvCapability.RECORDINGS -> settings.recordings
            SimpleTvCapability.TIMESHIFT -> settings.timeshift
            SimpleTvCapability.STOP -> settings.stop
            SimpleTvCapability.SETTINGS -> settings.settings
            SimpleTvCapability.APP_EXIT -> settings.appExit
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

fun simpleTvProfile(settings: SimpleTvSettings, unlocked: Boolean): SimpleTvProfile =
    SimpleTvProfile(settings, unlocked)

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
