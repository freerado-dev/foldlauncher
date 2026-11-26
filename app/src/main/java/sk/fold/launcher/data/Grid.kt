package sk.fold.launcher.data

import kotlin.math.max
import kotlin.math.roundToInt

/** Obdĺžnik v bunkách mriežky. */
data class Cell(val x: Int, val y: Int, val w: Int, val h: Int)

object Grid {
    fun overlaps(a: Cell, b: Cell): Boolean =
        a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

    fun inside(c: Cell, cols: Int, rows: Int): Boolean =
        c.x >= 0 && c.y >= 0 && c.w >= 1 && c.h >= 1 && c.x + c.w <= cols && c.y + c.h <= rows

    fun isFree(c: Cell, others: List<Cell>, cols: Int, rows: Int): Boolean =
        inside(c, cols, rows) && others.none { overlaps(c, it) }

    fun findFree(w: Int, h: Int, others: List<Cell>, cols: Int, rows: Int): Cell? {
        val ww = w.coerceIn(1, cols)
        val hh = h.coerceIn(1, rows)
        for (y in 0..rows - hh) for (x in 0..cols - ww) {
            val c = Cell(x, y, ww, hh)
            if (isFree(c, others, cols, rows)) return c
        }
        return null
    }
}

enum class ItemType { APP, SHORTCUT, WIDGET, FOLDER, CLOCK, FAVORITES, SEARCH, ASSISTANT }

/** COVER = vonkajší displej (zložený Fold), INNER = vnútorný (rozložený); _LAND = na šírku (vlastné rozloženie). */
enum class Screen {
    COVER, INNER, COVER_LAND, INNER_LAND;

    val isCover: Boolean get() = this == COVER || this == COVER_LAND
    val isLand: Boolean get() = this == COVER_LAND || this == INNER_LAND
    /** Rozloženie na výšku pre ten istý displej. */
    val portrait: Screen get() = if (isCover) COVER else INNER
    val label: String get() = (if (isCover) "vonkajší" else "vnútorný") + (if (isLand) " na šírku" else " na výšku")
}

data class HomeItem(
    val id: Long,
    val type: ItemType,
    val page: Int,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    /** Kľúč aplikácie "balík/trieda|sériové číslo používateľa". */
    val app: String? = null,
    val widgetId: Int = -1,
    val shortcutId: String? = null,
    val pkg: String? = null,
    val serial: Long = 0,
    val label: String? = null,
    /** Obsah priečinka (kľúče aplikácií). */
    val apps: List<String> = emptyList(),
) {
    val cell: Cell get() = Cell(x, y, w, h)
    fun at(c: Cell, page: Int = this.page) = copy(page = page, x = c.x, y = c.y, w = c.w, h = c.h)
}

data class ScreenLayout(
    val cols: Int,
    val rows: Int,
    val pages: Int = 1,
    val items: List<HomeItem> = emptyList(),
) {
    fun cellsOn(page: Int, except: Long? = null): List<Cell> =
        items.filter { it.page == page && it.id != except }.map { it.cell }

    fun isFree(page: Int, c: Cell, except: Long?): Boolean =
        Grid.isFree(c, cellsOn(page, except), cols, rows)

    /** Umiestni prvok (použije jeho w/h) na prvé voľné miesto, prednostne na danú stránku. */
    fun place(item: HomeItem, preferredPage: Int): ScreenLayout {
        val pref = preferredPage.coerceIn(0, pages - 1)
        val order = listOf(pref) + (0 until pages).filter { it != pref }
        for (p in order) {
            Grid.findFree(item.w, item.h, cellsOn(p), cols, rows)?.let {
                return copy(items = items + item.at(it, p))
            }
        }
        val c = Grid.findFree(item.w, item.h, emptyList(), cols, rows)
            ?: Cell(0, 0, item.w.coerceIn(1, cols), item.h.coerceIn(1, rows))
        return copy(pages = pages + 1, items = items + item.at(c, pages))
    }

    fun update(id: Long, f: (HomeItem) -> HomeItem) =
        copy(items = items.map { if (it.id == id) f(it) else it })

    fun remove(id: Long) = copy(items = items.filterNot { it.id == id })

    /** Presun na inú stránku (na prvé voľné miesto). Vráti null, ak sa nezmestí. */
    fun moveToPage(id: Long, page: Int): ScreenLayout? {
        val item = items.find { it.id == id } ?: return null
        val base = if (page >= pages) copy(pages = page + 1) else this
        val c = Grid.findFree(item.w, item.h, base.cellsOn(page, id), cols, rows) ?: return null
        return base.update(id) { it.at(c, page) }
    }

    /**
     * Presun na obsadené miesto: prekážajúce prvky sa presunú na uvoľnené miesto ťahaného prvku
     * (ak sa tam zmestia), inak na najbližšie voľné miesto na stránke, prípadne na inú stránku.
     */
    fun moveWithSwap(id: Long, target: Cell): ScreenLayout? {
        val item = items.find { it.id == id } ?: return null
        if (!Grid.inside(target, cols, rows)) return null
        val page = item.page
        val blockers = items.filter { it.page == page && it.id != id && Grid.overlaps(it.cell, target) }
        if (blockers.isEmpty()) return update(id) { it.at(target) }
        val dx = item.x - target.x
        val dy = item.y - target.y
        var out = copy(items = items.filter { it.id != id && it !in blockers } + item.at(target))
        // 1) posun o rovnaký vektor – prvky si vymenia miesto
        val shifted = blockers.map { it.at(Cell(it.x + dx, it.y + dy, it.w, it.h)) }
        val fits = shifted.all { s ->
            Grid.isFree(s.cell, out.cellsOn(page) + shifted.filter { it.id != s.id && it.id < s.id }.map { it.cell }, cols, rows)
        } && shifted.indices.all { i -> shifted.indices.none { j -> j != i && Grid.overlaps(shifted[i].cell, shifted[j].cell) } }
        if (fits) return out.copy(items = out.items + shifted)
        // 2) každý prekážajúci prvok na najbližšie voľné miesto k pôvodnej pozícii ťahaného prvku
        val leftovers = mutableListOf<HomeItem>()
        for (b in blockers.sortedByDescending { it.w * it.h }) {
            val free = nearestFree(b.w, b.h, item.x, item.y, out.cellsOn(page))
            if (free != null) out = out.copy(items = out.items + b.at(free)) else leftovers += b
        }
        leftovers.forEach { out = out.place(it, page) }
        return out
    }

    private fun nearestFree(w: Int, h: Int, nearX: Int, nearY: Int, others: List<Cell>): Cell? {
        var best: Cell? = null
        var bestD = Int.MAX_VALUE
        for (y in 0..rows - h) for (x in 0..cols - w) {
            val c = Cell(x, y, w, h)
            if (!Grid.isFree(c, others, cols, rows)) continue
            val d = (x - nearX) * (x - nearX) + (y - nearY) * (y - nearY)
            if (d < bestD) { bestD = d; best = c }
        }
        return best
    }

    fun addPage() = copy(pages = pages + 1)

    fun removePage(page: Int): ScreenLayout =
        if (pages <= 1) copy(items = items.filterNot { it.page == page })
        else copy(
            pages = pages - 1,
            items = items.filter { it.page != page }
                .map { if (it.page > page) it.copy(page = it.page - 1) else it },
        )

    /** Zmena počtu riadkov bez prepočtu pozícií (štvorcové bunky) – čo sa nezmestí, presunie sa. */
    fun withRows(n: Int): ScreenLayout {
        if (n == rows || n < 1) return this
        var out = copy(rows = n, items = emptyList())
        val leftovers = mutableListOf<HomeItem>()
        for (item in items.sortedWith(compareBy({ it.page }, { it.y }, { it.x }))) {
            val h = item.h.coerceAtMost(n)
            val c = Cell(item.x, item.y.coerceIn(0, n - h), item.w, h)
            val target = if (out.isFree(item.page, c, null)) c
            else Grid.findFree(item.w, h, out.cellsOn(item.page), cols, n)
            if (target != null) out = out.copy(items = out.items + item.at(target))
            else leftovers += item.copy(h = h)
        }
        leftovers.forEach { out = out.place(it, it.page) }
        return out
    }

    /** Zmena hustoty mriežky – pozície a veľkosti sa prepočítajú pomerne, prekryvy sa vyriešia. */
    fun rescale(newCols: Int, newRows: Int): ScreenLayout {
        if (newCols == cols && newRows == rows) return this
        val sx = newCols.toFloat() / cols
        val sy = newRows.toFloat() / rows
        var out = copy(cols = newCols, rows = newRows, items = emptyList())
        val leftovers = mutableListOf<HomeItem>()
        for (item in items.sortedWith(compareBy({ it.page }, { it.y }, { it.x }))) {
            val w = max(1, (item.w * sx).roundToInt()).coerceAtMost(newCols)
            val h = max(1, (item.h * sy).roundToInt()).coerceAtMost(newRows)
            val x = (item.x * sx).roundToInt().coerceIn(0, newCols - w)
            val y = (item.y * sy).roundToInt().coerceIn(0, newRows - h)
            val c = Cell(x, y, w, h)
            val target = if (out.isFree(item.page, c, null)) c
            else Grid.findFree(w, h, out.cellsOn(item.page), newCols, newRows)
            if (target != null) out = out.copy(items = out.items + item.at(target))
            else leftovers += item.copy(w = w, h = h)
        }
        leftovers.forEach { out = out.place(it, it.page) }
        return out
    }
}
