package at.bernhardberger.tvhplayer.touch.guide

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.LocalArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationBackEnabled
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationReselections
import at.bernhardberger.tvhplayer.touch.ui.OnDestinationReselected
import at.bernhardberger.tvhplayer.touch.ui.PhoneBrowseViewport
import at.bernhardberger.tvhplayer.touch.ui.ReportFrameBarsWhen
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import at.bernhardberger.tvhplayer.touch.ui.pageMove
import at.bernhardberger.tvhplayer.ui.common.AppClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

/** Uses the session catalogue, not the base screen's viewport or selected tag. */
@Composable
internal fun programDetailsForPages(
    stack: ProgramPageStack,
    session: TvheadendSession,
    playingId: String?,
    dispatcher: CoroutineDispatcher,
): Map<GuidePageKey.Details, ProgramDetails> {
    val visits = stack.pages.filterIsInstance<GuidePageKey.Details>()
    if (visits.isEmpty()) return emptyMap()
    val observation by session.observation.collectAsStateWithLifecycle()
    val generation = observation.currentSession?.generationIdentity
    val clock = activeGuideClock()
    val config = Configuration(LocalConfiguration.current)
    val uses24Hour = AppClock.uses24Hour
    val artwork = LocalArtworkModels.current
    val context = LocalContext.current
    var result by remember { mutableStateOf<DetailsResult?>(null) }
    LaunchedEffect(visits, observation, clock, config, uses24Hour, artwork, playingId) {
        if (clock != null && observation.epgSnapshotForDisplay != null) {
            val details = withContext(dispatcher) {
                val local = context.createConfigurationContext(config)
                val texts = guideTexts(local, config.locales[0], clock.zone, uses24Hour)
                val mapper = GuideMapper(
                    observation.channelCatalogForDisplay?.channels.orEmpty(), observation,
                    observation.epgSnapshotForDisplay?.events.orEmpty(), clock, clock.today, texts, artwork, playingId,
                )
                visits.mapNotNull { key -> mapper.details(key.programId)?.let { key to it } }.toMap()
            }
            if (generation === session.observation.value.currentSession?.generationIdentity) {
                result = DetailsResult(generation, details)
                if (generation != null) stack.removeMissing(visits.toSet() - details.keys)
            }
        }
    }
    return result?.takeIf { it.generation === generation }?.details.orEmpty()
}

private data class DetailsResult(
    val generation: SessionGenerationIdentity?,
    val details: Map<GuidePageKey.Details, ProgramDetails>,
)

/** A destination's retained base screen and its shared channel/Details pages. */
@Suppress("LongParameterList")
@Composable
internal fun ProgramPageHost(
    destination: Destination,
    destinations: List<Destination>,
    layout: GuideLayout,
    pageStack: ProgramPageStack,
    details: Map<GuidePageKey.Details, ProgramDetails>,
    actions: GuideActions,
    onShowInGuide: ((ProgramDetails) -> Unit)?,
    channelPage: @Composable (GuidePageKey.Channel, Boolean, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    browse: @Composable (Boolean, Modifier) -> Unit,
) {
    val pages = pageStack.pages
    val readyPages = pages.filter { it !is GuidePageKey.Details || it in details }
    val stack = listOf(GuidePageKey.Browse) + readyPages
    // Back cancels unmapped visits and closes what was actually shown, in one update.
    val onBack = { pageStack.back(readyPages) }
    val canHandleBack = LocalDestinationBackEnabled.current
    if (canHandleBack && pages.isNotEmpty()) OnDestinationReselected(destination, pageStack::clear)
    val render by rememberUpdatedState<@Composable (GuidePageKey, ProgramDetails?) -> Unit> { page, initial ->
        val top = page == stack.last()
        val active = top && canHandleBack
        val back = { if (active) onBack() }
        ReportFrameBarsWhen(top) {
            when (page) {
                GuidePageKey.Browse -> ProgramBrowseFrame(layout, destinations, top, active, pages.isEmpty(), browse)
                is GuidePageKey.Details -> ProgramSubpage(destination, destinations, layout) {
                    val previous = stack.getOrNull(stack.indexOf(page) - 1) as? GuidePageKey.Channel
                    ProgramDetailsEntry(
                        details[page], checkNotNull(initial), previous, layout, actions, onShowInGuide, back,
                    )
                }
                is GuidePageKey.Channel -> ProgramSubpage(destination, destinations, layout) {
                    channelPage(page, active, back)
                }
            }
        }
    }
    val decorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<GuidePageKey>(),
        rememberViewModelStoreNavEntryDecorator<GuidePageKey>(),
    )
    val entries = rememberDecoratedNavEntries(stack, decorators) { page ->
        val initial = (page as? GuidePageKey.Details)?.let(details::get)
        NavEntry(page) { render(page, initial) }
    }
    val scene = rememberSceneState(entries, sceneStrategies = emptyList(), onBack = onBack)
    val navigation = rememberNavigationEventState(scene)
    NavigationBackHandler(navigation, isBackEnabled = canHandleBack && pages.isNotEmpty(), onBackCompleted = onBack)
    val forward = pageMove(forward = true)
    val backward = pageMove(forward = false)
    NavDisplay(
        scene, navigation, modifier = modifier.fillMaxSize(),
        transitionSpec = { forward },
        popTransitionSpec = { backward },
    )
}

@Composable
private fun ProgramBrowseFrame(
    layout: GuideLayout,
    destinations: List<Destination>,
    top: Boolean,
    active: Boolean,
    noOpenPages: Boolean,
    browse: @Composable (Boolean, Modifier) -> Unit,
) {
    val taps = LocalDestinationReselections.current
    CompositionLocalProvider(LocalDestinationReselections provides if (active && noOpenPages) taps else emptyFlow()) {
        PhoneBrowseViewport(layout.compact && destinations.size > 1, active) { browse(top, it) }
    }
}

@Composable
private fun ProgramSubpage(
    destination: Destination, destinations: List<Destination>, layout: GuideLayout, content: @Composable () -> Unit,
) {
    TouchFrame(
        initialDestination = destination, destinations = destinations,
        showTopBar = { false }, showNavigation = { !layout.compact },
    ) { content() }
}

@Suppress("LongParameterList")
@Composable
private fun ProgramDetailsEntry(
    current: ProgramDetails?,
    initial: ProgramDetails,
    previous: GuidePageKey.Channel?,
    layout: GuideLayout,
    actions: GuideActions,
    onShowInGuide: ((ProgramDetails) -> Unit)?,
    back: () -> Unit,
) {
    var last by remember { mutableStateOf(initial) }
    val details = current ?: last
    SideEffect { last = details }
    val show = onShowInGuide?.takeIf { details.channelId.isNotBlank() && previous?.channelId != details.channelId }
        ?.let { open -> { _: String -> open(details) } }
    ProgramDetailsPage(details, layout, actions.forDetails(show), back)
}
