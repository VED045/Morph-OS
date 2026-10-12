package ai.morphos.launcher.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable enum class WidgetKind { CLOCK, BATTERY, TIMER, COUNTER, BIRTHDAYS, CALENDAR }
@Serializable enum class WidgetShape { ROUNDED, CIRCLE, HEXAGON, STAR, RECTANGLE }
@Serializable data class WidgetSpec(
    val schemaVersion: Int = 1,
    val id: String,
    val title: String,
    val kind: WidgetKind,
    val shape: WidgetShape = WidgetShape.ROUNDED,
    val durationSeconds: Int = 1500,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val deadline: Long = 0,
    val count: Int = 0
)
@Serializable data class WidgetRequest(val prompt: String, val revision: Int, val route: String = "fast")
@Serializable data class WidgetProposal(val explanation: String, val widget: WidgetSpec, val revision: Int, val source: String, val model: String, val route: String = "fast")
object WidgetRegistry {
    val defaults = listOf(
        WidgetSpec(id = "clock", title = "Local time", kind = WidgetKind.CLOCK, shape = WidgetShape.CIRCLE),
        WidgetSpec(id = "battery", title = "Device energy", kind = WidgetKind.BATTERY),
        WidgetSpec(id = "focus", title = "Focus session", kind = WidgetKind.TIMER),
        WidgetSpec(id = "counter", title = "Daily wins", kind = WidgetKind.COUNTER)
    )
    fun validate(w: WidgetSpec): WidgetSpec {
        require(w.schemaVersion == 1 && Regex("[a-z][a-z0-9_-]{0,39}").matches(w.id))
        require(w.title.length in 1..40 && w.durationSeconds in 1..86400)
        require(w.rotation.isFinite() && w.rotation in -180f..180f && w.scale.isFinite() && w.scale in .75f..1.25f)
        require(w.deadline >= 0 && w.count in 0..99999)
        return w
    }
    fun decode(raw: String): WidgetSpec { require(raw.length <= 2048); return validate(Json.decodeFromString(raw)) }
    fun local(prompt: String, id: String): WidgetSpec? {
        val text = prompt.trim().lowercase()
        val kind = when {
            "birthday" in text -> WidgetKind.BIRTHDAYS
            "calendar" in text || "meeting" in text -> WidgetKind.CALENDAR
            "battery" in text -> WidgetKind.BATTERY
            "timer" in text || "countdown" in text -> WidgetKind.TIMER
            "counter" in text || "habit" in text -> WidgetKind.COUNTER
            "clock" in text -> WidgetKind.CLOCK
            else -> return null
        }
        val shape = when { "star" in text -> WidgetShape.STAR; "hex" in text -> WidgetShape.HEXAGON; "circle" in text || "circular" in text -> WidgetShape.CIRCLE; else -> WidgetShape.ROUNDED }
        val minutes = Regex("(\\d+)\\s*(minute|min)").find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (minutes != null && minutes !in 1..1440) return null
        return validate(WidgetSpec(id = id, title = when (kind) { WidgetKind.CLOCK -> "Local time"; WidgetKind.BATTERY -> "Device energy"; WidgetKind.TIMER -> "Focus session"; WidgetKind.COUNTER -> "Daily wins"; WidgetKind.BIRTHDAYS -> "Upcoming birthdays"; WidgetKind.CALENDAR -> "Next meetings" }, kind = kind, shape = shape, durationSeconds = (minutes ?: 25) * 60))
    }
}
