package ai.morphos.launcher.domain

import java.time.*
import kotlin.math.*

data class ContactEntry(val key: String, val uri: String, val name: String, val phones: List<String>, val nicknames: List<String>, val favorite: Boolean)
data class Choice(val key: String, val at: Long, val kind: String)
data class ContactResult(val contact: ContactEntry, val reason: String)
object ContactRanking {
    fun search(contacts: List<ContactEntry>, query: String, history: List<Choice>, pins: Set<String>, now: Long): List<ContactResult> {
        val q = query.trim().lowercase(); val digits = q.filter(Char::isDigit)
        val scores = contacts.mapNotNull { c ->
            val names = listOf(c.name) + c.nicknames
            val exact = names.any { it.equals(q, true) }
            val prefix = q.isNotBlank() && names.any { it.startsWith(q, true) }
            val matches = q.isBlank() || names.any { it.contains(q, true) } || (digits.isNotEmpty() && c.phones.any { it.filter(Char::isDigit).contains(digits) })
            if (!matches) return@mapNotNull null
            val choices = history.filter { it.kind == "contact" && it.key == c.key }
            val recent = choices.maxOfOrNull { it.at } ?: 0L
            val score = (if (exact) 10000 else if (prefix) 5000 else 0) + (if (c.key in pins) 2000 else 0) + (if (c.favorite) 500 else 0) + min(choices.size, 100) * 10 + (if (now - recent in 0..86400000L) 100 else 0)
            Triple(c, score, listOfNotNull(if (exact) "Exact match" else if (prefix) "Name prefix" else null, if (c.key in pins) "Pinned" else null, if (c.favorite) "Favorite" else null, if (choices.isNotEmpty()) "${choices.size} MorphOS selections" else null).joinToString(" · ").ifBlank { "Local match" })
        }
        return scores.sortedWith(compareByDescending<Triple<ContactEntry, Int, String>> { it.second }.thenBy { it.first.name.lowercase() }.thenBy { it.first.key }).map { ContactResult(it.first, it.third) }
    }
}
object AppPrediction {
    fun rank(history: List<Choice>, now: Long, hidden: Set<String> = emptySet(), zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val current = Instant.ofEpochMilli(now).atZone(zone)
        return history.filter { it.kind == "app" && it.key !in hidden && now - it.at in 0..30L * 86400000 }.groupBy { it.key }.mapNotNull { (key, events) ->
            if (events.size < 3) return@mapNotNull null
            val score = events.sumOf { event ->
                val time = Instant.ofEpochMilli(event.at).atZone(zone)
                val distance = abs(time.hour - current.hour).let { min(it, 24 - it) }
                val sameWeekend = (time.dayOfWeek.value >= 6) == (current.dayOfWeek.value >= 6)
                (if (distance <= 1) 4.0 else .4) * (if (sameWeekend) 1.2 else .7) * exp(-(now - event.at).toDouble() / (14 * 86400000.0))
            }
            if (score < 4) null else key to score
        }.sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first }).take(4).map { it.first }
    }
}
