package sk.fold.launcher.ui

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.fold.launcher.data.AppInfo
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.HomeItem
import sk.fold.launcher.data.IconCache
import sk.fold.launcher.data.ItemType
import sk.fold.launcher.data.Store

@Composable
private fun MenuRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun ShortcutRows(ui: LauncherUi, a: AppInfo, dismiss: () -> Unit) {
    val shortcuts = remember(a.key) { Apps.shortcuts(a) }
    shortcuts.forEach { sc -> ShortcutRow(ui, sc, dismiss) }
    if (shortcuts.isNotEmpty()) HorizontalDivider()
}

@Composable
private fun ShortcutRow(ui: LauncherUi, sc: ShortcutInfo, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, sc.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                Apps.la.getShortcutIconDrawable(sc, ctx.resources.displayMetrics.densityDpi)?.let { IconCache.toBitmap(it, 96).asImageBitmap() }
            }.getOrNull()
        }
    }
    DropdownMenuItem(
        text = { Text(sc.shortLabel?.toString() ?: sc.id) },
        leadingIcon = {
            val b = icon
            if (b != null) Image(b, null, Modifier.size(24.dp)) else Spacer(Modifier.size(24.dp))
        },
        trailingIcon = {
            IconButton(onClick = { ui.pinShortcut(sc); dismiss() }) { Icon(Icons.Filled.PushPin, "Pripnúť na plochu") }
        },
        onClick = { Apps.startShortcut(ui.activity, sc.`package`, sc.id, sc.userHandle); dismiss() },
    )
}

/** Menu po dlhom podržaní prvku na ploche. */
@Composable
fun ItemMenu(ui: LauncherUi, item: HomeItem, dismiss: () -> Unit) {
    val apps by Apps.list.collectAsState()
    val app = remember(apps, item.app) { apps.firstOrNull { it.key == item.app } }
    if (item.type == ItemType.APP && app != null) ShortcutRows(ui, app, dismiss)
    MenuRow("Presunúť / zmeniť veľkosť", Icons.Filled.OpenWith) { dismiss(); ui.startEdit(item.id) }
    when (item.type) {
        ItemType.FOLDER -> MenuRow("Upraviť obsah", Icons.Filled.Apps) { dismiss(); ui.sheet = Sheet.FolderApps(item.id) }
        ItemType.FAVORITES -> MenuRow("Upraviť obľúbené", Icons.Filled.Star) { dismiss(); ui.sheet = Sheet.Favorites }
        ItemType.CLOCK -> MenuRow("Nastavenia hodín a počasia", Icons.Filled.Schedule) { dismiss(); ui.sheet = Sheet.ClockSettings }
        ItemType.ASSISTANT -> MenuRow("Vybrať asistenta", Icons.Filled.Mic) { dismiss(); ui.sheet = Sheet.AssistantSettings }
        else -> {}
    }
    if (item.type == ItemType.APP || item.type == ItemType.FOLDER || item.type == ItemType.SHORTCUT)
        MenuRow("Premenovať", Icons.Filled.Edit) { dismiss(); ui.sheet = Sheet.RenameItem(item.id) }
    MenuRow("Odstrániť z plochy", Icons.Filled.Delete) { dismiss(); ui.remove(item) }
    if (app != null) {
        MenuRow("Informácie o aplikácii", Icons.Filled.Info) { dismiss(); Apps.details(ui.activity, app) }
        MenuRow("Odinštalovať", Icons.Filled.DeleteForever) { dismiss(); Apps.uninstall(ui.activity, app) }
    }
}

/** Menu po dlhom podržaní aplikácie v zozname. */
@Composable
fun AppMenu(ui: LauncherUi, a: AppInfo, dismiss: () -> Unit) {
    val s by Store.settings.collectAsState()
    ShortcutRows(ui, a, dismiss)
    MenuRow("Pridať na plochu", Icons.Filled.Home) {
        dismiss(); ui.addApp(a.key); ui.closeDrawer()
        ui.activity.toast("Pridané na plochu")
    }
    val fav = a.key in s.favorites
    MenuRow(if (fav) "Odobrať z obľúbených" else "Do obľúbených", if (fav) Icons.Filled.StarBorder else Icons.Filled.Star) {
        Store.editSettings { st -> st.copy(favorites = if (fav) st.favorites - a.key else st.favorites + a.key) }
        dismiss()
    }
    MenuRow("Premenovať", Icons.Filled.Edit) { dismiss(); ui.sheet = Sheet.Rename(a.key) }
    MenuRow("Skryť", Icons.Filled.VisibilityOff) {
        Store.editSettings { st -> st.copy(hidden = st.hidden + a.key) }
        dismiss()
        ui.activity.toast("Skryté – zobrazíš v Nastavenia → Skryté aplikácie")
    }
    MenuRow("Informácie o aplikácii", Icons.Filled.Info) { dismiss(); Apps.details(ui.activity, a) }
    MenuRow("Odinštalovať", Icons.Filled.DeleteForever) { dismiss(); Apps.uninstall(ui.activity, a) }
}
