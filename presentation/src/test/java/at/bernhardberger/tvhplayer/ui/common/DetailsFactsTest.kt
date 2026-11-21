package at.bernhardberger.tvhplayer.ui.common

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ContentGenre
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingStream
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.EpgCredit
import at.bernhardberger.tvheadend.sdk.core.EpgCreditRole
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRating
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvhplayer.core.programmeAboutText
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.settings.ClockFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Instant
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS")
class DetailsFactsTest {
    private val start = Instant.parse("2026-09-15T19:00:00Z")
    private val stop = Instant.parse("2026-09-15T21:00:00Z")

    @Test fun aboutFactsListOnlyFieldsThatArePresent() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        val rich = EpgEvent.create(EventId(1), ChannelId(1), start, stop, genre = "Documentary",
            episode = EpgEpisode(null, null, 2, 5, 3, 10, 1, 2, "S2 E3"),
            rating = EpgRating(12, "XMLTV:FSK:12", null, null, null, 4), copyrightYear = 2024,
            firstAired = Instant.parse("2024-01-01T12:00:00Z"), isNew = false,
            categories = listOf("Nature", " ", "Nature"), keywords = listOf("Mountain", "Winter"))
        Assert.assertEquals(listOf("Genre" to "Documentary", "Season" to "2 of 5", "Episode" to "3 of 10", "Part" to "1 of 2",
            "Year" to "2024", "Age rating" to "FSK 12", "Star rating" to "4", "First aired" to "Jan 1, 2024",
            "Broadcast" to "Repeat", "Categories" to "Nature", "Keywords" to "Mountain • Winter"),
            programmeAboutFacts(rich, null, resources, Locale.US))
        val sparse = EpgEvent.create(EventId(1), ChannelId(1), start, stop,
            episode = EpgEpisode(null, null, null, null, null, null, null, null, "Pilot"), isNew = true,
            contentGenre = ContentGenre.fromContentType(0x90))
        Assert.assertEquals(listOf("Genre" to "Education & factual", "Episode" to "Pilot", "Broadcast" to "New"),
            programmeAboutFacts(sparse, null, resources, Locale.US))
        val bare = EpgEvent.create(EventId(1), ChannelId(1), start, stop, title = "Ridge Light")
        Assert.assertEquals(emptyList<Pair<String, String>>(), programmeAboutFacts(bare, null, resources, Locale.US))
        Assert.assertEquals(listOf("Short", "Long\ntext"), programmeAboutText("Short", "Long\\ntext", synopsisShownInFull = false))
        Assert.assertEquals(listOf("Long\ntext"), programmeAboutText("Short", "Long\\ntext", synopsisShownInFull = true))
        Assert.assertEquals(emptyList<String>(), programmeAboutText(" Same", "Same ", synopsisShownInFull = true))
        Assert.assertEquals(listOf("Same"), programmeAboutText(" Same", "Same ", synopsisShownInFull = false))
        // A description that opens with the summary already says it: the summary is not repeated.
        Assert.assertEquals(listOf("A courier follows a signal.\nThe city lights up."),
            programmeAboutText("A courier follows a signal. ", "A courier follows a signal.\\nThe city lights up.",
                synopsisShownInFull = false))
        Assert.assertEquals(emptyList<String>(), programmeAboutText(" ", null, synopsisShownInFull = false))
    }

    @OptIn(ExperimentalTvheadendApi::class)
    @Test fun recordingFactsReuseTrackLanguagesAndHideUnknownAndDefaultProfile() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val profile = DvrConfiguration(DvrConfigId("default"), "", "")
        val entry = DvrEntry.create(DvrEntryId(1), configId = profile.id,
            removal = DvrRemoval.UntilSpaceNeeded,
            files = listOf(DvrRecordingFile.create(streams = listOf("deu", "ger", "eng").map {
                DvrRecordingStream(SubscriptionStreamType.AAC, language = it)
            })))
        fun facts(locale: Locale) = recordingMetadataFacts(entry, listOf(profile), context, locale, start.epochSeconds).toMap()
        Assert.assertEquals("German, English", facts(Locale.US)["Audio"])
        Assert.assertEquals("Deutsch, Englisch", facts(Locale.GERMAN)["Audio"])
        Assert.assertEquals("Kept until space is needed", facts(Locale.US)["Retention"])
        Assert.assertFalse(facts(Locale.US).containsKey("Errors"))
        Assert.assertFalse(facts(Locale.US).containsKey("Recording profile"))
        Assert.assertTrue(programmeCreditFacts(null, context.resources).isEmpty())
        val credited = EpgEvent.create(EventId(1), ChannelId(1), start, stop, credits = listOf(
            EpgCredit("Director One", EpgCreditRole.DIRECTOR), EpgCredit("Actor One", EpgCreditRole.ACTOR),
            EpgCredit("Actor Two", EpgCreditRole.ACTOR), EpgCredit("Actor Two", EpgCreditRole.ACTOR),
            EpgCredit("Actor One", EpgCreditRole.DIRECTOR), EpgCredit("Director Two", EpgCreditRole.DIRECTOR)))
        Assert.assertEquals(listOf("Cast" to listOf("Actor One", "Actor Two"), "Directors" to listOf("Director One", "Actor One", "Director Two")),
            programmeCreditFacts(credited, context.resources))
    }

    @Test fun `recording details give the recorded span as one interval with the date once`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val zone = ZoneId.systemDefault()
        val begin = Instant.fromEpochSeconds(LocalDateTime.of(2026, 9, 15, 18, 58).atZone(zone).toEpochSecond())
        val end = Instant.fromEpochSeconds(LocalDateTime.of(2026, 9, 15, 21, 5).atZone(zone).toEpochSecond())
        val now = LocalDateTime.of(2026, 10, 1, 12, 0).atZone(zone).toEpochSecond()
        val recording = DvrEntry.create(DvrEntryId(1), start = begin, stop = end)
        val oldFormat = AppClock.format
        AppClock.format = ClockFormat.TWENTY_FOUR_HOUR
        try {
            val recorded = recordingMetadataFacts(recording, emptyList(), context, Locale.US, now)
                .single { it.first == context.getString(R.string.details_recorded) }.second
            Assert.assertEquals("Tue, Sep 15, 18:58 – 21:05", recorded)
            val stoppedEarly = DvrEntry.create(DvrEntryId(2), state = DvrEntryState.COMPLETED,
                start = begin, stop = end, files = listOf(DvrRecordingFile.create(start = begin,
                    stop = Instant.fromEpochSeconds(begin.epochSeconds + 30 * 60))))
            Assert.assertEquals("Tue, Sep 15, 18:58 – 19:28", recordingMetadataFacts(stoppedEarly, emptyList(), context, Locale.US, now)
                .single { it.first == context.getString(R.string.details_recorded) }.second)
            // Nothing was recorded, so the slot it was scheduled for is labelled as such.
            for (state in listOf(DvrEntryState.MISSED, DvrEntryState.INVALID)) {
                val facts = recordingMetadataFacts(DvrEntry.create(DvrEntryId(3), state = state,
                    start = begin, stop = end), emptyList(), context, Locale.US, now)
                Assert.assertEquals(recorded, facts.single { it.first == "Scheduled" }.second)
                Assert.assertTrue(facts.none { it.first == context.getString(R.string.details_recorded) })
            }
        } finally {
            AppClock.format = oldFormat
        }
    }
}
