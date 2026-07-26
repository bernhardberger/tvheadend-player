package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Carousel
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.CarouselState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.rememberCarouselState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.HomeHeroSlide
import at.bernhardberger.tvhplayer.core.HomeSlideKind
import at.bernhardberger.tvhplayer.core.channelInitials
import at.bernhardberger.tvhplayer.ui.HomeHeroHeight
import at.bernhardberger.tvhplayer.ui.common.formatClock
import coil3.ImageLoader

/** Auto-scroll interval while the hero is unfocused (plan: 8 s). */
const val HOME_HERO_AUTO_SCROLL_MS = 8_000L

/**
 * Featured Home hero using [Carousel]. Single primary action per slide.
 *
 * Left/Right on the primary action change slides (they do not escape into the
 * side rail). The library keeps move APIs module-internal; we drive the public
 * [CarouselState.activeItemIndex] through a small bridge so D-pad matches the
 * ten-foot expectation.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeHeroCarousel(
    slides: List<HomeHeroSlide>,
    imageLoader: ImageLoader,
    onPrimaryAction: (HomeHeroSlide) -> Unit,
    modifier: Modifier = Modifier,
    primaryActionFocusRequester: FocusRequester? = null,
) {
    if (slides.isEmpty()) return

    val carouselState = rememberCarouselState()
    val autoScrollMs = if (slides.size <= 1) Long.MAX_VALUE else HOME_HERO_AUTO_SCROLL_MS
    // Shared requester so L/R slide changes keep focus on the primary action.
    val actionFocus = primaryActionFocusRequester ?: remember { FocusRequester() }
    var actionFocused by remember { mutableStateOf(false) }
    // After a programmatic slide change the old Button leaves composition — re-claim.
    LaunchedEffect(carouselState.activeItemIndex) {
        if (actionFocused) {
            runCatching { actionFocus.requestFocus() }
        }
    }

    Carousel(
        itemCount = slides.size,
        modifier = modifier
            .fillMaxWidth()
            .height(HomeHeroHeight)
            .testTag("home-hero-carousel"),
        carouselState = carouselState,
        autoScrollDurationMillis = autoScrollMs,
        carouselIndicator = {
            if (slides.size > 1) {
                val pageDescription = stringResource(
                    R.string.home_carousel_page,
                    carouselState.activeItemIndex + 1,
                    slides.size,
                )
                CarouselDefaults.IndicatorRow(
                    itemCount = slides.size,
                    activeItemIndex = carouselState.activeItemIndex,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 16.dp)
                        .testTag("home-hero-indicator")
                        .semantics { contentDescription = pageDescription },
                )
            }
        },
    ) { index ->
        val slide = slides[index]
        HeroSlideContent(
            slide = slide,
            imageLoader = imageLoader,
            pageLabel = stringResource(
                R.string.home_carousel_page,
                index + 1,
                slides.size,
            ),
            // Only the active slide hosts the focus target (and the L/R interceptor).
            primaryActionFocusRequester = if (index == carouselState.activeItemIndex) {
                actionFocus
            } else {
                null
            },
            interceptHorizontalDpad = index == carouselState.activeItemIndex && slides.size > 1,
            onActionFocusChanged = { focused ->
                if (index == carouselState.activeItemIndex) {
                    actionFocused = focused
                }
            },
            onHorizontalDpad = { goLeft ->
                val current = carouselState.activeItemIndex
                val target = if (goLeft) current - 1 else current + 1
                if (target in slides.indices) {
                    actionFocused = true
                    carouselState.seekToItem(target)
                    true
                } else {
                    // Clamp at ends — do not leak focus into the side rail.
                    true
                }
            },
            onPrimaryAction = { onPrimaryAction(slide) },
        )
    }
}

@Composable
private fun HeroSlideContent(
    slide: HomeHeroSlide,
    imageLoader: ImageLoader,
    pageLabel: String,
    primaryActionFocusRequester: FocusRequester?,
    interceptHorizontalDpad: Boolean,
    onActionFocusChanged: (Boolean) -> Unit,
    onHorizontalDpad: (goLeft: Boolean) -> Boolean,
    onPrimaryAction: () -> Unit,
) {
    val accent = remember(slide.accentSeed) { channelAccentColor(slide.accentSeed) }
    val initials = remember(slide.channelName) { channelInitials(slide.channelName) }
    val overline = when (slide.kind) {
        HomeSlideKind.LIVE -> stringResource(R.string.home_slide_live)
        HomeSlideKind.CONTINUE -> stringResource(R.string.home_slide_continue)
        HomeSlideKind.ON_NOW -> stringResource(R.string.home_slide_on_now)
        HomeSlideKind.RECORDING -> stringResource(R.string.home_slide_recording)
    }
    // Resume only for the active live session. Everything else is a fresh Watch.
    val actionLabel = when {
        slide.kind == HomeSlideKind.LIVE -> stringResource(R.string.home_resume)
        slide.kind == HomeSlideKind.RECORDING && !slide.playable -> {
            stringResource(R.string.nav_recordings)
        }
        else -> stringResource(R.string.home_watch)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    ),
                ),
            )
            .testTag("home-hero-slide-${slide.kind}-${slide.channelId}")
            .semantics { contentDescription = pageLabel },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight(0.78f)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (slide.piconPath.isNullOrBlank()) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    PiconBox(
                        imageLoader = imageLoader,
                        piconPath = slide.piconPath,
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .fillMaxHeight(0.72f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = buildString {
                            append(overline)
                            append(" · ")
                            slide.channelNumber?.let {
                                append(it)
                                append(" ")
                            }
                            append(slide.channelName)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    heroMetaLine(slide)?.let { meta ->
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    slide.progress?.let { progress ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    ),
                            )
                        }
                    }
                    heroNextLine(slide)?.let { next ->
                        Text(
                            text = next,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier
                        .then(
                            primaryActionFocusRequester?.let { Modifier.focusRequester(it) }
                                ?: Modifier,
                        )
                        .onFocusChanged { onActionFocusChanged(it.isFocused) }
                        .then(
                            if (interceptHorizontalDpad) {
                                // Block default focus search to the side rail; we own L/R.
                                Modifier
                                    .focusProperties {
                                        left = FocusRequester.Cancel
                                        right = FocusRequester.Cancel
                                    }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) {
                                            return@onPreviewKeyEvent false
                                        }
                                        when (event.key) {
                                            Key.DirectionLeft -> onHorizontalDpad(true)
                                            Key.DirectionRight -> onHorizontalDpad(false)
                                            else -> false
                                        }
                                    }
                            } else {
                                // Single-slide hero: still block Left so focus does not dump
                                // into the rail from the only featured action.
                                Modifier.focusProperties {
                                    left = FocusRequester.Cancel
                                }
                            },
                        )
                        .testTag("home-hero-primary"),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * Bridge to CarouselState's module-internal index setter.
 * The public API only exposes [CarouselState.activeItemIndex] as a getter; the
 * Carousel key handler is the stock path, but it does not run while a child
 * Button holds focus — so Left leaks to the side rail. Drive the JVM-visible
 * internal setter instead of hand-rolling the carousel chrome.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
private fun CarouselState.seekToItem(index: Int) {
    val method = CarouselState::class.java.getDeclaredMethod(
        "setActiveItemIndex\$tv_material",
        Int::class.javaPrimitiveType,
    )
    method.isAccessible = true
    method.invoke(this, index)
}

@Composable
private fun heroMetaLine(slide: HomeHeroSlide): String? {
    val start = slide.startSec
    val stop = slide.stopSec
    return when {
        start != null && stop != null -> {
            val range = stringResource(
                R.string.home_time_range,
                formatClock(start),
                formatClock(stop),
            )
            val remainingSec = stop - (System.currentTimeMillis() / 1000L)
            if (remainingSec > 0) {
                val minutes = ((remainingSec + 59) / 60).toInt().coerceAtLeast(1)
                "$range · ${stringResource(R.string.home_minutes_left, minutes)}"
            } else {
                range
            }
        }
        stop != null -> stringResource(R.string.home_ends_at, formatClock(stop))
        start != null -> stringResource(R.string.home_starts_at, formatClock(start))
        else -> null
    }
}

@Composable
private fun heroNextLine(slide: HomeHeroSlide): String? {
    val title = slide.nextTitle?.takeIf { it.isNotBlank() } ?: return null
    val start = slide.nextStartSec
    return if (start != null) {
        stringResource(R.string.home_next_programme, formatClock(start), title)
    } else {
        stringResource(R.string.player_next_event, title)
    }
}
