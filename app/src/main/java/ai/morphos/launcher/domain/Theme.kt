package ai.morphos.launcher.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class DrawerMode { GRID, LIST, CATEGORIES, RADIAL, FREQUENT, ALPHABETICAL, SEARCH_FIRST }
@Serializable
enum class Appearance { SYSTEM, LIGHT, DARK }
@Serializable
data class Palette(val background: String, val surface: String, val foreground: String, val muted: String, val accent: String)
@Serializable
data class ThemeSpec(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val description: String,
    val light: Palette,
    val dark: Palette,
    val columns: Int = 4,
    val drawer: DrawerMode = DrawerMode.GRID,
    val cornerRadius: Int = 24,
    val iconRadius: Int = 16,
    val monochromeIcons: Boolean = false,
    val typography: String = "sans",
    val spacing: Int = 16,
    val dockOpacity: Float = 0.85f,
    val gradient: Boolean = true,
    val reducedMotion: Boolean = false,
    val clockStyle: String = "DIGITAL",
    val homeStyle: String = "CALM",
    val wallpaperStyle: String = "AURORA"
)

object ThemeValidator {
    private val color = Regex("#[0-9A-Fa-f]{6}")
    private val identifier = Regex("[a-z][a-z0-9_-]{0,39}")
    fun validate(theme: ThemeSpec): ThemeSpec {
        require(theme.schemaVersion in 1..2) { "Unsupported schema version" }
        require(theme.clockStyle in setOf("DIGITAL", "DOT", "STACKED"))
        require(theme.homeStyle in setOf("CALM", "GEOMETRIC", "SHELF", "ORCHARD", "ONE"))
        require(theme.wallpaperStyle in setOf("SOLID", "AURORA", "ORBS", "GRAIN"))
        require(identifier.matches(theme.id)) { "Invalid theme identifier" }
        require(theme.name.length in 1..60 && theme.description.length <= 200)
        require(theme.columns in 3..6 && theme.cornerRadius in 0..40 && theme.iconRadius in 0..28)
        require(theme.spacing in 8..24 && theme.dockOpacity.isFinite() && theme.dockOpacity in 0.2f..1f)
        require(theme.typography in setOf("sans", "mono", "serif"))
        listOf(theme.light, theme.dark).forEach { p ->
            require(listOf(p.background, p.surface, p.foreground, p.muted, p.accent).all(color::matches))
        }
        return theme
    }
    fun decode(raw: String): ThemeSpec {
        require(raw.length <= 8192) { "Theme exceeds size limit" }
        return validate(Json.decodeFromString<ThemeSpec>(raw))
    }
}

object FallbackTheme {
    val value = ThemeSpec(
        id = "oxygen", name = "Oxygen", description = "Calm spacing. A clean, balanced home.",
        light = Palette("#F3F5F8", "#FFFFFF", "#18202A", "#566273", "#285CBD"),
        dark = Palette("#101218", "#202631", "#F4F6FA", "#ADB7C6", "#C1F17A")
    )
}

@Serializable
data class LauncherPreferences(
    val themeId: String = "oxygen",
    val appearance: Appearance = Appearance.SYSTEM,
    val drawerOverride: DrawerMode? = null,
    val columnsOverride: Int? = null,
    val pinned: List<String> = emptyList(),
    val customTheme: ThemeSpec? = null,
    val revision: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val widgets: List<WidgetSpec> = WidgetRegistry.defaults,
    val predictionsEnabled: Boolean = false,
    val hiddenApps: List<String> = emptyList(),
    val contactPins: List<String> = emptyList(),
    val rules: List<AutomationRule> = emptyList(),
    val doubleTapAction: String = "controls"
)

data class AppEntry(val id: String, val packageName: String, val className: String, val label: String, val category: String = "Other")

object AppSearch {
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val key = query.trim().lowercase(java.util.Locale.ROOT)
        return apps.filter { key.isEmpty() || it.label.lowercase(java.util.Locale.ROOT).contains(key) || it.packageName.contains(key, ignoreCase = true) }
            .sortedWith(compareBy<AppEntry> { if (key.isEmpty()) 0 else when {
                it.label.equals(key, true) -> 0
                it.label.startsWith(key, true) -> 1
                else -> 2
            } }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.id })
    }
}
