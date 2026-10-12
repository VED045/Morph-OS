package ai.morphos.launcher.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import ai.morphos.launcher.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.launcherStore by preferencesDataStore(name = "launcher_v1", corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })
@Singleton
class PreferencesRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val snapshot = stringPreferencesKey("snapshot_v2")
    private val undo = stringPreferencesKey("undo_v2")
    private val redo = stringPreferencesKey("redo_v2")
    private fun history(p: Preferences, key: Preferences.Key<String>): List<LauncherPreferences> = runCatching {
        val raw = p[key] ?: "[]"
        require(raw.length <= 500000)
        Json.decodeFromString<List<LauncherPreferences>>(raw).takeLast(20)
    }.getOrDefault(emptyList())
    private fun read(p: Preferences): LauncherPreferences {
        val current = runCatching {
            val raw = p[snapshot] ?: error("Legacy")
            require(raw.length <= 24000)
            Json.decodeFromString<LauncherPreferences>(raw).also {
                it.customTheme?.let(ThemeValidator::validate)
                require(it.widgets.size <= 8 && it.widgets.map { w -> w.id }.distinct().size == it.widgets.size)
                it.widgets.forEach(WidgetRegistry::validate)
                require(it.rules.size <= 8 && it.doubleTapAction in setOf("controls", "search", "none"))
                it.rules.forEach(AutomationEngine::validate)
                require(it.pinned.size <= 24 && it.revision >= 0)
            }
        }.getOrElse {
            LauncherPreferences(
                themeId = p[stringPreferencesKey("theme_id")] ?: "oxygen",
                appearance = runCatching { Appearance.valueOf(p[stringPreferencesKey("appearance")].orEmpty()) }.getOrDefault(Appearance.SYSTEM),
                drawerOverride = runCatching { DrawerMode.valueOf(p[stringPreferencesKey("drawer")].orEmpty()) }.getOrNull(),
                columnsOverride = p[intPreferencesKey("columns")]?.takeIf { it in 3..6 },
                pinned = runCatching { Json.decodeFromString<List<String>>(p[stringPreferencesKey("pins")] ?: "[]").filter { it.length <= 300 }.distinct().take(24) }.getOrDefault(emptyList())
            )
        }
        return current.copy(canUndo = history(p, undo).isNotEmpty(), canRedo = history(p, redo).isNotEmpty())
    }
    val preferences = context.launcherStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map(::read)
    private suspend fun change(expected: Int? = null, transform: (LauncherPreferences) -> LauncherPreferences) = context.launcherStore.edit { p ->
        val current = read(p)
        require(expected == null || expected == current.revision) { "Layout changed since preview. Generate a new preview." }
        val next = transform(current).copy(revision = current.revision + 1, canUndo = false, canRedo = false)
        p[undo] = Json.encodeToString((history(p, undo) + current.copy(canUndo = false, canRedo = false)).takeLast(20))
        p.remove(redo)
        p[snapshot] = Json.encodeToString(next)
    }
    suspend fun theme(id: String) = change { it.copy(themeId = id, customTheme = null, drawerOverride = null, columnsOverride = null) }
    suspend fun apply(theme: ThemeSpec, expected: Int) = change(expected) { it.copy(customTheme = ThemeValidator.validate(theme), drawerOverride = null, columnsOverride = null) }
    suspend fun appearance(value: Appearance) = change { it.copy(appearance = value) }
    suspend fun drawer(value: DrawerMode) = change { it.copy(drawerOverride = value) }
    suspend fun columns(value: Int) { require(value in 3..6); change { it.copy(columnsOverride = value) } }
    suspend fun togglePin(id: String) { require(id.length <= 300); change { it.copy(pinned = if (id in it.pinned) it.pinned - id else (it.pinned + id).take(24)) } }
    suspend fun travel(back: Boolean) = context.launcherStore.edit { p ->
        val source = if (back) undo else redo
        val target = if (back) redo else undo
        val list = history(p, source)
        if (list.isNotEmpty()) {
            val current = read(p)
            val restored = list.last()
            restored.customTheme?.let(ThemeValidator::validate)
            p[target] = Json.encodeToString((history(p, target) + current.copy(canUndo = false, canRedo = false)).takeLast(20))
            p[source] = Json.encodeToString(list.dropLast(1))
            p[snapshot] = Json.encodeToString(restored.copy(revision = current.revision + 1, canUndo = false, canRedo = false))
        }
    }
    suspend fun addWidget(widget: WidgetSpec, expected: Int) = change(expected) {
        require(it.widgets.size < 8 && it.widgets.none { w -> w.id == widget.id })
        it.copy(widgets = it.widgets + WidgetRegistry.validate(widget))
    }
    suspend fun removeWidget(id: String) = change { it.copy(widgets = it.widgets.filterNot { w -> w.id == id }) }
    suspend fun predictions(enabled: Boolean) = change { it.copy(predictionsEnabled = enabled) }
    suspend fun hideApp(packageName: String) = change { it.copy(hiddenApps = (it.hiddenApps + packageName).distinct().take(200)) }
    suspend fun clearHidden() = change { it.copy(hiddenApps = emptyList()) }
    suspend fun pinContact(key: String) = change { it.copy(contactPins = if (key in it.contactPins) it.contactPins - key else (it.contactPins + key).take(100)) }
    suspend fun addRule(rule: AutomationRule) = change { require(it.rules.size < 8); it.copy(rules = it.rules + AutomationEngine.validate(rule)) }
    suspend fun updateRule(rule: AutomationRule) = change { it.copy(rules = it.rules.map { r -> if (r.id == rule.id) AutomationEngine.validate(rule) else r }) }
    suspend fun removeRule(id: String) = change { it.copy(rules = it.rules.filterNot { r -> r.id == id }) }
    suspend fun gesture(action: String) = change { require(action in setOf("controls", "search", "none")); it.copy(doubleTapAction = action) }
    suspend fun automatedTheme(id: String, expected: Int) = change(expected) { it.copy(themeId = id, customTheme = null, drawerOverride = null, columnsOverride = null, appearance = Appearance.DARK) }
    suspend fun updateWidget(widget: WidgetSpec) = change { it.copy(widgets = it.widgets.map { w -> if (w.id == widget.id) WidgetRegistry.validate(widget) else w }) }
    suspend fun moveWidget(id: String, direction: Int) = change { p ->
        val list = p.widgets.toMutableList(); val index = list.indexOfFirst { it.id == id }
        if (index >= 0) { val next = (index + direction).coerceIn(0, list.lastIndex); java.util.Collections.swap(list, index, next) }
        p.copy(widgets = list)
    }
    suspend fun widgetAction(id: String) = change { p -> p.copy(widgets = p.widgets.map { w -> if (w.id != id) w else when (w.kind) {
        WidgetKind.TIMER -> w.copy(deadline = if (w.deadline > System.currentTimeMillis()) 0 else System.currentTimeMillis() + w.durationSeconds * 1000L)
        WidgetKind.COUNTER -> w.copy(count = (w.count + 1).coerceAtMost(99999))
        else -> w
    } }) }
    suspend fun reset() = context.launcherStore.edit { it.clear() }
}
