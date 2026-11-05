package at.bernhardberger.tvhplayer.appliance

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Looper
import android.os.PowerManager
import at.bernhardberger.tvhplayer.accessibility.ApplianceEntryAccessibilityService
import at.bernhardberger.tvhplayer.appliance.update.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [CompanionUpdateTest.ConnectedServiceShadow::class])
class CompanionUpdateTest {
    // Robolectric has no accessibility-system IPC connection. Supply only its service-info calls.
    @Implements(AccessibilityService::class)
    class ConnectedServiceShadow : ShadowAccessibilityService() {
        private var info = AccessibilityServiceInfo()
        @Implementation fun getServiceInfo(): AccessibilityServiceInfo = info
        @Implementation fun setServiceInfo(value: AccessibilityServiceInfo) { info = value }
    }

    @Test fun screenOffSchedulesOneNetworkJobAndScreenOnCancelsIt() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = Robolectric.buildService(ApplianceEntryAccessibilityService::class.java).create()
        val service = controller.get()
        service.serviceInfo = AccessibilityServiceInfo()
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected")
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job = scheduler.getPendingJob(CompanionUpdateJob.JOB_ID)
        assertNotNull(job)
        assertEquals(android.app.job.JobInfo.NETWORK_TYPE_ANY, job!!.networkType)
        assertEquals(ComponentName(context, CompanionUpdateJob::class.java), job.service)
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, scheduler.allPendingJobs.size)
        // Still noninteractive, so the service must not launch anything from this synthetic broadcast.
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(scheduler.getPendingJob(CompanionUpdateJob.JOB_ID))
        controller.destroy()
    }

    @Test fun connectNeverLaunchesWhileOffOrAfterUpdateButStillLaunchesAtOrdinaryEnable() {
        for ((interactive, marker, expected) in listOf(Triple(false, false, 0), Triple(true, true, 0), Triple(true, false, 1))) {
            val context = RuntimeEnvironment.getApplication()
            shadowOf(context).grantPermissions("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
            val store = UpdateStore(context)
            store.preferences.edit().clear().putBoolean("suppress_connect", marker).commit()
            shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(interactive)
            val starts = mutableListOf<Intent>()
            val controller = Robolectric.buildService(ApplianceEntryAccessibilityService::class.java).create()
            val service = controller.get()
            val wrapper = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) { starts += intent }
            }
            ReflectionHelpers.setField(service, "mBase", wrapper)
            service.serviceInfo = AccessibilityServiceInfo()
            ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected")
            assertEquals(expected, starts.size)
            controller.destroy()
        }
    }

    @Test fun stateSurvivesRestartAndVersionChangeRecordsSuccessWithoutLosingConnectGuard() {
        val context = RuntimeEnvironment.getApplication()
        val store = UpdateStore(context)
        assertTrue(store.automatic)
        store.setResult(UpdateResult.INSTALLER_FAILURE, "Cancelled by user")
        store.recordCheck()
        assertEquals(UpdateResult.CHECKING, store.result)
        assertEquals("", store.installerMessage)
        assertTrue(store.lastCheck > 0)
        store.setAutomatic(false)
        store.beforeCommit(7, manual = true)
        store.preferences.edit().putLong("commit_from", BuildConfig.VERSION_CODE - 1L).commit()
        val restarted = UpdateStore(context)
        restarted.reconcileInstalledVersion()
        assertFalse(restarted.automatic)
        assertEquals(UpdateResult.SUCCESS, restarted.result)
        assertEquals(-1, restarted.sessionId)
        assertTrue(restarted.consumeConnectSuppression())
        assertFalse(restarted.consumeConnectSuppression())
        assertTrue(restarted.consumeManualReturn())
        assertFalse(restarted.consumeManualReturn())
    }

    @Test fun installerPendingActionIsStoredNotLaunchedAndForeignSessionIsIgnored() {
        val context = RuntimeEnvironment.getApplication()
        val store = UpdateStore(context)
        store.beforeCommit(7, manual = false)
        val receiver = UpdateInstallReceiver()
        fun callback(session: Int, status: Int) = Intent().apply {
            action = "${context.packageName}.UPDATE_RESULT.$session"
            putExtra(PackageInstaller.EXTRA_SESSION_ID, session)
            putExtra(PackageInstaller.EXTRA_STATUS, status)
            putExtra(Intent.EXTRA_INTENT, Intent("confirm.test"))
        }
        receiver.onReceive(context, callback(8, PackageInstaller.STATUS_SUCCESS))
        assertEquals(UpdateResult.INSTALLING, store.result)
        receiver.onReceive(context, callback(7, PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertEquals(UpdateResult.PENDING_CONFIRMATION, UpdateStore(context).result)
        assertEquals("confirm.test", CompanionUpdater.takeConfirmation()?.action)
        receiver.onReceive(context, callback(7, PackageInstaller.STATUS_FAILURE_ABORTED).apply {
            putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "Cancelled by user")
        })
        assertEquals(UpdateResult.INSTALLER_FAILURE, store.result)
        assertEquals("Cancelled by user", store.installerMessage)
        assertFalse(store.consumeConnectSuppression())
    }

    @Test fun interruptedCheckingAndMissingOrUnsealedInstallBecomeRetryableButLiveInstallStaysBusy() {
        val context = RuntimeEnvironment.getApplication()
        val store = UpdateStore(context)
        store.recordCheck()
        UpdateStore(context).reconcileInstalledVersion()
        assertEquals(UpdateResult.NEVER, store.result)

        store.beforeCommit(9876, manual = false)
        UpdateStore(context).reconcileInstalledVersion()
        assertEquals(UpdateResult.INSTALLER_FAILURE, store.result)
        assertEquals(-1, store.sessionId)

        val installer = context.packageManager.packageInstaller
        val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        store.beforeCommit(id, manual = false)
        UpdateStore(context).reconcileInstalledVersion()
        assertEquals(UpdateResult.INSTALLER_FAILURE, store.result)

        ReflectionHelpers.setField(installer.getSessionInfo(id), "sealed", true)
        store.beforeCommit(id, manual = false)
        UpdateStore(context).reconcileInstalledVersion()
        assertEquals(UpdateResult.INSTALLING, store.result)
        assertEquals(id, store.sessionId)
    }

    @Test fun automaticConfirmationBecomesManualBeforeSuccessAndHomeRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val store = UpdateStore(context)
        store.beforeCommit(7, manual = false)
        store.setResult(UpdateResult.PENDING_CONFIRMATION)
        assertTrue(store.markManualConfirmation())
        // Both the success callback and the version-change reconciliation use finishInstall.
        UpdateStore(context).finishInstall(UpdateResult.SUCCESS)
        val controller = Robolectric.buildActivity(CompanionActivity::class.java)
        val activity = controller.get()
        val starts = mutableListOf<Intent>()
        ReflectionHelpers.setField(activity, "mBase", object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { starts += intent }
        })
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "routeHome",
            ReflectionHelpers.ClassParameter.from(Intent::class.java, home),
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, false))
        assertTrue(starts.isEmpty())
        assertFalse(UpdateStore(context).consumeManualReturn()) // routeHome consumed it, without launching.
        controller.destroy()
    }

    @Test fun missingOrOldSessionDoesNotSuppressOrdinaryConnect() {
        val context = RuntimeEnvironment.getApplication()
        val installer = context.packageManager.packageInstaller
        val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        val store = UpdateStore(context)
        store.beforeCommit(id, manual = false)
        assertTrue(store.consumeConnectSuppression())
        assertFalse(store.consumeConnectSuppression())
        store.beforeCommit(id, manual = false)
        store.preferences.edit().putLong("commit_at", System.currentTimeMillis() - UpdatePolicy.CONNECT_GUARD_MILLIS).commit()
        assertFalse(store.consumeConnectSuppression())
        store.beforeCommit(id, manual = false)
        installer.abandonSession(id)
        assertFalse(store.consumeConnectSuppression())
    }
}
