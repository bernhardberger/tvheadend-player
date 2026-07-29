package at.bernhardberger.tvhplayer.core

fun isPlayerShellTransition(
    initialRoute: String?,
    targetRoute: String?,
    playerRoutes: Set<String>,
): Boolean {
    if (initialRoute == null || targetRoute == null) return false

    val initialIsPlayer = initialRoute.substringBefore("/") in playerRoutes
    val targetIsPlayer = targetRoute.substringBefore("/") in playerRoutes
    return initialIsPlayer != targetIsPlayer
}
