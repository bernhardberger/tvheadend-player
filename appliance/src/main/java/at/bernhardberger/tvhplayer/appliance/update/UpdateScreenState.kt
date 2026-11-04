package at.bernhardberger.tvhplayer.appliance.update

import at.bernhardberger.tvhplayer.appliance.R

data class UpdateScreenState(
    val automatic: Boolean = true,
    val latestVersion: String = "",
    val lastCheck: Long = 0,
    val result: UpdateResult = UpdateResult.NEVER,
    val installerMessage: String = "",
)

internal fun UpdateResult.messageResource(): Int = when (this) {
    UpdateResult.NEVER -> R.string.update_never
    UpdateResult.CHECKING -> R.string.update_checking
    UpdateResult.UP_TO_DATE -> R.string.update_up_to_date
    UpdateResult.NO_NETWORK -> R.string.update_no_network
    UpdateResult.RATE_LIMITED -> R.string.update_rate_limited
    UpdateResult.RELEASE_PROBLEM -> R.string.update_release_problem
    UpdateResult.HASH_MISMATCH -> R.string.update_hash_mismatch
    UpdateResult.WRONG_PACKAGE -> R.string.update_wrong_package
    UpdateResult.NOT_NEWER -> R.string.update_not_newer
    UpdateResult.SIGNATURE_MISMATCH -> R.string.update_signature_mismatch
    UpdateResult.INSTALL_PERMISSION -> R.string.update_install_permission
    UpdateResult.WAITING_STANDBY -> R.string.update_waiting_standby
    UpdateResult.INSTALLING -> R.string.update_installing
    UpdateResult.PENDING_CONFIRMATION -> R.string.update_pending_confirmation
    UpdateResult.INSTALLER_FAILURE -> R.string.update_installer_failure
    UpdateResult.STORAGE_FAILURE -> R.string.update_storage_failure
    UpdateResult.SUCCESS -> R.string.update_success
}
