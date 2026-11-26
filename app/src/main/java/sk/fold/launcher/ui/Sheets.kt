@file:OptIn(ExperimentalFoundationApi::class)

package sk.fold.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import sk.fold.launcher.data.FOLDER_PREFIX
import sk.fold.launcher.data.FavFolder
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import sk.fold.launcher.data.Badges
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
                    item { ElementCard("Priečinok", "Skupina aplikácií", Icons.Filled.Folder) { ui.addElement(ItemType.FOLDER) } }
                    item { ElementCard("Hlasový asistent", "Gemini, Google, Bixby…", Icons.Filled.Mic) { ui.sheet = null; ui.addElement(ItemType.ASSISTANT) } }
                    item { ElementCard("Plávajúci asistent", "Kamkoľvek, nad všetkým", Icons.Filled.Mic) { ui.sheet = null; Store.editSettings { it.copy(floatAssist = true) } } }
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
fun AppPicker(
    title: String, initial: List<String>, onClose: () -> Unit,
    single: Boolean = false, extras: List<sk.fold.launcher.data.ExtraApp> = emptyList(),
    onDone: (List<String>) -> Unit,
) {
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
        if (single) TextButton(onClick = { onDone(emptyList()) }) { Text("Predvolené") }
        else TextButton(onClick = { onDone(selected.toList()) }) { Text("Hotovo (${selected.size})") }
    }) {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Hľadať") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(24.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            val ex = if (q.isBlank()) extras else extras.filter { norm(it.label).contains(norm(q)) }
            if (ex.isNotEmpty()) {
                item { SectionTitle("Bez ikony v zozname aplikácií") }
                items(ex, key = { it.key }) { e ->
                    val img = remember(e.key) { e.icon?.let { IconCache.toBitmap(it, 96).asImageBitmap() } }
                    Row(
                        Modifier.fillMaxWidth().clickable { onDone(listOf(e.key)) }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (img != null) Image(img, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(e.label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        RadioButton(selected = e.key in selected, onClick = { onDone(listOf(e.key)) })
                    }
                }
                item { SectionTitle("Všetky aplikácie") }
            }
            items(shown, key = { it.first.key }) { (a, label) ->
                val on = a.key in selected
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (single) onDone(listOf(a.key)) else if (on) selected.remove(a.key) else selected.add(a.key)
                    }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(a, 36.dp, s)
                    Spacer(Modifier.width(14.dp))
                    Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (single) RadioButton(selected = on, onClick = { onDone(listOf(a.key)) })
                    else Checkbox(checked = on, onCheckedChange = { if (on) selected.remove(a.key) else selected.add(a.key) })
                }
            }
        }
    }
}

// ---------------- priečinok ----------------

/** Okno priečinka – rovnaké pre priečinok na ploche aj v obľúbených. */
/** Tmavé „sklo“ s bielym textom – dobre čitateľné nad tapetou bez ohľadu na tému. */
private val PopupBg = Color(0xF21C1C20)
private val PopupText = Color(0xFFF2F2F5)
private val PopupSub = Color(0xB3F2F2F5)

@Composable
private fun PopupSurface(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = PopupBg, contentColor = PopupText) { content() }
}

@Composable
fun FolderDialog(
    ui: LauncherUi, name: String, keys: List<String>,
    onRename: () -> Unit, onAdd: () -> Unit, onRemoveApp: (String) -> Unit,
) {
    val apps by Apps.list.collectAsState()
    val s by Store.settings.collectAsState()
    val inside = remember(apps, keys) { keys.mapNotNull { k -> apps.firstOrNull { it.key == k } } }
    Dialog(onDismissRequest = { ui.sheet = null }) {
        PopupSurface {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).clickable { onRename() })
                    IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, "Premenovať") }
                    IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Pridať aplikácie") }
                }
                if (inside.isEmpty()) Text("Priečinok je prázdny – pridaj aplikácie tlačidlom +", Modifier.padding(vertical = 24.dp))
                else Text("Podrž aplikáciu pre odobratie z priečinka", style = MaterialTheme.typography.bodySmall, color = PopupSub)
                LazyVerticalGrid(columns = GridCells.Adaptive(84.dp), modifier = Modifier.heightIn(max = 460.dp)) {
                    items(inside, key = { it.key }) { a ->
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            Column(
                                Modifier.clip(RoundedCornerShape(14.dp))
                                    .combinedClickable(
                                        onClick = { Apps.launch(ui.activity, a); ui.sheet = null },
                                        onLongClick = { menu = true },
                                    )
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIconImage(a, 52.dp, s)
                                Spacer(Modifier.height(4.dp))
                                Text(labelOf(a, s), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Odobrať z priečinka") }, onClick = { menu = false; onRemoveApp(a.key) })
                                DropdownMenuItem(text = { Text("Informácie o aplikácii") }, onClick = { menu = false; Apps.details(ui.activity, a) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderSheet(ui: LauncherUi, screen: Screen, id: Long) {
    val layout by Store.layout(screen).collectAsState()
    val item = layout.items.find { it.id == id }
    if (item == null) { ui.sheet = null; return }
    FolderDialog(
        ui, item.label ?: "Priečinok", item.apps,
        onRename = { ui.sheet = Sheet.RenameItem(id) },
        onAdd = { ui.sheet = Sheet.FolderApps(id) },
        onRemoveApp = { k -> Store.editLayout(screen) { l -> l.update(id) { it.copy(apps = it.apps - k) } } },
    )
}

/** Priečinok z obľúbených – zoznam v štýle obľúbených, posúvateľný. */
@Composable
fun FavFolderSheet(ui: LauncherUi, id: String) {
    val s by Store.settings.collectAsState()
    val apps by Apps.list.collectAsState()
    val notifs by Badges.state.collectAsState()
    val f = s.favFolders[id]
    if (f == null) { ui.sheet = null; return }
    val inside = remember(apps, f.apps) { f.apps.mapNotNull { k -> apps.firstOrNull { it.key == k } } }
    val iconSize = s.favIconSize.dp
    val rowH = iconSize + 24.dp
    val titleSize = (s.favIconSize * 0.6f).coerceIn(13f, 24f).sp
    val subSize = (s.favIconSize * 0.42f).coerceIn(11f, 15f).sp
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    val ff = LocalFont.current
    Dialog(onDismissRequest = { ui.sheet = null }) {
        PopupSurface {
            Column(Modifier.padding(vertical = 16.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FavFolderIcon(28.dp, s)
                    Spacer(Modifier.width(12.dp))
                    Text(f.name, style = MaterialTheme.typography.titleLarge, fontFamily = ff,
                        modifier = Modifier.weight(1f).clickable { ui.sheet = Sheet.RenameFavFolder(id) })
                    IconButton(onClick = { ui.sheet = Sheet.RenameFavFolder(id) }) { Icon(Icons.Filled.Edit, "Premenovať") }
                    IconButton(onClick = { ui.sheet = Sheet.FavFolderApps(id) }) { Icon(Icons.Filled.Add, "Pridať aplikácie") }
                }
                if (inside.isEmpty()) Text("Priečinok je prázdny – pridaj aplikácie tlačidlom +", Modifier.padding(24.dp))
                LazyColumn(Modifier.heightIn(max = maxH).padding(horizontal = 8.dp)) {
                    items(inside, key = { it.key }) { a ->
                        var menu by remember { mutableStateOf(false) }
                        val n = notifs[a.pkg]
                        Box {
                            Row(
                                Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { Apps.launch(ui.activity, a); ui.sheet = null },
                                        onLongClick = { menu = true },
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box {
                                    AppIconImage(a, iconSize, s)
                                    BadgeMark(n?.count ?: 0, s, Modifier.align(Alignment.Center))
                                }
                                Spacer(Modifier.width(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(labelOf(a, s), fontSize = titleSize, fontFamily = ff, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val line = if (s.favNotifText && n != null) listOfNotNull(n.title, n.text).joinToString(" · ") else ""
                                    if (line.isNotEmpty()) Text(line, fontSize = subSize, fontFamily = ff, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, color = PopupSub)
                                }
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Odobrať z priečinka") }, onClick = {
                                    menu = false; editFavFolder(id) { it.copy(apps = it.apps - a.key) }
                                })
                                DropdownMenuItem(text = { Text("Informácie o aplikácii") }, onClick = { menu = false; Apps.details(ui.activity, a) })
                            }
                        }
                    }
                }
            }
        }
    }
}

fun editFavFolder(id: String, f: (FavFolder) -> FavFolder) = Store.editSettings { st ->
    val cur = st.favFolders[id] ?: return@editSettings st
    st.copy(favFolders = st.favFolders + (id to f(cur)))
}

/** Vytvorí priečinok v obľúbených a vráti jeho kľúč. */
fun newFavFolder(name: String): String {
    val id = FOLDER_PREFIX + Store.newId()
    Store.editSettings { st -> st.copy(favorites = st.favorites + id, favFolders = st.favFolders + (id to FavFolder(name, emptyList()))) }
    return id
}

// ---------------- obľúbené ----------------

/** Úprava obľúbených – poradie sa mení ťahaním (podrž riadok alebo ťahaj za úchyt). */
@Composable
fun FavoritesEditor(ui: LauncherUi, onClose: () -> Unit) {
    val s by Store.settings.collectAsState()
    val apps by Apps.list.collectAsState()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var picking by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var folderApps by remember { mutableStateOf<String?>(null) }
    var renameFolder by remember { mutableStateOf<String?>(null) }

    if (picking) {
        AppPicker("Obľúbené aplikácie", s.favorites.filterNot { it.startsWith(FOLDER_PREFIX) }, onClose = { picking = false }) { keys ->
            // zachová poradie a priečinky, nové aplikácie pridá na koniec
            Store.editSettings { st ->
                st.copy(favorites = st.favorites.filter { it.startsWith(FOLDER_PREFIX) || it in keys } + keys.filter { it !in st.favorites })
            }
            picking = false
        }
        return
    }
    folderApps?.let { fid ->
        AppPicker("Obsah priečinka", s.favFolders[fid]?.apps ?: emptyList(), onClose = { folderApps = null }) { keys ->
            editFavFolder(fid) { it.copy(apps = keys) }
            folderApps = null
        }
        return
    }

    // Jeden stabilný zoznam (gestá si ho pamätajú) – zosynchronizuje sa, keď sa práve neťahá.
    val list = remember { mutableStateListOf<String>().apply { addAll(s.favorites) } }
    var dragKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.favorites, dragKey) {
        if (dragKey == null && list.toList() != s.favorites) { list.clear(); list.addAll(s.favorites) }
    }
    var dragOff by remember { mutableFloatStateOf(0f) }
    val rowDp = 64.dp
    val rowPx = with(density) { rowDp.toPx() }
    fun commit() { Store.editSettings { it.copy(favorites = list.toList()) } }
    fun onDrag(k: String, dy: Float) {
        dragOff += dy
        val i = list.indexOf(k)
        if (dragOff > rowPx / 2 && i in 0 until list.size - 1) {
            list.swapAt(i, i + 1); dragOff -= rowPx; buzz(haptic, HapticFeedbackType.TextHandleMove)
        } else if (dragOff < -rowPx / 2 && i > 0) {
            list.swapAt(i, i - 1); dragOff += rowPx; buzz(haptic, HapticFeedbackType.TextHandleMove)
        }
    }

    FullSheet("Obľúbené", onClose = onClose, actions = {
        IconButton(onClick = { newFolder = true }) { Icon(Icons.Filled.CreateNewFolder, "Nový priečinok") }
        IconButton(onClick = { picking = true }) { Icon(Icons.Filled.Add, "Pridať aplikácie") }
    }) {
        Text(
            "Poradie zmeníš podržaním a potiahnutím riadku. Tlačidlom priečinka vytvoríš priečinok.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = dragKey == null)) {
            list.forEach { k ->
                key(k) {
                    val isDrag = dragKey == k
                    val folder = if (k.startsWith(FOLDER_PREFIX)) s.favFolders[k] else null
                    val a = if (folder == null) apps.firstOrNull { it.key == k } else null
                    val dragMod = Modifier.pointerInput(k) {
                        detectDragGestures(
                            onDragStart = { dragKey = k; dragOff = 0f; buzz(haptic, HapticFeedbackType.LongPress) },
                            onDrag = { c, d -> c.consume(); onDrag(k, d.y) },
                            onDragEnd = { if (dragKey == k) { dragKey = null; dragOff = 0f; commit() } },
                            onDragCancel = { if (dragKey == k) { dragKey = null; dragOff = 0f; commit() } },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().height(rowDp).zIndex(if (isDrag) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDrag) dragOff else 0f
                                if (isDrag) { scaleX = 1.02f; scaleY = 1.02f; shadowElevation = 12f }
                            }
                            .background(if (isDrag) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(14.dp))
                            .pointerInput(k) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragKey = k; dragOff = 0f; buzz(haptic, HapticFeedbackType.LongPress) },
                                    onDrag = { c, d -> c.consume(); onDrag(k, d.y) },
                                    onDragEnd = { if (dragKey == k) { dragKey = null; dragOff = 0f; commit() } },
                                    onDragCancel = { if (dragKey == k) { dragKey = null; dragOff = 0f; commit() } },
                                )
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.DragHandle, "Presunúť", Modifier.size(40.dp).then(dragMod).padding(8.dp))
                        when {
                            folder != null -> FavFolderIcon(40.dp, s)
                            a != null -> AppIconImage(a, 40.dp, s)
                            else -> Spacer(Modifier.size(40.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder?.name ?: a?.let { labelOf(it, s) } ?: k.substringBefore('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (folder != null) Text("Priečinok", style = MaterialTheme.typography.bodySmall)
                        }
                        if (folder != null) {
                            IconButton(onClick = { renameFolder = k }) { Icon(Icons.Filled.Edit, "Premenovať") }
                            IconButton(onClick = { folderApps = k }) { Icon(Icons.Filled.Apps, "Obsah") }
                        }
                        IconButton(onClick = {
                            list.remove(k)
                            Store.editSettings { st -> st.copy(favorites = st.favorites - k, favFolders = st.favFolders - k) }
                        }) { Icon(Icons.Filled.Close, "Odobrať") }
                    }
                }
            }
        }
    }

    if (newFolder) TextDialog("Nový priečinok", "Priečinok", "", onDismiss = { newFolder = false }) { name ->
        newFolder = false
        folderApps = newFavFolder(name.ifEmpty { "Priečinok" })
    }
    renameFolder?.let { fid ->
        TextDialog("Premenovať priečinok", s.favFolders[fid]?.name ?: "", "", onDismiss = { renameFolder = null }) { name ->
            if (name.isNotEmpty()) editFavFolder(fid) { it.copy(name = name) }
            renameFolder = null
        }
    }
}

private fun <T> MutableList<T>.swapAt(a: Int, b: Int) {
    val t = this[a]; this[a] = this[b]; this[b] = t
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
        Sheet.Favorites -> FavoritesEditor(ui) { ui.sheet = null }
        Sheet.ClockSettings -> ClockSettingsPage(ui) { ui.sheet = null }
        Sheet.AssistantSettings -> AssistantPage(ui) { ui.sheet = null }
        is Sheet.FavFolder -> FavFolderSheet(ui, sh.id)
        is Sheet.FavFolderApps -> AppPicker("Obsah priečinka", s.favFolders[sh.id]?.apps ?: emptyList(), onClose = { ui.sheet = Sheet.FavFolder(sh.id) }) { keys ->
            editFavFolder(sh.id) { it.copy(apps = keys) }
            ui.sheet = Sheet.FavFolder(sh.id)
        }
        is Sheet.RenameFavFolder -> TextDialog("Premenovať priečinok", s.favFolders[sh.id]?.name ?: "", "",
            onDismiss = { ui.sheet = Sheet.FavFolder(sh.id) }) { name ->
            if (name.isNotEmpty()) editFavFolder(sh.id) { it.copy(name = name) }
            ui.sheet = Sheet.FavFolder(sh.id)
        }
        is Sheet.Folder -> FolderSheet(ui, screen, sh.id)
        is Sheet.FolderApps -> {
            val item = Store.layout(screen).value.items.find { it.id == sh.id }
            AppPicker("Obsah priečinka", item?.apps ?: emptyList(), onClose = { ui.sheet = Sheet.Folder(sh.id) }) { keys ->
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
            TextDialog(if (item?.type == ItemType.FOLDER) "Názov priečinka" else "Názov na ploche", item?.let { itemTitle(it, s) } ?: "", "Prázdne = pôvodný názov",
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
