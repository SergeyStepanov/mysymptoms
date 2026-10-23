package world.freedev.mysymptoms

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

internal object EncryptedDatabase {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "symptom_journal_database_key_encryption"
    private const val PREFERENCES_NAME = "symptom_journal_encryption"
    private const val ENCRYPTED_KEY = "wrapped_database_passphrase"
    private const val GCM_IV_SIZE = 12
    private const val GCM_TAG_SIZE_BITS = 128
    private const val DATABASE_HEADER = "SQLite format 3\u0000"
    private val random = SecureRandom()

    fun initialize(context: Context, databaseName: String): String {
        System.loadLibrary("sqlcipher")
        val passphrase = getOrCreatePassphrase(context)
        migratePlaintextDatabase(context, databaseName, passphrase)
        return passphrase
    }

    private fun getOrCreatePassphrase(context: Context): String {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val wrappedKey = preferences.getString(ENCRYPTED_KEY, null)

        if (wrappedKey != null) {
            check(keyStore.containsAlias(KEY_ALIAS)) {
                "The encryption key for the symptom database is unavailable"
            }
            val combined = Base64.decode(wrappedKey, Base64.NO_WRAP)
            check(combined.size > GCM_IV_SIZE) { "The stored database key is invalid" }
            val iv = combined.copyOfRange(0, GCM_IV_SIZE)
            val ciphertext = combined.copyOfRange(GCM_IV_SIZE, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyStore.getKey(KEY_ALIAS, null),
                GCMParameterSpec(GCM_TAG_SIZE_BITS, iv)
            )
            val passphrase = String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
            check(passphrase.length == 43) { "The stored database key is invalid" }
            return passphrase
        }

        val wrappingKey = if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getKey(KEY_ALIAS, null)
        } else {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
                generateKey()
            }
        }

        val rawPassphrase = ByteArray(32).also(random::nextBytes)
        val passphrase = Base64.encodeToString(
            rawPassphrase,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        rawPassphrase.fill(0)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        val encrypted = cipher.doFinal(passphrase.toByteArray(StandardCharsets.UTF_8))
        val wrapped = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        check(
            preferences.edit()
                .putString(ENCRYPTED_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                .commit()
        ) { "Could not save the encrypted database key" }
        return passphrase
    }

    private fun migratePlaintextDatabase(context: Context, databaseName: String, passphrase: String) {
        val database = context.getDatabasePath(databaseName)
        val parent = checkNotNull(database.parentFile)
        check(parent.exists() || parent.mkdirs()) { "Could not create the database directory" }

        val staging = File(parent, "$databaseName.encrypted.tmp")
        val backup = File(parent, "$databaseName.plaintext.bak")

        if (!database.exists() && backup.exists()) {
            check(backup.renameTo(database)) { "Could not recover the journal database migration" }
        }
        if (database.exists() && !isPlaintext(database)) {
            check(staging.delete() || !staging.exists()) {
                "Could not clear the incomplete database migration"
            }
            deleteSidecars(staging)
            check(backup.delete() || !backup.exists()) {
                "Could not remove the plaintext database backup"
            }
            deleteSidecars(backup)
            return
        }
        if (!database.exists()) {
            check(staging.delete() || !staging.exists()) {
                "Could not clear the incomplete database migration"
            }
            deleteSidecars(staging)
            return
        }

        check(staging.delete() || !staging.exists()) { "Could not clear the incomplete database migration" }
        deleteSidecars(staging)

        val source = SQLiteDatabase.openDatabase(
            database.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            source.execSQL(
                "ATTACH DATABASE ? AS encrypted KEY ?",
                arrayOf(staging.absolutePath, passphrase)
            )
            source.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            source.execSQL("DETACH DATABASE encrypted")
        } finally {
            source.close()
        }

        val encrypted = SQLiteDatabase.openDatabase(
            staging.absolutePath,
            passphrase.toByteArray(StandardCharsets.UTF_8),
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null
        )
        try {
            encrypted.rawQuery("SELECT COUNT(*) FROM sqlite_master", emptyArray()).use { cursor ->
                check(cursor.moveToFirst()) { "Could not verify the encrypted journal database" }
            }
        } finally {
            encrypted.close()
        }

        deleteSidecars(database)
        check(database.renameTo(backup)) { "Could not preserve the original journal database" }
        try {
            check(staging.renameTo(database)) { "Could not install the encrypted journal database" }
        } catch (failure: Throwable) {
            if (!backup.renameTo(database)) {
                failure.addSuppressed(
                    IllegalStateException("Could not restore the original journal database")
                )
            }
            throw failure
        }
        check(backup.delete() || !backup.exists()) {
            "Could not remove the plaintext database backup"
        }
        deleteSidecars(backup)
    }

    private fun isPlaintext(database: File): Boolean {
        if (database.length() < DATABASE_HEADER.length) return false
        val header = ByteArray(DATABASE_HEADER.length)
        RandomAccessFile(database, "r").use { it.readFully(header) }
        return header.contentEquals(DATABASE_HEADER.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun deleteSidecars(database: File) {
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            val sidecar = File(database.parentFile, database.name + suffix)
            check(sidecar.delete() || !sidecar.exists()) {
                "Could not remove a temporary database file"
            }
        }
    }
}
