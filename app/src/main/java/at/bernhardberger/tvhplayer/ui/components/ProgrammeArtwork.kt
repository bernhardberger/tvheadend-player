package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvCardProgressStripHeight
import at.bernhardberger.tvhplayer.ui.TvLogoPlateColor
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

    fun onSuccess(state: AsyncImagePainter.State.Success) {
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
): ImageRequest? {
    val context = LocalPlatformContext.current
    val fallback = TvSurfaceColors.containerLow.toArgb()
    val plateAtEnd = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(source, context, mode, avoidPlate, ratio, fallback, plateAtEnd,
        startPlateShare, keepWideTitleCrop) {
        source?.let { ImageRequest.Builder(context).data(it)
            .transformations(ProgrammeArtworkTransformation(mode, ratio, avoidPlate, plateAtEnd, fallback,
                startPlateShare, keepWideTitleCrop)).build() }
    }
}

/** Non-focusable artwork shared by airing cards and browse menus. */
@Composable
internal fun ProgrammeArtwork(
    image: ChannelIcon?, channelIcon: ChannelIcon?, progress: Float?,
    modifier: Modifier = Modifier,
    plateModifier: Modifier = Modifier, progressModifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    val session = LocalArtworkContext.current.currentSession
    val model = remember(session, image) {
        if (image != null && session != null) programmeArtworkModel(image, session) else null
    }
    val load = rememberPictureLoad(model)
    val request = rememberProgrammeImageRequest(model, avoidPlate = channelIcon != null)
    Box(modifier.clip(MaterialTheme.shapes.small).background(TvSurfaceColors.containerLowest)) {
        if (model == null || load.failed || (channelIcon != null && load.needsFallback)) {
            PiconBox(channelIcon, Modifier.align(Alignment.Center).size(100.dp, 45.dp))
        }
        if (model != null && !load.failed) {
            AsyncImage(request, null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = load::onSuccess, onError = { load.onError() })
            if (load.loaded && channelIcon != null) ArtworkLogoPlate(channelIcon,
                Modifier.align(Alignment.BottomStart).padding(8.dp).then(plateModifier))
        }
        // Same embedded strip as the player's schedule tiles; card borders keep clear of it.
        if (progress != null) ProgressStrip(progress,
            progressModifier.align(Alignment.BottomStart).fillMaxWidth(), height = TvCardProgressStripHeight, color = progressColor)
    }
}

internal val ArtworkLogoPlateWidth = 56.dp

/**
 * A channel's logo embedded in a programme picture's bottom-start corner, on a dark plate. Sized
 * for an airing card's picture; a smaller picture passes its [scale], so the plate keeps its share.
 */
@Composable
internal fun ArtworkLogoPlate(channelIcon: ChannelIcon?, modifier: Modifier = Modifier, scale: Float = 1f) {
    PiconBox(channelIcon, modifier.size(ArtworkLogoPlateWidth * scale, 32.dp * scale)
        .background(TvLogoPlateColor, MaterialTheme.shapes.extraSmall)
        .padding(horizontal = 4.dp * scale, vertical = 3.dp * scale))
}

@Composable
internal fun programmeAiringTime(event: EpgEvent, nowSec: Long): String =
    if (event.isRunning(nowSec)) stringResource(R.string.details_on_now_until, formatHm(event.stop.epochSeconds))
    else detailsFactValue(DetailsFact(DetailsFactKind.STARTS, event.start.epochSeconds), nowSec)
