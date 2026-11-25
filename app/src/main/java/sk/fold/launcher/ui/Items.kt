package sk.fold.launcher.ui

import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sk.fold.launcher.data.AppInfo
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.BadgeStyle
import sk.fold.launcher.data.Badges
import sk.fold.launcher.data.ClockStyle
import sk.fold.launcher.data.FOLDER_PREFIX
import sk.fold.launcher.data.HomeItem
import sk.fold.launcher.data.IconCache
import sk.fold.launcher.data.ItemType
import sk.fold.launcher.data.LauncherWidgetView
import sk.fold.launcher.data.Settings
import sk.fold.launcher.data.Store
import sk.fold.launcher.data.Widgets
import sk.fold.launcher.data.labelOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun labelStyle(s: Settings, size: TextUnit, color: Color = Color.White) = TextStyle(
    color = color,
    fontSize = size,
    shadow = if (s.labelShadow) Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1.5f), 5f) else null,
)

@Composable
fun AppIconImage(a: AppInfo, size: Dp, s: Settings, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dark = LocalDark.current
    val px = IconCache.bucket(with(LocalDensity.current) { size.roundToPx() })
    val k = IconCache.key(a.key, px, s, dark)
    // Pri zmene aplikácie (napr. po preusporiadaní) sa ikona vždy nahradí – nikdy neostane stará.
    val bmp by produceState<ImageBitmap?>(IconCache.cached(k), k) {
        value = IconCache.cached(k) ?: withContext(Dispatchers.IO) { runCatching { IconCache.app(ctx, a, px, s, dark) }.getOrNull() }
    }
    val b = bmp
    if (b != null) Image(b, null, modifier.size(size)) else Spacer(modifier.size(size))
}

@Composable
fun ShortcutIconImage(item: HomeItem, size: Dp, s: Settings) {
    val ctx = LocalContext.current
    val dark = LocalDark.current
    val px = IconCache.bucket(with(LocalDensity.current) { size.roundToPx() })
    val bmp by produceState<ImageBitmap?>(null, item.id, px, s.iconShape, dark) {
        value = withContext(Dispatchers.IO) { runCatching { IconCache.shortcut(ctx, item, px, s, dark) }.getOrNull() }
    }
    val b = bmp
    if (b != null) Image(b, null, Modifier.size(size))
    else Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
}

@Composable
fun BadgeMark(count: Int, s: Settings, modifier: Modifier = Modifier) {
    if (count <= 0 || s.badges == BadgeStyle.NONE) return
    val c = MaterialTheme.colorScheme.primary
    if (s.badges == BadgeStyle.DOT) {
        Box(modifier.size(11.dp).clip(CircleShape).background(Color.White).padding(1.5.dp).clip(CircleShape).background(c))
    } else {
        Box(modifier.clip(CircleShape).background(c).padding(horizontal = 5.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
            Text(if (count > 99) "99+" else "$count", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Spätné volania na ťahanie widgetu po dlhom podržaní. */
class WidgetDrag(val start: () -> Unit, val move: (Offset) -> Unit, val end: (Boolean) -> Unit)

@Composable
fun ItemContent(ui: LauncherUi, item: HomeItem, s: Settings, wDp: Dp, hDp: Dp, drag: WidgetDrag? = null) {
    when (item.type) {
        ItemType.APP -> {
            val apps by Apps.list.collectAsState()
            val a = remember(apps, item.app) { apps.firstOrNull { it.key == item.app } }
            val notifs by Badges.state.collectAsState()
            if (a != null) IconTile(item.label ?: labelOf(a, s), s, wDp, hDp, notifs[a.pkg]?.count ?: 0) { sz -> AppIconImage(a, sz, s) }
            else IconTile("?", s, wDp, hDp, 0) { sz -> Box(Modifier.size(sz).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) }
        }
        ItemType.SHORTCUT -> {
            val notifs by Badges.state.collectAsState()
            IconTile(item.label ?: "", s, wDp, hDp, item.pkg?.let { notifs[it]?.count } ?: 0) { sz -> ShortcutIconImage(item, sz, s) }
        }
        ItemType.FOLDER -> {
            val apps by Apps.list.collectAsState()
            val notifs by Badges.state.collectAsState()
            val count = remember(apps, notifs, item.apps) {
                item.apps.sumOf { k -> apps.firstOrNull { it.key == k }?.let { notifs[it.pkg]?.count } ?: 0 }
            }
            IconTile(item.label ?: "Priečinok", s, wDp, hDp, count) { sz -> FolderIcon(item, sz, s) }
        }
        ItemType.WIDGET -> WidgetBox(ui, item, wDp, hDp, drag)
        ItemType.CLOCK -> ClockView(s, wDp, hDp)
        ItemType.FAVORITES -> FavoritesView(ui, s, hDp)
        ItemType.SEARCH -> {}
    }
}

@Composable
private fun IconTile(label: String, s: Settings, wDp: Dp, hDp: Dp, badge: Int, icon: @Composable (Dp) -> Unit) {
    // Zvislý odstup, aby ikony neboli nalepené nad sebou.
    val pad = hDp * 0.08f
    val labelH = (s.labelSize * 1.45f + 3f).dp
    val showLabel = s.showLabels && hDp - labelH - pad * 2 >= 24.dp
    val availH = hDp - pad * 2 - (if (showLabel) labelH + 3.dp else 0.dp)
    val iconSize = minOf(wDp * s.iconScale, availH).coerceAtLeast(16.dp)
    Column(
        Modifier.fillMaxSize().padding(vertical = pad),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box {
            icon(iconSize)
            BadgeMark(badge, s, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
        }
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(
                label, style = labelStyle(s, s.labelSize.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, fontFamily = LocalFont.current,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun FolderIcon(item: HomeItem, size: Dp, s: Settings) = FolderIconOf(item.apps, size, s)

/** Klasická ikona priečinka pre obľúbené – farba podľa nastavenia. */
@Composable
fun FavFolderIcon(size: Dp, s: Settings) {
    Icon(Icons.Filled.Folder, null, tint = Color(s.favFolderColor), modifier = Modifier.size(size))
}

/** Ikona priečinka – mriežka 2×2 prvých aplikácií. */
@Composable
fun FolderIconOf(keys: List<String>, size: Dp, s: Settings) {
    val apps by Apps.list.collectAsState()
    val inside = remember(apps, keys) { keys.mapNotNull { k -> apps.firstOrNull { it.key == k } }.take(4) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color.White.copy(alpha = 0.28f)).padding(size * 0.12f),
    ) {
        val mini = size * 0.34f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            for (row in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0..1) {
                    val a = inside.getOrNull(row * 2 + col)
                    if (a != null) AppIconImage(a, mini, s) else Spacer(Modifier.size(mini))
                }
            }
        }
    }
}

@Composable
private fun WidgetBox(ui: LauncherUi, item: HomeItem, wDp: Dp, hDp: Dp, drag: WidgetDrag?) {
    val ctx = LocalContext.current
    val info = remember(item.widgetId) { runCatching { Widgets.manager.getAppWidgetInfo(item.widgetId) }.getOrNull() }
    if (info == null) {
        Box(
            Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Widgets, null, tint = Color.White)
                Text("Widget nie je dostupný", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }
    val view = remember(item.widgetId) {
        Widgets.host.createView(ctx, item.widgetId, info) as? LauncherWidgetView
    }
    if (view == null) return
    val onLong by rememberUpdatedState { ui.startEdit(item.id) }
    AndroidView(
        factory = { (view.parent as? ViewGroup)?.removeView(view); view },
        modifier = Modifier.fillMaxSize(),
        update = { v ->
            v.onLongPress = { onLong() }
            v.onDragStart = { drag?.start?.invoke() }
            v.onDrag = { dx, dy -> drag?.move?.invoke(Offset(dx, dy)) }
            v.onDragEnd = { ok -> drag?.end?.invoke(ok) }
            v.onSwipe = { dir ->
                if (!ui.editMode) {
                    val st = Store.settings.value
                    if (dir < 0) ui.gesture(st.swipeUp) else ui.gesture(st.swipeDown)
                }
            }
        },
    )
    LaunchedEffect(view, wDp, hDp) { Widgets.updateSize(view, wDp.value, hDp.value) }
}

@Composable
private fun ClockView(s: Settings, wDp: Dp, hDp: Dp) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val density = LocalDensity.current
    val ff = LocalFont.current
    val time = remember(now, s.clock24) { SimpleDateFormat(if (s.clock24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 60000) { SimpleDateFormat("EEEE d. MMMM", Locale.getDefault()).format(Date(now)).replaceFirstChar { it.uppercase() } }
    fun sp(d: Dp) = with(density) { d.toSp() }
    Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
        when (s.clockStyle) {
            ClockStyle.LARGE -> Column {
                val tSize = minOf(if (s.clockDate) hDp * 0.6f else hDp * 0.82f, wDp * 0.3f)
                Text(time, style = labelStyle(s, sp(tSize)), fontWeight = FontWeight.Light, fontFamily = ff, maxLines = 1)
                if (s.clockDate) Text(date, style = labelStyle(s, sp(minOf(hDp * 0.14f, 22.dp))), fontFamily = ff, maxLines = 1)
            }
            ClockStyle.STACKED -> Column {
                val parts = time.split(":")
                val tSize = minOf(if (s.clockDate) hDp * 0.36f else hDp * 0.44f, wDp * 0.5f)
                Text(parts.getOrElse(0) { "" }, style = labelStyle(s, sp(tSize)), fontWeight = FontWeight.Bold, fontFamily = ff, lineHeight = sp(tSize))
                Text(parts.getOrElse(1) { "" }, style = labelStyle(s, sp(tSize), MaterialTheme.colorScheme.primary), fontWeight = FontWeight.Bold, fontFamily = ff, lineHeight = sp(tSize))
                if (s.clockDate) Text(date, style = labelStyle(s, sp(minOf(hDp * 0.11f, 18.dp))), fontFamily = ff, maxLines = 1)
            }
            ClockStyle.MINIMAL -> Row(verticalAlignment = Alignment.CenterVertically) {
                val tSize = minOf(hDp * 0.5f, wDp * 0.16f)
                Text(time, style = labelStyle(s, sp(tSize)), fontWeight = FontWeight.Medium, fontFamily = ff)
                if (s.clockDate) {
                    Spacer(Modifier.width(10.dp))
                    Text(date, style = labelStyle(s, sp(tSize * 0.42f)), fontFamily = ff, maxLines = 2)
                }
            }
        }
    }
}

/** Zoznam obľúbených v štýle Niagara – ikona, názov a posledná notifikácia; priečinky; posúvanie keď sa nezmestí. */
@Composable
private fun FavoritesView(ui: LauncherUi, s: Settings, hDp: Dp) {
    val apps by Apps.list.collectAsState()
    val notifs by Badges.state.collectAsState()
    val entries = remember(apps, s.favorites, s.favFolders) {
        s.favorites.mapNotNull { k ->
            if (k.startsWith(FOLDER_PREFIX)) s.favFolders[k]?.let { k to null }
            else apps.firstOrNull { it.key == k }?.let { k to it }
        }
    }
    val ff = LocalFont.current
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
                "Obľúbené sú prázdne.\nV zozname aplikácií podrž aplikáciu → Do obľúbených.",
                style = labelStyle(s, 13.sp), textAlign = TextAlign.Center, fontFamily = ff,
            )
        }
        return
    }
    val iconSize = s.favIconSize.dp
    // Rozmery ako v Niagare: ikona ~28 dp, riadok ~44 dp, názov ~20 sp.
    val rowH = iconSize + 24.dp
    val titleSize = (s.favIconSize * 0.6f).coerceIn(13f, 24f).sp
    val subSize = (s.favIconSize * 0.42f).coerceIn(11f, 15f).sp
    val needScroll = rowH * entries.size > hDp
    Column(
        Modifier.fillMaxSize().then(if (needScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = if (needScroll) Arrangement.Top else Arrangement.Center,
    ) {
        entries.forEach { (k, a) -> key(k) {
            val folder = if (a == null) s.favFolders[k] else null
            val count = if (a != null) notifs[a.pkg]?.count ?: 0
            else folder?.apps?.sumOf { key -> apps.firstOrNull { it.key == key }?.let { notifs[it.pkg]?.count } ?: 0 } ?: 0
            Row(
                Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(12.dp))
                    .clickable { if (a != null) Apps.launch(ui.activity, a) else ui.sheet = Sheet.FavFolder(k) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    if (a != null) AppIconImage(a, iconSize, s) else FavFolderIcon(iconSize, s)
                    BadgeMark(count, s, Modifier.align(Alignment.TopEnd))
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (a != null) labelOf(a, s) else folder?.name ?: "", style = labelStyle(s, titleSize),
                        fontFamily = ff, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val n = a?.let { notifs[it.pkg] }
                    val line = when {
                        folder != null -> ""
                        s.favNotifText && n != null -> listOfNotNull(n.title, n.text).joinToString(" · ")
                        else -> ""
                    }
                    if (line.isNotEmpty()) Text(
                        line, style = labelStyle(s, subSize, Color.White.copy(alpha = 0.85f)),
                        fontFamily = ff, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        }
    }
}
