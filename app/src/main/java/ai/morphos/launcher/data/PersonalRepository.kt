package ai.morphos.launcher.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.provider.ContactsContract
import androidx.room.*
import ai.morphos.launcher.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Entity(tableName = "choices")
data class ChoiceRow(@PrimaryKey val id: String, val entityKey: String, val at: Long, val kind: String)
@Dao interface ChoicesDao {
    @Query("SELECT * FROM choices ORDER BY at DESC LIMIT 2000") fun observe(): Flow<List<ChoiceRow>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(events: List<ChoiceRow>)
    @Query("DELETE FROM choices WHERE id NOT IN (SELECT id FROM choices ORDER BY at DESC LIMIT 2000)") suspend fun trim()
    @Query("DELETE FROM choices") suspend fun clear()
}
@Database(entities = [ChoiceRow::class], version = 1, exportSchema = false)
abstract class PersonalDatabase : RoomDatabase() { abstract fun choices(): ChoicesDao }
@Singleton class PersonalRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val db = Room.databaseBuilder(context, PersonalDatabase::class.java, "personal.db").build()
    val history = db.choices().observe().map { list -> list.map { Choice(it.entityKey, it.at, it.kind) } }
    suspend fun record(key: String, kind: String, at: Long = System.currentTimeMillis()) {
        db.withTransaction { db.choices().insert(listOf(ChoiceRow("$kind:$key:$at", key, at, kind))); db.choices().trim() }
    }
    suspend fun clear() = db.choices().clear()
    fun usageAllowed(): Boolean = context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    suspend fun importUsage() = withContext(Dispatchers.IO) {
        if (!usageAllowed()) return@withContext
        val now = System.currentTimeMillis()
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(now - 7 * 86400000L, now)
        val event = UsageEvents.Event(); val rows = mutableListOf<ChoiceRow>(); var scanned = 0
        while (events.hasNextEvent() && scanned++ < 20000 && rows.size < 2000) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.packageName != context.packageName) rows += ChoiceRow("app:${event.packageName}:${event.timeStamp}", event.packageName, event.timeStamp, "app")
        }
        db.withTransaction { db.choices().insert(rows); db.choices().trim() }
    }
    suspend fun contacts(): List<ContactEntry> = withContext(Dispatchers.IO) {
        if (!WidgetBindings.allowed(context, Manifest.permission.READ_CONTACTS)) return@withContext emptyList()
        val names = mutableMapOf<String, ContactEntry>()
        runCatching {
            context.contentResolver.query(ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.LOOKUP_KEY, ContactsContract.Data.DISPLAY_NAME, ContactsContract.Data.STARRED, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
                "${ContactsContract.Data.MIMETYPE} IN (?,?,?)", arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE), ContactsContract.Data.DISPLAY_NAME + " ASC")?.use { c ->
                var count = 0
                while (c.moveToNext() && count++ < 10000 && names.size < 2000) {
                    val key = c.getString(1) ?: continue
                    val uri = ContactsContract.Contacts.getLookupUri(c.getLong(0), key) ?: continue
                    val old = names[key] ?: ContactEntry(key, uri.toString(), c.getString(2) ?: "Contact", emptyList(), emptyList(), c.getInt(3) == 1)
                    names[key] = when (c.getString(4)) {
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> old.copy(phones = (old.phones + (c.getString(5) ?: "")).distinct().take(10))
                        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> old.copy(nicknames = (old.nicknames + (c.getString(5) ?: "")).distinct().take(10))
                        else -> old
                    }
                }
            }
        }
        names.values.toList()
    }
}
