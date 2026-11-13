package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.ui.common.formatNarrowDuration
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.components.ConfirmationChoice
import at.bernhardberger.tvhplayer.ui.components.ConfirmationDialog
import at.bernhardberger.tvhplayer.ui.components.ConfirmationSubject
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.ServerAvatarLook
import at.bernhardberger.tvhplayer.ui.screens.recordings.PendingRecordingAction
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingConfirmationDialog
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingConfirmationAirTime
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import at.bernhardberger.tvhplayer.ui.screens.settings.PlaybackInterruption
import at.bernhardberger.tvhplayer.ui.screens.settings.SwitchServerDialog
import at.bernhardberger.tvhplayer.ui.screens.settings.RemoveServerDialog
import at.bernhardberger.tvhplayer.ui.screens.settings.SWITCH_KEEP
import at.bernhardberger.tvhplayer.ui.screens.settings.REMOVE_KEEP
import at.bernhardberger.tvhplayer.ui.screens.settings.serverConfirmationDetail
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Confirmation card evidence, one scene per centre-dialog round 8 H3 mockup: recording confirmations
 * through [RecordingConfirmationDialog], Guide and server callers, and folder card resources.
 * 960×540 dp, English, font scale 1.0, safe button focused, fictional fixture art.
 * Writes to build/outputs/centre-dialog-captures (ignored).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
class ConfirmationDialogCaptureTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule(effectContext = kotlinx.coroutines.test.StandardTestDispatcher())
    private var defaultZone: TimeZone? = null
    private val observation = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList())),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )
    private val session = FakeSessionObservation(observation).captureCurrentSession()

    @Before fun setup() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        images.install(FixtureArt.imageLoader(app, PICONS.entries.associate { ArtworkId(it.value) to FixtureArt.picon(it.key) } +
            ART.entries.associate { ArtworkId(it.value) to FixtureArt.art(it.key) }))
    }

    @After fun restoreZone() {
        defaultZone?.let(TimeZone::setDefault)
    }

    @Test fun delete() = recording("delete", PendingRecordingAction.DELETE,
        entry("Paper Kite Tales", "2026-10-03T08:00:00Z", "2026-10-03T08:30:00Z", art = "paper-kite-tales"),
        channel(4, "Kite Kids", "kite-kids"), actionFocused = true)

    @Test fun removeEntry() = recording("remove-entry", PendingRecordingAction.DELETE,
        entry("River Court", "2026-10-01T18:00:00Z", "2026-10-01T19:45:00Z", art = "river-court",
            state = DvrEntryState.MISSED, recorded = false),
        channel(2, "Harbor Sport", "harbor-sport"))

    /** Running with a known end: 1 h 5 min recorded, 45 minutes of the listed time left. */
    @Test fun stop() = recording("stop", PendingRecordingAction.STOP,
        entry("Harbor Kickoff", "2026-10-03T20:15:00Z", "2026-10-03T22:05:00Z", art = "harbor-kickoff",
            state = DvrEntryState.RECORDING, recordedUntil = "2026-10-03T21:20:00Z"),
        channel(2, "Harbor Sport", "harbor-sport"), now = "2026-10-03T21:20:00Z")

    /** Past the listed end, with the Guide's recording-profile row. */
    @Test fun stopOpenEnd() = component("stop-open-end") {
        ConfirmationDialog(
            question = stringResource(R.string.stop_recording_confirm_title),
            subject = programme("Harbor Kickoff", "harbor-kickoff", 2, "Harbor Sport", "harbor-sport",
                recordingConfirmationAirTime(Instant.parse("2026-10-03T20:15:00Z").epochSeconds,
                    Instant.parse("2026-10-03T22:05:00Z").epochSeconds,
                    Instant.parse("2026-10-03T22:20:00Z").epochSeconds), progress = 1f,
                target = stringResource(R.string.recording_confirmation_profile, "Sports HD (high bitrate)")),
            sentence = stringResource(R.string.stop_recording_confirm_body_open,
                formatNarrowDuration(7_500L, LocalContext.current.resources)),
            safe = keep(R.string.keep_recording), action = choice(R.string.stop_recording, R.drawable.ic_stop),
            onDismiss = {}, onConfirm = {},
        )
    }

    @Test fun cancelGuide() = recording("cancel-guide", PendingRecordingAction.CANCEL,
        entry("Lantern Road", "2026-10-03T21:30:00Z", "2026-10-03T22:00:00Z", art = "lantern-road",
            state = DvrEntryState.SCHEDULED, recorded = false),
        channel(5, "Ion Frame", "ion-frame"), now = "2026-10-03T19:00:00Z")

    /** Another user's scheduled recording, with the owner row. */
    @OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun cancelOwner() {
        val channel = channel(5, "Ion Frame", "ion-frame")
        val event = EpgEvent.create(EventId(1), channel.id, Instant.parse("2026-10-03T21:30:00Z"),
            Instant.parse("2026-10-03T22:00:00Z"), title = "Lantern Road", image = ArtworkId(ART.getValue("lantern-road")))
        val entries = SessionObservation.create(dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(
            DvrEntry.create(DvrEntryId(1), eventId = event.id, state = DvrEntryState.SCHEDULED,
                configId = DvrConfigId("one"), owner = "Fixture owner", creator = null),
            DvrEntry.create(DvrEntryId(2), eventId = event.id, state = DvrEntryState.SCHEDULED,
                configId = DvrConfigId("one"), owner = "Other fixture owner", creator = null),
        )))).dvrEntriesForEvent(event)
        component("cancel-owner", safeTag = "two-column-first") {
            ConfirmProgrammeActionDialog(ProgrammeAction.CANCEL_RECORDING, event, channel,
                Instant.parse("2026-10-03T19:00:00Z").epochSeconds, session, observation,
                onDismiss = {}, onConfirm = {}, entries = entries)
        }
    }

    /** No picture: the slot shows the logo; the glow follows the logo. */
    @Test fun deleteLogoA() = recording("delete-logo-a", PendingRecordingAction.DELETE,
        entry("Tide Watch", "2026-10-02T21:00:00Z", "2026-10-02T21:45:00Z"),
        channel(1, "Ridge Earth", "ridge-earth"))

    @Test fun deleteLogoB() = recording("delete-logo-b", PendingRecordingAction.DELETE,
        entry("Coastal Miles", "2026-10-01T20:00:00Z", "2026-10-01T20:30:00Z"),
        channel(6, "Amber Stage", "amber-stage"))

    /** Neither picture nor logo: the slot shows the channel number. */
    @Test fun deleteNoLogo() = recording("delete-nologo", PendingRecordingAction.DELETE,
        entry("Night Ferry", "2026-10-01T22:00:00Z", "2026-10-01T22:45:00Z"),
        channel(14, "Meridian Two", picon = null))

    /** A long name and channel widen the card. */
    @Test fun deleteLong() = recording("delete-long", PendingRecordingAction.DELETE,
        entry("Ridge Light: The remarkable journey along the mountain ridge in winter",
            "2026-10-03T20:00:00Z", "2026-10-03T21:00:00Z", art = "ridge-light"),
        channel(101, "Ridge Earth Documentaries HD", "ridge-earth"))

    @Test fun folder() = component("folder") {
        ConfirmationDialog(
            question = stringResource(R.string.delete_folder_all_confirm_question),
            subject = ConfirmationSubject.Folder("Documentaries", stringResource(R.string.server_detail_separator,
                pluralStringResource(R.plurals.recordings_folder_recording_count, 3, 3), "6.6 GB")),
            sentence = stringResource(R.string.delete_folder_confirm_body) + " " + stringResource(R.string.delete_folder_running_kept),
            safe = keep(R.string.keep), action = choice(R.string.context_delete_all, R.drawable.ic_delete_outlined),
            onDismiss = {}, onConfirm = {},
        )
    }

    @Test fun folderWatched() = component("folder-watched") {
        ConfirmationDialog(
            question = stringResource(R.string.delete_folder_watched_confirm_question),
            subject = ConfirmationSubject.Folder("Documentaries", pluralStringResource(R.plurals.recordings_folder_recording_count, 3, 3)),
            sentence = stringResource(R.string.delete_folder_confirm_body),
            safe = keep(R.string.keep), action = choice(R.string.context_delete_watched, R.drawable.ic_delete_outlined),
            onDismiss = {}, onConfirm = {},
        )
    }

    @Test fun serverSwitch() = component("server-switch", SWITCH_KEEP) {
        SwitchServerDialog("Bedroom", ServerAvatarLook(1, "B"), serverConfirmationDetail("bedroom.invalid", 9982, "Fixture account"),
            PlaybackInterruption.RECORDING, onSwitch = {}, onKeep = {})
    }

    @Test fun serverSwitchAnon() = component("server-switch-anon", SWITCH_KEEP) {
        SwitchServerDialog("Guest server", ServerAvatarLook(4, null), serverConfirmationDetail("guest.invalid", 9982, null),
            PlaybackInterruption.LIVE, onSwitch = {}, onKeep = {})
    }

    @Test fun serverRemove() = component("server-remove", REMOVE_KEEP) {
        RemoveServerDialog("Living room", ServerAvatarLook(2, "L"), serverConfirmationDetail("living-room.invalid", 9982, "Fixture account"),
            active = false, onRemove = {}, onKeep = {})
    }

    @Test fun serverRemoveActive() = component("server-remove-active", REMOVE_KEEP) {
        RemoveServerDialog("Living room", ServerAvatarLook(2, "L"), serverConfirmationDetail("living-room.invalid", 9982, "Fixture account"),
            active = true, onRemove = {}, onKeep = {})
    }

    private fun recording(scene: String, action: PendingRecordingAction, entry: DvrEntry, channel: Channel,
        now: String = "2026-10-05T12:00:00Z", actionFocused: Boolean = false) = component(scene, actionFocused = actionFocused) {
        RecordingConfirmationDialog(action, entry, channel, Instant.parse(now).epochSeconds, session, observation,
            backEnabled = true, onDismiss = {}, onConfirm = {})
    }

    private fun component(scene: String, safeTag: String = SAFE, actionFocused: Boolean = false, content: @Composable () -> Unit) {
        val still = FixtureArt.art("still-ridge-light")
        lateinit var view: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize()) {
                        Image(still.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        ProvideArtworkSession(session, observation) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.settleFrames(15)
        compose.onNodeWithTag(safeTag).assertIsFocused()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("build/outputs/centre-dialog-captures").apply { mkdirs() }
        File(directory, "confirm-$scene.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (actionFocused) {
            compose.pressKey(Key.DirectionRight)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            File(directory, "confirm-$scene-action-focused.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
    }

    @Composable private fun keep(label: Int) = ConfirmationChoice(stringResource(label), R.drawable.ic_check, SAFE)

    @Composable private fun choice(label: Int, icon: Int) = ConfirmationChoice(stringResource(label), icon, ACTION)

    private fun programme(name: String, art: String, number: Long, channelName: String, picon: String, airTime: String,
        progress: Float? = null, target: String? = null) = ConfirmationSubject.Programme(
        name = name, image = ChannelIcon.ImageCache(ArtworkId(ART.getValue(art))),
        channelIcon = ChannelIcon.ImageCache(ArtworkId(PICONS.getValue(picon))), channelNumber = number,
        channelName = channelName, airTime = airTime, progress = progress, target = target)

    private fun entry(title: String, start: String, stop: String, art: String? = null,
        state: DvrEntryState = DvrEntryState.COMPLETED, recorded: Boolean = true, recordedUntil: String = stop) =
        DvrEntry.create(DvrEntryId(1), title = title, state = state,
            start = Instant.parse(start), stop = Instant.parse(stop),
            files = if (recorded) listOf(DvrRecordingFile.create(start = Instant.parse(start), stop = Instant.parse(recordedUntil)))
            else emptyList(),
            image = art?.let { ArtworkId(ART.getValue(it)) })

    private fun channel(number: Long, name: String, picon: String?) = Channel.create(ChannelId(number), name = name, number = number,
        icon = picon?.let { ChannelIcon.ImageCache(ArtworkId(PICONS.getValue(it))) })

    private companion object {
        const val SAFE = "recording-confirmation-back"
        const val ACTION = "recording-confirmation-confirm"
        val PICONS = mapOf("kite-kids" to 1, "harbor-sport" to 2, "ion-frame" to 3, "ridge-earth" to 4, "amber-stage" to 5)
        val ART = mapOf("paper-kite-tales" to 101, "river-court" to 102, "harbor-kickoff" to 103,
            "lantern-road" to 104, "ridge-light" to 105)
    }
}
