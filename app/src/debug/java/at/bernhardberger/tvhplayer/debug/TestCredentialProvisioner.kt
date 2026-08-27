package at.bernhardberger.tvhplayer.debug

import android.content.Context
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import org.json.JSONObject
import java.io.File

internal data class TestCredentialPayload(
    val host: String,
    val htspPort: Int,
    val username: String,
    val password: String,
    val autoConnect: Boolean,
)

internal fun validateTestCredentialPayload(
    host: Any?,
    htspPort: Any?,
    username: Any?,
    password: Any?,
    autoConnect: Any?,
): TestCredentialPayload? {
    val normalizedHost = (host as? String)?.trim()
        ?.takeIf { it.isNotEmpty() && '\n' !in it && '\r' !in it }
        ?: return null
    val portNumber = htspPort as? Number ?: return null
    val portLong = portNumber.toLong()
    if (portNumber.toDouble() != portLong.toDouble() || portLong !in 1L..65_535L) return null
    val normalizedUsername = (username as? String)?.trim()
        ?.takeIf { it.isNotEmpty() && '\n' !in it && '\r' !in it }
        ?: return null
    val normalizedPassword = (password as? String)?.takeIf { it.isNotEmpty() } ?: return null
    val shouldAutoConnect = autoConnect as? Boolean ?: return null

    return TestCredentialPayload(
        host = normalizedHost,
        htspPort = portLong.toInt(),
        username = normalizedUsername,
        password = normalizedPassword,
        autoConnect = shouldAutoConnect,
    )
}

internal class TestCredentialProvisioner(private val context: Context) {
    suspend fun consumeStagedPayload() {
        val stagingFile = File(context.filesDir, STAGING_FILE_NAME)
        if (!stagingFile.isFile) return

        val resultFile = File(context.filesDir, RESULT_FILE_NAME)
        var result = "failed"
        try {
            val payload = readPayload(stagingFile) ?: return
            val profileStore = TvheadendServerProfileStore(context)
            ServerSettingsStore(context, profileStore).savePasswordServer(
                host = payload.host,
                htspPort = payload.htspPort,
                username = payload.username,
                password = payload.password,
                autoConnect = payload.autoConnect,
            )
            result = "ok"
        } finally {
            stagingFile.delete()
            resultFile.writeText(result, Charsets.UTF_8)
        }
    }

    private fun readPayload(file: File): TestCredentialPayload? {
        if (file.length() !in 1..MAX_PAYLOAD_BYTES) return null
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
            ?: return null
        return validateTestCredentialPayload(
            host = json.opt("host").takeUnless { it === JSONObject.NULL },
            htspPort = json.opt("htsp_port").takeUnless { it === JSONObject.NULL },
            username = json.opt("username").takeUnless { it === JSONObject.NULL },
            password = json.opt("password").takeUnless { it === JSONObject.NULL },
            autoConnect = json.opt("auto_connect").takeUnless { it === JSONObject.NULL },
        )
    }

    private companion object {
        const val STAGING_FILE_NAME = "tvhplayer_test_provisioning.json"
        const val RESULT_FILE_NAME = "tvhplayer_test_provisioning.result"
        const val MAX_PAYLOAD_BYTES = 64 * 1024L
    }
}
