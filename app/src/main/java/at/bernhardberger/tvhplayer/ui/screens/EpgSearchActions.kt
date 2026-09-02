package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgRepository
import at.bernhardberger.tvheadend.sdk.core.EpgSearchRequest
import at.bernhardberger.tvheadend.sdk.core.EpgSearchResult

internal class EpgSearchActions(
    private val searchEpg: suspend (CurrentSessionObservation, EpgSearchRequest) -> EpgSearchResult,
) {
    constructor(repository: EpgRepository) : this(repository::search)

    suspend fun execute(
        currentSession: CurrentSessionObservation,
        query: String,
        tagId: ChannelTagId?,
    ): EpgSearchResult = searchEpg(
        currentSession,
        EpgSearchRequest.create(
            query = query.trim(),
            fullText = true,
            tagId = tagId,
        ),
    )
}
