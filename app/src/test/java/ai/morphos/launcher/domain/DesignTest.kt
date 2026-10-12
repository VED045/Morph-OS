package ai.morphos.launcher.domain

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DesignTest {
    @Test fun minimalPatchPreservesUnchangedFields() {
        val original = FallbackTheme.value
        val next = DesignEngine.apply(original, buildJsonObject { put("monochromeIcons", true) })
        assertEquals(original.light, next.light)
        assertEquals(original.columns, next.columns)
        assertTrue(next.monochromeIcons)
        assertEquals(2, next.schemaVersion)
    }
    @Test fun rejectsCodeAndBadBounds() {
        listOf("{\"shell\":\"echo hi\"}", "{\"columns\":99}", "{\"monochromeIcons\":null}").forEach {
            assertTrue(runCatching { DesignEngine.apply(FallbackTheme.value, Json.parseToJsonElement(it).jsonObject) }.isFailure)
        }
    }
    @Test fun localCommandsAreExplicitAndUnknownPromptsNeedModel() {
        assertNull(DesignEngine.local("redesign everything", 1))
        assertEquals("local", DesignEngine.local("Use a dot clock", 5)!!.source)
    }
}
