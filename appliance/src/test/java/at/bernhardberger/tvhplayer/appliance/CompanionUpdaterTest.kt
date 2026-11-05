package at.bernhardberger.tvhplayer.appliance

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.os.PowerManager
import at.bernhardberger.tvhplayer.appliance.update.CompanionUpdater
import at.bernhardberger.tvhplayer.appliance.update.UpdateResult
import at.bernhardberger.tvhplayer.appliance.update.UpdateStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPackageInstaller
import org.robolectric.util.ReflectionHelpers
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.management.ManagementFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CompanionUpdaterTest {
    private fun performCheck(context: Context) {
        val method = CompanionUpdater::class.java.getDeclaredMethod("performCheck",
            Context::class.java, Boolean::class.javaPrimitiveType, AtomicBoolean::class.java)
        method.isAccessible = true
        method.invoke(CompanionUpdater, context, true, AtomicBoolean(false))
    }

    @Test fun interruptedBeforeCommitAndOrphanSessionsAreAbandonedAndRetryIsAllowed() {
        val context = RuntimeEnvironment.getApplication()
        val installer = context.packageManager.packageInstaller
        fun create() = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        val recorded = create()
        val orphan = create()
        UpdateStore(context).beforeCommit(recorded, manual = false)
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        performCheck(context)
        assertNull(installer.getSessionInfo(recorded))
        assertNull(installer.getSessionInfo(orphan))
        assertEquals(-1, UpdateStore(context).sessionId)
        assertEquals(UpdateResult.INSTALL_PERMISSION, UpdateStore(context).result)
        // This second attempt is not blocked by the interrupted installation either.
        performCheck(context)
        assertEquals(UpdateResult.INSTALL_PERMISSION, UpdateStore(context).result)
    }

    @Test fun permissionIsCheckedBeforeAnyFeedOrApkDownload() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        performCheck(context) // The production feed is deliberately not contacted by this offline test.
        assertEquals(UpdateResult.INSTALL_PERMISSION, UpdateStore(context).result)
    }

    @Test fun recordedSealedSessionIsKeptButOtherSessionsAreAbandoned() {
        val context = RuntimeEnvironment.getApplication()
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val committed = installer.createSession(params)
        val orphan = installer.createSession(params)
        ReflectionHelpers.setField(installer.getSessionInfo(committed), "sealed", true)
        ReflectionHelpers.setField(installer.getSessionInfo(orphan), "sealed", true)
        UpdateStore(context).beforeCommit(committed, manual = false)
        performCheck(context)
        assertNotNull(installer.getSessionInfo(committed))
        assertNull(installer.getSessionInfo(orphan))
        assertEquals(committed, UpdateStore(context).sessionId)
        assertEquals(UpdateResult.INSTALLING, UpdateStore(context).result)
    }

    @Implements(PackageInstaller::class)
    class BrokenInstallerShadow : ShadowPackageInstaller() {
        @Implementation override fun getMySessions(): List<PackageInstaller.SessionInfo> =
            throw UnsupportedOperationException("Unavailable installer")
    }

    @Test @Config(shadows = [BrokenInstallerShadow::class])
    fun unexpectedInstallerRuntimeFailureIsContained() {
        val context = RuntimeEnvironment.getApplication()
        val installer = context.packageManager.packageInstaller
        val session = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        UpdateStore(context).beforeCommit(session, manual = false)
        performCheck(context)
        assertEquals(UpdateResult.INSTALLER_FAILURE, UpdateStore(context).result)
        assertNull(installer.getSessionInfo(session))
        assertEquals(-1, UpdateStore(context).sessionId)
        assertFalse(File(context.cacheDir, "companion-update.apk").exists())
    }

    @Test fun localStorageFailureIsNotReportedAsNoNetwork() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager).setCanRequestPackageInstalls(true)
        val apk = File(context.cacheDir, "companion-update.apk")
        apk.mkdirs()
        File(apk, "busy").writeText("test")
        try {
            performCheck(context)
            assertEquals(UpdateResult.STORAGE_FAILURE, UpdateStore(context).result)
        } finally { apk.deleteRecursively() }
    }

    private fun copy(input: InputStream, output: OutputStream, cancelled: AtomicBoolean, deadline: Long): UpdateResult {
        val method = CompanionUpdater::class.java.getDeclaredMethod("copyBounded", InputStream::class.java,
            OutputStream::class.java, Long::class.javaPrimitiveType, AtomicBoolean::class.java, Long::class.javaPrimitiveType)
        method.isAccessible = true
        val thrown = assertThrows(InvocationTargetException::class.java) {
            method.invoke(CompanionUpdater, input, output, 1024L, cancelled, deadline)
        }
        return ReflectionHelpers.getField(thrown.cause, "result")
    }

    @Test fun cancellationAfterBufferReadDoesNotWriteAndDeadlineStopsTrickle() {
        val cancelled = AtomicBoolean(false)
        val output = ByteArrayOutputStream()
        val input = object : InputStream() {
            override fun read(): Int = error("buffer reads only")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                cancelled.set(true)
                return 1
            }
        }
        assertEquals(UpdateResult.WAITING_STANDBY, copy(input, output, cancelled, Long.MAX_VALUE))
        assertEquals(0, output.size())
        assertEquals(UpdateResult.NO_NETWORK, copy(InputStream.nullInputStream(), output,
            AtomicBoolean(false), System.nanoTime() - 1))
        val brokenDisk = object : OutputStream() {
            override fun write(value: Int) { throw java.io.IOException("No space") }
        }
        assertEquals(UpdateResult.STORAGE_FAILURE, copy(byteArrayOf(1).inputStream(), brokenDisk,
            AtomicBoolean(false), Long.MAX_VALUE))
    }

    @Test fun wakeDisconnectsActiveTransferAndManualRequestRunsAfterItUnwinds() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        val entered = CountDownLatch(1)
        val unwind = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val disconnected = AtomicBoolean(false)
        val connection = object : HttpURLConnection(URL("https://example.invalid")) {
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { disconnected.set(true) }
        }
        val worker: ExecutorService = ReflectionHelpers.getStaticField(CompanionUpdater::class.java, "worker")
        val automatic = worker.submit {
            ReflectionHelpers.setStaticField(CompanionUpdater::class.java, "activeCancellation", cancelled)
            ReflectionHelpers.setStaticField(CompanionUpdater::class.java, "activeManual", false)
            ReflectionHelpers.setStaticField(CompanionUpdater::class.java, "activeConnection", connection)
            UpdateStore(context).recordCheck()
            entered.countDown()
            check(unwind.await(5, TimeUnit.SECONDS))
            ReflectionHelpers.setStaticField(CompanionUpdater::class.java, "activeConnection", null)
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            UpdateStore(context).reconcileInstalledVersion()
            assertEquals(UpdateResult.CHECKING, UpdateStore(context).result)
            CompanionUpdater.cancelAutomatic()
            assertTrue(cancelled.get())
            assertTrue(disconnected.get())
            CompanionUpdater.check(context, manual = true) { finished.countDown() }
            assertEquals(1L, finished.count) // Queued rather than silently dropped.
            unwind.countDown()
            automatic.get(5, TimeUnit.SECONDS)
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(UpdateResult.INSTALL_PERMISSION, UpdateStore(context).result)
        } finally {
            unwind.countDown()
            ReflectionHelpers.setStaticField(CompanionUpdater::class.java, "activeCancellation", null)
        }
    }

    @Test fun workerActivationBetweenReconciliationSnapshotAndRewriteWaitsForReconciliation() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        val store = UpdateStore(context)
        store.setResult(UpdateResult.CHECKING)
        val snapshotRead = CountDownLatch(1)
        val resumeRead = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val preferences = object : SharedPreferences by store.preferences {
            override fun getString(key: String?, default: String?): String? {
                val snapshot = store.preferences.getString(key, default)
                if (key == "result") {
                    snapshotRead.countDown()
                    check(resumeRead.await(5, TimeUnit.SECONDS))
                }
                return snapshot
            }
        }
        val wrapped = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
        }
        val worker: ExecutorService = ReflectionHelpers.getStaticField(CompanionUpdater::class.java, "worker")
        val workerThread = worker.submit<Thread> { Thread.currentThread() }.get(5, TimeUnit.SECONDS)
        val reconciler = Executors.newSingleThreadExecutor()
        val reconciliation = reconciler.submit { UpdateStore(wrapped).reconcileInstalledVersion() }
        try {
            assertTrue(snapshotRead.await(5, TimeUnit.SECONDS))
            // Request real production activation while reconciliation is paused after the idle snapshot.
            CompanionUpdater.check(context, manual = false) { finished.countDown() }
            val threads = ManagementFactory.getThreadMXBean()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var blockedOnUpdater = false
            while (finished.count != 0L && System.nanoTime() < deadline) {
                val info = threads.getThreadInfo(workerThread.id)
                blockedOnUpdater = info?.threadState == Thread.State.BLOCKED &&
                    info.lockInfo?.identityHashCode == System.identityHashCode(CompanionUpdater)
                if (blockedOnUpdater) break
                Thread.yield()
            }
            assertTrue("Worker activation must wait on the reconciliation monitor", blockedOnUpdater)
            resumeRead.countDown()
            reconciliation.get(5, TimeUnit.SECONDS)
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(UpdateResult.INSTALL_PERMISSION, store.result)
        } finally {
            resumeRead.countDown()
            reconciler.shutdownNow()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
        }
    }
}
