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
    fun enabled(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
}

/** Počúva notifikácie len kvôli odznakom a textu v obľúbených. */
class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onListenerDisconnected() { Badges.state.value = emptyMap() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        val out = HashMap<String, NotifInfo>()
        list.filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .sortedBy { it.postTime }
            .forEach { sbn ->
                val ex = sbn.notification.extras
                val prev = out[sbn.packageName]
                out[sbn.packageName] = NotifInfo(
                    (prev?.count ?: 0) + 1,
                    ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: prev?.title,
                    ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: prev?.text,
                )
            }
        Badges.state.value = out
    }

    companion object {
        fun component(ctx: Context) = ComponentName(ctx, BadgeService::class.java)
    }
}
