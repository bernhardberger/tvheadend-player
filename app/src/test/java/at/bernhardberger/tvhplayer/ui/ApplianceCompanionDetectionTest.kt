package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.Signature
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.ui.screens.isApplianceCompanionInstalled
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ApplianceCompanionDetectionTest {
    private val context = RuntimeEnvironment.getApplication()

    @Suppress("DEPRECATION") // Robolectric's package fixture uses the legacy signature field.
    private fun install(packageId: String, certificate: String?) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = packageId
            signatures = certificate?.let { arrayOf(Signature(it.toByteArray())) }
        })
    }

    @Test fun missingCompanionIsNotInstalled() {
        install(context.packageName, "player")
        assertFalse(isApplianceCompanionInstalled(context))
    }

    @Test fun sameCertificateCompanionIsInstalled() {
        install(context.packageName, "player")
        install(BuildConfig.APPLIANCE_COMPANION_PACKAGE.ifEmpty { "at.bernhardberger.tvhplayer.appliance.debug" }, "player")
        if (BuildConfig.BUILD_TYPE == "profile") {
            assertTrue(BuildConfig.APPLIANCE_COMPANION_PACKAGE.isEmpty())
            assertFalse(isApplianceCompanionInstalled(context))
        } else {
            assertTrue(isApplianceCompanionInstalled(context))
        }
    }

    @Test fun signatureDetectionIsReevaluatedAfterCompanionInstallation() {
        install(context.packageName, "player")
        assertFalse(isApplianceCompanionInstalled(context))
        install(BuildConfig.APPLIANCE_COMPANION_PACKAGE.ifEmpty { "at.bernhardberger.tvhplayer.appliance.debug" }, "player")
        assertEquals(BuildConfig.APPLIANCE_COMPANION_PACKAGE.isNotEmpty(), isApplianceCompanionInstalled(context))
    }

    @Test fun differentCertificateCompanionIsNotInstalled() {
        install(context.packageName, "player")
        install(BuildConfig.APPLIANCE_COMPANION_PACKAGE.ifEmpty { "at.bernhardberger.tvhplayer.appliance.debug" }, "other")
        assertFalse(isApplianceCompanionInstalled(context))
    }

    @Test fun unsignedPackagesDoNotCountAsMatchingCertificates() {
        install(context.packageName, null)
        install(BuildConfig.APPLIANCE_COMPANION_PACKAGE.ifEmpty { "at.bernhardberger.tvhplayer.appliance.debug" }, null)
        assertFalse(isApplianceCompanionInstalled(context))
    }
}
