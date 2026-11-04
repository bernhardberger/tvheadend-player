package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvServerAvatarColors
import java.util.Locale

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

/**
 * A server profile's round avatar: the server's color with the account's letter or the server
 * glyph. No shared component shows a server; the row title or caption beside it names the
 * profile, so the avatar itself is decorative for accessibility.
 */
@Composable
fun ServerAvatar(look: ServerAvatarLook, size: Dp, modifier: Modifier = Modifier) {
    val (container, content) = TvServerAvatarColors[look.colorIndex]
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        if (look.letter == null) {
            Icon(painterResource(R.drawable.ic_dns), contentDescription = null, tint = content,
                modifier = Modifier.size(size * 0.5f))
        } else {
            val fontSize = with(LocalDensity.current) { (size * 0.45f).toSp() }
            Text(look.letter, color = content, fontSize = fontSize, lineHeight = fontSize, maxLines = 1)
        }
    }
}
