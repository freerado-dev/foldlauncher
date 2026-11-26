package sk.fold.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.ViewConfiguration
import kotlin.math.abs

object Widgets {
    const val HOST_ID = 1077
    lateinit var host: LauncherWidgetHost
    lateinit var manager: AppWidgetManager

    fun init(ctx: Context) {
        manager = AppWidgetManager.getInstance(ctx)
        host = LauncherWidgetHost(ctx.applicationContext, HOST_ID)
    }

    fun providers(): List<AppWidgetProviderInfo> =
        Apps.um.userProfiles.flatMap { u ->
            runCatching { manager.getInstalledProvidersForProfile(u) }.getOrDefault(emptyList())
        }

    /** Počet buniek pre widget podľa jeho minimálnej veľkosti. */
    fun span(s: Screen, info: AppWidgetProviderInfo, density: Float): Pair<Int, Int> =
        Store.span(s, info.minWidth / density + 8f, info.minHeight / density + 8f, roundUp = true)

    fun updateSize(v: AppWidgetHostView, wDp: Float, hDp: Float) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) v.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp)))
            else {
                @Suppress("DEPRECATION")
                v.updateAppWidgetSize(null, wDp.toInt(), hDp.toInt(), wDp.toInt(), hDp.toInt())
            }
        }
    }

    fun delete(id: Int) {
        if (id >= 0) runCatching { host.deleteAppWidgetId(id) }
    }

    /** Uvoľní widget len ak ho nepoužíva iné rozloženie (napr. na šírku / na výšku). */
    fun release(id: Int, itemId: Long) {
        if (!Store.widgetUsedElsewhere(id, itemId)) delete(id)
    }
}

class LauncherWidgetHost(ctx: Context, id: Int) : AppWidgetHost(ctx, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LauncherWidgetView(context)
}

/**
 * Host view, ktorý rozpozná dlhé podržanie aj nad interaktívnym widgetom, po ňom widget rovno ťahá
 * a zvislé potiahnutie (ak sa obsah widgetu nedá posúvať) odovzdá ploche.
 */
class LauncherWidgetView(ctx: Context) : AppWidgetHostView(ctx) {
    var onLongPress: (() -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragEnd: ((Boolean) -> Unit)? = null
    /** -1 = potiahnutie nahor, +1 = nadol. */
    var onSwipe: ((Int) -> Unit)? = null
    /** Vo feede: zvislé ťahanie posúva zoznam (posun v px) a pustenie odovzdá rýchlosť (px/s). */
    var onScrollDelta: ((Float) -> Unit)? = null
    var onScrollEnd: ((Float) -> Unit)? = null
    /** Vodorovné potiahnutie → listovanie stránok (+1 = ďalšia, -1 = predchádzajúca). */
    var onPageSwipe: ((Int) -> Unit)? = null
    private var scrolling = false
    private var lastScrollY = 0f
    private var vt: VelocityTracker? = null

    private var longPressed = false
    private var dragging = false
    private var swiped = false
    private var downX = 0f
    private var downY = 0f
    private var startRawX = 0f
    private var startRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val lp = Runnable {
        if (isAttachedToWindow && !swiped) {
            longPressed = true
            startRawX = lastRawX; startRawY = lastRawY
            if (Store.settings.value.haptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }
    }

    private fun canScrollH(v: View, x: Float, y: Float, dir: Int): Boolean {
        if (v !== this && v.canScrollHorizontally(dir)) return true
        if (v is ViewGroup) for (i in v.childCount - 1 downTo 0) {
            val c = v.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            val cx = x + v.scrollX - c.left
            val cy = y + v.scrollY - c.top
            if (cx >= 0 && cy >= 0 && cx < c.width && cy < c.height && canScrollH(c, cx, cy, dir)) return true
        }
        return false
    }

    private fun addVelocity(ev: MotionEvent) {
        val e = MotionEvent.obtain(ev)
        e.setLocation(ev.rawX, ev.rawY)
        vt?.addMovement(e)
        e.recycle()
    }

    private fun canScroll(v: View, x: Float, y: Float, dir: Int): Boolean {
        if (v !== this && v.canScrollVertically(dir)) return true
        if (v is ViewGroup) for (i in v.childCount - 1 downTo 0) {
            val c = v.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            val cx = x + v.scrollX - c.left
            val cy = y + v.scrollY - c.top
            if (cx >= 0 && cy >= 0 && cx < c.width && cy < c.height && canScroll(c, cx, cy, dir)) return true
        }
        return false
    }

    /** Vráti true, ak gesto prevzala plocha (potiahnutie). */
    private fun track(ev: MotionEvent): Boolean {
        lastRawX = ev.rawX; lastRawY = ev.rawY
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                longPressed = false; dragging = false; swiped = false; scrolling = false
                vt?.recycle(); vt = VelocityTracker.obtain(); addVelocity(ev)
                removeCallbacks(lp)
                postDelayed(lp, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (!longPressed && !swiped) {
                addVelocity(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > slop || abs(dy) > slop) removeCallbacks(lp)
                if (onPageSwipe != null && abs(dx) > slop * 2 && abs(dx) > abs(dy) * 1.5f) {
                    val dir = if (dx < 0) 1 else -1
                    if (!canScrollH(this, downX, downY, dir)) {
                        swiped = true
                        onPageSwipe?.invoke(dir)
                        return true
                    }
                }
                if (abs(dy) > slop * 2 && abs(dy) > abs(dx) * 1.5f) {
                    val dir = if (dy > 0) 1 else -1
                    // obsah, ktorý sa dá posúvať v smere prsta, má prednosť
                    if (!canScroll(this, downX, downY, -dir)) {
                        swiped = true
                        if (onScrollDelta != null) {
                            scrolling = true
                            lastScrollY = ev.rawY
                            onScrollDelta?.invoke(dy)
                        } else onSwipe?.invoke(dir)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> removeCallbacks(lp)
        }
        return false
    }

    private fun handleLongPressed(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(ev.rawX - startRawX) + abs(ev.rawY - startRawY) > slop) {
                    dragging = true
                    onDragStart?.invoke()
                }
                if (dragging) onDrag?.invoke(ev.rawX - lastRawX, ev.rawY - lastRawY)
                lastRawX = ev.rawX; lastRawY = ev.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd?.invoke(ev.actionMasked == MotionEvent.ACTION_UP)
                longPressed = false; dragging = false
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (longPressed || swiped) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (longPressed) handleLongPressed(ev)
                swiped = false
            }
            return true
        }
        return track(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (longPressed) { handleLongPressed(ev); return true }
        if (swiped) {
            if (scrolling) {
                addVelocity(ev)
                when (ev.actionMasked) {
                    MotionEvent.ACTION_MOVE -> { onScrollDelta?.invoke(ev.rawY - lastScrollY); lastScrollY = ev.rawY }
                    MotionEvent.ACTION_UP -> {
                        vt?.computeCurrentVelocity(1000)
                        onScrollEnd?.invoke(vt?.yVelocity ?: 0f)
                        scrolling = false
                    }
                    MotionEvent.ACTION_CANCEL -> { onScrollEnd?.invoke(0f); scrolling = false }
                }
            }
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) swiped = false
            return true
        }
        // Dotyk, ktorý nespracovala žiadna časť widgetu, sledujeme sami (dlhé podržanie, potiahnutie).
        track(ev)
        super.onTouchEvent(ev)
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        removeCallbacks(lp)
    }
}
