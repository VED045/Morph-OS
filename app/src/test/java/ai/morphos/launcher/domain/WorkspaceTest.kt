package ai.morphos.launcher.domain

import org.junit.Assert.*
import org.junit.Test

class WorkspaceTest {
    @Test fun seedKeepsNothingWidgetsOnHomeAndOxygenWidgetsInShelf() {
        val widgets = WidgetRegistry.defaults.map { it.id }
        val apps = (0..19).map { "app$it" }
        val nothing = Workspace.seed(widgets, apps)
        val oxygen = Workspace.seed(widgets, apps, true)
        assertEquals(24, nothing.size)
        assertTrue(nothing.any { it.kind == HomeKind.WIDGET && it.page == 0 })
        assertTrue(oxygen.filter { it.kind == HomeKind.WIDGET }.all { it.page == 1 })
        Workspace.validate(nothing, 3); Workspace.validate(oxygen, 3)
    }
    @Test fun iconsSwapAndWidgetCollisionFailsWithoutOverwriting() {
        val a = HomeItem("a", HomeKind.APP, "a", 0, 0, 0)
        val b = HomeItem("b", HomeKind.APP, "b", 0, 1, 0)
        val widget = HomeItem("w", HomeKind.WIDGET, "w", 0, 2, 0, 2, 2)
        val list = listOf(a,b,widget)
        val moved = Workspace.move(list, "a", 0, 1, 0, 2)
        assertEquals(0, moved.first { it.key == "b" }.column)
        assertTrue(runCatching { Workspace.move(list, "a", 0, 2, 0, 2) }.isFailure)
        assertEquals(a, list[0])
        assertTrue(runCatching { Workspace.move(list, "w", 0, 3, 0, 2) }.isFailure)
    }
    @Test fun placementFindsEmptyCellsAndRejectsDuplicatesOrFullGrid() {
        var items = emptyList<HomeItem>()
        repeat(24) { index -> items = items + Workspace.place(items, HomeKind.APP, "a$index", 1) }
        assertTrue(runCatching { Workspace.place(items, HomeKind.APP, "overflow", 1) }.isFailure)
        assertTrue(runCatching { Workspace.place(items, HomeKind.APP, "a0", 1) }.isFailure)
        assertEquals(1, Workspace.place(items, HomeKind.APP, "next", 2, 1).page)
    }
}
