package world.freedev.mysymptoms

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import android.database.sqlite.SQLiteDatabase as PlainDatabase

@RunWith(AndroidJUnit4::class)
class EncryptedStoreTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var passphrase: String
    private val stores = mutableListOf<SymptomStore>()
    private val preferences = mutableSetOf<String>()

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "journal-test-${UUID.randomUUID()}"
        directory = File(target.cacheDir, prefix)
        check(directory.mkdir())
        context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("$prefix-$name", mode).also {
                    preferences.add("$prefix-$name")
                }
        }
        passphrase = EncryptedDatabase.initialize(context, SymptomStore.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        stores.forEach { it.close() }
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        preferences.forEach { target.deleteSharedPreferences(it) }
        if (::directory.isInitialized) assertTrue(directory.deleteRecursively())
    }

    @Test
    fun migratesVersionTwoPlaintextWithoutLosingSchemaVersionOrData() {
        createPlaintext(version = 2)

        val store = openStore()

        assertEquals(listOf(Profile(7, "Test profile")), store.getProfiles())
        assertEquals(listOf(SymptomEvent(42, 7, "headache", 100, "existing note")),
            store.eventsBetween(7, 0, 200))
        assertEquals(2, store.readableDatabase.version)
        assertEncryptedAndClean()
        assertEquals(passphrase, EncryptedDatabase.initialize(context, SymptomStore.DATABASE_NAME))
    }

    @Test
    fun migratesVersionOnePlaintextAndUpgradesNotes() {
        createPlaintext(version = 1)

        val store = openStore()

        assertEquals(listOf(SymptomEvent(42, 7, "headache", 100, "")),
            store.eventsBetween(7, 0, 200))
        store.updateEvent(42, 7, 150, "added after migration")
        assertEquals("added after migration", store.eventsBetween(7, 0, 200).single().note)
        assertEquals(2, store.readableDatabase.version)
        assertEncryptedAndClean()
    }

    @Test
    fun eventOperationsRespectProfilesBoundariesAndPersistAcrossReopen() {
        var store = openStore()
        val first = store.ensureInitialProfile().single().id
        assertEquals(first, store.addOrNameProfile("First", store.getProfiles()))
        val second = store.addOrNameProfile("Second", store.getProfiles())
        store.saveEvent(first, "headache", 99, "before")
        store.saveEvent(first, "fatigue", 100, "at start")
        store.saveEvent(first, "anxiety", 199, "It's a note")
        store.saveEvent(first, "headache", 200, "at end")
        store.saveEvent(second, "headache", 150, "another profile")
        val events = store.eventsBetween(first, 100, 200)
        assertEquals(listOf(199L, 100L), events.map { it.occurredAt })
        assertEquals(4, store.eventCount(first))
        assertEquals(1, store.eventCount(second))
        val event = events.first()
        assertThrows(IllegalStateException::class.java) {
            store.updateEvent(event.id, second, 123, "wrong profile")
        }
        assertThrows(IllegalStateException::class.java) { store.deleteEvent(event.id, second) }
        assertEquals(event, store.eventsBetween(first, 199, 200).single())
        store.updateEvent(event.id, first, 120, "updated")
        store.close()

        store = openStore()
        assertEquals(event.copy(occurredAt = 120, note = "updated"),
            store.eventsBetween(first, 120, 121).single())
        store.deleteEvent(event.id, first)
        assertEquals(3, store.eventCount(first))
        assertEquals(1, store.eventCount(second))
        assertThrows(IllegalStateException::class.java) { store.deleteEvent(event.id, first) }
    }

    @Test
    fun encryptedDatabaseRejectsWrongPassphraseAndUnknownProfile() {
        val store = openStore()
        store.ensureInitialProfile()
        assertThrows(android.database.SQLException::class.java) {
            store.saveEvent(Long.MAX_VALUE, "headache", 100)
        }
        store.close()

        assertThrows(android.database.SQLException::class.java) {
            SQLiteDatabase.openDatabase(
                context.getDatabasePath(SymptomStore.DATABASE_NAME).absolutePath,
                "wrong-passphrase".toByteArray(), null, SQLiteDatabase.OPEN_READWRITE, null
            ).use { it.rawQuery("SELECT COUNT(*) FROM profiles", emptyArray()).use { c -> c.moveToFirst() } }
        }
    }

    private fun openStore(): SymptomStore = SymptomStore(context).also(stores::add)

    private fun createPlaintext(version: Int) {
        PlainDatabase.openOrCreateDatabase(
            context.getDatabasePath(SymptomStore.DATABASE_NAME), null
        ).use { db ->
            db.execSQL("CREATE TABLE profiles (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)")
            val noteColumn = if (version >= 2) ", note TEXT NOT NULL DEFAULT ''" else ""
            db.execSQL(
                "CREATE TABLE symptom_events (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "profile_id INTEGER NOT NULL, symptom_id TEXT NOT NULL, occurred_at INTEGER NOT NULL" +
                    noteColumn + ", FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX events_profile_time ON symptom_events(profile_id, occurred_at)")
            db.execSQL("INSERT INTO profiles VALUES (7, 'Test profile')")
            if (version >= 2) {
                db.execSQL("INSERT INTO symptom_events VALUES (42, 7, 'headache', 100, 'existing note')")
            } else {
                db.execSQL("INSERT INTO symptom_events VALUES (42, 7, 'headache', 100)")
            }
            db.version = version
        }
    }

    private fun assertEncryptedAndClean() {
        val database = context.getDatabasePath(SymptomStore.DATABASE_NAME)
        val header = ByteArray(16)
        database.inputStream().use { assertEquals(header.size, it.read(header)) }
        assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray()))
        assertFalse(File(directory, "${database.name}.encrypted.tmp").exists())
        assertFalse(File(directory, "${database.name}.plaintext.bak").exists())
    }
}
