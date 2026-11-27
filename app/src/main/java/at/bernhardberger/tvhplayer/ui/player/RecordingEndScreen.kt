package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import androidx.tv.material3.WideClassicCard
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.images.ArtworkLogoCorner
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.common.*
import at.bernhardberger.tvhplayer.ui.components.*

internal const val EndCountdownMs = 10_000L
private val OfferWidth = 384.dp
private val OfferHeight = 81.dp
private val OfferImageWidth = 144.dp
private val OfferTextPadding = 12.dp
private val OfferArtworkTakenCorners = setOf(
    ArtworkLogoCorner.BOTTOM_START, ArtworkLogoCorner.BOTTOM_END, ArtworkLogoCorner.TOP_END,
)

internal val RecordingEndOffer.cardKey: Any
    get() = when (this) {
        is RecordingEndOffer.Recording -> entry.id
        is RecordingEndOffer.WatchLive -> channelId
    }

@Composable
internal fun RecordingEndScreen(
    ended: DvrEntry,
    offers: List<RecordingEndOffer>,
    observation: SessionObservation,
    origin: String,
    autoContinue: Boolean,
    onPlay: (RecordingEndOffer) -> Unit,
    onClose: () -> Unit,
    menu: @Composable (RecordingEndOffer, onClose: () -> Unit) -> Unit,
    nowSec: () -> Long = rememberCurrentEpochSeconds(),
    shown: Boolean = true,
    starting: Boolean = false,
    suppressUntilUp: ((Int) -> Unit)? = null,
) {
    var stopped by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val suppressedKeys = remember { mutableSetOf<Key>() }
    var menuKey by remember { mutableStateOf<Any?>(null) }
    var restoreMenuFocus by remember { mutableStateOf<Any?>(null) }
    var focusedKey by remember { mutableStateOf<Any?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val keys = offers.map { it.cardKey }
    val requesters = remember { mutableMapOf<Any, FocusRequester>() }
    keys.forEach { requesters.getOrPut(it) { FocusRequester() } }
    val selectedMenu = offers.firstOrNull { it.cardKey == menuKey }
    val first = offers.firstOrNull()
    val lifecycleOwner = LocalLifecycleOwner.current.lifecycle
    val lifecycle by lifecycleOwner.currentStateAsState()
    val running = !stopped && !starting && autoContinue && first is RecordingEndOffer.Recording
    val latestFirst by rememberUpdatedState(first)
    val latestPlay by rememberUpdatedState(onPlay)
    LaunchedEffect(running, shown, lifecycle, first?.cardKey) {
        if (!running || !shown || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        val started = withFrameMillis { it } - elapsedMs
        while (elapsedMs < EndCountdownMs) {
            val frame = withFrameMillis { it }
            if (!lifecycleOwner.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
            elapsedMs = frame - started
        }
        if (!stopped && lifecycleOwner.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            (latestFirst as? RecordingEndOffer.Recording)?.let(latestPlay)
        }
    }
    // If only a live offer remains there can be no automatic target, now or on focus return.
    LaunchedEffect(first is RecordingEndOffer.WatchLive) {
        if (first is RecordingEndOffer.WatchLive) stopped = true
    }
    LaunchedEffect(keys, selectedMenu?.cardKey, shown) {
        if (selectedMenu != null || !shown) return@LaunchedEffect
        val target = restoreMenuFocus?.takeIf(keys::contains)
            ?: focusedKey?.takeIf(keys::contains)
            ?: keys.getOrNull(focusedIndex.coerceAtMost(keys.lastIndex))
        if (target != null) {
            withFrameNanos { }
            requesters.getValue(target).requestFocus()
            restoreMenuFocus = null
        }
    }
    LaunchedEffect(first == null) { if (first == null) onClose() }
    if (first == null || !shown) return

    val title = stringResource(R.string.recording_end_title)
    val mainDescription = recordingEndDescription(ended, first, observation)
    val titles = offers.map { offer ->
        when (offer) {
            is RecordingEndOffer.Recording -> offer.entry.title.orEmpty()
            is RecordingEndOffer.WatchLive -> offer.event?.title
                ?: observation.channel(offer.channelId)?.name.orEmpty()
        }
    }
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 18.sp)
    val measure = rememberTextMeasurer()
    val titleWidth = with(LocalDensity.current) {
        (OfferWidth - OfferImageWidth - OfferTextPadding * 2).roundToPx()
    }
    val shownTitles = titles.map { text ->
        val layout = measure.measure(text, titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = titleWidth))
        text.take(layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.key == Key.Back || event.key == Key.Escape) return@onPreviewKeyEvent false
        fun suppressCycle() {
            if (suppressUntilUp != null) suppressUntilUp(event.nativeKeyEvent.keyCode)
            else suppressedKeys.add(event.key)
        }
        if (event.key in suppressedKeys) {
            if (event.type == KeyEventType.KeyUp) suppressedKeys.remove(event.key)
            return@onPreviewKeyEvent true
        }
        if (selectedMenu != null) return@onPreviewKeyEvent false
        if (running || starting) {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                stopped = true
                suppressCycle()
            }
            return@onPreviewKeyEvent true
        }
        when (event.key) {
            Key.DirectionUp, Key.DirectionDown -> {
                if (event.type == KeyEventType.KeyDown) {
                    val step = if (event.key == Key.DirectionUp) -1 else 1
                    val currentIndex = keys.indexOf(focusedKey)
                    if (currentIndex >= 0)
                        keys.getOrNull(currentIndex + step)?.let { requesters.getValue(it).requestFocus() }
                }
                true
            }
            Key.Menu -> {
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    suppressCycle()
                    menuKey = focusedKey
                }
                true
            }
            else -> false
        }
    }) {
        TwoColumnDialog(title, "", onClose,
            modifier = Modifier.testTag("recording-end-screen"),
            backEnabled = selectedMenu == null,
            dismissLevel = true,
            contentWidth = 844.dp,
            leadingWidth = 360.dp,
            actionsWidth = OfferWidth,
            backgroundColor = TvSurfaceColors.containerLowest.copy(alpha = .92f),
            leading = {
                Text(title, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
                Text(mainDescription, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (running) Text(stringResource(R.string.recording_end_countdown),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("recording-end-countdown").semantics { liveRegion = LiveRegionMode.Polite })
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)
                    Text(stringResource(R.string.recording_end_hold), style = MaterialTheme.typography.bodyMedium, color = color)
                    Text(stringResource(R.string.recording_end_back, origin), style = MaterialTheme.typography.bodyMedium, color = color)
                }
            },
        ) {
            offers.forEachIndexed { index, offer ->
                val id = offer.cardKey
                key(id) {
                    val original = titles[index]
                    val collision = shownTitles.count { it == shownTitles[index] } > 1
                    val alternative = (offer as? RecordingEndOffer.Recording)?.entry?.subtitle
                    val cardTitle = alternative?.takeIf { collision && it.isNotBlank() } ?: original
                    val trigger = rememberBrowseContextMenuTrigger({ menuKey = id },
                        enabled = { !starting && selectedMenu == null }, menuKey = false)
                    RecordingEndCard(offer, cardTitle, observation, nowSec,
                        countdown = if (index == 0 && running) ({ elapsedMs / EndCountdownMs.toFloat() }) else null,
                        onClick = { if (selectedMenu == null && !starting) { stopped = true; onPlay(offer) } },
                        onLongClick = trigger.onLongClick,
                        titleStyle = titleStyle,
                        modifier = Modifier.testTag("recording-end-card-$index")
                            .focusRequester(requesters.getValue(id))
                            .onFocusChanged { if (it.isFocused) { focusedKey = id; focusedIndex = index } }
                            .focusProperties {
                                canFocus = selectedMenu == null
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }.then(trigger.modifier),
                    )
                }
            }
        }
        WallClock(nowSec, Modifier.align(Alignment.TopEnd).padding(end = 58.dp, top = 32.dp))
        selectedMenu?.let { offer ->
            menu(offer) {
                restoreMenuFocus = offer.cardKey
                menuKey = null
            }
        }
    }
}

@Composable
private fun recordingEndDescription(ended: DvrEntry, next: RecordingEndOffer, observation: SessionObservation): String {
    val measure = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyLarge
    val constraints = Constraints(maxWidth = with(LocalDensity.current) { 360.dp.roundToPx() })
    val original = ended.title.orEmpty()
    val layout = measure.measure(original, style, maxLines = 2, overflow = TextOverflow.Ellipsis, constraints = constraints)
    val title = if (layout.hasVisualOverflow) original.take(layout.getLineEnd(1, visibleEnd = true)) + "…" else original
    return when (next) {
        is RecordingEndOffer.WatchLive -> stringResource(R.string.recording_end_live_description, title,
            observation.channel(next.channelId)?.name ?: ended.channelName.orEmpty())
        is RecordingEndOffer.Recording -> when (next.kind) {
            RecordingEndKind.CHAIN_PART -> {
                val channel = next.entry.channelId?.let(observation::channel)?.name ?: next.entry.channelName.orEmpty()
                val time = next.displayContinuationTime
                if (time == null) stringResource(R.string.recording_end_chain_beginning, title, channel)
                else {
                    val clock = formatClock(time.epochSeconds)
                    val full = stringResource(R.string.recording_end_chain_description, title, channel, clock)
                    if (measure.measure(full, style, maxLines = 4, constraints = constraints).hasVisualOverflow)
                        stringResource(R.string.recording_end_chain_short_description, title, channel, clock) else full
                }
            }
            RecordingEndKind.NEXT_EPISODE -> stringResource(R.string.recording_end_next_description, title)
            RecordingEndKind.OTHER_EPISODE -> stringResource(R.string.recording_end_more_description, title)
        }
    }
}

@Composable
private fun RecordingEndCard(
    offer: RecordingEndOffer,
    title: String,
    observation: SessionObservation,
    nowSec: () -> Long,
    countdown: (() -> Float)?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    titleStyle: TextStyle,
    modifier: Modifier,
    corner: Boolean = false,
) {
    val recording = (offer as? RecordingEndOffer.Recording)?.entry
    val channel = when (offer) {
        is RecordingEndOffer.Recording -> offer.entry.channelId
        is RecordingEndOffer.WatchLive -> offer.channelId
    }?.let(observation::channel)
    val event = when (offer) {
        is RecordingEndOffer.Recording -> offer.entry.eventId?.let(observation::event)
        is RecordingEndOffer.WatchLive -> offer.event
    }
    val locale = LocalConfiguration.current.locales[0]
    val interaction = remember { MutableInteractionSource() }
    val color = MaterialTheme.colorScheme.onSurface
    val ring = rememberCardFocusRing(interaction)
    val action = recordingEndActionLabel(offer)
    WideClassicCard(
        onClick = onClick, onLongClick = onLongClick, interactionSource = interaction,
        modifier = Modifier.cardFocusRing(ring, color).size(OfferWidth, OfferHeight).then(modifier),
        scale = EmbeddedProgressCardScale, border = embeddedProgressCardBorder(),
        contentPadding = PaddingValues(end = OfferTextPadding),
        image = {
            Box(Modifier.size(OfferImageWidth, OfferHeight)) {
                ProgrammeArtwork(recording?.let(::recordingArtwork) ?: event?.let(::programmeArtwork), channel?.icon, progress = null,
                    modifier = Modifier.fillMaxSize(), takenCorners = OfferArtworkTakenCorners)
                if (offer is RecordingEndOffer.Recording) RecordingEndProgress(offer, nowSec)
                if (corner && recording?.let(::recordingIsRunning) == true) {
                    RecordingBadge(recording.state, Modifier.align(Alignment.TopEnd).padding(6.dp), compact = true)
                }
            }
        },
        title = {
            val running = recording?.let(::recordingIsRunning) == true
            val time = if ((offer as? RecordingEndOffer.Recording)?.kind == RecordingEndKind.CHAIN_PART
                || offer is RecordingEndOffer.WatchLive) {
                val start = recording?.start ?: event?.start
                val stop = recording?.stop ?: event?.stop
                if (start != null && stop != null) "${formatClock(start.epochSeconds)}–${formatClock(stop.epochSeconds)}" else ""
            } else listOfNotNull(
                recording?.episode?.let { programmeEpisodeLine(it, null, androidx.compose.ui.platform.LocalContext.current.resources) },
                recording?.start?.let { formatRecordingDate(it.epochSeconds, true, locale, nowSec()) },
            ).filter(String::isNotBlank).joinToString(" · ")
            val facts = if (running) stringResource(R.string.recording_end_recording_facts, time) else time
            Column(
                Modifier.fillMaxHeight().padding(start = OfferTextPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                if (corner) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        countdown?.let { SkipCountdown(color, it, ringSize = 14.dp, drawSkipIcon = false) }
                        Text(stringResource(R.string.recording_up_next), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(title, style = titleStyle, maxLines = if (corner) 1 else 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!corner) countdown?.let { SkipCountdown(color, it, ringSize = 14.dp, drawSkipIcon = false) }
                    Text(action, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (corner || facts.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!corner && running) RecordingStatusIndicator(recording.state, announceState = false, size = 8.dp)
                        Text(if (corner) stringResource(R.string.recording_play_next_hint) else facts,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                            color = LocalContentColor.current.copy(alpha = .8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
    )
}

@Composable
internal fun RecordingPlayNextPrompt(
    offer: RecordingEndOffer.Recording?,
    shown: Boolean,
    observation: SessionObservation,
    nowSec: () -> Long,
    bannerShown: Boolean,
    bannerBottomInset: () -> Int,
    onTimeout: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    crossingId: Int = 0,
    initialElapsedMs: () -> Long = { 0L },
    onElapsed: (Long) -> Unit = {},
) {
    RecordingCornerPrompt(
        prompt = offer, shown = shown, onTimeout = { onTimeout() },
        modifier = modifier.padding(end = TvGridHorizontalMargin, bottom = PlayerChromeTokens.playNextBottomInset),
        bannerShown = bannerShown, bannerSkipBottomInset = bannerBottomInset,
        identity = { it.entry.id to crossingId },
        countdownKey = crossingId,
        initialElapsedMs = initialElapsedMs,
        onElapsed = onElapsed,
        playNext = true,
    ) { candidate, visible, elapsed, cardModifier ->
            RecordingEndCard(
                candidate, candidate.entry.title.orEmpty(), observation, nowSec, elapsed,
                onClick = { if (visible) onPlay() }, onLongClick = null,
                titleStyle = MaterialTheme.typography.titleSmall,
                modifier = cardModifier.testTag("recording-play-next-prompt")
                    .recordingPromptActivation(candidate.entry.id to crossingId, visible),
                corner = true,
            )
    }
}

@Composable
internal fun recordingEndActionLabel(offer: RecordingEndOffer): String = when (offer) {
    is RecordingEndOffer.WatchLive -> stringResource(R.string.context_watch_live)
    is RecordingEndOffer.Recording -> {
        val at = offer.displayContinuationTime
        val resume = offer.start as? RecordingEndStart.Resume
        when {
            at != null -> stringResource(R.string.recording_end_continue_at, formatClock(at.epochSeconds))
            resume != null -> stringResource(R.string.recording_end_resume_at, formatPlaybackDuration(resume.position.inWholeMilliseconds))
            else -> stringResource(R.string.recording_end_from_beginning)
        }
    }
}

@Composable
private fun BoxScope.RecordingEndProgress(offer: RecordingEndOffer.Recording, nowSec: () -> Long) {
    val fileStart = recordingEndFileStart(offer.entry) ?: return
    val chain = offer.kind == RecordingEndKind.CHAIN_PART
    val recordedUntil = offer.entry.files?.lastOrNull()?.stop
        ?: if (recordingIsRunning(offer.entry)) kotlin.time.Instant.fromEpochSeconds(nowSec()) else recordingPaddedStop(offer.entry)
        ?: return
    val expectedEnd = if (chain) maxOf(recordedUntil, recordingPaddedStop(offer.entry) ?: recordedUntil) else recordedUntil
    val whole = (expectedEnd - fileStart).inWholeMilliseconds.toFloat()
    if (whole <= 0) return
    val orange = MaterialTheme.colorScheme.tertiary
    BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp)
        .clearAndSetSemantics {}) {
        val resume = (offer.start as? RecordingEndStart.Resume)?.position
        if (chain) {
            ProgressStrip((recordedUntil - fileStart).inWholeMilliseconds / whole, Modifier.fillMaxWidth())
            offer.displayContinuationTime?.let { at ->
                val fraction = ((at - fileStart).inWholeMilliseconds / whole).coerceIn(0f, 1f)
                Box(Modifier.offset(x = (maxWidth - 3.dp) * fraction).size(3.dp, 5.dp)
                    .background(orange, MaterialTheme.shapes.extraSmall))
            }
        } else if (resume != null) {
            ProgressStrip(resume.inWholeMilliseconds / whole, Modifier.fillMaxWidth(), color = orange)
        }
    }
}
