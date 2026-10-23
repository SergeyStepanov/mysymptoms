package world.freedev.mysymptoms

import android.content.Context
import android.database.MatrixCursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptedDatabaseTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val context = mock(Context::class.java)
    private val source = mock(SQLiteDatabase::class.java)
    private val encrypted = mock(SQLiteDatabase::class.java)
    private lateinit var database: File
    private lateinit var staging: File
    private lateinit var backup: File
    private val plaintext = "SQLite format 3\u0000original".toByteArray()
    private val ciphertext = byteArrayOf(42, 71, 12, 99)

    @Before
    fun setUp() {
        database = File(temporaryFolder.root, "journal.db")
        staging = File(temporaryFolder.root, "journal.db.encrypted.tmp")
        backup = File(temporaryFolder.root, "journal.db.plaintext.bak")
        `when`(context.getDatabasePath("journal.db")).thenReturn(database)
    }

    @Test
    fun missingDatabaseRemovesIncompleteExportAndSidecars() {
        staging.writeText("incomplete")
        val sidecars = createSidecars(staging)

        migrate()

        assertFalse(database.exists())
        assertFalse(staging.exists())
        sidecars.forEach { assertFalse(it.exists()) }
    }

    @Test
    fun encryptedDatabaseIsNotExportedAgainAndPlaintextArtifactsAreRemoved() {
        database.writeBytes(ciphertext)
        backup.writeBytes(plaintext)
        staging.writeText("incomplete")
        val sidecars = createSidecars(staging) + createSidecars(backup)

        mockStatic(SQLiteDatabase::class.java).use { sqlcipher ->
            migrate()
            sqlcipher.verifyNoInteractions()
        }

        assertArrayEquals(ciphertext, database.readBytes())
        assertFalse(backup.exists())
        assertFalse(staging.exists())
        sidecars.forEach { assertFalse(it.exists()) }
    }

    @Test
    fun plaintextIsExportedVerifiedAndReplaced() {
        database.writeBytes(plaintext)
        val sidecars = createSidecars(database)

        withExport {
            migrate()
        }

        assertArrayEquals(ciphertext, database.readBytes())
        assertFalse(backup.exists())
        assertFalse(staging.exists())
        sidecars.forEach { assertFalse(it.exists()) }
        verify(source).execSQL(
            "ATTACH DATABASE ? AS encrypted KEY ?",
            arrayOf(staging.absolutePath, "test-passphrase")
        )
        verify(source).rawExecSQL("SELECT sqlcipher_export('encrypted')")
        verify(source).execSQL("DETACH DATABASE encrypted")
        verify(source).close()
        verify(encrypted).version = 2
        verify(encrypted).close()
    }

    @Test
    fun interruptedRenameRecoversBackupBeforeRetryingExport() {
        backup.writeBytes(plaintext)
        staging.writeText("incomplete")

        withExport { migrate() }

        assertArrayEquals(ciphertext, database.readBytes())
        assertFalse(backup.exists())
        assertFalse(staging.exists())
    }

    @Test
    fun failedExportPreservesOriginalAndClosesSource() {
        database.writeBytes(plaintext)
        val failure = IllegalStateException("export failed")
        doThrow(failure).`when`(source).rawExecSQL("SELECT sqlcipher_export('encrypted')")

        withExport {
            assertSame(failure, assertThrows(IllegalStateException::class.java) { migrate() })
        }

        assertArrayEquals(plaintext, database.readBytes())
        assertFalse(backup.exists())
        verify(source).close()
        verifyNoInteractions(encrypted)
    }

    @Test
    fun failedVerificationPreservesOriginalAndClosesBothDatabases() {
        database.writeBytes(plaintext)

        withExport(verified = false) {
            assertThrows(IllegalStateException::class.java) { migrate() }
        }

        assertArrayEquals(plaintext, database.readBytes())
        assertFalse(backup.exists())
        verify(source).close()
        verify(encrypted).close()
    }

    @Test
    fun failedInstallationRestoresOriginalFromBackup() {
        database.writeBytes(plaintext)

        withExport {
            doAnswer {
                assertTrue(staging.delete())
                null
            }.`when`(encrypted).close()

            assertThrows(IllegalStateException::class.java) { migrate() }
        }

        assertArrayEquals(plaintext, database.readBytes())
        assertFalse(backup.exists())
    }

    @Test
    fun failureToClearStagingIsReportedWithoutTouchingOriginal() {
        database.writeBytes(plaintext)
        assertTrue(staging.mkdir())
        File(staging, "blocked").writeText("occupied")

        assertThrows(IllegalStateException::class.java) { migrate() }

        assertArrayEquals(plaintext, database.readBytes())
        verifyNoInteractions(source, encrypted)
    }

    private fun migrate() =
        EncryptedDatabase.migratePlaintextDatabase(context, "journal.db", "test-passphrase")

    private fun createSidecars(file: File): List<File> =
        listOf("-wal", "-shm", "-journal").map { suffix ->
            File(file.parentFile, file.name + suffix).apply { writeText("temporary") }
        }

    private fun withExport(verified: Boolean = true, block: () -> Unit) {
        mockStatic(SQLiteDatabase::class.java).use { sqlcipher ->
            sqlcipher.`when`<SQLiteDatabase> {
                SQLiteDatabase.openDatabase(
                    database.absolutePath, null,
                    SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
                )
            }.thenReturn(source)
            sqlcipher.`when`<SQLiteDatabase> {
                SQLiteDatabase.openDatabase(
                    staging.absolutePath, "test-passphrase".toByteArray(), null,
                    SQLiteDatabase.OPEN_READWRITE, null
                )
            }.thenAnswer {
                assertTrue(staging.exists())
                encrypted
            }
            `when`(source.version).thenReturn(2)
            doAnswer {
                staging.writeBytes(ciphertext)
                null
            }.`when`(source).execSQL(
                "ATTACH DATABASE ? AS encrypted KEY ?",
                arrayOf(staging.absolutePath, "test-passphrase")
            )
            val cursor = MatrixCursor(arrayOf("count")).apply {
                if (verified) addRow(arrayOf(2))
            }
            `when`(encrypted.rawQuery("SELECT COUNT(*) FROM sqlite_master", emptyArray()))
                .thenReturn(cursor)
            block()
        }
    }
}
