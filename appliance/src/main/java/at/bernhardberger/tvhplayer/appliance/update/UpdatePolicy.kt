package at.bernhardberger.tvhplayer.appliance.update

import java.net.URI

enum class UpdateResult {
    NEVER, CHECKING, UP_TO_DATE, NO_NETWORK, RATE_LIMITED, RELEASE_PROBLEM,
    HASH_MISMATCH, WRONG_PACKAGE, NOT_NEWER, SIGNATURE_MISMATCH, INSTALL_PERMISSION,
    WAITING_STANDBY, INSTALLING, PENDING_CONFIRMATION, INSTALLER_FAILURE, STORAGE_FAILURE, SUCCESS,
}

object UpdatePolicy {
    const val CHECK_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L
    const val CONNECT_GUARD_MILLIS = 5 * 60 * 1000L

    fun parseChecksums(text: String): Map<String, String> {
        val hashes = linkedMapOf<String, String>()
        for (line in text.lineSequence().filter { it.isNotBlank() }) {
            val match = Regex("^([0-9a-fA-F]{64})  ([^/\\\\\\r\\n]+)$").matchEntire(line)
                ?: throw IllegalArgumentException("Malformed SHA256SUMS")
            val name = match.groupValues[2]
            require(name != "." && name != ".." && name == name.trim()) { "Invalid asset name" }
            require(hashes.put(name, match.groupValues[1].lowercase()) == null) { "Duplicate checksum" }
        }
        require(hashes.isNotEmpty()) { "Empty SHA256SUMS" }
        return hashes
    }

    fun companionAsset(names: List<String>): String? = names.filter { it.endsWith("-appliance.apk") }
        .singleOrNull()

    fun isUpToDate(expectedHash: String, installedHash: String): Boolean =
        expectedHash.equals(installedHash, ignoreCase = true)

    fun archiveRejection(
        expectedHash: String,
        downloadedHash: String,
        installedPackage: String,
        archivePackage: String,
        installedVersion: Long,
        archiveVersion: Long,
        installedSigners: Set<String>,
        archiveSigners: Set<String>,
    ): UpdateResult? = when {
        !expectedHash.equals(downloadedHash, ignoreCase = true) -> UpdateResult.HASH_MISMATCH
        installedPackage != archivePackage -> UpdateResult.WRONG_PACKAGE
        archiveVersion <= installedVersion -> UpdateResult.NOT_NEWER
        installedSigners.isEmpty() || installedSigners != archiveSigners -> UpdateResult.SIGNATURE_MISMATCH
        else -> null
    }

    fun shouldCheckAutomatically(enabled: Boolean, interactive: Boolean, lastCheck: Long, now: Long): Boolean =
        enabled && !interactive && (lastCheck == 0L || now - lastCheck >= CHECK_INTERVAL_MILLIS)

    fun mayInstallAutomatically(enabled: Boolean, interactive: Boolean): Boolean = enabled && !interactive

    fun shouldLaunchOnConnect(interactive: Boolean, suppressConnect: Boolean): Boolean =
        interactive && !suppressConnect

    fun completedSelfUpdate(fromVersion: Long, installedVersion: Long): Boolean =
        fromVersion >= 0 && installedVersion > fromVersion

    fun isConnectGuardFresh(startedAt: Long, now: Long): Boolean =
        startedAt > 0 && now - startedAt in 0 until CONNECT_GUARD_MILLIS

    fun installerResult(status: Int): UpdateResult = when (status) {
        -1 -> UpdateResult.PENDING_CONFIRMATION
        0 -> UpdateResult.SUCCESS
        else -> UpdateResult.INSTALLER_FAILURE
    }

    fun isAllowedUrl(url: String, debug: Boolean, feed: String): Boolean = try {
        val uri = URI(url)
        val feedUri = URI(feed)
        uri.userInfo == null && uri.fragment == null && !uri.host.isNullOrEmpty() &&
            (uri.scheme == "https" || (debug && feedUri.scheme == "http" && uri.scheme == "http" &&
                uri.host == feedUri.host && uri.port == feedUri.port &&
                uri.host in setOf("127.0.0.1", "10.0.2.2")))
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: java.net.URISyntaxException) {
        false
    }
}
