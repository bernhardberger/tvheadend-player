package at.bernhardberger.tvhplayer.settings

import android.content.Context
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.legacySecureDataStore by preferencesDataStore(name = "tvhplayer_secure")

/** Read-once source used only while moving predecessor credentials into the released SDK store. */
class LegacyCredentialSource(private val context: Context) {
    private val passwordKey = stringPreferencesKey("password_enc")

    suspend fun loadPassword(): LegacyPassword = withContext(Dispatchers.Default) {
        val encoded = context.legacySecureDataStore.data.first()[passwordKey]
            ?: return@withContext LegacyPassword.Empty
        try {
            LegacyPassword.Available(decrypt(encoded))
        } catch (_: Exception) {
            LegacyPassword.Unavailable
        }
    }

    suspend fun clearCiphertext() {
        context.legacySecureDataStore.edit { it.remove(passwordKey) }
    }

    suspend fun deleteObsoleteKey() = withContext(Dispatchers.Default) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun decrypt(encoded: String): String {
        val blob = Base64.decode(encoded, Base64.NO_WRAP)
        require(blob.size > GCM_IV_SIZE_BYTES) { "Encrypted password is truncated" }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, blob.copyOfRange(0, GCM_IV_SIZE_BYTES)),
        )
        return cipher.doFinal(blob.copyOfRange(GCM_IV_SIZE_BYTES, blob.size)).toString(Charsets.UTF_8)
    }

    private companion object {
        const val KEY_ALIAS = "tvhplayer_secure_aes_key"
        const val GCM_IV_SIZE_BYTES = 12
    }
}
