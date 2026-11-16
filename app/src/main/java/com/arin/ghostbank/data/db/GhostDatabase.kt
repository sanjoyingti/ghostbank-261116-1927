=== ABOUT: Fixed the database so it loads the encryption library before opening, otherwise the app crashes on start ===
package com.arin.ghostbank.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Database(
    entities = [
        AccountEntity::class, RegistryEntity::class, RunEntity::class, StageLogEntity::class,
        HarvestLegEntity::class, LedgerRowEntity::class, ProxyEntity::class,
        GhostProfileEntity::class, StealthScoreEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GhostDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun registry(): RegistryDao
    abstract fun runs(): RunDao
    abstract fun stages(): StageLogDao
    abstract fun legs(): HarvestLegDao
    abstract fun ledger(): LedgerDao
    abstract fun proxies(): ProxyDao
    abstract fun profiles(): GhostProfileDao
    abstract fun scores(): StealthScoreDao

    companion object {
        @Volatile private var instance: GhostDatabase? = null

        fun get(context: Context): GhostDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): GhostDatabase {
            runCatching { System.loadLibrary("sqlcipher") }
            val passphrase = KeystorePassphrase.getOrCreate(context)
            val factory = SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))
            return Room.databaseBuilder(context, GhostDatabase::class.java, "ghostbank.db")
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}

/** Generates a random 32-byte database passphrase, wrapped with an AES key held in the Android Keystore. */
object KeystorePassphrase {
    private const val ALIAS = "ghostbank_db_key"
    private const val PREFS = "ghostbank_secure"
    private const val KEY_WRAPPED = "wrapped_passphrase"
    private const val KEY_IV = "passphrase_iv"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(KEY_WRAPPED, null)
        val iv = prefs.getString(KEY_IV, null)
        val key = secretKey()
        if (wrapped != null && iv != null) {
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
                String(cipher.doFinal(Base64.decode(wrapped, Base64.NO_WRAP)), Charsets.UTF_8)
            } catch (_: Exception) {
                randomPassphrase().also { store(prefs, it) }
            }
        }
        return randomPassphrase().also { store(prefs, it) }
    }

    private fun store(prefs: android.content.SharedPreferences, passphrase: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_WRAPPED, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun randomPassphrase(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
