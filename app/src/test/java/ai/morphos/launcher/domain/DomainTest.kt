package ai.morphos.launcher.domain

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json

class DomainTest {
    @Test fun fallbackIsValidAndRoundTrips() {
        val theme = FallbackTheme.value
        assertEquals(theme, ThemeValidator.decode(Json.encodeToString(theme)))
    }
    @Test fun rejectsUnsupportedSchemaAndUnboundedLayout() {
        listOf(FallbackTheme.value.copy(schemaVersion = 3), FallbackTheme.value.copy(columns = 100),
            FallbackTheme.value.copy(dockOpacity = Float.NaN), FallbackTheme.value.copy(typography = "code"),
            FallbackTheme.value.copy(light = FallbackTheme.value.light.copy(accent = "javascript:evil"))).forEach {
            assertThrows(IllegalArgumentException::class.java) { ThemeValidator.validate(it) }
        }
    }
    @Test fun rejectsOversizedOrExecutableUnknownInput() {
        assertThrows(IllegalArgumentException::class.java) { ThemeValidator.decode(" ".repeat(8193)) }
        val raw = Json.encodeToString(FallbackTheme.value).dropLast(1) + ",\"execute\":\"shell\"}"
        assertThrows(IllegalArgumentException::class.java) { ThemeValidator.decode(raw) }
    }
    private val apps = listOf(AppEntry("1", "com.clock", "Main", "Clock"), AppEntry("2", "com.clockwork", "Main", "Clockwork"), AppEntry("3", "com.alarm", "Main", "Alarm Clock"))
    @Test fun localSearchPrioritizesExactThenPrefixAndPreservesStableIds() {
        assertEquals(listOf("1","2","3"), AppSearch.filter(apps.reversed(), "  cLoCk ").map { it.id })
        assertEquals("3", AppSearch.filter(apps, "com.alarm").single().id)
        assertTrue(AppSearch.filter(apps, "nonexistent").isEmpty())
    }
}
