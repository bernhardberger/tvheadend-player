package at.bernhardberger.tvhplayer.ui.screens.onboarding

import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryTestFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi

/** A running search that found [servers]. */
@OptIn(TvheadendTestingApi::class)
internal fun discovering(vararg servers: DiscoveredTvheadendServer): TvheadendDiscoveryState =
    TvheadendDiscoveryTestFactory.discovering(servers.toList())
