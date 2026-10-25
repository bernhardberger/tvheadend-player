package at.bernhardberger.tvhplayer.ui.screens.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import at.bernhardberger.tvhplayer.ui.components.GuidedStepLayout

/**
 * Where editing the saved connection starts: what it uses now, one row per part that opens its
 * step, and "Test and save", which tests in place and saves once the test passes.
 */
@Composable
internal fun SummaryStep(state: ConnectionSetupState) {
    val requesters = remember { SummaryFocus.entries.associateWith { FocusRequester() } }
    val connecting = state.step == SetupStep.CONNECTING
    LaunchedEffect(state.focusRequest) {
        val target = state.summaryFocus.takeIf { it != SummaryFocus.CLEAR_PASSWORD || state.savedSignIn }
            ?: SummaryFocus.SIGN_IN
        requesters.getValue(target).requestFocus()
    }
    val form = state.form
    val host = form.host.trim()
    GuidedStepLayout(
        guidance = {
            GuidedStepGuidance(
                title = if (connecting) {
                    stringResource(R.string.onboarding_connecting_title, host)
                } else {
                    stringResource(R.string.edit_connection)
                },
                body = when {
                    !connecting -> stringResource(R.string.onboarding_edit_body)
                    form.username.isBlank() -> stringResource(R.string.onboarding_connecting_body_anonymous)
                    else -> stringResource(R.string.onboarding_connecting_body, form.username)
                },
            )
        },
    ) {
        SummaryRow(
            state, requesters, SummaryFocus.ADDRESS,
            title = stringResource(R.string.onboarding_server_address),
            subtitle = host,
            icon = R.drawable.ic_dns,
        )
        SummaryRow(
            state, requesters, SummaryFocus.PORT,
            title = stringResource(R.string.port_htsp),
            subtitle = form.port.trim(),
            icon = R.drawable.ic_tag,
        )
        SummaryRow(
            state, requesters, SummaryFocus.SIGN_IN,
            title = stringResource(R.string.onboarding_edit_sign_in),
            subtitle = form.username.ifBlank { stringResource(R.string.onboarding_change_sign_in_anonymous) },
            icon = R.drawable.ic_person,
        )
        SummaryRow(
            state, requesters, SummaryFocus.FIND,
            title = stringResource(R.string.onboarding_edit_find_server),
            subtitle = stringResource(R.string.onboarding_find_server_detail),
            icon = R.drawable.ic_search,
        )
        if (state.savedSignIn) {
            SummaryRow(
                state, requesters, SummaryFocus.CLEAR_PASSWORD,
                title = stringResource(R.string.clear_saved_password),
                subtitle = null,
                icon = R.drawable.ic_delete_outlined,
                opensStep = false,
            )
        }
        BusyButton(
            busy = connecting || state.saving,
            label = R.string.onboarding_test_and_save,
            onClick = { state.openFromSummary(SummaryFocus.TEST) },
            modifier = Modifier
                .padding(top = 8.dp)
                .focusRequester(requesters.getValue(SummaryFocus.TEST))
                .testTag("setup-test-and-save"),
        )
        SaveFailedText(form.feedback)
    }
}

@Composable
private fun SummaryRow(
    state: ConnectionSetupState,
    requesters: Map<SummaryFocus, FocusRequester>,
    row: SummaryFocus,
    title: String,
    subtitle: String?,
    @DrawableRes icon: Int,
    opensStep: Boolean = true,
) {
    GuidedStepAction(
        title = title,
        subtitle = subtitle,
        icon = icon,
        trailingIcon = if (opensStep) R.drawable.ic_keyboard_arrow_right else null,
        containerColor = Color.Transparent,
        onClick = { state.openFromSummary(row) },
        modifier = Modifier
            .focusRequester(requesters.getValue(row))
            .testTag("setup-summary-${row.name.lowercase()}"),
    )
}
