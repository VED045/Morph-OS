package ai.morphos.launcher.data

import android.content.Context
import ai.morphos.launcher.domain.FallbackTheme
import ai.morphos.launcher.domain.ThemeSpec
import ai.morphos.launcher.domain.ThemeValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ThemeRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val loaded = MutableStateFlow(listOf(FallbackTheme.value))
    val catalog = loaded.asStateFlow()
    val themes: List<ThemeSpec> get() = loaded.value
    suspend fun load() = withContext(Dispatchers.IO) {
        loaded.value = buildList {
        // A damaged asset must never prevent access to installed applications.
        val names = runCatching { context.assets.list("themes")?.sorted().orEmpty() }.getOrDefault(emptyList())
        names.filter { it.endsWith(".json") }.take(32).forEach { name ->
            runCatching { context.assets.open("themes/$name").bufferedReader().use { ThemeValidator.decode(it.readText()) } }
                .getOrNull()?.let { add(it) }
        }
        if (none { it.id == FallbackTheme.value.id }) add(FallbackTheme.value)
        }.distinctBy { it.id }
    }
    fun find(id: String): ThemeSpec = themes.firstOrNull { it.id == id } ?: FallbackTheme.value
}
