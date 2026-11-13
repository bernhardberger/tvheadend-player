package at.bernhardberger.tvhplayer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors

/** What a confirmation acts on, drawn as the panel between the question and its sentence. */
@Immutable
internal sealed interface ConfirmationSubject {
    /** A programme or recording; [progress] is a running recording's share recorded so far. */
    data class Programme(
        val name: String,
        val image: ChannelIcon?,
        val channelIcon: ChannelIcon?,
        val channelNumber: Long?,
        val channelName: String,
        val airTime: String,
        val progress: Float? = null,
        val target: String? = null,
    ) : ConfirmationSubject

    data class Folder(val name: String, val detail: String) : ConfirmationSubject

    data class Server(val look: ServerAvatarLook, val name: String, val detail: String) : ConfirmationSubject
}

@Immutable
internal data class ConfirmationChoice(val label: String, @DrawableRes val icon: Int, val tag: String)

/**
 * A yes/no confirmation: a fixed question, the subject's panel, what happens, and the safe choice
 * first and focused. Back and the safe button dismiss; the action runs after the fade, through [onDismiss]
 * as the other centred dialogs do. [spokenSentence] is what TalkBack reads for the sentence when it differs.
 */
@Composable
internal fun ConfirmationDialog(
    question: String,
    subject: ConfirmationSubject,
    sentence: String,
    safe: ConfirmationChoice,
    action: ConfirmationChoice,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    backEnabled: Boolean = true,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    visibility: MutableTransitionState<Boolean> = remember {
        MutableTransitionState(false).apply { targetState = true }
    },
    spokenSentence: String? = null,
) {
    var confirmed by remember { mutableStateOf(false) }
    CentredDialogShell(paneTitle = question, onDismiss = { if (confirmed) onConfirm() else onDismiss() },
        modifier = Modifier.onPreviewKeyEvent(onPreviewKeyEvent), backEnabled = backEnabled,
        visibility = visibility) {
        ConfirmationCard(question, subject, sentence, spokenSentence) {
            ConfirmationButtons(safe, action,
                onSafe = { visibility.targetState = false },
                onAction = { confirmed = true; visibility.targetState = false })
        }
    }
}

@Composable
private fun ConfirmationButtons(
    safe: ConfirmationChoice, action: ConfirmationChoice, onSafe: () -> Unit, onAction: () -> Unit,
) {
    val safeFocus = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }; safeFocus.requestFocus()
    }
    Button(onClick = onSafe, modifier = Modifier.height(40.dp).focusRequester(safeFocus).focusProperties {
        left = FocusRequester.Cancel; right = actionFocus
    }.testTag(safe.tag), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        ChoiceContent(safe)
    }
    OutlinedButton(onClick = onAction, modifier = Modifier.height(40.dp).focusRequester(actionFocus).focusProperties {
        left = safeFocus; right = FocusRequester.Cancel
    }.testTag(action.tag), contentPadding = OutlinedButtonDefaults.ButtonWithIconContentPadding) {
        ChoiceContent(action)
    }
}

@Composable
private fun ChoiceContent(choice: ConfirmationChoice) {
    Icon(painterResource(choice.icon), null, Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(choice.label, maxLines = 1, softWrap = false)
}

@Composable
private fun ConfirmationCard(
    question: String, subject: ConfirmationSubject, sentence: String, spokenSentence: String?,
    buttons: @Composable RowScope.() -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val styles = ConfirmationStyles(
        question = MaterialTheme.typography.headlineMedium,
        sentence = MaterialTheme.typography.bodyLarge,
        name = MaterialTheme.typography.titleMedium,
        channel = MaterialTheme.typography.labelLarge,
        row = MaterialTheme.typography.bodyMedium,
    )
    Layout(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(TvSurfaceColors.container)
        .testTag("centred-dialog-card").padding(24.dp),
        content = {
            Column(Modifier.testTag("centred-dialog-text")) {
                Text(question, Modifier.semantics { heading() }, style = styles.question,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                ConfirmationPanel(subject, styles)
                Spacer(Modifier.height(16.dp))
                Text(sentence, spokenSentence?.takeIf { it != sentence }
                    ?.let { spoken -> Modifier.semantics { contentDescription = spoken } } ?: Modifier,
                    style = styles.sentence, color = TvTextColors.secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = buttons)
        }) { measurables, constraints ->
        val buttonRow = measurables[1].maxIntrinsicWidth(Constraints.Infinity)
        val column = constraints.constrainWidth(confirmationColumnWidth(
            buttonRow = buttonRow, base = ConfirmationBaseColumn.roundToPx(), max = ConfirmationMaxColumn.roundToPx(),
            step = 4.dp.roundToPx(),
        ) { width -> confirmationNeedsMoreWidth(measurer, styles, this, question, sentence, subject, width) })
        val childConstraints = Constraints.fixedWidth(column).copy(maxHeight = constraints.maxHeight)
        val text = measurables[0].measure(childConstraints)
        val actions = measurables[1].measure(
            Constraints(maxWidth = Constraints.Infinity, maxHeight = constraints.maxHeight),
        )
        val gap = 24.dp.roundToPx()
        layout(column, constraints.constrainHeight(text.height + gap + actions.height)) {
            text.placeRelative(0, 0)
            actions.placeRelative(0, text.height + gap)
        }
    }
}

@Immutable
internal class ConfirmationStyles(
    val question: TextStyle, val sentence: TextStyle, val name: TextStyle, val channel: TextStyle, val row: TextStyle,
)

private val ConfirmationBaseColumn = 352.dp
private val ConfirmationMaxColumn = 520.dp
private fun confirmationNeedsMoreWidth(
    measurer: TextMeasurer, styles: ConfirmationStyles, density: Density,
    question: String, sentence: String, subject: ConfirmationSubject, column: Int,
): Boolean = with(density) {
    fun measure(text: String, style: TextStyle, width: Int): TextLayoutResult =
        measurer.measure(text, style, constraints = Constraints(maxWidth = width.coerceAtLeast(0)), density = density)
    fun orphaned(text: String, style: TextStyle) = measure(text, style, column).let { layout ->
        layout.lineCount > 1 && endsOnOneWord(text.substring(layout.getLineStart(layout.lineCount - 1)))
    }
    val panelText = column - (PanelPadding * 2 + subject.leadWidth() + PanelTextGap).roundToPx()
    orphaned(question, styles.question) || orphaned(sentence, styles.sentence) ||
        confirmationPanelNeedsMoreWidth(subject, styles, panelText, density, ::measure)
}

private fun confirmationPanelNeedsMoreWidth(
    subject: ConfirmationSubject, styles: ConfirmationStyles, panelText: Int, density: Density,
    measure: (String, TextStyle, Int) -> TextLayoutResult,
): Boolean = with(density) {
    fun overflows(text: String?, style: TextStyle, width: Int) =
        text != null && measure(text, style, width).lineCount > 1
    val name = when (subject) {
        is ConfirmationSubject.Programme -> subject.name
        is ConfirmationSubject.Folder -> subject.name
        is ConfirmationSubject.Server -> subject.name
    }
    if (measure(name, styles.name, panelText).lineCount > 2) return true
    when (subject) {
        is ConfirmationSubject.Programme -> {
            val logo = if (subject.inlineLogo() != null) (InlineLogoWidth + 8.dp).roundToPx() else 0
            overflows(channelTitleText(subject.channelNumber, subject.channelName), styles.channel, panelText - logo) ||
                overflows(subject.target, styles.row, panelText)
        }
        is ConfirmationSubject.Folder -> overflows(subject.detail, styles.row, panelText)
        is ConfirmationSubject.Server -> overflows(subject.detail, styles.row, panelText)
    }
}

/**
 * The column a confirmation lays out in: [base], or the [buttonRow] when wider, growing by [step] up
 * to [max] for as long as [needsMoreWidth] holds at the current width.
 */
internal fun confirmationColumnWidth(
    buttonRow: Int, base: Int, max: Int, step: Int, needsMoreWidth: (Int) -> Boolean,
): Int {
    var width = maxOf(base, buttonRow)
    while (width < max && needsMoreWidth(width)) width = minOf(width + step, max)
    return width
}

/** A wrapped text's [lastLine] that holds a single word, which a wider column should pull up. */
internal fun endsOnOneWord(lastLine: String): Boolean = lastLine.trim().none(Char::isWhitespace)
