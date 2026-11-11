package at.bernhardberger.tvhplayer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.contentColorFor
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvServerAvatarColors
import java.util.Locale
import kotlin.math.sin

internal const val SERVER_AVATAR_IN_USE_TAG = "server-avatar-in-use"

/** The size of the avatar that leads a Settings row, the size of a row icon's box. */
val ServerAvatarRowSize = 32.dp

/**
 * What a server avatar shows: [colorIndex] into [TvServerAvatarColors], picked from the server's
 * address so every profile on one box shares it, and [letter] for the account, or null for the
 * server glyph of a profile without a sign-in.
 */
data class ServerAvatarLook(val colorIndex: Int, val letter: String?)

/**
 * The same host and port always give the same color; the letter is the label's first letter, else
 * the user name's. An anonymous profile has no account and shows the server glyph.
 */
fun serverAvatarLook(host: String, port: Int, label: String?, username: String?): ServerAvatarLook {
    val colorIndex = Math.floorMod("${host.trim().lowercase(Locale.ROOT)}:$port".hashCode(), TvServerAvatarColors.size)
    val letter = if (username.isNullOrBlank()) null else
        (label?.takeIf { it.isNotBlank() } ?: username).trim().firstLetter()
    return ServerAvatarLook(colorIndex, letter)
}

fun serverAvatarLook(profile: ServerProfileSummary): ServerAvatarLook = serverAvatarLook(
    profile.host, profile.port, profile.label,
    profile.username.takeIf { profile.authenticationMode == ServerProfileAuthenticationMode.PASSWORD },
)

/** The first letter or digit, upper-cased; a name without one falls back to its first character. */
private fun String.firstLetter(): String? {
    val start = indexOfFirst(Char::isLetterOrDigit).takeIf { it >= 0 } ?: if (isEmpty()) return null else 0
    val codePoint = codePointAt(start)
    return String(Character.toChars(codePoint)).uppercase(Locale.getDefault())
}

/** What a [ServerAvatar] says about its connection with a ring or a badge. */
enum class ServerAvatarStatus { NONE, IN_USE, CONNECTING, CONNECTED, FAILED }

private const val REVEAL_RING_SWEPT = 1f / 3f
private const val REVEAL_BADGE_DONE = 0.75f
private const val REVEAL_BADGE_PEAK_AT = 0.6f
private const val REVEAL_BADGE_PEAK = 1.2f
private const val REVEAL_AVATAR_PULSE = 0.06f

/** How much of the success ring is drawn: it closes over the first third. */
internal fun revealRingSweep(t: Float): Float = (t / REVEAL_RING_SWEPT).coerceIn(0f, 1f)

/** The ring has closed; it fades away while the avatar and badge settle. */
internal fun revealRingAlpha(t: Float): Float =
    1f - ((t - REVEAL_RING_SWEPT) / (REVEAL_BADGE_DONE - REVEAL_RING_SWEPT)).coerceIn(0f, 1f)

private fun settlePhase(t: Float): Float =
    ((t - REVEAL_RING_SWEPT) / (REVEAL_BADGE_DONE - REVEAL_RING_SWEPT)).coerceIn(0f, 1f)

/** 1.0 up to 1.06 and back while the ring fades. */
internal fun revealAvatarScale(t: Float): Float =
    1f + REVEAL_AVATAR_PULSE * sin(Math.PI.toFloat() * settlePhase(t))

/** 0 up to 1.2 and back to 1.0: the badge pops in with an overshoot. */
internal fun revealBadgeScale(t: Float): Float {
    val p = settlePhase(t)
    return if (t <= REVEAL_RING_SWEPT) 0f else if (p < REVEAL_BADGE_PEAK_AT) {
        REVEAL_BADGE_PEAK * p / REVEAL_BADGE_PEAK_AT
    } else {
        REVEAL_BADGE_PEAK - (REVEAL_BADGE_PEAK - 1f) * (p - REVEAL_BADGE_PEAK_AT) / (1f - REVEAL_BADGE_PEAK_AT)
    }
}

private val RingGap = 8.dp
private val RingStroke = 4.dp

/** The room a ring around an avatar of any size takes outside it. */
val ServerAvatarRingReserve = RingGap + RingStroke

/**
 * A server profile's round avatar: the server's color with the account's letter or the server
 * glyph. No shared component shows a server; the row title or caption beside it names the
 * profile, so the avatar itself is decorative for accessibility except for what its [status] says.
 * [ServerAvatarStatus.IN_USE] adds a check badge on the lower right of a list avatar;
 * [badgeRing] is the color of the surface the avatar sits on, which rings the badge. A larger
 * avatar shows a connection test: a progress ring while it connects, the success check
 * (animated by [reveal], 0 to 1, read while drawing) or a dimmed avatar with an error ring and
 * badge. Its ring is drawn outside the avatar, in [ServerAvatarRingReserve].
 */
@Composable
fun ServerAvatar(
    look: ServerAvatarLook,
    size: Dp,
    modifier: Modifier = Modifier,
    status: ServerAvatarStatus = ServerAvatarStatus.NONE,
    badgeRing: Color = MaterialTheme.colorScheme.background,
    reveal: () -> Float = { 1f },
) {
    val (container, content) = TvServerAvatarColors[look.colorIndex]
    val colors = MaterialTheme.colorScheme
    val connected = status == ServerAvatarStatus.CONNECTED
    val failed = status == ServerAvatarStatus.FAILED
    val ringColor = if (connected) colors.primary else colors.error.copy(alpha = FAILED_RING_ALPHA)
    Box(
        modifier.size(size).drawBehind {
            if (connected || failed) {
                val width = RingStroke.toPx()
                val inset = RingGap.toPx() + width / 2
                val t = reveal()
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = if (connected) 360f * revealRingSweep(t) else 360f,
                    useCenter = false,
                    topLeft = Offset(-inset, -inset),
                    size = Size(this.size.width + inset * 2, this.size.height + inset * 2),
                    alpha = if (connected) revealRingAlpha(t) else 1f,
                    style = Stroke(width),
                )
            }
        },
    ) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val scale = if (connected) revealAvatarScale(reveal()) else 1f
                    scaleX = scale
                    scaleY = scale
                    alpha = if (failed) FAILED_AVATAR_ALPHA else 1f
                }
                .clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            if (look.letter == null) {
                Icon(painterResource(R.drawable.ic_dns), contentDescription = null, tint = content,
                    modifier = Modifier.size(size * 0.5f))
            } else {
                val fontSize = with(LocalDensity.current) { (size * 0.45f).toSp() }
                Text(look.letter, color = content, fontSize = fontSize, lineHeight = fontSize, maxLines = 1)
            }
        }
        if (status == ServerAvatarStatus.CONNECTING) {
            val connecting = stringResource(R.string.connection_overview_connecting)
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).requiredSize(size + ServerAvatarRingReserve * 2)
                    .semantics { contentDescription = connecting },
                color = colors.primary,
                trackColor = colors.borderVariant,
                strokeWidth = RingStroke,
            )
        }
        if (status == ServerAvatarStatus.IN_USE || connected || failed) {
            ServerAvatarBadge(status, size, badgeRing, reveal)
        }
    }
}

/** The large avatar of a connection test, with the room its ring and badge take around it. */
@Composable
fun ServerAvatarHero(look: ServerAvatarLook, status: ServerAvatarStatus, reveal: () -> Float = { 1f }) {
    Box(Modifier.padding(ServerAvatarRingReserve)) {
        ServerAvatar(look, ServerAvatarHeroSize, status = status, reveal = reveal)
    }
}

private val ServerAvatarHeroSize = 120.dp

@Composable
private fun BoxScope.ServerAvatarBadge(
    status: ServerAvatarStatus,
    size: Dp,
    badgeRing: Color,
    reveal: () -> Float,
) {
    val inUse = status == ServerAvatarStatus.IN_USE
    val badge = size * if (inUse) 0.44f else 0.36f
    val ring = size / if (inUse) 16 else 20
    val look = BadgeLook.of(status, badgeRing)
    Box(
        Modifier.align(Alignment.BottomEnd).offset(ring, ring).size(badge + ring * 2)
            .then(if (inUse) Modifier.testTag(SERVER_AVATAR_IN_USE_TAG) else Modifier)
            .graphicsLayer {
                val scale = if (status == ServerAvatarStatus.CONNECTED) revealBadgeScale(reveal()) else 1f
                scaleX = scale
                scaleY = scale
            }
            .background(badgeRing, CircleShape).padding(ring)
            .background(look.fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(look.icon),
            contentDescription = look.description,
            tint = look.tint,
            modifier = Modifier.size(badge * 0.75f),
        )
    }
}

private class BadgeLook(val fill: Color, val tint: Color, @DrawableRes val icon: Int, val description: String) {
    companion object {
        /**
         * In use is a neutral disc: the content color of the surface under the avatar, with the check cut
         * out in that surface's color. A result takes the role color of its outcome.
         */
        @Composable
        fun of(status: ServerAvatarStatus, badgeRing: Color): BadgeLook {
            val colors = MaterialTheme.colorScheme
            return when (status) {
                ServerAvatarStatus.CONNECTED -> BadgeLook(
                    fill = colors.primary,
                    tint = colors.onPrimary,
                    icon = R.drawable.ic_check,
                    description = stringResource(R.string.connection_overview_connected),
                )
                ServerAvatarStatus.FAILED -> BadgeLook(
                    fill = colors.error,
                    tint = colors.onError,
                    icon = R.drawable.ic_priority_high,
                    description = stringResource(R.string.connection_overview_unavailable),
                )
                else -> BadgeLook(
                    fill = contentColorFor(badgeRing),
                    tint = badgeRing,
                    icon = R.drawable.ic_check,
                    description = stringResource(R.string.server_in_use),
                )
            }
        }
    }
}

private const val FAILED_RING_ALPHA = 0.7f
private const val FAILED_AVATAR_ALPHA = 0.45f
