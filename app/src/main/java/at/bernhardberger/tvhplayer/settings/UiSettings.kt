package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.core.HomeSectionKind

data class UiSettings(
    val autoStartPlayback: Boolean = false,
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val recordingProfileId: DvrConfigId? = null,
    val channelsView: ChannelsView = ChannelsView(),
    val guideView: GuideView = GuideView(),
    val showChannelNumbers: Boolean = true,
    val hiddenHomeSections: Set<HomeSectionKind> = emptySet(),
    val whatsNewSeenVersion: Int? = null,
    val recordingsView: DvrArchiveView = DvrArchiveView(),
)

fun resolvePlaybackAutoStart(storedValue: Boolean?): Boolean = storedValue ?: false

class UiSettingsStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)
    private object Keys {
        val AUTO_START_PLAYBACK = booleanPreferencesKey("autoStartPlayback")
        val WHATS_NEW_SEEN_VERSION = intPreferencesKey("whatsNewSeenVersion")
        val CLOCK_FORMAT = stringPreferencesKey("clockFormat")
        val SHOW_CHANNEL_NUMBERS = booleanPreferencesKey("showChannelNumbers")
        val RECORDING_PROFILE = stringPreferencesKey("recordingProfile")
        val CHANNELS_LAYOUT = stringPreferencesKey("channelsLayout")
        fun channelsProgramArt(layout: ChannelsLayout) = booleanPreferencesKey("channelsProgramArt${layout.name}")
        fun channelsChannelNames(layout: ChannelsLayout) = booleanPreferencesKey("channelsChannelNames${layout.name}")
        // "guideRows" stored the retired taller rows and named today's Regular rows DENSE.
        val GUIDE_ROWS = stringPreferencesKey("guideRowDensity")
        val GUIDE_HEADLINE = booleanPreferencesKey("guideHeadline")
        val GUIDE_GENRE_COLORS = booleanPreferencesKey("guideGenreColors")
        val RECORDINGS_GROUPING = stringPreferencesKey("recordingsGrouping")
        val RECORDINGS_SORT = stringPreferencesKey("recordingsSort")
        val RECORDINGS_SHOW = stringPreferencesKey("recordingsShow")
        fun homeSection(kind: HomeSectionKind) = booleanPreferencesKey("homeSection${kind.name}")
    }

    val settings: Flow<UiSettings> = dataStore.data.map { preferences ->
        UiSettings(
            whatsNewSeenVersion = preferences[Keys.WHATS_NEW_SEEN_VERSION],
            recordingsView = DvrArchiveView(
                grouping = DvrArchiveGrouping.entries.firstOrNull { it.name == preferences[Keys.RECORDINGS_GROUPING] }
                    ?: DvrArchiveGrouping.FOLDERS,
                sort = DvrArchiveSort.entries.firstOrNull { it.name == preferences[Keys.RECORDINGS_SORT] }
                    ?: DvrArchiveSort.NEWEST,
                show = DvrArchiveShow.entries.firstOrNull { it.name == preferences[Keys.RECORDINGS_SHOW] }
                    ?: DvrArchiveShow.ALL,
            ),
            autoStartPlayback = resolvePlaybackAutoStart(preferences[Keys.AUTO_START_PLAYBACK]),
            clockFormat = resolveClockFormat(preferences[Keys.CLOCK_FORMAT]),
            showChannelNumbers = preferences[Keys.SHOW_CHANNEL_NUMBERS] ?: true,
            hiddenHomeSections = HomeSectionKind.entries.filterTo(mutableSetOf()) {
                preferences[Keys.homeSection(it)] == false
            },
            recordingProfileId = preferences[Keys.RECORDING_PROFILE]?.takeIf(String::isNotBlank)?.let(::DvrConfigId),
            channelsView = resolveChannelsView(
                preferences[Keys.CHANNELS_LAYOUT], { preferences[Keys.channelsChannelNames(it)] },
            ) { preferences[Keys.channelsProgramArt(it)] },
            guideView = resolveGuideView(
                preferences[Keys.GUIDE_ROWS], preferences[Keys.GUIDE_HEADLINE], preferences[Keys.GUIDE_GENRE_COLORS],
            ),
        )
    }

    suspend fun setClockFormat(format: ClockFormat) {
        dataStore.edit { preferences ->
            preferences[Keys.CLOCK_FORMAT] = format.name
        }
    }

    suspend fun initializeWhatsNew(currentVersion: Int, firstInstallTime: Long, lastUpdateTime: Long) {
        dataStore.edit { preferences ->
            if (preferences[Keys.WHATS_NEW_SEEN_VERSION] == null) {
                preferences[Keys.WHATS_NEW_SEEN_VERSION] =
                    if (whatsNewUnseen(null, currentVersion, firstInstallTime, lastUpdateTime)) 0 else currentVersion
            }
        }
    }

    suspend fun markWhatsNewSeen(currentVersion: Int) {
        dataStore.edit { it[Keys.WHATS_NEW_SEEN_VERSION] = currentVersion }
    }

    suspend fun setRecordingsView(view: DvrArchiveView) {
        dataStore.edit {
            it[Keys.RECORDINGS_GROUPING] = view.grouping.name
            it[Keys.RECORDINGS_SORT] = view.sort.name
            it[Keys.RECORDINGS_SHOW] = view.show.name
        }
    }

    suspend fun setShowChannelNumbers(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_CHANNEL_NUMBERS] = show }
    }

    suspend fun setHomeSectionVisible(section: HomeSectionKind, visible: Boolean) {
        dataStore.edit { it[Keys.homeSection(section)] = visible }
    }

    suspend fun setChannelsView(view: ChannelsView) {
        dataStore.edit { preferences ->
            preferences[Keys.CHANNELS_LAYOUT] = view.layout.name
            ChannelsLayout.entries.filter { it.hasArtSwitch }
                .forEach {
                    preferences[Keys.channelsProgramArt(it)] = it in view.programArt
                    preferences[Keys.channelsChannelNames(it)] = it in view.channelNames
                }
        }
    }

    suspend fun setGuideView(view: GuideView) {
        dataStore.edit { preferences ->
            preferences[Keys.GUIDE_ROWS] = view.rows.name
            preferences[Keys.GUIDE_HEADLINE] = view.headline
            preferences[Keys.GUIDE_GENRE_COLORS] = view.genreColors
        }
    }

    suspend fun setAutoStartPlayback(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_START_PLAYBACK] = enabled
        }
    }

    suspend fun setRecordingProfile(id: DvrConfigId) {
        dataStore.edit { it[Keys.RECORDING_PROFILE] = id.value }
    }
}
