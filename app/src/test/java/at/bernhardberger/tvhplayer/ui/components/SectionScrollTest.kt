package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A page like the programme details: a top block, a row of cards, then the facts, each section
// resting at a 100dp heading keyline (mdpi: px = dp).
@OptIn(ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class SectionScrollTest {
    @get:Rule val compose = createComposeRule()

    private enum class Part { TOP, ROW, FACTS }
    private val factsRequester = BringIntoViewRequester()
    private val topFocus = FocusRequester()
    private lateinit var scope: CoroutineScope
    private var factsTop = 0

    private fun show() {
        compose.setContent {
            scope = rememberCoroutineScope()
            val scroll = rememberScrollState()
            var factsAt by remember { mutableIntStateOf(Int.MAX_VALUE) }
            val sections = rememberSectionScrollSpec(scroll, Part.TOP, RowChangeMotion,
                sectionAt = { top -> if (top < 300) Part.TOP else if (top < factsAt) Part.ROW else Part.FACTS }) { target, _ ->
                when (target) { Part.TOP -> 0; Part.ROW -> 300 - 100; Part.FACTS -> factsAt - 100 }
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides sections) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("page")) {
                    Box(Modifier.fillMaxWidth().height(300.dp).scrollSection(sections, Part.TOP)) {
                        Box(Modifier.size(100.dp, 40.dp).focusRequester(topFocus).focusable().testTag("top"))
                    }
                    Column(Modifier.scrollSection(sections, Part.ROW)) {
                        Spacer(Modifier.height(40.dp))
                        Row { repeat(2) { Box(Modifier.size(100.dp, 60.dp).focusable().testTag("card-$it")) } }
                        Spacer(Modifier.height(100.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(300.dp).onPlaced {
                        factsAt = it.positionInParent().y.roundToInt(); factsTop = factsAt
                    }.scrollSection(sections, Part.FACTS).bringIntoViewRequester(factsRequester).focusable().testTag("facts"))
                    Spacer(Modifier.height(440.dp))
                }
            }
        }
        compose.runOnIdle { topFocus.requestFocus() }
        compose.onNodeWithTag("top").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("card-0").assertIsFocused()
        Assert.assertEquals(200f, scrollValue(), 0f)
    }

    private fun scrollValue() = compose.onNodeWithTag("page").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun requestFacts(): Job {
        lateinit var job: Job
        compose.runOnIdle { job = scope.launch(start = CoroutineStart.UNDISPATCHED) { factsRequester.bringIntoView() } }
        return job
    }

    // A request other than input focus entering a section, such as a requester of its own, keeps
    // the standard path: it scrolls with the request and input focus stays where it is.
    @Test fun anotherRequestScrollsWithItsOwnRequest() {
        show()
        compose.mainClock.autoAdvance = false
        val request = requestFacts()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        Assert.assertTrue("the request waits for its own scroll", request.isActive)
        Assert.assertTrue("the page scrolls towards the facts: ${scrollValue()}", scrollValue() > 200f)
        compose.mainClock.advanceTimeBy(1000)
        Assert.assertTrue("the request completes with its scroll", request.isCompleted && !request.isCancelled)
        Assert.assertEquals(factsTop - 100f, scrollValue(), .5f)
        Assert.assertEquals(100f, compose.onNodeWithTag("facts").getUnclippedBoundsInRoot().top.value, .5f)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("card-0").assertIsFocused()
    }

    @Test fun aCancelledRequestStopsItsScroll() {
        show()
        compose.mainClock.autoAdvance = false
        val request = requestFacts()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val moved = scrollValue()
        Assert.assertTrue("the page scrolls towards the facts: $moved", moved > 200f && moved < factsTop - 100f)
        compose.runOnIdle { request.cancel() }
        val values = (1..30).map { compose.mainClock.advanceTimeByFrame(); scrollValue() }
        Assert.assertTrue("the facts are never reached once the caller is gone: $values",
            values.all { it < factsTop - 100f - 1f })
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("card-0").assertIsFocused()
    }
}
