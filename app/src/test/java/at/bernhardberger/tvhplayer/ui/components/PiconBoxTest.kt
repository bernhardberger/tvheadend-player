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

/** Real native ListItem drawing; all Coil requests are intercepted before any I/O. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PiconBoxTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var loader: ImageLoader
    private lateinit var view: View
    private var requests = 0
    private val loadingGate = CompletableDeferred<Unit>()
    private var focusedTint = Color.Unspecified
    private var restingTint = Color.Unspecified
    private var focusedBackground = Color.Unspecified
    private var background = Color.Unspecified
    private var passiveTint = Color.Unspecified

    @After fun release() {
        if (::loader.isInitialized) loader.shutdown()
    }

    @Test fun missingPiconFollowsNativeRowFocusColor() = assertRowFallback(ArtworkState.MISSING)

    @Test fun loadingPiconFollowsNativeRowFocusColor() = assertRowFallback(ArtworkState.LOADING)

    @Test fun failedPiconFollowsNativeRowFocusColor() = assertRowFallback(ArtworkState.ERROR)

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
        // TV Material 1.1.0 provides its leading slot content color at 80% opacity.
        val alpha = (if (state == ArtworkState.LOADING) .35f else 1f) * .8f
        focus("other-row")
        assertPiconColor(restingTint.copy(alpha = alpha).compositeOver(background))
        focus("row")
        assertPiconColor(focusedTint.copy(alpha = alpha).compositeOver(focusedBackground))
        focus("other-row")
        assertPiconColor(restingTint.copy(alpha = alpha).compositeOver(background))
        assertEquals(if (state == ArtworkState.MISSING) 0 else 1, requests)
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
        loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
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
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    val colors = ListItemDefaults.colors()
                    focusedTint = colors.focusedContentColor
                    restingTint = colors.contentColor
                    background = MaterialTheme.colorScheme.background
                    focusedBackground = colors.focusedContainerColor.compositeOver(background)
                    passiveTint = MaterialTheme.colorScheme.onSurfaceVariant
                    Column(Modifier.fillMaxSize().background(background).padding(40.dp)) {
                        val picon = if (state == ArtworkState.MISSING) null else ArtworkId(1)
                        if (inRow) {
                            ChannelRow(
                                modifier = Modifier.width(396.dp).testTag("row"),
                                number = 1,
                                name = "Documentary",
                                programTitle = "Programme",
                                progress = null,
                                imageLoader = loader,
                                currentSession = session,
                                piconPath = picon,
                                onFocus = {},
                                onConfirm = {},
                            )
                            ListItem(
                                selected = false,
                                onClick = {},
                                headlineContent = { Text("Other row") },
                                modifier = Modifier.width(396.dp).testTag("other-row"),
                            )
                        } else {
                            // Passive/player callers must not start inheriting an arbitrary ambient tint.
                            CompositionLocalProvider(LocalContentColor provides Color.Magenta) {
                                PiconBox(
                                    imageLoader = loader,
                                    currentSession = session,
                                    piconPath = picon,
                                    modifier = Modifier.size(60.dp, 36.dp).testTag("passive-picon"),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(if (state == ArtworkState.MISSING) 0 else 1, requests)
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
