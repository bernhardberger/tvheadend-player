package at.bernhardberger.tvhplayer.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvheadend.sdk.core.DvrSubscriptionError

@Composable
internal fun DvrSubscriptionError.label(): String = label(LocalContext.current)
