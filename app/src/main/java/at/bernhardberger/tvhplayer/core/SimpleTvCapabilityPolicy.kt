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
    PLAYER_CLOSE,
    FULL_PLAYBACK_OPTIONS,
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

enum class SimpleTvRouteGuardAction {
    ALLOW,
    REDIRECT_TO_LIVE,
    STOP_RECORDING_AND_REDIRECT_TO_LIVE,
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

sealed interface ProductProfile {
    data object Standard : ProductProfile

    data class Appliance(
        val timeshiftAllowed: Boolean,
    ) : ProductProfile
}

fun startupProductProfile(
    serverConfigured: Boolean,
    settings: SimpleTvSettings,
): ProductProfile = if (serverConfigured && settings.enabled) {
    applianceProductProfile(settings)
} else {
    ProductProfile.Standard
}

fun applianceProductProfile(settings: SimpleTvSettings): ProductProfile.Appliance =
    ProductProfile.Appliance(timeshiftAllowed = settings.timeshift)

fun ProductProfile.allows(capability: SimpleTvCapability): Boolean = when (this) {
    ProductProfile.Standard -> true
    is ProductProfile.Appliance -> when (capability) {
        SimpleTvCapability.LIVE_TV,
        SimpleTvCapability.UNLOCK -> true
        SimpleTvCapability.TIMESHIFT -> timeshiftAllowed
        SimpleTvCapability.CHANNEL_LIST,
        SimpleTvCapability.EPG,
        SimpleTvCapability.RECORDINGS,
        SimpleTvCapability.STOP,
        SimpleTvCapability.SETTINGS,
        SimpleTvCapability.APP_EXIT,
        SimpleTvCapability.PLAYER_CLOSE,
        SimpleTvCapability.FULL_PLAYBACK_OPTIONS -> false
    }
}

fun ProductProfile.allowsRoute(route: SimpleTvRoute): Boolean = allows(
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

fun simpleTvRouteGuardAction(
    profile: ProductProfile,
    route: SimpleTvRoute,
    recordingActive: Boolean,
): SimpleTvRouteGuardAction = when {
    profile.allowsRoute(route) -> SimpleTvRouteGuardAction.ALLOW
    recordingActive -> SimpleTvRouteGuardAction.STOP_RECORDING_AND_REDIRECT_TO_LIVE
    else -> SimpleTvRouteGuardAction.REDIRECT_TO_LIVE
}

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
