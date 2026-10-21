package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import at.bernhardberger.tvheadend.sdk.core.EpgRating
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.LocalWarmPlayback
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import coil3.ImageLoader
import kotlin.time.Instant

/**
 * Production Channels inside the production rail over the fictional fixture line-up.
 * Channels 8-18 are picon-only: no EPG. Channel 2 carries the long German name and title;
 * channel 6 has EPG without art. The view lives here as the app keeps it, so View options apply.
 */
internal class ChannelsLayoutFixture(private val compose: ComposeContentTestRule, private val images: ImageLoaderRule) {
    var selected by mutableStateOf<ChannelId?>(null)
    var channelsView by mutableStateOf(ChannelsView())
    var plays = 0
    lateinit var view: View

    val context: Application get() = ApplicationProvider.getApplicationContext()

    fun show(
        view: ChannelsView,
        imageLoader: ImageLoader? = null,
        tags: Boolean = true,
        manyTags: Boolean = false,
        playerReturn: PlayerReturnFocus? = null,
        scale: Float = 1f,
        warmPlayback: Boolean = false,
        allChannels: Boolean = true,
        emptyGroup: Boolean = false,
        recordingNow: Int? = null,
        scheduledNext: Int? = null,
        video: android.graphics.Bitmap? = null,
        guidePending: Boolean = false,
    ) {
        val observation = observation(tags, manyTags, emptyGroup, recordingNow, scheduledNext, guidePending)
        // All channels is opt-in under Settings: hiding it shows the tags only.
        val visibility = if (allChannels) ChannelScopeVisibility() else ChannelScopeVisibility(
            configured = true, allChannelsVisible = false,
            visibleTagIds = observation.channelCatalogForDisplay?.tags.orEmpty().map { it.id }.toSet(),
        )
        selected = playerReturn?.playingChannelId
        channelsView = view
        imageLoader?.let(images::install)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale), LocalWarmPlayback provides warmPlayback) {
                TVHeadendPlayerTheme {
                    this@ChannelsLayoutFixture.view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        // A still standing in for the live video behind the screen.
                        video?.let {
                            androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            at.bernhardberger.tvhplayer.ui.WarmPlaybackScrim()
                        }
                        SideRail(
                            currentRoute = AppDestination.CHANNELS,
                            showEpgMenu = true,
                            onRootBack = {},
                            onNavigate = {},
                        ) { padding, drawerActive ->
                            var activeTag by remember { mutableStateOf<ChannelTagId?>(null) }
                            ChannelsScreenContent(
                                contentPadding = padding,
                                initialFocusEnabled = !drawerActive,
                                channelScopeState = resolveChannelScopeState(observation.channelState, activeTag, visibility),
                                observation = observation,
                                tagNotice = false,
                                selectedId = { selected },
                                playingChannelId = playerReturn?.playingChannelId,
                                playerReturn = playerReturn,
                                view = channelsView,
                                onViewChange = { channelsView = it },
                                connectionUiState = ConnectionUiState.Ready,
                                onSelectChannel = { selected = it },
                                onSelectTag = { activeTag = it },
                                onDismissTagNotice = {},
                                onRetryConnection = {},
                                onOpenConnectionSettings = {},
                                onPlay = { _, _ -> plays++ },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The fictional fixture pack: picons by channel id, programme art from 101. */
    fun fixtureArt(): ImageLoader = FixtureArt.imageLoader(context, buildMap {
        PICONS.forEachIndexed { index, name -> put(ArtworkId(index + 1), FixtureArt.picon(name)) }
        ART.forEachIndexed { index, name -> put(ArtworkId(101 + index), FixtureArt.art(name)) }
    })

    fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    fun awaitFocus(target: SemanticsMatcher) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(target and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(target and isFocused()).assertIsFocused().assertIsDisplayed()
    }

    private fun observation(
        tags: Boolean, manyTags: Boolean, emptyGroup: Boolean, recordingNow: Int?, scheduledNext: Int?,
        guidePending: Boolean,
    ): SessionObservation {
        val channels = LINE_UP.mapIndexed { index, (name, _) ->
            Channel.create(
                id = ChannelId(index + 1L), name = name, number = index + 1L,
                icon = ArtworkId(index + 1),
                tagIds = if (manyTags) listOfNotNull(ChannelTagId(1).takeIf { index < 6 }, ChannelTagId(2L + index % (TAG_NAMES.size - 1)))
                    else listOf(ChannelTagId(if (index < 6) 1 else 2)),
            )
        }
        val channelTags = when {
            !tags -> emptyList()
            manyTags -> TAG_NAMES.mapIndexed { index, name ->
                val id = ChannelTagId(index + 1L)
                ChannelTag.create(id, name = name, channelIds = channels.filter { it.tagIds?.contains(id) == true }.map { it.id })
            }
            else -> listOfNotNull(
                ChannelTag.create(ChannelTagId(1), name = "Favorites", channelIds = channels.take(6).map { it.id }),
                ChannelTag.create(ChannelTagId(2), name = "Entertainment", channelIds = channels.drop(6).map { it.id }),
                ChannelTag.create(ChannelTagId(9), name = "Radio", channelIds = emptyList()).takeIf { emptyGroup },
            )
        }
        val now = System.currentTimeMillis() / 1000
        val events = channels.mapIndexedNotNull { index, channel ->
            val title = LINE_UP[index].second ?: return@mapIndexedNotNull null
            listOf(
                EpgEvent.create(
                    id = EventId(channel.id.value * 100), channelId = channel.id,
                    start = Instant.fromEpochSeconds(now - (10 + index * 7) * 60),
                    stop = Instant.fromEpochSeconds(now + (25 + index * 5) * 60),
                    title = title,
                    // Only the first programme has a subtitle, as EIT supplies one beside the title.
                    subtitle = "Winter on the ridge".takeIf { index == 0 },
                    // A rich XMLTV programme: genre, episode, year and age rating.
                    genre = "Documentary".takeIf { index == 0 },
                    episode = EpgEpisode(null, null, 2, 5, 3, 10, null, null, "S2 E3").takeIf { index == 0 },
                    copyrightYear = 2024L.takeIf { index == 0 },
                    rating = EpgRating(12, "XMLTV:FSK:12", null, null, null, null).takeIf { index == 0 },
                    summary = SYNOPSES[index],
                    // The Kettle Hour deliberately has no art: the immersive picon fallback.
                    image = ArtworkId(101 + index).takeIf { index != 5 },
                    nextEventId = EventId(channel.id.value * 100 + 1),
                ),
                EpgEvent.create(
                    id = EventId(channel.id.value * 100 + 1), channelId = channel.id,
                    start = Instant.fromEpochSeconds(now + (25 + index * 5) * 60),
                    stop = Instant.fromEpochSeconds(now + (85 + index * 5) * 60),
                    title = NEXT_TITLES[index],
                ),
            )
        }.flatten()
        return SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels, channelTags)),
            // A first connection whose guide is still on its way has no snapshot yet.
            epgState = if (guidePending) EpgRepositoryState.Synchronizing(null) else EpgRepositoryState.Current(
                EpgSnapshot.create(
                    events = events,
                    coverages = channels.map {
                        EpgCoverage.create(
                            channelId = it.id,
                            coveredFrom = Instant.fromEpochSeconds(now - 86400),
                            coveredTo = Instant.fromEpochSeconds(now + 7 * 86400),
                        )
                    },
                ),
            ),
            // Channel n's current programme is event n00, its next n01.
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOfNotNull(
                recordingNow?.let {
                    DvrEntry.create(DvrEntryId(1), channelId = ChannelId(it.toLong()), eventId = EventId(it * 100L),
                        state = DvrEntryState.RECORDING)
                },
                scheduledNext?.let {
                    DvrEntry.create(DvrEntryId(2), channelId = ChannelId(it.toLong()), eventId = EventId(it * 100L + 1),
                        state = DvrEntryState.SCHEDULED)
                },
            ))),
        )
    }

    companion object {
        /** Channel name to current programme, in fixture-pack line-up order. */
        val LINE_UP = listOf(
            "Ridge Earth" to "Ridge Light",
            "Harbor Sport International Fußballübertragungen HD" to
                "Harbor Kickoff: Harbor FC gegen Amber Town – Live aus dem Flutlichtstadion",
            "Northline News" to "Northline Tonight",
            "Kite Kids" to "Paper Kite Tales",
            "Ion Frame" to "Ion Wake",
            "Amber Stage" to "The Kettle Hour",
            "Harbor Lights" to "Harbor Lights Live",
            "Lantern Hour" to null,
            "Little Orbit" to null,
            "Glass Drama" to null,
            "Northline World" to null,
            "River Court" to null,
            "Landfunk 1" to null,
            "Reedfen 4" to null,
            "Coastal Miles" to null,
            "Roundtable" to null,
            "Foundry Docs" to null,
            "Quiet Hours" to null,
        )

        /** Fixture-pack names, by channel id; programme art follows at 101 + index. */
        val PICONS = listOf(
            "ridge-earth", "harbor-sport", "northline-news", "kite-kids", "ion-frame", "amber-stage",
            "harbor-lights", "lantern-hour", "little-orbit", "glass-drama", "northline-world", "river-court",
            "landfunk-1", "reedfen-4", "coastal-miles", "roundtable", "foundry-docs", "quiet-hours",
        )
        val ART = listOf("ridge-light", "harbor-kickoff", "northline-tonight", "paper-kite-tales", "ion-wake", "kettle-hour", "harbor-lights-live")
        private val SYNOPSES = listOf(
            "Seasonal mountain light, forests and weather moving over a ridge.",
            "Match coverage from a floodlit stadium as Harbor FC meet Amber Town.",
            "Evening studio news from a regional desk with two anchors.",
            "2D animation about a paper kite over painted hills.",
            "Sci-fi anime: a courier rides a magnetic rail through Ion Harbor.",
            "A warm kitchen programme about broths, bread and weeknight pans.",
            "A small-hall concert with a quartet.",
        )
        private val NEXT_TITLES = listOf("Tide Watch", "River Court", "Field Desk", "Little Orbit", "Lantern Road", "Glass Harbor", "Harbor Lights Live")
        private val TAG_NAMES = listOf(
            "Favorites", "Nachrichten und Wetter", "Sport", "Kinder und Familie", "Dokumentationen",
            "Unterhaltung", "Regionalprogramme", "Musik",
        )
    }
}
