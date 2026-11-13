package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.core.AutorecDraft
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.newAutorecDraft
import at.bernhardberger.tvhplayer.core.RecordingPattern
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
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
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.settings.ClockFormat
import java.io.File
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
                }
            }
        }
    }

    @Test fun seriesEditorReferenceFrames() {
        show()
        capture("04-new-series-first-level")
        compose.runOnIdle { requireNotNull(shown).focusedRow = "keep" }
        capture("05a-focus-keep-until")
        open(AutomaticRecordingEditorLevel.KEEP, "keep-null")
        capture("05b-keep-until-level")
        show()
        open(AutomaticRecordingEditorLevel.CHANNEL_TIME, "channel")
        capture("06a-channel-and-time-level")
        open(AutomaticRecordingEditorLevel.DAYS, "any-day")
        capture("06b-days-level")
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
            requireNotNull(shown).focusedRow = "enabled"
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
            requireNotNull(shown).confirmation = AutomaticRecordingEditorConfirmation.DELETE
        }
        capture("11d-delete-confirm", "slice-3-job-b")
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
        requireNotNull(shown).open(level, row)
    }

    private fun capture(name: String, directory: String = "slice-3-job-a") {
        val focus = when (shown?.confirmation) {
            AutomaticRecordingEditorConfirmation.LEAVE -> "keep-editing"
            AutomaticRecordingEditorConfirmation.DELETE -> "keep"
            null -> requireNotNull(shown).focusedRow
        }
        compose.onNodeWithTag("automatic-recording-$focus").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.settleFrames(1, PlayerMotion.PanelMs.toLong())
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("../captures/autorec-impl/$directory", "$name.png")
            CaptureOutput.writePng(file, bitmap)
            CaptureOutput.writeText(File(file.parentFile, "$name.txt")) {
                "Canvas ${view.width}x${view.height}; en-US; font scale 1.0; Europe/Berlin; 24-hour; focus $focus; " +
                    "production AutomaticRecordingEditor; fictional fixture art; no device or server."
            }
            bitmap.recycle()
        }
    }
}
