package at.bernhardberger.tvhplayer.htsp

data class HtspMessage(
    val method: String?,               // null pro reply, pokud to tak máš
    val seq: Int?,                     // seq pro korelaci
    val fields: Map<String, Any?>,     // decoded map
    val rawPayload: ByteArray? = null  // pro muxpkt TS bytes (pokud rovnou vytáhneš)
) {

    fun int(key: String): Int? = when (val v = fields[key]) {
        is Int -> v
        is Long -> v.toInt()
        is Short -> v.toInt()
        is Byte -> v.toInt()
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }

    fun long(key: String): Long? = when (val v = fields[key]) {
        is Long -> v
        is Int -> v.toLong()
        is Short -> v.toLong()
        is Byte -> v.toLong()
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

    fun bool(key: String): Boolean? = when (val v = fields[key]) {
        is Boolean -> v
        is Int -> v != 0
        is Long -> v != 0L
        is String -> when (v.lowercase()) {
            "1", "true", "yes", "y" -> true
            "0", "false", "no", "n" -> false
            else -> null
        }

        else -> null
    }

    fun str(key: String): String? = when (val v = fields[key]) {
        is String -> v
        else -> null
    }

    fun bin(key: String): ByteArray? = when (val v = fields[key]) {
        is ByteArray -> v
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    fun map(key: String): Map<String, Any?>? = fields[key] as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun list(key: String): List<Any?>? = fields[key] as? List<Any?>

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HtspMessage) return false

        if (seq != other.seq) return false
        if (method != other.method) return false
        if (fields != other.fields) return false

        val a = rawPayload
        val b = other.rawPayload
        if (a === null && b === null) return true
        if (a === null || b === null) return false
        if (!a.contentEquals(b)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = seq ?: 0
        result = 31 * result + (method?.hashCode() ?: 0)
        result = 31 * result + fields.hashCode()
        result = 31 * result + (rawPayload?.contentHashCode() ?: 0)
        return result
    }
}

sealed interface HtspEvent {
    val connectionAttemptId: Long

    data class ServerMessage(
        val msg: HtspMessage,
        override val connectionAttemptId: Long = 0L,
        val messageSequence: Long = 0L,
    ) : HtspEvent

    data class ConnectionError(
        val error: Throwable,
        override val connectionAttemptId: Long = 0L,
    ) : HtspEvent
}

data class HtspMuxEvent(
    val msg: HtspMessage,
    val connectionAttemptId: Long,
    val messageSequence: Long = 0L,
    val muxSequence: Long = 0L,
)

fun epgEventFromFields(fields: Map<String, Any?>): EpgEventEntry? {
    val eventId = fields.intValue("eventId", "id") ?: return null
    val channelId = fields.intValue("channelId", "channel") ?: return null
    val start = fields.longValue("start", "startTime") ?: return null
    val stop = fields.longValue("stop", "stopTime") ?: return null
    val episode = fields["episode"] as? Map<*, *>

    return EpgEventEntry(
        eventId = eventId,
        channelId = channelId,
        start = start,
        stop = stop,
        title = fields.stringValue("title", "eventTitle", "name") ?: "—",
        summary = fields.stringValue("summary"),
        description = fields.stringValue("description"),
        genre = fields.stringValue("genre", "category"),
        contentType = fields.intValue("contentType", "content"),
        seasonNumber = fields.intValue("seasonNumber", "season")
            ?: episode.intValue("seasonNumber", "season"),
        episodeNumber = fields.intValue("episodeNumber")
            ?: episode.intValue("episodeNumber", "number"),
        episodeCount = fields.intValue("episodeCount")
            ?: episode.intValue("episodeCount", "count"),
        partNumber = fields.intValue("partNumber", "part")
            ?: episode.intValue("partNumber", "part"),
        partCount = fields.intValue("partCount")
            ?: episode.intValue("partCount"),
        episodeId = fields.intValue("episodeId"),
        seriesLinkId = fields.intValue("serieslinkId", "seriesLinkId"),
    )
}

private fun Map<*, *>?.intValue(vararg keys: String): Int? {
    val map = this ?: return null
    for (key in keys) {
        val value = map[key]
        when (value) {
            is Number -> return value.toInt()
            is String -> value.toIntOrNull()?.let { return it }
        }
    }
    return null
}

private fun Map<*, *>.longValue(vararg keys: String): Long? {
    for (key in keys) {
        val value = this[key]
        when (value) {
            is Number -> return value.toLong()
            is String -> value.toLongOrNull()?.let { return it }
        }
    }
    return null
}

private fun Map<*, *>.stringValue(vararg keys: String): String? {
    for (key in keys) {
        when (val value = this[key]) {
            is String -> return value.takeIf { it.isNotBlank() }
            is List<*> -> {
                val joined = value.filterIsInstance<String>().joinToString(", ")
                if (joined.isNotBlank()) return joined
            }
        }
    }
    return null
}

data class SubscriptionStatus
    (
    val id: Int,
    val state: String? = null,   // "Running" / "No input" / "Scrambled" / ...
    val subscriptionError: String? = null,
)

data class ProfileItem(
    val id: String,
    val name: String
)
