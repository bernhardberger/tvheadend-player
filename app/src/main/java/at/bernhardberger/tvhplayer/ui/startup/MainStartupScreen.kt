package at.bernhardberger.tvhplayer.ui.startup

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.ui.components.LocalArtworkContext
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.ui.player.PlayerBusyRing
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupLoadingFeedback
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.ui.components.AppErrorAlert

private val MainStartupContentMaxWidth = 560.dp
internal val MainStartupMarkSize = 80.dp
private val MainStartupBackground = Color(0xFF0F1014)
private val MainStartupBrandFont = FontFamily(Font(R.font.outfit_550, FontWeight(550)))
private const val MainStartupRootTag = "main-startup-root"
private const val MainStartupMarkTag = "main-startup-mark"

/**
 * Opaque, state-driven startup status surface for appliance and autoplay entry.
 * The caller owns whether this screen is composed and provides its shell safe bounds.
 */
@Composable
fun MainStartupScreen(
    presentation: MainStartupPresentation,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onAction: (AppErrorAction) -> Unit = {},
    loadingFeedback: MainStartupLoadingFeedback = MainStartupLoadingFeedback.WAITING,
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    brandingVisible: Boolean = true,
    channel: Channel? = null,
    connected: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val actionable = presentation as? MainStartupPresentation.Actionable
    var wasActionable by remember { mutableStateOf(false) }

    LaunchedEffect(actionable != null) {
        if (wasActionable && actionable == null) {
            focusManager.clearFocus(force = true)
        }
        wasActionable = actionable != null
    }

    when (presentation) {
        is MainStartupPresentation.Passive -> MainStartupPassiveContent(
            presentation = presentation,
            contentPadding = contentPadding,
            modifier = modifier,
            loadingFeedback = loadingFeedback,
            motionEnabled = motionEnabled,
            brandingVisible = brandingVisible,
            channel = channel,
            connected = connected,
        )
        is MainStartupPresentation.Actionable -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(MainStartupBackground)
                .testTag(MainStartupRootTag),
        ) {
            AppErrorAlert(error = presentation.error, onAction = onAction)
        }
        MainStartupPresentation.Inactive,
        is MainStartupPresentation.Enter -> Unit
    }
}

@Composable
private fun MainStartupPassiveContent(
    presentation: MainStartupPresentation.Passive,
    contentPadding: PaddingValues,
    modifier: Modifier,
    loadingFeedback: MainStartupLoadingFeedback,
    motionEnabled: Boolean,
    brandingVisible: Boolean,
    channel: Channel?,
    connected: Boolean,
) {
    // A channel start intentionally masks Syncing/Reconnecting as Connecting, then Starting.
    ChannelStartupSurface(
        channel = channel,
        status = if (channel != null && !brandingVisible) stringResource(
            if (connected) R.string.main_startup_message_starting else R.string.main_startup_message_connecting,
        ) else stringResource(mainStartupMessageResource(presentation.messageKind)),
        modifier = modifier,
        freshStart = brandingVisible,
        motionEnabled = motionEnabled,
        feedbackVisible = loadingFeedback == MainStartupLoadingFeedback.WAITING,
        contentPadding = contentPadding,
        tuningChannel = channel?.takeIf { brandingVisible && connected },
    )
}

/**
 * Fixed startup slots shared by the app and live player. Slot contents change, never their
 * anchors. The shell owns keys and focus; only the visible stage is a live region.
 */
@Composable
internal fun ChannelStartupSurface(
    channel: Channel?,
    status: String,
    modifier: Modifier = Modifier,
    freshStart: Boolean = false,
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    feedbackVisible: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
    channelName: String? = channel?.name,
    announceStatus: Boolean = true,
    tuningChannel: Channel? = null,
) {
    var loaded by remember { mutableStateOf<StartupLogoContent?>(null) }
    // Only a replacement waits for its artwork: the first known channel names itself immediately.
    val displayedChannel = loaded?.channel?.takeUnless { it.id == channel?.id } ?: channel
    val title = startupIdentityTitle(displayedChannel, channel, channelName, freshStart)
    val brandTitle = stringResource(R.string.app_name)
    val contentAlpha by animateFloatAsState(
        if (feedbackVisible) 1f else 0f,
        tween(if (motionEnabled) PlayerMotion.ShortMs else 0),
        label = "startup-feedback",
    )
    // Keep the name's centre fixed while reserving two full typographic lines at larger fonts.
    val nameHeight = 64.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val glow = remember(freshStart) { Animatable(1f) }
    val glowPresent by remember(glow) { derivedStateOf { glow.value > 0f } }
    val contentVisible by remember { derivedStateOf { contentAlpha > 0f } }
    val fadeGlow = listOfNotNull(tuningChannel, channel.takeUnless { freshStart }).isNotEmpty()
    var glowStarted by remember(freshStart) { mutableStateOf(false) }
    val fadeRequested by rememberUpdatedState(fadeGlow)
    LaunchedEffect(freshStart) {
        snapshotFlow { fadeRequested }.first { it }
        glowStarted = true
    }
    LaunchedEffect(glowStarted, motionEnabled, freshStart) {
        if (glowStarted) glow.animateTo(0f, tween(if (motionEnabled) 2_000 else 0, easing = PlayerMotion.Standard))
    }
    Box(modifier.fillMaxSize().background(MainStartupBackground).testTag(MainStartupRootTag)
        .semantics {
            if (feedbackVisible) contentDescription = title ?: brandTitle
        }) {
        if (freshStart && motionEnabled && glowPresent) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = glow.value * contentAlpha }) {
                StartupBrandBackground(enabled = true)
            }
        }
        // Fractions retain the reference canvas's anchors on other TV sizes. Safe padding
        // bounds the text width, not the screen-centred vertical coordinates.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val safeWidth = Modifier.padding(
                start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                end = contentPadding.calculateRightPadding(LayoutDirection.Ltr),
            ).widthIn(max = 680.dp).fillMaxWidth()
            Box(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * (172f / 540f) - 30.dp)
                .size(136.dp, 60.dp).graphicsLayer { alpha = contentAlpha }) {
                StartupLogo(channel.takeUnless { freshStart }, loaded, motionEnabled) { loaded = it }
            }
            Box(safeWidth.align(Alignment.TopCenter).padding(top = maxHeight * (250f / 540f) - nameHeight / 2)
                .height(nameHeight).graphicsLayer { alpha = contentAlpha }, contentAlignment = Alignment.Center) {
                StartupIdentity(title, displayedChannel?.id.takeUnless { freshStart }, motionEnabled)
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * (328f / 540f) - 22.dp)
                .size(44.dp).graphicsLayer { alpha = contentAlpha }, contentAlignment = Alignment.Center) {
                if (motionEnabled) PlayerBusyRing(Modifier.testTag("main-startup-loader"))
            }
            // titleMedium's first baseline is anchored at 391dp; the reserved two-line slot
            // grows with font scale, well below the ring and inside the TV safe bounds.
            Box(safeWidth.align(Alignment.TopCenter).padding(top = maxHeight * (359f / 540f))
                .height(104.dp).graphicsLayer { alpha = contentAlpha }) {
                if (feedbackVisible) {
                    StartupStatus(status, motionEnabled, contentVisible && announceStatus, tuningChannel, channelName)
                }
            }
        }
    }
}

private fun startupIdentityTitle(
    displayed: Channel?, requested: Channel?, requestedName: String?, brand: Boolean,
): String? {
    if (brand) return null
    val name = if (displayed?.id == requested?.id) requestedName else displayed?.name
    return name?.let { channelTitleText(displayed?.visibleChannelNumber, it) }
}

@Composable
private fun StartupIdentity(title: String?, channelId: ChannelId?, motionEnabled: Boolean) {
    StartupSlot(title, motionEnabled, identity = channelId) { name, _ ->
        if (name == null) BrandWordmark(28.sp, 36.sp,
            Modifier.testTag("main-startup-wordmark").semantics { hideFromAccessibility() })
        else Text(name, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("main-startup-title").semantics { hideFromAccessibility() },
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StartupStatus(
    status: String, motionEnabled: Boolean, announce: Boolean,
    tuningChannel: Channel? = null, channelName: String? = null,
) {
    StartupSlot(Triple(status, tuningChannel, channelName), motionEnabled, identity = tuningChannel?.id ?: status) {
        (message, channel, name), visible ->
        if (channel != null) TuningStatus(channel, name ?: channel.name.orEmpty(), motionEnabled, visible && announce)
        else
        Text(message, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().paddingFromBaseline(top = 32.dp)
                .semantics {
                    if (visible && announce) liveRegion = LiveRegionMode.Polite else hideFromAccessibility()
                }.testTag("main-startup-status"))
    }
}

/** Replace by identity only; metadata updates within that identity stay in place. */
@Composable
private fun <T> StartupSlot(
    value: T,
    motionEnabled: Boolean,
    identity: Any? = value,
    content: @Composable (T, Boolean) -> Unit,
) {
    var shown by remember { mutableStateOf(value) }
    var shownIdentity by remember { mutableStateOf(identity) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(value, identity, motionEnabled) {
        val duration = if (motionEnabled) PlayerMotion.ShortMs else 0
        if (shownIdentity != identity) {
            alpha.animateTo(0f, tween(duration, easing = PlayerMotion.StandardAccelerate))
        }
        shown = value
        shownIdentity = identity
        alpha.animateTo(1f, tween(duration, easing = PlayerMotion.Standard))
    }
    Box(Modifier.graphicsLayer { this.alpha = alpha.value }, contentAlignment = Alignment.Center) {
        key(shownIdentity) { content(shown, alpha.value > 0f) }
    }
}

/**
 * Composition-scoped until the startup surface leaves at first frame. Do not retain across
 * session/profile replacement: the same id/icon would otherwise keep drawing under its old session.
 */
private data class StartupLogoContent(
    val channel: Channel,
    val session: CurrentSessionObservation?,
    val observation: SessionObservation?,
)

@Composable
private fun StartupLogo(
    channel: Channel?,
    loaded: StartupLogoContent?,
    motionEnabled: Boolean,
    onLoaded: (StartupLogoContent?) -> Unit,
) {
    val artwork = LocalArtworkContext.current
    fun ready(channel: Channel) {
        onLoaded(StartupLogoContent(channel, artwork.currentSession, artwork.latest.value))
    }
    LaunchedEffect(channel?.id, channel?.icon) {
        if (channel == null) onLoaded(null) else if (channel.icon == null) ready(channel)
    }
    val logoChanged = channel?.id != loaded?.channel?.id || channel?.icon != loaded?.channel?.icon
    if (channel != null && logoChanged && channel.icon != null) {
        key(channel.id, channel.icon) {
            PiconBox(channel.icon, Modifier.fillMaxSize().alpha(0f),
                onLogoSize = { ready(channel) }, onError = { ready(channel) })
        }
    }
    val display = remember(loaded, channel, logoChanged) {
        if (channel != null && !logoChanged) loaded?.copy(channel = channel) else loaded
    }
    // Brand, pending channel (empty), and loaded artwork are distinct slot values.
    // Read only the shown value below so a new request cannot cut off the outgoing mark.
    val slot = (channel == null) to display
    StartupSlot(slot, motionEnabled, identity = slot.first to display?.channel?.id) { (brand, logo), _ ->
        if (brand) Image(painterResource(R.drawable.startup_brand_symbol), null,
            Modifier.fillMaxSize().testTag(MainStartupMarkTag))
        else if (logo != null) ProvideArtworkSession(logo.session, logo.observation) {
            PiconBox(logo.channel.icon, Modifier.fillMaxSize().testTag("startup-logo-${logo.channel.name}"))
        }
    }
}

@Composable
internal fun MainStartupFrame(
    contentPadding: PaddingValues,
    modifier: Modifier,
    markModifier: Modifier = Modifier,
    backgroundMotionEnabled: Boolean = false,
    brandingVisible: Boolean = true,
    // Hidden through the brief wait, so a fast start shows no logo; the layout stays.
    brandShown: Boolean = true,
    bodyContent: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MainStartupBackground)
            .testTag(MainStartupRootTag),
        contentAlignment = Alignment.Center,
    ) {
        StartupBrandBackground(enabled = backgroundMotionEnabled)
        BoxWithConstraints(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val density = LocalDensity.current
            // Preserve the accepted brand anchor; reserve room for the larger ring and long status.
            val passiveReserve = 130.dp + 80.dp + (88.dp * density.fontScale)
            val passiveTop = (maxHeight / 2 - 114.dp)
                .coerceAtMost((maxHeight - passiveReserve).coerceAtLeast(0.dp))
                .coerceAtLeast(0.dp)
            // A brand-free return retains the player-aligned indicator.
            val ringTop = maxHeight / 2 - 22.dp
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = passiveTop)
                    .widthIn(max = MainStartupContentMaxWidth)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (brandingVisible) {
                    Image(
                        painter = painterResource(R.drawable.startup_brand_symbol),
                        contentDescription = null,
                        modifier = markModifier
                            .size(MainStartupMarkSize)
                            .alpha(if (brandShown) 1f else 0f)
                            .testTag(MainStartupMarkTag),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    BrandWordmark(
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        modifier = Modifier
                            .alpha(if (brandShown) 1f else 0f)
                            .semantics { heading() },
                    )
                }
                if (bodyContent != null && brandingVisible) {
                    bodyContent()
                }
            }
            if (!brandingVisible && bodyContent != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = ringTop)
                        .widthIn(max = MainStartupContentMaxWidth)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { bodyContent() }
            }
        }
    }
}

/** The product's name in its brand font and colors, at one size whatever the font scale. */
@Composable
internal fun BrandWordmark(fontSize: TextUnit, lineHeight: TextUnit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFE3E3E8))) { append("Tvheadend ") }
                withStyle(SpanStyle(color = Color(0xFFFA7F00))) { append("Player") }
            },
            fontFamily = MainStartupBrandFont,
            fontWeight = FontWeight(550),
            fontSize = fontSize,
            lineHeight = lineHeight,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = modifier,
        )
    }
}

@StringRes
internal fun mainStartupMessageResource(messageKind: MainStartupMessageKind): Int = when (messageKind) {
    MainStartupMessageKind.PREPARING -> R.string.main_startup_message_preparing
    MainStartupMessageKind.CONNECTING -> R.string.main_startup_message_connecting
    MainStartupMessageKind.SYNCING_METADATA -> R.string.main_startup_message_syncing_metadata
    MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA ->
        R.string.main_startup_message_waiting_for_current_channel_metadata
    MainStartupMessageKind.RECONNECTING -> R.string.main_startup_message_reconnecting
    MainStartupMessageKind.STARTING_TELEVISION -> R.string.main_startup_message_starting_television
    MainStartupMessageKind.RESUMING_PLAYBACK -> R.string.main_startup_message_resuming_playback
}
