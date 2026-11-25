package sk.fold.launcher.ui

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sk.fold.launcher.MainActivity
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.GestureAction
import sk.fold.launcher.data.HomeItem
import sk.fold.launcher.data.ItemType
import sk.fold.launcher.data.Screen
import sk.fold.launcher.data.Store
import sk.fold.launcher.data.SysActions
import sk.fold.launcher.data.Widgets

sealed interface Sheet {
    data object Widgets : Sheet
    data object AddApps : Sheet
    data object Settings : Sheet
    data object Favorites : Sheet
    data class Folder(val id: Long) : Sheet
    data class FolderApps(val id: Long) : Sheet
    data class Rename(val key: String) : Sheet
    data class RenameItem(val id: Long) : Sheet
    data object RemovePage : Sheet
}

/** Stav používateľského rozhrania launchera. */
class LauncherUi(val activity: MainActivity) {
    var screen by mutableStateOf(Screen.COVER)
    var drawerOpen by mutableStateOf(false)
    var searchFocus by mutableStateOf(false)
    var editMode by mutableStateOf(false)
    var selectedId by mutableStateOf<Long?>(null)
    var sheet by mutableStateOf<Sheet?>(null)
    var page by mutableIntStateOf(0)
    var requestPage by mutableStateOf<Int?>(null)
    var homeSignal by mutableIntStateOf(0)
    var resumeTick by mutableIntStateOf(0)
    var cellW by mutableFloatStateOf(70f)
    var cellH by mutableFloatStateOf(70f)

    fun showPage(p: Int) { requestPage = p }

    fun openDrawer(search: Boolean = false) {
        searchFocus = search
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
        searchFocus = false
    }

    fun startEdit(select: Long? = null) {
        if (Store.settings.value.lockLayout) {
            activity.toast("Rozloženie je zamknuté (Nastavenia → Plocha)")
            return
        }
        editMode = true
        selectedId = select
    }

    fun stopEdit() {
        editMode = false
        selectedId = null
    }

    fun back() {
        when {
            sheet != null -> sheet = null
            drawerOpen -> closeDrawer()
            editMode -> stopEdit()
        }
    }

    fun home() {
        if (sheet != null || drawerOpen || editMode) {
            sheet = null; closeDrawer(); stopEdit()
        } else homeSignal++
    }

    fun gesture(a: GestureAction) {
        when (a) {
            GestureAction.NONE -> {}
            GestureAction.DRAWER -> openDrawer()
            GestureAction.SEARCH -> openDrawer(search = true)
            GestureAction.NOTIFICATIONS -> SysActions.expand(activity, false)
            GestureAction.QUICK_SETTINGS -> SysActions.expand(activity, true)
            GestureAction.LOCK -> SysActions.lock(activity)
            GestureAction.EDIT -> startEdit()
            GestureAction.SETTINGS -> sheet = Sheet.Settings
        }
    }

    fun tap(item: HomeItem) {
        when (item.type) {
            ItemType.APP -> Apps.find(item.app)?.let { Apps.launch(activity, it) }
                ?: activity.toast("Aplikácia nie je nainštalovaná")
            ItemType.SHORTCUT -> {
                val u = Apps.userFor(item.serial)
                if (u != null && item.pkg != null && item.shortcutId != null) Apps.startShortcut(activity, item.pkg, item.shortcutId, u)
            }
            ItemType.FOLDER -> sheet = Sheet.Folder(item.id)
            ItemType.CLOCK -> runCatching {
                activity.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            ItemType.SEARCH -> openDrawer(search = true)
            else -> {}
        }
    }

    // ---------- pridávanie ----------

    fun addApp(key: String) {
        Store.lastPage = page
        showPage(Store.addApp(screen, key, page))
    }

    fun addElement(type: ItemType) {
        Store.lastPage = page
        val (w, h) = Store.defaultSpan(screen, type)
        val item = HomeItem(Store.newId(), type, page, 0, 0, w, h, label = if (type == ItemType.FOLDER) "Priečinok" else null)
        showPage(Store.add(screen, item, page))
        if (type == ItemType.FOLDER) sheet = Sheet.FolderApps(item.id)
    }

    fun addWidget(info: android.appwidget.AppWidgetProviderInfo) {
        Store.lastPage = page
        sheet = null
        activity.startAddWidget(info)
    }

    fun pinShortcut(sc: android.content.pm.ShortcutInfo) {
        if (!Apps.pinShortcut(sc)) { activity.toast("Skratku sa nepodarilo pripnúť – nastav Fold Launcher ako predvolený"); return }
        Store.lastPage = page
        val (w, h) = Store.appSpan(screen)
        showPage(Store.add(screen, HomeItem(Store.newId(), ItemType.SHORTCUT, page, 0, 0, w, h,
            shortcutId = sc.id, pkg = sc.`package`, serial = Apps.serialOf(sc.userHandle), label = sc.shortLabel?.toString()), page))
    }

    fun remove(item: HomeItem) {
        if (item.type == ItemType.WIDGET) Widgets.delete(item.widgetId)
        Store.editLayout(screen) { it.remove(item.id) }
        if (selectedId == item.id) selectedId = null
    }

    fun moveToPage(item: HomeItem, target: Int) {
        if (target < 0) return
        var ok = false
        Store.editLayout(screen) { l -> l.moveToPage(item.id, target)?.also { ok = true } ?: l }
        if (ok) showPage(target) else activity.toast("Na stránke nie je miesto")
    }
}
