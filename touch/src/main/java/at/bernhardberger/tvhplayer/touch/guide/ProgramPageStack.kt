package at.bernhardberger.tvhplayer.touch.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import java.time.LocalDate

/** Main-thread navigation state, owned by one destination. Deliberately not saved to disk. */
internal class ProgramPageStack {
    private var nextVisit = 0L
    var pages by mutableStateOf(emptyList<GuidePageKey>())
        private set

    // A resolved visit must still be drawable on the first frame after recreation.
    var detailsResult by mutableStateOf<DetailsResult?>(null)

    data class DetailsResult(
        val generation: SessionGenerationIdentity?,
        val details: Map<GuidePageKey.Details, ProgramDetails>,
    )

    /** Channels uses a single root entry for either drawing of the open channel. */
    val rootChannel: GuidePageKey.Channel? get() = pages.firstOrNull() as? GuidePageKey.Channel

    fun selectChannel(channelId: String) {
        val previous = rootChannel
        if (previous?.channelId == channelId) return
        pages = listOf(GuidePageKey.Channel(channelId, null, null, nextVisit++)) + pages.filterNot { it == previous }
    }

    fun removeChannel(channel: GuidePageKey.Channel) {
        pages = pages - channel
    }

    fun openDetails(programId: String) {
        if ((pages.lastOrNull() as? GuidePageKey.Details)?.programId == programId) return
        pages = pages + GuidePageKey.Details(programId, nextVisit++)
    }

    fun openChannel(channelId: String, day: LocalDate? = null, programId: String? = null) {
        if ((pages.lastOrNull() as? GuidePageKey.Channel)?.channelId == channelId) return
        pages = pages + GuidePageKey.Channel(channelId, day, programId, nextVisit++)
    }

    fun showInGuide(details: ProgramDetails) {
        openChannel(details.channelId, details.guideDay, details.id.takeIf { details.guideDay != null })
    }

    fun back(visiblePages: List<GuidePageKey>, paneChannel: GuidePageKey.Channel?) {
        val hasPending = pages.any { it != paneChannel && it !in visiblePages }
        val remaining = if (hasPending) visiblePages else visiblePages.dropLast(1)
        pages = pages.filter { it == paneChannel || it in remaining }
    }

    fun clear(keep: GuidePageKey.Channel? = null) {
        pages = pages.filter { it == keep }
        detailsResult = null
    }

    fun removeMissingChannels(available: Set<String>) {
        val removed = pages.indexOfFirst { it is GuidePageKey.Channel && it.channelId !in available }
        if (removed >= 0) pages = pages.take(removed)
    }

    fun removeMissing(missing: Set<GuidePageKey.Details>) {
        pages = pages.filterNot { it in missing }
    }
}
