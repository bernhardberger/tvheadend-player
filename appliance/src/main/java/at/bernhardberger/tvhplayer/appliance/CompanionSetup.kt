package at.bernhardberger.tvhplayer.appliance

import at.bernhardberger.tvhplayer.appliance.update.UpdateResult
import at.bernhardberger.tvhplayer.appliance.update.UpdateScreenState

/** The focusable rows of the setup screen, top to bottom. */
enum class CompanionRow { PLAYER, ACCESSIBILITY, HOME, SYSTEM_HOME, STARTUP, LIVE_TV, GUIDE, RECORDINGS, UNKNOWN_APPS, AUTOMATIC, UPDATE }

enum class UpdateRowMode { CHECK, AVAILABLE, CHECKING, INSTALLING }

enum class UpdateFailure { COULD_NOT_CHECK, UPDATE_FAILED }

enum class CompanionBody { PLAYER_MISSING, UPDATING, ACCESSIBILITY_OFF, BLOCKED, INSTALL_UNKNOWN_OFF, SEVERAL_OPEN, ALL_SET }

sealed interface CompanionOverall {
    data object PlayerMissing : CompanionOverall
    data object Updating : CompanionOverall
    data class NeedsFix(val count: Int, val first: CompanionRow) : CompanionOverall
    data object AllSet : CompanionOverall
}

/** Everything the setup screen shows. Plain data, so JVM tests cover the derivations below. */
data class CompanionScreenState(
    val playerVersion: String?,
    val accessibilityStatus: CompanionServiceStatus,
    val installAllowed: Boolean,
    val defaultHome: Boolean,
    val hasSystemHome: Boolean,
    val update: UpdateScreenState,
    val functions: CompanionFunctions = CompanionFunctions(),
) {
    val playerInstalled: Boolean get() = playerVersion != null

    /** The Google TV home row is the way out only while this app holds HOME and Back is consumed. */
    val showSystemHome: Boolean get() = defaultHome && hasSystemHome

    /** The updater's newest-release version is recorded only after the archive passed validation. */
    val updateMode: UpdateRowMode
        get() = when {
            update.result == UpdateResult.CHECKING -> UpdateRowMode.CHECKING
            update.result == UpdateResult.INSTALLING -> UpdateRowMode.INSTALLING
            update.latestVersion.isNotEmpty() && (update.result == UpdateResult.WAITING_STANDBY ||
                update.result == UpdateResult.PENDING_CONFIRMATION) -> UpdateRowMode.AVAILABLE
            else -> UpdateRowMode.CHECK
        }

    /** What the update row reports for a failed result, else null. A release that is not newer is no failure. */
    val updateFailure: UpdateFailure?
        get() = when (update.result) {
            UpdateResult.NO_NETWORK, UpdateResult.RATE_LIMITED, UpdateResult.RELEASE_PROBLEM ->
                UpdateFailure.COULD_NOT_CHECK
            UpdateResult.HASH_MISMATCH, UpdateResult.WRONG_PACKAGE, UpdateResult.SIGNATURE_MISMATCH,
            UpdateResult.INSTALL_PERMISSION, UpdateResult.INSTALLER_FAILURE, UpdateResult.STORAGE_FAILURE ->
                UpdateFailure.UPDATE_FAILED
            else -> null
        }

    /** HOME is optional and never counted: on some TVs a third-party HOME cannot be made the default. */
    val unresolved: List<CompanionRow>
        get() = buildList {
            if (!playerInstalled) add(CompanionRow.PLAYER)
            if (accessibilityStatus != CompanionServiceStatus.ON) add(CompanionRow.ACCESSIBILITY)
            if (!installAllowed) add(CompanionRow.UNKNOWN_APPS)
        }

    val overall: CompanionOverall
        get() = when {
            !playerInstalled -> CompanionOverall.PlayerMissing
            updateMode == UpdateRowMode.INSTALLING -> CompanionOverall.Updating
            unresolved.isNotEmpty() -> CompanionOverall.NeedsFix(unresolved.size, unresolved.first())
            else -> CompanionOverall.AllSet
        }

    /** The sentence under the status title: one specific sentence per single open item, else a generic one. */
    val body: CompanionBody
        get() = when (val overall = overall) {
            CompanionOverall.PlayerMissing -> CompanionBody.PLAYER_MISSING
            CompanionOverall.Updating -> CompanionBody.UPDATING
            CompanionOverall.AllSet -> CompanionBody.ALL_SET
            is CompanionOverall.NeedsFix -> when {
                overall.count > 1 -> CompanionBody.SEVERAL_OPEN
                overall.first == CompanionRow.ACCESSIBILITY ->
                    if (accessibilityStatus == CompanionServiceStatus.BLOCKED) CompanionBody.BLOCKED else CompanionBody.ACCESSIBILITY_OFF
                else -> CompanionBody.INSTALL_UNKNOWN_OFF
            }
        }

    /** The first unresolved row, else a pending or running update, else the player row. */
    val initialFocus: CompanionRow
        get() = unresolved.firstOrNull()
            ?: if (updateMode != UpdateRowMode.CHECK) CompanionRow.UPDATE else CompanionRow.PLAYER
}
