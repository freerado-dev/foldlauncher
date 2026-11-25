package sk.fold.launcher.data

import android.accessibilityservice.AccessibilityService
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
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

/** Náhradné zamykanie cez správcu zariadenia (keď je služba dostupnosti blokovaná). */
class LockAdmin : DeviceAdminReceiver()

object SysActions {
    fun adminComponent(ctx: Context) = ComponentName(ctx, LockAdmin::class.java)

    fun adminActive(ctx: Context): Boolean =
        runCatching { ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(ctx)) }.getOrDefault(false)

    fun requestAdmin(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(ctx))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Fold Launcher zamkne obrazovku dvojitým ťuknutím. Po takomto zamknutí môže systém pri prvom odomknutí vyžadovať PIN namiesto odtlačku.")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openAccessibility(ctx: Context) {
        Toast.makeText(ctx, "Zapni „Fold Launcher – gestá“ v Dostupnosti → Nainštalované aplikácie", Toast.LENGTH_LONG).show()
        runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun lock(ctx: Context) {
        val s = LockService.instance
        when {
            s != null -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            adminActive(ctx) -> runCatching { ctx.getSystemService(DevicePolicyManager::class.java).lockNow() }
            else -> {
                Toast.makeText(ctx, "Na zamykanie povoľ službu gest alebo správcu zariadenia (Nastavenia launchera → Povolenia)", Toast.LENGTH_LONG).show()
                requestAdmin(ctx)
            }
        }
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
