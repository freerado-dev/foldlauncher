@file:OptIn(ExperimentalFoundationApi::class)

package sk.fold.launcher.ui

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sk.fold.launcher.data.AppInfo
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.Badges
import sk.fold.launcher.data.DrawerSort
import sk.fold.launcher.data.DrawerStyle
import sk.fold.launcher.data.Store
import sk.fold.launcher.data.Settings
import sk.fold.launcher.data.labelOf
import sk.fold.launcher.data.letterOf
import sk.fold.launcher.data.norm
import java.text.Collator
import kotlin.math.abs

private class Entry(val app: AppInfo, val label: String, val letter: String)

@Composable
fun AppDrawer(ui: LauncherUi, s: Settings) {
    val apps by Apps.list.collectAsState()
    val notifs by Badges.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()
    val density = LocalDensity.current

    val usage by Store.usage.collectAsState()
    val recentKeys by Store.recent.collectAsState()
    val all = remember(apps, s.hidden, s.renames, s.showWork, s.drawerSort, usage) {
        val coll = Collator.getInstance()
        val base = apps.filter { it.key !in s.hidden && (s.showWork || !it.work) }
            .map { a -> labelOf(a, s).let { Entry(a, it, letterOf(it)) } }
            .sortedWith(compareBy(coll) { it.label })
        when (s.drawerSort) {
            DrawerSort.ALPHA -> base
            DrawerSort.USAGE -> base.sortedByDescending { usage[it.app.key] ?: 0 }
            DrawerSort.INSTALL -> base.sortedByDescending { it.app.installed }
        }
    }
    val recentApps = remember(apps, recentKeys, s.hidden) {
        recentKeys.mapNotNull { k -> apps.firstOrNull { it.key == k && it.key !in s.hidden } }.take(5)
    }
    val visible = remember(all, query) {
        if (query.isBlank()) all else {
            val q = norm(query.trim())
            all.filter { norm(it.label).contains(q) || it.app.pkg.contains(q) }
                .sortedBy { if (norm(it.label).startsWith(q)) 0 else 1 }
        }
    }
    val letters = remember(visible) { visible.map { it.letter }.distinct() }
    var bubble by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ui.searchFocus, s.autoKeyboard, ui.drawerOpen) {
        if (ui.drawerOpen && (ui.searchFocus || s.autoKeyboard)) runCatching { focus.requestFocus() }
    }

    fun openApp(a: AppInfo) {
        Apps.launch(ui.activity, a)
        keyboard?.hide()
        query = ""
        ui.closeDrawer()
    }

    // Zatváranie ťahaním nadol – zoznam ide za prstom a dobehne pružinou.
    val range = with(density) { (LocalConfiguration.current.screenHeightDp.dp * 0.6f).toPx() }
    val pull = remember(range) {
        object : NestedScrollConnection {
            var acc = 0f
            var flinging = false
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (acc > 0f && available.y < 0f) {
                    val use = maxOf(available.y, -acc)
                    acc += use
                    ui.dragDrawer(1f - acc / range)
                    return Offset(0f, use)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!flinging && available.y > 0f) {
                    if (acc == 0f) keyboard?.hide()
                    acc += available.y
                    ui.dragDrawer(1f - acc / range)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (acc > 0f) {
                    acc = 0f
                    ui.settleDrawer(-available.y / range)
                    return available
                }
                flinging = true
                return Velocity.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                flinging = false
                return Velocity.Zero
            }
        }
    }
    val headerDrag = Modifier.pointerInput(range) {
        var acc = 0f
        val tracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { acc = 0f; tracker.resetTracking(); keyboard?.hide() },
            onVerticalDrag = { ch, d ->
                ch.consume()
                tracker.addPosition(ch.uptimeMillis, ch.position)
                acc = (acc + d).coerceAtLeast(0f)
                ui.dragDrawer(1f - acc / range)
            },
            onDragEnd = { ui.settleDrawer(-tracker.calculateVelocity().y / range) },
            onDragCancel = { ui.settleDrawer(0f) },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = s.drawerAlpha)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxWidth().then(headerDrag).padding(top = 8.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
            }
            // Hlavička s vyhľadávaním – potiahnutím dole sa zoznam zavrie.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    .then(headerDrag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text("Hľadať aplikácie alebo na webe") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Vymazať") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val first = visible.firstOrNull()
                        if (first != null) openApp(first.app) else webSearch(ui, query)
                    }),
                )
                IconButton(onClick = { ui.sheet = Sheet.Settings }) { Icon(Icons.Filled.Settings, "Nastavenia") }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val list = s.drawerStyle == DrawerStyle.LIST
                val showBar = s.alphabet && query.isBlank() && letters.size > 1 && s.drawerSort == DrawerSort.ALPHA
                LazyVerticalGrid(
                    columns = if (list) GridCells.Adaptive(320.dp) else GridCells.Adaptive((s.drawerIconSize + 44f).dp),
                    state = grid,
                    modifier = Modifier.fillMaxSize().nestedScroll(pull),
                    contentPadding = PaddingValues(start = 8.dp, end = if (showBar) 40.dp else 8.dp, bottom = 24.dp),
                ) {
                    if (s.showRecent && query.isBlank() && recentApps.isNotEmpty()) {
                        item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Text("Naposledy", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    recentApps.forEach { a ->
                                        Column(
                                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                                .combinedClickable(onClick = { openApp(a) }).padding(vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            AppIconImage(a, 46.dp, s)
                                            Text(labelOf(a, s), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(visible, key = { _, e -> e.app.key }) { i, e ->
                        val firstOfLetter = query.isBlank() && s.drawerSort == DrawerSort.ALPHA && (i == 0 || visible[i - 1].letter != e.letter)
                        DrawerEntry(ui, e, s, list, if (list && firstOfLetter) e.letter else null,
                            notifs[e.app.pkg]?.count ?: 0) { openApp(e.app) }
                    }
                    if (query.isNotBlank()) item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .combinedClickable(onClick = { webSearch(ui, query) }).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Search, null)
                            Spacer(Modifier.width(12.dp))
                            Text("Hľadať „$query“ na webe")
                        }
                    }
                }
                if (showBar) {
                    AlphabetBar(
                        letters = letters,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onActive = { bubble = it },
                        onPick = { l ->
                            val idx = visible.indexOfFirst { it.letter == l }
                            val header = if (s.showRecent && recentApps.isNotEmpty()) 1 else 0
                            if (idx >= 0) scope.launch { grid.scrollToItem(idx + header) }
                        },
                    )
                }
                bubble?.let { b ->
                    Box(
                        Modifier.align(Alignment.Center).size(96.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(b, fontSize = 48.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun webSearch(ui: LauncherUi, q: String) {
    if (q.isBlank()) return
    val ok = runCatching {
        ui.activity.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!ok) runCatching {
        ui.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    ui.closeDrawer()
}

@Composable
private fun DrawerEntry(ui: LauncherUi, e: Entry, s: Settings, list: Boolean, letter: String?, badge: Int, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val ff = LocalFont.current
    Box {
        val mod = Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(
            onClick = onClick,
            onLongClick = { buzz(haptic, HapticFeedbackType.LongPress); menu = true },
        )
        if (list) {
            Row(mod.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    letter ?: "", Modifier.width(26.dp), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = ff,
                )
                Box {
                    AppIconImage(e.app, s.drawerIconSize.dp, s)
                    BadgeMark(badge, s, Modifier.align(Alignment.TopEnd))
                }
                Spacer(Modifier.width(14.dp))
                Text(e.label, color = onSurface, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = ff)
            }
        } else {
            Column(mod.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    AppIconImage(e.app, s.drawerIconSize.dp, s)
                    BadgeMark(badge, s, Modifier.align(Alignment.TopEnd))
                }
                Spacer(Modifier.height(4.dp))
                Text(e.label, color = onSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, fontFamily = ff, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            AppMenu(ui, e.app) { menu = false }
        }
    }
}

/** Abecedný posuvník s „vlnou“ písmen ako v Niagare. */
@Composable
private fun AlphabetBar(letters: List<String>, modifier: Modifier, onActive: (String?) -> Unit, onPick: (String) -> Unit) {
    var active by remember { mutableIntStateOf(-1) }
    val haptic = LocalHapticFeedback.current
    val pick by rememberUpdatedState(onPick)
    val act by rememberUpdatedState(onActive)
    val accent = MaterialTheme.colorScheme.primary
    val normal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    val density = LocalDensity.current
    Box(
        modifier.fillMaxHeight().width(36.dp).padding(vertical = 8.dp)
            .pointerInput(letters) {
                awaitEachGesture {
                    fun choose(y: Float) {
                        val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.size - 1)
                        if (i != active) {
                            active = i
                            buzz(haptic, HapticFeedbackType.TextHandleMove)
                            pick(letters[i]); act(letters[i])
                        }
                    }
                    val down = awaitFirstDown()
                    down.consume()
                    choose(down.position.y)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        c.consume()
                        choose(c.position.y)
                    }
                    active = -1
                    act(null)
                }
            },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            letters.forEachIndexed { i, l ->
                val f = if (active < 0) 0f else (1f - abs(i - active) / 4f).coerceIn(0f, 1f)
                Text(
                    l, fontSize = 11.sp, fontWeight = if (i == active) FontWeight.Bold else FontWeight.Medium,
                    color = if (i == active) accent else normal,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 1f + f * 1.1f
                        scaleY = 1f + f * 1.1f
                        translationX = -f * with(density) { 34.dp.toPx() }
                    },
                )
            }
        }
    }
}
