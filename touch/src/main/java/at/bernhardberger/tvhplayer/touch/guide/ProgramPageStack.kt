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
        pages = pages + GuidePageKey.Details(programId, nextVisit++)
    }

    fun openChannel(channelId: String, day: LocalDate? = null, programId: String? = null) {
        pages = pages + GuidePageKey.Channel(channelId, day, programId, nextVisit++)
    }

    fun showInGuide(details: ProgramDetails) {
        openChannel(details.channelId, details.guideDay, details.id.takeIf { details.guideDay != null })
    }

    fun pop() { pages = pages.dropLast(1) }

    fun clear() { pages = emptyList() }

    fun removeMissing(missing: Set<GuidePageKey.Details>) {
        pages = pages.filterNot { it in missing }
    }
}
