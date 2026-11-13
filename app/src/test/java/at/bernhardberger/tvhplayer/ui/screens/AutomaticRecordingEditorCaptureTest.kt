package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.core.AutorecDraft
import at.bernhardberger.tvhplayer.core.TimerDraft
import at.bernhardberger.tvhplayer.core.newTimerDraft
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.newAutorecDraft
import at.bernhardberger.tvhplayer.core.RecordingPattern
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.programmeDetailsActions
import at.bernhardberger.tvhplayer.core.recordingDetailsActions
import at.bernhardberger.tvhplayer.core.recordingContextActions
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.previewMatches
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.BroadcastType
import at.bernhardberger.tvheadend.sdk.core.DuplicateDetection
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.BrowseContextMenu
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsPage
import at.bernhardberger.tvhplayer.ui.components.ProgrammeArtwork
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.RecordingRule
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.core.programmeArtwork
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.settings.ClockFormat
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
class AutomaticRecordingEditorCaptureTest {
    private val previousZone = TimeZone.getDefault()
    private val previousClock = AppClock.format
    @Before fun useFixtureClock() {
        TimeZone.setDefault(TimeZone.getTimeZone(automaticRecordingZone))
        AppClock.format = ClockFormat.TWENTY_FOUR_HOUR
    }
    @After fun restoreClock() {
        TimeZone.setDefault(previousZone)
        AppClock.format = previousClock
    }
    @get:Rule val compose = createComposeRule()
    @get:Rule val images = ImageLoaderRule()
    private lateinit var fixture: AutomaticRecordingEditorFixture
    private lateinit var view: View
    private var shown by mutableStateOf<AutomaticRecordingEditorState?>(null)
    private var shownTimer by mutableStateOf<TimerRecordingEditorState?>(null)
    private var entryScene by mutableStateOf<(@Composable () -> Unit)?>(null)
    private val initial = automaticRecordingSeries.copy(
        channel = RecordingRuleChannel.SpecificChannel(automaticRecordingChannels.first().id),
    )

    @Before fun renderFixture() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        images.install(FixtureArt.imageLoader(context, mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"),
            ArtworkId(2) to FixtureArt.picon("harbor-sport"),
            ArtworkId(3) to FixtureArt.picon("northline-news"),
            ArtworkId(4) to FixtureArt.picon("kite-kids"),
            ArtworkId(101) to FixtureArt.art("ridge-light"),
            ArtworkId(102) to FixtureArt.art("little-orbit"),
        )).newBuilder().coroutineContext(Dispatchers.Main.immediate)
            .fetcherCoroutineContext(Dispatchers.Main.immediate).decoderCoroutineContext(Dispatchers.Main.immediate).build())
        compose.setContent {
            val scope = rememberCoroutineScope()
            fixture = remember { AutomaticRecordingEditorFixture(scope) }
            view = LocalView.current
            TVHeadendPlayerTheme {
                ProvideArtworkSession(automaticRecordingSession) {
                    shown?.let { state ->
                        AutomaticRecordingEditor(state, automaticRecordingChannels, listOf(
                            DvrConfiguration(DvrConfigId("default"), "Default", ""),
                            DvrConfiguration(DvrConfigId("archive"), "Archive", ""),
                         ), automaticRecordingEvents + automaticRecordingKeywordEvents,
                             scheduledEpisodeCount = if (state.editing) automaticRecordingSavedEntries.size else 0)
                    }
                    shownTimer?.let { state ->
                        TimerRecordingEditor(state, automaticRecordingChannels,
                            scheduledEntries = timerRecordingSavedEntries)
                    }
                    entryScene?.invoke()
                }
            }
        }
    }

    @Test fun entryPointsAndRunningRuleReferenceFrames() {
        val directory = "slice-5"
        for ((event, action, name) in listOf(
            Triple(automaticRecordingEvents.first(), BrowseContextAction.RECORD_SERIES,
                "03a-details-record-series"),
            Triple(automaticRecordingKeywordEvents.first(), BrowseContextAction.RECORD_TITLE,
                "03b-details-record-all-title"),
        )) {
            compose.runOnIdle {
                entryScene = {
                    ProgrammeDetailsPage(
                        event, null, automaticRecordingChannels.first { it.id == event.channelId },
                        event.start.epochSeconds + 60,
                        programmeDetailsActions(event, event.start.epochSeconds + 60, recording = null,
                            recordingAdmission = null, canModifyRecordings = true, canWatchChannel = true),
                        onAction = {}, onClose = {}, initialAction = action,
                    )
                }
            }
            capture(name, directory, "details-${action.name.lowercase()}")
        }
        for (enabled in listOf(true, false)) {
            val entry = automaticRecordingEntry(enabled)
            compose.runOnIdle {
                entryScene = {
                    BrowseContextMenu(
                        title = entry.title.orEmpty(), channel = automaticRecordingChannels.first().name.orEmpty(),
                        episode = programmeEpisodeLine(entry.episode, entry.subtitle,
                            ApplicationProvider.getApplicationContext<Application>().resources),
                        time = formatRecordingDate(requireNotNull(entry.start).epochSeconds, dateOnly = true,
                            nowSec = automaticRecordingNow.epochSeconds) + " · " +
                            formatClock(requireNotNull(entry.start).epochSeconds),
                        summary = automaticRecordingEvents.first().description.orEmpty(),
                        recordingState = entry.state, recordingRule = RecordingRule.SERIES,
                        recordingKind = AutomaticRecordingKind.SERIES, recordingEnabled = enabled,
                        actions = recordingContextActions(entry, admission = null,
                            canModifyRecordings = true, ruleAvailable = true),
                        onAction = {}, onClose = {},
                        artwork = {
                            ProgrammeArtwork(programmeArtwork(automaticRecordingEvents.first()),
                                automaticRecordingChannels.first().icon, progress = null)
                        },
                    )
                }
            }
            capture(if (enabled) "14a-rule-entry-menu" else "14c-skipped-entry-menu", directory,
                "browse-context-action-${if (enabled) "skip" else "unskip"}")
        }
        val running = automaticRecordingEntry(state = DvrEntryState.RECORDING)
        compose.runOnIdle {
            entryScene = {
                ProgrammeDetailsPage(null, running, automaticRecordingChannels.first(),
                    requireNotNull(running.start).epochSeconds + 600,
                    recordingDetailsActions(running, admission = null, canModifyRecordings = true,
                        canWatchChannel = true, nowSec = requireNotNull(running.start).epochSeconds + 600,
                        ruleAvailable = true),
                    onAction = {}, onClose = {},
                    recordingKind = AutomaticRecordingKind.SERIES)
            }
        }
        compose.onNodeWithTag("details-stop").assertIsFocused()
        capture("running-rule-recording-details", directory, "details-stop")
        compose.runOnIdle { entryScene = { RunningMarkSheet() } }
        capture("running-marks-16dp-rest-and-focus", directory, "running-mark-focused")
    }

    @Composable
    private fun RunningMarkSheet() {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("One-off / Automatic recording", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground)
            for (id in listOf("rest", "focused")) {
                var focused by remember { mutableStateOf(false) }
                GuidedStepAction(
                    title = if (id == "rest") "At rest" else "Focused",
                    modifier = Modifier.testTag("running-mark-$id").onFocusChanged { focused = it.isFocused },
                    leadingIcon = {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            RecordingStatusIndicator(DvrEntryState.RECORDING, focused = focused)
                            RecordingStatusIndicator(DvrEntryState.RECORDING, rule = RecordingRule.SERIES,
                                focused = focused)
                        }
                    },
                    onClick = {},
                )
            }
        }
    }

    @Test fun seriesEditorReferenceFrames() {
        show()
        capture("04-new-series-first-level")
        compose.runOnIdle { requireNotNull(shown).navigation.focusedRow = "keep" }
        capture("05a-focus-keep-until")
        open(AutomaticRecordingEditorLevel.KEEP, "keep-null")
        capture("05b-keep-until-level")
        show()
        open(AutomaticRecordingEditorLevel.CHANNEL_TIME, "channel")
        capture("06a-channel-and-time-level")
        open(AutomaticRecordingEditorLevel.DAYS, "any-day")
        capture("06b-days-level")
        show(initial.copy(days = setOf(DayOfWeek.SATURDAY)))
        open(AutomaticRecordingEditorLevel.CHANNEL_TIME, "channel")
        open(AutomaticRecordingEditorLevel.DAYS, "any-day")
        capture("06b-days-restricted")
        compose.runOnIdle { requireNotNull(shown).back() }
        open(AutomaticRecordingEditorLevel.START_WINDOW, "any-time")
        capture("06c-start-window-level")
        show()
        open(AutomaticRecordingEditorLevel.MORE, "duplicate")
        capture("07a-more-options-level")
        open(AutomaticRecordingEditorLevel.MAXIMUM, "choice-0")
        capture("07b-maximum-recordings-level")
        compose.runOnIdle { requireNotNull(shown).back() }
        open(AutomaticRecordingEditorLevel.BROADCAST, "choice-${BroadcastType.ALL}")
        capture("07c-broadcast-type-level")
        show()
        compose.runOnIdle { requireNotNull(shown).updateDraft { it.copy(maximumRecordings = 3) } }
        capture("07d-first-level-shows-changes")
        open(AutomaticRecordingEditorLevel.MORE, "duplicate")
        open(AutomaticRecordingEditorLevel.DUPLICATE, "choice-${DuplicateDetection.DVR_PROFILE}")
        capture("07e-duplicate-detection-level")
        show()
        open(AutomaticRecordingEditorLevel.UPCOMING, "episode-1")
        capture("08-upcoming-episodes")
        show(editing = true)
        capture("11a-edit-series-unchanged")
        compose.runOnIdle {
            requireNotNull(shown).updateDraft { it.copy(enabled = false) }
            requireNotNull(shown).navigation.focusedRow = "enabled"
        }
        capture("11b-edit-series-off-in-draft")
        compose.runOnIdle { requireNotNull(shown).back() }
        capture("15-leave-with-changes")
        for ((name, preview) in listOf(
            "16a-preview-loading" to AutorecPreviewState.Loading,
            "16b-preview-failed" to AutorecPreviewState.Failed(TvheadendFailure.TIMEOUT),
            "16c-preview-no-matches" to AutorecPreviewState.Matches(emptyList(), 0, false),
        )) {
            show(preview = preview)
            capture(name)
        }
    }

    @Test fun keywordAndDeleteReferenceFrames() {
        show(newAutorecDraft(AutomaticRecordingKind.KEYWORD).copy(pattern = RecordingPattern("orbit")))
        capture("09b-keyword-with-text", "slice-3-job-b")
        open(AutomaticRecordingEditorLevel.WORDS, "words")
        capture("09c-keyword-words-level", "slice-3-job-b")
        show(editing = true)
        open(AutomaticRecordingEditorLevel.MORE, "delete")
        compose.runOnIdle {
            requireNotNull(shown).updateDraft { it.copy(removal = DvrRemoval.Forever) }
            requireNotNull(shown).navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        }
        capture("11d-delete-confirm", "slice-3-job-b")
    }

    @Test fun mergedEditorConfirmationReferenceFrames() {
        for (changed in listOf(false, true)) {
            show(editing = true)
            compose.runOnIdle {
                val state = requireNotNull(shown)
                if (changed) state.updateDraft { it.copy(enabled = false) }
                state.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE
            }
            capture("delete-series-${if (changed) "changed" else "unchanged"}", "merge-main")
        }
        show()
        compose.runOnIdle {
            requireNotNull(shown).updateDraft { it.copy(removal = DvrRemoval.Forever) }
            requireNotNull(shown).navigation.confirmation = AutomaticRecordingEditorConfirmation.LEAVE
        }
        capture("leave-new-series", "merge-main")
        compose.runOnIdle {
            shown = null
            shownTimer = fixture.timer(newTimerDraft(automaticRecordingNow, automaticRecordingZone,
                startingRepeating = true).copy(channelId = automaticRecordingChannels[2].id,
                name = "Late news", existingTimer = true))
            requireNotNull(shownTimer).navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        }
        capture("delete-repeating-timer", "merge-main")
        compose.runOnIdle {
            requireNotNull(shownTimer).navigation.confirmation = null
            requireNotNull(shownTimer).updateDraft { it.copy(days = emptySet()) }
            requireNotNull(shownTimer).navigation.confirmation = AutomaticRecordingEditorConfirmation.LEAVE
        }
        capture("leave-invalid-edited-timer", "merge-main")
    }

    @Test fun timerEditorReferenceFrames() {
        val initialTimer = newTimerDraft(automaticRecordingNow, automaticRecordingZone).copy(
            channelId = automaticRecordingChannels[2].id, date = LocalDate.of(2026, 11, 13),
            start = LocalTime.of(23, 30), end = LocalTime.of(0, 30),
        )
        compose.runOnIdle { shownTimer = fixture.timer(initialTimer) }
        capture("10a-record-by-time-first-level", "slice-4")
        compose.runOnIdle {
            requireNotNull(shownTimer).navigation.open(AutomaticRecordingEditorLevel.CHANNEL_TIME, "end")
        }
        capture("10b-record-by-time-channel-and-time", "slice-4")
        compose.runOnIdle { requireNotNull(shownTimer).openClock(true) }
        capture("10c-ends-at-hour", "slice-4")
        compose.runOnIdle { requireNotNull(shownTimer).openMinutes(0) }
        capture("10d-ends-at-minutes", "slice-4")
        compose.runOnIdle {
            requireNotNull(shownTimer).navigation.returnTo(AutomaticRecordingEditorLevel.CHANNEL_TIME)
            requireNotNull(shownTimer).navigation.open(AutomaticRecordingEditorLevel.REPEAT, "done")
        }
        capture("10e-repeat-days", "slice-4")
        val repeating = initialTimer.copy(days = DayOfWeek.entries.take(5).toSet(), name = "Late news")
        compose.runOnIdle { shownTimer = fixture.timer(repeating) }
        capture("10f-record-by-time-repeating", "slice-4")
        compose.runOnIdle {
            shownTimer = fixture.timer(newTimerDraft(automaticRecordingNow, automaticRecordingZone,
                startingRepeating = true))
        }
        capture("10g-new-repeating-timer-from-kind", "slice-4")
        compose.runOnIdle {
            shownTimer = fixture.timer(repeating.copy(existingTimer = true))
            requireNotNull(shownTimer).updateDraft { it.copy(enabled = false) }
            requireNotNull(shownTimer).navigation.focusedRow = "enabled"
        }
        capture("12b-edit-timer-off-in-draft", "slice-4")
    }

    private fun show(
        draft: AutorecDraft = initial,
        editing: Boolean = false,
        preview: AutorecPreviewState? = null,
    ) {
        compose.runOnIdle {
            shown = fixture.state(draft, editing, prefilled = draft.kind == AutomaticRecordingKind.SERIES,
                load = { current, baseline, now, zone ->
                preview ?: current.previewMatches(
                    if (draft.kind == AutomaticRecordingKind.SERIES) automaticRecordingEvents
                        else automaticRecordingKeywordEvents,
                    if (editing) automaticRecordingSavedEntries else emptyList(),
                    if (editing) AutorecRuleId("automatic-recording") else null, now, zone, baseline,
                )
            })
        }
        compose.waitForIdle()
    }

    private fun open(level: AutomaticRecordingEditorLevel, row: String) = compose.runOnIdle {
        requireNotNull(shown).navigation.open(level, row)
    }

    private fun capture(name: String, directory: String = "slice-3-job-a", focusTag: String? = null) {
        val navigation = shownTimer?.navigation ?: shown?.navigation
        val focus = focusTag ?: when (navigation?.confirmation) {
            AutomaticRecordingEditorConfirmation.LEAVE -> "keep-editing"
            AutomaticRecordingEditorConfirmation.DELETE -> "keep"
            null -> requireNotNull(navigation).focusedRow
        }
        val target = hasTestTag(focusTag ?: "${requireNotNull(navigation).tagPrefix}-$focus")
        compose.onNode(if (navigation?.confirmation != null)
            target and hasAnyAncestor(hasTestTag("centred-dialog-card")) else target)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.settleFrames(1, PlayerMotion.PanelMs.toLong())
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("../captures/autorec-impl/$directory", "$name.png")
            CaptureOutput.writePng(file, bitmap)
            CaptureOutput.writeText(File(file.parentFile, "$name.txt")) {
                "Canvas ${view.width}x${view.height}; en-US; font scale 1.0; Europe/Berlin; 24-hour; focus $focus; " +
                    "production ${if (entryScene != null) "details/menu/status components" else if (shownTimer == null)
                        "AutomaticRecordingEditor" else "TimerRecordingEditor"}; " +
                    "fictional fixture art; no device or server."
            }
            bitmap.recycle()
        }
    }
}
