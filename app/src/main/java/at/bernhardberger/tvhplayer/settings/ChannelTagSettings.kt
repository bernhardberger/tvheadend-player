package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import at.bernhardberger.tvhplayer.core.updateChannelScopeVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class ChannelTagSettingsStore(private val context: Context) {
    private val activeTagKey = intPreferencesKey("activeChannelTagId")
    private val visibleScopesKey = stringSetPreferencesKey("visibleChannelScopes")
    private val _unavailableTagNotice = MutableStateFlow(false)

    val activeTagId: Flow<Int?> = context.dataStore.data.map { it[activeTagKey] }
    val scopeVisibility: Flow<ChannelScopeVisibility> = context.dataStore.data.map { preferences ->
        decodeVisibility(preferences[visibleScopesKey])
    }
    val unavailableTagNotice = _unavailableTagNotice.asStateFlow()

    suspend fun selectTag(tagId: Int?) {
        context.dataStore.edit { preferences ->
            if (tagId == null) {
                preferences.remove(activeTagKey)
            } else {
                preferences[activeTagKey] = tagId
            }
        }
        _unavailableTagNotice.value = false
    }

    suspend fun fallbackToScope(tagId: Int?) {
        context.dataStore.edit { preferences ->
            if (tagId == null) {
                preferences.remove(activeTagKey)
            } else {
                preferences[activeTagKey] = tagId
            }
        }
        _unavailableTagNotice.value = true
    }

    suspend fun setScopeVisible(
        tagId: Int?,
        visible: Boolean,
        availableTagIds: Set<Int>,
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
                    ?.toIntOrNull()
            },
        )
    }

    private fun encodeVisibility(visibility: ChannelScopeVisibility): Set<String> = buildSet {
        if (visibility.allChannelsVisible) add(ALL_CHANNELS_SCOPE)
        visibility.visibleTagIds.forEach { add("$TAG_SCOPE_PREFIX$it") }
    }

    private companion object {
        const val ALL_CHANNELS_SCOPE = "all"
        const val TAG_SCOPE_PREFIX = "tag:"
    }
}
