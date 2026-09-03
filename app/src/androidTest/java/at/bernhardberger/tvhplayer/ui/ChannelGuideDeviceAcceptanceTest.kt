package at.bernhardberger.tvhplayer.ui

import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.ExternalTargetAcceptanceRule
import at.bernhardberger.tvhplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class ChannelGuideDeviceAcceptanceTest {
    private val externalTargetAcceptance = ExternalTargetAcceptanceRule()
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(externalTargetAcceptance)
        .around(composeRule)

    @Test
    fun realCatalogRendersInNumericOrderAndGuideDoesNotClaimStaleEmptyState() {
        val session = GlobalContext.get().get<TvheadendSession>()
        val noChannels = composeRule.activity.getString(R.string.no_channels_available)

        var currentCatalog: ChannelCatalog? = null
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            val channelState = session.observation.value.channelState
            if (channelState is ChannelRepositoryState.Current && channelState.catalog.channels.size >= 2) {
                currentCatalog = channelState.catalog
                true
            } else {
                false
            }
        }
        val catalog = requireNotNull(currentCatalog)

        enterBrowseShell()
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("nav-channels")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("nav-epg")).fetchSemanticsNodes().isNotEmpty()
        }
        focusBrowseDestination("nav-channels")
        composeRule.onNodeWithTag("nav-channels")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("epg-screen")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.waitForIdle()
        assertTrue(
            "Guide presented no channels for a non-empty current catalog",
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(noChannels))
                .fetchSemanticsNodes().isEmpty(),
        )

        composeRule.onNodeWithTag("nav-epg")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(channelItemMatcher).fetchSemanticsNodes().size >= 2
        }

        val renderedIds = composeRule.onAllNodes(channelItemMatcher)
            .fetchSemanticsNodes()
            .map { node ->
                node.config[SemanticsProperties.TestTag].substringAfterLast('-').toLong()
            }
        val channelsById = catalog.channels.associateBy { it.id.value }
        val renderedChannels = renderedIds.map { id ->
            requireNotNull(channelsById[id]) { "Rendered channel $id was absent from the current catalog" }
        }
        assertTrue(
            "Rendered channels were not in typed numeric order",
            renderedChannels.zipWithNext().all { (left, right) ->
                compareChannels(left, right) <= 0
            },
        )

        val retainedState = session.observation.value.channelState
        assertTrue(retainedState is ChannelRepositoryState.Current)
        assertEquals(
            catalog.channels.map { it.id },
            (retainedState as ChannelRepositoryState.Current).catalog.channels.map { it.id },
        )
        assertTrue(!composeRule.activity.isFinishing)
    }

    @Test
    fun configuredConnectionUsesRedactedFieldPresenceSemantics() {
        val generalLabel = composeRule.activity.getString(R.string.settings_general_nav)
        val connectionLabel = composeRule.activity.getString(R.string.settings_connection_nav)

        enterBrowseShell()
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("nav-settings")).fetchSemanticsNodes().isNotEmpty()
        }
        focusBrowseDestination("nav-channels")
        composeRule.onNodeWithTag("nav-channels").performKeyInput {
            repeat(SETTINGS_NAVIGATION_STEPS) { pressKey(Key.DirectionDown) }
        }
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("nav-settings"))
                .fetchSemanticsNodes()
                .any { node -> node.config[SemanticsProperties.Focused] }
        }
        composeRule.onNodeWithTag("nav-settings").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithTag("nav-settings").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(generalLabel) and hasClickAction())
                .fetchSemanticsNodes()
                .any { node -> node.config[SemanticsProperties.Focused] }
        }
        composeRule.onNode(hasText(generalLabel) and hasClickAction())
            .assertIsFocused()
            .performKeyInput {
                repeat(CONNECTION_NAVIGATION_STEPS) { pressKey(Key.DirectionDown) }
            }
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(connectionLabel) and hasClickAction())
                .fetchSemanticsNodes()
                .any { node -> node.config[SemanticsProperties.Focused] }
        }
        composeRule.onNode(hasText(connectionLabel) and hasClickAction())
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(
                hasTestTag("connection-username") and hasConfiguredFieldValue,
            )
                .fetchSemanticsNodes().size == 1
        }
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(
                hasTestTag("connection-password") and
                    hasConfiguredFieldValue and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Password),
            )
                .fetchSemanticsNodes().size == 1
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            FIELD_PRESENCE_STATUS_CODE,
            Bundle().apply {
                putString("configuredUsernamePresent", "true")
                putString("configuredPasswordPresent", "true")
            },
        )

        assertTrue(!composeRule.activity.isFinishing)
    }

    private fun enterBrowseShell() {
        repeat(MAX_BROWSE_BACK_ACTIONS) {
            composeRule.waitForIdle()
            if (composeRule.onAllNodes(hasTestTag("nav-channels")).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
            composeRule.runOnUiThread {
                composeRule.activity.onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun focusBrowseDestination(tag: String) {
        composeRule.onNodeWithTag(tag).requestFocus()
        composeRule.waitUntil(timeoutMillis = DEVICE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag(tag))
                .fetchSemanticsNodes()
                .any { node -> node.config[SemanticsProperties.Focused] }
        }
    }

    private companion object {
        const val FIELD_PRESENCE_STATUS_CODE = 2
        const val DEVICE_TIMEOUT_MILLIS = 30_000L
        const val MAX_BROWSE_BACK_ACTIONS = 2
        const val SETTINGS_NAVIGATION_STEPS = 3
        const val CONNECTION_NAVIGATION_STEPS = 2

        val hasConfiguredFieldValue = SemanticsMatcher("has a configured field value") { node ->
            node.config.contains(SemanticsProperties.EditableText) &&
                node.config[SemanticsProperties.EditableText].isNotEmpty()
        }

        val channelItemMatcher = SemanticsMatcher("channel row or card") { node ->
            val tag = if (node.config.contains(SemanticsProperties.TestTag)) {
                node.config[SemanticsProperties.TestTag]
            } else {
                null
            }
            tag?.startsWith("channel-row-") == true || tag?.startsWith("channel-card-") == true
        }

        fun compareChannels(left: Channel, right: Channel): Int =
            compareValues(left.number == null, right.number == null)
                .takeIf { it != 0 }
                ?: compareValues(left.number, right.number)
                    .takeIf { it != 0 }
                ?: compareValues(left.numberMinor != null, right.numberMinor != null)
                    .takeIf { it != 0 }
                ?: compareValues(left.numberMinor, right.numberMinor)
                    .takeIf { it != 0 }
                ?: compareValues(left.id.value, right.id.value)
    }
}
