package ai.morphos.launcher.domain

import kotlinx.serialization.Serializable

@Serializable enum class HomeKind { APP, WIDGET }
@Serializable data class HomeItem(val key: String, val kind: HomeKind, val target: String, val page: Int, val column: Int, val row: Int, val width: Int = 1, val height: Int = 1)

object Workspace {
    const val columns = 4
    const val rows = 6
    const val maxPages = 6
    fun overlaps(a: HomeItem, b: HomeItem) = a.page == b.page && a.column < b.column + b.width && a.column + a.width > b.column && a.row < b.row + b.height && a.row + a.height > b.row
    fun validate(items: List<HomeItem>, pages: Int): List<HomeItem> {
        require(pages in 1..maxPages && items.size <= 72 && items.map { it.key }.distinct().size == items.size)
        items.forEach { item ->
            require(item.key.length in 1..320 && item.target.length in 1..300 && item.page in 0 until pages)
            require(item.width in 1..4 && item.height in 1..3 && item.column >= 0 && item.row >= 0 && item.column + item.width <= columns && item.row + item.height <= rows)
            require(item.kind != HomeKind.APP || item.width == 1 && item.height == 1)
            require(items.none { it.key != item.key && overlaps(item, it) }) { "That space is occupied" }
        }
        return items
    }
    fun place(items: List<HomeItem>, kind: HomeKind, target: String, pages: Int, preferred: Int = 0, width: Int = if (kind == HomeKind.WIDGET) 2 else 1, height: Int = if (kind == HomeKind.WIDGET) 2 else 1): HomeItem {
        require(items.none { it.kind == kind && it.target == target }) { "Already on home" }
        val order = listOf(preferred.coerceIn(0, pages - 1)) + (0 until pages).filter { it != preferred }
        for (page in order) for (row in 0 until rows) for (column in 0 until columns) {
            val item = HomeItem("${kind.name}:$target", kind, target, page, column, row, width, height)
            if (column + width <= columns && row + height <= rows && items.none { overlaps(it, item) }) return item
        }
        error("No space. Add a home page or remove an item.")
    }
    fun move(items: List<HomeItem>, key: String, page: Int, column: Int, row: Int, pages: Int): List<HomeItem> {
        val current = items.first { it.key == key }
        val next = current.copy(page = page, column = column, row = row)
        val collision = items.filter { it.key != key && overlaps(next, it) }
        val changed = if (current.kind == HomeKind.APP && collision.size == 1 && collision[0].kind == HomeKind.APP) {
            items.map { when (it.key) { key -> next; collision[0].key -> it.copy(page = current.page, column = current.column, row = current.row); else -> it } }
        } else items.map { if (it.key == key) next else it }
        return validate(changed, pages)
    }
    fun seed(widgetIds: List<String>, appIds: List<String>, oxygen: Boolean = false): List<HomeItem> {
        var items = emptyList<HomeItem>()
        widgetIds.forEachIndexed { index, id ->
            val page = if (index < 3) 0 else 1
            val item = if (oxygen) place(items, HomeKind.WIDGET, id, 3, 1) else when(index) {
                0 -> HomeItem("WIDGET:$id", HomeKind.WIDGET, id, 0, 0, 0, 2, 2)
                1 -> HomeItem("WIDGET:$id", HomeKind.WIDGET, id, 0, 2, 0, 2, 1)
                2 -> HomeItem("WIDGET:$id", HomeKind.WIDGET, id, 0, 2, 1, 2, 1)
                else -> place(items, HomeKind.WIDGET, id, 3, page)
            }
            items = items + item
        }
        appIds.distinct().take(20).forEachIndexed { index, id ->
            val item = if (index < 8) HomeItem("APP:$id", HomeKind.APP, id, 0, index % 4, 3 + index / 4)
            else place(items, HomeKind.APP, id, 3, 1)
            items = items + item
        }
        return validate(items, 3)
    }
}
