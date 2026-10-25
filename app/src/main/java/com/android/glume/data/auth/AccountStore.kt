package com.android.glume.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** An Immich account signed in on this TV. The API key itself is stored encrypted, separately. */
@Immutable
@Serializable
data class Account(
    val id: String,
    val serverUrl: String,
    val userId: String,
    val name: String,
    val email: String,
    val avatarColor: String = "primary",
    val hasProfileImage: Boolean = false,
    val apiKeyId: String = "",
)

private val Context.accountStore by preferencesDataStore(name = "accounts")

/**
 * Accounts on this TV. Each account's API key is encrypted with an AES-GCM key that lives in the
 * Android Keystore (hardware-backed where available) and can't be exported.
 */
class AccountStore(private val context: Context, scope: CoroutineScope) {
    private val listKey = stringPreferencesKey("accounts")
    private fun secretKey(id: String) = stringPreferencesKey("key:$id")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Account.serializer())

    /** Null until the first read finishes. */
    val accounts: StateFlow<List<Account>?> = context.accountStore.data
        .map { p -> p[listKey]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun add(account: Account, apiKey: String) {
        context.accountStore.edit { p ->
            val cur = p[listKey]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
            p[listKey] = json.encodeToString(serializer, cur.filter { it.id != account.id } + account)
            p[secretKey(account.id)] = Crypto.encrypt(apiKey)
        }
    }

    /** Forgets the account on this TV. The key stays valid on the server until revoked there. */
    suspend fun remove(id: String) {
        context.accountStore.edit { p ->
            val cur = p[listKey]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
            p[listKey] = json.encodeToString(serializer, cur.filter { it.id != id })
            p.remove(secretKey(id))
        }
    }

    suspend fun apiKey(id: String): String? =
        context.accountStore.data.first()[secretKey(id)]?.let { runCatching { Crypto.decrypt(it) }.getOrNull() }

    private object Crypto {
        private const val ALIAS = "glume_account_keys"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        private fun key(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return gen.generateKey()
        }

        fun encrypt(plain: String): String {
            val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val out = c.iv + c.doFinal(plain.toByteArray())
            return Base64.encodeToString(out, Base64.NO_WRAP)
        }

        fun decrypt(stored: String): String {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12)) }
            return String(c.doFinal(bytes, 12, bytes.size - 12))
        }
    }
}
