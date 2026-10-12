package ai.morphos.launcher.data

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BoundItem(val title: String, val detail: String, val uri: Uri)
object WidgetBindings {
    fun allowed(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    suspend fun birthdays(context: Context): List<BoundItem> = withContext(Dispatchers.IO) {
        if (!allowed(context, Manifest.permission.READ_CONTACTS)) return@withContext emptyList()
        val today = LocalDate.now()
        val list = mutableListOf<Pair<LocalDate, BoundItem>>()
        runCatching {
            context.contentResolver.query(ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.LOOKUP_KEY, ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()), null)?.use { cursor ->
                var scanned = 0
                while (cursor.moveToNext() && scanned++ < 2000) {
                    val date = runCatching { MonthDay.parse("--" + cursor.getString(3).takeLast(5)) }.getOrNull() ?: continue
                    var next = date.atYear(today.year)
                    if (next < today) next = date.atYear(today.year + 1)
                    val uri = ContactsContract.Contacts.getLookupUri(cursor.getLong(0), cursor.getString(1)) ?: continue
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, next)
                    list += next to BoundItem(cursor.getString(2) ?: "Contact", if (days == 0L) "Today" else "In $days days", uri)
                }
            }
        }
        list.sortedBy { it.first }.map { it.second }.distinctBy { it.uri }.take(3)
    }
    suspend fun calendar(context: Context): List<BoundItem> = withContext(Dispatchers.IO) {
        if (!allowed(context, Manifest.permission.READ_CALENDAR)) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now); ContentUris.appendId(builder, now + 7 * 86400000L)
        val list = mutableListOf<BoundItem>()
        runCatching {
            context.contentResolver.query(builder.build(), arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN), null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                while (c.moveToNext() && list.size < 3) {
                    list += BoundItem(c.getString(1) ?: "Calendar event", Instant.ofEpochMilli(c.getLong(2)).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE HH:mm")), ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, c.getLong(0)))
                }
            }
        }
        list
    }
}
