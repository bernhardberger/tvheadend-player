package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.DvrChangeKind
import at.bernhardberger.tvheadend.sdk.core.DvrChangeOrigin
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeIcon
import at.bernhardberger.tvhplayer.ui.notifications.NoticeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class NoticeFormatterTest {
    private val formatter get() = NoticeFormatter(ApplicationProvider.getApplicationContext<Application>())

    @Test fun lostNoticeOnlyMentionsSavedChannelsWhenPresent() {
        assertEquals("Connection lost", formatter.format(Notice.Connection(
            at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind.LOST)).headline)
        assertEquals("Connection lost • Showing saved channels", formatter.format(Notice.Connection(
            at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind.LOST, true)).headline)
    }

    @Test fun connectionNoticesShowWhetherThePlugIsIn() {
        assertEquals(AppNoticeIcon.DISCONNECTED, formatter.format(Notice.Connection(ConnectionNoticeKind.LOST)).icon)
        assertEquals(AppNoticeIcon.CONNECTED, formatter.format(Notice.Connection(ConnectionNoticeKind.RESTORED)).icon)
        assertEquals(AppNoticeIcon.WARNING, formatter.format(Notice.Connection(ConnectionNoticeKind.LOGIN_REJECTED)).icon)
    }

    @Test @Config(qualifiers = "de") fun lostNoticeWithAndWithoutSavedChannelsInGerman() {
        assertEquals("Verbindung verloren", formatter.format(Notice.Connection(
            at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind.LOST)).headline)
        assertEquals("Verbindung verloren • Gespeicherte Sender werden angezeigt", formatter.format(Notice.Connection(
            at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind.LOST, true)).headline)
    }

    @Test fun malformedMutationDoesNotClaimRejectionOrSuccess() {
        val result = formatter.format(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.UNKNOWN_OUTCOME))
        assertEquals("Recording action not confirmed", result.headline)
        assertEquals("The server reply could not be read. The change may have applied; check the recording before trying again.", result.detail)
    }

    @Test fun recordingStartedIncludesTheSuppliedEpisodeBeforeTheChannel() {
        val entry = DvrEntry.create(DvrEntryId(1), title = " MotoGP ",
            subtitle = " Grand Prix of Austria ", channelName = " Sport HD ")
        val result = formatter.format(Notice.Dvr(DvrChangeKind.RECORDING_STARTED, entry, DvrChangeOrigin.External))

        assertEquals("Recording started", result.headline)
        assertEquals("MotoGP • Grand Prix of Austria • Sport HD", result.detail)
    }

    @Test fun missingBlankOrDuplicateSubtitlesKeepTheExistingDetail() {
        for (subtitle in listOf(null, "", "  ", " MotoGP ")) {
            val entry = DvrEntry.create(DvrEntryId(1), title = "MotoGP", subtitle = subtitle, channelName = "Sport HD")
            val result = formatter.format(Notice.Dvr(DvrChangeKind.RECORDING_STARTED, entry, DvrChangeOrigin.External))

            assertEquals("MotoGP • Sport HD", result.detail)
        }
    }

    @Test fun groupedSchedulesAndCancellationsDoNotDescribeJustTheFirstEpisode() {
        val entry = DvrEntry.create(DvrEntryId(1), title = "MotoGP", subtitle = "Grand Prix of Austria",
            state = DvrEntryState.SCHEDULED)
        for (kind in listOf(DvrChangeKind.SCHEDULED, DvrChangeKind.REMOVED)) {
            val result = formatter.format(Notice.Dvr(kind, entry, DvrChangeOrigin.External, count = 3))

            assertFalse(result.detail.orEmpty().contains("Grand Prix of Austria"))
        }
    }
}
