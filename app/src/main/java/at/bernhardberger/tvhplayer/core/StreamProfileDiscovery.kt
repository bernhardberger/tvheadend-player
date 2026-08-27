package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class StreamProfileDiscovery internal constructor(
    private val ioDispatcher: CoroutineDispatcher,
    private val request: suspend () -> StreamProfilesResult,
) {
    constructor(
        session: TvheadendSession,
        ioDispatcher: CoroutineDispatcher,
    ) : this(ioDispatcher, session::getStreamProfiles)

    suspend fun discover(): StreamProfilesResult = withContext(ioDispatcher) { request() }
}
