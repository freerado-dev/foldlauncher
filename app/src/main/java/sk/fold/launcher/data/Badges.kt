package sk.fold.launcher.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow

data class NotifInfo(val count: Int, val title: String?, val text: String?)

object Badges {
    val state = MutableStateFlow<Map<String, NotifInfo>>(emptyMap())
    /** true, keď je služba skutočne pripojená k systému (nie len povolená). */
    val connected = MutableStateFlow(false)

    /** Po aktualizácii APK systém službu často nepripojí znova – vyžiadame to. */
    fun rebind(ctx: Context) {
        if (enabled(ctx) && !connected.value) runCatching {
            NotificationListenerService.requestRebind(BadgeService.component(ctx))
        }
    }
    fun enabled(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
}

/** Počúva notifikácie len kvôli odznakom a textu v obľúbených. */
class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() {
        Badges.connected.value = true
        refresh()
    }
    override fun onListenerDisconnected() {
        Badges.connected.value = false
        Badges.state.value = emptyMap()
        runCatching { NotificationListenerService.requestRebind(component(this)) }
    }
    override fun onNotificationRankingUpdate(rankingMap: NotificationListenerService.RankingMap?) = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        val out = HashMap<String, NotifInfo>()
        list.filter { !it.isOngoing && it.packageName != packageName }
            .groupBy { it.packageName }
            .forEach { (pkg, all) ->
                // Súhrnné notifikácie skupín nepočítame, ak má aplikácia aj jednotlivé správy.
                val real = all.filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
                val use = real.ifEmpty { all }.sortedBy { it.postTime }
                val last = use.last().notification.extras
                out[pkg] = NotifInfo(
                    use.size,
                    last?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                    (last?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: last?.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
                )
            }
        Badges.state.value = out
    }

    companion object {
        fun component(ctx: Context) = ComponentName(ctx, BadgeService::class.java)
    }
}
