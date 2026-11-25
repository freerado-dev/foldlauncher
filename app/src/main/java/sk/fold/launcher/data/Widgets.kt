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
}

class LauncherWidgetHost(ctx: Context, id: Int) : AppWidgetHost(ctx, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LauncherWidgetView(context)
}

/** Host view, ktorý rozpozná dlhé podržanie aj nad interaktívnym widgetom. */
class LauncherWidgetView(ctx: Context) : AppWidgetHostView(ctx) {
    var onLongPress: (() -> Unit)? = null
    private var longPressed = false
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val lp = Runnable {
        if (isAttachedToWindow) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (longPressed) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) longPressed = false
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                removeCallbacks(lp)
                postDelayed(lp, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(lp)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> removeCallbacks(lp)
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (longPressed) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) longPressed = false
            return true
        }
        // Ak dotyk nespracuje žiadna časť widgetu, dostaneme ho sem – sledujeme ho kvôli dlhému podržaniu.
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE ->
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(lp)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(lp)
        }
        super.onTouchEvent(ev)
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        removeCallbacks(lp)
    }
}
