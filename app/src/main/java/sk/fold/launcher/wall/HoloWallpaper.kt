package sk.fold.launcher.wall

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import sk.fold.launcher.data.RingStyle
import sk.fold.launcher.data.Store
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Živá tapeta v štýle Holo kruhu – na výšku aj na šírku s inou kompozíciou. */
class HoloWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = HoloEngine()

    inner class HoloEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var w = 0
        private var h = 0
        private val start = SystemClock.uptimeMillis()
        private val renderer = WallRenderer()
        private val frame = object : Runnable {
            override fun run() {
                draw()
                // pri vypnutej animácii sa nakreslí jeden statický obrázok a slučka sa zastaví
                if (visible && Store.settings.value.wallAnimate) handler.postDelayed(this, 33)
            }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(frame)
            if (v) handler.post(frame)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            w = width; h = height
            draw()
            // po rozložení / zmene displeja sa animácia spustí aj vtedy, keď nepríde zmena viditeľnosti
            if (isVisible) { visible = true; handler.removeCallbacks(frame); handler.post(frame) }
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) = draw()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Fold: každý displej má vlastnú inštanciu tapety
            setOffsetNotificationsEnabled(false)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            super.onDestroy()
        }

        private fun draw() {
            if (w == 0 || h == 0) return
            val holder = surfaceHolder
            val c: Canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull()
                ?: runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                val t = if (Store.settings.value.wallAnimate) (SystemClock.uptimeMillis() - start) / 1000f else 0f
                renderer.draw(c, w.toFloat(), h.toFloat(), t)
            } finally {
                runCatching { holder.unlockCanvasAndPost(c) }
            }
        }
    }
}

/** Kreslenie tapety (android.graphics). */
class WallRenderer {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rnd = Random(7)
    private val particles = List(70) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.3f + rnd.nextFloat(), rnd.nextFloat()) }

    private fun mix(c: Int, k: Float): Int = Color.rgb((Color.red(c) * k).toInt(), (Color.green(c) * k).toInt(), (Color.blue(c) * k).toInt())
    private fun lighten(c: Int, k: Float): Int = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * k).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * k).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * k).toInt(),
    )
    private fun alpha(c: Int, a: Float): Int = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    fun draw(c: Canvas, w: Float, h: Float, t: Float) {
        val s = Store.settings.value
        val col = s.wallColor.toInt()
        val soft = lighten(col, 0.45f)
        val land = w > h
        // hlavný kruh: na výšku hore v strede, na šírku vpravo
        val cx = if (land) w * 0.66f else w * 0.5f
        val cy = if (land) h * 0.5f else h * 0.34f
        val r = if (land) h * 0.38f else min(w, h) * 0.42f

        // pozadie – tmavé, s teplým žiarením okolo kruhu
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, maxOf(w, h) * 0.9f, intArrayOf(mix(col, 0.22f), mix(col, 0.08f), mix(col, 0.02f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // jemná HUD mriežka, ktorá sa pomaly posúva
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = alpha(col, 0.07f)
        val step = min(w, h) / 9f
        val shift = (t * 6f) % step
        var x = -step + shift
        while (x < w + step) { c.drawLine(x, 0f, x, h, p); x += step }
        var y = -step + shift
        while (y < h + step) { c.drawLine(0f, y, w, y, p); y += step }

        // skenovacia čiara
        val sy = (t * 0.08f % 1f) * h
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, sy - h * 0.06f, 0f, sy, intArrayOf(alpha(col, 0f), alpha(col, 0.12f)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, sy - h * 0.06f, w, sy, p)
        p.shader = null
        p.color = alpha(soft, 0.35f)
        c.drawRect(0f, sy, w, sy + 1.5f, p)

        // stúpajúce častice
        particles.forEach { q ->
            val px = q[0] * w + sin(t * 0.4f + q[3] * 6f) * 12f
            val py = ((q[1] - t * 0.015f * q[2]) % 1f + 1f) % 1f * h
            val a = 0.25f + 0.5f * ((sin(t * 2f + q[3] * 9f) + 1f) / 2f)
            p.color = alpha(soft, a * 0.7f)
            c.drawCircle(px, py, 1.2f + q[2] * 2.2f, p)
        }

        // hlavný a menší kruh
        if (s.wallStyle == RingStyle.RADAR) radar(c, cx, cy, r, t, col, soft) else ring(c, cx, cy, r, t, col, soft)
        val sx = if (land) w * 0.16f else w * 0.2f
        val sy2 = if (land) h * 0.8f else h * 0.84f
        ring(c, sx, sy2, r * 0.28f, t * 1.3f, col, soft)
    }

    private fun arcs(c: Canvas, cx: Float, cy: Float, rad: Float, start: Float, count: Int, sweep: Float, width: Float, color: Int) {
        rect.set(cx - rad, cy - rad, cx + rad, cy + rad)
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.color = color
        val stepA = 360f / count
        for (i in 0 until count) c.drawArc(rect, start + i * stepA, sweep, false, p)
    }

    private fun glow(c: Canvas, cx: Float, cy: Float, rad: Float, color: Int, a: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, rad, intArrayOf(alpha(color, a), alpha(color, 0f)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rad, p)
        p.shader = null
    }

    private fun ring(c: Canvas, cx: Float, cy: Float, r: Float, t: Float, col: Int, soft: Int) {
        val pulse = 0.85f + 0.15f * sin(t * 3.4f)
        glow(c, cx, cy, r * 1.15f, col, 0.30f * pulse)
        arcs(c, cx, cy, r * 0.9f, t * 14f, 10, 24f, r * 0.035f, alpha(col, 0.9f))
        // stupnica
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.012f
        for (i in 0 until 72) {
            val ang = Math.toRadians((i * 5 - t * 12f).toDouble())
            val inner = if (i % 6 == 0) r * 0.66f else r * 0.71f
            p.color = alpha(soft, if (i % 6 == 0) 0.9f else 0.45f)
            c.drawLine(cx + cos(ang).toFloat() * inner, cy + sin(ang).toFloat() * inner,
                cx + cos(ang).toFloat() * r * 0.76f, cy + sin(ang).toFloat() * r * 0.76f, p)
        }
        p.strokeWidth = r * 0.012f; p.color = alpha(col, 0.5f)
        c.drawCircle(cx, cy, r * 0.56f, p)
        arcs(c, cx, cy, r * 0.46f, t * 24f, 2, 100f, r * 0.05f, col)
        arcs(c, cx, cy, r * 0.36f, -t * 28f + 60f, 2, 60f, r * 0.02f, alpha(soft, 0.8f))
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 0.26f * pulse, intArrayOf(Color.WHITE, alpha(col, 0.9f), alpha(col, 0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.26f, p)
        p.shader = null
    }

    private fun radar(c: Canvas, cx: Float, cy: Float, r: Float, t: Float, col: Int, soft: Int) {
        glow(c, cx, cy, r * 1.1f, col, 0.2f)
        p.style = Paint.Style.STROKE
        for (k in 1..4) {
            p.strokeWidth = r * (if (k == 4) 0.025f else 0.01f)
            p.color = alpha(col, 0.22f + 0.1f * k)
            c.drawCircle(cx, cy, r * 0.23f * k, p)
        }
        val wave = (t / 2.4f) % 1f
        p.strokeWidth = r * 0.015f; p.color = alpha(col, (1f - wave) * 0.6f)
        c.drawCircle(cx, cy, r * (0.15f + 0.8f * wave), p)
        p.strokeWidth = r * 0.008f; p.color = alpha(soft, 0.35f)
        c.drawLine(cx - r * 0.95f, cy, cx + r * 0.95f, cy, p)
        c.drawLine(cx, cy - r * 0.95f, cx, cy + r * 0.95f, p)
        val sweep = (t * 86f) % 360f
        c.save()
        c.rotate(sweep, cx, cy)
        p.style = Paint.Style.FILL
        p.shader = SweepGradient(cx, cy, intArrayOf(alpha(col, 0f), alpha(col, 0f), alpha(col, 0.55f)), floatArrayOf(0f, 0.75f, 1f))
        c.drawCircle(cx, cy, r * 0.92f, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.018f; p.color = col
        c.drawLine(cx, cy, cx + r * 0.92f, cy, p)
        c.restore()
        p.style = Paint.Style.FILL
        listOf(35f to 0.62f, 140f to 0.38f, 215f to 0.8f, 300f to 0.5f, 95f to 0.85f).forEach { (deg, dist) ->
            val since = ((sweep - deg) % 360f + 360f) % 360f
            val a = (1f - since / 300f).coerceIn(0f, 1f)
            if (a > 0f) {
                val ang = Math.toRadians(deg.toDouble())
                val bx = cx + cos(ang).toFloat() * r * 0.92f * dist
                val by = cy + sin(ang).toFloat() * r * 0.92f * dist
                p.color = alpha(col, a * 0.4f); c.drawCircle(bx, by, r * 0.08f, p)
                p.color = alpha(soft, a); c.drawCircle(bx, by, r * 0.035f, p)
            }
        }
        glow(c, cx, cy, r * 0.12f, Color.WHITE, 1f)
    }
}
