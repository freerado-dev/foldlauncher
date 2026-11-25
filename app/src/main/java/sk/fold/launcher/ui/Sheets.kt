package sk.fold.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.fold.launcher.data.AppInfo
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.IconCache
import sk.fold.launcher.data.ItemType
import sk.fold.launcher.data.Screen
import sk.fold.launcher.data.Settings
import sk.fold.launcher.data.Store
import sk.fold.launcher.data.Widgets
import sk.fold.launcher.data.labelOf
import sk.fold.launcher.data.norm
import java.text.Collator

@Composable
fun FullSheet(title: String, onClose: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť") }
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                actions()
            }
            content()
        }
    }
}

// ---------------- widgety ----------------

private class WidgetGroup(val pkg: String, val label: String, val items: List<AppWidgetProviderInfo>)

@Composable
fun WidgetPicker(ui: LauncherUi, screen: Screen) {
    val ctx = LocalContext.current
    val groups by produceState<List<WidgetGroup>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            Widgets.providers().groupBy { it.provider.packageName }.map { (pkg, list) ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                WidgetGroup(pkg, label, list)
            }.sortedWith(compareBy(Collator.getInstance()) { it.label })
        }
    }
    FullSheet("Pridať na plochu", onClose = { ui.sheet = null }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionTitle("Fold Launcher") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { ElementCard("Hodiny", "Čas a dátum", Icons.Filled.Schedule) { ui.sheet = null; ui.addElement(ItemType.CLOCK) } }
                    item { ElementCard("Obľúbené", "Zoznam ako Niagara", Icons.Filled.Star) { ui.sheet = null; ui.addElement(ItemType.FAVORITES) } }
                    item { ElementCard("Vyhľadávanie", "Lišta hľadania", Icons.Filled.Search) { ui.sheet = null; ui.addElement(ItemType.SEARCH) } }
                    item { ElementCard("Priečinok", "Skupina aplikácií", Icons.Filled.Folder) { ui.addElement(ItemType.FOLDER) } }
                }
            }
            val g = groups
            if (g == null) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else items(g, key = { it.pkg }) { grp ->
                SectionTitle(grp.label)
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(grp.items, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                        WidgetCard(info, screen) { ui.addWidget(info) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ElementCard(title: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

@Composable
private fun WidgetCard(info: AppWidgetProviderInfo, screen: Screen, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics.density
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
                d?.let { IconCache.toBitmap(it, 480).asImageBitmap() }
            }.getOrNull()
        }
    }
    val label = remember(info) { runCatching { info.loadLabel(ctx.packageManager) }.getOrNull() ?: "" }
    val (w, h) = remember(info) { Widgets.span(screen, info, density) }
    Surface(
        Modifier.width(170.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                val b = preview
                if (b != null) Image(b, null, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(6.dp))
            Text(label, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            Text("$w × $h buniek", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------- výber aplikácií ----------------

@Composable
fun AppPicker(title: String, initial: List<String>, onClose: () -> Unit, onDone: (List<String>) -> Unit) {
    val apps by Apps.list.collectAsState()
    val s by Store.settings.collectAsState()
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var q by remember { mutableStateOf("") }
    val sorted = remember(apps, s.renames) {
        val c = Collator.getInstance()
        apps.map { it to labelOf(it, s) }.sortedWith(compareBy(c) { it.second })
    }
    val shown = remember(sorted, q) { if (q.isBlank()) sorted else sorted.filter { norm(it.second).contains(norm(q)) } }
    FullSheet(title, onClose = onClose, actions = {
        TextButton(onClick = { onDone(selected.toList()) }) { Text("Hotovo (${selected.size})") }
    }) {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Hľadať") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(24.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(shown, key = { it.first.key }) { (a, label) ->
                val on = a.key in selected
                Row(
                    Modifier.fillMaxWidth().clickable { if (on) selected.remove(a.key) else selected.add(a.key) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(a, 36.dp, s)
                    Spacer(Modifier.width(14.dp))
                    Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Checkbox(checked = on, onCheckedChange = { if (on) selected.remove(a.key) else selected.add(a.key) })
                }
            }
        }
    }
}

// ---------------- priečinok ----------------

@Composable
fun FolderSheet(ui: LauncherUi, screen: Screen, id: Long) {
    val layout by Store.layout(screen).collectAsState()
    val apps by Apps.list.collectAsState()
    val s by Store.settings.collectAsState()
    val item = layout.items.find { it.id == id }
    if (item == null) { ui.sheet = null; return }
    val inside = remember(apps, item.apps) { item.apps.mapNotNull { k -> apps.firstOrNull { it.key == k } } }
    Dialog(onDismissRequest = { ui.sheet = null }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label ?: "Priečinok", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { ui.sheet = Sheet.RenameItem(id) }) { Icon(Icons.Filled.Edit, "Premenovať") }
                    IconButton(onClick = { ui.sheet = Sheet.FolderApps(id) }) { Icon(Icons.Filled.Add, "Pridať") }
                }
                if (inside.isEmpty()) Text("Priečinok je prázdny – pridaj aplikácie tlačidlom +", Modifier.padding(vertical = 24.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(84.dp),
                    modifier = Modifier.heightIn(max = 460.dp),
                ) {
                    items(inside, key = { it.key }) { a ->
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).clickable { Apps.launch(ui.activity, a); ui.sheet = null }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIconImage(a, 52.dp, s)
                            Spacer(Modifier.height(4.dp))
                            Text(labelOf(a, s), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- obľúbené ----------------

@Composable
fun FavoritesEditor(onClose: () -> Unit) {
    val s by Store.settings.collectAsState()
    val apps by Apps.list.collectAsState()
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        AppPicker("Obľúbené aplikácie", s.favorites, onClose = { picking = false }) { keys ->
            // zachová poradie existujúcich, nové pridá na koniec
            Store.editSettings { st -> st.copy(favorites = st.favorites.filter { it in keys } + keys.filter { it !in st.favorites }) }
            picking = false
        }
        return
    }
    FullSheet("Obľúbené", onClose = onClose, actions = {
        IconButton(onClick = { picking = true }) { Icon(Icons.Filled.Add, "Pridať") }
    }) {
        Text(
            "Poradie zodpovedá zoznamu obľúbených na ploche. Notifikácie sa zobrazujú pod názvom.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(s.favorites, key = { _, k -> k }) { i, k ->
                val a = apps.firstOrNull { it.key == k }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (a != null) AppIconImage(a, 36.dp, s) else Spacer(Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(a?.let { labelOf(it, s) } ?: k.substringBefore('/'), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(enabled = i > 0, onClick = { Store.editSettings { st -> st.copy(favorites = st.favorites.swap(i, i - 1)) } }) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Hore")
                    }
                    IconButton(enabled = i < s.favorites.size - 1, onClick = { Store.editSettings { st -> st.copy(favorites = st.favorites.swap(i, i + 1)) } }) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Dole")
                    }
                    IconButton(onClick = { Store.editSettings { st -> st.copy(favorites = st.favorites - k) } }) {
                        Icon(Icons.Filled.Close, "Odobrať")
                    }
                }
            }
        }
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> {
    if (a !in indices || b !in indices) return this
    val m = toMutableList()
    val t = m[a]; m[a] = m[b]; m[b] = t
    return m
}

// ---------------- dialógy ----------------

@Composable
fun TextDialog(title: String, initial: String, hint: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Uložiť") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
}

@Composable
fun SheetHost(ui: LauncherUi, screen: Screen, s: Settings) {
    when (val sh = ui.sheet) {
        null -> {}
        Sheet.Widgets -> WidgetPicker(ui, screen)
        Sheet.AddApps -> AppPicker("Pridať aplikácie na plochu", emptyList(), onClose = { ui.sheet = null }) { keys ->
            ui.sheet = null
            keys.forEach { ui.addApp(it) }
        }
        Sheet.Settings -> SettingsScreen(ui, screen) { ui.sheet = null }
        Sheet.Favorites -> FavoritesEditor { ui.sheet = null }
        is Sheet.Folder -> FolderSheet(ui, screen, sh.id)
        is Sheet.FolderApps -> {
            val item = Store.layout(screen).value.items.find { it.id == sh.id }
            AppPicker("Obsah priečinka", item?.apps ?: emptyList(), onClose = { ui.sheet = null }) { keys ->
                Store.editLayout(screen) { l -> l.update(sh.id) { it.copy(apps = keys) } }
                ui.sheet = Sheet.Folder(sh.id)
            }
        }
        is Sheet.Rename -> {
            val a: AppInfo? = Apps.find(sh.key)
            TextDialog("Premenovať", a?.let { labelOf(it, s) } ?: "", "Prázdne = pôvodný názov (${a?.label ?: ""})",
                onDismiss = { ui.sheet = null }) { name ->
                Store.editSettings { st -> st.copy(renames = if (name.isEmpty()) st.renames - sh.key else st.renames + (sh.key to name)) }
                ui.sheet = null
            }
        }
        is Sheet.RenameItem -> {
            val item = Store.layout(screen).value.items.find { it.id == sh.id }
            TextDialog("Názov na ploche", item?.let { itemTitle(it, s) } ?: "", "Prázdne = pôvodný názov",
                onDismiss = { ui.sheet = null }) { name ->
                Store.editLayout(screen) { l -> l.update(sh.id) { it.copy(label = name.ifEmpty { null }) } }
                ui.sheet = if (item?.type == ItemType.FOLDER && !ui.editMode) Sheet.Folder(sh.id) else null
            }
        }
        Sheet.RemovePage -> AlertDialog(
            onDismissRequest = { ui.sheet = null },
            title = { Text("Odstrániť stránku?") },
            text = { Text("Všetky prvky na tejto stránke sa odstránia z plochy.") },
            confirmButton = {
                TextButton(onClick = {
                    val l = Store.layout(screen).value
                    l.items.filter { it.page == ui.page && it.type == ItemType.WIDGET }.forEach { Widgets.delete(it.widgetId) }
                    Store.editLayout(screen) { it.removePage(ui.page) }
                    ui.sheet = null
                }) { Text("Odstrániť") }
            },
            dismissButton = { TextButton(onClick = { ui.sheet = null }) { Text("Zrušiť") } },
        )
    }
}
