package at.bernhardberger.tvhplayer.settings

import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

private const val MAX_SERVER_PORT = 65535

internal fun publishedProfileId(profile: ServerProfileReadResult?, trial: Boolean): ServerProfileId? =
    if (trial) null else (profile as? ServerProfileReadResult.Available)?.id

internal suspend fun readBackSwitchRollback(
    profileStore: ServerProfileStore,
    previousId: ServerProfileId?,
    profiles: MutableStateFlow<ServerProfileListResult?>,
): Pair<ServerProfileReadResult, Boolean> {
    val rollbackFailed = previousId == null || profileStore.setActive(previousId) !is ServerProfileWriteResult.Applied
    profiles.value = profileStore.profiles()
    // A rejected rollback cannot be presented as the old selection. Reconcile with
    // persistence even when that means keeping the successfully tested target.
    return profileStore.loadActive() to rollbackFailed
}

internal suspend fun awaitSwitchOutcome(session: TvheadendSession, before: SessionObservation): SessionState =
    session.observation.first {
        it !== before && (it.sessionState is SessionState.Ready || it.sessionState is SessionState.Unavailable)
    }.sessionState

internal fun connectionFailedNotice(list: ServerProfileListResult.Available, id: ServerProfileId): Notice =
    Notice.ServerConnectionFailed(serverProfileDisplayName(list.profiles.first { it.id == id }))

internal fun postSwitchNotice(noticeCenter: () -> NoticeCenter?, notice: Notice?) {
    notice?.let { value -> noticeCenter()?.let { it.post(value, it.context()) } }
}

internal fun switchedProfileNotice(list: ServerProfileListResult.Available, id: ServerProfileId): Notice? =
    list.profiles.firstOrNull { it.id == id }?.let { Notice.ServerSwitched(id, serverProfileDisplayName(it)) }

internal fun failedSwitchNotice(
    list: ServerProfileListResult.Available,
    id: ServerProfileId,
    outcome: SessionState.Unavailable,
): Notice {
    val name = list.profiles.first { it.id == id }.let(::serverProfileDisplayName)
    return if (outcome.reason == SessionFailure.AUTHENTICATION_REJECTED)
        Notice.ServerSignInRejected(name) else Notice.ServerConnectionFailed(name)
}

internal fun duplicate(
    list: ServerProfileListResult.Available,
    host: String,
    port: Int,
    username: String?,
    noticeCenter: () -> NoticeCenter?,
    excluding: ServerProfileId? = null,
): ServerProfileId? {
    return existingServerProfile(list.profiles.filterNot { it.id == excluding }, host, port, username)?.also {
        noticeCenter()?.let { center -> center.post(Notice.ServerProfileAlreadyExists, center.context()) }
    }
}

internal fun validateServerAddress(host: String, port: Int) {
    require(host.isNotBlank() && host.none(Char::isISOControl) && port in 1..MAX_SERVER_PORT) {
        "Invalid server address"
    }
}

internal suspend fun readServerForEditing(
    id: ServerProfileId,
    readProfileForEditing: suspend (ServerProfileId) -> ServerProfileEditReadResult,
    profiles: StateFlow<ServerProfileListResult?>,
    applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
) {
    when (val result = readProfileForEditing(id)) {
        is ServerProfileEditReadResult.Anonymous -> applyAvailable(
            result.host,
            result.port,
            "",
            "",
        )
        is ServerProfileEditReadResult.Password -> applyAvailable(
            result.host,
            result.port,
            result.username,
            result.password,
        )
        ServerProfileEditReadResult.Missing -> Unit
        ServerProfileEditReadResult.Unavailable -> {
            val summary = (profiles.value as? ServerProfileListResult.Available)?.profiles?.find { it.id == id }
            if (summary != null) applyAvailable(summary.host, summary.port, summary.username.orEmpty(), "")
        }
    }
}
