package at.bernhardberger.tvhplayer.appliance.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import at.bernhardberger.tvhplayer.appliance.BuildConfig
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One worker for both entry points; no Activity or Service is retained by the updater. */
object CompanionUpdater {
    private val worker = Executors.newSingleThreadExecutor()
    private var activeCancellation: AtomicBoolean? = null
    private var activeManual = false
    private var activeConnection: HttpURLConnection? = null
    @Volatile var manualForeground = false
    @Volatile private var confirmation: Intent? = null

    fun takeConfirmation(): Intent? = synchronized(this) {
        confirmation.also { confirmation = null }
    }

    fun onConfirmation(intent: Intent?) { confirmation = intent }

    fun cancel(cancelled: AtomicBoolean) {
        val connection = synchronized(this) {
            cancelled.set(true)
            if (activeCancellation === cancelled) activeConnection else null
        }
        // Do not hold the worker's state lock across a platform/socket call.
        connection?.disconnect()
    }

    fun cancelAutomatic() {
        val cancelled = synchronized(this) { if (!activeManual) activeCancellation else null }
        cancelled?.let { cancel(it) }
    }

    fun check(context: Context, manual: Boolean, cancelled: AtomicBoolean = AtomicBoolean(false), finished: () -> Unit = {}) {
        val app = context.applicationContext
        if (manual) cancelAutomatic()
        worker.execute {
            synchronized(this) {
                activeCancellation = cancelled
                activeManual = manual
            }
            try {
                performCheck(app, manual, cancelled)
            } finally {
                synchronized(this) { activeCancellation = null }
                finished()
            }
        }
    }

    @Suppress("DEPRECATION") // API 28 supports signingInfo and longVersionCode with integer flags.
    private fun performCheck(context: Context, manual: Boolean, cancelled: AtomicBoolean) {
        val store = UpdateStore(context)
        val apk = File(context.cacheDir, "companion-update.apk")
        var installer: PackageInstaller? = null
        var sessionId = -1
        var committed = false
        try {
            val power = context.getSystemService(PowerManager::class.java)
            installer = context.packageManager.packageInstaller
            // The platform seals a session on commit. A durable pre-commit marker alone
            // cannot prove that the binder call happened before our process died.
            val sessions = installer.mySessions
            val inProgress = sessions.firstOrNull { it.sessionId == store.sessionId && it.isSealed &&
                !(manual && store.result == UpdateResult.PENDING_CONFIRMATION) }
            for (session in sessions) {
                if (session.sessionId != inProgress?.sessionId) installer.abandonSession(session.sessionId)
            }
            if (inProgress != null) {
                committed = true
                return
            }
            if (store.sessionId != -1) store.finishInstall(UpdateResult.NEVER)
            if (!manual && !UpdatePolicy.shouldCheckAutomatically(store.automatic, power.isInteractive,
                    store.lastCheck, System.currentTimeMillis())) return
            if (cancelled.get()) return
            confirmation = null
            store.recordCheck()
            if (!context.packageManager.canRequestPackageInstalls()) fail(UpdateResult.INSTALL_PERMISSION)
            if (apk.exists() && !apk.delete()) fail(UpdateResult.STORAGE_FAILURE)
            // A check has at most two minutes of network work, plus one bounded socket read.
            val deadline = System.nanoTime() + 120_000_000_000L
            val release = JSONObject(readText(BuildConfig.UPDATE_FEED, cancelled, deadline))
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) fail(UpdateResult.RELEASE_PROBLEM)
            val assets = release.getJSONArray("assets")
            val names = (0 until assets.length()).map { assets.getJSONObject(it).getString("name") }
            val asset = UpdatePolicy.companionAsset(names) ?: fail(UpdateResult.RELEASE_PROBLEM)
            if (names.count { it == "SHA256SUMS" } != 1) fail(UpdateResult.RELEASE_PROBLEM)
            fun assetUrl(name: String): String = assets.getJSONObject(names.indexOf(name)).getString("browser_download_url")
            val sums = readText(assetUrl("SHA256SUMS"), cancelled, deadline)
            val expected = try { UpdatePolicy.parseChecksums(sums)[asset] }
                catch (_: IllegalArgumentException) { fail(UpdateResult.RELEASE_PROBLEM) }
            if (expected == null) fail(UpdateResult.RELEASE_PROBLEM)
            if (UpdatePolicy.isUpToDate(expected, sha256(File(context.applicationInfo.sourceDir)))) {
                store.setResult(UpdateResult.UP_TO_DATE)
                return
            }
            if (cancelled.get()) fail(UpdateResult.WAITING_STANDBY)
            apk.outputStream().use { output -> download(assetUrl(asset), output, 128L * 1024 * 1024, cancelled, deadline) }
            val actual = sha256(apk)
            if (!expected.equals(actual, ignoreCase = true)) fail(UpdateResult.HASH_MISMATCH)
            val pm = context.packageManager
            val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: fail(UpdateResult.RELEASE_PROBLEM)
            val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            UpdatePolicy.archiveRejection(expected, actual, context.packageName, archive.packageName,
                installed.longVersionCode, archive.longVersionCode, signers(installed), signers(archive))?.let { fail(it) }
            store.setLatest(archive.versionName.orEmpty().ifEmpty { archive.longVersionCode.toString() })
            if (!mayCommit(store, power, manual, cancelled)) fail(UpdateResult.WAITING_STANDBY)
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { copyBounded(it, output, Long.MAX_VALUE, cancelled) }
                    session.fsync(output)
                }
                // Recheck after all network and disk work. A wake or toggle-off must not commit an automatic install.
                if (!mayCommit(store, power, manual, cancelled)) fail(UpdateResult.WAITING_STANDBY)
                val callback = Intent(context, UpdateInstallReceiver::class.java).apply {
                    action = "${context.packageName}.UPDATE_RESULT.$sessionId"
                }
                val pending = PendingIntent.getBroadcast(context, sessionId, callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                store.beforeCommit(sessionId, manual)
                session.commit(pending.intentSender)
                committed = true
            }
        } catch (failure: UpdateFailure) {
            store.setResult(failure.result)
        } catch (_: JSONException) {
            store.setResult(UpdateResult.RELEASE_PROBLEM)
        } catch (_: IOException) {
            store.setResult(if (cancelled.get()) UpdateResult.WAITING_STANDBY else UpdateResult.STORAGE_FAILURE)
        } catch (_: RuntimeException) {
            store.setResult(if (cancelled.get()) UpdateResult.WAITING_STANDBY else UpdateResult.INSTALLER_FAILURE)
        } finally {
            apk.delete()
            val failedSession = if (sessionId != -1) sessionId else store.sessionId
            if (failedSession != -1 && !committed) {
                try { installer?.abandonSession(failedSession) } catch (_: RuntimeException) { /* Cleanup must not kill HOME. */ }
                store.finishInstall(store.result)
            }
        }
    }

    private fun mayCommit(store: UpdateStore, power: PowerManager, manual: Boolean, cancelled: AtomicBoolean): Boolean =
        !cancelled.get() && if (manual) manualForeground
        else UpdatePolicy.mayInstallAutomatically(store.automatic, power.isInteractive)

    private fun signers(info: PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners
        ?.map { digest(it.toByteArray()) }?.toSet().orEmpty()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun readText(url: String, cancelled: AtomicBoolean, deadline: Long): String = java.io.ByteArrayOutputStream().use { output ->
        download(url, output, 1024 * 1024, cancelled, deadline)
        output.toString(Charsets.UTF_8.name())
    }

    private fun download(rawUrl: String, output: OutputStream, limit: Long, cancelled: AtomicBoolean, deadline: Long) {
        var url = rawUrl
        repeat(6) { redirect ->
            checkTransfer(cancelled, deadline)
            if (!UpdatePolicy.isAllowedUrl(url, BuildConfig.DEBUG, BuildConfig.UPDATE_FEED)) fail(UpdateResult.RELEASE_PROBLEM)
            val connection = URL(url).openConnection() as HttpURLConnection
            synchronized(this) { activeConnection = connection }
            try {
                checkTransfer(cancelled, deadline)
                connection.instanceFollowRedirects = false
                val timeout = ((deadline - System.nanoTime()) / 1_000_000).coerceIn(1, 20_000).toInt()
                connection.connectTimeout = timeout
                connection.readTimeout = timeout
                connection.setRequestProperty("User-Agent", "TvheadendPlayerAppliance/${BuildConfig.VERSION_NAME}")
                when (connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        if (redirect == 5) fail(UpdateResult.RELEASE_PROBLEM)
                        val location = connection.getHeaderField("Location") ?: fail(UpdateResult.RELEASE_PROBLEM)
                        val next = URL(URL(url), location).toString()
                        if (URL(url).protocol == "https" && URL(next).protocol != "https") fail(UpdateResult.RELEASE_PROBLEM)
                        url = next
                    }
                    403, 429 -> fail(UpdateResult.RATE_LIMITED)
                    200 -> {
                        if (connection.contentLengthLong > limit) fail(UpdateResult.RELEASE_PROBLEM)
                        connection.inputStream.use { copyBounded(it, output, limit, cancelled, deadline) }
                        return
                    }
                    in 500..599 -> fail(UpdateResult.NO_NETWORK)
                    else -> fail(UpdateResult.RELEASE_PROBLEM)
                }
            } catch (_: IOException) {
                fail(if (cancelled.get()) UpdateResult.WAITING_STANDBY else UpdateResult.NO_NETWORK)
            } finally {
                synchronized(this) { activeConnection = null }
                connection.disconnect()
            }
        }
    }

    private fun checkTransfer(cancelled: AtomicBoolean, deadline: Long) {
        if (cancelled.get()) fail(UpdateResult.WAITING_STANDBY)
        if (System.nanoTime() >= deadline) fail(UpdateResult.NO_NETWORK)
    }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long,
                            cancelled: AtomicBoolean, deadline: Long = Long.MAX_VALUE) {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            checkTransfer(cancelled, deadline)
            val count = input.read(buffer)
            checkTransfer(cancelled, deadline)
            if (count == -1) return
            total += count
            if (total > limit) fail(UpdateResult.RELEASE_PROBLEM)
            try { output.write(buffer, 0, count) } catch (_: IOException) { fail(UpdateResult.STORAGE_FAILURE) }
        }
    }

    private class UpdateFailure(val result: UpdateResult) : Exception()
    private fun fail(result: UpdateResult): Nothing = throw UpdateFailure(result)
}
