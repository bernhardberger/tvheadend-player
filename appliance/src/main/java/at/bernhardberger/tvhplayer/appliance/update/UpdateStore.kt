package at.bernhardberger.tvhplayer.appliance.update

import android.content.Context
import android.content.SharedPreferences
import at.bernhardberger.tvhplayer.appliance.BuildConfig

class UpdateStore(private val context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences("companion_updates", Context.MODE_PRIVATE)
    val automatic: Boolean get() = preferences.getBoolean("automatic", true)
    val lastCheck: Long get() = preferences.getLong("last_check", 0)
    val latestVersion: String get() = preferences.getString("latest_version", "").orEmpty()
    val result: UpdateResult get() = runCatching {
        UpdateResult.valueOf(preferences.getString("result", UpdateResult.NEVER.name)!!)
    }.getOrDefault(UpdateResult.NEVER)
    val installerMessage: String get() = preferences.getString("installer_message", "").orEmpty()
    val sessionId: Int get() = preferences.getInt("session_id", -1)

    fun setAutomatic(enabled: Boolean) { preferences.edit().putBoolean("automatic", enabled).apply() }
    fun setResult(result: UpdateResult, message: String = "") {
        preferences.edit().putString("result", result.name).putString("installer_message", message).commit()
    }

    fun recordCheck() {
        preferences.edit().putLong("last_check", System.currentTimeMillis())
            .putString("latest_version", "").putString("installer_message", "")
            .putString("result", UpdateResult.CHECKING.name).commit()
    }

    fun setLatest(version: String) { preferences.edit().putString("latest_version", version).commit() }

    fun beforeCommit(sessionId: Int, manual: Boolean) {
        check(preferences.edit().putInt("session_id", sessionId)
            .putLong("commit_from", BuildConfig.VERSION_CODE.toLong()).putBoolean("commit_manual", manual)
            .putLong("commit_at", System.currentTimeMillis())
            .putBoolean("suppress_connect", true).putString("result", UpdateResult.INSTALLING.name).commit())
    }

    fun markManualConfirmation(): Boolean = sessionId != -1 &&
        preferences.edit().putBoolean("commit_manual", true).commit()

    fun finishInstall(result: UpdateResult, message: String = "") {
        val manual = preferences.getBoolean("commit_manual", false)
        preferences.edit().remove("session_id").remove("commit_from").remove("commit_manual").remove("commit_at")
            .putBoolean("suppress_connect", result == UpdateResult.SUCCESS)
            .putBoolean("manual_return", result == UpdateResult.SUCCESS && manual)
            .putString("result", result.name).putString("installer_message", message).commit()
    }

    fun reconcileInstalledVersion() {
        if (UpdatePolicy.completedSelfUpdate(preferences.getLong("commit_from", -1), BuildConfig.VERSION_CODE.toLong())) {
            finishInstall(UpdateResult.SUCCESS)
        }
    }

    fun consumeConnectSuppression(): Boolean {
        val suppress = preferences.getBoolean("suppress_connect", false)
        if (sessionId != -1) {
            val liveSession = try { context.packageManager.packageInstaller.getSessionInfo(sessionId) != null }
                catch (_: RuntimeException) { false }
            if (!liveSession || !UpdatePolicy.isConnectGuardFresh(
                    preferences.getLong("commit_at", 0), System.currentTimeMillis())) {
                preferences.edit().remove("suppress_connect").commit()
                return false
            }
        }
        // Success has no session; its one-shot marker is consumed on this reconnect.
        preferences.edit().remove("suppress_connect").commit()
        return suppress
    }

    fun consumeManualReturn(): Boolean {
        val manual = preferences.getBoolean("manual_return", false)
        preferences.edit().remove("manual_return").commit()
        return manual
    }
}
