package at.bernhardberger.tvhplayer.ui.screens.onboarding

import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState

/**
 * A running search that found [servers]. The SDK keeps this state's constructor internal and
 * ships no discovery fake, so tests reach it through reflection.
 */
internal fun discovering(vararg servers: DiscoveredTvheadendServer): TvheadendDiscoveryState =
    TvheadendDiscoveryState.Discovering::class.java
        .getDeclaredConstructor(List::class.java)
        .apply { isAccessible = true }
        .newInstance(servers.toList())
