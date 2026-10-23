package com.matan.vault.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/** Keys live in the Android Keystore; the DB passphrase is random and stored encrypted. */
object KeyManager {
    private const val PREFS = "vault_keys"
    private const val DB_KEY = "db_passphrase"

    private fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    fun securePrefs(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS,
        masterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Synchronized
    fun databasePassphrase(context: Context): ByteArray {
        val prefs = securePrefs(context)
        val existing = prefs.getString(DB_KEY, null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(DB_KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
        return bytes
    }

    fun encryptedFile(context: Context, file: File): EncryptedFile =
        EncryptedFile.Builder(
            context,
            file,
            masterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
}

/** Encrypted storage for page images. Every write gets a fresh file name. */
object FileStore {
    private fun dir(context: Context): File =
        File(context.filesDir, "vault").apply { mkdirs() }

    fun file(context: Context, name: String) = File(dir(context), name)

    fun write(context: Context, bytes: ByteArray): String {
        val name = UUID.randomUUID().toString() + ".bin"
        KeyManager.encryptedFile(context, file(context, name)).openFileOutput().use { it.write(bytes) }
        return name
    }

    fun read(context: Context, name: String): ByteArray =
        KeyManager.encryptedFile(context, file(context, name)).openFileInput().use { it.readBytes() }

    fun delete(context: Context, name: String) {
        file(context, name).delete()
    }
}
