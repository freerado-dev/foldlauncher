package sk.fold.launcher.wall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import sk.fold.launcher.data.Store
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Tapeta s vlastným obrázkom, cez ktorého drážky prebiehajú svetelné lúče. */
class BeamWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = BeamEngine()

    inner class BeamEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var w = 0
        private var h = 0
        private val start = SystemClock.uptimeMillis()
        private val renderer = BeamRenderer(this@BeamWallpaper)
        private val frame = object : Runnable {
            override fun run() {
                draw()
                if (visible) handler.postDelayed(this, 33)
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
                renderer.draw(c, w, h, (SystemClock.uptimeMillis() - start) / 1000f)
            } finally {
                runCatching { holder.unlockCanvasAndPost(c) }
            }
        }
    }
}

object BeamImage {
    fun file(ctx: Context) = File(ctx.filesDir, "beam_bg.jpg")

    /**
     * Uloží vybraný obrázok a nájde v ňom šikmé drážky. Vráti (smer +1 = „/“ alebo -1 = „\\“, rozostup a posun
     * – obe ako podiel šírky obrázka), alebo null.
     */
    fun importAndDetect(ctx: Context, uri: Uri): Triple<Int, Float, Float>? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { inp -> file(ctx).outputStream().use { inp.copyTo(it) } }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file(ctx).path, opts)
        val sample = max(1, opts.outWidth / 700)
        val bmp = BitmapFactory.decodeFile(file(ctx).path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val r = detect(bmp)
        bmp.recycle()
        r
    }.getOrNull()

    private fun detect(b: Bitmap): Triple<Int, Float, Float>? {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatArray(w * h) { val c = px[it]; (Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f) }
        var best: Triple<Int, Float, Float>? = null
        var bestScore = 0f
        for (dir in intArrayOf(1, -1)) {
            // profil jasu pozdĺž s = x + dir*y (pre dir = -1 posunutý o h)
            val n = w + h + 2
            val sum = FloatArray(n)
            val cnt = IntArray(n)
            val xMax = (w * 0.62f).toInt() // ľavá, rovnomernejšia časť obrázka
            for (y in 0 until h) for (x in 0 until xMax) {
                val s = x + dir * y + (if (dir < 0) h else 0)
                sum[s] += lum[y * w + x]; cnt[s]++
            }
            val prof = FloatArray(n) { if (cnt[it] > 0) sum[it] / cnt[it] else 0f }
            val a = n / 4
            val bnd = n * 3 / 4
            // odstránenie pomalých zmien jasu
            val det = FloatArray(bnd - a) { i ->
                var m = 0f; var k = 0
                for (j in -20..20) { val q = a + i + j; if (q in 0 until n) { m += prof[q]; k++ } }
                prof[a + i] - m / k
            }
            var bestLag = 0
            var bestAc = 0f
            for (lag in 4..80) {
                var acc = 0f
                for (i in 0 until det.size - lag) acc += det[i] * det[i + lag]
                if (acc > bestAc) { bestAc = acc; bestLag = lag }
            }
            if (bestLag == 0) continue
            // najtmavší posun v rámci periódy = drážka
            var darkest = 0
            var dv = Float.MAX_VALUE
            for (k in 0 until bestLag) {
                var m = 0f; var c = 0
                var q = a + k
                while (q < bnd) { m += prof[q]; c++; q += bestLag }
                val v = m / max(1, c)
                if (v < dv) { dv = v; darkest = k }
            }
            val phaseS = (a + darkest - (if (dir < 0) h else 0)).toFloat()
            if (bestAc > bestScore) {
                bestScore = bestAc
                best = Triple(dir, bestLag.toFloat() / w, (((phaseS % bestLag) + bestLag) % bestLag) / w)
            }
        }
        return best
    }
}

class BeamRenderer(private val ctx: Context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var bmp: Bitmap? = null
    private var bmpStamp = -1L
    private var bmpFor = 0
    private val src = Rect()
    private val dst = RectF()

    private fun alpha(c: Int, a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
    private fun hash(i: Int): Float { var x = i * 374761393 + 668265263; x = (x xor (x ushr 13)) * 1274126177; return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777215f }

    private fun ensureBitmap(w: Int, h: Int) {
        val f = BeamImage.file(ctx)
        val stamp = if (f.exists()) f.lastModified() else 0L
        if (stamp == bmpStamp && bmpFor == max(w, h)) return
        bmpStamp = stamp; bmpFor = max(w, h)
        bmp?.recycle(); bmp = null
        if (!f.exists()) return
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        val need = max(w, h)
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= need) sample *= 2
        bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun draw(c: Canvas, w: Int, h: Int, t: Float) {
        val s = Store.settings.value
        ensureBitmap(w, h)
        val b = bmp
        // obrázok ako „center crop“
        var sc = 1f; var ox = 0f; var oy = 0f; var bw = w.toFloat()
        if (b != null) {
            sc = max(w / b.width.toFloat(), h / b.height.toFloat())
            ox = (w - b.width * sc) / 2f; oy = (h - b.height * sc) / 2f
            bw = b.width.toFloat()
            src.set(0, 0, b.width, b.height)
            dst.set(ox, oy, ox + b.width * sc, oy + b.height * sc)
            c.drawBitmap(b, src, dst, bmpPaint)
        } else {
            c.drawColor(Color.rgb(28, 28, 30))
        }
        val col = s.beamColor.toInt()
        val d = if (s.beamDir >= 0) 1 else -1
        val periodS = s.beamPeriod * bw * sc
        if (periodS < 3f) return
        // drážka k: x + d*y = C_k (v súradniciach obrazovky)
        val base = sc * (s.beamPhase + s.beamShift * s.beamPeriod) * bw + ox + d * oy
        val cMin = if (d > 0) 0f else -h.toFloat()
        val cMax = if (d > 0) (w + h).toFloat() else w.toFloat()
        val kMin = floor((cMin - base) / periodS).toInt()
        val kMax = floor((cMax - base) / periodS).toInt()
        if (kMax <= kMin) return
        val dur = 1.8f
        val cycle = dur + s.beamInterval
        repeat(s.beamCount.coerceIn(1, 3)) { lane ->
            val tt = t + lane * cycle / s.beamCount.coerceIn(1, 3)
            val idx = floor(tt / cycle).toInt()
            val prog = (tt - idx * cycle) / dur
            if (prog > 1f) return@repeat
            val k = kMin + 1 + (hash(idx * 31 + lane) * (kMax - kMin - 1)).toInt()
            val C = base + k * periodS
            // úsek drážky viditeľný na obrazovke, zľava doprava
            val x0: Float; val x1: Float
            if (d > 0) { x0 = max(0f, C - h); x1 = min(w.toFloat(), C) } else { x0 = max(0f, C); x1 = min(w.toFloat(), C + h) }
            if (x1 - x0 < 10f) return@repeat
            fun yOf(x: Float) = if (d > 0) C - x else x - C
            val ease = prog * prog * (3 - 2 * prog)
            val hx = x0 + (x1 - x0) * ease
            val tail = min(w, h) * 0.32f / 1.414f
            val tx = max(x0, hx - tail)
            val hy = yOf(hx); val ty = yOf(tx)
            val fade = when { prog < 0.08f -> prog / 0.08f; prog > 0.9f -> (1f - prog) / 0.1f; else -> 1f }
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            // žiara
            p.shader = LinearGradient(tx, ty, hx, hy, alpha(col, 0f), alpha(col, 0.45f * fade), Shader.TileMode.CLAMP)
            p.strokeWidth = periodS * 0.55f
            c.drawLine(tx, ty, hx, hy, p)
            // jadro lúča
            p.shader = LinearGradient(tx, ty, hx, hy, alpha(col, 0f), alpha(Color.WHITE, 0.95f * fade), Shader.TileMode.CLAMP)
            p.strokeWidth = max(2f, periodS * 0.14f)
            c.drawLine(tx, ty, hx, hy, p)
            // svetlo na čele lúča
            p.style = Paint.Style.FILL
            p.shader = RadialGradient(hx, hy, periodS * 1.6f, intArrayOf(alpha(Color.WHITE, 0.9f * fade), alpha(col, 0.5f * fade), alpha(col, 0f)), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(hx, hy, periodS * 1.6f, p)
            p.shader = null
        }
    }
}

