package com.dailynews.reader.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.newsPreferences by preferencesDataStore("dailynews")

class SettingsStore(private val context: Context) {
    private val catalogKey = intPreferencesKey("source_catalog_version")
    suspend fun catalogVersion() = context.newsPreferences.data.first()[catalogKey] ?: 0
    suspend fun markCatalogVersion(version: Int) { context.newsPreferences.edit { it[catalogKey] = version } }
    private val settingsKey = stringPreferencesKey("reader_settings")
    private val credentialKey = stringPreferencesKey("deepseek_key_encrypted")
    val settings: Flow<ReaderSettings> = context.newsPreferences.data.map { prefs ->
        prefs[settingsKey]?.let { runCatching { newsJson.decodeFromString<ReaderSettings>(it) }.getOrNull() }
            ?: ReaderSettings()
    }
    val hasKey: Flow<Boolean> = context.newsPreferences.data.map { !it[credentialKey].isNullOrBlank() }
    suspend fun upgradeAiDefaults() {
        val versionKey = intPreferencesKey("ai_defaults_version")
        context.newsPreferences.edit { prefs ->
            if ((prefs[versionKey] ?: 0) < 1) {
                val current = prefs[settingsKey]?.let { runCatching { newsJson.decodeFromString<ReaderSettings>(it) }.getOrNull() } ?: ReaderSettings()
                prefs[settingsKey] = newsJson.encodeToString(current.copy(
                    model = if (current.model == "deepseek-chat") "deepseek-flash" else current.model,
                    maxOutputTokens = if (current.maxOutputTokens == 7000) 393216 else current.maxOutputTokens,
                ))
                prefs[versionKey] = 1
            }
        }
    }
    suspend fun current() = settings.first()
    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.newsPreferences.edit { prefs ->
            val current = prefs[settingsKey]?.let {
                runCatching { newsJson.decodeFromString<ReaderSettings>(it) }.getOrNull()
            } ?: ReaderSettings()
            prefs[settingsKey] = newsJson.encodeToString(transform(current))
        }
    }
    suspend fun saveApiKey(value: String) {
        val encrypted = if (value.isBlank()) null else encrypt(value.trim())
        context.newsPreferences.edit { if (encrypted == null) it.remove(credentialKey) else it[credentialKey] = encrypted }
    }
    suspend fun apiKey(): String = context.newsPreferences.data.first()[credentialKey]?.let { decrypt(it) }.orEmpty()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("dailynews_api", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("dailynews_api", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val parts = value.split(":", limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }
}
