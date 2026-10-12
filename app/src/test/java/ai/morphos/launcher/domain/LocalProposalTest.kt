package ai.morphos.launcher.domain

import org.junit.Assert.*
import org.junit.Test

class LocalProposalTest {
    @Test fun localPatchIsPreviewOnlyAndUsesCapturedRevision() {
        val proposal = LocalProposal.design("""{"explanation":"Use a grid","patch":{"drawer":"GRID"}}""", FallbackTheme.value, 7, "model")
        assertEquals(7, proposal.revision); assertEquals("device", proposal.source)
        assertEquals(FallbackTheme.value.light, DesignEngine.apply(FallbackTheme.value, proposal.patch).light)
    }
    @Test fun unsupportedOrUnsafeModelOutputIsRejected() {
        listOf("""{"explanation":"Execute","patch":{"shell":"bad"}}""", """{"explanation":"Grid","patch":{"columns":90}}""", """{"explanation":"Oops","patch":{},"code":"bad"}""", "Not JSON").forEach {
            assertTrue(runCatching { LocalProposal.design(it, FallbackTheme.value, 0, "model") }.isFailure)
        }
    }
    @Test fun widgetOutputCannotChoosePersistentStateOrActions() {
        val result = LocalProposal.widget("""{"explanation":"A timer","widget":{"kind":"TIMER","title":"Focus","durationSeconds":1200}}""", 3, "model")
        assertEquals(1200, result.widget.durationSeconds); assertEquals(0L, result.widget.deadline)
        assertTrue(runCatching { LocalProposal.widget("""{"explanation":"No","widget":{"kind":"TIMER","title":"Focus","deadline":100}}""", 0, "model") }.isFailure)
    }
}
