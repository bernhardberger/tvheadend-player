package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule

/** Real native ListItem drawing; all Coil requests are intercepted before any I/O. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PiconBoxTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var requests = 0
    private val loadingGate = CompletableDeferred<Unit>()
    private var background = Color.Unspecified
    private var passiveTint = Color.Unspecified
    /** The library's leading-slot content color by focus, read from the plain item. */
    private val leadingTint = HashMap<Boolean, Color>()
    private var rowFocusFill = Color.Unspecified

    @Test fun missingPiconTakesTheRowsContentColor() = assertRowFallback(ArtworkState.MISSING)

    @Test fun loadingPiconTakesTheRowsContentColor() = assertRowFallback(ArtworkState.LOADING)

    @Test fun failedPiconTakesTheRowsContentColor() = assertRowFallback(ArtworkState.ERROR)

    @Test fun successfulPiconKeepsItsOwnPixelsWhenRowFocusChanges() {
        show(ArtworkState.SUCCESS, inRow = true)
        focus("row")
        assertPiconColor(Color(PICON_COLOR))
        focus("other-row")
        assertPiconColor(Color(PICON_COLOR))
    }

    @Test fun passiveMissingPiconKeepsExistingDefaultTint() = assertPassiveFallback(ArtworkState.MISSING)

    @Test fun passiveLoadingPiconKeepsExistingDefaultTint() = assertPassiveFallback(ArtworkState.LOADING)

    @Test fun passiveFailedPiconKeepsExistingDefaultTint() = assertPassiveFallback(ArtworkState.ERROR)

    private fun assertRowFallback(state: ArtworkState) {
        show(state, inRow = true)
        // No plate: the icon takes the item's leading content color, dark on the light focus pill.
        val alpha = if (state == ArtworkState.LOADING) .35f else 1f
        focus("other-row")
        val rest = leadingTint.getValue(false).let { it.copy(alpha = it.alpha * alpha) }.compositeOver(background)
        val focused = leadingTint.getValue(true).let { it.copy(alpha = it.alpha * alpha) }
            .compositeOver(rowFocusFill.compositeOver(background))
        assertPiconColor(rest)
        focus("row")
        assertPiconColor(focused)
        focus("other-row")
        assertPiconColor(rest)
        // The logo is inked for each ground the row shows: rest, focus, rest again.
        assertEquals(if (state == ArtworkState.MISSING) 0 else 3, requests)
    }

    private fun assertPassiveFallback(state: ArtworkState) {
        show(state, inRow = false)
        val alpha = if (state == ArtworkState.LOADING) .35f else 1f
        assertPiconColor(passiveTint.copy(alpha = alpha).compositeOver(background), "passive-picon")
    }

    private fun show(state: ArtworkState, inRow: Boolean) {
        val session = FakeSessionObservation(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
            )),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).captureCurrentSession()
        val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
            .components {
                add(Interceptor { chain ->
                    assertTrue(chain.request.data is AppArtworkSource)
                    requests++
                    when (state) {
                        ArtworkState.MISSING -> error("Absent artwork must not be requested")
                        ArtworkState.LOADING -> {
                            loadingGate.await()
                            error("Loading request must remain suspended")
                        }
                        ArtworkState.ERROR -> ErrorResult(null, chain.request, IllegalStateException("Synthetic picon failure"))
                        ArtworkState.SUCCESS -> SuccessResult(
                            Bitmap.createBitmap(60, 36, Bitmap.Config.ARGB_8888).apply {
                                eraseColor(PICON_COLOR)
                            }.asImage(), chain.request, DataSource.MEMORY,
                        )
                    }
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .diskCache(null)
            .build()
        images.install(loader)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    background = MaterialTheme.colorScheme.background
                    passiveTint = MaterialTheme.colorScheme.onSurfaceVariant
                    rowFocusFill = ListItemDefaults.colors().focusedContainerColor
                    Column(Modifier.fillMaxSize().background(background).padding(40.dp)) {
                        val picon = if (state == ArtworkState.MISSING) null else ArtworkId(1)
                        if (inRow) {
                            ProvideArtworkSession(session) {
                                ChannelRow(
                                    modifier = Modifier.width(396.dp).testTag("row"),
                                    number = 1,
                                    name = "Documentary",
                                    programTitle = "Programme",
                                    progress = null,
                                    piconPath = picon,
                                    onFocus = {},
                                    onConfirm = {},
                                )
                            }
                            var otherFocused by remember { mutableStateOf(false) }
                            ListItem(
                                selected = false,
                                onClick = {},
                                headlineContent = { Text("Other row") },
                                leadingContent = { leadingTint[otherFocused] = LocalContentColor.current },
                                modifier = Modifier.width(396.dp).testTag("other-row")
                                    .onFocusChanged { otherFocused = it.isFocused },
                            )
                        } else {
                            // Passive/player callers must not start inheriting an arbitrary ambient tint.
                            CompositionLocalProvider(LocalContentColor provides Color.Magenta) {
                                ProvideArtworkSession(session) {
                                    PiconBox(
                                        piconPath = picon,
                                        modifier = Modifier.size(60.dp, 36.dp).testTag("passive-picon"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // Successful rows also warm the other focus fill; failed/loading logos do not prefetch.
        assertEquals(when {
            state == ArtworkState.MISSING -> 0
            state == ArtworkState.SUCCESS && inRow -> 2
            else -> 1
        }, requests)
    }

    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
        compose.onNodeWithTag(tag).assertIsFocused()
    }

    private fun assertPiconColor(expected: Color, tag: String = "channel-picon") {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val target = expected.toArgb()
            var matchingPixels = 0
            for (y in floor(bounds.top).toInt() until ceil(bounds.bottom).toInt()) {
                for (x in floor(bounds.left).toInt() until ceil(bounds.right).toInt()) {
                    val pixel = bitmap.getPixel(x, y)
                    if (listOf(0, 8, 16).all { shift ->
                        abs(((pixel shr shift) and 255) - ((target shr shift) and 255)) <= 3
                    }) matchingPixels++
                }
            }
            bitmap.recycle()
            assertTrue("Expected rendered picon tint ${target.toUInt().toString(16)}; matching pixels=$matchingPixels", matchingPixels >= 5)
        }
    }

    private enum class ArtworkState { MISSING, LOADING, ERROR, SUCCESS }

    private companion object {
        const val PICON_COLOR = 0xFF19A56F.toInt()
    }
}
