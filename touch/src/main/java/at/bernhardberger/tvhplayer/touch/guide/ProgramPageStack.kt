package at.bernhardberger.tvhplayer.touch.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** Main-thread navigation state, owned by one destination. Deliberately not saved to disk. */
internal class ProgramPageStack {
    private var nextVisit = 0L
    var pages by mutableStateOf(emptyList<GuidePageKey>())
        private set

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

    fun back(visiblePages: List<GuidePageKey>) {
        pages = pages.filter { it in visiblePages }.dropLast(1)
    }

    fun clear() { pages = emptyList() }

    fun removeMissingChannels(available: Set<String>) {
        val removed = pages.indexOfFirst { it is GuidePageKey.Channel && it.channelId !in available }
        if (removed >= 0) pages = pages.take(removed)
    }

    fun removeMissing(missing: Set<GuidePageKey.Details>) {
        pages = pages.filterNot { it in missing }
    }
}
