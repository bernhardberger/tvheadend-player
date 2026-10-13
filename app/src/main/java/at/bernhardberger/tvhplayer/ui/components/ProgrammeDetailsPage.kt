package at.bernhardberger.tvhplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.common.*
import at.bernhardberger.tvhplayer.ui.player.detailsSubtitle
import at.bernhardberger.tvhplayer.ui.notifications.label
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** Browse details only; the shell remains the sole owner of the video scrim. */
@Composable
internal fun ProgrammeDetailsPage(
    event: EpgEvent?,
    recording: DvrEntry?,
    channel: Channel?,
    nowSec: Long,
    actions: List<BrowseContextAction>,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    onAction: (BrowseContextAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    related: List<EpgEvent> = emptyList(),
    onRelated: (EpgEvent) -> Unit = {},
    returnToCard: EventId? = null,
    onReturnToCardConsumed: () -> Unit = {},
    initialAction: BrowseContextAction? = null,
    backEnabled: Boolean = true,
) {
    val title = event?.title ?: recording?.title.orEmpty()
    val image = event?.image ?: recording?.image
    val subtitle = event?.let(::detailsSubtitle) ?: recording?.subtitle
    val synopsis = (event?.summary ?: recording?.summary)?.takeIf(String::isNotBlank)
        ?: (event?.description ?: recording?.description)?.takeIf(String::isNotBlank)
    val description = (event?.description ?: recording?.description)?.takeIf(String::isNotBlank) ?: synopsis
    val status = detailsStatus(event, recording, nowSec)
    val facts = event?.let { programmeDetailsFacts(it, nowSec) } ?: recording?.let(::recordingDetailsFacts).orEmpty()
    val requesters = remember { BrowseContextAction.entries.associateWith { FocusRequester() } }
    val panelFocus = remember { FocusRequester() }
    val readingFocus = remember { FocusRequester() }
    val readingScroll = rememberScrollState()
    var readingFocused by remember { mutableStateOf(false) }
    val pageScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val cardFocus = remember(related.map { it.id }) { related.associate { it.id to FocusRequester() } }
    val cardBringIntoView = remember(related.map { it.id }) { related.associate { it.id to BringIntoViewRequester() } }
    var more by remember { mutableStateOf(returnToCard != null) }
    var lastAction by remember { mutableStateOf(initialAction ?: actions.firstOrNull()) }
    var lastCard by remember { mutableStateOf(returnToCard ?: related.firstOrNull()?.id) }
    var openingRelease by remember { mutableStateOf(true) }
    var transitionKey by remember { mutableStateOf<Key?>(null) }
    val primary = actions.firstOrNull()?.let(requesters::get) ?: panelFocus
    fun close() { if (more) more = false else onClose() }
    BackHandler(enabled = backEnabled, onBack = ::close)
    LaunchedEffect(more, actions, related.map { it.id }) {
        withFrameNanos { }
        if (more && related.isNotEmpty()) {
            (cardFocus[lastCard] ?: cardFocus.getValue(related.first().id)).requestFocus()
            if (returnToCard != null) onReturnToCardConsumed()
        } else if (more && description != null) {
            readingFocus.requestFocus()
        } else {
            more = false
            pageScroll.scrollTo(0)
            (lastAction?.takeIf { it in actions }?.let(requesters::get) ?: primary).requestFocus()
        }
    }
    LaunchedEffect(more, lastCard) {
        if (more) {
            withFrameNanos { }
            lastCard?.let { cardBringIntoView[it]?.bringIntoView() }
        }
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(modifier.fillMaxSize().semantics { paneTitle = title }.testTag("programme-details-page")
        .onPreviewKeyEvent { key ->
            val activation = key.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
            when {
                key.key == transitionKey -> {
                    if (key.type == KeyEventType.KeyUp) transitionKey = null
                    true
                }
                activation && openingRelease -> {
                    if (key.type == KeyEventType.KeyDown && key.nativeKeyEvent.repeatCount == 0) {
                        openingRelease = false
                        false
                    } else {
                        if (key.type == KeyEventType.KeyUp) openingRelease = false
                        true
                    }
                }
                key.key == Key.DirectionDown && !more && (related.isNotEmpty() || description != null) -> {
                    if (key.type == KeyEventType.KeyDown) { transitionKey = key.key; more = true }
                    true
                }
                key.key == Key.DirectionUp && more && (related.isNotEmpty() || readingScroll.value == 0) -> {
                    if (key.type == KeyEventType.KeyDown) { transitionKey = key.key; more = false }
                    true
                }
                else -> false
            }
        }.then(if (actions.isEmpty()) Modifier.focusRequester(panelFocus).focusProperties {
            up = FocusRequester.Cancel; down = FocusRequester.Cancel
            left = FocusRequester.Cancel; right = FocusRequester.Cancel
        }.focusable() else Modifier.focusGroup())) {
        if (!more && image != null && currentSession != null) {
            AsyncImage(AppArtworkSource(currentSession, image), null, imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(432.dp).testTag("details-hero")
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent),
                            startY = 300.dp.toPx(), endY = size.height), blendMode = BlendMode.DstIn)
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black),
                            startX = 150.dp.toPx(), endX = 500.dp.toPx()), blendMode = BlendMode.DstIn)
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.Black),
                            start = Offset(250.dp.toPx(), 250.dp.toPx()), end = Offset(500.dp.toPx(), 0f)),
                            blendMode = BlendMode.DstIn)
                    })
        }
        Column(Modifier.fillMaxSize().verticalScroll(pageScroll)
            .padding(start = 58.dp, end = 58.dp, top = if (more) 40.dp else 116.dp, bottom = 32.dp)) {
            if (more) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PiconBox(imageLoader, channel?.icon, Modifier.size(36.dp, 20.dp), currentSession)
                    Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(channel?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    status?.let { DetailsStatusChip(it) }
                }
                Spacer(Modifier.height(28.dp))
                Text(stringResource(R.string.details_about), style = MaterialTheme.typography.titleMedium)
                description?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(max = 560.dp)
                        .then(if (related.isEmpty()) Modifier.heightIn(max = 320.dp)
                            .focusRequester(readingFocus)
                            .onFocusChanged { readingFocused = it.isFocused }
                            .border(2.dp, if (readingFocused) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .padding(8.dp)
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.DirectionDown || event.key == Key.DirectionUp) {
                                    if (event.type == KeyEventType.KeyDown) scope.launch {
                                        readingScroll.scrollTo((readingScroll.value +
                                            if (event.key == Key.DirectionDown) 120 else -120).coerceAtLeast(0))
                                    }
                                    true
                                } else false
                            }
                            .verticalScroll(readingScroll).focusable() else Modifier)
                        .testTag("details-description"))
                }
                val secondary = listOfNotNull(
                    event?.genre?.takeIf(String::isNotBlank)?.let { stringResource(R.string.details_genre) to it },
                    event?.episode?.let { episode ->
                        episode.onscreen?.takeIf(String::isNotBlank) ?: listOfNotNull(
                            episode.seasonNumber?.let { "S$it" },
                            episode.episodeNumber?.let { "E$it" },
                            episode.partNumber?.let { part -> stringResource(R.string.details_part) + " $part" +
                                episode.partCount?.let { "/$it" }.orEmpty() },
                        ).joinToString(" ").takeIf(String::isNotBlank)
                    }?.let { stringResource(R.string.details_episode) to it },
                )
                if (secondary.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    DetailsFactsRow(secondary)
                }
            } else {
                Column(Modifier.widthIn(max = 484.dp).testTag("details-information")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PiconBox(imageLoader, channel?.icon, Modifier.size(36.dp, 20.dp), currentSession)
                        Text(listOfNotNull(channel?.number?.toString(), channel?.name).joinToString(" · ") +
                            event?.genre?.takeIf(String::isNotBlank)?.let { " | $it" }.orEmpty(),
                            Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        status?.let { DetailsStatusChip(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val recordingFailure = recording?.subscriptionError?.label()?.takeIf(String::isNotBlank)
                    (recordingFailure ?: synopsis)?.let {
                        Spacer(Modifier.height(20.dp))
                        Text(it, Modifier.testTag("details-synopsis"), style = MaterialTheme.typography.bodyLarge,
                            color = if (recordingFailure != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (facts.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Column(Modifier.width(IntrinsicSize.Max)) {
                            DetailsFactsRow(facts.map { stringResource(it.kind.label()) to detailsFactValue(it, nowSec) })
                            if (event == null && facts.any { it.kind == DetailsFactKind.LEFT }) {
                                val length = facts.first { it.kind == DetailsFactKind.LENGTH }.seconds
                                val left = facts.first { it.kind == DetailsFactKind.LEFT }.seconds
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    Box(Modifier.fillMaxWidth(1f - left.toFloat() / length).fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp).testTag("details-actions"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    actions.forEachIndexed { index, action ->
                        key(action) {
                            val buttonModifier = Modifier.height(40.dp).focusRequester(requesters.getValue(action))
                                .onFocusChanged { if (it.isFocused) lastAction = action }
                                .focusProperties {
                                    left = actions.getOrNull(index - 1)?.let(requesters::get) ?: FocusRequester.Cancel
                                    right = actions.getOrNull(index + 1)?.let(requesters::get) ?: FocusRequester.Cancel
                                    up = FocusRequester.Cancel; down = FocusRequester.Cancel
                                }.testTag("details-${action.name.lowercase()}")
                            val content: @Composable RowScope.() -> Unit = {
                                Icon(painterResource(action.icon()), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(action.label(action == BrowseContextAction.WATCH &&
                                    (event == null && recording?.state == DvrEntryState.SCHEDULED ||
                                        event != null && (nowSec < event.start.epochSeconds || nowSec >= event.stop.epochSeconds)))),
                                    maxLines = 1, softWrap = false)
                            }
                            if (index == 0) Button(onClick = { onAction(action) }, modifier = buttonModifier, content = content)
                            else OutlinedButton(onClick = { onAction(action) }, modifier = buttonModifier, content = content)
                        }
                    }
                }
            }
            if (related.isNotEmpty()) {
                Spacer(Modifier.height(if (more) 28.dp else 48.dp))
                Text(stringResource(R.string.details_next_on, channel?.name.orEmpty()), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    related.forEachIndexed { index, item ->
                        StandardCardContainer(
                            imageCard = { interaction ->
                                Card(onClick = { onRelated(item) }, interactionSource = interaction,
                                    modifier = Modifier.size(180.dp, 101.25.dp).focusRequester(cardFocus.getValue(item.id))
                                        .onFocusChanged { if (it.isFocused) { lastCard = item.id; more = true } }
                                        .focusProperties {
                                            canFocus = more; down = FocusRequester.Cancel
                                            left = related.getOrNull(index - 1)?.id?.let(cardFocus::get) ?: FocusRequester.Cancel
                                            right = related.getOrNull(index + 1)?.id?.let(cardFocus::get) ?: FocusRequester.Cancel
                                        }
                                        .semantics { contentDescription = "${item.title.orEmpty()}, ${formatHm(item.start.epochSeconds)}" }
                                        .testTag("details-card-${item.id.value}")) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (item.image != null && currentSession != null) AsyncImage(
                                            AppArtworkSource(currentSession, item.image!!), null, imageLoader,
                                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else PiconBox(imageLoader, channel?.icon, Modifier.size(80.dp, 40.dp), currentSession)
                                    }
                                }
                            },
                            title = { Text(item.title.orEmpty(), Modifier.padding(top = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall) },
                            subtitle = { Text("${formatHm(item.start.epochSeconds)} · ${formatPlaybackDuration((item.stop - item.start).inWholeSeconds, LocalContext.current.resources)}",
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.width(180.dp)
                                .bringIntoViewRequester(cardBringIntoView.getValue(item.id))
                                .padding(bottom = if (more) 45.dp else 0.dp),
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun DetailsFactsRow(facts: List<Pair<String, String>>) {
    Row(Modifier.clearAndSetSemantics { contentDescription = facts.joinToString(", ") { "${it.first} ${it.second}" } },
        horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        facts.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DetailsStatusChip(status: DetailsStatus) {
    val color = when (status) {
        DetailsStatus.ON_NOW -> MaterialTheme.colorScheme.primary
        DetailsStatus.RECORDING, DetailsStatus.SCHEDULED -> TvRecordingColor
        DetailsStatus.FAILED -> MaterialTheme.colorScheme.error
    }
    Row(Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(stringResource(when (status) {
            DetailsStatus.ON_NOW -> R.string.details_status_on_now
            DetailsStatus.RECORDING -> R.string.recording_state_recording
            DetailsStatus.SCHEDULED -> R.string.details_status_scheduled
            DetailsStatus.FAILED -> R.string.details_status_failed
        }), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun DetailsFactKind.label(): Int = when (this) {
    DetailsFactKind.STARTS -> R.string.details_starts
    DetailsFactKind.STARTED -> R.string.details_started
    DetailsFactKind.RECORDED -> R.string.details_recorded
    DetailsFactKind.LENGTH -> R.string.details_length
    DetailsFactKind.LEFT -> R.string.details_left
}

@Composable
internal fun detailsFactValue(fact: DetailsFact, nowSec: Long): String {
    val resources = LocalContext.current.resources
    return when (fact.kind) {
        DetailsFactKind.LENGTH, DetailsFactKind.LEFT -> formatPlaybackDuration(fact.seconds, resources)
        DetailsFactKind.RECORDED -> formatRecordingSchedule(fact.seconds, dateOnly = true)
        DetailsFactKind.STARTS, DetailsFactKind.STARTED -> {
            val zone = java.time.ZoneId.systemDefault()
            val day = java.time.Instant.ofEpochSecond(fact.seconds).atZone(zone).toLocalDate()
            val today = java.time.Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
            when (day) {
                today -> formatHm(fact.seconds)
                today.plusDays(1) -> stringResource(R.string.details_tomorrow, formatHm(fact.seconds))
                else -> formatRecordingSchedule(fact.seconds)
            }
        }
    }
}
