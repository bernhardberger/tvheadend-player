package at.bernhardberger.tvhplayer.core

enum class StreamSignalSection { STREAM, SOURCE, RECEPTION, HEALTH }
data class StreamSignalRow(val label: String, val value: String?)

/** Labels and values are formatted by the caller using existing diagnostics and resource strings. */
fun streamSignalDetails(
    stream: List<StreamSignalRow> = emptyList(),
    source: List<StreamSignalRow> = emptyList(),
    reception: List<StreamSignalRow> = emptyList(),
    health: List<StreamSignalRow> = emptyList(),
    liveFrontend: Boolean = false,
): Map<StreamSignalSection, List<StreamSignalRow>> = linkedMapOf(
    StreamSignalSection.STREAM to stream,
    StreamSignalSection.SOURCE to source,
    StreamSignalSection.RECEPTION to if (liveFrontend) reception else emptyList(),
    StreamSignalSection.HEALTH to health,
).mapValues { (_, rows) -> rows.filter { it.label.isNotBlank() && !it.value.isNullOrBlank() } }
    .filterValues { it.isNotEmpty() }
