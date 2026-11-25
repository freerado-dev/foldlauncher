package sk.fold.launcher

import android.app.Application
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.Store
import sk.fold.launcher.data.Widgets

class App : Application() {
    companion object { const val CRASH_FILE = "posledna-chyba.txt" }

    override fun onCreate() {
        super.onCreate()
        // Pri páde uložíme výpis chyby – launcher ho pri ďalšom štarte ukáže (dá sa skopírovať).
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { java.io.File(filesDir, CRASH_FILE).writeText(android.util.Log.getStackTraceString(e)) }
            prev?.uncaughtException(t, e)
        }
        Store.init(this)
        Widgets.init(this)
        Apps.init(this)
    }
}
