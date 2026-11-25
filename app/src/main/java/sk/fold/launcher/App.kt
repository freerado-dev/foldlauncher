package sk.fold.launcher

import android.app.Application
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.Store
import sk.fold.launcher.data.Widgets

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Widgets.init(this)
        Apps.init(this)
    }
}
