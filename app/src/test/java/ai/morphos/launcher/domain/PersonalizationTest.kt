package ai.morphos.launcher.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PersonalizationTest {
    @Test fun contactRankingPreservesExactMatchAndExplainsLocalFrequency() {
        val now = 100000L
        val exact = ContactEntry("a", "uri", "Rahul", listOf("+91 123 456"), listOf("R"), false)
        val prefix = ContactEntry("b", "uri2", "Rahul Work", emptyList(), emptyList(), true)
        val history = List(10) { Choice("b", now - 1000, "contact") }
        assertEquals("a", ContactRanking.search(listOf(prefix, exact), "rahul", history, setOf("b"), now).first().contact.key)
        assertEquals("a", ContactRanking.search(listOf(exact), "123456", history, emptySet(), now).single().contact.key)
        assertEquals("a", ContactRanking.search(listOf(exact), "R", history, emptySet(), now).single().contact.key)
        assertTrue(ContactRanking.search(listOf(prefix), "rahul", history, emptySet(), now).single().reason.contains("10 MorphOS"))
    }
    @Test fun predictionHandlesMidnightColdStartAndDismissal() {
        val zone = ZoneId.of("UTC"); val now = Instant.parse("2026-10-12T00:00:00Z").toEpochMilli()
        val evening = (1..5).map { Choice("clock", now - it * 86400000L - 3600000L, "app") }
        assertEquals(listOf("clock"), AppPrediction.rank(evening, now, zone = zone))
        assertTrue(AppPrediction.rank(evening.take(2), now, zone = zone).isEmpty())
        assertTrue(AppPrediction.rank(evening, now, setOf("clock"), zone).isEmpty())
        assertTrue(AppPrediction.rank(listOf(Choice("clock", now + 1, "app")), now, zone = zone).isEmpty())
    }
}
