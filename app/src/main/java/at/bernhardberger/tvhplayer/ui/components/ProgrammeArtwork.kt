package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvCardProgressStripHeight
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import at.bernhardberger.tvhplayer.images.ProgrammeArtworkMode
import at.bernhardberger.tvhplayer.images.ProgrammeArtworkTransformation
import at.bernhardberger.tvhplayer.images.ArtworkLogoCorner
import at.bernhardberger.tvhplayer.images.ArtworkLogoGround
import at.bernhardberger.tvhplayer.images.ArtworkLogoPlace
import at.bernhardberger.tvhplayer.images.ArtworkLogoCornerTransformation
import coil3.BitmapImage
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import kotlinx.coroutines.delay

/**
 * Where a card's picture has got to. A card keeps what it shows without a picture until the picture
 * has faded in over it, and for good if it fails, so loading never leaves a black card. Drawn under
 * the picture, the fallback stays out of sight when the picture comes straight from memory.
 */
@Stable
internal class PictureLoad {
    /** The picture has arrived; what sits on top of a picture can show. */
    var loaded by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    internal var fadingIn by mutableStateOf(false)
        private set
    private var covered by mutableStateOf(false)
    val needsFallback: Boolean get() = !covered

    /** The picture's own places for a channel logo, best first; null when its request did not ask. */
    private var logoPlaces by mutableStateOf<List<ArtworkLogoPlace>?>(null)

    /** The first of the picture's places whose corner nothing else on the card has [taken]. */
    fun logoPlace(taken: Set<ArtworkLogoCorner> = emptySet()): ArtworkLogoPlace =
        logoPlaces?.firstOrNull { it.corner !in taken }
            ?: ArtworkLogoPlace(ArtworkLogoCorner.BOTTOM_START, ArtworkLogoGround.SHADE)

    fun onSuccess(state: AsyncImagePainter.State.Success) {
        // Known with the picture, so the logo never jumps after it shows.
        logoPlaces = (state.result.image as? BitmapImage)?.bitmap?.let(ArtworkLogoCornerTransformation::of)
        loaded = true
        if (state.result.dataSource == DataSource.MEMORY_CACHE) covered = true else fadingIn = true
    }

    fun onError() {
        failed = true
    }

    fun onFadedIn() {
        fadingIn = false
        covered = true
    }
}

/** A [model] of null is a card without a picture; a changed model starts over. */
@Composable
internal fun rememberPictureLoad(model: Any?): PictureLoad {
    val load = remember(model) { PictureLoad() }
    if (load.fadingIn) LaunchedEffect(load) {
        delay(PlayerMotion.MediumMs.toLong())
        load.onFadedIn()
    }
    return load
}

/** One request policy for programme pictures only; picons keep their own decode path. */
@Composable
internal fun rememberProgrammeImageRequest(
    source: Any?,
    mode: ProgrammeArtworkMode = ProgrammeArtworkMode.CROP,
    avoidPlate: Boolean = false,
    ratio: Double = 16.0 / 9,
    startPlateShare: Double = 0.0,
    keepWideTitleCrop: Boolean = false,
    /**
     * The logo box's share of the picture's width: also note the picture's
     * [ArtworkLogoCornerTransformation] places for a logo placed per picture. Zero for none.
     */
    logoShare: Double = 0.0,
): ImageRequest? {
    val context = LocalPlatformContext.current
    val fallback = TvSurfaceColors.containerLow.toArgb()
    val plateAtEnd = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(source, context, mode, avoidPlate, ratio, fallback, plateAtEnd,
        startPlateShare, keepWideTitleCrop, logoShare) {
        source?.let { ImageRequest.Builder(context).data(it)
            .transformations(listOfNotNull(ProgrammeArtworkTransformation(mode, ratio, avoidPlate, plateAtEnd, fallback,
                startPlateShare, keepWideTitleCrop),
                if (logoShare > 0) ArtworkLogoCornerTransformation(ratio, logoShare, plateAtEnd) else null)).build() }
    }
}

/** Non-focusable artwork shared by airing cards and browse menus. */
@Composable
internal fun ProgrammeArtwork(
    image: ChannelIcon?, channelIcon: ChannelIcon?, progress: Float?,
    modifier: Modifier = Modifier,
    plateModifier: Modifier = Modifier, progressModifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    /** Corners the caller draws its own marks in right now, such as a card's recording mark. */
    takenCorners: Set<ArtworkLogoCorner> = emptySet(),
) {
    val session = LocalArtworkContext.current.currentSession
    val model = remember(session, image) {
        if (image != null && session != null) programmeArtworkModel(image, session) else null
    }
    val load = rememberPictureLoad(model)
    val request = rememberProgrammeImageRequest(model, avoidPlate = channelIcon != null,
        logoShare = if (channelIcon != null) ArtworkLogoShare else 0.0)
    var widthPx by remember { mutableIntStateOf(0) }
    Box(modifier.onSizeChanged { widthPx = it.width }
        .clip(MaterialTheme.shapes.small).background(TvSurfaceColors.containerLowest)) {
        if (model == null || load.failed || (channelIcon != null && load.needsFallback)) {
            PiconBox(channelIcon, Modifier.align(Alignment.Center).size(100.dp, 45.dp))
        }
        if (model != null && !load.failed) {
            AsyncImage(request, null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = load::onSuccess, onError = { load.onError() })
            if (load.loaded && channelIcon != null) ArtworkCornerLogo(
                channelIcon, load.logoPlace(takenCorners), plateModifier,
                scale = widthPx / (LocalDensity.current.density * AiringCardWidth.value),
                bottomEdge = if (progress != null) TvCardProgressStripHeight else 0.dp)
        }
        // Same embedded strip as the player's schedule tiles; card borders keep clear of it.
        if (progress != null) ProgressStrip(progress,
            progressModifier.align(Alignment.BottomStart).fillMaxWidth(), height = TvCardProgressStripHeight, color = progressColor)
    }
}

internal val ArtworkLogoPlateWidth = 56.dp
internal val ArtworkLogoShare = (ArtworkLogoPlateWidth / AiringCardWidth).toDouble()

/** The logo's distance from the picture's edges, measured to its visible shape. */
internal val ArtworkLogoInset = 10.dp
private val ArtworkLogoHeight = 24.dp
private val ArtworkLogoShadeWidth = 115.dp
private val ArtworkLogoShadeHeight = 70.dp

/**
 * A channel's logo on a programme picture, in its [place]'s corner. Where the picture is busy or
 * mid-toned there, a shade tones only that corner: no plate edge, and a ground for the logo, dark
 * for most logos and light for a dark one.
 * A calm light or dark corner carries the logo as it is, inked for that ground. Sized for an airing card's
 * picture; a smaller picture passes its [scale], so the logo keeps its share. A progress strip
 * along the picture's bottom is its [bottomEdge]: a bottom logo keeps its inset from the strip.
 */
@Composable
internal fun BoxScope.ArtworkCornerLogo(
    picon: ChannelIcon, place: ArtworkLogoPlace, modifier: Modifier = Modifier, scale: Float,
    bottomEdge: Dp = 0.dp, onError: (() -> Unit)? = null,
) {
    if (scale <= 0f) return
    val corner = place.corner
    val atBottom = corner == ArtworkLogoCorner.BOTTOM_START || corner == ArtworkLogoCorner.BOTTOM_END
    val atStart = corner == ArtworkLogoCorner.BOTTOM_START || corner == ArtworkLogoCorner.TOP_START
    // A dark logo stands on a light shade, and takes one on a calm dark picture too; every other
    // logo keeps the dark shade.
    val artwork = LocalArtworkContext.current
    val source = requireNotNull(rememberPiconSource(artwork, picon))
    val toneKey = remember(artwork, source) { artwork.logoToneKey(source) }
    var needsLight by remember(source, toneKey) { mutableStateOf(toneKey?.let(logoTones::get)) }
    // The first load can correct the shade; later variants only refresh the hint so they cannot flip it back.
    var hasMeasuredTone by remember(source, toneKey) { mutableStateOf(false) }
    val light = needsLight == true
    val shaded = when (place.ground) {
        ArtworkLogoGround.SHADE -> needsLight != null
        ArtworkLogoGround.DARK_PICTURE -> light
        ArtworkLogoGround.LIGHT_PICTURE -> false
    }
    if (shaded) ArtworkCornerShade(atStart, atBottom, light, scale)
    val alignment = corner.alignment()
    PiconBox(picon, Modifier.align(alignment).padding(bottom = if (atBottom) bottomEdge else 0.dp)
        .padding(ArtworkLogoInset * scale)
        .size(ArtworkLogoPlateWidth * scale, ArtworkLogoHeight * scale).then(modifier),
        // Trimmed, so the logo's visible shape and not its file's empty margin keeps the inset.
        alignment = alignment, placeholders = false, trim = true, onError = onError,
        // Parts of the logo that would vanish on a light picture go dark, as on a focused row.
        ground = if (place.ground == ArtworkLogoGround.LIGHT_PICTURE || light) Color.White
            else MaterialTheme.colorScheme.background,
        onLogoTone = { tone ->
            if (!hasMeasuredTone) {
                hasMeasuredTone = true
                if (needsLight != tone) needsLight = tone
            }
            artwork.logoToneKey(source)?.let { logoTones[it] = tone }
        })
}

private fun ArtworkLogoCorner.alignment(): Alignment = when (this) {
    ArtworkLogoCorner.BOTTOM_START -> Alignment.BottomStart
    ArtworkLogoCorner.BOTTOM_END -> Alignment.BottomEnd
    ArtworkLogoCorner.TOP_START -> Alignment.TopStart
    ArtworkLogoCorner.TOP_END -> Alignment.TopEnd
}

@Composable
private fun BoxScope.ArtworkCornerShade(atStart: Boolean, atBottom: Boolean, light: Boolean, scale: Float) {
    Box(Modifier.matchParentSize().drawBehind {
        val atLeft = atStart == (layoutDirection == LayoutDirection.Ltr)
        val center = Offset(if (atLeft) 0f else size.width, if (atBottom) size.height else 0f)
        val radius = (ArtworkLogoShadeWidth * scale).toPx()
        val tone = if (light) Color.White else Color.Black
        val peak = if (light) .9f else .8f
        scale(1f, ArtworkLogoShadeHeight / ArtworkLogoShadeWidth, pivot = center) {
            drawCircle(Brush.radialGradient(
                0f to tone.copy(alpha = peak), .25f to tone.copy(alpha = peak * .725f),
                .5f to tone.copy(alpha = peak * .46f), .75f to tone.copy(alpha = peak * .21f),
                1f to Color.Transparent, center = center, radius = radius), radius, center)
        }
    })
}

private fun ArtworkContext.logoToneKey(source: Any): String? = when (source) {
    is AppArtworkSource -> source.currentSession?.let { tvheadend?.artwork?.cacheKey(it, source.id) }
        ?: source.savedKey
    is String -> "remote:$source"
    else -> null
}

/** The last measured tone for each logo, with profile-stable keys that do not retain the source model. */
private val logoTones = mutableMapOf<String, Boolean>()

/**
 * A channel without a logo, named on its programme picture: its name on a scrim along the
 * picture's bottom, where a logo cannot say it. [scale] and [bottomEdge] as for [ArtworkCornerLogo].
 */
@Composable
internal fun BoxScope.ArtworkChannelName(
    name: String, modifier: Modifier = Modifier, scale: Float = 1f, bottomEdge: Dp = 0.dp,
) {
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
        .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = .8f)))
        .padding(bottom = bottomEdge)
        .padding(start = ArtworkLogoInset * scale, end = ArtworkLogoInset * scale,
            top = 20.dp * scale, bottom = 6.dp * scale)) {
        Text(name, modifier, style = MaterialTheme.typography.labelLarge, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun programmeAiringTime(event: EpgEvent, nowSec: Long): String =
    if (event.isRunning(nowSec)) stringResource(R.string.details_on_now_until, formatHm(event.stop.epochSeconds))
    else detailsFactValue(DetailsFact(DetailsFactKind.STARTS, event.start.epochSeconds), nowSec)
