package ai.morphos.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ai.morphos.launcher.domain.AppEntry
import ai.morphos.launcher.domain.AppSearch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AppsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val cache = LruCache<String, ImageBitmap>(96)
    suspend fun discover(): List<AppEntry> = withContext(Dispatchers.IO) {
        AppSearch.filter(launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.applicationInfo.packageName != context.packageName && it.applicationInfo.enabled }
            .map { AppEntry(it.componentName.flattenToString(), it.componentName.packageName, it.componentName.className, it.label.toString(), when (it.applicationInfo.category) {
                android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Games"
                android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
                android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Maps"
                android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Images"
                else -> "Other"
            }) }, "")
    }
    suspend fun icon(app: AppEntry): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(app.id) ?: runCatching {
            val drawable = context.packageManager.getActivityIcon(ComponentName(app.packageName, app.className))
            drawable.toBitmap(128, 128).asImageBitmap().also { cache.put(app.id, it) }
        }.getOrNull()
    }
    fun launch(app: AppEntry) = launcherApps.startMainActivity(ComponentName(app.packageName, app.className), Process.myUserHandle(), null, null)
    suspend fun monochromeIcon(app: AppEntry): ImageBitmap? = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT < 33) return@withContext null
        val key = app.id + ":mono"
        cache.get(key) ?: runCatching {
            val icon = context.packageManager.getActivityIcon(ComponentName(app.packageName, app.className)) as? android.graphics.drawable.AdaptiveIconDrawable
            icon?.monochrome?.toBitmap(128, 128)?.asImageBitmap()?.also { cache.put(key, it) }
        }.getOrNull()
    }
    fun watch(callback: LauncherApps.Callback) = launcherApps.registerCallback(callback)
    fun unwatch(callback: LauncherApps.Callback) = launcherApps.unregisterCallback(callback)
    fun invalidateIcons() = cache.evictAll()
    suspend fun shortcuts(app: AppEntry): List<android.content.pm.ShortcutInfo> = withContext(Dispatchers.IO) {
        if (!launcherApps.hasShortcutHostPermission()) return@withContext emptyList()
        runCatching { launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setPackage(app.packageName).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), Process.myUserHandle()).orEmpty().filter { it.isEnabled }.take(4) }.getOrDefault(emptyList())
    }
    fun launchShortcut(shortcut: android.content.pm.ShortcutInfo) = launcherApps.startShortcut(shortcut.`package`, shortcut.id, null, null, Process.myUserHandle())
}
