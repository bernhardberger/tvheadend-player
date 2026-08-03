package at.bernhardberger.tvhplayer.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class ProfileDiscoveryFailurePolicyTest {
    @Test
    fun rawSdkFailureIsReducedToMessageFreeUiState() {
        val rawDetail = "server.example.invalid/private/profile"

        val state = profileDiscoveryFailureState(IllegalStateException(rawDetail))

        assertSame(ProfilesUiState.Error, state)
        assertFalse(state.toString().contains(rawDetail))
    }
}
