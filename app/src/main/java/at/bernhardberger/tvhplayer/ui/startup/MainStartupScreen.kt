package at.bernhardberger.tvhplayer.ui.startup

import android.animation.ValueAnimator
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupLoadingFeedback
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.ui.TvSpacing32
import at.bernhardberger.tvhplayer.ui.components.AppErrorAlert

private val MainStartupContentMaxWidth = 560.dp
private val MainStartupMarkSize = 80.dp
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
) {
    MainStartupFrame(
        contentPadding = contentPadding,
        modifier = modifier,
        backgroundMotionEnabled = motionEnabled &&
            (brandingVisible || loadingFeedback == MainStartupLoadingFeedback.WAITING),
        brandingVisible = brandingVisible,
        brandShown = loadingFeedback == MainStartupLoadingFeedback.WAITING,
    ) {
        when (loadingFeedback) {
            MainStartupLoadingFeedback.HIDDEN -> Unit
            MainStartupLoadingFeedback.WAITING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (brandingVisible) Spacer(Modifier.height(TvSpacing32))
                    if (motionEnabled) {
                        if (brandingVisible) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(44.dp).testTag("main-startup-loader"),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            at.bernhardberger.tvhplayer.ui.player.PlayerBusyRing(
                                modifier = Modifier.testTag("main-startup-loader"),
                            )
                        }
                    } else {
                        // Keep the status anchor without a frozen spinner.
                        Spacer(Modifier.height(44.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    MainStartupStatus(presentation.messageKind)
                }
            }
        }
    }
}

@Composable
private fun MainStartupStatus(messageKind: MainStartupMessageKind) {
    Text(
        text = stringResource(mainStartupMessageResource(messageKind)),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("main-startup-status")
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun MainStartupFrame(
    contentPadding: PaddingValues,
    modifier: Modifier,
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
                        modifier = Modifier
                            .size(MainStartupMarkSize)
                            .alpha(if (brandShown) 1f else 0f)
                            .testTag(MainStartupMarkTag),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color(0xFFE3E3E8))) { append("Tvheadend ") }
                                withStyle(SpanStyle(color = Color(0xFFFA7F00))) { append("Player") }
                            },
                            fontFamily = MainStartupBrandFont,
                            fontWeight = FontWeight(550),
                            fontSize = 28.sp,
                            lineHeight = 36.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .alpha(if (brandShown) 1f else 0f)
                                .semantics { heading() },
                        )
                    }
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
