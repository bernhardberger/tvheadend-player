package at.bernhardberger.tvhplayer.touch.recordings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import java.io.IOException
import kotlinx.coroutines.flow.first

/** How the archive is drawn, independent of its current connection or browse position. */
internal class RecordingsViewStore(private val preferences: DataStore<Preferences>) {
    suspend fun read(): DvrArchiveView {
        val defaults = DvrArchiveView()
        val saved = try {
            preferences.data.first()
        } catch (_: IOException) {
            return defaults
        }
        return DvrArchiveView(
            grouping = DvrArchiveGrouping.entries.firstOrNull { it.name == saved[Grouping] } ?: defaults.grouping,
            sort = DvrArchiveSort.entries.firstOrNull { it.name == saved[Sort] } ?: defaults.sort,
            show = DvrArchiveShow.entries.firstOrNull { it.name == saved[Show] } ?: defaults.show,
        )
    }

    suspend fun write(view: DvrArchiveView) {
        preferences.edit {
            it[Grouping] = view.grouping.name
            it[Sort] = view.sort.name
            it[Show] = view.show.name
        }
    }

    private companion object {
        val Grouping = stringPreferencesKey("touch_recordings_grouping")
        val Sort = stringPreferencesKey("touch_recordings_sort")
        val Show = stringPreferencesKey("touch_recordings_show")
    }
}
