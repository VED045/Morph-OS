package ai.morphos.launcher.domain
import kotlinx.serialization.Serializable
import java.time.*

@Serializable enum class Trigger { TIME_WINDOW, CHARGING, BATTERY_BELOW }
@Serializable data class AutomationRule(val id: String, val trigger: Trigger, val themeId: String, val enabled: Boolean = false, val startHour: Int = 22, val endHour: Int = 6, val threshold: Int = 20, val weekdaysOnly: Boolean = false)
object AutomationEngine {
    fun validate(rule: AutomationRule): AutomationRule {
        require(Regex("[a-z][a-z0-9_-]{0,39}").matches(rule.id))
        require(Regex("[a-z][a-z0-9_-]{0,39}").matches(rule.themeId))
        require(rule.startHour in 0..23 && rule.endHour in 0..23 && rule.startHour != rule.endHour && rule.threshold in 5..95)
        return rule
    }
    fun match(rules: List<AutomationRule>, time: LocalDateTime, battery: Int, charging: Boolean): AutomationRule? = rules.firstOrNull { r ->
        r.enabled && (!r.weekdaysOnly || time.dayOfWeek.value <= 5) && when (r.trigger) {
            Trigger.TIME_WINDOW -> if (r.startHour < r.endHour) time.hour in r.startHour until r.endHour else time.hour >= r.startHour || time.hour < r.endHour
            Trigger.CHARGING -> charging
            Trigger.BATTERY_BELOW -> battery >= 0 && battery < r.threshold
        }
    }
    fun signature(rule: AutomationRule, time: LocalDateTime): String {
        val date = if (rule.trigger == Trigger.TIME_WINDOW && rule.startHour > rule.endHour && time.hour < rule.endHour) time.toLocalDate().minusDays(1) else time.toLocalDate()
        return rule.id + ":" + date
    }
}
