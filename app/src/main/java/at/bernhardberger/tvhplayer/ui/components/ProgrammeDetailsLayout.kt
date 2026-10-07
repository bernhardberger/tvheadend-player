package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvGhostFillAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTextSecondaryAlpha
import kotlinx.coroutines.launch

/** The player's four-column reading and action slots, shared by all details hosts. */
internal val DetailsColumnWidth = 268.dp
internal val DetailsTileHeight = 151.dp
internal val DetailsInset = 72.dp

/** Fullscreen modal hosts reserve the player's 76dp inset + 48dp header + 32dp gap; the layout owns the bottom. */
internal fun Modifier.programmeDetailsFrame(): Modifier =
    padding(start = 130.dp, end = 130.dp, top = 156.dp)

@Composable
internal fun ProgrammeDetailsLayout(
    modifier: Modifier = Modifier,
    reading: @Composable () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier.fillMaxSize().padding(bottom = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        reading()
        Column(Modifier.width(DetailsColumnWidth).fillMaxHeight().testTag("details-actions"),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            content = actions)
    }
}

@Composable
internal fun ProgrammeDetailsInformation(
    title: String?,
    subtitle: String?,
    facts: String,
    body: String?,
    modifier: Modifier = Modifier,
    tile: @Composable (Modifier) -> Unit,
    status: @Composable () -> Unit = {},
) {
    Column(modifier.width(DetailsColumnWidth).fillMaxHeight().testTag("details-information"),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tile(Modifier.size(DetailsColumnWidth, DetailsTileHeight))
        Spacer(Modifier.height(10.dp))
        title?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("details-title").semantics { heading() })
        }
        subtitle?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(facts, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).testTag("details-facts"))
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            status()
        }
        body?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = TvOverlayTextSecondaryAlpha),
                modifier = Modifier.weight(1f, fill = false).padding(top = 6.dp))
        }
    }
}

/**
 * The TV kit's two-column dialog action: a full-column 48dp button with a 20dp icon and a label
 * that wraps instead of cutting off. Hosts retain their action identities and focus restoration.
 */
@Composable
internal fun ProgrammeDetailsButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Button(onClick = onClick,
        modifier = modifier.width(DetailsColumnWidth).heightIn(min = 48.dp).onPreviewKeyEvent {
            it.key == Key.DirectionLeft || it.key == Key.DirectionRight
        },
        shape = ButtonDefaults.shape(shape = shape, focusedShape = shape, pressedShape = shape),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TvGhostFillAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        // The button's surface pins content to its top, so the label holds a full 24dp line to fill the 48dp height.
        Text(title, Modifier.heightIn(min = 24.dp).wrapContentHeight(), style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ProgrammeDetailsFullDescription(
    modifier: Modifier = Modifier,
    reading: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val readingFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val scrollStep = with(LocalDensity.current) { 60.dp.roundToPx() }
    val paneTitle = stringResource(R.string.details_read_more)
    LaunchedEffect(Unit) { withFrameNanos { }; readingFocus.requestFocus() }
    Row(modifier.fillMaxSize().padding(bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        reading()
        Surface(modifier = Modifier.weight(1f).fillMaxHeight().testTag("details-full-description"),
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (focused) 0.06f else 0.03f),
                contentColor = MaterialTheme.colorScheme.onSurface),
            border = Border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (focused) 0.3f else 0.1f)))) {
            Box(Modifier.fillMaxSize().testTag("player-info-reading")
                .semantics { this.paneTitle = paneTitle }
                .focusRequester(readingFocus).onFocusChanged { focused = it.isFocused }
                .focusProperties {
                    up = FocusRequester.Cancel; down = FocusRequester.Cancel
                    left = FocusRequester.Cancel; right = FocusRequester.Cancel
                }
                .onPreviewKeyEvent { event ->
                    val delta = when (event.key) {
                        Key.DirectionDown -> scrollStep
                        Key.DirectionUp -> -scrollStep
                        Key.DirectionLeft, Key.DirectionRight -> return@onPreviewKeyEvent true
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.type == KeyEventType.KeyDown) scope.launch {
                        scroll.scrollTo((scroll.value + delta).coerceIn(0, scroll.maxValue))
                    }
                    true
                }.focusable().padding(20.dp)) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fade = 32.dp.toPx().coerceAtMost(size.height)
                        if (scroll.canScrollBackward) drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = fade), blendMode = BlendMode.DstIn)
                        if (scroll.canScrollForward) drawRect(
                            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                            blendMode = BlendMode.DstIn)
                    }.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
            }
        }
    }
}
