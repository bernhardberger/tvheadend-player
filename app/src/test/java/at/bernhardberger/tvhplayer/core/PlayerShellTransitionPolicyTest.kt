package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerShellTransitionPolicyTest {
    private val playerRoutes = setOf("player", "recording-player")
    private val playerDestinations = listOf(
        "player/{channelId}/{serviceId}/{channelName}",
        "player/1/1/Channel",
        "recording-player/{recordingId}/{startMode}/{startPosition}",
        "recording-player/42/default/-1",
    )
    private val shellDestinations = listOf(
        "channels",
        "epg",
        "epg/{category}",
        "epg/news",
        "recordings",
        "settings",
        "unlock",
    )

    @Test
    fun `player and non-player route edges are instant in both directions`() {
        playerDestinations.forEach { playerDestination ->
            shellDestinations.forEach { shellDestination ->
                assertTrue(
                    "$playerDestination -> $shellDestination",
                    isPlayerShellTransition(
                        initialRoute = playerDestination,
                        targetRoute = shellDestination,
                        playerRoutes = playerRoutes,
                    )
                )
                assertTrue(
                    "$shellDestination -> $playerDestination",
                    isPlayerShellTransition(
                        initialRoute = shellDestination,
                        targetRoute = playerDestination,
                        playerRoutes = playerRoutes,
                    )
                )
            }
        }
    }

    @Test
    fun `same-family route edges retain the default transition`() {
        shellDestinations.forEach { initialRoute ->
            shellDestinations.forEach { targetRoute ->
                assertFalse(
                    "$initialRoute -> $targetRoute",
                    isPlayerShellTransition(
                        initialRoute = initialRoute,
                        targetRoute = targetRoute,
                        playerRoutes = playerRoutes,
                    )
                )
            }
        }
        playerDestinations.forEach { initialRoute ->
            playerDestinations.forEach { targetRoute ->
                assertFalse(
                    "$initialRoute -> $targetRoute",
                    isPlayerShellTransition(
                        initialRoute = initialRoute,
                        targetRoute = targetRoute,
                        playerRoutes = playerRoutes,
                    )
                )
            }
        }
    }

    @Test
    fun `missing route metadata never disables the default transition`() {
        assertFalse(
            isPlayerShellTransition(
                initialRoute = null,
                targetRoute = "player/1/1/Channel",
                playerRoutes = playerRoutes,
            )
        )
        assertFalse(
            isPlayerShellTransition(
                initialRoute = "player/1/1/Channel",
                targetRoute = null,
                playerRoutes = playerRoutes,
            )
        )
    }
}
