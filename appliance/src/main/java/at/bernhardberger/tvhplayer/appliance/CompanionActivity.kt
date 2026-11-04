package at.bernhardberger.tvhplayer.appliance

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import at.bernhardberger.tvhplayer.ui.TvSpacing24

class CompanionActivity : ComponentActivity() {
    private var playerVersion by mutableStateOf<String?>(null)
    private var systemHome by mutableStateOf<ComponentName?>(null)
    private val homeBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, homeBack)
        refreshStatus()
        setContent {
            TVHeadendPlayerTheme {
                CompanionScreen(
                    playerVersion = playerVersion,
                    hasSystemHome = systemHome != null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(TvFullScreenPadding),
                    onOpenPlayer = ::openPlayer,
                    onOpenSettings = { openScreen(Intent(Settings.ACTION_SETTINGS)) },
                    onOpenSystemHome = {
                        systemHome?.let { component ->
                            openScreen(homeIntent().setComponent(component).addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                        }
                    },
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
        refreshStatus()
    }

    private fun routeHome(intent: Intent, hasSavedState: Boolean) {
        if (shouldOpenPlayerForHome(intent.action == Intent.ACTION_MAIN,
                intent.hasCategory(Intent.CATEGORY_HOME), hasSavedState)) openPlayer()
    }

    private fun openPlayer() {
        val launch = packageManager.getLeanbackLaunchIntentForPackage(BuildConfig.PLAYER_PACKAGE)
            ?: packageManager.getLaunchIntentForPackage(BuildConfig.PLAYER_PACKAGE)
            ?: return
        openScreen(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    private fun openScreen(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            refreshStatus()
        } catch (_: SecurityException) {
            refreshStatus()
        }
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
        homeBack.isEnabled = shouldConsumeBack(
            packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == packageName)
    }

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
}

@Composable
private fun CompanionScreen(
    playerVersion: String?,
    hasSystemHome: Boolean,
    modifier: Modifier = Modifier,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystemHome: () -> Unit,
) {
    val firstButton = remember { FocusRequester() }
    val playerInstalled = playerVersion != null
    LaunchedEffect(playerInstalled) { firstButton.requestFocus() }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(TvSpacing24),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = if (playerVersion != null) stringResource(R.string.player_installed, playerVersion)
                else stringResource(R.string.player_not_installed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (playerInstalled) {
            Button(onClick = onOpenPlayer, modifier = Modifier.focusRequester(firstButton)) {
                Text(stringResource(R.string.open_player))
            }
        }
        Button(onClick = onOpenSettings,
            modifier = if (!playerInstalled) Modifier.focusRequester(firstButton) else Modifier) {
            Text(stringResource(R.string.android_settings))
        }
        if (hasSystemHome) {
            Button(onClick = onOpenSystemHome) { Text(stringResource(R.string.google_tv_home)) }
        }
    }
}
