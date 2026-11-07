package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.DvrChangeKind
import at.bernhardberger.tvheadend.sdk.core.DvrChangeOrigin
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvheadend.sdk.core.DvrSubscriptionError
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.PauseUnavailableReason
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
    @Test fun failedServerSwitchDistinguishesConnectionAndSignIn() {
        assertEquals("Can’t connect to Cabin", formatter.format(Notice.ServerConnectionFailed("Cabin")).headline)
        assertEquals("Couldn’t sign in to Cabin. Check the username and password.",
            formatter.format(Notice.ServerSignInRejected("Cabin")).headline)
    }
    private val formatter get() = NoticeFormatter(ApplicationProvider.getApplicationContext<Application>())

    @Test fun successfulSwitchNamesTheServerWithTheConnectedIcon() {
        val id = at.bernhardberger.tvheadend.sdk.core.ServerProfileId.from("00000000-0000-4000-8000-000000000001")
        val formatted = formatter.format(Notice.ServerSwitched(id, "Cabin"))
        assertEquals("Connected to Cabin", formatted.headline)
        assertEquals(AppNoticeIcon.CONNECTED, formatted.icon)
        val other = at.bernhardberger.tvheadend.sdk.core.ServerProfileId.from("00000000-0000-4000-8000-000000000002")
        assertFalse(Notice.ServerSwitched(id, "Cabin").key == Notice.ServerSwitched(other, "Cabin").key)
    }

    @Test fun removedServerNoticeNamesTheServer() {
        val id = at.bernhardberger.tvheadend.sdk.core.ServerProfileId.from("00000000-0000-4000-8000-000000000001")
        assertEquals("Removed kids on tvheadend.home.arpa",
            formatter.format(Notice.ServerProfileRemoved(id, "kids on tvheadend.home.arpa")).headline)
    }

    @Test fun automaticAdSkipUsesInformationalEnglishText() {
        assertEquals("Ad break skipped", formatter.format(Notice.AutomaticAdBreakSkip(1_000)).headline)
        assertEquals(at.bernhardberger.tvhplayer.notices.NoticeSeverity.INFO, Notice.AutomaticAdBreakSkip(1_000).severity)
        assertFalse(Notice.AutomaticAdBreakSkip(1_000).key == Notice.AutomaticAdBreakSkip(3_000).key)
    }

    @Test fun unavailablePauseNamesTheSettingOrChannel() {
        assertEquals("Timeshift is off in Settings.",
            formatter.format(Notice.PauseUnavailable(PauseUnavailableReason.SETTINGS_OFF)).headline)
        assertEquals("Pause isn't available on this channel.",
            formatter.format(Notice.PauseUnavailable(PauseUnavailableReason.CHANNEL_UNAVAILABLE)).headline)
    }

    @Test @Config(qualifiers = "de") fun unavailablePauseUsesTheGermanSettingsLabel() {
        assertEquals("Timeshift ist in den Einstellungen ausgeschaltet.",
            formatter.format(Notice.PauseUnavailable(PauseUnavailableReason.SETTINGS_OFF)).headline)
        assertEquals("Pausieren ist auf diesem Sender nicht möglich.",
            formatter.format(Notice.PauseUnavailable(PauseUnavailableReason.CHANNEL_UNAVAILABLE)).headline)
    }

    @Test fun lostNoticeOnlyMentionsSavedChannelsWhenPresent() {
        assertEquals("Connection lost", formatter.format(Notice.Connection(
            at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind.LOST)).headline)
        assertEquals("Connection lost • Showing saved channels", formatter.format(Notice.Connection(
            at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind.LOST, true)).headline)
    }

    @Test fun connectionNoticesShowWhetherThePlugIsIn() {
        assertEquals(AppNoticeIcon.DISCONNECTED, formatter.format(Notice.Connection(ConnectionNoticeKind.LOST)).icon)
        assertEquals(AppNoticeIcon.CONNECTED, formatter.format(Notice.Connection(ConnectionNoticeKind.RESTORED)).icon)
        assertEquals(AppNoticeIcon.SIGN_IN_REJECTED, formatter.format(Notice.Connection(ConnectionNoticeKind.LOGIN_REJECTED)).icon)
    }

    @Test fun aFailedSwitchShowsItsOwnIconForUnreachableAndRejected() {
        assertEquals(AppNoticeIcon.DISCONNECTED, formatter.format(Notice.ServerConnectionFailed("Cabin")).icon)
        assertEquals(AppNoticeIcon.SIGN_IN_REJECTED, formatter.format(Notice.ServerSignInRejected("Cabin")).icon)
    }

    @Test @Config(qualifiers = "de") fun lostNoticeWithAndWithoutSavedChannelsInGerman() {
        assertEquals("Verbindung verloren", formatter.format(Notice.Connection(
            at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind.LOST)).headline)
        assertEquals("Verbindung verloren • Gespeicherte Sender werden angezeigt", formatter.format(Notice.Connection(
            at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind.LOST, true)).headline)
    }

    @Test fun malformedMutationDoesNotClaimRejectionOrSuccess() {
        val result = formatter.format(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.UNKNOWN_OUTCOME))
        assertEquals("Recording change not confirmed", result.headline)
        assertEquals("Check the recording before trying again.", result.detail)
    }

    @Test fun failedActionHeadlineNamesTheAction() {
        val headlines = listOf(DvrMutationKind.SCHEDULE, DvrMutationKind.STOP, DvrMutationKind.CANCEL, DvrMutationKind.DELETE).associateWith {
            formatter.format(Notice.DvrActionFailed(it, DvrMutationFeedback.PERMISSION_DENIED)).headline
        }
        assertEquals(mapOf(
            DvrMutationKind.SCHEDULE to "Couldn’t schedule recording",
            DvrMutationKind.STOP to "Couldn’t stop recording",
            DvrMutationKind.CANCEL to "Couldn’t cancel recording",
            DvrMutationKind.DELETE to "Couldn’t delete recording",
        ), headlines)
    }

    @Test fun timeoutAndUnknownOutcomeShareOneNotConfirmedText() {
        for (action in listOf(DvrMutationKind.SCHEDULE, DvrMutationKind.DELETE)) for (failure in listOf(DvrMutationFeedback.TIMEOUT, DvrMutationFeedback.UNKNOWN_OUTCOME)) {
            val result = formatter.format(Notice.DvrActionFailed(action, failure))
            assertEquals("Recording change not confirmed", result.headline)
            assertEquals("Check the recording before trying again.", result.detail)
        }
    }

    @Test fun failedActionDetailNamesTheServerNotTheProduct() {
        fun detail(failure: DvrMutationFeedback, serverMessage: String? = null) =
            formatter.format(Notice.DvrActionFailed(DvrMutationKind.STOP, failure, serverMessage)).detail
        assertEquals("This account can’t manage recordings.", detail(DvrMutationFeedback.PERMISSION_DENIED))
        assertEquals("The server refused the request.", detail(DvrMutationFeedback.REJECTED))
        assertEquals("The server refused the request. Disk quota", detail(DvrMutationFeedback.REJECTED, "Disk quota"))
        assertEquals("This Tvheadend server can’t do this.", detail(DvrMutationFeedback.NOT_SUPPORTED))
        assertEquals("The server is busy. Try again in a moment.", detail(DvrMutationFeedback.CONNECTION_LIMIT))
        assertEquals("Not connected to the server.", detail(DvrMutationFeedback.CONNECTION_UNAVAILABLE))
    }

    @Test fun recordingFailureReasonReusesTheCatalogTitlesOfTheSameCondition() {
        fun detail(error: DvrSubscriptionError) = formatter.format(Notice.Dvr(DvrChangeKind.RECORDING_FAILED,
            DvrEntry.create(DvrEntryId(1), title = "Show", subscriptionError = error), DvrChangeOrigin.External)).detail
        assertEquals("Show • No free tuner", detail(DvrSubscriptionError.NO_FREE_ADAPTER))
        assertEquals("Show • No signal", detail(DvrSubscriptionError.BAD_SIGNAL))
        assertEquals("Show • Channel not available", detail(DvrSubscriptionError.MUX_NOT_ENABLED))
        assertEquals("Show • Channel can’t be decrypted", detail(DvrSubscriptionError.SCRAMBLED))
        assertEquals("Show • Recording storage full", detail(DvrSubscriptionError.NO_DISK_SPACE))
        assertEquals("Show • Recording target unavailable", detail(DvrSubscriptionError.INVALID_TARGET))
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

    @Test fun retainedRemovedEntryIsDeletedAndRealCancellationsStayCanceled() {
        val removed = DvrEntry.create(DvrEntryId(1), title = "Show", state = DvrEntryState.REMOVED)
        val result = formatter.format(Notice.Dvr(DvrChangeKind.REMOVED, removed,
            DvrChangeOrigin.ThisClient(DvrMutationKind.DELETE)))
        assertEquals("Recording deleted", result.headline)
        assertEquals(AppNoticeIcon.DELETE, result.icon)
        for ((state, origin) in listOf(
            DvrEntryState.SCHEDULED to DvrChangeOrigin.External,
            DvrEntryState.INVALID to DvrChangeOrigin.ThisClient(DvrMutationKind.CANCEL),
        )) {
            val canceled = formatter.format(Notice.Dvr(DvrChangeKind.REMOVED,
                DvrEntry.create(DvrEntryId(2), state = state), origin))
            assertEquals("Recording canceled", canceled.headline)
            assertEquals(AppNoticeIcon.CANCEL, canceled.icon)
        }
    }
}
