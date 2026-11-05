package at.bernhardberger.tvhplayer.appliance

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.accessibility.ApplianceEntryAccessibilityService
import at.bernhardberger.tvhplayer.appliance.update.CompanionUpdater
import at.bernhardberger.tvhplayer.appliance.update.UpdateResult
import at.bernhardberger.tvhplayer.appliance.update.UpdateScreenState
import at.bernhardberger.tvhplayer.appliance.update.UpdateStore
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme

class CompanionActivity : ComponentActivity() {
    private var playerVersion by mutableStateOf<String?>(null)
    private var systemHome by mutableStateOf<ComponentName?>(null)
    private var accessibilityStatus by mutableStateOf(CompanionServiceStatus.OFF)
    private var functions by mutableStateOf(CompanionFunctions())
    private var installAllowed by mutableStateOf(false)
    private var defaultHome by mutableStateOf(false)
    private val updates by lazy { UpdateStore(this) }
    private var updateState by mutableStateOf(UpdateScreenState())
    private var manualUpdateRequested = false
    private var foreground = false
    private val updateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        Handler(Looper.getMainLooper()).post { refreshUpdates() }
    }
    private val homeBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, homeBack)
        updates.reconcileInstalledVersion()
        updates.preferences.registerOnSharedPreferenceChangeListener(updateListener)
        refreshUpdates()
        refreshStatus()
        setContent {
            TVHeadendPlayerTheme {
                CompanionScreen(
                    state = CompanionScreenState(
                        playerVersion = playerVersion,
                        accessibilityStatus = accessibilityStatus,
                        installAllowed = installAllowed,
                        defaultHome = defaultHome,
                        hasSystemHome = systemHome != null,
                        update = updateState,
                        functions = functions,
                    ),
                    onRow = ::onRow,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
        routeHome(intent, savedInstanceState != null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeHome(intent, hasSavedState = false)
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        CompanionUpdater.manualForeground = true
        updates.reconcileInstalledVersion()
        refreshUpdates()
        refreshStatus()
    }

    override fun onPause() {
        foreground = false
        manualUpdateRequested = false
        CompanionUpdater.manualForeground = false
        super.onPause()
    }

    override fun onDestroy() {
        updates.preferences.unregisterOnSharedPreferenceChangeListener(updateListener)
        super.onDestroy()
    }

    override fun onStop() {
        CompanionServiceState.cancelCapture()
        super.onStop()
    }

    private fun refreshUpdates() {
        updateState = UpdateScreenState(updates.automatic, updates.latestVersion, updates.lastCheck,
            updates.result, updates.installerMessage)
        if (foreground && manualUpdateRequested && updates.result == UpdateResult.PENDING_CONFIRMATION) {
            CompanionUpdater.takeConfirmation()?.let {
                manualUpdateRequested = false
                if (updates.markManualConfirmation()) openScreen(it)
                else updates.setResult(UpdateResult.INSTALLER_FAILURE)
            }
        }
    }

    private fun updateNow() {
        manualUpdateRequested = true
        if (updates.result == UpdateResult.PENDING_CONFIRMATION) {
            CompanionUpdater.takeConfirmation()?.let {
                manualUpdateRequested = false
                if (updates.markManualConfirmation()) openScreen(it)
                else updates.setResult(UpdateResult.INSTALLER_FAILURE)
                return
            }
        }
        // If Android reclaimed a pending confirmation, a fresh session supplies a fresh confirmation intent.
        CompanionUpdater.check(this, manual = true)
    }

    private fun routeHome(intent: Intent, hasSavedState: Boolean) {
        if (updates.consumeManualReturn()) return
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        if (shouldOpenPlayerForHome(intent.action == Intent.ACTION_MAIN,
                intent.hasCategory(Intent.CATEGORY_HOME), hasSavedState)) openPlayer()
    }

    private fun openPlayer() {
        val launch = packageManager.getLeanbackLaunchIntentForPackage(BuildConfig.PLAYER_PACKAGE)
            ?: packageManager.getLaunchIntentForPackage(BuildConfig.PLAYER_PACKAGE)
            ?: return
        openScreen(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    private fun openScreen(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        refreshStatus()
        false
    } catch (_: SecurityException) {
        refreshStatus()
        false
    }

    /** Opens the first intent the system accepts; Settings screens differ between TV builds. */
    private fun openFirst(vararg intents: Intent) {
        intents.any { openScreen(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun onRow(row: CompanionRow) {
        refreshStatus()
        when (row) {
            CompanionRow.PLAYER ->
                if (playerVersion != null) openPlayer()
                else openFirst(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${BuildConfig.PLAYER_PACKAGE}")),
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.PLAYER_PACKAGE}")),
                )
            CompanionRow.ACCESSIBILITY -> openFirst(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS))
            CompanionRow.HOME -> openFirst(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            CompanionRow.SYSTEM_HOME -> systemHome?.let { component ->
                openScreen(homeIntent().setComponent(component).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            }
            CompanionRow.UNKNOWN_APPS -> openFirst(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_SETTINGS),
            )
            CompanionRow.AUTOMATIC -> updates.setAutomatic(!updates.automatic)
            CompanionRow.UPDATE -> updateNow()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val component = ComponentName(this, ApplianceEntryAccessibilityService::class.java)
        return Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    @Suppress("DEPRECATION") // Integer flags also support the minimum API 28.
    private fun refreshStatus() {
        playerVersion = try {
            packageManager.getPackageInfo(BuildConfig.PLAYER_PACKAGE, 0).versionName.orEmpty()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val home = homeIntent()
        systemHome = packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .filter { it.packageName != packageName && it.exported && it.enabled }
            .filter { it.applicationInfo.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .sortedWith(compareByDescending<android.content.pm.ActivityInfo> {
                it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            }.thenBy { it.packageName }.thenBy { it.name })
            .firstOrNull()?.let { ComponentName(it.packageName, it.name) }
        accessibilityStatus = companionServiceStatus(isAccessibilityServiceEnabled(), CompanionServiceState.connected.value)
        functions = FunctionStore(this).functions
        installAllowed = packageManager.canRequestPackageInstalls()
        defaultHome = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
        homeBack.isEnabled = shouldConsumeBack(defaultHome)
    }

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
}
