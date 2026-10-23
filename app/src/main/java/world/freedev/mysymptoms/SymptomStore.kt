package world.freedev.mysymptoms

import android.content.ContentValues
import android.content.Context
import androidx.annotation.StringRes
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

data class Symptom(
    val id: String,
    @StringRes val titleResId: Int,
    val icon: String
)

data class Profile(
    val id: Long,
    val name: String?
)

data class SymptomEvent(
    val id: Long,
    val profileId: Long,
    val symptomId: String,
    val occurredAt: Long,
    val note: String
)

val symptoms = listOf(
    Symptom("headache", R.string.symptom_headache, "🤕"),
    Symptom("fatigue", R.string.symptom_fatigue, "😴"),
    Symptom("tingling", R.string.symptom_tingling, "✨"),
    Symptom("low_mood", R.string.symptom_low_mood, "🌧️"),
    Symptom("apathy", R.string.symptom_apathy, "🫥"),
    Symptom("anxiety", R.string.symptom_anxiety, "😟"),
    Symptom("pain_head", R.string.symptom_pain_head, "🧠"),
    Symptom("pain_chest", R.string.symptom_pain_chest, "🫀"),
    Symptom("pain_abdomen", R.string.symptom_pain_abdomen, "🩹"),
    Symptom("pain_back", R.string.symptom_pain_back, "🦴"),
    Symptom("pain_joints", R.string.symptom_pain_joints, "🦵")
)

fun timestampMinutesAgo(nowMillis: Long, minutesAgo: Int): Long =
    nowMillis - minutesAgo * 60_000L

class SymptomStore private constructor(
    context: Context,
    passphrase: String
) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    passphrase,
    null,
    DATABASE_VERSION,
    0,
    null,
    null,
    false
) {
    constructor(context: Context) : this(
        context.applicationContext,
        EncryptedDatabase.initialize(context.applicationContext, DATABASE_NAME)
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE symptom_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER NOT NULL,
                symptom_id TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX events_profile_time ON symptom_events(profile_id, occurred_at)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE symptom_events ADD COLUMN note TEXT NOT NULL DEFAULT ''")
        }
    }

    fun ensureInitialProfile(): List<Profile> {
        val existing = getProfiles()
        if (existing.isNotEmpty()) return existing

        val initialProfile = ContentValues().apply { putNull("name") }
        val id = writableDatabase.insertOrThrow(
            "profiles",
            null,
            initialProfile
        )
        preferences.edit().putLong(KEY_ACTIVE_PROFILE, id).apply()
        return getProfiles()
    }

    fun getProfiles(): List<Profile> {
        val result = mutableListOf<Profile>()
        readableDatabase.query(
            "profiles",
            arrayOf("id", "name"),
            null,
            null,
            null,
            null,
            "id"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Profile(
                    id = cursor.getLong(0),
                    name = cursor.getString(1)
                )
            }
        }
        return result
    }

    fun activeProfileId(profiles: List<Profile>): Long {
        val savedId = preferences.getLong(KEY_ACTIVE_PROFILE, -1L)
        return profiles.firstOrNull { it.id == savedId }?.id
            ?: profiles.first().id
    }

    fun setActiveProfile(profileId: Long) {
        preferences.edit().putLong(KEY_ACTIVE_PROFILE, profileId).apply()
    }

    fun savedLayout(): String =
        preferences.getString(KEY_LAYOUT, LAYOUT_TABLE) ?: LAYOUT_TABLE

    fun setLayout(layout: String) {
        preferences.edit().putString(KEY_LAYOUT, layout).apply()
    }

    fun addOrNameProfile(name: String, profiles: List<Profile>): Long {
        val unnamedInitialProfile = profiles.singleOrNull()?.takeIf { it.name.isNullOrBlank() }
        if (unnamedInitialProfile != null) {
            val values = ContentValues().apply { put("name", name) }
            writableDatabase.update(
                "profiles",
                values,
                "id = ?",
                arrayOf(unnamedInitialProfile.id.toString())
            )
            return unnamedInitialProfile.id
        }

        val values = ContentValues().apply { put("name", name) }
        return writableDatabase.insertOrThrow("profiles", null, values)
    }

    fun saveEvent(profileId: Long, symptomId: String, occurredAt: Long, note: String = "") {
        val values = ContentValues().apply {
            put("profile_id", profileId)
            put("symptom_id", symptomId)
            put("occurred_at", occurredAt)
            put("note", note)
        }
        writableDatabase.insertOrThrow("symptom_events", null, values)
    }

    fun updateEvent(eventId: Long, profileId: Long, occurredAt: Long, note: String) {
        val values = ContentValues().apply {
            put("occurred_at", occurredAt)
            put("note", note)
        }
        check(
            writableDatabase.update(
                "symptom_events",
                values,
                "id = ? AND profile_id = ?",
                arrayOf(eventId.toString(), profileId.toString())
            ) == 1
        ) { "Could not update symptom event $eventId" }
    }

    fun deleteEvent(eventId: Long, profileId: Long) {
        check(
            writableDatabase.delete(
                "symptom_events",
                "id = ? AND profile_id = ?",
                arrayOf(eventId.toString(), profileId.toString())
            ) == 1
        ) { "Could not delete symptom event $eventId" }
    }

    fun eventCount(profileId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM symptom_events WHERE profile_id = ?",
            arrayOf(profileId.toString())
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Could not count symptom events" }
            return cursor.getInt(0)
        }
    }

    fun eventsBetween(profileId: Long, startInclusive: Long, endExclusive: Long): List<SymptomEvent> {
        val result = mutableListOf<SymptomEvent>()
        readableDatabase.query(
            "symptom_events",
            arrayOf("id", "profile_id", "symptom_id", "occurred_at", "note"),
            "profile_id = ? AND occurred_at >= ? AND occurred_at < ?",
            arrayOf(profileId.toString(), startInclusive.toString(), endExclusive.toString()),
            null,
            null,
            "occurred_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SymptomEvent(
                    id = cursor.getLong(0),
                    profileId = cursor.getLong(1),
                    symptomId = cursor.getString(2),
                    occurredAt = cursor.getLong(3),
                    note = cursor.getString(4)
                )
            }
        }
        return result
    }

    companion object {
        internal const val DATABASE_NAME = "symptom_journal.db"
        private const val DATABASE_VERSION = 2
        private const val PREFERENCES_NAME = "symptom_journal_preferences"
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"
        const val KEY_LAYOUT = "symptom_layout"
        const val LAYOUT_LIST = "list"
        const val LAYOUT_TABLE = "table"
    }
}
