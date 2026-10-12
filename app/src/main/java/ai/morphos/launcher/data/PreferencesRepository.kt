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
        require(raw.length <= 2000000)
        Json.decodeFromString<List<LauncherPreferences>>(raw).takeLast(20).map(::validateSnapshot)
    }.getOrDefault(emptyList())
    private fun validateSnapshot(it: LauncherPreferences): LauncherPreferences {
        it.customTheme?.let(ThemeValidator::validate)
        require(it.widgets.size <= 8 && it.widgets.map { w -> w.id }.distinct().size == it.widgets.size)
        it.widgets.forEach(WidgetRegistry::validate)
        require(it.rules.size <= 8 && it.doubleTapAction in setOf("controls", "search", "none"))
        it.rules.forEach(AutomationEngine::validate)
        require(it.pinned.size <= 24 && it.revision >= 0)
        it.homeItems?.let { items -> Workspace.validate(items, it.homePages) }
        require(it.dockApps.size <= 4 && it.dockApps.distinct().size == it.dockApps.size)
        require(it.iconTreatment in setOf("original", "monochrome", "theme") && it.localAiMode in setOf("off", "fallback", "local"))
        require(it.homePages in 1..Workspace.maxPages)
        require(it.columnsOverride == null || it.columnsOverride in 3..6)
        require(it.pinned.all { id -> id.isNotBlank() && id.length <= 300 } && it.pinned.distinct().size == it.pinned.size)
        require(it.dockApps.all { id -> id.isNotBlank() && id.length <= 300 })
        require(it.homeItems.orEmpty().filter { item -> item.kind == HomeKind.WIDGET }.all { item -> it.widgets.any { w -> w.id == item.target } })
        return it
    }
    private fun read(p: Preferences): LauncherPreferences {
        val current = runCatching {
            val raw = p[snapshot] ?: error("Legacy")
            require(raw.length <= 100000)
            Json.decodeFromString<LauncherPreferences>(raw).also {
                validateSnapshot(it)
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
        require(current.revision < Int.MAX_VALUE)
        val next = validateSnapshot(transform(current).copy(revision = current.revision + 1, canUndo = false, canRedo = false))
        require(Json.encodeToString(next).length <= 100000)
        p[undo] = Json.encodeToString((history(p, undo) + current.copy(canUndo = false, canRedo = false)).takeLast(20))
        p.remove(redo)
        p[snapshot] = Json.encodeToString(next)
    }
    suspend fun theme(id: String) = change { p -> p.copy(themeId = id, customTheme = null, drawerOverride = null, columnsOverride = null,
        homeItems = if (p.homeItems != null && !p.workspaceCustomized) Workspace.seed(p.widgets.map { it.id }, p.homeItems.filter { it.kind == HomeKind.APP }.map { it.target }, id == "oxygen") else p.homeItems) }
    suspend fun apply(theme: ThemeSpec, expected: Int, iconTreatment: String? = null) = change(expected) { it.copy(customTheme = ThemeValidator.validate(theme), drawerOverride = null, columnsOverride = null, iconTreatment = iconTreatment ?: it.iconTreatment) }
    suspend fun appearance(value: Appearance) = change { it.copy(appearance = value) }
    suspend fun drawer(value: DrawerMode) = change { it.copy(drawerOverride = value) }
    suspend fun columns(value: Int) { require(value in 3..6); change { it.copy(columnsOverride = value) } }
    suspend fun togglePin(id: String) { require(id.length <= 300); change { p ->
        if (p.homeItems == null) p.copy(pinned = if (id in p.pinned) p.pinned - id else (p.pinned + id).take(24))
        else if (p.homeItems.any { it.kind == HomeKind.APP && it.target == id }) p.copy(homeItems = p.homeItems.filterNot { it.kind == HomeKind.APP && it.target == id }, pinned = p.pinned - id, workspaceCustomized = true)
        else p.copy(homeItems = p.homeItems + Workspace.place(p.homeItems, HomeKind.APP, id, p.homePages), pinned = (p.pinned + id).take(24), workspaceCustomized = true)
    } }
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
        it.copy(widgets = it.widgets + WidgetRegistry.validate(widget), homeItems = it.homeItems?.let { items -> items + Workspace.place(items, HomeKind.WIDGET, widget.id, it.homePages, 1) }, workspaceCustomized = true)
    }
    suspend fun removeWidget(id: String) = change { it.copy(widgets = it.widgets.filterNot { w -> w.id == id }, homeItems = it.homeItems?.filterNot { w -> w.kind == HomeKind.WIDGET && w.target == id }, workspaceCustomized = true) }
    suspend fun ensureWorkspace(apps: List<String>) = context.launcherStore.edit { p ->
        val current = read(p)
        if (current.homeItems == null) {
            val dock = apps.distinct().take(4)
            val ordered = (current.pinned + apps.filterNot { it in dock }).distinct()
            p[snapshot] = Json.encodeToString(current.copy(homeItems = Workspace.seed(current.widgets.map { it.id }, ordered, current.themeId == "oxygen"), dockApps = dock))
        }
    }
    suspend fun page(add: Boolean, index: Int) = change { p ->
        if (add) { require(p.homePages < Workspace.maxPages); p.copy(homePages = p.homePages + 1, workspaceCustomized = true) }
        else { require(p.homePages > 1 && index in 0 until p.homePages && p.homeItems.orEmpty().none { it.page == index }); p.copy(homePages = p.homePages - 1, homeItems = p.homeItems?.map { if (it.page > index) it.copy(page = it.page - 1) else it }, workspaceCustomized = true) }
    }
    suspend fun moveHome(key: String, page: Int, column: Int, row: Int) = change { p -> p.copy(homeItems = Workspace.move(p.homeItems.orEmpty(), key, page, column, row, p.homePages), workspaceCustomized = true) }
    suspend fun resizeHome(key: String, width: Int, height: Int) = change { p -> p.copy(homeItems = Workspace.validate(p.homeItems.orEmpty().map { if (it.key == key) it.copy(width = width, height = height) else it }, p.homePages), workspaceCustomized = true) }
    suspend fun restoreWidget(id: String) = change { p -> require(p.widgets.any { it.id == id }); val items = p.homeItems.orEmpty(); p.copy(homeItems = items + Workspace.place(items, HomeKind.WIDGET, id, p.homePages, preferred = 1), workspaceCustomized = true) }
    suspend fun removeHome(key: String) = change { p -> p.copy(homeItems = p.homeItems?.filterNot { it.key == key }, pinned = p.pinned.filterNot { id -> p.homeItems?.any { it.key == key && it.kind == HomeKind.APP && it.target == id } == true }, workspaceCustomized = true) }
    suspend fun dock(id: String) = change { p -> p.copy(dockApps = if (id in p.dockApps) p.dockApps - id else { require(p.dockApps.size < 4) { "Dock is full" }; p.dockApps + id }, workspaceCustomized = true) }
    suspend fun icons(value: String) = change { require(value in setOf("original", "monochrome", "theme")); it.copy(iconTreatment = value) }
    suspend fun localAi(value: String) = change { require(value in setOf("off", "fallback", "local")); it.copy(localAiMode = value) }
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
