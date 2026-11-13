package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.AutorecDraft
import at.bernhardberger.tvhplayer.core.AutorecEpisodeState
import at.bernhardberger.tvhplayer.core.AutorecPreviewRow
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.RecordingPattern
import at.bernhardberger.tvhplayer.core.TimerDraft
import at.bernhardberger.tvhplayer.core.newTimerDraft
import java.time.ZoneId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleId
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession

internal val automaticRecordingNow = Instant.parse("2026-11-12T12:00:00Z")
internal val automaticRecordingZone = ZoneId.of("Europe/Berlin")
internal val automaticRecordingSeries = AutorecDraft(
    kind = AutomaticRecordingKind.SERIES,
    pattern = RecordingPattern(words = "Ridge Light"),
    seriesLinkUri = "ridge-light-series",
    name = "Ridge Light",
)
internal val automaticRecordingEpisodes = listOf(
    "Frost on the Pines", "The Long Thaw", "Snowline", "Beyond the Arctic Circle", "Blue Hour", "First Light",
).mapIndexed { index, subtitle ->
    val start = Instant.parse("2026-11-13T18:00:00Z") + index.days
    AutorecPreviewRow(
        eventId = EventId(index.toLong() + 1),
        recordingId = null,
        title = "Ridge Light",
        subtitle = subtitle,
        channelId = ChannelId(101),
        start = start,
        stop = start + 50.minutes,
        state = AutorecEpisodeState.MATCH,
    )
}
internal val automaticRecordingMatches = AutorecPreviewState.Matches(automaticRecordingEpisodes, 6, false)
internal val automaticRecordingChannels = listOf(
    Channel.create(ChannelId(101), "Ridge Earth", number = 1, icon = ChannelIcon.ImageCache(ArtworkId(1))),
    Channel.create(ChannelId(102), "Harbor Sport", number = 2, icon = ChannelIcon.ImageCache(ArtworkId(2))),
    Channel.create(ChannelId(103), "Northline News", number = 3, icon = ChannelIcon.ImageCache(ArtworkId(3))),
    Channel.create(ChannelId(104), "Kite Kids", number = 4, icon = ChannelIcon.ImageCache(ArtworkId(4))),
)
internal val automaticRecordingEvents = automaticRecordingEpisodes.map {
    EpgEvent.create(
        id = requireNotNull(it.eventId), channelId = it.channelId, start = it.start, stop = requireNotNull(it.stop),
        title = it.title, subtitle = it.subtitle, image = ArtworkId(101),
        seriesLinkUri = automaticRecordingSeries.seriesLinkUri,
        description = "Seasonal mountain light, forests and weather moving over a ridge.",
    )
}
internal val automaticRecordingKeywordEvents = listOf("Marbles and Bowls", "The Quiet Moon", "Round and Round")
    .mapIndexed { index, subtitle ->
        val start = Instant.parse("2026-11-14T08:30:00Z") + index.days
        EpgEvent.create(
            id = EventId(index.toLong() + 201), channelId = ChannelId(104), start = start, stop = start + 25.minutes,
            title = "Little Orbit", subtitle = subtitle, image = ArtworkId(102),
            description = "A gentle science table about orbits using marbles and a bowl.",
        )
    }
internal val automaticRecordingSavedEntries = automaticRecordingEpisodes.take(5).mapIndexed { index, row ->
    DvrEntry.create(
        DvrEntryId(index.toLong() + 1), eventId = row.eventId, autorecRuleId = AutorecRuleId("automatic-recording"),
        title = row.title, subtitle = row.subtitle, channelId = row.channelId, start = row.start, stop = row.stop,
        duplicate = index == 1 || index == 3, enabled = index != 2,
    )
}
internal val timerRecordingSavedEntries = listOf(0, 3, 4, 5).mapIndexed { index, dayOffset ->
    val start = Instant.parse("2026-11-13T22:30:00Z") + dayOffset.days
    DvrEntry.create(
        DvrEntryId(index.toLong() + 101), timerecRuleId = TimerecRuleId("timer"),
        title = "Late news", channelId = ChannelId(103), start = start, stop = start + 60.minutes,
    )
}
internal val automaticRecordingSession = FakeTvheadendSession(
    SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
        )),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = automaticRecordingChannels)),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create(
            events = automaticRecordingEvents + automaticRecordingKeywordEvents,
        )),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = automaticRecordingSavedEntries)),
    ),
).captureCurrentSession()

internal class AutomaticRecordingEditorFixture(
    private val scope: CoroutineScope,
) {
    val submissions = mutableListOf<Pair<AutorecDraft, Map<EventId, Boolean>>>()
    var outcome: AutorecSaveOutcome = AutorecSaveOutcome.Applied(AutorecRuleId("series"))
    var closes = 0
    var deletions = 0
    var deletionResult: DvrMutationResult<*> = DvrMutationResult.Confirmed(Unit)
    val timerSubmissions = mutableListOf<TimerDraft>()
    var timerResult: DvrMutationResult<*> = DvrMutationResult.Confirmed(Unit)
    var timerNow = automaticRecordingNow

    fun timer(draft: TimerDraft = newTimerDraft(timerNow, automaticRecordingZone)) = TimerRecordingEditorState(
        draft, scope, saveDraft = { submitted, _ -> timerSubmissions += submitted; timerResult },
        now = { timerNow }, zone = automaticRecordingZone, onClose = { closes++ },
        deleteTimer = { deletions++; deletionResult },
    )

    fun state(
        draft: AutorecDraft = automaticRecordingSeries,
        editing: Boolean = false,
        prefilled: Boolean = true,
        load: suspend (AutorecDraft, AutorecDraft?, Instant, ZoneId) -> AutorecPreviewState = { _, _, _, _ ->
            automaticRecordingMatches
        },
    ) = AutomaticRecordingEditorState(
        initial = draft,
        scope = scope,
        loadPreview = load,
        saveDraft = { submitted, choices ->
            submissions += submitted to choices
            outcome
        },
        now = { automaticRecordingNow },
        zone = automaticRecordingZone,
        editing = editing,
        prefilled = prefilled,
        onClose = { closes++ },
        deleteRule = {
            deletions++
            deletionResult
        },
    )
}
