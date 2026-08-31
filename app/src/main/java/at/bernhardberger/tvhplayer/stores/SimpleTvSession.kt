package at.bernhardberger.tvhplayer.stores

import at.bernhardberger.tvhplayer.core.ProductProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SimpleTvSession {
    private val _profile = MutableStateFlow<ProductProfile>(ProductProfile.Standard)
    val profile = _profile.asStateFlow()

    fun enter(profile: ProductProfile.Appliance) {
        _profile.value = profile
    }

    fun exit() {
        _profile.value = ProductProfile.Standard
    }
}
