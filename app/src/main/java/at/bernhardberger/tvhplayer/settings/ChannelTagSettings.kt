package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val ALL_CHANNELS_SCOPE = "all"
private const val TAG_SCOPE_PREFIX = "tag:"
private const val SDK_U32_MAX = 0xffff_ffffL

internal val activeTagKey = longPreferencesKey("activeChannelTagIdLong")

internal fun sdkU32IdOrNull(value: Long): Long? = value.takeIf { it in 0L..SDK_U32_MAX }

internal fun persistedIdToLongOrNull(value: String): Long? =
    value.toLongOrNull()?.let(::sdkU32IdOrNull)

internal fun persistedTagScope(tagId: ChannelTagId): String = "$TAG_SCOPE_PREFIX${tagId.value}"

data class ChannelTagPreferences(
    val activeTagId: ChannelTagId? = null,
    val visibility: ChannelScopeVisibility = ChannelScopeVisibility(),
)

class ChannelTagSettingsStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)
    private val visibleScopesKey = stringSetPreferencesKey("visibleChannelScopes")
    val settings: Flow<ChannelTagPreferences> = dataStore.data.map { preferences ->
        ChannelTagPreferences(
            activeTagId = preferences[activeTagKey]?.let(::sdkU32IdOrNull)?.let(::ChannelTagId),
            visibility = decodeVisibility(preferences[visibleScopesKey]),
        )
    }

    suspend fun save(settings: ChannelTagPreferences) {
        dataStore.edit { preferences ->
            if (settings.activeTagId == null) {
                preferences.remove(activeTagKey)
            } else {
                preferences[activeTagKey] = settings.activeTagId.value
            }
            if (settings.visibility.configured) {
                preferences[visibleScopesKey] = encodeVisibility(settings.visibility)
            } else {
                preferences.remove(visibleScopesKey)
            }
        }
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
