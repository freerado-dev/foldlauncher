package sk.fold.launcher.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/** Služba dostupnosti len na zamknutie obrazovky a otvorenie notifikácií. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: LockService? = null
    }
}

object SysActions {
    fun openAccessibility(ctx: Context) {
        Toast.makeText(ctx, "Zapni „Fold Launcher – gestá“ v Dostupnosti → Nainštalované aplikácie", Toast.LENGTH_LONG).show()
        runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun lock(ctx: Context) {
        val s = LockService.instance
        if (s == null) openAccessibility(ctx)
        else s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun expand(ctx: Context, quick: Boolean) {
        LockService.instance?.let {
            it.performGlobalAction(
                if (quick) AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                else AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            )
            return
        }
        try {
            @Suppress("WrongConstant")
            val sbm = ctx.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod(if (quick) "expandSettingsPanel" else "expandNotificationsPanel")
                .invoke(sbm)
        } catch (e: Throwable) {
            openAccessibility(ctx)
        }
    }
}
