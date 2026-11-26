package sk.fold.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sk.fold.launcher.data.LauncherTheme
import sk.fold.launcher.data.Settings
import kotlin.math.cos
import kotlin.math.sin

/** Farby témy Hologram – tmavomodré sklo a tyrkysové žiarenie. */
object Holo {
    val Cyan = Color(0xFF3ED6FF)
    val CyanSoft = Color(0xFF8BE9FF)
    val Text = Color(0xFFE2F7FF)
    val Glass = Color(0x4410284A)
    val GlassDeep = Color(0xEE06101E)
    val Navy = Color(0xFF050B16)
}

val Settings.holo: Boolean get() = launcherTheme == LauncherTheme.HOLO

/**
 * Sklo: matná výplň, šikmý odlesk svetla prechádzajúci cez sklo a žiarenie po hranách,
 * ktoré slabne smerom dovnútra. Odlesk a hrany sa kreslia NAD obsah, takže svetlo ide „cez sklo“.
 */
fun Modifier.glass(s: Settings, radius: Dp = 18.dp, fill: Boolean = true): Modifier {
    if (!s.holo) return this
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).drawWithContent {
        val w = size.width
        val h = size.height
        val r = radius.toPx()
        if (fill) drawRect(Brush.verticalGradient(listOf(Color(0x5A1A3C64), Color(0x300A1A30), Color(0x40102A48))))
        drawContent()
        // svetlo prechádzajúce cez sklo – šikmý gradient od ľavého horného rohu až k hranám
        drawRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.22f),
                0.28f to Color.White.copy(alpha = 0.06f),
                0.5f to Color.Transparent,
                0.82f to Holo.Cyan.copy(alpha = 0.05f),
                1f to Holo.Cyan.copy(alpha = 0.16f),
                start = Offset.Zero, end = Offset(w, h),
            )
        )
        // žiarenie po hranách – niekoľko vrstiev, slabnú smerom dovnútra
        val edge = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.9f), Holo.Cyan.copy(alpha = 0.35f), Holo.Cyan.copy(alpha = 0.15f), Color.White.copy(alpha = 0.5f)),
            start = Offset.Zero, end = Offset(w, h),
        )
        val stroke = 1.2.dp.toPx()
        for (i in 0 until 5) {
            val inset = i * 1.6.dp.toPx() + stroke / 2
            drawRoundRect(
                edge, topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2),
                cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f)), alpha = 0.9f / (1 + i * 1.6f),
                style = Stroke(if (i == 0) stroke else stroke * 1.6f),
            )
        }
        // lesklá horná hrana
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent), startX = r, endX = w - r),
            Offset(r, stroke), Offset(w - r, stroke), stroke,
        )
    }
}

/** Panel na skle s vnútorným odsadením (hodiny, obľúbené…). */
fun Modifier.holoPanel(s: Settings, pad: Dp = 8.dp, radius: Dp = 16.dp): Modifier =
    if (!s.holo) this else this.glass(s, radius, fill = true).padding(pad)

/** Widget „na skle“ – bez výplne (widget má vlastné pozadie), so svetlom a hranami cez obsah. */
fun Modifier.holoFrame(s: Settings, radius: Dp = 18.dp): Modifier = glass(s, radius, fill = false)

/** Pôvodný animovaný holografický kruh – otáčajúce sa segmenty, stupnica a žiariace jadro. */
@Composable
fun HoloRing(modifier: Modifier = Modifier.fillMaxSize()) {
    val t = rememberInfiniteTransition(label = "ring")
    val a1 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "a1")
    val a2 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(15000, easing = LinearEasing)), label = "a2")
    val pulse by t.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "p")
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        fun ring(rad: Float) = Pair(Offset(c.x - rad, c.y - rad), Size(rad * 2, rad * 2))
        drawCircle(Brush.radialGradient(listOf(Holo.Cyan.copy(alpha = 0.28f * pulse), Color.Transparent), c, r), r)
        // vonkajšie segmenty
        rotate(a1, c) {
            val (tl, sz) = ring(r * 0.9f)
            for (i in 0 until 10) drawArc(Holo.Cyan.copy(alpha = 0.85f), i * 36f, 24f, false, tl, sz, style = Stroke(r * 0.035f))
        }
        // stupnica
        rotate(-a2 * 0.5f, c) {
            for (i in 0 until 72) {
                val ang = Math.toRadians(i * 5.0)
                val inner = if (i % 6 == 0) r * 0.66f else r * 0.71f
                val outer = r * 0.76f
                drawLine(
                    Holo.CyanSoft.copy(alpha = if (i % 6 == 0) 0.9f else 0.45f),
                    Offset(c.x + cos(ang).toFloat() * inner, c.y + sin(ang).toFloat() * inner),
                    Offset(c.x + cos(ang).toFloat() * outer, c.y + sin(ang).toFloat() * outer),
                    strokeWidth = r * 0.012f,
                )
            }
        }
        drawCircle(Holo.Cyan.copy(alpha = 0.5f), r * 0.56f, c, style = Stroke(r * 0.012f))
        // vnútorné oblúky
        rotate(a2, c) {
            val (tl, sz) = ring(r * 0.46f)
            drawArc(Holo.Cyan, 0f, 100f, false, tl, sz, style = Stroke(r * 0.05f))
            drawArc(Holo.Cyan, 180f, 100f, false, tl, sz, style = Stroke(r * 0.05f))
        }
        rotate(-a1 * 2f, c) {
            val (tl, sz) = ring(r * 0.36f)
            drawArc(Holo.CyanSoft.copy(alpha = 0.8f), 60f, 60f, false, tl, sz, style = Stroke(r * 0.02f))
            drawArc(Holo.CyanSoft.copy(alpha = 0.8f), 240f, 60f, false, tl, sz, style = Stroke(r * 0.02f))
        }
        // jadro
        drawCircle(
            Brush.radialGradient(listOf(Color.White, Holo.Cyan.copy(alpha = 0.9f), Color.Transparent), c, r * 0.26f * pulse),
            r * 0.26f, c,
        )
    }
}
