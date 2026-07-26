package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.HomeCardItem
import at.bernhardberger.tvhplayer.core.HomeFocusTarget
import at.bernhardberger.tvhplayer.core.HomeHeroSlide
import at.bernhardberger.tvhplayer.core.HomeRowKind
import at.bernhardberger.tvhplayer.core.HomeSlideKind
import at.bernhardberger.tvhplayer.core.homeInitialFocusTarget
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.TvBrowsePanelAlpha
import at.bernhardberger.tvhplayer.ui.TvScreenPadding
import at.bernhardberger.tvhplayer.viewmodels.HomeViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    connectionUiState: ConnectionUiState,
    onRetryConnection: () -> Unit,
    onPlayChannel: (channelId: Int, serviceId: Int, name: String) -> Unit,
    onPlayRecording: (recordingId: Int) -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenChannels: () -> Unit,
    allowRecordings: Boolean = true,
    homeVm: HomeViewModel = koinViewModel(),
    imageLoader: ImageLoader = koinInject(),
) {
    // Rebuild through the ViewModel so allowRecordings=false uses the full fallback chain.
    LaunchedEffect(allowRecordings) {
        homeVm.setAllowRecordings(allowRecordings)
    }
    val model by homeVm.dashboard.collectAsStateWithLifecycle()

    val initialFocus = remember { FocusRequester() }
    val initialFocusTarget = remember(model) { homeInitialFocusTarget(model) }
    // Per-target latch: re-arm when empty STATUS_ACTION becomes HERO after data lands,
    // but never re-request within the same target (EPG refresh must not steal focus).
    // model emptiness is only a retry key until the requester is attached for the target.
    var claimedTarget by remember { mutableStateOf<HomeFocusTarget?>(null) }
    LaunchedEffect(initialFocusTarget, model.hero.isNotEmpty(), model.rows.isNotEmpty()) {
        if (claimedTarget == initialFocusTarget) return@LaunchedEffect
        if (runCatching { initialFocus.requestFocus() }.isSuccess) {
            claimedTarget = initialFocusTarget
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvBrowsePanelAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(TvScreenPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.nav_home),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }

            if (model.hero.isNotEmpty()) {
                item(key = "hero-section") {
                    HomeSectionTitle(stringResource(R.string.home_now_playing))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            model.hero,
                            key = { _, slide ->
                                "hero-${slide.kind}-${slide.channelId}-${slide.recordingId}"
                            },
                        ) { index, slide ->
                            HomeHeroCard(
                                slide = slide,
                                imageLoader = imageLoader,
                                modifier = if (
                                    index == 0 && initialFocusTarget == HomeFocusTarget.HERO
                                ) {
                                    Modifier.focusRequester(initialFocus)
                                } else {
                                    Modifier
                                },
                                onClick = {
                                    when {
                                        slide.kind == HomeSlideKind.RECORDING &&
                                            slide.playable &&
                                            slide.recordingId != null -> {
                                            onPlayRecording(slide.recordingId)
                                        }
                                        slide.kind == HomeSlideKind.RECORDING -> onOpenRecordings()
                                        else -> {
                                            onPlayChannel(
                                                slide.channelId,
                                                slide.channelId,
                                                slide.channelName,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            model.rows.forEach { row ->
                item(key = "row-${row.kind}") {
                    HomeSectionTitle(
                        text = stringResource(
                            when (row.kind) {
                                HomeRowKind.RECENT -> R.string.home_recent
                                HomeRowKind.ON_NOW -> R.string.home_on_now
                                HomeRowKind.RECORDINGS -> R.string.home_latest_recordings
                                HomeRowKind.SCHEDULED -> R.string.home_upcoming_recordings
                            },
                        ),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            row.items,
                            key = { _, item -> item.key },
                        ) { _, item ->
                            HomeContentCard(
                                item = item,
                                imageLoader = imageLoader,
                                onClick = {
                                    when {
                                        item.recordingId != null && item.playable -> {
                                            onPlayRecording(item.recordingId)
                                        }
                                        item.recordingId != null -> onOpenRecordings()
                                        else -> {
                                            onPlayChannel(
                                                item.channelId,
                                                item.channelId,
                                                item.channelName,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (model.hero.isEmpty() && model.rows.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = when (connectionUiState) {
                                ConnectionUiState.Connecting,
                                ConnectionUiState.SyncingChannels -> stringResource(R.string.loading)
                                is ConnectionUiState.Error -> stringResource(R.string.epg_server_failure)
                                else -> stringResource(R.string.home_empty)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val retry = connectionUiState == ConnectionUiState.Connecting ||
                            connectionUiState == ConnectionUiState.SyncingChannels ||
                            connectionUiState is ConnectionUiState.Error
                        Button(
                            onClick = if (retry) onRetryConnection else onOpenChannels,
                            modifier = if (initialFocusTarget == HomeFocusTarget.STATUS_ACTION) {
                                Modifier.focusRequester(initialFocus)
                            } else {
                                Modifier
                            },
                        ) {
                            Text(stringResource(if (retry) R.string.retry else R.string.nav_channels))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun HomeHeroCard(
    slide: HomeHeroSlide,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.width(420.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PiconBox(
                imageLoader = imageLoader,
                piconPath = slide.piconPath,
                modifier = Modifier.width(88.dp).height(56.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = slide.title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(slide.channelName)
                        if (slide.kind == HomeSlideKind.RECORDING) {
                            append(" • ")
                            append(stringResource(R.string.recordings_recording_now))
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomeContentCard(
    item: HomeCardItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.width(300.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PiconBox(
                imageLoader = imageLoader,
                piconPath = item.piconPath,
                modifier = Modifier.width(64.dp).height(44.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (item.recordingId != null) item.title else item.channelName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.recordingId != null) {
                        item.channelName
                    } else {
                        item.title
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
