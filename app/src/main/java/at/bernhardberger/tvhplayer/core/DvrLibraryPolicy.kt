package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrConfig
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState

enum class DvrSection {
    UPCOMING_ACTIVE,
    COMPLETED,
    FAILED_CANCELLED,
}

sealed interface DvrConfigChoice {
    data class Automatic(val configId: String?) : DvrConfigChoice
    data class RequiresSelection(val configs: List<DvrConfig>) : DvrConfigChoice
}

fun chooseDvrConfig(configs: List<DvrConfig>): DvrConfigChoice {
    val usable = configs.filter { it.enabled }
    return when {
        usable.size <= 1 -> DvrConfigChoice.Automatic(usable.singleOrNull()?.id)
        else -> DvrConfigChoice.RequiresSelection(usable)
    }
}

fun groupRecordings(entries: List<DvrEntry>): Map<DvrSection, List<DvrEntry>> = mapOf(
    DvrSection.UPCOMING_ACTIVE to entries
        .filter { it.state == DvrState.SCHEDULED || it.state == DvrState.RECORDING }
        .sortedWith(
            compareBy<DvrEntry> { it.state != DvrState.RECORDING }
                .thenBy { it.start }
        ),
    DvrSection.COMPLETED to entries
        .filter { it.state == DvrState.COMPLETED }
        .sortedByDescending { it.start },
    DvrSection.FAILED_CANCELLED to entries
        .filter { it.state == DvrState.FAILED || it.state == DvrState.CANCELLED }
        .sortedByDescending { it.start },
)
