package sk.fold.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Trvalé uloženie nastavení a rozložení plochy (JSON v SharedPreferences). */
object Store {
    private lateinit var sp: SharedPreferences
    val settings = MutableStateFlow(Settings())
    private val layouts = mapOf(
        Screen.COVER to MutableStateFlow(defaultLayout(Screen.COVER)),
        Screen.INNER to MutableStateFlow(defaultLayout(Screen.INNER)),
    )
    private val ids = AtomicLong(System.currentTimeMillis())

    /** Posledná zobrazená obrazovka a stránka – sem pridávajú aj iné aplikácie (pripnutie). */
    @Volatile var lastScreen = Screen.COVER
    @Volatile var lastPage = 0
    /** Veľkosť bunky v dp pre každú obrazovku (aktualizuje UI). */
    val cellDp = mutableMapOf(Screen.COVER to (68f to 68f), Screen.INNER to (72f to 72f))

    fun newId(): Long = ids.incrementAndGet()

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        sp.getString("settings", null)?.let { s ->
            runCatching { settings.value = settingsFromJson(JSONObject(s)) }
        }
        for (s in Screen.entries) sp.getString("layout_${s.name}", null)?.let { j ->
            runCatching { layouts.getValue(s).value = layoutFromJson(JSONObject(j), s) }
        }
    }

    fun layout(s: Screen): StateFlow<ScreenLayout> = layouts.getValue(s)

    @Synchronized
    fun editLayout(s: Screen, f: (ScreenLayout) -> ScreenLayout) {
        val flow = layouts.getValue(s)
        val n = f(flow.value)
        flow.value = n
        sp.edit().putString("layout_${s.name}", layoutToJson(n).toString()).apply()
    }

    @Synchronized
    fun editSettings(f: (Settings) -> Settings) {
        val n = f(settings.value)
        settings.value = n
        sp.edit().putString("settings", settingsToJson(n).toString()).apply()
    }

    fun defaultLayout(s: Screen) = if (s == Screen.COVER) ScreenLayout(6, 12) else ScreenLayout(12, 10)

    // ---------- umiestňovanie ----------

    fun span(s: Screen, wDp: Float, hDp: Float, roundUp: Boolean = false): Pair<Int, Int> {
        val l = layout(s).value
        val (cw, ch) = cellDp.getValue(s)
        val fw = wDp / cw
        val fh = hDp / ch
        val w = if (roundUp) ceil(fw - 0.15f).toInt() else fw.roundToInt()
        val h = if (roundUp) ceil(fh - 0.15f).toInt() else fh.roundToInt()
        return w.coerceIn(1, l.cols) to h.coerceIn(1, l.rows)
    }

    fun appSpan(s: Screen): Pair<Int, Int> =
        span(s, 64f, if (settings.value.showLabels) 82f else 64f)

    fun defaultSpan(s: Screen, type: ItemType): Pair<Int, Int> = when (type) {
        ItemType.CLOCK -> span(s, 300f, 130f)
        ItemType.FAVORITES -> span(s, 300f, 330f)
        ItemType.SEARCH -> span(s, 320f, 54f)
        else -> appSpan(s)
    }

    /** Pridá prvok (veľkosť w/h už nastavená) na plochu, vráti stránku, kam sa dostal. */
    fun add(s: Screen, item: HomeItem, page: Int = lastPage): Int {
        var placedPage = 0
        editLayout(s) { l ->
            val n = l.place(item, page)
            placedPage = n.items.last().page
            n
        }
        return placedPage
    }

    fun addApp(s: Screen, key: String, page: Int = lastPage): Int {
        val (w, h) = appSpan(s)
        return add(s, HomeItem(newId(), ItemType.APP, page, 0, 0, w, h, app = key), page)
    }

    fun ensureDefaults(favs: List<String>) {
        if (settings.value.initialized) return
        editSettings { it.copy(initialized = true, favorites = it.favorites.ifEmpty { favs }) }
        if (layout(Screen.COVER).value.items.isEmpty()) editLayout(Screen.COVER) {
            it.copy(items = listOf(
                HomeItem(newId(), ItemType.CLOCK, 0, 0, 1, 6, 2),
                HomeItem(newId(), ItemType.FAVORITES, 0, 0, 4, 6, 6),
                HomeItem(newId(), ItemType.SEARCH, 0, 0, 11, 6, 1),
            ))
        }
        if (layout(Screen.INNER).value.items.isEmpty()) editLayout(Screen.INNER) {
            it.copy(items = listOf(
                HomeItem(newId(), ItemType.CLOCK, 0, 1, 1, 5, 2),
                HomeItem(newId(), ItemType.FAVORITES, 0, 1, 4, 5, 5),
                HomeItem(newId(), ItemType.SEARCH, 0, 3, 9, 6, 1),
            ))
        }
    }

    /** Skopíruje rozloženie jednej obrazovky na druhú (bez widgetov, tie majú vlastné ID). */
    fun copyLayout(from: Screen, to: Screen): List<Int> {
        val src = layout(from).value
        val dst = layout(to).value
        val removedWidgets = dst.items.filter { it.type == ItemType.WIDGET }.map { it.widgetId }
        val copied = src.copy(items = src.items.filter { it.type != ItemType.WIDGET }.map { it.copy(id = newId()) })
            .rescale(dst.cols, dst.rows)
        editLayout(to) { copied }
        return removedWidgets
    }

    /** Odstráni z plochy a obľúbených aplikácie odinštalovaného balíka. */
    fun removePackage(pkg: String) {
        val prefix = "$pkg/"
        for (s in Screen.entries) editLayout(s) { l ->
            l.copy(items = l.items.filterNot { it.type == ItemType.APP && it.app?.startsWith(prefix) == true }
                .map { if (it.type == ItemType.FOLDER) it.copy(apps = it.apps.filterNot { a -> a.startsWith(prefix) }) else it })
        }
        editSettings { it.copy(favorites = it.favorites.filterNot { a -> a.startsWith(prefix) }) }
    }

    // ---------- záloha ----------

    fun exportJson(): String = JSONObject().apply {
        put("version", 1)
        put("settings", settingsToJson(settings.value))
        for (s in Screen.entries) put("layout_${s.name}", layoutToJson(layout(s).value))
    }.toString(2)

    /** Vráti ID widgetov, ktoré treba uvoľniť (staré rozloženie), alebo null pri chybe. */
    fun importJson(text: String): List<Int>? = runCatching {
        val o = JSONObject(text)
        val old = Screen.entries.flatMap { s -> layout(s).value.items.filter { it.type == ItemType.WIDGET }.map { it.widgetId } }
        o.optJSONObject("settings")?.let { js -> editSettings { settingsFromJson(js).copy(initialized = true) } }
        for (s in Screen.entries) o.optJSONObject("layout_${s.name}")?.let { jl ->
            val l = layoutFromJson(jl, s)
            editLayout(s) { l.copy(items = l.items.filter { it.type != ItemType.WIDGET }) }
        }
        old
    }.getOrNull()

    // ---------- JSON ----------

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

    private inline fun <reified T : Enum<T>> en(v: String?, d: T): T =
        if (v == null) d else runCatching { enumValueOf<T>(v) }.getOrDefault(d)

    private fun itemToJson(i: HomeItem) = JSONObject().apply {
        put("id", i.id); put("type", i.type.name); put("page", i.page)
        put("x", i.x); put("y", i.y); put("w", i.w); put("h", i.h)
        i.app?.let { put("app", it) }
        if (i.widgetId != -1) put("widgetId", i.widgetId)
        i.shortcutId?.let { put("shortcutId", it) }
        i.pkg?.let { put("pkg", it) }
        put("serial", i.serial)
        i.label?.let { put("label", it) }
        if (i.apps.isNotEmpty()) put("apps", JSONArray(i.apps))
    }

    private fun itemFromJson(o: JSONObject): HomeItem? = runCatching {
        HomeItem(
            id = o.getLong("id"),
            type = ItemType.valueOf(o.getString("type")),
            page = o.optInt("page", 0),
            x = o.getInt("x"), y = o.getInt("y"), w = o.getInt("w"), h = o.getInt("h"),
            app = o.str("app"),
            widgetId = o.optInt("widgetId", -1),
            shortcutId = o.str("shortcutId"),
            pkg = o.str("pkg"),
            serial = o.optLong("serial", 0),
            label = o.str("label"),
            apps = o.optJSONArray("apps")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
        )
    }.getOrNull()

    private fun layoutToJson(l: ScreenLayout) = JSONObject().apply {
        put("cols", l.cols); put("rows", l.rows); put("pages", l.pages)
        put("items", JSONArray().apply { l.items.forEach { put(itemToJson(it)) } })
    }

    private fun layoutFromJson(o: JSONObject, s: Screen): ScreenLayout {
        val d = defaultLayout(s)
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::itemFromJson) }
        val pages = maxOf(o.optInt("pages", 1), (items.maxOfOrNull { it.page } ?: 0) + 1)
        return ScreenLayout(o.optInt("cols", d.cols), o.optInt("rows", d.rows), pages, items)
    }

    private fun settingsToJson(s: Settings) = JSONObject().apply {
        put("theme", s.theme.name); put("dynamicColor", s.dynamicColor); put("accent", s.accent)
        put("iconShape", s.iconShape.name); put("iconPack", s.iconPack); put("themedIcons", s.themedIcons)
        put("iconScale", s.iconScale.toDouble()); put("showLabels", s.showLabels)
        put("labelSize", s.labelSize.toDouble()); put("labelShadow", s.labelShadow); put("font", s.font.name)
        put("hideStatusBar", s.hideStatusBar); put("wallpaperDim", s.wallpaperDim.toDouble())
        put("showGrid", s.showGrid); put("lockLayout", s.lockLayout)
        put("drawerStyle", s.drawerStyle.name); put("drawerAlpha", s.drawerAlpha.toDouble())
        put("alphabet", s.alphabet); put("autoKeyboard", s.autoKeyboard)
        put("drawerIconSize", s.drawerIconSize.toDouble())
        put("badges", s.badges.name); put("favNotifText", s.favNotifText)
        put("doubleTap", s.doubleTap.name); put("swipeDown", s.swipeDown.name); put("swipeUp", s.swipeUp.name)
        put("clock24", s.clock24); put("clockDate", s.clockDate); put("clockStyle", s.clockStyle.name)
        put("favorites", JSONArray(s.favorites))
        put("hidden", JSONArray(s.hidden.toList()))
        put("renames", JSONObject().apply { s.renames.forEach { (k, v) -> put(k, v) } })
        put("initialized", s.initialized)
    }

    private fun settingsFromJson(o: JSONObject): Settings {
        val d = Settings()
        fun f(k: String, def: Float) = o.optDouble(k, def.toDouble()).toFloat()
        val ren = o.optJSONObject("renames")
        return Settings(
            theme = en(o.str("theme"), d.theme),
            dynamicColor = o.optBoolean("dynamicColor", d.dynamicColor),
            accent = o.optLong("accent", d.accent),
            iconShape = en(o.str("iconShape"), d.iconShape),
            iconPack = o.optString("iconPack", ""),
            themedIcons = o.optBoolean("themedIcons", d.themedIcons),
            iconScale = f("iconScale", d.iconScale),
            showLabels = o.optBoolean("showLabels", d.showLabels),
            labelSize = f("labelSize", d.labelSize),
            labelShadow = o.optBoolean("labelShadow", d.labelShadow),
            font = en(o.str("font"), d.font),
            hideStatusBar = o.optBoolean("hideStatusBar", d.hideStatusBar),
            wallpaperDim = f("wallpaperDim", d.wallpaperDim),
            showGrid = o.optBoolean("showGrid", d.showGrid),
            lockLayout = o.optBoolean("lockLayout", d.lockLayout),
            drawerStyle = en(o.str("drawerStyle"), d.drawerStyle),
            drawerAlpha = f("drawerAlpha", d.drawerAlpha),
            alphabet = o.optBoolean("alphabet", d.alphabet),
            autoKeyboard = o.optBoolean("autoKeyboard", d.autoKeyboard),
            drawerIconSize = f("drawerIconSize", d.drawerIconSize),
            badges = en(o.str("badges"), d.badges),
            favNotifText = o.optBoolean("favNotifText", d.favNotifText),
            doubleTap = en(o.str("doubleTap"), d.doubleTap),
            swipeDown = en(o.str("swipeDown"), d.swipeDown),
            swipeUp = en(o.str("swipeUp"), d.swipeUp),
            clock24 = o.optBoolean("clock24", d.clock24),
            clockDate = o.optBoolean("clockDate", d.clockDate),
            clockStyle = en(o.str("clockStyle"), d.clockStyle),
            favorites = o.optJSONArray("favorites")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
            hidden = o.optJSONArray("hidden")?.let { a -> List(a.length()) { a.getString(it) }.toSet() } ?: emptySet(),
            renames = ren?.keys()?.asSequence()?.associateWith { ren.getString(it) } ?: emptyMap(),
            initialized = o.optBoolean("initialized", false),
        )
    }
}
