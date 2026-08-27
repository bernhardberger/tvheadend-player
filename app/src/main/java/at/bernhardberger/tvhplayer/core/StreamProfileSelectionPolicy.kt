package at.bernhardberger.tvhplayer.core

data class StreamProfileSelectionOption(
    val id: String,
    val name: String,
)

/**
 * Returns a migration candidate only for one case-sensitive exact legacy-name
 * match. Persisting or completing migration remains the released-profile
 * discovery owner's responsibility.
 */
fun exactLegacyProfileUuid(
    legacyName: String?,
    discoveredProfiles: List<StreamProfileSelectionOption>,
): String? {
    val evidence = legacyName?.takeIf(String::isNotEmpty) ?: return null
    return discoveredProfiles.singleOrNull { it.name == evidence }?.id
}

fun selectedStreamProfileUuid(
    persistedUuid: String?,
    legacyName: String?,
    currentUuid: String?,
    discoveredProfiles: List<StreamProfileSelectionOption>,
): String? {
    fun existing(candidate: String?): String? = candidate?.takeIf { uuid ->
        discoveredProfiles.any { it.id == uuid }
    }

    val legacyUuid = if (persistedUuid.isNullOrEmpty()) {
        exactLegacyProfileUuid(legacyName, discoveredProfiles)
    } else {
        null
    }

    return existing(persistedUuid)
        ?: legacyUuid
        ?: existing(currentUuid)
        ?: discoveredProfiles.firstOrNull()?.id
}
