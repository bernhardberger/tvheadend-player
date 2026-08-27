package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamProfileSelectionPolicyTest {
    private val profiles = listOf(
        StreamProfileSelectionOption(id = "uuid-direct", name = "htsp"),
        StreamProfileSelectionOption(id = "uuid-pass", name = "pass"),
    )

    @Test
    fun persistedUuidWinsOnlyWhenItExistsInCurrentDiscovery() {
        assertEquals(
            "uuid-pass",
            selectedStreamProfileUuid(
                persistedUuid = "uuid-pass",
                legacyName = "htsp",
                currentUuid = "uuid-direct",
                discoveredProfiles = profiles,
            ),
        )
        assertEquals(
            "uuid-direct",
            selectedStreamProfileUuid(
                persistedUuid = "removed-uuid",
                legacyName = "htsp",
                currentUuid = "uuid-direct",
                discoveredProfiles = profiles,
            ),
        )
    }

    @Test
    fun legacyEvidenceSelectsOnlyOneCaseSensitiveExactMatch() {
        assertEquals("uuid-direct", exactLegacyProfileUuid("htsp", profiles))
        assertNull(exactLegacyProfileUuid("HTSP", profiles))
        assertNull(
            exactLegacyProfileUuid(
                "htsp",
                profiles + StreamProfileSelectionOption(id = "uuid-duplicate", name = "htsp"),
            ),
        )
    }

    @Test
    fun legacyEvidenceWinsOverCurrentAndFirstFallbackWithoutBeingPersistedHere() {
        assertEquals(
            "uuid-pass",
            selectedStreamProfileUuid(
                persistedUuid = null,
                legacyName = "pass",
                currentUuid = "uuid-direct",
                discoveredProfiles = profiles,
            ),
        )
    }

    @Test
    fun currentThenFirstProfileProvideDeterministicNonPersistentFallbacks() {
        assertEquals(
            "uuid-pass",
            selectedStreamProfileUuid(null, null, "uuid-pass", profiles),
        )
        assertEquals(
            "uuid-direct",
            selectedStreamProfileUuid(null, null, "removed-uuid", profiles),
        )
        assertNull(selectedStreamProfileUuid(null, null, null, emptyList()))
    }
}
