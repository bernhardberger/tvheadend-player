package at.bernhardberger.tvhplayer.settings

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyServerProfileMigrationTest {
    @Test
    fun missingSdkProfileIsStoredVerifiedThenLegacyMaterialIsClearedInOrder() = runTest {
        val calls = mutableListOf<String>()
        val source = LegacyServerProfile(
            host = " tvh.example.invalid ",
            port = 9982,
            username = " viewer ",
            password = LegacyPassword.Available("secret"),
        )
        var loadCount = 0

        val result = migrateLegacyServerProfile(
            loadSdkProfile = {
                calls += "loadSdk"
                if (loadCount++ == 0) SdkProfileState.Missing else {
                    SdkProfileState.Available("tvh.example.invalid", 9982, password = true)
                }
            },
            loadLegacyProfile = { calls += "loadLegacy"; source },
            storeSdkProfile = { calls += "storeSdk"; SdkProfileWriteResult.Stored },
            clearLegacyPassword = { calls += "clearPassword" },
            clearLegacyEndpoint = { calls += "clearEndpoint" },
            deleteLegacyKey = { calls += "deleteKey" },
        )

        assertEquals(LegacyProfileMigrationResult.Ready, result)
        assertEquals(
            listOf(
                "loadSdk",
                "loadLegacy",
                "storeSdk",
                "loadSdk",
                "clearPassword",
                "clearEndpoint",
                "deleteKey",
            ),
            calls,
        )
    }

    @Test
    fun unavailableOrMismatchedReadbackRetainsEveryLegacySource() = runTest {
        val unavailableCalls = mutableListOf<String>()
        assertEquals(
            LegacyProfileMigrationResult.RetryableUnavailable,
            migrateLegacyServerProfile(
                loadSdkProfile = { unavailableCalls += "loadSdk"; SdkProfileState.Unavailable },
                loadLegacyProfile = { error("legacy source must not be read") },
                storeSdkProfile = { error("profile must not be stored") },
                clearLegacyPassword = { unavailableCalls += "clearPassword" },
                clearLegacyEndpoint = { unavailableCalls += "clearEndpoint" },
                deleteLegacyKey = { unavailableCalls += "deleteKey" },
            ),
        )
        assertEquals(listOf("loadSdk"), unavailableCalls)

        val mismatchCalls = mutableListOf<String>()
        var loadCount = 0
        assertEquals(
            LegacyProfileMigrationResult.RequiresEntry,
            migrateLegacyServerProfile(
                loadSdkProfile = {
                    mismatchCalls += "loadSdk"
                    if (loadCount++ == 0) SdkProfileState.Missing else {
                        SdkProfileState.Available("other.invalid", 9982, password = false)
                    }
                },
                loadLegacyProfile = {
                    mismatchCalls += "loadLegacy"
                    LegacyServerProfile("tvh.example.invalid", 9982, "", LegacyPassword.Empty)
                },
                storeSdkProfile = { mismatchCalls += "storeSdk"; SdkProfileWriteResult.Stored },
                clearLegacyPassword = { mismatchCalls += "clearPassword" },
                clearLegacyEndpoint = { mismatchCalls += "clearEndpoint" },
                deleteLegacyKey = { mismatchCalls += "deleteKey" },
            ),
        )
        assertEquals(listOf("loadSdk", "loadLegacy", "storeSdk", "loadSdk"), mismatchCalls)
    }

    @Test
    fun availableSdkProfileResumesIdempotentCleanupWithoutReadingLegacyValues() = runTest {
        val calls = mutableListOf<String>()

        assertEquals(
            LegacyProfileMigrationResult.Ready,
            migrateLegacyServerProfile(
                loadSdkProfile = {
                    calls += "loadSdk"
                    SdkProfileState.Available("tvh.example.invalid", 9982, password = true)
                },
                loadLegacyProfile = { error("verified migration must not reread legacy values") },
                storeSdkProfile = { error("verified profile must not be replaced") },
                clearLegacyPassword = { calls += "clearPassword" },
                clearLegacyEndpoint = { calls += "clearEndpoint" },
                deleteLegacyKey = { calls += "deleteKey" },
            ),
        )
        assertEquals(
            listOf("loadSdk", "clearPassword", "clearEndpoint", "deleteKey"),
            calls,
        )
    }

    @Test
    fun processFenceRunsOneMigrationAndRetriesOnlyUnavailableProfileAccess() = runTest {
        var completedCalls = 0
        val completedFence = LegacyProfileMigrationFence {
            completedCalls += 1
            LegacyProfileMigrationResult.Ready
        }

        val first = async { completedFence.await() }
        val second = async { completedFence.await() }
        assertEquals(LegacyProfileMigrationResult.Ready, first.await())
        assertEquals(LegacyProfileMigrationResult.Ready, second.await())
        assertEquals(1, completedCalls)

        var retryCalls = 0
        val retryFence = LegacyProfileMigrationFence {
            retryCalls += 1
            if (retryCalls == 1) {
                LegacyProfileMigrationResult.RetryableUnavailable
            } else {
                LegacyProfileMigrationResult.RequiresEntry
            }
        }
        assertEquals(LegacyProfileMigrationResult.RetryableUnavailable, retryFence.await())
        assertEquals(LegacyProfileMigrationResult.RequiresEntry, retryFence.await())
        assertEquals(LegacyProfileMigrationResult.RequiresEntry, retryFence.await())
        assertEquals(2, retryCalls)
    }
}
