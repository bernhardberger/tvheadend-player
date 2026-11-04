package at.bernhardberger.tvhplayer.appliance

import at.bernhardberger.tvhplayer.appliance.update.UpdatePolicy
import at.bernhardberger.tvhplayer.appliance.update.UpdateResult
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val hash = "ab".repeat(32)

    @Test fun checksumParsingIsStrictAndRejectsDuplicates() {
        assertEquals(mapOf("release-appliance.apk" to hash),
            UpdatePolicy.parseChecksums("${hash.uppercase()}  release-appliance.apk\r\n\n"))
        for (invalid in listOf("", "bad  file.apk", "$hash file.apk", "$hash *file.apk",
                "$hash  ../file.apk", "$hash  file.apk\n$hash  file.apk", "$hash  file.apk\nmalformed")) {
            assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.parseChecksums(invalid) }
        }
    }

    @Test fun exactlyOneCompanionAssetIsRequired() {
        assertNull(UpdatePolicy.companionAsset(listOf("player-play.apk", "SHA256SUMS")))
        assertEquals("v1-appliance.apk", UpdatePolicy.companionAsset(listOf("v1-appliance.apk", "v1-play.apk")))
        assertNull(UpdatePolicy.companionAsset(listOf("v1-appliance.apk", "v2-appliance.apk")))
        assertNull(UpdatePolicy.companionAsset(listOf("v1-appliance.apk", "v1-appliance.apk")))
    }

    @Test fun hashIdentitySkipsDownloadAndArchiveMustPassEveryGuard() {
        assertTrue(UpdatePolicy.isUpToDate(hash, hash.uppercase()))
        assertFalse(UpdatePolicy.isUpToDate(hash, "cd".repeat(32)))
        fun rejection(actual: String = hash, pkg: String = "companion", version: Long = 2,
                      signers: Set<String> = setOf("certificate"), installedSigners: Set<String> = setOf("certificate")) =
            UpdatePolicy.archiveRejection(hash, actual, "companion", pkg, 1, version, installedSigners, signers)
        assertNull(rejection())
        assertEquals(UpdateResult.HASH_MISMATCH, rejection(actual = "cd".repeat(32)))
        assertEquals(UpdateResult.WRONG_PACKAGE, rejection(pkg = "companion.debug"))
        assertEquals(UpdateResult.NOT_NEWER, rejection(version = 1))
        assertEquals(UpdateResult.NOT_NEWER, rejection(version = 0))
        assertEquals(UpdateResult.SIGNATURE_MISMATCH, rejection(signers = setOf("other")))
        assertEquals(UpdateResult.SIGNATURE_MISMATCH, rejection(signers = emptySet(), installedSigners = emptySet()))
        assertEquals(UpdateResult.SIGNATURE_MISMATCH, rejection(signers = setOf("certificate", "other")))
    }

    @Test fun automaticChecksRequireToggleStandbyAndIntervalAndInstallRechecksWake() {
        val now = 10 * UpdatePolicy.CHECK_INTERVAL_MILLIS
        assertTrue(UpdatePolicy.shouldCheckAutomatically(true, false, 0, now))
        assertTrue(UpdatePolicy.shouldCheckAutomatically(true, false, now - UpdatePolicy.CHECK_INTERVAL_MILLIS, now))
        assertFalse(UpdatePolicy.shouldCheckAutomatically(true, false, now - UpdatePolicy.CHECK_INTERVAL_MILLIS + 1, now))
        assertFalse(UpdatePolicy.shouldCheckAutomatically(false, false, 0, now))
        assertFalse(UpdatePolicy.shouldCheckAutomatically(true, true, 0, now))
        assertFalse(UpdatePolicy.shouldCheckAutomatically(true, false, now + 1, now))
        assertTrue(UpdatePolicy.mayInstallAutomatically(true, false))
        assertFalse(UpdatePolicy.mayInstallAutomatically(true, true))
        assertFalse(UpdatePolicy.mayInstallAutomatically(false, false))
    }

    @Test fun connectGuardSuppressesPostUpdateAndAllStandbyLaunches() {
        assertTrue(UpdatePolicy.shouldLaunchOnConnect(true, false))
        assertFalse(UpdatePolicy.shouldLaunchOnConnect(false, false))
        assertFalse(UpdatePolicy.shouldLaunchOnConnect(true, true))
        assertFalse(UpdatePolicy.shouldLaunchOnConnect(false, true))
        assertTrue(UpdatePolicy.completedSelfUpdate(1, 2))
        assertFalse(UpdatePolicy.completedSelfUpdate(1, 1))
        assertFalse(UpdatePolicy.completedSelfUpdate(-1, 2))
        val now = 1_000_000L
        assertTrue(UpdatePolicy.isConnectGuardFresh(now, now))
        assertTrue(UpdatePolicy.isConnectGuardFresh(now - UpdatePolicy.CONNECT_GUARD_MILLIS + 1, now))
        assertFalse(UpdatePolicy.isConnectGuardFresh(now - UpdatePolicy.CONNECT_GUARD_MILLIS, now))
        assertFalse(UpdatePolicy.isConnectGuardFresh(0, now))
        assertFalse(UpdatePolicy.isConnectGuardFresh(now + 1, now))
    }

    @Test fun installerStatusesMapToPersistentResultsIncludingUnknownFailure() {
        assertEquals(UpdateResult.PENDING_CONFIRMATION, UpdatePolicy.installerResult(-1))
        assertEquals(UpdateResult.SUCCESS, UpdatePolicy.installerResult(0))
        for (status in listOf(1, 2, 3, 4, 5, 6, 7, 8, Int.MIN_VALUE)) {
            assertEquals(UpdateResult.INSTALLER_FAILURE, UpdatePolicy.installerResult(status))
        }
    }

    @Test fun releaseRejectsCleartextAndDebugAllowsOnlyConfiguredLoopbackOrigin() {
        val feed = "https://api.github.com/repos/example/player/releases/latest"
        assertTrue(UpdatePolicy.isAllowedUrl("https://objects.githubusercontent.com/asset", false, feed))
        assertFalse(UpdatePolicy.isAllowedUrl("http://127.0.0.1/asset", false, feed))
        val local = "http://127.0.0.1:8080/latest"
        assertTrue(UpdatePolicy.isAllowedUrl("http://127.0.0.1:8080/asset", true, local))
        assertFalse(UpdatePolicy.isAllowedUrl("http://127.0.0.1:8081/asset", true, local))
        assertFalse(UpdatePolicy.isAllowedUrl("http://10.0.2.2:8080/asset", true, local))
        assertFalse(UpdatePolicy.isAllowedUrl("https://@example.org/asset", true, local))
        assertFalse(UpdatePolicy.isAllowedUrl("file:///asset", true, local))
        assertFalse(UpdatePolicy.isAllowedUrl("not a URL", true, local))
    }
}
