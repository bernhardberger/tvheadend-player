package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBackPolicyTest {
    @Test
    fun passiveForegroundLayersReturnFocusToThePlayerRoot() {
        listOf(
            PlayerForegroundLayer.NUMBER_ENTRY,
            PlayerForegroundLayer.PENDING_SEEK_PREVIEW,
            PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW,
            PlayerForegroundLayer.STATS,
            PlayerForegroundLayer.NONE,
        ).forEach { layer ->
            assertTrue(layer.name, playerRootFocusRequired(layer))
        }

        listOf(
            PlayerForegroundLayer.CONFIRMATION,
            PlayerForegroundLayer.INFO,
            PlayerForegroundLayer.OPTIONS_DETAIL,
            PlayerForegroundLayer.OPTIONS_ROOT,
            PlayerForegroundLayer.CHANNEL_DRAWER,
            PlayerForegroundLayer.RECOVERY,
            PlayerForegroundLayer.TERMINAL_ERROR,
            PlayerForegroundLayer.CONTROLS,
        ).forEach { layer ->
            assertFalse(layer.name, playerRootFocusRequired(layer))
        }
    }

    @Test
    fun foregroundLayerFollowsTheCompleteVisualPriority() {
        val allVisible = PlayerForegroundContext(
            confirmationVisible = true,
            infoVisible = true,
            optionsPage = PlaybackOptionsPage.AUDIO,
            numberEntryVisible = true,
            channelDrawerVisible = true,
            recoveryVisible = true,
            terminalErrorVisible = true,
            seekPreviewPhase = PlayerSeekPreviewPhase.PENDING,
            controlsVisible = true,
            statsEnabled = true,
        )

        val expected = listOf(
            PlayerForegroundLayer.CONFIRMATION,
            PlayerForegroundLayer.INFO,
            PlayerForegroundLayer.OPTIONS_DETAIL,
            PlayerForegroundLayer.NUMBER_ENTRY,
            PlayerForegroundLayer.CHANNEL_DRAWER,
            PlayerForegroundLayer.RECOVERY,
            PlayerForegroundLayer.TERMINAL_ERROR,
            PlayerForegroundLayer.PENDING_SEEK_PREVIEW,
            PlayerForegroundLayer.CONTROLS,
            PlayerForegroundLayer.STATS,
            PlayerForegroundLayer.NONE,
        )
        val contexts = listOf(
            allVisible,
            allVisible.copy(confirmationVisible = false),
            allVisible.copy(confirmationVisible = false, infoVisible = false),
            allVisible.copy(
                confirmationVisible = false,
                infoVisible = false,
                optionsPage = null,
            ),
            allVisible.copy(
                confirmationVisible = false,
                infoVisible = false,
                optionsPage = null,
                numberEntryVisible = false,
            ),
            allVisible.copy(
                confirmationVisible = false,
                infoVisible = false,
                optionsPage = null,
                numberEntryVisible = false,
                channelDrawerVisible = false,
            ),
            allVisible.copy(
                confirmationVisible = false,
                infoVisible = false,
                optionsPage = null,
                numberEntryVisible = false,
                channelDrawerVisible = false,
                recoveryVisible = false,
            ),
            allVisible.copy(
                confirmationVisible = false,
                infoVisible = false,
                optionsPage = null,
                numberEntryVisible = false,
                channelDrawerVisible = false,
                recoveryVisible = false,
                terminalErrorVisible = false,
            ),
            allVisible.copy(
                confirmationVisible = false,
                infoVisible = false,
                optionsPage = null,
                numberEntryVisible = false,
                channelDrawerVisible = false,
                recoveryVisible = false,
                terminalErrorVisible = false,
                seekPreviewPhase = PlayerSeekPreviewPhase.NONE,
            ),
            allVisible.copy(
                confirmationVisible = false,
                infoVisible = false,
                optionsPage = null,
                numberEntryVisible = false,
                channelDrawerVisible = false,
                recoveryVisible = false,
                terminalErrorVisible = false,
                seekPreviewPhase = PlayerSeekPreviewPhase.NONE,
                controlsVisible = false,
            ),
            allVisible.copy(
                confirmationVisible = false,
                infoVisible = false,
                optionsPage = null,
                numberEntryVisible = false,
                channelDrawerVisible = false,
                recoveryVisible = false,
                terminalErrorVisible = false,
                seekPreviewPhase = PlayerSeekPreviewPhase.NONE,
                controlsVisible = false,
                statsEnabled = false,
            ),
        )

        assertEquals(expected, contexts.map(::playerForegroundLayer))
    }

    @Test
    fun optionsRootAndDispatchedSeekHaveDistinctLayers() {
        assertEquals(
            PlayerForegroundLayer.OPTIONS_ROOT,
            playerForegroundLayer(baseContext().copy(optionsPage = PlaybackOptionsPage.ROOT)),
        )
        assertEquals(
            PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW,
            playerForegroundLayer(
                baseContext().copy(seekPreviewPhase = PlayerSeekPreviewPhase.DISPATCHED)
            ),
        )
    }

    @Test
    fun everyHigherLayerSuppressesRenderedStats() {
        val stats = baseContext().copy(statsEnabled = true)
        assertEquals(PlayerForegroundLayer.STATS, playerForegroundLayer(stats))

        listOf(
            stats.copy(confirmationVisible = true),
            stats.copy(infoVisible = true),
            stats.copy(optionsPage = PlaybackOptionsPage.ROOT),
            stats.copy(numberEntryVisible = true),
            stats.copy(channelDrawerVisible = true),
            stats.copy(recoveryVisible = true),
            stats.copy(terminalErrorVisible = true),
            stats.copy(seekPreviewPhase = PlayerSeekPreviewPhase.PENDING),
            stats.copy(seekPreviewPhase = PlayerSeekPreviewPhase.DISPATCHED),
            stats.copy(controlsVisible = true),
        ).forEach { context ->
            assertNotEquals(context.toString(), PlayerForegroundLayer.STATS, playerForegroundLayer(context))
        }
    }

    @Test
    fun eachDismissibleLayerReturnsItsExplicitBackAction() {
        val actions = PlayerForegroundLayer.entries.associateWith { layer ->
            playerBackAction(
                surface = PlayerSurface.LIVE,
                simpleTvActive = false,
                foregroundLayer = layer,
            )
        }

        assertEquals(PlayerBackAction.DISMISS_CONFIRMATION, actions[PlayerForegroundLayer.CONFIRMATION])
        assertEquals(PlayerBackAction.CLOSE_INFO, actions[PlayerForegroundLayer.INFO])
        assertEquals(PlayerBackAction.RETURN_TO_OPTIONS_ROOT, actions[PlayerForegroundLayer.OPTIONS_DETAIL])
        assertEquals(PlayerBackAction.CLOSE_OPTIONS, actions[PlayerForegroundLayer.OPTIONS_ROOT])
        assertEquals(PlayerBackAction.CLEAR_NUMBER_ENTRY, actions[PlayerForegroundLayer.NUMBER_ENTRY])
        assertEquals(PlayerBackAction.CLOSE_CHANNEL_DRAWER, actions[PlayerForegroundLayer.CHANNEL_DRAWER])
        assertEquals(PlayerBackAction.CANCEL_PENDING_SEEK, actions[PlayerForegroundLayer.PENDING_SEEK_PREVIEW])
        assertEquals(PlayerBackAction.DISMISS_SEEK_FEEDBACK, actions[PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW])
        assertEquals(PlayerBackAction.HIDE_CONTROLS, actions[PlayerForegroundLayer.CONTROLS])
        assertEquals(PlayerBackAction.HIDE_STATS, actions[PlayerForegroundLayer.STATS])
    }

    @Test
    fun normalAndSimpleTvLiveBackDifferOnlyAtRecoveryAndLayerlessPlayer() {
        listOf(PlayerForegroundLayer.RECOVERY, PlayerForegroundLayer.NONE).forEach { layer ->
            assertEquals(
                PlayerBackAction.CLOSE_PLAYER,
                playerBackAction(PlayerSurface.LIVE, simpleTvActive = false, layer),
            )
            assertEquals(
                PlayerBackAction.CONSUME_WITHOUT_CHANGE,
                playerBackAction(PlayerSurface.LIVE, simpleTvActive = true, layer),
            )
        }
    }

    @Test
    fun terminalRecordingErrorAndLayerlessRecordingClose() {
        listOf(PlayerForegroundLayer.TERMINAL_ERROR, PlayerForegroundLayer.NONE).forEach { layer ->
            assertEquals(
                PlayerBackAction.CLOSE_PLAYER,
                playerBackAction(PlayerSurface.RECORDING, simpleTvActive = false, layer),
            )
        }
    }

    private fun baseContext() = PlayerForegroundContext(
        confirmationVisible = false,
        infoVisible = false,
        optionsPage = null,
        numberEntryVisible = false,
        channelDrawerVisible = false,
        recoveryVisible = false,
        terminalErrorVisible = false,
        seekPreviewPhase = PlayerSeekPreviewPhase.NONE,
        controlsVisible = false,
        statsEnabled = false,
    )
}
