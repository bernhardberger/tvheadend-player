package at.bernhardberger.tvhplayer.appliance

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.PowerManager
import at.bernhardberger.tvhplayer.appliance.update.CompanionUpdater
import at.bernhardberger.tvhplayer.appliance.update.UpdateResult
import at.bernhardberger.tvhplayer.appliance.update.UpdateStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Enable only via the existing build-time feed, e.g. -Ptvhplayer.applianceUpdateFeed=http://127.0.0.1:18764/latest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CompanionUpdaterFeedTest {
    private fun localFeed(): URI {
        val feed = URI(BuildConfig.UPDATE_FEED)
        assumeTrue("Requires the debug loopback build-time feed", BuildConfig.DEBUG &&
            feed.scheme == "http" && feed.host == "127.0.0.1" && feed.port in 1..65535)
        return feed
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun performCheck(context: Context, manual: Boolean = true, cancelled: AtomicBoolean = AtomicBoolean(false)) {
        val method = CompanionUpdater::class.java.getDeclaredMethod("performCheck",
            Context::class.java, Boolean::class.javaPrimitiveType, AtomicBoolean::class.java)
        method.isAccessible = true
        method.invoke(CompanionUpdater, context, manual, cancelled)
    }

    private fun prepareArchive(context: Context, newer: Boolean) {
        val signing = SigningInfo()
        shadowOf(signing).setSignatures(arrayOf(Signature("fixture signer".toByteArray())))
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).apply { signingInfo = signing }
        shadowOf(context.packageManager).installPackage(installed)
        shadowOf(context.packageManager).setPackageArchiveInfo(File(context.cacheDir, "companion-update.apk").path,
            PackageInfo().apply {
                packageName = context.packageName
                setLongVersionCode(installed.longVersionCode + if (newer) 1 else 0)
                versionName = "fixture"
                signingInfo = signing
            })
    }

    private fun prepare(context: Context): ByteArray {
        shadowOf(context.packageManager).setCanRequestPackageInstalls(true)
        val installed = "installed fixture APK".toByteArray()
        val file = File(context.cacheDir, "installed.apk").apply { writeBytes(installed) }
        context.applicationInfo.sourceDir = file.path
        UpdateStore(context).preferences.edit().clear().commit()
        return installed
    }

    @Test fun releaseChecksumsAndApkFlowRejectsBadHashAndAmbiguousAssetsWithoutDownloadingIdenticalApk() {
        val feed = localFeed()
        val context = RuntimeEnvironment.getApplication()
        val installed = prepare(context)
        Feed(feed).use { server ->
            server.expected = hash(installed)
            performCheck(context)
            assertEquals(UpdateResult.UP_TO_DATE, UpdateStore(context).result)
            assertEquals(0, server.count("/apk"))
            assertEquals(1, server.count("/sums"))

            val installer = context.packageManager.packageInstaller
            val stale = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
            UpdateStore(context).beforeCommit(stale, manual = false)
            server.expected = "ab".repeat(32)
            performCheck(context)
            assertEquals(UpdateResult.HASH_MISMATCH, UpdateStore(context).result)
            assertEquals(1, server.count("/apk"))
            assertFalse(File(context.cacheDir, "companion-update.apk").exists())
            assertNull(installer.getSessionInfo(stale))
            assertTrue(installer.mySessions.isEmpty())
            for (names in listOf(emptyList(), listOf("one-appliance.apk", "two-appliance.apk"))) {
                server.apkNames = names
                performCheck(context)
                assertEquals(UpdateResult.RELEASE_PROBLEM, UpdateStore(context).result)
                assertEquals(1, server.count("/apk"))
            }
            server.failure.get()?.let { throw AssertionError("Fixture server failed", it) }
        }
    }

    @Test fun wakeDuringRealDownloadDeletesPartialFileAndQueuedManualCheckCompletes() {
        val feed = localFeed()
        val context = RuntimeEnvironment.getApplication()
        val installed = prepare(context)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        Feed(feed).use { server ->
            server.expected = "ab".repeat(32)
            server.pauseApk = true
            val automaticFinished = CountDownLatch(1)
            val manualFinished = CountDownLatch(1)
            val automaticResult = AtomicReference<UpdateResult>()
            val partialRemoved = AtomicBoolean(false)
            CompanionUpdater.check(context, manual = false) {
                automaticResult.set(UpdateStore(context).result)
                partialRemoved.set(!File(context.cacheDir, "companion-update.apk").exists())
                automaticFinished.countDown()
            }
            try {
                assertTrue(server.apkStarted.await(10, TimeUnit.SECONDS))
                shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
                CompanionUpdater.cancelAutomatic()
                server.expected = hash(installed)
                CompanionUpdater.check(context, manual = true) { manualFinished.countDown() }
                assertTrue(automaticFinished.await(10, TimeUnit.SECONDS))
                assertEquals(UpdateResult.WAITING_STANDBY, automaticResult.get())
                assertTrue(partialRemoved.get())
                assertTrue(manualFinished.await(10, TimeUnit.SECONDS))
                assertEquals(UpdateResult.UP_TO_DATE, UpdateStore(context).result)
                assertEquals(1, server.count("/apk"))
            } finally {
                server.releaseApk.countDown()
                CompanionUpdater.cancelAutomatic()
            }
        }
    }

    @Test fun knownNotNewerAssetIsNotDownloadedAgainUntilInstalledApkChanges() {
        val feed = localFeed()
        val context = RuntimeEnvironment.getApplication()
        prepare(context)
        prepareArchive(context, newer = false)
        Feed(feed).use { server ->
            server.expected = hash(ByteArray(64) { 7 })
            performCheck(context)
            assertEquals(UpdateResult.NOT_NEWER, UpdateStore(context).result)
            assertEquals(server.expected, UpdateStore(context).notNewerHash)
            assertEquals(1, server.count("/apk"))
            performCheck(context)
            assertEquals(UpdateResult.NOT_NEWER, UpdateStore(context).result)
            assertEquals(1, server.count("/apk"))
            File(context.applicationInfo.sourceDir).writeText("changed installed fixture APK")
            performCheck(context)
            assertEquals(UpdateResult.NOT_NEWER, UpdateStore(context).result)
            assertEquals(2, server.count("/apk"))
            server.failure.get()?.let { throw AssertionError("Fixture server failed", it) }
        }
    }

    @Test fun wakeCancellationAndToggleOffAfterPersistenceAbandonInsteadOfCommitting() {
        val feed = localFeed()
        val context = RuntimeEnvironment.getApplication()
        val power = context.getSystemService(PowerManager::class.java)
        Feed(feed).use { server ->
            for (change in listOf("wake", "cancel", "toggle")) {
                prepare(context)
                prepareArchive(context, newer = true)
                shadowOf(power).setIsInteractive(false)
                server.expected = hash(ByteArray(64) { 7 })
                val store = UpdateStore(context)
                val cancelled = AtomicBoolean(false)
                var reachedPersistence = false
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "result" && store.result == UpdateResult.INSTALLING) {
                        reachedPersistence = true
                        when (change) {
                            "wake" -> shadowOf(power).setIsInteractive(true)
                            "cancel" -> cancelled.set(true)
                            "toggle" -> store.setAutomatic(false)
                        }
                    }
                }
                store.preferences.registerOnSharedPreferenceChangeListener(listener)
                try {
                    performCheck(context, manual = false, cancelled)
                    assertTrue("Must reach pre-commit persistence for $change", reachedPersistence)
                    assertEquals(change, UpdateResult.WAITING_STANDBY, store.result)
                    assertEquals(-1, store.sessionId)
                    assertTrue(context.packageManager.packageInstaller.mySessions.isEmpty())
                    assertFalse(File(context.cacheDir, "companion-update.apk").exists())
                } finally {
                    store.preferences.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            server.failure.get()?.let { throw AssertionError("Fixture server failed", it) }
        }
    }

    private class Feed(private val feed: URI) : Closeable {
        private val server = ServerSocket(feed.port, 8, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        private val workers = Executors.newCachedThreadPool()
        private val requests = ConcurrentHashMap<String, Int>()
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        val failure = AtomicReference<Throwable>()
        @Volatile var expected = "ab".repeat(32)
        @Volatile var apkNames = listOf("fixture-appliance.apk")
        @Volatile var pauseApk = false
        val apkStarted = CountDownLatch(1)
        val releaseApk = CountDownLatch(1)
        private val origin = "http://127.0.0.1:${feed.port}"
        private val accept = Thread {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    sockets.add(socket)
                    workers.execute {
                        try { respond(socket) } catch (error: Exception) {
                            if (!server.isClosed && !pauseApk) failure.compareAndSet(null, error)
                        } finally { sockets.remove(socket); socket.close() }
                    }
                }
            } catch (error: Exception) {
                if (!server.isClosed) failure.compareAndSet(null, error)
            }
        }.apply { isDaemon = true; start() }

        fun count(path: String): Int = requests[path] ?: 0

        private fun respond(socket: Socket) {
            socket.soTimeout = 10_000
            val reader = socket.getInputStream().bufferedReader()
            val path = reader.readLine().split(' ')[1]
            while (!reader.readLine().isNullOrEmpty()) { /* HTTP headers */ }
            requests.merge(path, 1, Int::plus)
            val body = when (path) {
                feed.rawPath -> {
                    val assets = JSONArray()
                    for (name in apkNames + "SHA256SUMS") assets.put(JSONObject()
                        .put("name", name).put("browser_download_url", origin + if (name == "SHA256SUMS") "/sums" else "/apk"))
                    JSONObject().put("assets", assets).toString().toByteArray()
                }
                "/sums" -> "$expected  fixture-appliance.apk\n".toByteArray()
                "/apk" -> ByteArray(if (pauseApk) 65536 else 64) { 7 }
                else -> error("Unexpected fixture request")
            }
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            if (path == "/apk" && pauseApk) {
                output.write(body, 0, 1024)
                output.flush()
                apkStarted.countDown()
                check(releaseApk.await(15, TimeUnit.SECONDS))
                output.write(body, 1024, body.size - 1024)
            } else output.write(body)
            output.flush()
        }

        override fun close() {
            server.close()
            releaseApk.countDown()
            sockets.forEach { it.close() }
            workers.shutdownNow()
            accept.join(1000)
            check(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
