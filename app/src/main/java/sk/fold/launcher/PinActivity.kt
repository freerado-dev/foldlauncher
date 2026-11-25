package sk.fold.launcher

import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.HomeItem
import sk.fold.launcher.data.ItemType
import sk.fold.launcher.data.Store
import sk.fold.launcher.data.Widgets

/** Prijíma požiadavky aplikácií na pripnutie skratky alebo widgetu na plochu. */
class PinActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            val la = getSystemService(LauncherApps::class.java)
            val req = la.getPinItemRequest(intent)
            if (req != null && req.isValid) {
                val screen = Store.lastScreen
                when (req.requestType) {
                    LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                        val sc = req.shortcutInfo
                        if (sc != null && req.accept()) {
                            val (w, h) = Store.appSpan(screen)
                            Store.add(screen, HomeItem(Store.newId(), ItemType.SHORTCUT, Store.lastPage, 0, 0, w, h,
                                shortcutId = sc.id, pkg = sc.`package`, serial = Apps.serialOf(sc.userHandle),
                                label = sc.shortLabel?.toString()))
                            Toast.makeText(this, "Pridané na plochu", Toast.LENGTH_SHORT).show()
                        }
                    }
                    LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> {
                        val info = req.getAppWidgetProviderInfo(this)
                        if (info != null) {
                            val id = Widgets.host.allocateAppWidgetId()
                            val ok = req.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) })
                            if (ok) {
                                val (w, h) = Widgets.span(screen, info, resources.displayMetrics.density)
                                Store.add(screen, HomeItem(Store.newId(), ItemType.WIDGET, Store.lastPage, 0, 0, w, h, widgetId = id))
                                Toast.makeText(this, "Widget pridaný na plochu", Toast.LENGTH_SHORT).show()
                            } else Widgets.delete(id)
                        }
                    }
                }
            }
        }
        finish()
    }
}
