package ai.morphos.launcher.domain
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class AutomationTest {
    @Test fun overnightWindowAndDisabledRulesAreCorrect() {
        val rule = AutomationRule("night", Trigger.TIME_WINDOW, "amoled", true)
        assertEquals(rule, AutomationEngine.match(listOf(rule), LocalDateTime.parse("2026-10-12T23:00:00"), 50, false))
        assertEquals(rule, AutomationEngine.match(listOf(rule), LocalDateTime.parse("2026-10-13T01:00:00"), 50, false))
        assertNull(AutomationEngine.match(listOf(rule), LocalDateTime.parse("2026-10-13T12:00:00"), 50, false))
        assertNull(AutomationEngine.match(listOf(rule.copy(enabled = false)), LocalDateTime.parse("2026-10-12T23:00:00"), 50, false))
        assertEquals(AutomationEngine.signature(rule, LocalDateTime.parse("2026-10-12T23:00:00")), AutomationEngine.signature(rule, LocalDateTime.parse("2026-10-13T01:00:00")))
    }
    @Test fun batteryAndChargingAreBoundedAndPrioritized() {
        val rule = AutomationRule("low", Trigger.BATTERY_BELOW, "amoled", true)
        val now = LocalDateTime.parse("2026-10-12T12:00:00")
        assertNull(AutomationEngine.match(listOf(rule), now, -1, false))
        assertEquals(rule, AutomationEngine.match(listOf(rule), now, 10, false))
        assertNull(AutomationEngine.match(listOf(rule), now, 90, false))
        assertTrue(runCatching { AutomationEngine.validate(rule.copy(startHour = 30)) }.isFailure)
    }
}
