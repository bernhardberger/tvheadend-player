package at.bernhardberger.tvhplayer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * The remembered choices of the current server's channels or recordings. An item without an
 * entry, and any lookup before the server identity exists, has none and follows its default.
 */
data class ItemChoices<T : Any> internal constructor(val profile: String?, private val values: Map<Long, T>) {
    operator fun get(item: Long?): T? = item?.let(values::get)
}

/**
 * One choice per channel or recording, scoped by the stable SDK server profile identifier.
 * The default is the absence of an entry, so changing a setting reaches every item without
 * a choice of its own. [itemField] and [valueField] name the two stored fields of an entry.
 */
class ItemChoiceStore<T : Any>(
    private val dataStore: DataStore<Preferences>,
    name: String,
    private val itemField: String,
    private val valueField: String,
    private val serializer: KSerializer<T>,
    private val maxEntries: Int,
) {
    private class Entry<T>(val profile: String, val item: Long, val value: T)

    private val key = stringPreferencesKey(name)

    /** Re-emits only when the server identity or its choices change. */
    val choices: Flow<ItemChoices<T>> = dataStore.data
        .map { preferences ->
            val profile = preferences[activeProfileKey]
            ItemChoices(
                profile,
                if (profile == null) emptyMap()
                else decode(preferences[key]).filter { it.profile == profile }.associate { it.item to it.value },
            )
        }
        .distinctUntilChanged()

    /**
     * Remembers [value] for [item] in the [profile] captured with the choice; null forgets it.
     * Returns false, storing nothing, if that identity is absent or has since been replaced.
     */
    suspend fun set(profile: String?, item: Long, value: T?): Boolean {
        if (profile == null) return false
        var stored = false
        dataStore.edit { preferences ->
            if (preferences[activeProfileKey] != profile) return@edit
            val others = decode(preferences[key]).filterNot { it.profile == profile && it.item == item }
            val entries = if (value == null) others else others + Entry(profile, item, value)
            preferences[key] = encode(
                entries.filterNot { it.profile == profile } +
                    entries.filter { it.profile == profile }.takeLast(maxEntries),
            )
            stored = true
        }
        return stored
    }

    internal suspend fun removeProfile(profile: String) {
        dataStore.edit { it[key] = encode(decode(it[key]).filterNot { entry -> entry.profile == profile }) }
    }

    private fun encode(entries: List<Entry<T>>): String = JsonArray(
        entries.map { entry ->
            buildJsonObject {
                put("profile", entry.profile)
                put(itemField, entry.item)
                put(valueField, Json.encodeToJsonElement(serializer, entry.value))
            }
        },
    ).toString()

    /** An entry this version cannot read counts as no choice; the readable ones stay. */
    private fun decode(value: String?): List<Entry<T>> {
        val elements = try {
            value?.let { Json.parseToJsonElement(it) as? JsonArray }
        } catch (_: IllegalArgumentException) {
            null
        }
        return elements.orEmpty().mapNotNull { element ->
            try {
                val fields = element.jsonObject
                Entry(
                    fields.getValue("profile").jsonPrimitive.content,
                    fields.getValue(itemField).jsonPrimitive.long,
                    Json.decodeFromJsonElement(serializer, fields.getValue(valueField)),
                )
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: NoSuchElementException) {
                null
            }
        }
    }
}
