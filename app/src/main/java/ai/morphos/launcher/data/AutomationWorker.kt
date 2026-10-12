package ai.morphos.launcher.data
import android.content.*
import android.os.BatteryManager
import androidx.work.*
import ai.morphos.launcher.domain.*
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class AutomationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try { evaluate(applicationContext); Result.success() } catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (enabled) manager.enqueueUniquePeriodicWork("theme_rules", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<AutomationWorker>(15, TimeUnit.MINUTES).build())
            else manager.cancelUniqueWork("theme_rules")
        }
        suspend fun evaluate(context: Context) {
            val prefs = PreferencesRepository(context); val current = prefs.preferences.first()
            val now = LocalDateTime.now()
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) > 0
            val match = AutomationEngine.match(current.rules, now, percent, charging)
            val marker = context.getSharedPreferences("automation_marker", Context.MODE_PRIVATE)
            if (match == null) { marker.edit().remove("last").apply(); return }
            val signature = AutomationEngine.signature(match, now)
            if (marker.getString("last", null) == signature) return
            val themes = ThemeRepository(context); themes.load()
            if (themes.themes.none { it.id == match.themeId }) return
            prefs.automatedTheme(match.themeId, current.revision)
            marker.edit().putString("last", signature).apply()
        }
    }
}
