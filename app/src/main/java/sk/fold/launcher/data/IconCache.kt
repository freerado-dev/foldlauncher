package sk.fold.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun clear() = cache.evictAll()

    fun bucket(px: Int): Int = ((px + 23) / 24 * 24).coerceIn(48, 480)

    fun key(id: String, px: Int, s: Settings, dark: Boolean) =
        "$id|$px|${s.iconShape}|${s.iconPack}|${s.themedIcons}|$dark"

    fun cached(k: String): ImageBitmap? = cache.get(k)

    fun app(ctx: Context, a: AppInfo, px: Int, s: Settings, dark: Boolean): ImageBitmap {
        val k = key(a.key, px, s, dark)
        cache.get(k)?.let { return it }
        val dpi = ctx.resources.displayMetrics.densityDpi
        val packIcon = if (s.iconPack.isNotEmpty()) IconPacks.drawable(ctx, s.iconPack, a.component, dpi) else null
        val bmp = if (packIcon != null) plain(packIcon, px)
        else render(ctx, runCatching { a.info.getIcon(dpi) }.getOrNull() ?: ctx.packageManager.defaultActivityIcon, px, s, dark)
        val out = badge(ctx, bmp, a.user).asImageBitmap()
        cache.put(k, out)
        return out
    }

    fun shortcut(ctx: Context, item: HomeItem, px: Int, s: Settings, dark: Boolean): ImageBitmap? {
        val k = key("sc:${item.pkg}:${item.shortcutId}:${item.serial}", px, s, dark)
        cache.get(k)?.let { return it }
        val user = Apps.userFor(item.serial) ?: return null
        val info = Apps.shortcutInfo(item.pkg ?: return null, item.shortcutId ?: return null, user) ?: return null
        val d = runCatching { Apps.la.getShortcutIconDrawable(info, ctx.resources.displayMetrics.densityDpi) }.getOrNull()
            ?: return null
        val out = render(ctx, d, px, s, dark).asImageBitmap()
        cache.put(k, out)
        return out
    }

    /** Ikona bez úpravy tvaru (napr. z balíka ikon alebo náhľad). */
    fun plain(d: Drawable, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, px, px)
        d.draw(c)
        return bmp
    }

    fun toBitmap(d: Drawable, maxPx: Int): Bitmap {
        val iw = d.intrinsicWidth
        val ih = d.intrinsicHeight
        val (w, h) = if (iw <= 0 || ih <= 0) maxPx to maxPx else {
            val sc = minOf(1f, maxPx.toFloat() / maxOf(iw, ih))
            maxOf(1, (iw * sc).toInt()) to maxOf(1, (ih * sc).toInt())
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun themedColors(ctx: Context, dark: Boolean): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 31) {
            if (dark) ctx.getColor(android.R.color.system_neutral1_800) to ctx.getColor(android.R.color.system_accent1_100)
            else ctx.getColor(android.R.color.system_accent1_100) to ctx.getColor(android.R.color.system_neutral2_700)
        } else if (dark) Color.rgb(40, 40, 46) to Color.rgb(200, 215, 255) else Color.rgb(220, 228, 255) to Color.rgb(50, 55, 70)

    fun render(ctx: Context, d: Drawable, px: Int, s: Settings, dark: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val shape = s.iconShape
        if (d is AdaptiveIconDrawable) {
            d.setBounds(0, 0, px, px)
            val path = if (shape == IconShape.SYSTEM) Path(d.iconMask) else shapePath(shape, px.toFloat())
            c.save()
            c.clipPath(path)
            val l = -px / 4
            val r = px + px / 4
            val mono = if (s.themedIcons && Build.VERSION.SDK_INT >= 33) d.monochrome else null
            if (mono != null) {
                val (bg, fg) = themedColors(ctx, dark)
                c.drawColor(bg)
                val m = mono.mutate()
                m.setTint(fg)
                m.setBounds(l, l, r, r)
                m.draw(c)
            } else {
                d.background?.let { it.setBounds(l, l, r, r); it.draw(c) }
                d.foreground?.let { it.setBounds(l, l, r, r); it.draw(c) }
            }
            c.restore()
        } else if (shape == IconShape.SYSTEM) {
            d.setBounds(0, 0, px, px)
            d.draw(c)
        } else {
            c.save()
            c.clipPath(shapePath(shape, px.toFloat()))
            c.drawColor(if (s.themedIcons) themedColors(ctx, dark).first else Color.WHITE)
            val i = (px * 0.17f).toInt()
            d.setBounds(i, i, px - i, px - i)
            d.draw(c)
            c.restore()
        }
        return bmp
    }

    private fun badge(ctx: Context, bmp: Bitmap, user: UserHandle): Bitmap {
        if (user == Process.myUserHandle()) return bmp
        val badged = ctx.packageManager.getUserBadgedIcon(BitmapDrawable(ctx.resources, bmp), user)
        return plain(badged, bmp.width)
    }

    fun shapePath(shape: IconShape, s: Float): Path = Path().apply {
        when (shape) {
            IconShape.SYSTEM, IconShape.CIRCLE -> addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
            IconShape.ROUNDED -> addRoundRect(RectF(0f, 0f, s, s), s * 0.25f, s * 0.25f, Path.Direction.CW)
            IconShape.SQUARE -> addRoundRect(RectF(0f, 0f, s, s), s * 0.06f, s * 0.06f, Path.Direction.CW)
            IconShape.TEARDROP -> {
                val r = s / 2
                val k = s * 0.12f
                addRoundRect(RectF(0f, 0f, s, s), floatArrayOf(r, r, r, r, k, k, r, r), Path.Direction.CW)
            }
            IconShape.SQUIRCLE -> {
                val n = 96
                val half = s / 2
                for (i in 0..n) {
                    val t = 2.0 * Math.PI * i / n
                    val ct = cos(t)
                    val st = sin(t)
                    val x = half + half * (sign(ct) * abs(ct).pow(0.5)).toFloat()
                    val y = half + half * (sign(st) * abs(st).pow(0.5)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }
}

/** Balíky ikon kompatibilné s ADW/Nova (appfilter.xml). */
object IconPacks {
    data class Pack(val pkg: String, val label: String)

    fun list(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        return listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
            .flatMap { runCatching { pm.queryIntentActivities(Intent(it), 0) }.getOrDefault(emptyList()) }
            .distinctBy { it.activityInfo.packageName }
            .map { Pack(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    private var loaded: String? = null
    private var map: Map<String, String> = emptyMap()
    private var res: Resources? = null

    @Synchronized
    private fun ensure(ctx: Context, pkg: String) {
        if (loaded == pkg) return
        loaded = pkg
        map = emptyMap()
        res = null
        runCatching {
            val r = ctx.packageManager.getResourcesForApplication(pkg)
            val id = r.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (id != 0) r.getXml(id) else
                XmlPullParserFactory.newInstance().newPullParser().apply { setInput(r.assets.open("appfilter.xml"), "utf-8") }
            val m = HashMap<String, String>()
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null) {
                        val cn = ComponentName.unflattenFromString(comp.removePrefix("ComponentInfo{").removeSuffix("}"))
                        if (cn != null) m[cn.flattenToString()] = dr
                    }
                }
                ev = parser.next()
            }
            map = m
            res = r
        }
    }

    @Synchronized
    fun drawable(ctx: Context, pkg: String, comp: ComponentName, dpi: Int): Drawable? {
        ensure(ctx, pkg)
        val name = map[comp.flattenToString()] ?: return null
        val r = res ?: return null
        val id = r.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return runCatching { r.getDrawableForDensity(id, dpi, null) }.getOrNull()
    }
}
