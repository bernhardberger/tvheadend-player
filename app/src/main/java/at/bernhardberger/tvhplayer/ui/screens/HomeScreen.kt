package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.HomeDashboardModel
import at.bernhardberger.tvhplayer.core.HomeFocusTarget
import at.bernhardberger.tvhplayer.core.HomeRowKind
import at.bernhardberger.tvhplayer.core.HomeSlideKind
import at.bernhardberger.tvhplayer.core.homeInitialFocusTarget
import at.bernhardberger.tvhplayer.ui.TvBrowsePanelAlpha
import at.bernhardberger.tvhplayer.ui.TvScreenPadding
import at.bernhardberger.tvhplayer.ui.components.ActionsTemplate
import at.bernhardberger.tvhplayer.ui.components.HomeHeroCarousel
import at.bernhardberger.tvhplayer.ui.components.ProgrammeCard
import at.bernhardberger.tvhplayer.viewmodels.HomeViewModel
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun HomeScreen(
    connectionUiState: ConnectionUiState,
    onRetryConnection: () -> Unit,
    onPlayChannel: (channelId: Int, serviceId: Int, name: String) -> Unit,
    onPlayRecording: (recordingId: Int) -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenChannels: () -> Unit,
    allowRecordings: Boolean = true,
    homeVm: HomeViewModel = koinViewModel { parametersOf(allowRecordings) },
    imageLoader: ImageLoader = koinInject(),
) {
    LaunchedEffect(allowRecordings) {
        homeVm.setAllowRecordings(allowRecordings)
    }
    val model by homeVm.dashboard.collectAsStateWithLifecycle()
    HomeDashboard(
        model = model,
        connectionUiState = connectionUiState,
        imageLoader = imageLoader,
        onRetryConnection = onRetryConnection,
        onPlayChannel = onPlayChannel,
        onPlayRecording = onPlayRecording,
        onOpenRecordings = onOpenRecordings,
        onOpenChannels = onOpenChannels,
    )
}

/**
 * Pure presentation entry used by [HomeScreen] and instrumentation tests.
 */
@Composable
fun HomeDashboard(
    model: HomeDashboardModel,
    connectionUiState: ConnectionUiState,
    imageLoader: ImageLoader,
    onRetryConnection: () -> Unit,
    onPlayChannel: (channelId: Int, serviceId: Int, name: String) -> Unit,
    onPlayRecording: (recordingId: Int) -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenChannels: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val initialFocus = remember { FocusRequester() }
    val initialFocusTarget = remember(model) { homeInitialFocusTarget(model) }
    // Per-target latch with bounded frame retries until the requester is attached.
    var claimedTarget by remember { mutableStateOf<HomeFocusTarget?>(null) }
    LaunchedEffect(initialFocusTarget) {
        if (claimedTarget == initialFocusTarget) return@LaunchedEffect
        repeat(5) {
            if (runCatching { initialFocus.requestFocus() }.isSuccess) {
                claimedTarget = initialFocusTarget
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home-screen"),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvBrowsePanelAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(TvScreenPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
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
                    // The action sits at the hero's bottom edge, so bringing just that
                    // into view leaves the title and channel scrolled off the top —
                    // the identity disappears exactly when OK is about to be pressed.
                    // Whenever the hero holds focus, show all of it.
                    HomeHeroCarousel(
                        modifier = Modifier.onFocusChanged { state ->
                            if (state.hasFocus) {
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        },
                        slides = model.hero,
                        imageLoader = imageLoader,
                        primaryActionFocusRequester = if (
                            initialFocusTarget == HomeFocusTarget.HERO
                        ) {
                            initialFocus
                        } else {
                            null
                        },
                        onPrimaryAction = { slide ->
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
                        // 8 dp absorbs 1.06 focused scale (~5.3 dp overflow) without clipping.
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .focusRestorer()
                            .testTag("home-row-${row.kind.name.lowercase()}"),
                    ) {
                        itemsIndexed(
                            row.items,
                            key = { _, item -> item.key },
                        ) { _, item ->
                            ProgrammeCard(
                                item = item,
                                imageLoader = imageLoader,
                                testTag = "home-card-${item.key}",
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
                item(key = "home-empty") {
                    val retry = connectionUiState == ConnectionUiState.Connecting ||
                        connectionUiState == ConnectionUiState.SyncingChannels ||
                        connectionUiState is ConnectionUiState.Error
                    val title = when (connectionUiState) {
                        ConnectionUiState.Connecting,
                        ConnectionUiState.SyncingChannels -> stringResource(R.string.loading)
                        is ConnectionUiState.Error -> stringResource(R.string.epg_server_failure)
                        else -> stringResource(R.string.home_empty)
                    }
                    ActionsTemplate(
                        title = title,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .testTag("home-empty-state"),
                        actions = {
                            Button(
                                onClick = if (retry) onRetryConnection else onOpenChannels,
                                modifier = if (initialFocusTarget == HomeFocusTarget.STATUS_ACTION) {
                                    Modifier
                                        .focusRequester(initialFocus)
                                        .testTag("home-status-action")
                                } else {
                                    Modifier.testTag("home-status-action")
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (retry) R.string.retry else R.string.nav_channels,
                                    ),
                                )
                            }
                        },
                    )
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
