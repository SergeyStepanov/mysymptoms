package world.freedev.mysymptoms

import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SymptomStoreTest {
    private val database = mock(SQLiteDatabase::class.java)
    private lateinit var store: SymptomStore

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("symptom_journal_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = spy(SymptomStore(context, "test-passphrase"))
        doReturn(database).`when`(store).writableDatabase
        doReturn(database).`when`(store).readableDatabase
    }

    @Test
    fun saveEventBindsAllFieldsRatherThanInterpolatingNote() {
        val note = "It's a note'); DELETE FROM symptom_events; --"

        store.saveEvent(7, "headache", 123_456, note)

        val values = ArgumentCaptor.forClass(ContentValues::class.java)
        verify(database).insertOrThrow(eq("symptom_events"), isNull(), values.capture())
        assertEquals(7L, values.value.getAsLong("profile_id"))
        assertEquals("headache", values.value.getAsString("symptom_id"))
        assertEquals(123_456L, values.value.getAsLong("occurred_at"))
        assertEquals(note, values.value.getAsString("note"))
    }

    @Test
    fun saveEventDefaultsToEmptyNote() {
        store.saveEvent(7, "fatigue", 123)

        val values = ArgumentCaptor.forClass(ContentValues::class.java)
        verify(database).insertOrThrow(eq("symptom_events"), isNull(), values.capture())
        assertEquals("", values.value.getAsString("note"))
    }

    @Test
    fun insertFailureIsPropagated() {
        val failure = IllegalStateException("database full")
        `when`(database.insertOrThrow(eq("symptom_events"), isNull(), any()))
            .thenThrow(failure)

        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            store.saveEvent(7, "headache", 123)
        })
    }

    @Test
    fun updateIsScopedToEventAndProfileAndDoesNotChangeSymptom() {
        `when`(database.update(eq("symptom_events"), any(), anyString(), any())).thenReturn(1)

        store.updateEvent(42, 7, 456, "changed")

        val values = ArgumentCaptor.forClass(ContentValues::class.java)
        verify(database).update(
            eq("symptom_events"), values.capture(), eq("id = ? AND profile_id = ?"),
            eq(arrayOf("42", "7"))
        )
        assertEquals(2, values.value.size())
        assertEquals(456L, values.value.getAsLong("occurred_at"))
        assertEquals("changed", values.value.getAsString("note"))
    }

    @Test
    fun missingOrWrongProfileUpdateIsReported() {
        assertThrows(IllegalStateException::class.java) {
            store.updateEvent(42, 8, 456, "changed")
        }
    }

    @Test
    fun deleteIsScopedToEventAndProfile() {
        `when`(database.delete("symptom_events", "id = ? AND profile_id = ?", arrayOf("42", "7")))
            .thenReturn(1)

        store.deleteEvent(42, 7)

        verify(database).delete("symptom_events", "id = ? AND profile_id = ?", arrayOf("42", "7"))
    }

    @Test
    fun missingOrWrongProfileDeleteIsReported() {
        assertThrows(IllegalStateException::class.java) { store.deleteEvent(42, 8) }
    }

    @Test
    fun eventsQueryUsesHalfOpenIntervalAndNewestFirstAndMapsEveryField() {
        val cursor = MatrixCursor(arrayOf("id", "profile_id", "symptom_id", "occurred_at", "note"))
            .apply {
                addRow(arrayOf<Any>(42L, 7L, "headache", 150L, "note"))
                addRow(arrayOf<Any>(43L, 7L, "fatigue", 100L, ""))
            }
        `when`(database.query(
            "symptom_events", arrayOf("id", "profile_id", "symptom_id", "occurred_at", "note"),
            "profile_id = ? AND occurred_at >= ? AND occurred_at < ?",
            arrayOf("7", "100", "200"), null, null, "occurred_at DESC"
        )).thenReturn(cursor)

        assertEquals(
            listOf(
                SymptomEvent(42, 7, "headache", 150, "note"),
                SymptomEvent(43, 7, "fatigue", 100, "")
            ),
            store.eventsBetween(7, 100, 200)
        )
        assertTrue(cursor.isClosed)
    }

    @Test
    fun countIsScopedToProfileAndClosesCursor() {
        val cursor = MatrixCursor(arrayOf("count")).apply { addRow(arrayOf(3)) }
        `when`(database.rawQuery(
            "SELECT COUNT(*) FROM symptom_events WHERE profile_id = ?", arrayOf("7")
        )).thenReturn(cursor)

        assertEquals(3, store.eventCount(7))
        assertTrue(cursor.isClosed)
    }

    @Test
    fun layoutAndActiveProfileSurviveNewStoreInstance() {
        val profiles = listOf(Profile(7, "First"), Profile(8, "Second"))
        assertEquals(SymptomStore.LAYOUT_TABLE, store.savedLayout())
        store.setLayout(SymptomStore.LAYOUT_LIST)
        store.setActiveProfile(8)

        val restored = SymptomStore(RuntimeEnvironment.getApplication(), "test-passphrase")

        assertEquals(SymptomStore.LAYOUT_LIST, restored.savedLayout())
        assertEquals(8L, restored.activeProfileId(profiles))
        assertEquals(7L, restored.activeProfileId(profiles.take(1)))
    }

    @Test
    fun versionOneUpgradeAddsNonNullNoteWithEmptyDefault() {
        store.onUpgrade(database, 1, 2)

        verify(database).execSQL("ALTER TABLE symptom_events ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}
