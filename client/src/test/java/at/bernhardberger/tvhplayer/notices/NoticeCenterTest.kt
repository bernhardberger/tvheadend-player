package at.bernhardberger.tvhplayer.notices

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackNotice
import org.junit.Assert.*
import org.junit.Test

class NoticeCenterTest {
    private var time = 0L
    private var context = NoticeContext(0, null)
    private val center = NoticeCenter({ time }, { context })
    private fun post(id: Int) = center.post(Notice.Dvr(DvrChangeKind.RECORDING_STARTED,
        DvrEntry.create(DvrEntryId(id.toLong())), DvrChangeOrigin.External), context)

    @Test fun fifoReplacementKeepsItsSlotAndOnlyEightPending() {
        post(1)
        val first = center.state.value.pending.single()
        assertTrue(center.show(first.id, 4_000))
        post(2); post(3); post(2)
        assertEquals(listOf("dvr:2", "dvr:3"), center.state.value.pending.map { it.notice.key })
        (4..10).forEach(::post)
        assertEquals((3..10).map { "dvr:$it" }, center.state.value.pending.map { it.notice.key })
        assertEquals(first.id, center.state.value.active!!.notice.id)
        assertFalse(center.show(first.id, 4_000))
    }

    @Test fun pendingExpiryDoesNotCapAccessibilityExtendedDisplayOrRestartIt() {
        post(1)
        val id = center.state.value.pending.single().id
        time = 29_000
        assertTrue(center.show(id, 60_000))
        time = 31_000; center.prune()
        assertEquals(89_000L, center.state.value.active!!.expiresAt)
        assertFalse(center.show(id, 60_000))
        time = 89_000; center.prune()
        assertNull(center.state.value.candidate)
    }

    @Test fun profileAndSessionReplacementInvalidatePendingActiveAndLateResults() {
        context = NoticeContext(0, readyObservation().currentSession!!.generationIdentity)
        val old = context
        post(1); center.show(center.state.value.pending.single().id, 4_000); post(2)
        context = NoticeContext(1, null)
        center.prune()
        assertFalse(center.post(Notice.CacheClear(true), old))
        assertNull(center.state.value.candidate)
        context = NoticeContext(1, readyObservation().currentSession!!.generationIdentity)
        post(1)
        context = NoticeContext(1, readyObservation().currentSession!!.generationIdentity)
        center.prune()
        assertNull(center.state.value.candidate)
    }

    @Test fun blockedNoticesExpireAndReplacedCandidatesCannotShow() {
        post(1)
        val stale = center.state.value.pending.single().id
        time = 1_000; post(1)
        assertFalse(center.show(stale, 4_000))
        time = 31_000; center.prune()
        assertNull(center.state.value.candidate)
    }

    @Test fun backgroundFailuresAreFailuresButLimitExpiryIsInformational() {
        assertEquals(NoticeSeverity.INFO, Notice.BackgroundPlayback(BackgroundPlaybackNotice.LIMIT_EXPIRED).severity)
        assertEquals(NoticeSeverity.FAILURE, Notice.BackgroundPlayback(BackgroundPlaybackNotice.TUNER_LOST).severity)
        assertEquals(NoticeSeverity.FAILURE, Notice.BackgroundPlayback(BackgroundPlaybackNotice.INTERRUPTED).severity)
    }
}

internal fun readyObservation(): SessionObservation =
    at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).observation.value
