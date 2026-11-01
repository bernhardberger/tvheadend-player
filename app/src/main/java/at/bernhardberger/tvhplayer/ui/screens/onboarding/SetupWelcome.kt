package at.bernhardberger.tvhplayer.ui.screens.onboarding

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.components.arrivalFocus
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.startup.BrandWordmark
import at.bernhardberger.tvhplayer.ui.startup.MainStartupFrame
import at.bernhardberger.tvhplayer.ui.startup.MainStartupMarkSize

private const val WelcomeRevealDelayMs = 300
private const val WelcomeRevealMs = 400
private const val WelcomeMorphMs = 450

/** The signature's mark, where the startup mark shrinks to as the Server step comes in. */
private val SignatureMarkSize = 20.dp
private val SignatureBottom = 27.dp

/** Where the startup screen's mark is drawn, from its frame's anchor to the mark's center. */
private val StartupMarkCenterAboveMiddle = 114.dp - MainStartupMarkSize / 2

/**
 * First run's opening, outside the numbered Server / Account / Connect steps: the startup screen
 * stays, the welcome line and Continue fade in, and Continue morphs it away over the Server step.
 *
 * Continue has focus from the start. A key during the fade-in only ends it, however long it is
 * held, so it can't also press a button the viewer has not seen yet. Back still leaves the app.
 */
@Composable
internal fun SetupWelcome(
    state: ConnectionSetupState,
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
) {
    val onWelcome = state.step == SetupStep.WELCOME
    val morph = animateFloatAsState(
        targetValue = if (onWelcome) 0f else 1f,
        animationSpec = tween(if (motionEnabled) WelcomeMorphMs else 0, easing = PlayerMotion.Standard),
        label = "welcome-morph",
    )
    val morphing by remember { derivedStateOf { morph.value < 1f } }
    if (!onWelcome && !morphing) return

    var revealed by rememberSaveable { mutableStateOf(!motionEnabled) }
    val reveal = remember { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(revealed) {
        if (revealed) {
            reveal.snapTo(1f)
        } else {
            reveal.animateTo(1f, tween(WelcomeRevealMs, WelcomeRevealDelayMs, PlayerMotion.Standard))
            revealed = true
        }
    }
    var skipKey by remember { mutableStateOf<Key?>(null) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - morph.value }
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Back -> false
                    event.key == skipKey -> {
                        if (event.type == KeyEventType.KeyUp) skipKey = null
                        true
                    }
                    !revealed -> {
                        if (event.type == KeyEventType.KeyDown) {
                            revealed = true
                            skipKey = event.key
                        }
                        true
                    }
                    else -> false
                }
            }
            .then(if (onWelcome) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
        val toMarkerX = (SignatureMarkSize - TvBrowseColumnWidth * 2 - TvBrowseColumnGap) / 2
        val toMarkerY = (maxHeight - SignatureBottom - SignatureMarkSize / 2) - (maxHeight / 2 - StartupMarkCenterAboveMiddle)
        val markerScale = SignatureMarkSize / MainStartupMarkSize
        MainStartupFrame(
            contentPadding = TvFullScreenPadding,
            modifier = Modifier.fillMaxSize(),
            markModifier = Modifier.graphicsLayer {
                translationX = toMarkerX.toPx() * direction * morph.value
                translationY = toMarkerY.toPx() * morph.value
                val scale = 1f + (markerScale - 1f) * morph.value
                scaleX = scale
                scaleY = scale
            },
            backgroundMotionEnabled = motionEnabled,
            bodyContent = {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_welcome_body),
                    style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = reveal.value },
                )
            },
        )
        if (onWelcome) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = WelcomeActionBottom)) {
                WelcomeContinue(state, Modifier.graphicsLayer { alpha = reveal.value })
            }
        }
    }
}

private val WelcomeActionBottom = 64.dp

@Composable
private fun WelcomeContinue(state: ConnectionSetupState, modifier: Modifier) {
    Button(
        onClick = state::enterAddress,
        modifier = modifier.then(arrivalFocus()).testTag("setup-welcome-continue"),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) { Text(stringResource(R.string.onboarding_continue)) }
}

/**
 * The brand under first run's numbered steps, where the Welcome mark comes to rest: the mark, name
 * and version, at the foot of the guidance column. It only decorates, so it holds no focus and
 * says nothing to a screen reader.
 */
@Composable
internal fun SetupSignature() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .width(TvBrowseColumnWidth * 2 + TvBrowseColumnGap)
                .fillMaxHeight()
                .padding(bottom = SignatureBottom),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .testTag("setup-signature")
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.startup_brand_symbol),
                    contentDescription = null,
                    modifier = Modifier.size(SignatureMarkSize),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrandWordmark(fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.alignByBaseline())
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
}
