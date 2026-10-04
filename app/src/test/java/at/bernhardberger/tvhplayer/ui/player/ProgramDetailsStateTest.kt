package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import at.bernhardberger.tvheadend.sdk.core.EpgRating
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import java.util.Locale
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramDetailsStateTest {
    private val event = EpgEvent.create(EventId(2), ChannelId(1),
        Instant.fromEpochSeconds(100), Instant.fromEpochSeconds(200), title = "Later")

    @Test fun subtitleUsesOnlySuppliedSubtitleOrEpisodeFields() {
        val episode = EpgEpisode(null, null, 2, null, 3, null, null, null, "Chapter three")
        fun programme(subtitle: String?, episode: EpgEpisode?) = EpgEvent.create(
            EventId(1), ChannelId(1), event.start, event.stop, subtitle = subtitle, episode = episode)
        assertEquals("Episode title", detailsSubtitle(programme("Episode title", episode)))
        assertEquals("Chapter three", detailsSubtitle(programme(" ", episode)))
        assertEquals("S2 E3", detailsSubtitle(programme(null, episode.copy(onscreen = null))))
        assertEquals("E3", detailsSubtitle(programme(null, episode.copy(onscreen = null, seasonNumber = null))))
        assertNull(detailsSubtitle(programme(null, null)))
    }

    @Test fun scheduleIncludesTheProgrammeStartingExactlyAtTheCurrentProgrammesEnd() {
        val current = EpgEvent.create(EventId(1), ChannelId(1),
            Instant.fromEpochSeconds(0), event.start, title = "Now")
        val observation = SessionObservation.create(epgState = EpgRepositoryState.Current(
            EpgSnapshot.create(events = listOf(current, event))))
        assertEquals(listOf(current, event), channelSchedule(current, { observation.nextEvent(ChannelId(1), it) }))
    }

    @Test fun backUnwindsReadingThenOpenedProgrammeThenScheduleThenCloses() {
        val state = ProgramDetailsState().apply { tab = 1 }
        state.open(event)
        assertEquals("pushing details does not select the Details tab", 1, state.tab)
        state.lastAction = "details-more-info"
        state.readMore = true

        assertTrue(state.back())
        assertFalse(state.readMore)
        assertEquals(event, state.opened)
        assertEquals("details-more-info", state.lastAction)
        assertTrue(state.back())
        assertNull(state.opened)
        assertEquals(1, state.tab)
        assertEquals(event.id, state.returnRow)
        assertTrue(state.back())
        assertEquals(0, state.tab)
        assertNull(state.lastAction)
        assertFalse(state.back())
    }

    @Test fun backFromEitherTabFocusReturnsToTheFirstActionBeforeClosing() {
        for (tab in 0..1) {
            val state = ProgramDetailsState().apply {
                this.tab = tab
                tabFocused = true
                lastAction = "details-other-airings"
            }
            assertTrue(state.back())
            assertEquals(0, state.tab)
            assertFalse(state.tabFocused)
            assertNull(state.lastAction)
            assertFalse(state.back())
        }
    }

    @Test fun reopeningResetsAllNestedNavigationAndRequestsTheFirstAction() {
        val state = ProgramDetailsState().apply {
            open(event)
            readMore = true
            tabFocused = true
            lastAction = "details-more-info"
        }
        val previousRequest = state.focusRequest
        state.reset()
        assertEquals(0, state.tab)
        assertFalse(state.readMore)
        assertFalse(state.tabFocused)
        assertNull(state.opened)
        assertNull(state.returnRow)
        assertNull(state.lastAction)
        assertTrue(state.focusRequest > previousRequest)
        assertFalse(state.back())
    }

    @Test fun railScheduleBackReturnsToItsCallerAfterUnwindingAnOpenedProgramme() {
        val state = ProgramDetailsState()
        state.reset(startOnSchedule = true)
        assertEquals(1, state.tab)
        assertFalse(state.back())
        state.open(event)
        assertTrue(state.back())
        assertEquals(event.id, state.returnRow)
        assertFalse(state.back())
    }

    @Test fun recordingResultCannotApplyAfterClosingChangingProgrammeOrReplacingTheRequest() {
        val session = FakeSessionObservation(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).captureCurrentSession()
        val target = ProgrammeRecordingTarget.from(event, session)
        val request = at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction.CreateProgramme(target, null)
        assertTrue(detailsRecordingResultIsCurrent(request, request, target, target, infoOpen = true, sameSelection = true))
        assertFalse(detailsRecordingResultIsCurrent(request, request, target, target, infoOpen = false, sameSelection = true))
        assertFalse(detailsRecordingResultIsCurrent(request, request, target, target, infoOpen = true, sameSelection = false))
        assertFalse(detailsRecordingResultIsCurrent(request, request, target, target.copy(eventId = EventId(3)), true, true))
        assertFalse(detailsRecordingResultIsCurrent(request, request, target, target.copy(title = "Updated"), true, true))
        assertFalse(detailsRecordingResultIsCurrent(request, request, target, null, true, true))
        assertFalse(detailsRecordingResultIsCurrent(request, request.copy(), target, target, true, true))
        assertFalse(detailsRecordingResultIsCurrent(request, null, target, target, true, true))
        val navigation = ProgramDetailsState()
        val before = navigation.selectionVersion
        navigation.open(event)
        navigation.back()
        assertTrue("returning to the same event does not revive a request", navigation.selectionVersion > before)
    }

    @Test fun factsOnlyBreakBetweenCompleteSegments() {
        assertEquals("1\u00a0·\u00a0ORF1\u00a0HD · 20:15\u2060–\u206021:45 · 90\u00a0min · S2\u00a0E4",
            programmeFactsSegments(listOf("1 · ORF1 HD", "20:15–21:45", "90 min", "S2 E4")))
    }

    @Test fun readingIncludesEverySuppliedViewerFieldAndOmitsMissingOrDuplicateText() {
        val rich = EpgEvent.create(event.id, event.channelId, event.start, event.stop,
            description = "Full\\ntext", summary = "Short summary", genre = "Drama",
            categories = listOf("Film", " ", "Film"), keywords = listOf("Mountain", "Rescue"),
            episode = EpgEpisode(null, null, 2, 5, 3, 10, 1, 2, "S2 E3"),
            rating = EpgRating(12, "PG", null, "Authority", "AT", 4), copyrightYear = 2024,
            firstAired = Instant.fromEpochSeconds(1_704_110_400))
        val fields = programmeReadingFields(rich, Locale.US).toMap()
        assertEquals("Full\ntext", fields[R.string.details_description])
        assertEquals("Short summary", fields[R.string.details_summary])
        assertEquals("Film", fields[R.string.details_categories])
        assertEquals("Mountain · Rescue", fields[R.string.details_keywords])
        assertEquals("S2 E3", fields[R.string.details_episode])
        assertEquals("2", fields[R.string.details_season])
        assertEquals("5", fields[R.string.details_seasons])
        assertEquals("3", fields[R.string.details_episode_number])
        assertEquals("10", fields[R.string.details_episodes])
        assertEquals("1", fields[R.string.details_part])
        assertEquals("2", fields[R.string.details_parts])
        assertEquals("12", fields[R.string.details_age_rating])
        assertEquals("PG", fields[R.string.details_rating])
        assertEquals("Authority", fields[R.string.details_rating_authority])
        assertEquals("AT", fields[R.string.details_rating_country])
        assertEquals("4", fields[R.string.details_stars])
        assertEquals("2024", fields[R.string.details_copyright_year])
        assertTrue(fields.getValue(R.string.details_first_aired).contains("2024"))
        assertTrue(programmeReadingFields(event, Locale.US).isEmpty())
        val duplicate = EpgEvent.create(event.id, event.channelId, event.start, event.stop,
            description = "Same text", summary = " Same text ")
        assertEquals(listOf(R.string.details_description to "Same text"), programmeReadingFields(duplicate, Locale.US))
        val xmltv = EpgEvent.create(event.id, event.channelId, event.start, event.stop,
            rating = EpgRating(null, "XMLTV:Freiwillige Selbstkontrolle der Filmwirtschaft:0", null,
                "Freiwillige Selbstkontrolle der Filmwirtschaft", null, null))
        assertEquals(listOf(R.string.details_rating to "0",
            R.string.details_rating_authority to "Freiwillige Selbstkontrolle der Filmwirtschaft"),
            programmeReadingFields(xmltv, Locale.US))
    }
}
