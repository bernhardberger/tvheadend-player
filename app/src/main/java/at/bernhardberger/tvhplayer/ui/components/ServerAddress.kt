package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R

/** The address shown outside editable fields: HTSP's default port adds no useful information. */
@Composable
internal fun serverAddress(host: String, port: String): String =
    if (port.trim() == "9982") host.trim()
    else stringResource(R.string.onboarding_change_address_detail, host.trim(), port.trim())
