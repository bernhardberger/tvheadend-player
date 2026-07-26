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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
 * Featured Home hero using [Carousel] for slide chrome only.
 *
 * The primary action is a **stable** sibling of the carousel, not a child of the
 * animated slide. Putting the Button inside the slide remounted it on every
 * index change, dropped focus, and the nearest focusable was the side rail —
 * which opens [ModalNavigationDrawer]. Left/Right are consumed on that stable
 * button so they change slides instead of searching left into the rail.
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
    val actionFocus = primaryActionFocusRequester ?: remember { FocusRequester() }
    val activeIndex = carouselState.activeItemIndex.coerceIn(0, slides.lastIndex)
    val activeSlide = slides[activeIndex]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeHeroHeight)
            .testTag("home-hero-carousel"),
    ) {
        Carousel(
            itemCount = slides.size,
            // Carousel puts a focusable box in front of its content, so arriving from a
            // row lands on that box: no visible focus, and the action unreachable. Push
            // focus straight through to the slide's button. Left/Right still bubble up
            // from the button to Carousel's own key handler, so slides keep changing.
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        runCatching { actionFocus.requestFocus() }
                    }
                },
            carouselState = carouselState,
            autoScrollDurationMillis = autoScrollMs,
            carouselIndicator = {
                if (slides.size > 1) {
                    val pageDescription = stringResource(
                        R.string.home_carousel_page,
                        activeIndex + 1,
                        slides.size,
                    )
                    CarouselDefaults.IndicatorRow(
                        itemCount = slides.size,
                        activeItemIndex = activeIndex,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 16.dp)
                            .testTag("home-hero-indicator")
                            .semantics { contentDescription = pageDescription },
                    )
                }
            },
        ) { index ->
            // Visual only — no focusable children inside the animated slide.
            HeroSlideVisual(
                slide = slides[index],
                imageLoader = imageLoader,
                pageLabel = stringResource(
                    R.string.home_carousel_page,
                    index + 1,
                    slides.size,
                ),
            )
        }

        // Stable action chrome overlaid on the active slide layout.
        HeroPrimaryAction(
            slide = activeSlide,
            focusRequester = actionFocus,
            multiSlide = slides.size > 1,
            onHorizontalDpad = { goLeft ->
                if (slides.size <= 1) {
                    // Single slide: swallow Left so focus does not enter the rail.
                    return@HeroPrimaryAction goLeft
                }
                val current = carouselState.activeItemIndex
                val target = if (goLeft) current - 1 else current + 1
                if (target in slides.indices) {
                    carouselState.seekToItem(target)
                }
                // Always consume — clamp at ends, never leak into the drawer.
                true
            },
            onPrimaryAction = { onPrimaryAction(activeSlide) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp + 140.dp + 24.dp, bottom = 22.dp),
        )
    }
}

@Composable
private fun HeroSlideVisual(
    slide: HomeHeroSlide,
    imageLoader: ImageLoader,
    pageLabel: String,
) {
    val accent = rememberChannelAccent(
        imageLoader = imageLoader,
        piconPath = slide.piconPath,
        channelId = slide.channelId,
    )
    val initials = remember(slide.channelName) { channelInitials(slide.channelName) }
    val overline = when (slide.kind) {
        HomeSlideKind.LIVE -> stringResource(R.string.home_slide_live)
        HomeSlideKind.CONTINUE -> stringResource(R.string.home_slide_continue)
        HomeSlideKind.ON_NOW -> stringResource(R.string.home_slide_on_now)
        HomeSlideKind.RECORDING -> stringResource(R.string.home_slide_recording)
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
                // Reserve the same vertical slot the overlay button occupies.
                Box(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun HeroPrimaryAction(
    slide: HomeHeroSlide,
    focusRequester: FocusRequester,
    multiSlide: Boolean,
    onHorizontalDpad: (goLeft: Boolean) -> Boolean,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resume only for the active live session. Everything else is Watch.
    val actionLabel = when {
        slide.kind == HomeSlideKind.LIVE -> stringResource(R.string.home_resume)
        slide.kind == HomeSlideKind.RECORDING && !slide.playable -> {
            stringResource(R.string.nav_recordings)
        }
        else -> stringResource(R.string.home_watch)
    }

    Button(
        onClick = onPrimaryAction,
        modifier = modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> onHorizontalDpad(true)
                    Key.DirectionRight -> {
                        // Right only special-cased when there are multiple slides;
                        // otherwise let focus move down-stream naturally.
                        if (multiSlide) onHorizontalDpad(false) else false
                    }
                    else -> false
                }
            }
            .testTag("home-hero-primary"),
    ) {
        Text(actionLabel)
    }
}

/**
 * Bridge to CarouselState's module-internal index setter (JVM-visible).
 * Used only so a focused child can still change slides — the stock Carousel
 * key handler does not run while a sibling Button holds focus.
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
