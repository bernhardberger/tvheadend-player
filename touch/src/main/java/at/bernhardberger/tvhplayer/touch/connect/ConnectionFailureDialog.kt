package at.bernhardberger.tvhplayer.touch.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.ui.common.AppErrorText
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupAction
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupFailure
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import at.bernhardberger.tvhplayer.ui.screens.onboarding.actionsFor

/** The failures the form cannot point at: a refused sign-in and "not Tvheadend" show in the fields. */
internal val SetupFailure.showsDialog: Boolean
    get() = this != SetupFailure.SIGN_IN_REJECTED && this != SetupFailure.NOT_TVHEADEND

/**
 * A connection test that failed for a reason the form cannot show. Where the shared policy lets the
 * connection be kept untested ([SetupAction.CONTINUE_ANYWAY] or, editing, [SetupAction.SAVE_ANYWAY]),
 * that replaces Try again; [onAction] gets the action the user chose.
 */
@Composable
internal fun ConnectionFailureDialog(
    failure: SetupFailure,
    mode: SetupMode,
    onCancel: () -> Unit,
    onAction: (SetupAction) -> Unit,
) {
    val context = LocalContext.current
    val text = remember(context) { AppErrorText(context) }
    val condition = failure.condition
    val anyway = failure.actionsFor(mode).firstOrNull {
        it == SetupAction.CONTINUE_ANYWAY || it == SetupAction.SAVE_ANYWAY
    }
    val hint = condition.touchHintRes()?.takeIf { failure.showsHint }?.let { stringResource(it) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(condition.touchTitleRes())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(listOfNotNull(text.reason(condition), hint).joinToString(" "))
                when (anyway) {
                    SetupAction.CONTINUE_ANYWAY ->
                        Text(stringResource(SharedR.string.onboarding_continue_anyway_detail))
                    SetupAction.SAVE_ANYWAY -> Text(stringResource(SharedR.string.onboarding_save_anyway_detail))
                    else -> Unit
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(SharedR.string.cancel)) } },
        confirmButton = {
            val action = anyway ?: SetupAction.TRY_AGAIN
            TextButton(onClick = { onAction(action) }) {
                Text(
                    stringResource(
                        when (action) {
                            SetupAction.CONTINUE_ANYWAY -> SharedR.string.onboarding_continue_anyway
                            SetupAction.SAVE_ANYWAY -> SharedR.string.onboarding_save_anyway
                            else -> SharedR.string.connection_try_again
                        },
                    ),
                )
            }
        },
    )
}
