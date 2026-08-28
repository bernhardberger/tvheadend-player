package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import at.bernhardberger.tvhplayer.core.updateChannelScopeVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private const val ALL_CHANNELS_SCOPE = "all"
private const val TAG_SCOPE_PREFIX = "tag:"
private const val SDK_U32_MAX = 0xffff_ffffL

internal val legacyActiveTagKey = intPreferencesKey("activeChannelTagId")
internal val activeTagKey = longPreferencesKey("activeChannelTagIdLong")

internal fun sdkU32IdOrNull(value: Long): Long? = value.takeIf { it in 0L..SDK_U32_MAX }

internal fun persistedIdToLongOrNull(value: String): Long? {
    val persisted = value.toLongOrNull() ?: return null
    val normalized = when {
        persisted < Int.MIN_VALUE -> return null
        persisted < 0L -> persisted.toInt().toUInt().toLong()
        else -> persisted
    }
    return sdkU32IdOrNull(normalized)
}

internal fun persistedTagScope(tagId: ChannelTagId): String = "$TAG_SCOPE_PREFIX${tagId.value}"

internal fun intToLongPreferenceMigration(legacyKey: Preferences.Key<Int>, losslessKey: Preferences.Key<Long>) =
    object : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = currentData[legacyKey] != null
    override suspend fun migrate(currentData: Preferences) = currentData.toMutablePreferences().apply {
        this[losslessKey] = this[losslessKey] ?: checkNotNull(currentData[legacyKey]).toUInt().toLong()
        remove(legacyKey)
    }
    override suspend fun cleanUp() = Unit
}

internal fun activeTagIdMigration() = intToLongPreferenceMigration(legacyActiveTagKey, activeTagKey)

class ChannelTagSettingsStore(private val context: Context) {
    private val visibleScopesKey = stringSetPreferencesKey("visibleChannelScopes")
    private val _unavailableTagNotice = MutableStateFlow(false)

    val activeTagId: Flow<ChannelTagId?> = context.dataStore.data.map {
        it[activeTagKey]?.let(::sdkU32IdOrNull)?.let(::ChannelTagId)
    }
    val scopeVisibility: Flow<ChannelScopeVisibility> = context.dataStore.data.map { preferences ->
        decodeVisibility(preferences[visibleScopesKey])
    }
    val unavailableTagNotice = _unavailableTagNotice.asStateFlow()

    suspend fun selectTag(tagId: ChannelTagId?) {
        context.dataStore.edit { preferences ->
            if (tagId == null) {
                preferences.remove(activeTagKey)
            } else {
                preferences[activeTagKey] = tagId.value
            }
        }
        _unavailableTagNotice.value = false
    }

    suspend fun fallbackToScope(tagId: ChannelTagId?) {
        context.dataStore.edit { preferences ->
            if (tagId == null) {
                preferences.remove(activeTagKey)
            } else {
                preferences[activeTagKey] = tagId.value
            }
        }
        _unavailableTagNotice.value = true
    }

    suspend fun setScopeVisible(
        tagId: ChannelTagId?,
        visible: Boolean,
        availableTagIds: Set<ChannelTagId>,
    ) {
        context.dataStore.edit { preferences ->
            val updated = updateChannelScopeVisibility(
                current = decodeVisibility(preferences[visibleScopesKey]),
                availableTagIds = availableTagIds,
                tagId = tagId,
                visible = visible,
            )
            preferences[visibleScopesKey] = encodeVisibility(updated)
        }
    }

    fun dismissUnavailableTagNotice() {
        _unavailableTagNotice.value = false
    }

    private fun decodeVisibility(values: Set<String>?): ChannelScopeVisibility {
        if (values == null) return ChannelScopeVisibility()
        return ChannelScopeVisibility(
            configured = true,
            allChannelsVisible = ALL_CHANNELS_SCOPE in values,
            visibleTagIds = values.mapNotNullTo(mutableSetOf()) { value ->
                value.removePrefix(TAG_SCOPE_PREFIX)
                    .takeIf { value.startsWith(TAG_SCOPE_PREFIX) }
                    ?.let(::persistedIdToLongOrNull)
                    ?.let(::ChannelTagId)
            },
        )
    }

    private fun encodeVisibility(visibility: ChannelScopeVisibility): Set<String> = buildSet {
        if (visibility.allChannelsVisible) add(ALL_CHANNELS_SCOPE)
        visibility.visibleTagIds.forEach { add(persistedTagScope(it)) }
    }
}
