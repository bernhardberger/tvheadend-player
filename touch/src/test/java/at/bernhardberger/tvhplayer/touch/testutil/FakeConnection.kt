package at.bernhardberger.tvhplayer.touch.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrRepository
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.touch.guide.GuideViewModel
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsViewModel
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The app's session and profile owner on the SDK's fakes, as the composition root builds them.
 * Everything runs on the main looper, so Robolectric idling settles it; [close] after each test.
 * [readForEditing] is what the editor reads for the connection in use (the fake store has no
 * editing read of its own). While [writesHeld], the owner's profile writes wait, so a test can act
 * while a save is on its way; the test's own setup writes to [store] directly.
 */
@OptIn(TvheadendTestingApi::class)
internal class FakeConnection(
    readForEditing: ServerProfileEditReadResult = ServerProfileEditReadResult.Missing,
) {
    val session = FakeTvheadendSession()
    val dvrRepliesHeld = MutableStateFlow(false)
    val scheduleRequests = mutableListOf<DvrScheduleRequest>()
    private val sdkDvr = session.dvrRepository
    val browseSession = object : TvheadendSession by session {
        override val dvrRepository = object : DvrRepository by sdkDvr {
            override suspend fun scheduleEntry(currentSession: CurrentSessionObservation, request: DvrScheduleRequest) =
                sdkDvr.scheduleEntry(currentSession, request).also {
                    scheduleRequests += request
                    dvrRepliesHeld.first { !it }
                }

            override suspend fun deleteEntry(currentSession: CurrentSessionObservation, id: DvrEntryId) =
                sdkDvr.deleteEntry(currentSession, id).also { dvrRepliesHeld.first { !it } }
        }
    }
    val store = FakeServerProfileStore()
    val writesHeld = MutableStateFlow(false)
    private val writes = HeldWrites(store, writesHeld)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val owner = AppProfileOwner(
        session, writes, PlayerSettingsStore(MemoryPreferences()), Dispatchers.Main,
        { readForEditing },
    )
    /** The queue's clock; a test that times notices sets it to the Compose main clock. */
    var noticeClock: () -> Long = { 0L }

    /** The app's one notice queue, for the connection in use as the composition root builds it. */
    val noticeCenter = NoticeCenter({ noticeClock() }) {
        NoticeContext(owner.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
    }
    private val notices = ConnectionNoticeSource(
        session.observation, owner.configurationGeneration, noticeCenter, owner.switchInProgress,
    )

    /** Starts the owner, which reads the saved connection as the app does at start. */
    fun start() = apply {
        scope.launch { owner.run() }
        scope.launch { notices.run() }
    }

    /** The connection state the app shows, as the composition root's view model gives it. */
    fun presentation() = AppConnectionViewModel(session, owner, notices)

    /** The Channels destination's state, as the composition root's view model gives it. */
    fun channels() = ChannelsViewModel(session, ChannelTagSettingsStore(MemoryPreferences()), owner.liveProfile)

    /** The Recordings destination's state, as the composition root's view model gives it. */
    fun recordings() = RecordingsViewModel(session)

    /** True when the last applied password write carried this sign-in; the values never leave the test. */
    fun savedSignIn(username: String, password: String): Boolean = writes.lastSignIn == (username to password)

    /** The next applied write is stored, but the list read right after it fails, as a store can. */
    fun failReadAfterNextWrite() {
        writes.failReadAfterNextWrite = true
    }

    /** The Guide on Main, so a coverage request settles with the looper. */
    fun guide() = GuideViewModel(session, EmptyCoroutineContext)

    fun close() = scope.cancel()
}

private class HeldWrites(
    private val store: FakeServerProfileStore,
    private val held: MutableStateFlow<Boolean>,
) : ServerProfileStore by store {
    var lastSignIn: Pair<String, String>? = null
        private set
    var failReadAfterNextWrite = false
    private var failRead = false

    override suspend fun profiles(): ServerProfileListResult {
        if (!failRead) return store.profiles()
        failRead = false
        return ServerProfileListResult.Unavailable
    }

    private suspend fun <T> write(operation: suspend () -> T): T {
        held.first { !it }
        return operation().also {
            if (it is ServerProfileWriteResult.Applied && failReadAfterNextWrite) {
                failReadAfterNextWrite = false
                failRead = true
            }
        }
    }

    override suspend fun addAnonymous(host: String, port: Int, label: String?, activate: Boolean) =
        write { store.addAnonymous(host, port, label, activate) }

    override suspend fun addPassword(
        host: String, port: Int, username: String, password: String, label: String?, activate: Boolean,
    ) = write { store.addPassword(host, port, username, password, label, activate) }
        .also { if (it is ServerProfileWriteResult.Applied) lastSignIn = username to password }

    override suspend fun updateAnonymous(id: ServerProfileId, host: String, port: Int, label: String?) =
        write { store.updateAnonymous(id, host, port, label) }

    override suspend fun updatePassword(
        id: ServerProfileId, host: String, port: Int, username: String, password: String, label: String?,
    ) = write { store.updatePassword(id, host, port, username, password, label) }
        .also { if (it is ServerProfileWriteResult.Applied) lastSignIn = username to password }

    override suspend fun updateLabel(id: ServerProfileId, label: String?): ServerProfileWriteResult =
        write { store.updateLabel(id, label) }

    override suspend fun remove(id: ServerProfileId): ServerProfileWriteResult = write { store.remove(id) }
}

private class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(data.value).also { data.value = it }
}
