package ai.morphos.launcher.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ai.morphos.launcher.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class RepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun personalChoiceHistoryIsPersistedAndCanBeDeleted() = runBlocking<Unit> {
        val repo = PersonalRepository(context); repo.clear()
        repo.record("stable-lookup", "contact", 12345)
        assertEquals("stable-lookup", PersonalRepository(context).history.first().single().key)
        repo.clear()
        assertTrue(repo.history.first().isEmpty())
        assertTrue(repo.contacts().isEmpty())
    }
    @Test fun promptWidgetAndInteractiveStateSurviveRecreation() = runBlocking<Unit> {
        val repo = PreferencesRepository(context); repo.reset()
        val widget = WidgetRegistry.local("Create a star 20 minute timer", "study")!!
        assertEquals(WidgetKind.TIMER, widget.kind)
        assertEquals(WidgetShape.STAR, widget.shape)
        repo.addWidget(widget, 0)
        repo.widgetAction("study")
        val restored = PreferencesRepository(context).preferences.first().widgets.first { it.id == "study" }
        assertTrue(restored.deadline > System.currentTimeMillis())
        assertEquals(1200, restored.durationSeconds)
        repo.widgetAction("counter")
        assertEquals(1, repo.preferences.first().widgets.first { it.id == "counter" }.count)
        repo.widgetAction("study")
        assertEquals(0L, repo.preferences.first().widgets.first { it.id == "study" }.deadline)
        repo.reset()
    }
    @Test fun personalWidgetDataIsEmptyWhenPermissionDenied() = runBlocking<Unit> {
        assertTrue(WidgetBindings.birthdays(context).isEmpty())
        assertTrue(WidgetBindings.calendar(context).isEmpty())
    }
    @Test fun revisionApplyUndoRedoAndStalePreviewAreAtomic() = runBlocking<Unit> {
        val repo = PreferencesRepository(context)
        repo.reset()
        val custom = FallbackTheme.value.copy(schemaVersion = 2, columns = 5)
        repo.apply(custom, 0)
        val saved = PreferencesRepository(context).preferences.first()
        assertEquals(custom, saved.customTheme)
        assertTrue(saved.canUndo)
        assertTrue(runCatching { repo.apply(custom.copy(columns = 6), 0) }.isFailure)
        assertEquals(custom, repo.preferences.first().customTheme)
        repo.travel(true)
        assertNull(repo.preferences.first().customTheme)
        assertTrue(repo.preferences.first().canRedo)
        repo.travel(false)
        assertEquals(custom, repo.preferences.first().customTheme)
        repo.theme("nothing")
        assertFalse(repo.preferences.first().canRedo)
        repo.reset()
    }
    @Test fun everyOfflineThemeValidatesAndUnknownThemeFallsBack() = runBlocking<Unit> {
        val repository = ThemeRepository(context)
        repository.load()
        assertEquals(14, repository.themes.size)
        repository.themes.forEach { ThemeValidator.validate(it) }
        assertEquals(FallbackTheme.value, repository.find("missing"))
    }
    @Test fun preferencesSurviveRepositoryRecreationAndResetIsAtomic() = runBlocking<Unit> {
        val first = PreferencesRepository(context)
        first.reset()
        first.theme("nothing")
        first.appearance(Appearance.DARK)
        first.drawer(DrawerMode.LIST)
        first.columns(5)
        first.togglePin("com.example/.Main")
        val restored = PreferencesRepository(context).preferences.first()
        assertEquals("nothing", restored.themeId)
        assertEquals(Appearance.DARK, restored.appearance)
        assertEquals(DrawerMode.LIST, restored.drawerOverride)
        assertEquals(5, restored.columnsOverride)
        assertEquals(listOf("com.example/.Main"), restored.pinned)
        first.togglePin("com.example/.Main")
        assertTrue(first.preferences.first().pinned.isEmpty())
        first.reset()
        assertEquals(LauncherPreferences(), first.preferences.first())
    }
    @Test fun themeChangeResetsOverridesButPreservesPins() = runBlocking<Unit> {
        val repository = PreferencesRepository(context)
        repository.reset()
        repository.togglePin("com.example/.Main")
        repository.columns(6)
        repository.drawer(DrawerMode.LIST)
        repository.theme("ios")
        val result = repository.preferences.first()
        assertNull(result.columnsOverride)
        assertNull(result.drawerOverride)
        assertEquals(listOf("com.example/.Main"), result.pinned)
        repository.reset()
    }
    @Test fun pinsAreBoundedAndDuplicatesToggle() = runBlocking<Unit> {
        val repository = PreferencesRepository(context)
        repository.reset()
        repeat(40) { repository.togglePin("app$it/.Main") }
        assertEquals(24, repository.preferences.first().pinned.size)
        repository.reset()
    }
    @Test fun corruptPreferenceFileRecoversToSafeDefaults() = runBlocking<Unit> {
        val file = java.io.File(context.filesDir, "datastore/launcher_v1.preferences_pb")
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        val repository = PreferencesRepository(context)
        assertEquals(LauncherPreferences(), repository.preferences.first())
        repository.theme("nothing")
        assertEquals("nothing", repository.preferences.first().themeId)
        repository.reset()
    }
}
