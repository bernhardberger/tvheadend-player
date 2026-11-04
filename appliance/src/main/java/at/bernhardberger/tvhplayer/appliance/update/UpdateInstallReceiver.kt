package at.bernhardberger.tvhplayer.appliance.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

class UpdateInstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION") // The typed parcelable overload is only available on API 33+.
    override fun onReceive(context: Context, intent: Intent) {
        val store = UpdateStore(context)
        val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (session == -1 || session != store.sessionId ||
            intent.action != "${context.packageName}.UPDATE_RESULT.$session") return
        val result = UpdatePolicy.installerResult(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE))
        if (result == UpdateResult.PENDING_CONFIRMATION) {
            CompanionUpdater.onConfirmation(intent.getParcelableExtra(Intent.EXTRA_INTENT))
            store.setResult(result)
        } else {
            CompanionUpdater.onConfirmation(null)
            store.finishInstall(result, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                .orEmpty().filter { !it.isISOControl() }.take(500))
        }
    }
}
